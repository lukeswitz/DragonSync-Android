package com.rootdown.dragonsync.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.ui.adapters.DroneListAdapter;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;

public class DroneListBottomSheet extends BottomSheetDialogFragment implements DroneListAdapter.OnDroneClickListener {
    private CoTViewModel viewModel;
    private DroneListAdapter adapter;

    public static DroneListBottomSheet newInstance() {
        return new DroneListBottomSheet();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(CoTViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drone_list_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.drone_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DroneListAdapter(this);
        recyclerView.setAdapter(adapter);

        viewModel.getParsedMessages().observe(getViewLifecycleOwner(),
                messages -> adapter.updateMessages(messages));
    }

    @Override
    public void onDroneClick(CoTMessage message) {
        // Handle drone selection
        dismiss();
    }

    @Override
    public void onLiveMapClick(CoTMessage message) {
        // Already on live map, no need to handle
    }
}