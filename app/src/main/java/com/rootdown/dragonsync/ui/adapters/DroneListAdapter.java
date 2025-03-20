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
import com.rootdown.dragonsync.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
            displayId = context.getString(R.string.spoofed_drone_prefix, displayId);
            holder.droneId.setTextColor(context.getColor(R.color.warning_amber));
        } else {
            holder.droneId.setTextColor(context.getColor(R.color.on_surface_high));
        }

        // Add source label
        if (message.getType() != null) {
            if (message.getType().contains("BLE")) {
                displayId = context.getString(R.string.format_drone_id_bt, displayId);
            } else if (message.getType().contains("WiFi")) {
                displayId = context.getString(R.string.format_drone_id_wifi, displayId);
            } else if (message.getMac() == null) {
                displayId = context.getString(R.string.format_drone_id_sdr, displayId);
            }
        }

        holder.droneId.setText(displayId);

        // Set description if available
        if (message.getDescription() != null && !message.getDescription().isEmpty()) {
            holder.description.setText(message.getDescription());
            holder.description.setVisibility(View.VISIBLE);
        } else {
            holder.description.setText(R.string.drone_description_placeholder);
            holder.description.setVisibility(View.GONE);
        }

        // Set position if available
        if (message.getCoordinate() != null) {
            holder.position.setText(context.getString(R.string.format_coordinates,
                    message.getCoordinate().getLatitude(),
                    message.getCoordinate().getLongitude()));
            holder.position.setVisibility(View.VISIBLE);
        } else {
            holder.position.setText(R.string.position_placeholder);
            holder.position.setVisibility(View.GONE);
        }

        // Set altitude if available
        if (message.getAlt() != null && !message.getAlt().isEmpty()) {
            try {
                double alt = Double.parseDouble(message.getAlt());
                holder.altitude.setText(context.getString(R.string.format_altitude, alt));
                holder.altitude.setVisibility(View.VISIBLE);
            } catch (NumberFormatException e) {
                holder.altitude.setText(R.string.altitude_placeholder);
                holder.altitude.setVisibility(View.GONE);
            }
        } else {
            holder.altitude.setText(R.string.altitude_placeholder);
            holder.altitude.setVisibility(View.GONE);
        }

        // Set speed if available
        if (message.getSpeed() != null && !message.getSpeed().isEmpty()) {
            try {
                double speed = Double.parseDouble(message.getSpeed());
                holder.speed.setText(context.getString(R.string.format_speed, speed));
                holder.speed.setVisibility(View.VISIBLE);
            } catch (NumberFormatException e) {
                holder.speed.setText(R.string.speed_placeholder);
                holder.speed.setVisibility(View.GONE);
            }
        } else {
            holder.speed.setText(R.string.speed_placeholder);
            holder.speed.setVisibility(View.GONE);
        }

        // Set RSSI if available
        if (message.getRssi() != null) {
            holder.rssi.setText(context.getString(R.string.format_rssi, message.getRssi()));

            // Set color based on signal strength thresholds from Constants
            int rssiColor;
            if (message.getRssi() > Constants.RSSI_GOOD_THRESHOLD) {
                rssiColor = context.getColor(R.color.status_green);
            } else if (message.getRssi() > Constants.RSSI_MEDIUM_THRESHOLD) {
                rssiColor = context.getColor(R.color.status_yellow);
            } else {
                rssiColor = context.getColor(R.color.status_red);
            }
            holder.rssi.setTextColor(rssiColor);
            holder.rssi.setVisibility(View.VISIBLE);
        } else {
            holder.rssi.setText(R.string.rssi_placeholder);
            holder.rssi.setVisibility(View.GONE);
        }

        // Set timestamp if available
        if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
            try {
                long timestamp = Long.parseLong(message.getTimestamp());
                Date date = new Date(timestamp);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                holder.timestamp.setText(context.getString(R.string.format_timestamp,
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        cal.get(Calendar.SECOND)));
                holder.timestamp.setVisibility(View.VISIBLE);
            } catch (NumberFormatException e) {
                holder.timestamp.setText(R.string.timestamp_placeholder);
                holder.timestamp.setVisibility(View.GONE);
            }
        } else {
            holder.timestamp.setText(R.string.timestamp_placeholder);
            holder.timestamp.setVisibility(View.GONE);
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