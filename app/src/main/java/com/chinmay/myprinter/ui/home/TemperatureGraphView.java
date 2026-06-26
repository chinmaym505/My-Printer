package com.chinmay.myprinter.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TemperatureGraphView extends View {
    private static final int MAX_DATA_POINTS = 720; // 12 minutes at 1 Hz (matches history store interval)
    private static final int MAX_TEMP = 250;

    private final Paint nozzlePaint;
    private final Paint nozzleTargetPaint;
    private final Paint bedPaint;
    private final Paint bedTargetPaint;
    private final Paint gridPaint;
    private final Paint textPaint;

    private final List<Float> nozzleTemps = new ArrayList<>();
    private final List<Float> nozzleTargets = new ArrayList<>();
    private final List<Float> bedTemps = new ArrayList<>();
    private final List<Float> bedTargets = new ArrayList<>();
    private final List<Long> timestamps = new ArrayList<>();

    public TemperatureGraphView(Context context) {
        this(context, null);
    }

    public TemperatureGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Nozzle temp line (red)
        nozzlePaint = new Paint();
        nozzlePaint.setColor(Color.rgb(255, 100, 100));
        nozzlePaint.setStrokeWidth(4f);
        nozzlePaint.setStyle(Paint.Style.STROKE);
        nozzlePaint.setAntiAlias(true);

        // Nozzle target line (red, dashed)
        nozzleTargetPaint = new Paint();
        nozzleTargetPaint.setColor(Color.rgb(255, 100, 100));
        nozzleTargetPaint.setStrokeWidth(2f);
        nozzleTargetPaint.setStyle(Paint.Style.STROKE);
        nozzleTargetPaint.setPathEffect(new DashPathEffect(new float[]{10, 5}, 0));
        nozzleTargetPaint.setAntiAlias(true);

        // Bed temp line (blue)
        bedPaint = new Paint();
        bedPaint.setColor(Color.rgb(100, 150, 255));
        bedPaint.setStrokeWidth(4f);
        bedPaint.setStyle(Paint.Style.STROKE);
        bedPaint.setAntiAlias(true);

        // Bed target line (blue, dashed)
        bedTargetPaint = new Paint();
        bedTargetPaint.setColor(Color.rgb(100, 150, 255));
        bedTargetPaint.setStrokeWidth(2f);
        bedTargetPaint.setStyle(Paint.Style.STROKE);
        bedTargetPaint.setPathEffect(new DashPathEffect(new float[]{10, 5}, 0));
        bedTargetPaint.setAntiAlias(true);

        // Grid lines
        gridPaint = new Paint();
        gridPaint.setColor(Color.rgb(200, 200, 200));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        // Text
        textPaint = new Paint();
        textPaint.setColor(Color.rgb(100, 100, 100));
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);
    }

    public void addDataPoint(float nozzleTemp, float nozzleTarget, float bedTemp, float bedTarget) {
        addDataPoint(nozzleTemp, nozzleTarget, bedTemp, bedTarget, System.currentTimeMillis());
    }

    public void addDataPoint(float nozzleTemp, float nozzleTarget, float bedTemp, float bedTarget, long timestamp) {
        nozzleTemps.add(nozzleTemp);
        nozzleTargets.add(nozzleTarget);
        bedTemps.add(bedTemp);
        bedTargets.add(bedTarget);
        timestamps.add(timestamp);

        // Keep only last MAX_DATA_POINTS
        while (nozzleTemps.size() > MAX_DATA_POINTS) {
            nozzleTemps.remove(0);
            nozzleTargets.remove(0);
            bedTemps.remove(0);
            bedTargets.remove(0);
            timestamps.remove(0);
        }

        invalidate(); // Redraw
    }

    public void clear() {
        nozzleTemps.clear();
        nozzleTargets.clear();
        bedTemps.clear();
        bedTargets.clear();
        timestamps.clear();
        invalidate();
    }

    // Getter methods for saving state
    public List<Float> getNozzleTemps() { return new ArrayList<>(nozzleTemps); }
    public List<Float> getNozzleTargets() { return new ArrayList<>(nozzleTargets); }
    public List<Float> getBedTemps() { return new ArrayList<>(bedTemps); }
    public List<Float> getBedTargets() { return new ArrayList<>(bedTargets); }
    public List<Long> getTimestamps() { return new ArrayList<>(timestamps); }

    // Setter methods for restoring state
    public void restoreData(List<Float> nozzleTemps, List<Float> nozzleTargets,
                           List<Float> bedTemps, List<Float> bedTargets, List<Long> timestamps) {
        this.nozzleTemps.clear();
        this.nozzleTargets.clear();
        this.bedTemps.clear();
        this.bedTargets.clear();
        this.timestamps.clear();

        if (nozzleTemps != null) this.nozzleTemps.addAll(nozzleTemps);
        if (nozzleTargets != null) this.nozzleTargets.addAll(nozzleTargets);
        if (bedTemps != null) this.bedTemps.addAll(bedTemps);
        if (bedTargets != null) this.bedTargets.addAll(bedTargets);
        if (timestamps != null) this.timestamps.addAll(timestamps);

        invalidate();
    }

    // Visible time window: 12 minutes (6 × 2-min grid intervals)
    private static final long WINDOW_MS = 12 * 60 * 1000L;
    private static final long GRID_INTERVAL_MS = 2 * 60 * 1000L;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        // Leave room for "250°C" label on the left and rotated time labels at the bottom
        float lPad = 72f, rPad = 12f, tPad = 10f, bPad = 44f;
        float plotLeft = lPad, plotRight = w - rPad, plotTop = tPad, plotBottom = h - bPad;

        canvas.drawColor(Color.rgb(250, 250, 250));

        // Window anchored to now so the graph scrolls in real time
        long windowEnd   = System.currentTimeMillis();
        long windowStart = windowEnd - WINDOW_MS;

        // Horizontal grid lines + Y-axis labels
        for (int temp = 0; temp <= MAX_TEMP; temp += 50) {
            float y = tempToY(temp, plotTop, plotBottom);
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
            canvas.drawText(temp + "°C", 4, y + 8, textPaint);
        }

        // Vertical grid lines at 2-minute boundaries
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm", Locale.US);
        long firstGrid = ((windowStart / GRID_INTERVAL_MS) + 1) * GRID_INTERVAL_MS;
        for (long t = firstGrid; t <= windowEnd; t += GRID_INTERVAL_MS) {
            float x = timeToX(t, windowStart, windowEnd, plotLeft, plotRight);
            canvas.drawLine(x, plotTop, x, plotBottom, gridPaint);
            canvas.save();
            canvas.rotate(-45, x, plotBottom + 20);
            canvas.drawText(timeFmt.format(new Date(t)), x, plotBottom + 20, textPaint);
            canvas.restore();
        }

        if (nozzleTemps.isEmpty()) return;

        drawLine(canvas, nozzleTemps,   nozzlePaint,       windowStart, windowEnd, plotLeft, plotRight, plotTop, plotBottom);
        drawLine(canvas, nozzleTargets, nozzleTargetPaint, windowStart, windowEnd, plotLeft, plotRight, plotTop, plotBottom);
        drawLine(canvas, bedTemps,      bedPaint,          windowStart, windowEnd, plotLeft, plotRight, plotTop, plotBottom);
        drawLine(canvas, bedTargets,    bedTargetPaint,    windowStart, windowEnd, plotLeft, plotRight, plotTop, plotBottom);
    }

    private float timeToX(long t, long windowStart, long windowEnd, float left, float right) {
        return left + ((t - windowStart) / (float)(windowEnd - windowStart)) * (right - left);
    }

    private float tempToY(float temp, float top, float bottom) {
        return bottom - (temp / MAX_TEMP) * (bottom - top);
    }

    private void drawLine(Canvas canvas, List<Float> temps, Paint paint,
                          long windowStart, long windowEnd,
                          float left, float right, float top, float bottom) {
        Path path = new Path();
        boolean first = true;
        for (int i = 0; i < temps.size() && i < timestamps.size(); i++) {
            long ts = timestamps.get(i);
            if (ts < windowStart || ts > windowEnd) continue;
            float x = timeToX(ts, windowStart, windowEnd, left, right);
            float y = tempToY(temps.get(i), top, bottom);
            if (first) { path.moveTo(x, y); first = false; }
            else        { path.lineTo(x, y); }
        }
        if (!first) canvas.drawPath(path, paint);
    }
}
