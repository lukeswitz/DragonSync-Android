package com.rootdown.dragonsync.network;

import android.util.Log;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZMQHandler {
    private static final String TAG = "ZMQHandler";

    public enum MessageFormat {
        BLUETOOTH,
        WIFI,
        SDR
    }

    private MessageFormat messageFormat = MessageFormat.BLUETOOTH;
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private ZContext context;
    private ZMQ.Socket telemetrySocket;
    private ZMQ.Socket statusSocket;
    private ExecutorService executor;
    private final AtomicBoolean shouldContinueRunning = new AtomicBoolean(false);
    private String currentHost;
    private int currentTelemetryPort;
    private int currentStatusPort;
    private MessageHandler telemetryHandler;
    private MessageHandler statusHandler;

    public interface MessageHandler {
        void onMessage(String message);
    }

    public void connect(String host, int telemetryPort, int statusPort,
                        MessageHandler onTelemetry, MessageHandler onStatus) {
        if (host.isEmpty() || telemetryPort <= 0 || statusPort <= 0) {
            Log.e(TAG, "Invalid connection parameters");
            return;
        }

        if (isConnected.get()) {
            Log.w(TAG, "Already connected");
            return;
        }

        // Store parameters for potential reconnection
        this.currentHost = host;
        this.currentTelemetryPort = telemetryPort;
        this.currentStatusPort = statusPort;
        this.telemetryHandler = onTelemetry;
        this.statusHandler = onStatus;

        disconnect();
        shouldContinueRunning.set(true);

        try {
            Log.d(TAG, "Creating ZMQ context");
            context = new ZContext();
            executor = Executors.newFixedThreadPool(2);

            // Setup telemetry socket
            Log.d(TAG, "Setting up telemetry socket on " + host + ":" + telemetryPort);
            telemetrySocket = context.createSocket(SocketType.SUB);
            telemetrySocket.subscribe("");  // Subscribe to all topics
            configureSocket(telemetrySocket);
            telemetrySocket.connect(String.format("tcp://%s:%d", host, telemetryPort));

            // Setup status socket
            Log.d(TAG, "Setting up status socket on " + host + ":" + statusPort);
            statusSocket = context.createSocket(SocketType.SUB);
            statusSocket.subscribe("");  // Subscribe to all topics
            configureSocket(statusSocket);
            statusSocket.connect(String.format("tcp://%s:%d", host, statusPort));

            // Start polling on background threads
            startPolling(telemetrySocket, onTelemetry, "telemetry");
            startPolling(statusSocket, onStatus, "status");

            isConnected.set(true);
            Log.i(TAG, "ZMQ: Connected successfully to " + host);

        } catch (Exception e) {
            Log.e(TAG, "ZMQ Setup Error: " + e.getMessage(), e);
            disconnect();
        }
    }

    private void configureSocket(ZMQ.Socket socket) {
        try {
            socket.setRcvHWM(1000);
            socket.setLinger(0);
            socket.setReceiveTimeOut(500);  // Slightly increased timeout

            // TCP keep alive settings
            socket.setTCPKeepAlive(1);
            socket.setTCPKeepAliveIdle(60);  // Reduced idle time
            socket.setTCPKeepAliveInterval(30);  // Reduced interval

            Log.d(TAG, "Socket configured");
        } catch (Exception e) {
            Log.e(TAG, "Error configuring socket: " + e.getMessage(), e);
        }
    }

    private void startPolling(ZMQ.Socket socket, MessageHandler handler, String socketType) {
        executor.execute(() -> {
            Log.i(TAG, "Starting " + socketType + " polling thread");
            int pollTimeoutMs = 250;
            int emptyPollCount = 0;
            int maxEmptyPolls = 20;  // Report every ~5 seconds of inactivity

            try {
                while (shouldContinueRunning.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        byte[] data = socket.recv(ZMQ.DONTWAIT);

                        if (data != null && data.length > 0) {
                            emptyPollCount = 0;
                            String message = new String(data);
                            Log.d(TAG, socketType + " received: " + message.substring(0, Math.min(50, message.length())) + "...");

                            if (handler != null) {
                                handler.onMessage(message);
                            }
                        } else {
                            emptyPollCount++;
                            if (emptyPollCount >= maxEmptyPolls) {
                                Log.d(TAG, socketType + " socket: No data received for ~" + (pollTimeoutMs * emptyPollCount / 1000) + " seconds");
                                emptyPollCount = 0;
                            }
                            // Sleep to avoid tight loop
                            Thread.sleep(pollTimeoutMs);
                        }
                    } catch (ZMQException e) {
                        if (e.getErrorCode() == ZMQ.Error.EAGAIN.getCode()) {
                            // No message available, just continue
                            Thread.sleep(pollTimeoutMs);
                        } else if (shouldContinueRunning.get()) {
                            Log.e(TAG, socketType + " ZMQ error: " + e.getMessage() + " (code: " + e.getErrorCode() + ")", e);
                            // Add delay before retry
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                if (shouldContinueRunning.get()) {
                    Log.e(TAG, socketType + " polling thread error: " + e.getMessage(), e);
                }
            }

            Log.i(TAG, socketType + " polling thread exiting");
        });
    }

    public void sendServiceCommand(String command, CommandCallback callback) {
        if (statusSocket == null || !isConnected.get()) {
            callback.onResult(false, "Not connected");
            return;
        }

        executor.execute(() -> {
            try {
                statusSocket.send(command.getBytes(), 0);
                byte[] response = statusSocket.recv();
                if (response != null) {
                    callback.onResult(true, new String(response));
                } else {
                    callback.onResult(false, "No response");
                }
            } catch (Exception e) {
                callback.onResult(false, e.getMessage());
            }
        });
    }

    public interface CommandCallback {
        void onResult(boolean success, String response);
    }

    public MessageFormat getMessageFormat() {
        return messageFormat;
    }

    public void setMessageFormat(MessageFormat format) {
        this.messageFormat = format;
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public void disconnect() {
        if (!isConnected.get() && context == null) {
            // Already disconnected, no need to do it again
            return;
        }

        Log.i(TAG, "ZMQ: Disconnecting...");
        shouldContinueRunning.set(false);

        if (executor != null) {
            executor.shutdownNow();
        }

        try {
            if (telemetrySocket != null) {
                telemetrySocket.close();
                telemetrySocket = null;
            }

            if (statusSocket != null) {
                statusSocket.close();
                statusSocket = null;
            }

            if (context != null) {
                context.close();
                context = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "ZMQ Cleanup Error: " + e.getMessage(), e);
        }

        executor = null;
        isConnected.set(false);
        Log.i(TAG, "ZMQ: Disconnected");
    }

    public void reconnect() {
        if (currentHost != null && currentTelemetryPort > 0 && currentStatusPort > 0) {
            Log.i(TAG, "Attempting to reconnect to " + currentHost);
            disconnect();
            connect(currentHost, currentTelemetryPort, currentStatusPort, telemetryHandler, statusHandler);
        } else {
            Log.e(TAG, "Cannot reconnect: connection parameters not available");
        }
    }
}