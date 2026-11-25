package com.johnwpb.torquewidgets;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GaugeRenderer {
    static final int SIZE = 800;
    static final String[] FONTS = {"sans-serif-medium", "sans-serif", "sans-serif-condensed",
            "sans-serif-light", "sans-serif-rounded", "serif", "monospace", "casual"};

    static final class Label {
        final String id, name, text;
        final float x, y, size;
        final boolean active;
        final RectF bounds = new RectF();

        Label(String id, String name, String text, float x, float y, float size, boolean active) {
            this.id = id;
            this.name = name;
            this.text = text;
            this.x = x;
            this.y = y;
            this.size = size;
            this.active = active;
        }
    }

    static List<Label> labels(GaugeConfig config, float value) {
        List<Label> labels = new ArrayList<>();
        boolean info = config.flag("info", false);
        boolean available = Float.isFinite(value);
        int layout = config.layout();
        boolean circle = layout == 0;
        boolean rectangular = layout == 1;
        float min = config.minimum(), max = config.maximum();
        for (int i = 0; i <= 8; i++) {
            double angle = angle(circle, i / 8f);
            float x = rectangular ? 70 + i * 660 / 8f : 400 + (float) Math.cos(angle) * 350;
            float y = rectangular ? 300 : (circle ? 400 : 425) + (float) Math.sin(angle) * 350;
            labels.add(new Label("scale" + i, "Scale number " + (i + 1),
                    Integer.toString(Math.round(min + (max - min) * i / 8f)), x, y, 30, !info));
        }
        int decimals = Math.max(0, Math.min(6, config.integer("decimals", 0)));
        String reading = decimals == 0 ? Long.toString(Math.round((double) value))
                : String.format(Locale.getDefault(), "%." + decimals + "f", value);
        String current = available ? config.string("prefix", "") + reading + config.string("suffix", "") : "--";
        labels.add(new Label("reading", "Current reading", current, 400,
                info || circle ? 378 : rectangular ? 470 : 555, info || !circle ? 150 : 105, true));
        labels.add(new Label("units", "Units", config.units(), 400,
                info || circle ? 468 : rectangular ? 560 : 653, 48, true));
        labels.add(new Label("title", "Gauge title", config.string("Name", "Coolant"), 400,
                info || circle ? 535 : rectangular ? 630 : 725, 32, true));
        labels.add(new Label("min", "Minimum value", number(min), 120, 690, 29, true));
        labels.add(new Label("max", "Maximum value", number(max), 680, 690, 29, true));
        labels.add(new Label("low", "Low warning", config.string("lowMessage", "LOW"), 400, 715, 30,
                available && value <= config.number("lowLimit", Float.NEGATIVE_INFINITY)));
        labels.add(new Label("high", "High warning", config.string("highMessage", "HIGH"), 400, 750, 30,
                available && value >= config.number("highLimit", Float.POSITIVE_INFINITY)));
        labels.add(new Label("noData", "NO DATA", config.string("noDataMessage", "NO DATA"), 400,
                circle ? 575 : 762, 23, !available));
        for (String id : config.string("customLabels", "").split(",")) {
            if (id.isEmpty()) continue;
            String text = config.string(config.textKey(id, "content"), "Custom label");
            labels.add(new Label(id, text.isEmpty() ? "Custom label" : text, text, 400, 660, 32, true));
        }
        return labels;
    }

    static String number(float value) {
        return value == (int) value ? Integer.toString((int) value) : Float.toString(value);
    }

    static float position(GaugeConfig config, Label label, String axis, float fallback) {
        return config.number(config.textKey(label.id, axis), fallback / SIZE) * SIZE;
    }

    static void draw(Canvas canvas, GaugeConfig config, float value, List<Label> labels,
                     String revealedLabel) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!config.flag("info", false)) drawScale(canvas, paint, config, value);
        paint.setTextAlign(Paint.Align.CENTER);
        for (Label label : labels) {
            String id = label.id;
            boolean defaultVisible = !id.equals("min") && !id.equals("max");
            boolean visible = config.flag(config.textKey(id, "visible"), defaultVisible);
            boolean reveal = id.equals(revealedLabel);
            if ((!label.active || !visible) && !reveal) continue;
            float x = position(config, label, "x", label.x);
            float y = position(config, label, "y", label.y);
            paint.setTypeface(Typeface.create(config.string(config.textKey(id, "font"), FONTS[0]),
                    config.flag(config.textKey(id, "bold"), false) ? Typeface.BOLD : Typeface.NORMAL));
            paint.setTextSize(Math.max(1, config.number(config.textKey(id, "size"), label.size)));
            paint.setColor(config.color(config.textKey(id, "color"), config.color("Font color", GaugeConfig.BLUE)));
            if (!visible || !label.active) paint.setAlpha(105);
            float baseline = y - (paint.ascent() + paint.descent()) / 2;
            float halfWidth = Math.max(12, paint.measureText(label.text) / 2);
            label.bounds.set(x - halfWidth - 7, baseline + paint.ascent() - 4,
                    x + halfWidth + 7, baseline + paint.descent() + 4);
            canvas.drawText(label.text, x, baseline, paint);
        }
    }

    private static void drawScale(Canvas canvas, Paint paint, GaugeConfig config, float value) {
        boolean circle = config.layout() == 0;
        boolean rectangular = config.layout() == 1;
        float y = circle ? 400 : 425;
        float range = config.maximum() - config.minimum();
        float fraction = Float.isFinite(value) && range > 0
                ? Math.max(0, Math.min(1, (value - config.minimum()) / range)) : 0;
        int indicator = config.color("Indicator color", GaugeConfig.BLUE);
        paint.setColor(config.color("Tick color", GaugeConfig.BLUE));
        for (int i = 0; i <= 40; i++) {
            boolean major = i % 5 == 0;
            float point = i / 40f;
            paint.setStrokeWidth(major ? 12 : 6);
            if (rectangular) {
                float x = 70 + 660 * point;
                canvas.drawLine(x, 250, x, major ? 275 : 265, paint);
            } else {
                radial(canvas, paint, y, angle(circle, point), major ? 270 : 285, 310);
            }
        }
        switch (config.integer("style", 0)) {
            case 0:
                paint.setStrokeWidth(13);
                for (int i = 0; i < 32; i++) {
                    paint.setColor(fraction >= (i + 1) / 32f ? indicator : 0x334f7790);
                    float point = (i + .5f) / 32;
                    if (rectangular) {
                        float x = 70 + 660 * point;
                        canvas.drawLine(x, 175, x, 225, paint);
                    } else {
                        radial(canvas, paint, y, angle(circle, point), 190, 250);
                    }
                }
                break;
            case 1:
                paint.setColor(config.color("Needle color", indicator));
                paint.setStrokeWidth(12);
                paint.setStrokeCap(Paint.Cap.ROUND);
                if (rectangular) {
                    canvas.drawLine(400, 386, 70 + 660 * fraction, 214, paint);
                    canvas.drawCircle(400, 386, 18, paint);
                } else {
                    radial(canvas, paint, y, angle(circle, fraction), 0, 260);
                    canvas.drawCircle(400, y, 18, paint);
                }
                paint.setStrokeCap(Paint.Cap.BUTT);
                break;
            case 2:
                paint.setColor(indicator);
                paint.setStrokeWidth(22);
                if (rectangular) {
                    canvas.drawLine(70, 200, 70 + 660 * fraction, 200, paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(170, y - 230, 630, y + 230, circle ? 135 : 180,
                            (circle ? 270 : 180) * fraction, false, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                break;
            default:
                break;
        }
    }

    private static double angle(boolean circle, float fraction) {
        return Math.toRadians(circle ? 135 + 270 * fraction : 180 + 180 * fraction);
    }

    private static void radial(Canvas canvas, Paint paint, float y, double angle, float from, float to) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        canvas.drawLine(400 + dx * from, y + dy * from, 400 + dx * to, y + dy * to, paint);
    }
}
