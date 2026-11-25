package com.johnwpb.torquewidgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class GaugeSettings extends Activity {
    private int id;
    private SharedPreferences prefs;
    private GaugeConfig config;
    private GaugePreview preview;
    private ScrollView scroll;
    private LinearLayout panel, colors, editor;
    private Spinner labelPicker;
    private List<GaugeRenderer.Label> labels;
    private TextView position, testValue;
    private View customConversion;
    private boolean restoringColor;
    private boolean ready;
    private Runnable closeColor;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || !ownsWidget(id)) return;
        prefs = CoolantWidget.prefs(this, id);
        config = new GaugeConfig(prefs);
        if (state != null && state.getSerializable("draft") instanceof HashMap) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> draft = (HashMap<String, Object>) state.getSerializable("draft");
            config.values.clear();
            config.values.putAll(draft);
        }
        LinearLayout root = column();
        LinearLayout columns = new LinearLayout(this);
        root.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(this);
        LinearLayout left = column();
        left.setPadding(dp(12), dp(10), dp(12), dp(14));
        panel = column();
        colors = column();
        colors.setVisibility(View.GONE);
        left.addView(panel);
        left.addView(colors);
        scroll.addView(left);
        columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout right = column();
        right.setPadding(dp(8), dp(12), dp(8), 0);
        columns.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        preview = new GaugePreview(this, config, element -> {
            selectLabel(element);
            if (closeColor == null) scroll.post(() -> scroll.smoothScrollTo(0, 0));
        }, this::updatePositionLabel);
        preview.fraction = state == null ? .5f : state.getFloat("fraction", .5f);
        preview.offline = state != null && state.getBoolean("offline");
        preview.snap = state == null ? config.flag("snap", false) : state.getBoolean("snap");
        preview.guides = state == null ? config.flag("guides", true) : state.getBoolean("guides");
        testValue = label(right, "");
        SeekBar slider = new SeekBar(this);
        slider.setContentDescription("Simulated vehicle reading");
        slider.setMax(1000);
        slider.setProgress(Math.round(preview.fraction * 1000));
        right.addView(slider);
        GaugeColorPicker.track(slider, progress -> {
            preview.fraction = progress / 1000f;
            changed();
        });
        toggle(right, "Simulate no data", preview.offline, value -> {
            preview.offline = value;
            slider.setEnabled(!value);
            changed();
        });
        slider.setEnabled(!preview.offline);
        right.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        label(panel, "Text elements");
        labelPicker = new Spinner(this);
        panel.addView(labelPicker);
        editor = column();
        panel.addView(editor);
        button(panel, "Add custom label", () -> {
            String newId = "custom-" + UUID.randomUUID();
            ArrayList<String> ids = customIds();
            ids.add(newId);
            config.values.put("customLabels", String.join(",", ids));
            populateLabels(newId);
        });
        toggle(panel, "Snap to grid", preview.snap, value -> {
            preview.snap = value;
            config.values.put("snap", value);
            changed();
        });
        toggle(panel, "Alignment guides", preview.guides, value -> {
            preview.guides = value;
            config.values.put("guides", value);
            changed();
        });

        label(panel, "Gauge");
        configField(panel, "Gauge title", "Name", "Coolant");
        configField(panel, "Torque PID", "PID", "05");
        configField(panel, "Minimum", "Minimum", "0");
        configField(panel, "Maximum", "Maximum", "260");
        String[] layouts = config.layout() == 2
                ? new String[]{"Circular", "Rectangular", "Semicircular"}
                : new String[]{"Circular", "Rectangular"};
        choice(panel, "Layout", layouts, config.layout(), value -> put("layout", value));
        choice(panel, "Indicator style", new String[]{"Illuminated segments", "Sweeping needle",
                "Continuous indicator", "None"}, config.integer("style", 0), value -> put("style", value));
        toggle(panel, "Display information only", config.flag("info", false), value -> put("info", value));
        colorButton(panel, "Default text color", "Font color", GaugeConfig.BLUE);
        colorButton(panel, "Tick mark color", "Tick color", GaugeConfig.BLUE);
        colorButton(panel, "Indicator color", "Indicator color", GaugeConfig.BLUE);
        colorButton(panel, "Needle color", "Needle color", config.color("Indicator color", GaugeConfig.BLUE));
        LinearLayout conversion = column();
        customConversion = conversion;
        choice(panel, "Temperature and units", new String[]{"Custom units and conversion",
                "Celsius (Torque PID reports °C)", "Fahrenheit (Torque PID reports °C)"},
                config.integer("temperature", 0), this::changeTemperature);
        panel.addView(conversion);
        configField(conversion, "Units", "Units", "°F");
        configField(conversion, "Multiplier", "Multiplier", "1.8");
        configField(conversion, "Offset", "Offset", "32");
        conversion.setVisibility(config.integer("temperature", 0) == 0 ? View.VISIBLE : View.GONE);

        LinearLayout actions = new LinearLayout(this);
        root.addView(actions);
        Button cancel = button(actions, "Cancel", this::finish);
        cancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button save = button(actions, "Save", this::save);
        save.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        populateLabels(state == null ? null : state.getString("selected"));
        updatePreview();
        setContentView(root);
        ready = true;
        if (state != null) {
            scroll.post(() -> scroll.scrollTo(0, state.getInt("scroll")));
            String colorKey = state.getString("colorKey");
            if (colorKey != null) {
                restoringColor = true;
                restoreColorDraft(colorKey, state.getString("colorOriginal"));
                restoringColor = false;
            }
        }
    }

    private void changeTemperature(int mode) {
        put("temperature", mode);
        customConversion.setVisibility(mode == 0 ? View.VISIBLE : View.GONE);
    }

    @Override protected void onResume() {
        super.onResume();
        if (config == null) showWidgetList();
    }

    private boolean ownsWidget(int widgetId) {
        return CoolantWidget.ownsWidget(this, AppWidgetManager.getInstance(this), widgetId);
    }

    private void showWidgetList() {
        ScrollView list = new ScrollView(this);
        LinearLayout items = column();
        items.setPadding(dp(16), dp(16), dp(16), dp(16));
        list.addView(items);
        label(items, "Torque Widgets");
        int[] widgets = AppWidgetManager.getInstance(this)
                .getAppWidgetIds(new ComponentName(this, CoolantWidget.class));
        if (widgets.length == 0) label(items, "Add a gauge from your home screen’s widget picker.");
        else {
            Button live = button(items, TorqueLiveService.running ? "Stop live updates" : "Start live updates",
                    () -> { });
            live.setOnClickListener(view -> {
                Intent service = new Intent(this, TorqueLiveService.class);
                if (TorqueLiveService.running) {
                    stopService(service);
                    live.setText("Start live updates");
                } else {
                    startForegroundService(service);
                    live.setText("Stop live updates");
                }
            });
        }
        for (int widget : widgets) {
            GaugeConfig placed = new GaugeConfig(CoolantWidget.prefs(this, widget));
            button(items, placed.string("Name", "Coolant") + " · " + placed.units() + " · #" + widget,
                    () -> startActivity(new Intent(this, GaugeSettings.class)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget)));
        }
        setContentView(list);
    }

    private ArrayList<String> customIds() {
        ArrayList<String> ids = new ArrayList<>();
        for (String id : config.string("customLabels", "").split(",")) {
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private void populateLabels(String selected) {
        labels = GaugeRenderer.labels(config, preview.reading());
        ArrayList<String> names = new ArrayList<>();
        int selection = 0;
        String target = selected == null ? "title" : selected;
        for (int i = 0; i < labels.size(); i++) {
            GaugeRenderer.Label item = labels.get(i);
            names.add(item.name);
            if (item.id.equals(target)) selection = i;
        }
        labelPicker.setOnItemSelectedListener(null);
        labelPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        labelPicker.setSelection(selection);
        preview.selected = null;
        selectLabel(labels.get(selection).id);
        listen(labelPicker, index -> selectLabel(labels.get(index).id));
    }

    private void selectLabel(String element) {
        if (element.equals(preview.selected)) return;
        preview.selected = element;
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).id.equals(element)) {
                labelPicker.setSelection(i);
                break;
            }
        }
        showLabelEditor(element);
        preview.invalidate();
    }

    private void showLabelEditor(String id) {
        editor.removeAllViews();
        GaugeRenderer.Label item = currentLabel(id);
        if (item == null) return;
        if (id.equals("reading")) {
            String[] precision = {"0", "1", "2", "3", "4", "5", "6"};
            choice(editor, "Decimal places", precision, config.integer("decimals", 0),
                    value -> put("decimals", value));
            configField(editor, "Prefix", "prefix", "");
            configField(editor, "Suffix", "suffix", "");
        }
        if (id.equals("low") || id.equals("high")) {
            configField(editor, "Warning text", id + "Message", id.equals("low") ? "LOW" : "HIGH");
            configField(editor, id.equals("low") ? "Warn at or below" : "Warn at or above", id + "Limit", "");
        }
        if (id.equals("noData")) configField(editor, "Message", "noDataMessage", "NO DATA");
        if (id.startsWith("custom-")) {
            configField(editor, "Label text", key(id, "content"), "Custom label");
            button(editor, "Remove custom label", () -> {
                ArrayList<String> ids = customIds();
                ids.remove(id);
                config.values.put("customLabels", String.join(",", ids));
                config.values.keySet().removeIf(key -> key.startsWith("text." + id + "."));
                populateLabels("title");
            });
        }
        int family = java.util.Arrays.asList(GaugeRenderer.FONTS)
                .indexOf(config.string(key(id, "font"), GaugeRenderer.FONTS[0]));
        choice(editor, "Font", GaugeRenderer.FONTS, Math.max(0, family),
                value -> put(key(id, "font"), GaugeRenderer.FONTS[value]));
        configField(editor, "Font size (relative to gauge)", key(id, "size"),
                config.string(key(id, "size"), "")).setHint(GaugeRenderer.number(item.size));
        colorButton(editor, "Text color", key(id, "color"), config.color("Font color", GaugeConfig.BLUE));
        toggle(editor, "Bold", config.flag(key(id, "bold"), false), value -> put(key(id, "bold"), value));
        toggle(editor, "Visible", config.flag(key(id, "visible"), !id.equals("min") && !id.equals("max")),
                value -> put(key(id, "visible"), value));
        toggle(editor, "Lock position", config.flag(key(id, "locked"), false),
                value -> put(key(id, "locked"), value));
        position = label(editor, "");
        button(editor, "Reset position", () -> {
            config.values.remove(key(id, "x"));
            config.values.remove(key(id, "y"));
            changed();
        });
        updatePositionLabel();
    }

    private String key(String id, String property) { return config.textKey(id, property); }

    private GaugeRenderer.Label currentLabel(String id) {
        for (GaugeRenderer.Label item : GaugeRenderer.labels(config, preview.reading())) {
            if (item.id.equals(id)) return item;
        }
        return null;
    }

    private void updatePositionLabel() {
        if (position == null) return;
        GaugeRenderer.Label item = currentLabel(preview.selected);
        if (item == null) return;
        position.setText(String.format(Locale.getDefault(), "Position  %.1f%% across, %.1f%% down",
                GaugeRenderer.position(config, item, "x", item.x) / 8,
                GaugeRenderer.position(config, item, "y", item.y) / 8));
    }

    private void put(String key, Object value) {
        config.values.put(key, value);
        changed();
    }

    private void changed() {
        if (ready) updatePreview();
    }

    private void updatePreview() {
        testValue.setText(preview.offline ? "Test value · NO DATA"
                : String.format(Locale.getDefault(), "Test value · %.2f %s", preview.reading(), config.units()));
        updatePositionLabel();
        preview.invalidate();
    }

    private void save() {
        if (closeColor != null) {
            Toast.makeText(this, "Apply or cancel the open color picker first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ownsWidget(id)) {
            finish();
            return;
        }
        String error = validationError();
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        config.save(prefs);
        if (AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(getIntent().getAction())) {
            CoolantWidget.render(this, AppWidgetManager.getInstance(this), id, config, Float.NaN);
        }
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        sendBroadcast(new Intent(this, CoolantWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{id}));
        finish();
    }

    private String validationError() {
        float min = config.number("Minimum", Float.NaN), max = config.number("Maximum", Float.NaN);
        if (!config.values.containsKey("Minimum")) min = config.minimum();
        if (!config.values.containsKey("Maximum")) max = config.maximum();
        if (!Float.isFinite(max - min) || max <= min) return "Enter a valid minimum and a greater maximum.";
        if (config.string("PID", "05").trim().isEmpty()) return "Enter a Torque PID.";
        if (config.integer("temperature", 0) == 0) {
            for (String key : new String[]{"Multiplier", "Offset"}) {
                if (config.values.containsKey(key) && !Float.isFinite(config.number(key, Float.NaN))) {
                    return key + " must be a number.";
                }
            }
        }
        for (String key : new String[]{"lowLimit", "highLimit"}) {
            if (!config.string(key, "").trim().isEmpty() && !Float.isFinite(config.number(key, Float.NaN))) {
                return "Enter numeric warning thresholds or leave them blank.";
            }
        }
        for (GaugeRenderer.Label item : GaugeRenderer.labels(config, 0)) {
            String key = key(item.id, "size");
            if (config.string(key, "").trim().isEmpty()) continue;
            float size = config.number(key, Float.NaN);
            if (!Float.isFinite(size) || size < 1 || size > GaugeRenderer.SIZE) {
                return item.name + " font size must be between 1 and 800.";
            }
        }
        return null;
    }

    private String activeColorKey, originalColor;
    private int previousScroll;

    private void colorButton(LinearLayout group, String title, String key, int fallback) {
        button(group, title, () -> openColor(title, key, fallback, config.string(key, null)));
    }

    private void restoreColorDraft(String key, String original) {
        int fallback = key.equals("Needle color") ? config.color("Indicator color", GaugeConfig.BLUE)
                : key.startsWith("text.") ? config.color("Font color", GaugeConfig.BLUE) : GaugeConfig.BLUE;
        openColor(key.startsWith("text.") ? "Text color" : key, key, fallback, original);
    }

    private void openColor(String title, String key, int fallback, String original) {
        originalColor = original;
        activeColorKey = key;
        previousScroll = scroll.getScrollY();
        int current = config.color(key, fallback);
        int before = fallback;
        if (original != null && !original.isEmpty()) {
            try { before = android.graphics.Color.parseColor(original); }
            catch (IllegalArgumentException ignored) { }
        }
        panel.setVisibility(View.GONE);
        colors.setVisibility(View.VISIBLE);
        colors.removeAllViews();
        label(colors, title);
        GaugeColorPicker picker = new GaugeColorPicker(this, before, current, value -> put(key, value));
        colors.addView(picker);
        closeColor = () -> {
            if (original == null) config.values.remove(key);
            else config.values.put(key, original);
            endColor();
        };
        button(colors, "Apply color", () -> {
            String color = picker.validColor();
            if (color == null) return;
            put(key, color);
            endColor();
        });
        button(colors, "Cancel color", () -> closeColor.run());
        if (!restoringColor) scroll.scrollTo(0, 0);
    }

    private void endColor() {
        closeColor = null;
        activeColorKey = null;
        colors.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        showLabelEditor(preview.selected);
        updatePreview();
        scroll.post(() -> scroll.scrollTo(0, previousScroll));
    }

    @Override public void onBackPressed() {
        if (closeColor != null) closeColor.run();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (config == null) return;
        state.putSerializable("draft", new HashMap<>(config.values));
        state.putString("selected", preview.selected);
        state.putFloat("fraction", preview.fraction);
        state.putBoolean("offline", preview.offline);
        state.putBoolean("snap", preview.snap);
        state.putBoolean("guides", preview.guides);
        state.putInt("scroll", scroll.getScrollY());
        state.putString("colorKey", activeColorKey);
        state.putString("colorOriginal", originalColor);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView label(LinearLayout group, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, dp(8), 0, dp(1));
        group.addView(view);
        return view;
    }

    private Button button(LinearLayout group, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        group.addView(button);
        return button;
    }

    private EditText configField(LinearLayout group, String title, String key, String fallback) {
        TextView caption = label(group, title);
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setText(config.string(key, fallback));
        field.setId(View.generateViewId());
        caption.setLabelFor(field.getId());
        group.addView(field);
        watch(field, value -> put(key, value));
        return field;
    }

    private void toggle(LinearLayout group, String title, boolean initial, Consumer<Boolean> change) {
        Switch control = new Switch(this);
        control.setText(title);
        control.setChecked(initial);
        control.setOnCheckedChangeListener((button, checked) -> change.accept(checked));
        group.addView(control);
    }

    private void choice(LinearLayout group, String title, String[] options, int initial, IntConsumer change) {
        TextView caption = label(group, title);
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        caption.setLabelFor(spinner.getId());
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        spinner.setSelection(Math.max(0, Math.min(options.length - 1, initial)));
        group.addView(spinner);
        listen(spinner, change);
    }

    private void listen(Spinner spinner, IntConsumer change) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                change.accept(index);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    static void watch(EditText field, Consumer<String> change) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                change.accept(text.toString());
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
    }
}
