package com.johnwpb.torquewidgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DashboardLayout {
    static final int MAX_GAUGES = 16;

    static final class Gauge {
        int id;
        float x, y, size;

        Gauge(int id, float x, float y, float size) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        RectF bounds(float width, float height) {
            float half = size * Math.min(width, height) / 2;
            float cx = Math.max(half, Math.min(width - half, x * width));
            float cy = Math.max(half, Math.min(height - half, y * height));
            return new RectF(cx - half, cy - half, cx + half, cy + half);
        }
    }

    private static SharedPreferences preferences(Context context, int id) {
        return context.getSharedPreferences("dashboard" + id, Context.MODE_PRIVATE);
    }

    static List<Gauge> read(Context context, int id) {
        return decode(preferences(context, id).getString("gauges", "[]"));
    }

    static List<Gauge> decode(String encoded) {
        List<Gauge> gauges = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length() && i < MAX_GAUGES; i++) {
                JSONObject entry = array.getJSONObject(i);
                int id = entry.getInt("id");
                if (id < -1) gauges.add(new Gauge(id, (float) entry.getDouble("x"),
                        (float) entry.getDouble("y"), (float) entry.getDouble("size")));
            }
        } catch (JSONException ignored) {
            // Recover any complete gauges already decoded.
        }
        return gauges;
    }

    static String encode(List<Gauge> gauges) {
        JSONArray array = new JSONArray();
        try {
            for (Gauge gauge : gauges) {
                JSONObject entry = new JSONObject();
                entry.put("id", gauge.id).put("x", gauge.x).put("y", gauge.y).put("size", gauge.size);
                array.put(entry);
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Invalid dashboard layout", e);
        }
        return array.toString();
    }

    static boolean save(Context context, int id, List<Gauge> gauges) {
        return preferences(context, id).edit().putString("gauges", encode(gauges)).commit();
    }

    static int newGauge(Context context, Integer source) throws IOException {
        SharedPreferences sequence = context.getSharedPreferences("dashboardGaugeIds", Context.MODE_PRIVATE);
        int id = sequence.getInt("next", -2);
        if (!sequence.edit().putInt("next", id - 1).commit()) throw new IOException("Could not allocate gauge");
        if (source == null) return id;
        SharedPreferences.Editor editor = CoolantWidget.prefs(context, id).edit();
        for (Map.Entry<String, ?> entry : CoolantWidget.prefs(context, source).getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        }
        try {
            if (!editor.commit()) throw new IOException("Could not copy gauge settings");
            Bitmap image = GaugeBackground.read(context, source);
            if (image != null) GaugeBackground.save(context, id, image);
            return id;
        } catch (IOException e) {
            deleteGauge(context, id);
            throw e;
        }
    }

    static void deleteGauge(Context context, int id) {
        if (id >= -1) return;
        GaugeBackground.remove(context, id);
        context.deleteSharedPreferences("gauge" + id);
    }

    static void delete(Context context, int id) {
        for (Gauge gauge : read(context, id)) deleteGauge(context, gauge.id);
        context.deleteSharedPreferences("dashboard" + id);
    }

    static void draw(Canvas canvas, RectF bounds, Map<String, ?> settings, Bitmap image, float value) {
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / GaugeFace.SIZE, bounds.height() / GaugeFace.SIZE);
        canvas.clipRect(0, 0, GaugeFace.SIZE, GaugeFace.SIZE);
        GaugeBackground.draw(canvas, image, settings);
        GaugeFace.draw(canvas, settings, value, null);
        canvas.restore();
    }
}
