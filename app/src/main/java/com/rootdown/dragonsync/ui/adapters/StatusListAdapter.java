// java/com.rootdown.dragonsync/ui/adapters/StatusListAdapter.java
package com.rootdown.dragonsync.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.StatusMessage;
import java.util.ArrayList;
import java.util.List;

public class StatusListAdapter extends RecyclerView.Adapter<StatusListAdapter.ViewHolder> {
    private List<StatusMessage> messages = new ArrayList<>();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView serialNumber;
        public TextView cpuUsage;
        public TextView memoryUsage;
        public TextView temperature;
        public TextView uptime;

        public ViewHolder(View view) {
            super(view);
            serialNumber = view.findViewById(R.id.serial_number);
            cpuUsage = view.findViewById(R.id.cpu_usage);
            memoryUsage = view.findViewById(R.id.memory_usage);
            temperature = view.findViewById(R.id.temperature);
            uptime = view.findViewById(R.id.uptime);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_status, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        StatusMessage message = messages.get(position);
        holder.serialNumber.setText(message.getSerialNumber());
        holder.cpuUsage.setText(String.format("CPU: %.1f%%", message.getSystemStats().getCpuUsage()));
        holder.memoryUsage.setText(String.format("Memory: %.1f%%",
                (message.getSystemStats().getMemory().getUsed() /
                        (float)message.getSystemStats().getMemory().getTotal()) * 100));
        holder.temperature.setText(String.format("%.1f°C", message.getSystemStats().getTemperature()));
        holder.uptime.setText(formatUptime(message.getSystemStats().getUptime()));
    }

    private String formatUptime(double uptime) {
        int hours = (int)(uptime / 3600);
        int minutes = (int)((uptime % 3600) / 60);
        int seconds = (int)(uptime % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<StatusMessage> newMessages) {
        messages = newMessages;
        notifyDataSetChanged();
    }
}