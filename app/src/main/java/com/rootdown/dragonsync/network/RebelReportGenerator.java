package com.rootdown.dragonsync.network;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RebelReportGenerator {
    private final RebelHistoryManager historyManager;
    private final RebelAnalyticsEngine.ThreatIntelligence threatIntel;

    public RebelReportGenerator(RebelHistoryManager historyManager) {
        this.historyManager = historyManager;
        this.threatIntel = new RebelAnalyticsEngine.ThreatIntelligence();
    }

    public String generateExecutiveSummary(long startTime, long endTime) {
        List<RebelHistoryManager.DroneHistoryEntry> RebelEntries =
                historyManager.getRebelHistory(startTime, endTime);
        Map<String, Integer> typeStats = historyManager.getRebelTypeStatistics(startTime, endTime);

        StringBuilder report = new StringBuilder();
        report.append("THREAT SUMMARY\n");
        report.append("========================\n\n");
        report.append("Reporting Period: ").append(new Date(startTime)).append(" to ").append(new Date(endTime)).append("\n\n");

        report.append("THREAT OVERVIEW:\n");
        report.append("Total Rebel Detections: ").append(RebelEntries.size()).append("\n");
        report.append("Unique Threat Types: ").append(typeStats.size()).append("\n");

        double avgThreatScore = RebelEntries.stream()
                .mapToDouble(e -> threatIntel.calculateThreatScore(e.getRebelDetections()))
                .average().orElse(0.0);
        report.append("Average Threat Score: ").append(String.format("%.2f", avgThreatScore)).append("\n\n");

        report.append("TOP THREATS:\n");
        typeStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" detections\n"));

        return report.toString();
    }

    public String generateTechnicalReport(long startTime, long endTime) {
        StringBuilder report = new StringBuilder();
        report.append("TECHNICAL THREAT ANALYSIS REPORT\n");
        report.append("=================================\n\n");

        List<RebelHistoryManager.DroneHistoryEntry> entries = historyManager.getRebelHistory(startTime, endTime);

        report.append("ATTACK PLATFORM ANALYSIS:\n");
        Map<String, Long> platformCounts = entries.stream()
                .flatMap(e -> e.getRebelDetections().stream())
                .collect(Collectors.groupingBy(b -> b.getType().name(), Collectors.counting()));

        platformCounts.forEach((platform, count) ->
                report.append("- ").append(platform).append(": ").append(count).append(" instances\n"));

        report.append("\nCORRELATION ANALYSIS:\n");
        RebelAnalyticsEngine.RebelCorrelationEngine correlator = new RebelAnalyticsEngine.RebelCorrelationEngine();
        List<String> correlations = correlator.correlateRebels(entries);
        correlations.forEach(c -> report.append("- ").append(c).append("\n"));

        report.append("\nGEOSPATIAL ANALYSIS:\n");
        RebelAnalyticsEngine.GeospatialAnalyzer geoAnalyzer = new RebelAnalyticsEngine.GeospatialAnalyzer();
        List<String> geoPatterns = geoAnalyzer.analyzeGeospatialPatterns(entries);
        geoPatterns.forEach(p -> report.append("- ").append(p).append("\n"));

        return report.toString();
    }
}
