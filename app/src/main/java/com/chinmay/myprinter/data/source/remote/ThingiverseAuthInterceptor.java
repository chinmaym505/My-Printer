package com.chinmay.myprinter.data.source.remote;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ThingiverseAuthInterceptor implements Interceptor {
    private String accessToken;

    public ThingiverseAuthInterceptor(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        // Don't add auth if no token
        if (accessToken == null || accessToken.isEmpty()) {
            return chain.proceed(original);
        }

        Request.Builder requestBuilder = original.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .method(original.method(), original.body());

        Request request = requestBuilder.build();
        return chain.proceed(request);
    }
}
