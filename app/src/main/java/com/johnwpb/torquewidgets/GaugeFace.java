package com.johnwpb.torquewidgets;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Map;

// Preview-only drawing so settings changes cannot affect the launcher's widget update path.
final class GaugeFace {
    static final int SIZE = 800;
    static final int BLUE = 0xff84d5ff;
    private static final Typeface TYPEFACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static String text(Map<String, ?> settings, String key, String fallback) {
        Object value = settings.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    static float number(Map<String, ?> settings, String key, float fallback) {
        try {
            float parsed = Float.parseFloat(text(settings, key, ""));
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static int color(Map<String, ?> settings, String key, int fallback) {
        try {
            return Color.parseColor(text(settings, key, ""));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    static void draw(Canvas canvas, Map<String, ?> settings, float value, GaugeLabels.HitAreas hits) {
        GpsMetric metric = GpsMetric.selected(settings);
        float min = metric == null ? number(settings, "Minimum", 0) : metric.min;
        float max = metric == null ? number(settings, "Maximum", 260) : metric.max;
        int font = color(settings, "Font color", BLUE);
        boolean circle = Boolean.TRUE.equals(settings.get("circle"));
        boolean info = Boolean.TRUE.equals(settings.get("info"));
        boolean valid = Float.isFinite(value);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(TYPEFACE);
        if (!info) drawScale(canvas, paint, settings, value, min, max, circle, font, hits);
        paint.setColor(font);
        GaugeLabels.draw(canvas, paint, settings, hits, "reading",
                GpsMetric.reading(metric, value), 400,
                info ? 420 : circle ? 425 : 610, info || !circle ? 150 : 105);
        GaugeLabels.draw(canvas, paint, settings, hits, "units", metric == null ? text(settings, "Units", "°F") : metric.units,
                400, info ? 490 : circle ? 485 : 670, 48);
        GaugeLabels.draw(canvas, paint, settings, hits, "title", GpsMetric.title(metric, text(settings, "Name", "Coolant")),
                400, info ? 550 : circle ? 545 : 735, 32);
        if (!valid) GaugeLabels.draw(canvas, paint, settings, hits, "noData", "NO DATA",
                400, circle ? 580 : 770, 23);
    }

    private static void drawScale(Canvas canvas, Paint paint, Map<String, ?> settings, float value,
                                  float min, float max, boolean circle, int font, GaugeLabels.HitAreas hits) {
        float y = circle ? 400 : 425, start = circle ? 135 : 180, sweep = circle ? 270 : 180;
        int tick = color(settings, "Tick color", BLUE);
        int indicator = color(settings, "Indicator color", BLUE);
        float range = max - min;
        float fraction = Float.isFinite(value) && Float.isFinite(range) && range > 0
                ? Math.max(0, Math.min(1, (value - min) / range)) : 0;
        boolean showTicks = !Boolean.FALSE.equals(settings.get("showTicks"));
        boolean showScale = !Boolean.FALSE.equals(settings.get("showScale"));
        for (int i = 0; i <= 40; i++) {
            double angle = Math.toRadians(start + sweep * i / 40);
            boolean major = i % 5 == 0;
            if (showTicks) {
                paint.setColor(tick);
                paint.setStrokeWidth(major ? 12 : 6);
                line(canvas, paint, y, angle, major ? 270 : 285, 310);
            }
            if (major && showScale) {
                paint.setColor(font);
                GaugeLabels.draw(canvas, paint, settings, hits, "scale" + i / 5,
                        Integer.toString(Math.round(min + range * i / 40)),
                        400 + (float) Math.cos(angle) * 350,
                        y + (float) Math.sin(angle) * 350 + 10, 30);
            }
        }
        Object style = settings.get("style");
        switch (style instanceof Integer ? (Integer) style : 0) {
            case 0:
                paint.setStrokeWidth(13);
                for (int i = 0; i < 32; i++) {
                    paint.setColor(fraction >= (i + 1) / 32f ? indicator : 0x334f7790);
                    line(canvas, paint, y, Math.toRadians(start + sweep * (i + .5) / 32), 190, 250);
                }
                break;
            case 1:
                paint.setColor(color(settings, "Needle color", indicator));
                paint.setStrokeWidth(12);
                paint.setStrokeCap(Paint.Cap.ROUND);
                line(canvas, paint, y, Math.toRadians(start + sweep * fraction), 0, 260);
                canvas.drawCircle(400, y, 18, paint);
                break;
            case 2:
                paint.setColor(indicator);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(22);
                canvas.drawArc(170, y - 230, 630, y + 230, start, sweep * fraction, false, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            default:
                break;
        }
    }

    private static void line(Canvas canvas, Paint paint, float y, double angle, float inner, float outer) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        canvas.drawLine(400 + dx * inner, y + dy * inner, 400 + dx * outer, y + dy * outer, paint);
    }
}
