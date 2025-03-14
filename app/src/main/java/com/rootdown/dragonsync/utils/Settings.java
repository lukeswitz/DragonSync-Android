package com.rootdown.dragonsync.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rootdown.dragonsync.models.ConnectionMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Settings {
    private static Settings instance;
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    private final Context context;
    private final Gson gson;

    private Settings(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.editor = prefs.edit();
        this.gson = new Gson();
    }

    public static synchronized Settings getInstance(Context context) {
        if (instance == null) {
            instance = new Settings(context);
        }
        return instance;
    }

    // Connection Settings
    public ConnectionMode getConnectionMode() {
        String mode = prefs.getString(Constants.KEY_CONNECTION_MODE,
                ConnectionMode.MULTICAST.name());
        return ConnectionMode.valueOf(mode);
    }

    public void setConnectionMode(ConnectionMode mode) {
        editor.putString(Constants.KEY_CONNECTION_MODE, mode.name()).apply();
    }

    public String getZmqHost() {
        return prefs.getString(Constants.KEY_ZMQ_HOST, Constants.DEFAULT_ZMQ_HOST);
    }

    public void setZmqHost(String host) {
        editor.putString(Constants.KEY_ZMQ_HOST, host).apply();
        updateConnectionHistory(host, true);
    }


    public String getMulticastHost() {
        return prefs.getString(Constants.KEY_MULTICAST_HOST,
                Constants.DEFAULT_MULTICAST_HOST);
    }

    public void setMulticastHost(String host) {
        editor.putString(Constants.KEY_MULTICAST_HOST, host).apply();
        updateConnectionHistory(host, false);
    }

    // Dash things

    public void setSpoofDetectionEnabled(boolean enabled) {
        editor.putBoolean(Constants.KEY_SPOOF_DETECTION_ENABLED, enabled).apply();
    }

    public void setSerialConsoleEnabled(boolean enabled) {
        editor.putBoolean(Constants.KEY_SERIAL_CONSOLE_ENABLED, enabled).apply();
    }

    public void setSystemWarningsEnabled(boolean enabled) {
        editor.putBoolean(Constants.KEY_SYSTEM_WARNINGS_ENABLED, enabled).apply();
    }

    // Connection History
    public List<String> getZmqHostHistory() {
        String json = prefs.getString("zmq_host_history", "[]");
        Type type = new TypeToken<ArrayList<String>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public List<String> getMulticastHostHistory() {
        String json = prefs.getString("multicast_host_history", "[]");
        Type type = new TypeToken<ArrayList<String>>(){}.getType();
        return gson.fromJson(json, type);
    }

    private void updateConnectionHistory(String host, boolean isZmq) {
        List<String> history = isZmq ? getZmqHostHistory() : getMulticastHostHistory();

        // Remove if exists and add to front
        history.remove(host);
        history.add(0, host);

        // Keep only last 5 entries
        if (history.size() > Constants.MAX_HOST_HISTORY) {
            history = history.subList(0, Constants.MAX_HOST_HISTORY);
        }

        // Save updated history
        String key = isZmq ? "zmq_host_history" : "multicast_host_history";
        editor.putString(key, gson.toJson(history)).apply();
    }

    // Port Settings
    public int getMulticastPort() {
        return prefs.getInt("multicast_port", Constants.DEFAULT_MULTICAST_PORT);
    }

    public int getZmqTelemetryPort() {
        return prefs.getInt("zmq_telemetry_port", Constants.DEFAULT_ZMQ_TELEMETRY_PORT);
    }

    public int getZmqStatusPort() {
        return prefs.getInt("zmq_status_port", Constants.DEFAULT_ZMQ_STATUS_PORT);
    }

    public int getZmqSpectrumPort() {
        return prefs.getInt("zmq_spectrum_port", Constants.DEFAULT_ZMQ_SPECTRUM_PORT);
    }

    public boolean isLocationEstimationEnabled() {
        return prefs.getBoolean(Constants.KEY_ENABLE_LOCATION_ESTIMATION, true);
    }

    public void setLocationEstimationEnabled(boolean enabled) {
        editor.putBoolean(Constants.KEY_ENABLE_LOCATION_ESTIMATION, enabled).apply();
    }

    // Feature Flags
    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(Constants.KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        editor.putBoolean(Constants.KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean keepScreenOn() {
        return prefs.getBoolean(Constants.KEY_KEEP_SCREEN_ON, false);
    }

    public void setKeepScreenOn(boolean enabled) {
        editor.putBoolean(Constants.KEY_KEEP_SCREEN_ON, enabled).apply();
    }

    public boolean isListening() {
        return prefs.getBoolean(Constants.KEY_IS_LISTENING, false);
    }

    public void setListening(boolean listening) {
        editor.putBoolean(Constants.KEY_IS_LISTENING, listening).apply();
    }

    // Warning Thresholds
    public double getCpuWarningThreshold() {
        return prefs.getFloat("cpu_warning_threshold",
                (float)Constants.DEFAULT_CPU_WARNING_THRESHOLD);
    }

    public double getTempWarningThreshold() {
        return prefs.getFloat("temp_warning_threshold",
                (float)Constants.DEFAULT_TEMP_WARNING_THRESHOLD);
    }

    public double getMemoryWarningThreshold() {
        return prefs.getFloat("memory_warning_threshold",
                (float)Constants.DEFAULT_MEMORY_WARNING_THRESHOLD);
    }

    public double getPlutoTempThreshold() {
        return prefs.getFloat("pluto_temp_threshold",
                (float)Constants.DEFAULT_PLUTO_TEMP_THRESHOLD);
    }

    public double getZynqTempThreshold() {
        return prefs.getFloat("zynq_temp_threshold",
                (float)Constants.DEFAULT_ZYNQ_TEMP_THRESHOLD);
    }

    public int getProximityThreshold() {
        return prefs.getInt("proximity_threshold", Constants.DEFAULT_PROXIMITY_THRESHOLD);
    }

    public void setWarningThresholds(double cpu, double temp, double memory,
                                     double plutoTemp, double zynqTemp, int proximity) {
        editor.putFloat("cpu_warning_threshold", (float)cpu)
                .putFloat("temp_warning_threshold", (float)temp)
                .putFloat("memory_warning_threshold", (float)memory)
                .putFloat("pluto_temp_threshold", (float)plutoTemp)
                .putFloat("zynq_temp_threshold", (float)zynqTemp)
                .putInt("proximity_threshold", proximity)
                .apply();
    }

    // Warning Enables
    public boolean isSystemWarningsEnabled() {
        return prefs.getBoolean(Constants.KEY_SYSTEM_WARNINGS_ENABLED, true);
    }

    public boolean isSpoofDetectionEnabled() {
        return prefs.getBoolean(Constants.KEY_SPOOF_DETECTION_ENABLED, true);
    }

    public boolean isProximityWarningsEnabled() {
        return prefs.getBoolean(Constants.KEY_ENABLE_PROXIMITY_WARNINGS, true);
    }

    public boolean isSerialConsoleEnabled() {
        return prefs.getBoolean(Constants.KEY_SERIAL_CONSOLE_ENABLED, false);
    }
}