package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ui.ChatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TrueGram: TikTok link -> video (or photos for photo posts) on the device. No server of ours.
 * Self-contained. Only public pages are read.
 */
public class TrueGramTikTok {

    public interface Callback {
        void onDone(List<File> files, boolean isVideo, String caption);
        void onError(String reason);
    }

    private static final String PREFS = "truegram_tiktok";
    private static final String KEY_ENABLED = "enabled";
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final int MAX_PHOTOS = 10;
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final Pattern LINK = Pattern.compile(
            "https?://(?:www\\.|vm\\.|vt\\.|m\\.)?tiktok\\.com/[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern REHYDRATION = Pattern.compile(
            "<script[^>]*id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern SIGI = Pattern.compile(
            "<script[^>]*id=\"SIGI_STATE\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern PLAY_ADDR = Pattern.compile("\"playAddr\"\\s*:\\s*\"(https?:[^\"]+)\"");
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL);

    /** Result of parsing a page. */
    private static final class Found {
        boolean video;
        final List<String> urls = new ArrayList<>();
        String caption;
        String error;
    }

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

    public static String findUrl(String text) {
        if (text == null) {
            return null;
        }
        final Matcher m = LINK.matcher(text);
        return m.find() ? m.group() : null;
    }

    // ------------------------------------------------------------ download

    /** Background work; callback runs on the UI thread. */
    public static void download(String pageUrl, Callback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final Found found = findMedia(pageUrl);
                final List<File> files = new ArrayList<>();
                final File dir = ApplicationLoader.applicationContext.getCacheDir();
                final long stamp = System.currentTimeMillis();
                for (int i = 0; i < found.urls.size(); i++) {
                    final File out = new File(dir, "tiktok_" + stamp + "_" + i + (found.video ? ".mp4" : ".jpg"));
                    fetchTo(found.urls.get(i), out, found.video);
                    files.add(out);
                }
                final boolean isVideo = found.video;
                final String caption = found.caption;
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onDone(files, isVideo, caption);
                    }
                });
            } catch (Exception e) {
                final String reason = e.getMessage() != null ? e.getMessage() : e.toString();
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onError("TrueGram: " + reason);
                    }
                });
            }
        });
    }

    private static HttpURLConnection open(String url) throws IOException {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        return c;
    }

    private static String readAll(InputStream in, long limit) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream is = in) {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > limit) {
                    break;
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Loads the page and works out what it contains. */
    static Found findMedia(String pageUrl) throws IOException {
        final HttpURLConnection c = open(pageUrl);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
        final int code = c.getResponseCode();
        final String body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream(), 6L * 1024 * 1024);
        c.disconnect();
        if (code >= 400) {
            throw new IOException("TikTok ответил кодом " + code + ". Ссылка закрыта или сервис недоступен");
        }
        if (body.contains("captcha") || body.contains("verify-bar")) {
            throw new IOException("TikTok просит пройти проверку (капчу). Попробуйте позже");
        }
        Found f = parseRehydration(body);
        if (f == null || (f.urls.isEmpty() && f.error == null)) {
            final Found s = parseSigi(body);
            if (s != null) {
                f = s;
            }
        }
        if (f == null || f.urls.isEmpty()) {
            final Found r = parseRegex(body);
            if (r != null) {
                f = r;
            }
        }
        if (f == null || f.urls.isEmpty()) {
            final String reason = f != null && f.error != null ? f.error : "не найдены ни видео, ни фото";
            final Matcher t = TITLE.matcher(body);
            final String title = t.find() ? t.group(1).trim() : "без заголовка";
            throw new IOException("TikTok: " + reason + " (страница: " + title + ")");
        }
        return f;
    }

    private static void fetchTo(String url, File out, boolean isVideo) throws IOException {
        final HttpURLConnection c = open(url);
        c.setRequestProperty("Referer", "https://www.tiktok.com/");
        c.setRequestProperty("Accept", isVideo ? "video/mp4,video/*;q=0.9,*/*;q=0.5"
                : "image/avif,image/webp,image/*,*/*;q=0.5");
        final int code = c.getResponseCode();
        final String type = c.getContentType();
        if (code >= 400) {
            c.disconnect();
            throw new IOException("файл TikTok не открылся, код " + code);
        }
        long written = 0;
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            final byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                written += n;
                if (written > MAX_BYTES) {
                    throw new IOException("файл больше 200 МБ");
                }
                os.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
        if (out.length() < 2048) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw new IOException("скачался пустой файл (" + written + " байт, тип " + type + ")");
        }
        if (isVideo && !looksLikeMp4(out)) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            throw new IOException("по ссылке пришёл не видеофайл (тип " + type + "), TikTok закрыл прямой доступ");
        }
    }

    private static boolean looksLikeMp4(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            final byte[] head = new byte[12];
            if (in.read(head) < 12) {
                return false;
            }
            return head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ page parsing

    private static Found parseRehydration(String body) {
        try {
            final Matcher m = REHYDRATION.matcher(body);
            if (!m.find()) {
                return null;
            }
            final JSONObject root = new JSONObject(m.group(1).trim());
            final JSONObject scope = root.optJSONObject("__DEFAULT_SCOPE__");
            final JSONObject detail = scope == null ? null : scope.optJSONObject("webapp.video-detail");
            if (detail == null) {
                return null;
            }
            final JSONObject info = detail.optJSONObject("itemInfo");
            final Found f = fromStruct(info == null ? null : info.optJSONObject("itemStruct"));
            if (f != null) {
                return f;
            }
            final Found err = new Found();
            final String msg = detail.optString("statusMsg", "");
            final int code = detail.optInt("statusCode", 0);
            if (!msg.isEmpty() || code != 0) {
                err.error = msg.isEmpty() ? "код ответа " + code : msg;
            }
            return err;
        } catch (Exception e) {
            return null;
        }
    }

    private static Found parseSigi(String body) {
        try {
            final Matcher m = SIGI.matcher(body);
            if (!m.find()) {
                return null;
            }
            final JSONObject root = new JSONObject(m.group(1).trim());
            final JSONObject modules = root.optJSONObject("ItemModule");
            if (modules == null) {
                return null;
            }
            final Iterator<String> keys = modules.keys();
            while (keys.hasNext()) {
                final Found f = fromStruct(modules.optJSONObject(keys.next()));
                if (f != null) {
                    return f;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static Found parseRegex(String body) {
        final Matcher p = PLAY_ADDR.matcher(body);
        if (p.find()) {
            final Found f = new Found();
            f.video = true;
            f.urls.add(p.group(1).replace("\\u002F", "/").replace("\\/", "/"));
            return f;
        }
        return null;
    }

    /** Builds a result from one item: a photo post (imagePost) or a video. */
    private static Found fromStruct(JSONObject struct) {
        if (struct == null) {
            return null;
        }
        final Found f = new Found();
        f.caption = struct.optString("desc", null);
        final JSONObject imagePost = struct.optJSONObject("imagePost");
        if (imagePost != null) {
            final JSONArray images = imagePost.optJSONArray("images");
            if (images != null) {
                for (int i = 0; i < images.length() && f.urls.size() < MAX_PHOTOS; i++) {
                    final JSONObject image = images.optJSONObject(i);
                    final JSONObject imageUrl = image == null ? null : image.optJSONObject("imageURL");
                    final JSONArray list = imageUrl == null ? null : imageUrl.optJSONArray("urlList");
                    final String url = list == null ? null : firstHttp(list.optString(0, null));
                    if (url != null) {
                        f.urls.add(url);
                    }
                }
            }
            f.video = false;
            return f.urls.isEmpty() ? null : f;
        }
        final JSONObject video = struct.optJSONObject("video");
        if (video != null) {
            String url = firstHttp(video.optString("playAddr", null), video.optString("downloadAddr", null));
            if (url == null) {
                final JSONArray bitrates = video.optJSONArray("bitrateInfo");
                final JSONObject first = bitrates == null ? null : bitrates.optJSONObject(0);
                final JSONObject play = first == null ? null : first.optJSONObject("PlayAddr");
                final JSONArray list = play == null ? null : play.optJSONArray("UrlList");
                url = list == null ? null : firstHttp(list.optString(0, null));
            }
            if (url != null) {
                f.video = true;
                f.urls.add(url);
                return f;
            }
        }
        return null;
    }

    private static String firstHttp(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String c : candidates) {
            if (c != null && c.startsWith("http")) {
                return c.replace("\\u002F", "/").replace("\\/", "/");
            }
        }
        return null;
    }

    // ------------------------------------------------------------ hook for SendMessagesHelper

    /**
     * Called from SendMessagesHelper.sendMessage. Returns true when the text was taken over:
     * the link is replaced by the video (or photos) sent as normal media messages.
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
            public void onDone(List<File> files, boolean isVideo, String caption) {
                final String cap = typed.isEmpty() ? caption : typed;
                for (int i = 0; i < files.size(); i++) {
                    final String path = files.get(i).getAbsolutePath();
                    final CharSequence c = i == 0 ? cap : null;
                    if (isVideo) {
                        SendMessagesHelper.prepareSendingVideo(AccountInstance.getInstance(account), path,
                                null, null, null, p.peer, p.replyToMsg, p.replyToTopMsg, null, p.replyQuote,
                                null, 0, null, p.notify, p.scheduleDate, p.scheduleRepeatPeriod,
                                false, false, c, p.sendMessageChatArguments, 0, p.stars);
                    } else {
                        SendMessagesHelper.prepareSendingPhoto(AccountInstance.getInstance(account), path,
                                null, p.peer, p.replyToMsg, p.replyToTopMsg, p.replyQuote, c,
                                null, null, null, 0, null, p.notify, p.scheduleDate, 0,
                                p.sendMessageChatArguments);
                    }
                }
            }

            @Override
            public void onError(String reason) {
                Toast.makeText(ApplicationLoader.applicationContext, reason, Toast.LENGTH_LONG).show();
            }
        });
        return true;
    }
}
