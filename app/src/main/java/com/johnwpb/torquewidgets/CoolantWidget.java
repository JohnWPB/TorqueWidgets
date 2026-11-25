package com.johnwpb.torquewidgets;
import android.appwidget.*;import android.content.*;import android.graphics.*;import android.os.*;import android.widget.RemoteViews;import org.prowl.torque.remote.ITorqueService;
public class CoolantWidget extends AppWidgetProvider {
 static SharedPreferences prefs(Context c,int id){return c.getSharedPreferences("gauge"+id,0);}
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){PendingResult pending=goAsync();Context app=c.getApplicationContext();Handler h=new Handler(Looper.getMainLooper());
 class Connection implements ServiceConnection {boolean done;void finish(ITorqueService s){if(done)return;done=true;for(int id:ids){float f=Float.NaN;SharedPreferences p=prefs(app,id);try{if(s!=null){String[] v=s.getPIDValues(new String[]{p.getString("PID","05")});f=Float.parseFloat(v[0])*Float.parseFloat(p.getString("Multiplier","1.8"))+Float.parseFloat(p.getString("Offset","32"));}}catch(Exception ignored){}render(app,m,id,f);}try{app.unbindService(this);}catch(Exception ignored){}pending.finish();}public void onServiceConnected(ComponentName n,IBinder b){finish(ITorqueService.Stub.asInterface(b));}public void onServiceDisconnected(ComponentName n){finish(null);}}
 Connection conn=new Connection();try{if(!app.bindService(new Intent("org.prowl.torque.remote.ITorqueService").setPackage("org.prowl.torque"),conn,Context.BIND_AUTO_CREATE))conn.finish(null);else h.postDelayed(()->conn.finish(null),5000);}catch(Exception e){conn.finish(null);}}
 static void render(Context context, AppWidgetManager manager, int id, float value) {
     Bitmap bitmap = Bitmap.createBitmap(GaugeFace.SIZE, GaugeFace.SIZE, Bitmap.Config.ARGB_8888);
     GaugeFace.draw(new Canvas(bitmap), prefs(context, id).getAll(), value);
     RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.gauge);
     views.setImageViewBitmap(R.id.gauge, bitmap);
     Intent settings = new Intent(context, GaugeSettings.class)
             .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
     views.setOnClickPendingIntent(R.id.gauge, android.app.PendingIntent.getActivity(context, id,
             settings, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
     manager.updateAppWidget(id, views);
 }
}
