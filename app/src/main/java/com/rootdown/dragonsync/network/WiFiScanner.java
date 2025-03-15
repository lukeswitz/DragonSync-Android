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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rootdown.dragonsync.utils.DroneDataParser;

public class WiFiScanner {
    private static final String TAG = "WiFiScanner";
    private static final long SCAN_INTERVAL = 30000; // 30 seconds between scans
    private static final int[] DRI_CID = {0xFA, 0x0B, 0xBC};  // Drone Remote ID Company Identifier
    private static final int CID_LENGTH = 3;
    private static final int DRI_START_OFFSET = 4;
    private static final int VENDOR_TYPE_LENGTH = 1;
    private static final int VENDOR_TYPE_VALUE = 0x0D;  // Open Drone ID Application Code

    private Context context;
    private WifiManager wifiManager;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OnDroneDetectedListener listener;
    private BroadcastReceiver wifiScanReceiver;
    private final DroneDataParser dataParser;

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
        this.dataParser = new DroneDataParser();
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
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                Context.RECEIVER_NOT_EXPORTED
        );

        // Start periodic scanning
        handler.post(scanRunnable);
        isScanning = true;
        Log.d(TAG, "🔍 STARTING WIFI SCAN FOR DRONES");
        return true;
    }

    public void stopScanning() {
        if (isScanning) {
            try {
                context.unregisterReceiver(wifiScanReceiver);
            } catch (IllegalArgumentException e) {
                // Ignore if receiver wasn't registered
            }

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
                Log.e(TAG, "Failed to start WiFi scan - scan throttling may be active");
            }
        }
    }

    private void processResults() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        List<ScanResult> results = wifiManager.getScanResults();
        for (ScanResult result : results) {
            // Check if it's a drone based on SSID
            if (isDroneWiFi(result)) {
                JSONArray droneData = createDroneWiFiData(result);
                if (droneData.length() > 0) {
                    listener.onDroneDetected(droneData);
                    Log.d(TAG, "Detected drone WiFi: " + result.SSID + ", BSSID: " + result.BSSID);
                }
            }

            // Check for Remote ID beacons in information elements
            processInformationElements(result);
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

    private void processInformationElements(ScanResult result) {
        // Android version dependent code to get the information elements
        try {
            ScanResult.InformationElement[] elements = getInformationElements(result);
            if (elements == null) return;

            for (ScanResult.InformationElement element : elements) {
                if (element == null) continue;

                int id = getElementId(element);
                if (id == 221) { // Vendor-specific element
                    byte[] data = getElementData(element);
                    if (data != null) {
                        processRemoteIdVendorData(result, ByteBuffer.wrap(data));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing information elements: " + e.getMessage());
        }
    }

    private void processRemoteIdVendorData(ScanResult result, ByteBuffer buffer) {
        if (buffer.remaining() < 30) return;

        byte[] cidBytes = new byte[CID_LENGTH];
        buffer.get(cidBytes, 0, CID_LENGTH);

        byte[] vendorType = new byte[VENDOR_TYPE_LENGTH];
        buffer.get(vendorType);

        // Check if this is a Drone Remote ID element
        if ((cidBytes[0] & 0xFF) == DRI_CID[0] &&
                (cidBytes[1] & 0xFF) == DRI_CID[1] &&
                (cidBytes[2] & 0xFF) == DRI_CID[2] &&
                vendorType[0] == VENDOR_TYPE_VALUE) {

            // Position buffer at the start of the Remote ID data
            buffer.position(DRI_START_OFFSET);
            byte[] beaconData = new byte[buffer.remaining()];
            buffer.get(beaconData);

            // Parse and notify
            JSONArray droneData = dataParser.parseWiFiBeaconData(beaconData, result.BSSID, result.level);
            if (droneData.length() > 0) {
                listener.onDroneDetected(droneData);
                Log.d(TAG, "Detected drone Remote ID in beacon: " + result.BSSID);
            }
        }
    }

    private ScanResult.InformationElement[] getInformationElements(ScanResult result) {
        try {
            // For Android 12 (API 31) and newer
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Convert List<InformationElement> to InformationElement[]
                List<ScanResult.InformationElement> elements = result.getInformationElements();
                return elements.toArray(new ScanResult.InformationElement[0]);
            } else {
                // For older versions, use reflection
                java.lang.reflect.Field field = ScanResult.class.getDeclaredField("informationElements");
                field.setAccessible(true);
                return (ScanResult.InformationElement[]) field.get(result);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get information elements: " + e.getMessage());
            return null;
        }
    }

    private int getElementId(ScanResult.InformationElement element) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                return element.getId();
            } else {
                // For older versions, use reflection
                java.lang.reflect.Field field = element.getClass().getDeclaredField("id");
                field.setAccessible(true);
                return (int) field.get(element);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get element ID: " + e.getMessage());
            return -1;
        }
    }

    private byte[] getElementData(ScanResult.InformationElement element) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                ByteBuffer buffer = element.getBytes();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                return data;
            } else {
                // For older versions, use reflection
                java.lang.reflect.Field field = element.getClass().getDeclaredField("bytes");
                field.setAccessible(true);
                return (byte[]) field.get(element);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get element data: " + e.getMessage());
            return null;
        }
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

            // Add self-ID message
            JSONObject selfIdObj = new JSONObject();
            JSONObject selfId = new JSONObject();
            selfIdObj.put("Self-ID Message", selfId);
            selfId.put("text", droneModel);
            messagesArray.put(selfIdObj);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating drone data: " + e.getMessage());
        }

        return messagesArray;
    }
}