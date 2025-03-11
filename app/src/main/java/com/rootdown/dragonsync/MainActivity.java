package com.rootdown.dragonsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.util.Log;
import android.view.MenuItem;
import android.widget.FrameLayout;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cotViewModel = new ViewModelProvider(this).get(CoTViewModel.class);
        statusViewModel = new ViewModelProvider(this).get(StatusViewModel.class);

        // Initialize views and navigation
        initializeNavigation(savedInstanceState);
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
