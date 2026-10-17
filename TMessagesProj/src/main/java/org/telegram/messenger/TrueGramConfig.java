package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/**
 * TrueGram settings and the local list of messages that were deleted by someone else
 * while "keep deleted messages" was enabled.
 */
public class TrueGramConfig {

    private static final String PREFS_NAME = "truegram";
    private static final String KEY_SAVE_DELETED = "save_deleted";

    private static volatile SharedPreferences prefs;

    private static SharedPreferences prefs() {
        SharedPreferences p = prefs;
        if (p == null) {
            synchronized (TrueGramConfig.class) {
                p = prefs;
                if (p == null) {
                    p = prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                }
            }
        }
        return p;
    }

    public static boolean isSaveDeletedEnabled() {
        return prefs().getBoolean(KEY_SAVE_DELETED, false);
    }

    public static void setSaveDeletedEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_SAVE_DELETED, enabled).apply();
    }

    private static String key(int account, long channelId, int messageId) {
        return "d_" + account + "_" + channelId + "_" + messageId;
    }

    /** channelId is 0 for private chats and basic groups. */
    public static void markDeleted(int account, long channelId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        final SharedPreferences.Editor editor = prefs().edit();
        for (int i = 0; i < ids.size(); i++) {
            final Integer id = ids.get(i);
            if (id != null) {
                editor.putBoolean(key(account, channelId, id), true);
            }
        }
        editor.apply();
    }

    public static boolean isMarkedDeleted(int account, MessageObject object) {
        if (object == null || object.messageOwner == null) {
            return false;
        }
        final long channelId = object.messageOwner.peer_id != null ? object.messageOwner.peer_id.channel_id : 0;
        return prefs().getBoolean(key(account, channelId, object.getId()), false);
    }
}
