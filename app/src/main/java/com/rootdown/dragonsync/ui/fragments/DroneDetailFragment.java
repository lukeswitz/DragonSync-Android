package com.rootdown.dragonsync.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;
import java.util.List;

public class DroneDetailFragment extends Fragment {
    private CoTMessage message;
    private MapView mapView;
    private List<LatLng> flightPath;
    private CoTViewModel viewModel;

    public static DroneDetailFragment newInstance(CoTMessage message) {
        DroneDetailFragment fragment = new DroneDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable("message", message);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(CoTViewModel.class);
        if (getArguments() != null) {
            message = getArguments().getParcelable("message");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drone_detail, container, false);
//
//        mapView = view.findViewById(R.id.map_view);
//        mapView.onCreate(savedInstanceState);
//
//        initializeViews(view);
//        setupMapAndFlightPath(savedInstanceState);

        return view;
    }

    private void initializeViews(View view) {
        // Basic Info Section
        TextView droneId = view.findViewById(R.id.drone_id);
        TextView description = view.findViewById(R.id.description);
        TextView rssi = view.findViewById(R.id.rssi);

//        droneId.setText(message.getUid());
//        if (!message.getDescription().isEmpty()) {
//            description.setText(message.getDescription());
//        }
//
//        if (message.getRssi() != null) {
//            rssi.setText(String.format("%d dBm", message.getRssi()));
//            rssi.setTextColor(getRssiColor(message.getRssi()));
//        }
//
//        // Signal Sources Section
//        if (!message.getSignalSources().isEmpty()) {
//            setupSignalSourcesView(view.findViewById(R.id.signal_sources_container));
//        }
//
//        // MAC Randomization Warning
//        if (viewModel.getMacHistory().getValue().containsKey(message.getUid())) {
//            Set<String> macs = viewModel.getMacHistory().getValue().get(message.getUid());
//            if (macs != null && macs.size() > 1) {
//                setupMacRandomizationWarning(view.findViewById(R.id.mac_warning_container), macs);
//            }
//        }
//
//        // Position Info
//        setupPositionInfo(view);
//
//        // Operator Info if available
//        if (hasOperatorInfo()) {
//            setupOperatorInfo(view);
//        }
//
//        // Movement Info
//        setupMovementInfo(view);
//
//        // Signal Data Section
//        setupSignalData(view);

        // Setup Export/Share button
//        view.findViewById(R.id.export_button).setOnClickListener(v -> exportData());
    }

    private void setupMapAndFlightPath(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(googleMap -> {
            googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

            // Add drone marker
            if (message.getCoordinate() != null) {
                LatLng position = new LatLng(
                        message.getCoordinate().getLatitude(),
                        message.getCoordinate().getLongitude()
                );

//                googleMap.addMarker(new MarkerOptions()
//                        .position(position)
//                        .title(message.getUid()));

                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
            }

            // Add flight path if available
            if (flightPath != null && !flightPath.isEmpty()) {
                googleMap.addPolyline(new PolylineOptions()
                        .addAll(flightPath)
                        .color(Color.BLUE)
                        .width(2f));
            }

            // Add operator location if available
//            if (message.getPilotLat() != "0.0" && message.getPilotLon() != "0.0") {
//                LatLng operatorPos = new LatLng(
//                        Double.parseDouble(message.getPilotLat()),
//                        Double.parseDouble(message.getPilotLon())
//                );
//
//                googleMap.addMarker(new MarkerOptions()
//                        .position(operatorPos)
//                        .title("Operator")
//                        .icon(BitmapDescriptorFactory.defaultMarker(
//                                BitmapDescriptorFactory.HUE_GREEN)));
//            }
        });
    }

    // Continue with helper methods for each section...
}