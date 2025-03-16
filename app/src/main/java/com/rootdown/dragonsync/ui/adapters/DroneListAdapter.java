package com.rootdown.dragonsync.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DroneListAdapter extends RecyclerView.Adapter<DroneListAdapter.ViewHolder> {
    private List<CoTMessage> messages = new ArrayList<>();
    private OnDroneClickListener listener;

    public interface OnDroneClickListener {
        void onDroneClick(CoTMessage message);
        void onLiveMapClick(CoTMessage message);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView droneIcon;
        public TextView droneId;
        public TextView position;
        public TextView description;
        public TextView rssi;
        public TextView timestamp;
        public TextView details;
        public Button liveMapButton;

        public ViewHolder(View view) {
            super(view);
            droneIcon = view.findViewById(R.id.drone_icon);
            droneId = view.findViewById(R.id.drone_id);
            position = view.findViewById(R.id.position);
            description = view.findViewById(R.id.description);
            rssi = view.findViewById(R.id.rssi);
            timestamp = view.findViewById(R.id.timestamp);
            details = view.findViewById(R.id.details);
            liveMapButton = view.findViewById(R.id.live_map_button);  // TODO livemap---
        }
    }

    public DroneListAdapter(OnDroneClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drone, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CoTMessage message = messages.get(position);

        // Set drone ID and indicator for spoofed drones
        String displayId = message.getUid();
        if (message.isSpoofed()) {
            displayId = "⚠️ " + displayId;
            holder.droneId.setTextColor(Color.YELLOW);
        } else {
            holder.droneId.setTextColor(Color.WHITE);
        }

        // Add source label
        if (message.getType() != null) {
            if (message.getType().contains("BLE")) {
                displayId += " [BT]";
            } else if (message.getType().contains("WiFi")) {
                displayId += " [WiFi]";
            } else if (message.getMac() == null) {
                displayId += "[SDR]";
            }
        }

        holder.droneId.setText(displayId);

        // Set position if available
        if (message.getCoordinate() != null) {
            holder.position.setText(String.format(Locale.US, "Lat: %.6f, Lon: %.6f",
                    message.getCoordinate().getLatitude(),
                    message.getCoordinate().getLongitude()));
            holder.position.setVisibility(View.VISIBLE);
        } else {
            holder.position.setVisibility(View.GONE);
        }

        // Set description if available
        if (message.getDescription() != null && !message.getDescription().isEmpty()) {
            holder.description.setText(message.getDescription());
            holder.description.setVisibility(View.VISIBLE);
        } else {
            holder.description.setVisibility(View.GONE);
        }

        // Set RSSI if available
        if (message.getRssi() != null) {
            holder.rssi.setText(String.format(Locale.US, "RSSI: %d dBm", message.getRssi()));
            holder.rssi.setVisibility(View.VISIBLE);
        } else {
            holder.rssi.setVisibility(View.GONE);
        }

        // Set timestamp if available
        if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
            try {
                long timestamp = Long.parseLong(message.getTimestamp());
                Date date = new Date(timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
                holder.timestamp.setText("Time: " + sdf.format(date));
                holder.timestamp.setVisibility(View.VISIBLE);
            } catch (NumberFormatException e) {
                holder.timestamp.setText("Time: " + message.getTimestamp());
                holder.timestamp.setVisibility(View.VISIBLE);
            }
        } else {
            holder.timestamp.setVisibility(View.GONE);
        }

        // Set additional details like altitude, speed
        StringBuilder details = new StringBuilder();

        if (message.getAlt() != null && !message.getAlt().isEmpty()) {
            try {
                double alt = Double.parseDouble(message.getAlt());
                details.append(String.format(Locale.US, "Alt: %.1f m", alt));
            } catch (NumberFormatException e) {
                // Skip if can't parse
            }
        }

        if (message.getSpeed() != null && !message.getSpeed().isEmpty()) {
            if (details.length() > 0) details.append(", ");
            try {
                double speed = Double.parseDouble(message.getSpeed());
                details.append(String.format(Locale.US, "Speed: %.1f m/s", speed));
            } catch (NumberFormatException e) {
                // Skip if can't parse
            }
        }

        if (details.length() > 0) {
            holder.details.setText(details.toString());
            holder.details.setVisibility(View.VISIBLE);
        } else {
            holder.details.setVisibility(View.GONE);
        }

        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDroneClick(message);
            }
        });

        holder.liveMapButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLiveMapClick(message);
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<CoTMessage> newMessages) {
        messages = newMessages;
        notifyDataSetChanged();
    }
}