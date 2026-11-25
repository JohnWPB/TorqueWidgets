package com.johnwpb.torquewidgets;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GpsWidgetService extends Service {
    private static final String CHANNEL = "widget_gps";
    private static final String STOP = "com.johnwpb.torquewidgets.STOP_GPS";
    static boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private boolean stopped;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            try {
                startForeground(802, notification());
            } catch (SecurityException e) {
                stopSelf();
                return START_NOT_STICKY;
            }
            running = true;
            handler.post(poll);
        }
        return START_NOT_STICKY;
    }

    private void poll() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new ComponentName(this, CoolantWidget.class));
        Map<Integer, GpsMetric> widgets = new LinkedHashMap<>();
        for (int id : ids) {
            GpsMetric metric = GpsMetric.selected(CoolantWidget.prefs(this, id).getAll());
            if (metric != null) widgets.put(id, metric);
        }
        if (widgets.isEmpty()) {
            stopSelf();
            return;
        }
        GpsSample.request(this, handler, widgets.containsValue(GpsMetric.SIGNAL), (location, strength) -> {
            if (stopped) return;
            for (Map.Entry<Integer, GpsMetric> entry : widgets.entrySet()) {
                int id = entry.getKey();
                if (manager.getAppWidgetInfo(id) != null
                        && entry.getValue() == GpsMetric.selected(CoolantWidget.prefs(this, id).getAll())) {
                    CoolantWidget.render(this, manager, id, entry.getValue().read(location, strength));
                }
            }
            handler.postDelayed(poll, 2500);
        });
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "GPS widgets",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, GpsWidgetService.class)
                .setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Torque Widgets")
                .setContentText("GPS readings are active")
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true)
                .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopped = true;
        running = false;
        handler.removeCallbacks(poll);
        super.onDestroy();
    }
}
