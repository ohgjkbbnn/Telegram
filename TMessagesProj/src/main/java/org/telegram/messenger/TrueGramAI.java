package org.telegram.messenger;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TrueGram AI core: providers (OpenAI-compatible APIs), keys, models, chat requests, auto-reply settings.
 * OpenAI, OpenRouter, DeepSeek, Groq, Mistral and any custom OpenAI-compatible endpoint.
 */
public class TrueGramAI {

    public static class Provider {
        public final String id, name, baseUrl, preferredModel;

        Provider(String id, String name, String baseUrl, String preferredModel) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
            this.preferredModel = preferredModel;
        }
    }

    public static final Provider[] PROVIDERS = {
            new Provider("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            new Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
            new Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            new Provider("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            new Provider("mistral", "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest"),
            new Provider("custom", "Свой API (OpenAI-совместимый)", "", ""),
    };

    public static class Msg {
        public final String role;
        public String text;
        public final ArrayList<String> images = new ArrayList<>();

        public Msg(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    public interface Callback<T> {
        void done(T result, String error);
    }

    public static final String DEFAULT_STYLE = "Отвечай кратко и по делу, на языке собеседника.";
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("truegram_ai", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ token usage

    /** Cumulative tokens used for this provider since the key was added (sum of all requests' "usage"). */
    public static long getUsedTokens(String providerId) {
        return prefs().getLong("usage_" + providerId, 0L);
    }

    public static void resetUsedTokens(String providerId) {
        prefs().edit().remove("usage_" + providerId).apply();
    }

    private static void addUsedTokens(String providerId, long tokens) {
        if (tokens <= 0) {
            return;
        }
        prefs().edit().putLong("usage_" + providerId, getUsedTokens(providerId) + tokens).apply();
    }

    // ------------------------------------------------------------ settings

    public static Provider byId(String id) {
        for (Provider p : PROVIDERS) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return PROVIDERS[0];
    }

    public static String getKey(String pid) {
        return prefs().getString("key_" + pid, "");
    }

    public static void setKey(String pid, String key) {
        prefs().edit().putString("key_" + pid, key == null ? "" : key.trim()).apply();
    }

    public static boolean isConnected(String pid) {
        return !getKey(pid).isEmpty();
    }

    public static String getBaseUrl(Provider p) {
        final String custom = prefs().getString("base_" + p.id, "");
        String url = custom.isEmpty() ? p.baseUrl : custom;
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static void setBaseUrl(String pid, String url) {
        prefs().edit().putString("base_" + pid, url == null ? "" : url.trim()).apply();
    }

    public static String getActiveProvider() {
        final String id = prefs().getString("active", "");
        if (!id.isEmpty() && isConnected(id)) {
            return id;
        }
        for (Provider p : PROVIDERS) {
            if (isConnected(p.id)) {
                return p.id;
            }
        }
        return "";
    }

    public static void setActiveProvider(String pid) {
        prefs().edit().putString("active", pid).apply();
    }

    public static String getModel(String pid) {
        return prefs().getString("model_" + pid, "");
    }

    public static void setModel(String pid, String model) {
        prefs().edit().putString("model_" + pid, model).apply();
    }

    public static ArrayList<String> getModels(String pid) {
        final ArrayList<String> out = new ArrayList<>();
        try {
            final JSONArray a = new JSONArray(prefs().getString("models_" + pid, "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(a.getString(i));
            }
        } catch (Exception ignore) {
        }
        return out;
    }

    public static void saveModels(String pid, List<String> models) {
        prefs().edit().putString("models_" + pid, new JSONArray(models).toString()).apply();
    }

    public static boolean isConfigured() {
        final String pid = getActiveProvider();
        return !pid.isEmpty() && !getModel(pid).isEmpty();
    }

    public static String getStyle() {
        return prefs().getString("style", DEFAULT_STYLE);
    }

    public static void setStyle(String s) {
        prefs().edit().putString("style", s == null ? "" : s.trim()).apply();
    }

    public static String getAutoReplyExtra() {
        return prefs().getString("ar_extra", "Ты отвечаешь вместо владельца аккаунта в мессенджере. Пиши коротко и естественно, как живой человек.");
    }

    public static void setAutoReplyExtra(String s) {
        prefs().edit().putString("ar_extra", s == null ? "" : s.trim()).apply();
    }

    // auto-reply chats (dialog ids)
    public static Set<Long> getAutoReplyDialogs() {
        final Set<Long> out = new HashSet<>();
        for (String s : prefs().getStringSet("ar_dialogs", new HashSet<>())) {
            try {
                out.add(Long.parseLong(s));
            } catch (Exception ignore) {
            }
        }
        return out;
    }

    public static boolean isAutoReply(long dialogId) {
        return getAutoReplyDialogs().contains(dialogId);
    }

    /** Returns the new state. */
    public static boolean toggleAutoReply(long dialogId) {
        final Set<String> set = new HashSet<>(prefs().getStringSet("ar_dialogs", new HashSet<>()));
        final String id = String.valueOf(dialogId);
        final boolean on = !set.contains(id);
        if (on) {
            set.add(id);
        } else {
            set.remove(id);
        }
        prefs().edit().putStringSet("ar_dialogs", set).apply();
        return on;
    }

    public static void removeAutoReply(long dialogId) {
        final Set<String> set = new HashSet<>(prefs().getStringSet("ar_dialogs", new HashSet<>()));
        set.remove(String.valueOf(dialogId));
        prefs().edit().putStringSet("ar_dialogs", set).apply();
    }

    // saved AI chat (text only)
    public static ArrayList<Msg> loadChat() {
        final ArrayList<Msg> out = new ArrayList<>();
        try {
            final JSONArray a = new JSONArray(prefs().getString("chat", "[]"));
            for (int i = 0; i < a.length(); i++) {
                final JSONObject o = a.getJSONObject(i);
                out.add(new Msg(o.getString("r"), o.getString("t")));
            }
        } catch (Exception ignore) {
        }
        return out;
    }

    public static void saveChat(List<Msg> msgs) {
        try {
            final JSONArray a = new JSONArray();
            final int from = Math.max(0, msgs.size() - 60);
            for (int i = from; i < msgs.size(); i++) {
                final Msg m = msgs.get(i);
                if (m.text == null || m.text.equals("\u2026")) {
                    continue;
                }
                a.put(new JSONObject().put("r", m.role).put("t", m.text));
            }
            prefs().edit().putString("chat", a.toString()).apply();
        } catch (Exception ignore) {
        }
    }

    public static String pickModel(Provider p, List<String> models) {
        if (models.isEmpty()) {
            return "";
        }
        if (models.contains(p.preferredModel)) {
            return p.preferredModel;
        }
        for (String hint : new String[]{"mini", "chat", "small", "instruct", "flash"}) {
            for (String m : models) {
                if (m.toLowerCase().contains(hint)) {
                    return m;
                }
            }
        }
        return models.get(0);
    }

    // ------------------------------------------------------------ network

    private static void ui(Runnable r) {
        AndroidUtilities.runOnUIThread(r);
    }

    private static String readAll(InputStream in) throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toString("UTF-8");
    }

    private static String shortError(String body) {
        try {
            final JSONObject o = new JSONObject(body);
            final Object e = o.opt("error");
            if (e instanceof JSONObject) {
                return ((JSONObject) e).optString("message", body);
            }
            if (e != null) {
                return String.valueOf(e);
            }
            return o.optString("message", body);
        } catch (Exception ignore) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
    }

    private static String request(String method, String url, String key, String body) throws Exception {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Accept", "application/json");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        final int code = c.getResponseCode();
        final InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        final String text = in == null ? "" : readAll(in);
        if (code >= 400) {
            throw new Exception(code + ": " + shortError(text));
        }
        return text;
    }

    private static boolean isChatModel(String id) {
        final String s = id.toLowerCase();
        for (String bad : new String[]{"embed", "whisper", "tts", "dall-e", "moderation", "guard", "rerank", "transcribe", "image", "audio", "realtime"}) {
            if (s.contains(bad)) {
                return false;
            }
        }
        return true;
    }

    /** Loads the model list; also validates the key (401 = bad key). */
    public static void fetchModels(final Provider p, final String key, final String baseUrl, final Callback<ArrayList<String>> cb) {
        EXEC.execute(() -> {
            try {
                if (baseUrl == null || baseUrl.isEmpty()) {
                    throw new Exception("не указан адрес API");
                }
                final String text = request("GET", baseUrl + "/models", key, null);
                final JSONObject root = new JSONObject(text);
                final JSONArray data = root.optJSONArray("data");
                if (data == null) {
                    throw new Exception("неожиданный ответ сервера");
                }
                final ArrayList<String> models = new ArrayList<>();
                for (int i = 0; i < data.length(); i++) {
                    final String id = data.getJSONObject(i).optString("id", "");
                    if (!id.isEmpty() && isChatModel(id)) {
                        models.add(id);
                    }
                }
                Collections.sort(models);
                ui(() -> cb.done(models, null));
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                ui(() -> cb.done(null, msg));
            }
        });
    }

    /** Chat request with the active provider/model. Images go only with the last message that has them. */
    public static void chat(String system, List<Msg> history, Callback<String> cb) {
        final String pid = getActiveProvider();
        if (pid.isEmpty() || getModel(pid).isEmpty()) {
            cb.done(null, "ИИ не настроен: добавьте API-ключ");
            return;
        }
        final Provider p = byId(pid);
        final String key = getKey(pid);
        final String model = getModel(pid);
        final String base = getBaseUrl(p);
        final ArrayList<Msg> copy = new ArrayList<>(history);
        EXEC.execute(() -> {
            try {
                final JSONArray messages = new JSONArray();
                if (system != null && !system.trim().isEmpty()) {
                    messages.put(new JSONObject().put("role", "system").put("content", system));
                }
                int lastWithImages = -1;
                for (int i = copy.size() - 1; i >= 0; i--) {
                    if (!copy.get(i).images.isEmpty()) {
                        lastWithImages = i;
                        break;
                    }
                }
                for (int i = 0; i < copy.size(); i++) {
                    final Msg m = copy.get(i);
                    if (m.text == null || m.text.equals("\u2026")) {
                        continue;
                    }
                    final JSONObject o = new JSONObject().put("role", m.role);
                    if (i == lastWithImages) {
                        final JSONArray parts = new JSONArray();
                        parts.put(new JSONObject().put("type", "text").put("text", m.text));
                        for (String url : m.images) {
                            parts.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", url)));
                        }
                        o.put("content", parts);
                    } else {
                        o.put("content", m.text);
                    }
                    messages.put(o);
                }
                final String body = new JSONObject().put("model", model).put("messages", messages).toString();
                final String text = request("POST", base + "/chat/completions", key, body);
                final JSONObject root = new JSONObject(text);
                final JSONObject usage = root.optJSONObject("usage");
                if (usage != null) {
                    addUsedTokens(pid, usage.optLong("total_tokens", 0L));
                }
                final JSONObject choice = root.getJSONArray("choices").getJSONObject(0);
                final Object content = choice.getJSONObject("message").opt("content");
                final StringBuilder sb = new StringBuilder();
                if (content instanceof JSONArray) {
                    final JSONArray a = (JSONArray) content;
                    for (int i = 0; i < a.length(); i++) {
                        sb.append(a.getJSONObject(i).optString("text", ""));
                    }
                } else if (content != null) {
                    sb.append(content);
                }
                final String reply = sb.toString().trim();
                ui(() -> cb.done(reply, reply.isEmpty() ? "пустой ответ модели" : null));
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                ui(() -> cb.done(null, msg));
            }
        });
    }

    // ------------------------------------------------------------ media helpers

    private static String bitmapToDataUrl(Bitmap bmp, int maxSide) {
        final int w = bmp.getWidth(), h = bmp.getHeight();
        final float k = Math.min(1f, (float) maxSide / Math.max(w, h));
        final Bitmap scaled = k < 1f ? Bitmap.createScaledBitmap(bmp, Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), true) : bmp;
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, bos);
        return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    public static String imageToDataUrl(Context ctx, Uri uri) throws Exception {
        final ContentResolver cr = ctx.getContentResolver();
        final BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 2600) {
            sample *= 2;
        }
        final BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        try (InputStream in = cr.openInputStream(uri)) {
            final Bitmap bmp = BitmapFactory.decodeStream(in, null, o2);
            if (bmp == null) {
                throw new Exception("не удалось открыть изображение");
            }
            return bitmapToDataUrl(bmp, 1280);
        }
    }

    /** Models cannot take video directly, so a few evenly spaced frames are sent as images. */
    public static ArrayList<String> videoFrames(Context ctx, Uri uri, int count) throws Exception {
        final MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, uri);
            final long durMs = Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            final ArrayList<String> out = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final long t = (long) (durMs * 1000L * (i + 0.5) / count);
                final Bitmap f = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (f != null) {
                    out.add(bitmapToDataUrl(f, 1024));
                }
            }
            if (out.isEmpty()) {
                throw new Exception("не удалось достать кадры из видео");
            }
            return out;
        } finally {
            r.release();
        }
    }
}
