// java/com.rootdown.dragonsync/models/ServiceControl.java
package com.rootdown.dragonsync.models;

import java.util.List;

public class ServiceControl {
    private String id;
    private String service;
    private ServiceCategory category;
    private String description;
    private List<String> dependencies;
    private boolean isCritical;
    private ServiceStatus status;
    private ResourceUsage resources;
    private List<ServiceIssue> issues;

    public enum ServiceCategory {
        RADIO("radio", "antenna_radiowaves_left_and_right", "#2196F3"),
        SENSORS("sensors", "sensor", "#4CAF50"),
        COMMS("comms", "network", "#FF9800");

        private final String value;
        private final String icon;
        private final String color;

        ServiceCategory(String value, String icon, String color) {
            this.value = value;
            this.icon = icon;
            this.color = color;
        }

        public String getValue() { return value; }
        public String getIcon() { return icon; }
        public String getColor() { return color; }
    }

    public static class ServiceStatus {
        private boolean isActive;
        private boolean isEnabled;
        private String statusText;
        private String rawStatus;
        private HealthStatus healthStatus;

        public void setActive(boolean active) {
            this.isActive = active;
        }

        public enum HealthStatus {
            HEALTHY("#4CAF50"),
            WARNING("#FFC107"),
            CRITICAL("#F44336"),
            UNKNOWN("#9E9E9E");

            private final String color;
            HealthStatus(String color) { this.color = color; }
            public String getColor() { return color; }
        }
    }

    public static class ResourceUsage {
        private double cpuPercent;
        private double memoryPercent;
    }

    public static class ServiceIssue {
        private String message;
        private IssueSeverity severity;

        public enum IssueSeverity {
            HIGH("#F44336"),
            MEDIUM("#FF9800"),
            WARNING("#FFC107");

            private final String color;
            IssueSeverity(String color) { this.color = color; }
            public String getColor() { return color; }
        }
    }

    // Basic getters
    public String getId() { return id; }
    public String getService() { return service; }
    public ServiceCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public List<String> getDependencies() { return dependencies; }
    public boolean isCritical() { return isCritical; }
    public ServiceStatus getStatus() { return status; }
    public ResourceUsage getResources() { return resources; }
    public List<ServiceIssue> getIssues() { return issues; }
}