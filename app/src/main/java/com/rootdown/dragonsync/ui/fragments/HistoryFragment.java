// java/com.rootdown.dragonsync/ui/fragments/HistoryFragment.java
package com.rootdown.dragonsync.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.ui.adapters.HistoryAdapter;
import com.rootdown.dragonsync.utils.DroneStorage;
import com.rootdown.dragonsync.utils.DroneStorage.DroneEncounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class HistoryFragment extends Fragment {
    private RecyclerView historyList;
    private ExtendedFloatingActionButton exportButton;
    private DroneStorage droneStorage;
    private HistoryAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        droneStorage = DroneStorage.getInstance(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        historyList = view.findViewById(R.id.history_list);
        exportButton = view.findViewById(R.id.export_button);

        setupRecyclerView();
        setupExportButton();

        return view;
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter(encounter -> {
            // Show encounter detail
            showEncounterDetail(encounter);
        });

        historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyList.setAdapter(adapter);

        updateEncountersList();
    }

    private void setupExportButton() {
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
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.history_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });

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
                    adapter.sort(comparator);
                })
                .show();
    }

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete All Encounters")
                .setMessage("Are you sure you want to delete all encounters? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    droneStorage.deleteAllEncounters();
                    updateEncountersList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateEncountersList() {
        // Get encounters from storage and update adapter
        adapter.updateEncounters(new ArrayList<>(droneStorage.getEncounters().values()));
    }

    private void showEncounterDetail(DroneEncounter encounter) {
        // TODO: Implement encounter detail view
    }

    private void exportCSV() {
        String csv = droneStorage.exportToCSV();
        // TODO: Implement file sharing
    }

    private void exportKML() {
        // TODO: Implement KML export
    }

//    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
//        // Implementation details omitted for brevity
//    }
}