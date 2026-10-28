package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TrueGramConfig;

/**
 * TrueGram "dark bubbles" style: translucent dark tint, dashed colored frame and a name plate.
 * Used by ChatMessageCell only when TrueGramConfig.getBubbleStyle() == 1.
 */
public final class TrueGramBubble {

    private static final RectF rect = new RectF();
    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    private TrueGramBubble() {
    }

    public static boolean isEnabled() {
        return TrueGramConfig.getBubbleStyle() == 1;
    }

    /** Dashes flow along the frame: the phase moves about 1 dp every 80 ms, so the dashes slide smoothly. */
    private static DashPathEffect dash() {
        final float phase = AndroidUtilities.dp(1) * ((SystemClock.uptimeMillis() / 80f) % 10f);
        return new DashPathEffect(new float[]{AndroidUtilities.dp(6), AndroidUtilities.dp(4)}, phase);
    }

    /**
     * Draws the bubble body along the exact outline the theme bubble uses (MessageDrawable.makePath()):
     * dark translucent fill and a dashed frame in the accent color.
     */
    public static void drawPath(Canvas canvas, Path path, int accent, float alpha, boolean selected) {
        if (path == null) {
            return;
        }
        final float a = Math.max(0f, Math.min(1f, alpha));
        final int strength = TrueGramConfig.getBubbleStrength();
        final float fillFactor = (0.14f + 0.16f * strength / 100f) + (selected ? 0.22f : 0f);
        final int fillAlpha = clampAlpha(255f * a * fillFactor);
        final int strokeAlpha = clampAlpha(255f * a);

        fillPaint.setColor((accent & 0x00FFFFFF) | (fillAlpha << 24));
        canvas.drawPath(path, fillPaint);

        strokePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        strokePaint.setPathEffect(dash());
        strokePaint.setColor((accent & 0x00FFFFFF) | (strokeAlpha << 24));
        canvas.drawPath(path, strokePaint);
    }

    /** Draws the rounded plate behind the sender name, tinted with the accent color. */
    public static void drawNamePlate(Canvas canvas, float left, float top, float right, float bottom, int accent, float alpha) {
        final float a = Math.max(0f, Math.min(1f, alpha));
        rect.set(left, top, right, bottom);
        fillPaint.setColor((accent & 0x00FFFFFF) | (clampAlpha(255f * a * 0.22f) << 24));
        final float r = AndroidUtilities.dp(10);
        canvas.drawRoundRect(rect, r, r, fillPaint);
    }

    /**
     * Slowly cycling color, like a garland: each message has its own phase (seed), one full cycle takes about 12 seconds.
     */
    public static int rainbowColor(int seed) {
        final float hue = ((SystemClock.uptimeMillis() / 33f) + seed * 47f) % 360f;
        return Color.HSVToColor(new float[]{hue, 0.75f, 1f});
    }

    private static int clampAlpha(float value) {
        return Math.max(0, Math.min(255, (int) value));
    }
}
