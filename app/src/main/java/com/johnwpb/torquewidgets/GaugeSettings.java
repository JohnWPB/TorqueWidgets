package com.johnwpb.torquewidgets;
import android.view.View;
import java.util.HashMap;
import java.util.Map;
import android.app.*;import android.appwidget.*;import android.os.*;import android.content.*;import android.widget.*;
public class GaugeSettings extends Activity {
 int id; SharedPreferences prefs; LinearLayout panel;
 private final Map<String, Object> previewSettings = new HashMap<>();
 private ScrollView scroll;
 private LinearLayout picker;
 private GaugePreview preview;
 private Runnable cancelColor;
 private int settingsScroll;

 private void watch(EditText field, java.util.function.Consumer<String> change) {
  field.addTextChangedListener(new android.text.TextWatcher() {
   public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
   public void onTextChanged(CharSequence s, int start, int before, int count) { change.accept(s.toString()); }
   public void afterTextChanged(android.text.Editable text) {}
  });
 }

 private void previewSetting(String key, Object value) {
  previewSettings.put(key, value);
  preview.invalidate();
 }

 private void buildColumns() {
  LinearLayout columns = new LinearLayout(this);
  scroll = new ScrollView(this);
  LinearLayout left = new LinearLayout(this);
  left.setOrientation(LinearLayout.VERTICAL);
  panel = new LinearLayout(this);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(24, 24, 24, 24);
  picker = new LinearLayout(this);
  picker.setOrientation(LinearLayout.VERTICAL);
  picker.setVisibility(View.GONE);
  left.addView(panel);
  left.addView(picker);
  scroll.addView(left);
  columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
  LinearLayout right = new LinearLayout(this);
  right.setOrientation(LinearLayout.VERTICAL);
  right.setPadding(12, 24, 12, 12);
  TextView title = new TextView(this);
  title.setText("Gauge preview · sample reading");
  right.addView(title);
  Switch noData = new Switch(this);
  noData.setText("Preview no data");
  right.addView(noData);
  preview = new GaugePreview(this, previewSettings);
  noData.setOnCheckedChangeListener((button, checked) -> {
   preview.noData = checked;
   preview.invalidate();
  });
  right.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
  columns.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
  setContentView(columns);
 }

 private void closeColor() {
  cancelColor = null;
  picker.removeAllViews();
  picker.setVisibility(View.GONE);
  panel.setVisibility(View.VISIBLE);
  scroll.post(() -> scroll.scrollTo(0, settingsScroll));
 }

 @Override public void onBackPressed() {
  if (cancelColor != null) cancelColor.run();
  else super.onBackPressed();
 }

 private void sizeRow(TextView caption, String labelId) {
  LinearLayout row = new LinearLayout(this);
  panel.addView(row);
  row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
  Spinner size = new Spinner(this);
  size.setContentDescription(caption.getText() + " font size");
  int[] sizes = {0, 16, 20, 24, 28, 32, 36, 42, 48, 56, 64, 80, 96, 112, 128, 150, 175, 200};
  String[] options = new String[sizes.length];
  int selected = 0;
  String key = GaugeLabels.key(labelId, "size");
  for (int i = 0; i < sizes.length; i++) {
   options[i] = sizes[i] == 0 ? "Default" : Integer.toString(sizes[i]);
   if (sizes[i] == prefs.getInt(key, 0)) selected = i;
  }
  size.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
  row.addView(size, new LinearLayout.LayoutParams(0, -2, 1));
  size.setSelection(selected);
  size.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
   public void onItemSelected(AdapterView<?> parent, View view, int index, long itemId) {
    previewSetting(key, sizes[index]);
   }
   public void onNothingSelected(AdapterView<?> parent) {}
  });
 }

 private void sizeRow(String title, String labelId) {
  TextView caption = new TextView(this);
  caption.setText(title);
  sizeRow(caption, labelId);
 }

 EditText field(String key, String fallback) {
  TextView caption = new TextView(this);
  caption.setText(key);
  if (key.equals("Name")) sizeRow(caption, "title");
  else if (key.equals("Units")) sizeRow(caption, "units");
  else panel.addView(caption);
  EditText edit = new EditText(this);
  edit.setSingleLine(true);
  edit.setText(prefs.getString(key, fallback));
  panel.addView(edit);
  previewSettings.put(key, edit.getText().toString());
  watch(edit, value -> previewSetting(key, value));
  if (key.endsWith("color")) {
   edit.setFocusable(false);
   edit.setOnClickListener(v -> pickColor(key, edit));
  }
  return edit;
 }
 public void onCreate(Bundle b){super.onCreate(b);id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);setResult(RESULT_CANCELED);if(id<0){LinearLayout l=new LinearLayout(this);l.setOrientation(1);TextView t=new TextView(this);t.setText("Torque Widgets — add independent gauges from your home screen widget picker.");l.addView(t);setContentView(l);return;}prefs=getSharedPreferences("gauge"+id,0);previewSettings.putAll(prefs.getAll());buildColumns();
 String[] keys={"Name","PID","Minimum","Maximum","Units","Multiplier","Offset","Font color","Tick color","Indicator color","Needle color"};String[] defaults={"Coolant","05","0","260","°F","1.8","32","#84D5FF","#84D5FF","#84D5FF","#84D5FF"};EditText[] fields=new EditText[keys.length];for(int i=0;i<keys.length;i++)fields[i]=field(keys[i],defaults[i]);Switch circle=new Switch(this);circle.setText("Circular gauge (off = semicircle)");circle.setChecked(prefs.getBoolean("circle",false));panel.addView(circle);previewSettings.put("circle",circle.isChecked());circle.setOnCheckedChangeListener((v,checked)->previewSetting("circle",checked));Switch info=new Switch(this);info.setText("Display information only");info.setChecked(prefs.getBoolean("info",false));panel.addView(info);previewSettings.put("info",info.isChecked());info.setOnCheckedChangeListener((v,checked)->previewSetting("info",checked));Spinner style=new Spinner(this);style.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Illuminated segments","Sweeping needle","Continuous arc","None"}));style.setSelection(prefs.getInt("style",0));panel.addView(style);previewSettings.put("style",style.getSelectedItemPosition());
 style.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
  public void onItemSelected(AdapterView<?> parent, View view, int index, long itemId) { previewSetting("style",index); }
  public void onNothingSelected(AdapterView<?> parent) {}
 });
 sizeRow("Current reading", "reading");
 sizeRow("NO DATA message", "noData");
 for (int i = 0; i < 9; i++) sizeRow("Scale number " + (i + 1), "scale" + i);
 Button save=new Button(this);save.setText("Save");panel.addView(save);save.setOnClickListener(v->{try{float min=Float.parseFloat(fields[2].getText().toString()),max=Float.parseFloat(fields[3].getText().toString());if(!Float.isFinite(min)||!Float.isFinite(max)||max<=min)throw new Exception();for(int i=5;i<7;i++)if(!Float.isFinite(Float.parseFloat(fields[i].getText().toString())))throw new Exception();for(int i=7;i<keys.length;i++)android.graphics.Color.parseColor(fields[i].getText().toString());if(fields[1].getText().toString().trim().isEmpty())throw new Exception();SharedPreferences.Editor ed=prefs.edit();
 for (Map.Entry<String, Object> entry : previewSettings.entrySet()) {
  if (!entry.getKey().startsWith("label.")) continue;
  Object value = entry.getValue();
  if (value instanceof Float) ed.putFloat(entry.getKey(), (Float) value);
  if (value instanceof Integer) ed.putInt(entry.getKey(), (Integer) value);
 }
 for(int i=0;i<keys.length;i++)ed.putString(keys[i],fields[i].getText().toString().trim());ed.putBoolean("circle",circle.isChecked()).putBoolean("info",info.isChecked()).putInt("style",style.getSelectedItemPosition()).apply();Intent result=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);setResult(RESULT_OK,result);sendBroadcast(new Intent(this,CoolantWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,new int[]{id}));finish();}catch(Exception ex){Toast.makeText(this,"Check range, conversion and hex colors",Toast.LENGTH_LONG).show();}});}

 void pickColor(String key, EditText target){
  String previous = target.getText().toString();
  settingsScroll = scroll.getScrollY();
  final float[] hsv={200,.5f,1}; int original=android.graphics.Color.parseColor(target.getText().toString()); android.graphics.Color.colorToHSV(original,hsv);
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(20,12,20,12);
  SeekBar[] bars={new SeekBar(this),new SeekBar(this),new SeekBar(this)};
  TextView before=new TextView(this),after=new TextView(this);before.setText("Original");after.setText("Selected");before.setBackgroundColor(original);before.setTextColor(android.graphics.Color.WHITE);after.setTextColor(android.graphics.Color.WHITE);
  EditText hex=new EditText(this);hex.setSingleLine(true);hex.setHint("#RRGGBB");
  android.view.View spectrum=new android.view.View(this){
   android.graphics.Paint p=new android.graphics.Paint(3);
   protected void onDraw(android.graphics.Canvas c){float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
    p.setShader(new android.graphics.LinearGradient(0,0,w,0,new int[]{0xffff0000,0xffffff00,0xff00ff00,0xff00ffff,0xff0000ff,0xffff00ff,0xffff0000},null,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
    p.setShader(new android.graphics.LinearGradient(0,0,0,h,0x00ffffff,0xffffffff,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xff000000);float x=hsv[0]/360*w,y=(1-hsv[1])*h;c.drawCircle(x,y,12,p);p.setStrokeWidth(2);p.setColor(-1);c.drawCircle(x,y,12,p);p.setStyle(android.graphics.Paint.Style.FILL);}
   public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==0||e.getAction()==2||e.getAction()==1){getParent().requestDisallowInterceptTouchEvent(true);bars[0].setProgress(Math.min(359,Math.max(0,Math.round(e.getX()/getWidth()*359))));bars[1].setProgress(Math.min(100,Math.max(0,Math.round((1-e.getY()/getHeight())*100))));if(e.getAction()==1)performClick();return true;}return true;}
   public boolean performClick(){super.performClick();return true;}
  };
  box.addView(spectrum,new LinearLayout.LayoutParams(-1,(int)(180*getResources().getDisplayMetrics().density)));
  String[] names={"Hue","Saturation","Brightness"};for(int i=0;i<3;i++){TextView label=new TextView(this);label.setText(names[i]);box.addView(label);bars[i].setMax(i==0?359:100);bars[i].setProgress(Math.round(hsv[i]*(i==0?1:100)));box.addView(bars[i]);}
  box.addView(before);box.addView(after);box.addView(hex);
  final boolean[] busy={false};Runnable refresh=()->{if(busy[0])return;busy[0]=true;hsv[0]=bars[0].getProgress();hsv[1]=bars[1].getProgress()/100f;hsv[2]=bars[2].getProgress()/100f;int color=android.graphics.Color.HSVToColor(hsv);after.setBackgroundColor(color);after.setTextColor(hsv[2]>.65f&&hsv[1]<.65f?0xff000000:0xffffffff);hex.setText(String.format(java.util.Locale.US,"#%06X",color&0xffffff));spectrum.invalidate();busy[0]=false;previewSetting(key,String.format(java.util.Locale.US,"#%06X",color&0xffffff));};
  for(SeekBar bar:bars)bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){refresh.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
  hex.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(busy[0]||!s.toString().matches("#[0-9a-fA-F]{6}"))return;busy[0]=true;int color=android.graphics.Color.parseColor(s.toString());android.graphics.Color.colorToHSV(color,hsv);bars[0].setProgress(Math.round(hsv[0]));bars[1].setProgress(Math.round(hsv[1]*100));bars[2].setProgress(Math.round(hsv[2]*100));after.setBackgroundColor(color);spectrum.invalidate();busy[0]=false;previewSetting(key,String.format(java.util.Locale.US,"#%06X",color&0xffffff));}public void afterTextChanged(android.text.Editable e){}});
  refresh.run();
  panel.setVisibility(View.GONE);
  picker.addView(box);
  picker.setVisibility(View.VISIBLE);
  scroll.post(() -> scroll.scrollTo(0, 0));
  cancelColor = () -> {
   previewSetting(key, previous);
   closeColor();
  };
  Button apply = new Button(this);
  apply.setText("Apply color");
  box.addView(apply);
  apply.setOnClickListener(v -> {
   String value = hex.getText().toString();
   if (!value.matches("#[0-9a-fA-F]{6}")) {
    hex.setError("Enter #RRGGBB");
    return;
   }
   target.setText(value.toUpperCase(java.util.Locale.US));
   closeColor();
  });
  Button cancel = new Button(this);
  cancel.setText("Cancel color");
  box.addView(cancel);
  cancel.setOnClickListener(v -> cancelColor.run());
 }
}
