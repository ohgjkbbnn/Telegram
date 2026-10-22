package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/** TrueGram: block Telegram sponsored messages (ads shown at the bottom of channels and groups). */
public class TrueGramAdBlock {

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("truegram_ad_block", Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return prefs().getBoolean("enabled", false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean("enabled", enabled).apply();
    }
}
