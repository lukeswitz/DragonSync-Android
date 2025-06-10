package com.rootdown.dragonsync.network;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.rootdown.dragonsync.utils.DroneDataParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class WiFiScanner {
    private static final String TAG = "WiFiScanner";
    private static final long SCAN_INTERVAL = 3000; // 3 seconds for WiFi beacon scanning
    private static final int MAX_CONSECUTIVE_FAILURES = 2;
    private static final int PERMISSIONS_REQUEST_CODE = 987;
    // OpenDroneID WiFi Constants
    private static final int CID_LENGTH = 3;
    private static final int[] DRI_CID = {0xFA, 0x0B, 0xBC}; // OpenDroneID OUI
    private static final int DRI_START_OFFSET = 4;
    private static final int VENDOR_TYPE_LENGTH = 1;
    private static final int VENDOR_TYPE_VALUE = 0x0D; // OpenDroneID vendor type
    private static final int VENDOR_SPECIFIC_IE_ID = 221; // Vendor specific information element ID
    private static final String OPENDRONEID_NAN_SERVICE_NAME = "org.opendroneid.remoteid";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanThrottled = false;

    private final Context context;
    private final WifiManager wifiManager;
    private final WifiAwareManager wifiAwareManager;
    private final OnDroneDetectedListener listener;
    private final DroneDataParser dataParser;

    // WiFi Beacon scanning
    private boolean isBeaconScanningEnabled = true;
    private boolean isBeaconScanning = false;
    private CountDownTimer beaconScanTimer;
    private BroadcastReceiver wifiScanReceiver;
    private int scanSuccess = 0;
    private int scanFailed = 0;

    // WiFi NaN scanning
    private boolean isNaNSupported = false;
    private boolean isNaNScanning = false;
    private WifiAwareSession wifiAwareSession;
    private SubscribeDiscoverySession subscribeSession;

    private IntentFilter wifiScanFilter;


    public interface OnDroneDetectedListener {
        void onDroneDetected(JSONArray droneData);
        void onError(String error);
    }

    public WiFiScanner(Context context, OnDroneDetectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.dataParser = new DroneDataParser();

        // Initialize WiFi Manager for beacon scanning
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "Could not get WifiManager");
            isBeaconScanningEnabled = false;
        }

        // Initialize WiFi Aware Manager for NaN scanning (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
                wifiAwareManager = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
                isNaNSupported = true;
                Log.d(TAG, "WiFi Aware (NaN) is supported on this device");
            } else {
                wifiAwareManager = null;
                Log.d(TAG, "WiFi Aware (NaN) is not supported on this device");
            }
        } else {
            wifiAwareManager = null;
            Log.d(TAG, "WiFi Aware requires Android 8.0+ (API 26)");
        }

        setupWifiBeaconReceiver();
    }

    public boolean startScanning() {
        boolean success = false;

        // Check permissions
        if (!hasRequiredPermissions()) {
            listener.onError("Missing required permissions for WiFi scanning");
            return false;
        }

        // Ensure WiFi is enabled
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            Log.d(TAG, "Enabling WiFi for scanning");
            wifiManager.setWifiEnabled(true);
        }

        // Start WiFi beacon scanning
        if (isBeaconScanningEnabled && startBeaconScanning()) {
            success = true;
            Log.d(TAG, "WiFi beacon scanning started");
        }

        // Start WiFi NaN scanning if supported
        if (isNaNSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && startNaNScanning()) {
            success = true;
            Log.d(TAG, "WiFi NaN scanning started");
        }

        if (!success) {
            listener.onError("Failed to start any WiFi scanning methods");
        }

        Log.i(TAG, "🔍 STARTING WIFI REMOTE ID SCAN (Beacon: " + isBeaconScanning + ", NaN: " + isNaNScanning + ")");
        return success;
    }

    private boolean startBeaconScanning() {
        if (!isBeaconScanningEnabled || wifiManager == null || isScanThrottled) {
            return false;
        }

        if (isBeaconScanning) return true;

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing permissions for beacon scanning");
            return false;
        }

        // Register receiver
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiScanReceiver, wifiScanFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(wifiScanReceiver, wifiScanFilter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering WiFi scan receiver: " + e.getMessage());
            return false;
        }

        startBeaconScanTimer();
        return performWifiScan();
    }

    private void handleScanThrottling() {
        scanFailed++;
        if (scanFailed >= MAX_CONSECUTIVE_FAILURES) {
            Log.w(TAG, "Scan throttling detected - pausing scans for 120 seconds");
            isScanThrottled = true;
            handler.postDelayed(() -> {
                isScanThrottled = false;
                scanFailed = 0;
                startBeaconScanning();
            }, 120000); // 2 minutes
        }
    }

    private boolean performWifiScan() {

        try {
            boolean started = wifiManager.startScan();
            if (started) {
                scanSuccess++;
                scanFailed = 0; // Reset failure counter on success
                isBeaconScanning = true;
            } else {
                handleScanThrottling();
            }
            return started;
        } catch (SecurityException e) {
            Log.e(TAG, "Fatal security exception: " + e.getMessage());
            listener.onError("Permanent scan permission failure");
            stopScanning();
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private boolean startNaNScanning() {
        if (!isNaNSupported || wifiAwareManager == null || isNaNScanning) {
            return false;
        }

        if (!wifiAwareManager.isAvailable()) {
            Log.w(TAG, "WiFi Aware is currently not available");
            return false;
        }

        try {
            wifiAwareManager.attach(attachCallback, identityChangedListener, null);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting WiFi NaN: " + e.getMessage());
            listener.onError("Permission denied for WiFi NaN scanning");
            return false;
        }
    }

    public void stopScanning() {
        stopBeaconScanning();
        stopNaNScanning();
        Log.d(TAG, "WiFi scanning stopped");
    }

    private void stopBeaconScanning() {
        if (beaconScanTimer != null) {
            beaconScanTimer.cancel();
            beaconScanTimer = null;
        }

        // Unregister receiver
        try {
            context.unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Receiver not registered");
        }

        isBeaconScanning = false;
        Log.d(TAG, "WiFi beacon scanning stopped");
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void stopNaNScanning() {
        if (subscribeSession != null) {
            subscribeSession.close();
            subscribeSession = null;
        }

        if (wifiAwareSession != null) {
            wifiAwareSession.close();
            wifiAwareSession = null;
        }

        isNaNScanning = false;
        Log.d(TAG, "WiFi NaN scanning stopped");
    }

    private void setupWifiBeaconReceiver() {
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                String action = intent.getAction();

                if (success && WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                    Log.d(TAG, "WiFi beacon scan results available");
                    processBeaconScanResults();
                } else {
                    Log.e(TAG, "WiFi beacon scan was not successful");
                    scanFailed++;
                    if (listener != null) {
                        listener.onError("WiFi beacon scan failed");
                    }
                }
            }
        };

        wifiScanFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    }

    private void startBeaconScanTimer() {
        if (beaconScanTimer != null) {
            beaconScanTimer.cancel();
        }

        beaconScanTimer = new CountDownTimer(Long.MAX_VALUE, SCAN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                if (!isScanThrottled) {
                    performWifiScan();
                }
            }
            public void onFinish() {}
        }.start();
    }

    private void processBeaconScanResults() {
        if (wifiManager == null) {
            return;
        }

        // Check for location permission before calling getScanResults()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission - can't get WiFi scan results");
            listener.onError("Location permission required for WiFi scanning");
            return;
        }

        try {
            List<ScanResult> results = wifiManager.getScanResults();
            if (results == null || results.isEmpty()) {
                Log.d(TAG, "No scan results (normal on modern Android)");
                return; // Don't treat empty results as errors
            }

            Log.d(TAG, "Processing " + (results != null ? results.size() : 0) + " WiFi beacon scan results");

            if (results == null || results.isEmpty()) {
                Log.d(TAG, "No WiFi beacon scan results found");
                return;
            }

            for (ScanResult scanResult : results) {
                try {
                    processBeaconResult(scanResult);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing beacon scan result: " + e.getMessage(), e);
                }
            }

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
            listener.onError("Permissions revoked during scan");
        }


    }

    private void processBeaconResult(ScanResult scanResult) {
        try {
            // Check for OpenDroneID information elements in WiFi beacons
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ - use public API
                for (ScanResult.InformationElement element : scanResult.getInformationElements()) {
                    if (element != null && element.getId() == VENDOR_SPECIFIC_IE_ID) {
                        ByteBuffer buf = element.getBytes();
                        if (buf != null) {
                            processRemoteIdVendorIE(scanResult, buf);
                        }
                    }
                }
            } else {
                // Android 6-10 - use reflection to access hidden field
                processInformationElementsReflection(scanResult);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing information elements: " + e.getMessage(), e);
        }
    }

    private void processInformationElementsReflection(ScanResult scanResult) {
        try {
            // Use reflection to access hidden informationElements field
            java.lang.reflect.Field field = ScanResult.class.getDeclaredField("informationElements");
            field.setAccessible(true);
            ScanResult.InformationElement[] elements = (ScanResult.InformationElement[]) field.get(scanResult);

            if (elements == null) return;

            for (ScanResult.InformationElement element : elements) {
                if (element == null) continue;

                // Get element ID using reflection
                java.lang.reflect.Field idField = element.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                int id = (int) idField.get(element);

                if (id == VENDOR_SPECIFIC_IE_ID) {
                    // Get element data using reflection
                    java.lang.reflect.Field bytesField = element.getClass().getDeclaredField("bytes");
                    bytesField.setAccessible(true);
                    byte[] data = (byte[]) bytesField.get(element);

                    if (data != null) {
                        ByteBuffer buf = ByteBuffer.wrap(data).asReadOnlyBuffer();
                        processRemoteIdVendorIE(scanResult, buf);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Reflection access to information elements failed: " + e.getMessage());
        }
    }

    private void processRemoteIdVendorIE(ScanResult scanResult, ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() < CID_LENGTH + VENDOR_TYPE_LENGTH) {
            return;
        }

        // Reset buffer position and set byte order
        buffer.rewind();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Read and verify OpenDroneID header
        byte[] cidBytes = new byte[CID_LENGTH];
        buffer.get(cidBytes, 0, CID_LENGTH);

        byte[] vendorType = new byte[VENDOR_TYPE_LENGTH];
        buffer.get(vendorType);

        // Check for OpenDroneID information element pattern (0xFA 0x0B 0xBC 0x0D)
        if ((cidBytes[0] & 0xFF) == DRI_CID[0] &&
                (cidBytes[1] & 0xFF) == DRI_CID[1] &&
                (cidBytes[2] & 0xFF) == DRI_CID[2] &&
                vendorType[0] == VENDOR_TYPE_VALUE) {

            // Extract the actual Remote ID data
            buffer.position(DRI_START_OFFSET);
            byte[] beaconData = new byte[buffer.remaining()];
            buffer.get(beaconData);

            Log.i(TAG, "Found OpenDroneID WiFi beacon data: " + scanResult.BSSID +
                    ", data length: " + beaconData.length +
                    ", RSSI: " + scanResult.level + "dBm");

            try {
                // Use the parseOpenDroneIDMessage method for proper OpenDroneID parsing
                JSONObject droneMessage = dataParser.parseOpenDroneIDMessage(beaconData, scanResult.BSSID, scanResult.level);

                if (droneMessage != null) {
                    // Convert single message to array format expected by the listener
                    JSONArray droneData = new JSONArray();
                    droneData.put(droneMessage);

                    listener.onDroneDetected(droneData);
                    Log.d(TAG, "Successfully parsed drone Remote ID from WiFi beacon: " + scanResult.BSSID);
                } else {
                    Log.w(TAG, "parseOpenDroneIDMessage returned null for: " + scanResult.BSSID);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing OpenDroneID message: " + e.getMessage(), e);

                // Fallback to the original parsing method
                try {
                    JSONArray droneData = dataParser.parseWiFiBeaconData(beaconData, scanResult.BSSID, scanResult.level);
                    if (droneData.length() > 0) {
                        listener.onDroneDetected(droneData);
                        Log.d(TAG, "Successfully parsed using fallback method: " + scanResult.BSSID);
                    } else {
                        Log.w(TAG, "Both parsing methods failed for: " + scanResult.BSSID);
                    }
                } catch (Exception fallbackException) {
                    Log.e(TAG, "Fallback parsing also failed: " + fallbackException.getMessage());
                }
            }
        }
    }

    // WiFi NaN (Neighbor Aware Networking) callbacks
    @TargetApi(Build.VERSION_CODES.O)
    private final AttachCallback attachCallback = new AttachCallback() {
        @Override
        public void onAttached(WifiAwareSession session) {
            if (!isNaNSupported) return;

            wifiAwareSession = session;
            SubscribeConfig config = new SubscribeConfig.Builder()
                    .setServiceName(OPENDRONEID_NAN_SERVICE_NAME)
                    .build();

            try {
                wifiAwareSession.subscribe(config, new DiscoverySessionCallback() {
                    @Override
                    public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
                        subscribeSession = session;
                        isNaNScanning = true;
                        Log.i(TAG, "WiFi NaN subscribe started for OpenDroneID service");
                    }

                    @Override
                    public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
                        Log.i(TAG, "WiFi NaN: OpenDroneID service discovered from peer: " + peerHandle.hashCode() +
                                ", data length: " + serviceSpecificInfo.length);

                        try {
                            // Try to parse as OpenDroneID message first
                            JSONObject droneMessage = dataParser.parseOpenDroneIDMessage(serviceSpecificInfo,
                                    "NaN-" + peerHandle.hashCode(), 0);

                            if (droneMessage != null) {
                                JSONArray droneData = new JSONArray();
                                droneData.put(droneMessage);
                                listener.onDroneDetected(droneData);
                                Log.d(TAG, "Successfully parsed drone Remote ID from WiFi NaN");
                            } else {
                                // Fallback to beacon data parsing
                                JSONArray droneData = dataParser.parseWiFiBeaconData(serviceSpecificInfo,
                                        "NaN-" + peerHandle.hashCode(), 0);
                                if (droneData.length() > 0) {
                                    listener.onDroneDetected(droneData);
                                    Log.d(TAG, "Successfully parsed drone Remote ID from WiFi NaN using fallback");
                                } else {
                                    Log.w(TAG, "Failed to parse WiFi NaN data with both methods");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing WiFi NaN data: " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onSessionTerminated() {
                        subscribeSession = null;
                        isNaNScanning = false;
                        Log.w(TAG, "WiFi NaN session terminated");
                    }
                }, null);

            } catch (SecurityException e) {
                Log.e(TAG, "Security exception subscribing to WiFi NaN: " + e.getMessage());
                listener.onError("Permission denied for WiFi NaN subscription");
            }
        }

        @Override
        public void onAttachFailed() {
            Log.e(TAG, "WiFi NaN attach failed");
            listener.onError("WiFi NaN attach failed");
        }
    };

    @TargetApi(Build.VERSION_CODES.O)
    private final IdentityChangedListener identityChangedListener = new IdentityChangedListener() {
        @Override
        public void onIdentityChanged(byte[] mac) {
            Log.d(TAG, "WiFi NaN identity changed, new MAC length: " + mac.length);
        }
    };

    private boolean hasRequiredPermissions() {
        boolean hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasWifiState = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hasNearby = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNearby = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }

        return hasFineLocation && hasWifiState && hasNearby;
    }

    // Helper function to build the list of missing permissions.
    private String[] getMissingPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_WIFI_STATE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }

        // Consider CHANGE_WIFI_STATE as optional and don't block for it

        return permissionsToRequest.toArray(new String[0]);
    }

    //To be called to start the scanning logic from the activity.
    public void startScanningWithPermissionCheck(){
        if (hasRequiredPermissions()) {
            // start scanning logic, or call other functions
            Log.i(TAG, "Starting wifi scanner");
            // startScanning() // your other class method
        } else{
            // handle failure
            Log.i(TAG, "Permissions missing to start wifi scanner");
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Status methods
    public boolean isScanning() {
        return isBeaconScanning || isNaNScanning;
    }

    public boolean isBeaconScanningActive() {
        return isBeaconScanning;
    }

    public boolean isNaNScanningActive() {
        return isNaNScanning;
    }

    public boolean isNaNSupported() {
        return isNaNSupported;
    }

    public void getScanStats() {
        Log.d(TAG, "WiFi Scan Stats - Success: " + scanSuccess + ", Failed: " + scanFailed +
                ", Beacon Active: " + isBeaconScanning + ", NaN Active: " + isNaNScanning);
    }
}