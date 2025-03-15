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

import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.utils.DroneDataParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    // Message types (from ASTM F3411)
    private static final byte MESSAGE_TYPE_BASIC_ID = 0x00;
    private static final byte MESSAGE_TYPE_LOCATION = 0x01;
    private static final byte MESSAGE_TYPE_AUTH = 0x02;
    private static final byte MESSAGE_TYPE_SELF_ID = 0x03;
    private static final byte MESSAGE_TYPE_SYSTEM = 0x04;
    private static final byte MESSAGE_TYPE_OPERATOR_ID = 0x05;

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
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BLE scan: " + e.getMessage());
            return false;
        }
        return false;
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

        // Get drone-specific manufacturer data
        byte[] openDroneIdData = scanRecord.getManufacturerSpecificData(OPENDRONEID_MFG_ID);
        byte[] astmData = scanRecord.getManufacturerSpecificData(ASTM_MFG_ID);

        byte[] droneData = null;

        // Check manufacturer specific data
        if (openDroneIdData != null) {
            droneData = openDroneIdData;
        } else if (astmData != null) {
            droneData = astmData;
        } else {
            // Check if it's in the service data
            droneData = scanRecord.getServiceData(SERVICE_pUUID);
        }

        if (droneData != null) {
            // Parse ASTM F3411 format
            try {
                JSONArray messages = dataParser.parseBluetoothData(droneData, deviceAddress, rssi);
                if (messages.length() > 0) {
                    listener.onDroneDetected(messages);
                    Log.d(TAG, "Drone detected: " + deviceAddress + ", RSSI: " + rssi);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing drone data: " + e.getMessage());
            }
        }
    }
}