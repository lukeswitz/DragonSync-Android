package com.rootdown.dragonsync.ui.fragments;   // keep with your other Fragments

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.rootdown.dragonsync.network.RebelDashboardViewModel;
import com.rootdown.dragonsync.network.RebelHistoryManager;

public class RebelDashboardViewModelFactory implements ViewModelProvider.Factory {
    private final RebelHistoryManager historyManager;
    public RebelDashboardViewModelFactory(RebelHistoryManager hm) { this.historyManager = hm; }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> cls) {
        if (cls.isAssignableFrom(RebelDashboardViewModel.class)) {
            return (T) new RebelDashboardViewModel(historyManager);
        }
        throw new IllegalArgumentException("Unknown ViewModel " + cls);
    }
}