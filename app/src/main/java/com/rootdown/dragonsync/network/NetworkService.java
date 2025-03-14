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
import com.rootdown.dragonsync.models.StatusMessage;
import com.rootdown.dragonsync.utils.Settings;

import org.json.JSONException;
import org.json.JSONObject;

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

            ConnectionMode mode = settings.getConnectionMode();

            // Handle onboard detection mode separately
            if (mode == ConnectionMode.ONBOARD) {
                // Start the onboard detection service instead
                Intent onboardIntent = new Intent(this, OnboardDetectionService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(onboardIntent);
                } else {
                    startService(onboardIntent);
                }

                // This service is not needed for onboard mode
                stopSelf();
                return START_NOT_STICKY;
            }

            // Otherwise proceed with network modes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, createNotification());
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
        multicastHandler = new MulticastHandler(this);  // Pass context
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

                        // Automatically turn off the connection when an error occurs
                        settings.setListening(false);

                        // Send a broadcast to update the UI
                        Intent statusIntent = new Intent("com.rootdown.dragonsync.CONNECTION_ERROR");
                        statusIntent.setPackage(getPackageName());
                        statusIntent.putExtra("error_message", error);
                        sendBroadcast(statusIntent);

                        // Stop the service
                        stopSelf();
                    }
                }
        );
    }


    private void handleMessage(String message, boolean isTelemetry) {
        logMessageFormat(message);

        XMLParser parser = new XMLParser();
        XMLParser.ParseResult result = parser.parse(message);

        if (result.error != null) {
            Log.e(TAG, "Failed to parse message: " + result.error);
            return;
        }

        // Check if we have a status message
        if (result.statusMessage != null) {
            Intent statusIntent = new Intent("com.rootdown.dragonsync.STATUS");
            statusIntent.setPackage(getPackageName());
            statusIntent.putExtra("status_message", result.statusMessage);
            statusIntent.putExtra("raw_message", message);
            sendBroadcast(statusIntent);
            Log.d(TAG, "Broadcast status message");
        }

        // Check if we have a telemetry message (but only if it's not the same as a status message)
        if (result.cotMessage != null) {
            Intent telemetryIntent = new Intent("com.rootdown.dragonsync.TELEMETRY");
            telemetryIntent.setPackage(getPackageName());
            telemetryIntent.putExtra("parsed_message", result.cotMessage);
            telemetryIntent.putExtra("raw_message", message);
            sendBroadcast(telemetryIntent);
            Log.d(TAG, "Broadcast telemetry for drone: " + result.cotMessage.getUid());
        }
    }

    private void logMessageFormat(String message) {
        String logMessage = message.length() > 500 ? message.substring(0, 500) + "..." : message;

        if (message.trim().startsWith("<")) {
            if (message.contains("CPU Usage")) {
                Log.d(TAG, "Status XML Message: " + logMessage);
            } else if (message.contains("drone-")) {
                Log.d(TAG, "CoT XML Message: " + logMessage);
            } else {
                Log.d(TAG, "Unknown XML format: " + logMessage);
            }
        } else {
            try {
                new JSONObject(message);
                Log.d(TAG, "JSON Message: " + logMessage);
            } catch (JSONException e) {
                Log.d(TAG, "Unknown message format: " + logMessage);
            }
        }
    }

    private StatusMessage parseStatusMessage(JSONObject json) {
        try {
            JSONObject systemStats = json.getJSONObject("system_stats");

            StatusMessage message = new StatusMessage();
            if (json.has("serial_number")) {
                message.setSerialNumber(json.getString("serial_number"));
            }

            // Parse system stats
            StatusMessage.SystemStats stats = new StatusMessage.SystemStats();
            if (systemStats.has("cpu")) {
                stats.setCpuUsage(systemStats.getDouble("cpu"));
            }

            if (systemStats.has("temperature")) {
                stats.setTemperature(systemStats.getDouble("temperature"));
            }

            if (systemStats.has("uptime")) {
                stats.setUptime(systemStats.getDouble("uptime"));
            }

            if (systemStats.has("memory")) {
                JSONObject memJson = systemStats.getJSONObject("memory");
                StatusMessage.SystemStats.MemoryStats memory = new StatusMessage.SystemStats.MemoryStats();
                memory.setTotal(memJson.getLong("total"));
                memory.setUsed(memJson.getLong("used"));
                memory.setFree(memJson.getLong("free"));
                memory.setPercent(memJson.getDouble("percent"));
                stats.setMemory(memory);
            }

            message.setSystemStats(stats);

            // Parse ANT stats if available
            if (json.has("ant_stats")) {
                JSONObject antJson = json.getJSONObject("ant_stats");
                StatusMessage.ANTStats antStats = new StatusMessage.ANTStats();

                if (antJson.has("pluto_temp")) {
                    antStats.setPlutoTemp(antJson.getDouble("pluto_temp"));
                }

                if (antJson.has("zynq_temp")) {
                    antStats.setZynqTemp(antJson.getDouble("zynq_temp"));
                }

                message.setAntStats(antStats);
            }

            // Parse GPS data if available
            if (json.has("gps_data")) {
                JSONObject gpsJson = json.getJSONObject("gps_data");
                StatusMessage.GPSData gpsData = new StatusMessage.GPSData();

                if (gpsJson.has("latitude")) {
                    gpsData.setLatitude(gpsJson.getDouble("latitude"));
                }

                if (gpsJson.has("longitude")) {
                    gpsData.setLongitude(gpsJson.getDouble("longitude"));
                }

                if (gpsJson.has("altitude")) {
                    gpsData.setAltitude(gpsJson.getDouble("altitude"));
                }

                if (gpsJson.has("speed")) {
                    gpsData.setSpeed(gpsJson.getDouble("speed"));
                }

                message.setGpsData(gpsData);
            }

            return message;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing status message: " + e.getMessage());
            return null;
        }
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