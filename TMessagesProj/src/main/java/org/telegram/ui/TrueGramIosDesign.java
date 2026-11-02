package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/**
 * TrueGram: "Дизайн iOS" switch (Оформление). Applies a glass-like look to the
 * header pill and other chrome. Read once per screen creation, so the change
 * takes effect after the screen is reopened (or Telegram restarted).
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

    /**
     * Translucent rounded "glass" pill behind a view (header button group).
     * Does nothing when the switch is off.
     */
    public static void stylePill(View view) {
        if (view == null || !isEnabled()) {
            return;
        }
        int base = Theme.getColor(Theme.key_actionBarDefault);
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(AndroidUtilities.dp(18));
        pill.setColor((base & 0x00FFFFFF) | 0x80000000);
        pill.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        view.setBackground(pill);
    }
}
