package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

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
import java.util.ArrayList;
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
    private static final String KEY_EDITED_COLOR = "edited_color";
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

    /** 0 = not chosen (use the normal time color). Color of the ↺ mark on edited messages. */
    public static int getEditedColor() {
        return prefs().getInt(KEY_EDITED_COLOR, 0);
    }

    public static void setEditedColor(int color) {
        prefs().edit().putInt(KEY_EDITED_COLOR, color).apply();
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
        if (m == null || m.peer_id == null || !isSaveEditsEnabled()) {
            return;
        }
        onPhotoEdited(account, m);
        if (m.message == null) {
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
        final long ch = object.messageOwner.peer_id.channel_id;
        final SharedPreferences p = prefs();
        final int id = object.getId();
        return p.contains(key("h_", account, ch, id)) || p.contains(key("ph_", account, ch, id)) || p.contains(key("pm_", account, ch, id));
    }

    // ---------- photo edit history ----------

    private static final Set<String> pendingPhotos = ConcurrentHashMap.newKeySet();

    private static File photoCopyFile(int account, long ch, int msgId, long photoId) {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "truegram");
        dir.mkdirs();
        return new File(dir, "p_" + account + "_" + ch + "_" + msgId + "_" + photoId + ".jpg");
    }

    private static boolean copyFile(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            final byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return dst.length() > 0;
        } catch (Throwable e) {
            dst.delete();
            return false;
        }
    }

    /** Finds the best downloaded file of the message photo, trying every size from the largest. */
    private static File findPhotoFile(int account, TLRPC.Message m) {
        if (m == null || m.media == null || m.media.photo == null) {
            return null;
        }
        final FileLoader loader = FileLoader.getInstance(account);
        try {
            final File f = loader.getPathToMessage(m);
            if (f != null && f.exists() && f.length() > 0) {
                return f;
            }
        } catch (Throwable ignore) {
        }
        final ArrayList<TLRPC.PhotoSize> sizes = m.media.photo.sizes;
        if (sizes != null) {
            for (int i = sizes.size() - 1; i >= 0; i--) {
                final TLRPC.PhotoSize s = sizes.get(i);
                if (s == null || s instanceof TLRPC.TL_photoStrippedSize || s instanceof TLRPC.TL_photoSizeEmpty) {
                    continue;
                }
                for (int pass = 0; pass < 2; pass++) {
                    try {
                        final File f = loader.getPathToAttach(s, pass == 1);
                        if (f != null && f.exists() && f.length() > 0) {
                            return f;
                        }
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
        return null;
    }

    private static void addPhotoHistory(SharedPreferences p, String hk, String path) {
        final String h = p.getString(hk, "");
        if (!h.isEmpty()) {
            for (String s : h.split(SEP)) {
                if (s.equals(path)) {
                    return;
                }
            }
        }
        p.edit().putString(hk, h.isEmpty() ? path : h + SEP + path).apply();
    }

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
        final File src = findPhotoFile(account, m);
        if (src == null) {
            pendingPhotos.remove(ik);
            return;
        }
        final long photoId = m.media.photo.id;
        final int msgId = m.id;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final File dst = photoCopyFile(account, ch, msgId, photoId);
                if (dst.exists() && dst.length() > 0 || copyFile(src, dst)) {
                    prefs().edit().putLong(ik, photoId).putString(key("pp_", account, ch, msgId), dst.getAbsolutePath()).apply();
                }
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
        if (path != null && new File(path).exists()) {
            addPhotoHistory(p, key("ph_", account, ch, m.id), path);
        }
        // forget the old photo so the new one is remembered on next display
        p.edit().remove(ik).remove(key("pp_", account, ch, m.id)).apply();
    }

    /**
     * Called when the chat swaps the old message object for the edited one: the old photo file
     * is still on the device at this moment, so its copy is made right here.
     */
    public static void onPhotoReplaced(int account, TLRPC.Message oldMsg, TLRPC.Message newMsg) {
        if (!isSaveEditsEnabled() || oldMsg == null || newMsg == null || oldMsg.peer_id == null) {
            return;
        }
        if (oldMsg.id <= 0 || oldMsg.id != newMsg.id) {
            return;
        }
        if (oldMsg.media == null || oldMsg.media.photo == null || newMsg.media == null || newMsg.media.photo == null) {
            return;
        }
        if (newMsg.edit_date == 0 || newMsg.edit_date == oldMsg.edit_date) {
            return;
        }
        final long oldPhotoId = oldMsg.media.photo.id;
        if (oldPhotoId == 0 || oldPhotoId == newMsg.media.photo.id) {
            return;
        }
        final long ch = oldMsg.peer_id.channel_id;
        final SharedPreferences p = prefs();
        final File dst = photoCopyFile(account, ch, oldMsg.id, oldPhotoId);
        if (!dst.exists() || dst.length() == 0) {
            final File src = findPhotoFile(account, oldMsg);
            if (src == null || !copyFile(src, dst)) {
                // the original was never downloaded to this device, remember at least the fact of the edit
                p.edit().putBoolean(key("pm_", account, ch, oldMsg.id), true).apply();
                return;
            }
        }
        addPhotoHistory(p, key("ph_", account, ch, oldMsg.id), dst.getAbsolutePath());
        p.edit().remove(key("pm_", account, ch, oldMsg.id)).apply();
    }

    public static void showEditHistory(Context context, int account, MessageObject object) {
        if (!hasEditHistory(account, object)) {
            return;
        }
        final long ch = object.messageOwner.peer_id.channel_id;
        final SharedPreferences p = prefs();
        final String h = p.getString(key("h_", account, ch, object.getId()), "");
        final String ph = p.getString(key("ph_", account, ch, object.getId()), "");
        final boolean photoMissing = p.getBoolean(key("pm_", account, ch, object.getId()), false);
        final int textColor = Theme.getColor(Theme.key_dialogTextBlack);
        final int grayColor = Theme.getColor(Theme.key_dialogTextGray2);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        final int pad = AndroidUtilities.dp(20);
        root.setPadding(pad, AndroidUtilities.dp(8), pad, 0);

        if (!ph.isEmpty()) {
            final String[] paths = ph.split(SEP);
            for (int i = 0; i < paths.length; i++) {
                final TextView label = new TextView(context);
                label.setText(i == 0 ? "Оригинал (фото):" : "Фото, версия " + (i + 1) + ":");
                label.setTextColor(grayColor);
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
        if (photoMissing && ph.isEmpty()) {
            final TextView note = new TextView(context);
            note.setText("Фото изменили, но оригинал сохранить не удалось: он не был загружен на устройство до правки.");
            note.setTextSize(16);
            note.setTextColor(textColor);
            root.addView(note);
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
            tv.setTextColor(textColor);
            root.addView(tv);
        }
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        new AlertDialog.Builder(context)
                .setTitle(ph.isEmpty() && h.isEmpty() ? "Что было изменено" : "Что было раньше")
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
