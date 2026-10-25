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

/** TrueGram banner server client. All calls run on the background queue; callbacks come from that thread. */
public final class TrueGramBannerApi {

    public static final String BASE = "http://2.26.124.20:8000";

    public interface Callback {
        void done(int code, String message);
    }

    public interface RegisterCallback {
        void done(int code, String token);
    }

    public interface PeerCallback {
        void done(File file, boolean video);
    }

    private TrueGramBannerApi() {
    }

    public static void register(final long userId, final RegisterCallback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                HttpURLConnection c = open(BASE + "/v1/register", "POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(("{\"user_id\":" + userId + "}").getBytes("UTF-8"));
                }
                int code = c.getResponseCode();
                String body = read(code < 400 ? c.getInputStream() : c.getErrorStream());
                String token = code == 200 ? new JSONObject(body).optString("token", "") : "";
                cb.done(code, token);
            } catch (Exception e) {
                FileLog.e(e);
                cb.done(-1, "");
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
