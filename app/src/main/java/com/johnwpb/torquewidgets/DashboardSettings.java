package com.johnwpb.torquewidgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DashboardSettings extends Activity {
    private static final int EDIT = 201;
    private final List<DashboardLayout.Gauge> gauges = new ArrayList<>();
    private final ArrayList<Integer> staged = new ArrayList<>();
    private final Map<Integer, Bitmap> images = new HashMap<>();
    private int widgetId, editing = -1, original = -1;
    private boolean saved;
    private DashboardLayout.Gauge selected;
    private AppWidgetManager manager;
    private DashboardCanvas preview;
    private Button pickGauge, editGauge, front, back, remove;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        setResult(RESULT_CANCELED, result());
        manager = AppWidgetManager.getInstance(this);
        if (!DashboardWidget.owns(this, manager, widgetId)) {
            finish();
            return;
        }
        if (state == null) gauges.addAll(DashboardLayout.read(this, widgetId));
        else {
            gauges.addAll(DashboardLayout.decode(state.getString("layout", "[]")));
            ArrayList<Integer> pending = state.getIntegerArrayList("staged");
            if (pending != null) staged.addAll(pending);
            selected = find(state.getInt("selected", -1));
            editing = state.getInt("editing", -1);
            original = state.getInt("original", -1);
        }
        if (selected == null && !gauges.isEmpty()) selected = gauges.get(gauges.size() - 1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(12));
        root.setBackgroundColor(0xff10161f);
        getWindow().setStatusBarColor(0xff10161f);
        getWindow().setNavigationBarColor(0xff10161f);
        TextView heading = new TextView(this);
        heading.setText("Torque Dashboard");
        heading.setTextColor(0xffedf5ff);
        heading.setTextSize(22);
        root.addView(heading);
        TextView hint = new TextView(this);
        hint.setText("Drag a gauge to move it. Drag its marked corner to resize.");
        hint.setTextColor(0xffabc0d7);
        root.addView(hint);

        LinearLayout actions = row(root);
        button(actions, "Add gauge", this::addMenu);
        pickGauge = button(actions, "Select gauge", this::selectMenu);
        editGauge = button(actions, "Edit gauge", this::editSelected);

        preview = new DashboardCanvas();
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout layers = row(root);
        back = button(layers, "Send to back", () -> layer(false));
        front = button(layers, "Bring to front", () -> layer(true));
        remove = button(layers, "Remove", this::removeSelected);
        LinearLayout footer = row(root);
        button(footer, "Cancel", this::finish);
        Button save = button(footer, "Save dashboard", this::save);
        save.setTextColor(GaugeFace.BLUE);
        setContentView(root);
        updateSelection();
    }

    private Intent result() {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    }

    private LinearLayout row(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row);
        return row;
    }

    private Button button(LinearLayout group, String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff253447);
        background.setCornerRadius(dp(10));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x3084d5ff), background, null));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, -2, 1);
        layout.setMargins(dp(2), dp(4), dp(2), dp(4));
        group.addView(button, layout);
        return button;
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private String name(int id) {
        SharedPreferences prefs = CoolantWidget.prefs(this, id);
        return GpsMetric.title(GpsMetric.selected(prefs.getAll()), prefs.getString("Name", "Coolant"));
    }

    private DashboardLayout.Gauge find(int id) {
        for (DashboardLayout.Gauge gauge : gauges) if (gauge.id == id) return gauge;
        return null;
    }

    private void addMenu() {
        if (gauges.size() >= DashboardLayout.MAX_GAUGES) {
            toast("This dashboard is full.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Add gauge")
                .setItems(new String[]{"Speedometer", "Fuel gauge", "Coolant", "Use existing gauge"},
                        (dialog, index) -> {
                            if (index == 3) copyMenu();
                            else addGauge(index, null);
                        }).show();
    }

    private void copyMenu() {
        int[] standalone = manager.getAppWidgetIds(new ComponentName(this, CoolantWidget.class));
        List<Integer> sources = new ArrayList<>();
        for (int id : standalone) sources.add(id);
        for (DashboardLayout.Gauge gauge : gauges) sources.add(gauge.id);
        if (sources.isEmpty()) {
            toast("Add or configure a Torque gauge first.");
            return;
        }
        String[] names = new String[sources.size()];
        for (int i = 0; i < names.length; i++) names[i] = name(sources.get(i));
        new AlertDialog.Builder(this).setTitle("Copy gauge settings")
                .setItems(names, (dialog, index) -> addGauge(-1, sources.get(index))).show();
    }

    private void addGauge(int preset, Integer source) {
        try {
            int id = DashboardLayout.newGauge(this, source);
            staged.add(id);
            SharedPreferences.Editor config = CoolantWidget.prefs(this, id).edit();
            if (preset == 0) {
                config.putString("Name", "Speedometer").putString("PID", "0d").putString("Units", "mph")
                        .putString("Maximum", "160").putString("Multiplier", "0.621371")
                        .putString("Offset", "0").putBoolean("circle", true).putInt("style", 1).apply();
            } else if (preset == 1) {
                config.putString("Name", "Fuel").putString("PID", "2f").putString("Units", "%")
                        .putString("Maximum", "100").putString("Multiplier", "1").putString("Offset", "0").apply();
            }
            selected = gauges.isEmpty() ? new DashboardLayout.Gauge(id, .5f, .5f, .78f)
                    : new DashboardLayout.Gauge(id, .75f, .27f, .36f);
            gauges.add(selected);
            updateSelection();
        } catch (IOException e) {
            toast("Could not add the gauge.");
        }
    }

    private void selectMenu() {
        String[] names = new String[gauges.size()];
        for (int i = 0; i < names.length; i++) names[i] = name(gauges.get(i).id) + "  ·  " + (i + 1);
        new AlertDialog.Builder(this).setTitle("Select gauge").setItems(names, (dialog, index) -> {
            selected = gauges.get(index);
            updateSelection();
        }).show();
    }

    private void editSelected() {
        if (selected == null || editing < -1) return;
        try {
            original = selected.id;
            editing = DashboardLayout.newGauge(this, original);
            staged.add(editing);
            startActivityForResult(new Intent(this, DashboardGaugeSettings.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, editing), EDIT);
        } catch (IOException e) {
            toast("Could not open the gauge editor.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EDIT || editing >= -1) return;
        DashboardLayout.Gauge gauge = find(original);
        if (result == RESULT_OK && gauge != null) {
            gauge.id = editing;
            selected = gauge;
        } else {
            DashboardLayout.deleteGauge(this, editing);
            staged.remove(Integer.valueOf(editing));
        }
        editing = original = -1;
        updateSelection();
    }

    private void layer(boolean forward) {
        if (selected == null) return;
        gauges.remove(selected);
        gauges.add(forward ? gauges.size() : 0, selected);
        updateSelection();
    }

    private void removeSelected() {
        if (selected == null) return;
        gauges.remove(selected);
        selected = gauges.isEmpty() ? null : gauges.get(gauges.size() - 1);
        updateSelection();
    }

    private void updateSelection() {
        boolean hasSelection = selected != null;
        pickGauge.setEnabled(!gauges.isEmpty());
        pickGauge.setText(hasSelection ? name(selected.id) + " ▾" : "Select gauge");
        editGauge.setEnabled(hasSelection);
        remove.setEnabled(hasSelection);
        front.setEnabled(hasSelection && gauges.indexOf(selected) < gauges.size() - 1);
        back.setEnabled(hasSelection && gauges.indexOf(selected) > 0);
        preview.invalidate();
    }

    private void save() {
        if (!DashboardWidget.owns(this, manager, widgetId)) {
            finish();
            return;
        }
        List<DashboardLayout.Gauge> old = DashboardLayout.read(this, widgetId);
        if (!DashboardLayout.save(this, widgetId, gauges)) {
            toast("Could not save the dashboard.");
            return;
        }
        for (DashboardLayout.Gauge gauge : old) {
            if (find(gauge.id) == null) DashboardLayout.deleteGauge(this, gauge.id);
        }
        for (int id : staged) {
            if (find(id) == null) DashboardLayout.deleteGauge(this, id);
        }
        saved = true;
        DashboardWidget.show(this, manager, widgetId);
        sendBroadcast(new Intent(this, DashboardWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId}));
        if (!gauges.isEmpty()) {
            try {
                startForegroundService(new Intent(this, GpsWidgetService.class));
            } catch (SecurityException | IllegalStateException e) {
                toast("Live updates could not start.");
            }
        }
        setResult(RESULT_OK, result());
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("layout", DashboardLayout.encode(gauges));
        state.putIntegerArrayList("staged", staged);
        state.putInt("selected", selected == null ? -1 : selected.id);
        state.putInt("editing", editing);
        state.putInt("original", original);
    }

    @Override protected void onDestroy() {
        if (isFinishing() && !saved) for (int id : staged) DashboardLayout.deleteGauge(this, id);
        super.onDestroy();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class DashboardCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF area = new RectF();
        private RectF before;
        private float downX, downY;
        private boolean resizing;

        DashboardCanvas() {
            super(DashboardSettings.this);
            setContentDescription("Dashboard preview");
        }

        @Override protected void onDraw(Canvas canvas) {
            int margin = dp(12);
            float availableWidth = getWidth() - margin * 2f, availableHeight = getHeight() - margin * 2f;
            if (availableWidth <= 0 || availableHeight <= 0) return;
            float aspect = DashboardWidget.aspect(DashboardSettings.this, manager, widgetId);
            float width = Math.min(availableWidth, availableHeight * aspect), height = width / aspect;
            float left = (getWidth() - width) / 2, top = (getHeight() - height) / 2;
            area.set(left, top, left + width, top + height);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff1a2533);
            canvas.drawRoundRect(area, dp(12), dp(12), paint);
            canvas.save();
            canvas.translate(left, top);
            canvas.clipRect(0, 0, width, height);
            for (DashboardLayout.Gauge gauge : gauges) {
                Map<String, ?> config = CoolantWidget.prefs(DashboardSettings.this, gauge.id).getAll();
                if (!images.containsKey(gauge.id)) {
                    images.put(gauge.id, GaugeBackground.read(DashboardSettings.this, gauge.id));
                }
                GpsMetric metric = GpsMetric.selected(config);
                float min = metric != null ? metric.min : GaugeFace.number(config, "Minimum", 0);
                float max = metric != null ? metric.max : GaugeFace.number(config, "Maximum", 260);
                DashboardLayout.draw(canvas, gauge.bounds(width, height), config, images.get(gauge.id),
                        (float) (min + ((double) max - min) * .55));
            }
            canvas.restore();
            if (selected == null) return;
            RectF bounds = selected.bounds(width, height);
            bounds.offset(left, top);
            paint.setColor(GaugeFace.BLUE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawRect(bounds, paint);
            paint.setStyle(Paint.Style.FILL);
            float handle = dp(7);
            canvas.drawRoundRect(bounds.right - handle, bounds.bottom - handle,
                    bounds.right + handle, bounds.bottom + handle, dp(3), dp(3), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() - area.left, y = event.getY() - area.top;
            float width = area.width(), height = area.height();
            if (width <= 0 || height <= 0) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    resizing = false;
                    before = null;
                    if (selected != null) {
                        RectF bounds = selected.bounds(width, height);
                        resizing = Math.hypot(x - bounds.right, y - bounds.bottom) <= dp(22);
                        if (resizing) before = bounds;
                    }
                    if (!resizing) {
                        for (int i = gauges.size() - 1; i >= 0; i--) {
                            DashboardLayout.Gauge candidate = gauges.get(i);
                            RectF bounds = candidate.bounds(width, height);
                            if (bounds.contains(x, y)) {
                                selected = candidate;
                                before = bounds;
                                break;
                            }
                        }
                    }
                    downX = x;
                    downY = y;
                    if (before != null) updateSelection();
                    return before != null;
                case MotionEvent.ACTION_MOVE:
                    if (before == null || selected == null) return false;
                    float dx = x - downX, dy = y - downY;
                    if (resizing) {
                        float shorter = Math.min(width, height);
                        float maximum = Math.min(shorter, Math.min(width - before.left, height - before.top));
                        float side = Math.min(maximum, Math.max(shorter * .12f, before.width() + (dx + dy) / 2));
                        selected.size = side / shorter;
                        selected.x = (before.left + side / 2) / width;
                        selected.y = (before.top + side / 2) / height;
                    } else {
                        selected.x = Math.max(0, Math.min(1, (before.centerX() + dx) / width));
                        selected.y = Math.max(0, Math.min(1, (before.centerY() + dy) / height));
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (before == null) return false;
                    before = null;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                    return true;
                default:
                    return before != null;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
