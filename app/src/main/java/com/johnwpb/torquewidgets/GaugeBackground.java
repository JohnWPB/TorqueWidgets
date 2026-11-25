package com.johnwpb.torquewidgets;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import java.util.Map;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class GaugeBackground {
    private static AtomicFile file(Context context, int id) {
        return new AtomicFile(new File(context.getFilesDir(), "gauge-background-" + id + ".png"));
    }

    static Bitmap open(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth < 1 || options.outHeight < 1) throw new IOException("Unsupported image");
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = 1;
        int largest = Math.max(options.outWidth, options.outHeight);
        while (largest / decode.inSampleSize > 2400) decode.inSampleSize *= 2;
        try (InputStream input = resolver.openInputStream(uri)) {
            Bitmap image = BitmapFactory.decodeStream(input, null, decode);
            if (image == null) throw new IOException("Invalid image");
            return image;
        }
    }

    static Bitmap read(Context context, int id) {
        AtomicFile background = file(context, id);
        if (!background.getBaseFile().exists()) return null;
        try (InputStream input = background.openRead()) {
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            return null;
        }
    }

    static void save(Context context, int id, Bitmap image) throws IOException {
        AtomicFile background = file(context, id);
        if (image == null) {
            background.delete();
            return;
        }
        FileOutputStream output = background.startWrite();
        try {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Could not encode background image");
            }
            background.finishWrite(output);
        } catch (IOException | RuntimeException e) {
            background.failWrite(output);
            throw new IOException("Could not write background image", e);
        }
    }

    static void remove(Context context, int id) {
        file(context, id).delete();
    }

    static RectF bounds(Bitmap image, Map<String, ?> settings) {
        float side = GaugeFace.SIZE;
        float ratio = image.getWidth() / (float) image.getHeight();
        float fitWidth = Math.min(1, ratio);
        float width = coordinate(settings, "imageWidth", fitWidth) * side;
        float left = coordinate(settings, "imageLeft", (1 - fitWidth) / 2) * side;
        float top = coordinate(settings, "imageTop", (1 - Math.min(1, 1 / ratio)) / 2) * side;
        return new RectF(left, top, left + width, top + width / ratio);
    }

    private static float coordinate(Map<String, ?> settings, String key, float fallback) {
        Object value = settings.get(key);
        return value instanceof Float && Float.isFinite((Float) value) ? (Float) value : fallback;
    }

    static void draw(Canvas canvas, Bitmap image, Map<String, ?> settings) {
        if (image != null) {
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(image, null, bounds(image, settings), paint);
        }
    }
}
