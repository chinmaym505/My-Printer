package com.chinmay.myprinter.slicer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.chinmay.myprinter.R;

public class SlicerService extends Service {

    public static final String ACTION_COMPLETE    = "com.chinmay.myprinter.SLICE_COMPLETE";
    public static final String ACTION_CANCEL      = "com.chinmay.myprinter.SLICE_CANCEL";
    public static final String EXTRA_STL_PATH     = "stl_path";
    public static final String EXTRA_OUTPUT_PATH  = "output_path";
    public static final String EXTRA_RESULT       = "result";
    public static final String EXTRA_FILE_NAME    = "file_name";
    public static final String EXTRA_LAYER_HEIGHT = "layer_height";
    public static final String EXTRA_NOZZLE_DIAM  = "nozzle_diameter";
    public static final String EXTRA_PRINT_SPEED  = "print_speed";
    public static final String EXTRA_TRAVEL_SPEED = "travel_speed";
    public static final String EXTRA_INFILL_PCT   = "infill_percent";
    public static final String EXTRA_WALL_COUNT   = "wall_count";
    public static final String EXTRA_NOZZLE_TEMP         = "nozzle_temp";
    public static final String EXTRA_NOZZLE_TEMP_LAYER0  = "nozzle_temp_layer0";
    public static final String EXTRA_BED_TEMP            = "bed_temp";
    public static final String EXTRA_RETRACTION_AMOUNT   = "retraction_amount";

    public static final String ACTION_PROGRESS      = "com.chinmay.myprinter.SLICE_PROGRESS";
    public static final String EXTRA_PROGRESS_PCT   = "progress_pct";
    public static final String EXTRA_PROGRESS_STAGE = "progress_stage";

    public static final String ACTION_FILES_CHANGED = "com.chinmay.myprinter.FILES_CHANGED";

    private static final String TAG         = "SlicerService";
    private static final String CHANNEL_ID  = "slicer";
    private static final int    NOTIF_ID    = 2001;

    private CuraEngineWrapper cura;
    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        if (ACTION_CANCEL.equals(intent.getAction())) {
            if (cura != null) cura.cancel();
            return START_NOT_STICKY;
        }

        String stlPath  = intent.getStringExtra(EXTRA_STL_PATH);
        String outPath  = intent.getStringExtra(EXTRA_OUTPUT_PATH);
        String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        if (stlPath == null || outPath == null) { stopSelf(); return START_NOT_STICKY; }

        SlicerSettings s = new SlicerSettings();
        s.layerHeight    = intent.getFloatExtra(EXTRA_LAYER_HEIGHT, 0.20f);
        s.nozzleDiameter = intent.getFloatExtra(EXTRA_NOZZLE_DIAM,  0.40f);
        s.printSpeed     = intent.getFloatExtra(EXTRA_PRINT_SPEED,  50.0f);
        s.travelSpeed    = intent.getFloatExtra(EXTRA_TRAVEL_SPEED, 150.0f);
        s.infillPercent  = intent.getIntExtra  (EXTRA_INFILL_PCT,   20);
        s.wallCount      = intent.getIntExtra  (EXTRA_WALL_COUNT,   2);
        s.nozzleTemp        = intent.getFloatExtra(EXTRA_NOZZLE_TEMP,        200.0f);
        s.nozzleTempLayer0  = intent.getFloatExtra(EXTRA_NOZZLE_TEMP_LAYER0, s.nozzleTemp + 5);
        s.bedTemp           = intent.getFloatExtra(EXTRA_BED_TEMP,           60.0f);
        s.retractionAmount  = intent.getFloatExtra(EXTRA_RETRACTION_AMOUNT,  1.0f);

        String label = fileName != null ? fileName : "model";
        startForeground(NOTIF_ID, buildNotif(label, 0, "Starting…"));
        runSlice(stlPath, outPath, label, s);
        return START_NOT_STICKY;
    }

    private void runSlice(String stlPath, String outPath, String label, SlicerSettings s) {
        cura = new CuraEngineWrapper(this);

        new Thread(() -> {
            Log.d(TAG, "Slicing " + stlPath);
            String error = cura.slice(stlPath, outPath, s, (pct, stage) -> {
                handler.post(() -> updateNotif(label, pct, stage));
                Intent pg = new Intent(ACTION_PROGRESS);
                pg.setPackage(getPackageName());
                pg.putExtra(EXTRA_PROGRESS_PCT, pct);
                pg.putExtra(EXTRA_PROGRESS_STAGE, stage);
                sendBroadcast(pg);
            });

            Log.d(TAG, "Slice result: " + (error == null ? "OK" : error));

            Intent bc = new Intent(ACTION_COMPLETE);
            bc.setPackage(getPackageName());
            bc.putExtra(EXTRA_RESULT,      error == null ? "OK" : error);
            bc.putExtra(EXTRA_OUTPUT_PATH, outPath);
            bc.putExtra(EXTRA_FILE_NAME,   label);
            sendBroadcast(bc);

            stopForeground(true);
            stopSelf();
        }, "SlicerThread").start();
    }

    private Notification buildNotif(String name, int pct, String stage) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Slicing " + name)
                .setContentText(pct > 0 ? pct + "% — " + stage : stage)
                .setSmallIcon(R.drawable.ic_print)
                .setProgress(100, pct, pct == 0)
                .setOngoing(true)
                .build();
    }

    private void updateNotif(String name, int pct, String stage) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotif(name, pct, stage));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Slicing", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cura != null) cura.cancel();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
