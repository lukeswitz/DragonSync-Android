package com.rootdown.dragonsync.models;

public enum ConnectionMode {
    MULTICAST("Multicast"),
    ZMQ("Direct ZMQ");

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
                return android.R.drawable.ic_menu_share; // For network/broadcast
            case ZMQ:
                return android.R.drawable.ic_menu_send;
            default:
                return android.R.drawable.ic_menu_preferences;
        }
    }
}