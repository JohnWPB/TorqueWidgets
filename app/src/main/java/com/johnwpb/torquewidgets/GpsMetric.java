package com.johnwpb.torquewidgets;

import android.location.Location;
import java.util.Locale;
import java.util.Map;

enum GpsMetric {
    SPEED("Speed", "mph", 0, 160),
    SIGNAL("Signal strength", "dB-Hz", 0, 60),
    LATITUDE("Latitude", "°", -90, 90),
    LONGITUDE("Longitude", "°", -180, 180),
    DIRECTION("Direction of travel", "°", 0, 360);

    final String label, units;
    final float min, max;

    GpsMetric(String label, String units, float min, float max) {
        this.label = label;
        this.units = units;
        this.min = min;
        this.max = max;
    }

    static GpsMetric selected(Map<String, ?> settings) {
        if (!Boolean.TRUE.equals(settings.get("gpsProvider"))) return null;
        return configured(settings);
    }

    static GpsMetric configured(Map<String, ?> settings) {
        Object key = settings.get("gpsMetric");
        for (GpsMetric metric : values()) {
            if (metric.name().equals(key)) return metric;
        }
        return SPEED;
    }

    static String title(GpsMetric metric, String title) {
        return metric != null && "Coolant".equals(title) ? metric.label : title;
    }

    static String reading(GpsMetric metric, float value) {
        if (!Float.isFinite(value)) return "--";
        if (metric == LATITUDE || metric == LONGITUDE) return String.format(Locale.getDefault(), "%.5f", value);
        return Integer.toString(Math.round(value));
    }

    float read(Location location, float signal) {
        if (this == SIGNAL) return signal;
        if (location == null) return Float.NaN;
        switch (this) {
            case SPEED: return location.hasSpeed() ? location.getSpeed() * 2.2369363f : Float.NaN;
            case LATITUDE: return (float) location.getLatitude();
            case LONGITUDE: return (float) location.getLongitude();
            case DIRECTION: return location.hasBearing() ? location.getBearing() : Float.NaN;
            default: return Float.NaN;
        }
    }
}
