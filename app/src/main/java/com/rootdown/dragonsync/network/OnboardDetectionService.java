package com.rootdown.dragonsync.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;
import com.rootdown.dragonsync.utils.DeviceLocationManager;
import com.rootdown.dragonsync.utils.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OnboardDetectionService extends Service {
    private static final String TAG = "OnboardDetectionService";
    private static final String CHANNEL_ID = "DragonSyncOnboardDetection";
    private static final int NOTIFICATION_ID = 2;

    private BluetoothScanner bluetoothScanner;
    private WiFiScanner wifiScanner;
    private Settings settings;
    private boolean isRunning = false;
    private XMLParser xmlParser;
    private DeviceLocationManager locationManager;
    private Location lastDeviceLocation;
    private Map<String, Long> lastDetectionTime = new HashMap<>(); // For filtering duplicate detections
    private Map<String, String> knownDroneIds = new HashMap<>();
    private Map<String, String> macToDroneIdMap = new HashMap<>();

    private Map<String, String> persistentMacToDroneId = new HashMap<>();
    private Map<String, Long> macLastSeen = new HashMap<>();
    private static final long MAC_MAPPING_TIMEOUT = 30000; // 30 seconds

    @Override
    public void onCreate() {
        super.onCreate();
        settings = Settings.getInstance(this);
        xmlParser = new XMLParser();
        createNotificationChannel();

        // Initialize location manager
        locationManager = DeviceLocationManager.getInstance(this);
        locationManager.addListener(location -> {
            lastDeviceLocation = location;
            Log.d(TAG, "Device location updated: " + location.getLatitude() + ", " + location.getLongitude());
        });

        // Initialize BLE scanner if permissions are granted
        if (hasBluetoothPermissions()) {
            bluetoothScanner = new BluetoothScanner(this, new BluetoothScanner.OnDroneDetectedListener() {
                @Override
                public void onDroneDetected(JSONArray droneData) {
                    processDroneData(droneData, "BLE");
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "BLE Scanner error: " + error);
                }
            });
        } else {
            Log.e(TAG, "Bluetooth permissions not granted");
        }

        // Initialize WiFi scanner if permissions are granted
        if (hasWifiPermissions()) {
            wifiScanner = new WiFiScanner(this, new WiFiScanner.OnDroneDetectedListener() {
                @Override
                public void onDroneDetected(JSONArray droneData) {
                    processDroneData(droneData, "WiFi");
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "WiFi Scanner error: " + error);
                }
            });
        } else {
            Log.e(TAG, "WiFi permissions not granted");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Onboard detection service started");

        if (!isRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, createNotification());
            }

            startDetection();
            isRunning = true;
        }

        return START_STICKY;
    }

    private void startDetection() {
        // Update settings to reflect that we're listening
        settings.setListening(true);

        // Start location tracking
        locationManager.startLocationUpdates();

        // Start BLE scanning if available
        if (bluetoothScanner != null) {
            boolean bleStarted = bluetoothScanner.startScanning();
            Log.d(TAG, "BLE scanning " + (bleStarted ? "started" : "failed to start"));
        }

        // Start WiFi scanning if available
        if (wifiScanner != null) {
            boolean wifiStarted = wifiScanner.startScanning();
            Log.d(TAG, "WiFi scanning " + (wifiStarted ? "started" : "failed to start"));
        }
    }

    private void processDroneData(JSONArray droneData, String source) {
        if (droneData == null || droneData.length() == 0) {
            return;
        }

        try {
            Log.d(TAG, "Processing " + droneData.length() + " drone message(s) from source: " + source);

            // Clean up old MAC mappings
            cleanupOldMacMappings();

            // Step 1: Collect drone IDs from Basic ID messages and update persistent mapping
            Map<String, String> sessionMacToDroneId = new HashMap<>();
            String primaryDroneId = null;

            for (int i = 0; i < droneData.length(); i++) {
                JSONObject msgObj = droneData.getJSONObject(i);

                if (msgObj.has("Basic ID")) {
                    JSONObject basicIdData = msgObj.getJSONObject("Basic ID");
                    if (basicIdData.has("id") && basicIdData.has("MAC")) {
                        String droneId = basicIdData.getString("id");
                        String mac = basicIdData.getString("MAC");

                        if (droneId.matches("^[A-Z0-9]+$") && !droneId.matches("^0+$") && droneId.length() >= 6) {
                            primaryDroneId = droneId;
                            sessionMacToDroneId.put(mac, droneId);

                            // Update persistent mapping
                            persistentMacToDroneId.put(mac, droneId);
                            macLastSeen.put(mac, System.currentTimeMillis());

                            Log.d(TAG, "Found valid drone ID: " + droneId + " for MAC: " + mac);
                        }
                    }
                }
            }

            // Step 2: Look for drone ID in Self-ID messages as fallback
            if (primaryDroneId == null) {
                for (int i = 0; i < droneData.length(); i++) {
                    JSONObject msgObj = droneData.getJSONObject(i);
                    if (msgObj.has("Self-ID Message")) {
                        JSONObject selfIdData = msgObj.getJSONObject("Self-ID Message");
                        if (selfIdData.has("text") && selfIdData.has("MAC")) {
                            String selfIdText = selfIdData.getString("text");
                            String mac = selfIdData.getString("MAC");

                            if (selfIdText.length() >= 10 && selfIdText.matches(".*[A-Z0-9]{10,}.*")) {
                                String extractedId = selfIdText.replaceAll("^\\d+", "");
                                if (extractedId.length() >= 6 && extractedId.matches("^[A-Z0-9]+$")) {
                                    primaryDroneId = extractedId;
                                    sessionMacToDroneId.put(mac, primaryDroneId);

                                    // Update persistent mapping
                                    persistentMacToDroneId.put(mac, primaryDroneId);
                                    macLastSeen.put(mac, System.currentTimeMillis());

                                    Log.d(TAG, "Extracted drone ID from Self-ID: " + primaryDroneId + " for MAC: " + mac);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: Process each message with consistent drone ID
            for (int i = 0; i < droneData.length(); i++) {
                JSONObject msgObj = droneData.getJSONObject(i);
                String messageType = null;
                JSONObject messageData = null;

                Iterator<String> keys = msgObj.keys();
                if (keys.hasNext()) {
                    messageType = keys.next();
                    messageData = msgObj.getJSONObject(messageType);
                }

                if (messageType == null || messageData == null) {
                    continue;
                }

                // Skip unknown message types
                if (!isKnownMessageType(messageType)) {
                    Log.d(TAG, "Skipping unknown message type: " + messageType);
                    continue;
                }

                // Apply drone ID from session or persistent mapping
                if (messageData.has("MAC")) {
                    String mac = messageData.getString("MAC");
                    String droneId = null;

                    // Try session mapping first, then persistent mapping
                    if (sessionMacToDroneId.containsKey(mac)) {
                        droneId = sessionMacToDroneId.get(mac);
                    } else if (persistentMacToDroneId.containsKey(mac)) {
                        droneId = persistentMacToDroneId.get(mac);
                        // Update last seen time
                        macLastSeen.put(mac, System.currentTimeMillis());
                    }

                    if (droneId != null) {
                        messageData.put("OVERRIDE_UID", droneId);
                        Log.d(TAG, "Applied drone ID " + droneId + " to " + messageType + " from MAC " + mac);
                    }
                }

                Log.d(TAG, "Processing " + messageType + " with data: " + messageData.toString(2));
                CoTMessage message = convertToCoTMessage(messageType, messageData, source);

                if (message != null && message.getUid() != null) {
                    processMessage(message, source, messageData);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing drone data: " + e.getMessage(), e);
        }
    }

    private void cleanupOldMacMappings() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = macLastSeen.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > MAC_MAPPING_TIMEOUT) {
                String mac = entry.getKey();
                persistentMacToDroneId.remove(mac);
                iterator.remove();
                Log.d(TAG, "Cleaned up old MAC mapping: " + mac);
            }
        }
    }

    private boolean isKnownMessageType(String messageType) {
        return messageType.equals("Basic ID") ||
                messageType.equals("Location/Vector Message") ||
                messageType.equals("Self-ID Message") ||
                messageType.equals("System Message") ||
                messageType.equals("Operator ID Message") ||
                messageType.equals("Authentication Message") ||
                messageType.equals("Auth Message");
    }


    private void processMessage(CoTMessage message, String source, JSONObject messageData) {
        // Skip messages without RSSI or with NaN MAC addresses
        if (message.getRssi() == null || message.getRssi() == 0) {
            Log.d(TAG, "Skipping message without valid RSSI: " + message.getUid());
            return;
        }

        // Skip NaN messages (WiFi NaN without proper signal strength)
        if (message.getUid() != null && message.getUid().contains("NaN-")) {
            Log.d(TAG, "Skipping NaN message: " + message.getUid());
            return;
        }

        // Rest of your existing processMessage code stays the same...
        String uniqueKey = message.getUid();
        if (uniqueKey == null || uniqueKey.isEmpty()) {
            Log.d(TAG, "Skipping message without valid UID");
            return;
        }

        long currentTime = System.currentTimeMillis();
        Long lastDetection = lastDetectionTime.get(uniqueKey);
        boolean shouldProcess = lastDetection == null || (currentTime - lastDetection) >= 2000;

        if (shouldProcess) {
            // Update last detection time
            lastDetectionTime.put(uniqueKey, currentTime);

            // Only estimate location if drone doesn't provide its own coordinates
            if (message.getCoordinate() == null &&
                    lastDeviceLocation != null &&
                    message.getRssi() != null &&
                    settings.isLocationEstimationEnabled()) {

                estimateDroneLocation(message);
            }

            // Set the device (operator) location for display purposes
            if (lastDeviceLocation != null) {
                message.setPilotLat(String.valueOf(lastDeviceLocation.getLatitude()));
                message.setPilotLon(String.valueOf(lastDeviceLocation.getLongitude()));
            }

            // Calculate distance between user and drone if both coordinates are available
            if (lastDeviceLocation != null && message.getCoordinate() != null) {
                float distanceInMeters = lastDeviceLocation.distanceTo(message.getCoordinate());

                // Store the calculated distance in the message
                if (message.getRawMessage() == null) {
                    message.setRawMessage(new HashMap<>());
                }
                message.getRawMessage().put("calculated_distance", distanceInMeters);

                Log.d(TAG, "  Distance: " + distanceInMeters + "m");
            }

            // Broadcast the enhanced telemetry message
            Intent telemetryIntent = new Intent("com.rootdown.dragonsync.TELEMETRY");
            telemetryIntent.setPackage(getPackageName());
            telemetryIntent.putExtra("parsed_message", message);
            telemetryIntent.putExtra("raw_message", "Onboard detection: " + source);
            sendBroadcast(telemetryIntent);

            Log.d(TAG, "Broadcast telemetry message for: " + message.getUid());
        } else {
            Log.d(TAG, "Throttling duplicate detection for: " + uniqueKey);
        }
    }

    private CoTMessage convertToCoTMessage(String messageType, JSONObject messageData, String source) {
        CoTMessage message = new CoTMessage();
        Log.d(TAG, "Converting message type: " + messageType + " with data: " + messageData.toString());

        try {
            // Set common transport fields
            if (messageData.has("MAC")) {
                message.setMac(messageData.getString("MAC"));
            }
            if (messageData.has("RSSI")) {
                message.setRssi(messageData.getInt("RSSI"));
            }
            message.setType(source + "_ONBOARD");

            // Use override UID if provided, otherwise fall back to message-specific logic
            String overrideUid = messageData.optString("OVERRIDE_UID", null);

            switch (messageType) {
                case "Basic ID":
                    convertBasicIdMessage(message, messageData, overrideUid);
                    break;

                case "Location/Vector Message":
                    convertLocationMessage(message, messageData, overrideUid);
                    break;

                case "Self-ID Message":
                    convertSelfIdMessage(message, messageData, overrideUid);
                    break;

                case "System Message":
                    convertSystemMessage(message, messageData, overrideUid);
                    break;

                case "Operator ID Message":
                    convertOperatorIdMessage(message, messageData, overrideUid);
                    break;

                case "Authentication Message":
                case "Auth Message":
                    convertAuthMessage(message, messageData, overrideUid);
                    break;

                default:
                    Log.w(TAG, "Unknown message type: " + messageType);
                    return null;
            }

            // Validate coordinates if present
            if (message.getLat() != null && message.getLon() != null) {
                if (!isValidCoordinate(message.getLat(), message.getLon())) {
                    Log.w(TAG, "Invalid coordinates detected, clearing: " + message.getLat() + ", " + message.getLon());
                    message.setLat(null);
                    message.setLon(null);
                }
            }

            // Store raw message data
            Map<String, Object> rawData = new HashMap<>();
            Iterator<String> keys = messageData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                rawData.put(key, messageData.get(key));
            }
            message.setRawMessage(rawData);

            return message;

        } catch (JSONException e) {
            Log.e(TAG, "Error converting drone data: " + e.getMessage(), e);
            return null;
        }
    }

    private boolean isValidCoordinate(String latStr, String lonStr) {
        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);

            // Check if coordinates are within valid Earth bounds
            return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0 &&
                    !(lat == 0.0 && lon == 0.0); // Reject null island
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void convertBasicIdMessage(CoTMessage message, JSONObject messageData, String overrideUid) throws JSONException {
        if (overrideUid != null) {
            message.setUid(overrideUid);
        } else if (messageData.has("id")) {
            String idValue = messageData.getString("id");
            if (idValue.matches("^[A-Z0-9]+$") && !idValue.matches("^0+$") && idValue.length() >= 6) {
                message.setUid(idValue);
            } else {
                message.setUid(messageData.optString("MAC", "UNKNOWN_BASIC"));
            }
        } else {
            message.setUid(messageData.optString("MAC", "UNKNOWN_BASIC"));
        }

        if (messageData.has("ua_type")) {
            String uaTypeStr = messageData.getString("ua_type");
            DroneSignature.IdInfo.UAType uaType = mapUAType(uaTypeStr);
            message.setUaType(uaType);
        }

        if (messageData.has("id_type")) {
            message.setIdType(messageData.getString("id_type"));
        }
        if (messageData.has("description")) {
            message.setDescription(messageData.getString("description"));
        }
        if (messageData.has("manufacturer")) {
            message.setManufacturer(messageData.getString("manufacturer"));
        }
    }

    private void convertLocationMessage(CoTMessage message, JSONObject messageData, String overrideUid) throws JSONException {
        // Use override UID first, then fallback to MAC
        if (overrideUid != null) {
            message.setUid(overrideUid);
        } else {
            message.setUid(messageData.optString("MAC", "UNKNOWN_LOCATION"));
        }

        if (messageData.has("latitude") && messageData.has("longitude")) {
            double lat = messageData.optDouble("latitude", 0);
            double lon = messageData.optDouble("longitude", 0);

            if (lat != 0 || lon != 0) {
                message.setLat(String.valueOf(lat));
                message.setLon(String.valueOf(lon));
            }
        }

        // Parse other location fields with unit stripping
        parseLocationField(message, messageData, "speed", message::setSpeed);
        parseLocationField(message, messageData, "vert_speed", message::setVspeed);
        parseLocationField(message, messageData, "geodetic_altitude", message::setAlt);
        parseLocationField(message, messageData, "height_agl", message::setHeight);

        if (messageData.has("direction")) {
            message.setDirection(String.valueOf(messageData.get("direction")));
        }

        message.setTimestamp(String.valueOf(System.currentTimeMillis()));
    }

    private void convertSelfIdMessage(CoTMessage message, JSONObject messageData, String overrideUid) throws JSONException {
        if (overrideUid != null) {
            message.setUid(overrideUid);
        } else if (messageData.has("text")) {
            String selfIdText = messageData.getString("text");

            // Extract drone ID from Self-ID text if it looks like a valid drone ID
            if (selfIdText.length() >= 10 && selfIdText.matches(".*[A-Z0-9]{10,}.*")) {
                // Remove any leading digits and extract the alphanumeric part
                String extractedId = selfIdText.replaceAll("^\\d+", "").trim();
                if (extractedId.length() >= 6 && extractedId.matches("^[A-Z0-9]+$")) {
                    message.setUid(extractedId);
                    Log.d(TAG, "Extracted drone ID from Self-ID text: " + extractedId);
                } else {
                    message.setUid(messageData.optString("MAC", "UNKNOWN_SELFID"));
                }
            } else {
                message.setUid(messageData.optString("MAC", "UNKNOWN_SELFID"));
            }
        } else {
            message.setUid(messageData.optString("MAC", "UNKNOWN_SELFID"));
        }

        if (messageData.has("text")) {
            String selfIdText = messageData.getString("text");
            message.setSelfIDText(selfIdText);

            if (message.getDescription() == null || message.getDescription().isEmpty()) {
                message.setDescription(selfIdText);
            }
        }

        if (messageData.has("description_type")) {
            message.setSelfIdType(String.valueOf(messageData.get("description_type")));
        }
    }

    private void convertSystemMessage(CoTMessage message, JSONObject messageData, String overrideUid) throws JSONException {
        if (overrideUid != null) {
            message.setUid(overrideUid);
        } else {
            message.setUid(messageData.optString("MAC", "UNKNOWN_SYSTEM"));
        }

        if (messageData.has("operator_lat") && messageData.has("operator_lon")) {
            message.setPilotLat(String.valueOf(messageData.getDouble("operator_lat")));
            message.setPilotLon(String.valueOf(messageData.getDouble("operator_lon")));
        }

        if (messageData.has("operator_altitude_geo")) {
            parseLocationField(message, messageData, "operator_altitude_geo",
                    value -> {
                        if (message.getRawMessage() == null) {
                            message.setRawMessage(new HashMap<>());
                        }
                        message.getRawMessage().put("operator_altitude_geo", value);
                    });
        }
    }

    private void convertOperatorIdMessage(CoTMessage message, JSONObject messageData, String overrideUid) throws JSONException {
        if (overrideUid != null) {
            message.setUid(overrideUid);
        } else if (messageData.has("operator_id")) {
            String operatorId = messageData.getString("operator_id");
            message.setOperatorId(operatorId);
            message.setUid(operatorId);
        } else {
            message.setUid(messageData.optString("MAC", "UNKNOWN_OPERATOR"));
        }

        if (messageData.has("operator_id_type")) {
            message.setOperatorIdType(String.valueOf(messageData.get("operator_id_type")));
        }
    }

    private void convertAuthMessage(CoTMessage message, JSONObject messageData, String overrideUid) throws JSONException {
        // Use override UID if available, otherwise fall back to MAC
        if (overrideUid != null) {
            message.setUid(overrideUid);
        } else {
            message.setUid(messageData.optString("MAC", "UNKNOWN_AUTH"));
            Log.d(TAG, "Auth message using MAC fallback: " + message.getUid());
        }

        if (messageData.has("auth_type")) {
            message.setAuthType(String.valueOf(messageData.get("auth_type")));
        }
        if (messageData.has("page_number")) {
            message.setAuthPage(String.valueOf(messageData.get("page_number")));
        }
        if (messageData.has("last_page_index")) {
            message.setAuthLength(String.valueOf(messageData.get("last_page_index")));
        }
        if (messageData.has("timestamp_raw")) {
            message.setAuthTimestamp(String.valueOf(messageData.get("timestamp_raw")));
        }
        if (messageData.has("auth_data")) {
            message.setAuthData(messageData.getString("auth_data"));
        }

        message.setDescription("Authenticated Drone");
    }

    private void parseLocationField(CoTMessage message, JSONObject messageData, String fieldName,
                                    java.util.function.Consumer<String> setter) {
        try {
            if (messageData.has(fieldName)) {
                String valueStr = messageData.getString(fieldName);
                if (valueStr.contains(" ")) {
                    valueStr = valueStr.split(" ")[0]; // Strip units
                }
                setter.accept(valueStr);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Error parsing field " + fieldName + ": " + e.getMessage());
        }
    }

    private DroneSignature.IdInfo.UAType mapUAType(String uaTypeStr) {
        if (uaTypeStr == null || uaTypeStr.isEmpty())
            return DroneSignature.IdInfo.UAType.OTHER;

        if (uaTypeStr.contains("Helicopter") || uaTypeStr.contains("Multirotor"))
            return DroneSignature.IdInfo.UAType.HELICOPTER;
        else if (uaTypeStr.contains("Aeroplane") || uaTypeStr.contains("Airplane"))
            return DroneSignature.IdInfo.UAType.AEROPLANE;
        else if (uaTypeStr.contains("Glider"))
            return DroneSignature.IdInfo.UAType.GLIDER;
        else if (uaTypeStr.contains("VTOL"))
            return DroneSignature.IdInfo.UAType.HYBRID_LIFT;
        else if (uaTypeStr.contains("Airship"))
            return DroneSignature.IdInfo.UAType.AIRSHIP;
        else if (uaTypeStr.contains("Free Balloon"))
            return DroneSignature.IdInfo.UAType.FREE_BALLOON;
        else if (uaTypeStr.contains("Captive Balloon"))
            return DroneSignature.IdInfo.UAType.CAPTIVE;
        else if (uaTypeStr.contains("Rocket"))
            return DroneSignature.IdInfo.UAType.ROCKET;
        else if (uaTypeStr.equals("None"))
            return DroneSignature.IdInfo.UAType.NONE;

        return DroneSignature.IdInfo.UAType.OTHER;
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasWifiPermissions() {
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void addDeviceLocationToData(JSONArray droneData) throws JSONException {
        // Look for a System Message object to add device location
        boolean foundSystemMessage = false;
        for (int i = 0; i < droneData.length(); i++) {
            JSONObject item = droneData.getJSONObject(i);
            if (item.has("System Message")) {
                JSONObject sysMsg = item.getJSONObject("System Message");
                sysMsg.put("operator_lat", lastDeviceLocation.getLatitude());
                sysMsg.put("operator_lon", lastDeviceLocation.getLongitude());
                if (lastDeviceLocation.hasAltitude()) {
                    sysMsg.put("operator_altitude_geo", lastDeviceLocation.getAltitude());
                }
                foundSystemMessage = true;
                break;
            }
        }

        // If no System Message found, create one
//        if (!foundSystemMessage) {
//            JSONObject systemObj = new JSONObject();
//            JSONObject systemMessage = new JSONObject();
//            systemObj.put("System Message", systemMessage);
//
//            systemMessage.put("operator_lat", lastDeviceLocation.getLatitude());
//            systemMessage.put("operator_lon", lastDeviceLocation.getLongitude());
//            if (lastDeviceLocation.hasAltitude()) {
//                systemMessage.put("operator_altitude_geo", lastDeviceLocation.getAltitude());
//            }
//            systemMessage.put("MAC", "device_" + System.currentTimeMillis());
//            systemMessage.put("RSSI", 0);
//
//            droneData.put(systemObj);
//        }
    }

    // Estimate drone location based on RSSI and device location - For drones without any GPS
    private void estimateDroneLocation(CoTMessage message) {
        if (message.getRssi() == null || lastDeviceLocation == null) return;

        if (!settings.isLocationEstimationEnabled()) {
            return;
        }

        // Simple distance estimation based on RSSI
        // This is a very basic model and could be improved
        // RSSI = -20 * log10(distance) - 41  (simplified free space path loss model)

        int rssi = message.getRssi();
        double distance = Math.pow(10, ((-rssi - 41) / 20.0)); // in meters

        // Cap the distance to something reasonable based on BLE/WiFi range
        distance = Math.min(distance, 300); // Max 300 meters

        // Generate random angle if we don't have previous data
        double bearing = Math.random() * 360; // Random direction

        // Calculate new position
        double lat = lastDeviceLocation.getLatitude();
        double lon = lastDeviceLocation.getLongitude();

        // Convert distance and bearing to lat/lon change
        // This is a simplified calculation and doesn't account for Earth's curvature
        // but should be good enough for short distances
        double latChange = distance * Math.cos(Math.toRadians(bearing)) / 111111.0;
        double lonChange = distance * Math.sin(Math.toRadians(bearing)) /
                (111111.0 * Math.cos(Math.toRadians(lat)));

        // Set drone location
        message.setLat(String.valueOf(lat + latChange));
        message.setLon(String.valueOf(lon + lonChange));

        // Generate a reasonable altitude (10-120 meters above ground)
        double altitude = 0;
        if (lastDeviceLocation.hasAltitude()) {
            altitude = lastDeviceLocation.getAltitude() + 10 + Math.random() * 110;
        } else {
            altitude = 100; // Default 100m
        }
        message.setAlt(String.valueOf(altitude));

        // Set estimated height above ground
        message.setHeight(String.valueOf(10 + Math.random() * 110));

        // Tag as estimated location
        Map<String, Object> rawData = message.getRawMessage();
        if (rawData == null) {
            rawData = new HashMap<>();
            message.setRawMessage(rawData);
        }
        rawData.put("location_estimated", true);
        rawData.put("estimated_distance", distance);

        Log.d(TAG, "Estimated drone location: " + (lat + latChange) + ", " + (lon + lonChange) +
                " (distance: " + distance + "m, bearing: " + bearing + "°)");
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DragonSync Onboard Detection")
                .setContentText("Scanning for drones via BT and WiFi...")
                .setSmallIcon(R.drawable.ic_drone_accent)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DragonSync Onboard Detection",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Scanning for drones using device radios");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        // Stop location updates
        locationManager.stopLocationUpdates();
        locationManager.removeListener(this::updateUserLocation);

        if (bluetoothScanner != null) {
            bluetoothScanner.stopScanning();
        }

        if (wifiScanner != null) {
            wifiScanner.stopScanning();
        }

        // Update settings to reflect that we're no longer listening
        settings.setListening(false);
        isRunning = false;

        Log.i(TAG, "Onboard detection service destroyed");
        super.onDestroy();
    }

    private void updateUserLocation(Location location) {
        lastDeviceLocation = location;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}