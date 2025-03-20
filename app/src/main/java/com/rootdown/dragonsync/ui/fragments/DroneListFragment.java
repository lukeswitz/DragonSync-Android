package com.rootdown.dragonsync.ui.fragments;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.ui.adapters.DroneListAdapter;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;
import com.rootdown.dragonsync.models.CoTMessage;

import java.util.List;

public class DroneListFragment extends Fragment implements DroneListAdapter.OnDroneClickListener {
    private CoTViewModel viewModel;
    private DroneListAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(CoTViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drone_list, container, false);

        // Find views with null checking
        recyclerView = view.findViewById(R.id.drone_list);
        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView with ID drone_list not found in layout");
            return view;
        }

        // Set up recyclerview
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Initialize adapter with proper context
        adapter = new DroneListAdapter(requireContext(), this);
        recyclerView.setAdapter(adapter);

        // Set up empty state view
        emptyStateView = view.findViewById(R.id.empty_state);

        // Observe viewmodel data
        viewModel.getParsedMessages().observe(getViewLifecycleOwner(), this::updateDroneList);

        return view;
    }

    private void updateDroneList(List<CoTMessage> messages) {
        if (adapter == null) return;

        adapter.updateMessages(messages);

        // Show/hide empty state
        if (emptyStateView != null) {
            emptyStateView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setupRecyclerView(View view) {
        recyclerView = view.findViewById(R.id.drone_list);
        if (recyclerView == null) {
            Log.e("DroneListFragment", "RecyclerView with ID drone_list not found in layout");
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DroneListAdapter(this);
        recyclerView.setAdapter(adapter);

        // Observe view model data
        viewModel.getParsedMessages().observe(getViewLifecycleOwner(),
                messages -> adapter.updateMessages(messages));
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