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
            // Add device location to the drone data if available
            if (lastDeviceLocation != null) {
                addDeviceLocationToData(droneData);
            }

            // Process each message in the array
            for (int i = 0; i < droneData.length(); i++) {
                JSONObject msgObj = droneData.getJSONObject(i);

                // Get the appropriate message type and data
                String messageType = null;
                JSONObject messageData = null;
                Iterator<String> keys = msgObj.keys();
                if (keys.hasNext()) {
                    messageType = keys.next();
                    messageData = msgObj.getJSONObject(messageType);
                }

                if (messageType == null || messageData == null) continue;

                // Extract key identifiers
                String id = messageData.optString("id", "");
                String mac = messageData.optString("MAC", "");

                // Create a unique key for deduplication
                String uniqueKey = !id.isEmpty() ? id : mac;
                uniqueKey = !uniqueKey.isEmpty() ? uniqueKey : String.valueOf(messageData.hashCode());

                // Check if we've recently seen this drone
                long currentTime = System.currentTimeMillis();
                Long lastDetection = lastDetectionTime.get(uniqueKey);

                // Deduplicate within 2 seconds
                if (lastDetection != null && (currentTime - lastDetection) < 2000) {
                    continue;
                }

                // Update last detection time
                lastDetectionTime.put(uniqueKey, currentTime);

                // Convert to CoTMessage
                CoTMessage message = convertToCoTMessage(messageType, messageData, source);

                if (message != null) {
                    // Only estimate location if drone doesn't provide its own coordinates
                    // AND user has enabled location estimation
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
                    }

                    // Broadcast the enhanced telemetry message
                    Intent telemetryIntent = new Intent("com.rootdown.dragonsync.TELEMETRY");
                    telemetryIntent.setPackage(getPackageName());
                    telemetryIntent.putExtra("parsed_message", message);
                    telemetryIntent.putExtra("raw_message", messageType + ": " + messageData.toString());
                    sendBroadcast(telemetryIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing drone data: " + e.getMessage(), e);
        }
    }

    private CoTMessage convertToCoTMessage(String messageType, JSONObject messageData, String source) {
        CoTMessage message = new CoTMessage();

        try {
            // Common fields
            if (messageData.has("MAC")) {
                message.setMac(messageData.getString("MAC"));
            }

            if (messageData.has("RSSI")) {
                message.setRssi(messageData.getInt("RSSI"));
            }

            // Source information
            message.setType(source + "_ONBOARD");

            // Specific message type handling
            switch (messageType) {
                case "Basic ID":
                    if (messageData.has("id")) {
                        message.setUid(messageData.getString("id"));
                    } else if (messageData.has("MAC")) {
                        // Use MAC as fallback ID
                        message.setUid(messageData.getString("MAC"));
                    } else {
                        message.setUid("Unknown_" + System.currentTimeMillis());
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
                    break;

                case "Location/Vector Message":
                    if (messageData.has("latitude") && messageData.has("longitude")) {
                        message.setLat(String.valueOf(messageData.getDouble("latitude")));
                        message.setLon(String.valueOf(messageData.getDouble("longitude")));
                    }

                    if (messageData.has("speed")) {
                        String speedStr = messageData.getString("speed");
                        // Extract numeric part if needed
                        if (speedStr.contains(" ")) {
                            speedStr = speedStr.split(" ")[0];
                        }
                        message.setSpeed(speedStr);
                    }

                    if (messageData.has("vert_speed")) {
                        String vspeedStr = messageData.getString("vert_speed");
                        if (vspeedStr.contains(" ")) {
                            vspeedStr = vspeedStr.split(" ")[0];
                        }
                        message.setVspeed(vspeedStr);
                    }

                    if (messageData.has("geodetic_altitude")) {
                        String altStr = messageData.getString("geodetic_altitude");
                        if (altStr.contains(" ")) {
                            altStr = altStr.split(" ")[0];
                        }
                        message.setAlt(altStr);
                    }

                    if (messageData.has("height_agl")) {
                        String heightStr = messageData.getString("height_agl");
                        if (heightStr.contains(" ")) {
                            heightStr = heightStr.split(" ")[0];
                        }
                        message.setHeight(heightStr);
                    }

                    if (messageData.has("direction")) {
                        message.setDirection(String.valueOf(messageData.getDouble("direction")));
                    }

                    if (messageData.has("timestamp")) {
                        // Convert to milliseconds if needed
                        message.setTimestamp(String.valueOf(System.currentTimeMillis()));
                    }
                    break;

                case "Self-ID Message":
                    if (messageData.has("text")) {
                        message.setSelfIDText(messageData.getString("text"));

                        // If we don't have a description yet, use self-ID text
                        if (message.getDescription() == null || message.getDescription().isEmpty()) {
                            message.setDescription(messageData.getString("text"));
                        }
                    }
                    break;

                case "System Message":
                    if (messageData.has("operator_lat") && messageData.has("operator_lon")) {
                        message.setPilotLat(String.valueOf(messageData.getDouble("operator_lat")));
                        message.setPilotLon(String.valueOf(messageData.getDouble("operator_lon")));
                    }

                    if (messageData.has("operator_altitude_geo")) {
                        String opAltStr = messageData.getString("operator_altitude_geo");
                        if (opAltStr.contains(" ")) {
                            opAltStr = opAltStr.split(" ")[0];
                        }
                        // We could store this in a metadata field if needed
                    }
                    break;

                case "Operator ID Message":
                    if (messageData.has("operator_id")) {
                        message.setOperatorId(messageData.getString("operator_id"));
                    }

                    if (messageData.has("operator_id_type")) {
                        message.setOperatorIdType(messageData.getString("operator_id_type"));
                    }
                    break;
            }

            // For any unhandled message fields, store them in raw message data
            Map<String, Object> rawData = new HashMap<>();
            Iterator<String> keys = messageData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                rawData.put(key, messageData.get(key));
            }
            message.setRawMessage(rawData);

            return message;

        } catch (JSONException e) {
            Log.e(TAG, "Error converting drone data to CoT Message: " + e.getMessage());
            return null;
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
        if (!foundSystemMessage) {
            JSONObject systemObj = new JSONObject();
            JSONObject systemMessage = new JSONObject();
            systemObj.put("System Message", systemMessage);

            systemMessage.put("operator_lat", lastDeviceLocation.getLatitude());
            systemMessage.put("operator_lon", lastDeviceLocation.getLongitude());
            if (lastDeviceLocation.hasAltitude()) {
                systemMessage.put("operator_altitude_geo", lastDeviceLocation.getAltitude());
            }

            droneData.put(systemObj);
        }
    }

    // Estimate drone location based on RSSI and device location - For drones without any GPS (DISABLED)
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
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DragonSync Onboard Detection")
                .setContentText("Scanning for drones via BT and WiFi...")
                .setSmallIcon(R.drawable.ic_drone)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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