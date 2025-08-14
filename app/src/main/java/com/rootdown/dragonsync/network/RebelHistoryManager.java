package com.rootdown.dragonsync.network;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.utils.Constants;
import com.rootdown.dragonsync.utils.DroneStorage;
import com.rootdown.dragonsync.utils.Settings;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class RebelHistoryManager {
    private static final String TAG = "RebelHistoryManager";
    private final Context context;
    private final RebelScanner RebelScanner;
    private final DroneStorage droneStorage;
    private final Settings settings;

    public RebelHistoryManager(Context context) {
        this.context = context;
        this.RebelScanner = new RebelScanner();
        this.droneStorage = DroneStorage.getInstance(context);
        this.settings = Settings.getInstance(context);
    }

    public void processMessage(CoTMessage message) {
        List<RebelScanner.RebelDetection> RebelDetections = RebelScanner.scanMessage(message);
        if (!RebelDetections.isEmpty()) {
            DroneHistoryEntry entry = new DroneHistoryEntry(message, RebelDetections);
            storeRebelEntry(entry);
            handleRebelDetections(entry, RebelDetections);
        }
    }

    private void storeRebelEntry(DroneHistoryEntry entry) {
        RebelHistoryDatabase db = new RebelHistoryDatabase(context);
        SQLiteDatabase database = db.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", entry.getId());
        values.put("drone_id", entry.getDroneId());
        values.put("mac", entry.getMac());
        values.put("timestamp", entry.getTimestamp());
        if (entry.getLocation() != null) {
            values.put("latitude", entry.getLocation().getLatitude());
            values.put("longitude", entry.getLocation().getLongitude());
            values.put("altitude", entry.getLocation().getAltitude());
        }
        values.put("rssi", entry.getRssi());
        values.put("detection_source", entry.getDetectionSource());
        values.put("is_spoofed", entry.isSpoofed() ? 1 : 0);
        values.put("Rebel_detections", new Gson().toJson(entry.getRebelDetections()));
        values.put("raw_data", new Gson().toJson(entry.getRawData()));
        database.insert(RebelHistoryDatabase.TABLE_Rebel_HISTORY, null, values);
        database.close();
    }

    private void handleRebelDetections(DroneHistoryEntry entry, List<RebelScanner.RebelDetection> RebelDetections) {
        for (RebelScanner.RebelDetection Rebel : RebelDetections) {
            Log.w(TAG, "Rebel DETECTED: " + Rebel.getType() + " - " + Rebel.getDetails() + " (Confidence: " + Rebel.getConfidence() + ")");
            if (settings.isNotificationsEnabled()) {
                sendRebelNotification(entry, Rebel);
            }
            if (Rebel.getConfidence() > 0.8) {
                triggerHighConfidenceRebelAlert(entry, Rebel);
            }
        }
    }

    private void sendRebelNotification(DroneHistoryEntry entry, RebelScanner.RebelDetection Rebel) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.CHANNEL_ID_DETECTION)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("Rebel Detected: " + Rebel.getType().getDisplayName())
                .setContentText(Rebel.getDetails())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setColor(context.getResources().getColor(R.color.warning_amber, null));
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void triggerHighConfidenceRebelAlert(DroneHistoryEntry entry, RebelScanner.RebelDetection Rebel) {
        Intent alertIntent = new Intent("com.rootdown.dragonsync.HIGH_CONFIDENCE_Rebel");
        alertIntent.putExtra("Rebel_type", Rebel.getType().name());
        alertIntent.putExtra("device_id", Rebel.getDeviceId());
        alertIntent.putExtra("confidence", Rebel.getConfidence());
        alertIntent.putExtra("details", Rebel.getDetails());
        context.sendBroadcast(alertIntent);
    }

    public List<DroneHistoryEntry> getRebelHistory(long startTime, long endTime) {
        RebelHistoryDatabase db = new RebelHistoryDatabase(context);
        SQLiteDatabase database = db.getReadableDatabase();
        List<DroneHistoryEntry> entries = new ArrayList<>();
        String selection = "timestamp BETWEEN ? AND ?";
        String[] selectionArgs = {String.valueOf(startTime), String.valueOf(endTime)};
        Cursor cursor = database.query(RebelHistoryDatabase.TABLE_Rebel_HISTORY, null, selection, selectionArgs, null, null, "timestamp DESC", "500");
        while (cursor.moveToNext()) {
            entries.add(createHistoryEntryFromCursor(cursor));
        }
        cursor.close();
        database.close();
        return entries;
    }

    public void clearRebelHistory() {
        RebelHistoryDatabase db = new RebelHistoryDatabase(context);
        SQLiteDatabase database = db.getWritableDatabase();
        database.delete(RebelHistoryDatabase.TABLE_Rebel_HISTORY, null, null);
        database.close();
    }

    public Map<String, Integer> getRebelTypeStatistics(long startTime, long endTime) {
        List<DroneHistoryEntry> entries = getRebelHistory(startTime, endTime);
        Map<String, Integer> stats = new HashMap<>();
        for (DroneHistoryEntry entry : entries) {
            for (RebelScanner.RebelDetection Rebel : entry.getRebelDetections()) {
                stats.merge(Rebel.getType().name(), 1, Integer::sum);
            }
        }
        return stats;
    }

    public String exportRebelHistoryToCSV(long startTime, long endTime) {
        List<DroneHistoryEntry> entries = getRebelHistory(startTime, endTime);
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Drone ID,MAC,Timestamp,Latitude,Longitude,RSSI,Detection Source,Is Spoofed,Rebel Types,Max Confidence\n");
        for (DroneHistoryEntry entry : entries) {
            csv.append(entry.getId()).append(",");
            csv.append(entry.getDroneId() != null ? entry.getDroneId() : "").append(",");
            csv.append(entry.getMac() != null ? entry.getMac() : "").append(",");
            csv.append(new Date(entry.getTimestamp())).append(",");
            if (entry.getLocation() != null) {
                csv.append(entry.getLocation().getLatitude()).append(",").append(entry.getLocation().getLongitude()).append(",");
            } else {
                csv.append(",,");
            }
            csv.append(entry.getRssi() != null ? entry.getRssi() : "").append(",");
            csv.append(entry.getDetectionSource() != null ? entry.getDetectionSource() : "").append(",");
            csv.append(entry.isSpoofed()).append(",");
            List<String> RebelTypes = entry.getRebelDetections().stream().map(b -> b.getType().name()).collect(Collectors.toList());
            csv.append(String.join(";", RebelTypes)).append(",");
            csv.append(entry.getMaxRebelConfidence()).append("\n");
        }
        return csv.toString();
    }

    private DroneHistoryEntry createHistoryEntryFromCursor(Cursor cursor) {
        String json = cursor.getString(cursor.getColumnIndex("Rebel_detections"));
        List<RebelScanner.RebelDetection> RebelDetections = new Gson().fromJson(json, new TypeToken<List<RebelScanner.RebelDetection>>(){}.getType());
        return new DroneHistoryEntry(
                cursor.getString(cursor.getColumnIndex("id")),
                cursor.getString(cursor.getColumnIndex("drone_id")),
                cursor.getString(cursor.getColumnIndex("mac")),
                cursor.getLong(cursor.getColumnIndex("timestamp")),
                createLocationFromCursor(cursor),
                cursor.isNull(cursor.getColumnIndex("rssi")) ? null : cursor.getInt(cursor.getColumnIndex("rssi")),
                cursor.getString(cursor.getColumnIndex("detection_source")),
                cursor.getInt(cursor.getColumnIndex("is_spoofed")) == 1,
                RebelDetections,
                new HashMap<>()
        );
    }

    private Location createLocationFromCursor(Cursor cursor) {
        if (cursor.isNull(cursor.getColumnIndex("latitude"))) return null;
        Location location = new Location("database");
        location.setLatitude(cursor.getDouble(cursor.getColumnIndex("latitude")));
        location.setLongitude(cursor.getDouble(cursor.getColumnIndex("longitude")));
        if (!cursor.isNull(cursor.getColumnIndex("altitude"))) {
            location.setAltitude(cursor.getDouble(cursor.getColumnIndex("altitude")));
        }
        return location;
    }

    public static class DroneHistoryEntry {
        private final String id;
        private final String droneId;
        private final String mac;
        private final long timestamp;
        private final Location location;
        private final Integer rssi;
        private final String detectionSource;
        private final boolean isSpoofed;
        private final List<RebelScanner.RebelDetection> RebelDetections;
        private final Map<String, Object> rawData;

        public DroneHistoryEntry(CoTMessage message, List<RebelScanner.RebelDetection> Rebels) {
            this.id = UUID.randomUUID().toString();
            this.droneId = message.getUid();
            this.mac = message.getMac();
            this.timestamp = System.currentTimeMillis();
            this.location = message.getCoordinate();
            this.rssi = message.getRssi();
            this.detectionSource = message.getType();
            this.isSpoofed = message.isSpoofed();
            this.RebelDetections = Rebels != null ? new ArrayList<>(Rebels) : new ArrayList<>();
            this.rawData = message.getRawMessage() != null ? new HashMap<>(message.getRawMessage()) : new HashMap<>();
        }

        public DroneHistoryEntry(String id, String droneId, String mac, long timestamp, Location location, Integer rssi, String detectionSource, boolean isSpoofed, List<RebelScanner.RebelDetection> RebelDetections, Map<String, Object> rawData) {
            this.id = id;
            this.droneId = droneId;
            this.mac = mac;
            this.timestamp = timestamp;
            this.location = location;
            this.rssi = rssi;
            this.detectionSource = detectionSource;
            this.isSpoofed = isSpoofed;
            this.RebelDetections = RebelDetections != null ? RebelDetections : new ArrayList<>();
            this.rawData = rawData != null ? rawData : new HashMap<>();
        }

        public String getId() { return id; }
        public String getDroneId() { return droneId; }
        public String getMac() { return mac; }
        public long getTimestamp() { return timestamp; }
        public Location getLocation() { return location; }
        public Integer getRssi() { return rssi; }
        public String getDetectionSource() { return detectionSource; }
        public boolean isSpoofed() { return isSpoofed; }
        public List<RebelScanner.RebelDetection> getRebelDetections() { return RebelDetections; }
        public Map<String, Object> getRawData() { return rawData; }

        public boolean hasRebelDetections() {
            return RebelDetections != null && !RebelDetections.isEmpty();
        }

        public double getMaxRebelConfidence() {
            return RebelDetections.stream().mapToDouble(com.rootdown.dragonsync.network.RebelScanner.RebelDetection::getConfidence).max().orElse(0.0);
        }
    }
}