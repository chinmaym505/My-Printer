package com.chinmay.myprinter.data.model.thingiverse;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Thing {
    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("thumbnail")
    private String thumbnail;

    @SerializedName("url")
    private String url;

    @SerializedName("public_url")
    private String publicUrl;

    @SerializedName("creator")
    private Creator creator;

    @SerializedName("description")
    private String description;

    @SerializedName("like_count")
    private int likeCount;

    @SerializedName("download_count")
    private int downloadCount;

    @SerializedName("view_count")
    private int viewCount;

    @SerializedName("is_liked")
    private boolean isLiked;

    @SerializedName("is_published")
    private boolean isPublished;

    @SerializedName("added")
    private String added;

    @SerializedName("modified")
    private String modified;

    @SerializedName("default_image")
    private ThingImage defaultImage;

    // Getters and setters
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

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
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

    public Creator getCreator() {
        return creator;
    }

    public void setCreator(Creator creator) {
        this.creator = creator;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public boolean isLiked() {
        return isLiked;
    }

    public void setLiked(boolean liked) {
        isLiked = liked;
    }

    public boolean isPublished() {
        return isPublished;
    }

    public void setPublished(boolean published) {
        isPublished = published;
    }

    public String getAdded() {
        return added;
    }

    public void setAdded(String added) {
        this.added = added;
    }

    public String getModified() {
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
    }

    public ThingImage getDefaultImage() {
        return defaultImage;
    }

    public void setDefaultImage(ThingImage defaultImage) {
        this.defaultImage = defaultImage;
    }

    public static class Creator {
        @SerializedName("id")
        private long id;

        @SerializedName("name")
        private String name;

        @SerializedName("thumbnail")
        private String thumbnail;

        @SerializedName("url")
        private String url;

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

        public String getThumbnail() {
            return thumbnail;
        }

        public void setThumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
