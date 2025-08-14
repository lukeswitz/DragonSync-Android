package com.rootdown.dragonsync.network;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Map;

public class RebelDashboardViewModel extends ViewModel {
    private final RebelHistoryManager historyManager;
    private final MutableLiveData<List<RebelHistoryManager.DroneHistoryEntry>> recentRebels = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> RebelStats = new MutableLiveData<>();
    private final MutableLiveData<Integer> activeRebelCount = new MutableLiveData<>();

    public RebelDashboardViewModel(RebelHistoryManager historyManager) {
        this.historyManager = historyManager;
        refreshData();
    }

    public LiveData<List<RebelHistoryManager.DroneHistoryEntry>> getRecentRebels() { return recentRebels; }
    public LiveData<Map<String, Integer>> getRebelStats() { return RebelStats; }
    public LiveData<Integer> getActiveRebelCount() { return activeRebelCount; }

    public void refreshData() {
        long now = System.currentTimeMillis();
        long last24Hours = now - (24 * 60 * 60 * 1000);

        List<RebelHistoryManager.DroneHistoryEntry> recent = historyManager.getRebelHistory(last24Hours, now);
        recentRebels.postValue(recent);

        Map<String, Integer> stats = historyManager.getRebelTypeStatistics(last24Hours, now);
        RebelStats.postValue(stats);

        activeRebelCount.postValue(recent.size());
    }
}
