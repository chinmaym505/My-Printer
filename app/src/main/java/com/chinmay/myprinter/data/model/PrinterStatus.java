package com.chinmay.myprinter.data.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PrinterStatus {
    private PrinterState state;
    private boolean connected;
    private Temperature nozzleTemp;
    private Temperature bedTemp;
    private Position position;
    private int printProgress;
    private float printProgressFloat; // 0.0–1.0 raw value from virtual_sdcard
    private int currentLayer;
    private int totalLayers;
    private String currentFilename;
    private long printDuration; // seconds, raw from Moonraker print_stats.print_duration
    private long totalDuration; // seconds (estimated)
    private long filamentUsed; // mm

    public PrinterStatus() {
        this.state = PrinterState.DISCONNECTED;
        this.connected = false;
        this.nozzleTemp = new Temperature();
        this.bedTemp = new Temperature();
        this.position = new Position();
        this.printProgress = 0;
        this.printProgressFloat = 0f;
        this.currentLayer = 0;
        this.totalLayers = 0;
        this.currentFilename = "";
        this.printDuration = 0;
        this.totalDuration = 0;
        this.filamentUsed = 0;
    }

    public PrinterState getState() {
        return state;
    }

    public void setState(PrinterState state) {
        this.state = state;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public Temperature getNozzleTemp() {
        return nozzleTemp;
    }

    public void setNozzleTemp(Temperature nozzleTemp) {
        this.nozzleTemp = nozzleTemp;
    }

    public Temperature getBedTemp() {
        return bedTemp;
    }

    public void setBedTemp(Temperature bedTemp) {
        this.bedTemp = bedTemp;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public int getPrintProgress() {
        return printProgress;
    }

    public void setPrintProgress(int printProgress) {
        this.printProgress = printProgress;
    }

    public int getCurrentLayer() {
        return currentLayer;
    }

    public void setCurrentLayer(int currentLayer) {
        this.currentLayer = currentLayer;
    }

    public int getTotalLayers() {
        return totalLayers;
    }

    public void setTotalLayers(int totalLayers) {
        this.totalLayers = totalLayers;
    }

    public String getCurrentFilename() {
        return currentFilename;
    }

    public void setCurrentFilename(String currentFilename) {
        this.currentFilename = currentFilename;
    }

    public long getPrintDuration() {
        return printDuration;
    }

    public void setPrintDuration(long printDuration) {
        this.printDuration = printDuration;
    }

    public float getPrintProgressFloat() {
        return printProgressFloat;
    }

    public void setPrintProgressFloat(float progress) {
        this.printProgressFloat = progress;
        this.printProgress = Math.round(progress * 100);
    }

    public long getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(long totalDuration) {
        this.totalDuration = totalDuration;
    }

    public long getFilamentUsed() {
        return filamentUsed;
    }

    public void setFilamentUsed(long filamentUsed) {
        this.filamentUsed = filamentUsed;
    }

    // Calculate time remaining in seconds: elapsed * (1 - progress) / progress
    public long getTimeRemaining() {
        if (printProgressFloat > 0f && printProgressFloat < 1f) {
            return (long)(printDuration * (1.0f - printProgressFloat) / printProgressFloat);
        }
        return 0;
    }

    // Get estimated finish time as timestamp
    public long getEstimatedFinishTime() {
        if (getTimeRemaining() > 0) {
            return System.currentTimeMillis() + (getTimeRemaining() * 1000);
        }
        return 0;
    }

    // Format duration as HH:MM:SS or MM:SS
    public static String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format(Locale.US, "%d:%02d", minutes, seconds % 60);
        }
    }

    public String getFormattedDuration() {
        return formatDuration(printDuration);
    }

    public String getFormattedTimeRemaining() {
        return formatDuration(getTimeRemaining());
    }

    public String getFormattedEstimatedFinishTime() {
        long finishTime = getEstimatedFinishTime();
        if (finishTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
            return sdf.format(new Date(finishTime));
        }
        return "--:--";
    }

    public String getFormattedEstimatedFinishDate() {
        long finishTime = getEstimatedFinishTime();
        if (finishTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.US);
            Date finishDate = new Date(finishTime);
            Date today = new Date();

            // Check if it's today or tomorrow
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
            String finishDay = dayFormat.format(finishDate);
            String todayDay = dayFormat.format(today);

            if (finishDay.equals(todayDay)) {
                return "Today";
            }

            return sdf.format(finishDate);
        }
        return "";
    }
}
