package com.chinmay.myprinter.slicer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FileDownloader {

    public interface Callback {
        void onSuccess(File file);
        void onFailure(String error);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final OkHttpClient client = new OkHttpClient();

    public static void download(String url, File dest, String authToken, Callback cb) {
        executor.execute(() -> {
            try {
                dest.getParentFile().mkdirs();

                Request.Builder builder = new Request.Builder().url(url);
                if (authToken != null && !authToken.isEmpty()) {
                    builder.addHeader("Authorization", "Bearer " + authToken);
                }

                try (Response resp = client.newCall(builder.build()).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        cb.onFailure("Download failed: HTTP " + resp.code());
                        return;
                    }
                    try (InputStream in = resp.body().byteStream();
                         FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    }
                }
                cb.onSuccess(dest);
            } catch (IOException e) {
                cb.onFailure("Download error: " + e.getMessage());
            }
        });
    }
}
