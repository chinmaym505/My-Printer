package com.chinmay.myprinter.data.model.thingiverse;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SearchResponse {
    @SerializedName("hits")
    private List<Thing> hits;

    @SerializedName("total")
    private int total;

    public List<Thing> getHits() {
        return hits;
    }

    public void setHits(List<Thing> hits) {
        this.hits = hits;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
