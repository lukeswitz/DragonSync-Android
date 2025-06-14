package com.rootdown.dragonsync.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.DroneSignature;
import com.rootdown.dragonsync.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DroneListAdapter extends RecyclerView.Adapter<DroneListAdapter.ViewHolder> {
    private List<CoTMessage> messages = new ArrayList<>();
    private OnDroneClickListener listener;
    private Context context;

    public interface OnDroneClickListener {
        void onDroneClick(CoTMessage message);
        void onLiveMapClick(CoTMessage message);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView detectionTypeIcon;
        public TextView droneTitle;
        public TextView droneId;
        public TextView droneType;
        public TextView macAddress;
        public TextView position;
        public TextView description;
        public TextView rssi;
        public TextView timestamp;
        public TextView altitude;
        public TextView speed;
        public TextView pilotLocation;
        public TextView macAddressDetail;
        public TextView manufacturer;
        public Button detailsButton;
        public Button liveMapButton;
        public com.google.android.material.card.MaterialCardView rssiContainer;
        public ImageView warningIcon;
        public ImageView spoofWarningIcon;
        public ImageView verifiedIcon;
        public FrameLayout miniMap;
        public LinearLayout macWarningContainer;
        public LinearLayout macListContainer;
        public LinearLayout pilotLocationRow;
        public LinearLayout manufacturerRow;
        public View macWarningSeparator;

        public ViewHolder(View view) {
            super(view);
            detectionTypeIcon = view.findViewById(R.id.detection_type_icon);
            droneTitle = view.findViewById(R.id.drone_title);
            droneId = view.findViewById(R.id.drone_id);
            droneType = view.findViewById(R.id.drone_type);
            macAddress = view.findViewById(R.id.mac_address);
            position = view.findViewById(R.id.position);
            description = view.findViewById(R.id.description);
            rssi = view.findViewById(R.id.rssi);
            rssiContainer = view.findViewById(R.id.rssi_container);
            timestamp = view.findViewById(R.id.timestamp);
            altitude = view.findViewById(R.id.altitude);
            speed = view.findViewById(R.id.speed);
            pilotLocation = view.findViewById(R.id.pilot_location);
            macAddressDetail = view.findViewById(R.id.mac_address_detail);
            manufacturer = view.findViewById(R.id.manufacturer);
            detailsButton = view.findViewById(R.id.details_button);
            liveMapButton = view.findViewById(R.id.live_map_button);
            warningIcon = view.findViewById(R.id.warning_icon);
            spoofWarningIcon = view.findViewById(R.id.spoof_warning_icon);
            verifiedIcon = view.findViewById(R.id.verified_icon);
            miniMap = view.findViewById(R.id.mini_map);
            macWarningContainer = view.findViewById(R.id.mac_warning_container);
            macListContainer = view.findViewById(R.id.mac_list_container);
            pilotLocationRow = view.findViewById(R.id.pilot_location_row);
            manufacturerRow = view.findViewById(R.id.manufacturer_row);
            macWarningSeparator = view.findViewById(R.id.mac_warning_separator);
        }
    }

    public DroneListAdapter(OnDroneClickListener listener) {
        this.listener = listener;
    }

    public DroneListAdapter(Context context, OnDroneClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (context == null) {
            context = parent.getContext();
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drone, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CoTMessage message = messages.get(position);

        // Set drone title (friendly name)
        String title = determineDroneTitle(message);
        holder.droneTitle.setText(title);

        // Set drone ID
        holder.droneId.setText(message.getUid());

        // Set drone type
        if (message.getUaType() != null) {
            holder.droneType.setText("Type: " + formatUAType(message.getUaType()));
        } else {
            holder.droneType.setText("Type: Unknown");
        }

        // Set MAC address and connection type
        if (message.getMac() != null) {
            String connectionType = determineConnectionType(message);
            holder.macAddress.setText(message.getMac() + " " + connectionType);

            // Set appropriate icon
            int iconRes = getConnectionIcon(connectionType);
            holder.detectionTypeIcon.setImageResource(iconRes);
        } else {
            holder.macAddress.setText("No MAC address");
            holder.detectionTypeIcon.setImageResource(R.drawable.ic_radio_tower);
        }

        // Set RSSI with color coding
        if (message.getRssi() != null) {
            holder.rssi.setText(message.getRssi() + " dBm");
            int rssiColor = getRssiColor(message.getRssi());
            holder.rssi.setTextColor(ContextCompat.getColor(context, rssiColor));

            // Update RSSI container colors
            holder.rssiContainer.setStrokeColor(ContextCompat.getColor(context, rssiColor));
            int bgColor = getRssiBgColor(message.getRssi());
            holder.rssiContainer.setCardBackgroundColor(ContextCompat.getColor(context, bgColor));
        } else {
            holder.rssi.setText("— dBm");
            holder.rssi.setTextColor(ContextCompat.getColor(context, R.color.on_surface_medium));
        }

        // Set verified/spoofed status
        if (message.isSpoofed()) {
            holder.spoofWarningIcon.setVisibility(View.VISIBLE);
            if (holder.verifiedIcon != null) {
                holder.verifiedIcon.setVisibility(View.GONE);
            }
        } else {
            holder.spoofWarningIcon.setVisibility(View.GONE);
            if (holder.verifiedIcon != null) {
                holder.verifiedIcon.setVisibility(View.VISIBLE);
            }
        }

        // Set position
        if (message.getCoordinate() != null) {
            holder.position.setText(String.format(Locale.US, "%.6f, %.6f",
                    message.getCoordinate().getLatitude(),
                    message.getCoordinate().getLongitude()));
        } else {
            holder.position.setText("Unknown");
        }

        // Set altitude
        if (message.getAlt() != null && !message.getAlt().isEmpty()) {
            try {
                double alt = Double.parseDouble(message.getAlt());
                holder.altitude.setText(String.format(Locale.US, "%.1fm", alt));
            } catch (NumberFormatException e) {
                holder.altitude.setText("—");
            }
        } else {
            holder.altitude.setText("—");
        }

        // Set speed
        if (message.getSpeed() != null && !message.getSpeed().isEmpty()) {
            try {
                double speed = Double.parseDouble(message.getSpeed());
                holder.speed.setText(String.format(Locale.US, "%.1fm/s", speed));
            } catch (NumberFormatException e) {
                holder.speed.setText("—");
            }
        } else {
            holder.speed.setText("—");
        }

        // Set pilot location
        if (message.getPilotLat() != null && message.getPilotLon() != null) {
            try {
                double lat = Double.parseDouble(message.getPilotLat());
                double lon = Double.parseDouble(message.getPilotLon());
                holder.pilotLocation.setText(String.format(Locale.US, "%.2f, %.2f", lat, lon));
            } catch (NumberFormatException e) {
                holder.pilotLocation.setText("—");
            }
        } else {
            holder.pilotLocation.setText("—");
        }

        // Set manufacturer
        if (message.getManufacturer() != null && !message.getManufacturer().isEmpty()) {
            holder.manufacturer.setText(message.getManufacturer());
        } else {
            holder.manufacturer.setText("Unknown");
        }

        // Set MAC address detail
        if (message.getMac() != null) {
            holder.macAddressDetail.setText(message.getMac());
        } else {
            holder.macAddressDetail.setText("—");
        }

        // Set description
        if (message.getDescription() != null && !message.getDescription().isEmpty()) {
            holder.description.setText(message.getDescription());
            holder.description.setVisibility(View.VISIBLE);
        } else {
            holder.description.setVisibility(View.GONE);
        }

        // Set timestamp
        if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
            try {
                long timestamp = Long.parseLong(message.getTimestamp());
                Date date = new Date(timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                holder.timestamp.setText("Last seen: " + sdf.format(date));
            } catch (NumberFormatException e) {
                holder.timestamp.setText("Last seen: Unknown");
            }
        } else {
            holder.timestamp.setText("Last seen: Unknown");
        }

        // Setup MAC randomization warning
        setupMacRandomizationWarning(holder, message);

        // Setup click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDroneClick(message);
            }
        });

        if (holder.detailsButton != null) {
            holder.detailsButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDroneClick(message);
                }
            });
        }

        if (holder.liveMapButton != null) {
            holder.liveMapButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLiveMapClick(message);
                }
            });
        }
    }

    private String determineDroneTitle(CoTMessage message) {
        // Logic to determine friendly drone names
        if (message.getSelfIDText() != null && !message.getSelfIDText().isEmpty()) {
            return message.getSelfIDText();
        } else if (message.getDescription() != null && !message.getDescription().isEmpty()) {
            return message.getDescription();
        } else if (message.getManufacturer() != null) {
            switch (message.getManufacturer()) {
                case "DJI":
                    return "DJI Drone";
                default:
                    return message.getManufacturer() + " Drone";
            }
        } else if (message.getType() != null && message.getType().contains("WiFi")) {
            return "WiFi Drone";
        }
        return "Unknown Drone";
    }

    private String determineConnectionType(CoTMessage message) {
        if (message.getType() != null) {
            if (message.getType().contains("BLE") || message.getType().contains("BLUETOOTH")) {
                return "BT";
            } else if (message.getType().contains("WiFi")) {
                return "WiFi";
            }
        }
        return "SDR";
    }

    private int getConnectionIcon(String connectionType) {
        switch (connectionType) {
            case "BT":
                return R.drawable.ic_bluetooth;
            case "WiFi":
                return R.drawable.ic_wifi;
            default:
                return R.drawable.ic_radio_tower;
        }
    }

    private int getRssiColor(int rssi) {
        if (rssi > Constants.RSSI_GOOD_THRESHOLD) {
            return R.color.status_green;
        } else if (rssi > Constants.RSSI_MEDIUM_THRESHOLD) {
            return R.color.status_yellow;
        } else {
            return R.color.status_red;
        }
    }

    private int getRssiBgColor(int rssi) {
        if (rssi > Constants.RSSI_GOOD_THRESHOLD) {
            return R.color.status_green_10;
        } else if (rssi > Constants.RSSI_MEDIUM_THRESHOLD) {
            return R.color.status_yellow_10;
        } else {
            return R.color.status_red_10;
        }
    }

    private String formatUAType(DroneSignature.IdInfo.UAType uaType) {
        switch (uaType) {
            case HELICOPTER:
                return "Helicopter/Multirotor";
            case AEROPLANE:
                return "Aeroplane";
            case GLIDER:
                return "Glider";
            case AIRSHIP:
                return "Airship";
            case FREE_BALLOON:
                return "Free Balloon";
            case CAPTIVE:
                return "Captive Balloon";
            case ROCKET:
                return "Rocket";
            case NONE:
                return "None";
            default:
                return "Other";
        }
    }

    private void setupMacRandomizationWarning(ViewHolder holder, CoTMessage message) {
        if (holder.macListContainer != null) {
            holder.macListContainer.removeAllViews();
        }

        if (shouldShowMacWarning(message)) {
            if (holder.macWarningContainer != null) {
                holder.macWarningContainer.setVisibility(View.VISIBLE);
            }
            if (holder.macWarningSeparator != null) {
                holder.macWarningSeparator.setVisibility(View.VISIBLE);
            }

            Set<String> macs = getMacHistoryForDrone(message.getUid());
            if (macs != null && holder.macListContainer != null) {
                for (String mac : macs) {
                    TextView macView = new TextView(context);
                    macView.setText("• " + mac);
                    macView.setTextColor(ContextCompat.getColor(context, R.color.on_surface_medium));
                    macView.setTextSize(12f);
                    macView.setTypeface(android.graphics.Typeface.MONOSPACE);
                    holder.macListContainer.addView(macView);
                }
            }
        } else {
            if (holder.macWarningContainer != null) {
                holder.macWarningContainer.setVisibility(View.GONE);
            }
            if (holder.macWarningSeparator != null) {
                holder.macWarningSeparator.setVisibility(View.GONE);
            }
        }
    }

    private boolean shouldShowMacWarning(CoTMessage message) {
        if (message.getMac() == null || message.getMac().isEmpty()) {
            return false;
        }

        Set<String> macs = getMacHistoryForDrone(message.getUid());
        return macs != null && macs.size() > 1;
    }

    private Set<String> getMacHistoryForDrone(String uid) {
        // TODO: Implement MAC history tracking
        // This should connect to your MAC history tracking system
        return null;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<CoTMessage> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }
}