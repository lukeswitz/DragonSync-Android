package com.rootdown.dragonsync.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DroneDataParser {
    private static final String TAG = "DroneDataParser";

    // Message types from ASTM F3411
    private static final byte MESSAGE_TYPE_BASIC_ID = 0x00;
    private static final byte MESSAGE_TYPE_LOCATION = 0x01;
    private static final byte MESSAGE_TYPE_AUTH = 0x02;
    private static final byte MESSAGE_TYPE_SELF_ID = 0x03;
    private static final byte MESSAGE_TYPE_SYSTEM = 0x04;
    private static final byte MESSAGE_TYPE_OPERATOR_ID = 0x05;
    private static final byte MESSAGE_TYPE_MESSAGE_PACK = 0x0F;

    // Constants for parsing
    private static final int MAX_MESSAGE_SIZE = 25;
    private static final int MAX_ID_BYTE_SIZE = 20;
    private static final int MAX_STRING_BYTE_SIZE = 23;
    private static final double LAT_LONG_MULTIPLIER = 1e-7;

    public JSONArray parseBluetoothData(byte[] data, String macAddress, int rssi) {
        JSONArray messagesArray = new JSONArray();

        try {
            // Handle ASTM F3411 format
            // Check if we have minimum data required (1 byte header + some data)
            if (data.length < 2) return messagesArray;

            // Get message type from first byte (top 4 bits)
            int headerByte = data[0] & 0xFF;
            int messageType = (headerByte >> 4) & 0x0F;
            int messageVersion = headerByte & 0x0F;

            // Create a JSON message based on the type
            switch (messageType) {
                case MESSAGE_TYPE_BASIC_ID:
                    messagesArray.put(parseBasicIdMessage(data, macAddress, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_LOCATION:
                    messagesArray.put(parseLocationMessage(data, macAddress, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_SELF_ID:
                    messagesArray.put(parseSelfIdMessage(data, macAddress, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_SYSTEM:
                    messagesArray.put(parseSystemMessage(data, macAddress, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_OPERATOR_ID:
                    messagesArray.put(parseOperatorIdMessage(data, macAddress, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_MESSAGE_PACK:
                    // Message pack may contain multiple messages
                    JSONArray packMessages = parseMessagePack(data, macAddress, rssi, messageVersion);
                    for (int i = 0; i < packMessages.length(); i++) {
                        messagesArray.put(packMessages.get(i));
                    }
                    break;

                default:
                    Log.w(TAG, "Unknown message type: " + messageType);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing Bluetooth data: " + e.getMessage(), e);
        }

        return messagesArray;
    }

    // Add the missing parseWiFiBeaconData method
    public JSONArray parseWiFiBeaconData(byte[] beaconData, String bssid, int rssi) {
        JSONArray messagesArray = new JSONArray();

        try {
            if (beaconData.length < 1) return messagesArray;

            // Parse the beacon data similar to Bluetooth data
            int headerByte = beaconData[0] & 0xFF;
            int messageType = (headerByte >> 4) & 0x0F;
            int messageVersion = headerByte & 0x0F;

            // Process the message based on type
            switch (messageType) {
                case MESSAGE_TYPE_BASIC_ID:
                    messagesArray.put(parseBasicIdMessage(beaconData, bssid, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_LOCATION:
                    messagesArray.put(parseLocationMessage(beaconData, bssid, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_SELF_ID:
                    messagesArray.put(parseSelfIdMessage(beaconData, bssid, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_SYSTEM:
                    messagesArray.put(parseSystemMessage(beaconData, bssid, rssi, messageVersion, 1));
                    break;

                case MESSAGE_TYPE_OPERATOR_ID:
                    messagesArray.put(parseOperatorIdMessage(beaconData, bssid, rssi, messageVersion, 1));
                    break;

                default:
                    Log.w(TAG, "Unknown WiFi beacon message type: " + messageType);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing WiFi beacon data: " + e.getMessage(), e);
        }

        return messagesArray;
    }

    public JSONObject parseOpenDroneIDMessage(byte[] messageData, String bssid, int rssi) {
        try {
            JSONObject messageObj = new JSONObject();
            if (messageData.length < 25) {
                Log.w(TAG, "OpenDroneID message too short: " + messageData.length + " bytes");
                return null;
            }

            ByteBuffer buffer = ByteBuffer.wrap(messageData);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Read message header
            int messageType = buffer.get() & 0xFF;

            // Add common fields
            JSONObject commonData = new JSONObject();
            commonData.put("MAC", bssid);
            commonData.put("RSSI", rssi);

            switch (messageType) {
                case 0x00: // Basic ID
                    JSONObject basicId = parseBasicIDMessage(buffer, commonData);
                    if (basicId != null) {
                        messageObj.put("Basic ID", basicId);
                    }
                    break;

                case 0x01: // Location/Vector
                    JSONObject location = parseLocationMessage(buffer, commonData);
                    if (location != null) {
                        messageObj.put("Location/Vector Message", location);
                    }
                    break;

                case 0x02: // Authentication
                    JSONObject auth = parseAuthMessage(buffer, commonData);
                    if (auth != null) {
                        messageObj.put("Authentication Message", auth);
                    }
                    break;

                case 0x03: // Self-ID
                    JSONObject selfId = parseSelfIDMessage(buffer, commonData);
                    if (selfId != null) {
                        messageObj.put("Self-ID Message", selfId);
                    }
                    break;

                case 0x04: // System
                    JSONObject system = parseSystemMessage(buffer, commonData);
                    if (system != null) {
                        messageObj.put("System Message", system);
                    }
                    break;

                case 0x05: // Operator ID
                    JSONObject operator = parseOperatorIDMessage(buffer, commonData);
                    if (operator != null) {
                        messageObj.put("Operator ID Message", operator);
                    }
                    break;

                case 0x0F: // Message Pack
                    // For message packs, we should parse multiple messages
                    // This is more complex and might need special handling
                    Log.d(TAG, "Message pack detected, using fallback parser");
                    return null; // Let the fallback method handle this

                default:
                    Log.w(TAG, "Unknown OpenDroneID message type: " + messageType);
                    return null;
            }

            return messageObj;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing OpenDroneID message: " + e.getMessage(), e);
            return null;
        }
    }

    private JSONObject parseAuthMessage(ByteBuffer buffer, JSONObject commonData) throws Exception {
        JSONObject auth = new JSONObject();
        auth.put("MAC", commonData.getString("MAC"));
        auth.put("RSSI", commonData.getInt("RSSI"));

        int authType = buffer.get() & 0xFF;
        int pageNumber = buffer.get() & 0xFF;
        int lastPageIndex = buffer.get() & 0xFF;
        int length = buffer.get() & 0xFF;
        long timestamp = buffer.getInt() & 0xFFFFFFFFL;

        // Read auth data (17 bytes)
        byte[] authData = new byte[17];
        buffer.get(authData);

        auth.put("auth_type", authType);
        auth.put("page_number", pageNumber);
        auth.put("last_page_index", lastPageIndex);
        auth.put("length", length);
        auth.put("timestamp_raw", timestamp);
        auth.put("auth_data", bytesToHex(authData));

        return auth;
    }

    private JSONObject parseBasicIDMessage(ByteBuffer buffer, JSONObject commonData) throws Exception {
        JSONObject basicId = new JSONObject();
        basicId.put("MAC", commonData.getString("MAC"));
        basicId.put("RSSI", commonData.getInt("RSSI"));

        int idType = buffer.get() & 0xFF;
        int uaType = buffer.get() & 0xFF;

        // Read ID (20 bytes)
        byte[] idBytes = new byte[20];
        buffer.get(idBytes);
        String id = new String(idBytes, "UTF-8").trim();

        basicId.put("id_type", getIdTypeString(idType));
        basicId.put("ua_type", getUATypeString(uaType));
        basicId.put("id", id);

        return basicId;
    }

    // Add the missing getIdTypeString method
    private String getIdTypeString(int idType) {
        switch (idType) {
            case 0: return "None";
            case 1: return "Serial Number (ANSI/CTA-2063-A)";
            case 2: return "CAA Assigned Registration ID";
            case 3: return "UTM (USS) Assigned ID";
            case 4: return "Specific Session ID";
            default: return "Unknown";
        }
    }

    private JSONObject parseBasicIdMessage(byte[] data, String macAddress, int rssi, int version, int offset) {
        JSONObject messageObj = new JSONObject();

        try {
            JSONObject basicId = new JSONObject();

            // Add transport-related info
            basicId.put("protocol_version", "F3411." + (version == 0 ? "19" : (version == 1 ? "22" : "23")));
            basicId.put("MAC", macAddress);
            basicId.put("RSSI", rssi);

            // Parse ID type and UA type
            if (data.length >= offset + 1) {
                int typeByte = data[offset] & 0xFF;
                int idType = (typeByte >> 4) & 0x0F;
                int uaType = typeByte & 0x0F;

                // Map ID type
                switch (idType) {
                    case 0:
                        basicId.put("id_type", "None");
                        break;
                    case 1:
                        basicId.put("id_type", "Serial Number (ANSI/CTA-2063-A)");
                        break;
                    case 2:
                        basicId.put("id_type", "CAA Assigned Registration ID");
                        break;
                    case 3:
                        basicId.put("id_type", "UTM (USS) Assigned ID");
                        break;
                    default:
                        basicId.put("id_type", "Unknown");
                }

                // Map UA type
                switch (uaType) {
                    case 0:
                        basicId.put("ua_type", "None");
                        break;
                    case 1:
                        basicId.put("ua_type", "Aeroplane");
                        break;
                    case 2:
                        basicId.put("ua_type", "Helicopter (or Multirotor)");
                        break;
                    case 3:
                        basicId.put("ua_type", "Gyroplane");
                        break;
                    case 4:
                        basicId.put("ua_type", "Hybrid Lift");
                        break;
                    case 5:
                        basicId.put("ua_type", "Ornithopter");
                        break;
                    case 6:
                        basicId.put("ua_type", "Glider");
                        break;
                    case 7:
                        basicId.put("ua_type", "Kite");
                        break;
                    case 8:
                        basicId.put("ua_type", "Free Balloon");
                        break;
                    case 9:
                        basicId.put("ua_type", "Captive Balloon");
                        break;
                    case 10:
                        basicId.put("ua_type", "Airship");
                        break;
                    case 11:
                        basicId.put("ua_type", "Free Fall/Parachute");
                        break;
                    case 12:
                        basicId.put("ua_type", "Rocket");
                        break;
                    case 13:
                        basicId.put("ua_type", "Tethered Powered Aircraft");
                        break;
                    case 14:
                        basicId.put("ua_type", "Ground Obstacle");
                        break;
                    case 15:
                        basicId.put("ua_type", "Other");
                        break;
                }
            }

            // Parse UAS ID (20 bytes)
            if (data.length >= offset + 1 + MAX_ID_BYTE_SIZE) {
                byte[] uasIdBytes = Arrays.copyOfRange(data, offset + 1, offset + 1 + MAX_ID_BYTE_SIZE);
                // Trim null bytes and convert to string
                String uasId = new String(uasIdBytes, StandardCharsets.UTF_8).trim();
                basicId.put("id", uasId);
            }

            messageObj.put("Basic ID", basicId);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating Basic ID message: " + e.getMessage());
        }

        return messageObj;
    }

    private JSONObject parseLocationMessage(ByteBuffer buffer, JSONObject commonData) throws Exception {
        JSONObject location = new JSONObject();
        location.put("MAC", commonData.getString("MAC"));
        location.put("RSSI", commonData.getInt("RSSI"));

        int status = buffer.get() & 0xFF;
        int direction = buffer.get() & 0xFF;
        int speedMultiplier = buffer.get() & 0xFF;
        int vertSpeedMultiplier = buffer.get() & 0xFF;

        // Read latitude/longitude (4 bytes each)
        double latitude = buffer.getInt() / 10000000.0;
        double longitude = buffer.getInt() / 10000000.0;

        // Read altitude/height (2 bytes each)
        int altitudeRaw = buffer.getShort() & 0xFFFF;
        int heightRaw = buffer.getShort() & 0xFFFF;

        // Read speeds (1 byte each)
        int speedRaw = buffer.get() & 0xFF;
        int vertSpeedRaw = buffer.get() & 0xFF;

        location.put("latitude", latitude);
        location.put("longitude", longitude);
        location.put("geodetic_altitude", altitudeRaw - 1000); // Offset by 1000m
        location.put("height_agl", heightRaw - 1000);
        location.put("speed", speedRaw * (speedMultiplier == 0 ? 0.25 : speedMultiplier));
        location.put("vert_speed", (vertSpeedRaw - 63) * 0.5);
        location.put("direction", direction * 1.4); // Convert to degrees
        location.put("status", getStatusString(status));

        return location;
    }

    private JSONObject parseLocationMessage(byte[] data, String macAddress, int rssi, int version, int offset) {
        JSONObject messageObj = new JSONObject();

        try {
            JSONObject location = new JSONObject();

            // Add protocol version
            location.put("protocol_version", "F3411." + (version == 0 ? "19" : (version == 1 ? "22" : "23")));
            location.put("MAC", macAddress);
            location.put("RSSI", rssi);

            if (data.length >= offset + 1) {
                int statusByte = data[offset] & 0xFF;
                int status = (statusByte >> 4) & 0x0F;
                int heightType = (statusByte >> 2) & 0x01;
                int ewDirection = (statusByte >> 1) & 0x01;
                int speedMult = statusByte & 0x01;

                // Operational status
                switch (status) {
                    case 0:
                        location.put("op_status", "Undeclared");
                        break;
                    case 1:
                        location.put("op_status", "Ground");
                        break;
                    case 2:
                        location.put("op_status", "Airborne");
                        break;
                    case 3:
                        location.put("op_status", "Emergency");
                        break;
                    case 4:
                        location.put("op_status", "Remote ID System Failure");
                        break;
                    default:
                        location.put("op_status", "Unknown");
                }

                // Height type
                location.put("height_type", heightType == 0 ? "Above Takeoff" : "Above Ground");

                // E/W Direction
                location.put("ew_dir_segment", ewDirection == 0 ? "East" : "West");

                // Speed multiplier
                location.put("speed_multiplier", speedMult == 0 ? "0.25" : "0.75");
            }

            // Direction
            if (data.length >= offset + 2) {
                int direction = data[offset + 1] & 0xFF;
                // Apply E/W correction if needed
                int ewDirection = ((data[offset] >> 1) & 0x01);
                double finalDirection = (ewDirection == 0) ? direction : direction + 180;
                if (finalDirection > 360) finalDirection -= 360;
                location.put("direction", finalDirection);
            }

            // Speed (horizontal)
            if (data.length >= offset + 3) {
                int speedHori = data[offset + 2] & 0xFF;
                int speedMult = data[offset] & 0x01;
                double speed = speedMult == 0 ? speedHori * 0.25 : (speedHori * 0.75) + (255 * 0.25);
                location.put("speed", String.format("%.2f m/s", speed));
            }

            // Speed (vertical)
            if (data.length >= offset + 4) {
                int speedVert = data[offset + 3];
                double vspeed = speedVert * 0.5;
                location.put("vert_speed", String.format("%.2f m/s", vspeed));
            }

            // Latitude and Longitude
            if (data.length >= offset + 12) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 4, 8).order(ByteOrder.LITTLE_ENDIAN);
                int lat = buffer.getInt();
                int lon = buffer.getInt();

                double latitude = lat * LAT_LONG_MULTIPLIER;
                double longitude = lon * LAT_LONG_MULTIPLIER;

                location.put("latitude", latitude);
                location.put("longitude", longitude);
            }

            // Altitude (pressure)
            if (data.length >= offset + 14) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 12, 2).order(ByteOrder.LITTLE_ENDIAN);
                int altPressure = buffer.getShort() & 0xFFFF;
                double pressureAlt = (altPressure / 2.0) - 1000.0;
                location.put("pressure_altitude", String.format("%.1f m", pressureAlt));
            }

            // Altitude (geodetic)
            if (data.length >= offset + 16) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 14, 2).order(ByteOrder.LITTLE_ENDIAN);
                int altGeodetic = buffer.getShort() & 0xFFFF;
                double geodeticAlt = (altGeodetic / 2.0) - 1000.0;
                location.put("geodetic_altitude", String.format("%.1f m", geodeticAlt));
            }

            // Height above takeoff/ground
            if (data.length >= offset + 18) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 16, 2).order(ByteOrder.LITTLE_ENDIAN);
                int height = buffer.getShort() & 0xFFFF;
                double heightValue = (height / 2.0) - 1000.0;
                location.put("height_agl", String.format("%.1f m", heightValue));
            }

            // Accuracy values
            if (data.length >= offset + 19) {
                int accuracyByte = data[offset + 18] & 0xFF;
                int horizAcc = accuracyByte & 0x0F;
                int vertAcc = (accuracyByte >> 4) & 0x0F;

                switch (horizAcc) {
                    case 0: location.put("horizontal_accuracy", "Unknown"); break;
                    case 1: location.put("horizontal_accuracy", "<10 m"); break;
                    case 2: location.put("horizontal_accuracy", "<30 m"); break;
                    case 3: location.put("horizontal_accuracy", "<100 m"); break;
                    case 4: location.put("horizontal_accuracy", "<300 m"); break;
                    case 5: location.put("horizontal_accuracy", "<1000 m"); break;
                    case 6: location.put("horizontal_accuracy", "<3000 m"); break;
                    case 7: location.put("horizontal_accuracy", "<10000 m"); break;
                    case 8: location.put("horizontal_accuracy", "<30000 m"); break;
                    default: location.put("horizontal_accuracy", "Unknown");
                }

                switch (vertAcc) {
                    case 0: location.put("vertical_accuracy", "Unknown"); break;
                    case 1: location.put("vertical_accuracy", "<1 m"); break;
                    case 2: location.put("vertical_accuracy", "<3 m"); break;
                    case 3: location.put("vertical_accuracy", "<10 m"); break;
                    case 4: location.put("vertical_accuracy", "<30 m"); break;
                    case 5: location.put("vertical_accuracy", "<100 m"); break;
                    case 6: location.put("vertical_accuracy", "<300 m"); break;
                    case 7: location.put("vertical_accuracy", "<1000 m"); break;
                    case 8: location.put("vertical_accuracy", "<3000 m"); break;
                    default: location.put("vertical_accuracy", "Unknown");
                }
            }

            if (data.length >= offset + 20) {
                int accByte = data[offset + 19] & 0xFF;
                int baroAcc = (accByte >> 4) & 0x0F;
                int speedAcc = accByte & 0x0F;

                switch (baroAcc) {
                    case 0: location.put("baro_accuracy", "Unknown"); break;
                    case 1: location.put("baro_accuracy", "<1 m"); break;
                    case 2: location.put("baro_accuracy", "<3 m"); break;
                    case 3: location.put("baro_accuracy", "<10 m"); break;
                    case 4: location.put("baro_accuracy", "<30 m"); break;
                    case 5: location.put("baro_accuracy", "<100 m"); break;
                    case 6: location.put("baro_accuracy", "<300 m"); break;
                    case 7: location.put("baro_accuracy", "<1000 m"); break;
                    case 8: location.put("baro_accuracy", "<3000 m"); break;
                    default: location.put("baro_accuracy", "Unknown");
                }

                switch (speedAcc) {
                    case 0: location.put("speed_accuracy", "Unknown"); break;
                    case 1: location.put("speed_accuracy", "<0.3 m/s"); break;
                    case 2: location.put("speed_accuracy", "<1 m/s"); break;
                    case 3: location.put("speed_accuracy", "<3 m/s"); break;
                    case 4: location.put("speed_accuracy", "<10 m/s"); break;
                    case 5: location.put("speed_accuracy", "<30 m/s"); break;
                    case 6: location.put("speed_accuracy", "<100 m/s"); break;
                    case 7: location.put("speed_accuracy", "<300 m/s"); break;
                    case 8: location.put("speed_accuracy", "<1000 m/s"); break;
                    default: location.put("speed_accuracy", "Unknown");
                }
            }

            // Timestamp
            if (data.length >= offset + 22) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 20, 2).order(ByteOrder.LITTLE_ENDIAN);
                int timestamp = buffer.getShort() & 0xFFFF;
                int minutes = timestamp / 600;
                double seconds = (timestamp % 600) / 10.0;
                location.put("timestamp", String.format("%d min %.1f s", minutes, seconds));
            }

            // Time accuracy
            if (data.length >= offset + 23) {
                int timeAcc = data[offset + 22] & 0x0F;
                double accuracy = timeAcc * 0.1;
                location.put("timestamp_accuracy", String.format("%.1f s", accuracy));
            }

            messageObj.put("Location/Vector Message", location);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating Location message: " + e.getMessage());
        }

        return messageObj;
    }

    private JSONObject parseSelfIDMessage(ByteBuffer buffer, JSONObject commonData) throws Exception {
        JSONObject selfId = new JSONObject();
        selfId.put("MAC", commonData.getString("MAC"));
        selfId.put("RSSI", commonData.getInt("RSSI"));

        int descType = buffer.get() & 0xFF;

        // Read description text (23 bytes)
        byte[] textBytes = new byte[23];
        buffer.get(textBytes);
        String text = new String(textBytes, "UTF-8").trim();

        selfId.put("description_type", descType);
        selfId.put("text", text);

        return selfId;
    }

    private JSONObject parseSelfIdMessage(byte[] data, String macAddress, int rssi, int version, int offset) {
        JSONObject messageObj = new JSONObject();

        try {
            JSONObject selfId = new JSONObject();

            // Add protocol version and transport info
            selfId.put("protocol_version", "F3411." + (version == 0 ? "19" : (version == 1 ? "22" : "23")));
            selfId.put("MAC", macAddress);
            selfId.put("RSSI", rssi);

            // Description type
            if (data.length >= offset + 1) {
                int descType = data[offset] & 0xFF;

                switch (descType) {
                    case 0:
                        selfId.put("description_type", "Text");
                        break;
                    case 1:
                        selfId.put("description_type", "Emergency");
                        break;
                    case 2:
                        selfId.put("description_type", "Extended Status");
                        break;
                    default:
                        selfId.put("description_type", "Reserved");
                }
            }

            // Description text
            if (data.length >= offset + 1 + MAX_STRING_BYTE_SIZE) {
                byte[] descBytes = Arrays.copyOfRange(data, offset + 1, offset + 1 + MAX_STRING_BYTE_SIZE);
                // Get text until null terminator or end
                String description = new String(descBytes, StandardCharsets.UTF_8).trim();
                selfId.put("text", description);
            }

            messageObj.put("Self-ID Message", selfId);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating Self-ID message: " + e.getMessage());
        }

        return messageObj;
    }

    // Add overloaded method for ByteBuffer (called from parseOpenDroneIDMessage)
    private JSONObject parseSystemMessage(ByteBuffer buffer, JSONObject commonData) throws Exception {
        JSONObject system = new JSONObject();
        system.put("MAC", commonData.getString("MAC"));
        system.put("RSSI", commonData.getInt("RSSI"));

        int locationType = buffer.get() & 0xFF;
        int classificationType = buffer.get() & 0xFF;

        // Read operator coordinates (4 bytes each)
        double operatorLat = buffer.getInt() / 10000000.0;
        double operatorLon = buffer.getInt() / 10000000.0;

        // Read area information
        int areaCount = buffer.getShort() & 0xFFFF;
        int areaRadius = buffer.get() & 0xFF;
        int areaCeiling = buffer.getShort() & 0xFFFF;
        int areaFloor = buffer.getShort() & 0xFFFF;

        system.put("operator_lat", operatorLat);
        system.put("operator_lon", operatorLon);
        system.put("area_count", areaCount);
        system.put("area_radius", areaRadius);
        system.put("area_ceiling", areaCeiling - 1000);
        system.put("area_floor", areaFloor - 1000);
        system.put("operator_location_type", getLocationTypeString(locationType));
        system.put("classification_type", getClassificationString(classificationType));

        return system;
    }

    // Add overloaded method for byte array
    private JSONObject parseSystemMessage(byte[] data, String macAddress, int rssi, int version, int offset) {
        JSONObject messageObj = new JSONObject();

        try {
            JSONObject system = new JSONObject();
            system.put("protocol_version", "F3411." + (version == 0 ? "19" : (version == 1 ? "22" : "23")));
            system.put("MAC", macAddress);
            system.put("RSSI", rssi);

            // Parse location type and classification
            if (data.length >= offset + 2) {
                int locationType = data[offset] & 0xFF;
                int classificationType = data[offset + 1] & 0xFF;

                system.put("operator_location_type", getLocationTypeString(locationType));
                system.put("classification_type", getClassificationString(classificationType));
            }

            // Parse operator coordinates (latitude and longitude)
            if (data.length >= offset + 10) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 2, 8).order(ByteOrder.LITTLE_ENDIAN);
                int lat = buffer.getInt();
                int lon = buffer.getInt();

                double operatorLat = lat * LAT_LONG_MULTIPLIER;
                double operatorLon = lon * LAT_LONG_MULTIPLIER;

                system.put("operator_lat", operatorLat);
                system.put("operator_lon", operatorLon);
            }

            // Parse area information
            if (data.length >= offset + 16) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 10, 6).order(ByteOrder.LITTLE_ENDIAN);
                int areaCount = buffer.getShort() & 0xFFFF;
                int areaRadius = buffer.get() & 0xFF;
                int areaCeiling = buffer.getShort() & 0xFFFF;
                int areaFloor = buffer.get() & 0xFF;

                system.put("area_count", areaCount);
                system.put("area_radius", areaRadius);
                system.put("area_ceiling", areaCeiling - 1000);
                system.put("area_floor", areaFloor - 1000);
            }

            messageObj.put("System Message", system);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating System message: " + e.getMessage());
        }

        return messageObj;
    }

        private JSONObject parseOperatorIDMessage(ByteBuffer buffer, JSONObject commonData) throws Exception {
            JSONObject operator = new JSONObject();
            operator.put("MAC", commonData.getString("MAC"));
            operator.put("RSSI", commonData.getInt("RSSI"));

            int idType = buffer.get() & 0xFF;

            // Read operator ID (20 bytes)
            byte[] idBytes = new byte[20];
            buffer.get(idBytes);
            String operatorId = new String(idBytes, "UTF-8").trim();

            operator.put("operator_id_type", getOperatorIdTypeString(idType));
            operator.put("operator_id", operatorId);

            return operator;
        }

        // Add overloaded method for byte array
        private JSONObject parseOperatorIdMessage(byte[] data, String macAddress, int rssi, int version, int offset) {
            JSONObject messageObj = new JSONObject();

            try {
                JSONObject operator = new JSONObject();
                operator.put("protocol_version", "F3411." + (version == 0 ? "19" : (version == 1 ? "22" : "23")));
                operator.put("MAC", macAddress);
                operator.put("RSSI", rssi);

                // Parse operator ID type
                if (data.length >= offset + 1) {
                    int idType = data[offset] & 0xFF;
                    operator.put("operator_id_type", getOperatorIdTypeString(idType));
                }

                // Parse operator ID (20 bytes)
                if (data.length >= offset + 1 + MAX_ID_BYTE_SIZE) {
                    byte[] operatorIdBytes = Arrays.copyOfRange(data, offset + 1, offset + 1 + MAX_ID_BYTE_SIZE);
                    String operatorId = new String(operatorIdBytes, StandardCharsets.UTF_8).trim();
                    operator.put("operator_id", operatorId);
                }

                messageObj.put("Operator ID Message", operator);

            } catch (JSONException e) {
                Log.e(TAG, "Error creating Operator ID message: " + e.getMessage());
            }

            return messageObj;
        }

        private JSONArray parseMessagePack(byte[] data, String macAddress, int rssi, int version) {
            JSONArray packMessages = new JSONArray();

            try {
                // Parse message pack header
                if (data.length < 3) return packMessages;

                // Message size and count
                int messageSize = data[1] & 0xFF;
                int messagesInPack = data[2] & 0xFF;

                if (messageSize <= 0 || messagesInPack <= 0 || messageSize * messagesInPack > data.length - 3) {
                    Log.e(TAG, "Invalid message pack: size=" + messageSize + ", count=" + messagesInPack);
                    return packMessages;
                }

                // Process each message in the pack
                for (int i = 0; i < messagesInPack; i++) {
                    int offset = 3 + (i * messageSize);
                    if (offset + messageSize > data.length) break;

                    // Get message type
                    int headerByte = data[offset] & 0xFF;
                    int messageType = (headerByte >> 4) & 0x0F;
                    int messageVersion = headerByte & 0x0F;

                    // Parse based on type
                    switch (messageType) {
                        case MESSAGE_TYPE_BASIC_ID:
                            packMessages.put(parseBasicIdMessage(data, macAddress, rssi, messageVersion, offset));
                            break;

                        case MESSAGE_TYPE_LOCATION:
                            packMessages.put(parseLocationMessage(data, macAddress, rssi, messageVersion, offset));
                            break;

                        case MESSAGE_TYPE_SELF_ID:
                            packMessages.put(parseSelfIdMessage(data, macAddress, rssi, messageVersion, offset));
                            break;

                        case MESSAGE_TYPE_SYSTEM:
                            packMessages.put(parseSystemMessage(data, macAddress, rssi, messageVersion, offset));
                            break;

                        case MESSAGE_TYPE_OPERATOR_ID:
                            packMessages.put(parseOperatorIdMessage(data, macAddress, rssi, messageVersion, offset));
                            break;

                        default:
                            Log.w(TAG, "Unknown message type in pack: " + messageType);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error parsing message pack: " + e.getMessage(), e);
            }

            return packMessages;
        }

        private String getUATypeString(int uaType) {
            switch (uaType) {
                case 0: return "None";
                case 1: return "Aeroplane";
                case 2: return "Helicopter (or Multirotor)";
                case 3: return "Gyroplane";
                case 4: return "VTOL";
                case 5: return "Ornithopter";
                case 6: return "Glider";
                case 7: return "Kite";
                case 8: return "Free Balloon";
                case 9: return "Captive Balloon";
                case 10: return "Airship";
                case 11: return "Free Fall/Parachute";
                case 12: return "Rocket";
                case 13: return "Tethered Powered Aircraft";
                case 14: return "Ground Obstacle";
                case 15: return "Other";
                default: return "Unknown";
            }
        }

        private String getStatusString(int status) {
            switch (status) {
                case 0: return "Undeclared";
                case 1: return "Ground";
                case 2: return "Airborne";
                case 3: return "Emergency";
                case 4: return "Remote ID System Failure";
                default: return "Unknown";
            }
        }

        private String getLocationTypeString(int locationType) {
            switch (locationType) {
                case 0: return "Takeoff";
                case 1: return "Live GNSS";
                case 2: return "Fixed";
                default: return "Unknown";
            }
        }

        private String getClassificationString(int classification) {
            switch (classification) {
                case 0: return "Undeclared";
                case 1: return "EU";
                default: return "Unknown";
            }
        }

        private String getOperatorIdTypeString(int idType) {
            switch (idType) {
                case 0: return "CAA Assigned Operator ID";
                default: return "Unknown";
            }
        }

        private String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }