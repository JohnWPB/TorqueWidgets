package com.johnwpb.torquewidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DashboardWidget extends AppWidgetProvider {
    private static final Map<Integer, Float> readings = new HashMap<>();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) show(context, manager, id);
        if (GpsWidgetService.running) return;
        PendingResult pending = goAsync();
        refresh(context.getApplicationContext(), manager, ids, () -> {
            if (pending != null) pending.finish();
        });
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                    int id, Bundle options) {
        show(context, manager, id);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) {
            for (DashboardLayout.Gauge gauge : DashboardLayout.read(context, id)) readings.remove(gauge.id);
            DashboardLayout.delete(context, id);
        }
    }

    static boolean owns(Context context, AppWidgetManager manager, int id) {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        return info != null && new ComponentName(context, DashboardWidget.class).equals(info.provider);
    }

    static float aspect(Context context, AppWidgetManager manager, int id) {
        Bundle options = manager.getAppWidgetOptions(id);
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int width = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int height = options.getInt(landscape ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        return width > 0 && height > 0 ? Math.max(.2f, Math.min(5, width / (float) height)) : 1.4f;
    }

    static void refresh(Context context, AppWidgetManager manager, int[] ids, Runnable complete) {
        Set<Integer> gauges = new HashSet<>();
        for (int id : ids) {
            for (DashboardLayout.Gauge gauge : DashboardLayout.read(context, id)) gauges.add(gauge.id);
        }
        int[] sources = gauges.stream().mapToInt(Integer::intValue).toArray();
        Set<Integer> changed = new HashSet<>();
        CoolantWidget.query(context, sources, true, (id, value) -> {
            if (!Float.valueOf(value).equals(readings.put(id, value))) changed.add(id);
        }, () -> {
            try {
                for (int id : ids) {
                    for (DashboardLayout.Gauge gauge : DashboardLayout.read(context, id)) {
                        if (changed.contains(gauge.id)) {
                            show(context, manager, id);
                            break;
                        }
                    }
                }
            } finally {
                complete.run();
            }
        });
    }

    static void show(Context context, AppWidgetManager manager, int id) {
        if (!owns(context, manager, id)) return;
        float aspect = aspect(context, manager, id);
        int width = Math.round(800 * Math.min(1, aspect)), height = Math.round(800 * Math.min(1, 1 / aspect));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        List<DashboardLayout.Gauge> gauges = DashboardLayout.read(context, id);
        for (DashboardLayout.Gauge gauge : gauges) {
            Map<String, ?> settings = CoolantWidget.prefs(context, gauge.id).getAll();
            float reading = readings.getOrDefault(gauge.id, Float.NaN);
            if (GpsMetric.selected(settings) == null) {
                reading = TemperatureUnit.displayReading(settings, reading);
            }
            DashboardLayout.draw(canvas, gauge.bounds(width, height), settings,
                    GaugeBackground.read(context, gauge.id), reading);
        }
        if (gauges.isEmpty()) {
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(GaugeFace.BLUE);
            text.setTextSize(Math.min(width, height) / 12f);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Tap to add gauges", width / 2f, height / 2f, text);
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.dashboard);
        views.setImageViewBitmap(R.id.dashboard, bitmap);
        Intent edit = new Intent(context, DashboardSettings.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        views.setOnClickPendingIntent(R.id.dashboard, PendingIntent.getActivity(context, id, edit,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(id, views);
    }
}
