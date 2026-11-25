package com.johnwpb.torquewidgets;
import android.appwidget.*;import android.content.*;import android.graphics.*;import android.os.*;import android.widget.RemoteViews;import org.prowl.torque.remote.ITorqueService;
public class CoolantWidget extends AppWidgetProvider {
 static SharedPreferences prefs(Context c,int id){return c.getSharedPreferences("gauge"+id,0);}
 @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
  PendingResult pending = goAsync();
  Context app = context.getApplicationContext();
  Handler handler = new Handler(Looper.getMainLooper());
  java.util.List<Integer> torque = new java.util.ArrayList<>();
  java.util.Map<Integer, GpsMetric> gps = new java.util.HashMap<>();
  for (int id : ids) {
   GpsMetric metric = GpsMetric.selected(prefs(app, id).getAll());
   if (metric == null) torque.add(id);
   else if (!GpsWidgetService.running) gps.put(id, metric);
  }
  int[] remaining = {(torque.isEmpty() ? 0 : 1) + (gps.isEmpty() ? 0 : 1)};
  if (remaining[0] == 0) {
   if (pending != null) pending.finish();
   return;
  }
  Runnable completed = () -> {
   if (--remaining[0] == 0 && pending != null) pending.finish();
  };
  if (!gps.isEmpty()) {
   GpsSample.request(app, handler, gps.containsValue(GpsMetric.SIGNAL), (location, strength) -> {
    try {
     for (java.util.Map.Entry<Integer, GpsMetric> item : gps.entrySet()) {
      render(app, manager, item.getKey(), item.getValue().read(location, strength));
     }
    } finally {
     completed.run();
    }
   });
  }
  if (torque.isEmpty()) return;
  class Connection implements ServiceConnection {
   boolean done, bound;
   Runnable timeout = () -> finish(null);

   void finish(ITorqueService service) {
    if (done) return;
    done = true;
    handler.removeCallbacks(timeout);
    try {
     for (int id : torque) {
      float value = Float.NaN;
      SharedPreferences settings = prefs(app, id);
      try {
       if (service != null) {
        String[] results = service.getPIDValues(new String[]{settings.getString("PID", "05")});
        value = Float.parseFloat(results[0]) * Float.parseFloat(settings.getString("Multiplier", "1.8"))
                + Float.parseFloat(settings.getString("Offset", "32"));
       }
      } catch (Exception ignored) { }
      render(app, manager, id, value);
     }
    } finally {
     if (bound) app.unbindService(this);
     completed.run();
    }
   }

   public void onServiceConnected(ComponentName name, IBinder binder) {
    finish(ITorqueService.Stub.asInterface(binder));
   }
   public void onServiceDisconnected(ComponentName name) { finish(null); }
   public void onNullBinding(ComponentName name) { finish(null); }
  }
  Connection connection = new Connection();
  try {
   connection.bound = app.bindService(new Intent("org.prowl.torque.remote.ITorqueService")
           .setPackage("org.prowl.torque"), connection, Context.BIND_AUTO_CREATE);
  } catch (SecurityException ignored) { }
  if (connection.bound) handler.postDelayed(connection.timeout, 5000);
  else connection.finish(null);
 }
 static void render(Context c, AppWidgetManager m, int id, float f) {
 SharedPreferences s = prefs(c, id);
 java.util.Map<String, ?> labels = s.getAll();
 GpsMetric metric = GpsMetric.selected(labels);
 float min = metric == null ? GaugeFace.number(labels, "Minimum", 0) : metric.min;
 float max = metric == null ? GaugeFace.number(labels, "Maximum", 260) : metric.max;
 int font = GaugeFace.color(labels, "Font color", GaugeFace.BLUE);
 boolean circle = s.getBoolean("circle", false);
 boolean info = s.getBoolean("info", false);
 Bitmap b = Bitmap.createBitmap(GaugeFace.SIZE, GaugeFace.SIZE, Bitmap.Config.ARGB_8888);
 Canvas a = new Canvas(b);
 GaugeBackground.draw(a, GaugeBackground.read(c, id), labels);
 Paint p = new Paint(3);
 p.setTextAlign(Paint.Align.CENTER);
 p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
 if (!info) GaugeFace.drawScale(a, p, labels, f, min, max, circle, font, null);
 p.setColor(font);
 GaugeLabels.draw(a, p, labels, null, "reading", GpsMetric.reading(metric,f),
         400, info ? 420 : circle ? 425 : 610, info || !circle ? 150 : 105);
 GaugeLabels.draw(a, p, labels, null, "units", metric==null?s.getString("Units", "°F"):metric.units,
         400, info ? 490 : circle ? 485 : 670, 48);
 GaugeLabels.draw(a, p, labels, null, "title", GpsMetric.title(metric, s.getString("Name", "Coolant")),
         400, info ? 550 : circle ? 545 : 735, 32);
 if (!Float.isFinite(f)) GaugeLabels.draw(a, p, labels, null, "noData", "NO DATA", 400, circle ? 580 : 770, 23);

 RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.gauge);v.setImageViewBitmap(R.id.gauge,b);Intent settings=new Intent(c,GaugeSettings.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);v.setOnClickPendingIntent(R.id.gauge,android.app.PendingIntent.getActivity(c,id,settings,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);}

 @Override public void onDeleted(Context context, int[] ids) {
     for (int id : ids) GaugeBackground.remove(context, id);
 }
}
