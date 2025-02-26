package com.rootdown.dragonsync.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.ConnectionMode;
import com.rootdown.dragonsync.utils.Settings;

public class NetworkService extends Service {
    private static final String TAG = "NetworkService";
    private static final String CHANNEL_ID = "DragonSyncNetwork";
    private static final int NOTIFICATION_ID = 1;

    private ZMQHandler zmqHandler;
    private MulticastHandler multicastHandler;
    private Settings settings;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = Settings.getInstance(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Network service onStartCommand triggered");

        if (!isRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, createNotification());
            }

            // Check for explicit connection mode
            if (intent != null && intent.hasExtra("CONNECTION_MODE")) {
                String modeString = intent.getStringExtra("CONNECTION_MODE");
                try {
                    ConnectionMode mode = ConnectionMode.valueOf(modeString);
                    settings.setConnectionMode(mode);
                    Log.i(TAG, "Using connection mode from intent: " + mode);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Invalid connection mode: " + modeString);
                }
            }

            startNetworkHandlers();
            isRunning = true;
        }
        return START_STICKY;
    }

    private void startNetworkHandlers() {
        ConnectionMode mode = settings.getConnectionMode();
        Log.i(TAG, "Starting network handler with mode: " + mode.name());

        switch (mode) {
            case ZMQ:
                startZMQHandler();
                break;
            case MULTICAST:
                startMulticastHandler();
                break;
            default:
                Log.e(TAG, "Unknown connection mode: " + mode);
                break;
        }
    }

    private void startZMQHandler() {
        zmqHandler = new ZMQHandler();
        Log.d(TAG, "ZMQ starting");
        zmqHandler.connect(
                settings.getZmqHost(),
                settings.getZmqTelemetryPort(),
                settings.getZmqStatusPort(),
                message -> {
                    Log.d(TAG, "ZMQ telemetry received: " + message.substring(0, Math.min(50, message.length())));
                    handleMessage(message, true);
                },
                message -> {
                    Log.d(TAG, "ZMQ status received: " + message.substring(0, Math.min(50, message.length())));
                    handleMessage(message, false);
                }
        );
    }

    private void startMulticastHandler() {
        multicastHandler = new MulticastHandler();
        Log.d(TAG, "Multicast starting");
        multicastHandler.startListening(
                settings.getMulticastHost(),
                settings.getMulticastPort(),
                new MulticastHandler.MessageHandler() {
                    @Override
                    public void onMessage(String message) {
                        Log.d(TAG, "Multicast message received: " + message.substring(0, Math.min(50, message.length())));
                        handleMessage(message, true);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Multicast error: " + error);
                        updateNotification("Connection error: " + error);
                    }
                }
        );
    }

    private void handleMessage(String message, boolean isTelemetry) {
        // Parse and broadcast message
        Intent intent = new Intent(isTelemetry ? "com.rootdown.dragonsync.TELEMETRY"
                : "com.rootdown.dragonsync.STATUS");
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DragonSync Network")
                .setContentText("Monitoring drone signals...")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    private void updateNotification(String status) {
        NotificationManager notificationManager =
                getSystemService(NotificationManager.class);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DragonSync Network")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DragonSync Network Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Network monitoring service");

        NotificationManager notificationManager =
                getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (zmqHandler != null) {
            zmqHandler.disconnect();
            zmqHandler = null;
            Log.d(TAG, "ZMQ stopped");
        }
        if (multicastHandler != null) {
            multicastHandler.stopListening();
            multicastHandler = null;
            Log.d(TAG, "Multicast stopped");
        }

        // Update the settings to reflect that we're no longer listening
        settings.setListening(false);

        isRunning = false;
        Log.i(TAG, "Network service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}