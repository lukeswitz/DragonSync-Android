package com.rootdown.dragonsync.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastHandler {
    private static final String TAG = "MulticastHandler";
    private static final int BUFFER_SIZE = 65536;

    private Context context;
    private WifiManager.MulticastLock multicastLock;
    private MulticastSocket socket;
    private InetAddress group;
    private ExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private MessageHandler messageHandler;

    public interface MessageHandler {
        void onMessage(String message);
        void onError(String error);
    }

    public MulticastHandler(Context context) {
        this.context = context.getApplicationContext();
    }

    public MulticastHandler() {
        this.context = null;
    }

    public void startListening(String multicastAddress, int port, MessageHandler handler) {
        if (isRunning.get()) {
            Log.w(TAG, "Already listening");
            return;
        }

        this.messageHandler = handler;
        executor = Executors.newSingleThreadExecutor();

        // Acquire multicast lock if context available
        if (context != null) {
            try {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                multicastLock = wifiManager.createMulticastLock("DragonSync.multicastLock");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();
                Log.d(TAG, "Multicast lock acquired");
            } catch (Exception e) {
                Log.e(TAG, "Failed to acquire multicast lock: " + e.getMessage());
            }
        }

        executor.execute(() -> {
            try {
                setupSocketWithNetworkInterfaces(multicastAddress, port);
                listenForMessages();
            } catch (IOException e) {
                Log.e(TAG, "Error setting up network: " + e.getMessage());
                if (messageHandler != null) {
                    messageHandler.onError("Failed to setup network: " + e.getMessage());
                }
                stopListening();
            }
        });
    }

    private void setupSocketWithNetworkInterfaces(String multicastAddress, int port) throws IOException {
        socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        socket.setSoTimeout(1000);
        group = InetAddress.getByName(multicastAddress);
        Log.i(TAG, "Multicast socket created on port " + port);
        Log.i(TAG, "Multicast group: " + multicastAddress);

        NetworkInterface activeInterface = getActiveNetworkInterface();
        boolean joinedGroup = false;
        if (activeInterface != null) {
            try {
                socket.joinGroup(new InetSocketAddress(group, port), activeInterface);
                joinedGroup = true;
                Log.i(TAG, "Successfully joined multicast group on active interface: " + activeInterface.getDisplayName());
            } catch (IOException e) {
                Log.d(TAG, "Failed to join on active interface " + activeInterface.getDisplayName() + ": " + e.getMessage());
            }
        }
        if (!joinedGroup) {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements() && !joinedGroup) {
                NetworkInterface iface = interfaces.nextElement();
                try {
                    if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast())
                        continue;
                    Log.d(TAG, "Trying interface: " + iface.getDisplayName());
                    try {
                        socket.joinGroup(new InetSocketAddress(group, port), iface);
                        joinedGroup = true;
                        Log.i(TAG, "Successfully joined multicast group on interface: " + iface.getDisplayName());
                    } catch (IOException e) {
                        Log.d(TAG, "Failed to join on interface " + iface.getDisplayName() + ": " + e.getMessage());
                    }
                } catch (SocketException e) {
                    Log.d(TAG, "Error with interface " + iface.getDisplayName() + ": " + e.getMessage());
                }
            }
        }
        if (!joinedGroup) {
            throw new IOException("Could not join multicast group on any interface");
        }
        isRunning.set(true);
    }

    private NetworkInterface getActiveNetworkInterface() {
        if (context != null) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    LinkProperties lp = cm.getLinkProperties(activeNetwork);
                    if (lp != null && lp.getInterfaceName() != null) {
                        try {
                            NetworkInterface nif = NetworkInterface.getByName(lp.getInterfaceName());
                            if (nif != null && nif.isUp() && nif.supportsMulticast()) {
                                Log.i(TAG, "Active network interface: " + nif.getDisplayName());
                                return nif;
                            }
                        } catch (SocketException e) {
                            Log.e(TAG, "Error obtaining active network interface: " + e.getMessage());
                        }
                    }
                }
            }
        }
        Log.d(TAG, "No active network interface found");
        return null;
    }



    private void listenForMessages() {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        Log.i(TAG, "Listening for multicast packets on port " + socket.getLocalPort());

        int timeoutCount = 0;
        while (isRunning.get()) {
            try {
                packet.setLength(buffer.length);
                socket.receive(packet);

                String sender = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                Log.i(TAG, "RECEIVED PACKET: " + packet.getLength() + " bytes from " + sender);

                String message = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength()
                );

                Log.d(TAG, "Message content: " + message.substring(0, Math.min(100, message.length())) + "...");

                if (messageHandler != null) {
                    messageHandler.onMessage(message);
                }

                // Reset timeout counter when we receive a message
                timeoutCount = 0;

            } catch (java.net.SocketTimeoutException e) {
                // Reduce log spam from timeouts
                timeoutCount++;
                if (timeoutCount % 10 == 0) {
                    Log.d(TAG, "No packets received (" + timeoutCount + " timeouts)");
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error receiving packet: " + e.getMessage());
                    if (messageHandler != null) {
                        messageHandler.onError("Receive error: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void stopListening() {
        isRunning.set(false);

        if (socket != null) {
            try {
                if (group != null) {
                    try {
                        socket.leaveGroup(group);
                    } catch (IOException e) {
                        Log.e(TAG, "Error leaving multicast group: " + e.getMessage());
                    }
                }
            } finally {
                socket.close();
                socket = null;
            }
        }

        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
            Log.d(TAG, "Multicast lock released");
            multicastLock = null;
        }

        if (executor != null) {
            executor.shutdown();
            executor = null;
        }

        Log.i(TAG, "Multicast listener stopped");
    }

    public boolean isListening() {
        return isRunning.get();
    }
}