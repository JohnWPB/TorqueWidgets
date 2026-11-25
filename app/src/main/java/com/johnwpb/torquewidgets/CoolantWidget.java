package com.johnwpb.torquewidgets;
import android.appwidget.*;import android.content.*;import android.graphics.*;import android.os.*;import android.widget.RemoteViews;import org.prowl.torque.remote.ITorqueService;
public class CoolantWidget extends AppWidgetProvider {
 static SharedPreferences prefs(Context c,int id){return c.getSharedPreferences("gauge"+id,0);}
 @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
  PendingResult pending = goAsync();
  query(context, ids, false, (id, value) -> render(context, manager, id, value),
          () -> { if (pending != null) pending.finish(); });
 }

 static void query(Context context, int[] ids, boolean includeGps,
                   java.util.function.BiConsumer<Integer, Float> result, Runnable complete) {
  Context app = context.getApplicationContext();
  Handler handler = new Handler(Looper.getMainLooper());
  java.util.List<Integer> torque = new java.util.ArrayList<>();
  java.util.Map<Integer, GpsMetric> gps = new java.util.HashMap<>();
  for (int id : ids) {
   GpsMetric metric = GpsMetric.selected(prefs(app, id).getAll());
   if (metric == null) torque.add(id);
   else if (includeGps || !GpsWidgetService.running) gps.put(id, metric);
  }
  int[] remaining = {(torque.isEmpty() ? 0 : 1) + (gps.isEmpty() ? 0 : 1)};
  if (remaining[0] == 0) {
   complete.run();
   return;
  }
  Runnable completed = () -> {
   if (--remaining[0] == 0) complete.run();
  };
  if (!gps.isEmpty()) {
   GpsSample.request(app, handler, gps.containsValue(GpsMetric.SIGNAL), (location, strength) -> {
    try {
     for (java.util.Map.Entry<Integer, GpsMetric> item : gps.entrySet()) {
      result.accept(item.getKey(), item.getValue().read(location, strength));
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
        value = Float.parseFloat(results[0]);
       }
      } catch (Exception ignored) { }
      result.accept(id, value);
     }
    } finally {
     try { if (bound) app.unbindService(this); }
     finally { completed.run(); }
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
 static void line(Canvas a,Paint p,float x,float y,double t,float r1,float r2){a.drawLine(x+(float)Math.cos(t)*r1,y+(float)Math.sin(t)*r1,x+(float)Math.cos(t)*r2,y+(float)Math.sin(t)*r2,p);}
 static void render(Context c,AppWidgetManager m,int id,float f){SharedPreferences s=prefs(c,id);java.util.Map<String,?> labels=s.getAll();GpsMetric metric=GpsMetric.selected(labels);float min=metric==null?Float.parseFloat(s.getString("Minimum","0")):metric.min,max=metric==null?Float.parseFloat(s.getString("Maximum","260")):metric.max;int font=Color.parseColor(s.getString("Font color","#84D5FF")),tick=Color.parseColor(s.getString("Tick color","#84D5FF")),indicator=Color.parseColor(s.getString("Indicator color","#84D5FF"));boolean circle=s.getBoolean("circle",false);float y=circle?400:425,start=circle?135:180,sweep=circle?270:180;Bitmap b=Bitmap.createBitmap(800,800,Bitmap.Config.ARGB_8888);Canvas a=new Canvas(b);GaugeBackground.draw(a,GaugeBackground.read(c,id),labels);Paint p=new Paint(3);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setStrokeCap(Paint.Cap.BUTT);
 if (metric == null) f = TemperatureUnit.displayReading(labels, f);
 boolean info=s.getBoolean("info",false);int style=s.getInt("style",0);float fraction=Float.isFinite(f)?Math.max(0,Math.min(1,(f-min)/(max-min))):0;if(!info){for(int i=0;i<=40;i++){double t=Math.toRadians(start+sweep*i/40);if(s.getBoolean("showTicks",true)){p.setColor(tick);p.setStrokeWidth(i%5==0?12:6);line(a,p,400,y,t, i%5==0?270:285,310);}if(i%5==0&&s.getBoolean("showScale",true)){p.setColor(font);GaugeLabels.draw(a,p,labels,null,"scale"+i/5,String.valueOf(Math.round(min+(max-min)*i/40)),400+(float)Math.cos(t)*350,y+(float)Math.sin(t)*350+10,30);}}
 if(style==0)for(int i=0;i<32;i++){double t=Math.toRadians(start+sweep*(i+.5)/32);p.setColor(Float.isFinite(f)&&(f-min)/(max-min)>=(i+1)/32f?indicator:0x334f7790);p.setStrokeWidth(13);line(a,p,400,y,t,190,250);}
 if(style==1){p.setColor(Color.parseColor(s.getString("Needle color",s.getString("Indicator color","#84D5FF"))));p.setStrokeWidth(12);p.setStrokeCap(Paint.Cap.ROUND);line(a,p,400,y,Math.toRadians(NeedleCalibration.angleFor(labels,f,min,max,circle,fraction)),0,260);a.drawCircle(400,y,18,p);}if(style==2){p.setColor(indicator);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(22);a.drawArc(170,y-230,630,y+230,start,sweep*fraction,false,p);p.setStyle(Paint.Style.FILL);}}
 p.setColor(font);
 GaugeLabels.draw(a, p, labels, null, "reading", GpsMetric.reading(metric,f),
         400, info ? 420 : circle ? 425 : 610, info || !circle ? 150 : 105);
 GaugeLabels.draw(a, p, labels, null, "units", metric == null ? TemperatureUnit.displayUnits(labels) : metric.units,
         400, info ? 490 : circle ? 485 : 670, 48);
 GaugeLabels.draw(a, p, labels, null, "title", GpsMetric.title(metric, s.getString("Name", "Coolant")),
         400, info ? 550 : circle ? 545 : 735, 32);
 if (!Float.isFinite(f)) GaugeLabels.draw(a, p, labels, null, "noData", "NO DATA", 400, circle ? 580 : 770, 23);

 RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.gauge);v.setImageViewBitmap(R.id.gauge,b);Intent settings=new Intent(c,GaugeSettings.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);v.setOnClickPendingIntent(R.id.gauge,android.app.PendingIntent.getActivity(c,id,settings,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);}

 @Override public void onDeleted(Context context, int[] ids) {
     for (int id : ids) GaugeBackground.remove(context, id);
 }
}
