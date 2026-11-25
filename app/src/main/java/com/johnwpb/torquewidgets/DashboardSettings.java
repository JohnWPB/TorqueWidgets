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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public final class DashboardSettings extends Activity {
    private static final int EDIT = 201;
    private final List<DashboardLayout.Gauge> gauges = new ArrayList<>();
    private final ArrayList<Integer> staged = new ArrayList<>();
    private final Map<Integer, Bitmap> images = new HashMap<>();
    private int widgetId, editing = -1, original = -1;
    private boolean saved;
    private BackupActions<List<DashboardLayout.Gauge>> backups;
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

        backups = new BackupActions<>(this, true, this::backupSnapshot, this::prepareRestore,
                this::restoreDraft, this::discardRestore, state);
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
        button(footer, "Backup", backups::show);
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
        Set<Integer> existing = new LinkedHashSet<>();
        for (int id : manager.getAppWidgetIds(new ComponentName(this, CoolantWidget.class))) {
            existing.add(id);
        }
        for (int id : manager.getAppWidgetIds(new ComponentName(this, DashboardWidget.class))) {
            if (id == widgetId) continue;
            for (DashboardLayout.Gauge gauge : DashboardLayout.read(this, id)) existing.add(gauge.id);
        }
        for (DashboardLayout.Gauge gauge : gauges) existing.add(gauge.id);
        List<Integer> sources = new ArrayList<>(existing);
        String[] choices = new String[sources.size() + 1];
        choices[0] = "New gauge";
        for (int i = 0; i < sources.size(); i++) choices[i + 1] = name(sources.get(i));
        new AlertDialog.Builder(this).setTitle("Add gauge").setItems(choices, (dialog, index) -> {
            if (index == 0) openGaugeEditor(null);
            else copyGauge(sources.get(index - 1));
        }).show();
    }

    private void copyGauge(int source) {
        try {
            int id = DashboardLayout.newGauge(this, source);
            staged.add(id);
            placeGauge(id);
        } catch (IOException e) {
            toast("Could not copy the gauge.");
        }
    }

    private void placeGauge(int id) {
        selected = gauges.isEmpty() ? new DashboardLayout.Gauge(id, .5f, .5f, .78f)
                : new DashboardLayout.Gauge(id, .75f, .27f, .36f);
        gauges.add(selected);
        updateSelection();
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
        if (selected != null) openGaugeEditor(selected.id);
    }

    private void openGaugeEditor(Integer source) {
        if (editing < -1) return;
        try {
            int draft = DashboardLayout.newGauge(this, source);
            original = source == null ? -1 : source;
            editing = draft;
            staged.add(draft);
            startActivityForResult(new Intent(this, DashboardGaugeSettings.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, draft), EDIT);
        } catch (IOException e) {
            toast("Could not open the gauge editor.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (backups != null && backups.handle(request, result, data)) return;
        if (request != EDIT || editing >= -1) return;
        int draft = editing;
        DashboardLayout.Gauge previous = find(original);
        boolean creating = original == -1;
        editing = original = -1;
        if (result == RESULT_OK && creating) {
            placeGauge(draft);
            return;
        }
        if (result == RESULT_OK && previous != null) {
            previous.id = draft;
            selected = previous;
        } else {
            DashboardLayout.deleteGauge(this, draft);
            staged.remove(Integer.valueOf(draft));
        }
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

    private SettingsBackup backupSnapshot() {
        List<SettingsBackup.Profile> profiles = new ArrayList<>();
        for (DashboardLayout.Gauge gauge : gauges) {
            SettingsBackup.Profile profile = new SettingsBackup.Profile(
                    CoolantWidget.prefs(this, gauge.id).getAll(), gauge.x, gauge.y, gauge.size);
            profile.imageFile = GaugeBackground.source(this, gauge.id);
            profiles.add(profile);
        }
        return new SettingsBackup(true, profiles);
    }

    private List<DashboardLayout.Gauge> prepareRestore(SettingsBackup backup) throws IOException {
        List<DashboardLayout.Gauge> restored = new ArrayList<>();
        try {
            for (SettingsBackup.Profile profile : backup.profiles) {
                int id = DashboardLayout.newGauge(this, null);
                restored.add(new DashboardLayout.Gauge(id, profile.x, profile.y, profile.size));
                profile.apply(this, id);
            }
            return restored;
        } catch (IOException | RuntimeException e) {
            discardRestore(restored);
            throw new IOException("Could not restore the dashboard.", e);
        }
    }

    private void discardRestore(List<DashboardLayout.Gauge> restored) {
        for (DashboardLayout.Gauge gauge : restored) DashboardLayout.deleteGauge(this, gauge.id);
    }

    private void restoreDraft(List<DashboardLayout.Gauge> restored) {
        for (int id : staged) DashboardLayout.deleteGauge(this, id);
        staged.clear();
        gauges.clear();
        images.clear();
        gauges.addAll(restored);
        for (DashboardLayout.Gauge gauge : restored) staged.add(gauge.id);
        selected = gauges.isEmpty() ? null : gauges.get(gauges.size() - 1);
        updateSelection();
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
        if (backups != null) backups.saveState(state);
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
