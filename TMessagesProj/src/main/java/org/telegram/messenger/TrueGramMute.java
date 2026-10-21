package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TrueGram: "Мут в ЛС". When enabled, commands typed in a private chat
 * (!mute, !unmute, !status) are not sent; a muted person's new messages are deleted.
 */
public class TrueGramMute implements NotificationCenter.NotificationCenterDelegate {

    private static final String PREFS = "truegram_mute";
    private static final String PREFIX = "!";
    private static final long DEFAULT_MS = 60L * 60L * 1000L;
    private static final long MAX_MS = 5L * 365L * 24L * 3600L * 1000L;
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([smhdw])");

    private static final TrueGramMute INSTANCE = new TrueGramMute();
    private static boolean attached;
    private static HashMap<String, Long> muted; // "account:dialogId" -> until (ms), 0 = forever

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ settings

    public static boolean isEnabled() {
        return prefs().getBoolean("enabled", false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean("enabled", enabled).apply();
        if (enabled) {
            init();
        }
    }

    public static boolean isRevokeEnabled() {
        return prefs().getBoolean("revoke", true);
    }

    public static void setRevokeEnabled(boolean enabled) {
        prefs().edit().putBoolean("revoke", enabled).apply();
    }

    // ------------------------------------------------------------------ init

    /** Call once at app start (ApplicationLoader) so incoming messages are watched on every account. */
    public static synchronized void init() {
        if (attached) {
            return;
        }
        attached = true;
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).addObserver(INSTANCE, NotificationCenter.didReceiveNewMessages);
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id != NotificationCenter.didReceiveNewMessages || !isEnabled() || args == null || args.length < 2) {
                return;
            }
            if (!(args[1] instanceof ArrayList)) {
                return;
            }
            final HashMap<Long, ArrayList<Integer>> pending = new HashMap<>();
            for (Object o : (ArrayList<?>) args[1]) {
                if (!(o instanceof MessageObject)) {
                    continue;
                }
                final MessageObject m = (MessageObject) o;
                if (m.messageOwner == null || m.messageOwner.out) {
                    continue;
                }
                final long dialogId = m.getDialogId();
                final int messageId = m.getId();
                if (dialogId <= 0 || messageId <= 0 || !isMuted(account, dialogId)) {
                    continue;
                }
                ArrayList<Integer> ids = pending.get(dialogId);
                if (ids == null) {
                    ids = new ArrayList<>();
                    pending.put(dialogId, ids);
                }
                ids.add(messageId);
            }
            for (Map.Entry<Long, ArrayList<Integer>> e : pending.entrySet()) {
                deleteMessages(account, e.getKey(), e.getValue());
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // ------------------------------------------------------------------ outgoing commands

    /** Returns true if the text was a mute command (the message must not be sent). */
    public static boolean handleOutgoing(int account, long dialogId, String text) {
        try {
            if (text == null || !isEnabled()) {
                return false;
            }
            text = text.trim();
            if (!text.startsWith(PREFIX)) {
                return false;
            }
            final String[] parts = text.split("\\s+", 2);
            final String cmd = parts[0].toLowerCase(Locale.ROOT);
            final String arg = parts.length > 1 ? parts[1].trim() : "";
            if (cmd.equals(PREFIX + "mute")) {
                commandMute(account, dialogId, arg);
            } else if (cmd.equals(PREFIX + "unmute")) {
                commandUnmute(account, dialogId);
            } else if (cmd.equals(PREFIX + "status")) {
                commandStatus(account, dialogId);
            } else {
                return false;
            }
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    private static void commandMute(int account, long dialogId, String arg) {
        if (dialogId <= 0) {
            toast("Мут работает только в личных чатах");
            return;
        }
        if (dialogId == UserConfig.getInstance(account).getClientUserId()) {
            toast("Нельзя замутить себя");
            return;
        }
        long ms = arg.isEmpty() ? DEFAULT_MS : parseDurationMs(arg);
        if (ms < 0) {
            toast("Неверный формат времени. Примеры: 30m, 2h, 1d, forever");
            return;
        }
        final long until = ms == 0 ? 0 : System.currentTimeMillis() + ms;
        synchronized (TrueGramMute.class) {
            load().put(key(account, dialogId), until);
            save();
        }
        final String name = displayName(account, dialogId);
        toast(until == 0 ? "Замутил " + name + " навсегда" : "Замутил " + name + " до " + fmt(until));
    }

    private static void commandUnmute(int account, long dialogId) {
        if (dialogId <= 0) {
            toast("Размутить можно только в личном чате");
            return;
        }
        boolean had;
        synchronized (TrueGramMute.class) {
            had = load().remove(key(account, dialogId)) != null;
            if (had) {
                save();
            }
        }
        toast(had ? "Размутил " + displayName(account, dialogId) : "Этот пользователь не в муте");
    }

    private static void commandStatus(int account, long dialogId) {
        if (dialogId <= 0) {
            toast("Статус показывается в личном чате");
            return;
        }
        Long until;
        synchronized (TrueGramMute.class) {
            until = load().get(key(account, dialogId));
        }
        if (until == null || (until != 0 && until <= System.currentTimeMillis())) {
            toast("Этот пользователь не в муте");
            return;
        }
        final String name = displayName(account, dialogId);
        toast(until == 0 ? name + " в муте навсегда" : name + " в муте до " + fmt(until));
    }

    // ------------------------------------------------------------------ storage

    private static String key(int account, long dialogId) {
        return account + ":" + dialogId;
    }

    private static HashMap<String, Long> load() {
        if (muted == null) {
            muted = new HashMap<>();
            try {
                final JSONObject json = new JSONObject(prefs().getString("muted", "{}"));
                final Iterator<String> it = json.keys();
                while (it.hasNext()) {
                    final String k = it.next();
                    muted.put(k, json.getLong(k));
                }
            } catch (Exception ignore) {
            }
        }
        return muted;
    }

    private static void save() {
        try {
            final JSONObject json = new JSONObject();
            for (Map.Entry<String, Long> e : load().entrySet()) {
                json.put(e.getKey(), e.getValue());
            }
            prefs().edit().putString("muted", json.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isMuted(int account, long dialogId) {
        synchronized (TrueGramMute.class) {
            final HashMap<String, Long> map = load();
            final String k = key(account, dialogId);
            final Long until = map.get(k);
            if (until == null) {
                return false;
            }
            if (until != 0 && until <= System.currentTimeMillis()) {
                map.remove(k);
                save();
                return false;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** -1 = invalid, 0 = forever, otherwise milliseconds. */
    private static long parseDurationMs(String text) {
        text = text.trim().toLowerCase(Locale.ROOT);
        if (text.equals("forever") || text.equals("навсегда") || text.equals("inf")) {
            return 0;
        }
        try {
            if (!text.replaceAll("(\\d+)\\s*([smhdw])", "").replaceAll("\\s", "").isEmpty()) {
                return -1;
            }
            long total = 0;
            boolean found = false;
            final Matcher m = DURATION.matcher(text);
            while (m.find()) {
                found = true;
                final long n = Long.parseLong(m.group(1));
                long unit;
                switch (m.group(2).charAt(0)) {
                    case 's': unit = 1L; break;
                    case 'm': unit = 60L; break;
                    case 'h': unit = 3600L; break;
                    case 'd': unit = 86400L; break;
                    default: unit = 604800L; break;
                }
                total += n * unit * 1000L;
            }
            if (!found) {
                if (text.matches("\\d+")) {
                    total = Long.parseLong(text) * 60L * 1000L; // bare number = minutes
                } else {
                    return -1;
                }
            }
            if (total <= 0) {
                return -1;
            }
            return Math.min(total, MAX_MS);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String fmt(long ms) {
        return new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private static String displayName(int account, long userId) {
        try {
            final TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
            if (user != null) {
                final String name = UserObject.getUserName(user);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Throwable ignore) {
        }
        return "id " + userId;
    }

    private static void toast(final String text) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show());
    }

    /**
     * MessagesController.deleteMessages has different signatures between Telegram versions,
     * so the shortest overload is found by reflection and filled with safe defaults.
     */
    private static void deleteMessages(final int account, final long dialogId, final ArrayList<Integer> ids) {
        final boolean revoke = isRevokeEnabled();
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Method best = null;
                for (Method m : MessagesController.class.getMethods()) {
                    if (!m.getName().equals("deleteMessages")) {
                        continue;
                    }
                    final Class<?>[] t = m.getParameterTypes();
                    if (t.length < 6 || t[0] != ArrayList.class || t[1] != ArrayList.class || t[2] != TLRPC.EncryptedChat.class || t[3] != long.class) {
                        continue;
                    }
                    if (best == null || t.length < best.getParameterTypes().length) {
                        best = m;
                    }
                }
                if (best == null) {
                    FileLog.e("TrueGramMute: deleteMessages not found");
                    return;
                }
                final Class<?>[] t = best.getParameterTypes();
                final Object[] args = new Object[t.length];
                boolean forAllSet = false;
                for (int i = 0; i < t.length; i++) {
                    if (i == 0) {
                        args[i] = ids;
                    } else if (i == 3) {
                        args[i] = dialogId;
                    } else if (t[i] == boolean.class) {
                        args[i] = !forAllSet && revoke;
                        forAllSet = true;
                    } else if (t[i] == int.class) {
                        args[i] = 0;
                    } else if (t[i] == long.class) {
                        args[i] = 0L;
                    } else if (t[i].isPrimitive()) {
                        FileLog.e("TrueGramMute: unsupported deleteMessages signature");
                        return;
                    } else {
                        args[i] = null;
                    }
                }
                best.invoke(MessagesController.getInstance(account), args);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }
}
