// java/com.rootdown.dragonsync/models/StatusMessage.java
package com.rootdown.dragonsync.models;

import android.location.Location;

public class StatusMessage {
    private String id;
    private String uid;
    private String serialNumber;
    private double timestamp;
    private GPSData gpsData;
    private SystemStats systemStats;
    private ANTStats antStats;

    public static class GPSData {
        private double latitude;
        private double longitude;
        private double altitude;
        private double speed;

        public Location getCoordinate() {
            Location location = new Location("");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            return location;
        }
    }

    public static class SystemStats {
        private double cpuUsage;
        private MemoryStats memory;
        private DiskStats disk;
        private double temperature;
        private double uptime;
        public double getCpuUsage() { return cpuUsage; }
        public double getTemperature() { return temperature; }
        public double getUptime() { return uptime; }
        public MemoryStats getMemory() { return memory; }


        public static class MemoryStats {
            private long total;
            private long available;
            private double percent;
            private long used;
            private long free;
            private long active;
            private long inactive;
            private long buffers;
            private long cached;
            private long shared;
            private long slab;
            public long getTotal() { return total; }
            public long getUsed() { return used; }
        }

        public static class DiskStats {
            private long total;
            private long used;
            private long free;
            private double percent;
        }
    }

    public static class ANTStats {
        private double plutoTemp;
        private double zynqTemp;
    }

    // Basic getters
    public String getId() { return id; }
    public String getUid() { return uid; }
    public String getSerialNumber() { return serialNumber; }
    public double getTimestamp() { return timestamp; }
    public GPSData getGpsData() { return gpsData; }
    public SystemStats getSystemStats() { return systemStats; }
    public ANTStats getAntStats() { return antStats; }
}