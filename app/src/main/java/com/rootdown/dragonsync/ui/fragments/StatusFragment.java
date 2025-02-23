// java/com.rootdown.dragonsync/ui/fragments/StatusFragment.java
package com.rootdown.dragonsync.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.ui.adapters.StatusListAdapter;
import com.rootdown.dragonsync.viewmodels.ServiceViewModel;
import com.rootdown.dragonsync.viewmodels.StatusViewModel;

public class StatusFragment extends Fragment {
    private StatusViewModel statusViewModel;
    private ServiceViewModel serviceViewModel;
    private StatusListAdapter adapter;
    private RecyclerView statusList;
    private boolean showServiceManagement = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusViewModel = new ViewModelProvider(requireActivity()).get(StatusViewModel.class);
        serviceViewModel = new ViewModelProvider(requireActivity()).get(ServiceViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);

        statusList = view.findViewById(R.id.status_list);
        setupRecyclerView();
        setupObservers();

        return view;
    }

    private void setupRecyclerView() {
        adapter = new StatusListAdapter();
        statusList.setLayoutManager(new LinearLayoutManager(requireContext()));
        statusList.setAdapter(adapter);
    }

    private void setupObservers() {
        statusViewModel.getStatusMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.updateMessages(messages);
            if (!messages.isEmpty()) {
                statusList.scrollToPosition(messages.size() - 1);
            }
        });

        serviceViewModel.getHealthReport().observe(getViewLifecycleOwner(), report -> {
            // Update health status UI
        });

        serviceViewModel.getCriticalServices().observe(getViewLifecycleOwner(), services -> {
            // Update critical services UI
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        serviceViewModel.startMonitoring();
    }

    @Override
    public void onPause() {
        super.onPause();
        serviceViewModel.stopMonitoring();
    }
}