package com.rootdown.dragonsync.network;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.Manifest;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.utils.Settings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefensiveActionManager {
    private static final String TAG = "DefensiveActionManager";

    private final Context context;
    private final Settings settings;
    private final WifiManager wifiManager;
    private final Handler mainHandler;

    // Threat tracking
    private final Set<String> blockedMACs = ConcurrentHashMap.newKeySet();
    private final Set<String> blockedSSIDs = ConcurrentHashMap.newKeySet();
    private final Set<String> quarantinedDevices = ConcurrentHashMap.newKeySet();

    // Action cooldowns to prevent spam
    private final ConcurrentHashMap<String, Long> actionCooldowns = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 30000; // 30 seconds

    public DefensiveActionManager(Context context) {
        this.context = context;
        this.settings = Settings.getInstance(context);
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void executeDefensiveActions(CoTMessage message, List<RebelScanner.RebelDetection> detections) {
        for (RebelScanner.RebelDetection detection : detections) {
            String actionKey = detection.getDeviceId() + "_" + detection.getType();

            // Check cooldown
            if (isOnCooldown(actionKey)) {
                continue;
            }

            Log.w(TAG, "🛡️ Executing defensive actions for: " + detection.getType() + " (confidence: " + detection.getConfidence() + ")");

            // Execute actions based on threat type and confidence
            if (detection.getConfidence() > 0.9) {
                executeEmergencyActions(message, detection);
            } else if (detection.getConfidence() > 0.7) {
                executeHighThreatActions(message, detection);
            } else if (detection.getConfidence() > 0.5) {
                executeModerateActions(message, detection);
            }

            // Set cooldown
            actionCooldowns.put(actionKey, System.currentTimeMillis());
        }
    }

    private void executeEmergencyActions(CoTMessage message, RebelScanner.RebelDetection detection) {
        Log.e(TAG, "🚨 EMERGENCY ACTIONS TRIGGERED: " + detection.getType());

        switch (detection.getType()) {
            case DEAUTH_FLOOD:
            case KRACK_ATTACK_DEVICE:
            case DRAGONBLOOD_DEVICE:
                // Immediate network disconnect
                emergencyDisconnectWiFi(message, detection);
                alertUser("CRITICAL THREAT", "Immediate WiFi disconnection due to " + detection.getType());
                sendThreatTohound(message, detection, "EMERGENCY");
                // Block future connections to this network
                blockSSID(message.getSsid(), detection);
                blockMAC(message.getMac(), detection);
                break;

            case WIRELESS_PINEAPPLE_CLONES:
            case FAKE_CAPTIVE_PORTAL:
            case KARMA_ATTACK_DEVICE:
            case MANA_ATTACK_DEVICE:
            case ROGUE_ACCESS_POINT:
                // Block the rogue AP permanently
                blockSSID(message.getSsid(), detection);
                blockMAC(message.getMac(), detection);
                // If currently connected to this network, disconnect immediately
                if (isConnectedToNetwork(message.getSsid())) {
                    emergencyDisconnectWiFi(message, detection);
                }
                alertUser("🚫 ROGUE ACCESS POINT BLOCKED", "Blocked malicious AP: " + message.getSsid());
                sendThreatTohound(message, detection, "EMERGENCY");
                break;

            case OMG_CABLE:
            case BASH_BUNNY:
            case RUBBER_DUCKY:
                // Physical device threat - maximum alert
                triggerPhysicalThreatAlert(message, detection);
                sendThreatTohound(message, detection, "PHYSICAL_THREAT");
                // Consider this a security incident
                triggerSecurityIncident(message, detection);
                break;
        }
    }

    private void executeHighThreatActions(CoTMessage message, RebelScanner.RebelDetection detection) {
        Log.w(TAG, "⚠️ HIGH THREAT ACTIONS: " + detection.getType());

        switch (detection.getType()) {
            case ESP32_WROOM:
            case ESP32_CAM:
            case ESPRESSIF_ATTACK_DEVICE:
            case PWNAGOTCHI:
                // Quarantine and monitor - don't block immediately but watch closely
                quarantineDevice(message.getMac(), detection);
                enhanceMonitoring(message, detection);
                alertUser("🔍 Suspicious Device Detected", "ESP32/Attack device under monitoring: " + message.getMac());
                sendThreatTohound(message, detection, "HIGH");
                // Increase scan frequency for this device type
                requestEnhancedScanning(message.getMac());
                break;

            case SUSPICIOUS_OUI:
            case UNKNOWN_VENDOR:
            case CHINESE_KNOCKOFF:
                // Flag for enhanced monitoring but don't block
                flagForMonitoring(message.getMac(), detection);
                sendThreatTohound(message, detection, "MODERATE");
                Log.i(TAG, "Flagged suspicious vendor device: " + message.getMac());
                break;

            case PROXIMITY_THREAT:
                // Very close threat - increase alertness
                alertUser("⚠️ Close Range Threat", "High-power signal detected nearby: " + detection.getDetails());
                enhanceMonitoring(message, detection);
                sendThreatTohound(message, detection, "HIGH");
                break;
        }
    }

    private void executeModerateActions(CoTMessage message, RebelScanner.RebelDetection detection) {
        Log.i(TAG, "📊 MODERATE ACTIONS: " + detection.getType());

        // Log detailed information for analysis
        logThreatActivity(message, detection);
        sendThreatTohound(message, detection, "LOW");

        // Add to watchlist for pattern detection
        addToWatchlist(message.getMac(), detection);
    }

    private void emergencyDisconnectWiFi(CoTMessage message, RebelScanner.RebelDetection detection) {
        try {
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                Log.e(TAG, "🚨 EMERGENCY DISCONNECT: " + detection.getType());

                // First try graceful disconnect
                wifiManager.disconnect();

                // If still connected after 2 seconds, force disable WiFi
                mainHandler.postDelayed(() -> {
                    if (wifiManager.getConnectionInfo().getNetworkId() != -1) {
                        Log.w(TAG, "Graceful disconnect failed, forcing WiFi disable");
                        wifiManager.setWifiEnabled(false);

                        // Re-enable after 10 seconds but don't reconnect to the threat network
                        mainHandler.postDelayed(() -> {
                            wifiManager.setWifiEnabled(true);
                            alertUser("✅ WiFi Re-enabled", "Network threat cleared, WiFi restored. Threat network blocked.");
                        }, 10000);
                    }
                }, 2000);

                alertUser("🚨 EMERGENCY DISCONNECT", "WiFi disconnected due to " + detection.getType().name());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for WiFi control: " + e.getMessage());
            // Fall back to user notification if we can't control WiFi directly
            alertUser("⚠️ MANUAL ACTION REQUIRED", "Disconnect WiFi immediately! " + detection.getType() + " detected");
        }
    }

    private void blockSSID(String ssid, RebelScanner.RebelDetection detection) {
        if (ssid == null || ssid.isEmpty()) return;

        blockedSSIDs.add(ssid);

        // Remove from saved networks if it exists
        try {
            List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
            if (configs != null) {
                for (WifiConfiguration config : configs) {
                    if (ssid.equals(config.SSID.replace("\"", ""))) {
                        wifiManager.removeNetwork(config.networkId);
                        wifiManager.saveConfiguration();
                        Log.w(TAG, "✅ Removed malicious SSID from saved networks: " + ssid);
                        break;
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for network configuration: " + e.getMessage());
        }

        // Store in persistent blocklist
        settings.addBlockedSSID(ssid, detection.getType().toString());
        Log.e(TAG, "🚫 PERMANENTLY BLOCKED SSID: " + ssid);
    }

    private void blockMAC(String mac, RebelScanner.RebelDetection detection) {
        if (mac == null || mac.isEmpty()) return;

        blockedMACs.add(mac);
        settings.addBlockedMAC(mac, detection.getType().toString());
        Log.w(TAG, "🚫 Blocked MAC address: " + mac);

        // Also add to system-level MAC filtering if possible
        try {
            // Attempt to add to WiFi MAC filter (requires system permissions)
            Intent macFilterIntent = new Intent("com.rootdown.dragonsync.ADD_MAC_FILTER");
            macFilterIntent.putExtra("mac_address", mac);
            macFilterIntent.putExtra("reason", detection.getType().toString());
            context.sendBroadcast(macFilterIntent);
        } catch (Exception e) {
            Log.d(TAG, "System MAC filtering not available: " + e.getMessage());
        }
    }

    private void quarantineDevice(String mac, RebelScanner.RebelDetection detection) {
        if (mac == null || mac.isEmpty()) return;

        quarantinedDevices.add(mac);
        settings.addQuarantinedDevice(mac, detection.getType().toString());
        Log.w(TAG, "🔒 Quarantined device: " + mac);

        // Set up enhanced monitoring for quarantined device
        Intent quarantineIntent = new Intent("com.rootdown.dragonsync.DEVICE_QUARANTINED");
        quarantineIntent.putExtra("mac_address", mac);
        quarantineIntent.putExtra("threat_type", detection.getType().toString());
        quarantineIntent.putExtra("confidence", detection.getConfidence());
        quarantineIntent.putExtra("quarantine_time", System.currentTimeMillis());
        context.sendBroadcast(quarantineIntent);
    }

    private void sendThreatTohound(CoTMessage message, RebelScanner.RebelDetection detection, String severity) {
        Intent houndIntent = new Intent("com.rootdown.dragonsync.THREAT_REPORT");
        houndIntent.putExtra("threat_type", detection.getType().toString());
        houndIntent.putExtra("device_id", detection.getDeviceId());
        houndIntent.putExtra("mac", message.getMac());
        houndIntent.putExtra("ssid", message.getSsid());
        houndIntent.putExtra("rssi", message.getRssi());
        houndIntent.putExtra("confidence", detection.getConfidence());
        houndIntent.putExtra("severity", severity);
        houndIntent.putExtra("timestamp", System.currentTimeMillis());
        houndIntent.putExtra("details", detection.getDetails());

        if (message.getCoordinate() != null) {
            houndIntent.putExtra("latitude", message.getCoordinate().getLatitude());
            houndIntent.putExtra("longitude", message.getCoordinate().getLongitude());
        }

        context.sendBroadcast(houndIntent);
        Log.i(TAG, "📡 Sent threat report to hound: " + detection.getType() + " (severity: " + severity + ")");
    }

    private void alertUser(String title, String message) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "THREAT_ALERTS")
                    .setSmallIcon(R.drawable.ic_security_alert)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setColor(context.getResources().getColor(R.color.critical_red, null));

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

            // Check permission before showing notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission - cannot show alert: " + title);
                    // Fall back to system log for critical alerts
                    Log.e(TAG, "🚨 CRITICAL ALERT (No notification permission): " + title + " - " + message);
                    return;
                }
            }

            // Check if notifications are enabled by user
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
                Log.w(TAG, "🔔 User Alert: " + title + " - " + message);
            } else {
                Log.w(TAG, "Notifications disabled by user - cannot show alert: " + title);
                // Fall back to system log for critical alerts
                Log.e(TAG, "🚨 CRITICAL ALERT (Notifications disabled): " + title + " - " + message);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception showing notification: " + e.getMessage());
            // Fall back to system log
            Log.e(TAG, "🚨 CRITICAL ALERT (Security exception): " + title + " - " + message);
        } catch (Exception e) {
            Log.e(TAG, "Exception showing notification: " + e.getMessage());
            // Fall back to system log
            Log.e(TAG, "🚨 CRITICAL ALERT (Exception): " + title + " - " + message);
        }
    }

    private void triggerPhysicalThreatAlert(CoTMessage message, RebelScanner.RebelDetection detection) {
        Intent physicalThreatIntent = new Intent("com.rootdown.dragonsync.PHYSICAL_THREAT");
        physicalThreatIntent.putExtra("device_type", detection.getType().toString());
        physicalThreatIntent.putExtra("location", message.getCoordinate());
        context.sendBroadcast(physicalThreatIntent);

        alertUser("🚨 PHYSICAL THREAT DETECTED",
                "Malicious device nearby: " + detection.getType() + ". CHECK YOUR ENVIRONMENT IMMEDIATELY!");

        Log.e(TAG, "🚨 PHYSICAL THREAT ALERT: " + detection.getType());
    }

    private boolean isOnCooldown(String actionKey) {
        Long lastAction = actionCooldowns.get(actionKey);
        return lastAction != null && (System.currentTimeMillis() - lastAction) < ACTION_COOLDOWN_MS;
    }

    private void enhanceMonitoring(CoTMessage message, RebelScanner.RebelDetection detection) {
        Intent enhanceIntent = new Intent("com.rootdown.dragonsync.ENHANCE_MONITORING");
        enhanceIntent.putExtra("target_mac", message.getMac());
        enhanceIntent.putExtra("target_ssid", message.getSsid());
        enhanceIntent.putExtra("threat_type", detection.getType().toString());
        enhanceIntent.putExtra("enhanced_scan_interval", 500); // Scan every 500ms instead of normal interval
        enhanceIntent.putExtra("monitoring_duration", 300000); // Monitor for 5 minutes
        enhanceIntent.putExtra("alert_threshold", 0.6); // Lower threshold for alerts on this device
        context.sendBroadcast(enhanceIntent);

        Log.i(TAG, "🔍 Enhanced monitoring activated for: " + message.getMac());
    }

    private void flagForMonitoring(String mac, RebelScanner.RebelDetection detection) {
        if (mac == null || mac.isEmpty()) return;

        settings.addSuspiciousDevice(mac, detection.getType().toString());

        // Add to active monitoring list with lower priority
        Intent flagIntent = new Intent("com.rootdown.dragonsync.FLAG_FOR_MONITORING");
        flagIntent.putExtra("mac_address", mac);
        flagIntent.putExtra("suspicion_level", detection.getConfidence());
        flagIntent.putExtra("reason", detection.getType().toString());
        flagIntent.putExtra("flag_time", System.currentTimeMillis());
        context.sendBroadcast(flagIntent);

        Log.i(TAG, "🏃 Flagged for monitoring: " + mac + " (reason: " + detection.getType() + ")");
    }

    private void logThreatActivity(CoTMessage message, RebelScanner.RebelDetection detection) {
        // Create detailed threat log entry
        String logEntry = String.format(
                "THREAT_LOG | Time:%d | Type:%s | MAC:%s | SSID:%s | RSSI:%s | Confidence:%.2f | Details:%s | Location:%s",
                System.currentTimeMillis(),
                detection.getType(),
                message.getMac() != null ? message.getMac() : "N/A",
                message.getSsid() != null ? message.getSsid() : "N/A",
                message.getRssi() != null ? message.getRssi().toString() : "N/A",
                detection.getConfidence(),
                detection.getDetails(),
                message.getCoordinate() != null ?
                        message.getCoordinate().getLatitude() + "," + message.getCoordinate().getLongitude() : "N/A"
        );

        Log.i(TAG, logEntry);

        // Store in persistent threat log for analysis
        Intent logIntent = new Intent("com.rootdown.dragonsync.LOG_THREAT_ACTIVITY");
        logIntent.putExtra("log_entry", logEntry);
        logIntent.putExtra("threat_type", detection.getType().toString());
        logIntent.putExtra("mac_address", message.getMac());
        logIntent.putExtra("confidence", detection.getConfidence());
        context.sendBroadcast(logIntent);
    }

    private void addToWatchlist(String mac, RebelScanner.RebelDetection detection) {
        if (mac == null || mac.isEmpty()) return;

        settings.addSuspiciousDevice(mac, detection.getType().toString());

        // Add to active watchlist with monitoring parameters
        Intent watchlistIntent = new Intent("com.rootdown.dragonsync.ADD_TO_WATCHLIST");
        watchlistIntent.putExtra("mac_address", mac);
        watchlistIntent.putExtra("watch_reason", detection.getType().toString());
        watchlistIntent.putExtra("confidence", detection.getConfidence());
        watchlistIntent.putExtra("watch_duration", 3600000); // Watch for 1 hour
        watchlistIntent.putExtra("escalation_threshold", 0.7); // Escalate if confidence goes above 0.7
        context.sendBroadcast(watchlistIntent);

        Log.i(TAG, "👁️ Added to watchlist: " + mac + " (" + detection.getType() + ")");
    }

    private void requestEnhancedScanning(String mac) {
        Intent enhanceIntent = new Intent("com.rootdown.dragonsync.ENHANCE_SCANNING");
        enhanceIntent.putExtra("target_mac", mac);
        enhanceIntent.putExtra("scan_interval", 100); // Very fast scanning - 100ms intervals
        enhanceIntent.putExtra("scan_duration", 180000); // Scan for 3 minutes
        enhanceIntent.putExtra("priority", "HIGH");
        context.sendBroadcast(enhanceIntent);

        Log.i(TAG, "⚡ Enhanced scanning requested for: " + mac);
    }

    private void triggerSecurityIncident(CoTMessage message, RebelScanner.RebelDetection detection) {
        Intent incidentIntent = new Intent("com.rootdown.dragonsync.SECURITY_INCIDENT");
        incidentIntent.putExtra("incident_type", "PHYSICAL_THREAT");
        incidentIntent.putExtra("threat_device", detection.getType().toString());
        incidentIntent.putExtra("device_id", detection.getDeviceId());
        incidentIntent.putExtra("confidence", detection.getConfidence());
        incidentIntent.putExtra("timestamp", System.currentTimeMillis());
        incidentIntent.putExtra("details", detection.getDetails());

        if (message.getCoordinate() != null) {
            incidentIntent.putExtra("latitude", message.getCoordinate().getLatitude());
            incidentIntent.putExtra("longitude", message.getCoordinate().getLongitude());
        }

        if (message.getMac() != null) {
            incidentIntent.putExtra("mac_address", message.getMac());
        }

        if (message.getRssi() != null) {
            incidentIntent.putExtra("signal_strength", message.getRssi());
            // Estimate proximity based on signal strength
            incidentIntent.putExtra("estimated_distance", estimateDistance(message.getRssi()));
        }

        context.sendBroadcast(incidentIntent);

        Log.e(TAG, "🚨 SECURITY INCIDENT TRIGGERED: " + detection.getType() + " | Device: " + detection.getDeviceId());

        // Also trigger maximum priority user notification
        alertUser("🚨 SECURITY BREACH",
                "PHYSICAL ATTACK DEVICE DETECTED! " + detection.getType() + " is within range. Take immediate action!");
    }

    private double estimateDistance(Integer rssi) {
        if (rssi == null) return -1;

        // Rough distance estimation based on RSSI
        // Formula: distance = 10^((Tx Power - RSSI) / (10 * n))
        // Assuming Tx Power = -20 dBm, path loss exponent n = 2
        double txPower = -20.0;
        double pathLossExponent = 2.0;

        double distance = Math.pow(10, (txPower - rssi) / (10 * pathLossExponent));
        return Math.round(distance * 100.0) / 100.0; // Round to 2 decimal places
    }

    private boolean isConnectedToNetwork(String ssid) {
        if (wifiManager == null || ssid == null) return false;

        try {
            String currentSSID = wifiManager.getConnectionInfo().getSSID();
            if (currentSSID != null) {
                // Remove quotes from SSID if present
                currentSSID = currentSSID.replace("\"", "");
                return ssid.equals(currentSSID);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for WiFi info: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error checking current network: " + e.getMessage());
        }

        return false;
    }

    // Threat intelligence methods
    public boolean isMACBlocked(String mac) {
        return blockedMACs.contains(mac) || settings.getBlockedMACs().stream()
                .anyMatch(entry -> entry.startsWith(mac + "|"));
    }

    public boolean isSSIDBlocked(String ssid) {
        return blockedSSIDs.contains(ssid) || settings.getBlockedSSIDs().stream()
                .anyMatch(entry -> entry.startsWith(ssid + "|"));
    }

    public boolean isMACQuarantined(String mac) {
        return quarantinedDevices.contains(mac) || settings.getQuarantinedDevices().stream()
                .anyMatch(entry -> entry.startsWith(mac + "|"));
    }

    public boolean isMACOnWatchlist(String mac) {
        return settings.getSuspiciousDevices().stream()
                .anyMatch(entry -> entry.startsWith(mac + "|"));
    }

    // Cleanup methods
    public void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        long expiryTime = 24 * 60 * 60 * 1000; // 24 hours

        // Clean up action cooldowns
        actionCooldowns.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > expiryTime);

        Log.d(TAG, "Cleaned up expired defensive action entries");
    }

    public void resetDefensiveState() {
        blockedMACs.clear();
        blockedSSIDs.clear();
        quarantinedDevices.clear();
        actionCooldowns.clear();

        Log.w(TAG, "🔄 Defensive state reset - all temporary blocks cleared");
    }

    // Statistics and reporting
    public DefensiveActionStats getStats() {
        return new DefensiveActionStats(
                blockedMACs.size(),
                blockedSSIDs.size(),
                quarantinedDevices.size(),
                actionCooldowns.size()
        );
    }

    public static class DefensiveActionStats {
        public final int blockedMACCount;
        public final int blockedSSIDCount;
        public final int quarantinedDeviceCount;
        public final int recentActionCount;

        public DefensiveActionStats(int blockedMACs, int blockedSSIDs, int quarantined, int recentActions) {
            this.blockedMACCount = blockedMACs;
            this.blockedSSIDCount = blockedSSIDs;
            this.quarantinedDeviceCount = quarantined;
            this.recentActionCount = recentActions;
        }

        @Override
        public String toString() {
            return String.format("Defensive Stats: %d MACs blocked, %d SSIDs blocked, %d devices quarantined, %d recent actions",
                    blockedMACCount, blockedSSIDCount, quarantinedDeviceCount, recentActionCount);
        }
    }

    // Public getters for UI and external access
    public Set<String> getBlockedMACs() {
        Set<String> all = new HashSet<>(blockedMACs);
        // Add persistent blocked MACs from settings
        settings.getBlockedMACs().forEach(entry -> {
            String[] parts = entry.split("\\|");
            if (parts.length > 0) all.add(parts[0]);
        });
        return all;
    }

    public Set<String> getBlockedSSIDs() {
        Set<String> all = new HashSet<>(blockedSSIDs);
        // Add persistent blocked SSIDs from settings
        settings.getBlockedSSIDs().forEach(entry -> {
            String[] parts = entry.split("\\|");
            if (parts.length > 0) all.add(parts[0]);
        });
        return all;
    }

    public Set<String> getQuarantinedDevices() {
        Set<String> all = new HashSet<>(quarantinedDevices);
        // Add persistent quarantined devices from settings
        settings.getQuarantinedDevices().forEach(entry -> {
            String[] parts = entry.split("\\|");
            if (parts.length > 0) all.add(parts[0]);
        });
        return all;
    }

    public Set<String> getWatchlist() {
        Set<String> watchlist = new HashSet<>();
        settings.getSuspiciousDevices().forEach(entry -> {
            String[] parts = entry.split("\\|");
            if (parts.length > 0) watchlist.add(parts[0]);
        });
        return watchlist;
    }

    // Emergency override methods for manual control
    public void emergencyBlockMAC(String mac, String reason) {
        if (mac != null && !mac.isEmpty()) {
            blockedMACs.add(mac);
            settings.addBlockedMAC(mac, "MANUAL_OVERRIDE_" + reason);
            Log.e(TAG, "🚨 EMERGENCY MAC BLOCK: " + mac + " (Reason: " + reason + ")");

            Intent emergencyIntent = new Intent("com.rootdown.dragonsync.EMERGENCY_MAC_BLOCK");
            emergencyIntent.putExtra("mac_address", mac);
            emergencyIntent.putExtra("reason", reason);
            context.sendBroadcast(emergencyIntent);
        }
    }

    public void emergencyBlockSSID(String ssid, String reason) {
        if (ssid != null && !ssid.isEmpty()) {
            blockedSSIDs.add(ssid);
            settings.addBlockedSSID(ssid, "MANUAL_OVERRIDE_" + reason);
            Log.e(TAG, "🚨 EMERGENCY SSID BLOCK: " + ssid + " (Reason: " + reason + ")");

            Intent emergencyIntent = new Intent("com.rootdown.dragonsync.EMERGENCY_SSID_BLOCK");
            emergencyIntent.putExtra("ssid", ssid);
            emergencyIntent.putExtra("reason", reason);
            context.sendBroadcast(emergencyIntent);
        }
    }

    public void removeFromBlocklist(String identifier, boolean isMAC) {
        if (isMAC) {
            blockedMACs.remove(identifier);
            Log.i(TAG, "Removed MAC from blocklist: " + identifier);
        } else {
            blockedSSIDs.remove(identifier);
            Log.i(TAG, "Removed SSID from blocklist: " + identifier);
        }
    }
}