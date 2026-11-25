package com.johnwpb.torquewidgets;

import android.content.SharedPreferences;
import android.graphics.Color;
import java.util.HashMap;
import java.util.Map;

final class GaugeConfig {
    static final int BLUE = 0xff84d5ff;
    final Map<String, Object> values;
    private static final Map<String, String> SPEED_DEFAULTS = defaults("Speedometer", "0d", "160",
            "mph", "0.621371", "0");
    private static final Map<String, String> COOLANT_DEFAULTS = defaults("Coolant", "05", "260",
            "°F", "1.8", "32");
    private final Map<String, ?> original;
    private final boolean speedDefaults;

    GaugeConfig(SharedPreferences preferences) {
        original = preferences.getAll();
        values = new HashMap<>(original);
        speedDefaults = original.isEmpty() || flag("speedDefaults", false);
    }

    private static Map<String, String> defaults(String name, String pid, String maximum,
                                                 String units, String multiplier, String offset) {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("Name", name);
        defaults.put("PID", pid);
        defaults.put("Minimum", "0");
        defaults.put("Maximum", maximum);
        defaults.put("Units", units);
        defaults.put("Multiplier", multiplier);
        defaults.put("Offset", offset);
        return defaults;
    }

    String gaugeDefault(String key) {
        return (speedDefaults ? SPEED_DEFAULTS : COOLANT_DEFAULTS).get(key);
    }

    String name() { return string("Name", gaugeDefault("Name")); }
    String pid() { return string("PID", gaugeDefault("PID")).trim(); }

    String string(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    boolean flag(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    int integer(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    float number(String key, float fallback) {
        try {
            float number = Float.parseFloat(string(key, ""));
            return Float.isFinite(number) ? number : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    int color(String key, int fallback) {
        try {
            return Color.parseColor(string(key, ""));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    int layout() {
        return integer("layout", flag("circle", false) ? 0 : values.containsKey("PID") ? 2 : 1);
    }

    float minimum() { return number("Minimum", 0); }
    float maximum() { return number("Maximum", Float.parseFloat(gaugeDefault("Maximum"))); }

    String units() {
        switch (integer("temperature", 0)) {
            case 1: return "°C";
            case 2: return "°F";
            default: return string("Units", gaugeDefault("Units"));
        }
    }

    float convert(float value) {
        switch (integer("temperature", 0)) {
            case 1: return value;
            case 2: return value * 1.8f + 32;
            default: return value * number("Multiplier", Float.parseFloat(gaugeDefault("Multiplier")))
                    + number("Offset", Float.parseFloat(gaugeDefault("Offset")));
        }
    }

    String textKey(String id, String property) {
        return "text." + id + "." + property;
    }

    void save(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : original.keySet()) {
            if (!values.containsKey(key)) editor.remove(key);
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (java.util.Objects.equals(value, original.get(entry.getKey()))) continue;
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
        }
        editor.putBoolean("speedDefaults", speedDefaults).apply();
    }
}
