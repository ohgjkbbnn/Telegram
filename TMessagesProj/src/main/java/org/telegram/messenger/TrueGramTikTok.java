package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TrueGram: turns a TikTok link into a video file on the device, without any server of ours.
 * Self-contained: settings, link detection, download. Only public pages are read.
 *
 * Usage:
 *   if (TrueGramTikTok.isEnabled()) {
 *       String link = TrueGramTikTok.findUrl(text);
 *       if (link != null) TrueGramTikTok.download(link, callback);
 *   }
 */
public class TrueGramTikTok {

    public interface Callback {
        void onDone(File video, String caption);
        void onError(String reason);
    }

    private static final String PREFS = "truegram_tiktok";
    private static final String KEY_ENABLED = "enabled";
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final Pattern LINK = Pattern.compile(
            "https?://(?:www\\.|vm\\.|vt\\.|m\\.)?tiktok\\.com/[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern REHYDRATION = Pattern.compile(
            "<script[^>]*id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern SIGI = Pattern.compile(
            "<script[^>]*id=\"SIGI_STATE\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern PLAY_ADDR = Pattern.compile("\"playAddr\"\\s*:\\s*\"(https?:[^\"]+)\"");

    // ------------------------------------------------------------ settings

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    // ------------------------------------------------------------ link detection

    /** Returns the first TikTok link in the text, or null. */
    public static String findUrl(String text) {
        if (text == null) {
            return null;
        }
        final Matcher m = LINK.matcher(text);
        return m.find() ? m.group() : null;
    }

    // ------------------------------------------------------------ download

    /** Runs on a background thread; the callback is called on the UI thread. */
    public static void download(String pageUrl, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final String page = fetchText(pageUrl);
                final String videoUrl = extractVideoUrl(page);
                if (videoUrl == null) {
                    fail(callback, "Не удалось найти видео по ссылке. Возможно, TikTok изменил страницу или видео закрытое.");
                    return;
                }
                final String caption = extractCaption(page);
                final File out = new File(ApplicationLoader.applicationContext.getCacheDir(),
                        "tiktok_" + System.currentTimeMillis() + ".mp4");
                downloadTo(videoUrl, out);
                if (out.length() < 1024) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    fail(callback, "Видео скачалось пустым. TikTok мог закрыть прямой доступ к файлу.");
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onDone(out, caption);
                    }
                });
            } catch (Exception e) {
                fail(callback, "Ошибка загрузки: " + e.getMessage());
            }
        });
    }

    private static void fail(Callback callback, String reason) {
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onError(reason);
            }
        });
    }

    private static HttpURLConnection open(String url) throws Exception {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        return c;
    }

    private static String fetchText(String url) throws Exception {
        final HttpURLConnection c = open(url);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        try (InputStream in = c.getInputStream()) {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 5 * 1024 * 1024) {
                    break;
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static void downloadTo(String videoUrl, File out) throws Exception {
        final HttpURLConnection c = open(videoUrl);
        c.setRequestProperty("Referer", "https://www.tiktok.com/");
        c.setRequestProperty("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8");
        long written = 0;
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            final byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                written += n;
                if (written > MAX_BYTES) {
                    throw new IllegalStateException("файл больше 200 МБ");
                }
                os.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------ page parsing

    /** Finds the direct video address in the page data. Tries the known layouts, then a plain search. */
    static String extractVideoUrl(String page) {
        if (page == null || page.isEmpty()) {
            return null;
        }
        try {
            final Matcher r = REHYDRATION.matcher(page);
            if (r.find()) {
                final JSONObject root = new JSONObject(r.group(1).trim());
                final JSONObject scope = root.optJSONObject("__DEFAULT_SCOPE__");
                if (scope != null) {
                    final JSONObject detail = scope.optJSONObject("webapp.video-detail");
                    if (detail != null) {
                        final JSONObject item = detail.optJSONObject("itemInfo");
                        final String url = videoFrom(item == null ? null : item.optJSONObject("itemStruct"));
                        if (url != null) {
                            return url;
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        }
        try {
            final Matcher s = SIGI.matcher(page);
            if (s.find()) {
                final JSONObject root = new JSONObject(s.group(1).trim());
                final JSONObject modules = root.optJSONObject("ItemModule");
                if (modules != null) {
                    final Iterator<String> keys = modules.keys();
                    while (keys.hasNext()) {
                        final String url = videoFrom(modules.optJSONObject(keys.next()));
                        if (url != null) {
                            return url;
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        }
        final Matcher p = PLAY_ADDR.matcher(page);
        if (p.find()) {
            return p.group(1).replace("\\u002F", "/").replace("\\/", "/");
        }
        return null;
    }

    private static String videoFrom(JSONObject item) {
        if (item == null) {
            return null;
        }
        final JSONObject video = item.optJSONObject("video");
        if (video == null) {
            return null;
        }
        final String play = video.optString("playAddr", "");
        if (play.startsWith("http")) {
            return play;
        }
        final String download = video.optString("downloadAddr", "");
        return download.startsWith("http") ? download : null;
    }

    static String extractCaption(String page) {
        try {
            final Matcher r = REHYDRATION.matcher(page);
            if (r.find()) {
                final JSONObject root = new JSONObject(r.group(1).trim());
                final JSONObject detail = root.optJSONObject("__DEFAULT_SCOPE__") == null ? null
                        : root.optJSONObject("__DEFAULT_SCOPE__").optJSONObject("webapp.video-detail");
                final JSONObject item = detail == null ? null : detail.optJSONObject("itemInfo");
                final JSONObject struct = item == null ? null : item.optJSONObject("itemStruct");
                return struct == null ? null : struct.optString("desc", null);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * Called from SendMessagesHelper.sendMessage. Returns true when the text was taken over
     * (a TikTok link is replaced by a downloaded video, sent as a normal video message).
     */
    public static boolean intercept(int account, SendMessagesHelper.SendMessageParams p) {
        if (!isEnabled() || p == null || p.path != null || p.message == null) {
            return false;
        }
        final String link = findUrl(p.message);
        if (link == null) {
            return false;
        }
        final String typed = p.message.replace(link, "").trim();
        download(link, new Callback() {
            @Override
            public void onDone(File video, String caption) {
                final String cap = typed.isEmpty() ? caption : typed;
                SendMessagesHelper.prepareSendingVideo(AccountInstance.getInstance(account), video.getAbsolutePath(),
                        null, null, null, p.peer, p.replyToMsg, p.replyToTopMsg, null, p.replyQuote, p.entities, 0, null,
                        p.notify, p.scheduleDate, p.scheduleRepeatPeriod, false, false, cap, p.sendMessageChatArguments, 0, p.stars);
            }

            @Override
            public void onError(String reason) {
                Toast.makeText(ApplicationLoader.applicationContext, reason, Toast.LENGTH_LONG).show();
            }
        });
        return true;
    }
}
