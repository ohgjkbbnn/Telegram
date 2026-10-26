package org.telegram.ui;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** TrueGram banner server client. All calls run on the background queue; callbacks come from that thread. */
public final class TrueGramBannerApi {

    public static final String BASE = "http://2.26.124.20:8000";

    public interface Callback {
        void done(int code, String message);
    }

    public interface StartCallback {
        void done(int code, String session);
    }

    public interface TokenCallback {
        void done(int code, String token);
    }

    public interface PeerCallback {
        void done(File file, boolean video);
    }

    private TrueGramBannerApi() {
    }

    /** Asks the server to send a confirm button to this Telegram account in the bot. */
    public static void startAuth(final long userId, final StartCallback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                HttpURLConnection c = open(BASE + "/v1/auth/start?user_id=" + userId, "POST");
                c.setDoOutput(true);
                int code = c.getResponseCode();
                if (code != 200) {
                    cb.done(code, null);
                    return;
                }
                cb.done(200, new JSONObject(read(c.getInputStream())).optString("session", ""));
            } catch (Exception e) {
                FileLog.e(e);
                cb.done(-1, null);
            }
        });
    }

    /** Polls the session until the user confirms in the bot (about 3 minutes max). */
    public static void waitToken(final String session, final TokenCallback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                for (int i = 0; i < 90; i++) {
                    HttpURLConnection c = open(BASE + "/v1/auth/status?session=" + URLEncoder.encode(session, "UTF-8"), "GET");
                    int code = c.getResponseCode();
                    if (code != 200) {
                        cb.done(code, null);
                        return;
                    }
                    JSONObject json = new JSONObject(read(c.getInputStream()));
                    if ("ok".equals(json.optString("status"))) {
                        cb.done(200, json.optString("token", ""));
                        return;
                    }
                    Thread.sleep(2000);
                }
                cb.done(408, null);
            } catch (Exception e) {
                FileLog.e(e);
                cb.done(-1, null);
            }
        });
    }

    public static void upload(final long userId, final String token, final File file, final boolean video, final Callback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String boundary = "----TGBanner" + System.currentTimeMillis();
                HttpURLConnection c = open(BASE + "/v1/banner?user_id=" + userId, "POST");
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                c.setRequestProperty("X-Token", token);
                c.setDoOutput(true);
                c.setChunkedStreamingMode(64 * 1024);
                String mime = video ? "video/mp4" : "image/jpeg";
                try (OutputStream out = c.getOutputStream(); InputStream in = new java.io.FileInputStream(file)) {
                    out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"banner\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes("UTF-8"));
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
                }
                int code = c.getResponseCode();
                cb.done(code, read(code < 400 ? c.getInputStream() : c.getErrorStream()));
            } catch (Exception e) {
                FileLog.e(e);
                cb.done(-1, String.valueOf(e.getMessage()));
            }
        });
    }

    public static void delete(final long userId, final String token, final Callback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                HttpURLConnection c = open(BASE + "/v1/banner?user_id=" + userId, "DELETE");
                c.setRequestProperty("X-Token", token);
                int code = c.getResponseCode();
                cb.done(code, "");
            } catch (Exception e) {
                FileLog.e(e);
                cb.done(-1, "");
            }
        });
    }

    /** Loads another user's banner: checks the version, downloads it once into the cache. */
    public static void loadPeer(final long userId, final PeerCallback cb) {
        if (userId <= 0) {
            cb.done(null, false);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject info = new JSONObject(read(open(BASE + "/v1/banner/" + userId + "/info", "GET").getInputStream()));
                if (!info.optBoolean("exists", false)) {
                    cb.done(null, false);
                    return;
                }
                int version = info.optInt("version", 0);
                boolean video = info.optBoolean("is_video", false);
                File cache = new File(ApplicationLoader.applicationContext.getCacheDir(), "tg_banner_" + userId + "_" + version + (video ? ".mp4" : ".jpg"));
                if (!cache.exists() || cache.length() == 0) {
                    download(BASE + "/v1/banner/" + userId, cache);
                }
                cb.done(cache, video);
            } catch (Exception e) {
                FileLog.e(e);
                cb.done(null, false);
            }
        });
    }

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        return c;
    }

    private static void download(String url, File target) throws Exception {
        HttpURLConnection c = open(url, "GET");
        File tmp = new File(target.getParentFile(), target.getName() + ".part");
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        try (InputStream s = in) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = s.read(buf)) > 0) {
                bytes.write(buf, 0, n);
            }
            return bytes.toString("UTF-8");
        }
    }
}
