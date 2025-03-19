package com.rootdown.dragonsync.network;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.rootdown.dragonsync.utils.DroneDataParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

public class WiFiScanner {
    private static final String TAG = "WiFiScanner";
    private static final long SCAN_INTERVAL = 10000; // 10 seconds

    // OpenDroneID Vendor Specific Information Element Constants
    private static final int CID_LENGTH = 3;
    private static final int[] DRI_CID = {0xFA, 0x0B, 0xBC};
    private static final int DRI_START_OFFSET = 4;
    private static final int VENDOR_TYPE_LENGTH = 1;
    private static final int VENDOR_TYPE_VALUE = 0x0D;

    private final Context context;
    private final WifiManager wifiManager;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OnDroneDetectedListener listener;
    private CountDownTimer countDownTimer;
    private final DroneDataParser dataParser;

    // Stats tracking
    private int scanSuccess = 0;
    private int scanFailed = 0;

    public interface OnDroneDetectedListener {
        void onDroneDetected(JSONArray droneData);
        void onError(String error);
    }

    public WiFiScanner(Context context, OnDroneDetectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.dataParser = new DroneDataParser();

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.e(TAG, "WiFi Scanning is not supported on this device");
            wifiManager = null;
            return;
        }

        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager == null) {
            Log.e(TAG, "Could not get WifiManager");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.d(TAG, "Trying to enable WiFi");
            wifiManager.setWifiEnabled(true);
        }

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wifiScanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(wifiScanReceiver, filter);
        }
    }

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                Log.d(TAG, "WiFi scan results available");
                processScanResults();
            } else {
                Log.e(TAG, "WiFi scan was not successful");
                listener.onError("WiFi scan failed");
            }
        }
    };

    public boolean startScanning() {
        if (wifiManager == null) {
            Log.e(TAG, "WiFi manager not available");
            return false;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "WiFi is disabled");
            return false;
        }

        if (isScanning) {
            return true;
        }

        Log.d(TAG, "🔍 STARTING WIFI SCAN FOR DRONES");
        startCountDownTimer();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing location permission for WiFi scanning");
            return false;
        }

        boolean started = wifiManager.startScan();
        Log.d(TAG, "Initial WiFi scan started: " + started);
        if (started) {
            scanSuccess++;
        } else {
            scanFailed++;
        }

        isScanning = true;
        return true;
    }

    public void stopScanning() {
        if (isScanning) {
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }

            try {
                context.unregisterReceiver(wifiScanReceiver);
            } catch (IllegalArgumentException e) {
                // Ignore if receiver wasn't registered
            }

            isScanning = false;
            Log.d(TAG, "WiFi scanning stopped");
        }
    }

    private void startCountDownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(Long.MAX_VALUE, SCAN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                if (wifiManager != null && isScanning) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Missing location permission for WiFi scanning");
                        return;
                    }

                    boolean started = wifiManager.startScan();
                    Log.d(TAG, "Periodic WiFi scan started: " + started +
                            " (success: " + scanSuccess + ", failed: " + scanFailed + ")");

                    if (started) {
                        scanSuccess++;
                    } else {
                        scanFailed++;

                        // More aggressive scanning if throttled - try alternate approach
                        if (scanFailed > 3) {
                            Log.d(TAG, "Scan throttling detected, trying alternative approach");
                            handler.postDelayed(() -> {
                                wifiManager.startScan();
                            }, 2000);
                        }
                    }
                }
            }

            public void onFinish() {
                // Will not be called with Long.MAX_VALUE
            }
        }.start();
    }

    private void processScanResults() {
        if (wifiManager == null) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing location permission - can't get WiFi scan results");
            return;
        }

        List<ScanResult> results = wifiManager.getScanResults();
        Log.d(TAG, "Processing " + (results != null ? results.size() : 0) + " WiFi scan results");

        if (results == null || results.isEmpty()) {
            Log.d(TAG, "No WiFi scan results found");
            return;
        }

        for (ScanResult scanResult : results) {
            try {
                // First check if this is a drone-specific WiFi network
                if (isDroneWiFi(scanResult)) {
                    JSONArray droneData = createDroneWiFiData(scanResult);
                    if (droneData.length() > 0) {
                        listener.onDroneDetected(droneData);
                        Log.d(TAG, "Detected drone WiFi: " + scanResult.SSID + ", BSSID: " + scanResult.BSSID);
                    }
                }

                // Then check for OpenDroneID information elements regardless of SSID
                processInformationElements(scanResult);

            } catch (Exception e) {
                Log.e(TAG, "Error processing scan result: " + e.getMessage(), e);
            }
        }
    }

    private boolean isDroneWiFi(ScanResult scanResult) {
        if (scanResult.SSID == null || scanResult.SSID.isEmpty()) {
            return false;
        }

        String ssid = scanResult.SSID.toUpperCase();

        // Comprehensive list of known drone manufacturers in SSIDs
        return ssid.contains("DJI") ||
                ssid.contains("MAVIC") ||
                ssid.contains("PHANTOM") ||
                ssid.contains("TELLO") ||
                ssid.contains("ANAFI") ||
                ssid.contains("PARROT") ||
                ssid.contains("SKYDIO") ||
                ssid.contains("AUTEL") ||
                ssid.contains("YUNEEC") ||
                ssid.contains("DRONE") ||
                ssid.contains("UAV") ||
                ssid.contains("UAS") ||
                ssid.contains("COPTER") ||
                ssid.contains("QUAD") ||
                ssid.contains("REMOTE ID") ||
                ssid.contains("DRI_") ||
                ssid.contains("RID") ||
                ssid.contains("OPENDRONE");
    }

    private void processInformationElements(ScanResult scanResult) {
        try {
            ScanResult.InformationElement[] elements = getInformationElements(scanResult);
            if (elements == null) return;

            for (ScanResult.InformationElement element : elements) {
                if (element == null) continue;

                int id = getElementId(element);
                if (id == 221) { // Vendor-specific IE
                    byte[] data = getElementData(element);
                    if (data != null) {
                        processRemoteIdVendorData(scanResult, ByteBuffer.wrap(data));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing information elements: " + e.getMessage(), e);
        }
    }

    private void processRemoteIdVendorData(ScanResult scanResult, ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() < CID_LENGTH + VENDOR_TYPE_LENGTH) return;

        byte[] cidBytes = new byte[CID_LENGTH];
        buffer.get(cidBytes, 0, CID_LENGTH);

        byte[] vendorType = new byte[VENDOR_TYPE_LENGTH];
        buffer.get(vendorType);

        // Check for OpenDroneID information element pattern
        if ((cidBytes[0] & 0xFF) == DRI_CID[0] &&
                (cidBytes[1] & 0xFF) == DRI_CID[1] &&
                (cidBytes[2] & 0xFF) == DRI_CID[2] &&
                vendorType[0] == VENDOR_TYPE_VALUE) {

            // Extract the actual Remote ID data
            buffer.position(DRI_START_OFFSET);
            byte[] beaconData = new byte[buffer.remaining()];
            buffer.get(beaconData);

            JSONArray droneData = dataParser.parseWiFiBeaconData(beaconData, scanResult.BSSID, scanResult.level);
            if (droneData.length() > 0) {
                listener.onDroneDetected(droneData);
                Log.d(TAG, "Detected drone Remote ID in beacon: " + scanResult.BSSID +
                        " (RSSI: " + scanResult.level + "dBm)");
            }
        }
    }

    private ScanResult.InformationElement[] getInformationElements(ScanResult scanResult) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                List<ScanResult.InformationElement> elements = scanResult.getInformationElements();
                return elements != null ? elements.toArray(new ScanResult.InformationElement[0]) : null;
            } else {
                java.lang.reflect.Field field = ScanResult.class.getDeclaredField("informationElements");
                field.setAccessible(true);
                return (ScanResult.InformationElement[]) field.get(scanResult);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get information elements: " + e.getMessage(), e);
            return null;
        }
    }

    private int getElementId(ScanResult.InformationElement element) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return element.getId();
            } else {
                java.lang.reflect.Field field = element.getClass().getDeclaredField("id");
                field.setAccessible(true);
                return (int) field.get(element);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get element ID: " + e.getMessage(), e);
            return -1;
        }
    }

    private byte[] getElementData(ScanResult.InformationElement element) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ByteBuffer buffer = element.getBytes();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                return data;
            } else {
                java.lang.reflect.Field field = element.getClass().getDeclaredField("bytes");
                field.setAccessible(true);
                return (byte[]) field.get(element);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get element data: " + e.getMessage(), e);
            return null;
        }
    }

    private JSONArray createDroneWiFiData(ScanResult scanResult) {
        JSONArray messagesArray = new JSONArray();

        try {
            JSONObject basicIdObj = new JSONObject();
            JSONObject basicId = new JSONObject();
            basicIdObj.put("Basic ID", basicId);

            basicId.put("MAC", scanResult.BSSID);
            basicId.put("RSSI", scanResult.level);

            String manufacturer = "Unknown";
            String droneModel = scanResult.SSID;
            String idType = "WiFi SSID";

            // Detect manufacturer from SSID
            if (scanResult.SSID.contains("DJI") ||
                    scanResult.SSID.contains("MAVIC") ||
                    scanResult.SSID.contains("PHANTOM") ||
                    scanResult.SSID.contains("TELLO")) {
                manufacturer = "DJI";
            } else if (scanResult.SSID.contains("ANAFI") ||
                    scanResult.SSID.contains("PARROT") ||
                    scanResult.SSID.contains("BEBOP")) {
                manufacturer = "Parrot";
            } else if (scanResult.SSID.contains("SKYDIO")) {
                manufacturer = "Skydio";
            } else if (scanResult.SSID.contains("AUTEL")) {
                manufacturer = "Autel";
            } else if (scanResult.SSID.contains("YUNEEC")) {
                manufacturer = "Yuneec";
            }

            // Try to determine drone model from SSID
            if (manufacturer.equals("DJI")) {
                if (scanResult.SSID.contains("MAVIC")) {
                    droneModel = "DJI Mavic";
                    if (scanResult.SSID.contains("AIR")) droneModel += " Air";
                    else if (scanResult.SSID.contains("MINI")) droneModel += " Mini";
                    else if (scanResult.SSID.contains("PRO")) droneModel += " Pro";
                } else if (scanResult.SSID.contains("PHANTOM")) {
                    droneModel = "DJI Phantom";
                    if (scanResult.SSID.contains("3")) droneModel += " 3";
                    else if (scanResult.SSID.contains("4")) droneModel += " 4";
                } else if (scanResult.SSID.contains("TELLO")) {
                    droneModel = "DJI Tello";
                } else if (scanResult.SSID.contains("INSPIRE")) {
                    droneModel = "DJI Inspire";
                }
            }

            basicId.put("id_type", idType);
            basicId.put("id", scanResult.SSID);
            basicId.put("description", droneModel);
            basicId.put("ua_type", "Helicopter (or Multirotor)");
            basicId.put("manufacturer", manufacturer);

            messagesArray.put(basicIdObj);

            // Add Self-ID message
            JSONObject selfIdObj = new JSONObject();
            JSONObject selfId = new JSONObject();
            selfIdObj.put("Self-ID Message", selfId);
            selfId.put("text", droneModel);
            selfId.put("description_type", 0);
            selfId.put("MAC", scanResult.BSSID);
            selfId.put("RSSI", scanResult.level);
            messagesArray.put(selfIdObj);

            // Try to add capabilities data if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                JSONObject locObj = new JSONObject();
                JSONObject location = new JSONObject();
                locObj.put("Location/Vector Message", location);
                location.put("MAC", scanResult.BSSID);
                location.put("RSSI", scanResult.level);
                // We don't have actual location data yet, but this prepares
                // for potential additional vendor-specific data parsing in the future
                messagesArray.put(locObj);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error creating drone data: " + e.getMessage(), e);
        }

        return messagesArray;
    }
}