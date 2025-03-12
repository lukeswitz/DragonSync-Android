package com.rootdown.dragonsync.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.rootdown.dragonsync.models.StatusMessage;
import java.util.ArrayList;
import java.util.List;

public class StatusViewModel extends ViewModel {
    private final MutableLiveData<List<StatusMessage>> statusMessages = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<StatusMessage>> getStatusMessages() {
        return statusMessages;
    }

    public void checkSystemThresholds() {
        // Will implement based on iOS version
    }

    public void updateStatusMessage(StatusMessage message) {
        if (message == null) return;

        List<StatusMessage> currentMessages = statusMessages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }

        boolean updated = false;
        if (message.getId() != null) {
            for (int i = 0; i < currentMessages.size(); i++) {
                StatusMessage existing = currentMessages.get(i);
                if (existing.getId() != null && existing.getId().equals(message.getId())) {
                    currentMessages.set(i, message);
                    updated = true;
                    break;
                }
            }
        }

        if (!updated) {
            currentMessages.add(message);
        }

        statusMessages.postValue(new ArrayList<>(currentMessages));
    }
}