
package com.rootdown.dragonsync.network;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastHandler {
    private static final String TAG = "MulticastHandler";
    private static final int BUFFER_SIZE = 65536;

    private MulticastSocket socket;
    private InetAddress group;
    private ExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private MessageHandler messageHandler;

    public interface MessageHandler {
        void onMessage(String message);
        void onError(String error);
    }

    public void startListening(String multicastAddress, int port, MessageHandler handler) {
        if (isRunning.get()) {
            Log.w(TAG, "Already listening");
            return;
        }

        this.messageHandler = handler;
        executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                setupMulticastSocket(multicastAddress, port);
                listenForMessages();
            } catch (IOException e) {
                Log.e(TAG, "Error setting up multicast: " + e.getMessage());
                messageHandler.onError("Failed to setup multicast: " + e.getMessage());
                stopListening();
            }
        });
    }

    private void setupMulticastSocket(String multicastAddress, int port) throws IOException {
        socket = new MulticastSocket(port);
        group = InetAddress.getByName(multicastAddress);

        // Improved network interface discovery
        NetworkInterface networkInterface = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isUp() && iface.supportsMulticast() && !iface.isLoopback()) {
                // Found a suitable interface
                networkInterface = iface;
                Log.i(TAG, "Using network interface: " + iface.getDisplayName());
                break;
            }
        }

        if (networkInterface == null) {
            // Fall back to default behavior
            Log.w(TAG, "No suitable multicast network interface found, using default");
        } else {
            socket.setNetworkInterface(networkInterface);
        }

        socket.joinGroup(group);
        socket.setReuseAddress(true);
        socket.setSoTimeout(1000); // 1 second timeout for receives

        isRunning.set(true);
        Log.i(TAG, "Multicast socket setup complete");
    }

    private void listenForMessages() {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (isRunning.get()) {
            try {
                socket.receive(packet);
                String message = new String(
                        packet.getData(),
                        packet.getOffset(),
                        packet.getLength()
                );

                if (messageHandler != null) {
                    messageHandler.onMessage(message);
                }

                packet.setLength(buffer.length); // Reset the length for next receive

            } catch (java.net.SocketTimeoutException e) {
                // This is expected due to our timeout setting, just continue
                continue;
            } catch (IOException e) {
                if (isRunning.get()) {
                    // Only log if we're still supposed to be running
                    Log.e(TAG, "Error receiving multicast: " + e.getMessage());
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
                    socket.leaveGroup(group);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error leaving multicast group: " + e.getMessage());
            } finally {
                socket.close();
                socket = null;
            }
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