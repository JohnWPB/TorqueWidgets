package com.johnwpb.torquewidgets;

import android.app.Activity;
import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class GaugeBackup {
    private GaugeBackup() {}

    static void share(Activity activity) {
        Context context = activity.getApplicationContext();
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage("Preparing backup of saved gauges…");
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            try {
                File archive = create(context);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        archive.delete();
                        return;
                    }
                    progress.dismiss();
                    Uri uri = BackupShareProvider.uri(context, archive);
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType(BackupShareProvider.MIME)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri(archive.getName(), uri));
                    try {
                        activity.startActivity(Intent.createChooser(send, null));
                    } catch (ActivityNotFoundException | SecurityException error) {
                        archive.delete();
                        Toast.makeText(activity, "Could not open Android sharing.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException | JSONException | SecurityException error) {
                android.util.Log.w("TorqueWidgets", "Could not create gauge backup", error);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    progress.dismiss();
                    Toast.makeText(activity, "Could not create the gauge backup.", Toast.LENGTH_LONG).show();
                });
            }
        }, "Torque gauge backup").start();
    }

    private static File create(Context context) throws IOException, JSONException {
        File directory = BackupShareProvider.directory(context);
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create the backup cache");
        }
        File[] expired = directory.listFiles(file -> file.getName().startsWith("Torque-Widgets-Backup-")
                && file.lastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
        if (expired != null) {
            for (File file : expired) file.delete();
        }
        File archive = File.createTempFile("Torque-Widgets-Backup-" + System.currentTimeMillis() + "-",
                ".zip", directory);
        boolean complete = false;
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive)))) {
                write(context, zip);
            }
            complete = true;
            return archive;
        } finally {
            if (!complete) archive.delete();
        }
    }

    private static void write(Context context, ZipOutputStream zip) throws IOException, JSONException {
        AppWidgetManager widgets = AppWidgetManager.getInstance(context);
        JSONArray standalone = new JSONArray();
        for (int id : widgets.getAppWidgetIds(new ComponentName(context, CoolantWidget.class))) {
            standalone.put(writeGauge(context, zip, id));
        }

        JSONArray dashboards = new JSONArray();
        for (int id : widgets.getAppWidgetIds(new ComponentName(context, DashboardWidget.class))) {
            JSONArray gauges = new JSONArray();
            for (DashboardLayout.Gauge gauge : DashboardLayout.read(context, id)) {
                gauges.put(writeGauge(context, zip, gauge.id)
                        .put("x", gauge.x).put("y", gauge.y).put("size", gauge.size));
            }
            dashboards.put(new JSONObject().put("id", id).put("gauges", gauges));
        }

        JSONObject manifest = new JSONObject().put("version", 1)
                .put("standaloneGauges", standalone).put("dashboards", dashboards);
        zip.putNextEntry(new ZipEntry("settings.json"));
        zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static JSONObject writeGauge(Context context, ZipOutputStream zip, int id)
            throws IOException, JSONException {
        JSONObject gauge = new JSONObject().put("id", id)
                .put("settings", new JSONObject(CoolantWidget.prefs(context, id).getAll()));
        try (InputStream background = GaugeBackground.openSaved(context, id)) {
            if (background != null) {
                String path = "backgrounds/gauge-" + id + ".png";
                zip.putNextEntry(new ZipEntry(path));
                byte[] buffer = new byte[8192];
                int count;
                while ((count = background.read(buffer)) != -1) {
                    zip.write(buffer, 0, count);
                }
                zip.closeEntry();
                gauge.put("background", path);
            }
        }
        return gauge;
    }
}
