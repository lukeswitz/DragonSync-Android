package com.rootdown.dragonsync.network;

import android.Manifest;
import android.annotation.TargetApi;
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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.rootdown.dragonsync.utils.DroneDataParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WiFiScanner {
    private static final String TAG = "WiFiScanner";

    private static final long AGGRESSIVE_SCAN_INTERVAL = 100; // 100ms instead of 3000ms
    private static final long NORMAL_SCAN_INTERVAL = 500; // 500ms fallback

    private static final int CID_LENGTH = 3;
    private static final int[] ASD_STAN_CID = {0xFA, 0x0B, 0xBC}; // ASD-STAN owned identifier
    private static final int VENDOR_TYPE_VALUE = 0x0D; // OpenDroneID vendor type
    private static final int VENDOR_SPECIFIC_IE_ID = 221;
    private static final String OPENDRONEID_NAN_SERVICE_NAME = "org.opendroneid.remoteid";

    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long THROTTLE_BACKOFF_TIME = 30000; // 30 seconds instead of 120
    private final AtomicBoolean isThrottled = new AtomicBoolean(false);

    private final Context context;
    private final WifiManager wifiManager;
    private final WifiAwareManager wifiAwareManager;
    private final OnDroneDetectedListener listener;
    private final DroneDataParser dataParser;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Scanning state
    private boolean isBeaconScanning = false;
    private boolean isNaNScanning = false;
    private boolean isNaNSupported = false;
    private BroadcastReceiver wifiScanReceiver;
    private IntentFilter wifiScanFilter;

    private long currentScanInterval = AGGRESSIVE_SCAN_INTERVAL;
    private int consecutiveFailures = 0;
    private int successfulScans = 0;
    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (isBeaconScanning && !isThrottled.get()) {
                performWifiScan();
                handler.postDelayed(this, currentScanInterval);
            }
        }
    };

    // NaN components
    private WifiAwareSession wifiAwareSession;
    private SubscribeDiscoverySession subscribeSession;

    public interface OnDroneDetectedListener {
        void onDroneDetected(JSONArray droneData);
        void onError(String error);
    }

    public WiFiScanner(Context context, OnDroneDetectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.dataParser = new DroneDataParser();

        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Initialize WiFi Aware for NaN (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
                wifiAwareManager = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
                isNaNSupported = true;
                Log.d(TAG, "WiFi Aware (NaN) supported");
            } else {
                wifiAwareManager = null;
            }
        } else {
            wifiAwareManager = null;
        }

        setupWifiBeaconReceiver();
    }

    public boolean startScanning() {
        if (!hasRequiredPermissions()) {
            listener.onError("Missing required permissions for WiFi scanning");
            return false;
        }

        boolean success = false;

        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            Log.d(TAG, "Enabling WiFi for optimal scanning");
            wifiManager.setWifiEnabled(true);
        }

        // Start beacon scanning
        if (startBeaconScanning()) {
            success = true;
            Log.d(TAG, "WiFi beacon scanning started with aggressive timing");
        }

        // Start NaN scanning
        if (isNaNSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && startNaNScanning()) {
            success = true;
            Log.d(TAG, "WiFi NaN scanning started");
        }

        if (!success) {
            listener.onError("Failed to start WiFi scanning");
        }

        Log.i(TAG, "🔍 OPTIMIZED WIFI REMOTE ID SCAN STARTED (Beacon: " + isBeaconScanning + ", NaN: " + isNaNScanning + ")");
        return success;
    }

    private boolean startBeaconScanning() {
        if (wifiManager == null || isThrottled.get()) {
            return false;
        }

        if (isBeaconScanning) return true;

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing permissions for beacon scanning");
            return false;
        }

        try {
            // Register receiver with proper flags for different Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiScanReceiver, wifiScanFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(wifiScanReceiver, wifiScanFilter);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering WiFi scan receiver: " + e.getMessage());
            return false;
        }

        isBeaconScanning = true;
        handler.post(scanRunnable);
        return true;
    }

    private boolean performWifiScan() {
        if (wifiManager == null) return false;

        try {
            boolean started = wifiManager.startScan();
            if (started) {
                successfulScans++;
                consecutiveFailures = 0;

                if (successfulScans > 10 && currentScanInterval > AGGRESSIVE_SCAN_INTERVAL) {
                    currentScanInterval = AGGRESSIVE_SCAN_INTERVAL;
                    Log.d(TAG, "Switching to aggressive scan timing");
                }

                return true;
            } else {
                handleScanFailure();
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception during WiFi scan: " + e.getMessage());
            listener.onError("WiFi scan permission denied");
            return false;
        }
    }

    private void handleScanFailure() {
        consecutiveFailures++;

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            Log.w(TAG, "Scan throttling detected - implementing backoff strategy");
            isThrottled.set(true);
            currentScanInterval = NORMAL_SCAN_INTERVAL;

            handler.postDelayed(() -> {
                isThrottled.set(false);
                consecutiveFailures = 0;
                Log.d(TAG, "Throttling backoff complete, resuming scanning");
            }, THROTTLE_BACKOFF_TIME);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private boolean startNaNScanning() {
        if (!isNaNSupported || wifiAwareManager == null || isNaNScanning) {
            return false;
        }

        if (!wifiAwareManager.isAvailable()) {
            Log.w(TAG, "WiFi Aware currently unavailable");
            return false;
        }

        try {
            wifiAwareManager.attach(attachCallback, identityChangedListener, null);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting WiFi NaN: " + e.getMessage());
            return false;
        }
    }

    private void setupWifiBeaconReceiver() {
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);

                if (success && WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    processBeaconScanResults();
                } else {
                    handleScanFailure();
                }
            }
        };

        wifiScanFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    }

    private void processBeaconScanResults() {
        if (wifiManager == null) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            List<ScanResult> results = wifiManager.getScanResults();
            if (results == null) return;

            for (ScanResult scanResult : results) {
                if (scanResult.level < -90) continue; // Skip very weak signals
                processBeaconResult(scanResult);
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting scan results: " + e.getMessage());
        }
    }

    private void processBeaconResult(ScanResult scanResult) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ - use public API
                for (ScanResult.InformationElement element : scanResult.getInformationElements()) {
                    if (element != null && element.getId() == VENDOR_SPECIFIC_IE_ID) {
                        ByteBuffer buf = element.getBytes();
                        if (buf != null && buf.remaining() >= 4) {
                            processRemoteIdVendorIE(scanResult, buf);
                        }
                    }
                }
            } else {
                // Android 6-10 - use reflection for hidden API
                processInformationElementsReflection(scanResult);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing beacon result: " + e.getMessage());
        }
    }

    private void processInformationElementsReflection(ScanResult scanResult) {
        try {
            java.lang.reflect.Field field = ScanResult.class.getDeclaredField("informationElements");
            field.setAccessible(true);
            ScanResult.InformationElement[] elements = (ScanResult.InformationElement[]) field.get(scanResult);

            if (elements == null) return;

            for (ScanResult.InformationElement element : elements) {
                if (element == null) continue;

                java.lang.reflect.Field idField = element.getClass().getDeclaredField("id");
                idField.setAccessible(true);
                int id = (int) idField.get(element);

                if (id == VENDOR_SPECIFIC_IE_ID) {
                    java.lang.reflect.Field bytesField = element.getClass().getDeclaredField("bytes");
                    bytesField.setAccessible(true);
                    byte[] data = (byte[]) bytesField.get(element);

                    if (data != null && data.length >= 4) {
                        ByteBuffer buf = ByteBuffer.wrap(data).asReadOnlyBuffer();
                        processRemoteIdVendorIE(scanResult, buf);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Reflection access failed: " + e.getMessage());
        }
    }

    private void processRemoteIdVendorIE(ScanResult scanResult, ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() < (CID_LENGTH + 1)) return;

        buffer.rewind();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] cidBytes = new byte[CID_LENGTH];
        buffer.get(cidBytes, 0, CID_LENGTH);
        byte vendorType = buffer.get();

        // Quick check for ASD-STAN OpenDroneID header (0xFA 0x0B 0xBC 0x0D)
        if ((cidBytes[0] & 0xFF) != ASD_STAN_CID[0] ||
                (cidBytes[1] & 0xFF) != ASD_STAN_CID[1] ||
                (cidBytes[2] & 0xFF) != ASD_STAN_CID[2] ||
                (vendorType & 0xFF) != VENDOR_TYPE_VALUE) {
            return; // Not OpenDroneID data
        }

        // Extract Remote ID payload
        byte[] beaconData = new byte[buffer.remaining()];
        buffer.get(beaconData);

        Log.i(TAG, "🎯 FOUND OpenDroneID WiFi beacon: " + scanResult.BSSID +
                ", data length: " + beaconData.length +
                ", RSSI: " + scanResult.level + "dBm");

        try {
            JSONArray droneData = dataParser.parseWiFiBeaconData(beaconData, scanResult.BSSID, scanResult.level);
            if (droneData.length() > 0) {
                listener.onDroneDetected(droneData);
                Log.d(TAG, "✅ Successfully parsed OpenDroneID WiFi beacon");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing OpenDroneID data: " + e.getMessage());
        }
    }

    // WiFi NaN callbacks
    @TargetApi(Build.VERSION_CODES.O)
    private final AttachCallback attachCallback = new AttachCallback() {
        @Override
        public void onAttached(WifiAwareSession session) {
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
                        Log.i(TAG, "✅ WiFi NaN OpenDroneID subscription active");
                    }

                    @Override
                    public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
                        Log.i(TAG, "🎯 WiFi NaN OpenDroneID service discovered, data: " + serviceSpecificInfo.length + " bytes");

                        try {
                            JSONArray droneData = dataParser.parseWiFiBeaconData(serviceSpecificInfo,
                                    "NaN-" + peerHandle.hashCode(), 0);
                            if (droneData.length() > 0) {
                                listener.onDroneDetected(droneData);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing WiFi NaN data: " + e.getMessage());
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
            }
        }

        @Override
        public void onAttachFailed() {
            Log.e(TAG, "WiFi NaN attach failed");
        }
    };

    @TargetApi(Build.VERSION_CODES.O)
    private final IdentityChangedListener identityChangedListener = new IdentityChangedListener() {
        @Override
        public void onIdentityChanged(byte[] mac) {
            Log.d(TAG, "WiFi NaN identity changed");
        }
    };

    public void stopScanning() {
        isBeaconScanning = false;
        handler.removeCallbacks(scanRunnable);

        try {
            context.unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }

        stopNaNScanning();
        Log.d(TAG, "WiFi scanning stopped");
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
    }

    private boolean hasRequiredPermissions() {
        boolean hasFineLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasWifiState = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hasNearby = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNearby = ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }

        return hasFineLocation && hasWifiState && hasNearby;
    }

    // Status methods
    public boolean isScanning() { return isBeaconScanning || isNaNScanning; }
    public boolean isBeaconScanningActive() { return isBeaconScanning; }
    public boolean isNaNScanningActive() { return isNaNScanning; }
    public boolean isNaNSupported() { return isNaNSupported; }
    public boolean isCurrentlyThrottled() { return isThrottled.get(); }

    public void getScanStats() {
        Log.d(TAG, "WiFi Scan Stats - Success: " + successfulScans +
                ", Failures: " + consecutiveFailures +
                ", Interval: " + currentScanInterval + "ms" +
                ", Throttled: " + isThrottled.get() +
                ", Beacon: " + isBeaconScanning +
                ", NaN: " + isNaNScanning);
    }
}