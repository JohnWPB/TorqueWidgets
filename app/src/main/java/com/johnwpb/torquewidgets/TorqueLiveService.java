package com.johnwpb.torquewidgets;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.prowl.torque.remote.ITorqueService;

public final class TorqueLiveService extends Service {
    private static final String CHANNEL = "torque_live";
    private static final String STOP = "com.johnwpb.torquewidgets.STOP_LIVE";
    private static final long INTERVAL = 2000;
    static boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<Integer, Reading> displayed = new HashMap<>();
    private AppWidgetManager manager;
    private ComponentName provider;
    private boolean bound, stopped;
    private ITorqueService torque;
    private long nextBind;
    private final Runnable poll = this::poll;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            torque = ITorqueService.Stub.asInterface(binder);
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            torque = null;
        }

        @Override public void onBindingDied(ComponentName name) { disconnect(); }

        @Override public void onNullBinding(ComponentName name) { disconnect(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        manager = AppWidgetManager.getInstance(this);
        provider = new ComponentName(this, CoolantWidget.class);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction()) && running) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            startForeground(1, notification());
            if (intent != null && STOP.equals(intent.getAction())) {
                stopSelf();
                return START_NOT_STICKY;
            }
            running = true;
            handler.post(poll);
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Torque live updates",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, GaugeSettings.class),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, TorqueLiveService.class)
                .setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Torque Widgets")
                .setContentText("Live vehicle readings are on")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true)
                .build();
    }

    private void poll() {
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids.length == 0) {
            stopSelf();
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (!bound && now >= nextBind) {
            nextBind = now + 10000;
            try {
                bound = bindService(new Intent("org.prowl.torque.remote.ITorqueService")
                        .setPackage("org.prowl.torque"), connection, Context.BIND_AUTO_CREATE);
            } catch (SecurityException ignored) {
                // Keep displaying the disconnected state if Torque cannot be reached.
            }
        }
        ITorqueService service = torque;
        worker.execute(() -> {
            Map<Integer, Reading> readings = new HashMap<>();
            Map<String, Float> pids = new HashMap<>();
            for (int id : ids) {
                GaugeConfig config = new GaugeConfig(CoolantWidget.prefs(this, id));
                String pid = config.pid();
                float raw = pids.computeIfAbsent(pid, key -> CoolantWidget.read(service, config));
                readings.put(id, new Reading(config, config.convert(raw)));
            }
            handler.post(() -> {
                if (stopped) return;
                displayed.keySet().retainAll(readings.keySet());
                for (Map.Entry<Integer, Reading> entry : readings.entrySet()) {
                    int id = entry.getKey();
                    if (manager.getAppWidgetInfo(id) == null) continue;
                    Reading current = entry.getValue(), previous = displayed.get(id);
                    if (previous == null || Float.compare(previous.value, current.value) != 0
                            || !previous.config.values.equals(current.config.values)) {
                        CoolantWidget.render(this, manager, id, current.config, current.value);
                        displayed.put(id, current);
                    }
                }
                handler.postDelayed(poll, INTERVAL);
            });
        });
    }

    private void disconnect() {
        torque = null;
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopped = true;
        running = false;
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        disconnect();
        super.onDestroy();
    }

    private static final class Reading {
        final GaugeConfig config;
        final float value;

        Reading(GaugeConfig config, float value) {
            this.config = config;
            this.value = value;
        }
    }
}
