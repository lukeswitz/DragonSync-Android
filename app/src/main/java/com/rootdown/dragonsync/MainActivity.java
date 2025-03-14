package com.rootdown.dragonsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.StatusMessage;
import com.rootdown.dragonsync.ui.fragments.DashboardFragment;
import com.rootdown.dragonsync.ui.fragments.DroneListFragment;
import com.rootdown.dragonsync.ui.fragments.HistoryFragment;
import com.rootdown.dragonsync.ui.fragments.SettingsFragment;
import com.rootdown.dragonsync.ui.fragments.StatusFragment;

import androidx.lifecycle.ViewModelProvider;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;
import com.rootdown.dragonsync.viewmodels.StatusViewModel;

public class MainActivity extends FragmentActivity {
    private CoTViewModel cotViewModel;
    private StatusViewModel statusViewModel;
    private FrameLayout fragmentContainer;
    private BottomNavigationView bottomNav;
    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cotViewModel = new ViewModelProvider(this).get(CoTViewModel.class);
        statusViewModel = new ViewModelProvider(this).get(StatusViewModel.class);

        // Initialize views and navigation
        initializeNavigation(savedInstanceState);

        // Request permissions needed for onboard detection
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        // For Android 12+ (SDK 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
            }, REQUEST_PERMISSIONS_CODE);
        } else {
            // For Android 6-11
            requestPermissions(new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
            }, REQUEST_PERMISSIONS_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.i("MainActivity", "All permissions granted for onboard detection");
            } else {
                Log.w("MainActivity", "Some permissions were denied for onboard detection");
                // Show a message to the user explaining why permissions are needed
                Toast.makeText(this, "Some features may be limited without required permissions",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeNavigation(Bundle savedInstanceState) {
        fragmentContainer = findViewById(R.id.fragment_container);
        bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);

        // Only set default fragment if this is the first creation
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_settings);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SettingsFragment())
                    .commit();
        }
    }

    private BroadcastReceiver telemetryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("parsed_message")) {
                CoTMessage message = intent.getParcelableExtra("parsed_message");
                if (message != null) {
                    cotViewModel.updateMessage(message);
                }
            }
        }
    };

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("status_message")) {
                StatusMessage message = intent.getParcelableExtra("status_message");
                if (message != null) {
                    statusViewModel.updateStatusMessage(message);
                }
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(telemetryReceiver,
                new IntentFilter("com.rootdown.dragonsync.TELEMETRY"),
                Context.RECEIVER_NOT_EXPORTED);

        registerReceiver(statusReceiver,
                new IntentFilter("com.rootdown.dragonsync.STATUS"),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(telemetryReceiver);
        unregisterReceiver(statusReceiver);
    }

    private boolean onNavigationItemSelected(MenuItem item) {
        Fragment fragment = null;
        Log.d("BottomNav", "Item selected: " + item.getTitle());
        int itemId = item.getItemId();
        if (itemId == R.id.nav_dashboard) {
            fragment = new DashboardFragment();
        } else if (itemId == R.id.nav_drones) {
            fragment = new DroneListFragment();
        } else if (itemId == R.id.nav_status) {
            fragment = new StatusFragment();
        } else if (itemId == R.id.nav_settings) {
            fragment = new SettingsFragment();
        } else if (itemId == R.id.nav_history) {
            fragment = new HistoryFragment();
        }

        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }
}
