package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * TrueGram: «Отправлять всегда курсивом». Если включено, весь текст сообщения уходит курсивом.
 * Настройка в «Основные», рядом с «Жирным шрифтом при отправке».
 */
public final class TrueGramAutoItalic {

    private static final String PREFS = "truegram";
    private static final String KEY = "auto_italic";

    private TrueGramAutoItalic() {
    }

    public static boolean isEnabled() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return false;
            }
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return p.getBoolean(KEY, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Adds an italic entity over the whole message when the option is on.
     * Offsets are UTF-16 units, the same as the text length, so the span covers everything.
     */
    public static ArrayList<TLRPC.MessageEntity> wrap(ArrayList<TLRPC.MessageEntity> entities, int length) {
        if (length <= 0 || !isEnabled()) {
            return entities;
        }
        final ArrayList<TLRPC.MessageEntity> list = entities != null ? entities : new ArrayList<>();
        final TLRPC.TL_messageEntityItalic italic = new TLRPC.TL_messageEntityItalic();
        italic.offset = 0;
        italic.length = length;
        list.add(0, italic);
        return list;
    }
}
