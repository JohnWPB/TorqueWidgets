package com.johnwpb.torquewidgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.function.Consumer;

final class GaugePreview extends View {
    private final GaugeConfig config;
    private final Consumer<String> select;
    private final Runnable positionChanged;
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<GaugeRenderer.Label> labels;
    String selected;
    boolean snap, guides, offline;
    float fraction = .5f;
    private float left, top, scale, offsetX, offsetY;
    private boolean dragging, movable, moved;

    GaugePreview(Context context, GaugeConfig config, Consumer<String> select, Runnable positionChanged) {
        super(context);
        this.config = config;
        this.select = select;
        this.positionChanged = positionChanged;
        setContentDescription("Live gauge preview. Select and drag gauge text.");
    }

    float reading() {
        return offline ? Float.NaN : (float) (config.minimum() * (1.0 - fraction)
                + config.maximum() * (double) fraction);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        scale = size / GaugeRenderer.SIZE;
        if (scale <= 0) return;
        left = (getWidth() - size) / 2;
        top = (getHeight() - size) / 2;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        canvas.clipRect(0, 0, GaugeRenderer.SIZE, GaugeRenderer.SIZE);
        overlay.setStyle(Paint.Style.FILL);
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                overlay.setColor((row + col) % 2 == 0 ? 0xff30363e : 0xff424952);
                canvas.drawRect(col * 50, row * 50, (col + 1) * 50, (row + 1) * 50, overlay);
            }
        }
        if (snap && guides) {
            overlay.setStrokeWidth(1);
            overlay.setColor(0x5584d5ff);
            for (int at = 40; at < GaugeRenderer.SIZE; at += 40) {
                canvas.drawLine(at, 0, at, GaugeRenderer.SIZE, overlay);
                canvas.drawLine(0, at, GaugeRenderer.SIZE, at, overlay);
            }
        }
        float value = reading();
        labels = GaugeRenderer.labels(config, value);
        GaugeRenderer.draw(canvas, config, value, labels, selected);
        for (GaugeRenderer.Label label : labels) {
            if (!label.id.equals(selected) || label.bounds.isEmpty()) continue;
            overlay.setStrokeWidth(2);
            overlay.setColor(0xff84d5ff);
            if (guides) {
                canvas.drawLine(label.bounds.centerX(), 0, label.bounds.centerX(), GaugeRenderer.SIZE, overlay);
                canvas.drawLine(0, label.bounds.centerY(), GaugeRenderer.SIZE, label.bounds.centerY(), overlay);
            }
            overlay.setStyle(Paint.Style.STROKE);
            canvas.drawRect(label.bounds, overlay);
        }
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (labels == null || scale <= 0) return false;
        float x = (event.getX() - left) / scale;
        float y = (event.getY() - top) / scale;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                GaugeRenderer.Label hit = null;
                float nearest = Float.POSITIVE_INFINITY;
                float padding = getResources().getDisplayMetrics().density * 8 / scale;
                for (GaugeRenderer.Label label : labels) {
                    if (label.bounds.isEmpty()) continue;
                    RectF target = new RectF(label.bounds);
                    target.inset(-padding, -padding);
                    float distance = (float) Math.hypot(x - target.centerX(), y - target.centerY());
                    if (target.contains(x, y) && distance <= nearest) {
                        hit = label;
                        nearest = distance;
                    }
                }
                if (hit == null) return false;
                offsetX = x - GaugeRenderer.position(config, hit, "x", hit.x);
                offsetY = y - GaugeRenderer.position(config, hit, "y", hit.y);
                movable = !config.flag(config.textKey(hit.id, "locked"), false);
                dragging = true;
                moved = false;
                select.accept(hit.id);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging && movable) {
                    updatePosition("x", x - offsetX);
                    updatePosition("y", y - offsetY);
                    moved = true;
                    invalidate();
                    positionChanged.run();
                }
                return dragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved) performClick();
                return true;
            default:
                return dragging;
        }
    }

    private void updatePosition(String axis, float value) {
        float normalized = Math.max(0, Math.min(1, value / GaugeRenderer.SIZE));
        if (snap) normalized = Math.round(normalized * 20) / 20f;
        config.values.put(config.textKey(selected, axis), Float.toString(normalized));
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
