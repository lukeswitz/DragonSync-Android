// java/com.rootdown.dragonsync/network/XMLParser.java
package com.rootdown.dragonsync.network;

import android.util.Log;
import android.util.Xml;
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
        // Process JSON content similar to Swift version
        // Will implement JSON parsing logic
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