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
    private static final int MAX_DATA_POINTS = 360; // 12 minutes at 2 sec intervals (6 grid squares * 2 min)
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int padding = 40;

        // Draw background
        canvas.drawColor(Color.rgb(250, 250, 250));

        // Draw grid lines (horizontal)
        for (int temp = 0; temp <= MAX_TEMP; temp += 50) {
            float y = height - padding - (temp / (float) MAX_TEMP) * (height - 2 * padding);
            canvas.drawLine(padding, y, width - padding, y, gridPaint);

            // Draw temperature labels
            canvas.drawText(temp + "°", 5, y + 8, textPaint);
        }

        // Draw vertical grid lines with timestamps (2 min intervals)
        if (!timestamps.isEmpty()) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
            int gridCount = 6; // 6 vertical lines for 2 min intervals = 12 minutes total

            for (int i = 0; i <= gridCount; i++) {
                float x = padding + (i / (float) gridCount) * (width - 2 * padding);
                canvas.drawLine(x, padding, x, height - padding, gridPaint);

                // Find the closest timestamp to this grid position
                int dataIndex = (int) ((i / (float) gridCount) * (timestamps.size() - 1));
                if (dataIndex < timestamps.size()) {
                    String timeLabel = timeFormat.format(new Date(timestamps.get(dataIndex)));
                    canvas.save();
                    canvas.rotate(-45, x, height - padding + 20);
                    canvas.drawText(timeLabel, x, height - padding + 20, textPaint);
                    canvas.restore();
                }
            }
        } else {
            // Draw generic vertical grid lines if no timestamps
            int gridCount = 6;
            for (int i = 0; i <= gridCount; i++) {
                float x = padding + (i / (float) gridCount) * (width - 2 * padding);
                canvas.drawLine(x, padding, x, height - padding, gridPaint);
            }
        }

        if (nozzleTemps.isEmpty()) {
            return;
        }

        // Draw temperature lines
        drawTemperatureLine(canvas, nozzleTemps, nozzlePaint, width, height, padding);
        drawTemperatureLine(canvas, nozzleTargets, nozzleTargetPaint, width, height, padding);
        drawTemperatureLine(canvas, bedTemps, bedPaint, width, height, padding);
        drawTemperatureLine(canvas, bedTargets, bedTargetPaint, width, height, padding);
    }

    private void drawTemperatureLine(Canvas canvas, List<Float> temps, Paint paint,
                                     int width, int height, int padding) {
        if (temps.isEmpty()) return;

        Path path = new Path();
        boolean first = true;

        for (int i = 0; i < temps.size(); i++) {
            float x = padding + (i / (float) (MAX_DATA_POINTS - 1)) * (width - 2 * padding);
            float temp = temps.get(i);
            float y = height - padding - (temp / MAX_TEMP) * (height - 2 * padding);

            if (first) {
                path.moveTo(x, y);
                first = false;
            } else {
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, paint);
    }
}
