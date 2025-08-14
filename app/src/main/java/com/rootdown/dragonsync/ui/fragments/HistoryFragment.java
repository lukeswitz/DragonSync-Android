package com.rootdown.dragonsync.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.network.RebelHistoryManager;
import com.rootdown.dragonsync.ui.adapters.HistoryAdapter;
import com.rootdown.dragonsync.utils.DroneStorage;
import com.rootdown.dragonsync.utils.DroneStorage.DroneEncounter;
import com.rootdown.dragonsync.viewmodels.HistoryViewModel;
import com.rootdown.dragonsync.viewmodels.HistoryViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {
    private RecyclerView heartbeatsList;
    private RecyclerView networksTable;
    private MaterialButton exportButton;
    private MaterialButton clearButton;
    private TextView welcomeMessage;
    private TextView uniqueNetworksCount;
    private TextView totalEntriesCount;
    private TextView noHeartbeatsMessage;

    private DroneStorage droneStorage;
    private RebelHistoryManager RebelHistoryManager;
    private HistoryAdapter heartbeatsAdapter;
    private HistoryAdapter networksAdapter;
    private HistoryViewModel historyVM;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        droneStorage = DroneStorage.getInstance(requireContext());
        RebelHistoryManager = new RebelHistoryManager(requireContext());

        historyVM = new ViewModelProvider(
                requireActivity(),
                new HistoryViewModelFactory(droneStorage, RebelHistoryManager)
        ).get(HistoryViewModel.class);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);
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

        // Initialize views
        heartbeatsList = view.findViewById(R.id.heartbeats_list);
        networksTable = view.findViewById(R.id.networks_table);
        exportButton = view.findViewById(R.id.export_seen);
        clearButton = view.findViewById(R.id.clear_database_button);
        welcomeMessage = view.findViewById(R.id.welcome_message);
        uniqueNetworksCount = view.findViewById(R.id.unique_networks_count);
        totalEntriesCount = view.findViewById(R.id.total_entries_count);
        noHeartbeatsMessage = view.findViewById(R.id.no_heartbeats_message);

        setupRecyclerViews();
        setupActionButtons();
        setupObservers();
        updateAllData(); // legacy fallback TODO

        return view;
    }

    private void setupRecyclerViews() {
        // Setup heartbeats list
        heartbeatsAdapter = new HistoryAdapter(new HistoryAdapter.OnEncounterClickListener() {
            @Override
            public void onEncounterClick(DroneEncounter encounter) {
                showEncounterDetail(encounter);
            }

            @Override
            public void onRebelClick(RebelHistoryManager.DroneHistoryEntry RebelEntry) {
                showRebelDetail(RebelEntry);
            }
        });

        heartbeatsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        heartbeatsList.setAdapter(heartbeatsAdapter);
        heartbeatsList.setNestedScrollingEnabled(false);

        // Setup networks table
        networksAdapter = new HistoryAdapter(new HistoryAdapter.OnEncounterClickListener() {
            @Override
            public void onEncounterClick(DroneEncounter encounter) {
                showEncounterDetail(encounter);
            }

            @Override
            public void onRebelClick(RebelHistoryManager.DroneHistoryEntry RebelEntry) {
                showRebelDetail(RebelEntry);
            }
        });

        networksTable.setLayoutManager(new LinearLayoutManager(requireContext()));
        networksTable.setAdapter(networksAdapter);
    }

    private void setupObservers() {

        historyVM.getHourlyRebels().observe(getViewLifecycleOwner(), Rebels -> {
            if (Rebels == null || Rebels.isEmpty()) {
                heartbeatsList.setVisibility(View.GONE);
                noHeartbeatsMessage.setVisibility(View.VISIBLE);
            } else {
                heartbeatsList.setVisibility(View.VISIBLE);
                noHeartbeatsMessage.setVisibility(View.GONE);
                heartbeatsAdapter.updateRebelEntries(Rebels);
            }
        });

        historyVM.getEncounters().observe(getViewLifecycleOwner(), encs -> {
            // networks list needs both encounters and weekly Rebels → we’ll wait for both below
            List<RebelHistoryManager.DroneHistoryEntry> wb = historyVM.getWeeklyRebels().getValue();
            if (wb != null) updateNetworks(encs, wb);
        });

        historyVM.getWeeklyRebels().observe(getViewLifecycleOwner(), wb -> {
            List<DroneEncounter> encs = historyVM.getEncounters().getValue();
            if (encs != null) updateNetworks(encs, wb);
        });
    }

    private void updateNetworks(List<DroneEncounter> encs,
                                List<RebelHistoryManager.DroneHistoryEntry> wb) {

        networksAdapter.updateEncounters(encs);
        networksAdapter.updateRebelEntries(wb);

        // — banner counts —
        int unique   = encs.size();
        int total    = unique + wb.size();
        int Rebels  = wb.size();

        uniqueNetworksCount.setText("Unique Drones: " + unique);
        totalEntriesCount.setText("Total Detections: " + total);

        View root = getView();
        if (root != null) {
            TextView RebelCountView = root.findViewById(R.id.Rebel_count);
            if (RebelCountView != null) RebelCountView.setText("Rebel Alerts: " + Rebels);
        }
    }


    private void setupActionButtons() {
        exportButton.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Export Format")
                    .setItems(new String[]{"CSV", "KML"}, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                exportCSV();
                                break;
                            case 1:
                                exportKML();
                                break;
                        }
                    })
                    .show();
        });

        clearButton.setOnClickListener(v -> confirmDeleteAll());
    }

    private void updateAllData() {
        updateStatistics();
        updateHeartbeatsList();
        updateNetworksList();
        updateWelcomeMessage();
    }

    private void updateStatistics() {
        List<DroneEncounter> encounters = new ArrayList<>(droneStorage.getEncounters().values());

        long now = System.currentTimeMillis();
        long lastWeek = now - (7L * 24 * 60 * 60 * 1000);
        List<RebelHistoryManager.DroneHistoryEntry> RebelEntries = RebelHistoryManager.getRebelHistory(lastWeek, now);

        int uniqueNetworks = encounters.size();
        int totalEntries = encounters.size() + RebelEntries.size();
        int RebelCount = RebelEntries.size();

        uniqueNetworksCount.setText("Unique Drones: " + uniqueNetworks);
        totalEntriesCount.setText("Total Detections: " + totalEntries);

        // Find the additional views if they exist
        View rootView = getView();
        if (rootView != null) {
            TextView RebelCountView = rootView.findViewById(R.id.Rebel_count);
            TextView lastDetectionView = rootView.findViewById(R.id.last_detection);

            if (RebelCountView != null) {
                RebelCountView.setText("Rebel Alerts: " + RebelCount);
            }

            if (lastDetectionView != null) {
                String lastDetection = "Never";
                if (!encounters.isEmpty()) {
                    DroneEncounter latest = encounters.stream()
                            .max(Comparator.comparing(DroneEncounter::getLastSeen))
                            .orElse(null);
                    if (latest != null) {
                        lastDetection = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                .format(latest.getLastSeen());
                    }
                }
                lastDetectionView.setText("Last Detection: " + lastDetection);
            }
        }
    }

    private void updateHeartbeatsList() {
        long now = System.currentTimeMillis();
        long lastHour = now - (60 * 60 * 1000); // Last hour for heartbeats
        List<RebelHistoryManager.DroneHistoryEntry> recentRebelEntries = RebelHistoryManager.getRebelHistory(lastHour, now);

        if (recentRebelEntries.isEmpty()) {
            heartbeatsList.setVisibility(View.GONE);
            noHeartbeatsMessage.setVisibility(View.VISIBLE);
        } else {
            heartbeatsList.setVisibility(View.VISIBLE);
            noHeartbeatsMessage.setVisibility(View.GONE);
            heartbeatsAdapter.updateRebelEntries(recentRebelEntries);
        }
    }

    private void updateNetworksList() {
        List<DroneEncounter> encounters = new ArrayList<>(droneStorage.getEncounters().values());

        long now = System.currentTimeMillis();
        long lastWeek = now - (7L * 24 * 60 * 60 * 1000);
        List<RebelHistoryManager.DroneHistoryEntry> RebelEntries = RebelHistoryManager.getRebelHistory(lastWeek, now);

        networksAdapter.updateEncounters(encounters);
        networksAdapter.updateRebelEntries(RebelEntries);
    }

    private void updateWelcomeMessage() {
        welcomeMessage.setText("Welcome to DragonSync - Monitoring active drone networks and threats");
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.history_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (networksAdapter != null) {
                            networksAdapter.filter(newText);
                        }
                        return true;
                    }
                });
            }
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sort) {
            showSortOptions();
            return true;
        } else if (item.getItemId() == R.id.action_delete_all) {
            confirmDeleteAll();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortOptions() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sort By")
                .setItems(new String[]{
                        "Last Seen",
                        "First Seen",
                        "Max Altitude",
                        "Max Speed"
                }, (dialog, which) -> {
                    Comparator<DroneEncounter> comparator;
                    switch (which) {
                        case 0:
                            comparator = (a, b) -> b.getLastSeen().compareTo(a.getLastSeen());
                            break;
                        case 1:
                            comparator = (a, b) -> b.getFirstSeen().compareTo(a.getFirstSeen());
                            break;
                        case 2:
                            comparator = (a, b) -> Double.compare(b.getMaxAltitude(), a.getMaxAltitude());
                            break;
                        case 3:
                            comparator = (a, b) -> Double.compare(b.getMaxSpeed(), a.getMaxSpeed());
                            break;
                        default:
                            return;
                    }
                    if (networksAdapter != null) {
                        networksAdapter.sort(comparator);
                    }
                })
                .show();
    }

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Database")
                .setMessage("Are you sure you want to clear all drone encounters and Rebel detections? This action cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    droneStorage.deleteAllEncounters();
                    RebelHistoryManager.clearRebelHistory();
                    updateAllData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEncounterDetail(DroneEncounter encounter) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Drone Encounter: " + encounter.getId())
                .setMessage("First Seen: " + encounter.getFirstSeen() + "\n" +
                        "Last Seen: " + encounter.getLastSeen() + "\n" +
                        "Max Altitude: " + encounter.getMaxAltitude() + "m\n" +
                        "Max Speed: " + encounter.getMaxSpeed() + "m/s")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showRebelDetail(RebelHistoryManager.DroneHistoryEntry RebelEntry) {
        String RebelTypes = RebelEntry.getRebelDetections().isEmpty() ? "Unknown" :
                RebelEntry.getRebelDetections().get(0).getType().getDisplayName();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rebel Detection: " + RebelTypes)
                .setMessage("Device ID: " + (RebelEntry.getDroneId() != null ? RebelEntry.getDroneId() : "Unknown") + "\n" +
                        "MAC: " + (RebelEntry.getMac() != null ? RebelEntry.getMac() : "Unknown") + "\n" +
                        "Timestamp: " + new java.util.Date(RebelEntry.getTimestamp()) + "\n" +
                        "Confidence: " + RebelEntry.getMaxRebelConfidence() + "\n" +
                        "Source: " + (RebelEntry.getDetectionSource() != null ? RebelEntry.getDetectionSource() : "Unknown"))
                .setPositiveButton("OK", null)
                .show();
    }

    private void exportCSV() {
        String droneCSV = droneStorage.exportToCSV();
        long now = System.currentTimeMillis();
        long lastMonth = now - (30L * 24 * 60 * 60 * 1000);
        String RebelCSV = RebelHistoryManager.exportRebelHistoryToCSV(lastMonth, now);

        String combinedCSV = "DRONE ENCOUNTERS:\n" + droneCSV + "\n\nRebel DETECTIONS:\n" + RebelCSV;
        // TODO: Implement file saving/sharing logic
    }

    private void exportKML() {
        // TODO: Implement KML export
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAllData(); // legacy fallback
        historyVM.refreshData();
    }
}