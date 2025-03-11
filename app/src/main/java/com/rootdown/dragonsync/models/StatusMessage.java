package com.rootdown.dragonsync.models;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

public class StatusMessage implements Parcelable {
    private String id;
    private String uid;
    private String serialNumber;
    private double timestamp;
    private GPSData gpsData;
    private SystemStats systemStats;
    private ANTStats antStats;

    // Constructor
    public StatusMessage() {
    }

    // Parcelable implementation
    protected StatusMessage(Parcel in) {
        id = in.readString();
        uid = in.readString();
        serialNumber = in.readString();
        timestamp = in.readDouble();
        gpsData = in.readParcelable(GPSData.class.getClassLoader());
        systemStats = in.readParcelable(SystemStats.class.getClassLoader());
        antStats = in.readParcelable(ANTStats.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(uid);
        dest.writeString(serialNumber);
        dest.writeDouble(timestamp);
        dest.writeParcelable(gpsData, flags);
        dest.writeParcelable(systemStats, flags);
        dest.writeParcelable(antStats, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<StatusMessage> CREATOR = new Creator<StatusMessage>() {
        @Override
        public StatusMessage createFromParcel(Parcel in) {
            return new StatusMessage(in);
        }

        @Override
        public StatusMessage[] newArray(int size) {
            return new StatusMessage[size];
        }
    };

    // Inner classes must also implement Parcelable
    public static class GPSData implements Parcelable {
        private double latitude;
        private double longitude;
        private double altitude;
        private double speed;

        public GPSData() {
        }

        protected GPSData(Parcel in) {
            latitude = in.readDouble();
            longitude = in.readDouble();
            altitude = in.readDouble();
            speed = in.readDouble();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeDouble(latitude);
            dest.writeDouble(longitude);
            dest.writeDouble(altitude);
            dest.writeDouble(speed);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<GPSData> CREATOR = new Creator<GPSData>() {
            @Override
            public GPSData createFromParcel(Parcel in) {
                return new GPSData(in);
            }

            @Override
            public GPSData[] newArray(int size) {
                return new GPSData[size];
            }
        };

        // Existing methods
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }

        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }

        public double getAltitude() { return altitude; }
        public void setAltitude(double altitude) { this.altitude = altitude; }

        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }

        public Location getCoordinate() {
            Location location = new Location("");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            return location;
        }
    }

    public static class SystemStats implements Parcelable {
        private double cpuUsage;
        private MemoryStats memory;
        private DiskStats disk;
        private double temperature;
        private double uptime;

        public SystemStats() {
        }

        protected SystemStats(Parcel in) {
            cpuUsage = in.readDouble();
            memory = in.readParcelable(MemoryStats.class.getClassLoader());
            disk = in.readParcelable(DiskStats.class.getClassLoader());
            temperature = in.readDouble();
            uptime = in.readDouble();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeDouble(cpuUsage);
            dest.writeParcelable(memory, flags);
            dest.writeParcelable(disk, flags);
            dest.writeDouble(temperature);
            dest.writeDouble(uptime);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<SystemStats> CREATOR = new Creator<SystemStats>() {
            @Override
            public SystemStats createFromParcel(Parcel in) {
                return new SystemStats(in);
            }

            @Override
            public SystemStats[] newArray(int size) {
                return new SystemStats[size];
            }
        };

        // Existing methods
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public double getUptime() { return uptime; }
        public void setUptime(double uptime) { this.uptime = uptime; }

        public MemoryStats getMemory() { return memory; }
        public void setMemory(MemoryStats memory) { this.memory = memory; }

        public DiskStats getDisk() { return disk; }
        public void setDisk(DiskStats disk) { this.disk = disk; }

        public static class MemoryStats implements Parcelable {
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

            public MemoryStats() {
            }

            protected MemoryStats(Parcel in) {
                total = in.readLong();
                available = in.readLong();
                percent = in.readDouble();
                used = in.readLong();
                free = in.readLong();
                active = in.readLong();
                inactive = in.readLong();
                buffers = in.readLong();
                cached = in.readLong();
                shared = in.readLong();
                slab = in.readLong();
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeLong(total);
                dest.writeLong(available);
                dest.writeDouble(percent);
                dest.writeLong(used);
                dest.writeLong(free);
                dest.writeLong(active);
                dest.writeLong(inactive);
                dest.writeLong(buffers);
                dest.writeLong(cached);
                dest.writeLong(shared);
                dest.writeLong(slab);
            }

            @Override
            public int describeContents() {
                return 0;
            }

            public static final Creator<MemoryStats> CREATOR = new Creator<MemoryStats>() {
                @Override
                public MemoryStats createFromParcel(Parcel in) {
                    return new MemoryStats(in);
                }

                @Override
                public MemoryStats[] newArray(int size) {
                    return new MemoryStats[size];
                }
            };

            // Existing methods
            public long getTotal() { return total; }
            public void setTotal(long total) { this.total = total; }

            public long getUsed() { return used; }
            public void setUsed(long used) { this.used = used; }

            public double getPercent() { return percent; }
            public void setPercent(double percent) { this.percent = percent; }

            public long getFree() { return free; }
            public void setFree(long free) { this.free = free; }
        }

        public static class DiskStats implements Parcelable {
            private long total;
            private long used;
            private long free;
            private double percent;

            public DiskStats() {
            }

            protected DiskStats(Parcel in) {
                total = in.readLong();
                used = in.readLong();
                free = in.readLong();
                percent = in.readDouble();
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeLong(total);
                dest.writeLong(used);
                dest.writeLong(free);
                dest.writeDouble(percent);
            }

            @Override
            public int describeContents() {
                return 0;
            }

            public static final Creator<DiskStats> CREATOR = new Creator<DiskStats>() {
                @Override
                public DiskStats createFromParcel(Parcel in) {
                    return new DiskStats(in);
                }

                @Override
                public DiskStats[] newArray(int size) {
                    return new DiskStats[size];
                }
            };
        }
    }

    public static class ANTStats implements Parcelable {
        private double plutoTemp;
        private double zynqTemp;

        public ANTStats() {
        }

        protected ANTStats(Parcel in) {
            plutoTemp = in.readDouble();
            zynqTemp = in.readDouble();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeDouble(plutoTemp);
            dest.writeDouble(zynqTemp);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<ANTStats> CREATOR = new Creator<ANTStats>() {
            @Override
            public ANTStats createFromParcel(Parcel in) {
                return new ANTStats(in);
            }

            @Override
            public ANTStats[] newArray(int size) {
                return new ANTStats[size];
            }
        };

        // Existing methods
        public double getPlutoTemp() { return plutoTemp; }
        public void setPlutoTemp(double plutoTemp) { this.plutoTemp = plutoTemp; }

        public double getZynqTemp() { return zynqTemp; }
        public void setZynqTemp(double zynqTemp) { this.zynqTemp = zynqTemp; }
    }

    // Existing methods
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public double getTimestamp() { return timestamp; }
    public void setTimestamp(double timestamp) { this.timestamp = timestamp; }

    public GPSData getGpsData() { return gpsData; }
    public void setGpsData(GPSData gpsData) { this.gpsData = gpsData; }

    public SystemStats getSystemStats() { return systemStats; }
    public void setSystemStats(SystemStats systemStats) { this.systemStats = systemStats; }

    public ANTStats getAntStats() { return antStats; }
    public void setAntStats(ANTStats antStats) { this.antStats = antStats; }
}