package com.chinmay.myprinter.slicer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GCodeUploader {

    public interface Callback {
        void onSuccess(String filename);
        void onFailure(String error);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void upload(String printerBaseUrl, File gcodeFile,
                              boolean startAfterUpload, Callback callback) {
        executor.execute(() -> {
            try {
                String base = printerBaseUrl.endsWith("/")
                        ? printerBaseUrl.substring(0, printerBaseUrl.length() - 1)
                        : printerBaseUrl;

                RequestBody body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("root", "gcodes")
                        .addFormDataPart("file", gcodeFile.getName(),
                                RequestBody.create(
                                        MediaType.parse("application/octet-stream"),
                                        gcodeFile))
                        .build();

                Request upload = new Request.Builder()
                        .url(base + "/server/files/upload")
                        .post(body)
                        .build();

                try (Response resp = client.newCall(upload).execute()) {
                    if (!resp.isSuccessful()) {
                        callback.onFailure("Upload failed: HTTP " + resp.code());
                        return;
                    }
                }

                if (startAfterUpload) {
                    Request start = new Request.Builder()
                            .url(base + "/printer/print/start?filename=" + gcodeFile.getName())
                            .post(RequestBody.create(null, new byte[0]))
                            .build();
                    try (Response resp = client.newCall(start).execute()) {
                        // Non-fatal if start fails (printer may be busy)
                        if (!resp.isSuccessful()) {
                            callback.onSuccess(gcodeFile.getName() + " (uploaded; start failed: HTTP " + resp.code() + ")");
                            return;
                        }
                    }
                }

                callback.onSuccess(gcodeFile.getName());
            } catch (IOException e) {
                callback.onFailure("Upload error: " + e.getMessage());
            }
        });
    }
}
