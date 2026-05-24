package com.chinmay.myprinter.data.model;

public class GCodeFile {
    private String filename;
    private long size;
    private String path;

    public GCodeFile() {
    }

    public GCodeFile(String filename, long size) {
        this.filename = filename;
        this.size = size;
    }

    public GCodeFile(String filename, long size, String path) {
        this.filename = filename;
        this.size = size;
        this.path = path;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}
