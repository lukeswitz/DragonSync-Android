package com.rootdown.dragonsync.network;

import android.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;

public class JsonDataParser {
    private static final String TAG = "JsonDataParser";

    public CoTMessage parseData(String jsonData) {
        try {
            JsonElement element = JsonParser.parseString(jsonData);

            // Check if we have ESP32 format (single object) or DJI/BT/WiFi/Sniff format (array)
            if (element.isJsonObject()) {
                // ESP32 Format: Single JSON object
                return parseESP32Format(element.getAsJsonObject());
            } else if (element.isJsonArray()) {
                // DJI/BT/WiFi/Sniff Format: Array of JSON objects
                return parseDroneArrayFormat(element.getAsJsonArray());
            } else {
                Log.e(TAG, "Unknown JSON format: neither object nor array");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON data: " + e.getMessage());
            return null;
        }
    }

    private CoTMessage parseESP32Format(JsonObject json) {
        CoTMessage message = new CoTMessage();

        // Parse Basic ID
        if (json.has("Basic ID")) {
            JsonObject basicId = json.getAsJsonObject("Basic ID");
            if (basicId.has("id")) message.setUid(basicId.get("id").getAsString());
            if (basicId.has("MAC")) message.setMac(basicId.get("MAC").getAsString());
            if (basicId.has("id_type")) message.setIdType(basicId.get("id_type").getAsString());
            if (basicId.has("ua_type")) {
                int uaTypeValue = basicId.get("ua_type").getAsInt();
                message.setUaType(mapUAType(uaTypeValue));
            }
        }

        // Parse Location/Vector
        if (json.has("Location/Vector Message")) {
            JsonObject location = json.getAsJsonObject("Location/Vector Message");
            if (location.has("latitude")) message.setLat(String.valueOf(location.get("latitude").getAsDouble()));
            if (location.has("longitude")) message.setLon(String.valueOf(location.get("longitude").getAsDouble()));
            if (location.has("speed")) message.setSpeed(String.valueOf(location.get("speed").getAsDouble()));
            if (location.has("vert_speed")) message.setVspeed(String.valueOf(location.get("vert_speed").getAsDouble()));
            if (location.has("geodetic_altitude")) message.setAlt(String.valueOf(location.get("geodetic_altitude").getAsDouble()));
            if (location.has("height_agl")) message.setHeight(String.valueOf(location.get("height_agl").getAsDouble()));
            if (location.has("direction")) message.setDirection(String.valueOf(location.get("direction").getAsDouble()));
            if (location.has("horiz_acc")) message.setHorizontalAccuracy(String.valueOf(location.get("horiz_acc").getAsInt()));
            if (location.has("vert_acc")) message.setVerticalAccuracy(String.valueOf(location.get("vert_acc").getAsInt()));
            if (location.has("timestamp")) message.setTimestamp(String.valueOf(location.get("timestamp").getAsLong()));
        }

        // Parse Self-ID
        if (json.has("Self-ID Message")) {
            JsonObject selfId = json.getAsJsonObject("Self-ID Message");
            if (selfId.has("text")) message.setSelfIDText(selfId.get("text").getAsString());
            if (selfId.has("description")) message.setDescription(selfId.get("description").getAsString());
        }

        // Parse System Message - critical for operator and home locations
        if (json.has("System Message")) {
            JsonObject system = json.getAsJsonObject("System Message");
            if (system.has("operator_lat")) message.setPilotLat(String.valueOf(system.get("operator_lat").getAsDouble()));
            if (system.has("operator_lon")) message.setPilotLon(String.valueOf(system.get("operator_lon").getAsDouble()));

            // Home location for DJI drones
            if (system.has("home_lat")) message.setHomeLat(String.valueOf(system.get("home_lat").getAsDouble()));
            if (system.has("home_lon")) message.setHomeLon(String.valueOf(system.get("home_lon").getAsDouble()));

            // Additional system params
            if (system.has("area_count")) message.setAreaCount(String.valueOf(system.get("area_count").getAsInt()));
            if (system.has("area_radius")) message.setAreaRadius(String.valueOf(system.get("area_radius").getAsInt()));
            if (system.has("area_ceiling")) message.setAreaCeiling(String.valueOf(system.get("area_ceiling").getAsInt()));
            if (system.has("area_floor")) message.setAreaFloor(String.valueOf(system.get("area_floor").getAsInt()));
        }

        return message;
    }

    private CoTMessage parseDroneArrayFormat(JsonArray jsonArray) {
        CoTMessage message = new CoTMessage();

        // Iterate through each message block in the array
        for (JsonElement element : jsonArray) {
            if (!element.isJsonObject()) continue;

            JsonObject obj = element.getAsJsonObject();

            // Check for Basic ID
            if (obj.has("Basic ID")) {
                JsonObject basicId = obj.getAsJsonObject("Basic ID");
                if (basicId.has("id")) message.setUid(basicId.get("id").getAsString());
                if (basicId.has("MAC")) message.setMac(basicId.get("MAC").getAsString());
                if (basicId.has("RSSI") && basicId.get("RSSI").isJsonPrimitive()) {
                    message.setRssi(basicId.get("RSSI").getAsInt());
                }
                if (basicId.has("description")) message.setDescription(basicId.get("description").getAsString());
                if (basicId.has("id_type")) message.setIdType(basicId.get("id_type").getAsString());
                if (basicId.has("ua_type")) {
                    // Handle ua_type as either string or number
                    if (basicId.get("ua_type").isJsonPrimitive()) {
                        if (basicId.get("ua_type").getAsJsonPrimitive().isNumber()) {
                            message.setUaType(mapUAType(basicId.get("ua_type").getAsInt()));
                        } else {
                            message.setUaType(mapUATypeFromString(basicId.get("ua_type").getAsString()));
                        }
                    }
                }
            }

            // Check for Location/Vector Message
            else if (obj.has("Location/Vector Message")) {
                JsonObject location = obj.getAsJsonObject("Location/Vector Message");

                // Handle different data formats
                if (location.has("latitude")) {
                    JsonElement latElement = location.get("latitude");
                    if (latElement.isJsonPrimitive()) {
                        if (latElement.getAsJsonPrimitive().isNumber()) {
                            message.setLat(String.valueOf(latElement.getAsDouble()));
                        } else {
                            message.setLat(latElement.getAsString());
                        }
                    }
                }

                if (location.has("longitude")) {
                    JsonElement lonElement = location.get("longitude");
                    if (lonElement.isJsonPrimitive()) {
                        if (lonElement.getAsJsonPrimitive().isNumber()) {
                            message.setLon(String.valueOf(lonElement.getAsDouble()));
                        } else {
                            message.setLon(lonElement.getAsString());
                        }
                    }
                }

                // Handle speed values (could be numeric or strings like "0.25 m/s")
                if (location.has("speed")) {
                    JsonElement speedElement = location.get("speed");
                    if (speedElement.isJsonPrimitive()) {
                        if (speedElement.getAsJsonPrimitive().isNumber()) {
                            message.setSpeed(String.valueOf(speedElement.getAsDouble()));
                        } else {
                            String speedStr = speedElement.getAsString();
                            if (speedStr.contains(" ")) {
                                speedStr = speedStr.split(" ")[0]; // Extract numeric part
                            }
                            message.setSpeed(speedStr);
                        }
                    }
                }

                // Other location values with same pattern
                parseLocationValues(message, location);
            }

            // Check for Self-ID Message
            else if (obj.has("Self-ID Message")) {
                JsonObject selfId = obj.getAsJsonObject("Self-ID Message");
                if (selfId.has("text")) message.setSelfIDText(selfId.get("text").getAsString());
            }

            // Check for System Message (operator location, etc)
            else if (obj.has("System Message")) {
                JsonObject system = obj.getAsJsonObject("System Message");

                // Special format - in some DJI formats, the operator is directly in latitude/longitude
                if (system.has("latitude") && system.has("longitude")) {
                    // We have operator location directly in system message
                    JsonElement latElement = system.get("latitude");
                    JsonElement lonElement = system.get("longitude");

                    if (latElement.isJsonPrimitive() && lonElement.isJsonPrimitive()) {
                        if (latElement.getAsJsonPrimitive().isNumber() &&
                                lonElement.getAsJsonPrimitive().isNumber()) {
                            message.setPilotLat(String.valueOf(latElement.getAsDouble()));
                            message.setPilotLon(String.valueOf(lonElement.getAsDouble()));
                        } else {
                            // Handle string format (might be redacted with "xxxx")
                            String pilotLat = latElement.getAsString();
                            String pilotLon = lonElement.getAsString();
                            if (!pilotLat.contains("x") && !pilotLon.contains("x")) {
                                message.setPilotLat(pilotLat);
                                message.setPilotLon(pilotLon);
                            }
                        }
                    }
                }

                // Standard operator location in dedicated fields
                if (system.has("operator_lat") && system.has("operator_lon")) {
                    message.setPilotLat(String.valueOf(system.get("operator_lat").getAsDouble()));
                    message.setPilotLon(String.valueOf(system.get("operator_lon").getAsDouble()));
                }

                // Home location for DJI
                if (system.has("home_lat") && system.has("home_lon")) {
                    message.setHomeLat(String.valueOf(system.get("home_lat").getAsDouble()));
                    message.setHomeLon(String.valueOf(system.get("home_lon").getAsDouble()));
                }
            }
        }

        return message;
    }

    private void parseLocationValues(CoTMessage message, JsonObject location) {
        // Parse all the other location parameters with proper type handling
        if (location.has("vert_speed")) {
            JsonElement vspeedElement = location.get("vert_speed");
            if (vspeedElement.isJsonPrimitive()) {
                if (vspeedElement.getAsJsonPrimitive().isNumber()) {
                    message.setVspeed(String.valueOf(vspeedElement.getAsDouble()));
                } else {
                    String vspeedStr = vspeedElement.getAsString();
                    if (vspeedStr.contains(" ")) {
                        vspeedStr = vspeedStr.split(" ")[0]; // Extract numeric part
                    }
                    message.setVspeed(vspeedStr);
                }
            }
        }

        if (location.has("geodetic_altitude")) {
            JsonElement altElement = location.get("geodetic_altitude");
            if (altElement.isJsonPrimitive()) {
                if (altElement.getAsJsonPrimitive().isNumber()) {
                    message.setAlt(String.valueOf(altElement.getAsDouble()));
                } else {
                    String altStr = altElement.getAsString();
                    if (altStr.contains(" ")) {
                        altStr = altStr.split(" ")[0]; // Extract numeric part
                    }
                    message.setAlt(altStr);
                }
            }
        }

        if (location.has("height_agl")) {
            JsonElement heightElement = location.get("height_agl");
            if (heightElement.isJsonPrimitive()) {
                if (heightElement.getAsJsonPrimitive().isNumber()) {
                    message.setHeight(String.valueOf(heightElement.getAsDouble()));
                } else {
                    String heightStr = heightElement.getAsString();
                    if (heightStr.contains(" ")) {
                        heightStr = heightStr.split(" ")[0]; // Extract numeric part
                    }
                    message.setHeight(heightStr);
                }
            }
        }

        if (location.has("direction")) {
            JsonElement dirElement = location.get("direction");
            if (dirElement.isJsonPrimitive()) {
                if (dirElement.getAsJsonPrimitive().isNumber()) {
                    message.setDirection(String.valueOf(dirElement.getAsInt()));
                } else {
                    message.setDirection(dirElement.getAsString());
                }
            }
        }

        // Timestamp handling
        if (location.has("timestamp")) {
            JsonElement timestampElement = location.get("timestamp");
            if (timestampElement.isJsonPrimitive()) {
                if (timestampElement.getAsJsonPrimitive().isNumber()) {
                    message.setTimestamp(String.valueOf(timestampElement.getAsLong()));
                } else {
                    // Handle timestamp format like "28 min 40.0 s" by converting to milliseconds
                    try {
                        String timeStr = timestampElement.getAsString();
                        long timestamp = parseTextualTimestamp(timeStr);
                        message.setTimestamp(String.valueOf(timestamp));
                    } catch (Exception e) {
                        // If parsing fails, just use the raw string
                        message.setTimestamp(timestampElement.getAsString());
                    }
                }
            }
        }
    }

    private long parseTextualTimestamp(String timeString) {
        // Parse timestamp format like "28 min 40.0 s"
        long timestamp = 0;

        try {
            // Handle simple case first
            if (timeString.matches("\\d+")) {
                return Long.parseLong(timeString);
            }

            // Handle complex format
            if (timeString.contains("min")) {
                String minPart = timeString.split("min")[0].trim();
                timestamp += Long.parseLong(minPart) * 60 * 1000; // minutes to ms
            }

            if (timeString.contains("s")) {
                String secPart = timeString.split("s")[0];
                if (secPart.contains("min")) {
                    secPart = secPart.split("min")[1].trim();
                }
                float seconds = Float.parseFloat(secPart);
                timestamp += (long)(seconds * 1000); // seconds to ms
            }

            // If it's relative time, convert to absolute by adding current time
            if (timestamp > 0) {
                timestamp = System.currentTimeMillis() - timestamp;
            }

            return timestamp;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing textual timestamp: " + timeString);
            return System.currentTimeMillis(); // Fallback to current time
        }
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
            return uaTypeStr.contains("free") ?
                    DroneSignature.IdInfo.UAType.FREE_BALLOON :
                    DroneSignature.IdInfo.UAType.CAPTIVE;
        } else if (uaTypeStr.contains("airship")) {
            return DroneSignature.IdInfo.UAType.AIRSHIP;
        } else if (uaTypeStr.contains("rocket")) {
            return DroneSignature.IdInfo.UAType.ROCKET;
        } else if (uaTypeStr.contains("kite")) {
            return DroneSignature.IdInfo.UAType.KITE;
        } else if (uaTypeStr.contains("none")) {
            return DroneSignature.IdInfo.UAType.NONE;
        }

        return DroneSignature.IdInfo.UAType.OTHER;
    }
}