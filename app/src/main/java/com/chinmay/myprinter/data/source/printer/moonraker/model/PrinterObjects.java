package com.chinmay.myprinter.data.source.printer.moonraker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class PrinterObjects {
    @SerializedName("status")
    private StatusData status;

    @SerializedName("eventtime")
    private double eventtime;

    public StatusData getStatus() {
        return status;
    }

    public void setStatus(StatusData status) {
        this.status = status;
    }

    public double getEventtime() {
        return eventtime;
    }

    public void setEventtime(double eventtime) {
        this.eventtime = eventtime;
    }

    public static class StatusData {
        @SerializedName("extruder")
        private HeaterData extruder;

        @SerializedName("heater_bed")
        private HeaterData heaterBed;

        @SerializedName("toolhead")
        private ToolheadData toolhead;

        @SerializedName("print_stats")
        private PrintStatsData printStats;

        @SerializedName("virtual_sdcard")
        private VirtualSdcardData virtualSdcard;

        public HeaterData getExtruder() {
            return extruder;
        }

        public void setExtruder(HeaterData extruder) {
            this.extruder = extruder;
        }

        public HeaterData getHeaterBed() {
            return heaterBed;
        }

        public void setHeaterBed(HeaterData heaterBed) {
            this.heaterBed = heaterBed;
        }

        public ToolheadData getToolhead() {
            return toolhead;
        }

        public void setToolhead(ToolheadData toolhead) {
            this.toolhead = toolhead;
        }

        public PrintStatsData getPrintStats() {
            return printStats;
        }

        public void setPrintStats(PrintStatsData printStats) {
            this.printStats = printStats;
        }

        public VirtualSdcardData getVirtualSdcard() {
            return virtualSdcard;
        }

        public void setVirtualSdcard(VirtualSdcardData virtualSdcard) {
            this.virtualSdcard = virtualSdcard;
        }
    }

    public static class VirtualSdcardData {
        @SerializedName("progress")
        private Float progress;

        @SerializedName("file_position")
        private Long filePosition;

        @SerializedName("is_active")
        private Boolean isActive;

        public Float getProgress() {
            return progress;
        }

        public Long getFilePosition() {
            return filePosition;
        }

        public Boolean getIsActive() {
            return isActive;
        }
    }

    public static class HeaterData {
        @SerializedName("temperature")
        private Float temperature;

        @SerializedName("target")
        private Float target;

        @SerializedName("power")
        private Float power;

        public Float getTemperature() {
            return temperature;
        }

        public void setTemperature(Float temperature) {
            this.temperature = temperature;
        }

        public Float getTarget() {
            return target;
        }

        public void setTarget(Float target) {
            this.target = target;
        }

        public Float getPower() {
            return power;
        }

        public void setPower(Float power) {
            this.power = power;
        }
    }

    public static class ToolheadData {
        @SerializedName("position")
        private List<Float> position;

        @SerializedName("homed_axes")
        private String homedAxes;

        public List<Float> getPosition() {
            return position;
        }

        public void setPosition(List<Float> position) {
            this.position = position;
        }

        public String getHomedAxes() {
            return homedAxes;
        }

        public void setHomedAxes(String homedAxes) {
            this.homedAxes = homedAxes;
        }
    }

    public static class PrintStatsData {
        @SerializedName("state")
        private String state;

        @SerializedName("filename")
        private String filename;

        @SerializedName("total_duration")
        private float totalDuration;

        @SerializedName("print_duration")
        private float printDuration;

        @SerializedName("filament_used")
        private float filamentUsed;

        @SerializedName("info")
        private PrintInfoData info;

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public float getTotalDuration() {
            return totalDuration;
        }

        public void setTotalDuration(float totalDuration) {
            this.totalDuration = totalDuration;
        }

        public float getPrintDuration() {
            return printDuration;
        }

        public void setPrintDuration(float printDuration) {
            this.printDuration = printDuration;
        }

        public float getFilamentUsed() {
            return filamentUsed;
        }

        public void setFilamentUsed(float filamentUsed) {
            this.filamentUsed = filamentUsed;
        }

        public PrintInfoData getInfo() {
            return info;
        }

        public void setInfo(PrintInfoData info) {
            this.info = info;
        }
    }

    public static class PrintInfoData {
        @SerializedName("current_layer")
        private int currentLayer;

        @SerializedName("total_layer")
        private int totalLayer;

        public int getCurrentLayer() {
            return currentLayer;
        }

        public void setCurrentLayer(int currentLayer) {
            this.currentLayer = currentLayer;
        }

        public int getTotalLayer() {
            return totalLayer;
        }

        public void setTotalLayer(int totalLayer) {
            this.totalLayer = totalLayer;
        }
    }
}
