package com.rootdown.dragonsync.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.ConnectionMode;
import com.rootdown.dragonsync.network.NetworkService;
import com.rootdown.dragonsync.network.OnboardDetectionService;
import com.rootdown.dragonsync.utils.Settings;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";
    private Settings settings;
    private TabLayout connectionModeTabs;
    private EditText hostInput;
    private SwitchMaterial connectionSwitch;
    private TextView connectionStatus;
    private SwitchMaterial notificationsSwitch;
    private SwitchMaterial spoofDetectionSwitch;
    private SwitchMaterial screenOnSwitch;
    private SwitchMaterial serialConsoleSwitch;
    private SwitchMaterial systemWarningsSwitch;
    private ViewGroup thresholdsContainer;
    private TextInputLayout hostInputLayout;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isConnecting = false;
    private static boolean appFirstLaunch = true;
    private ConnectionMode currentMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = Settings.getInstance(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        initializeViews(view);
        setupConnectionModes();
        loadCurrentSettings();
        setupListeners();

        // Only force disconnect on first app launch
        if (appFirstLaunch) {
            connectionSwitch.setChecked(false);
            settings.setListening(false);
            updateConnectionStatusUI(false);
            appFirstLaunch = false;
        } else {
            // Just update UI to match current state
            connectionSwitch.setChecked(settings.isListening());
            updateConnectionStatusUI(settings.isListening());
        }

        updateConnectionStatusUI(settings.isListening());
        return view;
    }

    private BroadcastReceiver connectionErrorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("error_message")) {
                String errorMessage = intent.getStringExtra("error_message");

                // Update the UI to show disconnected state
                isConnecting = false;
                connectionSwitch.setChecked(false);
                updateConnectionStatusUI(false);

                // Show an error message to the user
                Toast.makeText(requireContext(),
                        getString(R.string.connection_error),
                        Toast.LENGTH_LONG).show();
            }
        }
    };


    @Override
    public void onResume() {
        super.onResume();

        // Register for connection error broadcasts
        requireContext().registerReceiver(connectionErrorReceiver,
                new IntentFilter("com.rootdown.dragonsync.CONNECTION_ERROR"),
                Context.RECEIVER_NOT_EXPORTED);

        // Check the actual connection state and update UI accordingly
        boolean isListening = settings.isListening();
        connectionSwitch.setChecked(isListening);
        updateConnectionStatusUI(isListening);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister receiver
        try {
            requireContext().unregisterReceiver(connectionErrorReceiver);
        } catch (IllegalArgumentException e) {
            // ignore it
        }
    }

    private void initializeViews(View view) {
        connectionModeTabs = view.findViewById(R.id.connection_mode_tabs);
        hostInput = view.findViewById(R.id.host_input);
        connectionSwitch = view.findViewById(R.id.connection_switch);
        hostInputLayout = view.findViewById(R.id.host_input_layout);
        // Create status TextView programmatically
        connectionStatus = new TextView(requireContext());
        connectionStatus.setText("Disconnected");
        connectionStatus.setTextColor(getResources().getColor(R.color.red, null));
        SwitchMaterial locationEstimationSwitch = view.findViewById(R.id.location_estimation_switch); // TODO decide if we need this

        // Add it to the parent layout right after the switch
        ViewGroup parent = (ViewGroup) connectionSwitch.getParent();
        parent.addView(connectionStatus, parent.indexOfChild(connectionSwitch) + 1);

        // Bind view stuff
        notificationsSwitch = view.findViewById(R.id.notifications_switch);
        spoofDetectionSwitch = view.findViewById(R.id.spoof_detection_switch);
        screenOnSwitch = view.findViewById(R.id.screen_on_switch);
        serialConsoleSwitch = view.findViewById(R.id.serial_console_switch);
        systemWarningsSwitch = view.findViewById(R.id.system_warnings_switch);
        thresholdsContainer = view.findViewById(R.id.thresholds_container);
    }

    private void setupConnectionModes() {
        // Clear existing tabs first
        connectionModeTabs.removeAllTabs();

        for (ConnectionMode mode : ConnectionMode.values()) {
            TabLayout.Tab tab = connectionModeTabs.newTab();
            tab.setText(mode.getDisplayName());
            tab.setIcon(getResources().getDrawable(mode.getIcon(), null));
            tab.setContentDescription("Connection mode: " + mode.getDisplayName());
            connectionModeTabs.addTab(tab);
        }

        // Set current mode
        currentMode = settings.getConnectionMode();
        connectionModeTabs.selectTab(connectionModeTabs.getTabAt(currentMode.ordinal()));

        // Update UI based on selected mode
        updateUIForConnectionMode(currentMode);
    }

    private void updateUIForConnectionMode(ConnectionMode mode) {
        if (mode == ConnectionMode.ONBOARD) {
            // Hide the host input since it's not needed for onboard mode
            hostInputLayout.setVisibility(View.GONE);

            // Check if there's an existing info text
            TextView infoText = new TextView(requireContext());
            infoText.setTag("onboard_info_text");
            infoText.setText("Uses your device's Bluetooth and WiFi " +
                    "to detect nearby drones without external hardware.");
            infoText.setPadding(16, 16, 16, 16);

            ViewGroup parent = (ViewGroup) hostInputLayout.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof TextView && "onboard_info_text".equals(child.getTag())) {
                    parent.removeView(child);
                    break;
                }
            }
            parent.addView(infoText, parent.indexOfChild(hostInputLayout));

            // Show current connection status
            updateConnectionStatusUI(settings.isListening());
        } else {
            // Other modes - keep existing code
            hostInputLayout.setVisibility(View.VISIBLE);
            connectionSwitch.setEnabled(true);

            ViewGroup parent = (ViewGroup) hostInputLayout.getParent();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof TextView && "onboard_info_text".equals(child.getTag())) {
                    parent.removeView(child);
                    break;
                }
            }

            // Update connection status
            updateConnectionStatusUI(connectionSwitch.isChecked(), false);
        }
        connectionSwitch.setVisibility(View.VISIBLE);
    }

    private void loadCurrentSettings() {
        // Connection settings
        updateHostInput();
        connectionSwitch.setChecked(settings.isListening());

        // Feature toggles
        notificationsSwitch.setChecked(settings.isNotificationsEnabled());
        spoofDetectionSwitch.setChecked(settings.isSpoofDetectionEnabled());
        screenOnSwitch.setChecked(settings.keepScreenOn());
        serialConsoleSwitch.setChecked(settings.isSerialConsoleEnabled());
        systemWarningsSwitch.setChecked(settings.isSystemWarningsEnabled());
//        locationEstimationSwitch.setChecked(settings.isLocationEstimationEnabled());

        // Warning thresholds
        setupThresholdDials();
    }

    private void setupListeners() {
        connectionModeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (settings.isListening()) {
                    Toast.makeText(requireContext(), "Disconnect before switching modes.", Toast.LENGTH_SHORT).show();
                    connectionModeTabs.selectTab(connectionModeTabs.getTabAt(currentMode.ordinal())); // Revert selection
                    return;
                }

                ConnectionMode mode = ConnectionMode.values()[tab.getPosition()];
                settings.setConnectionMode(mode);
                currentMode = mode;
                updateUIForConnectionMode(mode);
                updateHostInput();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });



        connectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isConnecting) return;

            ConnectionMode mode = settings.getConnectionMode();

            if (isChecked) {
                // Start appropriate service based on connection mode
                if (mode == ConnectionMode.ONBOARD) {
                    // Start onboard detection service directly
                    Log.d(TAG, "Starting Onboard Detection Service");
                    Intent intent = new Intent(requireContext(), OnboardDetectionService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(intent);
                    } else {
                        requireContext().startService(intent);
                    }
                    settings.setListening(true);
                    updateConnectionStatusUI(true);
                } else {
                    // Start network service for other modes
                    startConnection();
                }
            } else {
                // Stop service
                if (mode == ConnectionMode.ONBOARD) {
                    Log.d(TAG, "Stopping Onboard Detection Service");
                    requireContext().stopService(new Intent(requireContext(), OnboardDetectionService.class));
                    settings.setListening(false);
                    updateConnectionStatusUI(false);
                } else {
                    stopConnection();
                }
            }
        });

        //        SwitchMaterial locationEstimationSwitch = view.findViewById(R.id.location_estimation_switch);
//        locationEstimationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//            @Override
//            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                settings.setLocationEstimationEnabled(isChecked);
//            }
//        });


        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.setNotificationsEnabled(isChecked));

        spoofDetectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.setSpoofDetectionEnabled(isChecked));

        screenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.setKeepScreenOn(isChecked));

        serialConsoleSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.setSerialConsoleEnabled(isChecked));

        systemWarningsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.setSystemWarningsEnabled(isChecked));
    }

    private void updateHostInput() {
        ConnectionMode mode = settings.getConnectionMode();
        String currentHost = mode == ConnectionMode.ZMQ ?
                settings.getZmqHost() : settings.getMulticastHost();
        hostInput.setText(currentHost);

        hostInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String newHost = hostInput.getText().toString();
                if (mode == ConnectionMode.ZMQ) {
                    settings.setZmqHost(newHost);
                } else {
                    settings.setMulticastHost(newHost);
                }
            }
        });
    }

    private void startConnection() {
        isConnecting = true;
        updateConnectionStatusUI(false, true);

        // Ensure the current connection mode is set
        ConnectionMode currentMode = settings.getConnectionMode();

        // Save host settings before connecting
        String hostValue = hostInput.getText().toString().trim();
        if (!hostValue.isEmpty()) {
            if (currentMode == ConnectionMode.ZMQ) {
                settings.setZmqHost(hostValue);
            } else {
                settings.setMulticastHost(hostValue);
            }
        }

        // Start service with explicit connection mode
        Intent intent = new Intent(requireContext(), NetworkService.class);
        intent.putExtra("CONNECTION_MODE", currentMode.name());

        // Use startForegroundService for compatibility with newer Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
            Log.i(TAG, "Started foreground service with mode: " + currentMode.name());
        } else {
            requireContext().startService(intent);
            Log.i(TAG, "Started service with mode: " + currentMode.name());
        }

        // Set listening state immediately
        settings.setListening(true);

        // Update UI after a short delay to give service time to start
        mainHandler.postDelayed(() -> {
            isConnecting = false;
            updateConnectionStatusUI(true);

            // Notify activity if needed
            if (getActivity() != null) {
                getActivity().setResult(1);
            }
        }, 500);
    }

    private void stopConnection() {
        settings.setListening(false);
        requireContext().stopService(new Intent(requireContext(), NetworkService.class));
        requireContext().stopService(new Intent(requireContext(), OnboardDetectionService.class)); // Ensure onboard mode is stopped too
        updateConnectionStatusUI(false);

        // Notify activity if needed
        if (getActivity() != null) {
            getActivity().setResult(0);
        }
    }

    private void restartConnection() {
        if (settings.isListening()) {
            stopConnection();
            mainHandler.postDelayed(this::startConnection, 500);
        }
    }

    private void updateConnectionStatusUI(boolean connected) {
        updateConnectionStatusUI(connected, false);
    }

    private void updateConnectionStatusUI(boolean connected, boolean connecting) {
        if (connectionStatus != null) {
            if (connecting) {
                connectionStatus.setText("Connecting...");
                connectionStatus.setTextColor(getResources().getColor(R.color.orange, null));
            } else if (currentMode == ConnectionMode.ONBOARD) {
                // Only show "Active" if we're in onboard mode AND actually connected/listening
                if (connectionSwitch.isChecked() && settings.isListening()) {
                    connectionStatus.setText("Active");
                    connectionStatus.setTextColor(getResources().getColor(R.color.green, null));
                } else {
                    connectionStatus.setText("Disconnected");
                    connectionStatus.setTextColor(getResources().getColor(R.color.red, null));
                }
            } else if (connectionSwitch.isChecked()) {
                connectionStatus.setText("Connected");
                connectionStatus.setTextColor(getResources().getColor(R.color.green, null));
            } else {
                connectionStatus.setText("Disconnected");
                connectionStatus.setTextColor(getResources().getColor(R.color.red, null));
            }
        }
    }

    private void setupThresholdDials() {
        // Clear any existing dials
        thresholdsContainer.removeAllViews();

        // Create CPU warning threshold dial
        View cpuDial = createThresholdDial(
                "CPU",
                (int)settings.getCpuWarningThreshold(),
                "%"
        );
        thresholdsContainer.addView(cpuDial);

        // Create Temperature warning threshold dial
        View tempDial = createThresholdDial(
                "Temp",
                (int)settings.getTempWarningThreshold(),
                "°C"
        );
        thresholdsContainer.addView(tempDial);

        // Create Memory warning threshold dial
        View memoryDial = createThresholdDial(
                "Memory",
                (int)(settings.getMemoryWarningThreshold() * 100),
                "%"
        );
        thresholdsContainer.addView(memoryDial);

        // Create Proximity threshold (RSSI) dial
        View proximityDial = createThresholdDial(
                "RSSI",
                Math.abs(settings.getProximityThreshold()),
                "dBm"
        );
        thresholdsContainer.addView(proximityDial);
    }

    private View createThresholdDial(String title, int value, String unit) {
        View dialView = getLayoutInflater().inflate(R.layout.view_circular_gauge, thresholdsContainer, false);

        TextView titleText = dialView.findViewById(R.id.title);
        TextView valueText = dialView.findViewById(R.id.value);
        TextView unitText = dialView.findViewById(R.id.unit);

        // Set text values
        titleText.setText(title);
        valueText.setText(String.valueOf(value));
        unitText.setText(unit);

        // Configure the progress indicators
        com.google.android.material.progressindicator.CircularProgressIndicator progress =
                dialView.findViewById(R.id.progress);
        com.google.android.material.progressindicator.CircularProgressIndicator progressBackground =
                dialView.findViewById(R.id.progress_background);

        progress.setIndicatorColor(getResources().getColor(R.color.orange, null));
        progressBackground.setIndicatorColor(getResources().getColor(R.color.orange, null));

        progress.setProgress(value);

        return dialView;
    }
}