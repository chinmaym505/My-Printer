package com.chinmay.myprinter.data.model.thingiverse;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ThingImage {
    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("url")
    private String url;

    @SerializedName("sizes")
    private List<ImageSize> sizes;

    public long getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public List<ImageSize> getSizes() { return sizes; }

    public String getBestUrl() {
        if (sizes != null) {
            // Prefer "large", fall back to "medium", then "small", then "thumb"
            for (String preferred : new String[]{"large", "medium", "small", "thumb"}) {
                for (ImageSize size : sizes) {
                    if (preferred.equals(size.getType()) && size.getUrl() != null) {
                        return size.getUrl();
                    }
                }
            }
        }
        return url;
    }

    public static class ImageSize {
        @SerializedName("type")
        private String type;

        @SerializedName("size")
        private String size;

        @SerializedName("url")
        private String url;

        public String getType() { return type; }
        public String getSize() { return size; }
        public String getUrl() { return url; }
    }
}
