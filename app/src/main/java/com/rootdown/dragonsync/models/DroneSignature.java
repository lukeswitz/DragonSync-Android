package com.rootdown.dragonsync.models;

import android.location.Location;
import java.util.List;

public class DroneSignature {
    private IdInfo primaryId;
    private IdInfo secondaryId;
    private String operatorId;
    private String sessionId;
    private PositionInfo position;
    private MovementVector movement;
    private HeightInfo heightInfo;
    private TransmissionInfo transmissionInfo;
    private BroadcastPattern broadcastPattern;
    private double timestamp;
    private double firstSeen;
    private Double messageInterval;

    public CoTMessage getPrimaryId() {
        return primaryId;
    }

    public static class IdInfo extends CoTMessage {
        private String id;
        private IdType type;
        private String protocolVersion;
        private UAType uaType;
        private String macAddress;

        public enum IdType {
            SERIAL_NUMBER("Serial Number (ANSI/CTA-2063-A)"),
            CAA_REGISTRATION("CAA Assigned Registration ID"),
            UTM_ASSIGNED("UTM (USS) Assigned ID"),
            SESSION_ID("Specific Session ID"),
            UNKNOWN("Unknown");

            private final String displayName;
            IdType(String displayName) { this.displayName = displayName; }
            public String getDisplayName() { return displayName; }
        }

        public enum UAType {
            NONE, AEROPLANE, HELICOPTER, GYROPLANE, HYBRID_LIFT,
            ORNITHOPTER, GLIDER, KITE, FREE_BALLOON, CAPTIVE,
            AIRSHIP, FREE_FALL, ROCKET, TETHERED, GROUND_OBSTACLE, OTHER;
        }
    }

    public static class PositionInfo {
        private Location coordinate;
        private double altitude;
        private AltitudeReference altitudeReference;
        private Location lastKnownGoodPosition;
        private Location operatorLocation;
        private Location homeLocation;
        private Double horizontalAccuracy;
        private Double verticalAccuracy;
        private double timestamp;

        public enum AltitudeReference {
            TAKEOFF("Takeoff Location"),
            GROUND("Ground Level"),
            WGS84("WGS84");

            private final String displayName;
            AltitudeReference(String displayName) { this.displayName = displayName; }
            public String getDisplayName() { return displayName; }
        }
    }

    public static class MovementVector {
        private double groundSpeed;
        private double verticalSpeed;
        private double heading;
        private Double climbRate;
        private Double turnRate;
        private List<Location> flightPath;
        private double timestamp;
    }

    public static class HeightInfo {
        private double heightAboveGround;
        private Double heightAboveTakeoff;
        private HeightReferenceType referenceType;
        private Double horizontalAccuracy;
        private Double verticalAccuracy;
        private double consistencyScore;
        private Double lastKnownGoodHeight;
        private double timestamp;

        public enum HeightReferenceType {
            GROUND("Above Ground Level"),
            TAKEOFF("Above Takeoff"),
            PRESSURE("Pressure Altitude"),
            WGS84("WGS84");

            private final String displayName;
            HeightReferenceType(String displayName) { this.displayName = displayName; }
            public String getDisplayName() { return displayName; }
        }
    }

    public static class TransmissionInfo {
        private TransmissionType transmissionType;
        private Double signalStrength;
        private Double expectedSignalStrength;
        private String macAddress;
        private Double frequency;
        private ProtocolType protocolType;
        private List<MessageType> messageTypes;
        private double timestamp;
        private Integer channel;
        private String advMode;
        private String advAddress;
        private Integer did;
        private Integer sid;
        private Integer accessAddress;
        private Integer phy;

        public enum TransmissionType {
            BLE("BT4/5 DroneID"),
            WIFI("WiFi DroneID"),
            ESP32("ESP32 DroneID"),
            UNKNOWN("Unknown");

            private final String displayName;
            TransmissionType(String displayName) { this.displayName = displayName; }
            public String getDisplayName() { return displayName; }
        }

        public enum ProtocolType {
            OPEN_DRONE_ID("Open Drone ID"),
            LEGACY_REMOTE_ID("Legacy Remote ID"),
            ASTM_F3411("ASTM F3411"),
            CUSTOM("Custom");

            private final String displayName;
            ProtocolType(String displayName) { this.displayName = displayName; }
            public String getDisplayName() { return displayName; }
        }

        public enum MessageType {
            BT45("BT4/5 DroneID"),
            WIFI("WiFi DroneID"),
            ESP32("ESP32 DroneID");

            private final String displayName;
            MessageType(String displayName) { this.displayName = displayName; }
            public String getDisplayName() { return displayName; }
        }
    }

    public static class BroadcastPattern {
        private List<TransmissionInfo.MessageType> messageSequence;
        private List<Double> intervalPattern;
        private double consistency;
        private double startTime;
        private double lastUpdate;
    }

    public static class SpoofDetectionResult {
        private double confidence;
        private String reason;

        public SpoofDetectionResult() {
            this.confidence = 0.0;
            this.reason = "";
        }

        public SpoofDetectionResult(double confidence, String reason) {
            this.confidence = confidence;
            this.reason = reason;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}