package com.rootdown.dragonsync.ui.views;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.rootdown.dragonsync.R;

public class DetectionCapabilitiesView extends LinearLayout {

    private Context context;

    public DetectionCapabilitiesView(Context context) {
        super(context);
        init(context);
    }

    public DetectionCapabilitiesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        this.context = context;
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.view_detection_capabilities, this, true);

        updateCapabilities();
    }

    private void updateCapabilities() {
        // Bluetooth capabilities
        updateCapabilityIndicator(R.id.bt_legacy_indicator, R.id.bt_legacy_label,
                hasBluetoothSupport(), "BT Legacy", "Bluetooth 4.x Legacy Advertising");

        updateCapabilityIndicator(R.id.bt_long_range_indicator, R.id.bt_long_range_label,
                hasBluetoothLongRangeSupport(), "BT LR", "Bluetooth 5 Long Range");

        // WiFi capabilities
        updateCapabilityIndicator(R.id.wifi_beacon_indicator, R.id.wifi_beacon_label,
                hasWifiBeaconSupport(), "Beacon", "WiFi Beacon Reception");

        updateCapabilityIndicator(R.id.wifi_nan_indicator, R.id.wifi_nan_label,
                hasWifiNaNSupport(), "NaN", "WiFi Neighbor Aware Networking");
    }

    private void updateCapabilityIndicator(int indicatorId, int labelId, boolean supported,
                                           String shortLabel, String description) {
        ImageView indicator = findViewById(indicatorId);
        TextView label = findViewById(labelId);

        if (supported) {
            indicator.setImageResource(android.R.drawable.checkbox_on_background);
            indicator.setColorFilter(ContextCompat.getColor(context, R.color.status_green));
            label.setTextColor(ContextCompat.getColor(context, R.color.status_green));
        } else {
            indicator.setImageResource(android.R.drawable.ic_delete);
            indicator.setColorFilter(ContextCompat.getColor(context, R.color.status_red));
            label.setTextColor(ContextCompat.getColor(context, R.color.status_red));
        }

        label.setText(shortLabel);
        indicator.setContentDescription(description + (supported ? " supported" : " not supported"));
    }

    private boolean hasBluetoothSupport() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private boolean hasBluetoothLongRangeSupport() {
        // BT5 Long Range requires Android 8.0+ and proper BLE support
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                hasBluetoothSupport();
    }

    private boolean hasWifiBeaconSupport() {
        // WiFi Beacon works on Android 6+ with WiFi
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    private boolean hasWifiNaNSupport() {
        // WiFi NaN requires Android 8.0+ and FEATURE_WIFI_AWARE
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }
}