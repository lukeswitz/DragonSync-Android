package com.rootdown.dragonsync.network;

import android.util.Log;
import android.util.Xml;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;
import com.rootdown.dragonsync.models.StatusMessage;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XMLParser {
    private static final String TAG = "XMLParser";
    private ZMQHandler.MessageFormat messageFormat = ZMQHandler.MessageFormat.BLUETOOTH;
    private Map<String, Object> rawMessage;
    private String currentElement = "";
    private String currentIdType = "Unknown";
    private String parentElement = "";
    private ArrayList<String> elementStack = new ArrayList<>();
    private Map<String, String> eventAttributes = new HashMap<>();
    private Map<String, String> pointAttributes = new HashMap<>();
    private Map<String, String> messageAttributes = new HashMap<>();

    // Message values
    private String speed = "0.0";
    private String vspeed = "0.0";
    private String alt = "0.0";
    private String height = "0.0";
    private String pilotLat = "0.0";
    private String pilotLon = "0.0";
    private String pHomeLat = "0.0";
    private String pHomeLon = "0.0";
    private String droneDescription = "";
    private String currentValue = "";
    private String messageContent = "";
    private String remarks = "";
    private double cpuUsage = 0.0;

    // Result objects
    private CoTMessage cotMessage;
    private StatusMessage statusMessage;
    private boolean isStatusMessage = false;

    private final Map<String, ArrayList<String>> macPrefixesByManufacturer = new HashMap<>();

    public XMLParser() {
        initializeMacPrefixes();
    }

    private void initializeMacPrefixes() {
        // DJI
        ArrayList<String> djiPrefixes = new ArrayList<>();
        djiPrefixes.add("04:A8:5A");
        djiPrefixes.add("34:D2:62");
        djiPrefixes.add("48:1C:B9");
        djiPrefixes.add("58:B8:58");
        djiPrefixes.add("60:60:1F");  // Mavic 1 Pro
        djiPrefixes.add("E4:7A:2C");
        macPrefixesByManufacturer.put("DJI", djiPrefixes);

        // Add other manufacturers...
    }

    public ParseResult parse(String xmlStr) {
        ParseResult result = new ParseResult();
        XmlPullParser parser = Xml.newPullParser();

        try {
            parser.setInput(new StringReader(xmlStr));
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        handleStartTag(parser);
                        break;
                    case XmlPullParser.TEXT:
                        handleText(parser);
                        break;
                    case XmlPullParser.END_TAG:
                        handleEndTag(parser);
                        break;
                }
                eventType = parser.next();
            }

            if (isStatusMessage && statusMessage != null) {
                result.statusMessage = statusMessage;
            } else if (cotMessage != null) {
                result.cotMessage = cotMessage;
            }

        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing XML: " + e.getMessage());
            result.error = e.getMessage();
        }

        return result;
    }

    private void handleStartTag(XmlPullParser parser) {
        currentElement = parser.getName();
        currentValue = "";
        messageContent = "";
        elementStack.add(currentElement);

        if (currentElement.equals("event")) {
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                eventAttributes.put(parser.getAttributeName(i),
                        parser.getAttributeValue(i));
            }
            remarks = "";
        } else if (currentElement.equals("point")) {
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                pointAttributes.put(parser.getAttributeName(i),
                        parser.getAttributeValue(i));
            }
            if (pointAttributes.containsKey("hae")) {
                alt = pointAttributes.get("hae");
            }
        }
    }

    private void handleText(XmlPullParser parser) {
        String text = parser.getText();
        if (currentElement.equals("message")) {
            messageContent += text;
            processMessageContent(text);
        } else if (currentElement.equals("remarks")) {
            remarks += text;
        } else {
            currentValue += text.trim();
        }
    }

    private void handleEndTag(XmlPullParser parser) {
        // Similar to Swift version's parser didEndElement
        String element = parser.getName();
        if (!elementStack.isEmpty()) {
            elementStack.remove(elementStack.size() - 1);
        }

        if (element.equals("event")) {
            finalizeMessage();
        }
    }

    private void processMessageContent(String content) {
        if (content == null || content.isEmpty()) return;

        try {
            if (content.startsWith("{")) {
                JSONObject json = new JSONObject(content);

                if (json.has("system_stats")) {
                    isStatusMessage = true;

                    JSONObject stats = json.getJSONObject("system_stats");
                    if (stats.has("cpu")) {
                        cpuUsage = stats.getDouble("cpu");
                    }

                    statusMessage = new StatusMessage();
                    if (json.has("serial_number")) {
                        statusMessage.setSerialNumber(json.getString("serial_number"));
                    }
                    if (json.has("timestamp")) {
                        statusMessage.setTimestamp(json.getDouble("timestamp"));
                    }

                    // Extract memory stats
                    if (stats.has("memory")) {
                        JSONObject memory = stats.getJSONObject("memory");
                        StatusMessage.SystemStats.MemoryStats memoryStats = new StatusMessage.SystemStats.MemoryStats();
                        if (memory.has("total")) {
                            memoryStats.setTotal(memory.getLong("total"));
                        }
                        if (memory.has("used")) {
                            memoryStats.setUsed(memory.getLong("used"));
                        }
                        // Set memory stats to system stats
                    }

                    // Create and populate system stats
                    StatusMessage.SystemStats systemStats = new StatusMessage.SystemStats();
                    systemStats.setCpuUsage(cpuUsage);
                    if (stats.has("temperature")) {
                        systemStats.setTemperature(stats.getDouble("temperature"));
                    }
                    if (stats.has("uptime")) {
                        systemStats.setUptime(stats.getDouble("uptime"));
                    }

                    statusMessage.setSystemStats(systemStats);
                } else if (json.has("drone_id")) {
                    cotMessage = new CoTMessage();

                    if (json.has("drone_id")) {
                        cotMessage.setUid(json.getString("drone_id"));
                    }
                    if (json.has("lat")) {
                        cotMessage.setLat(json.getString("lat"));
                    }
                    if (json.has("lon")) {
                        cotMessage.setLon(json.getString("lon"));
                    }
                    if (json.has("alt")) {
                        cotMessage.setAlt(json.getString("alt"));
                    }
                    if (json.has("speed")) {
                        cotMessage.setSpeed(json.getString("speed"));
                    }
                    if (json.has("direction")) {
                        cotMessage.setDirection(json.getString("direction"));
                    }
                    if (json.has("rssi")) {
                        cotMessage.setRssi(json.getInt("rssi"));
                    }
                    if (json.has("mac")) {
                        cotMessage.setMac(json.getString("mac"));
                    }
                    if (json.has("description")) {
                        cotMessage.setDescription(json.getString("description"));
                    }
                    if (json.has("pilot_lat")) {
                        cotMessage.setPilotLat(json.getString("pilot_lat"));
                    }
                    if (json.has("pilot_lon")) {
                        cotMessage.setPilotLon(json.getString("pilot_lon"));
                    }
                    if (json.has("height")) {
                        cotMessage.setHeight(json.getString("height"));
                    }
                    if (json.has("timestamp")) {
                        cotMessage.setTimestamp(json.getString("timestamp"));
                    }

                    // Extract signal sources if available
                    if (json.has("signal_sources") && json.get("signal_sources") instanceof JSONArray) {
                        JSONArray sources = json.getJSONArray("signal_sources");
                        for (int i = 0; i < sources.length(); i++) {
                            JSONObject source = sources.getJSONObject(i);
                            String sourceMac = source.optString("mac", "");
                            int sourceRssi = source.optInt("rssi", 0);
                            String sourceType = source.optString("type", "UNKNOWN");
                            long sourceTimestamp = source.optLong("timestamp", System.currentTimeMillis());

                            CoTMessage.SignalSource.SignalType signalType;
                            switch (sourceType.toUpperCase()) {
                                case "BLUETOOTH":
                                case "BT":
                                case "BLE":
                                    signalType = CoTMessage.SignalSource.SignalType.BLUETOOTH;
                                    break;
                                case "WIFI":
                                    signalType = CoTMessage.SignalSource.SignalType.WIFI;
                                    break;
                                case "SDR":
                                    signalType = CoTMessage.SignalSource.SignalType.SDR;
                                    break;
                                default:
                                    signalType = CoTMessage.SignalSource.SignalType.UNKNOWN;
                            }

                            cotMessage.getSignalSources().add(
                                    new CoTMessage.SignalSource(sourceMac, sourceRssi, signalType, sourceTimestamp)
                            );
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON message content: " + e.getMessage());
        }
    }

    private void finalizeMessage() {
        if (remarks.contains("CPU Usage:")) {
            createStatusMessage();
        } else {
            createCoTMessage();
        }
    }

    private void createStatusMessage() {
        // Create StatusMessage object from parsed data
        // Similar to Swift version's logic
    }

    private void createCoTMessage() {
        // Create CoTMessage object from parsed data
        // Similar to Swift version's logic
    }

    public static class ParseResult {
        public CoTMessage cotMessage;
        public StatusMessage statusMessage;
        public String error;
    }
}