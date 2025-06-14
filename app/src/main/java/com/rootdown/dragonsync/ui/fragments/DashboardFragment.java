package com.rootdown.dragonsync.ui.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
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

    private GridLayout systemMetricsGrid;
    private GridLayout droneStatsGrid;
    private GridLayout sdrStatsGrid;
    private RecyclerView recentDronesList;
    private RecyclerView warningsList;

    private TextView trackedDronesText;
    private TextView spoofedDronesText;
    private TextView nearbyDronesText;

    private DroneListAdapter droneListAdapter;
    com.google.android.material.chip.Chip sdrStatusChip;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cotViewModel = new ViewModelProvider(requireActivity()).get(CoTViewModel.class);
        statusViewModel = new ViewModelProvider(requireActivity()).get(StatusViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // Apply status bar insets with more padding
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.getSystemWindowInsetTop() + 60, // top margin
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets.consumeSystemWindowInsets();
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupObservers();

        MaterialButton viewAllButton = view.findViewById(R.id.view_all_drones);
        if (viewAllButton != null) {
            viewAllButton.setOnClickListener(v -> {
                if (getActivity() != null) {
                    BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
                    if (bottomNav != null) {
                        bottomNav.setSelectedItemId(R.id.nav_drones);
                    }
                }
            });
        }
    }

    private void initializeViews(View view) {
        systemMetricsGrid = view.findViewById(R.id.metrics_grid);
        droneStatsGrid = view.findViewById(R.id.drone_stats_grid);
        sdrStatsGrid = view.findViewById(R.id.sdr_stats_grid);
        sdrStatusChip = view.findViewById(R.id.sdr_status_chip);
        recentDronesList = view.findViewById(R.id.recent_drones_list);
        recentDronesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        droneListAdapter = new DroneListAdapter(requireContext(), new DroneListAdapter.OnDroneClickListener() {
            @Override
            public void onDroneClick(CoTMessage message) {
                showDroneDetail(message);
            }

            @Override
            public void onLiveMapClick(CoTMessage message) {
                showLiveMap(message);
            }
        });
        recentDronesList.setAdapter(droneListAdapter);

        warningsList = view.findViewById(R.id.warnings_list);
        warningsList.setLayoutManager(new LinearLayoutManager(requireContext()));

        createSystemMetricsGauges();
        createDroneStatsCards();
        createSDRStatsGauges();
    }

    private void createSystemMetricsGauges() {
        systemMetricsGrid.removeAllViews();
        systemMetricsGrid.setUseDefaultMargins(true);
        systemMetricsGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        CircularGaugeView cpuGauge = createGauge(requireContext(), "CPU", 0, "%", Color.GREEN);
        GridLayout.LayoutParams cpuParams = new GridLayout.LayoutParams();
        cpuParams.setGravity(Gravity.CENTER);
        systemMetricsGrid.addView(cpuGauge, cpuParams);

        CircularGaugeView memGauge = createGauge(requireContext(), "MEM", 0, "%", Color.GREEN);
        GridLayout.LayoutParams memParams = new GridLayout.LayoutParams();
        memParams.setGravity(Gravity.CENTER);
        systemMetricsGrid.addView(memGauge, memParams);

        CircularGaugeView tempGauge = createGauge(requireContext(), "TEMP", 0, "°C", Color.GREEN);
        GridLayout.LayoutParams tempParams = new GridLayout.LayoutParams();
        tempParams.setGravity(Gravity.CENTER);
        systemMetricsGrid.addView(tempGauge, tempParams);
    }

    private void createDroneStatsCards() {
        droneStatsGrid.removeAllViews();

        View trackedCard = LayoutInflater.from(requireContext()).inflate(R.layout.card_counter, droneStatsGrid, false);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
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

        View spoofedCard = LayoutInflater.from(requireContext()).inflate(R.layout.card_counter, droneStatsGrid, false);
        GridLayout.LayoutParams spoofedParams = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
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

        View nearbyCard = LayoutInflater.from(requireContext()).inflate(R.layout.card_counter, droneStatsGrid, false);
        GridLayout.LayoutParams nearbyParams = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
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
        sdrStatsGrid.removeAllViews();

        CircularGaugeView plutoGauge = createGauge(requireContext(), "PLUTO", 0, "°C", Color.GREEN);
        GridLayout.LayoutParams plutoParams = new GridLayout.LayoutParams();
        plutoParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER);
        plutoParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER);
        plutoParams.setGravity(Gravity.CENTER);
        sdrStatsGrid.addView(plutoGauge, plutoParams);

        CircularGaugeView zynqGauge = createGauge(requireContext(), "ZYNQ", 0, "°C", Color.GREEN);
        GridLayout.LayoutParams zynqParams = new GridLayout.LayoutParams();
        zynqParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER);
        zynqParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER);
        zynqParams.setGravity(Gravity.CENTER);
        sdrStatsGrid.addView(zynqGauge, zynqParams);

        TextView statusText = new TextView(requireContext());
        statusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        GridLayout.LayoutParams statusParams = new GridLayout.LayoutParams();
        statusParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER);
        statusParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER);
        statusParams.setGravity(Gravity.CENTER);
        sdrStatsGrid.addView(statusText, statusParams);
    }

    private void setupObservers() {
        statusViewModel.getStatusMessages().observe(getViewLifecycleOwner(), messages -> {
            if (!messages.isEmpty()) {
                updateSystemMetrics(messages.get(messages.size() - 1));
            }
        });

        cotViewModel.getParsedMessages().observe(getViewLifecycleOwner(), messages -> {
            updateDroneMetrics(messages);

            List<CoTMessage> recentMessages = getRecentMessages(messages, 1);
            droneListAdapter.updateMessages(recentMessages);

            TextView emptyStateText = getView().findViewById(R.id.no_drones_message);
            if (emptyStateText != null) {
                emptyStateText.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
            }

            if (recentDronesList != null) {
                recentDronesList.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void updateSystemMetrics(StatusMessage status) {
        if (status == null || status.getSystemStats() == null) return;

        updateGauge(systemMetricsGrid, 0, status.getSystemStats().getCpuUsage(), getColorForMetric(
                status.getSystemStats().getCpuUsage(),
                Constants.CPU_WARNING_THRESHOLD,
                Constants.CPU_CRITICAL_THRESHOLD
        ));

        if (status.getSystemStats().getMemory() != null) {
            double memPercent = ((double)status.getSystemStats().getMemory().getUsed() /
                    status.getSystemStats().getMemory().getTotal()) * 100.0;
            updateGauge(systemMetricsGrid, 1, memPercent, getColorForMetric(
                    memPercent,
                    Constants.MEMORY_WARNING_THRESHOLD * 100,
                    Constants.MEMORY_CRITICAL_THRESHOLD * 100
            ));
        }

        updateGauge(systemMetricsGrid, 2, status.getSystemStats().getTemperature(), getColorForMetric(
                status.getSystemStats().getTemperature(),
                Constants.TEMP_WARNING_THRESHOLD,
                Constants.TEMP_CRITICAL_THRESHOLD
        ));

        if (status.getAntStats() != null) {
            updateGauge(sdrStatsGrid, 0, status.getAntStats().getPlutoTemp(), getColorForMetric(
                    status.getAntStats().getPlutoTemp(),
                    Constants.PLUTO_TEMP_WARNING_THRESHOLD,
                    Constants.PLUTO_TEMP_CRITICAL_THRESHOLD
            ));

            updateGauge(sdrStatsGrid, 1, status.getAntStats().getZynqTemp(), getColorForMetric(
                    status.getAntStats().getZynqTemp(),
                    Constants.ZYNQ_TEMP_WARNING_THRESHOLD,
                    Constants.ZYNQ_TEMP_CRITICAL_THRESHOLD
            ));

        }

        if (status.getAntStats() != null) {
            sdrStatusChip.setText("ACTIVE");
            sdrStatusChip.setChipBackgroundColor(ColorStateList.valueOf(getResources().getColor(R.color.status_green_10, null)));
            sdrStatusChip.setChipStrokeColor(ColorStateList.valueOf(getResources().getColor(R.color.status_green, null)));
            sdrStatusChip.setTextColor(getResources().getColor(R.color.status_green, null));
        } else {
            sdrStatusChip.setText("INACTIVE");
            sdrStatusChip.setChipBackgroundColor(ColorStateList.valueOf(getResources().getColor(R.color.status_red_10, null)));
            sdrStatusChip.setChipStrokeColor(ColorStateList.valueOf(getResources().getColor(R.color.status_red, null)));
            sdrStatusChip.setTextColor(getResources().getColor(R.color.status_red, null));
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

            if (drone.getRssi() != null && drone.getRssi() > Constants.PROXIMITY_THRESHOLD) {
                nearbyDrones++;
            }
        }

        trackedDronesText.setText(getString(R.string.drone_count_tracked, totalDrones));
        spoofedDronesText.setText(getString(R.string.drone_count_spoofed, spoofedDrones));
        nearbyDronesText.setText(getString(R.string.drone_count_nearby, nearbyDrones));
    }

    private int getColorForMetric(double value, double warningThreshold, double criticalThreshold) {
        if (value >= criticalThreshold) {
            return requireContext().getColor(R.color.status_red);
        } else if (value >= warningThreshold) {
            return requireContext().getColor(R.color.status_yellow);
        } else {
            return requireContext().getColor(R.color.status_green);
        }
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
        DroneDetailFragment detailFragment = DroneDetailFragment.newInstance(message);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit();
    }

    private void showLiveMap(CoTMessage message) {
        LiveMapFragment mapFragment = LiveMapFragment.newInstance(message);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, mapFragment)
                .addToBackStack(null)
                .commit();
    }

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