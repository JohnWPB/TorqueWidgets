package com.johnwpb.torquewidgets;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.RemoteViews;
import org.prowl.torque.remote.ITorqueService;
public class CoolantWidget extends AppWidgetProvider {
 static final int BLUE=Color.rgb(132,213,255);
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
  PendingResult pending=goAsync();
  Context app=c.getApplicationContext();
  Handler h=new Handler(Looper.getMainLooper());
  ServiceConnection connection=new ServiceConnection(){
   boolean done=false;
   void finish(float value){if(done)return;done=true;for(int id:ids)render(app,m,id,value);try{app.unbindService(this);}catch(Exception ignored){}pending.finish();}
   public void onServiceConnected(ComponentName n,IBinder b){float f=Float.NaN;try{String[] v=ITorqueService.Stub.asInterface(b).getPIDValues(new String[]{"05"});if(v!=null&&v.length>0)f=Float.parseFloat(v[0])*1.8f+32;}catch(Exception ignored){}finish(f);}
   public void onServiceDisconnected(ComponentName n){finish(Float.NaN);}
  };
  try{if(!app.bindService(new Intent("org.prowl.torque.remote.ITorqueService").setPackage("org.prowl.torque"),connection,Context.BIND_AUTO_CREATE)){for(int id:ids)render(app,m,id,Float.NaN);pending.finish();return;}}catch(Exception e){for(int id:ids)render(app,m,id,Float.NaN);pending.finish();return;}
 }
 static void render(Context c,AppWidgetManager m,int id,float f){
  Bitmap b=Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888);Canvas a=new Canvas(b);Paint p=new Paint(3);p.setColor(0xff101820);a.drawCircle(320,320,310,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xff344958);a.drawCircle(320,320,305,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",0));
  for(int i=0;i<=40;i++){double t=Math.toRadians(135+i*6.75);boolean major=i%5==0;p.setColor(BLUE);p.setStrokeWidth(major?5:2);float r=major?249:264;a.drawLine((float)(320+280*Math.cos(t)),(float)(320+280*Math.sin(t)),(float)(320+r*Math.cos(t)),(float)(320+r*Math.sin(t)),p);if(major){p.setTextSize(29);a.drawText(""+(100+i*5),(float)(320+215*Math.cos(t)),(float)(330+215*Math.sin(t)),p);}}
  if(Float.isFinite(f)){double t=Math.toRadians(135+270*Math.max(0,Math.min(1,(f-100)/200)));p.setColor(0xffff795d);p.setStrokeWidth(8);p.setStrokeCap(Paint.Cap.ROUND);a.drawLine(320,320,(float)(320+190*Math.cos(t)),(float)(320+190*Math.sin(t)),p);}
  p.setColor(BLUE);a.drawCircle(320,320,13,p);p.setTextSize(62);a.drawText(Float.isFinite(f)?Math.round(f)+"°F":"-- °F",320,420,p);p.setTextSize(27);a.drawText("COOLANT",320,470,p);if(!Float.isFinite(f)){p.setTextSize(21);a.drawText("NO DATA",320,505,p);}
  RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.gauge);v.setImageViewBitmap(R.id.gauge,b);Intent refresh=new Intent(c,CoolantWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,new int[]{id});v.setOnClickPendingIntent(R.id.gauge,android.app.PendingIntent.getBroadcast(c,id,refresh,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);
 }
}