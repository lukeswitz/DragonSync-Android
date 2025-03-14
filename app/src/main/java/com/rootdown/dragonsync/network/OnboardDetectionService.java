package com.rootdown.dragonsync.network;

import android.Manifest;
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
import com.rootdown.dragonsync.utils.DeviceLocationManager;
import com.rootdown.dragonsync.utils.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
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

            // Parse the drone data into CoTMessage
            String jsonString = droneData.toString();
            XMLParser.ParseResult result = xmlParser.parse(jsonString);

            if (result.error != null) {
                Log.e(TAG, "Error parsing drone data: " + result.error);
                return;
            }

            // If we have a valid CoT message, enhance it with additional data
            if (result.cotMessage != null) {
                // Tag the message source
                result.cotMessage.setType(source + "_ONBOARD");

                // Only estimate location if drone doesn't provide its own coordinates
                // AND user has enabled location estimation
                if (result.cotMessage.getCoordinate() == null &&
                        lastDeviceLocation != null &&
                        result.cotMessage.getRssi() != null &&
                        settings.isLocationEstimationEnabled()) {

                    estimateDroneLocation(result.cotMessage);
                }

                // Set the device (operator) location for display purposes
                if (lastDeviceLocation != null) {
                    result.cotMessage.setPilotLat(String.valueOf(lastDeviceLocation.getLatitude()));
                    result.cotMessage.setPilotLon(String.valueOf(lastDeviceLocation.getLongitude()));
                }

                // Calculate distance between user and drone if both coordinates are available
                if (lastDeviceLocation != null && result.cotMessage.getCoordinate() != null) {
                    float distanceInMeters = lastDeviceLocation.distanceTo(result.cotMessage.getCoordinate());

                    // Store the calculated distance in the message
                    if (result.cotMessage.getRawMessage() == null) {
                        result.cotMessage.setRawMessage(new HashMap<>());
                    }
                    result.cotMessage.getRawMessage().put("calculated_distance", distanceInMeters);
                }

                // Broadcast the enhanced telemetry message
                Intent telemetryIntent = new Intent("com.rootdown.dragonsync.TELEMETRY");
                telemetryIntent.setPackage(getPackageName());
                telemetryIntent.putExtra("parsed_message", result.cotMessage);
                telemetryIntent.putExtra("raw_message", jsonString);
                sendBroadcast(telemetryIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing drone data: " + e.getMessage(), e);
        }
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasWifiPermissions() {
        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
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
                    sysMsg.put("operator_alt_geo", lastDeviceLocation.getAltitude());
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
                systemMessage.put("operator_alt_geo", lastDeviceLocation.getAltitude());
            }

            droneData.put(systemObj);
        }
    }

    // Estimate drone location based on RSSI and device location
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


    @Override
    public void onDestroy() {
        // Stop location updates
        locationManager.stopLocationUpdates();
//        locationManager.removeListener(locationListener); // TODO add location listener

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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}