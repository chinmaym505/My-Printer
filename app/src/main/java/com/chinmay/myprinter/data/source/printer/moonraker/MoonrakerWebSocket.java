package com.chinmay.myprinter.data.source.printer.moonraker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.chinmay.myprinter.data.source.printer.moonraker.model.PrinterObjects;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MoonrakerWebSocket {
    private static final String TAG = "MoonrakerWebSocket";
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int PING_INTERVAL_SECONDS = 30;

    private WebSocket webSocket;
    private final OkHttpClient client;
    private final Gson gson;
    private final Handler handler;
    private String wsUrl;
    private StatusUpdateListener listener;
    private boolean shouldReconnect = true;
    private int reconnectAttempts = 0;

    public interface StatusUpdateListener {
        void onStatusUpdate(PrinterObjects.StatusData status);
        void onConnectionChanged(boolean connected);
        void onError(String error);
    }

    public MoonrakerWebSocket() {
        this.client = new OkHttpClient.Builder()
                .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void connect(String host, int port, StatusUpdateListener listener) {
        this.wsUrl = "ws://" + host + ":" + port + "/websocket";
        this.listener = listener;
        this.shouldReconnect = true;
        this.reconnectAttempts = 0;
        connectInternal();
    }

    private void connectInternal() {
        Log.d(TAG, "Connecting to WebSocket: " + wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connected");
                reconnectAttempts = 0;
                handler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionChanged(true);
                    }
                });

                // Subscribe to printer objects
                subscribeToObjects();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "WebSocket message: " + text);
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure", t);
                handler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionChanged(false);
                        listener.onError("Connection failed: " + t.getMessage());
                    }
                });

                // Attempt to reconnect
                if (shouldReconnect) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " " + reason);
                handler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionChanged(false);
                    }
                });

                if (shouldReconnect && code != 1000) {
                    scheduleReconnect();
                }
            }
        });
    }

    private void subscribeToObjects() {
        // Subscribe to printer status updates
        JsonObject params = new JsonObject();
        JsonObject objects = new JsonObject();
        objects.add("extruder", gson.toJsonTree(new String[]{"temperature", "target", "power"}));
        objects.add("heater_bed", gson.toJsonTree(new String[]{"temperature", "target", "power"}));
        objects.add("toolhead", gson.toJsonTree(new String[]{"position", "homed_axes"}));
        objects.add("print_stats", gson.toJsonTree(new String[]{"state", "filename", "print_duration", "total_duration", "filament_used", "info"}));
        objects.add("display_status", gson.toJsonTree(new String[]{"progress", "message"}));
        objects.add("virtual_sdcard", gson.toJsonTree(new String[]{"progress", "file_position", "is_active"}));
        params.add("objects", objects);

        JsonObject message = new JsonObject();
        message.addProperty("jsonrpc", "2.0");
        message.addProperty("method", "printer.objects.subscribe");
        message.add("params", params);
        message.addProperty("id", 1);

        String json = gson.toJson(message);
        Log.d(TAG, "Subscribing to objects: " + json);
        webSocket.send(json);
    }

    private void handleMessage(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();

            // Check if this is a status update notification
            if (json.has("method")) {
                String method = json.get("method").getAsString();
                if ("notify_status_update".equals(method)) {
                    JsonObject params = json.getAsJsonArray("params").get(0).getAsJsonObject();

                    // Parse the status update
                    PrinterObjects.StatusData statusData = new PrinterObjects.StatusData();

                    if (params.has("extruder")) {
                        statusData.setExtruder(gson.fromJson(params.get("extruder"), PrinterObjects.HeaterData.class));
                    }

                    if (params.has("heater_bed")) {
                        statusData.setHeaterBed(gson.fromJson(params.get("heater_bed"), PrinterObjects.HeaterData.class));
                    }

                    if (params.has("toolhead")) {
                        statusData.setToolhead(gson.fromJson(params.get("toolhead"), PrinterObjects.ToolheadData.class));
                    }

                    if (params.has("print_stats")) {
                        statusData.setPrintStats(gson.fromJson(params.get("print_stats"), PrinterObjects.PrintStatsData.class));
                    }

                    // Notify listener on main thread
                    handler.post(() -> {
                        if (listener != null) {
                            listener.onStatusUpdate(statusData);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing WebSocket message", e);
        }
    }

    private void scheduleReconnect() {
        reconnectAttempts++;
        int delay = Math.min(RECONNECT_DELAY_MS * reconnectAttempts, 30000); // Max 30 seconds
        Log.d(TAG, "Scheduling reconnect in " + delay + "ms (attempt " + reconnectAttempts + ")");

        handler.postDelayed(() -> {
            if (shouldReconnect) {
                connectInternal();
            }
        }, delay);
    }

    public void disconnect() {
        Log.d(TAG, "Disconnecting WebSocket");
        shouldReconnect = false;
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }
    }

    public boolean isConnected() {
        return webSocket != null;
    }
}
