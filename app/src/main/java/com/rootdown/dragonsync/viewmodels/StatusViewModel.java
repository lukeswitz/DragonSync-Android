// java/com.rootdown.dragonsync/viewmodels/StatusViewModel.java
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
        List<StatusMessage> currentMessages = statusMessages.getValue();
        if (currentMessages != null) {
            // Update or add new message
            boolean updated = false;
            for (int i = 0; i < currentMessages.size(); i++) {
                if (currentMessages.get(i).getId().equals(message.getId())) {
                    currentMessages.set(i, message);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                currentMessages.add(message);
            }
            statusMessages.setValue(currentMessages);
        }
    }
}