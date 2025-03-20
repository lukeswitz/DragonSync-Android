package com.rootdown.dragonsync.utils;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class Constants {
    // Network Constants
    public static final int DEFAULT_MULTICAST_PORT = 6969;
    public static final int DEFAULT_ZMQ_TELEMETRY_PORT = 4224;
    public static final int DEFAULT_ZMQ_STATUS_PORT = 4225;
    public static final int DEFAULT_ZMQ_SPECTRUM_PORT = 4226;
    public static final int DEFAULT_SERIAL_MULTICAST_PORT = 6970;
    public static final int DEFAULT_SERIAL_ZMQ_PORT = 4227;
    public static final String DEFAULT_MULTICAST_HOST = "224.0.0.1";
    public static final String DEFAULT_ZMQ_HOST = "0.0.0.0";

    // Warning Thresholds
    public static final double CPU_WARNING_THRESHOLD = 70.0;      // 70%
    public static final double CPU_CRITICAL_THRESHOLD = 90.0;     // 90%
    public static final double MEMORY_WARNING_THRESHOLD = 0.7;    // 70%
    public static final double MEMORY_CRITICAL_THRESHOLD = 0.9;   // 90%
    public static final double TEMP_WARNING_THRESHOLD = 60.0;     // 60°C
    public static final double TEMP_CRITICAL_THRESHOLD = 80.0;    // 80°C
    public static final double PLUTO_TEMP_WARNING_THRESHOLD = 70.0; // 70°C
    public static final double PLUTO_TEMP_CRITICAL_THRESHOLD = 85.0; // 85°C
    public static final double ZYNQ_TEMP_WARNING_THRESHOLD = 70.0;  // 70°C
    public static final double ZYNQ_TEMP_CRITICAL_THRESHOLD = 85.0; // 85°C
    public static final int PROXIMITY_THRESHOLD = -60;            // -60 dBm

    // RSSI Thresholds
    public static final int RSSI_GOOD_THRESHOLD = -60;      // Good signal: > -60 dBm
    public static final int RSSI_MEDIUM_THRESHOLD = -80;    // Medium signal: > -80 dBm

    // Spoof Detection Thresholds
    public static final double MIN_RSSI_DELTA = 15.0;
    public static final double MIN_DISTANCE = 10.0;      // Minimum distance in meters
    public static final double MAX_SPEED_MPS = 150.0;    // Maximum realistic speed in m/s
    public static final double MIN_POSITION_CHANGE = 50.0;
    public static final double CONFIDENCE_THRESHOLD = 0.7; // Minimum confidence to report spoof

    // Map Marker Colors
    public static final float MARKER_COLOR_DRONE = BitmapDescriptorFactory.HUE_AZURE;
    public static final float MARKER_COLOR_HOME = BitmapDescriptorFactory.HUE_YELLOW;
    public static final float MARKER_COLOR_OPERATOR = BitmapDescriptorFactory.HUE_GREEN;
    public static final float MARKER_COLOR_USER = BitmapDescriptorFactory.HUE_BLUE;
    public static final int MAX_FLIGHT_PATH_POINTS = 200;

    // Storage Constants
    public static final String PREFS_NAME = "dragonsync_prefs";
    public static final String KEY_CONNECTION_MODE = "connection_mode";
    public static final String KEY_ZMQ_HOST = "zmq_host";
    public static final String KEY_MULTICAST_HOST = "multicast_host";
    public static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    public static final String KEY_IS_LISTENING = "is_listening";
    public static final String KEY_SPOOF_DETECTION_ENABLED = "spoof_detection_enabled";
    public static final String KEY_SYSTEM_WARNINGS_ENABLED = "system_warnings_enabled";
    public static final String KEY_ENABLE_PROXIMITY_WARNINGS = "enable_proximity_warnings";
    public static final String KEY_SERIAL_CONSOLE_ENABLED = "serial_console_enabled";
    public static final String KEY_ENABLE_LOCATION_ESTIMATION = "enable_location_estimation";

    // Cache Sizes
    public static final int MAX_MESSAGES_CACHE = 1000;
    public static final int MAX_HISTORY_ENTRIES = 100;
    public static final int MAX_HOST_HISTORY = 5;

    // Notification constants
    public static final String CHANNEL_ID_NETWORK = "DragonSyncNetwork";
    public static final String CHANNEL_ID_DETECTION = "DragonSyncDetection";
    public static final int NOTIFICATION_ID_NETWORK = 1;
    public static final int NOTIFICATION_ID_DETECTION = 2;

    private Constants() {
        // Prevent instantiation
    }
}