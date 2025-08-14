package com.rootdown.dragonsync.network;

import android.util.Log;
import android.util.Xml;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;
import com.rootdown.dragonsync.models.StatusMessage;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XMLParser {
    private static final String TAG = "XMLParser";
    private final Map<String, ArrayList<String>> macPrefixesByManufacturer = new HashMap<>();
    private final Gson gson = new Gson();

    public XMLParser() {
        initializeMacPrefixes();
    }

    private void initializeMacPrefixes() {
        // Initialize MAC prefixes for manufacturer detection
        ArrayList<String> djiPrefixes = new ArrayList<>();
        djiPrefixes.add("04:A8:5A");
        djiPrefixes.add("34:D2:62");
        djiPrefixes.add("48:1C:B9");
        djiPrefixes.add("58:B8:58");
        djiPrefixes.add("60:60:1F");
        djiPrefixes.add("E4:7A:2C");
        macPrefixesByManufacturer.put("DJI", djiPrefixes);
    }

    public ParseResult parse(String message) {
        ParseResult result = new ParseResult();

        // First check if this is XML (multicast mode)
        if (message.trim().startsWith("<")) {
            return parseXML(message);
        }

        // Not XML, so it's JSON (ZMQ mode)
        try {
            JsonElement jsonElement = JsonParser.parseString(message);

            if (jsonElement.isJsonObject()) {
                // ESP32 format (single JSON object)
                return parseESP32Format(jsonElement.getAsJsonObject());
            } else if (jsonElement.isJsonArray()) {
                // DJI/BT/WiFi format (array of JSON objects)
                return parseDroneArrayFormat(jsonElement.getAsJsonArray());
            } else {
                Log.e(TAG, "Unexpected JSON format, neither object nor array");
                result.error = "Unexpected JSON format";
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON parsing error: " + e.getMessage());
            result.error = "JSON parsing error: " + e.getMessage();
        }

        return result;
    }

    private void extractStatusFromRemarks(String remarks, CoTMessage cotMessage, ParseResult result) {
        if (remarks == null || remarks.isEmpty()) return;

        Log.d(TAG, "Parsing remarks for status: " + remarks);

        StatusMessage statusMessage = new StatusMessage();
        statusMessage.setId(cotMessage.getUid()); // Use the CoT UID as status ID
        statusMessage.setSerialNumber(cotMessage.getUid());
        statusMessage.setTimestamp(System.currentTimeMillis() / 1000.0);

        StatusMessage.SystemStats stats = new StatusMessage.SystemStats();
        StatusMessage.SystemStats.MemoryStats memory = new StatusMessage.SystemStats.MemoryStats();
        StatusMessage.ANTStats antStats = new StatusMessage.ANTStats();

        // Parse CPU usage
        if (remarks.contains("CPU Usage:")) {
            int cpuStart = remarks.indexOf("CPU Usage:") + 11;
            int cpuEnd = remarks.indexOf("%", cpuStart);
            if (cpuEnd > cpuStart) {
                String cpuStr = remarks.substring(cpuStart, cpuEnd).trim();
                try {
                    double cpuUsage = Double.parseDouble(cpuStr);
                    stats.setCpuUsage(cpuUsage);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse CPU usage: " + cpuStr);
                }
            }
        }

        // Memory Total
        if (remarks.contains("Memory Total:")) {
            int memTotalStart = remarks.indexOf("Memory Total:") + 13;
            int memTotalEnd = remarks.indexOf("MB", memTotalStart);
            if (memTotalEnd > memTotalStart) {
                String memTotalStr = remarks.substring(memTotalStart, memTotalEnd).trim();
                try {
                    long memoryTotal = (long)(Double.parseDouble(memTotalStr) * 1024 * 1024); // Convert MB to bytes
                    memory.setTotal(memoryTotal);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse Memory Total: " + memTotalStr);
                }
            }
        }

        // Memory Available
        if (remarks.contains("Memory Available:")) {
            int memAvailStart = remarks.indexOf("Memory Available:") + 17;
            int memAvailEnd = remarks.indexOf("MB", memAvailStart);
            if (memAvailEnd > memAvailStart) {
                String memAvailStr = remarks.substring(memAvailStart, memAvailEnd).trim();
                try {
                    long memoryAvailable = (long)(Double.parseDouble(memAvailStr) * 1024 * 1024); // Convert MB to bytes
                    // Calculate used memory
                    if (memory.getTotal() > 0) {
                        memory.setUsed(memory.getTotal() - memoryAvailable);
                        memory.setFree(memoryAvailable);
                        memory.setPercent((double) memory.getUsed() / memory.getTotal());
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse Memory Available: " + memAvailStr);
                }
            }
        }

        // Parse Disk Total and Used
        if (remarks.contains("Disk Total:") && remarks.contains("Disk Used:")) {
            int diskTotalStart = remarks.indexOf("Disk Total:") + 11;
            int diskTotalEnd = remarks.indexOf("MB", diskTotalStart);
            int diskUsedStart = remarks.indexOf("Disk Used:") + 10;
            int diskUsedEnd = remarks.indexOf("MB", diskUsedStart);

            try {
                if (diskTotalEnd > diskTotalStart && diskUsedEnd > diskUsedStart) {
                    String diskTotalStr = remarks.substring(diskTotalStart, diskTotalEnd).trim();
                    String diskUsedStr = remarks.substring(diskUsedStart, diskUsedEnd).trim();

                    StatusMessage.SystemStats.DiskStats disk = new StatusMessage.SystemStats.DiskStats();
                    disk.total = (long)(Double.parseDouble(diskTotalStr) * 1024 * 1024); // Convert MB to bytes
                    disk.used = (long)(Double.parseDouble(diskUsedStr) * 1024 * 1024); // Convert MB to bytes
                    disk.free = disk.total - disk.used;
                    disk.percent = (double) disk.used / disk.total;

                    stats.setDisk(disk);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse disk information");
            }
        }

        // Temperature
        if (remarks.contains("Temperature:")) {
            int tempStart = remarks.indexOf("Temperature:") + 12;
            int tempEnd = remarks.indexOf("°C", tempStart);
            if (tempEnd > tempStart) {
                String tempStr = remarks.substring(tempStart, tempEnd).trim();
                try {
                    double temperature = Double.parseDouble(tempStr);
                    stats.setTemperature(temperature);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse Temperature: " + tempStr);
                }
            }
        }

        // Parse Uptime
        if (remarks.contains("Uptime:")) {
            int uptimeStart = remarks.indexOf("Uptime:") + 7;
            int uptimeEnd = remarks.indexOf("seconds", uptimeStart);
            if (uptimeEnd == -1) uptimeEnd = remarks.indexOf("second", uptimeStart);
            if (uptimeEnd == -1) uptimeEnd = remarks.indexOf("minutes", uptimeStart);
            if (uptimeEnd == -1) uptimeEnd = remarks.indexOf("minute", uptimeStart);
            if (uptimeEnd == -1) uptimeEnd = remarks.indexOf("hours", uptimeStart);
            if (uptimeEnd == -1) uptimeEnd = remarks.indexOf("hour", uptimeStart);
            if (uptimeEnd > uptimeStart) {
                String uptimeStr = remarks.substring(uptimeStart, uptimeEnd).trim();
                try {
                    double uptime = Double.parseDouble(uptimeStr);
                    // Convert to seconds if needed based on unit
                    if (remarks.substring(uptimeEnd).trim().startsWith("minute")) {
                        uptime *= 60;
                    } else if (remarks.substring(uptimeEnd).trim().startsWith("hour")) {
                        uptime *= 3600;
                    }
                    stats.setUptime(uptime);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse Uptime: " + uptimeStr);
                }
            }
        }

        // Parse Pluto Temperature
        if (remarks.contains("Pluto Temp:")) {
            Log.d(TAG, "Found Pluto Temp in remarks");
            int plutoTempStart = remarks.indexOf("Pluto Temp:") + 11;

            // Skip whitespace
            while (plutoTempStart < remarks.length() && Character.isWhitespace(remarks.charAt(plutoTempStart))) {
                plutoTempStart++;
            }

            int plutoTempEnd = plutoTempStart;

            // Find the end of the number
            while (plutoTempEnd < remarks.length() &&
                    Character.isDigit(remarks.charAt(plutoTempEnd))) {
                plutoTempEnd++;
            }

            if (plutoTempEnd > plutoTempStart) {
                String plutoTempStr = remarks.substring(plutoTempStart, plutoTempEnd).trim();
                Log.d(TAG, "Extracted Pluto Temp string: '" + plutoTempStr + "'");

                try {
                    double plutoTemp = Double.parseDouble(plutoTempStr);
                    antStats.setPlutoTemp(plutoTemp);
                    Log.d(TAG, "Successfully parsed Pluto Temp: " + plutoTemp);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse Pluto Temp: " + plutoTempStr + ", Error: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "No valid Pluto Temp value found after the label");
            }
        } else {
            Log.d(TAG, "No Pluto Temp found in remarks");
        }

        // Parse Zynq Temperature
        if (remarks.contains("Zynq Temp:")) {
            Log.d(TAG, "Found Zynq Temp in remarks");
            int zynqTempStart = remarks.indexOf("Zynq Temp:") + 10;

            // Skip whitespace
            while (zynqTempStart < remarks.length() && Character.isWhitespace(remarks.charAt(zynqTempStart))) {
                zynqTempStart++;
            }

            int zynqTempEnd = zynqTempStart;

            // Find the end of the number
            while (zynqTempEnd < remarks.length() &&
                    Character.isDigit(remarks.charAt(zynqTempEnd))) {
                zynqTempEnd++;
            }

            if (zynqTempEnd > zynqTempStart) {
                String zynqTempStr = remarks.substring(zynqTempStart, zynqTempEnd).trim();
                Log.d(TAG, "Extracted Zynq Temp string: '" + zynqTempStr + "'");

                try {
                    double zynqTemp = Double.parseDouble(zynqTempStr);
                    antStats.setZynqTemp(zynqTemp);
                    Log.d(TAG, "Successfully parsed Zynq Temp: " + zynqTemp);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse Zynq Temp: " + zynqTempStr + ", Error: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "No valid Zynq Temp value found after the label");
            }
        } else {
            Log.d(TAG, "No Zynq Temp found in remarks");
        }

        if (antStats.getPlutoTemp() > 0 || antStats.getZynqTemp() > 0) {
            Log.d(TAG, "Setting ANT stats - Pluto: " + antStats.getPlutoTemp() + ", Zynq: " + antStats.getZynqTemp());
            statusMessage.setAntStats(antStats);
        } else {
            Log.d(TAG, "No ANT stats set - values were zero");
        }

        // Create the message
        stats.setMemory(memory);
        statusMessage.setSystemStats(stats);

        // Add ANT stats if parsed
        if (antStats.getPlutoTemp() > 0 || antStats.getZynqTemp() > 0) {
            statusMessage.setAntStats(antStats);
        }

        // Add GPS data from the CoT message
        if (cotMessage.getCoordinate() != null) {
            StatusMessage.GPSData gpsData = new StatusMessage.GPSData();
            gpsData.setLatitude(cotMessage.getCoordinate().getLatitude());
            gpsData.setLongitude(cotMessage.getCoordinate().getLongitude());

            if (cotMessage.getAlt() != null && !cotMessage.getAlt().isEmpty()) {
                try {
                    gpsData.setAltitude(Double.parseDouble(cotMessage.getAlt()));
                } catch (NumberFormatException e) {
                    // Ignore parse errors TODO - sometime
                }
            }

            if (cotMessage.getSpeed() != null && !cotMessage.getSpeed().isEmpty()) {
                try {
                    gpsData.setSpeed(Double.parseDouble(cotMessage.getSpeed()));
                } catch (NumberFormatException e) {
                    // Ignore parse errors TODO - lowpri
                }
            }

            statusMessage.setGpsData(gpsData);
        }

        // Set the status message in the result
        result.statusMessage = statusMessage;
    }

    private ParseResult parseESP32Format(JsonObject json) {
        ParseResult result = new ParseResult();

        // Check if this is an ESP32 status message format
        if (json.has("serial_number") && json.has("system_stats")) {
            // This is a status message, not a drone telemetry message
            StatusMessage statusMessage = new StatusMessage();

            // Set serial number
            statusMessage.setSerialNumber(json.get("serial_number").getAsString());

            // Parse system stats
            JsonObject systemStats = json.getAsJsonObject("system_stats");
            StatusMessage.SystemStats stats = new StatusMessage.SystemStats();

            if (systemStats.has("temperature")) {
                stats.setTemperature(systemStats.get("temperature").getAsDouble());
            }

            if (systemStats.has("uptime")) {
                stats.setUptime(systemStats.get("uptime").getAsDouble());
            }

            // Parse memory stats
            if (systemStats.has("memory")) {
                JsonObject memoryJson = systemStats.getAsJsonObject("memory");
                StatusMessage.SystemStats.MemoryStats memory = new StatusMessage.SystemStats.MemoryStats();

                if (memoryJson.has("total")) {
                    memory.setTotal(memoryJson.get("total").getAsLong());
                }
                if (memoryJson.has("used")) {
                    memory.setUsed(memoryJson.get("used").getAsLong());
                }
                if (memoryJson.has("available")) {
                    memory.setFree(memoryJson.get("available").getAsLong());
                }
                if (memoryJson.has("percent")) {
                    memory.setPercent(memoryJson.get("percent").getAsDouble());
                }

                stats.setMemory(memory);
            }

            statusMessage.setSystemStats(stats);
            result.statusMessage = statusMessage;

            return result;
        }


        CoTMessage cotMessage = new CoTMessage();
        Map<String, Object> rawData = new HashMap<>();

        // Basic ID info
        if (json.has("Basic ID")) {
            JsonObject basicId = json.getAsJsonObject("Basic ID");
            if (basicId.has("id")) {
                String id = basicId.get("id").getAsString();
                cotMessage.setUid(id);
                rawData.put("uid", id);
            }
            if (basicId.has("MAC")) {
                String mac = basicId.get("MAC").getAsString();
                cotMessage.setMac(mac);
                rawData.put("mac", mac);

                // Detect manufacturer from MAC
                String manufacturer = findManufacturer(mac);
                cotMessage.setManufacturer(manufacturer);
                rawData.put("manufacturer", manufacturer);
            }
            if (basicId.has("id_type")) {
                String idType = basicId.get("id_type").getAsString();
                cotMessage.setIdType(idType);
                rawData.put("idType", idType);
            }

            // Map ua_type if present
            if (basicId.has("ua_type")) {
                try {
                    int uaTypeValue = basicId.get("ua_type").getAsInt();
                    DroneSignature.IdInfo.UAType uaType = mapUAType(uaTypeValue);
                    cotMessage.setUaType(uaType);
                    rawData.put("uaType", uaType);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse UA type: " + e.getMessage());
                }
            }
        }

        // Location/Vector info
        if (json.has("Location/Vector Message")) {
            JsonObject locationVector = json.getAsJsonObject("Location/Vector Message");
            if (locationVector.has("latitude")) {
                String lat = String.valueOf(locationVector.get("latitude").getAsDouble());
                cotMessage.setLat(lat);
                rawData.put("lat", lat);
            }
            if (locationVector.has("longitude")) {
                String lon = String.valueOf(locationVector.get("longitude").getAsDouble());
                cotMessage.setLon(lon);
                rawData.put("lon", lon);
            }
            if (locationVector.has("speed")) {
                String speed = String.valueOf(locationVector.get("speed").getAsDouble());
                cotMessage.setSpeed(speed);
                rawData.put("speed", speed);
            }
            if (locationVector.has("vert_speed")) {
                String vspeed = String.valueOf(locationVector.get("vert_speed").getAsDouble());
                cotMessage.setVspeed(vspeed);
                rawData.put("vspeed", vspeed);
            }
            if (locationVector.has("geodetic_altitude")) {
                String alt = String.valueOf(locationVector.get("geodetic_altitude").getAsDouble());
                cotMessage.setAlt(alt);
                rawData.put("alt", alt);
            }
            if (locationVector.has("height_agl")) {
                String height = String.valueOf(locationVector.get("height_agl").getAsDouble());
                cotMessage.setHeight(height);
                rawData.put("height", height);
            }
            if (locationVector.has("direction")) {
                String direction = String.valueOf(locationVector.get("direction").getAsDouble());
                cotMessage.setDirection(direction);
                rawData.put("direction", direction);
            }
            if (locationVector.has("status")) {
                String status = String.valueOf(locationVector.get("status").getAsInt());
                cotMessage.setStatus(status);
                rawData.put("status", status);
            }
            if (locationVector.has("horiz_acc")) {
                String horizAcc = String.valueOf(locationVector.get("horiz_acc").getAsInt());
                cotMessage.setHorizontalAccuracy(horizAcc);
                rawData.put("horizontal_accuracy", horizAcc);
            }
            if (locationVector.has("vert_acc")) {
                String vertAcc = String.valueOf(locationVector.get("vert_acc").getAsInt());
                cotMessage.setVerticalAccuracy(vertAcc);
                rawData.put("vertical_accuracy", vertAcc);
            }
            if (locationVector.has("baro_acc")) {
                String baroAcc = String.valueOf(locationVector.get("baro_acc").getAsInt());
                cotMessage.setBaroAccuracy(baroAcc);
                rawData.put("baro_accuracy", baroAcc);
            }
            if (locationVector.has("speed_acc")) {
                String speedAcc = String.valueOf(locationVector.get("speed_acc").getAsInt());
                cotMessage.setSpeedAccuracy(speedAcc);
                rawData.put("speed_accuracy", speedAcc);
            }
            if (locationVector.has("timestamp")) {
                String timestamp = String.valueOf(locationVector.get("timestamp").getAsLong());
                cotMessage.setTimestamp(timestamp);
                rawData.put("timestamp", timestamp);
            }
        }

        // Self-ID info
        if (json.has("Self-ID Message")) {
            JsonObject selfId = json.getAsJsonObject("Self-ID Message");
            if (selfId.has("text")) {
                String text = selfId.get("text").getAsString();
                cotMessage.setSelfIDText(text);
                rawData.put("selfIDText", text);
            }
            if (selfId.has("description")) {
                String description = selfId.get("description").getAsString();
                cotMessage.setDescription(description);
                rawData.put("description", description);
            }
            if (selfId.has("description_type")) {
                String selfIdType = String.valueOf(selfId.get("description_type").getAsInt());
                cotMessage.setSelfIdType(selfIdType);
                rawData.put("selfIdType", selfIdType);
            }
        }

        // Extract System Message info (including operator location)
        if (json.has("System Message")) {
            JsonObject sysMsg = json.getAsJsonObject("System Message");
            if (sysMsg.has("operator_lat")) {
                String pilotLat = String.valueOf(sysMsg.get("operator_lat").getAsDouble());
                cotMessage.setPilotLat(pilotLat);
                rawData.put("pilotLat", pilotLat);
            }
            if (sysMsg.has("operator_lon")) {
                String pilotLon = String.valueOf(sysMsg.get("operator_lon").getAsDouble());
                cotMessage.setPilotLon(pilotLon);
                rawData.put("pilotLon", pilotLon);
            }
            // Check for home location (primarily for DJI)
            if (sysMsg.has("home_lat")) {
                String homeLat = String.valueOf(sysMsg.get("home_lat").getAsDouble());
                cotMessage.setHomeLat(homeLat);
                rawData.put("homeLat", homeLat);
            }
            if (sysMsg.has("home_lon")) {
                String homeLon = String.valueOf(sysMsg.get("home_lon").getAsDouble());
                cotMessage.setHomeLon(homeLon);
                rawData.put("homeLon", homeLon);
            }
            // Additional system message data
            if (sysMsg.has("area_count")) {
                String areaCount = String.valueOf(sysMsg.get("area_count").getAsInt());
                cotMessage.setAreaCount(areaCount);
                rawData.put("area_count", areaCount);
            }
            if (sysMsg.has("area_radius")) {
                String areaRadius = String.valueOf(sysMsg.get("area_radius").getAsInt());
                cotMessage.setAreaRadius(areaRadius);
                rawData.put("area_radius", areaRadius);
            }
            if (sysMsg.has("area_ceiling")) {
                String areaCeiling = String.valueOf(sysMsg.get("area_ceiling").getAsInt());
                cotMessage.setAreaCeiling(areaCeiling);
                rawData.put("area_ceiling", areaCeiling);
            }
            if (sysMsg.has("area_floor")) {
                String areaFloor = String.valueOf(sysMsg.get("area_floor").getAsInt());
                cotMessage.setAreaFloor(areaFloor);
                rawData.put("area_floor", areaFloor);
            }
            if (sysMsg.has("operator_alt_geo")) {
                String operatorAltGeo = String.valueOf(sysMsg.get("operator_alt_geo").getAsInt());
                cotMessage.setOperatorAltGeo(operatorAltGeo);
                rawData.put("operatorAltGeo", operatorAltGeo);
            }
            if (sysMsg.has("classification")) {
                String classification = String.valueOf(sysMsg.get("classification").getAsInt());
                cotMessage.setClassification(classification);
                rawData.put("classification", classification);
            }
        }

        // Extract Auth Message if present
        if (json.has("Auth Message")) {
            JsonObject authMsg = json.getAsJsonObject("Auth Message");
            if (authMsg.has("type")) {
                String authType = String.valueOf(authMsg.get("type").getAsInt());
                cotMessage.setAuthType(authType);
                rawData.put("authType", authType);
            }
            if (authMsg.has("page")) {
                String authPage = String.valueOf(authMsg.get("page").getAsInt());
                cotMessage.setAuthPage(authPage);
                rawData.put("authPage", authPage);
            }
            if (authMsg.has("length")) {
                String authLength = String.valueOf(authMsg.get("length").getAsInt());
                cotMessage.setAuthLength(authLength);
                rawData.put("authLength", authLength);
            }
            if (authMsg.has("timestamp")) {
                String authTimestamp = String.valueOf(authMsg.get("timestamp").getAsLong());
                cotMessage.setAuthTimestamp(authTimestamp);
                rawData.put("authTimestamp", authTimestamp);
            }
            if (authMsg.has("data")) {
                String authData = authMsg.get("data").getAsString();
                cotMessage.setAuthData(authData);
                rawData.put("authData", authData);
            }
        }

        // Extract metadata from the top level (specific to ESP32 format)
        if (json.has("index")) {
            String index = String.valueOf(json.get("index").getAsInt());
            cotMessage.setIndex(index);
            rawData.put("index", index);
        }
        if (json.has("runtime")) {
            String runtime = String.valueOf(json.get("runtime").getAsInt());
            cotMessage.setRuntime(runtime);
            rawData.put("runtime", runtime);
        }

        // Check if we have any signal source info to add
        if (cotMessage.getMac() != null && !cotMessage.getMac().isEmpty() && cotMessage.getRssi() != null) {
            long timestamp = System.currentTimeMillis();
            try {
                if (cotMessage.getTimestamp() != null) {
                    timestamp = Long.parseLong(cotMessage.getTimestamp());
                }
            } catch (NumberFormatException e) {
                // Use current timestamp as fallback
            }

            CoTMessage.SignalSource source = new CoTMessage.SignalSource(
                    cotMessage.getMac(),
                    cotMessage.getRssi(),
                    CoTMessage.SignalSource.SignalType.WIFI, // ESP32 is always WIFI
                    timestamp
            );
            cotMessage.getSignalSources().add(source);
        }

        // Store the raw data for future access
        cotMessage.setRawMessage(rawData);
        result.cotMessage = cotMessage;

        return result;
    }

    private ParseResult parseDroneArrayFormat(JsonArray jsonArray) {
        ParseResult result = new ParseResult();
        CoTMessage cotMessage = new CoTMessage();
        Map<String, Object> rawData = new HashMap<>();

        // Process each element in the array
        for (JsonElement element : jsonArray) {
            if (!element.isJsonObject()) continue;

            JsonObject obj = element.getAsJsonObject();

            // Process Basic ID
            if (obj.has("Basic ID")) {
                JsonObject basicId = obj.getAsJsonObject("Basic ID");
                if (basicId.has("id")) {
                    String id = basicId.get("id").getAsString();
                    cotMessage.setUid(id);
                    rawData.put("uid", id);
                }
                if (basicId.has("MAC")) {
                    String mac = basicId.get("MAC").getAsString();
                    cotMessage.setMac(mac);
                    rawData.put("mac", mac);

                    // Detect manufacturer
                    String manufacturer = findManufacturer(mac);
                    cotMessage.setManufacturer(manufacturer);
                    rawData.put("manufacturer", manufacturer);
                }
                if (basicId.has("RSSI")) {
                    Integer rssi = basicId.get("RSSI").getAsInt();
                    cotMessage.setRssi(rssi);
                    rawData.put("rssi", rssi);
                }
                if (basicId.has("id_type")) {
                    String idType = basicId.get("id_type").getAsString();
                    cotMessage.setIdType(idType);
                    rawData.put("idType", idType);
                }
                if (basicId.has("description")) {
                    String description = basicId.get("description").getAsString();
                    cotMessage.setDescription(description);
                    rawData.put("description", description);
                }
                if (basicId.has("ua_type")) {
                    if (basicId.get("ua_type").isJsonPrimitive()) {
                        // Handle numeric ua_type
                        if (basicId.get("ua_type").getAsJsonPrimitive().isNumber()) {
                            int uaTypeValue = basicId.get("ua_type").getAsInt();
                            cotMessage.setUaType(mapUAType(uaTypeValue));
                        } else {
                            // Handle string ua_type like "Helicopter (or Multirotor)"
                            String uaTypeStr = basicId.get("ua_type").getAsString();
                            cotMessage.setUaType(mapUATypeFromString(uaTypeStr));
                        }
                        rawData.put("uaType", cotMessage.getUaType());
                    }
                }

                // Add signal source if we have MAC and RSSI
                if (cotMessage.getMac() != null && !cotMessage.getMac().isEmpty() && cotMessage.getRssi() != null) {
                    CoTMessage.SignalSource source = new CoTMessage.SignalSource(
                            cotMessage.getMac(),
                            cotMessage.getRssi(),
                            determineSignalType(basicId),
                            System.currentTimeMillis()
                    );
                    cotMessage.getSignalSources().add(source);
                }
            }

            // Process Location/Vector Message
            else if (obj.has("Location/Vector Message")) {
                JsonObject locationVector = obj.getAsJsonObject("Location/Vector Message");

                // Handle latitude - could be a number or string
                if (locationVector.has("latitude")) {
                    JsonElement latElement = locationVector.get("latitude");
                    if (latElement.isJsonPrimitive()) {
                        if (latElement.getAsJsonPrimitive().isNumber()) {
                            String lat = String.valueOf(latElement.getAsDouble());
                            cotMessage.setLat(lat);
                            rawData.put("lat", lat);
                        } else {
                            String lat = latElement.getAsString();
                            cotMessage.setLat(lat);
                            rawData.put("lat", lat);
                        }
                    }
                }

                // Handle longitude - could be a number or string
                if (locationVector.has("longitude")) {
                    JsonElement lonElement = locationVector.get("longitude");
                    if (lonElement.isJsonPrimitive()) {
                        if (lonElement.getAsJsonPrimitive().isNumber()) {
                            String lon = String.valueOf(lonElement.getAsDouble());
                            cotMessage.setLon(lon);
                            rawData.put("lon", lon);
                        } else {
                            String lon = lonElement.getAsString();
                            cotMessage.setLon(lon);
                            rawData.put("lon", lon);
                        }
                    }
                }

                // Process speed - could be a number or string like "0.25 m/s"
                if (locationVector.has("speed")) {
                    JsonElement speedElement = locationVector.get("speed");
                    if (speedElement.isJsonPrimitive()) {
                        if (speedElement.getAsJsonPrimitive().isNumber()) {
                            String speed = String.valueOf(speedElement.getAsDouble());
                            cotMessage.setSpeed(speed);
                            rawData.put("speed", speed);
                        } else {
                            String speedStr = speedElement.getAsString();
                            if (speedStr.contains(" ")) {
                                speedStr = speedStr.split(" ")[0]; // Extract numeric part
                            }
                            cotMessage.setSpeed(speedStr);
                            rawData.put("speed", speedStr);
                        }
                    }
                }

                // Process vertical speed - could be a number or string
                if (locationVector.has("vert_speed")) {
                    JsonElement vspeedElement = locationVector.get("vert_speed");
                    if (vspeedElement.isJsonPrimitive()) {
                        if (vspeedElement.getAsJsonPrimitive().isNumber()) {
                            String vspeed = String.valueOf(vspeedElement.getAsDouble());
                            cotMessage.setVspeed(vspeed);
                            rawData.put("vspeed", vspeed);
                        } else {
                            String vspeedStr = vspeedElement.getAsString();
                            if (vspeedStr.contains(" ")) {
                                vspeedStr = vspeedStr.split(" ")[0]; // Extract numeric part
                            }
                            cotMessage.setVspeed(vspeedStr);
                            rawData.put("vspeed", vspeedStr);
                        }
                    }
                }

                // Process altitude - geodetic_altitude
                if (locationVector.has("geodetic_altitude")) {
                    JsonElement altElement = locationVector.get("geodetic_altitude");
                    if (altElement.isJsonPrimitive()) {
                        if (altElement.getAsJsonPrimitive().isNumber()) {
                            String alt = String.valueOf(altElement.getAsDouble());
                            cotMessage.setAlt(alt);
                            rawData.put("alt", alt);
                        } else {
                            String altStr = altElement.getAsString();
                            if (altStr.contains(" ")) {
                                altStr = altStr.split(" ")[0]; // Extract numeric part
                            }
                            cotMessage.setAlt(altStr);
                            rawData.put("alt", altStr);
                        }
                    }
                }

                // Process height - height_agl
                if (locationVector.has("height_agl")) {
                    JsonElement heightElement = locationVector.get("height_agl");
                    if (heightElement.isJsonPrimitive()) {
                        if (heightElement.getAsJsonPrimitive().isNumber()) {
                            String height = String.valueOf(heightElement.getAsDouble());
                            cotMessage.setHeight(height);
                            rawData.put("height", height);
                        } else {
                            String heightStr = heightElement.getAsString();
                            if (heightStr.contains(" ")) {
                                heightStr = heightStr.split(" ")[0]; // Extract numeric part
                            }
                            cotMessage.setHeight(heightStr);
                            rawData.put("height", heightStr);
                        }
                    }
                }

                // Additional location parameters
                if (locationVector.has("direction")) {
                    JsonElement dirElement = locationVector.get("direction");
                    if (dirElement.isJsonPrimitive()) {
                        if (dirElement.getAsJsonPrimitive().isNumber()) {
                            String direction = String.valueOf(dirElement.getAsInt());
                            cotMessage.setDirection(direction);
                            rawData.put("direction", direction);
                        } else {
                            String direction = dirElement.getAsString();
                            cotMessage.setDirection(direction);
                            rawData.put("direction", direction);
                        }
                    }
                }

                // Process protocol fields
                if (locationVector.has("protocol_version")) {
                    String protocol = locationVector.get("protocol_version").getAsString();
                    cotMessage.setLocationProtocol(protocol);
                    rawData.put("location_protocol", protocol);
                }

                if (locationVector.has("op_status")) {
                    String opStatus = locationVector.get("op_status").getAsString();
                    cotMessage.setOpStatus(opStatus);
                    rawData.put("op_status", opStatus);
                }

                if (locationVector.has("height_type")) {
                    String heightType = locationVector.get("height_type").getAsString();
                    cotMessage.setHeightType(heightType);
                    rawData.put("height_type", heightType);
                }

                if (locationVector.has("ew_dir_segment")) {
                    String ewDirSegment = locationVector.get("ew_dir_segment").getAsString();
                    cotMessage.setEwDirSegment(ewDirSegment);
                    rawData.put("ew_dir_segment", ewDirSegment);
                }

                if (locationVector.has("speed_multiplier")) {
                    String speedMultiplier = locationVector.get("speed_multiplier").getAsString();
                    cotMessage.setSpeedMultiplier(speedMultiplier);
                    rawData.put("speed_multiplier", speedMultiplier);
                }

                // Process accuracy fields
                if (locationVector.has("vertical_accuracy")) {
                    String verticalAccuracy = locationVector.get("vertical_accuracy").getAsString();
                    cotMessage.setVerticalAccuracy(verticalAccuracy);
                    rawData.put("vertical_accuracy", verticalAccuracy);
                }

                if (locationVector.has("horizontal_accuracy")) {
                    String horizontalAccuracy = locationVector.get("horizontal_accuracy").getAsString();
                    cotMessage.setHorizontalAccuracy(horizontalAccuracy);
                    rawData.put("horizontal_accuracy", horizontalAccuracy);
                }

                if (locationVector.has("baro_accuracy")) {
                    String baroAccuracy = locationVector.get("baro_accuracy").getAsString();
                    cotMessage.setBaroAccuracy(baroAccuracy);
                    rawData.put("baro_accuracy", baroAccuracy);
                }

                if (locationVector.has("speed_accuracy")) {
                    String speedAccuracy = locationVector.get("speed_accuracy").getAsString();
                    cotMessage.setSpeedAccuracy(speedAccuracy);
                    rawData.put("speed_accuracy", speedAccuracy);
                }

                // Process timestamp
                if (locationVector.has("timestamp")) {
                    String timestamp = locationVector.get("timestamp").getAsString();
                    cotMessage.setTimestamp(timestamp);
                    rawData.put("timestamp", timestamp);
                }

                if (locationVector.has("timestamp_accuracy")) {
                    String timestampAccuracy = locationVector.get("timestamp_accuracy").getAsString();
                    cotMessage.setTimestampAccuracy(timestampAccuracy);
                    rawData.put("timestamp_accuracy", timestampAccuracy);
                }
            }

            // Process Self-ID Message
            else if (obj.has("Self-ID Message")) {
                JsonObject selfId = obj.getAsJsonObject("Self-ID Message");
                if (selfId.has("text")) {
                    String text = selfId.get("text").getAsString();
                    cotMessage.setSelfIDText(text);
                    rawData.put("selfIDText", text);
                }

                if (selfId.has("text_type")) {
                    String selfIdType = selfId.get("text_type").getAsString();
                    cotMessage.setSelfIdType(selfIdType);
                    rawData.put("selfIdType", selfIdType);
                }

                // In some formats, description might also be here
                if (selfId.has("description")) {
                    String description = selfId.get("description").getAsString();
                    cotMessage.setDescription(description);
                    rawData.put("description", description);
                }

                if (selfId.has("protocol_version")) {
                    // Store protocol version if needed
                    String protocolVersion = selfId.get("protocol_version").getAsString();
                    rawData.put("selfIdProtocolVersion", protocolVersion);
                }
            }

            // Process System Message (including operator location)
            else if (obj.has("System Message")) {
                JsonObject sysMsg = obj.getAsJsonObject("System Message");

                // Process operator (pilot) location
                // For DJI, pilot location is in System Message as latitude/longitude
                if (sysMsg.has("latitude")) {
                    JsonElement pilotLatElement = sysMsg.get("latitude");
                    if (pilotLatElement.isJsonPrimitive()) {
                        if (pilotLatElement.getAsJsonPrimitive().isNumber()) {
                            String pilotLat = String.valueOf(pilotLatElement.getAsDouble());
                            cotMessage.setPilotLat(pilotLat);
                            rawData.put("pilotLat", pilotLat);
                        } else {
                            String pilotLat = pilotLatElement.getAsString();
                            cotMessage.setPilotLat(pilotLat);
                            rawData.put("pilotLat", pilotLat);
                        }
                    }
                }

                if (sysMsg.has("longitude")) {
                    JsonElement pilotLonElement = sysMsg.get("longitude");
                    if (pilotLonElement.isJsonPrimitive()) {
                        if (pilotLonElement.getAsJsonPrimitive().isNumber()) {
                            String pilotLon = String.valueOf(pilotLonElement.getAsDouble());
                            cotMessage.setPilotLon(pilotLon);
                            rawData.put("pilotLon", pilotLon);
                        } else {
                            String pilotLon = pilotLonElement.getAsString();
                            cotMessage.setPilotLon(pilotLon);
                            rawData.put("pilotLon", pilotLon);
                        }
                    }
                }

                // Process home location (primarily for DJI)
                if (sysMsg.has("home_lat")) {
                    JsonElement homeLatElement = sysMsg.get("home_lat");
                    if (homeLatElement.isJsonPrimitive()) {
                        if (homeLatElement.getAsJsonPrimitive().isNumber()) {
                            String homeLat = String.valueOf(homeLatElement.getAsDouble());
                            cotMessage.setHomeLat(homeLat);
                            rawData.put("homeLat", homeLat);
                        } else {
                            String homeLat = homeLatElement.getAsString();
                            cotMessage.setHomeLat(homeLat);
                            rawData.put("homeLat", homeLat);
                        }
                    }
                }

                if (sysMsg.has("home_lon")) {
                    JsonElement homeLonElement = sysMsg.get("home_lon");
                    if (homeLonElement.isJsonPrimitive()) {
                        if (homeLonElement.getAsJsonPrimitive().isNumber()) {
                            String homeLon = String.valueOf(homeLonElement.getAsDouble());
                            cotMessage.setHomeLon(homeLon);
                            rawData.put("homeLon", homeLon);
                        } else {
                            String homeLon = homeLonElement.getAsString();
                            cotMessage.setHomeLon(homeLon);
                            rawData.put("homeLon", homeLon);
                        }
                    }
                }

                // Additional system fields
                if (sysMsg.has("operator_location_type")) {
                    String operatorLocationType = sysMsg.get("operator_location_type").getAsString();
                    cotMessage.setOperatorLocationType(operatorLocationType);
                    rawData.put("operator_location_type", operatorLocationType);
                }

                if (sysMsg.has("classification_type")) {
                    String classificationType = sysMsg.get("classification_type").getAsString();
                    cotMessage.setClassificationType(classificationType);
                    rawData.put("classification_type", classificationType);
                }

                if (sysMsg.has("area_count")) {
                    JsonElement areaCountElement = sysMsg.get("area_count");
                    if (areaCountElement.isJsonPrimitive()) {
                        String areaCount = areaCountElement.getAsString();
                        cotMessage.setAreaCount(areaCount);
                        rawData.put("area_count", areaCount);
                    }
                }

                if (sysMsg.has("area_radius")) {
                    JsonElement areaRadiusElement = sysMsg.get("area_radius");
                    if (areaRadiusElement.isJsonPrimitive()) {
                        String areaRadius = areaRadiusElement.getAsString();
                        cotMessage.setAreaRadius(areaRadius);
                        rawData.put("area_radius", areaRadius);
                    }
                }

                if (sysMsg.has("area_ceiling")) {
                    JsonElement areaCeilingElement = sysMsg.get("area_ceiling");
                    if (areaCeilingElement.isJsonPrimitive()) {
                        String areaCeiling = areaCeilingElement.getAsString();
                        cotMessage.setAreaCeiling(areaCeiling);
                        rawData.put("area_ceiling", areaCeiling);
                    }
                }

                if (sysMsg.has("area_floor")) {
                    JsonElement areaFloorElement = sysMsg.get("area_floor");
                    if (areaFloorElement.isJsonPrimitive()) {
                        String areaFloor = areaFloorElement.getAsString();
                        cotMessage.setAreaFloor(areaFloor);
                        rawData.put("area_floor", areaFloor);
                    }
                }

                if (sysMsg.has("geodetic_altitude")) {
                    JsonElement altElement = sysMsg.get("geodetic_altitude");
                    if (altElement.isJsonPrimitive()) {
                        // Use this if we don't already have an altitude
                        if (cotMessage.getAlt() == null || cotMessage.getAlt().isEmpty()) {
                            if (altElement.getAsJsonPrimitive().isNumber()) {
                                String alt = String.valueOf(altElement.getAsDouble());
                                cotMessage.setAlt(alt);
                                rawData.put("alt", alt);
                            } else {
                                String altStr = altElement.getAsString();
                                if (altStr.contains(" ")) {
                                    altStr = altStr.split(" ")[0];
                                }
                                cotMessage.setAlt(altStr);
                                rawData.put("alt", altStr);
                            }
                        }
                    }
                }

                if (sysMsg.has("timestamp") || sysMsg.has("timestamp_raw")) {
                    // Use timestamp from system message if we don't already have one
                    if (cotMessage.getTimestamp() == null || cotMessage.getTimestamp().isEmpty()) {
                        if (sysMsg.has("timestamp_raw")) {
                            String timestamp = String.valueOf(sysMsg.get("timestamp_raw").getAsLong());
                            cotMessage.setTimestamp(timestamp);
                            rawData.put("timestamp", timestamp);
                        } else {
                            String timestamp = sysMsg.get("timestamp").getAsString();
                            cotMessage.setTimestamp(timestamp);
                            rawData.put("timestamp", timestamp);
                        }
                    }
                }
            }

            // Process Operator ID Message
            else if (obj.has("Operator ID Message")) {
                JsonObject opIdMsg = obj.getAsJsonObject("Operator ID Message");
                if (opIdMsg.has("operator_id")) {
                    String operatorId = opIdMsg.get("operator_id").getAsString();
                    cotMessage.setOperatorId(operatorId);
                    rawData.put("operator_id", operatorId);
                }
                if (opIdMsg.has("operator_id_type")) {
                    String operatorIdType = opIdMsg.get("operator_id_type").getAsString();
                    cotMessage.setOperatorIdType(operatorIdType);
                    rawData.put("operator_id_type", operatorIdType);
                }
                if (opIdMsg.has("protocol_version")) {
                    // Store protocol version if needed
                    String protocolVersion = opIdMsg.get("protocol_version").getAsString();
                    rawData.put("operatorIdProtocolVersion", protocolVersion);
                }
            }

            // Process Authentication Message
            else if (obj.has("Authentication Message") || obj.has("Auth Message")) {
                JsonObject authMsg = obj.has("Authentication Message") ?
                        obj.getAsJsonObject("Authentication Message") :
                        obj.getAsJsonObject("Auth Message");

                if (authMsg.has("auth_type")) {
                    String authType = authMsg.get("auth_type").getAsString();
                    cotMessage.setAuthType(authType);
                    rawData.put("authType", authType);
                }
                if (authMsg.has("page_number")) {
                    String authPage = String.valueOf(authMsg.get("page_number").getAsInt());
                    cotMessage.setAuthPage(authPage);
                    rawData.put("authPage", authPage);
                }
                if (authMsg.has("last_page_index")) {
                    String authLength = String.valueOf(authMsg.get("last_page_index").getAsInt());
                    cotMessage.setAuthLength(authLength);
                    rawData.put("authLength", authLength);
                }
                if (authMsg.has("timestamp_raw")) {
                    String authTimestamp = String.valueOf(authMsg.get("timestamp_raw").getAsLong());
                    cotMessage.setAuthTimestamp(authTimestamp);
                    rawData.put("authTimestamp", authTimestamp);
                } else if (authMsg.has("timestamp")) {
                    String authTimestamp = authMsg.get("timestamp").getAsString();
                    cotMessage.setAuthTimestamp(authTimestamp);
                    rawData.put("authTimestamp", authTimestamp);
                }
                if (authMsg.has("auth_data")) {
                    String authData = authMsg.get("auth_data").getAsString();
                    cotMessage.setAuthData(authData);
                    rawData.put("authData", authData);
                }
                if (authMsg.has("protocol_version")) {
                    // Store protocol version if needed
                    String protocolVersion = authMsg.get("protocol_version").getAsString();
                    rawData.put("authProtocolVersion", protocolVersion);
                }
            }
        }

        // Store the raw data for future access
        cotMessage.setRawMessage(rawData);
        result.cotMessage = cotMessage;

        return result;
    }

    private ParseResult parseXML(String xmlStr) {
        ParseResult result = new ParseResult();
        XmlPullParser parser = Xml.newPullParser();

        String currentElement = "";
        ArrayList<String> elementStack = new ArrayList<>();
        Map<String, String> eventAttributes = new HashMap<>();
        Map<String, String> pointAttributes = new HashMap<>();
        String remarks = "";
        boolean isStatusNode = false;

        try {
            parser.setInput(new StringReader(xmlStr));
            int eventType = parser.getEventType();

            CoTMessage cotMessage = new CoTMessage();
            Map<String, Object> rawData = new HashMap<>();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentElement = parser.getName();
                        elementStack.add(currentElement);

                        if (currentElement.equals("event")) {
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attrName = parser.getAttributeName(i);
                                String attrValue = parser.getAttributeValue(i);
                                eventAttributes.put(attrName, attrValue);

                                // Check if this is a status node based on type
                                if (attrName.equals("type") && attrValue.equals("b-m-p-s-m")) {
                                    isStatusNode = true;
                                }

                                // Extract UID from event attributes
                                switch (attrName) {
                                    case "uid" -> {
                                        // Remove "drone-" prefix if present
                                        String uid = attrValue;
                                        if (uid.startsWith("drone-")) {
                                            uid = uid.substring(6);
                                        }
                                        cotMessage.setUid(uid);
                                        rawData.put("uid", uid);
                                    }
                                    case "time" -> {
                                        cotMessage.setTime(attrValue);
                                        rawData.put("time", attrValue);
                                    }
                                    case "start" -> {
                                        cotMessage.setStart(attrValue);
                                        rawData.put("start", attrValue);
                                    }
                                    case "stale" -> {
                                        cotMessage.setStale(attrValue);
                                        rawData.put("stale", attrValue);
                                    }
                                    case "how" -> {
                                        cotMessage.setHow(attrValue);
                                        rawData.put("how", attrValue);
                                    }
                                    case "type" -> {
                                        cotMessage.setType(attrValue);
                                        rawData.put("type", attrValue);
                                    }
                                }
                            }
                        } else if (currentElement.equals("point")) {
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                String attrName = parser.getAttributeName(i);
                                String attrValue = parser.getAttributeValue(i);
                                pointAttributes.put(attrName, attrValue);

                                // Extract location from point attributes
                                switch (attrName) {
                                    case "lat" -> {
                                        cotMessage.setLat(attrValue);
                                        rawData.put("lat", attrValue);
                                    }
                                    case "lon" -> {
                                        cotMessage.setLon(attrValue);
                                        rawData.put("lon", attrValue);
                                    }
                                    case "hae" -> {
                                        cotMessage.setAlt(attrValue);
                                        rawData.put("alt", attrValue);
                                        cotMessage.setHae(attrValue);
                                        rawData.put("hae", attrValue);
                                    }
                                    case "ce" -> {
                                        cotMessage.setCe(attrValue);
                                        rawData.put("ce", attrValue);
                                    }
                                    case "le" -> {
                                        cotMessage.setLe(attrValue);
                                        rawData.put("le", attrValue);
                                    }
                                }
                            }
                        }
                        break;

                    case XmlPullParser.TEXT:
                        String text = parser.getText();
                        if (currentElement.equals("remarks")) {
                            remarks += text;
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if (parser.getName().equals("remarks") && !remarks.isEmpty()) {
                            // Parse remarks for additional data
                            parseRemarks(remarks, cotMessage, rawData);

                            // Check if this contains status information
                            if (remarks.contains("CPU Usage:") || remarks.contains("Memory Total:") ||
                                    remarks.contains("Temperature:")) {
                                extractStatusFromRemarks(remarks, cotMessage, result);
                            }

                            remarks = "";
                        }
                        break;
                }
                eventType = parser.next();
            }

            // Store the raw data and finalize the CoT message
            cotMessage.setRawMessage(rawData);

            // Only set the cotMessage in result if this is not a status node
            if (!isStatusNode || result.statusMessage == null) {
                result.cotMessage = cotMessage;
            }

        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing XML: " + e.getMessage());
            result.error = e.getMessage();
        }

        return result;
    }

    private void parseRemarks(String remarks, CoTMessage cotMessage, Map<String, Object> rawData) {
        if (remarks == null || remarks.isEmpty()) return;

        // Extract MAC address
        if (remarks.contains("MAC:")) {
            int macStart = remarks.indexOf("MAC:") + 5;
            int macEnd = remarks.indexOf(",", macStart);
            if (macEnd == -1) macEnd = remarks.length();
            String mac = remarks.substring(macStart, macEnd).trim();
            if (!mac.equals("None")) {
                cotMessage.setMac(mac);
                rawData.put("mac", mac);

                // Detect manufacturer
                String manufacturer = findManufacturer(mac);
                cotMessage.setManufacturer(manufacturer);
                rawData.put("manufacturer", manufacturer);
            }
        }

        // Extract RSSI
        if (remarks.contains("RSSI:")) {
            int rssiStart = remarks.indexOf("RSSI:") + 5;
            int rssiEnd = remarks.indexOf("dBm", rssiStart);
            if (rssiEnd > rssiStart) {
                String rssiStr = remarks.substring(rssiStart, rssiEnd).trim();
                if (!rssiStr.equals("None")) {
                    try {
                        Integer rssi = Integer.parseInt(rssiStr);
                        cotMessage.setRssi(rssi);
                        rawData.put("rssi", rssi);

                        // Add signal source if we have MAC
                        if (cotMessage.getMac() != null && !cotMessage.getMac().isEmpty()) {
                            CoTMessage.SignalSource source = new CoTMessage.SignalSource(
                                    cotMessage.getMac(),
                                    cotMessage.getRssi(),
                                    CoTMessage.SignalSource.SignalType.UNKNOWN, // Can't determine from XML
                                    System.currentTimeMillis()
                            );
                            cotMessage.getSignalSources().add(source);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Failed to parse RSSI: " + rssiStr);
                    }
                }
            }
        }

        // Extract Self-ID
        if (remarks.contains("Self-ID:")) {
            int selfIdStart = remarks.indexOf("Self-ID:") + 8;
            int selfIdEnd = remarks.indexOf(",", selfIdStart);
            if (selfIdEnd == -1) selfIdEnd = remarks.indexOf("]", selfIdStart);
            if (selfIdEnd == -1) selfIdEnd = remarks.length();

            String selfId = remarks.substring(selfIdStart, selfIdEnd).trim();
            cotMessage.setSelfIDText(selfId);
            rawData.put("selfIDText", selfId);
        }

        // Extract description if not already set
        if (cotMessage.getDescription() == null && remarks.contains("Description:")) {
            int descStart = remarks.indexOf("Description:") + 12;
            int descEnd = remarks.indexOf(",", descStart);
            if (descEnd == -1) descEnd = remarks.length();
            String description = remarks.substring(descStart, descEnd).trim();
            cotMessage.setDescription(description);
            rawData.put("description", description);
        }

        // Extract Location/Vector data
        if (remarks.contains("Location/Vector:")) {
            int locStart = remarks.indexOf("Location/Vector:") + 16;
            int locEnd = remarks.indexOf("]", locStart);
            if (locEnd > locStart) {
                String locData = remarks.substring(locStart, locEnd).trim();

                // Speed
                if (locData.contains("Speed:")) {
                    int speedStart = locData.indexOf("Speed:") + 6;
                    int speedEnd = locData.indexOf("m/s", speedStart);
                    if (speedEnd > speedStart) {
                        String speed = locData.substring(speedStart, speedEnd).trim();
                        cotMessage.setSpeed(speed);
                        rawData.put("speed", speed);
                    }
                }

                // Vertical Speed
                if (locData.contains("Vert Speed:")) {
                    int vSpeedStart = locData.indexOf("Vert Speed:") + 11;
                    int vSpeedEnd = locData.indexOf("m/s", vSpeedStart);
                    if (vSpeedEnd > vSpeedStart) {
                        String vspeed = locData.substring(vSpeedStart, vSpeedEnd).trim();
                        cotMessage.setVspeed(vspeed);
                        rawData.put("vspeed", vspeed);
                    }
                }

                // Geodetic Altitude
                if (locData.contains("Geodetic Altitude:")) {
                    int altStart = locData.indexOf("Geodetic Altitude:") + 18;
                    int altEnd = locData.indexOf("m", altStart);
                    if (altEnd > altStart) {
                        String alt = locData.substring(altStart, altEnd).trim();
                        cotMessage.setAlt(alt);
                        rawData.put("alt", alt);
                    }
                }

                // Height AGL
                if (locData.contains("Height AGL:")) {
                    int heightStart = locData.indexOf("Height AGL:") + 11;
                    int heightEnd = locData.indexOf("m", heightStart);
                    if (heightEnd > heightStart) {
                        String height = locData.substring(heightStart, heightEnd).trim();
                        cotMessage.setHeight(height);
                        rawData.put("height", height);
                    }
                }
            }
        }

        // Extract System data (operator location)
        if (remarks.contains("System:")) {
            int sysStart = remarks.indexOf("System:") + 8;
            int sysEnd = remarks.indexOf("]", sysStart);
            if (sysEnd > sysStart) {
                String sysData = remarks.substring(sysStart, sysEnd).trim();

                // Operator Latitude
                if (sysData.contains("Operator Lat:")) {
                    int opLatStart = sysData.indexOf("Operator Lat:") + 13;
                    int opLatEnd = sysData.indexOf(",", opLatStart);
                    if (opLatEnd == -1) opLatEnd = sysData.indexOf(" ", opLatStart);
                    if (opLatEnd > opLatStart) {
                        String pilotLat = sysData.substring(opLatStart, opLatEnd).trim();
                        cotMessage.setPilotLat(pilotLat);
                        rawData.put("pilotLat", pilotLat);
                    }
                }

                // Operator Longitude
                if (sysData.contains("Operator Lon:")) {
                    int opLonStart = sysData.indexOf("Operator Lon:") + 13;
                    int opLonEnd = sysData.indexOf(",", opLonStart);
                    if (opLonEnd == -1) opLonEnd = sysData.indexOf(" ", opLonStart);
                    if (opLonEnd == -1) opLonEnd = sysData.length();
                    if (opLonEnd > opLonStart) {
                        String pilotLon = sysData.substring(opLonStart, opLonEnd).trim();
                        cotMessage.setPilotLon(pilotLon);
                        rawData.put("pilotLon", pilotLon);
                    }
                }

                // Home Latitude (DJI)
                if (sysData.contains("Home Lat:")) {
                    int homeLatStart = sysData.indexOf("Home Lat:") + 9;
                    int homeLatEnd = sysData.indexOf(",", homeLatStart);
                    if (homeLatEnd == -1) homeLatEnd = sysData.indexOf(" ", homeLatStart);
                    if (homeLatEnd > homeLatStart) {
                        String homeLat = sysData.substring(homeLatStart, homeLatEnd).trim();
                        cotMessage.setHomeLat(homeLat);
                        rawData.put("homeLat", homeLat);
                    }
                }

                // Home Longitude (DJI)
                if (sysData.contains("Home Lon:")) {
                    int homeLonStart = sysData.indexOf("Home Lon:") + 9;
                    int homeLonEnd = sysData.indexOf(",", homeLonStart);
                    if (homeLonEnd == -1) homeLonEnd = sysData.indexOf(" ", homeLonStart);
                    if (homeLonEnd == -1) homeLonEnd = sysData.length();
                    if (homeLonEnd > homeLonStart) {
                        String homeLon = sysData.substring(homeLonStart, homeLonEnd).trim();
                        cotMessage.setHomeLon(homeLon);
                        rawData.put("homeLon", homeLon);
                    }
                }
            }
        }
    }

    private String findManufacturer(String mac) {
        if (mac == null || mac.isEmpty() || mac.equals("None")) {
            return "Unknown";
        }

        // Normalize MAC format
        mac = mac.toUpperCase().replace(":", "");

        for (Map.Entry<String, ArrayList<String>> entry : macPrefixesByManufacturer.entrySet()) {
            for (String knownPrefix : entry.getValue()) {
                String normalizedPrefix = knownPrefix.toUpperCase().replace(":", "");
                if (mac.startsWith(normalizedPrefix)) {
                    return entry.getKey();
                }
            }
        }

        return "Unknown";
    }

    private DroneSignature.IdInfo.UAType mapUAType(int uaTypeValue) {
        switch (uaTypeValue) {
            case 0: return DroneSignature.IdInfo.UAType.NONE;
            case 1: return DroneSignature.IdInfo.UAType.AEROPLANE;
            case 2: return DroneSignature.IdInfo.UAType.HELICOPTER;
            case 3: return DroneSignature.IdInfo.UAType.GYROPLANE;
            case 4: return DroneSignature.IdInfo.UAType.HYBRID_LIFT;
            case 5: return DroneSignature.IdInfo.UAType.ORNITHOPTER;
            case 6: return DroneSignature.IdInfo.UAType.GLIDER;
            case 7: return DroneSignature.IdInfo.UAType.KITE;
            case 8: return DroneSignature.IdInfo.UAType.FREE_BALLOON;
            case 9: return DroneSignature.IdInfo.UAType.CAPTIVE;
            case 10: return DroneSignature.IdInfo.UAType.AIRSHIP;
            case 11: return DroneSignature.IdInfo.UAType.FREE_FALL;
            case 12: return DroneSignature.IdInfo.UAType.ROCKET;
            case 13: return DroneSignature.IdInfo.UAType.TETHERED;
            case 14: return DroneSignature.IdInfo.UAType.GROUND_OBSTACLE;
            case 15: return DroneSignature.IdInfo.UAType.OTHER;
            default: return DroneSignature.IdInfo.UAType.OTHER;
        }
    }

    private DroneSignature.IdInfo.UAType mapUATypeFromString(String uaTypeStr) {
        if (uaTypeStr == null) return DroneSignature.IdInfo.UAType.OTHER;

        uaTypeStr = uaTypeStr.toLowerCase();

        if (uaTypeStr.contains("helicopter") || uaTypeStr.contains("multirotor")) {
            return DroneSignature.IdInfo.UAType.HELICOPTER;
        } else if (uaTypeStr.contains("airplane") || uaTypeStr.contains("aeroplane")) {
            return DroneSignature.IdInfo.UAType.AEROPLANE;
        } else if (uaTypeStr.contains("glider")) {
            return DroneSignature.IdInfo.UAType.GLIDER;
        } else if (uaTypeStr.contains("balloon")) {
            if (uaTypeStr.contains("free")) {
                return DroneSignature.IdInfo.UAType.FREE_BALLOON;
            } else {
                return DroneSignature.IdInfo.UAType.CAPTIVE;
            }
        } else if (uaTypeStr.contains("airship")) {
            return DroneSignature.IdInfo.UAType.AIRSHIP;
        } else if (uaTypeStr.contains("rocket")) {
            return DroneSignature.IdInfo.UAType.ROCKET;
        } else if (uaTypeStr.contains("kite")) {
            return DroneSignature.IdInfo.UAType.KITE;
        } else if (uaTypeStr.contains("none")) {
            return DroneSignature.IdInfo.UAType.NONE;
        } else {
            return DroneSignature.IdInfo.UAType.OTHER;
        }
    }

    private CoTMessage.SignalSource.SignalType determineSignalType(JsonObject basicId) {
        // Try to determine signal type based on available information
        if (basicId.has("protocol_version")) {
            String protocol = basicId.get("protocol_version").getAsString();
            if (protocol.contains("BT") || protocol.contains("F3411")) {
                return CoTMessage.SignalSource.SignalType.BLUETOOTH;
            }
        }

        // Check for manufacturer or other hints
        if (basicId.has("description")) {
            String desc = basicId.get("description").getAsString();
            if (desc.contains("DJI")) {
                return CoTMessage.SignalSource.SignalType.WIFI;
            }
        }

        return CoTMessage.SignalSource.SignalType.UNKNOWN;
    }

    public static class ParseResult {
        public CoTMessage cotMessage;
        public StatusMessage statusMessage;
        public String error;
    }
}