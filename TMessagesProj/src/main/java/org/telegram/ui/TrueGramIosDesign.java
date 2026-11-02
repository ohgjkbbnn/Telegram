package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * TrueGram: "Дизайн iOS" switch (Оформление). Applies a glass-like look to the
 * header pill, the round search button next to the bottom bar and other chrome.
 * Read once per screen creation, so the change takes effect after the screen is
 * reopened (or Telegram restarted).
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

    /** Translucent glass background: base colour at ~70% alpha with a faint light edge. */
    private static GradientDrawable glassDrawable(int shape, float radiusPx) {
        int base = Theme.getColor(Theme.key_actionBarDefault);
        GradientDrawable d = new GradientDrawable();
        d.setShape(shape);
        if (shape == GradientDrawable.RECTANGLE) {
            d.setCornerRadius(radiusPx);
        }
        d.setColor((base & 0x00FFFFFF) | 0xB3000000);
        d.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
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

    /**
     * Round glass button with the search icon, placed to the right of the bottom bar.
     * Caller adds it to the frame and wires the click.
     */
    public static View createSearchButton(Context context, Runnable onClick) {
        FrameLayout button = new FrameLayout(context);
        button.setBackground(glassDrawable(GradientDrawable.OVAL, 0));
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageResource(R.drawable.outline_header_search);
        icon.setColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon));
        button.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        button.setOnClickListener(v -> {
            if (onClick != null) {
                onClick.run();
            }
        });
        return button;
    }
}
