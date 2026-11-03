package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * TrueGram: "Дизайн iOS" switch (Оформление). Applies a glass-like look to the
 * translucent liquid-glass surfaces used by the tab bar, chat header and composer.
 * Screen-level blur sources are created when a screen opens, so reopen the screen
 * after changing the switch.
 */
public final class TrueGramIosDesign {

    private static final String PREFS = "truegram";
    private static final String KEY_ENABLED = "ios_design";

    private TrueGramIosDesign() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        try {
            return prefs().getBoolean(KEY_ENABLED, false);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setEnabled(boolean value) {
        try {
            prefs().edit().putBoolean(KEY_ENABLED, value).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Accent-tinted, translucent color for a surface backed by the blur renderer. */
    public static int glassTintColor(int color) {
        int opaque = color | 0xFF000000;
        int tinted = ColorUtils.blendARGB(opaque, TrueGramM3.primary(), 0.12f);
        int alpha = Color.alpha(color);
        if (alpha == 0) {
            alpha = 255;
        }
        return ColorUtils.setAlphaComponent(tinted, Math.min(0xA8, Math.max(0x64, Math.round(alpha * 0.40f))));
    }

    /** Subtle translucent fill for the composer field floating above the glass island. */
    public static int glassSurfaceColor(int color) {
        int tinted = ColorUtils.blendARGB(color | 0xFF000000, TrueGramM3.primary(), 0.08f);
        return ColorUtils.setAlphaComponent(tinted, 0x48);
    }

    /** Soft adaptive rim for glass surfaces, using the active Telegram theme. */
    public static int glassStrokeColor() {
        int outline = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        int tinted = ColorUtils.blendARGB(outline | 0xFF000000, Color.WHITE, 0.22f);
        return ColorUtils.setAlphaComponent(tinted, 0x72);
    }

    /** Translucent glass background: base colour at ~70% alpha with a faint light edge. */
    private static GradientDrawable glassDrawable(int shape, float radiusPx) {
        int base = ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefault), TrueGramM3.primary(), 0.08f);
        GradientDrawable d = new GradientDrawable();
        d.setShape(shape);
        if (shape == GradientDrawable.RECTANGLE) {
            d.setCornerRadius(radiusPx);
        }
        d.setColors(new int[] {
                ColorUtils.setAlphaComponent(base, 0xB0),
                ColorUtils.setAlphaComponent(base, 0x96)
        });
        d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        d.setStroke(AndroidUtilities.dp(1), glassStrokeColor());
        return d;
    }

    /**
     * Translucent rounded "glass" pill behind a view (header button group).
     * Does nothing when the switch is off.
     */
    public static void stylePill(View view) {
        if (view == null || !isEnabled()) {
            return;
        }
        view.setBackground(glassDrawable(GradientDrawable.RECTANGLE, AndroidUtilities.dp(18)));
    }

}
