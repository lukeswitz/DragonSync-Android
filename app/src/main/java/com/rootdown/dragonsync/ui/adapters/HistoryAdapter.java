package com.rootdown.dragonsync.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.utils.DroneStorage.DroneEncounter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private List<DroneEncounter> encounters = new ArrayList<>();
    private final OnEncounterClickListener listener;

    public interface OnEncounterClickListener {
        void onEncounterClick(DroneEncounter encounter);
    }

    public HistoryAdapter(OnEncounterClickListener listener) {
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView droneId;
        TextView timestamp;
        TextView details;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            droneId = itemView.findViewById(R.id.drone_id);
            timestamp = itemView.findViewById(R.id.timestamp);
            details = itemView.findViewById(R.id.details);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_encounter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DroneEncounter encounter = encounters.get(position);
        holder.droneId.setText(String.valueOf(encounter.getId()));
        holder.timestamp.setText(formatTimestamp(encounter.getLastSeen()));
        holder.details.setText(formatDetails(encounter));
        holder.itemView.setOnClickListener(v -> listener.onEncounterClick(encounter));
    }

    @Override
    public int getItemCount() {
        return encounters.size();
    }

    public void updateEncounters(List<DroneEncounter> newEncounters) {
        encounters = new ArrayList<>(newEncounters);
        notifyDataSetChanged();
    }

    public void filter(String text) {
        if (text.isEmpty()) {
            encounters = new ArrayList<>(encounters);
        } else {
            encounters = encounters.stream()
                    .filter(e -> e.getId().contains(text.toLowerCase()))
                    .collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }

    public void sort(Comparator<DroneEncounter> comparator) {
        encounters.sort(comparator);
        notifyDataSetChanged();
    }

    private String formatTimestamp(Date date) {
        return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.getDefault()).format(date);
    }

    private String formatDetails(DroneEncounter encounter) {
        return String.format("Alt: %.1fm, Speed: %.1fm/s",
                encounter.getMaxAltitude(),
                encounter.getMaxSpeed());
    }
}