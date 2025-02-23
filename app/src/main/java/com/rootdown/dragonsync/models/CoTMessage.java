package com.rootdown.dragonsync.models;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

public class CoTMessage implements Parcelable {

    private String id;
    private String caaRegistration;
    private String uid;
    private String type;

    // Basic location and movement
    private String lat;
    private String lon;
    private String homeLat;
    private String homeLon;
    private String speed;
    private String vspeed;
    private String alt;
    private String height;
    private String pilotLat;
    private String pilotLon;
    private String description;
    private String selfIDText;
    private UAType uaType;

    // Basic ID and signal info
    private String idType;
    private String protocolVersion;
    private String mac;
    private int rssi;
    private String manufacturer;
    private List<SignalSource> signalSources;

    // Location/Vector Message fields
    private String locationProtocol;
    private String opStatus;
    private String heightType;
    private String ewDirSegment;
    private String speedMultiplier;
    private String direction;
    private Double geodeticAltitude;
    private String verticalAccuracy;
    private String horizontalAccuracy;
    private String baroAccuracy;
    private String speedAccuracy;
    private String timestamp;
    private String timestampAccuracy;

    public String getUid() { return uid; }
    public String getLat() { return lat; }
    public String getLon() { return lon; }
    public String getDescription() { return description; }
    public Integer getRssi() { return rssi; }
    public String getMac() { return mac; }
    public String getAlt() { return alt; }
    public String getSpeed() { return speed; }

    // Raw message data
    private Map<String, Object> rawMessage;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {

    }

    public enum UAType {
        NONE("None"),
        AEROPLANE("Aeroplane"),
        HELICOPTER("Helicopter/Multirotor"),
        GYROPLANE("Gyroplane"),
        HYBRID_LIFT("Hybrid Lift"),
        ORNITHOPTER("Ornithopter"),
        GLIDER("Glider"),
        KITE("Kite"),
        FREE_BALLOON("Free Balloon"),
        CAPTIVE("Captive Balloon"),
        AIRSHIP("Airship"),
        FREE_FALL("Free Fall/Parachute"),
        ROCKET("Rocket"),
        TETHERED("Tethered Powered Aircraft"),
        GROUND_OBSTACLE("Ground Obstacle"),
        OTHER("Other");

        private final String displayName;

        UAType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return switch (this) {
                case NONE -> "airplane"; // Fallback
                case AEROPLANE -> "airplane";
                case HELICOPTER -> "airplane";
                case GYROPLANE, HYBRID_LIFT -> "airplane_circle";
                case ORNITHOPTER -> "bird";
                case GLIDER -> "paperplane";
                case KITE -> "wind";
                case FREE_BALLOON -> "balloon";
                case CAPTIVE -> "balloon_fill";
                case AIRSHIP -> "airplane_circle";
                case FREE_FALL -> "arrow_down_circle";
                case ROCKET -> "rocket";
                case TETHERED -> "link_circle";
                case GROUND_OBSTACLE -> "exclamationmark_triangle";
                default -> "questionmark_circle";
            };
        }
    }

    public static class SignalSource {
        private String mac;
        private int rssi;
        private SignalType type;
        private long timestamp;

        public enum SignalType {
            BLUETOOTH("BT4/5 DroneID"),
            WIFI("WiFi DroneID"),
            SDR("SDR DroneID"),
            UNKNOWN("Unknown");

            private final String displayName;

            SignalType(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        // Constructor
        public SignalSource(String mac, int rssi, SignalType type, long timestamp) {
            this.mac = mac;
            this.rssi = rssi;
            this.type = type;
            this.timestamp = timestamp;
        }

        // Getters and setters
        public String getMac() { return mac; }
        public int getRssi() { return rssi; }
        public SignalType getType() { return type; }
        public long getTimestamp() { return timestamp; }
    }

    // Convenience method to get coordinate
    public Location getCoordinate() {
        try {
            Location location = new Location("");
            location.setLatitude(Double.parseDouble(lat));
            location.setLongitude(Double.parseDouble(lon));
            return location;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Getters and setters for all fields

}