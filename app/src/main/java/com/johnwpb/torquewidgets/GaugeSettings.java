package com.johnwpb.torquewidgets;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import java.io.IOException;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import java.util.HashMap;
import java.util.Map;
import android.app.*;import android.appwidget.*;import android.os.*;import android.content.*;import android.widget.*;
public class GaugeSettings extends Activity {
 private static final int PAGE = 0xff10161f;
 private static final int SURFACE = 0xff182332;
 private static final int CONTROL = 0xff243346;
 private static final int BORDER = 0xff34475c;
 private static final int TEXT = 0xffedf5ff;
 private static final int MUTED = 0xffabc0d7;
 private static final int ACCENT = 0xff84d5ff;
 private int id;
 private SharedPreferences prefs;
 private LinearLayout panel, section, footer;
 private final Map<String, Object> previewSettings = new HashMap<>();
 private Runnable refreshReadingFontSize;
 private ScrollView scroll;
 private LinearLayout picker;
 private GaugePreview preview;
 private Runnable cancelColor;
 private int settingsScroll;
 private static final int PICK_BACKGROUND = 801;
 private Switch indicatorNumbers, scaleNumbers;
 private Button removeBackground;
 private boolean backgroundChanged;
 private Switch editImage;
 private Button fitImage, locationAccess;
 private LinearLayout gpsControls, pidControls;
 private Button temperatureUnits, calibrateNeedle;
 private EditText customUnits, minimum, maximum;
 private static final int LOCATION_PERMISSION = 802;

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

 private void previewGaugeMode(String key, boolean value) {
  previewSetting(key, value);
  refreshReadingFontSize.run();
  updateCalibrationButton();
 }

 private int dp(int size) {
  return Math.round(size * getResources().getDisplayMetrics().density);
 }

 private LinearLayout column() {
  LinearLayout view = new LinearLayout(this);
  view.setOrientation(LinearLayout.VERTICAL);
  return view;
 }

 private GradientDrawable surface(int color, int radius) {
  GradientDrawable background = new GradientDrawable();
  background.setColor(color);
  background.setCornerRadius(dp(radius));
  if (color == SURFACE || color == CONTROL) background.setStroke(dp(1), BORDER);
  return background;
 }

 private TextView text(String value, int size, int color, boolean heading) {
  TextView view = new TextView(this);
  view.setText(value);
  view.setTextColor(color);
  view.setTextSize(size);
  view.setTypeface(Typeface.create(heading ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
  return view;
 }

 private void startSection(String title) {
  section = column();
  section.setBackground(surface(SURFACE, 16));
  section.setPadding(dp(12), dp(12), dp(12), dp(10));
  LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
  layout.bottomMargin = dp(12);
  panel.addView(section, layout);
  TextView heading = text(title, 16, ACCENT, true);
  heading.setPadding(0, 0, 0, dp(8));
  section.addView(heading);
 }

 private void styleSwitch(Switch control) {
  int[][] states = {{android.R.attr.state_checked}, {}};
  control.setThumbTintList(new ColorStateList(states, new int[]{0xffe1f5ff, MUTED}));
  control.setTrackTintList(new ColorStateList(states, new int[]{0xff3986ad, BORDER}));
  control.setTextColor(TEXT);
  control.setMinHeight(dp(48));
 }

 private Button action(String label, boolean primary) {
  Button button = new Button(this);
  button.setText(label);
  button.setAllCaps(false);
  button.setTextColor(primary ? PAGE : TEXT);
  button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3084d5ff),
          surface(primary ? ACCENT : CONTROL, 12), null));
  button.setMinHeight(dp(48));
  return button;
 }

 private void buildColumns() {
  LinearLayout root = column();
  root.setBackgroundColor(PAGE);
  root.setFocusableInTouchMode(true);
  TextView heading = text("Gauge settings", 24, TEXT, true);
  heading.setPadding(dp(16), dp(12), dp(16), dp(12));
  root.addView(heading);
  LinearLayout columns = new LinearLayout(this);
  root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
  scroll = new ScrollView(this);
  scroll.setClipToPadding(false);
  scroll.setPadding(dp(12), 0, dp(6), 0);
  LinearLayout left = column();
  panel = column();
  picker = column();
  picker.setBackground(surface(SURFACE, 16));
  picker.setVisibility(View.GONE);
  left.addView(panel);
  left.addView(picker);
  scroll.addView(left);
  int screenWidth = getResources().getConfiguration().screenWidthDp;
  float settingsWeight = screenWidth < 500 ? 2.1f : 1.2f;
  columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, settingsWeight));

  LinearLayout right = column();
  right.setBackground(surface(SURFACE, 16));
  right.setPadding(dp(12), dp(12), dp(12), dp(8));
  LinearLayout previewHeader = new LinearLayout(this);
  TextView previewTitle = text("Live preview", 17, TEXT, true);
  calibrateNeedle = action("Calibrate Needle", false);
  calibrateNeedle.setOnClickListener(view -> openNeedleCalibration());
  LinearLayout.LayoutParams calibrateLayout = new LinearLayout.LayoutParams(-2, -2);
  if (screenWidth < 500) {
   previewHeader.setOrientation(LinearLayout.VERTICAL);
   calibrateLayout.gravity = Gravity.END;
   previewHeader.addView(calibrateNeedle, calibrateLayout);
   previewHeader.addView(previewTitle);
  } else {
   previewHeader.setGravity(Gravity.CENTER_VERTICAL);
   previewHeader.addView(previewTitle, new LinearLayout.LayoutParams(0, -2, 1));
   previewHeader.addView(calibrateNeedle, calibrateLayout);
  }
  right.addView(previewHeader, new LinearLayout.LayoutParams(-1, -2));
  right.addView(text("Drag a label to move it", 12, MUTED, false));
  Switch noData = new Switch(this);
  noData.setText("Preview no data");
  styleSwitch(noData);
  right.addView(noData);
  preview = new GaugePreview(this, previewSettings);
  noData.setOnCheckedChangeListener((button, checked) -> {
   preview.noData = checked;
   preview.invalidate();
  });
  right.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
  preview.backgroundImage = GaugeBackground.read(this, id);
  Button browse = action("Browse background", false);
  browse.setOnClickListener(view -> {
   Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
           .addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(picker, PICK_BACKGROUND);
  });
  right.addView(browse, new LinearLayout.LayoutParams(-1, -2));
  removeBackground = action("Remove background", false);
  removeBackground.setVisibility(preview.backgroundImage == null ? View.GONE : View.VISIBLE);
  removeBackground.setOnClickListener(view -> setBackgroundImage(null));
  right.addView(removeBackground, new LinearLayout.LayoutParams(-1, -2));
  editImage = new Switch(this);
  editImage.setText("Edit image");
  styleSwitch(editImage);
  editImage.setOnCheckedChangeListener((button, checked) -> {
   preview.editBackground = checked;
   preview.invalidate();
  });
  right.addView(editImage);
  fitImage = action("Fit image", false);
  fitImage.setOnClickListener(view -> {
   resetImagePosition();
   preview.invalidate();
  });
  right.addView(fitImage, new LinearLayout.LayoutParams(-1, -2));
  updateImageButtons();
  LinearLayout.LayoutParams previewLayout = new LinearLayout.LayoutParams(0, -1, 1);
  previewLayout.setMargins(dp(6), 0, dp(12), dp(8));
  columns.addView(right, previewLayout);

  footer = new LinearLayout(this);
  footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
  footer.setPadding(dp(12), dp(8), dp(12), dp(12));
  root.addView(footer);
  setContentView(root);
 }

 private void closeColor() {
  cancelColor = null;
  picker.removeAllViews();
  picker.setVisibility(View.GONE);
  panel.setVisibility(View.VISIBLE);
  footer.setVisibility(View.VISIBLE);
  scroll.post(() -> scroll.scrollTo(0, settingsScroll));
 }

 @Override public void onBackPressed() {
  if (cancelColor != null) cancelColor.run();
  else super.onBackPressed();
 }

 private int automaticReadingFontSize() {
  boolean circle = Boolean.TRUE.equals(previewSettings.get("circle"));
  boolean informationOnly = Boolean.TRUE.equals(previewSettings.get("info"));
  return circle && !informationOnly ? 105 : 150;
 }

 private void sizeRow(TextView caption, String labelId, int automaticSize) {
  LinearLayout row = new LinearLayout(this);
  section.addView(row);
  row.setGravity(Gravity.CENTER_VERTICAL);
  row.setMinimumHeight(dp(48));
  row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
  boolean showCurrentSize = automaticSize > 0;
  TextView font = text(showCurrentSize ? "Font size:" : "Font:", 12, MUTED, false);
  row.addView(font);
  int[] presets = {0, 16, 20, 24, 28, 32, 36, 42, 48, 56, 64, 80, 96, 112, 128, 150, 175, 200};
  String key = GaugeLabels.key(labelId, "size");
  int savedSize = prefs.getInt(key, 0);
  int selected = 0;
  for (int i = 1; i < presets.length; i++) {
   if (presets[i] == savedSize) selected = i;
  }
  boolean includeSavedSize = showCurrentSize && savedSize > 0 && selected == 0;
  int[] sizes = includeSavedSize ? java.util.Arrays.copyOf(presets, presets.length + 1) : presets;
  if (includeSavedSize) {
   selected = presets.length;
   sizes[selected] = savedSize;
  }
  boolean reading = labelId.equals("reading");
  java.util.function.IntSupplier effectiveAutomaticSize =
          reading ? this::automaticReadingFontSize : () -> automaticSize;
  String[] options = new String[sizes.length];
  options[0] = showCurrentSize ? "Automatic (" + effectiveAutomaticSize.getAsInt() + ")" : "Default";
  for (int i = 1; i < sizes.length; i++) options[i] = Integer.toString(sizes[i]);
  String sizeTitle = caption.getText() + " font size";
  Button size = dropdownButton(sizeTitle, options, selected, 12,
          index -> index == 0 && showCurrentSize
                  ? Integer.toString(effectiveAutomaticSize.getAsInt()) : options[index],
          index -> previewSetting(key, sizes[index]));
  if (reading) {
   refreshReadingFontSize = () -> {
    String current = Integer.toString(effectiveAutomaticSize.getAsInt());
    options[0] = "Automatic (" + current + ")";
    if ((Integer) previewSettings.getOrDefault(key, 0) <= 0) {
     setDropdownCaption(size, sizeTitle, current);
    }
   };
  }
  if (showCurrentSize) size.setMinimumWidth(dp(52));
  LinearLayout.LayoutParams sizeLayout = new LinearLayout.LayoutParams(-2, -2);
  sizeLayout.leftMargin = dp(4);
  sizeLayout.rightMargin = dp(4);
  row.addView(size, sizeLayout);
  Switch visible = new Switch(this);
  styleSwitch(visible);
  visible.setContentDescription(labelId.equals("noData") ? caption.getText() : "Show " + caption.getText());
  String visibility = GaugeLabels.key(labelId, "visible");
  visible.setChecked(prefs.getBoolean(visibility, true));
  visible.setOnCheckedChangeListener((button, checked) -> previewSetting(visibility, checked));
  row.addView(visible, new LinearLayout.LayoutParams(-2, -2));
 }

 private void sizeRow(String title, String labelId, int automaticSize) {
  TextView caption = text(title, 14, TEXT, true);
  sizeRow(caption, labelId, automaticSize);
 }

 EditText field(String key, String fallback) {
  boolean colorField = key.endsWith("color");
  if (!colorField) {
   TextView caption = text(key.equals("Name") ? "Gauge title" : key, 14, TEXT, true);
   if (key.equals("Name")) sizeRow(caption, "title", 32);
   else if (key.equals("Units")) sizeRow(caption, "units", 48);
   else section.addView(caption);
  }
  EditText edit = new EditText(this);
  edit.setSingleLine(true);
  edit.setText(prefs.getString(key, fallback));
  edit.setTextColor(TEXT);
  edit.setTextSize(14);
  edit.setBackground(surface(CONTROL, 10));
  edit.setPadding(dp(10), 0, dp(10), 0);
  edit.setMinHeight(dp(44));
  LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
  layout.setMargins(0, dp(4), 0, dp(10));
  if (!colorField) section.addView(edit, layout);
  previewSettings.put(key, edit.getText().toString());
  watch(edit, value -> previewSetting(key, value));
  return edit;
 }

 private void addUnitsControls() {
  customUnits = field("Units", "°F");
  TemperatureUnit[] units = TemperatureUnit.values();
  String[] choices = {units[0].label, units[1].label};
  temperatureUnits = dropdownButton("Units", choices, TemperatureUnit.configured(previewSettings).ordinal(), 14,
          index -> selectTemperatureUnit(units[index]));
  LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
  layout.setMargins(0, dp(4), 0, dp(10));
  section.addView(temperatureUnits, layout);
 }

 private void selectTemperatureUnit(TemperatureUnit unit) {
  TemperatureUnit previous = TemperatureUnit.configured(previewSettings);
  if (previous != unit) {
   for (EditText range : new EditText[]{minimum, maximum}) {
    float value = GaugeFace.number(previewSettings, range == minimum ? "Minimum" : "Maximum", Float.NaN);
    float converted = previous.convertTo(value, unit);
    if (Float.isFinite(converted)) range.setText(Float.toString(converted));
   }
  }
  previewSetting(TemperatureUnit.KEY, unit.name());
 }

 private LinearLayout colorRow() {
  LinearLayout row = new LinearLayout(this);
  row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
  section.addView(row, new LinearLayout.LayoutParams(-2, -2));
  return row;
 }

 private void addColorRowSwitch(LinearLayout row, Switch control) {
  CharSequence title = control.getText();
  control.setText("");
  control.setContentDescription(title);
  row.addView(control, new LinearLayout.LayoutParams(-2, -2));
  TextView caption = text(title.toString(), 14, TEXT, false);
  caption.setOnClickListener(view -> control.performClick());
  LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2, 1);
  layout.setMarginStart(dp(6));
  row.addView(caption, layout);
 }

 private Button addColorButton(LinearLayout row, String colorName, Runnable openPicker) {
  Button button = action("Choose Color", false);
  button.setTextSize(12);
  button.setMinWidth(0);
  button.setMinimumWidth(0);
  button.setPadding(dp(8), 0, dp(8), 0);
  button.setContentDescription("Choose " + colorName);
  button.setOnClickListener(view -> openPicker.run());
  LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
  layout.setMarginStart(dp(6));
  row.addView(button, layout);
  return button;
 }

 private void indicatorStyleRow() {
  String title = "Indicator style";
  TextView caption = text(title, 14, TEXT, true);
  section.addView(caption);
  LinearLayout row = colorRow();
  String[] styles = {"Illuminated segments", "Sweeping needle", "Continuous arc", "None"};
  int savedStyle = (Integer) previewSettings.getOrDefault("style", 0);
  int selected = Math.max(0, Math.min(styles.length - 1, savedStyle));
  EditText indicatorColor = field("Indicator color", "#84D5FF");
  EditText needleColor = field("Needle color", "#84D5FF");
  Button chooseColor = addColorButton(row, "indicator color", () -> {
   Object style = previewSettings.get("style");
   if (Integer.valueOf(1).equals(style)) pickColor("Needle color", needleColor);
   else pickColor("Indicator color", indicatorColor);
  });
  java.util.function.IntConsumer showColorControl = index -> {
   boolean needle = index == 1;
   chooseColor.setVisibility(index == 3 ? View.GONE : View.VISIBLE);
   chooseColor.setContentDescription(needle ? "Choose needle color" : "Choose indicator color");
  };
  Button menu = dropdownButton(title, styles, selected, 14, index -> {
   previewSetting("style", index);
   showColorControl.accept(index);
   updateCalibrationButton();
  });
  menu.setEllipsize(android.text.TextUtils.TruncateAt.END);
  menu.setId(View.generateViewId());
  caption.setLabelFor(menu.getId());
  row.addView(menu, 0, new LinearLayout.LayoutParams(-2, -2, 1));
  showColorControl.accept(selected);
  updateCalibrationButton();
 }

 private void updateCalibrationButton() {
  boolean hasNeedle = Integer.valueOf(1).equals(previewSettings.get("style"))
          && !Boolean.TRUE.equals(previewSettings.get("info"));
  calibrateNeedle.setVisibility(hasNeedle ? View.VISIBLE : View.GONE);
 }

 private void openNeedleCalibration() {
  GpsMetric metric = GpsMetric.selected(previewSettings);
  float min = metric == null ? GaugeFace.number(previewSettings, "Minimum", Float.NaN) : metric.min;
  float max = metric == null ? GaugeFace.number(previewSettings, "Maximum", Float.NaN) : metric.max;
  if (!(max > min) || !Float.isFinite(max - min)) {
   Toast.makeText(this, "Set a valid gauge range before calibrating.", Toast.LENGTH_LONG).show();
   return;
  }
  String units = metric == null ? TemperatureUnit.displayUnits(previewSettings).trim() : metric.units;
  new NeedleCalibrationDialog(min, max, units).show();
 }

 private final class NeedleCalibrationDialog extends Dialog {
  private final float min, max;
  private final String units;
  private final String[] values = new String[2];
  private final float[] angles = new float[2];
  private final GaugePreview dial;
  private final TextView instruction;
  private final EditText value;
  private final Button back, reset, next;
  private int step;

  NeedleCalibrationDialog(float min, float max, String units) {
   super(GaugeSettings.this, android.R.style.Theme_Material_NoActionBar);
   this.min = min;
   this.max = max;
   this.units = units.isEmpty() ? "" : " " + units;
   NeedleCalibration saved = NeedleCalibration.read(previewSettings);
   boolean circle = Boolean.TRUE.equals(previewSettings.get("circle"));
   values[0] = saved == null ? mark(min) : mark(saved.lowValue);
   values[1] = saved == null ? "" : mark(saved.highValue);
   angles[0] = saved == null ? NeedleCalibration.defaultAngle(circle, 0) : saved.lowAngle;
   angles[1] = saved == null ? NeedleCalibration.defaultAngle(circle, .5f) : saved.highAngle;
   LinearLayout content = column();
   content.setBackgroundColor(PAGE);
   content.setPadding(dp(16), dp(12), dp(16), dp(12));
   content.setFocusableInTouchMode(true);
   content.addView(text("Calibrate Needle", 22, TEXT, true));
   instruction = text("", 14, MUTED, false);
   instruction.setPadding(0, dp(4), 0, dp(6));
   content.addView(instruction);

   dial = new GaugePreview(GaugeSettings.this, previewSettings);
   dial.backgroundImage = preview.backgroundImage;
   dial.calibratingNeedle = true;
   dial.setContentDescription("Gauge preview. Drag the needle to the mark.");
   content.addView(dial, new LinearLayout.LayoutParams(-1, 0, 1));

   value = new EditText(GaugeSettings.this);
   value.setSingleLine(true);
   value.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
           | InputType.TYPE_NUMBER_FLAG_SIGNED);
   value.setTextColor(TEXT);
   value.setHintTextColor(MUTED);
   value.setTextSize(16);
   value.setSelectAllOnFocus(true);
   value.setBackground(surface(CONTROL, 10));
   value.setPadding(dp(12), 0, dp(12), 0);
   value.setMinHeight(dp(48));
   watch(value, input -> {
    dial.calibrationValue = enteredValue(input);
    dial.invalidate();
   });
   content.addView(value, new LinearLayout.LayoutParams(-1, -2));
   LinearLayout buttons = new LinearLayout(GaugeSettings.this);
   buttons.setPadding(0, dp(8), 0, 0);
   content.addView(buttons);
   Button cancel = action("Cancel", false);
   cancel.setOnClickListener(view -> dismiss());
   buttons.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
   back = action("Back", false);
   back.setOnClickListener(view -> {
    rememberPoint();
    showStep(0);
   });
   buttons.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
   reset = action("Reset", false);
   reset.setOnClickListener(view -> {
    previewSettings.remove(NeedleCalibration.KEY);
    preview.invalidate();
    dismiss();
   });
   buttons.addView(reset, new LinearLayout.LayoutParams(0, -2, 1));
   next = action("Next", true);
   next.setOnClickListener(view -> acceptPoint());
   buttons.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
   setContentView(content);
   getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
           | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
   showStep(0);
  }

  private void showStep(int index) {
   step = index;
   if (value.getWindowToken() != null) {
    getSystemService(InputMethodManager.class).hideSoftInputFromWindow(value.getWindowToken(), 0);
   }
   value.clearFocus();
   value.setError(null);
   instruction.setText(index == 0
           ? "1 of 2. Drag the needle to a lower mark, then enter its value."
           : "2 of 2. Drag the needle clockwise to a higher mark and enter its value. Tap Apply, then Save in gauge settings.");
   String hint = units.isEmpty() ? "Value at the needle" : "Value at the needle (" + units.trim() + ")";
   value.setHint(hint);
   value.setContentDescription(hint);
   dial.calibrationAngle = angles[index];
   value.setText(values[index]);
   dial.invalidate();
   back.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
   reset.setVisibility(index == 0 && previewSettings.containsKey(NeedleCalibration.KEY)
           ? View.VISIBLE : View.GONE);
   next.setText(index == 0 ? "Next" : "Apply");
  }

  private void rememberPoint() {
   values[step] = value.getText().toString();
   angles[step] = dial.calibrationAngle;
  }

  private void acceptPoint() {
   rememberPoint();
   float reading = enteredValue(values[step]);
   if (!Float.isFinite(reading) || reading < min || reading > max) {
    value.setError("Enter a value from " + mark(min) + " to " + mark(max) + units + ".");
    return;
   }
   if (step == 0) {
    showStep(1);
    return;
   }
   float low = enteredValue(values[0]);
   if (reading <= low) {
    value.setError("Enter a value higher than " + mark(low) + units + ".");
    return;
   }
   if (!NeedleCalibration.differentAngles(angles[0], angles[1])) {
    Toast.makeText(GaugeSettings.this, "Drag the needle to a different mark.", Toast.LENGTH_LONG).show();
    return;
   }
   NeedleCalibration calibration = new NeedleCalibration(low, angles[0], reading, angles[1]);
   previewSetting(NeedleCalibration.KEY, calibration.encode(previewSettings));
   dismiss();
  }

  private float enteredValue(String input) {
   try {
    return Float.parseFloat(input.trim().replace(',', '.'));
   } catch (NumberFormatException error) {
    return Float.NaN;
   }
  }

  private String mark(float reading) {
   String number = Float.toString(reading);
   return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
  }
 }

 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().setStatusBarColor(PAGE);
  getWindow().setNavigationBarColor(PAGE);
  id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
  setResult(RESULT_CANCELED);
  if (id < 0 && !(this instanceof DashboardGaugeSettings && id < -1)) {
   LinearLayout root = column();
   root.setBackgroundColor(PAGE);
   root.setPadding(dp(16), dp(16), dp(16), dp(16));
   root.addView(text("Add Torque Widgets from your home screen widget picker.", 16, TEXT, false));
   Button backup = action("Make a backup", false);
   backup.setOnClickListener(view -> GaugeBackup.share(this));
   root.addView(backup, new LinearLayout.LayoutParams(-2, -2));
   setContentView(root);
   return;
  }
  prefs = CoolantWidget.prefs(this, id);
  previewSettings.putAll(prefs.getAll());
  if (previewSettings.isEmpty()) previewSettings.put("style", 1);
  buildColumns();

  startSection("Labels");
  field("Name", "Coolant");
  addUnitsControls();
  sizeRow("Reading", "reading", automaticReadingFontSize());
  sizeRow("Display no data when information is unavailable", "noData", 23);

  startSection("Gauge");
  dropdown("Gauge style", new String[]{"Semicircular", "Circular"},
          prefs.getBoolean("circle", false) ? 1 : 0, index -> previewGaugeMode("circle", index == 1));
  LinearLayout ticks = colorRow();
  addColorRowSwitch(ticks, toggle("showTicks", "Show tick marks", true));
  EditText tickColor = field("Tick color", "#84D5FF");
  addColorButton(ticks, "tick color", () -> pickColor("Tick color", tickColor));
  LinearLayout numbers = colorRow();
  indicatorNumbers = scaleSwitch("Show indicator numbers");
  addColorRowSwitch(numbers, indicatorNumbers);
  EditText fontColor = field("Font color", "#84D5FF");
  addColorButton(numbers, "shared font color", () -> pickColor("Font color", fontColor));
  Switch informationOnly = toggle("info", "Information only", false);
  informationOnly.setOnCheckedChangeListener((button, checked) -> previewGaugeMode("info", checked));
  section.addView(informationOnly);
  indicatorStyleRow();
  startSection("Information provider");
  boolean useGps = GpsMetric.selected(previewSettings) != null;
  dropdown("Information provider", new String[]{"PID", "GPS information"}, useGps ? 1 : 0, index -> {
   previewSetting("gpsProvider", index == 1);
   updateProvider();
  });
  LinearLayout provider = section;
  pidControls = column();
  provider.addView(pidControls);
  section = pidControls;
  EditText pid = field("PID", "05");
  watch(pid, value -> updateUnitsControls());
  minimum = field("Minimum", "0");
  maximum = field("Maximum", "260");
  gpsControls = column();
  provider.addView(gpsControls);
  section = gpsControls;
  GpsMetric[] metrics = GpsMetric.values();
  String[] choices = new String[metrics.length];
  for (int i = 0; i < metrics.length; i++) choices[i] = metrics[i].label;
  GpsMetric selected = GpsMetric.configured(previewSettings);
  dropdown("GPS information", choices, selected.ordinal(),
          index -> previewSetting("gpsMetric", metrics[index].name()));
  locationAccess = action("Allow GPS access", false);
  locationAccess.setOnClickListener(view -> requestGpsAccess());
  gpsControls.addView(locationAccess);
  updateProvider();
  startSection("Scale numbers");
  scaleNumbers = scaleSwitch("Show all scale numbers");
  section.addView(scaleNumbers);
  for (int i = 0; i < 9; i++) sizeRow("Number " + (i + 1), "scale" + i, 0);

  startSection("Backup");
  Button backup = action("Make a backup", false);
  backup.setOnClickListener(view -> GaugeBackup.share(this));
  section.addView(backup, new LinearLayout.LayoutParams(-2, -2));

  Button cancel = action("Cancel", false);
  cancel.setOnClickListener(view -> finish());
  footer.addView(cancel, new LinearLayout.LayoutParams(dp(92), -2));
  Button save = action("Save", true);
  save.setOnClickListener(view -> save());
  LinearLayout.LayoutParams saveLayout = new LinearLayout.LayoutParams(dp(96), -2);
  saveLayout.leftMargin = dp(8);
  footer.addView(save, saveLayout);
 }

 private void dropdown(String title, String[] choices, int selected, java.util.function.IntConsumer change) {
  TextView caption = text(title, 14, TEXT, true);
  section.addView(caption);
  Button menu = dropdownButton(title, choices, selected, 14, change);
  menu.setId(View.generateViewId());
  caption.setLabelFor(menu.getId());
  LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
  layout.topMargin = dp(4);
  layout.bottomMargin = dp(5);
  section.addView(menu, layout);
 }

 private Button dropdownButton(String title, String[] choices, int selected, int textSize,
                               java.util.function.IntConsumer change) {
  return dropdownButton(title, choices, selected, textSize, index -> choices[index], change);
 }

 private void setDropdownCaption(Button button, String title, String current) {
  button.setText(current + " ▾");
  button.setContentDescription(title + ", " + current);
 }

 private Button dropdownButton(String title, String[] choices, int selected, int textSize,
                               java.util.function.IntFunction<String> caption,
                               java.util.function.IntConsumer change) {
  Button button = new Button(this);
  button.setAllCaps(false);
  button.setTextSize(textSize);
  button.setTextColor(TEXT);
  button.setSingleLine(true);
  button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
  button.setMinWidth(0);
  button.setMinimumWidth(0);
  button.setMinHeight(dp(44));
  button.setMinimumHeight(dp(44));
  button.setPadding(dp(8), 0, dp(8), 0);
  button.setStateListAnimator(null);
  button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24ffffff),
          surface(0xff34373d, 8), null));
  int[] selection = {Math.max(0, Math.min(choices.length - 1, selected))};
  Runnable refresh = () -> setDropdownCaption(button, title, caption.apply(selection[0]));
  refresh.run();
  button.setOnClickListener(view -> {
   PopupMenu popup = new PopupMenu(this, button, Gravity.START);
   for (int i = 0; i < choices.length; i++) {
    popup.getMenu().add(0, i, i, choices[i]).setCheckable(true).setChecked(i == selection[0]);
   }
   popup.setOnMenuItemClickListener(item -> {
    selection[0] = item.getItemId();
    refresh.run();
    change.accept(selection[0]);
    return true;
   });
   popup.show();
  });
  return button;
 }

 private Switch scaleSwitch(String title) {
  Switch control = new Switch(this);
  control.setText(title);
  styleSwitch(control);
  control.setChecked(!Boolean.FALSE.equals(previewSettings.get("showScale")));
  control.setOnCheckedChangeListener((button, checked) -> {
   if (Boolean.valueOf(checked).equals(previewSettings.get("showScale"))) return;
   previewSetting("showScale", checked);
   indicatorNumbers.setChecked(checked);
   scaleNumbers.setChecked(checked);
  });
  return control;
 }

 @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
  super.onActivityResult(requestCode, resultCode, data);
  if (requestCode != PICK_BACKGROUND || resultCode != RESULT_OK || data == null || data.getData() == null) return;
  try {
   setBackgroundImage(GaugeBackground.open(getContentResolver(), data.getData()));
  } catch (IOException | SecurityException e) {
   Toast.makeText(this, "Choose a readable image.", Toast.LENGTH_LONG).show();
  }
 }

 private void setBackgroundImage(Bitmap image) {
  preview.backgroundImage = image;
  backgroundChanged = true;
  resetImagePosition();
  updateImageButtons();
  editImage.setChecked(image != null);
  preview.invalidate();
 }

 private void resetImagePosition() {
  for (String key : new String[]{"imageLeft", "imageTop", "imageWidth"}) previewSettings.remove(key);
 }

 private void updateImageButtons() {
  int visibility = preview.backgroundImage == null ? View.GONE : View.VISIBLE;
  removeBackground.setVisibility(visibility);
  editImage.setVisibility(visibility);
  fitImage.setVisibility(visibility);
 }

 private boolean locationGranted(String permission) {
  return checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED;
 }

 private void updateProvider() {
  if (gpsControls == null || locationAccess == null) return;
  boolean gps = GpsMetric.selected(previewSettings) != null;
  gpsControls.setVisibility(gps ? View.VISIBLE : View.GONE);
  pidControls.setVisibility(gps ? View.GONE : View.VISIBLE);
  updateUnitsControls();
  boolean fine = locationGranted(android.Manifest.permission.ACCESS_FINE_LOCATION);
  boolean background = Build.VERSION.SDK_INT < 29
          || locationGranted(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
  locationAccess.setVisibility(fine && background ? View.GONE : View.VISIBLE);
  locationAccess.setText(fine ? "Allow GPS on home screen" : "Allow precise GPS");
 }

 private void updateUnitsControls() {
  boolean gps = GpsMetric.selected(previewSettings) != null;
  boolean temperature = TemperatureUnit.isTemperaturePid(GaugeFace.text(previewSettings, "PID", "05"));
  temperatureUnits.setVisibility(!gps && temperature ? View.VISIBLE : View.GONE);
  customUnits.setVisibility(!gps && !temperature ? View.VISIBLE : View.GONE);
 }

 @Override protected void onResume() {
  super.onResume();
  updateProvider();
 }

 @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
  super.onRequestPermissionsResult(requestCode, permissions, results);
  if (requestCode == LOCATION_PERMISSION) updateProvider();
 }

 private void requestGpsAccess() {
  if (!locationGranted(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
   requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
           android.Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
   return;
  }
  new AlertDialog.Builder(this).setTitle("GPS on the home screen")
          .setMessage("In app permissions, open Location and choose Allow all the time for home-screen GPS readings.")
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Open settings", (dialog, which) -> startActivity(new Intent(
                  android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  android.net.Uri.fromParts("package", getPackageName(), null))))
          .show();
 }

 private Switch toggle(String key, String title, boolean fallback) {
  Switch control = new Switch(this);
  control.setText(title);
  styleSwitch(control);
  control.setChecked(prefs.getBoolean(key, fallback));
  previewSettings.put(key, control.isChecked());
  control.setOnCheckedChangeListener((view, value) -> previewSetting(key, value));
  return control;
 }

 private void save() {
  float min = GaugeFace.number(previewSettings, "Minimum", Float.NaN);
  float max = GaugeFace.number(previewSettings, "Maximum", Float.NaN);
  boolean valid = Float.isFinite(max - min) && max > min
          && !GaugeFace.text(previewSettings, "PID", "").trim().isEmpty();
  if (GpsMetric.selected(previewSettings) == null && !valid) {
   Toast.makeText(this, "Check the range and Torque PID.", Toast.LENGTH_LONG).show();
   return;
  }
  for (String key : new String[]{"Font color", "Tick color", "Indicator color", "Needle color"}) {
   try {
    android.graphics.Color.parseColor(GaugeFace.text(previewSettings, key, "").trim());
   } catch (IllegalArgumentException e) {
    Toast.makeText(this, "Check the gauge colors.", Toast.LENGTH_LONG).show();
    return;
   }
  }
  if (backgroundChanged) {
   try {
    GaugeBackground.save(this, id, preview.backgroundImage);
   } catch (IOException e) {
    Toast.makeText(this, "Could not save the background image.", Toast.LENGTH_LONG).show();
    return;
   }
  }
  SharedPreferences.Editor editor = prefs.edit();
  for (String key : new String[]{"imageLeft", "imageTop", "imageWidth", NeedleCalibration.KEY}) {
   if (!previewSettings.containsKey(key)) editor.remove(key);
  }
  for (Map.Entry<String, Object> entry : previewSettings.entrySet()) {
   String key = entry.getKey();
   Object value = entry.getValue();
   if (value instanceof String) editor.putString(key, ((String) value).trim());
   else if (value instanceof Float) editor.putFloat(key, (Float) value);
   else if (value instanceof Integer) editor.putInt(key, (Integer) value);
   else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
  }
  editor.apply();
  if (this instanceof DashboardGaugeSettings) {
   setResult(RESULT_OK);
   finish();
   return;
  }
  if (GpsMetric.selected(previewSettings) != null
          && locationGranted(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
   try {
    startForegroundService(new Intent(this, GpsWidgetService.class));
   } catch (IllegalStateException | SecurityException e) {
    Toast.makeText(this, "Live GPS could not start. Reopen widget settings and save again.",
            Toast.LENGTH_LONG).show();
   }
  }
  setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
  sendBroadcast(new Intent(this, CoolantWidget.class)
          .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
          .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{id}));
  finish();
 }

 void pickColor(String key, EditText target){
  String previous = target.getText().toString();
  settingsScroll = scroll.getScrollY();
  final float[] hsv={200,.5f,1}; int original=android.graphics.Color.parseColor(target.getText().toString()); android.graphics.Color.colorToHSV(original,hsv);
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(12),dp(12),dp(12),dp(12));box.addView(text(key,16,ACCENT,true));
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
  footer.setVisibility(View.GONE);
  picker.addView(box);
  picker.setVisibility(View.VISIBLE);
  scroll.post(() -> scroll.scrollTo(0, 0));
  cancelColor = () -> {
   previewSetting(key, previous);
   closeColor();
  };
  Button apply = action("Apply color", true);
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
  Button cancel = action("Cancel color", false);
  box.addView(cancel);
  cancel.setOnClickListener(v -> cancelColor.run());
 }
}
