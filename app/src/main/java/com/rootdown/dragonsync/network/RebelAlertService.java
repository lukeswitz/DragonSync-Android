package com.rootdown.dragonsync.network;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class RebelAlertService extends Service {
    private static final String TAG = "RebelAlertService";
    private RebelHistoryManager historyManager;
    private final BroadcastReceiver RebelReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("com.rootdown.dragonsync.HIGH_CONFIDENCE_Rebel".equals(intent.getAction())) {
                handleHighConfidenceRebel(intent);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        historyManager = new RebelHistoryManager(this);
        registerReceiver(RebelReceiver, new IntentFilter("com.rootdown.dragonsync.HIGH_CONFIDENCE_Rebel"));
    }

    private void handleHighConfidenceRebel(Intent intent) {
        String RebelType = intent.getStringExtra("Rebel_type");
        String deviceId = intent.getStringExtra("device_id");
        double confidence = intent.getDoubleExtra("confidence", 0.0);

        Log.w(TAG, "HIGH CONFIDENCE Rebel ALERT: " + RebelType + " (" + confidence + ")");

        if (confidence > 0.9) {
            triggerEmergencyProtocol(RebelType, deviceId);
        }
    }

    private void triggerEmergencyProtocol(String RebelType, String deviceId) {
        Intent emergencyIntent = new Intent("com.rootdown.dragonsync.EMERGENCY_Rebel_PROTOCOL");
        emergencyIntent.putExtra("Rebel_type", RebelType);
        emergencyIntent.putExtra("device_id", deviceId);
        sendBroadcast(emergencyIntent);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        unregisterReceiver(RebelReceiver);
        super.onDestroy();
    }
}
