package com.rootdown.dragonsync.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rootdown.dragonsync.network.RebelHistoryManager;
import com.rootdown.dragonsync.utils.DroneStorage;
import com.rootdown.dragonsync.utils.DroneStorage.DroneEncounter;

import java.util.ArrayList;
import java.util.List;

public class HistoryViewModel extends ViewModel {

    private final DroneStorage droneStorage;
    private final RebelHistoryManager historyManager;

    private final MutableLiveData<List<DroneEncounter>> encounters = new MutableLiveData<>();
    private final MutableLiveData<List<RebelHistoryManager.DroneHistoryEntry>> hourlyRebels = new MutableLiveData<>();
    private final MutableLiveData<List<RebelHistoryManager.DroneHistoryEntry>> weeklyRebels = new MutableLiveData<>();

    public HistoryViewModel(DroneStorage ds, RebelHistoryManager hm) {
        this.droneStorage = ds;
        this.historyManager = hm;
        refreshData();
    }

    public LiveData<List<DroneEncounter>> getEncounters()      { return encounters;   }
    public LiveData<List<RebelHistoryManager.DroneHistoryEntry>> getHourlyRebels() { return hourlyRebels; }
    public LiveData<List<RebelHistoryManager.DroneHistoryEntry>> getWeeklyRebels() { return weeklyRebels; }

    public void refreshData() {
        long now = System.currentTimeMillis();
        long lastHour  = now - 60 * 60 * 1000L;
        long lastWeek  = now - 7L * 24 * 60 * 60 * 1000;

        encounters.postValue(new ArrayList<>(droneStorage.getEncounters().values()));
        hourlyRebels.postValue(historyManager.getRebelHistory(lastHour, now));
        weeklyRebels.postValue(historyManager.getRebelHistory(lastWeek, now));
    }
}
