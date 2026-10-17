package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        onPhotoEdited(account, m);
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
        final long ch = object.messageOwner.peer_id.channel_id;
        final SharedPreferences p = prefs();
        return p.contains(key("h_", account, ch, object.getId())) || p.contains(key("ph_", account, ch, object.getId()));
    }

    // ---------- photo edit history ----------

    private static final Set<String> pendingPhotos = ConcurrentHashMap.newKeySet();

    /** Keeps a private copy of a photo so it can be shown after the sender replaces it. */
    public static void rememberPhoto(int account, TLRPC.Message m) {
        if (m == null || m.peer_id == null || m.media == null || m.media.photo == null || !isSaveEditsEnabled()) {
            return;
        }
        final long ch = m.peer_id.channel_id;
        final String ik = key("pi_", account, ch, m.id);
        final SharedPreferences p = prefs();
        if (p.contains(ik) || !pendingPhotos.add(ik)) {
            return;
        }
        final File src = FileLoader.getInstance(account).getPathToMessage(m);
        if (src == null || !src.exists() || src.length() == 0) {
            pendingPhotos.remove(ik);
            return;
        }
        final long photoId = m.media.photo.id;
        final int msgId = m.id;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "truegram");
                dir.mkdirs();
                final File dst = new File(dir, "p_" + account + "_" + ch + "_" + msgId + "_" + photoId + ".jpg");
                final FileInputStream in = new FileInputStream(src);
                final FileOutputStream out = new FileOutputStream(dst);
                final byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                in.close();
                out.close();
                prefs().edit().putLong(ik, photoId).putString(key("pp_", account, ch, msgId), dst.getAbsolutePath()).apply();
            } catch (Throwable ignore) {
            }
            pendingPhotos.remove(ik);
        });
    }

    private static void onPhotoEdited(int account, TLRPC.Message m) {
        if (m.media == null || m.media.photo == null) {
            return;
        }
        final long ch = m.peer_id.channel_id;
        final SharedPreferences p = prefs();
        final String ik = key("pi_", account, ch, m.id);
        if (!p.contains(ik) || p.getLong(ik, 0) == m.media.photo.id) {
            return;
        }
        final String path = p.getString(key("pp_", account, ch, m.id), null);
        final SharedPreferences.Editor e = p.edit();
        if (path != null && new File(path).exists()) {
            final String hk = key("ph_", account, ch, m.id);
            final String h = p.getString(hk, "");
            e.putString(hk, h.isEmpty() ? path : h + SEP + path);
        }
        // forget the old photo so the new one is remembered on next display
        e.remove(ik).remove(key("pp_", account, ch, m.id)).apply();
    }

    public static void showEditHistory(Context context, int account, MessageObject object) {
        if (!hasEditHistory(account, object)) {
            return;
        }
        final long ch = object.messageOwner.peer_id.channel_id;
        final SharedPreferences p = prefs();
        final String h = p.getString(key("h_", account, ch, object.getId()), "");
        final String ph = p.getString(key("ph_", account, ch, object.getId()), "");

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        final int pad = AndroidUtilities.dp(20);
        root.setPadding(pad, AndroidUtilities.dp(8), pad, 0);

        if (!ph.isEmpty()) {
            final String[] paths = ph.split(SEP);
            for (int i = 0; i < paths.length; i++) {
                final TextView label = new TextView(context);
                label.setText(i == 0 ? "Оригинал (фото):" : "Фото, версия " + (i + 1) + ":");
                label.setTextColor(0xFF8E8E93);
                root.addView(label);
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                final Bitmap bmp = BitmapFactory.decodeFile(paths[i], opts);
                if (bmp != null) {
                    final ImageView iv = new ImageView(context);
                    iv.setAdjustViewBounds(true);
                    iv.setImageBitmap(bmp);
                    final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));
                    root.addView(iv, lp);
                }
            }
        }
        if (!h.isEmpty()) {
            final String[] versions = h.split(SEP);
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < versions.length; i++) {
                if (sb.length() > 0) {
                    sb.append("\n\n— — —\n\n");
                }
                sb.append(i == 0 ? "Оригинал:\n" : "Версия " + (i + 1) + ":\n").append(versions[i]);
            }
            final TextView tv = new TextView(context);
            tv.setText(sb.toString());
            tv.setTextSize(16);
            tv.setTextColor(0xFFFFFFFF);
            root.addView(tv);
        }
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        new AlertDialog.Builder(context)
                .setTitle("Что было написано")
                .setView(scroll)
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
