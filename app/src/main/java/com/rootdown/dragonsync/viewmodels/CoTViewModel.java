package com.rootdown.dragonsync.viewmodels;

import android.app.Application;
import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;
import com.rootdown.dragonsync.network.MulticastHandler;
import com.rootdown.dragonsync.network.XMLParser;
import com.rootdown.dragonsync.network.ZMQHandler;
import com.rootdown.dragonsync.utils.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CoTViewModel extends ViewModel {
    private final Settings settings;
    private final XMLParser xmlParser;

    // No-argument constructor
    public CoTViewModel() {
        // Initialize without context
        xmlParser = new XMLParser();
        settings = null; // TODO this thing
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
                        processIncomingMessage(message);
                    }

                    @Override
                    public void onError(String error) {
                        // Handle error
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
                this::processIncomingMessage,
                this::processIncomingMessage
        );
        isListening.setValue(true);
    }

    private void processIncomingMessage(String message) {
        XMLParser.ParseResult result = xmlParser.parse(message);
        if (result.cotMessage != null) {
            updateMessage(result.cotMessage);
        }
    }

    private void updateMessage(CoTMessage message) {
        List<CoTMessage> currentMessages = parsedMessages.getValue();
        updateMacHistory(message);

        int existingIndex = findExistingMessageIndex(currentMessages, message);
        if (existingIndex != -1) {
            currentMessages.set(existingIndex, message);
        } else {
            currentMessages.add(message);
        }

        parsedMessages.postValue(currentMessages);
    }

    private int findExistingMessageIndex(List<CoTMessage> messages, CoTMessage newMessage) {
        if (messages == null) return -1;

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
        if (message.getMac() == null || message.getMac().isEmpty()) return;

        Map<String, Set<String>> history = macIdHistory.getValue();
        Set<String> macs = history.getOrDefault(message.getUid(), new HashSet<>());
        macs.add(message.getMac());

        // Check for MAC randomization
        if (message.getMac().length() >= 2) {
            char secondChar = message.getMac().charAt(1);
            if ("26AE".indexOf(secondChar) != -1) {
                macProcessing.put(message.getUid(), true);
            }
        }

        history.put(message.getUid(), macs);
        macIdHistory.postValue(history);
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