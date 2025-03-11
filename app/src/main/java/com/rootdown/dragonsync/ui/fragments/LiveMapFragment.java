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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;

import android.location.Location;
import android.util.Log;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveMapFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "LiveMapFragment";

    private MapView mapView;
    private GoogleMap googleMap;
    private CoTViewModel viewModel;
    private Map<String, Marker> droneMarkers = new HashMap<>();
    private Map<String, Marker> operatorMarkers = new HashMap<>();
    private Map<String, Marker> homeMarkers = new HashMap<>();
    private Map<String, List<LatLng>> flightPaths = new HashMap<>();
    private boolean userHasMovedMap = false;
    private CoTMessage initialMessage;

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
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_live_map, container, false);

        mapView = view.findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
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

        setupMapSettings();
        setupMapGestures();
        observeDrones();

        if (initialMessage != null && initialMessage.getCoordinate() != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(
                            initialMessage.getCoordinate().getLatitude(),
                            initialMessage.getCoordinate().getLongitude()
                    ),
                    15f
            ));
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
            for (CoTMessage message : messages) {
                updateDroneOnMap(message);
            }

            if (!userHasMovedMap && !messages.isEmpty()) {
                updateCameraToFitDrones(messages);
            }
        });
    }

    private void updateDroneOnMap(CoTMessage message) {
        if (message.getCoordinate() == null) return;

        LatLng position = new LatLng(
                message.getCoordinate().getLatitude(),
                message.getCoordinate().getLongitude()
        );

        // Update flight path
        List<LatLng> path = flightPaths.getOrDefault(message.getUid(), new ArrayList<>());
        path.add(position);
        if (path.size() > 200) {
            path.remove(0);
        }
        flightPaths.put(message.getUid(), path);

        // Draw flight path
        googleMap.addPolyline(new PolylineOptions()
                .addAll(path)
                .color(Color.BLUE)
                .width(2f));

        // Update marker
        Marker marker = droneMarkers.get(message.getUid());
        if (marker == null) {
            marker = googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(message.getUid()));
            droneMarkers.put(message.getUid(), marker);
        } else {
            marker.setPosition(position);
        }

        // Update marker info
        String snippet = String.format("Alt: %.1fm\nSpeed: %.1fm/s",
                Double.parseDouble(message.getAlt()),
                Double.parseDouble(message.getSpeed()));
        marker.setSnippet(snippet);

        // Add operator location if available
        if (message.getPilotLat() != null && message.getPilotLon() != null &&
                !message.getPilotLat().equals("0.0") && !message.getPilotLon().equals("0.0")) {

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
        }

        // Add home location if available (for DJI drones)
        if (message.getHomeLat() != null && message.getHomeLon() != null &&
                !message.getHomeLat().equals("0.0") && !message.getHomeLon().equals("0.0")) {

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
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}