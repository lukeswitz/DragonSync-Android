package com.rootdown.dragonsync.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DroneStorage {
    private static DroneStorage instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private static final String PREF_NAME = "drone_encounters";
    private static final String KEY_ENCOUNTERS = "encounters";

    public static class DroneEncounter {
        private String id;
        private Date firstSeen;
        private Date lastSeen;
        private List<FlightPathPoint> flightPath;
        private List<SignatureData> signatures;
        private Map<String, String> metadata;
        private Set<String> macHistory;

        public Date getLastSeen() {
            return null; //TODO do this
        }

        public Date getFirstSeen() {
            return firstSeen;
        }

        public String  getId() {
            return id;
        }

        public static class FlightPathPoint {
            private double latitude;
            private double longitude;
            private double altitude;
            private long timestamp;
            private Double homeLatitude;
            private Double homeLongitude;

            public Location getCoordinate() {
                Location location = new Location("");
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                return location;
            }

            public Location getHomeLocation() {
                if (homeLatitude == null || homeLongitude == null) return null;
                Location location = new Location("");
                location.setLatitude(homeLatitude);
                location.setLongitude(homeLongitude);
                return location;
            }
        }

        public static class SignatureData {
            private long timestamp;
            private double rssi;
            private double speed;
            private double height;
            private String mac;
            private boolean isValid;

            public SignatureData(long timestamp, double rssi, double speed,
                                 double height, String mac) {
                this.timestamp = timestamp;
                this.rssi = rssi;
                this.speed = speed;
                this.height = height;
                this.mac = mac;
                this.isValid = rssi != 0 || speed != 0 || height != 0;
            }
        }

        // Stats methods
        public double getMaxAltitude() {
            return flightPath.stream()
                    .mapToDouble(point -> point.altitude)
                    .filter(alt -> alt > 0)
                    .max()
                    .orElse(0);
        }

        public double getMaxSpeed() {
            return signatures.stream()
                    .mapToDouble(sig -> sig.speed)
                    .filter(speed -> speed > 0)
                    .max()
                    .orElse(0);
        }

        public double getAverageRSSI() {
            return signatures.stream()
                    .mapToDouble(sig -> sig.rssi)
                    .filter(rssi -> rssi != 0)
                    .average()
                    .orElse(0);
        }

        public long getTotalFlightTime() {
            return lastSeen.getTime() - firstSeen.getTime();
        }
    }

    private DroneStorage(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized DroneStorage getInstance(Context context) {
        if (instance == null) {
            instance = new DroneStorage(context.getApplicationContext());
        }
        return instance;
    }

    public void saveEncounter(DroneEncounter encounter) {
        Map<String, DroneEncounter> encounters = getEncounters();
        encounters.put(encounter.id, encounter);
        saveEncounters(encounters);
    }

    public void deleteEncounter(String id) {
        Map<String, DroneEncounter> encounters = getEncounters();
        encounters.remove(id);
        saveEncounters(encounters);
    }

    public void deleteAllEncounters() {
        prefs.edit().remove(KEY_ENCOUNTERS).apply();
    }

    public Map<String, DroneEncounter> getEncounters() {
        String json = prefs.getString(KEY_ENCOUNTERS, "{}");
        Type type = new TypeToken<Map<String, DroneEncounter>>(){}.getType();
        return gson.fromJson(json, type);
    }

    private void saveEncounters(Map<String, DroneEncounter> encounters) {
        String json = gson.toJson(encounters);
        prefs.edit().putString(KEY_ENCOUNTERS, json).apply();
    }

    public String exportToCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append(getCSVHeaders()).append("\n");

        Map<String, DroneEncounter> encounters = getEncounters();
        for (DroneEncounter encounter : encounters.values()) {
            csv.append(getCSVRow(encounter)).append("\n");
        }

        return csv.toString();
    }

    private String getCSVHeaders() {
        return "First Seen,First Seen Latitude,First Seen Longitude,First Seen Altitude (m)," +
                "Last Seen,Last Seen Latitude,Last Seen Longitude,Last Seen Altitude (m)," +
                "ID,CAA Registration,Primary MAC,Flight Path Points," +
                "Max Altitude (m),Max Speed (m/s),Average RSSI (dBm)," +
                "Flight Duration (HH:MM:SS),Height (m),Manufacturer," +
                "MAC Count,MAC History";
    }

    private String getCSVRow(DroneEncounter encounter) {
        // Format and return CSV row based on encounter data
        // TODO!!
        return "";
    }
}