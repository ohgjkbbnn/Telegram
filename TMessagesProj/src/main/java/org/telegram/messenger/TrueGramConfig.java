package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.List;

/**
 * TrueGram: settings, locally kept deleted messages and edit history.
 */
public class TrueGramConfig {

    private static final String PREFS_NAME = "truegram";
    private static final String KEY_SAVE_DELETED = "save_deleted";
    private static final String KEY_SAVE_EDITS = "save_edits";
    private static final String KEY_DELETED_COLOR = "deleted_color";
    private static final String SEP = "\u0001";
    private static final int MAX_TEXT = 4000;

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

    public static boolean isSaveEditsEnabled() {
        return prefs().getBoolean(KEY_SAVE_EDITS, false);
    }

    public static void setSaveEditsEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_SAVE_EDITS, enabled).apply();
    }

    private static String key(String prefix, int account, long channelId, int messageId) {
        return prefix + account + "_" + channelId + "_" + messageId;
    }

    private static String cap(String s) {
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    // ---------- deleted messages ----------

    /** 0 = not chosen (use the normal time color). */
    public static int getDeletedColor() {
        return prefs().getInt(KEY_DELETED_COLOR, 0);
    }

    public static void setDeletedColor(int color) {
        prefs().edit().putInt(KEY_DELETED_COLOR, color).apply();
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
                editor.putBoolean(key("d_", account, channelId, id), true);
            }
        }
        editor.apply();
    }

    public static boolean isMarkedDeleted(int account, MessageObject object) {
        if (object == null || object.messageOwner == null) {
            return false;
        }
        final long channelId = object.messageOwner.peer_id != null ? object.messageOwner.peer_id.channel_id : 0;
        return prefs().getBoolean(key("d_", account, channelId, object.getId()), false);
    }

    // ---------- edit history ----------

    public static void rememberText(int account, TLRPC.Message m) {
        if (m == null || m.peer_id == null || m.message == null || m.message.isEmpty() || !isSaveEditsEnabled()) {
            return;
        }
        final String k = key("t_", account, m.peer_id.channel_id, m.id);
        final SharedPreferences p = prefs();
        if (!p.contains(k)) {
            p.edit().putString(k, cap(m.message)).apply();
        }
    }

    public static void onEdited(int account, TLRPC.Message m) {
        if (m == null || m.peer_id == null || m.message == null || !isSaveEditsEnabled()) {
            return;
        }
        final long ch = m.peer_id.channel_id;
        final String tk = key("t_", account, ch, m.id);
        final SharedPreferences p = prefs();
        final String old = p.getString(tk, null);
        final String now = cap(m.message);
        if (old == null) {
            p.edit().putString(tk, now).apply();
        } else if (!old.equals(now)) {
            final String hk = key("h_", account, ch, m.id);
            final String h = p.getString(hk, "");
            p.edit().putString(hk, h.isEmpty() ? old : h + SEP + old).putString(tk, now).apply();
        }
    }

    public static boolean hasEditHistory(int account, MessageObject object) {
        if (object == null || object.messageOwner == null || object.messageOwner.peer_id == null) {
            return false;
        }
        return prefs().contains(key("h_", account, object.messageOwner.peer_id.channel_id, object.getId()));
    }

    public static void showEditHistory(Context context, int account, MessageObject object) {
        if (!hasEditHistory(account, object)) {
            return;
        }
        final String h = prefs().getString(key("h_", account, object.messageOwner.peer_id.channel_id, object.getId()), "");
        final String[] versions = h.split(SEP);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < versions.length; i++) {
            if (sb.length() > 0) {
                sb.append("\n\n— — —\n\n");
            }
            sb.append(i == 0 ? "Оригинал:\n" : "Версия " + (i + 1) + ":\n").append(versions[i]);
        }
        new AlertDialog.Builder(context)
                .setTitle("Что было написано")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------- data center ----------

    public static String dcName(TLRPC.User user) {
        if (user == null || user.photo == null || user.photo.dc_id == 0) {
            return "DC ?";
        }
        final int dc = user.photo.dc_id;
        String city;
        switch (dc) {
            case 1:
            case 3:
                city = "Miami";
                break;
            case 2:
            case 4:
                city = "Amsterdam";
                break;
            case 5:
                city = "Singapore";
                break;
            default:
                city = "?";
        }
        return "DC" + dc + ", " + city;
    }
}
