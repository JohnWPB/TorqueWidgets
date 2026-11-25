package com.johnwpb.torquewidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;
import org.prowl.torque.remote.ITorqueService;

public class CoolantWidget extends AppWidgetProvider {
    static SharedPreferences prefs(Context context, int id) {
        return context.getSharedPreferences("gauge" + id, Context.MODE_PRIVATE);
    }

    static boolean ownsWidget(Context context, AppWidgetManager manager, int id) {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        return info != null && new ComponentName(context, CoolantWidget.class).equals(info.provider);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        if (TorqueLiveService.running) return;
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        class Connection implements ServiceConnection {
            boolean bound, requested, done;
            final GaugeConfig[] configs = new GaugeConfig[ids.length];
            final Runnable timeout = () -> finish(null);

            void finish(float[] readings) {
                if (done) return;
                done = true;
                handler.removeCallbacks(timeout);
                try {
                    for (int i = 0; i < ids.length; i++) {
                        if (TorqueLiveService.running || !ownsWidget(app, manager, ids[i])) continue;
                        GaugeConfig config = new GaugeConfig(prefs(app, ids[i]));
                        float value = Float.NaN;
                        if (readings != null && config.string("PID", "05")
                                .equals(configs[i].string("PID", "05"))) {
                            value = config.convert(readings[i]);
                        }
                        render(app, manager, ids[i], config, value);
                    }
                } finally {
                    try {
                        if (bound) app.unbindService(this);
                    } finally {
                        pending.finish();
                    }
                }
            }

            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (done || requested) return;
                requested = true;
                ITorqueService service = ITorqueService.Stub.asInterface(binder);
                new Thread(() -> {
                    float[] readings = new float[ids.length];
                    for (int i = 0; i < ids.length; i++) {
                        configs[i] = new GaugeConfig(prefs(app, ids[i]));
                        readings[i] = read(service, configs[i]);
                    }
                    handler.post(() -> finish(readings));
                }, "Torque readings").start();
            }

            @Override public void onServiceDisconnected(ComponentName name) { finish(null); }
            @Override public void onNullBinding(ComponentName name) { finish(null); }
            @Override public void onBindingDied(ComponentName name) { finish(null); }
        }
        Connection connection = new Connection();
        try {
            connection.bound = app.bindService(new Intent("org.prowl.torque.remote.ITorqueService")
                    .setPackage("org.prowl.torque"), connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ignored) {
            // Render the disconnected state when Torque denies access.
        }
        if (connection.bound) handler.postDelayed(connection.timeout, 5000);
        else connection.finish(null);
    }

    static float read(ITorqueService torque, GaugeConfig config) {
        if (torque == null) return Float.NaN;
        try {
            String[] values = torque.getPIDValues(new String[]{config.string("PID", "05").trim()});
            return Float.parseFloat(values[0]);
        } catch (Exception ignored) {
            // The requested PID can be unavailable even when Torque is connected.
            return Float.NaN;
        }
    }

    static void render(Context context, AppWidgetManager manager, int id,
                               GaugeConfig config, float value) {
        Bitmap bitmap = Bitmap.createBitmap(GaugeRenderer.SIZE, GaugeRenderer.SIZE, Bitmap.Config.ARGB_8888);
        GaugeRenderer.draw(new Canvas(bitmap), config, value, GaugeRenderer.labels(config, value), null);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.gauge);
        views.setImageViewBitmap(R.id.gauge, bitmap);
        views.setViewVisibility(R.id.gauge_placeholder, View.GONE);
        views.setContentDescription(R.id.gauge, config.string("Name", "Coolant") + " "
                + (Float.isFinite(value) ? GaugeRenderer.number(value) + " " + config.units() : "NO DATA"));
        Intent settings = new Intent(context, GaugeSettings.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        views.setOnClickPendingIntent(R.id.gauge, PendingIntent.getActivity(context, id, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(id, views);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) context.deleteSharedPreferences("gauge" + id);
    }
}
