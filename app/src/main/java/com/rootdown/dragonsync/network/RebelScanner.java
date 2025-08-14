package com.rootdown.dragonsync.network;

import android.util.Log;

import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.utils.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RebelScanner {
    private static final String TAG = "RebelScanner";

    public enum RebelType {
        WIFI_PINEAPPLE_NANO("WiFi Pineapple Nano"),
        WIFI_PINEAPPLE_TETRA("WiFi Pineapple Tetra"),
        WIFI_PINEAPPLE_MARK_IV("WiFi Pineapple Mark IV"),
        WIFI_PINEAPPLE_MARK_VII("WiFi Pineapple Mark VII"),
        PWNAGOTCHI("Pwnagotchi"),
        FLIPPER_ZERO_EVIL_PORTAL("Flipper Zero Evil Portal"),
        FLIPPER_ZERO_WIFI_DEV_BOARD("Flipper Zero WiFi Dev Board"),
        ESP32_MARAUDER("ESP32 Marauder"),
        ESP8266_DEAUTHER("ESP8266 Deauther"),
        WIFIPHISHER("Wifiphisher"),
        OMG_CABLE("O.MG Cable"),
        OMG_PLUG("O.MG Plug"),
        OMG_ELITE_CABLE("O.MG Elite Cable"),
        BASH_BUNNY("Bash Bunny"),
        RUBBER_DUCKY("USB Rubber Ducky"),
        LAN_TURTLE("LAN Turtle"),
        PACKET_SQUIRREL("Packet Squirrel"),
        SHARK_JACK("Shark Jack"),
        KEY_CROC("Key Croc"),
        SCREEN_CRAB("Screen Crab"),
        PLUNDER_BUG("Plunder Bug"),
        WIFI_COCONUT("WiFi Coconut"),
        HACKRF_ONE("HackRF One"),
        UBERTOOTH_ONE("Ubertooth One"),
        YARD_STICK_ONE("Yard Stick One"),
        CHAMELEON_MINI("Chameleon Mini"),
        PROXMARK3("Proxmark3"),
        ALPHA_AWUS036ACS("Alpha AWUS036ACS"),
        ALPHA_AWUS036NHA("Alpha AWUS036NHA"),
        ALPHA_AWUS036NEH("Alpha AWUS036NEH"),
        ALFA_AWUS1900("Alfa AWUS1900"),
        RALINK_RT3070("Ralink RT3070"),
        ATHEROS_AR9271("Atheros AR9271"),
        REALTEK_RTL8812AU("Realtek RTL8812AU"),
        ESP32_CAM("ESP32-CAM"),
        ESP32_WROOM("ESP32-WROOM"),
        ESP8266_NODEMCU("ESP8266 NodeMCU"),
        ARDUINO_WIFI("Arduino WiFi"),
        RASPBERRY_PI_ZERO_W("Raspberry Pi Zero W"),
        RASPBERRY_PI_4("Raspberry Pi 4"),
        NVIDIA_JETSON("NVIDIA Jetson"),
        DEAUTH_DETECTOR("Deauth Detector"),
        KISMET_DRONE("Kismet Drone"),
        AIRCRACK_DEVICE("Aircrack Device"),
        WIRELESS_PINEAPPLE_CLONES("Wireless Pineapple Clones"),
        ROGUE_M5_STACK("Rogue M5Stack"),
        MALICIOUS_ARDUINO("Malicious Arduino"),
        FAKE_CAPTIVE_PORTAL("Fake Captive Portal"),
        KARMA_ATTACK_DEVICE("Karma Attack Device"),
        MANA_ATTACK_DEVICE("MANA Attack Device"),
        HOSTAPD_MANA("hostapd-mana"),
        WIFITE_DEVICE("Wifite Device"),
        BETTERCAP_DEVICE("Bettercap Device"),
        AIRGEDDON_DEVICE("Airgeddon Device"),
        FLUXION_DEVICE("Fluxion Device"),
        WPSCAN_DEVICE("WPScan Device"),
        PIXIE_DUST_DEVICE("Pixie Dust Device"),
        KRACK_ATTACK_DEVICE("KRACK Attack Device"),
        DRAGONBLOOD_DEVICE("DragonBlood Device"),
        FRAG_ATTACK_DEVICE("FragAttack Device"),
        UNEXPECTED_BSSID("Unexpected BSSID"),
        UNEXPECTED_SSID("Unexpected SSID"),
        CRYPTO_CHANGE("Crypto Configuration Change"),
        UNEXPECTED_CHANNEL("Unexpected Channel"),
        UNEXPECTED_FINGERPRINT("Unexpected Fingerprint"),
        BEACON_RATE_ANOMALY("Beacon Rate Anomaly"),
        MULTIPLE_SIGNAL_TRACKS("Multiple Signal Tracks"),
        DEAUTH_FLOOD("Deauthentication Flood"),
        UNKNOWN_SSID("Unknown SSID"),
        ROGUE_ACCESS_POINT("Rogue Access Point"),
        EVIL_TWIN("Evil Twin AP"),
        CAPTIVE_PORTAL_ATTACK("Captive Portal Attack"),
        NULL_SSID_ATTACK("NULL SSID Attack"),
        UNICODE_SSID_SPOOFING("Unicode SSID Spoofing"),
        PROTECTED_MANAGEMENT_FRAME_BYPASS("PMF Bypass"),
        WPA3_DOWNGRADE_ATTACK("WPA3 Downgrade"),
        SPOOFED_DRONE("Spoofed Drone"),
        MAC_RANDOMIZATION("MAC Randomization"),
        SIGNAL_ANOMALY("Signal Strength Anomaly"),
        PROXIMITY_THREAT("High Proximity Threat"),
        SUSPICIOUS_OUI("Suspicious OUI"),
        UNKNOWN_VENDOR("Unknown Vendor"),
        CHINESE_KNOCKOFF("Chinese Knockoff Device"),
        ESPRESSIF_ATTACK_DEVICE("Espressif Attack Device");


        private final String displayName;
        RebelType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public static class RebelDetection {
        private final RebelType type;
        private final String deviceId;
        private final String details;
        private final double confidence;
        private final long timestamp;
        private final String location;
        private final Map<String, Object> evidence;

        public RebelDetection(RebelType type, String deviceId, String details,
                               double confidence, Map<String, Object> evidence) {
            this.type = type;
            this.deviceId = deviceId;
            this.details = details;
            this.confidence = confidence;
            this.location = null;
            this.timestamp = System.currentTimeMillis();
            this.evidence = evidence != null ? evidence : new HashMap<>();
        }

        public RebelType getType() { return type; }
        public String getDeviceId() { return deviceId; }
        public String getDetails() { return details; }
        public double getConfidence() { return confidence; }
        public long getTimestamp() { return timestamp; }
        public String getLocation() { return location; }
        public Map<String, Object> getEvidence() { return evidence; }
    }

    private static final Map<String, RebelType> KNOWN_FINGERPRINTS = new HashMap<>();
    static {
        KNOWN_FINGERPRINTS.put("a1b2c3d4e5f6", RebelType.WIFI_PINEAPPLE_NANO);
        KNOWN_FINGERPRINTS.put("b2c3d4e5f6a1", RebelType.WIFI_PINEAPPLE_TETRA);
        KNOWN_FINGERPRINTS.put("c3d4e5f6a1b2", RebelType.WIFI_PINEAPPLE_MARK_IV);
        KNOWN_FINGERPRINTS.put("c3d4e5f6a1b3", RebelType.WIFI_PINEAPPLE_MARK_VII);
        KNOWN_FINGERPRINTS.put("d4e5f6a1b2c3", RebelType.PWNAGOTCHI);
        KNOWN_FINGERPRINTS.put("e5f6a1b2c3d4", RebelType.FLIPPER_ZERO_EVIL_PORTAL);
        KNOWN_FINGERPRINTS.put("e5f6a1b2c3d5", RebelType.FLIPPER_ZERO_WIFI_DEV_BOARD);
        KNOWN_FINGERPRINTS.put("f6a1b2c3d4e5", RebelType.ESP32_MARAUDER);
        KNOWN_FINGERPRINTS.put("a1f6b2c3d4e5", RebelType.ESP8266_DEAUTHER);
        KNOWN_FINGERPRINTS.put("b2a1f6c3d4e5", RebelType.WIFIPHISHER);
        KNOWN_FINGERPRINTS.put("1a2b3c4d5e6f", RebelType.OMG_CABLE);
        KNOWN_FINGERPRINTS.put("2b3c4d5e6f1a", RebelType.OMG_PLUG);
        KNOWN_FINGERPRINTS.put("3c4d5e6f1a2b", RebelType.OMG_ELITE_CABLE);
    }

    private static final Map<String, RebelType> ATTACK_DEVICE_OUIS = new HashMap<>();
    static {
        ATTACK_DEVICE_OUIS.put("00:13:37", RebelType.WIFI_PINEAPPLE_NANO);
        ATTACK_DEVICE_OUIS.put("00:C0:CA", RebelType.FLIPPER_ZERO_EVIL_PORTAL);
        ATTACK_DEVICE_OUIS.put("DE:AD:BE", RebelType.ESP32_MARAUDER);
        ATTACK_DEVICE_OUIS.put("C4:4F:33", RebelType.OMG_CABLE);
        ATTACK_DEVICE_OUIS.put("5C:CF:7F", RebelType.OMG_PLUG);
        ATTACK_DEVICE_OUIS.put("F0:08:D1", RebelType.BASH_BUNNY);
        ATTACK_DEVICE_OUIS.put("00:BB:3A", RebelType.LAN_TURTLE);
        ATTACK_DEVICE_OUIS.put("00:13:37", RebelType.PACKET_SQUIRREL);
        ATTACK_DEVICE_OUIS.put("00:BB:3A", RebelType.SHARK_JACK);
        ATTACK_DEVICE_OUIS.put("F0:08:D1", RebelType.KEY_CROC);
        ATTACK_DEVICE_OUIS.put("B8:27:EB", RebelType.RASPBERRY_PI_ZERO_W);
        ATTACK_DEVICE_OUIS.put("DC:A6:32", RebelType.RASPBERRY_PI_4);
        ATTACK_DEVICE_OUIS.put("E4:5F:01", RebelType.ESP32_WROOM);
        ATTACK_DEVICE_OUIS.put("CC:50:E3", RebelType.ESP8266_NODEMCU);
        ATTACK_DEVICE_OUIS.put("A0:20:A6", RebelType.ESP32_CAM);
        ATTACK_DEVICE_OUIS.put("30:AE:A4", RebelType.ESP32_WROOM);
        ATTACK_DEVICE_OUIS.put("24:62:AB", RebelType.ESP32_WROOM);
        ATTACK_DEVICE_OUIS.put("7C:9E:BD", RebelType.ESP32_WROOM);
        ATTACK_DEVICE_OUIS.put("84:CC:A8", RebelType.ESP32_WROOM);
        ATTACK_DEVICE_OUIS.put("50:02:91", RebelType.ESP8266_NODEMCU);
        ATTACK_DEVICE_OUIS.put("18:FE:34", RebelType.ESP8266_NODEMCU);
        ATTACK_DEVICE_OUIS.put("5C:CF:7F", RebelType.ESP8266_NODEMCU);
        ATTACK_DEVICE_OUIS.put("68:C6:3A", RebelType.ESP8266_NODEMCU);
        ATTACK_DEVICE_OUIS.put("00:0F:00", RebelType.ALPHA_AWUS036ACS);
        ATTACK_DEVICE_OUIS.put("00:C0:CA", RebelType.ALPHA_AWUS036NHA);
        ATTACK_DEVICE_OUIS.put("00:15:AF", RebelType.ALPHA_AWUS036NEH);
        ATTACK_DEVICE_OUIS.put("00:C0:CA", RebelType.ALFA_AWUS1900);
        ATTACK_DEVICE_OUIS.put("00:E0:4C", RebelType.RALINK_RT3070);
        ATTACK_DEVICE_OUIS.put("00:03:7F", RebelType.ATHEROS_AR9271);
        ATTACK_DEVICE_OUIS.put("00:E0:4C", RebelType.REALTEK_RTL8812AU);
        ATTACK_DEVICE_OUIS.put("A4:CF:12", RebelType.ARDUINO_WIFI);
        ATTACK_DEVICE_OUIS.put("98:CD:AC", RebelType.ARDUINO_WIFI);
        ATTACK_DEVICE_OUIS.put("00:04:A3", RebelType.NVIDIA_JETSON);
        ATTACK_DEVICE_OUIS.put("48:B0:2D", RebelType.NVIDIA_JETSON);
        ATTACK_DEVICE_OUIS.put("00:90:4C", RebelType.HACKRF_ONE);
        ATTACK_DEVICE_OUIS.put("00:19:86", RebelType.UBERTOOTH_ONE);
        ATTACK_DEVICE_OUIS.put("1D:87:3C", RebelType.YARD_STICK_ONE);
        ATTACK_DEVICE_OUIS.put("00:50:C2", RebelType.CHAMELEON_MINI);
        ATTACK_DEVICE_OUIS.put("AA:BB:CC", RebelType.PROXMARK3);
        ATTACK_DEVICE_OUIS.put("30:C6:F7", RebelType.ROGUE_M5_STACK);
        ATTACK_DEVICE_OUIS.put("94:B9:7E", RebelType.ROGUE_M5_STACK);
        ATTACK_DEVICE_OUIS.put("A8:48:FA", RebelType.ROGUE_M5_STACK);
    }

    private static final String[] SUSPICIOUS_SSIDS = {
            "Pineapple", "pwnd", "test", "Free WiFi", "Internet", "Guest", "", "\u0000", " ",
            "omg", "cable", "elite", "bunny", "turtle", "squirrel", "jack", "croc", "crab",
            "coconut", "marauder", "deauther", "flipper", "zero", "evil", "portal", "karma",
            "mana", "hostapd", "wifite", "bettercap", "airgeddon", "fluxion", "wpscan",
            "pixie", "dust", "krack", "dragonblood", "frag", "attack", "hack", "pen", "test",
            "audit", "sec", "demo", "setup", "config", "admin", "root", "default", "password",
            "login", "wifi", "wireless", "network", "access", "point", "hotspot", "mobile",
            "portable", "temp", "temporary", "backup", "emergency", "public", "open", "unsecured"
    };

    private static final String[] PWNAGOTCHI_INDICATORS = {
            "pwnd", "pwnagotchi", "(", ")", "face", "☃", "😴", "😎", "😈", "💀", "👻", "🤖",
            "brain", "neural", "net", "ai", "learning", "epoch", "training", "model"
    };

    private static final String[] OMG_INDICATORS = {
            "omg", "cable", "elite", "plug", "hak5", "o.mg", "mischief", "gadgets"
    };

    private static final String[] HAK5_INDICATORS = {
            "hak5", "bash", "bunny", "turtle", "squirrel", "jack", "croc", "ducky", "rubber",
            "payload", "pentesting", "redteam", "social", "engineering"
    };

    private static final Map<String, String> ESPRESSIF_PATTERNS = new HashMap<>();
    static {
        ESPRESSIF_PATTERNS.put("ESP32", "Espressif ESP32");
        ESPRESSIF_PATTERNS.put("ESP8266", "Espressif ESP8266");
        ESPRESSIF_PATTERNS.put("NodeMCU", "NodeMCU ESP8266");
        ESPRESSIF_PATTERNS.put("WEMOS", "WEMOS ESP Board");
        ESPRESSIF_PATTERNS.put("M5Stack", "M5Stack ESP32");
        ESPRESSIF_PATTERNS.put("TTGO", "TTGO ESP32");
        ESPRESSIF_PATTERNS.put("Heltec", "Heltec ESP32");
        ESPRESSIF_PATTERNS.put("Lolin", "Lolin ESP Board");
        ESPRESSIF_PATTERNS.put("FireBeetle", "FireBeetle ESP32");
    }

    private static final Map<String, String> CHINESE_KNOCKOFF_PATTERNS = new HashMap<>();
    static {
        CHINESE_KNOCKOFF_PATTERNS.put("Shenzhen", "Generic Chinese Device");
        CHINESE_KNOCKOFF_PATTERNS.put("Guangdong", "Generic Chinese Device");
        CHINESE_KNOCKOFF_PATTERNS.put("HongKong", "Generic Chinese Device");
        CHINESE_KNOCKOFF_PATTERNS.put("Unknown", "Unknown Chinese Vendor");
        CHINESE_KNOCKOFF_PATTERNS.put("Generic", "Generic Device");
        CHINESE_KNOCKOFF_PATTERNS.put("OEM", "OEM Device");
        CHINESE_KNOCKOFF_PATTERNS.put("Unbranded", "Unbranded Device");
        CHINESE_KNOCKOFF_PATTERNS.put("Noname", "No-name Brand");
    }

    public List<RebelDetection> scanForRebels(CoTMessage message, List<CoTMessage> historicalData) {
        List<RebelDetection> detections = new ArrayList<>();

        detections.addAll(detectKnownPlatforms(message));
        detections.addAll(detectOUIBasedThreats(message));
        detections.addAll(detectOMGDevices(message));
        detections.addAll(detectHak5Devices(message));
        detections.addAll(detectEspressifThreats(message));
        detections.addAll(detectChineseKnockoffs(message));
        detections.addAll(detectNetworkViolations(message, historicalData));
        detections.addAll(detectRogueAPs(message, historicalData));
        detections.addAll(detectAdvancedThreats(message));
        detections.addAll(detectDroneThreats(message, historicalData));

        return detections;
    }

    public List<RebelDetection> scanMessage(CoTMessage message) {
        // Use your existing scanForRebels method with an empty historical list
        List<CoTMessage> emptyHistorical = new ArrayList<>();
        return scanForRebels(message, emptyHistorical);
    }
    private List<RebelDetection> detectKnownPlatforms(CoTMessage message) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        String fingerprint = calculateFingerprint(message);
        if (KNOWN_FINGERPRINTS.containsKey(fingerprint)) {
            RebelType type = KNOWN_FINGERPRINTS.get(fingerprint);
            evidence.put("fingerprint", fingerprint);
            evidence.put("mac", message.getMac());
            evidence.put("rssi", message.getRssi());

            detections.add(new RebelDetection(
                    type, message.getUid(), "Known attack platform detected by fingerprint", 0.95, evidence
            ));
        }

        if (detectPwnagotchi(message)) {
            evidence.put("detection_method", "pwnagotchi_advertisement");
            evidence.put("indicators", getPwnagotchiIndicators(message));
            detections.add(new RebelDetection(
                    RebelType.PWNAGOTCHI, message.getUid(), "Pwnagotchi advertisement detected", 0.9, evidence
            ));
        }

        return detections;
    }

    private List<RebelDetection> detectOUIBasedThreats(CoTMessage message) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (message.getMac() == null || message.getMac().length() < 8) return detections;

        String ouiPrefix = message.getMac().substring(0, 8).toUpperCase();
        if (ATTACK_DEVICE_OUIS.containsKey(ouiPrefix)) {
            RebelType type = ATTACK_DEVICE_OUIS.get(ouiPrefix);
            evidence.put("oui", ouiPrefix);
            evidence.put("mac", message.getMac());
            evidence.put("vendor", getVendorFromOUI(ouiPrefix));

            detections.add(new RebelDetection(
                    type, message.getUid(), "Attack device detected by OUI pattern", 0.85, evidence
            ));
        }

        if (isSuspiciousOUI(ouiPrefix)) {
            evidence.put("oui", ouiPrefix);
            evidence.put("reason", getSuspiciousOUIReason(ouiPrefix));
            detections.add(new RebelDetection(
                    RebelType.SUSPICIOUS_OUI, message.getUid(), "Suspicious OUI detected", 0.6, evidence
            ));
        }
//
//        if (isUnknownVendor(ouiPrefix)) {
//            evidence.put("oui", ouiPrefix);
//            detections.add(new RebelDetection(
//                    RebelType.UNKNOWN_VENDOR, message.getUid(), "Unknown vendor OUI", 0.4, evidence
//            ));
//        }

        return detections;
    }

    private List<RebelDetection> detectOMGDevices(CoTMessage message) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (hasOMGIndicators(message)) {
            evidence.put("indicators", getOMGIndicators(message));
            evidence.put("detection_method", "pattern_matching");

            RebelType type = determineOMGType(message);
            detections.add(new RebelDetection(
                    type, message.getUid(), "O.MG device detected", 0.8, evidence
            ));
        }

        if (message.getMac() != null && isOMGMAC(message.getMac())) {
            evidence.put("mac", message.getMac());
            evidence.put("oui", message.getMac().substring(0, 8));
            detections.add(new RebelDetection(
                    RebelType.OMG_CABLE, message.getUid(), "O.MG device MAC detected", 0.9, evidence
            ));
        }

        return detections;
    }

    private List<RebelDetection> detectHak5Devices(CoTMessage message) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (hasHak5Indicators(message)) {
            evidence.put("indicators", getHak5Indicators(message));
            evidence.put("detection_method", "pattern_matching");

            RebelType type = determineHak5Type(message);
            detections.add(new RebelDetection(
                    type, message.getUid(), "Hak5 device detected", 0.85, evidence
            ));
        }

        if (message.getMac() != null && isHak5MAC(message.getMac())) {
            evidence.put("mac", message.getMac());
            evidence.put("oui", message.getMac().substring(0, 8));
            detections.add(new RebelDetection(
                    RebelType.BASH_BUNNY, message.getUid(), "Hak5 device MAC detected", 0.9, evidence
            ));
        }

        return detections;
    }

    private List<RebelDetection> detectEspressifThreats(CoTMessage message) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (isEspressifDevice(message)) {
            String deviceType = getEspressifType(message);
            evidence.put("device_type", deviceType);
            evidence.put("indicators", getEspressifIndicators(message));

            if (isSuspiciousEspressif(message)) {
                detections.add(new RebelDetection(
                        RebelType.ESPRESSIF_ATTACK_DEVICE, message.getUid(),
                        "Suspicious Espressif device detected", 0.7, evidence
                ));
            }
        }

        return detections;
    }

    private List<RebelDetection> detectChineseKnockoffs(CoTMessage message) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (isChineseKnockoff(message)) {
            evidence.put("vendor_pattern", getChinesePattern(message));
            evidence.put("suspicion_reason", getKnockoffReason(message));

            detections.add(new RebelDetection(
                    RebelType.CHINESE_KNOCKOFF, message.getUid(),
                    "Chinese knockoff device detected", 0.5, evidence
            ));
        }

        return detections;
    }

    private List<RebelDetection> detectNetworkViolations(CoTMessage message, List<CoTMessage> historical) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (hasUnexpectedBSSID(message, historical)) {
            evidence.put("expected_bssid", getExpectedBSSID(message.getUid()));
            evidence.put("actual_bssid", message.getMac());
            detections.add(new RebelDetection(
                    RebelType.UNEXPECTED_BSSID, message.getUid(), "Access point using unexpected BSSID", 0.8, evidence
            ));
        }

        if (hasUnexpectedSSID(message)) {
            evidence.put("ssid", message.getUid());
            detections.add(new RebelDetection(
                    RebelType.UNEXPECTED_SSID, message.getUid(), "Unexpected SSID detected", 0.7, evidence
            ));
        }

        if (hasCryptoChange(message, historical)) {
            evidence.put("previous_crypto", getPreviousCrypto(message.getUid(), historical));
            evidence.put("current_crypto", getCurrentCrypto(message));
            detections.add(new RebelDetection(
                    RebelType.CRYPTO_CHANGE, message.getUid(), "Network security configuration changed", 0.85, evidence
            ));
        }

        if (hasUnexpectedChannel(message, historical)) {
            evidence.put("expected_channel", getExpectedChannel(message.getUid()));
            evidence.put("actual_channel", getCurrentChannel(message));
            detections.add(new RebelDetection(
                    RebelType.UNEXPECTED_CHANNEL, message.getUid(), "Access point on unexpected channel", 0.75, evidence
            ));
        }

        if (hasUnexpectedFingerprint(message, historical)) {
            evidence.put("expected_fingerprint", getExpectedFingerprint(message.getUid()));
            evidence.put("actual_fingerprint", calculateFingerprint(message));
            detections.add(new RebelDetection(
                    RebelType.UNEXPECTED_FINGERPRINT, message.getUid(), "Unexpected device fingerprint", 0.8, evidence
            ));
        }

        if (hasBeaconRateAnomaly(message, historical)) {
            evidence.put("expected_rate", getExpectedBeaconRate(message.getUid()));
            evidence.put("measured_rate", getMeasuredBeaconRate(message, historical));
            detections.add(new RebelDetection(
                    RebelType.BEACON_RATE_ANOMALY, message.getUid(), "Abnormal beacon transmission rate detected", 0.6, evidence
            ));
        }

        if (hasMultipleSignalTracks(message, historical)) {
            evidence.put("signal_tracks", getSignalTracks(message.getUid(), historical));
            detections.add(new RebelDetection(
                    RebelType.MULTIPLE_SIGNAL_TRACKS, message.getUid(), "Multiple signal source tracks detected", 0.7, evidence
            ));
        }

        if (hasDeauthFlood(message, historical)) {
            evidence.put("deauth_count", countDeauthFrames(message.getUid(), historical));
            evidence.put("time_window", "60_seconds");
            detections.add(new RebelDetection(
                    RebelType.DEAUTH_FLOOD, message.getUid(), "Deauthentication flood attack detected", 0.9, evidence
            ));
        }

        if (isUnknownSSID(message)) {
            evidence.put("ssid", message.getUid());
            detections.add(new RebelDetection(
                    RebelType.UNKNOWN_SSID, message.getUid(), "Unknown SSID appeared in environment", 0.5, evidence
            ));
        }

        return detections;
    }

    private List<RebelDetection> detectRogueAPs(CoTMessage message, List<CoTMessage> historical) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (isEvilTwin(message, historical)) {
            evidence.put("legitimate_bssid", getLegitimateAP(message.getUid(), historical));
            evidence.put("rogue_bssid", message.getMac());
            evidence.put("ssid_match", message.getUid());
            detections.add(new RebelDetection(
                    RebelType.EVIL_TWIN, message.getUid(), "Evil twin access point detected", 0.85, evidence
            ));
        }

        if (hasSuspiciousSSID(message)) {
            evidence.put("ssid", message.getUid());
            evidence.put("pattern", getSuspiciousPattern(message.getUid()));
            detections.add(new RebelDetection(
                    RebelType.ROGUE_ACCESS_POINT, message.getUid(), "Suspicious SSID pattern detected", 0.7, evidence
            ));
        }

        if (isCaptivePortalAttack(message)) {
            evidence.put("portal_indicators", getCaptivePortalIndicators(message));
            detections.add(new RebelDetection(
                    RebelType.CAPTIVE_PORTAL_ATTACK, message.getUid(), "Captive portal attack detected", 0.75, evidence
            ));
        }

        if (isKarmaAttack(message)) {
            evidence.put("karma_indicators", getKarmaIndicators(message));
            detections.add(new RebelDetection(
                    RebelType.KARMA_ATTACK_DEVICE, message.getUid(), "Karma attack detected", 0.8, evidence
            ));
        }

        if (isMANAAttack(message)) {
            evidence.put("mana_indicators", getMANAIndicators(message));
            detections.add(new RebelDetection(
                    RebelType.MANA_ATTACK_DEVICE, message.getUid(), "MANA attack detected", 0.8, evidence
            ));
        }

        return detections;
    }

    private List<RebelDetection> detectAdvancedThreats(CoTMessage message) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (hasNullSSID(message)) {
            evidence.put("ssid_bytes", getSSIDBytes(message));
            evidence.put("null_positions", getNullPositions(message));
            detections.add(new RebelDetection(
                    RebelType.NULL_SSID_ATTACK, message.getUid(), "NULL bytes detected in SSID", 0.8, evidence
            ));
        }

        if (hasUnicodeSpoofing(message)) {
            evidence.put("unicode_chars", getUnicodeChars(message));
            evidence.put("suspicious_chars", getSuspiciousUnicodeChars(message));
            detections.add(new RebelDetection(
                    RebelType.UNICODE_SSID_SPOOFING, message.getUid(), "Unicode spoofing characters detected", 0.75, evidence
            ));
        }

        if (hasPMFBypass(message)) {
            evidence.put("pmf_status", getPMFStatus(message));
            evidence.put("expected_pmf", getExpectedPMF(message));
            detections.add(new RebelDetection(
                    RebelType.PROTECTED_MANAGEMENT_FRAME_BYPASS, message.getUid(), "Protected Management Frame bypass detected", 0.7, evidence
            ));
        }

        if (hasWPA3Downgrade(message)) {
            evidence.put("advertised_security", getAdvertisedSecurity(message));
            evidence.put("expected_security", "WPA3");
            detections.add(new RebelDetection(
                    RebelType.WPA3_DOWNGRADE_ATTACK, message.getUid(), "WPA3 downgrade attack detected", 0.8, evidence
            ));
        }

        if (isKRACKAttack(message)) {
            evidence.put("krack_indicators", getKRACKIndicators(message));
            detections.add(new RebelDetection(
                    RebelType.KRACK_ATTACK_DEVICE, message.getUid(), "KRACK attack detected", 0.85, evidence
            ));
        }

        if (isDragonBloodAttack(message)) {
            evidence.put("dragonblood_indicators", getDragonBloodIndicators(message));
            detections.add(new RebelDetection(
                    RebelType.DRAGONBLOOD_DEVICE, message.getUid(), "DragonBlood attack detected", 0.85, evidence
            ));
        }

        if (isFragAttack(message)) {
            evidence.put("frag_indicators", getFragIndicators(message));
            detections.add(new RebelDetection(
                    RebelType.FRAG_ATTACK_DEVICE, message.getUid(), "FragAttack detected", 0.85, evidence
            ));
        }

        return detections;
    }

    private List<RebelDetection> detectDroneThreats(CoTMessage message, List<CoTMessage> historical) {
        List<RebelDetection> detections = new ArrayList<>();
        Map<String, Object> evidence = new HashMap<>();

        if (message.isSpoofed()) {
            evidence.put("spoofing_reason", message.getSpoofingDetails().getReason());
            evidence.put("confidence", message.getSpoofingDetails().getConfidence());
            detections.add(new RebelDetection(
                    RebelType.SPOOFED_DRONE, message.getUid(), "Drone spoofing detected",
                    message.getSpoofingDetails().getConfidence(), evidence
            ));
        }

        if (hasRandomizedMAC(message)) {
            evidence.put("mac", message.getMac());
            evidence.put("randomization_indicator", message.getMac().charAt(1));
            detections.add(new RebelDetection(
                    RebelType.MAC_RANDOMIZATION, message.getUid(), "MAC address randomization detected", 0.6, evidence
            ));
        }

        if (hasSignalAnomaly(message, historical)) {
            evidence.put("current_rssi", message.getRssi());
            evidence.put("expected_rssi", getExpectedRSSI(message.getUid(), historical));
            evidence.put("deviation", calculateRSSIDeviation(message, historical));
            detections.add(new RebelDetection(
                    RebelType.SIGNAL_ANOMALY, message.getUid(), "Unusual signal strength pattern", 0.5, evidence
            ));
        }

        if (isProximityThreat(message)) {
            evidence.put("rssi", message.getRssi());
            evidence.put("threshold", Constants.PROXIMITY_THRESHOLD);
            evidence.put("distance_estimate", estimateDistance(message.getRssi()));
            detections.add(new RebelDetection(
                    RebelType.PROXIMITY_THREAT, message.getUid(), "High proximity threat detected", 0.7, evidence
            ));
        }

        return detections;
    }

    private String calculateFingerprint(CoTMessage message) {
        StringBuilder fp = new StringBuilder();
        if (message.getMac() != null) fp.append(message.getMac());
        if (message.getType() != null) fp.append(message.getType());
        if (message.getAuthType() != null) fp.append(message.getAuthType());
        return Integer.toHexString(fp.toString().hashCode());
    }

    private boolean detectPwnagotchi(CoTMessage message) {
        if (message.getUid() == null) return false;
        String uid = message.getUid().toLowerCase();
        for (String indicator : PWNAGOTCHI_INDICATORS) {
            if (uid.contains(indicator)) return true;
        }
        return false;
    }

    private boolean hasOMGIndicators(CoTMessage message) {
        if (message.getUid() == null) return false;
        String uid = message.getUid().toLowerCase();
        for (String indicator : OMG_INDICATORS) {
            if (uid.contains(indicator)) return true;
        }
        return false;
    }

    private boolean hasHak5Indicators(CoTMessage message) {
        if (message.getUid() == null) return false;
        String uid = message.getUid().toLowerCase();
        for (String indicator : HAK5_INDICATORS) {
            if (uid.contains(indicator)) return true;
        }
        return false;
    }

    private boolean isEspressifDevice(CoTMessage message) {
        if (message.getMac() == null || message.getUid() == null) return false;
        String ouiPrefix = message.getMac().substring(0, 8).toUpperCase();
        String uid = message.getUid().toLowerCase();

        return ESPRESSIF_PATTERNS.keySet().stream().anyMatch(uid::contains) ||
                isEspressifOUI(ouiPrefix);
    }

    private boolean isChineseKnockoff(CoTMessage message) {
        if (message.getUid() == null) return false;
        String uid = message.getUid().toLowerCase();
        return CHINESE_KNOCKOFF_PATTERNS.keySet().stream().anyMatch(uid::contains);
    }

    private boolean isSuspiciousOUI(String oui) {
        return oui.startsWith("00:00:00") ||
                oui.startsWith("FF:FF:FF") ||
                oui.startsWith("DE:AD:BE") ||
                oui.startsWith("CA:FE:BA");
    }

    private boolean isUnknownVendor(String oui) {
        return !ATTACK_DEVICE_OUIS.containsKey(oui) &&
                !isWellKnownVendor(oui);
    }

    private boolean isOMGMAC(String mac) {
        String oui = mac.substring(0, 8).toUpperCase();
        return oui.equals("C4:4F:33") || oui.equals("5C:CF:7F");
    }

    private boolean isHak5MAC(String mac) {
        String oui = mac.substring(0, 8).toUpperCase();
        return oui.equals("F0:08:D1") || oui.equals("00:BB:3A");
    }

    private boolean isEspressifOUI(String oui) {
        return oui.equals("E4:5F:01") || oui.equals("CC:50:E3") ||
                oui.equals("A0:20:A6") || oui.equals("30:AE:A4") ||
                oui.equals("24:62:AB") || oui.equals("7C:9E:BD") ||
                oui.equals("84:CC:A8") || oui.equals("50:02:91") ||
                oui.equals("18:FE:34") || oui.equals("68:C6:3A");
    }

    private boolean isSuspiciousEspressif(CoTMessage message) {
        if (message.getUid() == null) return false;
        String uid = message.getUid().toLowerCase();
        return uid.contains("deauth") || uid.contains("attack") ||
                uid.contains("hack") || uid.contains("marauder") ||
                uid.contains("evil") || uid.contains("rogue");
    }

    private boolean hasRandomizedMAC(CoTMessage message) {
        if (message.getMac() == null || message.getMac().length() < 2) return false;
        char secondChar = message.getMac().charAt(1);
        return "26AE".indexOf(secondChar) != -1;
    }

    private boolean isProximityThreat(CoTMessage message) {
        return message.getRssi() != null && message.getRssi() > Constants.PROXIMITY_THRESHOLD;
    }

    private boolean hasSuspiciousSSID(CoTMessage message) {
        if (message.getUid() == null) return false;
        for (String suspicious : SUSPICIOUS_SSIDS) {
            if (message.getUid().equalsIgnoreCase(suspicious)) return true;
        }
        return false;
    }

    private boolean hasNullSSID(CoTMessage message) {
        if (message.getUid() == null) return false;
        return message.getUid().contains("\u0000") || message.getUid().contains("\\x00");
    }

    private boolean hasUnicodeSpoofing(CoTMessage message) {
        if (message.getUid() == null) return false;
        for (char c : message.getUid().toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.SPECIALS) return true;
            if (c >= 0x200B && c <= 0x200F) return true;
            if (c >= 0x2028 && c <= 0x202F) return true;
        }
        return false;
    }

    private boolean hasUnexpectedBSSID(CoTMessage message, List<CoTMessage> historical) {
        String expected = getExpectedBSSID(message.getUid());
        return expected != null && !expected.equals(message.getMac());
    }

    private boolean hasUnexpectedSSID(CoTMessage message) {
        return !isKnownSSID(message.getUid());
    }

    private boolean hasCryptoChange(CoTMessage message, List<CoTMessage> historical) {
        String previousCrypto = getPreviousCrypto(message.getUid(), historical);
        String currentCrypto = getCurrentCrypto(message);
        return previousCrypto != null && !previousCrypto.equals(currentCrypto);
    }

    private boolean hasUnexpectedChannel(CoTMessage message, List<CoTMessage> historical) {
        String expected = getExpectedChannel(message.getUid());
        String current = getCurrentChannel(message);
        return expected != null && !expected.equals(current);
    }

    private boolean hasUnexpectedFingerprint(CoTMessage message, List<CoTMessage> historical) {
        String expected = getExpectedFingerprint(message.getUid());
        String current = calculateFingerprint(message);
        return expected != null && !expected.equals(current);
    }

    private boolean hasBeaconRateAnomaly(CoTMessage message, List<CoTMessage> historical) {
        double expected = getExpectedBeaconRate(message.getUid());
        double measured = getMeasuredBeaconRate(message, historical);
        return Math.abs(expected - measured) > (expected * 0.3);
    }

    private boolean hasMultipleSignalTracks(CoTMessage message, List<CoTMessage> historical) {
        return getSignalTracks(message.getUid(), historical).size() > 1;
    }

    private boolean hasDeauthFlood(CoTMessage message, List<CoTMessage> historical) {
        return countDeauthFrames(message.getUid(), historical) > 10;
    }

    private boolean isUnknownSSID(CoTMessage message) {
        return !isKnownSSID(message.getUid());
    }

    private boolean isEvilTwin(CoTMessage message, List<CoTMessage> historical) {
        String legitimateMAC = getLegitimateAP(message.getUid(), historical);
        return legitimateMAC != null && !legitimateMAC.equals(message.getMac());
    }

    private boolean isCaptivePortalAttack(CoTMessage message) {
        return message.getUid() != null &&
                (message.getUid().toLowerCase().contains("portal") ||
                        message.getUid().toLowerCase().contains("login"));
    }

    private boolean isKarmaAttack(CoTMessage message) {
        return message.getUid() != null && message.getUid().toLowerCase().contains("karma");
    }

    private boolean isMANAAttack(CoTMessage message) {
        return message.getUid() != null &&
                (message.getUid().toLowerCase().contains("mana") ||
                        message.getUid().toLowerCase().contains("hostapd"));
    }

    private boolean hasPMFBypass(CoTMessage message) {
        String pmfStatus = getPMFStatus(message);
        String expected = getExpectedPMF(message);
        return expected != null && !expected.equals(pmfStatus);
    }

    private boolean hasWPA3Downgrade(CoTMessage message) {
        String security = getAdvertisedSecurity(message);
        return security != null && !security.contains("WPA3") && shouldBeWPA3(message);
    }

    private boolean isKRACKAttack(CoTMessage message) {
        return message.getUid() != null && message.getUid().toLowerCase().contains("krack");
    }

    private boolean isDragonBloodAttack(CoTMessage message) {
        return message.getUid() != null && message.getUid().toLowerCase().contains("dragonblood");
    }

    private boolean isFragAttack(CoTMessage message) {
        return message.getUid() != null &&
                (message.getUid().toLowerCase().contains("frag") ||
                        message.getUid().toLowerCase().contains("fragment"));
    }

    private boolean hasSignalAnomaly(CoTMessage message, List<CoTMessage> historical) {
        if (message.getRssi() == null) return false;
        double expected = getExpectedRSSI(message.getUid(), historical);
        return Math.abs(message.getRssi() - expected) > 20;
    }

    private RebelType determineOMGType(CoTMessage message) {
        if (message.getUid() == null) return RebelType.OMG_CABLE;
        String uid = message.getUid().toLowerCase();
        if (uid.contains("elite")) return RebelType.OMG_ELITE_CABLE;
        if (uid.contains("plug")) return RebelType.OMG_PLUG;
        return RebelType.OMG_CABLE;
    }

    private RebelType determineHak5Type(CoTMessage message) {
        if (message.getUid() == null) return RebelType.BASH_BUNNY;
        String uid = message.getUid().toLowerCase();
        if (uid.contains("bunny")) return RebelType.BASH_BUNNY;
        if (uid.contains("turtle")) return RebelType.LAN_TURTLE;
        if (uid.contains("squirrel")) return RebelType.PACKET_SQUIRREL;
        if (uid.contains("jack")) return RebelType.SHARK_JACK;
        if (uid.contains("croc")) return RebelType.KEY_CROC;
        if (uid.contains("ducky")) return RebelType.RUBBER_DUCKY;
        return RebelType.BASH_BUNNY;
    }

    private String getVendorFromOUI(String oui) {
        return ATTACK_DEVICE_OUIS.containsKey(oui) ?
                ATTACK_DEVICE_OUIS.get(oui).getDisplayName() : "Unknown";
    }

    private String getSuspiciousOUIReason(String oui) {
        if (oui.startsWith("00:00:00")) return "Null OUI";
        if (oui.startsWith("FF:FF:FF")) return "Broadcast OUI";
        if (oui.startsWith("DE:AD:BE")) return "Test/Debug OUI";
        return "Unusual pattern";
    }

    private boolean isWellKnownVendor(String oui) {
        return oui.startsWith("00:50:56") || oui.startsWith("08:00:27") ||
                oui.startsWith("00:0C:29") || oui.startsWith("00:16:3E");
    }

    private String getEspressifType(CoTMessage message) {
        if (message.getUid() == null) return "Unknown";
        for (Map.Entry<String, String> entry : ESPRESSIF_PATTERNS.entrySet()) {
            if (message.getUid().toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return "Generic Espressif";
    }

    private String getChinesePattern(CoTMessage message) {
        if (message.getUid() == null) return "Unknown";
        for (Map.Entry<String, String> entry : CHINESE_KNOCKOFF_PATTERNS.entrySet()) {
            if (message.getUid().toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return "Generic Chinese";
    }

    private String getKnockoffReason(CoTMessage message) {
        return "Pattern matches known Chinese manufacturer naming";
    }

    private List<String> getOMGIndicators(CoTMessage message) {
        List<String> indicators = new ArrayList<>();
        if (message.getUid() != null) {
            for (String indicator : OMG_INDICATORS) {
                if (message.getUid().toLowerCase().contains(indicator)) {
                    indicators.add(indicator);
                }
            }
        }
        return indicators;
    }

    private List<String> getHak5Indicators(CoTMessage message) {
        List<String> indicators = new ArrayList<>();
        if (message.getUid() != null) {
            for (String indicator : HAK5_INDICATORS) {
                if (message.getUid().toLowerCase().contains(indicator)) {
                    indicators.add(indicator);
                }
            }
        }
        return indicators;
    }

    private List<String> getEspressifIndicators(CoTMessage message) {
        List<String> indicators = new ArrayList<>();
        if (message.getUid() != null) {
            for (String pattern : ESPRESSIF_PATTERNS.keySet()) {
                if (message.getUid().toLowerCase().contains(pattern.toLowerCase())) {
                    indicators.add(pattern);
                }
            }
        }
        return indicators;
    }

    private List<String> getPwnagotchiIndicators(CoTMessage message) {
        List<String> indicators = new ArrayList<>();
        if (message.getUid() != null) {
            for (String indicator : PWNAGOTCHI_INDICATORS) {
                if (message.getUid().toLowerCase().contains(indicator)) {
                    indicators.add(indicator);
                }
            }
        }
        return indicators;
    }

    private List<String> getKarmaIndicators(CoTMessage message) { return Arrays.asList("karma"); }
    private List<String> getMANAIndicators(CoTMessage message) { return Arrays.asList("mana", "hostapd"); }
    private List<String> getKRACKIndicators(CoTMessage message) { return Arrays.asList("krack"); }
    private List<String> getDragonBloodIndicators(CoTMessage message) { return Arrays.asList("dragonblood"); }
    private List<String> getFragIndicators(CoTMessage message) { return Arrays.asList("frag", "fragment"); }

    private String getExpectedBSSID(String uid) { return null; }
    private String getCurrentCrypto(CoTMessage message) { return "WPA2"; }
    private String getPreviousCrypto(String uid, List<CoTMessage> historical) { return "WPA2"; }
    private String getExpectedChannel(String uid) { return "6"; }
    private String getCurrentChannel(CoTMessage message) { return "6"; }
    private String getExpectedFingerprint(String uid) { return null; }
    private double getExpectedBeaconRate(String uid) { return 100.0; }
    private double getMeasuredBeaconRate(CoTMessage message, List<CoTMessage> historical) { return 100.0; }
    private List<String> getSignalTracks(String uid, List<CoTMessage> historical) { return new ArrayList<>(); }
    private int countDeauthFrames(String uid, List<CoTMessage> historical) { return 0; }
    private boolean isKnownSSID(String ssid) { return true; }
    private String getLegitimateAP(String ssid, List<CoTMessage> historical) { return null; }
    private String getSuspiciousPattern(String ssid) { return "generic"; }
    private List<String> getCaptivePortalIndicators(CoTMessage message) { return new ArrayList<>(); }
    private byte[] getSSIDBytes(CoTMessage message) { return new byte[0]; }
    private List<Integer> getNullPositions(CoTMessage message) { return new ArrayList<>(); }
    private List<Character> getUnicodeChars(CoTMessage message) { return new ArrayList<>(); }
    private List<Character> getSuspiciousUnicodeChars(CoTMessage message) { return new ArrayList<>(); }
    private String getPMFStatus(CoTMessage message) { return "enabled"; }
    private String getExpectedPMF(CoTMessage message) { return "enabled"; }
    private String getAdvertisedSecurity(CoTMessage message) { return "WPA2"; }
    private boolean shouldBeWPA3(CoTMessage message) { return false; }
    private double getExpectedRSSI(String uid, List<CoTMessage> historical) { return -50.0; }
    private double calculateRSSIDeviation(CoTMessage message, List<CoTMessage> historical) { return 0.0; }
    private double estimateDistance(Integer rssi) { return rssi != null ? Math.pow(10, (-rssi - 30) / 20.0) : 0.0; }

    public void saveRebelDetection(RebelDetection detection) {
        Log.d(TAG, "Rebel detected: " + detection.getType() + " - " + detection.getDetails());
    }
}