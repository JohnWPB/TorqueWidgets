package com.johnwpb.torquewidgets;

import java.util.Locale;
import java.util.Map;

enum TemperatureUnit {
    CELSIUS("Celsius", "°C"),
    FAHRENHEIT("Fahrenheit", "°F");

    static final String KEY = "temperatureUnit";
    final String label;
    final String symbol;

    TemperatureUnit(String label, String symbol) {
        this.label = label;
        this.symbol = symbol;
    }

    static boolean isTemperaturePid(String pid) {
        switch (pid.trim().toUpperCase(Locale.ROOT)) {
            case "05":
            case "0F":
            case "3C":
            case "3D":
            case "3E":
            case "3F":
            case "46":
            case "5C":
                return true;
            default:
                return false;
        }
    }

    static TemperatureUnit configured(Map<String, ?> settings) {
        Object stored = settings.get(KEY);
        if (CELSIUS.name().equals(stored)) return CELSIUS;
        if (FAHRENHEIT.name().equals(stored)) return FAHRENHEIT;
        return legacyNumber(settings, "Multiplier", 1.8f) == 1f
                && legacyNumber(settings, "Offset", 32f) == 0f ? CELSIUS : FAHRENHEIT;
    }

    static float displayReading(Map<String, ?> settings, float value) {
        if (isTemperaturePid(pid(settings))) return configured(settings).fromCelsius(value);
        return value * legacyNumber(settings, "Multiplier", 1f)
                + legacyNumber(settings, "Offset", 0f);
    }

    static String displayUnits(Map<String, ?> settings) {
        Object units = settings.get("Units");
        if (isTemperaturePid(pid(settings))) return configured(settings).symbol;
        return units instanceof String ? (String) units : FAHRENHEIT.symbol;
    }

    private static String pid(Map<String, ?> settings) {
        Object pid = settings.get("PID");
        return pid instanceof String ? (String) pid : "05";
    }

    private static float legacyNumber(Map<String, ?> settings, String key, float fallback) {
        Object value = settings.get(key);
        if (!(value instanceof String)) return fallback;
        try {
            return Float.parseFloat((String) value);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private float fromCelsius(float value) {
        return this == FAHRENHEIT ? (float) (value * 1.8 + 32) : value;
    }

    float convertTo(float value, TemperatureUnit target) {
        if (this == target) return value;
        return target == FAHRENHEIT ? target.fromCelsius(value) : (float) ((value - 32.0) / 1.8);
    }
}
