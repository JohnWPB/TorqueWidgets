package com.johnwpb.torquewidgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GaugePreview extends View {
    private final Map<String, Object> settings;
    private final List<Label> labels = new ArrayList<>();
    private Label active;
    private float scale, left, downX, downY;
    private boolean moved;
    boolean noData;

    GaugePreview(Context context, Map<String, Object> settings) {
        super(context);
        this.settings = settings;
        setContentDescription("Gauge preview. Drag a label to move it.");
    }

    @Override protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        scale = size / GaugeFace.SIZE;
        left = (getWidth() - size) / 2;
        float min = GaugeFace.number(settings, "Minimum", 0);
        float max = GaugeFace.number(settings, "Maximum", 260);
        float sample = (float) (min + ((double) max - min) * .55);
        labels.clear();
        canvas.save();
        canvas.translate(left, 0);
        canvas.scale(scale, scale);
        GaugeFace.draw(canvas, settings, noData ? Float.NaN : sample,
                (id, x, baseline, bounds) -> labels.add(new Label(id, x, baseline, bounds)));
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (scale <= 0) return false;
        float x = (event.getX() - left) / scale, y = event.getY() / scale;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                active = null;
                float nearest = Float.POSITIVE_INFINITY;
                float padding = 10 * getResources().getDisplayMetrics().density / scale;
                for (Label label : labels) {
                    RectF bounds = label.bounds;
                    float dx = Math.max(0, Math.max(bounds.left - x, x - bounds.right));
                    float dy = Math.max(0, Math.max(bounds.top - y, y - bounds.bottom));
                    float distance = (float) Math.hypot(dx, dy);
                    if (distance <= padding && distance <= nearest) {
                        active = label;
                        nearest = distance;
                    }
                }
                downX = x;
                downY = y;
                moved = false;
                return active != null;
            case MotionEvent.ACTION_MOVE:
                if (active == null) return false;
                if (!moved && Math.hypot(x - downX, y - downY) * scale
                        < ViewConfiguration.get(getContext()).getScaledTouchSlop()) return true;
                moved = true;
                settings.put(GaugeLabels.key(active.id, "x"), normalized(active.x + x - downX));
                settings.put(GaugeLabels.key(active.id, "y"), normalized(active.baseline + y - downY));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (active == null) return false;
                if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                active = null;
                return true;
            default:
                return active != null;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private static float normalized(float value) {
        return Math.max(0, Math.min(1, value / GaugeFace.SIZE));
    }

    private static final class Label {
        final String id;
        final float x, baseline;
        final RectF bounds;

        Label(String id, float x, float baseline, RectF bounds) {
            this.id = id;
            this.x = x;
            this.baseline = baseline;
            this.bounds = bounds;
        }
    }
}
