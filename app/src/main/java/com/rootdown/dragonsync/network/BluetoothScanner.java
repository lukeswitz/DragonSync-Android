package com.rootdown.dragonsync.network;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";
    private static final long SCAN_PERIOD = 10000; // 10 seconds

    // ASTM F3411 OpenDroneID manufacturer IDs
    private static final int OPENDRONEID_MFG_ID = 0x4150;
    private static final int ASTM_MFG_ID = 0xFFFA;

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

    public interface OnDroneDetectedListener {
        void onDroneDetected(JSONArray droneData);
        void onError(String error);
    }

    public BluetoothScanner(Context context, OnDroneDetectedListener listener) {
        this.context = context;
        this.listener = listener;

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
        ScanFilter openDroneIdFilter = new ScanFilter.Builder()
                .setManufacturerData(OPENDRONEID_MFG_ID, null)
                .build();
        ScanFilter astmFilter = new ScanFilter.Builder()
                .setManufacturerData(ASTM_MFG_ID, null)
                .build();
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
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            isScanning = true;
            Log.d(TAG, "BLE scanning started");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BLE scan: " + e.getMessage());
            listener.onError("Failed to start BLE scan: " + e.getMessage());
            return false;
        }
    }

    public void pauseScanning() {
        if (bluetoothLeScanner != null && isScanning) {
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
            // Process scan result
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

        byte[] droneData = (openDroneIdData != null) ? openDroneIdData : astmData;

        if (droneData != null) {
            // Parse ASTM F3411 format
            try {
                JSONArray messages = parseOpenDroneID(droneData, deviceAddress, rssi);
                if (messages.length() > 0) {
                    listener.onDroneDetected(messages);
                    Log.d(TAG, "Drone detected: " + deviceAddress + ", RSSI: " + rssi);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing drone data: " + e.getMessage());
            }
        }
    }

    private JSONArray parseOpenDroneID(byte[] data, String deviceAddress, int rssi) throws JSONException {
        JSONArray messagesArray = new JSONArray();

        // Minimum header size is 1 byte (message type)
        if (data.length < 1) return messagesArray;

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Read message type
        byte messageType = buffer.get();

        // Create Basic ID message (always included)
        JSONObject basicIdObj = new JSONObject();
        JSONObject basicId = new JSONObject();
        basicIdObj.put("Basic ID", basicId);

        // Add MAC address and RSSI to basic ID
        basicId.put("protocol_version", "F3411.22");
        basicId.put("MAC", deviceAddress);
        basicId.put("RSSI", rssi);

        switch (messageType) {
            case MESSAGE_TYPE_BASIC_ID:
                if (data.length >= 20) { // Basic ID message size
                    parseBasicIDMessage(buffer, basicId);
                }
                break;

            case MESSAGE_TYPE_LOCATION:
                JSONObject locationObj = new JSONObject();
                JSONObject locationMessage = new JSONObject();
                locationObj.put("Location/Vector Message", locationMessage);

                parseLocationMessage(buffer, locationMessage);
                messagesArray.put(locationObj);
                break;

            case MESSAGE_TYPE_SELF_ID:
                JSONObject selfIdObj = new JSONObject();
                JSONObject selfIdMessage = new JSONObject();
                selfIdObj.put("Self-ID Message", selfIdMessage);

                parseSelfIDMessage(buffer, selfIdMessage);
                messagesArray.put(selfIdObj);
                break;

            case MESSAGE_TYPE_SYSTEM:
                JSONObject systemObj = new JSONObject();
                JSONObject systemMessage = new JSONObject();
                systemObj.put("System Message", systemMessage);

                parseSystemMessage(buffer, systemMessage);
                messagesArray.put(systemObj);
                break;

            case MESSAGE_TYPE_OPERATOR_ID:
                JSONObject operatorObj = new JSONObject();
                JSONObject operatorMessage = new JSONObject();
                operatorObj.put("Operator ID Message", operatorMessage);

                parseOperatorIDMessage(buffer, operatorMessage);
                messagesArray.put(operatorObj);
                break;
        }

        // Always add basic ID
        messagesArray.put(basicIdObj);

        return messagesArray;
    }

    private void parseBasicIDMessage(ByteBuffer buffer, JSONObject basicId) throws JSONException {
        // Skip first byte (already read message type)

        byte idType = buffer.get();
        byte uaType = buffer.get();

        // Read 20-byte UAS ID field
        byte[] uasIdBytes = new byte[20];
        buffer.get(uasIdBytes);
        String uasId = bytesToHex(uasIdBytes).trim().replace("\0", "");

        // Map ID type
        String idTypeStr = "Unknown";
        switch (idType) {
            case 0:
                idTypeStr = "None";
                break;
            case 1:
                idTypeStr = "Serial Number (ANSI/CTA-2063-A)";
                break;
            case 2:
                idTypeStr = "CAA Assigned Registration ID";
                break;
            case 3:
                idTypeStr = "UTM (USS) Assigned ID";
                break;
            // Add more cases as needed
        }

        // Map UA type
        String uaTypeStr = mapUAType(uaType);

        basicId.put("id_type", idTypeStr);
        basicId.put("ua_type", uaTypeStr);
        basicId.put("id", uasId);
    }

    private void parseLocationMessage(ByteBuffer buffer, JSONObject locationMessage) throws JSONException {
        // Implementation depends on specific format
        // This is a simplified example - actual parsing would be more complex
        locationMessage.put("protocol_version", "F3411.22");

        // Skip status byte
        buffer.get();

        // Height and vertical reference
        byte heightRef = buffer.get();
        String heightType = (heightRef == 0) ? "Above Takeoff" : "Above Ground";
        locationMessage.put("height_type", heightType);

        // Direction segment (E/W)
        byte directionSegment = buffer.get();
        String ewDir = (directionSegment == 0) ? "East" : "West";
        locationMessage.put("ew_dir_segment", ewDir);

        // Speed multiplier (0.25 or 0.75)
        byte speedMult = buffer.get();
        String speedMultiplier = (speedMult == 0) ? "0.25" : "0.75";
        locationMessage.put("speed_multiplier", speedMultiplier);

        // Read basic location info
        locationMessage.put("latitude", buffer.getInt() / 10000000.0);
        locationMessage.put("longitude", buffer.getInt() / 10000000.0);

        // Process altitude
        int altitudeRaw = buffer.getShort() & 0xFFFF;
        double altitude = altitudeRaw * 0.5;
        locationMessage.put("geodetic_altitude", altitude);

        // Process height
        int heightRaw = buffer.getShort() & 0xFFFF;
        double height = heightRaw * 0.5;
        locationMessage.put("height_agl", height);

        // Process horizontal and vertical speed
        short horizontalSpeedRaw = buffer.getShort();
        double horizontalSpeed = horizontalSpeedRaw * 0.25;
        locationMessage.put("speed", horizontalSpeed + " m/s");

        short verticalSpeedRaw = buffer.getShort();
        double verticalSpeed = verticalSpeedRaw * 0.25;
        locationMessage.put("vert_speed", verticalSpeed + " m/s");
    }

    private void parseSelfIDMessage(ByteBuffer buffer, JSONObject selfIdMessage) throws JSONException {
        // Skip status byte
        buffer.get();

        // Read description type
        byte descriptionType = buffer.get();
        selfIdMessage.put("description_type", descriptionType);

        // Read description text (up to 23 bytes)
        byte[] descBytes = new byte[23];
        buffer.get(descBytes);
        String description = new String(descBytes).trim();

        selfIdMessage.put("protocol_version", "F3411.22");
        selfIdMessage.put("text", description);
    }

    private void parseSystemMessage(ByteBuffer buffer, JSONObject systemMessage) throws JSONException {
        // System message format is complex
        // This is a simplified implementation
        systemMessage.put("protocol_version", "F3411.22");

        // Skip flags and operational status
        buffer.get();
        byte opStatus = buffer.get();
        String opStatusStr = (opStatus == 0) ? "Undeclared" :
                (opStatus == 1) ? "Ground" :
                        (opStatus == 2) ? "Airborne" : "Unknown";
        systemMessage.put("op_status", opStatusStr);

        // Skip various fields
        buffer.position(buffer.position() + 4);

        // Read operator location
        int operatorLatRaw = buffer.getInt();
        double operatorLat = operatorLatRaw / 10000000.0;
        systemMessage.put("operator_lat", operatorLat);

        int operatorLonRaw = buffer.getInt();
        double operatorLon = operatorLonRaw / 10000000.0;
        systemMessage.put("operator_lon", operatorLon);

        // Area count (for geo-awareness)
        byte areaCount = buffer.get();
        systemMessage.put("area_count", areaCount);

        // Area radius
        short radiusRaw = buffer.getShort();
        systemMessage.put("area_radius", radiusRaw);

        // Area ceiling and floor
        short ceilingRaw = buffer.getShort();
        systemMessage.put("area_ceiling", ceilingRaw * 0.5);

        short floorRaw = buffer.getShort();
        systemMessage.put("area_floor", floorRaw * 0.5);
    }

    private void parseOperatorIDMessage(ByteBuffer buffer, JSONObject operatorMessage) throws JSONException {
        operatorMessage.put("protocol_version", "F3411.22");

        // Skip operator ID type
        byte operatorIdType = buffer.get();

        // Map operator ID type
        String operatorIdTypeStr = "Unknown";
        switch (operatorIdType) {
            case 0:
                operatorIdTypeStr = "Operator ID";
                break;
            // Add more cases as needed
        }

        operatorMessage.put("operator_id_type", operatorIdTypeStr);

        // Read operator ID (20 bytes)
        byte[] operatorIdBytes = new byte[20];
        buffer.get(operatorIdBytes);
        String operatorId = new String(operatorIdBytes).trim();
        operatorMessage.put("operator_id", operatorId);
    }

    private String mapUAType(byte uaType) {
        switch (uaType) {
            case 0: return "None";
            case 1: return "Aeroplane";
            case 2: return "Helicopter (or Multirotor)";
            case 3: return "Gyroplane";
            case 4: return "Hybrid Lift";
            case 5: return "Ornithopter";
            case 6: return "Glider";
            case 7: return "Kite";
            case 8: return "Free Balloon";
            case 9: return "Captive Balloon";
            case 10: return "Airship";
            case 11: return "Free Fall/Parachute";
            case 12: return "Rocket";
            case 13: return "Tethered Powered Aircraft";
            case 14: return "Ground Obstacle";
            case 15: return "Other";
            default: return "Unknown";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if (b == 0) break; // Stop at null terminator
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}