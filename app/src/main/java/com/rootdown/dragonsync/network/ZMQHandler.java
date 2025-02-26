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

        disconnect();
        shouldContinueRunning.set(true);

        try {
            context = new ZContext();
            executor = Executors.newFixedThreadPool(2);

            // Setup telemetry socket
            telemetrySocket = context.createSocket(SocketType.SUB);
            telemetrySocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
            configureSocket(telemetrySocket);
            telemetrySocket.connect(String.format("tcp://%s:%d", host, telemetryPort));

            // Setup status socket
            statusSocket = context.createSocket(SocketType.SUB);
            statusSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
            configureSocket(statusSocket);
            statusSocket.connect(String.format("tcp://%s:%d", host, statusPort));

            // Start polling on background threads
            startPolling(telemetrySocket, onTelemetry);
            startPolling(statusSocket, onStatus);

            isConnected.set(true);
            Log.i(TAG, "ZMQ: Connected successfully");

        } catch (Exception e) {
            Log.e(TAG, "ZMQ Setup Error: " + e.getMessage());
            disconnect();
        }
    }

    private void configureSocket(ZMQ.Socket socket) {
        socket.setRcvHWM(1000);
        socket.setLinger(0);
        socket.setReceiveTimeOut(250);
        socket.setImmediate(true);

        // TCP keep alive settings
        socket.setTCPKeepAlive(1);
        socket.setTCPKeepAliveIdle(120);
        socket.setTCPKeepAliveInterval(60);
    }

    private void startPolling(ZMQ.Socket socket, MessageHandler handler) {
        executor.execute(() -> {
            while (shouldContinueRunning.get()) {
                try {
                    byte[] data = socket.recv(ZMQ.NOBLOCK);
                    if (data != null) {
                        String message = new String(data);
                        handler.onMessage(message);
                    }
                    Thread.sleep(10); // Small delay to prevent busy waiting
                } catch (ZMQException e) {
                    if (e.getErrorCode() != ZMQ.Error.EAGAIN.getCode()
                            && shouldContinueRunning.get()) {
                        Log.e(TAG, "ZMQ Polling Error: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (shouldContinueRunning.get()) {
                        Log.e(TAG, "ZMQ polling thread interrupted: " + e.getMessage());
                    }
                } catch (Exception e) {
                    if (shouldContinueRunning.get()) {
                        Log.e(TAG, "ZMQ Polling Error: " + e.getMessage());
                    }
                }
            }
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
        Log.i(TAG, "ZMQ: Disconnecting...");
        shouldContinueRunning.set(false);

        if (executor != null) {
            executor.shutdown();
        }

        try {
            if (telemetrySocket != null) {
                telemetrySocket.close();
            }
            if (statusSocket != null) {
                statusSocket.close();
            }
            if (context != null) {
                context.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "ZMQ Cleanup Error: " + e.getMessage());
        }

        telemetrySocket = null;
        statusSocket = null;
        context = null;
        executor = null;
        isConnected.set(false);
        Log.i(TAG, "ZMQ: Disconnected");
    }
}