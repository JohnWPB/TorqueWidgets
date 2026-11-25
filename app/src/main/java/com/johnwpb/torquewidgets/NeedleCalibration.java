package com.johnwpb.torquewidgets;

import java.util.Locale;
import java.util.Map;

final class NeedleCalibration {
    static final String KEY = "needleCalibration";
    private static final double MAX_SWEEP = 359;

    final float lowValue, highValue, lowAngle, highAngle;

    NeedleCalibration(float lowValue, float lowAngle, float highValue, float highAngle) {
        this.lowValue = lowValue;
        this.lowAngle = lowAngle;
        this.highValue = highValue;
        this.highAngle = highAngle;
    }

    static float pivotY(boolean circle) {
        return circle ? 400 : 425;
    }

    static float defaultAngle(boolean circle, float fraction) {
        return circle ? 135 + 270 * fraction : 180 + 180 * fraction;
    }

    static float touchAngle(float dx, float dy) {
        float angle = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
        return angle == 360 ? 0 : angle;
    }

    static boolean differentAngles(float first, float second) {
        double sweep = clockwiseSweep(first, second);
        return sweep >= 1 && sweep <= MAX_SWEEP;
    }

    private static double clockwiseSweep(float first, float second) {
        return ((double) second - first + 360) % 360;
    }

    static float angleFor(Map<String, ?> settings, float value, float min, float max,
                          boolean circle, float fraction) {
        NeedleCalibration calibration = read(settings);
        if (calibration == null || !(max > min)) return defaultAngle(circle, fraction);
        return calibration.angle(Float.isFinite(value) ? value : min, min, max);
    }

    private float angle(float value, float min, float max) {
        double sweep = clockwiseSweep(lowAngle, highAngle);
        double degreesPerUnit = sweep / ((double) highValue - lowValue);
        double before = Math.max(0, ((double) lowValue - min) * degreesPerUnit);
        double after = Math.max(0, ((double) max - highValue) * degreesPerUnit);
        // Keep the calibrated marks exact and limit extrapolation to a single revolution.
        double extension = before + after;
        double limit = extension == 0 ? 1 : Math.min(1, (MAX_SWEEP - sweep) / extension);
        double reading = Math.max(min, Math.min(max, value));
        double offset = (reading - (double) lowValue) * degreesPerUnit;
        return (float) (lowAngle + Math.max(-before * limit, Math.min(sweep + after * limit, offset)));
    }

    String encode(Map<String, ?> settings) {
        TemperatureUnit unit = temperatureUnit(settings);
        return lowValue + "|" + lowAngle + "|" + highValue + "|" + highAngle + "|"
                + (unit == null ? "" : unit.name()) + "|" + source(settings);
    }

    static NeedleCalibration read(Map<String, ?> settings) {
        Object stored = settings.get(KEY);
        if (!(stored instanceof String)) return null;
        String[] fields = ((String) stored).split("\\|", 6);
        if (fields.length != 6 || !fields[5].equals(source(settings))) return null;
        try {
            float low = Float.parseFloat(fields[0]), first = Float.parseFloat(fields[1]);
            float high = Float.parseFloat(fields[2]), second = Float.parseFloat(fields[3]);
            TemperatureUnit unit = temperatureUnit(settings);
            if (unit != null) {
                TemperatureUnit saved = TemperatureUnit.valueOf(fields[4]);
                low = saved.convertTo(low, unit);
                high = saved.convertTo(high, unit);
            }
            if (!Float.isFinite(low) || !Float.isFinite(high) || high <= low
                    || !(first >= 0 && first < 360 && second >= 0 && second < 360)
                    || !differentAngles(first, second)) return null;
            return new NeedleCalibration(low, first, high, second);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static TemperatureUnit temperatureUnit(Map<String, ?> settings) {
        return GpsMetric.selected(settings) == null && TemperatureUnit.isTemperaturePid(pid(settings))
                ? TemperatureUnit.configured(settings) : null;
    }

    private static String source(Map<String, ?> settings) {
        GpsMetric gps = GpsMetric.selected(settings);
        return gps == null ? "PID:" + pid(settings) : "GPS:" + gps.name();
    }

    private static String pid(Map<String, ?> settings) {
        Object pid = settings.get("PID");
        return pid instanceof String ? ((String) pid).trim().toUpperCase(Locale.ROOT) : "05";
    }
}
