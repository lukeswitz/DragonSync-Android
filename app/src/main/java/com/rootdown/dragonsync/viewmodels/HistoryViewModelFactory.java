package com.rootdown.dragonsync.viewmodels;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.rootdown.dragonsync.network.RebelHistoryManager;
import com.rootdown.dragonsync.utils.DroneStorage;

public class HistoryViewModelFactory implements ViewModelProvider.Factory {
    private final DroneStorage ds;
    private final RebelHistoryManager hm;

    public HistoryViewModelFactory(DroneStorage ds, RebelHistoryManager hm) {
        this.ds = ds; this.hm = hm;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> cls) {
        if (cls.isAssignableFrom(HistoryViewModel.class)) {
            return (T) new HistoryViewModel(ds, hm);
        }
        throw new IllegalArgumentException("Unknown ViewModel " + cls);
    }
}
