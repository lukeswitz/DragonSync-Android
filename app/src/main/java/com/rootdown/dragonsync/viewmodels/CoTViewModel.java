package com.rootdown.dragonsync.viewmodels;

import android.app.Application;
import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.location.Location;
import android.util.Log;

import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;
import com.rootdown.dragonsync.network.MulticastHandler;
import com.rootdown.dragonsync.network.XMLParser;
import com.rootdown.dragonsync.network.ZMQHandler;
import com.rootdown.dragonsync.utils.Constants;
import com.rootdown.dragonsync.utils.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CoTViewModel extends ViewModel {
    private static final String TAG = "CoTViewModel";
    private final Settings settings;
    private final XMLParser xmlParser;

    // No-argument constructor
    public CoTViewModel() {
        // Initialize without context
        xmlParser = new XMLParser();
        settings = null;
    }

    public CoTViewModel(Application application) {
        settings = Settings.getInstance(application);
        xmlParser = new XMLParser();
    }
    private final MutableLiveData<List<CoTMessage>> parsedMessages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<DroneSignature>> droneSignatures = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<String, Set<String>>> macIdHistory = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Boolean> isListening = new MutableLiveData<>(false);
    private final Map<String, Boolean> macProcessing = new HashMap<>();

    private ZMQHandler zmqHandler;
    private MulticastHandler multicastHandler;

    public CoTViewModel(Context context) {
        settings = Settings.getInstance(context);
        xmlParser = new XMLParser();
    }

    public void startListening() {
        if (settings == null) {
            throw new IllegalStateException("Settings must be initialized before starting listening. Call initializeWithContext() first.");
        }

        if (isListening.getValue()) return;

        switch (settings.getConnectionMode()) {
            case MULTICAST:
                startMulticastListening();
                break;
            case ZMQ:
                startZMQListening();
                break;
            default:
                throw new IllegalStateException("Unknown connection mode: " + settings.getConnectionMode());
        }
    }

    private void startMulticastListening() {
        multicastHandler = new MulticastHandler();
        multicastHandler.startListening(
                settings.getMulticastHost(),
                settings.getMulticastPort(),
                new MulticastHandler.MessageHandler() {
                    @Override
                    public void onMessage(String message) {
                        Log.d(TAG, "Multicast message received: " +
                                message.substring(0, Math.min(100, message.length())) + "...");
                        processIncomingMessage(message);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Multicast error: " + error);
                    }
                }
        );
        isListening.setValue(true);
    }

    private void startZMQListening() {
        zmqHandler = new ZMQHandler();
        zmqHandler.connect(
                settings.getZmqHost(),
                settings.getZmqTelemetryPort(),
                settings.getZmqStatusPort(),
                message -> {
                    Log.d(TAG, "ZMQ telemetry received: " +
                            message.substring(0, Math.min(100, message.length())) + "...");
                    processIncomingMessage(message);
                },
                message -> {
                    Log.d(TAG, "ZMQ status received: " +
                            message.substring(0, Math.min(100, message.length())) + "...");
                    processStatusMessage(message);
                }
        );
        isListening.setValue(true);
    }

    private void processIncomingMessage(String message) {
        XMLParser.ParseResult result = xmlParser.parse(message);

        if (result.error != null) {
            Log.e(TAG, "Error parsing message: " + result.error);
            return;
        }

        if (result.cotMessage != null) {
            Log.d(TAG, "Successfully parsed CoT message with UID: " + result.cotMessage.getUid());
            updateMessage(result.cotMessage);
        } else {
            Log.w(TAG, "No valid message parsed from incoming data");
        }
    }

    private void processStatusMessage(String message) {
        // Handle status messages (system stats, etc.)
        XMLParser.ParseResult result = xmlParser.parse(message);

        if (result.error != null) {
            Log.e(TAG, "Error parsing status message: " + result.error);
            return;
        }

        if (result.statusMessage != null) {
            // Here you would typically update a status LiveData object
            // or broadcast the status message to other components
            Log.d(TAG, "Successfully parsed status message");
        } else if (result.cotMessage != null) {
            // Sometimes status messages might contain drone information too
            Log.d(TAG, "Status message contained CoT data, updating");
            updateMessage(result.cotMessage);
        }
    }

    public void updateMessage(CoTMessage message) {
        if (message == null || message.getUid() == null || message.getUid().isEmpty()) {
            Log.w(TAG, "Ignoring invalid message with null or empty UID");
            return;
        }

        List<CoTMessage> currentMessages = parsedMessages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }

        // Update MAC history for this drone
        updateMacHistory(message);

        // Perform spoof detection if enabled
        if (settings != null && settings.isSpoofDetectionEnabled()) {
            performSpoofDetection(message, currentMessages);
        }

        // Find existing message or add new one
        int existingIndex = findExistingMessageIndex(currentMessages, message);
        if (existingIndex != -1) {
            Log.d(TAG, "Updating existing message for UID: " + message.getUid());
            currentMessages.set(existingIndex, message);
        } else {
            Log.d(TAG, "Adding new message for UID: " + message.getUid());
            currentMessages.add(message);
        }

        // Limit the list size to avoid memory issues
        if (currentMessages.size() > Constants.MAX_MESSAGES_CACHE) {
            currentMessages = new ArrayList<>(
                    currentMessages.subList(
                            currentMessages.size() - Constants.MAX_MESSAGES_CACHE,
                            currentMessages.size()
                    )
            );
            Log.d(TAG, "Trimmed message cache to " + Constants.MAX_MESSAGES_CACHE + " items");
        }

        // Update the LiveData with a new list to trigger observers
        parsedMessages.postValue(new ArrayList<>(currentMessages));

        // Update drone signatures based on this message
        updateDroneSignatures(message);
    }

    private int findExistingMessageIndex(List<CoTMessage> messages, CoTMessage newMessage) {
        if (messages == null || newMessage == null || newMessage.getUid() == null) {
            return -1;
        }

        // First try to match by MAC address if available
        if (newMessage.getMac() != null && !newMessage.getMac().isEmpty()) {
            for (int i = 0; i < messages.size(); i++) {
                if (newMessage.getMac().equals(messages.get(i).getMac())) {
                    return i;
                }
            }
        }

        // Then try to match by UID
        for (int i = 0; i < messages.size(); i++) {
            if (newMessage.getUid().equals(messages.get(i).getUid())) {
                return i;
            }
        }

        return -1;
    }

    private void updateMacHistory(CoTMessage message) {
        if (message.getMac() == null || message.getMac().isEmpty() || message.getUid() == null) {
            return;
        }

        Map<String, Set<String>> history = macIdHistory.getValue();
        if (history == null) {
            history = new HashMap<>();
        }

        // Get or create the set of MACs for this UID
        Set<String> macs = history.getOrDefault(message.getUid(), new HashSet<>());
        macs.add(message.getMac());

        // Check for randomized MAC (for spoof detection)
        if (message.getMac().length() >= 2) {
            char secondChar = message.getMac().charAt(1);
            if ("26AE".indexOf(secondChar) != -1) {
                macProcessing.put(message.getUid(), true);
                Log.d(TAG, "Detected possible MAC randomization for UID: " + message.getUid());
            }
        }

        history.put(message.getUid(), macs);
        macIdHistory.postValue(new HashMap<>(history));
    }

    private void performSpoofDetection(CoTMessage message, List<CoTMessage> currentMessages) {
        // Find existing message for this UID if any
        CoTMessage existingMessage = null;
        for (CoTMessage msg : currentMessages) {
            if (msg.getUid().equals(message.getUid())) {
                existingMessage = msg;
                break;
            }
        }

        if (existingMessage == null) {
            // No previous message to compare with
            return;
        }

        // Compare location, if both messages have valid coordinates
        if (message.getCoordinate() != null && existingMessage.getCoordinate() != null) {
            Location newLocation = message.getCoordinate();
            Location oldLocation = existingMessage.getCoordinate();

            // Calculate distance between points
            float distance = newLocation.distanceTo(oldLocation);

            // Calculate time difference
            long timeDiff = 0;
            try {
                if (message.getTimestamp() != null && existingMessage.getTimestamp() != null) {
                    long newTime = Long.parseLong(message.getTimestamp());
                    long oldTime = Long.parseLong(existingMessage.getTimestamp());
                    timeDiff = newTime - oldTime;
                }
            } catch (NumberFormatException e) {
                // Use a default time difference if parsing fails
                timeDiff = 1000; // Assume 1 second
            }

            // Only check if time difference is positive and reasonable
            if (timeDiff > 0) {
                // Calculate speed in m/s
                float speed = distance / (timeDiff / 1000f);

                // Check if speed exceeds maximum realistic drone speed
                if (speed > Constants.MAX_SPEED_MPS && distance > Constants.MIN_POSITION_CHANGE) {
                    // Potential spoofing detected - unrealistic movement
                    Log.w(TAG, "Potential spoofing detected for UID: " + message.getUid() +
                            " - Calculated speed: " + speed + " m/s, Distance: " + distance + " m");

                    // Mark as spoofed
                    message.setSpoofed(true);

                    // Create spoofing details if needed
                    DroneSignature.SpoofDetectionResult spoofResult = new DroneSignature.SpoofDetectionResult();
                    message.setSpoofingDetails(spoofResult);
                }
            }
        }

        // Compare RSSI changes if both messages have RSSI values
        if (message.getRssi() != null && existingMessage.getRssi() != null) {
            int rssiDelta = Math.abs(message.getRssi() - existingMessage.getRssi());

            if (rssiDelta > Constants.MIN_RSSI_DELTA) {
                // Large RSSI change might indicate spoofing
                Log.w(TAG, "Suspicious RSSI change for UID: " + message.getUid() +
                        " - Delta: " + rssiDelta + " dBm");

                // This alone isn't conclusive, but can contribute to overall confidence
                if (message.isSpoofed()) {
                    // Increment confidence if already marked as spoofed
                    Log.w(TAG, "Multiple spoofing indicators detected for UID: " + message.getUid());
                }
            }
        }
    }

    private void updateDroneSignatures(CoTMessage message) {
        // Get current signatures
        List<DroneSignature> signatures = droneSignatures.getValue();
        if (signatures == null) {
            signatures = new ArrayList<>();
        }

        // Find existing signature or create new one
        DroneSignature existingSignature = null;
        for (DroneSignature sig : signatures) {
            if (sig.getPrimaryId() != null &&
                    sig.getPrimaryId().getId() != null &&
                    sig.getPrimaryId().getId().equals(message.getUid())) {
                existingSignature = sig;
                break;
            }
        }

        if (existingSignature != null) {
            // Update existing signature
            updateExistingSignature(existingSignature, message);
        } else {
            // Create new signature
            DroneSignature newSignature = createSignatureFromMessage(message);
            if (newSignature != null) {
                signatures.add(newSignature);
            }
        }

        // Update the LiveData
        droneSignatures.postValue(new ArrayList<>(signatures));
    }

    private void updateExistingSignature(DroneSignature signature, CoTMessage message) {
        // Update signature with new message data
        // This would update position, movement vector, etc.

        // Implementation depends on DroneSignature class structure TODO!
        // For now, just log that we're updating
        Log.d(TAG, "Updating drone signature for UID: " + message.getUid());
    }

    private DroneSignature createSignatureFromMessage(CoTMessage message) {
        // Create a new drone signature from the message
        // This would set all initial signature data

        // Implementation depends on DroneSignature class structure TODO!
        // For now, just log that we're creating a new signature
        Log.d(TAG, "Creating new drone signature for UID: " + message.getUid());

        // Return null for now as the implementation depends on DroneSignature structure
        return null;
    }

    public void stopListening() {
        if (zmqHandler != null) {
            zmqHandler.disconnect();
            zmqHandler = null;
        }
        if (multicastHandler != null) {
            multicastHandler.stopListening();
            multicastHandler = null;
        }
        isListening.setValue(false);
    }

    public void clearMessages() {
        parsedMessages.setValue(new ArrayList<>());
        droneSignatures.setValue(new ArrayList<>());
        macIdHistory.setValue(new HashMap<>());
        macProcessing.clear();
    }

    // Getters for LiveData
    public LiveData<List<CoTMessage>> getParsedMessages() {
        return parsedMessages;
    }

    public LiveData<List<DroneSignature>> getDroneSignatures() {
        return droneSignatures;
    }

    public LiveData<Map<String, Set<String>>> getMacHistory() {
        return macIdHistory;
    }

    public LiveData<Boolean> getIsListening() {
        return isListening;
    }

    public Map<String, Boolean> getMacProcessing() {
        return macProcessing;
    }
}