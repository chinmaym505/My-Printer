package com.chinmay.myprinter.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.chinmay.myprinter.data.model.thingiverse.SearchResponse;
import com.chinmay.myprinter.data.model.thingiverse.Thing;
import com.chinmay.myprinter.data.model.thingiverse.ThingFile;
import com.chinmay.myprinter.data.model.thingiverse.ThingImage;
import com.chinmay.myprinter.data.source.remote.FlexibleBooleanAdapter;
import com.chinmay.myprinter.data.source.remote.ThingiverseApiService;
import com.chinmay.myprinter.data.source.remote.ThingiverseAuthInterceptor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

import android.util.Log;

import java.io.IOException;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ThingiverseRepository {
    private static final String TAG = "ThingiverseRepo";
    private final ThingiverseApiService apiService;
    private final ThingiverseAuthInterceptor authInterceptor;
    private final MutableLiveData<List<Thing>> thingsLiveData;
    private final MutableLiveData<Thing> thingDetailsLiveData;
    private final MutableLiveData<List<ThingFile>> filesLiveData;
    private final MutableLiveData<List<ThingImage>> imagesLiveData;
    private final MutableLiveData<String> errorLiveData;
    private final MutableLiveData<Boolean> loadingLiveData;

    public ThingiverseRepository(String accessToken) {
        this.authInterceptor = new ThingiverseAuthInterceptor(accessToken);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Boolean.class, new FlexibleBooleanAdapter())
                .registerTypeAdapter(boolean.class, new FlexibleBooleanAdapter())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ThingiverseApiService.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        apiService = retrofit.create(ThingiverseApiService.class);

        thingsLiveData = new MutableLiveData<>();
        thingDetailsLiveData = new MutableLiveData<>();
        filesLiveData = new MutableLiveData<>();
        imagesLiveData = new MutableLiveData<>();
        errorLiveData = new MutableLiveData<>();
        loadingLiveData = new MutableLiveData<>(false);
    }

    public LiveData<List<Thing>> getThings() {
        return thingsLiveData;
    }

    public LiveData<Thing> getThingDetails() {
        return thingDetailsLiveData;
    }

    public LiveData<List<ThingFile>> getFiles() {
        return filesLiveData;
    }

    public LiveData<List<ThingImage>> getImages() {
        return imagesLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    public void setAccessToken(String accessToken) {
        authInterceptor.setAccessToken(accessToken);
    }

    public void searchThings(String query, int page, int perPage) {
        loadingLiveData.postValue(true);
        apiService.search(query, page, perPage).enqueue(new Callback<SearchResponse>() {
            @Override
            public void onResponse(Call<SearchResponse> call, Response<SearchResponse> response) {
                loadingLiveData.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    thingsLiveData.postValue(response.body().getHits());
                } else {
                    errorLiveData.postValue("Search failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<SearchResponse> call, Throwable t) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue("Search failed: " + t.getMessage());
            }
        });
    }

    public void getNewestThings(int page, int perPage) {
        loadingLiveData.postValue(true);
        apiService.getNewest(page, perPage).enqueue(new Callback<List<Thing>>() {
            @Override
            public void onResponse(Call<List<Thing>> call, Response<List<Thing>> response) {
                loadingLiveData.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    thingsLiveData.postValue(response.body());
                } else {
                    errorLiveData.postValue("Failed to load newest: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Thing>> call, Throwable t) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue("Failed to load newest: " + t.getMessage());
            }
        });
    }

    public void getPopularThings(int page, int perPage) {
        loadingLiveData.postValue(true);
        apiService.getPopular(page, perPage).enqueue(new Callback<List<Thing>>() {
            @Override
            public void onResponse(Call<List<Thing>> call, Response<List<Thing>> response) {
                loadingLiveData.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    thingsLiveData.postValue(response.body());
                } else {
                    errorLiveData.postValue("Failed to load popular: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Thing>> call, Throwable t) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue("Failed to load popular: " + t.getMessage());
            }
        });
    }

    public void getFeaturedThings(int page, int perPage) {
        loadingLiveData.postValue(true);
        apiService.getFeatured(page, perPage).enqueue(new Callback<List<Thing>>() {
            @Override
            public void onResponse(Call<List<Thing>> call, Response<List<Thing>> response) {
                loadingLiveData.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    thingsLiveData.postValue(response.body());
                } else {
                    errorLiveData.postValue("Failed to load featured: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Thing>> call, Throwable t) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue("Failed to load featured: " + t.getMessage());
            }
        });
    }

    public void getThingDetails(long thingId) {
        loadingLiveData.postValue(true);
        apiService.getThing(thingId).enqueue(new Callback<Thing>() {
            @Override
            public void onResponse(Call<Thing> call, Response<Thing> response) {
                loadingLiveData.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    thingDetailsLiveData.postValue(response.body());
                    getThingFiles(thingId);
                    getThingImages(thingId);
                } else {
                    String errorBody = "";
                    try { if (response.errorBody() != null) errorBody = response.errorBody().string(); } catch (IOException ignored) {}
                    String msg = "Details error " + response.code() + ": " + errorBody;
                    Log.e(TAG, msg);
                    errorLiveData.postValue(msg);
                }
            }

            @Override
            public void onFailure(Call<Thing> call, Throwable t) {
                loadingLiveData.postValue(false);
                Log.e(TAG, "Details failure", t);
                errorLiveData.postValue("Details failure: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        });
    }

    public void getThingFiles(long thingId) {
        apiService.getThingFiles(thingId).enqueue(new Callback<List<ThingFile>>() {
            @Override
            public void onResponse(Call<List<ThingFile>> call, Response<List<ThingFile>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    filesLiveData.postValue(response.body());
                } else {
                    String errorBody = "";
                    try { if (response.errorBody() != null) errorBody = response.errorBody().string(); } catch (IOException ignored) {}
                    Log.e(TAG, "Files error " + response.code() + ": " + errorBody);
                    errorLiveData.postValue("Files error " + response.code() + ": " + errorBody);
                }
            }

            @Override
            public void onFailure(Call<List<ThingFile>> call, Throwable t) {
                Log.e(TAG, "Files failure", t);
                errorLiveData.postValue("Files failure: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        });
    }

    public void getThingImages(long thingId) {
        apiService.getThingImages(thingId).enqueue(new Callback<List<ThingImage>>() {
            @Override
            public void onResponse(Call<List<ThingImage>> call, Response<List<ThingImage>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    imagesLiveData.postValue(response.body());
                } else {
                    String errorBody = "";
                    try { if (response.errorBody() != null) errorBody = response.errorBody().string(); } catch (IOException ignored) {}
                    Log.e(TAG, "Images error " + response.code() + ": " + errorBody);
                    errorLiveData.postValue("Images error " + response.code() + ": " + errorBody);
                }
            }

            @Override
            public void onFailure(Call<List<ThingImage>> call, Throwable t) {
                Log.e(TAG, "Images failure", t);
                errorLiveData.postValue("Images failure: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        });
    }
}
