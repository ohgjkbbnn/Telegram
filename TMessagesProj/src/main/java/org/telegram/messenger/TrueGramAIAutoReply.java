package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * AI auto-reply. Works only in chats enabled by the user (chat menu -> "ИИ-автоответ").
 * In groups it answers only when you are mentioned. Has rate limits so it can never loop.
 */
public class TrueGramAIAutoReply implements NotificationCenter.NotificationCenterDelegate {

    private static final TrueGramAIAutoReply[] INSTANCES = new TrueGramAIAutoReply[UserConfig.MAX_ACCOUNT_COUNT];
    private static final long MIN_GAP_MS = 8000L;
    private static final int MAX_PER_HOUR = 20;
    private static final int MAX_AGE_SEC = 90;

    private final int account;
    private final HashMap<Long, ArrayList<TrueGramAI.Msg>> history = new HashMap<>();
    private final HashMap<Long, ArrayList<Long>> sentTimes = new HashMap<>();
    private final HashSet<Long> busy = new HashSet<>();

    private TrueGramAIAutoReply(int account) {
        this.account = account;
    }

    /** Call once per account, e.g. from the SendMessagesHelper constructor. */
    public static void attach(int account) {
        synchronized (INSTANCES) {
            if (INSTANCES[account] != null) {
                return;
            }
            INSTANCES[account] = new TrueGramAIAutoReply(account);
        }
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(account).addObserver(INSTANCES[account], NotificationCenter.didReceiveNewMessages));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, int acc, Object... args) {
        if (id != NotificationCenter.didReceiveNewMessages || args == null || args.length < 2) {
            return;
        }
        if (args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2]) {
            return; // scheduled
        }
        final long dialogId = (Long) args[0];
        if (!TrueGramAI.isAutoReply(dialogId)) {
            return;
        }
        if (!TrueGramAI.isConfigured()) {
            TrueGramAI.setLastAutoReplyError("ИИ не настроен (нет активного ключа/модели)", dialogId);
            return;
        }
        final ArrayList<MessageObject> list = (ArrayList<MessageObject>) args[1];
        final int now = (int) (System.currentTimeMillis() / 1000L);
        boolean gotNew = false;
        for (MessageObject mo : list) {
            if (mo == null || mo.messageOwner == null || mo.isOut() || mo.messageOwner.date < now - MAX_AGE_SEC) {
                continue;
            }
            final String text = mo.messageOwner.message;
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            if (dialogId < 0 && !mo.messageOwner.mentioned && !TrueGramMute.isTargetingMe(account, mo)) {
                continue; // groups: only when mentioned (@tag) or replied to me — not relying on the server "mentioned" flag alone, it's not always set yet at this point
            }
            if (dialogId > 0) {
                final TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
                if (u == null || u.bot || u.self) {
                    continue;
                }
            }
            ArrayList<TrueGramAI.Msg> h = history.get(dialogId);
            if (h == null) {
                h = new ArrayList<>();
                history.put(dialogId, h);
            }
            h.add(new TrueGramAI.Msg("user", text));
            while (h.size() > 12) {
                h.remove(0);
            }
            gotNew = true;
        }
        if (gotNew) {
            reply(dialogId);
        }
    }

    private boolean allowed(long dialogId) {
        final long now = System.currentTimeMillis();
        ArrayList<Long> t = sentTimes.get(dialogId);
        if (t == null) {
            t = new ArrayList<>();
            sentTimes.put(dialogId, t);
        }
        for (int i = t.size() - 1; i >= 0; i--) {
            if (now - t.get(i) > 3600000L) {
                t.remove(i);
            }
        }
        if (t.size() >= MAX_PER_HOUR) {
            return false;
        }
        return t.isEmpty() || now - t.get(t.size() - 1) >= MIN_GAP_MS;
    }

    private void reply(final long dialogId) {
        if (busy.contains(dialogId) || !allowed(dialogId)) {
            return;
        }
        busy.add(dialogId);
        final ArrayList<TrueGramAI.Msg> h = history.get(dialogId);
        final String system = TrueGramAI.getStyle() + "\n" + TrueGramAI.getAutoReplyExtra();
        TrueGramAI.chat(system, h, (text, error) -> {
            busy.remove(dialogId);
            if (error != null || text == null || text.isEmpty()) {
                TrueGramAI.setLastAutoReplyError(error != null ? error : "пустой ответ модели", dialogId);
                return;
            }
            TrueGramAI.clearLastAutoReplyError();
            h.add(new TrueGramAI.Msg("assistant", text));
            sentTimes.get(dialogId).add(System.currentTimeMillis());
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                    text, dialogId, null, null, null, false, null, null, null, true, 0, 0, null, false));
        });
    }
}
