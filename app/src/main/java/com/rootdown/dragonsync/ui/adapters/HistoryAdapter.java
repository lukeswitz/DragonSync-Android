package com.rootdown.dragonsync.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.network.RebelHistoryManager;
import com.rootdown.dragonsync.network.RebelScanner;
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
    private List<RebelHistoryManager.DroneHistoryEntry> RebelEntries = new ArrayList<>();
    private List<DroneEncounter> originalEncounters = new ArrayList<>();
    private List<RebelHistoryManager.DroneHistoryEntry> originalRebelEntries = new ArrayList<>();
    private final OnEncounterClickListener listener;

    public interface OnEncounterClickListener {
        void onEncounterClick(DroneEncounter encounter);
        void onRebelClick(RebelHistoryManager.DroneHistoryEntry RebelEntry);
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
//            details = itemView.findViewById(R.id.details);
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
        if (position < encounters.size()) {
            DroneEncounter encounter = encounters.get(position);
            holder.droneId.setText("🛸 " + encounter.getId());
            if (holder.timestamp != null) {
                holder.timestamp.setText(formatTimestamp(encounter.getLastSeen()));
            }
            if (holder.details != null) {
                holder.details.setText(formatDetails(encounter));
            }
            holder.itemView.setOnClickListener(v -> listener.onEncounterClick(encounter));
        } else {
            int RebelIndex = position - encounters.size();
            RebelHistoryManager.DroneHistoryEntry RebelEntry = RebelEntries.get(RebelIndex);

            if (!RebelEntry.getRebelDetections().isEmpty()) {
                RebelScanner.RebelDetection primaryRebel = RebelEntry.getRebelDetections().get(0);
                holder.droneId.setText("🚨 " + primaryRebel.getType().getDisplayName());
            } else {
                holder.droneId.setText("🚨 Unknown Rebel");
            }

            if (holder.timestamp != null) {
                holder.timestamp.setText(formatTimestamp(new Date(RebelEntry.getTimestamp())));
            }

            if (holder.details != null) {
                String details = "Device: " + (RebelEntry.getDroneId() != null ? RebelEntry.getDroneId() : "Unknown") +
                        "\nThreat: " + (RebelEntry.getMaxRebelConfidence() > 0.8 ? "HIGH" :
                        RebelEntry.getMaxRebelConfidence() > 0.6 ? "MEDIUM" : "LOW");
                holder.details.setText(details);
            }

            holder.itemView.setOnClickListener(v -> listener.onRebelClick(RebelEntry));
        }
    }

    @Override
    public int getItemCount() {
        return encounters.size() + RebelEntries.size();
    }

    public void updateEncounters(List<DroneEncounter> newEncounters) {
        encounters = new ArrayList<>(newEncounters);
        originalEncounters = new ArrayList<>(newEncounters);
        notifyDataSetChanged();
    }

    public void updateRebelEntries(List<RebelHistoryManager.DroneHistoryEntry> newRebelEntries) {
        RebelEntries = new ArrayList<>(newRebelEntries);
        originalRebelEntries = new ArrayList<>(newRebelEntries);
        notifyDataSetChanged();
    }

    public void filter(String text) {
        if (text.isEmpty()) {
            encounters = new ArrayList<>(originalEncounters);
            RebelEntries = new ArrayList<>(originalRebelEntries);
        } else {
            String lowerText = text.toLowerCase();
            encounters = originalEncounters.stream()
                    .filter(e -> e.getId().toLowerCase().contains(lowerText))
                    .collect(Collectors.toList());

            RebelEntries = originalRebelEntries.stream()
                    .filter(e -> (e.getDroneId() != null && e.getDroneId().toLowerCase().contains(lowerText)) ||
                            (e.getMac() != null && e.getMac().toLowerCase().contains(lowerText)) ||
                            e.getRebelDetections().stream().anyMatch(b ->
                                    b.getType().getDisplayName().toLowerCase().contains(lowerText)))
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
        return String.format("Alt: %.1fm, Speed: %.1fm/s", encounter.getMaxAltitude(), encounter.getMaxSpeed());
    }
}