package com.chinmay.myprinter.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.chinmay.myprinter.R;
import com.chinmay.myprinter.data.model.PrinterState;
import com.chinmay.myprinter.data.model.PrinterStatus;
import com.chinmay.myprinter.data.model.Temperature;
import com.chinmay.myprinter.ui.main.MainActivity;

public class PrintProgressNotificationManager {

    static final String CHANNEL_PROGRESS = "print_progress";
    static final String CHANNEL_EVENTS   = "print_events";
    static final int    NOTIF_PROGRESS   = 1001;
    static final int    NOTIF_EVENT      = 1002;
    static final int    NOTIF_HEATING    = 1003;

    static final String ACTION_PAUSE    = "com.chinmay.myprinter.PRINT_PAUSE";
    static final String ACTION_RESUME   = "com.chinmay.myprinter.PRINT_RESUME";
    static final String ACTION_CANCEL   = "com.chinmay.myprinter.PRINT_CANCEL";
    static final String ACTION_HEAT_OFF = "com.chinmay.myprinter.HEAT_OFF";

    private final Context context;
    private final NotificationManagerCompat nm;
    private boolean progressVisible = false;
    private boolean heatingVisible  = false;

    public PrintProgressNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.nm = NotificationManagerCompat.from(this.context);
        createChannels();
    }

    /** Called from PrinterRepository.CombinedListener on every status poll (background thread OK). */
    public void onStatusUpdate(PrinterStatus status) {
        switch (status.getState()) {
            case PRINTING:
            case HEATING:
                cancelHeating();
                postProgress(status, false);
                break;
            case PAUSED:
                cancelHeating();
                postProgress(status, true);
                break;
            case COMPLETE:
                cancelHeating();
                cancelProgress();
                postEvent("Print complete!",
                        formatFilename(status.getCurrentFilename()) + " finished printing.",
                        false);
                break;
            case ERROR:
                cancelHeating();
                if (progressVisible) {
                    cancelProgress();
                    postEvent("Print error", "Check the printer for details.", true);
                }
                break;
            case IDLE:
                cancelProgress();
                if (hasActiveHeater(status)) {
                    postHeating(status);
                } else {
                    cancelHeating();
                }
                break;
            case DISCONNECTED:
                cancelProgress();
                cancelHeating();
                break;
        }
    }

    public void dismiss() {
        nm.cancel(NOTIF_PROGRESS);
        nm.cancel(NOTIF_EVENT);
        nm.cancel(NOTIF_HEATING);
        progressVisible = false;
        heatingVisible  = false;
    }

    // -----------------------------------------------------------------------

    private void postProgress(PrinterStatus status, boolean paused) {
        if (!hasPermission()) return;

        String filename = formatFilename(status.getCurrentFilename());
        int    progress = status.getPrintProgress();
        int    layer    = status.getCurrentLayer();
        int    total    = status.getTotalLayers();

        // "Layer 45/120  ·  37%"
        StringBuilder content = new StringBuilder();
        if (layer > 0 && total > 0) {
            content.append("Layer ").append(layer).append('/').append(total).append("  ·  ");
        }
        content.append(progress).append('%');

        // "~1:23 remaining  ·  done at 3:45 PM"
        String subText = null;
        if (status.getPrintProgressFloat() > 0.02f) {
            String remaining = status.getFormattedTimeRemaining();
            String at        = status.getFormattedEstimatedFinishTime();
            String day       = status.getFormattedEstimatedFinishDate();
            subText = "~" + remaining + " remaining  ·  "
                    + ("Today".equals(day) ? "" : day + " ")
                    + "done at " + at;
        }

        // Tap → open app
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, 10, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_print)
                .setContentTitle(paused ? "Paused — " + filename : filename)
                .setContentText(content.toString())
                .setProgress(100, progress, false)
                .setOngoing(!paused)          // can't swipe away while actively printing
                .setOnlyAlertOnce(true)       // no sound/vibration on each progress update
                .setContentIntent(openPi)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (subText != null) b.setSubText(subText);

        if (paused) {
            b.addAction(0, "Resume", actionPi(ACTION_RESUME, 12));
        } else {
            b.addAction(0, "Pause",  actionPi(ACTION_PAUSE,  11));
        }
        b.addAction(0, "Cancel", actionPi(ACTION_CANCEL, 13));

        nm.notify(NOTIF_PROGRESS, b.build());
        progressVisible = true;
    }

    private void postEvent(String title, String text, boolean isError) {
        if (!hasPermission()) return;
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, 10, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        nm.notify(NOTIF_EVENT,
                new NotificationCompat.Builder(context, CHANNEL_EVENTS)
                        .setSmallIcon(R.drawable.ic_print)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(openPi)
                        .setAutoCancel(true)
                        .setCategory(isError ? NotificationCompat.CATEGORY_ERROR
                                             : NotificationCompat.CATEGORY_STATUS)
                        .build());
    }

    private void postHeating(PrinterStatus status) {
        if (!hasPermission()) return;

        Temperature nozzle = status.getNozzleTemp();
        Temperature bed    = status.getBedTemp();

        boolean nozzleActive = nozzle.getTarget() > 0;
        boolean bedActive    = bed.getTarget()    > 0;
        boolean nozzleReady  = nozzleActive && nozzle.getCurrent() >= nozzle.getTarget() - 2f;
        boolean bedReady     = bedActive    && bed.getCurrent()    >= bed.getTarget()    - 2f;

        // "Nozzle  145°C → 220°C    Bed  60°C → 80°C"
        StringBuilder content = new StringBuilder();
        if (nozzleActive) {
            content.append("Nozzle  ").append((int) nozzle.getCurrent()).append("°C");
            if (!nozzleReady) content.append(" → ").append((int) nozzle.getTarget()).append("°C");
            else              content.append(" ✓");
        }
        if (nozzleActive && bedActive) content.append("    ");
        if (bedActive) {
            content.append("Bed  ").append((int) bed.getCurrent()).append("°C");
            if (!bedReady) content.append(" → ").append((int) bed.getTarget()).append("°C");
            else           content.append(" ✓");
        }

        // Progress = average fraction across active heaters
        int progress = 0, count = 0;
        if (nozzleActive && nozzle.getTarget() > 0) {
            progress += Math.min(100, (int)(nozzle.getCurrent() / nozzle.getTarget() * 100));
            count++;
        }
        if (bedActive && bed.getTarget() > 0) {
            progress += Math.min(100, (int)(bed.getCurrent() / bed.getTarget() * 100));
            count++;
        }
        if (count > 0) progress /= count;

        boolean allReady = (!nozzleActive || nozzleReady) && (!bedActive || bedReady);
        String title = allReady ? "Ready to print" : "Heating";

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(context, 14, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent heatOffIntent = new Intent(ACTION_HEAT_OFF);
        heatOffIntent.setPackage(context.getPackageName());
        PendingIntent heatOffPi = PendingIntent.getBroadcast(context, 15, heatOffIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        nm.notify(NOTIF_HEATING,
                new NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                        .setSmallIcon(R.drawable.ic_print)
                        .setContentTitle(title)
                        .setContentText(content.toString())
                        .setProgress(100, progress, false)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setContentIntent(openPi)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(0, "Turn off", heatOffPi)
                        .build());
        heatingVisible = true;
    }

    private void cancelHeating() {
        if (heatingVisible) {
            nm.cancel(NOTIF_HEATING);
            heatingVisible = false;
        }
    }

    private boolean hasActiveHeater(PrinterStatus status) {
        return status.getNozzleTemp().getTarget() > 0
            || status.getBedTemp().getTarget()    > 0;
    }

    private void cancelProgress() {
        if (progressVisible) {
            nm.cancel(NOTIF_PROGRESS);
            progressVisible = false;
        }
    }

    private PendingIntent actionPi(String action, int requestCode) {
        Intent i = new Intent(action);
        i.setPackage(context.getPackageName());
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static String formatFilename(String path) {
        if (path == null || path.isEmpty()) return "Unknown file";
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        if (path.endsWith(".gcode")) path = path.substring(0, path.length() - 6);
        return path;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mgr =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // Silent channel — updated on every status poll, never makes noise
            NotificationChannel progressCh = new NotificationChannel(
                    CHANNEL_PROGRESS, "Print Progress", NotificationManager.IMPORTANCE_LOW);
            progressCh.setDescription("Live progress bar while a print job is running");
            progressCh.setSound(null, null);

            // Default-importance channel — used only for completion and error alerts
            NotificationChannel eventsCh = new NotificationChannel(
                    CHANNEL_EVENTS, "Print Alerts", NotificationManager.IMPORTANCE_DEFAULT);
            eventsCh.setDescription("Alerts when a print finishes or encounters an error");

            mgr.createNotificationChannel(progressCh);
            mgr.createNotificationChannel(eventsCh);
        }
    }
}
