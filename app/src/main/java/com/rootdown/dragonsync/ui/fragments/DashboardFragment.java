package com.rootdown.dragonsync.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.StatusMessage;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;
import com.rootdown.dragonsync.viewmodels.StatusViewModel;

public class DashboardFragment extends Fragment {
    private CoTViewModel cotViewModel;
    private StatusViewModel statusViewModel;

    // View references
    private View systemMetricsView;
    private View droneMetricsView;
    private View sdrMetricsView;
    private RecyclerView dronesList;
    private RecyclerView warningsList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cotViewModel = new ViewModelProvider(requireActivity()).get(CoTViewModel.class);
        statusViewModel = new ViewModelProvider(requireActivity()).get(StatusViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupObservers();
    }

    private void initializeViews(View view) {
        systemMetricsView = view.findViewById(R.id.metrics_grid);
        droneMetricsView = view.findViewById(R.id.drone_stats_grid);
        sdrMetricsView = view.findViewById(R.id.sdr_stats_grid);

        dronesList = view.findViewById(R.id.recent_drones_list);
        dronesList.setLayoutManager(new LinearLayoutManager(requireContext()));

        warningsList = view.findViewById(R.id.warnings_list);
        warningsList.setLayoutManager(new LinearLayoutManager(requireContext()));
    }

    private void setupObservers() {
        // Observe status messages for system metrics
        statusViewModel.getStatusMessages().observe(getViewLifecycleOwner(), messages -> {
            if (!messages.isEmpty()) {
                updateSystemMetrics(messages.get(messages.size() - 1));
            }
        });

        // Observe drone messages
        cotViewModel.getParsedMessages().observe(getViewLifecycleOwner(), messages -> {
            updateDroneMetrics(messages);
        });
    }

    private void updateSystemMetrics(StatusMessage status) {
        // Update system metrics UI using status.getSystemStats()
    }

    private void updateDroneMetrics(List<CoTMessage> drones) {
        // Update drone metrics UI using drone messages
    }
}