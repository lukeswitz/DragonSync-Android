package com.rootdown.dragonsync.ui.fragments;

import static android.content.ContentValues.TAG;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import com.rootdown.dragonsync.ui.views.CircularGaugeView;
import com.rootdown.dragonsync.viewmodels.ServiceViewModel;
import com.rootdown.dragonsync.viewmodels.StatusViewModel;
import com.rootdown.dragonsync.utils.Constants;

import java.util.Locale;

public class StatusFragment extends Fragment implements OnMapReadyCallback {
    private StatusViewModel statusViewModel;
    private ServiceViewModel serviceViewModel;

    private TextView serverNameText;
    private TextView uptimeText;
    private TextView cpuValueText;
    private TextView tempValueText;
    private TextView plutoTempValueText;
    private TextView zynqTempValueText;
    private TextView memoryText;
    private TextView diskText;
    private TextView coordinatesText;
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
    private StatusMessage latestMessage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusViewModel = new ViewModelProvider(requireActivity()).get(StatusViewModel.class);
        serviceViewModel = new ViewModelProvider(requireActivity()).get(ServiceViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status, container, false);

        initializeViews(view);

        mapView = view.findViewById(R.id.map_view);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
        }

        setupObservers();

        return view;
    }

    private void initializeViews(View view) {
        // Server and system status
        serverNameText = view.findViewById(R.id.server_name_text);
        uptimeText = view.findViewById(R.id.uptime_text);

        // CircularGaugeViews
        CircularGaugeView cpuGauge = view.findViewById(R.id.cpu_gauge);
        CircularGaugeView tempGauge = view.findViewById(R.id.temp_gauge);
        CircularGaugeView plutoTempGauge = view.findViewById(R.id.pluto_temp_gauge);
        CircularGaugeView zynqTempGauge = view.findViewById(R.id.zynq_temp_gauge);

        // Memory and Disk
        memoryText = view.findViewById(R.id.memory_text);
        diskText = view.findViewById(R.id.disk_text);
        memoryProgress = view.findViewById(R.id.memory_progress);
        diskProgress = view.findViewById(R.id.disk_progress);

        // Location
        coordinatesText = view.findViewById(R.id.coordinates_text);
        altitudeText = view.findViewById(R.id.altitude_text);
        speedText = view.findViewById(R.id.speed_text);

        // TODO update these gauges and add others
        // cpuGauge.setValue(cpuPercentage);
        // cpuGauge.setTitle("CPU");
        // cpuGauge.setUnit("%");
    }

    private void setupObservers() {
        statusViewModel.getStatusMessages().observe(getViewLifecycleOwner(), messages -> {
            if (!messages.isEmpty()) {
                latestMessage = messages.get(messages.size() - 1);
                updateStatusUI(latestMessage);
            }
        });
    }

    private void updateStatusUI(StatusMessage message) {
        if (message == null || message.getSystemStats() == null) return;

        if (message.getSerialNumber() != null) {
            serverNameText.setText(message.getSerialNumber());
        }

        double uptime = message.getSystemStats().getUptime();
        uptimeText.setText(formatUptime(uptime));

        updateCpuUsage(message.getSystemStats().getCpuUsage());
        updateTemperature(message.getSystemStats().getTemperature());

        if (message.getSystemStats().getMemory() != null) {
            updateMemoryUsage(message.getSystemStats().getMemory());
        }

        if (message.getSystemStats().getDisk() != null) {
            updateDiskStatus(message.getSystemStats().getDisk());
        }

        if (message.getAntStats() != null) {
            updateSdrStats(message.getAntStats());
        }

        if (message.getGpsData() != null) {
            updateLocationInfo(message.getGpsData());
            updateMapLocation(message);
        }
    }

    private void updateDiskStatus(StatusMessage.SystemStats.DiskStats disk) {
        if (disk != null) {
            // Access the fields directly since they're declared in the class
            long totalBytes = disk.total;
            long usedBytes = disk.used;

            if (totalBytes > 0) {
                double usedGB = usedBytes / 1024.0 / 1024.0 / 1024.0;
                double totalGB = totalBytes / 1024.0 / 1024.0 / 1024.0;
                diskText.setText(String.format(Locale.getDefault(), "%.1f/%.1fGB", usedGB, totalGB));

                int diskPercent = (int)(disk.percent * 100);
                diskProgress.setProgress(diskPercent);

                int diskColor;
                double diskWarningThreshold = 0.9; // 90% usage
                double diskCriticalThreshold = 0.95; // 95% usage

                if (disk.percent > diskCriticalThreshold) {
                    diskColor = getResources().getColor(R.color.red, null);
                } else if (disk.percent > diskWarningThreshold) {
                    diskColor = getResources().getColor(R.color.orange, null);
                } else {
                    diskColor = getResources().getColor(R.color.green, null);
                }
                diskProgress.setProgressTintList(ColorStateList.valueOf(diskColor));
            }
        }
    }

    private void updateCpuUsage(double cpuUsage) {
        if (cpuValueText == null || cpuProgress == null) {
            Log.e(TAG, "CPU views not initialized");
            return;
        }

        cpuValueText.setText(String.format(Locale.getDefault(), "%.1f", cpuUsage));
        cpuProgress.setProgress((int)cpuUsage);

        int cpuColor;
        if (cpuUsage > Constants.DEFAULT_CPU_WARNING_THRESHOLD) {
            cpuColor = getResources().getColor(R.color.red, null);
        } else if (cpuUsage > Constants.DEFAULT_CPU_WARNING_THRESHOLD * 0.8) {
            cpuColor = getResources().getColor(R.color.orange, null);
        } else {
            cpuColor = getResources().getColor(R.color.green, null);
        }
        cpuProgress.setProgressTintList(ColorStateList.valueOf(cpuColor));
        cpuValueText.setTextColor(cpuColor);
    }

    private void updateTemperature(double temp) {

        if (tempValueText == null || tempProgress == null) {
            Log.e(TAG, "Temperature views not initialized");
            return;
        }

        tempValueText.setText(String.format(Locale.getDefault(), "%.1f", temp));
        tempProgress.setProgress((int)temp);

        int tempColor;
        if (temp > Constants.DEFAULT_TEMP_WARNING_THRESHOLD) {
            tempColor = getResources().getColor(R.color.red, null);
        } else if (temp > Constants.DEFAULT_TEMP_WARNING_THRESHOLD * 0.8) {
            tempColor = getResources().getColor(R.color.orange, null);
        } else {
            tempColor = getResources().getColor(R.color.green, null);
        }
        tempProgress.setProgressTintList(ColorStateList.valueOf(tempColor));
        tempValueText.setTextColor(tempColor);
    }

    private void updateMemoryUsage(StatusMessage.SystemStats.MemoryStats memory) {
        if (memory != null) {
            long memTotal = memory.getTotal();
            long memUsed = memory.getUsed();

            if (memTotal > 0) {
                double memGB = memUsed / 1024.0 / 1024.0 / 1024.0;
                double totalGB = memTotal / 1024.0 / 1024.0 / 1024.0;
                memoryText.setText(String.format(Locale.getDefault(), "%.1f/%.1fGB", memGB, totalGB));

                int memPercent = (int)((double)memUsed / memTotal * 100);
                memoryProgress.setProgress(memPercent);

                int memColor;
                if (memPercent > Constants.DEFAULT_MEMORY_WARNING_THRESHOLD * 100) {
                    memColor = getResources().getColor(R.color.red, null);
                } else if (memPercent > Constants.DEFAULT_MEMORY_WARNING_THRESHOLD * 0.8 * 100) {
                    memColor = getResources().getColor(R.color.orange, null);
                } else {
                    memColor = getResources().getColor(R.color.green, null);
                }
                memoryProgress.setProgressTintList(ColorStateList.valueOf(memColor));
            }
        }
    }

    private void updateSdrStats(StatusMessage.ANTStats antStats) {

        if (plutoTempValueText == null || zynqTempValueText == null) {
            Log.e(TAG, "SDR Temperature views not initialized");
            return;
        }

        double plutoTemp = antStats.getPlutoTemp();
        double zynqTemp = antStats.getZynqTemp();

        plutoTempValueText.setText(String.format(Locale.getDefault(), "%.1f", plutoTemp));
        zynqTempValueText.setText(String.format(Locale.getDefault(), "%.1f", zynqTemp));

        plutoTempProgress.setProgress((int)plutoTemp);
        zynqTempProgress.setProgress((int)zynqTemp);

        int plutoColor;
        if (plutoTemp > Constants.DEFAULT_PLUTO_TEMP_THRESHOLD) {
            plutoColor = getResources().getColor(R.color.red, null);
        } else if (plutoTemp > Constants.DEFAULT_PLUTO_TEMP_THRESHOLD * 0.8) {
            plutoColor = getResources().getColor(R.color.orange, null);
        } else {
            plutoColor = getResources().getColor(R.color.green, null);
        }
        plutoTempProgress.setProgressTintList(ColorStateList.valueOf(plutoColor));
        plutoTempValueText.setTextColor(plutoColor);

        int zynqColor;
        if (zynqTemp > Constants.DEFAULT_ZYNQ_TEMP_THRESHOLD) {
            zynqColor = getResources().getColor(R.color.red, null);
        } else if (zynqTemp > Constants.DEFAULT_ZYNQ_TEMP_THRESHOLD * 0.8) {
            zynqColor = getResources().getColor(R.color.orange, null);
        } else {
            zynqColor = getResources().getColor(R.color.green, null);
        }
        zynqTempProgress.setProgressTintList(ColorStateList.valueOf(zynqColor));
        zynqTempValueText.setTextColor(zynqColor);
    }

    private void updateLocationInfo(StatusMessage.GPSData gpsData) {
        String coordText = String.format(Locale.getDefault(), "%.6f°, %.6f°",
                gpsData.getLatitude(),
                gpsData.getLongitude());
        coordinatesText.setText(coordText);

        altitudeText.setText(String.format(Locale.getDefault(), getString(R.string.altitude_format), gpsData.getAltitude()));
        speedText.setText(String.format(Locale.getDefault(), getString(R.string.speed_format), gpsData.getSpeed()));
    }

    private void updateMapLocation(StatusMessage message) {
        if (googleMap != null && message.getGpsData() != null) {
            LatLng position = new LatLng(
                    message.getGpsData().getLatitude(),
                    message.getGpsData().getLongitude()
            );

            googleMap.clear();
            googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(message.getSerialNumber()));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
        }
    }

    private String formatUptime(double uptime) {
        int hours = (int)(uptime / 3600);
        int minutes = (int)((uptime % 3600) / 60);
        int seconds = (int)(uptime % 60);
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        if (latestMessage != null && latestMessage.getGpsData() != null) {
            updateMapLocation(latestMessage);
        }
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