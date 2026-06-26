package com.chinmay.myprinter.data.source.printer.moonraker;

import android.util.Log;

import com.chinmay.myprinter.data.model.GCodeFile;
import com.chinmay.myprinter.data.source.printer.CommandCallback;
import com.chinmay.myprinter.data.source.printer.ConnectionCallback;
import com.chinmay.myprinter.data.source.printer.PrinterStatusListener;
import com.chinmay.myprinter.data.model.Position;
import com.chinmay.myprinter.data.model.PrinterState;
import com.chinmay.myprinter.data.model.PrinterStatus;
import com.chinmay.myprinter.data.model.Temperature;
import com.chinmay.myprinter.data.source.printer.PrinterClient;
import com.chinmay.myprinter.data.source.printer.moonraker.model.FileListResponse;
import com.chinmay.myprinter.data.source.printer.moonraker.model.MoonrakerResponse;
import com.chinmay.myprinter.data.source.printer.moonraker.model.PrinterObjects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MoonrakerClient implements PrinterClient {
    private static final String TAG = "MoonrakerClient";

    private MoonrakerApiService apiService;
    private MoonrakerWebSocket webSocket;
    private final List<PrinterStatusListener> listeners;
    private PrinterStatus currentStatus;
    private boolean isConnected = false;
    private ConnectionCallback connectionCallback;

    public MoonrakerClient() {
        this.webSocket = new MoonrakerWebSocket();
        this.listeners = new ArrayList<>();
        this.currentStatus = createDefaultStatus();
    }

    @Override
    public void connect(String url, String apiKey, ConnectionCallback callback) {
        this.connectionCallback = callback;
        if (webSocket != null) webSocket.disconnect();
        Log.d(TAG, "Connecting to: " + url);

        // Parse URL to get host and port
        final String host;
        final int port;

        if (url.startsWith("http://") || url.startsWith("https://")) {
            url = url.substring(url.indexOf("://") + 3);
        }

        if (url.contains(":")) {
            String[] parts = url.split(":");
            host = parts[0];
            int tempPort = 7125; // Default Moonraker port
            try {
                tempPort = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid port", e);
            }
            port = tempPort;
        } else {
            host = url;
            port = 7125; // Default Moonraker port
        }

        // Create Retrofit instance
        String baseUrl = "http://" + host + ":" + port + "/";
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(MoonrakerApiService.class);

        // Test connection with printer info
        apiService.getPrinterInfo().enqueue(new Callback<MoonrakerResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<MoonrakerResponse<Map<String, Object>>> call,
                                   Response<MoonrakerResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    isConnected = true;

                    // Set state to IDLE when connected (will be updated by status query)
                    currentStatus.setState(PrinterState.IDLE);

                    callback.onConnected();

                    // Connect WebSocket for real-time updates
                    connectWebSocket(host, port);

                    // Query initial status
                    queryStatus();

                    // Query temperature history
                    queryTemperatureHistory();
                } else {
                    callback.onError("Failed to connect to printer");
                }
            }

            @Override
            public void onFailure(Call<MoonrakerResponse<Map<String, Object>>> call, Throwable t) {
                Log.e(TAG, "Connection failed", t);
                callback.onError("Connection failed: " + t.getMessage());
            }
        });
    }

    private void connectWebSocket(String host, int port) {
        webSocket.connect(host, port, new MoonrakerWebSocket.StatusUpdateListener() {
            @Override
            public void onStatusUpdate(PrinterObjects.StatusData status) {
                updateStatusFromMoonraker(status);
            }

            @Override
            public void onConnectionChanged(boolean connected) {
                Log.d(TAG, "WebSocket connection: " + connected);
                isConnected = connected;
                if (connectionCallback != null) {
                    if (connected) {
                        connectionCallback.onConnected();
                    } else {
                        connectionCallback.onDisconnected();
                    }
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WebSocket error: " + error);
            }
        });
    }

    private void queryStatus() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("extruder", "temperature,target,power");
        queryParams.put("heater_bed", "temperature,target,power");
        queryParams.put("toolhead", "position,homed_axes");
        queryParams.put("print_stats", "state,filename,print_duration,total_duration,filament_used,info");
        queryParams.put("virtual_sdcard", "progress,file_position,is_active");

        apiService.queryPrinterObjects(queryParams).enqueue(new Callback<MoonrakerResponse<PrinterObjects>>() {
            @Override
            public void onResponse(Call<MoonrakerResponse<PrinterObjects>> call,
                                   Response<MoonrakerResponse<PrinterObjects>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    PrinterObjects result = response.body().getResult();
                    if (result != null && result.getStatus() != null) {
                        updateStatusFromMoonraker(result.getStatus());
                    }
                }
            }

            @Override
            public void onFailure(Call<MoonrakerResponse<PrinterObjects>> call, Throwable t) {
                Log.e(TAG, "Status query failed", t);
            }
        });
    }

    private void updateStatusFromMoonraker(PrinterObjects.StatusData status) {
        // Update temperatures - only update if values are not null
        if (status.getExtruder() != null) {
            if (status.getExtruder().getTemperature() != null) {
                currentStatus.getNozzleTemp().setCurrent(status.getExtruder().getTemperature());
            }
            if (status.getExtruder().getTarget() != null) {
                currentStatus.getNozzleTemp().setTarget(status.getExtruder().getTarget());
            }
        }

        if (status.getHeaterBed() != null) {
            if (status.getHeaterBed().getTemperature() != null) {
                currentStatus.getBedTemp().setCurrent(status.getHeaterBed().getTemperature());
            }
            if (status.getHeaterBed().getTarget() != null) {
                currentStatus.getBedTemp().setTarget(status.getHeaterBed().getTarget());
            }
        }

        // Update position
        if (status.getToolhead() != null && status.getToolhead().getPosition() != null) {
            List<Float> pos = status.getToolhead().getPosition();
            if (pos.size() >= 3) {
                currentStatus.getPosition().setX(pos.get(0));
                currentStatus.getPosition().setY(pos.get(1));
                currentStatus.getPosition().setZ(pos.get(2));
            }
        }

        // Update print stats
        if (status.getPrintStats() != null) {
            String state = status.getPrintStats().getState();
            if (state != null) {
                PrinterState newState = convertState(state);
                currentStatus.setState(newState);
                Log.d(TAG, "Print state updated to: " + newState);
            }

            String filename = status.getPrintStats().getFilename();
            if (filename != null) {
                currentStatus.setCurrentFilename(filename);
                Log.d(TAG, "Print filename: " + filename);
            }

            // Duration is in seconds from Moonraker
            currentStatus.setPrintDuration((long) status.getPrintStats().getPrintDuration());
            currentStatus.setTotalDuration((long) status.getPrintStats().getTotalDuration());
            currentStatus.setFilamentUsed((long) status.getPrintStats().getFilamentUsed());
            Log.d(TAG, "Print duration: " + currentStatus.getPrintDuration() + "s");

            // Progress from virtual_sdcard (file bytes read / file size) — same source Fluidd uses.
            // Fall back to 0 if not yet received (delta updates may not include it every tick).
            if (status.getVirtualSdcard() != null && status.getVirtualSdcard().getProgress() != null) {
                float progress = status.getVirtualSdcard().getProgress();
                currentStatus.setPrintProgressFloat(progress);
                Log.d(TAG, "Print progress (virtual_sdcard): " + Math.round(progress * 100) + "%");
            }

            // Update layer info if available
            if (status.getPrintStats().getInfo() != null) {
                currentStatus.setCurrentLayer(status.getPrintStats().getInfo().getCurrentLayer());
                currentStatus.setTotalLayers(status.getPrintStats().getInfo().getTotalLayer());
                Log.d(TAG, "Layer info: " + currentStatus.getCurrentLayer() + "/" + currentStatus.getTotalLayers());
            }
        }

        // Notify listeners
        for (PrinterStatusListener listener : listeners) {
            listener.onStatusUpdate(currentStatus);
        }
    }

    private PrinterState convertState(String moonrakerState) {
        if (moonrakerState == null) {
            Log.d(TAG, "State is null, defaulting to IDLE");
            return PrinterState.IDLE;
        }

        Log.d(TAG, "Converting state: " + moonrakerState);
        String state = moonrakerState.toLowerCase();

        switch (state) {
            case "printing":
                return PrinterState.PRINTING;
            case "paused":
                return PrinterState.PAUSED;
            case "complete":
                return PrinterState.COMPLETE;
            case "cancelled":
                return PrinterState.IDLE;
            case "error":
                return PrinterState.ERROR;
            case "standby":
            case "ready":
                return PrinterState.IDLE;
            default:
                Log.d(TAG, "Unknown state '" + moonrakerState + "', defaulting to IDLE");
                return PrinterState.IDLE;
        }
    }

    @Override
    public void disconnect() {
        isConnected = false;
        if (webSocket != null) {
            webSocket.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public void addStatusListener(PrinterStatusListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeStatusListener(PrinterStatusListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void setTemperature(String heater, int target, CommandCallback callback) {
        String gcode;
        if ("nozzle".equals(heater) || "extruder".equals(heater)) {
            gcode = "M104 S" + target;
        } else if ("bed".equals(heater)) {
            gcode = "M140 S" + target;
        } else {
            callback.onError("Unknown heater: " + heater);
            return;
        }

        sendGCode(gcode, callback);
    }

    @Override
    public void homeAxis(String axis, CommandCallback callback) {
        String gcode = "G28 " + axis.toUpperCase();
        sendGCode(gcode, callback);
    }

    @Override
    public void moveAxis(String axis, float distance, CommandCallback callback) {
        String gcode = String.format("G91\nG1 %s%.2f F3000\nG90", axis.toUpperCase(), distance);
        sendGCode(gcode, callback);
    }

    @Override
    public void startPrint(String filename, CommandCallback callback) {
        apiService.startPrint(filename).enqueue(new Callback<MoonrakerResponse<String>>() {
            @Override
            public void onResponse(Call<MoonrakerResponse<String>> call,
                                   Response<MoonrakerResponse<String>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to start print");
                }
            }

            @Override
            public void onFailure(Call<MoonrakerResponse<String>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    @Override
    public void pausePrint(CommandCallback callback) {
        apiService.pausePrint().enqueue(createSimpleCallback(callback));
    }

    @Override
    public void resumePrint(CommandCallback callback) {
        apiService.resumePrint().enqueue(createSimpleCallback(callback));
    }

    @Override
    public void cancelPrint(CommandCallback callback) {
        apiService.cancelPrint().enqueue(createSimpleCallback(callback));
    }

    @Override
    public List<GCodeFile> listFiles() {
        if (apiService == null) return new ArrayList<>();
        try {
            Response<MoonrakerResponse<List<FileListResponse.FileItem>>> response =
                    apiService.listFiles("gcodes").execute();
            if (response.isSuccessful() && response.body() != null
                    && response.body().isSuccess()) {
                List<FileListResponse.FileItem> items = response.body().getResult();
                if (items != null) {
                    items.sort((a, b) -> Double.compare(b.getModified(), a.getModified()));
                    List<GCodeFile> files = new ArrayList<>();
                    for (FileListResponse.FileItem item : items) {
                        String name = item.getPath() != null ? item.getPath() : item.getFilename();
                        files.add(new GCodeFile(name, item.getSize(), item.getPath()));
                    }
                    Log.d(TAG, "listFiles: " + files.size() + " files");
                    return files;
                }
            } else {
                Log.e(TAG, "listFiles failed: HTTP " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "listFiles error: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public void listFilesAsync(FileListCallback callback) {
        apiService.listFiles("gcodes").enqueue(
                new Callback<MoonrakerResponse<List<FileListResponse.FileItem>>>() {
            @Override
            public void onResponse(
                    Call<MoonrakerResponse<List<FileListResponse.FileItem>>> call,
                    Response<MoonrakerResponse<List<FileListResponse.FileItem>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    List<FileListResponse.FileItem> items = response.body().getResult();
                    if (items != null) {
                        items.sort((a, b) -> Double.compare(b.getModified(), a.getModified()));
                        List<GCodeFile> files = new ArrayList<>();
                        for (FileListResponse.FileItem item : items) {
                            String name = item.getPath() != null ? item.getPath() : item.getFilename();
                            files.add(new GCodeFile(name, item.getSize(), item.getPath()));
                        }
                        callback.onSuccess(files);
                    } else {
                        callback.onError("No files found");
                    }
                } else {
                    callback.onError("Failed to list files: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<MoonrakerResponse<List<FileListResponse.FileItem>>> call,
                                  Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    @Override
    public void deleteFile(String filename, CommandCallback callback) {
        if (apiService == null) {
            callback.onError("Not connected");
            return;
        }
        apiService.deleteFile(filename).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call,
                                   retrofit2.Response<okhttp3.ResponseBody> response) {
                // Always close the body to release the OkHttp connection back to the pool
                if (response.body() != null) response.body().close();
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Delete failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    @Override
    public void sendGCode(String gcode, CommandCallback callback) {
        apiService.sendGcode(gcode).enqueue(createSimpleCallback(callback));
    }

    private Callback<MoonrakerResponse<String>> createSimpleCallback(CommandCallback callback) {
        return new Callback<MoonrakerResponse<String>>() {
            @Override
            public void onResponse(Call<MoonrakerResponse<String>> call,
                                   Response<MoonrakerResponse<String>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Command failed");
                }
            }

            @Override
            public void onFailure(Call<MoonrakerResponse<String>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        };
    }

    private PrinterStatus createDefaultStatus() {
        PrinterStatus status = new PrinterStatus();
        status.setState(PrinterState.DISCONNECTED);
        status.setNozzleTemp(new Temperature(20, 0));
        status.setBedTemp(new Temperature(20, 0));
        status.setPosition(new Position(0, 0, 0));
        status.setPrintProgress(0);
        status.setCurrentLayer(0);
        status.setTotalLayers(0);
        status.setPrintDuration(0);
        return status;
    }

    public interface FileListCallback {
        void onSuccess(List<GCodeFile> files);
        void onError(String error);
    }

    public interface TemperatureHistoryCallback {
        void onHistoryReceived(List<TemperatureHistoryPoint> history);
        void onError(String error);
    }

    public static class TemperatureHistoryPoint {
        public final long timestamp; // milliseconds (System.currentTimeMillis())
        public final float nozzleTemp;
        public final float nozzleTarget;
        public final float bedTemp;
        public final float bedTarget;

        public TemperatureHistoryPoint(long timestamp, float nozzleTemp, float nozzleTarget,
                                       float bedTemp, float bedTarget) {
            this.timestamp = timestamp;
            this.nozzleTemp = nozzleTemp;
            this.nozzleTarget = nozzleTarget;
            this.bedTemp = bedTemp;
            this.bedTarget = bedTarget;
        }
    }

    private void queryTemperatureHistory() {
        apiService.getTemperatureHistory().enqueue(new Callback<MoonrakerResponse<JsonObject>>() {
            @Override
            public void onResponse(Call<MoonrakerResponse<JsonObject>> call,
                                   Response<MoonrakerResponse<JsonObject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject result = response.body().getResult();
                    if (result != null) {
                        parseTemperatureHistory(result);
                    } else {
                        Log.e(TAG, "Temperature history: result is null");
                    }
                } else {
                    Log.e(TAG, "Temperature history request failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<MoonrakerResponse<JsonObject>> call, Throwable t) {
                Log.e(TAG, "Temperature history query failed", t);
            }
        });
    }

    private void parseTemperatureHistory(JsonObject result) {
        try {
            JsonObject extruder = result.has("extruder") ? result.getAsJsonObject("extruder") : null;
            JsonObject heaterBed = result.has("heater_bed") ? result.getAsJsonObject("heater_bed") : null;

            JsonArray nozzleTempsArr   = extruder  != null && extruder.has("temperatures")  ? extruder.getAsJsonArray("temperatures")  : null;
            JsonArray nozzleTargetsArr = extruder  != null && extruder.has("targets")        ? extruder.getAsJsonArray("targets")        : null;
            JsonArray bedTempsArr      = heaterBed != null && heaterBed.has("temperatures") ? heaterBed.getAsJsonArray("temperatures") : null;
            JsonArray bedTargetsArr    = heaterBed != null && heaterBed.has("targets")      ? heaterBed.getAsJsonArray("targets")      : null;

            if (nozzleTempsArr == null || nozzleTempsArr.size() == 0) {
                Log.d(TAG, "No temperature history available");
                return;
            }

            // Moonraker stores at ~1 Hz with no per-sample timestamps.
            // Reconstruct: last sample = now, earlier samples go back 1 s each.
            int count = nozzleTempsArr.size();
            long nowMs = System.currentTimeMillis();
            List<TemperatureHistoryPoint> history = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                long timestampMs = nowMs - (long)(count - 1 - i) * 1000L;
                float nozzleTemp   = nozzleTempsArr.get(i).getAsFloat();
                float nozzleTarget = nozzleTargetsArr != null && i < nozzleTargetsArr.size() ? nozzleTargetsArr.get(i).getAsFloat() : 0f;
                float bedTemp      = bedTempsArr      != null && i < bedTempsArr.size()      ? bedTempsArr.get(i).getAsFloat()      : 0f;
                float bedTarget    = bedTargetsArr    != null && i < bedTargetsArr.size()    ? bedTargetsArr.get(i).getAsFloat()    : 0f;
                history.add(new TemperatureHistoryPoint(timestampMs, nozzleTemp, nozzleTarget, bedTemp, bedTarget));
            }

            Log.d(TAG, "Temperature history loaded: " + count + " points");

            for (PrinterStatusListener listener : listeners) {
                if (listener instanceof TemperatureHistoryListener) {
                    ((TemperatureHistoryListener) listener).onTemperatureHistory(history);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing temperature history", e);
        }
    }

    public interface TemperatureHistoryListener {
        void onTemperatureHistory(List<TemperatureHistoryPoint> history);
    }
}
