package com.rootdown.dragonsync.network;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.rootdown.dragonsync.utils.DroneDataParser;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";
    private static final long SCAN_PERIOD = 10000; // 10 seconds

    // ASTM F3411 OpenDroneID manufacturer IDs
    private static final int OPENDRONEID_MFG_ID = 0x4150;
    private static final int ASTM_MFG_ID = 0xFFFA;
    private static final UUID SERVICE_UUID = UUID.fromString("0000fffa-0000-1000-8000-00805f9b34fb");
    private static final ParcelUuid SERVICE_pUUID = new ParcelUuid(SERVICE_UUID);
    private static final byte[] OPEN_DRONE_ID_AD_CODE = new byte[]{(byte) 0x0D};

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OnDroneDetectedListener listener;
    private final DroneDataParser dataParser;

    public interface OnDroneDetectedListener {
        void onDroneDetected(JSONArray droneData);
        void onError(String error);
    }

    public BluetoothScanner(Context context, OnDroneDetectedListener listener) {
        this.context = context;
        this.listener = listener;
        this.dataParser = new DroneDataParser();

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }
    }

    public boolean startScanning() {
        if (bluetoothLeScanner == null) {
            listener.onError("Bluetooth LE scanner not available");
            return false;
        }

        if (isScanning) {
            return true; // Already scanning
        }

        // Define scan filters for drone manufacturer IDs
        List<ScanFilter> filters = new ArrayList<>();

        // Add a filter for the OpenDroneID service UUID
        ScanFilter serviceFilter = new ScanFilter.Builder()
                .setServiceUuid(SERVICE_pUUID)
                .build();

        // Add manufacturer data filters
        ScanFilter openDroneIdFilter = new ScanFilter.Builder()
                .setManufacturerData(OPENDRONEID_MFG_ID, null)
                .build();

        // Create a proper mask array instead of a single byte
        byte[] astmMask = new byte[]{(byte)0x0F};
        ScanFilter astmFilter = new ScanFilter.Builder()
                .setManufacturerData(ASTM_MFG_ID, OPEN_DRONE_ID_AD_CODE, astmMask)
                .build();

        filters.add(serviceFilter);
        filters.add(openDroneIdFilter);
        filters.add(astmFilter);

        // Set up scan settings for low latency
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // Start scan with timeout
        handler.postDelayed(() -> {
            if (isScanning) {
                pauseScanning();
                // Restart after a short break to avoid overheating
                handler.postDelayed(this::startScanning, 2000);
            }
        }, SCAN_PERIOD);

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "🔍 STARTING BLUETOOTH SCAN FOR DRONES");
                bluetoothLeScanner.startScan(filters, settings, scanCallback);
                isScanning = true;
                return true;
            } else {
                Log.e(TAG, "Missing BLUETOOTH_SCAN permission");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BLE scan: " + e.getMessage());
            return false;
        }
    }

    public void pauseScanning() {
        if (bluetoothLeScanner != null && isScanning &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                Log.d(TAG, "BLE scanning paused");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping BLE scan: " + e.getMessage());
            }
        }
    }

    public void stopScanning() {
        pauseScanning();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "BLE scanning stopped completely");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            processScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                processScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            String errorMessage = "BLE scan failed with code: " + errorCode;
            Log.e(TAG, errorMessage);
            listener.onError(errorMessage);
        }
    };

    private void processScanResult(ScanResult result) {
        ScanRecord scanRecord = result.getScanRecord();
        if (scanRecord == null) return;

        String deviceAddress = result.getDevice().getAddress();
        int rssi = result.getRssi();

        // Log basic information
        Log.d(TAG, "Bluetooth device found: " + deviceAddress + ", RSSI: " + rssi);

        // Try different methods to extract OpenDroneID data

        // Check manufacturer specific data for OpenDroneID
        byte[] openDroneIdData = scanRecord.getManufacturerSpecificData(OPENDRONEID_MFG_ID);
        byte[] astmData = scanRecord.getManufacturerSpecificData(ASTM_MFG_ID);

        byte[] droneData = null;
        String source = "";

        if (openDroneIdData != null) {
            droneData = openDroneIdData;
            source = "OpenDroneID";
        } else if (astmData != null) {
            droneData = astmData;
            source = "ASTM";
        } else {
            // Try to find it in service data
            droneData = scanRecord.getServiceData(SERVICE_pUUID);
            source = "Service";
        }

        if (droneData != null) {
            // We found OpenDroneID data, parse it
            try {
                Log.d(TAG, "Found drone data from " + source + ": " + deviceAddress);
                JSONArray messages = dataParser.parseBluetoothData(droneData, deviceAddress, rssi);
                if (messages.length() > 0) {
                    listener.onDroneDetected(messages);
                    Log.d(TAG, "Drone detected: " + deviceAddress + ", RSSI: " + rssi);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing drone data: " + e.getMessage(), e);
            }
        } else {
            // No explicit drone data found, but check device name as a fallback
            String deviceName = scanRecord.getDeviceName();
            if (deviceName != null && isDroneDeviceName(deviceName)) {
                // Create a basic drone entry based just on the name and address
                JSONArray basicDroneInfo = createBasicDroneInfo(deviceName, deviceAddress, rssi);
                if (basicDroneInfo.length() > 0) {
                    listener.onDroneDetected(basicDroneInfo);
                    Log.d(TAG, "Drone detected by name: " + deviceName + ", address: " + deviceAddress);
                }
            }
        }
    }

    private boolean isDroneDeviceName(String name) {
        if (name == null) return false;

        String upperName = name.toUpperCase();
        return upperName.contains("DJI") ||
                upperName.contains("DRONE") ||
                upperName.contains("MAVIC") ||
                upperName.contains("PHANTOM") ||
                upperName.contains("TELLO") ||
                upperName.contains("PARROT") ||
                upperName.contains("ANAFI") ||
                upperName.contains("SKYDIO") ||
                upperName.contains("AUTEL") ||
                upperName.contains("UAV");
    }

    private JSONArray createBasicDroneInfo(String deviceName, String deviceAddress, int rssi) {
        // Create a minimal drone information structure when we just have the device name
        JSONArray messagesArray = new JSONArray();

        try {
            org.json.JSONObject basicIdObj = new org.json.JSONObject();
            org.json.JSONObject basicId = new org.json.JSONObject();
            basicIdObj.put("Basic ID", basicId);

            basicId.put("MAC", deviceAddress);
            basicId.put("RSSI", rssi);
            basicId.put("id_type", "Bluetooth Device Name");
            basicId.put("id", deviceName);
            basicId.put("ua_type", "Helicopter (or Multirotor)");

            // Try to determine manufacturer
            String manufacturer = "Unknown";
            if (deviceName.toUpperCase().contains("DJI")) {
                manufacturer = "DJI";
            } else if (deviceName.toUpperCase().contains("PARROT")) {
                manufacturer = "Parrot";
            } else if (deviceName.toUpperCase().contains("SKYDIO")) {
                manufacturer = "Skydio";
            }

            basicId.put("manufacturer", manufacturer);
            messagesArray.put(basicIdObj);

            // Add a basic Self-ID message
            org.json.JSONObject selfIdObj = new org.json.JSONObject();
            org.json.JSONObject selfId = new org.json.JSONObject();
            selfIdObj.put("Self-ID Message", selfId);
            selfId.put("text", deviceName);
            selfId.put("description_type", 0);
            selfId.put("MAC", deviceAddress);
            selfId.put("RSSI", rssi);
            messagesArray.put(selfIdObj);

        } catch (Exception e) {
            Log.e(TAG, "Error creating basic drone info: " + e.getMessage());
        }

        return messagesArray;
    }
}