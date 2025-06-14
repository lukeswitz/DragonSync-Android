package com.rootdown.dragonsync.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.ui.adapters.DroneListAdapter;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;
import com.rootdown.dragonsync.models.CoTMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DroneListFragment extends Fragment implements DroneListAdapter.OnDroneClickListener {
    private static final String TAG = "DroneListFragment";
    private CoTViewModel viewModel;
    private DroneListAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateView;
    private TextView droneCountView;
    private ChipGroup filterChips;
    private MaterialButton clearButton;
    private MaterialButton mapAllButton;

    private List<CoTMessage> allDrones = new ArrayList<>();
    private List<CoTMessage> filteredDrones = new ArrayList<>();
    private String currentFilter = "all";
    private String searchQuery = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        viewModel = new ViewModelProvider(requireActivity()).get(CoTViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drone_list, container, false);

        // Apply status bar insets
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.getSystemWindowInsetTop() + 26,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets.consumeSystemWindowInsets();
        });

        setupRecyclerView(view);
        setupFilterChips(view);
        setupButtons(view);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.drone_list_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                applyFilters();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                applyFilters();
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort_rssi) {
            sortDronesByRSSI();
            return true;
        } else if (id == R.id.action_sort_recent) {
            sortDronesByRecent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.drones_list);
        emptyStateView = view.findViewById(R.id.empty_state);
        droneCountView = view.findViewById(R.id.drone_count);

        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView with ID drone_list not found in layout");
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DroneListAdapter(requireContext(), this);
        recyclerView.setAdapter(adapter);

        // Observe view model data
        viewModel.getParsedMessages().observe(getViewLifecycleOwner(), messages -> {
            allDrones = new ArrayList<>(messages);
            applyFilters();
            updateDroneCount();
        });
    }

    private void setupFilterChips(View view) {
        filterChips = view.findViewById(R.id.filter_chips);
        if (filterChips == null) return;

        // Hide chips by default
        filterChips.setVisibility(View.GONE);

        // Set up click listeners for filter chips
        for (int i = 0; i < filterChips.getChildCount(); i++) {
            View child = filterChips.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                chip.setOnClickListener(v -> {
                    currentFilter = chip.getTag().toString();
                    applyFilters();

                    // Update chip selection state
                    for (int j = 0; j < filterChips.getChildCount(); j++) {
                        View otherChild = filterChips.getChildAt(j);
                        if (otherChild instanceof Chip) {
                            ((Chip) otherChild).setChecked(otherChild == chip);
                        }
                    }
                });
            }
        }
    }

    private void setupButtons(View view) {
        clearButton = view.findViewById(R.id.clear_button);
        mapAllButton = view.findViewById(R.id.map_all_button);
        MaterialButton filterButton = view.findViewById(R.id.filter_button);

        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                viewModel.clearMessages();
            });
        }

        if (mapAllButton != null) {
            mapAllButton.setOnClickListener(v -> {
                // Navigate to live map showing all drones
                LiveMapFragment mapFragment = LiveMapFragment.newInstance(null);
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, mapFragment)
                        .addToBackStack(null)
                        .commit();
            });
        }

        if (filterButton != null) {
            filterButton.setOnClickListener(v -> {
                // Toggle chip group visibility
                if (filterChips.getVisibility() == View.VISIBLE) {
                    filterChips.setVisibility(View.GONE);
                } else {
                    filterChips.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void applyFilters() {
        if (allDrones.isEmpty()) {
            filteredDrones = new ArrayList<>();
            updateUI();
            return;
        }

        // Start with all drones
        filteredDrones = new ArrayList<>(allDrones);

        // Apply type filter
        switch (currentFilter) {
            case "bluetooth":
                filteredDrones = filteredDrones.stream()
                        .filter(drone -> drone.getType() != null && drone.getType().contains("BLE"))
                        .collect(Collectors.toList());
                break;
            case "wifi":
                filteredDrones = filteredDrones.stream()
                        .filter(drone -> drone.getType() != null && drone.getType().contains("WiFi"))
                        .collect(Collectors.toList());
                break;
            case "sdr":
                filteredDrones = filteredDrones.stream()
                        .filter(drone -> drone.getMac() == null) // SDR typically has no MAC
                        .collect(Collectors.toList());
                break;
            case "spoofed":
                filteredDrones = filteredDrones.stream()
                        .filter(CoTMessage::isSpoofed)
                        .collect(Collectors.toList());
                break;
        }

        // Apply search filter if needed
        if (!searchQuery.isEmpty()) {
            String query = searchQuery.toLowerCase();
            filteredDrones = filteredDrones.stream()
                    .filter(drone ->
                            (drone.getUid() != null && drone.getUid().toLowerCase().contains(query)) ||
                                    (drone.getDescription() != null && drone.getDescription().toLowerCase().contains(query)) ||
                                    (drone.getMac() != null && drone.getMac().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        updateUI();
    }

    private void updateUI() {
        adapter.updateMessages(filteredDrones);

        // Show empty state if needed
        if (emptyStateView != null) {
            emptyStateView.setVisibility(filteredDrones.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(filteredDrones.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void updateDroneCount() {
        if (droneCountView == null) return;

        int total = allDrones.size();
        int filtered = filteredDrones.size();

        if (total == filtered) {
            droneCountView.setText(getString(R.string.drone_count_total, total));
        } else {
            droneCountView.setText(getString(R.string.drone_count_filtered, filtered, total));
        }
    }

    private void sortDronesByRSSI() {
        filteredDrones.sort((a, b) -> {
            Integer rssiA = a.getRssi();
            Integer rssiB = b.getRssi();

            if (rssiA == null && rssiB == null) return 0;
            if (rssiA == null) return 1;
            if (rssiB == null) return -1;

            // Strongest signal first (least negative)
            return rssiB.compareTo(rssiA);
        });

        adapter.updateMessages(filteredDrones);
    }

    private void sortDronesByRecent() {
        filteredDrones.sort((a, b) -> {
            String timestampA = a.getTimestamp();
            String timestampB = b.getTimestamp();

            if (timestampA == null && timestampB == null) return 0;
            if (timestampA == null) return 1;
            if (timestampB == null) return -1;

            try {
                // Most recent first
                long timeA = Long.parseLong(timestampA);
                long timeB = Long.parseLong(timestampB);
                return Long.compare(timeB, timeA);
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        adapter.updateMessages(filteredDrones);
    }

    @Override
    public void onDroneClick(CoTMessage message) {
        // Show drone details fragment
        DroneDetailFragment detailFragment = DroneDetailFragment.newInstance(message);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onLiveMapClick(CoTMessage message) {
        // Show live map focused on this drone
        LiveMapFragment mapFragment = LiveMapFragment.newInstance(message);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, mapFragment)
                .addToBackStack(null)
                .commit();
    }
}