package com.johnwpb.torquewidgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class SettingsBackup implements AutoCloseable {
    private static final int MAX_IMAGE = 24 * 1024 * 1024;
    private static final Pattern LABEL = Pattern.compile("label\\.(title|reading|units|noData|scale[0-8])\\.(visible|size|x|y)");
    private static final Pattern IMAGE = Pattern.compile("image-([0-9]+)\\.png");
    final boolean dashboard;
    final List<Profile> profiles;
    private File directory;

    static final class Profile {
        final Map<String, Object> settings;
        final float x, y, size;
        Bitmap image;
        File imageFile;

        Profile(Map<String, ?> settings, float x, float y, float size) {
            this.settings = new HashMap<>(settings);
            this.x = x;
            this.y = y;
            this.size = size;
        }

        Bitmap loadImage() throws IOException {
            if (imageFile == null) return image;
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getPath());
            if (bitmap == null) throw new IOException("Invalid background in backup");
            return bitmap;
        }

        void apply(Context context, int gaugeId) throws IOException {
            SharedPreferences.Editor editor = CoolantWidget.prefs(context, gaugeId).edit().clear();
            put(editor, settings);
            if (!editor.commit()) throw new IOException("Could not restore gauge settings");
            if (imageFile != null) {
                Bitmap background = loadImage();
                try {
                    GaugeBackground.save(context, gaugeId, background);
                } finally {
                    background.recycle();
                }
            }
        }
    }

    SettingsBackup(boolean dashboard, List<Profile> profiles) {
        this.dashboard = dashboard;
        this.profiles = profiles;
    }

    static void put(SharedPreferences.Editor editor, Map<String, ?> settings) {
        for (Map.Entry<String, ?> entry : settings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
        }
    }

    void write(OutputStream output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            JSONObject manifest = new JSONObject();
            manifest.put("version", 1).put("kind", dashboard ? "dashboard" : "gauge");
            JSONArray entries = new JSONArray();
            manifest.put("gauges", entries);
            for (Profile profile : profiles) {
                JSONObject entry = new JSONObject();
                Map<String, Object> normalized = new HashMap<>(profile.settings);
                normalized.replaceAll((key, value) -> value instanceof String ? ((String) value).trim() : value);
                entry.put("settings", new JSONObject(readSettings(new JSONObject(normalized))));
                if (dashboard) entry.put("x", profile.x).put("y", profile.y).put("size", profile.size);
                entries.put(entry);
            }
            zip.putNextEntry(new ZipEntry("settings.json"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (int i = 0; i < profiles.size(); i++) {
                Profile profile = profiles.get(i);
                if (profile.image == null && profile.imageFile == null) continue;
                zip.putNextEntry(new ZipEntry("image-" + i + ".png"));
                if (profile.imageFile != null) {
                    try (InputStream input = new FileInputStream(profile.imageFile)) {
                        copy(input, zip, MAX_IMAGE);
                    }
                } else if (!profile.image.compress(Bitmap.CompressFormat.PNG, 100, zip)) {
                    throw new IOException("Could not encode background");
                }
                zip.closeEntry();
            }
        } catch (JSONException e) {
            throw new IOException("Could not encode settings", e);
        }
    }

    static SettingsBackup read(Context context, InputStream input, boolean dashboard) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry first = zip.getNextEntry();
            if (first == null || !"settings.json".equals(first.getName())) throw new IOException("Invalid backup");
            ByteArrayOutputStream json = new ByteArrayOutputStream();
            copy(zip, json, 1024 * 1024);
            JSONObject manifest = new JSONObject(json.toString("UTF-8"));
            if (manifest.getInt("version") != 1
                    || !manifest.getString("kind").equals(dashboard ? "dashboard" : "gauge")) {
                throw new IOException(dashboard ? "Choose a dashboard backup." : "Choose a gauge backup.");
            }
            JSONArray entries = manifest.getJSONArray("gauges");
            int count = entries.length();
            if (dashboard ? count > DashboardLayout.MAX_GAUGES : count != 1) {
                throw new IOException("Invalid gauge count");
            }
            List<Profile> profiles = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                JSONObject entry = entries.getJSONObject(i);
                profiles.add(new Profile(readSettings(entry.getJSONObject("settings")),
                        dashboard ? position(entry, "x", 0, 1) : 0,
                        dashboard ? position(entry, "y", 0, 1) : 0,
                        dashboard ? position(entry, "size", .01f, 1) : 1));
            }
            SettingsBackup backup = new SettingsBackup(dashboard, profiles);
            try {
                for (ZipEntry image; (image = zip.getNextEntry()) != null; ) {
                    java.util.regex.Matcher match = IMAGE.matcher(image.getName());
                    if (!match.matches()) throw new IOException("Unexpected backup entry");
                    int index = Integer.parseInt(match.group(1));
                    if (index >= count || profiles.get(index).imageFile != null) {
                        throw new IOException("Invalid image entry");
                    }
                    if (backup.directory == null) {
                        backup.directory = new File(context.getCacheDir(), "restore-" + UUID.randomUUID());
                        if (!backup.directory.mkdir()) throw new IOException("Could not open restore cache");
                    }
                    File imageFile = new File(backup.directory, "image-" + index + ".png");
                    try (OutputStream output = new FileOutputStream(imageFile)) {
                        copy(zip, output, MAX_IMAGE);
                    }
                    BitmapFactory.Options limits = new BitmapFactory.Options();
                    limits.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(imageFile.getPath(), limits);
                    if (limits.outWidth < 1 || limits.outHeight < 1
                            || limits.outWidth > 2400 || limits.outHeight > 2400) {
                        throw new IOException("Invalid background in backup");
                    }
                    profiles.get(index).imageFile = imageFile;
                }
                return backup;
            } catch (IOException | RuntimeException e) {
                backup.close();
                throw e;
            }
        } catch (JSONException | NumberFormatException e) {
            throw new IOException("Invalid settings backup", e);
        }
    }

    private static float position(JSONObject entry, String key, float lower, float upper)
            throws JSONException, IOException {
        float value = (float) entry.getDouble(key);
        if (!Float.isFinite(value) || value < lower || value > upper) throw new IOException("Invalid layout");
        return value;
    }

    private static Map<String, Object> readSettings(JSONObject values) throws JSONException, IOException {
        Map<String, Object> settings = new HashMap<>();
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = values.get(key);
            boolean label = LABEL.matcher(key).matches();
            switch (key) {
                case "circle": case "info": case "gpsProvider": case "showTicks": case "showScale":
                    if (!(value instanceof Boolean)) throw new IOException("Invalid switch setting");
                    settings.put(key, value);
                    break;
                case "style":
                    settings.put(key, integer(value, 0, 3));
                    break;
                case "imageLeft": case "imageTop": case "imageWidth":
                    settings.put(key, decimal(value, key.equals("imageWidth") ? .00001f : -8, 8));
                    break;
                case "Name": case "PID": case "Units": case "gpsMetric":
                case "Minimum": case "Maximum": case "Multiplier": case "Offset":
                case "Font color": case "Tick color": case "Indicator color": case "Needle color":
                    if (!(value instanceof String) || ((String) value).length() > 4096) {
                        throw new IOException("Invalid gauge text setting");
                    }
                    if (key.endsWith("color")) {
                        try { Color.parseColor((String) value); }
                        catch (IllegalArgumentException e) { throw new IOException("Invalid color", e); }
                    }
                    settings.put(key, value);
                    break;
                default:
                    if (!label) break;
                    if (key.endsWith(".visible")) {
                        if (!(value instanceof Boolean)) throw new IOException("Invalid label visibility");
                        settings.put(key, value);
                    } else if (key.endsWith(".size")) settings.put(key, integer(value, 0, 200));
                    else settings.put(key, decimal(value, 0, 1));
            }
        }
        float minimum = GaugeFace.number(settings, "Minimum", settings.containsKey("Minimum") ? Float.NaN : 0);
        float maximum = GaugeFace.number(settings, "Maximum", settings.containsKey("Maximum") ? Float.NaN : 260);
        if (!Boolean.TRUE.equals(settings.get("gpsProvider"))
                && (!Float.isFinite(maximum - minimum) || maximum <= minimum
                || !Float.isFinite(GaugeFace.number(settings, "Multiplier", settings.containsKey("Multiplier") ? Float.NaN : 1))
                || !Float.isFinite(GaugeFace.number(settings, "Offset", settings.containsKey("Offset") ? Float.NaN : 0))
                || GaugeFace.text(settings, "PID", "05").trim().isEmpty())) {
            throw new IOException("Invalid gauge range or data settings");
        }
        return settings;
    }

    private static float decimal(Object value, float lower, float upper) throws IOException {
        if (!(value instanceof Number)) throw new IOException("Invalid number");
        float number = ((Number) value).floatValue();
        if (!Float.isFinite(number) || number < lower || number > upper) throw new IOException("Number out of range");
        return number;
    }

    private static int integer(Object value, int lower, int upper) throws IOException {
        float number = decimal(value, lower, upper);
        if (number != (int) number) throw new IOException("Invalid integer");
        return (int) number;
    }

    private static void copy(InputStream input, OutputStream output, int limit) throws IOException {
        byte[] bytes = new byte[8192];
        int total = 0;
        for (int size; (size = input.read(bytes)) != -1; ) {
            total += size;
            if (total > limit) throw new IOException("Backup entry is too large");
            output.write(bytes, 0, size);
        }
    }

    @Override public void close() {
        if (directory == null) return;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        directory.delete();
    }
}
