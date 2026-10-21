package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TrueGram "@all": writing @all in a group mentions its members. The style is chosen in settings:
 * with @usernames, names only, or invisible. Hooks: intercept(...) in SendMessagesHelper.sendMessage,
 * screen: TrueGramMentionsActivity (chat menu -> TrueGram).
 */
public class TrueGramMentions {

    private static final String PREFS = "truegram_mentions";
    private static final Pattern TRIGGER = Pattern.compile("(?i)(^|\\s)(@all)(?=\\s|$)");

    private static final String[] WHOM = {"Всех участников", "Только администраторов"};
    private static final int[] LIMITS = {25, 50, 100};
    private static final int[] PAUSES = {0, 30, 60, 300};
    private static final String[] STYLES = {"С юзами (@username)", "Без юзов (только имена)", "Без ников (невидимые)"};
    private static final int STYLE_USERNAME = 0, STYLE_NAME = 1, STYLE_INVISIBLE = 2;

    private static final int MAX_MEMBERS = 1000;
    private static final long CACHE_MS = 5 * 60 * 1000L;
    private static final HashMap<String, Cache> CACHE = new HashMap<>();
    private static boolean bypass;
    private static final HashMap<String, Integer> OFFSETS = new HashMap<>();
    private static final HashMap<String, Long> LAST_USE = new HashMap<>();
    private static volatile int curAccount;

    private static class Cache {
        long time;
        ArrayList<TLRPC.User> users;
    }

    private interface Callback {
        void done(ArrayList<TLRPC.User> users);
    }

    private static volatile SharedPreferences prefs;

    private static SharedPreferences prefs() {
        SharedPreferences p = prefs;
        if (p == null) {
            synchronized (TrueGramMentions.class) {
                p = prefs;
                if (p == null) {
                    p = prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                }
            }
        }
        return p;
    }

    // ------------------------------------------------------------ settings

    public static boolean isEnabled() { return prefs().getBoolean("enabled", true); }
    public static void setEnabled(boolean v) { prefs().edit().putBoolean("enabled", v).apply(); }
    private static int whom() { return Math.max(0, Math.min(WHOM.length - 1, prefs().getInt("whom", 0))); }
    private static int limitIdx() { return Math.max(0, Math.min(LIMITS.length - 1, prefs().getInt("limit", 2))); }
    private static int pauseIdx() { return Math.max(0, Math.min(PAUSES.length - 1, prefs().getInt("pause2", 0))); }
    private static int style() { return Math.max(0, Math.min(STYLES.length - 1, prefs().getInt("style3", 0))); }

    private static String pauseText(int sec) {
        return sec == 0 ? "Без паузы" : sec < 60 ? sec + " сек" : (sec / 60) + " мин";
    }

    // ------------------------------------------------------------ options API for the settings screen

    public static final int OPT_WHOM = 0, OPT_LIMIT = 1, OPT_PAUSE = 2, OPT_STYLE = 3;

    public static String[] optionNames(int opt) {
        switch (opt) {
            case OPT_WHOM:
                return WHOM;
            case OPT_LIMIT: {
                final String[] l = new String[LIMITS.length];
                for (int i = 0; i < l.length; i++) l[i] = LIMITS[i] + " чел.";
                return l;
            }
            case OPT_PAUSE: {
                final String[] p = new String[PAUSES.length];
                for (int i = 0; i < p.length; i++) p[i] = pauseText(PAUSES[i]);
                return p;
            }
            default:
                return STYLES;
        }
    }

    public static int optionIndex(int opt) {
        switch (opt) {
            case OPT_WHOM:
                return whom();
            case OPT_LIMIT:
                return limitIdx();
            case OPT_PAUSE:
                return pauseIdx();
            default:
                return style();
        }
    }

    public static void setOptionIndex(int opt, int idx) {
        final String[] keys = {"whom", "limit", "pause2", "style3"};
        prefs().edit().putInt(keys[opt], idx).apply();
    }

    public static String optionText(int opt) {
        return optionNames(opt)[optionIndex(opt)];
    }

    // ------------------------------------------------------------ hook for SendMessagesHelper.sendMessage

    /** Returns true if the message was taken over (it is sent later, after members are loaded). */
    public static boolean intercept(final SendMessagesHelper helper, final int account, final SendMessagesHelper.SendMessageParams params) {
        if (bypass || params == null || !isEnabled() || !DialogObject.isChatDialog(params.peer) || !containsTrigger(params.message)) {
            return false;
        }
        curAccount = account;
        final TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-params.peer);
        if (chat == null || (ChatObject.isChannel(chat) && !chat.megagroup)) {
            return false;
        }
        loadUsers(account, chat, users -> {
            if (users == null || users.isEmpty()) {
                toast("Не удалось получить участников. Сообщение отправлено как есть");
                send(helper, params);
                return;
            }
            // one @all = one message. Members over the limit are NOT sent automatically:
            // the next @all continues with the next members (round robin)
            final String ck = account + "_" + params.peer;
            final long now = System.currentTimeMillis();
            final int pauseSec = PAUSES[pauseIdx()];
            final Long last = LAST_USE.get(ck);
            if (pauseSec > 0 && last != null && now - last < pauseSec * 1000L) {
                toast("Пауза между упоминаниями: подождите ещё " + ((pauseSec * 1000L - (now - last)) / 1000 + 1) + " сек");
                send(helper, params);
                return;
            }
            final int limit = LIMITS[limitIdx()];
            final int size = users.size();
            final Integer saved = OFFSETS.get(ck);
            final int off = saved == null ? 0 : saved % size;
            final ArrayList<TLRPC.User> batch = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, size); i++) {
                batch.add(users.get((off + i) % size));
            }
            final Result r = build(params.message, params.entities, batch);
            if (r == null) {
                send(helper, params);
                return;
            }
            OFFSETS.put(ck, (off + batch.size()) % size);
            LAST_USE.put(ck, now);
            if (size > batch.size()) {
                toast("Упомянуто " + batch.size() + " из " + size + ". Напишите @all ещё раз для следующих");
            }
            params.message = r.message;
            params.entities = r.entities;
            send(helper, params);
        });
        return true;
    }

    private static volatile long lastSendMs;

    /** Called from SendMessagesHelper when a send fails: shows the real server error for fresh @all sends. */
    public static void onSendError(TLRPC.TL_error error) {
        if (error != null && System.currentTimeMillis() - lastSendMs < 20000L) {
            toast("TrueGram: сервер вернул ошибку " + error.code + " " + error.text);
        }
    }

    private static void send(SendMessagesHelper helper, SendMessagesHelper.SendMessageParams params) {
        lastSendMs = System.currentTimeMillis();
        bypass = true;
        try {
            helper.sendMessage(params);
        } finally {
            bypass = false;
        }
    }

    private static void toast(String text) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show());
    }

    // ------------------------------------------------------------ members

    private static void loadUsers(int account, TLRPC.Chat chat, Callback cb) {
        final String key = account + "_" + chat.id + "_" + whom();
        final Cache c = CACHE.get(key);
        if (c != null && System.currentTimeMillis() - c.time < CACHE_MS) {
            cb.done(c.users);
            return;
        }
        if (!ChatObject.isChannel(chat)) {
            cb.done(collectBasic(account, chat.id));
            return;
        }
        fetchPage(account, chat, 0, new ArrayList<>(), key, cb);
    }

    // supergroups: members come from the server, page by page
    private static void fetchPage(final int account, final TLRPC.Chat chat, final int offset,
                                  final ArrayList<TLRPC.User> acc, final String key, final Callback cb) {
        final MessagesController mc = MessagesController.getInstance(account);
        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = mc.getInputChannel(chat.id);
        req.filter = whom() == 1 ? new TLRPC.TL_channelParticipantsAdmins() : new TLRPC.TL_channelParticipantsRecent();
        req.offset = offset;
        req.limit = 200;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.TL_channels_channelParticipants)) {
                cb.done(acc.isEmpty() ? null : acc);
                return;
            }
            final TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
            mc.putUsers(res.users, false);
            final long me = UserConfig.getInstance(account).getClientUserId();
            for (TLRPC.ChannelParticipant p : res.participants) {
                final long id = p.peer != null ? MessageObject.getPeerId(p.peer) : p.user_id;
                if (id <= 0 || id == me) {
                    continue;
                }
                final TLRPC.User u = mc.getUser(id);
                if (u != null && !u.bot && !u.deleted) {
                    acc.add(u);
                }
            }
            final int next = offset + res.participants.size();
            if (res.participants.isEmpty() || next >= res.count || next >= MAX_MEMBERS) {
                if (acc.size() < Math.min(chat.participants_count, MAX_MEMBERS) / 2) {
                    toast("Telegram отдаёт только " + acc.size() + " из " + chat.participants_count + " участников: в больших группах список скрыт");
                }
                final Cache c = new Cache();
                c.time = System.currentTimeMillis();
                c.users = acc;
                CACHE.put(key, c);
                cb.done(acc);
            } else {
                fetchPage(account, chat, next, acc, key, cb);
            }
        }));
    }

    // basic groups: participants are already in ChatFull
    private static ArrayList<TLRPC.User> collectBasic(int account, long chatId) {
        final MessagesController mc = MessagesController.getInstance(account);
        final TLRPC.ChatFull full = mc.getChatFull(chatId);
        if (full == null || full.participants == null || full.participants.participants == null || full.participants.participants.isEmpty()) {
            mc.loadFullChat(chatId, 0, true);
            return null;
        }
        final long me = UserConfig.getInstance(account).getClientUserId();
        final boolean adminsOnly = whom() == 1;
        final ArrayList<TLRPC.User> out = new ArrayList<>();
        for (TLRPC.ChatParticipant p : full.participants.participants) {
            if (p.user_id == me) {
                continue;
            }
            if (adminsOnly && !(p instanceof TLRPC.TL_chatParticipantAdmin) && !(p instanceof TLRPC.TL_chatParticipantCreator)) {
                continue;
            }
            final TLRPC.User u = mc.getUser(p.user_id);
            if (u != null && !u.bot && !u.deleted) {
                out.add(u);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ message building

    public static class Result {
        public String message;
        public ArrayList<TLRPC.MessageEntity> entities;
        public int mode;
    }

    /** Only the plain command "@all". */
    public static boolean containsTrigger(String message) {
        return message != null && message.length() >= 4 && TRIGGER.matcher(message).find();
    }

    private static boolean hasVisible(CharSequence cs) {
        for (int i = 0; i < cs.length(); i++) {
            final char c = cs.charAt(i);
            if (!Character.isWhitespace(c) && c != '\u200B' && c != '\u2800' && c != '\u2060' && c != '\u2063') {
                return true;
            }
        }
        return false;
    }

    /**
     * The server trims zero-width chars at the start/end of a message (MESSAGE_EMPTY, lost mentions),
     * so invisible carriers must have a real visible char on both sides.
     */
    private static String guardInvisible(String text, int blockStart, int blockEnd, ArrayList<TLRPC.MessageEntity> ents) {
        final boolean before = hasVisible(text.substring(0, blockStart));
        final boolean after = hasVisible(text.substring(blockEnd));
        if (!before) {
            text = "\u00B7" + text;
            for (TLRPC.MessageEntity e : ents) {
                e.offset += 1;
            }
        }
        if (!after) {
            text = text + "\u00B7";
        }
        return text;
    }

    /** Replaces the first "@all" with mentions; members over the limit go to Result.rest. */
    private static Result build(String message, ArrayList<TLRPC.MessageEntity> entities, ArrayList<TLRPC.User> users) {
        final Matcher m = TRIGGER.matcher(message);
        if (!m.find()) {
            return null;
        }
        final int mode = style();
        final int start = m.start(2);
        final int end = m.end();

        final StringBuilder block = new StringBuilder();
        final ArrayList<TLRPC.MessageEntity> mentionEntities = new ArrayList<>();
        buildBlock(users, mode, start, block, mentionEntities);

        String text = message.substring(0, start) + block + message.substring(end);
        final int delta = block.length() - (end - start);
        final ArrayList<TLRPC.MessageEntity> out = new ArrayList<>(mentionEntities);
        if (entities != null) {
            for (TLRPC.MessageEntity e : entities) {
                if (e.offset >= end) {
                    e.offset += delta;
                    out.add(e);
                } else if (e.offset + e.length <= start) {
                    out.add(e);
                }
            }
        }
        if (mode == STYLE_INVISIBLE) {
            text = guardInvisible(text, start, start + block.length(), out);
        }
        Collections.sort(out, (a, b) -> a.offset - b.offset);

        final Result r = new Result();
        r.message = text;
        r.entities = out;
        r.mode = mode;
        return r;
    }

    private static void buildBlock(ArrayList<TLRPC.User> users, int mode, int baseOffset,
                                   StringBuilder text, ArrayList<TLRPC.MessageEntity> entities) {
        for (int i = 0; i < users.size(); i++) {
            final TLRPC.User user = users.get(i);
            final String username = user.username;
            if (mode == STYLE_USERNAME && username != null && !username.isEmpty()) {
                text.append('@').append(username); // plain @username: Telegram itself turns it into a mention
            } else {
                final TLRPC.TL_inputMessageEntityMentionName e = new TLRPC.TL_inputMessageEntityMentionName();
                e.user_id = MessagesController.getInstance(curAccount).getInputUser(user);
                e.offset = baseOffset + text.length();
                final String label;
                if (mode == STYLE_INVISIBLE) {
                    label = "\u200B";
                } else if (user.first_name != null && !user.first_name.isEmpty()) {
                    label = user.first_name;
                } else {
                    label = "участник";
                }
                e.length = label.length();
                text.append(label);
                entities.add(e);
            }
            if (mode != STYLE_INVISIBLE && i < users.size() - 1) {
                text.append(' ');
            }
        }
    }
}
