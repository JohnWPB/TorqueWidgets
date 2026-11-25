package com.johnwpb.torquewidgets;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.Map;

final class GaugeIndicator {
    static final String KEY = "style";
    static final int SEGMENTS = 0;
    static final int NEEDLE = 1;
    static final int ARC = 2;
    static final int NONE = 3;
    private static final int SEGMENT_COUNT = 32;

    static int style(Map<String, ?> settings) {
        Object stored = settings.get(KEY);
        if (stored instanceof Integer) return Math.max(SEGMENTS, Math.min(NONE, (Integer) stored));
        // Earlier versions omitted the style key when a configured gauge used segments.
        return settings.containsKey("Name") ? SEGMENTS : NEEDLE;
    }

    static void draw(Canvas canvas, Paint paint, Map<String, ?> settings, float value, float min,
                     float max, boolean circle, float dragAngle, float dragStart) {
        int style = style(settings);
        if (style == NONE) return;

        float y = GaugeCalibration.pivotY(circle);
        boolean dragging = Float.isFinite(dragAngle);
        float range = max - min;
        boolean validRange = Float.isFinite(range) && range > 0;
        float fraction = Float.isFinite(value) && validRange
                ? Math.max(0, Math.min(1, (value - min) / range)) : 0;
        float start = GaugeCalibration.defaultAngle(circle, 0);
        float sweep = GaugeCalibration.defaultAngle(circle, 1) - start;
        float angle = GaugeCalibration.defaultAngle(circle, fraction);
        float progress = sweep * fraction;

        GaugeCalibration calibration = validRange ? GaugeCalibration.read(settings) : null;
        if (calibration != null) {
            start = calibration.angle(min, min, max);
            sweep = calibration.angle(max, min, max) - start;
            angle = calibration.angle(value, min, max);
            progress = angle - start;
            fraction = sweep > 0 ? progress / sweep : 0;
        }
        if (dragging) {
            start = dragStart;
            angle = dragAngle;
            progress = (float) GaugeCalibration.clockwiseSweep(start, angle);
            sweep = Math.max(sweep, progress);
            fraction = sweep > 0 ? progress / sweep : 0;
        }

        int color = GaugeFace.color(settings, "Indicator color", GaugeFace.BLUE);
        switch (style) {
            case SEGMENTS:
                paint.setStrokeWidth(13);
                for (int i = 0; i < SEGMENT_COUNT; i++) {
                    float segment = start + sweep * (i + .5f) / SEGMENT_COUNT;
                    paint.setColor(fraction >= (i + 1f) / SEGMENT_COUNT ? color : 0x334f7790);
                    line(canvas, paint, y, segment, 190, 250);
                }
                if (dragging) {
                    paint.setColor(color);
                    line(canvas, paint, y, angle, 190, 250);
                }
                break;
            case NEEDLE:
                paint.setColor(GaugeFace.color(settings, "Needle color", color));
                paint.setStrokeWidth(12);
                paint.setStrokeCap(Paint.Cap.ROUND);
                line(canvas, paint, y, angle, 0, 260);
                canvas.drawCircle(400, y, 18, paint);
                break;
            case ARC:
                paint.setColor(color);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(22);
                canvas.drawArc(170, y - 230, 630, y + 230, start, progress, false, paint);
                paint.setStyle(Paint.Style.FILL);
                if (dragging) {
                    double radians = Math.toRadians(angle);
                    canvas.drawCircle(400 + (float) Math.cos(radians) * 230,
                            y + (float) Math.sin(radians) * 230, 11, paint);
                }
                break;
        }
    }

    private static void line(Canvas canvas, Paint paint, float y, float angle, float inner, float outer) {
        double radians = Math.toRadians(angle);
        float dx = (float) Math.cos(radians), dy = (float) Math.sin(radians);
        canvas.drawLine(400 + dx * inner, y + dy * inner, 400 + dx * outer, y + dy * outer, paint);
    }
}
