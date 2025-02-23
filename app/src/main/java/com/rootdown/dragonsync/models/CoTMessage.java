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

    protected CoTMessage(Parcel in) {
        id = in.readString();
        caaRegistration = in.readString();
        uid = in.readString();
        type = in.readString();
        lat = in.readString();
        lon = in.readString();
        homeLat = in.readString();
        homeLon = in.readString();
        speed = in.readString();
        vspeed = in.readString();
        alt = in.readString();
        height = in.readString();
        pilotLat = in.readString();
        pilotLon = in.readString();
        description = in.readString();
        selfIDText = in.readString();
        idType = in.readString();
        protocolVersion = in.readString();
        mac = in.readString();
        rssi = in.readInt();
        manufacturer = in.readString();
        locationProtocol = in.readString();
        opStatus = in.readString();
        heightType = in.readString();
        ewDirSegment = in.readString();
        speedMultiplier = in.readString();
        direction = in.readString();
        if (in.readByte() == 0) {
            geodeticAltitude = null;
        } else {
            geodeticAltitude = in.readDouble();
        }
        verticalAccuracy = in.readString();
        horizontalAccuracy = in.readString();
        baroAccuracy = in.readString();
        speedAccuracy = in.readString();
        timestamp = in.readString();
        timestampAccuracy = in.readString();
    }

    public static final Creator<CoTMessage> CREATOR = new Creator<CoTMessage>() {
        @Override
        public CoTMessage createFromParcel(Parcel in) {
            return new CoTMessage(in);
        }

        @Override
        public CoTMessage[] newArray(int size) {
            return new CoTMessage[size];
        }
    };

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
        dest.writeString(id);
        dest.writeString(caaRegistration);
        dest.writeString(uid);
        dest.writeString(type);
        dest.writeString(lat);
        dest.writeString(lon);
        dest.writeString(homeLat);
        dest.writeString(homeLon);
        dest.writeString(speed);
        dest.writeString(vspeed);
        dest.writeString(alt);
        dest.writeString(height);
        dest.writeString(pilotLat);
        dest.writeString(pilotLon);
        dest.writeString(description);
        dest.writeString(selfIDText);
        dest.writeString(idType);
        dest.writeString(protocolVersion);
        dest.writeString(mac);
        dest.writeInt(rssi);
        dest.writeString(manufacturer);
        dest.writeString(locationProtocol);
        dest.writeString(opStatus);
        dest.writeString(heightType);
        dest.writeString(ewDirSegment);
        dest.writeString(speedMultiplier);
        dest.writeString(direction);
        if (geodeticAltitude == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeDouble(geodeticAltitude);
        }
        dest.writeString(verticalAccuracy);
        dest.writeString(horizontalAccuracy);
        dest.writeString(baroAccuracy);
        dest.writeString(speedAccuracy);
        dest.writeString(timestamp);
        dest.writeString(timestampAccuracy);
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