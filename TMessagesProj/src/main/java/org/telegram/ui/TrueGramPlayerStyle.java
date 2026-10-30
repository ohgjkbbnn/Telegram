package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * TrueGram: player appearance. Slider style (line, thick, wave, dots) and cover shape.
 * Self-contained: settings, pickers and the slider view live here.
 */
public class TrueGramPlayerStyle {

    private static final String PREFS = "truegram_player_style";
    private static final String KEY_SLIDER = "slider";
    private static final String KEY_COVER = "cover";

    public static final String[] SLIDER_NAMES = {"Тонкий", "Толстый", "Волна", "Точки"};
    private static final int[] SLIDER_HEIGHT_DP = {2, 8, 14, 6};

    public static final String[] COVER_NAMES = {"Стандарт", "Квадрат", "Скругление", "Круг", "Плавный"};
    private static final int[] COVER_RADIUS_DP = {4, 0, 16, 999, 28};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getSlider() {
        final int v = prefs().getInt(KEY_SLIDER, 0);
        return v >= 0 && v < SLIDER_NAMES.length ? v : 0;
    }

    public static void setSlider(int v) {
        prefs().edit().putInt(KEY_SLIDER, v).apply();
        notifyListeners();
    }

    public static int getCover() {
        final int v = prefs().getInt(KEY_COVER, 0);
        return v >= 0 && v < COVER_NAMES.length ? v : 0;
    }

    public static void setCover(int v) {
        prefs().edit().putInt(KEY_COVER, v).apply();
        notifyListeners();
    }

    // open players subscribe here and update instantly
    private static final java.util.ArrayList<Runnable> listeners = new java.util.ArrayList<>();

    public static void addListener(Runnable r) {
        if (!listeners.contains(r)) {
            listeners.add(r);
        }
    }

    public static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private static void notifyListeners() {
        for (Runnable r : new java.util.ArrayList<>(listeners)) {
            r.run();
        }
    }

    public static String sliderName() {
        return SLIDER_NAMES[getSlider()];
    }

    public static String coverName() {
        return COVER_NAMES[getCover()];
    }

    /** Track thickness in dp for the real seek bar: def is used for the default style. */
    public static int seekLineDp(int def) {
        switch (getSlider()) {
            case 1:
                return 8;
            case 2:
                return 14;
            case 3:
                return 6;
            default:
                return def;
        }
    }

    /** Height of the slider in dp, used for its layout. */
    public static int sliderHeightDp() {
        return SLIDER_HEIGHT_DP[getSlider()];
    }

    /** Corner radius of the cover in px. */
    public static int coverRadiusPx() {
        return AndroidUtilities.dp(COVER_RADIUS_DP[getCover()]);
    }

    public static void showSliderPicker(Context context, Runnable onChanged) {
        final String[] items = new String[SLIDER_NAMES.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = (i == getSlider() ? "✓ " : "") + SLIDER_NAMES[i];
        }
        new AlertDialog.Builder(context)
                .setTitle("Ползунок в плеере")
                .setItems(items, (d, which) -> {
                    setSlider(which);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public static void showCoverPicker(Context context, Runnable onChanged) {
        final String[] items = new String[COVER_NAMES.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = (i == getCover() ? "✓ " : "") + COVER_NAMES[i];
        }
        new AlertDialog.Builder(context)
                .setTitle("Форма обложки")
                .setItems(items, (d, which) -> {
                    setCover(which);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ------------------------------------------------------------ slider view

    /** Same interface as the progress line used by the player: setProgress, setProgressColor, setBackgroundColor. */
    public static class Slider extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wave = new Path();
        private final RectF rect = new RectF();
        private float progress;
        private int progressColor = 0xFFFFFFFF;
        private int trackColor = 0x33FFFFFF;
        private final int style;

        public Slider(Context context) {
            super(context);
            style = getSlider();
            setWillNotDraw(false);
        }

        public void setProgressColor(int color) {
            progressColor = color;
            invalidate();
        }

        @Override
        public void setBackgroundColor(int color) {
            trackColor = color;
            invalidate();
        }

        public void setProgress(float value, boolean animated) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth();
            final float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            final float x = w * progress;
            if (style == 0) {
                paint.setColor(trackColor);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setColor(progressColor);
                canvas.drawRect(0, 0, x, h, paint);
            } else if (style == 1) {
                final float r = h / 2f;
                paint.setColor(trackColor);
                rect.set(0, 0, w, h);
                canvas.drawRoundRect(rect, r, r, paint);
                paint.setColor(progressColor);
                rect.set(0, 0, Math.max(x, h), h);
                canvas.drawRoundRect(rect, r, r, paint);
            } else if (style == 2) {
                final float mid = h / 2f;
                paint.setColor(trackColor);
                rect.set(0, mid - AndroidUtilities.dp(1), w, mid + AndroidUtilities.dp(1));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), paint);
                wave.reset();
                final float amp = AndroidUtilities.dp(2.5f);
                final float phase = (SystemClock.uptimeMillis() % 2000) / 2000f * (float) Math.PI * 2f;
                wave.moveTo(0, mid);
                for (float px = 0; px <= x; px += AndroidUtilities.dp(1)) {
                    wave.lineTo(px, mid + (float) Math.sin(px / AndroidUtilities.dp(10) + phase) * amp);
                }
                wave.lineTo(x, mid);
                paint.setColor(progressColor);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2));
                canvas.drawPath(wave, paint);
                paint.setStyle(Paint.Style.FILL);
                postInvalidateOnAnimation();
            } else {
                final float step = AndroidUtilities.dp(8);
                final float r = h / 2f;
                for (float px = r; px <= w - r; px += step) {
                    paint.setColor(px <= x ? progressColor : trackColor);
                    canvas.drawCircle(px, h / 2f, r, paint);
                }
            }
        }
    }

    /** Creates the slider for the current style. */
    public static Slider createSlider(Context context) {
        return new Slider(context);
    }
}
