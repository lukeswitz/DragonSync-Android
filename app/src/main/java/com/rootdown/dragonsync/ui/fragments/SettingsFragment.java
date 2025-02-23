// java/com.rootdown.dragonsync/ui/fragments/SettingsFragment.java
package com.rootdown.dragonsync.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.rootdown.dragonsync.R;
import com.rootdown.dragonsync.models.ConnectionMode;
import com.rootdown.dragonsync.utils.Settings;

public class SettingsFragment extends Fragment {
    private Settings settings;
    private TabLayout connectionModeTabs;
    private EditText hostInput;
    private SwitchMaterial connectionSwitch;
    private SwitchMaterial notificationsSwitch;
    private SwitchMaterial spoofDetectionSwitch;
    private SwitchMaterial screenOnSwitch;
    private SwitchMaterial serialConsoleSwitch;
    private SwitchMaterial systemWarningsSwitch;
    private ViewGroup thresholdsContainer;

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
        return view;
    }

    private void initializeViews(View view) {
        connectionModeTabs = view.findViewById(R.id.connection_mode_tabs);
        hostInput = view.findViewById(R.id.host_input);
        connectionSwitch = view.findViewById(R.id.connection_switch);
        notificationsSwitch = view.findViewById(R.id.notifications_switch);
        spoofDetectionSwitch = view.findViewById(R.id.spoof_detection_switch);
        screenOnSwitch = view.findViewById(R.id.screen_on_switch);
        serialConsoleSwitch = view.findViewById(R.id.serial_console_switch);
        systemWarningsSwitch = view.findViewById(R.id.system_warnings_switch);
        thresholdsContainer = view.findViewById(R.id.thresholds_container);
    }


    private void setupConnectionModes() {
        for (ConnectionMode mode : ConnectionMode.values()) {
            TabLayout.Tab tab = connectionModeTabs.newTab();
            tab.setText(mode.getDisplayName());
            tab.setIcon(getResources().getDrawable(mode.getIcon(), null));
            connectionModeTabs.addTab(tab);
        }

        // Set current mode
        ConnectionMode currentMode = settings.getConnectionMode();
        connectionModeTabs.selectTab(connectionModeTabs.getTabAt(currentMode.ordinal()));
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

        // Warning thresholds
        setupThresholdDials();
    }

    private void setupListeners() {
        connectionModeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                ConnectionMode mode = ConnectionMode.values()[tab.getPosition()];
                settings.setConnectionMode(mode);
                updateHostInput();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        connectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setListening(isChecked);
            // Notify activity to start/stop network service
            if (getActivity() != null) {
                getActivity().setResult(isChecked ? 1 : 0);
            }
        });

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

    private void setupThresholdDials() {
        // TODO: Implement circular dials for thresholds
        // Similar to iOS TacDial implementation
    }
}