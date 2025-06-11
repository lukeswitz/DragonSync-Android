package com.rootdown.dragonsync.models;

public enum ConnectionMode {
    MULTICAST("Muticast"),
    ZMQ("ZMQ"),
    ONBOARD("Onboard");

    private final String displayName;

    ConnectionMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getIcon() {
        switch (this) {
            case MULTICAST:
                return android.R.drawable.ic_menu_share;
            case ZMQ:
                return android.R.drawable.ic_menu_send;
            case ONBOARD:
                return android.R.drawable.ic_menu_compass;
            default:
                return android.R.drawable.ic_menu_preferences;
        }
    }
}