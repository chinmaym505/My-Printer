package com.chinmay.myprinter.ui.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class CameraView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CameraView";

    private SurfaceHolder holder;
    private CameraThread cameraThread;
    private Paint paint;
    private boolean surfaceCreated = false;
    private String streamUrl;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
        holder.addCallback(this);
        paint = new Paint();
        paint.setAntiAlias(true);
    }

    public void setStreamUrl(String url) {
        this.streamUrl = url;
        if (surfaceCreated && url != null && !url.isEmpty()) {
            startStream();
        }
    }

    public void stopStream() {
        if (cameraThread != null) {
            cameraThread.interrupt();
            cameraThread = null;
        }
    }

    private void startStream() {
        stopStream();
        if (streamUrl != null && !streamUrl.isEmpty()) {
            cameraThread = new CameraThread();
            cameraThread.start();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceCreated = true;
        if (streamUrl != null && !streamUrl.isEmpty()) {
            startStream();
        } else {
            drawPlaceholder("No camera configured");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Surface changed
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceCreated = false;
        stopStream();
    }

    private void drawPlaceholder(String message) {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.BLACK);
                paint.setColor(Color.WHITE);
                paint.setTextSize(40);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(message, getWidth() / 2, getHeight() / 2, paint);
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void drawFrame(Bitmap bitmap) {
        if (bitmap == null) return;

        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                // Calculate scaling to fit view while maintaining aspect ratio
                int viewWidth = getWidth();
                int viewHeight = getHeight();
                int bitmapWidth = bitmap.getWidth();
                int bitmapHeight = bitmap.getHeight();

                float scaleX = (float) viewWidth / bitmapWidth;
                float scaleY = (float) viewHeight / bitmapHeight;
                float scale = Math.min(scaleX, scaleY);

                int scaledWidth = (int) (bitmapWidth * scale);
                int scaledHeight = (int) (bitmapHeight * scale);

                int left = (viewWidth - scaledWidth) / 2;
                int top = (viewHeight - scaledHeight) / 2;

                canvas.drawColor(Color.BLACK);
                Rect destRect = new Rect(left, top, left + scaledWidth, top + scaledHeight);
                canvas.drawBitmap(bitmap, null, destRect, paint);
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private class CameraThread extends Thread {
        @Override
        public void run() {
            HttpURLConnection connection = null;
            MjpegInputStream mjpegInputStream = null;

            try {
                URL url = new URL(streamUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                mjpegInputStream = new MjpegInputStream(connection.getInputStream());

                while (!isInterrupted() && surfaceCreated) {
                    try {
                        Bitmap bitmap = mjpegInputStream.readMjpegFrame();
                        if (bitmap != null) {
                            drawFrame(bitmap);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading frame", e);
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to camera stream", e);
                post(() -> drawPlaceholder("Camera connection failed"));
            } finally {
                if (mjpegInputStream != null) {
                    try {
                        mjpegInputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing stream", e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
