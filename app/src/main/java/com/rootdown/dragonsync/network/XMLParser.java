package com.rootdown.dragonsync.network;

import android.util.Log;
import android.util.Xml;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.StatusMessage;
import org.json.JSONArray;
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
    private final Map<String, ArrayList<String>> macPrefixesByManufacturer = new HashMap<>();

    public XMLParser() {
        initializeMacPrefixes();
    }

    private void initializeMacPrefixes() {
        ArrayList<String> djiPrefixes = new ArrayList<>();
        djiPrefixes.add("04:A8:5A");
        djiPrefixes.add("34:D2:62");
        djiPrefixes.add("48:1C:B9");
        djiPrefixes.add("58:B8:58");
        djiPrefixes.add("60:60:1F");
        djiPrefixes.add("E4:7A:2C");
        macPrefixesByManufacturer.put("DJI", djiPrefixes);
    }

    public ParseResult parse(String xmlStr) {
        ParseResult result = new ParseResult();
        XmlPullParser parser = Xml.newPullParser();

        String currentElement = "";
        ArrayList<String> elementStack = new ArrayList<>();
        Map<String, String> eventAttributes = new HashMap<>();
        Map<String, String> pointAttributes = new HashMap<>();

        String speed = "0.0";
        String alt = "0.0";
        String droneDescription = "";
        String currentValue = "";
        String messageContent = "";
        String remarks = "";
        double cpuUsage = 0.0;

        CoTMessage cotMessage = null;
        StatusMessage statusMessage = null;
        boolean isStatusMessage = false;

        try {
            parser.setInput(new StringReader(xmlStr));
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
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
                        break;
                    case XmlPullParser.TEXT:
                        String text = parser.getText();
                        if (currentElement.equals("message")) {
                            messageContent += text;
                            if (text != null && !text.isEmpty() && text.startsWith("{")) {
                                try {
                                    JSONObject json = new JSONObject(text);

                                    if (json.has("system_stats")) {
                                        isStatusMessage = true;
                                        statusMessage = new StatusMessage();
                                        Log.d(TAG, "Created StatusMessage from JSON");

                                    } else if (json.has("drone_id") || json.has("uid")) {
                                        cotMessage = new CoTMessage();

                                        // Create a map of values for future field access
                                        Map<String, Object> rawData = new HashMap<>();
                                        rawData.put("uid", json.optString("drone_id", json.optString("uid", "")));
                                        rawData.put("lat", json.optString("lat", "0.0"));
                                        rawData.put("lon", json.optString("lon", "0.0"));
                                        rawData.put("alt", json.optString("alt", "0.0"));
                                        rawData.put("speed", json.optString("speed", "0.0"));
                                        rawData.put("direction", json.optString("direction", "0.0"));

                                        if (json.has("rssi")) {
                                            rawData.put("rssi", json.getInt("rssi"));
                                        }

                                        if (json.has("mac")) {
                                            rawData.put("mac", json.getString("mac"));
                                        }

                                        if (json.has("description")) {
                                            rawData.put("description", json.getString("description"));
                                        }

                                        if (json.has("pilot_lat")) {
                                            rawData.put("pilotLat", json.getString("pilot_lat"));
                                        }

                                        if (json.has("pilot_lon")) {
                                            rawData.put("pilotLon", json.getString("pilot_lon"));
                                        }

                                        cotMessage.rawMessage = rawData;
                                        Log.d(TAG, "Created CoTMessage from JSON");
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error parsing JSON: " + e.getMessage());
                                }
                            }
                        } else if (currentElement.equals("remarks")) {
                            remarks += text;
                        } else {
                            currentValue += text.trim();
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        String element = parser.getName();
                        if (!elementStack.isEmpty()) {
                            elementStack.remove(elementStack.size() - 1);
                        }

                        if (element.equals("event")) {
                            if (remarks.contains("CPU Usage:") && statusMessage == null) {
                                statusMessage = new StatusMessage();
                                isStatusMessage = true;
                                Log.d(TAG, "Created StatusMessage from remarks");
                            } else if (cotMessage == null) {
                                cotMessage = new CoTMessage();

                                // Create a map of values for future field access
                                Map<String, Object> rawData = new HashMap<>();
                                if (eventAttributes.containsKey("uid")) {
                                    rawData.put("uid", eventAttributes.get("uid"));
                                }

                                if (pointAttributes.containsKey("lat")) {
                                    rawData.put("lat", pointAttributes.get("lat"));
                                }

                                if (pointAttributes.containsKey("lon")) {
                                    rawData.put("lon", pointAttributes.get("lon"));
                                }

                                rawData.put("alt", alt);
                                rawData.put("speed", speed);
                                rawData.put("description", droneDescription);

                                cotMessage.rawMessage = rawData;
                                Log.d(TAG, "Created CoTMessage from XML attributes");
                            }
                        }
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

    private String findManufacturer(String mac) {
        if (mac == null || mac.isEmpty()) {
            return "Unknown";
        }

        String prefix = mac.toUpperCase().substring(0, Math.min(8, mac.length()));

        for (Map.Entry<String, ArrayList<String>> entry : macPrefixesByManufacturer.entrySet()) {
            for (String knownPrefix : entry.getValue()) {
                if (prefix.startsWith(knownPrefix.replace(":", ""))) {
                    return entry.getKey();
                }
            }
        }

        return "Unknown";
    }

    public static class ParseResult {
        public CoTMessage cotMessage;
        public StatusMessage statusMessage;
        public String error;
    }
}