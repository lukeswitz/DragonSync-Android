package com.rootdown.dragonsync.network;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WiFiScanner {
    private static final String TAG = "WiFiScanner";
    private static final long SCAN_INTERVAL = 30000; // 30 seconds between scans

    private Context context;
    private WifiManager wifiManager;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OnDroneDetectedListener listener;
    private BroadcastReceiver wifiScanReceiver;

    // Known drone WiFi SSID patterns
    private static final Map<String, String> DRONE_PATTERNS = new HashMap<>();
    static {
        // DJI drone patterns
        DRONE_PATTERNS.put("DJI_([A-Z0-9]+)", "DJI");
        DRONE_PATTERNS.put("MAVIC([A-Z0-9-]+)", "DJI");
        DRONE_PATTERNS.put("PHANTOM([A-Z0-9-]+)", "DJI");
        DRONE_PATTERNS.put("TELLO-([A-Z0-9]+)", "Ryze/DJI");

        // Parrot drone patterns
        DRONE_PATTERNS.put("ANAFI[_-]([A-Z0-9]+)", "Parrot");
        DRONE_PATTERNS.put("Bebop[_-]([A-Z0-9]+)", "Parrot");

        // Other known drones
        DRONE_PATTERNS.put("SKYDIO-([A-Z0-9]+)", "Skydio");
        DRONE_PATTERNS.put("AUTEL-([A-Z0-9]+)", "Autel");
        DRONE_PATTERNS.put("YUNEEC([_-][A-Z0-9]+)?", "Yuneec");
    }

    public interface OnDroneDetectedListener {
        void onDroneDetected(JSONArray droneData);
        void onError(String error);
    }

    public WiFiScanner(Context context, OnDroneDetectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        this.wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    processResults();
                } else {
                    Log.e(TAG, "WiFi scan was not successful");
                }
            }
        };
    }

    public boolean startScanning() {
        if (wifiManager == null) {
            listener.onError("WiFi manager not available");
            return false;
        }

        if (!wifiManager.isWifiEnabled()) {
            listener.onError("WiFi is disabled");
            return false;
        }

        if (isScanning) {
            return true; // Already scanning
        }

        // Register for scan results
        context.registerReceiver(
                wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );

        // Start periodic scanning
        handler.post(scanRunnable);
        isScanning = true;
        Log.d(TAG, "🔍 STARTING WIFI SCAN FOR DRONES");
        return true;
    }

    public void stopScanning() {
        if (isScanning) {
            context.unregisterReceiver(wifiScanReceiver);
            handler.removeCallbacks(scanRunnable);
            isScanning = false;
            Log.d(TAG, "WiFi scanning stopped");
        }
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanning) {
                performScan();
                handler.postDelayed(this, SCAN_INTERVAL);
            }
        }
    };

    private void performScan() {
        if (wifiManager != null) {
            boolean started = wifiManager.startScan();
            if (!started) {
                Log.e(TAG, "Failed to start WiFi scan");
            }
        }
    }

    private void processResults() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        List<ScanResult> results = wifiManager.getScanResults();
        for (ScanResult result : results) {
            if (isDroneWiFi(result)) {
                JSONArray droneData = createDroneWiFiData(result);
                if (droneData.length() > 0) {
                    listener.onDroneDetected(droneData);
                    Log.d(TAG, "Detected drone WiFi: " + result.SSID + ", BSSID: " + result.BSSID);
                }
            }
        }
    }

    private boolean isDroneWiFi(ScanResult result) {
        String ssid = result.SSID.toUpperCase();

        // Skip empty SSIDs
        if (ssid.isEmpty()) {
            return false;
        }

        // Check against known patterns
        for (String pattern : DRONE_PATTERNS.keySet()) {
            if (ssid.matches(pattern) || ssid.contains(pattern.replace("([A-Z0-9]+)", ""))) {
                return true;
            }
        }

        // Generic keywords
        return ssid.contains("DRONE") ||
                ssid.contains("UAV") ||
                ssid.contains("UAS") ||
                ssid.contains("COPTER");
    }

    private JSONArray createDroneWiFiData(ScanResult result) {
        JSONArray messagesArray = new JSONArray();

        try {
            // Create Basic ID message
            JSONObject basicIdObj = new JSONObject();
            JSONObject basicId = new JSONObject();
            basicIdObj.put("Basic ID", basicId);

            // Add MAC and RSSI
            basicId.put("MAC", result.BSSID);
            basicId.put("RSSI", result.level);

            // Try to identify manufacturer from SSID
            String manufacturer = "Unknown";
            String droneModel = result.SSID;
            String idType = "WiFi SSID";

            for (Map.Entry<String, String> entry : DRONE_PATTERNS.entrySet()) {
                Pattern pattern = Pattern.compile(entry.getKey());
                Matcher matcher = pattern.matcher(result.SSID);
                if (matcher.find()) {
                    manufacturer = entry.getValue();
                    if (matcher.groupCount() >= 1) {
                        String modelId = matcher.group(1);
                        if (modelId != null && !modelId.isEmpty()) {
                            droneModel = manufacturer + " " + modelId;
                        }
                    }
                    break;
                }
            }

            // For DJI drones, try to determine specific model from SSID
            if (manufacturer.equals("DJI")) {
                if (result.SSID.contains("MAVIC")) {
                    droneModel = "DJI Mavic";
                    if (result.SSID.contains("AIR")) droneModel += " Air";
                    else if (result.SSID.contains("MINI")) droneModel += " Mini";
                    else if (result.SSID.contains("PRO")) droneModel += " Pro";
                } else if (result.SSID.contains("PHANTOM")) {
                    droneModel = "DJI Phantom";
                    if (result.SSID.contains("3")) droneModel += " 3";
                    else if (result.SSID.contains("4")) droneModel += " 4";
                }
            }

            basicId.put("id_type", idType);
            basicId.put("id", result.SSID);
            basicId.put("description", droneModel);
            basicId.put("ua_type", "Helicopter (or Multirotor)");

            messagesArray.put(basicIdObj);

            // Add location if frequency is in 5GHz band (common for drone controllers)
            if (result.frequency > 5000) {
                // Create self-ID message
                JSONObject selfIdObj = new JSONObject();
                JSONObject selfId = new JSONObject();
                selfIdObj.put("Self-ID Message", selfId);
                selfId.put("text", droneModel);
                messagesArray.put(selfIdObj);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error creating drone data: " + e.getMessage());
        }

        return messagesArray;
    }
}