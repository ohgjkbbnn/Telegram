package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/** TrueGram: how transparent deleted messages are drawn in chats. */
public class TrueGramDeletedAlpha {

    public static final String[] NAMES = {
            "Выключена",
            "Слабая",
            "Средняя",
            "Сильная"
    };

    // alpha (0..255) for each level; 255 = no transparency
    private static final int[] ALPHAS = {255, 190, 120, 70};
    private static final int DEFAULT_LEVEL = 2;

    private static int level = -1;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("truegram_deleted_alpha", Context.MODE_PRIVATE);
    }

    public static int getLevel() {
        if (level < 0) {
            int l = prefs().getInt("level", DEFAULT_LEVEL);
            level = Math.max(0, Math.min(ALPHAS.length - 1, l));
        }
        return level;
    }

    public static void setLevel(int l) {
        level = Math.max(0, Math.min(ALPHAS.length - 1, l));
        prefs().edit().putInt("level", level).apply();
    }

    public static int getAlpha() {
        return ALPHAS[getLevel()];
    }
}
