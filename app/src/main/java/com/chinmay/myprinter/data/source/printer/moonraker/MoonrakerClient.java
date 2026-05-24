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

            // Calculate and update progress percentage
            int progress = currentStatus.calculateProgress();
            currentStatus.setPrintProgress(progress);
            Log.d(TAG, "Print progress: " + progress + "% (" +
                  currentStatus.getPrintDuration() + "s / " +
                  currentStatus.getTotalDuration() + "s)");

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
        apiService.deleteFile(filename).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call,
                                   retrofit2.Response<okhttp3.ResponseBody> response) {
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
        public final long timestamp; // Unix timestamp in seconds
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
        apiService.getTemperatureHistory().enqueue(new Callback<MoonrakerResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<MoonrakerResponse<Map<String, Object>>> call,
                                   Response<MoonrakerResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Map<String, Object> result = response.body().getResult();
                    parseTemperatureHistory(result);
                } else {
                    Log.e(TAG, "Failed to fetch temperature history");
                }
            }

            @Override
            public void onFailure(Call<MoonrakerResponse<Map<String, Object>>> call, Throwable t) {
                Log.e(TAG, "Temperature history query failed", t);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void parseTemperatureHistory(Map<String, Object> result) {
        try {
            List<TemperatureHistoryPoint> history = new ArrayList<>();

            // Get extruder data
            Map<String, Object> extruder = (Map<String, Object>) result.get("extruder");
            List<Double> extruderTemps = extruder != null ? (List<Double>) extruder.get("temperatures") : null;
            List<Double> extruderTargets = extruder != null ? (List<Double>) extruder.get("targets") : null;

            // Get heater_bed data
            Map<String, Object> heaterBed = (Map<String, Object>) result.get("heater_bed");
            List<Double> bedTemps = heaterBed != null ? (List<Double>) heaterBed.get("temperatures") : null;
            List<Double> bedTargets = heaterBed != null ? (List<Double>) heaterBed.get("targets") : null;

            // Get timestamps (same for all heaters)
            List<Double> timestamps = extruder != null ? (List<Double>) extruder.get("times") : null;

            if (timestamps == null || timestamps.isEmpty()) {
                Log.d(TAG, "No temperature history available");
                return;
            }

            // Build history points
            int count = timestamps.size();
            for (int i = 0; i < count; i++) {
                long timestamp = timestamps.get(i).longValue();
                float nozzleTemp = extruderTemps != null && i < extruderTemps.size() ?
                                  extruderTemps.get(i).floatValue() : 0;
                float nozzleTarget = extruderTargets != null && i < extruderTargets.size() ?
                                    extruderTargets.get(i).floatValue() : 0;
                float bedTemp = bedTemps != null && i < bedTemps.size() ?
                               bedTemps.get(i).floatValue() : 0;
                float bedTarget = bedTargets != null && i < bedTargets.size() ?
                                 bedTargets.get(i).floatValue() : 0;

                history.add(new TemperatureHistoryPoint(timestamp, nozzleTemp, nozzleTarget,
                                                        bedTemp, bedTarget));
            }

            Log.d(TAG, "Temperature history loaded: " + history.size() + " points");

            // Notify listeners with history
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
