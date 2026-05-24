package com.chinmay.myprinter.data.source.remote;

import com.chinmay.myprinter.data.model.thingiverse.SearchResponse;
import com.chinmay.myprinter.data.model.thingiverse.Thing;
import com.chinmay.myprinter.data.model.thingiverse.ThingFile;
import com.chinmay.myprinter.data.model.thingiverse.ThingImage;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ThingiverseApiService {
    String BASE_URL = "https://api.thingiverse.com/";

    /**
     * Search for things
     */
    @GET("search/{term}")
    Call<SearchResponse> search(
            @Path("term") String term,
            @Query("page") int page,
            @Query("per_page") int perPage
    );

    /**
     * Get newest things
     */
    @GET("newest")
    Call<List<Thing>> getNewest(
            @Query("page") int page,
            @Query("per_page") int perPage
    );

    /**
     * Get popular things
     */
    @GET("popular")
    Call<List<Thing>> getPopular(
            @Query("page") int page,
            @Query("per_page") int perPage
    );

    /**
     * Get featured things
     */
    @GET("featured")
    Call<List<Thing>> getFeatured(
            @Query("page") int page,
            @Query("per_page") int perPage
    );

    /**
     * Get thing details
     */
    @GET("things/{id}")
    Call<Thing> getThing(@Path("id") long thingId);

    /**
     * Get files for a thing
     */
    @GET("things/{id}/files")
    Call<List<ThingFile>> getThingFiles(@Path("id") long thingId);

    /**
     * Get images for a thing
     */
    @GET("things/{id}/images")
    Call<List<ThingImage>> getThingImages(@Path("id") long thingId);
}
