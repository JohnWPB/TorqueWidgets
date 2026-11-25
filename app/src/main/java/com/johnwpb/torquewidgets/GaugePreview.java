package com.johnwpb.torquewidgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Paint;
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
    Bitmap backgroundImage;
    boolean editBackground;
    boolean calibratingNeedle;
    float calibrationAngle, calibrationValue = Float.NaN;
    private RectF startingImage;
    private int corner;
    private final Paint handles = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        GpsMetric metric = GpsMetric.selected(settings);
        float min = metric == null ? GaugeFace.number(settings, "Minimum", 0) : metric.min;
        float max = metric == null ? GaugeFace.number(settings, "Maximum", 260) : metric.max;
        float sample = calibratingNeedle && Float.isFinite(calibrationValue) ? calibrationValue
                : (float) (min + ((double) max - min) * .55);
        labels.clear();
        canvas.save();
        canvas.translate(left, 0);
        canvas.scale(scale, scale);
        canvas.clipRect(0, 0, GaugeFace.SIZE, GaugeFace.SIZE);
        GaugeBackground.draw(canvas, backgroundImage, settings);
        GaugeFace.draw(canvas, settings, noData ? Float.NaN : sample,
                calibratingNeedle ? null : (id, x, baseline, bounds) -> labels.add(new Label(id, x, baseline, bounds)),
                calibratingNeedle ? calibrationAngle : Float.NaN);
        if (editBackground && backgroundImage != null) drawHandles(canvas);
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (scale <= 0) return false;
        float x = (event.getX() - left) / scale, y = event.getY() / scale;
        if (calibratingNeedle) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                float dx = x - GaugeFace.SIZE / 2f;
                float dy = y - NeedleCalibration.pivotY(Boolean.TRUE.equals(settings.get("circle")));
                if (Math.hypot(dx, dy) >= 30) {
                    calibrationAngle = NeedleCalibration.touchAngle(dx, dy);
                    invalidate();
                }
                if (action == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }
        if (editBackground && backgroundImage != null) return editImage(event, x, y);
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

    private void drawHandles(Canvas canvas) {
        RectF box = GaugeBackground.bounds(backgroundImage, settings);
        handles.setColor(0xff84d5ff);
        handles.setStrokeWidth(dpOnCanvas(2));
        handles.setStyle(Paint.Style.STROKE);
        canvas.drawRect(box, handles);
        handles.setStyle(Paint.Style.FILL);
        float radius = dpOnCanvas(6);
        for (int i = 0; i < 4; i++) {
            float x = clamp(cornerX(box, i), radius, GaugeFace.SIZE - radius);
            float y = clamp(cornerY(box, i), radius, GaugeFace.SIZE - radius);
            canvas.drawRect(x - radius, y - radius, x + radius, y + radius, handles);
        }
    }

    private boolean editImage(MotionEvent event, float x, float y) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startingImage = GaugeBackground.bounds(backgroundImage, settings);
                corner = -1;
                float nearest = dpOnCanvas(18);
                for (int i = 0; i < 4; i++) {
                    float radius = dpOnCanvas(6);
                    float handleX = clamp(cornerX(startingImage, i), radius, GaugeFace.SIZE - radius);
                    float handleY = clamp(cornerY(startingImage, i), radius, GaugeFace.SIZE - radius);
                    float distance = (float) Math.hypot(x - handleX, y - handleY);
                    if (distance <= nearest) {
                        nearest = distance;
                        corner = i;
                    }
                }
                downX = x;
                downY = y;
                if (corner < 0 && !startingImage.contains(x, y)) startingImage = null;
                return startingImage != null;
            case MotionEvent.ACTION_MOVE:
                if (startingImage == null) return false;
                RectF box = new RectF(startingImage);
                float dx = x - downX, dy = y - downY;
                if (corner < 0) {
                    float inset = Math.min(GaugeFace.SIZE / 3f, dpOnCanvas(20));
                    box.offset(clamp(dx, inset - box.right, GaugeFace.SIZE - inset - box.left),
                            clamp(dy, inset - box.bottom, GaugeFace.SIZE - inset - box.top));
                } else {
                    float anchorX = cornerX(box, corner ^ 1), anchorY = cornerY(box, corner ^ 2);
                    float vx = cornerX(box, corner) - anchorX;
                    float vy = cornerY(box, corner) - anchorY;
                    float diagonal = (float) Math.hypot(vx, vy);
                    float minimum = Math.min(1, dpOnCanvas(35) / diagonal);
                    float factor = clamp(1 + (dx * vx + dy * vy) / (diagonal * diagonal),
                            minimum, Math.max(1, 4 * GaugeFace.SIZE / diagonal));
                    box.set(Math.min(anchorX, anchorX + vx * factor), Math.min(anchorY, anchorY + vy * factor),
                            Math.max(anchorX, anchorX + vx * factor), Math.max(anchorY, anchorY + vy * factor));
                }
                settings.put("imageLeft", box.left / GaugeFace.SIZE);
                settings.put("imageTop", box.top / GaugeFace.SIZE);
                settings.put("imageWidth", box.width() / GaugeFace.SIZE);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                boolean handled = startingImage != null;
                startingImage = null;
                return handled;
            default:
                return startingImage != null;
        }
    }

    private float dpOnCanvas(int dp) {
        return dp * getResources().getDisplayMetrics().density / scale;
    }

    private static float cornerX(RectF box, int index) {
        return (index & 1) == 0 ? box.left : box.right;
    }

    private static float cornerY(RectF box, int index) {
        return (index & 2) == 0 ? box.top : box.bottom;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
