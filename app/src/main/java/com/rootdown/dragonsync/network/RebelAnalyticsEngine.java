package com.rootdown.dragonsync.network;

import android.location.Location;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RebelAnalyticsEngine {
    private static final String TAG = "RebelAnalyticsEngine";

    public static class ThreatIntelligence {
        private final Map<String, Double> riskScores;
        private final List<String> knownThreatActors;
        private final Map<String, String> attributionData;

        public ThreatIntelligence() {
            this.riskScores = new HashMap<>();
            this.knownThreatActors = new ArrayList<>();
            this.attributionData = new HashMap<>();
            loadThreatIntelligence();
        }

        private void loadThreatIntelligence() {
            riskScores.put("WIFI_PINEAPPLE", 0.95);
            riskScores.put("PWNAGOTCHI", 0.85);
            riskScores.put("OMG_CABLE", 0.90);
            riskScores.put("ESP32_MARAUDER", 0.80);
            riskScores.put("DEAUTH_FLOOD", 0.95);
            riskScores.put("EVIL_TWIN", 0.90);
            riskScores.put("SPOOFED_DRONE", 0.75);
        }

        public double calculateThreatScore(List<RebelScanner.RebelDetection> detections) {
            return detections.stream()
                    .mapToDouble(d -> riskScores.getOrDefault(d.getType().name(), 0.5) * d.getConfidence())
                    .max().orElse(0.0);
        }
    }

    public static class RebelCorrelationEngine {
        public List<String> correlateRebels(List<RebelHistoryManager.DroneHistoryEntry> entries) {
            List<String> correlations = new ArrayList<>();

            Map<String, List<RebelHistoryManager.DroneHistoryEntry>> macGroups = entries.stream()
                    .filter(e -> e.getMac() != null)
                    .collect(Collectors.groupingBy(RebelHistoryManager.DroneHistoryEntry::getMac));

            for (Map.Entry<String, List<RebelHistoryManager.DroneHistoryEntry>> group : macGroups.entrySet()) {
                if (group.getValue().size() > 1) {
                    Set<String> RebelTypes = group.getValue().stream()
                            .flatMap(e -> e.getRebelDetections().stream())
                            .map(b -> b.getType().name())
                            .collect(Collectors.toSet());

                    if (RebelTypes.size() > 1) {
                        correlations.add("MAC " + group.getKey() + " exhibits multiple attack patterns: " +
                                String.join(", ", RebelTypes));
                    }
                }
            }

            return correlations;
        }
    }

    public static class GeospatialAnalyzer {
        public List<String> analyzeGeospatialPatterns(List<RebelHistoryManager.DroneHistoryEntry> entries) {
            List<String> patterns = new ArrayList<>();

            Map<String, List<Location>> locationsByType = new HashMap<>();

            for (RebelHistoryManager.DroneHistoryEntry entry : entries) {
                if (entry.getLocation() != null && !entry.getRebelDetections().isEmpty()) {
                    for (RebelScanner.RebelDetection Rebel : entry.getRebelDetections()) {
                        locationsByType.computeIfAbsent(Rebel.getType().name(), k -> new ArrayList<>())
                                .add(entry.getLocation());
                    }
                }
            }

            for (Map.Entry<String, List<Location>> typeEntry : locationsByType.entrySet()) {
                if (typeEntry.getValue().size() >= 3) {
                    double avgDistance = calculateAverageDistance(typeEntry.getValue());
                    if (avgDistance < 100) {
                        patterns.add(typeEntry.getKey() + " detections clustered within " +
                                String.format("%.1f", avgDistance) + "m radius");
                    }
                }
            }

            return patterns;
        }

        private double calculateAverageDistance(List<Location> locations) {
            if (locations.size() < 2) return 0;

            double totalDistance = 0;
            int count = 0;

            for (int i = 0; i < locations.size(); i++) {
                for (int j = i + 1; j < locations.size(); j++) {
                    totalDistance += locations.get(i).distanceTo(locations.get(j));
                    count++;
                }
            }

            return count > 0 ? totalDistance / count : 0;
        }
    }
}