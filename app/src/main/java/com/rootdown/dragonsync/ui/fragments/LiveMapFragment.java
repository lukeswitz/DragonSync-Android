package com.rootdown.dragonsync.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.ConnectionMode;
import com.rootdown.dragonsync.utils.DeviceLocationManager;
import com.rootdown.dragonsync.utils.Settings;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;

import android.location.Location;
import android.util.Log;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LiveMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "LiveMapFragment";

    private DeviceLocationManager.LocationUpdateListener locationListener;
    private MapView mapView;
    private GoogleMap googleMap;
    private CoTViewModel viewModel;
    private Map<String, Marker> droneMarkers = new HashMap<>();
    private Map<String, Marker> operatorMarkers = new HashMap<>();
    private Map<String, Marker> homeMarkers = new HashMap<>();
    private Map<String, List<LatLng>> flightPaths = new HashMap<>();
    private boolean userHasMovedMap = false;
    private CoTMessage initialMessage;

    private Marker userLocationMarker;
    private boolean isOnboardMode = false;
    private DeviceLocationManager locationManager;
    private TextView activeDronesCount;

    public static LiveMapFragment newInstance(CoTMessage initialMessage) {
        LiveMapFragment fragment = new LiveMapFragment();
        Bundle args = new Bundle();
        args.putParcelable("initial_message", initialMessage);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(CoTViewModel.class);
        if (getArguments() != null) {
            initialMessage = getArguments().getParcelable("initial_message");
        }

        // Check if we're in onboard mode
        Settings settings = Settings.getInstance(requireContext());
        isOnboardMode = settings.getConnectionMode() == ConnectionMode.ONBOARD;

        // Set up location manager for onboard mode
        if (isOnboardMode) {
            locationManager = DeviceLocationManager.getInstance(requireContext());
        }

        locationListener = this::updateUserLocationOnMap;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_live_map, container, false);

        activeDronesCount = view.findViewById(R.id.active_drones_count);
        mapView = view.findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);


        View mapContainer = view.findViewById(R.id.map_view);
        mapContainer.setOnTouchListener((v, event) -> {
            // Prevent parent views from intercepting touch events
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        mapView.getMapAsync(this);

        view.findViewById(R.id.show_drone_list).setOnClickListener(v -> {
            // Show drone list bottom sheet
            DroneListBottomSheet.newInstance().show(
                    getChildFragmentManager(),
                    "drone_list"
            );
        });

        return view;
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);


        setupMapSettings();
        setupMapGestures();

        // Start tracking user location in onboard mode
        if (isOnboardMode) {
            locationManager.addListener(locationListener);
            locationManager.startLocationUpdates();

            // Initialize with current location if available
            Location currentLocation = locationManager.getCurrentLocation();
            if (currentLocation != null) {
                updateUserLocationOnMap(currentLocation);
            }
        }

        observeDrones();

        if (initialMessage != null && initialMessage.getCoordinate() != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(
                            initialMessage.getCoordinate().getLatitude(),
                            initialMessage.getCoordinate().getLongitude()
                    ),
                    15f
            ));
        } else if (isOnboardMode) {
            // In onboard mode without initial drone, center on user
            Location userLocation = locationManager.getCurrentLocation();
            if (userLocation != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(userLocation.getLatitude(), userLocation.getLongitude()),
                        15f
                ));
            }
        }
    }

    private void setupMapSettings() {
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Set up marker info window
        googleMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null; // Use default window frame
            }

            @Override
            public View getInfoContents(Marker marker) {
                View view = getLayoutInflater().inflate(R.layout.map_info_window, null);
                TextView title = view.findViewById(R.id.info_window_title);
                TextView snippet = view.findViewById(R.id.info_window_snippet);

                title.setText(marker.getTitle());
                snippet.setText(marker.getSnippet());

                return view;
            }
        });
    }

    private void setupMapGestures() {
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                userHasMovedMap = true;
            }
        });
    }

    private void observeDrones() {
        viewModel.getParsedMessages().observe(getViewLifecycleOwner(), messages -> {
            updateDroneCount(messages.size());

            for (CoTMessage message : messages) {
                updateDroneOnMap(message);
            }

            if (!userHasMovedMap && !messages.isEmpty()) {
                updateCameraToFitDrones(messages);
            }
        });
    }
    
    private void updateDroneCount(int count) {
        if (activeDronesCount != null) {
            activeDronesCount.setText(count + " ACTIVE DRONES");
        }
    }

    private void updateUserLocationOnMap(Location location) {
        if (googleMap == null || location == null) return;

        LatLng userPosition = new LatLng(location.getLatitude(), location.getLongitude());

        if (userLocationMarker == null) {
            // Create user marker if it doesn't exist
            userLocationMarker = googleMap.addMarker(new MarkerOptions()
                    .position(userPosition)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .zIndex(1.0f)); // Keep user marker on top
        } else {
            // Update existing marker
            userLocationMarker.setPosition(userPosition);
        }

        // If user hasn't moved the map manually, keep user in view
        if (!userHasMovedMap) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(userPosition));
        }
    }

    private void updateDroneOnMap(CoTMessage message) {
        if (googleMap == null || message == null || message.getCoordinate() == null) {
            return;
        }

        // Clear previous markers
        googleMap.clear();

        LatLng position = new LatLng(
                message.getCoordinate().getLatitude(),
                message.getCoordinate().getLongitude()
        );

        // Add drone marker
        Marker marker = googleMap.addMarker(new MarkerOptions()
                .position(position)
                .title(message.getUid()));

        // Update flight path
        List<LatLng> path = flightPaths.getOrDefault(message.getUid(), new ArrayList<>());
        path.add(position);
        if (path.size() > 200) {
            path.remove(0);
        }
        flightPaths.put(message.getUid(), path);

        // Draw flight path - only show the drone's actual path
        googleMap.addPolyline(new PolylineOptions()
                .addAll(path)
                .color(Color.BLUE)
                .width(2f));

        // Add home position if available
        if (message.getHomeLat() != null && message.getHomeLon() != null &&
                !message.getHomeLat().equals("0.0") && !message.getHomeLon().equals("0.0")) {
            try {
                LatLng homePos = new LatLng(
                        Double.parseDouble(message.getHomeLat()),
                        Double.parseDouble(message.getHomeLon())
                );

                googleMap.addMarker(new MarkerOptions()
                        .position(homePos)
                        .title("Home")
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_YELLOW)));

                // Draw line between drone and home
                googleMap.addPolyline(new PolylineOptions()
                        .add(position, homePos)
                        .color(Color.YELLOW)
                        .width(2f));
            } catch (NumberFormatException e) {
                // Ignore parse errors
            }
        }

        // Add operator position if available
        if (message.getPilotLat() != null && message.getPilotLon() != null &&
                !message.getPilotLat().equals("0.0") && !message.getPilotLon().equals("0.0")) {
            try {
                LatLng operatorPos = new LatLng(
                        Double.parseDouble(message.getPilotLat()),
                        Double.parseDouble(message.getPilotLon())
                );

                googleMap.addMarker(new MarkerOptions()
                        .position(operatorPos)
                        .title("Operator")
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_GREEN)));

                // Draw line between drone and operator
                googleMap.addPolyline(new PolylineOptions()
                        .add(position, operatorPos)
                        .color(Color.GREEN)
                        .width(2f));
            } catch (NumberFormatException e) {
                // Ignore parse errors
            }
        }

        // In onboard mode, we still show the user's location marker but without connecting lines
        if (isOnboardMode && userLocationMarker != null) {
            // Update user marker position (but don't connect it to drone)
            // The drone distance info can still be added to the marker snippet if needed
            if (message.getRawMessage() != null &&
                    message.getRawMessage().containsKey("calculated_distance")) {
                float distance = (float) message.getRawMessage().get("calculated_distance");

                // Add distance to drone marker snippet
                String snippet = marker != null && marker.getSnippet() != null ?
                        marker.getSnippet() : "";
                if (marker != null) {
                    marker.setSnippet(snippet + (snippet.isEmpty() ? "" : "\n") +
                            "Distance: " + String.format("%.1f", distance) + "m");
                }
            }
        }
    }

    private void updateMarkerInfo(CoTMessage message, Marker marker) {
        StringBuilder snippet = new StringBuilder();

        // Add altitude if available
        if (message.getAlt() != null && !message.getAlt().isEmpty()) {
            try {
                double alt = Double.parseDouble(message.getAlt());
                snippet.append(String.format(Locale.US, "Alt: %.1fm", alt));
            } catch (NumberFormatException e) {
                // Skip if can't parse
            }
        }

        // Add height if available
        if (message.getHeight() != null && !message.getHeight().isEmpty()) {
            try {
                double height = Double.parseDouble(message.getHeight());
                if (snippet.length() > 0) snippet.append("\n");
                snippet.append(String.format(Locale.US, "Height: %.1fm", height));
            } catch (NumberFormatException e) {
                // Skip if can't parse
            }
        }

        // Add speed if available
        if (message.getSpeed() != null && !message.getSpeed().isEmpty()) {
            try {
                double speed = Double.parseDouble(message.getSpeed());
                if (snippet.length() > 0) snippet.append("\n");
                snippet.append(String.format(Locale.US, "Speed: %.1fm/s", speed));
            } catch (NumberFormatException e) {
                // Skip if can't parse
            }
        }

        // Add RSSI if available
        if (message.getRssi() != null) {
            if (snippet.length() > 0) snippet.append("\n");
            snippet.append(String.format(Locale.US, "RSSI: %ddBm", message.getRssi()));
        }

        // Add drone type if available
        if (message.getUaType() != null) {
            if (snippet.length() > 0) snippet.append("\n");
            snippet.append("Type: ").append(message.getUaType().name());
        }

        // Set the snippet
        marker.setSnippet(snippet.toString());
    }

    private void updateOperatorLocation(CoTMessage message) {
        if (message.getPilotLat() != null && message.getPilotLon() != null &&
                !message.getPilotLat().equals("0.0") && !message.getPilotLon().equals("0.0")) {
            try {
                LatLng operatorPos = new LatLng(
                        Double.parseDouble(message.getPilotLat()),
                        Double.parseDouble(message.getPilotLon())
                );

                // Use a unique ID for the operator marker
                String operatorMarkerId = message.getUid() + "_operator";
                Marker operatorMarker = operatorMarkers.get(operatorMarkerId);

                if (operatorMarker == null) {
                    operatorMarker = googleMap.addMarker(new MarkerOptions()
                            .position(operatorPos)
                            .title("Operator for " + message.getUid())
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                    operatorMarkers.put(operatorMarkerId, operatorMarker);
                } else {
                    operatorMarker.setPosition(operatorPos);
                }

                // Add info to operator marker
                operatorMarker.setSnippet("Operator for drone: " + message.getUid());
            } catch (NumberFormatException e) {
                // Handle parsing errors silently
            }
        }
    }

    private void updateHomeLocation(CoTMessage message) {
        if (message.getHomeLat() != null && message.getHomeLon() != null &&
                !message.getHomeLat().equals("0.0") && !message.getHomeLon().equals("0.0")) {
            try {
                LatLng homePos = new LatLng(
                        Double.parseDouble(message.getHomeLat()),
                        Double.parseDouble(message.getHomeLon())
                );

                // Use a unique ID for the home marker
                String homeMarkerId = message.getUid() + "_home";
                Marker homeMarker = homeMarkers.get(homeMarkerId);

                if (homeMarker == null) {
                    homeMarker = googleMap.addMarker(new MarkerOptions()
                            .position(homePos)
                            .title("Home for " + message.getUid())
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    homeMarkers.put(homeMarkerId, homeMarker);
                } else {
                    homeMarker.setPosition(homePos);
                }

                // Add info to home marker
                homeMarker.setSnippet("Home location for drone: " + message.getUid());
            } catch (NumberFormatException e) {
                // Handle parsing errors silently
            }
        }
    }

    private void drawConnections(CoTMessage message, LatLng dronePosition) {
        // Draw line between drone and operator
        if (message.getPilotLat() != null && message.getPilotLon() != null &&
                !message.getPilotLat().equals("0.0") && !message.getPilotLon().equals("0.0")) {
            try {
                LatLng operatorPos = new LatLng(
                        Double.parseDouble(message.getPilotLat()),
                        Double.parseDouble(message.getPilotLon())
                );

                googleMap.addPolyline(new PolylineOptions()
                        .add(dronePosition, operatorPos)
                        .color(Color.GREEN)
                        .width(2f)
                        .pattern(Arrays.asList(
                                new Dot(), new Gap(10f)))); // Dotted line for operator
            } catch (NumberFormatException e) {
                // Skip on parsing errors
            }
        }

        // Draw line between drone and home
        if (message.getHomeLat() != null && message.getHomeLon() != null &&
                !message.getHomeLat().equals("0.0") && !message.getHomeLon().equals("0.0")) {
            try {
                LatLng homePos = new LatLng(
                        Double.parseDouble(message.getHomeLat()),
                        Double.parseDouble(message.getHomeLon())
                );

                googleMap.addPolyline(new PolylineOptions()
                        .add(dronePosition, homePos)
                        .color(Color.YELLOW)
                        .width(2f)
                        .pattern(Arrays.asList(
                                new Dash(20f), new Gap(10f)))); // Dashed line for home
            } catch (NumberFormatException e) {
                // Skip on parsing errors
            }
        }
    }

    private void updateCameraToFitDrones(List<CoTMessage> messages) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasValidCoordinates = false;

        for (CoTMessage message : messages) {
            if (message.getCoordinate() != null) {
                builder.include(new LatLng(
                        message.getCoordinate().getLatitude(),
                        message.getCoordinate().getLongitude()
                ));
                hasValidCoordinates = true;
            }
        }

        if (hasValidCoordinates) {
            LatLngBounds bounds = builder.build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                    bounds,
                    100 // padding in pixels
            ));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mapView.onDestroy();
        // Clean up location manager
        if (isOnboardMode && locationManager != null) {
            locationManager.removeListener(locationListener);
        }
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}