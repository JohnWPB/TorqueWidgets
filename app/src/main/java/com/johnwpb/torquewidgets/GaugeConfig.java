package com.johnwpb.torquewidgets;

import android.content.SharedPreferences;
import android.graphics.Color;
import java.util.HashMap;
import java.util.Map;

final class GaugeConfig {
    static final int BLUE = 0xff84d5ff;
    final Map<String, Object> values;
    private final Map<String, ?> original;

    GaugeConfig(SharedPreferences preferences) {
        original = preferences.getAll();
        values = new HashMap<>(original);
    }

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
    float maximum() { return number("Maximum", 260); }

    String units() {
        switch (integer("temperature", 0)) {
            case 1: return "°C";
            case 2: return "°F";
            default: return string("Units", "°F");
        }
    }

    float convert(float value) {
        switch (integer("temperature", 0)) {
            case 1: return value;
            case 2: return value * 1.8f + 32;
            default: return value * number("Multiplier", 1.8f) + number("Offset", 32);
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
        editor.apply();
    }
}
