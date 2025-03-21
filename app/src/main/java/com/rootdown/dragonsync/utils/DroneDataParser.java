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
                    messagesArray.put(parseBasicIdMessage(data, macAddress, rssi, messageVersion));
                    break;

                case MESSAGE_TYPE_LOCATION:
                    messagesArray.put(parseLocationMessage(data, macAddress, rssi, messageVersion));
                    break;

                case MESSAGE_TYPE_SELF_ID:
                    messagesArray.put(parseSelfIdMessage(data, macAddress, rssi, messageVersion));
                    break;

                case MESSAGE_TYPE_SYSTEM:
                    messagesArray.put(parseSystemMessage(data, macAddress, rssi, messageVersion));
                    break;

                case MESSAGE_TYPE_OPERATOR_ID:
                    messagesArray.put(parseOperatorIdMessage(data, macAddress, rssi, messageVersion));
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

    public JSONArray parseWiFiBeaconData(byte[] data, String macAddress, int rssi) {
        JSONArray messagesArray = new JSONArray();

        try {
            if (data.length < 1) return messagesArray;

            // Get message type from first byte (top 4 bits)
            int headerByte = data[0] & 0xFF;
            int messageType = (headerByte >> 4) & 0x0F;
            int messageVersion = headerByte & 0x0F;

            Log.d(TAG, "WiFi Beacon data: type=" + messageType + ", version=" + messageVersion);

            // Rest of parsing is similar to Bluetooth but with different offset expectations
            switch (messageType) {
                case MESSAGE_TYPE_BASIC_ID:
                    messagesArray.put(parseBasicIdMessage(data, macAddress, rssi, messageVersion, 0));
                    break;

                case MESSAGE_TYPE_LOCATION:
                    messagesArray.put(parseLocationMessage(data, macAddress, rssi, messageVersion, 0));
                    break;

                case MESSAGE_TYPE_SELF_ID:
                    messagesArray.put(parseSelfIdMessage(data, macAddress, rssi, messageVersion, 0));
                    break;

                case MESSAGE_TYPE_SYSTEM:
                    messagesArray.put(parseSystemMessage(data, macAddress, rssi, messageVersion, 0));
                    break;

                case MESSAGE_TYPE_OPERATOR_ID:
                    messagesArray.put(parseOperatorIdMessage(data, macAddress, rssi, messageVersion, 0));
                    break;

                case MESSAGE_TYPE_MESSAGE_PACK:
                    // Parse message pack
                    JSONArray packMessages = parseMessagePack(data, macAddress, rssi, messageVersion);
                    for (int i = 0; i < packMessages.length(); i++) {
                        messagesArray.put(packMessages.get(i));
                    }
                    break;

                default:
                    Log.w(TAG, "Unknown message type in WiFi beacon: " + messageType);
//
//                    // Fallback - add a basic message with the MAC address at minimum
//                    JSONObject fallbackObj = new JSONObject();
//                    JSONObject basicId = new JSONObject();
//                    basicId.put("MAC", macAddress);
//                    basicId.put("RSSI", rssi);
//                    basicId.put("id_type", "WiFi Beacon");
//                    basicId.put("id", macAddress);
//                    basicId.put("ua_type", "Unknown");
//                    fallbackObj.put("Basic ID", basicId);
//                    messagesArray.put(fallbackObj);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing WiFi beacon data: " + e.getMessage(), e);
        }

        return messagesArray;
    }

    private JSONObject parseBasicIdMessage(byte[] data, String macAddress, int rssi, int version) {
        // Default offset for Bluetooth scanning
        return parseBasicIdMessage(data, macAddress, rssi, version, 1);
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

    private JSONObject parseLocationMessage(byte[] data, String macAddress, int rssi, int version) {
        return parseLocationMessage(data, macAddress, rssi, version, 1);
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

    private JSONObject parseSelfIdMessage(byte[] data, String macAddress, int rssi, int version) {
        return parseSelfIdMessage(data, macAddress, rssi, version, 1);
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

    private JSONObject parseSystemMessage(byte[] data, String macAddress, int rssi, int version) {
        return parseSystemMessage(data, macAddress, rssi, version, 1);
    }

    private JSONObject parseSystemMessage(byte[] data, String macAddress, int rssi, int version, int offset) {
        JSONObject messageObj = new JSONObject();

        try {
            JSONObject system = new JSONObject();

            // Add protocol version and transport info
            system.put("protocol_version", "F3411." + (version == 0 ? "19" : (version == 1 ? "22" : "23")));
            system.put("MAC", macAddress);
            system.put("RSSI", rssi);

            // Flags byte
            if (data.length >= offset + 1) {
                int flags = data[offset] & 0xFF;
                int operatorLocationType = flags & 0x03;
                int classificationType = (flags >> 2) & 0x07;

                // Operator location type
                switch (operatorLocationType) {
                    case 0:
                        system.put("operator_location_type", "Take Off");
                        break;
                    case 1:
                        system.put("operator_location_type", "Live GNSS");
                        break;
                    case 2:
                        system.put("operator_location_type", "Fixed");
                        break;
                    default:
                        system.put("operator_location_type", "Invalid");
                }

                // Classification type
                switch (classificationType) {
                    case 0:
                        system.put("classification_type", "Undeclared");
                        break;
                    case 1:
                        system.put("classification_type", "EU");
                        break;
                    default:
                        system.put("classification_type", "Reserved");
                }
            }

            // Operator latitude/longitude
            if (data.length >= offset + 9) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 1, 8).order(ByteOrder.LITTLE_ENDIAN);
                int pilotLat = buffer.getInt();
                int pilotLon = buffer.getInt();

                double latitude = pilotLat * LAT_LONG_MULTIPLIER;
                double longitude = pilotLon * LAT_LONG_MULTIPLIER;

                system.put("operator_lat", latitude);
                system.put("operator_lon", longitude);
            }

            // Area count
            if (data.length >= offset + 11) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 9, 2).order(ByteOrder.LITTLE_ENDIAN);
                int areaCount = buffer.getShort() & 0xFFFF;
                system.put("area_count", areaCount);
            }

            // Area radius
            if (data.length >= offset + 12) {
                int radius = data[offset + 11] & 0xFF;
                int areaRadius = radius * 10; // in meters
                system.put("area_radius", areaRadius);
            }

            // Area ceiling and floor
            if (data.length >= offset + 16) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 12, 4).order(ByteOrder.LITTLE_ENDIAN);
                int ceiling = buffer.getShort() & 0xFFFF;
                int floor = buffer.getShort() & 0xFFFF;

                double ceilingAlt = (ceiling / 2.0) - 1000.0;
                double floorAlt = (floor / 2.0) - 1000.0;

                system.put("area_ceiling", String.format("%.1f m", ceilingAlt));
                system.put("area_floor", String.format("%.1f m", floorAlt));
            }

            // Category and class
            if (data.length >= offset + 17) {
                int catClass = data[offset + 16] & 0xFF;
                int category = (catClass >> 4) & 0x0F;
                int classValue = catClass & 0x0F;

                system.put("category", category);
                system.put("class", classValue);
            }

            // Operator altitude
            if (data.length >= offset + 19) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 17, 2).order(ByteOrder.LITTLE_ENDIAN);
                int opAlt = buffer.getShort() & 0xFFFF;

                double operatorAlt = (opAlt / 2.0) - 1000.0;
                system.put("operator_altitude_geo", String.format("%.1f m", operatorAlt));
            }

            // System timestamp (only in version 2+)
            if (version >= 2 && data.length >= offset + 23) {
                ByteBuffer buffer = ByteBuffer.wrap(data, offset + 19, 4).order(ByteOrder.LITTLE_ENDIAN);
                long timestamp = buffer.getInt() & 0xFFFFFFFFL;
                system.put("system_timestamp", timestamp);
            }

            messageObj.put("System Message", system);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating System message: " + e.getMessage());
        }

        return messageObj;
    }

    private JSONObject parseOperatorIdMessage(byte[] data, String macAddress, int rssi, int version) {
        return parseOperatorIdMessage(data, macAddress, rssi, version, 1);
    }

    private JSONObject parseOperatorIdMessage(byte[] data, String macAddress, int rssi, int version, int offset) {
        JSONObject messageObj = new JSONObject();

        try {
            JSONObject operatorId = new JSONObject();

            // Add protocol version and transport info
            operatorId.put("protocol_version", "F3411." + (version == 0 ? "19" : (version == 1 ? "22" : "23")));
            operatorId.put("MAC", macAddress);
            operatorId.put("RSSI", rssi);

            // Operator ID type
            if (data.length >= offset + 1) {
                int idType = data[offset] & 0xFF;

                switch (idType) {
                    case 0:
                        operatorId.put("operator_id_type", "Operator ID");
                        break;
                    default:
                        operatorId.put("operator_id_type", "Reserved");
                }
            }

            // Operator ID (20 bytes)
            if (data.length >= offset + 1 + MAX_ID_BYTE_SIZE) {
                byte[] opIdBytes = Arrays.copyOfRange(data, offset + 1, offset + 1 + MAX_ID_BYTE_SIZE);
                // Get text until null terminator or end
                String opId = new String(opIdBytes, StandardCharsets.UTF_8).trim();
                operatorId.put("operator_id", opId);
            }

            messageObj.put("Operator ID Message", operatorId);

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
}