package com.johnwpb.torquewidgets;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.Map;

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

    static void draw(Canvas canvas, Map<String, ?> settings, float value) {
        float min = number(settings, "Minimum", 0), max = number(settings, "Maximum", 260);
        int font = color(settings, "Font color", BLUE);
        boolean circle = Boolean.TRUE.equals(settings.get("circle"));
        boolean info = Boolean.TRUE.equals(settings.get("info"));
        boolean valid = Float.isFinite(value);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(TYPEFACE);
        if (!info) drawScale(canvas, paint, settings, value, min, max, circle, font);
        paint.setColor(font);
        String reading = valid ? text(settings, "Reading format", "{value}")
                .replace("{value}", Integer.toString(Math.round(value)))
                : text(settings, "Missing reading", "--");
        label(canvas, paint, settings, "Reading size", reading, 400,
                info ? 420 : circle ? 425 : 610, info || !circle ? 150 : 105, 780);
        label(canvas, paint, settings, "Units size", text(settings, "Units", "°F"),
                400, info ? 490 : circle ? 485 : 670, 48, 780);
        label(canvas, paint, settings, "Title size", text(settings, "Name", "Coolant"),
                400, info ? 550 : circle ? 545 : 735, 32, 780);
        if (!valid) {
            label(canvas, paint, settings, "No data size", text(settings, "No data text", "NO DATA"),
                    400, circle ? 580 : 770, 23, 780);
        }
    }

    private static void drawScale(Canvas canvas, Paint paint, Map<String, ?> settings, float value,
                                  float min, float max, boolean circle, int font) {
        float y = circle ? 400 : 425, start = circle ? 135 : 180, sweep = circle ? 270 : 180;
        int tick = color(settings, "Tick color", BLUE);
        int indicator = color(settings, "Indicator color", BLUE);
        float range = max - min;
        float fraction = Float.isFinite(value) && Float.isFinite(range) && range > 0
                ? Math.max(0, Math.min(1, (value - min) / range)) : 0;
        for (int i = 0; i <= 40; i++) {
            double angle = Math.toRadians(start + sweep * i / 40);
            boolean major = i % 5 == 0;
            paint.setColor(tick);
            paint.setStrokeWidth(major ? 12 : 6);
            line(canvas, paint, y, angle, major ? 270 : 285, 310);
            if (major) {
                paint.setColor(font);
                label(canvas, paint, settings, "Scale size", Integer.toString(Math.round(min + range * i / 40)),
                        400 + (float) Math.cos(angle) * 350,
                        y + (float) Math.sin(angle) * 350 + 10, 30, 100);
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

    private static void label(Canvas canvas, Paint paint, Map<String, ?> settings, String key,
                              String text, float x, float y, float fallback, float maxWidth) {
        paint.setTextSize(Math.max(1, Math.min(300, number(settings, key, fallback))));
        float width = paint.measureText(text);
        if (width > maxWidth) paint.setTextSize(paint.getTextSize() * maxWidth / width);
        float baseline = Math.min(SIZE - paint.descent(), Math.max(-paint.ascent(), y));
        canvas.drawText(text, x, baseline, paint);
    }

    private static void line(Canvas canvas, Paint paint, float y, double angle, float inner, float outer) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        canvas.drawLine(400 + dx * inner, y + dy * inner, 400 + dx * outer, y + dy * outer, paint);
    }
}
