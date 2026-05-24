package com.chinmay.myprinter.data.model.thingiverse;

import com.google.gson.annotations.SerializedName;

public class ThingFile {
    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("size")
    private long size;

    @SerializedName("url")
    private String url;

    @SerializedName("public_url")
    private String publicUrl;

    @SerializedName("download_url")
    private String downloadUrl;

    @SerializedName("threejs_url")
    private String threejsUrl;

    @SerializedName("thumbnail")
    private String thumbnail;

    @SerializedName("date")
    private String date;

    @SerializedName("formatted_size")
    private String formattedSize;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getThreejsUrl() {
        return threejsUrl;
    }

    public void setThreejsUrl(String threejsUrl) {
        this.threejsUrl = threejsUrl;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getFormattedSize() {
        return formattedSize;
    }

    public void setFormattedSize(String formattedSize) {
        this.formattedSize = formattedSize;
    }

    public String getFormattedSizeString() {
        if (formattedSize != null) {
            return formattedSize;
        }
        // Format size from bytes
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}
