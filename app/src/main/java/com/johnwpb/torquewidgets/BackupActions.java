package com.johnwpb.torquewidgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class BackupActions<T> {
    private static final int SAVE = 1701, LOAD = 1702;
    private final Activity activity;
    private final boolean dashboard;
    private final Supplier<SettingsBackup> snapshot;
    private final Restore<T> restore;
    private final Consumer<T> apply, discard;
    private File pendingExport;

    interface Restore<T> {
        T prepare(SettingsBackup backup) throws IOException;
    }

    BackupActions(Activity activity, boolean dashboard, Supplier<SettingsBackup> snapshot,
                  Restore<T> restore, Consumer<T> apply, Consumer<T> discard, Bundle state) {
        this.activity = activity;
        this.dashboard = dashboard;
        this.snapshot = snapshot;
        this.restore = restore;
        this.apply = apply;
        this.discard = discard;
        String name = state == null ? null : state.getString("pendingBackup");
        if (name != null) pendingExport = new File(BackupShareProvider.directory(activity), name);
    }

    void saveState(Bundle state) {
        if (pendingExport != null) state.putString("pendingBackup", pendingExport.getName());
    }

    void show() {
        new AlertDialog.Builder(activity).setTitle("Settings backup")
                .setItems(new String[]{"Save backup to file", "Share backup", "Load backup"},
                        (dialog, index) -> {
                            if (index == 2) {
                                activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), LOAD);
                            } else export(index == 1);
                        }).show();
    }

    private void export(boolean share) {
        SettingsBackup backup = snapshot.get();
        work("Preparing backup…", () -> {
            File directory = BackupShareProvider.directory(activity);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create backup");
            File[] stale = directory.listFiles();
            if (stale != null) for (File file : stale) {
                if (System.currentTimeMillis() - file.lastModified() > 7L * 86400000) file.delete();
            }
            String name = "Torque-" + (dashboard ? "Dashboard" : "Gauge") + "-"
                    + System.currentTimeMillis() + "-" + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100000) + ".zip";
            File file = new File(directory, name);
            try (OutputStream output = new FileOutputStream(file)) {
                backup.write(output);
                return file;
            } catch (IOException e) {
                file.delete();
                throw e;
            }
        }, file -> {
            if (share) {
                Uri uri = BackupShareProvider.uri(activity, file);
                Intent intent = new Intent(Intent.ACTION_SEND).setType(BackupShareProvider.MIME)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .setClipData(ClipData.newUri(activity.getContentResolver(), "Torque settings", uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(intent, "Share Torque settings"));
            } else {
                pendingExport = file;
                activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType(BackupShareProvider.MIME).addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, file.getName()), SAVE);
            }
        }, File::delete);
    }

    boolean handle(int request, int result, Intent data) {
        if (request != SAVE && request != LOAD) return false;
        File export = pendingExport;
        if (request == SAVE) pendingExport = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) {
            if (request == SAVE && export != null) export.delete();
            return true;
        }
        Uri uri = data.getData();
        if (request == SAVE) {
            work("Saving backup…", () -> {
                if (export == null) throw new IOException("Backup expired. Try saving again.");
                try (InputStream input = new FileInputStream(export);
                     OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("Could not open the selected destination");
                    byte[] bytes = new byte[8192];
                    for (int count; (count = input.read(bytes)) != -1; ) output.write(bytes, 0, count);
                } finally {
                    export.delete();
                }
                return true;
            }, done -> toast("Backup saved."), done -> {});
        } else {
            new AlertDialog.Builder(activity).setTitle("Load settings backup?")
                    .setMessage(dashboard ? "This replaces the dashboard draft. Tap Save dashboard to apply it."
                            : "This replaces the gauge draft. Tap Save to apply it.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Load", (dialog, button) -> work("Loading backup…", () -> {
                        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                            if (input == null) throw new IOException("Could not open the selected backup");
                            try (SettingsBackup backup = SettingsBackup.read(activity, input, dashboard)) {
                                return restore.prepare(backup);
                            }
                        }
                    }, apply, discard)).show();
        }
        return true;
    }

    private interface Job<R> { R run() throws IOException; }

    private <R> void work(String message, Job<R> job, Consumer<R> success, Consumer<R> abandoned) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(message);
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            try {
                R value = job.run();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        abandoned.accept(value);
                        return;
                    }
                    progress.dismiss();
                    try {
                        success.accept(value);
                    } catch (android.content.ActivityNotFoundException e) {
                        toast("Android could not open a file or sharing app.");
                    }
                });
            } catch (IOException | SecurityException e) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    progress.dismiss();
                    toast(e.getMessage() == null ? "Backup operation failed." : e.getMessage());
                });
            }
        }, "Torque settings backup").start();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
