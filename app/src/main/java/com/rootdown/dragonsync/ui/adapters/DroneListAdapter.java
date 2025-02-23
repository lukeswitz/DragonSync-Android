package com.rootdown.dragonsync.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import java.util.ArrayList;
import java.util.List;

public class DroneListAdapter extends RecyclerView.Adapter<DroneListAdapter.ViewHolder> {
    private List<CoTMessage> messages = new ArrayList<>();
    private OnDroneClickListener listener;

    public interface OnDroneClickListener {
        void onDroneClick(CoTMessage message);
        void onLiveMapClick(CoTMessage message);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView droneId;
        public TextView position;
        public TextView description;
        public TextView rssi;
        public View liveMapButton;

        public ViewHolder(View view) {
            super(view);
            droneId = view.findViewById(R.id.drone_id);
            position = view.findViewById(R.id.position);
            description = view.findViewById(R.id.description);
            rssi = view.findViewById(R.id.rssi);
            liveMapButton = view.findViewById(R.id.live_map_button);
        }
    }

    public DroneListAdapter(OnDroneClickListener listener) {
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drone, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        CoTMessage message = messages.get(position);
        holder.droneId.setText(message.getUid());
        holder.position.setText(String.format("%s, %s", message.getLat(), message.getLon()));
        holder.description.setText(message.getDescription());
        if (message.getRssi() != null) {
            holder.rssi.setText(String.format("%d dBm", message.getRssi()));
        }

        holder.itemView.setOnClickListener(v -> listener.onDroneClick(message));
        holder.liveMapButton.setOnClickListener(v -> listener.onLiveMapClick(message));
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