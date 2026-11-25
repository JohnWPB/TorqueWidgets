package com.johnwpb.torquewidgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class GaugeColorPicker extends LinearLayout {
    private final float[] hsv = new float[3];
    private final Consumer<String> change;
    private final SeekBar[] bars = new SeekBar[3];
    private final EditText hex;
    private final TextView selected;
    private final View spectrum;
    private boolean syncing;

    GaugeColorPicker(GaugeSettings context, int original, int current, Consumer<String> change) {
        super(context);
        this.change = change;
        setOrientation(VERTICAL);
        Color.colorToHSV(current, hsv);
        spectrum = new Spectrum(context);
        spectrum.setContentDescription("Color spectrum. Move horizontally for hue and vertically for saturation.");
        addView(spectrum, new LayoutParams(-1, context.dp(125)));
        String[] names = {"Hue", "Saturation", "Brightness"};
        for (int index = 0; index < names.length; index++) {
            TextView title = new TextView(context);
            title.setText(names[index]);
            addView(title);
            int axis = index;
            SeekBar bar = new SeekBar(context);
            bar.setContentDescription(names[index]);
            bar.setMax(axis == 0 ? 359 : 100);
            bars[axis] = bar;
            track(bar, progress -> {
                if (syncing) return;
                hsv[axis] = axis == 0 ? progress : progress / 100f;
                applyHsv();
            });
            addView(bar);
        }
        LinearLayout samples = new LinearLayout(context);
        TextView before = swatch(context, "Original");
        selected = swatch(context, "Selected");
        showSwatch(before, original);
        samples.addView(before, new LayoutParams(0, -2, 1));
        samples.addView(selected, new LayoutParams(0, -2, 1));
        addView(samples);
        hex = new EditText(context);
        hex.setSingleLine(true);
        hex.setHint("#RRGGBB");
        hex.setContentDescription("Hexadecimal color");
        hex.setText(format(current));
        addView(hex);
        syncBars();
        showSwatch(selected, current);
        GaugeSettings.watch(hex, text -> {
            if (syncing || !isHex(text)) return;
            int color = Color.parseColor(text);
            Color.colorToHSV(color, hsv);
            syncBars();
            showSwatch(selected, color);
            spectrum.invalidate();
            change.accept(format(color));
        });
    }

    String validColor() {
        String value = hex.getText().toString();
        if (isHex(value)) return format(Color.parseColor(value));
        hex.setError("Enter a color in #RRGGBB format");
        hex.requestFocus();
        return null;
    }

    private void applyHsv() {
        int color = Color.HSVToColor(hsv);
        String value = format(color);
        syncing = true;
        hex.setText(value);
        syncing = false;
        showSwatch(selected, color);
        spectrum.invalidate();
        change.accept(value);
    }

    private void syncBars() {
        syncing = true;
        bars[0].setProgress(Math.round(hsv[0]));
        bars[1].setProgress(Math.round(hsv[1] * 100));
        bars[2].setProgress(Math.round(hsv[2] * 100));
        syncing = false;
    }

    private static boolean isHex(String value) {
        return value.matches("#[0-9a-fA-F]{6}");
    }

    private static String format(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xffffff);
    }

    private static TextView swatch(GaugeSettings context, String text) {
        TextView sample = new TextView(context);
        sample.setText(text);
        sample.setGravity(android.view.Gravity.CENTER);
        sample.setPadding(0, context.dp(8), 0, context.dp(8));
        return sample;
    }

    private static void showSwatch(TextView swatch, int color) {
        swatch.setBackgroundColor(color);
        float brightness = .299f * Color.red(color) + .587f * Color.green(color) + .114f * Color.blue(color);
        swatch.setTextColor(brightness >= 145 ? Color.BLACK : Color.WHITE);
    }

    static void track(SeekBar bar, IntConsumer change) {
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar view, int progress, boolean user) {
                change.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar view) { }
            @Override public void onStopTrackingTouch(SeekBar view) { }
        });
    }

    private final class Spectrum extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader hues, saturation;

        Spectrum(Context context) { super(context); }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            if (width < 1 || height < 1) return;
            hues = new LinearGradient(0, 0, width, 0, new int[]{Color.RED, Color.YELLOW,
                    Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED}, null, Shader.TileMode.CLAMP);
            saturation = new LinearGradient(0, 0, 0, height, 0x00ffffff, 0xffffffff, Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setShader(hues);
            canvas.drawPaint(paint);
            paint.setShader(saturation);
            canvas.drawPaint(paint);
            paint.setShader(null);
            paint.setColor(Color.argb(Math.round((1 - hsv[2]) * 255), 0, 0, 0));
            canvas.drawPaint(paint);
            paint.setStyle(Paint.Style.STROKE);
            float x = hsv[0] / 360 * getWidth(), y = (1 - hsv[1]) * getHeight();
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(5);
            canvas.drawCircle(x, y, 11, paint);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(2);
            canvas.drawCircle(x, y, 11, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                hsv[0] = Math.max(0, Math.min(359, event.getX() * 360 / getWidth()));
                hsv[1] = Math.max(0, Math.min(1, 1 - event.getY() / getHeight()));
                syncBars();
                applyHsv();
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                if (action == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
