package com.rootdown.dragonsync.ui.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.StatusMessage;
import com.rootdown.dragonsync.ui.adapters.DroneListAdapter;
import com.rootdown.dragonsync.ui.views.CircularGaugeView;
import com.rootdown.dragonsync.utils.Constants;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;
import com.rootdown.dragonsync.viewmodels.StatusViewModel;

import java.util.ArrayList;
import java.util.List;

public class DashboardFragment extends Fragment {
    private CoTViewModel cotViewModel;
    private StatusViewModel statusViewModel;

    // View references
    private GridLayout systemMetricsGrid;
    private GridLayout droneStatsGrid;
    private GridLayout sdrStatsGrid;
    private RecyclerView recentDronesList;
    private RecyclerView warningsList;

    private TextView trackedDronesText;
    private TextView spoofedDronesText;
    private TextView nearbyDronesText;

    private DroneListAdapter droneListAdapter;

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
        systemMetricsGrid = view.findViewById(R.id.metrics_grid);
        droneStatsGrid = view.findViewById(R.id.drone_stats_grid);
        sdrStatsGrid = view.findViewById(R.id.sdr_stats_grid);

        recentDronesList = view.findViewById(R.id.recent_drones_list);
        recentDronesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        droneListAdapter = new DroneListAdapter(new DroneListAdapter.OnDroneClickListener() {
            @Override
            public void onDroneClick(CoTMessage message) {
                // Open drone detail view
                showDroneDetail(message);
            }

            @Override
            public void onLiveMapClick(CoTMessage message) {
                // Open live map view focused on this drone
                showLiveMap(message);
            }
        });
        recentDronesList.setAdapter(droneListAdapter);

        warningsList = view.findViewById(R.id.warnings_list);
        warningsList.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Initialize metrics with empty values
        createSystemMetricsGauges();
        createDroneStatsCards();
        createSDRStatsGauges();
    }

    private void createSystemMetricsGauges() {
        systemMetricsGrid.removeAllViews();

        // Center content
        systemMetricsGrid.setUseDefaultMargins(true);
        systemMetricsGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        // CPU usage gauge
        CircularGaugeView cpuGauge = createGauge(requireContext(), "CPU", 0, "%", Color.GREEN);
        GridLayout.LayoutParams cpuParams = new GridLayout.LayoutParams();
        cpuParams.setGravity(Gravity.CENTER);
        systemMetricsGrid.addView(cpuGauge, cpuParams);

        // Memory usage gauge
        CircularGaugeView memGauge = createGauge(requireContext(), "MEM", 0, "%", Color.GREEN);
        GridLayout.LayoutParams memParams = new GridLayout.LayoutParams();
        memParams.setGravity(Gravity.CENTER);
        systemMetricsGrid.addView(memGauge, memParams);

        // Temperature gauge
        CircularGaugeView tempGauge = createGauge(requireContext(), "TEMP", 0, "°C", Color.GREEN);
        GridLayout.LayoutParams tempParams = new GridLayout.LayoutParams();
        tempParams.setGravity(Gravity.CENTER);
        systemMetricsGrid.addView(tempGauge, tempParams);
    }

    private void createDroneStatsCards() {
        droneStatsGrid.removeAllViews();

        // Create tracker counter card
        View trackedCard = LayoutInflater.from(requireContext()).inflate(R.layout.card_counter, droneStatsGrid, false);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),  // columnSpec
                GridLayout.spec(GridLayout.UNDEFINED, 1f)   // rowSpec
        );
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.setMargins(16, 16, 16, 16);

        TextView trackedTitle = trackedCard.findViewById(R.id.counter_title);
        trackedTitle.setText("TRACKED");
        trackedTitle.setTextColor(getResources().getColor(R.color.teal_200, null));

        trackedDronesText = trackedCard.findViewById(R.id.counter_value);
        trackedDronesText.setTextColor(getResources().getColor(R.color.teal_200, null));

        droneStatsGrid.addView(trackedCard, params);

        // Create spoofed counter card
        View spoofedCard = LayoutInflater.from(requireContext()).inflate(R.layout.card_counter, droneStatsGrid, false);

        GridLayout.LayoutParams spoofedParams = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),  // columnSpec
                GridLayout.spec(GridLayout.UNDEFINED, 1f)   // rowSpec
        );
        spoofedParams.width = 0;
        spoofedParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        spoofedParams.setMargins(16, 16, 16, 16);

        TextView spoofedTitle = spoofedCard.findViewById(R.id.counter_title);
        spoofedTitle.setText("SPOOFED");
        spoofedTitle.setTextColor(getResources().getColor(R.color.orange, null));

        spoofedDronesText = spoofedCard.findViewById(R.id.counter_value);
        spoofedDronesText.setTextColor(getResources().getColor(R.color.orange, null));

        droneStatsGrid.addView(spoofedCard, spoofedParams);

        // Create nearby counter card
        View nearbyCard = LayoutInflater.from(requireContext()).inflate(R.layout.card_counter, droneStatsGrid, false);

        GridLayout.LayoutParams nearbyParams = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),  // columnSpec
                GridLayout.spec(GridLayout.UNDEFINED, 1f)   // rowSpec
        );
        nearbyParams.width = 0;
        nearbyParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        nearbyParams.setMargins(16, 16, 16, 16);

        TextView nearbyTitle = nearbyCard.findViewById(R.id.counter_title);
        nearbyTitle.setText("NEARBY");
        nearbyTitle.setTextColor(Color.YELLOW);

        nearbyDronesText = nearbyCard.findViewById(R.id.counter_value);
        nearbyDronesText.setTextColor(Color.YELLOW);

        droneStatsGrid.addView(nearbyCard, nearbyParams);
    }

    private void createSDRStatsGauges() {
        // Clear existing views
        sdrStatsGrid.removeAllViews();

        // PLUTO temperature gauge
        CircularGaugeView plutoGauge = createGauge(requireContext(), "PLUTO", 0, "°C", Color.GREEN);
        sdrStatsGrid.addView(plutoGauge);

        // ZYNQ temperature gauge
        CircularGaugeView zynqGauge = createGauge(requireContext(), "ZYNQ", 0, "°C", Color.GREEN);
        sdrStatsGrid.addView(zynqGauge);

        // SDR status text
        TextView statusText = new TextView(requireContext());
        statusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        sdrStatsGrid.addView(statusText);
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

            // Update the drone list
            List<CoTMessage> recentMessages = getRecentMessages(messages, 5);
            droneListAdapter.updateMessages(recentMessages);
        });
    }

    private void updateSystemMetrics(StatusMessage status) {
        if (status == null || status.getSystemStats() == null) return;

        // Update CPU gauge
        updateGauge(systemMetricsGrid, 0, status.getSystemStats().getCpuUsage(), getColorForValue(
                status.getSystemStats().getCpuUsage(),
                Constants.DEFAULT_CPU_WARNING_THRESHOLD * 0.8,
                Constants.DEFAULT_CPU_WARNING_THRESHOLD
        ));

        // Update Memory gauge
        if (status.getSystemStats().getMemory() != null) {
            double memPercent = ((double)status.getSystemStats().getMemory().getUsed() / status.getSystemStats().getMemory().getTotal()) * 100.0;
            updateGauge(systemMetricsGrid, 1, memPercent, getColorForValue(
                    memPercent,
                    Constants.DEFAULT_MEMORY_WARNING_THRESHOLD * 0.8 * 100,
                    Constants.DEFAULT_MEMORY_WARNING_THRESHOLD * 100
            ));
        }

        // Update Temperature gauge
        updateGauge(systemMetricsGrid, 2, status.getSystemStats().getTemperature(), getColorForValue(
                status.getSystemStats().getTemperature(),
                Constants.DEFAULT_TEMP_WARNING_THRESHOLD * 0.8,
                Constants.DEFAULT_TEMP_WARNING_THRESHOLD
        ));

        if (status.getAntStats() != null) {
            updateGauge(sdrStatsGrid, 0, status.getAntStats().getPlutoTemp(), getColorForValue(
                    status.getAntStats().getPlutoTemp(),
                    Constants.DEFAULT_PLUTO_TEMP_THRESHOLD * 0.8,
                    Constants.DEFAULT_PLUTO_TEMP_THRESHOLD
            ));

            updateGauge(sdrStatsGrid, 1, status.getAntStats().getZynqTemp(), getColorForValue(
                    status.getAntStats().getZynqTemp(),
                    Constants.DEFAULT_ZYNQ_TEMP_THRESHOLD * 0.8,
                    Constants.DEFAULT_ZYNQ_TEMP_THRESHOLD
            ));

            // Update status text (third child in the grid)
            if (sdrStatsGrid.getChildCount() > 2) {
                TextView statusText = (TextView) sdrStatsGrid.getChildAt(2);
                statusText.setText("STATUS\nACTIVE");
                statusText.setTextColor(Color.GREEN);
            }
        } else {
            // Set inactive status when no ANT stats available
            if (sdrStatsGrid.getChildCount() > 2) {
                TextView statusText = (TextView) sdrStatsGrid.getChildAt(2);
                statusText.setText("STATUS\nINACTIVE");
                statusText.setTextColor(Color.RED);
            }
        }
    }

    private void updateDroneMetrics(List<CoTMessage> drones) {
        if (drones == null) return;

        int totalDrones = drones.size();
        int spoofedDrones = 0;
        int nearbyDrones = 0;

        for (CoTMessage drone : drones) {
            if (drone.isSpoofed()) {
                spoofedDrones++;
            }

            // Check if drone is nearby based on RSSI
            if (drone.getRssi() != null && drone.getRssi() > Constants.DEFAULT_PROXIMITY_THRESHOLD) {
                nearbyDrones++;
            }
        }

        // Update counter cards
        trackedDronesText.setText(String.valueOf(totalDrones));
        spoofedDronesText.setText(String.valueOf(spoofedDrones));
        nearbyDronesText.setText(String.valueOf(nearbyDrones));
    }

    private List<CoTMessage> getRecentMessages(List<CoTMessage> messages, int count) {
        List<CoTMessage> result = new ArrayList<>();
        int size = messages.size();
        int startIndex = Math.max(0, size - count);

        for (int i = startIndex; i < size; i++) {
            result.add(messages.get(i));
        }

        return result;
    }

    private void showDroneDetail(CoTMessage message) {
        // Navigate to drone detail fragment
        DroneDetailFragment detailFragment = DroneDetailFragment.newInstance(message);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit();
    }

    private void showLiveMap(CoTMessage message) {
        // Navigate to live map fragment focused on this drone
        LiveMapFragment mapFragment = LiveMapFragment.newInstance(message);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, mapFragment)
                .addToBackStack(null)
                .commit();
    }

    // Utility methods for creating UI components
    private CircularGaugeView createGauge(Context context, String title, double value, String unit, int color) {
        CircularGaugeView gauge = new CircularGaugeView(context);
        gauge.setTitle(title);
        gauge.setValue(value);
        gauge.setUnit(unit);
        gauge.setColor(getResources().getColor(R.color.status_green, null));
        return gauge;
    }

    private View createCounterCard(Context context, String title, int count, int color) {
        View card = LayoutInflater.from(context).inflate(R.layout.card_counter, null);
        TextView titleText = card.findViewById(R.id.counter_title);
        TextView valueText = card.findViewById(R.id.counter_value);

        titleText.setText(title);
        valueText.setText(String.valueOf(count));
        valueText.setTextColor(color);

        return card;
    }

    private void updateGauge(GridLayout grid, int index, double value, int color) {
        if (grid.getChildCount() <= index) return;

        View child = grid.getChildAt(index);
        if (child instanceof CircularGaugeView) {
            CircularGaugeView gauge = (CircularGaugeView) child;
            gauge.setValue(value);
            gauge.setColor(color);
        }
    }

    private int getColorForValue(double value, double warningThreshold, double criticalThreshold) {
        if (value >= criticalThreshold) {
            return Color.RED;
        } else if (value >= warningThreshold) {
            return Color.YELLOW;
        } else {
            return getResources().getColor(R.color.status_green, null);
        }
    }
}