package com.rootdown.dragonsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.rootdown.dragonsync.models.CoTMessage;
import com.rootdown.dragonsync.models.StatusMessage;
import com.rootdown.dragonsync.ui.fragments.DashboardFragment;
import com.rootdown.dragonsync.ui.fragments.DroneListFragment;
import com.rootdown.dragonsync.ui.fragments.HistoryFragment;
import com.rootdown.dragonsync.ui.fragments.SettingsFragment;
import com.rootdown.dragonsync.ui.fragments.StatusFragment;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.rootdown.dragonsync.utils.Constants;
import com.rootdown.dragonsync.utils.Settings;
import com.rootdown.dragonsync.viewmodels.CoTViewModel;
import com.rootdown.dragonsync.viewmodels.StatusViewModel;

public class MainActivity extends FragmentActivity {
    private CoTViewModel cotViewModel;
    private StatusViewModel statusViewModel;
    private FrameLayout fragmentContainer;
    private BottomNavigationView bottomNav;
    private MaterialButton connectionIndicator;
    private static final int REQUEST_PERMISSIONS_CODE = 1001;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable edge-to-edge display
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        getWindow().setStatusBarColor(getColor(android.R.color.transparent));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_main);

        cotViewModel = new ViewModelProvider(this).get(CoTViewModel.class);
        statusViewModel = new ViewModelProvider(this).get(StatusViewModel.class);

        // Initialize views and navigation
        initializeNavigation(savedInstanceState);

        // Request permissions needed for onboard detection
        checkAndRequestPermissions();

        setupConnectionIndicator();
    }


    private void setupConnectionIndicator() {
        // Initialize views
        connectionIndicator = findViewById(R.id.connection_indicator);

        // Initialize settings
        Settings settings = Settings.getInstance(this);

        // Initial state update
        updateConnectionIndicator(settings.isListening());

        // Setup a more robust preference change listener
        preferenceChangeListener = (sharedPreferences, key) -> {
            if (Constants.KEY_IS_LISTENING.equals(key)) {
                boolean isListening = sharedPreferences.getBoolean(key, false);
                runOnUiThread(() -> {
                    updateConnectionIndicator(isListening);
                });
            }
        };

        // Register the listener
        settings.registerPreferenceChangeListener(preferenceChangeListener);

        // Make the button clickable to open settings
        connectionIndicator.setOnClickListener(v -> {
            bottomNav.setSelectedItemId(R.id.nav_settings);
        });
    }

    private void updateConnectionIndicator(boolean isConnected) {
        if (connectionIndicator == null) return;

        if (isConnected) {
            connectionIndicator.setVisibility(View.GONE);
        } else {
            connectionIndicator.setVisibility(View.VISIBLE);

            // Get status bar height and adjust margin
            int statusBarHeight = 0;
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = getResources().getDimensionPixelSize(resourceId);
            }

            // Update the top margin to account for status bar
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) connectionIndicator.getLayoutParams();
            params.topMargin = statusBarHeight + 40; // 40dp base margin + status bar height
            connectionIndicator.setLayoutParams(params);

            int color = getResources().getColor(R.color.status_red, null);
            connectionIndicator.setText(getString(R.string.status_disconnected));
            ColorStateList colorStateList = ColorStateList.valueOf(color);
            connectionIndicator.setTextColor(color);
            connectionIndicator.setStrokeColor(colorStateList);
            connectionIndicator.setIconTint(colorStateList);
        }
    }

    private void checkAndRequestPermissions() {
        // For Android 12+ (SDK 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.CHANGE_WIFI_STATE
            }, REQUEST_PERMISSIONS_CODE);
        } else {
            // For Android 6-11
            requestPermissions(new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
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
        TextView headerTitle = findViewById(R.id.header_title);

        int itemId = item.getItemId();
        if (itemId == R.id.nav_dashboard) {
            fragment = new DashboardFragment();
            headerTitle.setVisibility(View.GONE); // Hide main title
        } else if (itemId == R.id.nav_drones) {
            fragment = new DroneListFragment();
            headerTitle.setVisibility(View.GONE);
        } else if (itemId == R.id.nav_status) {
            fragment = new StatusFragment();
            headerTitle.setVisibility(View.GONE);
        } else if (itemId == R.id.nav_settings) {
            fragment = new SettingsFragment();
            headerTitle.setVisibility(View.VISIBLE); // Show for settings since it doesn't have its own
        } else if (itemId == R.id.nav_history) {
            fragment = new HistoryFragment();
            headerTitle.setVisibility(View.GONE);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (preferenceChangeListener != null) {
            Settings.getInstance(this).unregisterPreferenceChangeListener(preferenceChangeListener);
        }
    }
}
