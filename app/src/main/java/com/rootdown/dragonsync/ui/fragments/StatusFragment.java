package com.rootdown.dragonsync.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.widget.ProgressBar;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.StatusMessage;
import com.rootdown.dragonsync.viewmodels.ServiceViewModel;
import com.rootdown.dragonsync.viewmodels.StatusViewModel;
import com.rootdown.dragonsync.utils.Constants;

public class StatusFragment extends Fragment implements OnMapReadyCallback {
    private StatusViewModel statusViewModel;
    private ServiceViewModel serviceViewModel;

    // Status views
    private TextView serverNameText;
    private TextView uptimeText;
    private TextView cpuValueText;
    private TextView tempValueText;
    private TextView plutoTempValueText;
    private TextView zynqTempValueText;
    private TextView memoryText;
    private TextView diskText;
    private TextView latitudeText;
    private TextView longitudeText;
    private TextView altitudeText;
    private TextView speedText;

    private ProgressBar cpuProgress;
    private ProgressBar tempProgress;
    private ProgressBar plutoTempProgress;
    private ProgressBar zynqTempProgress;
    private ProgressBar memoryProgress;
    private ProgressBar diskProgress;

    private MapView mapView;
    private GoogleMap googleMap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusViewModel = new ViewModelProvider(requireActivity()).get(StatusViewModel.class);
        serviceViewModel = new ViewModelProvider(requireActivity()).get(ServiceViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);

        // Initialize views
        serverNameText = view.findViewById(R.id.server_name_text);
        uptimeText = view.findViewById(R.id.uptime_text);
        cpuValueText = view.findViewById(R.id.cpu_value_text);
        tempValueText = view.findViewById(R.id.temp_value_text);
        plutoTempValueText = view.findViewById(R.id.pluto_temp_value_text);
        zynqTempValueText = view.findViewById(R.id.zynq_temp_value_text);
        memoryText = view.findViewById(R.id.memory_text);
        diskText = view.findViewById(R.id.disk_text);
        latitudeText = view.findViewById(R.id.latitude_text);
        longitudeText = view.findViewById(R.id.longitude_text);
        altitudeText = view.findViewById(R.id.altitude_text);
        speedText = view.findViewById(R.id.speed_text);

        cpuProgress = view.findViewById(R.id.cpu_progress);
        tempProgress = view.findViewById(R.id.temp_progress);
        plutoTempProgress = view.findViewById(R.id.pluto_temp_progress);
        zynqTempProgress = view.findViewById(R.id.zynq_temp_progress);
        memoryProgress = view.findViewById(R.id.memory_progress);
        diskProgress = view.findViewById(R.id.disk_progress);

        mapView = view.findViewById(R.id.map_view);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }

        setupObservers();

        return view;
    }

    private void setupObservers() {
        statusViewModel.getStatusMessages().observe(getViewLifecycleOwner(), messages -> {
            if (!messages.isEmpty()) {
                StatusMessage latestMessage = messages.get(messages.size() - 1);
                updateStatusUI(latestMessage);
            }
        });
    }

    private void updateStatusUI(StatusMessage message) {
        if (message == null || message.getSystemStats() == null) return;

        // Update server name and uptime
        if (message.getSerialNumber() != null) {
            serverNameText.setText(message.getSerialNumber());
        }

        double uptime = message.getSystemStats().getUptime();
        uptimeText.setText(formatUptime(uptime));

        // Update CPU usage
        double cpuUsage = message.getSystemStats().getCpuUsage();
        cpuValueText.setText(String.format("%.1f", cpuUsage));
        cpuProgress.setProgress((int)cpuUsage);

        // Update CPU color based on threshold
        int cpuColor;
        if (cpuUsage > Constants.DEFAULT_CPU_WARNING_THRESHOLD) {
            cpuColor = Color.RED;
        } else if (cpuUsage > Constants.DEFAULT_CPU_WARNING_THRESHOLD * 0.8) {
            cpuColor = Color.YELLOW;
        } else {
            cpuColor = Color.GREEN;
        }
        cpuProgress.setProgressTintList(ColorStateList.valueOf(cpuColor));
        cpuValueText.setTextColor(cpuColor);

        // Update temperature
        double temp = message.getSystemStats().getTemperature();
        tempValueText.setText(String.format("%.1f", temp));
        tempProgress.setProgress((int)temp);

        // Update temperature color based on threshold
        int tempColor;
        if (temp > Constants.DEFAULT_TEMP_WARNING_THRESHOLD) {
            tempColor = Color.RED;
        } else if (temp > Constants.DEFAULT_TEMP_WARNING_THRESHOLD * 0.8) {
            tempColor = Color.YELLOW;
        } else {
            tempColor = Color.GREEN;
        }
        tempProgress.setProgressTintList(ColorStateList.valueOf(tempColor));
        tempValueText.setTextColor(tempColor);

        // Update memory usage
        if (message.getSystemStats().getMemory() != null) {
            long memTotal = message.getSystemStats().getMemory().getTotal();
            long memUsed = message.getSystemStats().getMemory().getUsed();

            if (memTotal > 0) {
                double memGB = memUsed / 1024.0 / 1024.0 / 1024.0;
                double totalGB = memTotal / 1024.0 / 1024.0 / 1024.0;
                memoryText.setText(String.format("%.1f/%.1fGB", memGB, totalGB));

                // Update memory progress
                int memPercent = (int)((double)memUsed / memTotal * 100);
                memoryProgress.setProgress(memPercent);
            }
        }

        // Update ANT stats if available
        if (message.getAntStats() != null) {
            double plutoTemp = message.getAntStats().getPlutoTemp();
            double zynqTemp = message.getAntStats().getZynqTemp();

            plutoTempValueText.setText(String.format("%.1f", plutoTemp));
            zynqTempValueText.setText(String.format("%.1f", zynqTemp));

            plutoTempProgress.setProgress((int)plutoTemp);
            zynqTempProgress.setProgress((int)zynqTemp);

            // Set colors based on thresholds
            int plutoColor;
            if (plutoTemp > Constants.DEFAULT_PLUTO_TEMP_THRESHOLD) {
                plutoColor = Color.RED;
            } else if (plutoTemp > Constants.DEFAULT_PLUTO_TEMP_THRESHOLD * 0.8) {
                plutoColor = Color.YELLOW;
            } else {
                plutoColor = Color.GREEN;
            }
            plutoTempProgress.setProgressTintList(ColorStateList.valueOf(plutoColor));
            plutoTempValueText.setTextColor(plutoColor);

            int zynqColor;
            if (zynqTemp > Constants.DEFAULT_ZYNQ_TEMP_THRESHOLD) {
                zynqColor = Color.RED;
            } else if (zynqTemp > Constants.DEFAULT_ZYNQ_TEMP_THRESHOLD * 0.8) {
                zynqColor = Color.YELLOW;
            } else {
                zynqColor = Color.GREEN;
            }
            zynqTempProgress.setProgressTintList(ColorStateList.valueOf(zynqColor));
            zynqTempValueText.setTextColor(zynqColor);
        }

        // Update GPS data if available
        if (message.getGpsData() != null) {
            latitudeText.setText(String.format("%.6f°", message.getGpsData().getLatitude()));
            longitudeText.setText(String.format("%.6f°", message.getGpsData().getLongitude()));
            altitudeText.setText(String.format("ALT %.1fm", message.getGpsData().getAltitude()));
            speedText.setText(String.format("SPD %.1fm/s", message.getGpsData().getSpeed()));

            // Update map if available
            updateMapLocation(message);
        }
    }

    private void updateMapLocation(StatusMessage message) {
        if (googleMap != null && message.getGpsData() != null) {
            LatLng position = new LatLng(
                    message.getGpsData().getLatitude(),
                    message.getGpsData().getLongitude()
            );

            // Clear previous markers
            googleMap.clear();

            // Add new marker
            googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(message.getSerialNumber()));

            // Move camera to the position
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
        }
    }

    private String formatUptime(double uptime) {
        int hours = (int)(uptime / 3600);
        int minutes = (int)((uptime % 3600) / 60);
        int seconds = (int)(uptime % 60);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        if (mapView != null) {
            mapView.onLowMemory();
        }
        super.onLowMemory();
    }
}