package com.johnwpb.torquewidgets;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
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
        if (options.outWidth != GaugeFace.SIZE || options.outHeight != GaugeFace.SIZE) {
            throw new IOException("Image must be 800 × 800 pixels");
        }
        try (InputStream input = resolver.openInputStream(uri)) {
            Bitmap image = BitmapFactory.decodeStream(input);
            if (image == null || image.getWidth() != GaugeFace.SIZE || image.getHeight() != GaugeFace.SIZE) {
                throw new IOException("Invalid background image");
            }
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

    static void draw(Canvas canvas, Bitmap image) {
        if (image != null) {
            canvas.drawBitmap(image, null, new Rect(0, 0, GaugeFace.SIZE, GaugeFace.SIZE), null);
        }
    }
}
