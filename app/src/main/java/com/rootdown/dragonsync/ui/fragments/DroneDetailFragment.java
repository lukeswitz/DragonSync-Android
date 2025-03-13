package com.rootdown.dragonsync.ui.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.card.MaterialCardView;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DroneDetailFragment extends Fragment implements OnMapReadyCallback {
	private CoTMessage message;
	private MapView mapView;
	private GoogleMap googleMap;
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

		mapView = view.findViewById(R.id.map_view);
		if (mapView != null) {
			mapView.onCreate(savedInstanceState);
			mapView.getMapAsync(this);
		}

		updateUI(view);

		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

        updateUI(view);
        setupTechnicalDetailsSection(view);

        // Set up real-time updates for the currently displayed drone
		viewModel.getParsedMessages().observe(getViewLifecycleOwner(), messages -> {
			if (message == null || message.getUid() == null) return;

			// Find the updated message for our drone
			for (CoTMessage updatedMessage : messages) {
				if (updatedMessage.getUid() != null &&
						updatedMessage.getUid().equals(message.getUid())) {
					// Update our reference to the message
					message = updatedMessage;

					// Refresh the UI with new data
					updateUI(view);

					// Update map if available
					if (googleMap != null) {
						updateMap();
					}

					break;
				}
			}
		});
	}

	private void updateUI(View view) {
		if (message == null) return;

		// Basic drone info
		TextView droneId = view.findViewById(R.id.drone_id);
		TextView manufacturer = view.findViewById(R.id.manufacturer);
		TextView description = view.findViewById(R.id.description);
		TextView lastSeen = view.findViewById(R.id.last_seen);
		TextView macAddress = view.findViewById(R.id.mac_address);
		TextView rssi = view.findViewById(R.id.rssi_value);

		// Set values
		// Drone ID
		droneId.setText(message.getUid());

		// Manufacturer (if available)
		if (message.getManufacturer() != null && !message.getManufacturer().isEmpty()) {
			manufacturer.setText(message.getManufacturer());
			manufacturer.setVisibility(View.VISIBLE);
		} else {
			manufacturer.setVisibility(View.GONE);
		}

		// Description
		if (message.getDescription() != null && !message.getDescription().isEmpty()) {
			description.setText(message.getDescription());
			description.setVisibility(View.VISIBLE);
		} else {
			description.setVisibility(View.GONE);
		}

		// Last seen (timestamp)
		if (message.getTimestamp() != null && !message.getTimestamp().isEmpty()) {
			try {
				long timestamp = Long.parseLong(message.getTimestamp());
				Date date = new Date(timestamp);
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
				lastSeen.setText(sdf.format(date));
			} catch (NumberFormatException e) {
				lastSeen.setText("Unknown");
			}
		} else {
			lastSeen.setText("Unknown");
		}

		// MAC Address
		if (message.getMac() != null && !message.getMac().isEmpty()) {
			macAddress.setText(message.getMac());
			macAddress.setVisibility(View.VISIBLE);
		} else {
			macAddress.setVisibility(View.GONE);
		}

		// RSSI
		if (message.getRssi() != null) {
			rssi.setText(String.format(Locale.US, "%d dBm", message.getRssi()));

			// Set color based on signal strength
			if (message.getRssi() > -60) {
				rssi.setTextColor(Color.GREEN);
			} else if (message.getRssi() > -80) {
				rssi.setTextColor(Color.YELLOW);
			} else {
				rssi.setTextColor(Color.RED);
			}
		} else {
			rssi.setText("Unknown");
		}

		// Position Information
		setupPositionInfo(view);

		// MAC Randomization Warning
		setupMacRandomizationWarning(view);

		// Signal Sources
		setupSignalSourcesInfo(view);

		// Spoofing Warning
		setupSpoofingWarning(view);
	}

	private void setupPositionInfo(View view) {
		TextView position = view.findViewById(R.id.position);
		TextView altitude = view.findViewById(R.id.altitude);
		TextView speed = view.findViewById(R.id.speed);

		if (message.getCoordinate() != null) {
			position.setText(String.format(Locale.US, "Lat: %.6f, Lon: %.6f",
					message.getCoordinate().getLatitude(),
					message.getCoordinate().getLongitude()));
		} else {
			position.setText("Position unknown");
		}

		// Altitude
		if (message.getAlt() != null && !message.getAlt().isEmpty()) {
			try {
				double altValue = Double.parseDouble(message.getAlt());
				altitude.setText(String.format(Locale.US, "Altitude: %.1f m MSL", altValue));
				altitude.setVisibility(View.VISIBLE);
			} catch (NumberFormatException e) {
				altitude.setVisibility(View.GONE);
			}
		} else {
			altitude.setVisibility(View.GONE);
		}

		// Speed
		if (message.getSpeed() != null && !message.getSpeed().isEmpty()) {
			try {
				double speedValue = Double.parseDouble(message.getSpeed());
				speed.setText(String.format(Locale.US, "Speed: %.1f m/s", speedValue));
				speed.setVisibility(View.VISIBLE);
			} catch (NumberFormatException e) {
				speed.setVisibility(View.GONE);
			}
		} else {
			speed.setVisibility(View.GONE);
		}
	}

    private void setupTechnicalDetailsSection(View view) {
        LinearLayout technicalDetailsContainer = view.findViewById(R.id.technical_details_container);
        technicalDetailsContainer.removeAllViews();

        if (message == null) return;

        // Create categories of technical details
        addDetailCategory(technicalDetailsContainer, "Basic Information");

        // Add basic details
        if (message.getUid() != null) {
            addDetailRow(technicalDetailsContainer, "Drone ID", message.getUid());
        }

        if (message.getIdType() != null) {
            addDetailRow(technicalDetailsContainer, "ID Type", message.getIdType());
        }

        if (message.getUaType() != null) {
            addDetailRow(technicalDetailsContainer, "UA Type", message.getUaType().name());
        }

        if (message.getMac() != null) {
            addDetailRow(technicalDetailsContainer, "MAC Address", message.getMac());
        }

        if (message.getManufacturer() != null) {
            addDetailRow(technicalDetailsContainer, "Manufacturer", message.getManufacturer());
        }

        // Location category
        addDetailCategory(technicalDetailsContainer, "Location Data");

        if (message.getLat() != null && message.getLon() != null) {
            addDetailRow(technicalDetailsContainer, "Coordinates",
                    String.format("Lat: %s, Lon: %s", message.getLat(), message.getLon()));
        }

        if (message.getAlt() != null) {
            addDetailRow(technicalDetailsContainer, "Altitude", message.getAlt() + " m");
        }

        if (message.getHeight() != null) {
            addDetailRow(technicalDetailsContainer, "Height AGL", message.getHeight() + " m");
        }

        if (message.getDirection() != null) {
            addDetailRow(technicalDetailsContainer, "Direction", message.getDirection() + "°");
        }

        if (message.getSpeed() != null) {
            addDetailRow(technicalDetailsContainer, "Speed", message.getSpeed() + " m/s");
        }

        if (message.getVspeed() != null) {
            addDetailRow(technicalDetailsContainer, "Vertical Speed", message.getVspeed() + " m/s");
        }

        // Operator/Home location category
        addDetailCategory(technicalDetailsContainer, "Operator & Home Data");

        if (message.getPilotLat() != null && message.getPilotLon() != null) {
            addDetailRow(technicalDetailsContainer, "Operator Location",
                    String.format("Lat: %s, Lon: %s", message.getPilotLat(), message.getPilotLon()));
        }

        if (message.getHomeLat() != null && message.getHomeLon() != null) {
            addDetailRow(technicalDetailsContainer, "Home Location",
                    String.format("Lat: %s, Lon: %s", message.getHomeLat(), message.getHomeLon()));
        }

        // Additional data from newer formats
        if (message.getOpStatus() != null) {
            addDetailRow(technicalDetailsContainer, "Operation Status", message.getOpStatus());
        }

        if (message.getHeightType() != null) {
            addDetailRow(technicalDetailsContainer, "Height Type", message.getHeightType());
        }

        if (message.getEwDirSegment() != null) {
            addDetailRow(technicalDetailsContainer, "Direction Segment", message.getEwDirSegment());
        }

        if (message.getSpeedMultiplier() != null) {
            addDetailRow(technicalDetailsContainer, "Speed Multiplier", message.getSpeedMultiplier());
        }

        // Signal data category
        addDetailCategory(technicalDetailsContainer, "Signal Information");

        if (message.getRssi() != null) {
            addDetailRow(technicalDetailsContainer, "RSSI", message.getRssi() + " dBm");
        }

        // Signal sources
        if (message.getSignalSources() != null && !message.getSignalSources().isEmpty()) {
            for (int i = 0; i < message.getSignalSources().size(); i++) {
                CoTMessage.SignalSource source = message.getSignalSources().get(i);
                addDetailRow(technicalDetailsContainer, "Signal " + (i+1),
                        String.format("%s: %s (%d dBm)",
                                source.getType().getDisplayName(),
                                source.getMac(),
                                source.getRssi()));
            }
        }

        // Accuracy data category
        addDetailCategory(technicalDetailsContainer, "Accuracy Information");

        if (message.getHorizontalAccuracy() != null) {
            addDetailRow(technicalDetailsContainer, "Horizontal Accuracy", message.getHorizontalAccuracy() + " m");
        }

        if (message.getVerticalAccuracy() != null) {
            addDetailRow(technicalDetailsContainer, "Vertical Accuracy", message.getVerticalAccuracy() + " m");
        }

        if (message.getBaroAccuracy() != null) {
            addDetailRow(technicalDetailsContainer, "Barometric Accuracy", message.getBaroAccuracy() + " m");
        }

        if (message.getSpeedAccuracy() != null) {
            addDetailRow(technicalDetailsContainer, "Speed Accuracy", message.getSpeedAccuracy() + " m/s");
        }

        // System data category
        if (message.getAreaCount() != null || message.getAreaRadius() != null ||
                message.getAreaCeiling() != null || message.getAreaFloor() != null) {

            addDetailCategory(technicalDetailsContainer, "System Information");

            if (message.getAreaCount() != null) {
                addDetailRow(technicalDetailsContainer, "Area Count", message.getAreaCount());
            }

            if (message.getAreaRadius() != null) {
                addDetailRow(technicalDetailsContainer, "Area Radius", message.getAreaRadius() + " m");
            }

            if (message.getAreaCeiling() != null) {
                addDetailRow(technicalDetailsContainer, "Area Ceiling", message.getAreaCeiling() + " m");
            }

            if (message.getAreaFloor() != null) {
                addDetailRow(technicalDetailsContainer, "Area Floor", message.getAreaFloor() + " m");
            }
        }

        // Authentication data
        if (message.getAuthType() != null || message.getAuthPage() != null ||
                message.getAuthLength() != null || message.getAuthTimestamp() != null) {

            addDetailCategory(technicalDetailsContainer, "Authentication Data");

            if (message.getAuthType() != null) {
                addDetailRow(technicalDetailsContainer, "Auth Type", message.getAuthType());
            }

            if (message.getAuthPage() != null) {
                addDetailRow(technicalDetailsContainer, "Auth Page", message.getAuthPage());
            }

            if (message.getAuthLength() != null) {
                addDetailRow(technicalDetailsContainer, "Auth Length", message.getAuthLength());
            }

            if (message.getAuthTimestamp() != null) {
                addDetailRow(technicalDetailsContainer, "Auth Timestamp", message.getAuthTimestamp());
            }
        }

        // Raw message data if available
        if (message.getRawMessage() != null && !message.getRawMessage().isEmpty()) {
            addDetailCategory(technicalDetailsContainer, "Raw Message Data");

            // Show some key fields from raw data
            if (message.getIndex() != null) {
                addDetailRow(technicalDetailsContainer, "Message Index", message.getIndex());
            }

            if (message.getRuntime() != null) {
                addDetailRow(technicalDetailsContainer, "Runtime", message.getRuntime() + " ms");
            }
        }
    }

    // Helper methods to create the UI elements
    private void addDetailCategory(ViewGroup container, String title) {
        TextView categoryTitle = new TextView(requireContext());
        categoryTitle.setText(title);
        categoryTitle.setTextAppearance(android.R.style.TextAppearance_Medium);
        categoryTitle.setTypeface(null, Typeface.BOLD);
        categoryTitle.setPadding(0, 16, 0, 8);

        container.addView(categoryTitle);

        // Add a divider
        View divider = new View(requireContext());
        divider.setBackgroundColor(Color.LTGRAY);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(params);

        container.addView(divider);
    }

    private void addDetailRow(ViewGroup container, String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        TextView labelView = new TextView(requireContext());
        labelView.setText(label + ":");
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));

        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));

        row.addView(labelView);
        row.addView(valueView);

        container.addView(row);

        // Add a light divider
        View divider = new View(requireContext());
        divider.setBackgroundColor(Color.parseColor("#20000000")); // Semi-transparent divider
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(params);

        container.addView(divider);
    }

	private void setupMacRandomizationWarning(View view) {
		MaterialCardView macWarning = view.findViewById(R.id.mac_warning_card);
		TextView macList = view.findViewById(R.id.mac_list);

		if (viewModel != null && viewModel.getMacHistory().getValue() != null) {
			String uid = message.getUid();
			Map<String, Set<String>> macHistory = viewModel.getMacHistory().getValue();

			if (macHistory.containsKey(uid)) {
				Set<String> macs = macHistory.get(uid);
				if (macs != null && macs.size() > 1) {
					macWarning.setVisibility(View.VISIBLE);

					StringBuilder sb = new StringBuilder();
					for (String mac : macs) {
						sb.append(mac).append("\n");
					}
					macList.setText(sb.toString().trim());
				} else {
					macWarning.setVisibility(View.GONE);
				}
			} else {
				macWarning.setVisibility(View.GONE);
			}
		} else {
			macWarning.setVisibility(View.GONE);
		}
	}

	private void setupSignalSourcesInfo(View view) {
		TextView signalSources = view.findViewById(R.id.signal_sources);
		View signalChart = view.findViewById(R.id.signal_chart);

		// Hide the placeholder chart
		signalChart.setVisibility(View.GONE);

		if (message.getSignalSources() != null && !message.getSignalSources().isEmpty()) {
			StringBuilder sb = new StringBuilder();

			for (CoTMessage.SignalSource source : message.getSignalSources()) {
				String typeLabel = "UNKNOWN";
				String typeIcon = "🔍";

				// Set appropriate icon and label based on signal type
				switch (source.getType()) {
					case BLUETOOTH:
						typeLabel = "BLUETOOTH";
						typeIcon = "\uD83D\uDD37";
						break;
					case WIFI:
						typeLabel = "WIFI";
						typeIcon = "\uD83D\uDCF6";
						break;
					case SDR:
						typeLabel = "SDR";
						typeIcon = "\uD83D\uDCE1";
						break;
					default:
						typeLabel = "UNKNOWN";
						break;
				}

				// Format: Icon Type: MAC (RSSI dBm) Timestamp
				sb.append(typeIcon)
						.append(" ")
						.append(typeLabel)
						.append(": ")
						.append(source.getMac())
						.append(" (")
						.append(source.getRssi())
						.append(" dBm)");

				// Add timestamp if available
				long timestamp = source.getTimestamp();
				if (timestamp > 0) {
					try {
						Date date = new Date(timestamp);
						SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
						sb.append(" at ").append(sdf.format(date));
					} catch (Exception e) {
						// Ignore timestamp formatting errors
					}
				}

				sb.append("\n");
			}

			signalSources.setText(sb.toString().trim());
		} else if (message.getRssi() != null && message.getMac() != null) {
			// If no signal sources array but we have RSSI and MAC, show that
			String sourceName = "UNKNOWN";
			String sourceIcon = "🔍";

			// No MAC means its decoded via SDR
			if (message.getMac() == null) {
					sourceName = "SDR";
					sourceIcon = "\uD83D\uDCE1";
			}

			signalSources.setText(String.format("%s %s: %s (%d dBm)",
					sourceIcon, sourceName, message.getMac(), message.getRssi()));
		} else {
			signalSources.setText("No signal source information available");
		}
	}

	private void setupSpoofingWarning(View view) {
		MaterialCardView spoofWarning = view.findViewById(R.id.spoof_warning_card);

		if (message.isSpoofed()) {
			spoofWarning.setVisibility(View.VISIBLE);

			TextView spoofReason = view.findViewById(R.id.spoof_reason);
			if (message.getSpoofingDetails() != null && message.getSpoofingDetails().getReason() != null) {
				spoofReason.setText(message.getSpoofingDetails().getReason());
			} else {
				StringBuilder reasonBuilder = new StringBuilder("Possible spoofing detected: ");

				// Check for unrealistic movements
				if (message.getCoordinate() != null) {
					reasonBuilder.append("Unusual movement pattern or unrealistic speed detected. ");
				}

				// If we see multiple MAC addresses, mention that weirdness
				if (viewModel != null && viewModel.getMacHistory().getValue() != null) {
					Map<String, Set<String>> macHistory = viewModel.getMacHistory().getValue();
					if (macHistory.containsKey(message.getUid())) {
						Set<String> macs = macHistory.get(message.getUid());
						if (macs != null && macs.size() > 2) {
							reasonBuilder.append("Multiple MAC addresses observed (").append(macs.size()).append("). ");
						}
					}
				}
				spoofReason.setText(reasonBuilder.toString());
			}
		} else {
			spoofWarning.setVisibility(View.GONE);
		}
	}

    private void updateMap() {
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
        googleMap.addMarker(new MarkerOptions()
                .position(position)
                .title("Drone: " + message.getUid())
                .icon(BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_send)));


        // Add home position if available
        if (message.getHomeLat() != null && message.getHomeLon() != null &&
                !message.getHomeLat().equals("0.0") && !message.getHomeLon().equals("0.0")) {
            try {
                double homeLat = Double.parseDouble(message.getHomeLat());
                double homeLon = Double.parseDouble(message.getHomeLon());

                LatLng homePos = new LatLng(homeLat, homeLon);

				googleMap.addMarker(new MarkerOptions()
						.position(homePos)
						.title("Home")
						.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
						.anchor(0.5f, 0.5f)
						.alpha(0.9f)
						.zIndex(1.0f));

                // Draw line between drone and home
                googleMap.addPolyline(new PolylineOptions()
                        .add(position, homePos)
                        .color(Color.YELLOW)
                        .width(2f)
                        .pattern(Arrays.asList(new Dash(20f), new Gap(10f)))); // Dashed line
            } catch (NumberFormatException e) {
                // Ignore parse errors
                Log.d("DroneDetailFragment", "Error parsing home coordinates: " + e.getMessage());
            }
        }

        // Add operator position if available
        if (message.getPilotLat() != null && message.getPilotLon() != null &&
                !message.getPilotLat().equals("0.0") && !message.getPilotLon().equals("0.0")) {
            try {
                double pilotLat = Double.parseDouble(message.getPilotLat());
                double pilotLon = Double.parseDouble(message.getPilotLon());

                LatLng operatorPos = new LatLng(pilotLat, pilotLon);

				googleMap.addMarker(new MarkerOptions()
						.position(operatorPos)
						.title("Operator")
						.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
						.anchor(0.5f, 0.5f)
						.alpha(0.9f)
						.zIndex(2.0f));

                // Draw line between drone and operator
                googleMap.addPolyline(new PolylineOptions()
                        .add(position, operatorPos)
                        .color(Color.GREEN)
                        .width(2f)
                        .pattern(Arrays.asList(new Dot(), new Gap(10f)))); // Dotted line
            } catch (NumberFormatException e) {
                // Ignore parse errors
                Log.d("DroneDetailFragment", "Error parsing operator coordinates: " + e.getMessage());
            }
        }

        // Adjust camera bounds to show all points
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boundsBuilder.include(position); // Always include drone position

        boolean hasAdditionalPoints = false;

        // Add home location to bounds if available
        if (message.getHomeLat() != null && message.getHomeLon() != null &&
                !message.getHomeLat().equals("0.0") && !message.getHomeLon().equals("0.0")) {
            try {
                double homeLat = Double.parseDouble(message.getHomeLat());
                double homeLon = Double.parseDouble(message.getHomeLon());
                boundsBuilder.include(new LatLng(homeLat, homeLon));
                hasAdditionalPoints = true;
            } catch (NumberFormatException e) {
                // Ignore parse errors
            }
        }

        // Add operator location to bounds if available
        if (message.getPilotLat() != null && message.getPilotLon() != null &&
                !message.getPilotLat().equals("0.0") && !message.getPilotLon().equals("0.0")) {
            try {
                double pilotLat = Double.parseDouble(message.getPilotLat());
                double pilotLon = Double.parseDouble(message.getPilotLon());
                boundsBuilder.include(new LatLng(pilotLat, pilotLon));
                hasAdditionalPoints = true;
            } catch (NumberFormatException e) {
                // Ignore parse errors
            }
        }

        // Move camera to fit all points
        try {
            if (hasAdditionalPoints) {
                // Fit all of them
                LatLngBounds bounds = boundsBuilder.build();
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 30));
            } else {
                // If we only have the drone position, use a fixed zoom level
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
            }
        } catch (Exception e) {
            // Fallback if building bounds fails
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
            Log.e("DroneDetailFragment", "Error adjusting camera: " + e.getMessage());
        }
    }

	@Override
	public void onMapReady(GoogleMap map) {
		googleMap = map;
		googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

		if (message != null && message.getCoordinate() != null) {
			LatLng position = new LatLng(
					message.getCoordinate().getLatitude(),
					message.getCoordinate().getLongitude()
			);

			// Initial setup of markers
			updateMap();

			// Move camera to drone position with zoom
			googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
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