package com.rootdown.dragonsync.models;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CoTMessage implements Parcelable {
    private String id;
    private String caaRegistration;
    private String uid;
    private String type;
    private String lat;
    private String lon;
    private String homeLat;
    private String homeLon;
    private String speed;
    private String vspeed;
    private String alt;
    private String height;
    private String pilotLat;
    private String pilotLon;
    private String description;
    private String selfIDText;
    private DroneSignature.IdInfo.UAType uaType;
    private String idType;
    private String mac;
    private Integer rssi;
    private String manufacturer;
    private List<SignalSource> signalSources = new ArrayList<>();
    private String location_protocol;
    private String op_status;
    private String height_type;
    private String ew_dir_segment;
    private String speed_multiplier;
    private String direction;
    private String vertical_accuracy;
    private String horizontal_accuracy;
    private String baro_accuracy;
    private String speed_accuracy;
    private String timestamp;
    private String timestamp_accuracy;
    private String time;
    private String start;
    private String stale;
    private String how;
    private String ce;
    private String le;
    private String hae;
    private Integer aux_rssi;
    private Integer channel;
    private Integer phy;
    private Integer aa;
    private String adv_mode;
    private String adv_mac;
    private Integer did;
    private Integer sid;
    private String timeSpeed;
    private String status;
    private String opStatus;
    private String altPressure;
    private String heightType;
    private String horizAcc;
    private String vertAcc;
    private String baroAcc;
    private String speedAcc;
    private String timestampAccuracy;
    private String operator_id;
    private String operator_id_type;
    private String classification_type;
    private String operator_location_type;
    private String area_count;
    private String area_radius;
    private String area_ceiling;
    private String area_floor;
    private String advMode;
    private Integer txAdd;
    private Integer rxAdd;
    private Integer adLength;
    private Integer accessAddress;
    private String operatorAltGeo;
    private String areaCount;
    private String areaRadius;
    private String areaCeiling;
    private String areaFloor;
    private String classification;
    private String selfIdType;
    private String selfIdId;
    private String authType;
    private String authPage;
    private String authLength;
    private String authTimestamp;
    private String authData;
    public boolean isSpoofed;
    public DroneSignature.SpoofDetectionResult spoofingDetails;
    private String index;
    private String runtime;
    public Map<String, Object> rawMessage;

    public static class SignalSource implements Parcelable {
        private final String mac;
        private final int rssi;
        private final SignalType type;
        private final long timestamp;

        public enum SignalType {
            BLUETOOTH("BT4/5 DroneID"),
            WIFI("WiFi DroneID"),
            SDR("SDR DroneID"),
            UNKNOWN("Unknown");

            private final String displayName;

            SignalType(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        public SignalSource(String mac, int rssi, SignalType type, long timestamp) {
            this.mac = mac;
            this.rssi = rssi;
            this.type = type;
            this.timestamp = timestamp;
        }

        protected SignalSource(Parcel in) {
            mac = in.readString();
            rssi = in.readInt();
            type = SignalType.valueOf(in.readString());
            timestamp = in.readLong();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mac);
            dest.writeInt(rssi);
            dest.writeString(type.name());
            dest.writeLong(timestamp);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<SignalSource> CREATOR = new Creator<SignalSource>() {
            @Override
            public SignalSource createFromParcel(Parcel in) {
                return new SignalSource(in);
            }

            @Override
            public SignalSource[] newArray(int size) {
                return new SignalSource[size];
            }
        };

        public String getMac() { return mac; }
        public int getRssi() { return rssi; }
        public SignalType getType() { return type; }
        public long getTimestamp() { return timestamp; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SignalSource that = (SignalSource) o;
            return mac.equals(that.mac) && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mac, type);
        }
    }

    public CoTMessage() {}

    protected CoTMessage(Parcel in) {
        id = in.readString();
        caaRegistration = in.readString();
        uid = in.readString();
        type = in.readString();
        lat = in.readString();
        lon = in.readString();
        homeLat = in.readString();
        homeLon = in.readString();
        speed = in.readString();
        vspeed = in.readString();
        alt = in.readString();
        height = in.readString();
        pilotLat = in.readString();
        pilotLon = in.readString();
        description = in.readString();
        selfIDText = in.readString();
        uaType = DroneSignature.IdInfo.UAType.valueOf(in.readString());
        idType = in.readString();
        mac = in.readString();
        rssi = in.readInt();
        manufacturer = in.readString();
        signalSources = in.createTypedArrayList(SignalSource.CREATOR);
        location_protocol = in.readString();
        op_status = in.readString();
        height_type = in.readString();
        ew_dir_segment = in.readString();
        speed_multiplier = in.readString();
        direction = in.readString();
        vertical_accuracy = in.readString();
        horizontal_accuracy = in.readString();
        baro_accuracy = in.readString();
        speed_accuracy = in.readString();
        timestamp = in.readString();
        timestamp_accuracy = in.readString();
        time = in.readString();
        start = in.readString();
        stale = in.readString();
        how = in.readString();
        ce = in.readString();
        le = in.readString();
        hae = in.readString();
        aux_rssi = in.readInt();
        channel = in.readInt();
        phy = in.readInt();
        aa = in.readInt();
        adv_mode = in.readString();
        adv_mac = in.readString();
        did = in.readInt();
        sid = in.readInt();
        timeSpeed = in.readString();
        status = in.readString();
        opStatus = in.readString();
        altPressure = in.readString();
        heightType = in.readString();
        horizAcc = in.readString();
        vertAcc = in.readString();
        baroAcc = in.readString();
        speedAcc = in.readString();
        timestampAccuracy = in.readString();
        operator_id = in.readString();
        operator_id_type = in.readString();
        classification_type = in.readString();
        operator_location_type = in.readString();
        area_count = in.readString();
        area_radius = in.readString();
        area_ceiling = in.readString();
        area_floor = in.readString();
        advMode = in.readString();
        txAdd = in.readInt();
        rxAdd = in.readInt();
        adLength = in.readInt();
        accessAddress = in.readInt();
        operatorAltGeo = in.readString();
        areaCount = in.readString();
        areaRadius = in.readString();
        areaCeiling = in.readString();
        areaFloor = in.readString();
        classification = in.readString();
        selfIdType = in.readString();
        selfIdId = in.readString();
        authType = in.readString();
        authPage = in.readString();
        authLength = in.readString();
        authTimestamp = in.readString();
        authData = in.readString();
        isSpoofed = in.readByte() != 0;
        index = in.readString();
        runtime = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(caaRegistration);
        dest.writeString(uid);
        dest.writeString(type);
        dest.writeString(lat);
        dest.writeString(lon);
        dest.writeString(homeLat);
        dest.writeString(homeLon);
        dest.writeString(speed);
        dest.writeString(vspeed);
        dest.writeString(alt);
        dest.writeString(height);
        dest.writeString(pilotLat);
        dest.writeString(pilotLon);
        dest.writeString(description);
        dest.writeString(selfIDText);
        dest.writeString(uaType.name());
        dest.writeString(idType);
        dest.writeString(mac);
        dest.writeInt(rssi != null ? rssi : 0);
        dest.writeString(manufacturer);
        dest.writeTypedList(signalSources);
        dest.writeString(location_protocol);
        dest.writeString(op_status);
        dest.writeString(height_type);
        dest.writeString(ew_dir_segment);
        dest.writeString(speed_multiplier);
        dest.writeString(direction);
        dest.writeString(vertical_accuracy);
        dest.writeString(horizontal_accuracy);
        dest.writeString(baro_accuracy);
        dest.writeString(speed_accuracy);
        dest.writeString(timestamp);
        dest.writeString(timestamp_accuracy);
        dest.writeString(time);
        dest.writeString(start);
        dest.writeString(stale);
        dest.writeString(how);
        dest.writeString(ce);
        dest.writeString(le);
        dest.writeString(hae);
        dest.writeInt(aux_rssi != null ? aux_rssi : 0);
        dest.writeInt(channel != null ? channel : 0);
        dest.writeInt(phy != null ? phy : 0);
        dest.writeInt(aa != null ? aa : 0);
        dest.writeString(adv_mode);
        dest.writeString(adv_mac);
        dest.writeInt(did != null ? did : 0);
        dest.writeInt(sid != null ? sid : 0);
        dest.writeString(timeSpeed);
        dest.writeString(status);
        dest.writeString(opStatus);
        dest.writeString(altPressure);
        dest.writeString(heightType);
        dest.writeString(horizAcc);
        dest.writeString(vertAcc);
        dest.writeString(baroAcc);
        dest.writeString(speedAcc);
        dest.writeString(timestampAccuracy);
        dest.writeString(operator_id);
        dest.writeString(operator_id_type);
        dest.writeString(classification_type);
        dest.writeString(operator_location_type);
        dest.writeString(area_count);
        dest.writeString(area_radius);
        dest.writeString(area_ceiling);
        dest.writeString(area_floor);
        dest.writeString(advMode);
        dest.writeInt(txAdd != null ? txAdd : 0);
        dest.writeInt(rxAdd != null ? rxAdd : 0);
        dest.writeInt(adLength != null ? adLength : 0);
        dest.writeInt(accessAddress != null ? accessAddress : 0);
        dest.writeString(operatorAltGeo);
        dest.writeString(areaCount);
        dest.writeString(areaRadius);
        dest.writeString(areaCeiling);
        dest.writeString(areaFloor);
        dest.writeString(classification);
        dest.writeString(selfIdType);
        dest.writeString(selfIdId);
        dest.writeString(authType);
        dest.writeString(authPage);
        dest.writeString(authLength);
        dest.writeString(authTimestamp);
        dest.writeString(authData);
        dest.writeByte((byte) (isSpoofed ? 1 : 0));
        dest.writeString(index);
        dest.writeString(runtime);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CoTMessage> CREATOR = new Creator<CoTMessage>() {
        @Override
        public CoTMessage createFromParcel(Parcel in) {
            return new CoTMessage(in);
        }

        @Override
        public CoTMessage[] newArray(int size) {
            return new CoTMessage[size];
        }
    };

    // Helper methods
    public Location getCoordinate() {
        try {
            double latDouble = Double.parseDouble(lat);
            double lonDouble = Double.parseDouble(lon);
            if (latDouble != 0 || lonDouble != 0) {
                Location location = new Location("");
                location.setLatitude(latDouble);
                location.setLongitude(lonDouble);
                return location;
            }
        } catch (NumberFormatException e) {}
        return null;
    }

    public String getFormattedAltitude() {
        try {
            double altValue = Double.parseDouble(alt);
            if (altValue != 0) {
                return String.format("%.1f m MSL", altValue);
            }
        } catch (NumberFormatException e) {}
        return null;
    }

    public String getFormattedHeight() {
        try {
            if (height != null) {
                double heightValue = Double.parseDouble(height);
                if (heightValue != 0) {
                    return String.format("%.1f m AGL", heightValue);
                }
            }
        } catch (NumberFormatException e) {}
        return null;
    }

    // Getters and setters for all fields
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCaaRegistration() { return caaRegistration; }
    public void setCaaRegistration(String caaRegistration) { this.caaRegistration = caaRegistration; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLat() { return lat; }
    public void setLat(String lat) { this.lat = lat; }

    public String getLon() { return lon; }
    public void setLon(String lon) { this.lon = lon; }

    public String getHomeLat() { return homeLat; }
    public void setHomeLat(String homeLat) { this.homeLat = homeLat; }

    public String getHomeLon() { return homeLon; }
    public void setHomeLon(String homeLon) { this.homeLon = homeLon; }

    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }

    public String getVspeed() { return vspeed; }
    public void setVspeed(String vspeed) { this.vspeed = vspeed; }

    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }

    public String getHeight() { return height; }
    public void setHeight(String height) { this.height = height; }

    public String getPilotLat() { return pilotLat; }
    public void setPilotLat(String pilotLat) { this.pilotLat = pilotLat; }

    public String getPilotLon() { return pilotLon; }
    public void setPilotLon(String pilotLon) { this.pilotLon = pilotLon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSelfIDText() { return selfIDText; }
    public void setSelfIDText(String selfIDText) { this.selfIDText = selfIDText; }

    public DroneSignature.IdInfo.UAType getUaType() { return uaType; }
    public void setUaType(DroneSignature.IdInfo.UAType uaType) { this.uaType = uaType; }

    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }

    public String getMac() { return mac; }
    public void setMac(String mac) { this.mac = mac; }

    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public List<SignalSource> getSignalSources() { return signalSources; }
    public void setSignalSources(List<SignalSource> signalSources) { this.signalSources = signalSources; }

    public String getLocationProtocol() { return location_protocol; }
    public void setLocationProtocol(String location_protocol) { this.location_protocol = location_protocol; }

    public String getOpStatus() { return op_status; }
    public void setOpStatus(String op_status) { this.op_status = op_status; }

    public String getHeightType() { return height_type; }
    public void setHeightType(String height_type) { this.height_type = height_type; }

    public String getEwDirSegment() { return ew_dir_segment; }
    public void setEwDirSegment(String ew_dir_segment) { this.ew_dir_segment = ew_dir_segment; }

    public String getSpeedMultiplier() { return speed_multiplier; }
    public void setSpeedMultiplier(String speed_multiplier) { this.speed_multiplier = speed_multiplier; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getVerticalAccuracy() { return vertical_accuracy; }
    public void setVerticalAccuracy(String vertical_accuracy) { this.vertical_accuracy = vertical_accuracy; }

    public String getHorizontalAccuracy() { return horizontal_accuracy; }
    public void setHorizontalAccuracy(String horizontal_accuracy) { this.horizontal_accuracy = horizontal_accuracy; }

    public String getBaroAccuracy() { return baro_accuracy; }
    public void setBaroAccuracy(String baro_accuracy) { this.baro_accuracy = baro_accuracy; }

    public String getSpeedAccuracy() { return speed_accuracy; }
    public void setSpeedAccuracy(String speed_accuracy) { this.speed_accuracy = speed_accuracy; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getTimestampAccuracy() { return timestamp_accuracy; }
    public void setTimestampAccuracy(String timestamp_accuracy) { this.timestamp_accuracy = timestamp_accuracy; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }

    public String getStale() { return stale; }
    public void setStale(String stale) { this.stale = stale; }

    public String getHow() { return how; }
    public void setHow(String how) { this.how = how; }

    public String getCe() { return ce; }
    public void setCe(String ce) { this.ce = ce; }

    public String getLe() { return le; }
    public void setLe(String le) { this.le = le; }

    public String getHae() { return hae; }
    public void setHae(String hae) { this.hae = hae; }

    public Integer getAuxRssi() { return aux_rssi; }
    public void setAuxRssi(Integer aux_rssi) { this.aux_rssi = aux_rssi; }

    public Integer getChannel() { return channel; }
    public void setChannel(Integer channel) { this.channel = channel; }

    public Integer getPhy() { return phy; }
    public void setPhy(Integer phy) { this.phy = phy; }

    public Integer getAa() { return aa; }
    public void setAa(Integer aa) { this.aa = aa; }

    public String getAdvMode() { return adv_mode; }
    public void setAdvMode(String adv_mode) { this.adv_mode = adv_mode; }

    public String getAdvMac() { return adv_mac; }
    public void setAdvMac(String adv_mac) { this.adv_mac = adv_mac; }

    public Integer getDid() { return did; }
    public void setDid(Integer did) { this.did = did; }

    public Integer getSid() { return sid; }
    public void setSid(Integer sid) { this.sid = sid; }

    public String getTimeSpeed() { return timeSpeed; }
    public void setTimeSpeed(String timeSpeed) { this.timeSpeed = timeSpeed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAltPressure() { return altPressure; }
    public void setAltPressure(String altPressure) { this.altPressure = altPressure; }

    public String getHeightTypeDetail() { return heightType; }
    public void setHeightTypeDetail(String heightType) { this.heightType = heightType; }

    public String getHorizAcc() { return horizAcc; }
    public void setHorizAcc(String horizAcc) { this.horizAcc = horizAcc; }

    public String getVertAcc() { return vertAcc; }
    public void setVertAcc(String vertAcc) { this.vertAcc = vertAcc; }

    public String getBaroAcc() { return baroAcc; }
    public void setBaroAcc(String baroAcc) { this.baroAcc = baroAcc; }

    public String getSpeedAcc() { return speedAcc; }
    public void setSpeedAcc(String speedAcc) { this.speedAcc = speedAcc; }

    public String getOperatorId() { return operator_id; }
    public void setOperatorId(String operator_id) { this.operator_id = operator_id; }

    public String getOperatorIdType() { return operator_id_type; }
    public void setOperatorIdType(String operator_id_type) { this.operator_id_type = operator_id_type; }

    public String getClassificationType() { return classification_type; }
    public void setClassificationType(String classification_type) { this.classification_type = classification_type; }

    public String getOperatorLocationType() { return operator_location_type; }
    public void setOperatorLocationType(String operator_location_type) { this.operator_location_type = operator_location_type; }

    public String getAreaCount() { return area_count; }
    public void setAreaCount(String area_count) { this.area_count = area_count; }

    public String getAreaRadius() { return area_radius; }
    public void setAreaRadius(String area_radius) { this.area_radius = area_radius; }

    public String getAreaCeiling() { return area_ceiling; }
    public void setAreaCeiling(String area_ceiling) { this.area_ceiling = area_ceiling; }

    public String getAreaFloor() { return area_floor; }
    public void setAreaFloor(String area_floor) { this.area_floor = area_floor; }

    public Integer getTxAdd() { return txAdd; }
    public void setTxAdd(Integer txAdd) { this.txAdd = txAdd; }

    public Integer getRxAdd() { return rxAdd; }
    public void setRxAdd(Integer rxAdd) { this.rxAdd = rxAdd; }

    public Integer getAdLength() { return adLength; }
    public void setAdLength(Integer adLength) { this.adLength = adLength; }

    public Integer getAccessAddress() { return accessAddress; }
    public void setAccessAddress(Integer accessAddress) { this.accessAddress = accessAddress; }

    public String getOperatorAltGeo() { return operatorAltGeo; }
    public void setOperatorAltGeo(String operatorAltGeo) { this.operatorAltGeo = operatorAltGeo; }

//    public String getAreaCount() { return areaCount; }
//    public void setAreaCount(String areaCount) { this.areaCount = areaCount; }
//
//    public String getAreaRadius() { return areaRadius; }
//    public void setAreaRadius(String areaRadius) { this.areaRadius = areaRadius; }
//
//    public String getAreaCeiling() { return areaCeiling; }
//    public void setAreaCeiling(String areaCeiling) { this.areaCeiling = areaCeiling; }
//
//    public String getAreaFloor() { return areaFloor; }
//    public void setAreaFloor(String areaFloor) { this.areaFloor = areaFloor; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public String getSelfIdType() { return selfIdType; }
    public void setSelfIdType(String selfIdType) { this.selfIdType = selfIdType; }

    public String getSelfIdId() { return selfIdId; }
    public void setSelfIdId(String selfIdId) { this.selfIdId = selfIdId; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getAuthPage() { return authPage; }
    public void setAuthPage(String authPage) { this.authPage = authPage; }

    public String getAuthLength() { return authLength; }
    public void setAuthLength(String authLength) { this.authLength = authLength; }

    public String getAuthTimestamp() { return authTimestamp; }
    public void setAuthTimestamp(String authTimestamp) { this.authTimestamp = authTimestamp; }

    public String getAuthData() { return authData; }
    public void setAuthData(String authData) { this.authData = authData; }

    public boolean isSpoofed() { return isSpoofed; }
    public void setSpoofed(boolean spoofed) { this.isSpoofed = spoofed; }

    public DroneSignature.SpoofDetectionResult getSpoofingDetails() { return spoofingDetails; }
    public void setSpoofingDetails(DroneSignature.SpoofDetectionResult spoofingDetails) { this.spoofingDetails = spoofingDetails; }

    public String getIndex() { return index; }
    public void setIndex(String index) { this.index = index; }

    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }

    public Map<String, Object> getRawMessage() { return rawMessage; }
    public void setRawMessage(Map<String, Object> rawMessage) { this.rawMessage = rawMessage; }

    public Map<String, Object> toDictionary() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("uid", uid);
        dict.put("id", id);
        dict.put("type", type);
        dict.put("lat", lat);
        dict.put("lon", lon);
        dict.put("latitude", lat);
        dict.put("longitude", lon);
        dict.put("speed", speed);
        dict.put("vspeed", vspeed);
        dict.put("alt", alt);
        dict.put("pilotLat", pilotLat);
        dict.put("pilotLon", pilotLon);
        dict.put("description", description);
        dict.put("selfIDText", selfIDText);
        dict.put("uaType", uaType);
        dict.put("idType", idType);
        dict.put("isSpoofed", isSpoofed);
        dict.put("rssi", rssi != null ? rssi : 0.0);
        dict.put("mac", mac != null ? mac : "");
        dict.put("manufacturer", manufacturer != null ? manufacturer : "");
        dict.put("op_status", op_status != null ? op_status : "");
        dict.put("ew_dir_segment", ew_dir_segment != null ? ew_dir_segment : "");
        dict.put("direction", direction != null ? direction : "");

        return dict;
    }
}