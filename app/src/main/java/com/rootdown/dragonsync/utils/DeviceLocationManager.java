package com.rootdown.dragonsync.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;
import java.util.List;

public class DeviceLocationManager {
    private static final String TAG = "DeviceLocationManager";
    private static DeviceLocationManager instance;

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private final LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private boolean isTrackingLocation = false;
    private final List<LocationUpdateListener> listeners = new ArrayList<>();

    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
    }

    private DeviceLocationManager(Context context) {
        this.context = context.getApplicationContext();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // Setup location request
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateDistanceMeters(5)
                .build();

        setupLocationCallback();
    }

    public static synchronized DeviceLocationManager getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceLocationManager(context);
        }
        return instance;
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    currentLocation = location;
                    notifyListeners(location);
                    Log.d(TAG, "Location update: " + location.getLatitude() + ", " + location.getLongitude());
                }
            }
        };
    }

    public void startLocationUpdates() {
        if (isTrackingLocation) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );

        isTrackingLocation = true;
        Log.d(TAG, "Started location tracking");
    }

    public void stopLocationUpdates() {
        if (!isTrackingLocation) return;

        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTrackingLocation = false;
        Log.d(TAG, "Stopped location tracking");
    }

    public Location getCurrentLocation() {
        // Try to get last known location if we don't have a current one
        if (currentLocation == null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLocation = location;
                        notifyListeners(location);
                    }
                });
            }
        }
        return currentLocation;
    }

    public void addListener(LocationUpdateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(LocationUpdateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(Location location) {
        for (LocationUpdateListener listener : listeners) {
            listener.onLocationUpdated(location);
        }
    }

    public boolean isTrackingLocation() {
        return isTrackingLocation;
    }
}