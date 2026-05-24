package com.chinmay.myprinter.data.source.printer.moonraker.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class FileListResponse {
    @SerializedName("files")
    private List<FileItem> files;

    @SerializedName("disk_usage")
    private DiskUsage diskUsage;

    public List<FileItem> getFiles() {
        return files;
    }

    public void setFiles(List<FileItem> files) {
        this.files = files;
    }

    public DiskUsage getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(DiskUsage diskUsage) {
        this.diskUsage = diskUsage;
    }

    public static class FileItem {
        @SerializedName("path")
        private String path;

        @SerializedName("modified")
        private double modified;

        @SerializedName("size")
        private long size;

        @SerializedName("filename")
        private String filename;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public double getModified() {
            return modified;
        }

        public void setModified(double modified) {
            this.modified = modified;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }
    }

    public static class DiskUsage {
        @SerializedName("total")
        private long total;

        @SerializedName("used")
        private long used;

        @SerializedName("free")
        private long free;

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getUsed() {
            return used;
        }

        public void setUsed(long used) {
            this.used = used;
        }

        public long getFree() {
            return free;
        }

        public void setFree(long free) {
            this.free = free;
        }
    }
}
