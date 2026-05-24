package com.chinmay.myprinter.data.source.printer;

import android.os.Handler;
import android.os.Looper;

import com.chinmay.myprinter.data.model.GCodeFile;
import com.chinmay.myprinter.data.model.Position;
import com.chinmay.myprinter.data.model.PrinterState;
import com.chinmay.myprinter.data.model.PrinterStatus;
import com.chinmay.myprinter.data.model.Temperature;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MockPrinterClient implements PrinterClient {
    private final PrinterStatus currentStatus;
    private final ScheduledExecutorService scheduler;
    private final Handler mainHandler;
    private final List<PrinterStatusListener> listeners;

    private boolean connected;
    private ScheduledFuture<?> statusUpdateTask;

    // Temperature simulation
    private float currentNozzleTemp = 20f;
    private float targetNozzleTemp = 0f;
    private float currentBedTemp = 20f;
    private float targetBedTemp = 0f;

    // Print simulation
    private boolean isPrinting = false;
    private long printStartTime = 0;
    private long printDurationMs = 0;
    private int totalLayers = 0;

    public MockPrinterClient() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.listeners = new ArrayList<>();
        this.currentStatus = new PrinterStatus();
        this.connected = false;

        initializeStatus();
    }

    private void initializeStatus() {
        currentStatus.setState(PrinterState.DISCONNECTED);
        currentStatus.setConnected(false);
        currentStatus.getNozzleTemp().setCurrent(20f);
        currentStatus.getNozzleTemp().setTarget(0f);
        currentStatus.getBedTemp().setCurrent(20f);
        currentStatus.getBedTemp().setTarget(0f);
        currentStatus.getPosition().setX(0f);
        currentStatus.getPosition().setY(0f);
        currentStatus.getPosition().setZ(0f);
    }

    @Override
    public void connect(String url, String apiKey, ConnectionCallback callback) {
        // Simulate connection delay
        scheduler.schedule(() -> {
            connected = true;
            currentStatus.setConnected(true);
            currentStatus.setState(PrinterState.IDLE);

            mainHandler.post(callback::onConnected);
            startStatusUpdates();
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void disconnect() {
        if (statusUpdateTask != null) {
            statusUpdateTask.cancel(true);
        }
        connected = false;
        currentStatus.setConnected(false);
        currentStatus.setState(PrinterState.DISCONNECTED);
        notifyListeners();
    }

    @Override
    public boolean isConnected() {
        return connected;
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
        if (!connected) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Not connected"));
            }
            return;
        }

        if ("extruder".equals(heater) || "nozzle".equals(heater)) {
            targetNozzleTemp = target;
            currentStatus.getNozzleTemp().setTarget(target);
        } else if ("bed".equals(heater)) {
            targetBedTemp = target;
            currentStatus.getBedTemp().setTarget(target);
        }

        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    @Override
    public void homeAxis(String axis, CommandCallback callback) {
        if (!connected) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Not connected"));
            }
            return;
        }

        // Simulate homing delay
        scheduler.schedule(() -> {
            if ("X".equals(axis) || "all".equals(axis)) {
                currentStatus.getPosition().setX(0f);
            }
            if ("Y".equals(axis) || "all".equals(axis)) {
                currentStatus.getPosition().setY(0f);
            }
            if ("Z".equals(axis) || "all".equals(axis)) {
                currentStatus.getPosition().setZ(0f);
            }

            if (callback != null) {
                mainHandler.post(callback::onSuccess);
            }
        }, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void moveAxis(String axis, float distance, CommandCallback callback) {
        if (!connected) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Not connected"));
            }
            return;
        }

        Position pos = currentStatus.getPosition();
        if ("X".equals(axis)) {
            pos.setX(Math.max(0, Math.min(220, pos.getX() + distance)));
        } else if ("Y".equals(axis)) {
            pos.setY(Math.max(0, Math.min(220, pos.getY() + distance)));
        } else if ("Z".equals(axis)) {
            pos.setZ(Math.max(0, Math.min(250, pos.getZ() + distance)));
        }

        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    @Override
    public void startPrint(String filename, CommandCallback callback) {
        if (!connected) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Not connected"));
            }
            return;
        }

        if (isPrinting) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Already printing"));
            }
            return;
        }

        // Start heating sequence
        currentStatus.setState(PrinterState.HEATING);
        currentStatus.setCurrentFilename(filename);
        targetBedTemp = 60;
        targetNozzleTemp = 200;
        currentStatus.getBedTemp().setTarget(60);
        currentStatus.getNozzleTemp().setTarget(200);

        // Estimate print time based on filename (mock)
        printDurationMs = 60 * 60 * 1000; // 1 hour default
        totalLayers = 200;
        currentStatus.setTotalLayers(totalLayers);

        // Check temperatures and start print
        scheduler.schedule(this::checkIfReadyToPrint, 2000, TimeUnit.MILLISECONDS);

        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void checkIfReadyToPrint() {
        if (currentNozzleTemp >= targetNozzleTemp - 2 &&
            currentBedTemp >= targetBedTemp - 2) {
            // Temperatures reached, start printing
            isPrinting = true;
            printStartTime = System.currentTimeMillis();
            currentStatus.setState(PrinterState.PRINTING);
            currentStatus.setPrintProgress(0);
            currentStatus.setCurrentLayer(0);
        } else {
            // Check again in 2 seconds
            scheduler.schedule(this::checkIfReadyToPrint, 2000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void pausePrint(CommandCallback callback) {
        if (isPrinting && currentStatus.getState() == PrinterState.PRINTING) {
            currentStatus.setState(PrinterState.PAUSED);
            isPrinting = false;
            if (callback != null) {
                mainHandler.post(callback::onSuccess);
            }
        }
    }

    @Override
    public void resumePrint(CommandCallback callback) {
        if (currentStatus.getState() == PrinterState.PAUSED) {
            currentStatus.setState(PrinterState.PRINTING);
            isPrinting = true;
            // Adjust start time to account for pause
            long elapsed = System.currentTimeMillis() - printStartTime;
            printStartTime = System.currentTimeMillis() - (currentStatus.getPrintProgress() * printDurationMs / 100);

            if (callback != null) {
                mainHandler.post(callback::onSuccess);
            }
        }
    }

    @Override
    public void cancelPrint(CommandCallback callback) {
        isPrinting = false;
        currentStatus.setState(PrinterState.IDLE);
        currentStatus.setPrintProgress(0);
        currentStatus.setCurrentLayer(0);
        currentStatus.setCurrentFilename("");
        targetNozzleTemp = 0;
        targetBedTemp = 0;
        currentStatus.getNozzleTemp().setTarget(0);
        currentStatus.getBedTemp().setTarget(0);

        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    @Override
    public List<GCodeFile> listFiles() {
        // Return mock file list
        List<GCodeFile> files = new ArrayList<>();
        files.add(new GCodeFile("test_cube.gcode", 50 * 1024, "gcodes/test_cube.gcode"));
        files.add(new GCodeFile("benchy.gcode", 2 * 1024 * 1024, "gcodes/benchy.gcode"));
        files.add(new GCodeFile("calibration.gcode", 100 * 1024, "gcodes/calibration.gcode"));
        return files;
    }

    @Override
    public void deleteFile(String filename, CommandCallback callback) {
        if (callback != null) mainHandler.post(callback::onSuccess);
    }

    @Override
    public void sendGCode(String gcode, CommandCallback callback) {
        if (!connected) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Not connected"));
            }
            return;
        }

        // Simulate command execution
        scheduler.schedule(() -> {
            if (callback != null) {
                mainHandler.post(callback::onSuccess);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    private void startStatusUpdates() {
        // Send status updates every 2 seconds
        statusUpdateTask = scheduler.scheduleAtFixedRate(() -> {
            updateTemperatures();
            updatePrintProgress();
            notifyListeners();
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void updateTemperatures() {
        // Simulate heating: nozzle heats at ~1.5°C/second, bed at ~0.8°C/second
        if (currentNozzleTemp < targetNozzleTemp) {
            currentNozzleTemp += 3.0f; // 1.5°C per second * 2 second interval
            if (currentNozzleTemp > targetNozzleTemp) {
                currentNozzleTemp = targetNozzleTemp;
            }
        } else if (currentNozzleTemp > targetNozzleTemp) {
            currentNozzleTemp -= 4.0f; // Cool down faster
            if (currentNozzleTemp < targetNozzleTemp) {
                currentNozzleTemp = targetNozzleTemp;
            }
        }

        if (currentBedTemp < targetBedTemp) {
            currentBedTemp += 1.6f; // 0.8°C per second * 2 second interval
            if (currentBedTemp > targetBedTemp) {
                currentBedTemp = targetBedTemp;
            }
        } else if (currentBedTemp > targetBedTemp) {
            currentBedTemp -= 2.0f; // Cool down
            if (currentBedTemp < targetBedTemp) {
                currentBedTemp = targetBedTemp;
            }
        }

        currentStatus.getNozzleTemp().setCurrent(currentNozzleTemp);
        currentStatus.getBedTemp().setCurrent(currentBedTemp);
    }

    private void updatePrintProgress() {
        if (!isPrinting || currentStatus.getState() != PrinterState.PRINTING) {
            return;
        }

        long elapsed = System.currentTimeMillis() - printStartTime;
        currentStatus.setPrintDuration(elapsed);

        float progress = (float) elapsed / printDurationMs;

        if (progress >= 1.0f) {
            // Print complete
            isPrinting = false;
            currentStatus.setState(PrinterState.COMPLETE);
            currentStatus.setPrintProgress(100);
            currentStatus.setCurrentLayer(totalLayers);
            targetNozzleTemp = 0;
            targetBedTemp = 0;
            currentStatus.getNozzleTemp().setTarget(0);
            currentStatus.getBedTemp().setTarget(0);
        } else {
            currentStatus.setPrintProgress((int) (progress * 100));
            currentStatus.setCurrentLayer((int) (progress * totalLayers));
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (PrinterStatusListener listener : listeners) {
                listener.onStatusUpdate(currentStatus);
            }
        });
    }

    public void shutdown() {
        if (statusUpdateTask != null) {
            statusUpdateTask.cancel(true);
        }
        scheduler.shutdown();
    }
}
