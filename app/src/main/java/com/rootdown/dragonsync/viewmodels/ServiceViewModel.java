package com.rootdown.dragonsync.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.rootdown.dragonsync.models.ServiceControl;
import com.rootdown.dragonsync.network.ZMQHandler;
import com.rootdown.dragonsync.utils.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServiceViewModel extends ViewModel {
    private final MutableLiveData<List<ServiceControl>> services = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<HealthReport> healthReport = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final ZMQHandler zmqHandler = new ZMQHandler();

    public static class HealthReport {
        private String overallHealth;
        private List<ServiceControl.ServiceIssue> issues;
        private long timestamp;

        public HealthReport(String overallHealth, List<ServiceControl.ServiceIssue> issues, long timestamp) {
            this.overallHealth = overallHealth;
            this.issues = issues;
            this.timestamp = timestamp;
        }

        public int getStatusColor() {
            switch (overallHealth.toLowerCase()) {
                case "healthy": return android.graphics.Color.GREEN;
                case "degraded": return android.graphics.Color.YELLOW;
                default: return android.graphics.Color.RED;
            }
        }
    }

    public void startMonitoring() {
//        zmqHandler.connect(
//                Settings.getInstance(getApplication()).getZmqHost(),
//                Settings.getInstance(getApplication()).getZmqStatusPort(),
//                message -> handleStatusUpdate(message)
//        );
    }

    public void stopMonitoring() {
        zmqHandler.disconnect();
    }

    public void toggleService(ServiceControl service) {
        isLoading.setValue(true);

        Map<String, Object> command = new HashMap<>();
        command.put("command", new HashMap<String, Object>() {{
            put("type", "service_control");
            put("service", service.getId());
//            put("action", service.getStatus().isActive() ? "disable" : "enable");
            put("timestamp", System.currentTimeMillis() / 1000.0);
        }});

//        zmqHandler.sendServiceCommand(command, (success, response) -> {
//            isLoading.setValue(false);
//            if (!success) {
//                error.setValue((String) response);
//            }
//        });
    }

    public void restartService(ServiceControl service) {
        isLoading.setValue(true);

        Map<String, Object> command = new HashMap<>();
        command.put("command", new HashMap<String, Object>() {{
            put("type", "service_control");
            put("service", service.getId());
            put("action", "restart");
            put("timestamp", System.currentTimeMillis() / 1000.0);
        }});

//        zmqHandler.sendServiceCommand(command, (success, response) -> {
//            isLoading.setValue(false);
//            if (!success) {
//                error.setValue((String) response);
//            }
//        });
    }

    private void handleStatusUpdate(String message) {
        try {
            JSONObject json = new JSONObject(message);
            JSONObject stats = json.getJSONObject("system_stats");
            JSONObject services = stats.getJSONObject("services");

            List<ServiceControl> updatedServices = new ArrayList<>();
            JSONObject categories = services.getJSONObject("by_category");

            Iterator<String> categoryKeys = categories.keys();
            while (categoryKeys.hasNext()) {
                String category = categoryKeys.next();
                JSONObject categoryServices = categories.getJSONObject(category);

                Iterator<String> serviceKeys = categoryServices.keys();
                while (serviceKeys.hasNext()) {
                    String serviceName = serviceKeys.next();
                    JSONObject details = categoryServices.getJSONObject(serviceName);
                    updatedServices.add(parseServiceControl(serviceName, category, details));
                }
            }

            this.services.postValue(updatedServices);

            if (services.has("health_report")) {
                JSONObject healthReport = services.getJSONObject("health_report");
                this.healthReport.postValue(new HealthReport(
                        healthReport.getString("overall_health"),
                        parseIssues(healthReport.getJSONArray("issues")),
                        System.currentTimeMillis()
                ));
            }

        } catch (JSONException e) {
            error.postValue("Failed to parse service status: " + e.getMessage());
        }
    }

    private ServiceControl parseServiceControl(String name, String category, JSONObject details) {
        // Parse service details and create ServiceControl object
    // TODO!
        return null;
    }

    private List<ServiceControl.ServiceIssue> parseIssues(JSONArray issuesArray) {
        // Parse issues array and create ServiceIssue objects
        // TODO!
        return new ArrayList<>();
    }

    public LiveData<List<ServiceControl>> getServices() {
        return services;
    }

    public LiveData<List<ServiceControl>> getCriticalServices() {
        return Transformations.map(services, serviceList ->
                serviceList.stream()
                        .filter(ServiceControl::isCritical)
                        .collect(Collectors.toList())
        );
    }

    public LiveData<HealthReport> getHealthReport() {
        return healthReport;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }
}