package com.rootdown.dragonsync.network;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RebelHistoryDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "Rebel_history.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_Rebel_HISTORY = "Rebel_history";
    public static final String TABLE_SSID_HISTORY = "ssid_history";

    private static final String CREATE_Rebel_HISTORY_TABLE =
            "CREATE TABLE " + TABLE_Rebel_HISTORY + " (" +
                    "id TEXT PRIMARY KEY," +
                    "drone_id TEXT," +
                    "mac TEXT," +
                    "timestamp INTEGER," +
                    "latitude REAL," +
                    "longitude REAL," +
                    "altitude REAL," +
                    "rssi INTEGER," +
                    "detection_source TEXT," +
                    "is_spoofed INTEGER," +
                    "Rebel_detections TEXT," +
                    "raw_data TEXT" +
                    ")";

    private static final String CREATE_SSID_HISTORY_TABLE =
            "CREATE TABLE " + TABLE_SSID_HISTORY + " (" +
                    "id TEXT PRIMARY KEY," +
                    "ssid TEXT," +
                    "bssid TEXT," +
                    "first_seen INTEGER," +
                    "last_seen INTEGER," +
                    "detection_count INTEGER," +
                    "is_suspicious INTEGER," +
                    "Rebel_detections INTEGER" +
                    ")";

    public RebelHistoryDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_Rebel_HISTORY_TABLE);
        db.execSQL(CREATE_SSID_HISTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_Rebel_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SSID_HISTORY);
        onCreate(db);
    }
}