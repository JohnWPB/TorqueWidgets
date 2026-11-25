package com.johnwpb.torquewidgets;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import java.util.function.BiConsumer;

final class GpsSample {
    static void request(Context context, Handler handler, boolean needSignal,
                        BiConsumer<Location, Float> callback) {
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.accept(null, Float.NaN);
            return;
        }
        class Request implements LocationListener {
            Location location;
            float strength = Float.NaN;
            boolean done, listening, hasStatus, statusRegistered;
            final Runnable timeout = this::finish;
            final GnssStatus.Callback status = new GnssStatus.Callback() {
                @Override public void onSatelliteStatusChanged(GnssStatus satellites) {
                    float strongest = Float.NaN;
                    for (int i = 0; i < satellites.getSatelliteCount(); i++) {
                        float signal = satellites.getCn0DbHz(i);
                        if (signal > 0 && (!Float.isFinite(strongest) || signal > strongest)) strongest = signal;
                    }
                    strength = strongest;
                    hasStatus = true;
                    if (location != null) finish();
                }
            };

            void start() {
                try {
                    if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        finish();
                        return;
                    }
                    Location cached = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (cached != null && SystemClock.elapsedRealtimeNanos() - cached.getElapsedRealtimeNanos()
                            <= 30_000_000_000L) location = cached;
                    if (needSignal) statusRegistered = manager.registerGnssStatusCallback(status, handler);
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this, handler.getLooper());
                    listening = true;
                    handler.postDelayed(timeout, 5500);
                } catch (SecurityException | IllegalArgumentException e) {
                    finish();
                }
            }

            @Override public void onLocationChanged(Location fix) {
                location = fix;
                if (!statusRegistered || hasStatus) finish();
            }

            @Override public void onProviderDisabled(String provider) { finish(); }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

            void finish() {
                if (done) return;
                done = true;
                handler.removeCallbacks(timeout);
                if (listening) manager.removeUpdates(this);
                if (statusRegistered) manager.unregisterGnssStatusCallback(status);
                callback.accept(location, strength);
            }
        }
        new Request().start();
    }
}
