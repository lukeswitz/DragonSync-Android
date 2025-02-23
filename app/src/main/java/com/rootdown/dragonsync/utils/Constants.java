// java/com.rootdown.dragonsync/utils/Constants.java
package com.rootdown.dragonsync.utils;

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
    public static final double DEFAULT_CPU_WARNING_THRESHOLD = 80.0;  // 80%
    public static final double DEFAULT_TEMP_WARNING_THRESHOLD = 70.0; // 70°C
    public static final double DEFAULT_MEMORY_WARNING_THRESHOLD = 0.85; // 85%
    public static final double DEFAULT_PLUTO_TEMP_THRESHOLD = 85.0;  // 85°C
    public static final double DEFAULT_ZYNQ_TEMP_THRESHOLD = 85.0;   // 85°C
    public static final int DEFAULT_PROXIMITY_THRESHOLD = -60;       // -60 dBm

    // Spoof Detection Thresholds
    public static final double MIN_RSSI_DELTA = 15.0;
    public static final double MIN_DISTANCE = 10.0;      // Minimum distance in meters
    public static final double MAX_SPEED_MPS = 150.0;    // Maximum realistic speed in m/s
    public static final double MIN_POSITION_CHANGE = 50.0;
    public static final double CONFIDENCE_THRESHOLD = 0.7; // Minimum confidence to report spoof

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

    // Cache Sizes
    public static final int MAX_MESSAGES_CACHE = 1000;
    public static final int MAX_HISTORY_ENTRIES = 100;
    public static final int MAX_HOST_HISTORY = 5;

    private Constants() {
        // Prevent instantiation
    }
}