package com.johnwpb.torquewidgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;

public class GaugeSettings extends Activity {
    private static final String[] SIZE_KEYS = {
            "Title size", "Reading size", "Units size", "No data size", "Scale size"
    };

    private final Map<String, Object> draft = new HashMap<>();
    private int id;
    private SharedPreferences prefs;
    private LinearLayout panel, picker;
    private ScrollView scroll;
    private View preview;
    private int settingsScroll;
    private Runnable cancelColor;
    private float previewPosition = .55f;
    private boolean noData;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        setResult(RESULT_CANCELED);
        if (id < 0) {
            LinearLayout root = column();
            label(root, "Torque Widgets — add independent gauges from your home screen widget picker.");
            setContentView(root);
            return;
        }

        prefs = CoolantWidget.prefs(this, id);
        draft.putAll(prefs.getAll());
        LinearLayout root = column();
        LinearLayout columns = new LinearLayout(this);
        root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(this);
        LinearLayout left = column();
        left.setPadding(dp(12), dp(8), dp(12), dp(12));
        scroll.addView(left);
        columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        panel = column();
        picker = column();
        picker.setVisibility(View.GONE);
        left.addView(panel);
        left.addView(picker);

        LinearLayout right = column();
        right.setPadding(dp(10), dp(8), dp(10), dp(8));
        columns.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        label(right, "Gauge preview");
        label(right, "Test reading");
        SeekBar test = new SeekBar(this);
        test.setContentDescription("Test reading");
        test.setMax(100);
        test.setProgress(55);
        right.addView(test);
        track(test, progress -> {
            previewPosition = progress / 100f;
            preview.invalidate();
        });
        Switch disconnected = new Switch(this);
        disconnected.setText("Simulate no data");
        right.addView(disconnected);
        disconnected.setOnCheckedChangeListener((v, checked) -> {
            noData = checked;
            test.setEnabled(!checked);
            preview.invalidate();
        });
        preview = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float size = Math.min(getWidth(), getHeight());
                if (size <= 0) return;
                float min = GaugeFace.number(draft, "Minimum", 0);
                float max = GaugeFace.number(draft, "Maximum", 260);
                float value = noData ? Float.NaN : (float) (min + ((double) max - min) * previewPosition);
                canvas.save();
                canvas.translate((getWidth() - size) / 2, 0);
                canvas.scale(size / GaugeFace.SIZE, size / GaugeFace.SIZE);
                GaugeFace.draw(canvas, draft, value);
                canvas.restore();
            }
        };
        preview.setContentDescription("Live gauge preview");
        right.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        field("Name", "Gauge title", "Coolant", false);
        field("Units", "Units text", "°F", false);
        field("Reading format", "Reading text ({value} inserts the live number)", "{value}", false);
        field("Missing reading", "Reading text when disconnected", "--", false);
        field("No data text", "No-data message", "NO DATA", false);
        for (String key : SIZE_KEYS) sizeField(key);

        label(panel, "Gauge");
        field("PID", "Torque PID", "05", false);
        field("Minimum", "Minimum", "0", false);
        field("Maximum", "Maximum", "260", false);
        field("Multiplier", "Multiplier", "1.8", false);
        field("Offset", "Offset", "32", false);
        field("Font color", "Font color", "#84D5FF", true);
        field("Tick color", "Tick mark color", "#84D5FF", true);
        field("Indicator color", "Indicator color", "#84D5FF", true);
        field("Needle color", "Needle color", GaugeFace.text(draft, "Indicator color", "#84D5FF"), true);
        toggle("circle", "Circular gauge (off = semicircle)");
        toggle("info", "Display information only");
        label(panel, "Indicator style");
        Spinner style = new Spinner(this);
        style.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Illuminated segments", "Sweeping needle", "Continuous arc", "None"}));
        Object storedStyle = draft.get("style");
        style.setSelection(storedStyle instanceof Integer ? (Integer) storedStyle : 0);
        style.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                put("style", index);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        panel.addView(style);
        LinearLayout actions = new LinearLayout(this);
        root.addView(actions);
        button(actions, "Cancel", this::finish).setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        button(actions, "Save", this::save).setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        setContentView(root);
    }

    private void sizeField(String key) {
        EditText edit = field(key, key + " (blank uses default)", "", false);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setHint("Default");
    }

    private EditText field(String key, String title, String fallback, boolean color) {
        TextView caption = label(panel, title);
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setId(View.generateViewId());
        caption.setLabelFor(edit.getId());
        edit.setText(GaugeFace.text(draft, key, fallback));
        panel.addView(edit);
        if (color) {
            edit.setFocusable(false);
            edit.setOnClickListener(v -> {
                settingsScroll = scroll.getScrollY();
                pickColor(key, edit);
            });
        }
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                put(key, s.toString());
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        return edit;
    }

    private void toggle(String key, String title) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setChecked(Boolean.TRUE.equals(draft.get(key)));
        control.setOnCheckedChangeListener((v, value) -> put(key, value));
        panel.addView(control);
    }

    private void put(String key, Object value) {
        draft.put(key, value);
        preview.invalidate();
    }

    private void save() {
        if (cancelColor != null) {
            Toast.makeText(this, "Apply or cancel the selected color first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String error = validate();
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, Object> entry : draft.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(key, key.equals("PID") ? ((String) value).trim() : (String) value);
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            if (value instanceof Integer) editor.putInt(key, (Integer) value);
        }
        editor.apply();
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        setResult(RESULT_OK, result);
        sendBroadcast(new Intent(this, CoolantWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{id}));
        finish();
    }

    private String validate() {
        float min = GaugeFace.number(draft, "Minimum", draft.containsKey("Minimum") ? Float.NaN : 0);
        float max = GaugeFace.number(draft, "Maximum", draft.containsKey("Maximum") ? Float.NaN : 260);
        if (!Float.isFinite(max - min) || max <= min) return "Enter a valid minimum and maximum.";
        for (String key : new String[]{"Multiplier", "Offset"}) {
            if (draft.containsKey(key) && !Float.isFinite(GaugeFace.number(draft, key, Float.NaN))) {
                return key + " must be a number.";
            }
        }
        String pid = GaugeFace.text(draft, "PID", "05").trim();
        if (pid.isEmpty()) return "Enter a Torque PID.";
        if (!GaugeFace.text(draft, "Reading format", "{value}").contains("{value}")) {
            return "Include {value} in the reading text to display the live number.";
        }
        for (String key : SIZE_KEYS) {
            if (GaugeFace.text(draft, key, "").trim().isEmpty()) continue;
            float size = GaugeFace.number(draft, key, Float.NaN);
            if (!Float.isFinite(size) || size < 1 || size > 300) return key + " must be between 1 and 300.";
        }
        return null;
    }

    @Override public void onBackPressed() {
        if (cancelColor != null) cancelColor.run();
        else super.onBackPressed();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(LinearLayout group, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setPadding(0, dp(8), 0, dp(2));
        group.addView(label);
        return label;
    }

    private Button button(LinearLayout group, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(v -> action.run());
        group.addView(button);
        return button;
    }

    private void track(SeekBar seekBar, java.util.function.IntConsumer action) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                action.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

 void pickColor(String key, EditText target){
  final float[] hsv={200,.5f,1}; String previous = target.getText().toString();
  Object originalDraft = draft.get(key);
  int fallback = key.equals("Needle color") ? GaugeFace.color(draft, "Indicator color", GaugeFace.BLUE)
          : GaugeFace.BLUE;
  int original = GaugeFace.color(draft, key, fallback); android.graphics.Color.colorToHSV(original,hsv);
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(20,12,20,12);
  SeekBar[] bars={new SeekBar(this),new SeekBar(this),new SeekBar(this)};
  TextView before=new TextView(this),after=new TextView(this);before.setText("Original");after.setText("Selected");before.setBackgroundColor(original);before.setTextColor(android.graphics.Color.WHITE);after.setTextColor(android.graphics.Color.WHITE);
  EditText hex=new EditText(this);hex.setSingleLine(true);hex.setHint("#RRGGBB");hex.setText(String.format(java.util.Locale.US,"#%06X",original&0xffffff));after.setBackgroundColor(original);
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
  final boolean[] busy={false};Runnable refresh=()->{if(busy[0])return;busy[0]=true;hsv[0]=bars[0].getProgress();hsv[1]=bars[1].getProgress()/100f;hsv[2]=bars[2].getProgress()/100f;int color=android.graphics.Color.HSVToColor(hsv);after.setBackgroundColor(color);after.setTextColor(hsv[2]>.65f&&hsv[1]<.65f?0xff000000:0xffffffff);hex.setText(String.format(java.util.Locale.US,"#%06X",color&0xffffff));spectrum.invalidate();busy[0]=false;target.setText(String.format(java.util.Locale.US,"#%06X",color&0xffffff));};
  for(SeekBar bar:bars)bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){refresh.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
  hex.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(busy[0]||!s.toString().matches("#[0-9a-fA-F]{6}"))return;busy[0]=true;int color=android.graphics.Color.parseColor(s.toString());android.graphics.Color.colorToHSV(color,hsv);bars[0].setProgress(Math.round(hsv[0]));bars[1].setProgress(Math.round(hsv[1]*100));bars[2].setProgress(Math.round(hsv[2]*100));after.setBackgroundColor(color);spectrum.invalidate();busy[0]=false;target.setText(String.format(java.util.Locale.US,"#%06X",color&0xffffff));}public void afterTextChanged(android.text.Editable e){}});
  panel.setVisibility(View.GONE);
  picker.addView(box);
  picker.setVisibility(View.VISIBLE);
  scroll.post(() -> scroll.scrollTo(0, 0));
  cancelColor = () -> {
   target.setText(previous);
   if (originalDraft == null) draft.remove(key);
   else draft.put(key, originalDraft);
   preview.invalidate();
   closeColor();
  };
  button(box, "Apply color", () -> {
   if (!hex.getText().toString().matches("#[0-9a-fA-F]{6}")) {
    hex.setError("Enter #RRGGBB");
    return;
   }
   target.setText(hex.getText().toString().toUpperCase(java.util.Locale.US));
   closeColor();
  });
  button(box, "Cancel color", () -> cancelColor.run());
 }

 private void closeColor() {
  cancelColor = null;
  picker.removeAllViews();
  picker.setVisibility(View.GONE);
  panel.setVisibility(View.VISIBLE);
  scroll.post(() -> scroll.scrollTo(0, settingsScroll));
 }
}
