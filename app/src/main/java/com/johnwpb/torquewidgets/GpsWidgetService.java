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
import android.content.pm.ServiceInfo;
import android.os.Build;
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
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                int type = hasLocationPermission() ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        : ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                startForeground(802, notification(), type);
            } else {
                startForeground(802, notification());
            }
        } catch (SecurityException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            handler.post(poll);
        }
        return START_NOT_STICKY;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void poll() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new ComponentName(this, CoolantWidget.class));
        int[] dashboards = manager.getAppWidgetIds(new ComponentName(this, DashboardWidget.class));
        Map<Integer, GpsMetric> widgets = new LinkedHashMap<>();
        if (hasLocationPermission()) {
            for (int id : ids) {
                GpsMetric metric = GpsMetric.selected(CoolantWidget.prefs(this, id).getAll());
                if (metric != null) widgets.put(id, metric);
            }
        }
        boolean hasDashboard = false;
        for (int id : dashboards) {
            if (!DashboardLayout.read(this, id).isEmpty()) {
                hasDashboard = true;
                break;
            }
        }
        if (widgets.isEmpty() && !hasDashboard) {
            stopSelf();
            return;
        }
        int[] remaining = {2};
        Runnable complete = () -> {
            if (--remaining[0] == 0 && !stopped) handler.postDelayed(poll, 2500);
        };
        DashboardWidget.refresh(this, manager, dashboards, complete);
        if (widgets.isEmpty()) {
            complete.run();
            return;
        }
        GpsSample.request(this, handler, widgets.containsValue(GpsMetric.SIGNAL), (location, strength) -> {
            if (stopped) return;
            try {
                for (Map.Entry<Integer, GpsMetric> entry : widgets.entrySet()) {
                    int id = entry.getKey();
                    if (manager.getAppWidgetInfo(id) != null
                            && entry.getValue() == GpsMetric.selected(CoolantWidget.prefs(this, id).getAll())) {
                        CoolantWidget.render(this, manager, id, entry.getValue().read(location, strength));
                    }
                }
            } finally {
                complete.run();
            }
        });
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Widget readings",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, GpsWidgetService.class)
                .setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Torque Widgets")
                .setContentText("Live widget readings are active")
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
