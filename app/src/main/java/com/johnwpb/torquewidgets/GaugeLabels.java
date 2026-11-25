package com.johnwpb.torquewidgets;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.Map;

final class GaugeLabels {
    interface HitAreas {
        void add(String id, float x, float baseline, RectF bounds);
    }

    static String key(String id, String property) {
        return "label." + id + "." + property;
    }

    static void draw(Canvas canvas, Paint paint, Map<String, ?> settings, HitAreas hits,
                     String id, String text, float x, float baseline, int size) {
        if (Boolean.FALSE.equals(settings.get(key(id, "visible")))) return;
        Object chosen = settings.get(key(id, "size"));
        if (chosen instanceof Integer && (Integer) chosen > 0) size = (Integer) chosen;
        paint.setTextSize(size);
        Object savedX = settings.get(key(id, "x"));
        Object savedY = settings.get(key(id, "y"));
        if (hits == null && !(savedX instanceof Float) && !(savedY instanceof Float)) {
            canvas.drawText(text, x, baseline, paint);
            return;
        }
        float canvasSize = GaugeFace.SIZE;
        float halfWidth = Math.min(canvasSize / 2, paint.measureText(text) / 2);
        if (savedX instanceof Float) {
            x = Math.max(halfWidth, Math.min(canvasSize - halfWidth, (Float) savedX * canvasSize));
        }
        Rect ink = new Rect();
        paint.getTextBounds(text, 0, text.length(), ink);
        if (savedY instanceof Float) {
            baseline = Math.max(-ink.top, Math.min(canvasSize - ink.bottom, (Float) savedY * canvasSize));
        }
        canvas.drawText(text, x, baseline, paint);
        if (hits != null && !text.isEmpty()) {
            hits.add(id, x, baseline,
                    new RectF(x - halfWidth, baseline + ink.top, x + halfWidth, baseline + ink.bottom));
        }
    }
}
