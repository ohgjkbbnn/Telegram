package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TrueGram: synced song lyrics (LRC) fetched from LRCLIB and shown under the audio player bar.
 * Network and parsing run on a background thread, results are cached in memory (small LRU).
 */
public class TrueGramLyrics {

    public static class Line {
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public interface Callback {
        /** Called on the UI thread. lines == null (or empty) if nothing was found. */
        void onResult(String key, List<Line> lines);
    }

    private static final String USER_AGENT = "TrueGram/13.0.2 (https://github.com/ohgjkbbnn/Telegram)";
    private static final int CACHE_SIZE = 30;

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Object lock = new Object();
    private static volatile int requestToken = 0;

    private static final Map<String, List<Line>> cache = new LinkedHashMap<String, List<Line>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Line>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    // ---------------- settings (stored in TrueGramConfig, edited in the TrueGram settings screen) ----------------

    public static boolean isEnabled() {
        return TrueGramConfig.isLyricsEnabled();
    }

    /** Color of already sung lines; 0 means "use the theme color". */
    public static int getSungColor() {
        return TrueGramConfig.getLyricsColor();
    }

    // ---------------- loading ----------------

    public static String makeKey(String artist, String title, int durationSec) {
        return (artist == null ? "" : artist.trim().toLowerCase()) + "|" + (title == null ? "" : title.trim().toLowerCase()) + "|" + durationSec;
    }

    public static void cancel() {
        requestToken++;
    }

    public static void load(final String rawArtist, final String rawTitle, final int durationSec, final Callback callback) {
        final int token = ++requestToken;
        String[] at = normalize(rawArtist, rawTitle);
        final String artist = at[0];
        final String title = at[1];
        if (title.isEmpty()) {
            deliver(token, null, null, callback);
            return;
        }
        final String key = makeKey(artist, title, durationSec);
        List<Line> cached;
        synchronized (lock) {
            cached = cache.get(key);
        }
        if (cached != null) {
            deliver(token, key, cached, callback);
            return;
        }
        executor.execute(() -> {
            List<Line> result = null;
            try {
                result = fetch(artist, title, durationSec, token);
            } catch (Throwable ignore) {
            }
            if (result == null) {
                result = Collections.emptyList();
            }
            synchronized (lock) {
                cache.put(key, result);
            }
            deliver(token, key, result, callback);
        });
    }

    private static void deliver(final int token, final String key, final List<Line> lines, final Callback callback) {
        AndroidUtilities.runOnUIThread(() -> {
            if (token != requestToken) {
                return; // a newer track was requested, drop the stale answer
            }
            callback.onResult(key, lines);
        });
    }

    private static List<Line> fetch(String artist, String title, int durationSec, int token) throws Exception {
        String base = "https://lrclib.net/api/";
        String q = "track_name=" + enc(title);
        if (!artist.isEmpty()) {
            q += "&artist_name=" + enc(artist);
        }
        // 1) exact match by artist + title + duration
        if (durationSec > 0 && token == requestToken) {
            String body = httpGet(base + "get?" + q + "&duration=" + durationSec);
            if (body != null) {
                List<Line> r = parseLrc(new JSONObject(body).optString("syncedLyrics", ""));
                if (!r.isEmpty()) {
                    return r;
                }
            }
        }
        // 2) fallback: search, take the best result by duration
        if (token != requestToken) {
            return null;
        }
        String body = httpGet(base + "search?" + q);
        if (body == null) {
            return null;
        }
        JSONArray arr = new JSONArray(body);
        List<Line> best = null;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String synced = o.optString("syncedLyrics", "");
            if (synced.isEmpty() || "null".equals(synced)) {
                continue;
            }
            double diff = durationSec > 0 ? Math.abs(o.optDouble("duration", durationSec) - durationSec) : 0;
            if (durationSec > 0 && diff > 8) {
                continue;
            }
            if (diff < bestDiff) {
                List<Line> r = parseLrc(synced);
                if (!r.isEmpty()) {
                    best = r;
                    bestDiff = diff;
                }
            }
        }
        return best;
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) {
                return null;
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 2 * 1024 * 1024) {
                    return null; // sanity limit
                }
            }
            in.close();
            return out.toString("UTF-8");
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    // ---------------- helpers ----------------

    /** Cleans tags / file names a bit so the search has a chance to match. */
    static String[] normalize(String artist, String title) {
        artist = artist == null ? "" : artist.trim();
        title = title == null ? "" : title.trim();
        title = title.replaceAll("(?i)\\.(mp3|m4a|flac|ogg|opus|wav|aac)$", "").replace('_', ' ').trim();
        if (artist.isEmpty() || artist.equalsIgnoreCase("unknown") || artist.equalsIgnoreCase("<unknown>")) {
            artist = "";
            int idx = title.indexOf(" - ");
            if (idx > 0) {
                artist = title.substring(0, idx).trim();
                title = title.substring(idx + 3).trim();
            }
        }
        String junk = "(?i)\\s*[\\(\\[][^\\)\\]]*(official|video|audio|lyric|clip|remaster|visuali[sz]er)[^\\)\\]]*[\\)\\]]";
        title = title.replaceAll(junk, "").trim();
        return new String[]{artist, title};
    }

    /** Parses LRC text ("[mm:ss.xx]line", several stamps per line allowed). */
    static List<Line> parseLrc(String lrc) {
        ArrayList<Line> out = new ArrayList<>();
        if (lrc == null || lrc.isEmpty()) {
            return out;
        }
        for (String raw : lrc.split("\n")) {
            String s = raw.trim();
            ArrayList<Long> stamps = new ArrayList<>();
            int pos = 0;
            while (pos < s.length() && s.charAt(pos) == '[') {
                int end = s.indexOf(']', pos);
                if (end < 0) {
                    break;
                }
                long t = parseStamp(s.substring(pos + 1, end));
                if (t < 0) {
                    break; // not a time tag (e.g. [ar:...]) -> ignore whole line
                }
                stamps.add(t);
                pos = end + 1;
            }
            if (stamps.isEmpty()) {
                continue;
            }
            String text = s.substring(pos).trim();
            if (text.isEmpty()) {
                text = "\u266A";
            }
            for (long t : stamps) {
                out.add(new Line(t, text));
            }
        }
        Collections.sort(out, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return out;
    }

    private static long parseStamp(String s) {
        try {
            int colon = s.indexOf(':');
            if (colon <= 0) {
                return -1;
            }
            long min = Long.parseLong(s.substring(0, colon));
            String rest = s.substring(colon + 1);
            long sec;
            long ms = 0;
            int dot = rest.indexOf('.');
            if (dot < 0) {
                dot = rest.indexOf(':');
            }
            if (dot >= 0) {
                sec = Long.parseLong(rest.substring(0, dot));
                String frac = rest.substring(dot + 1);
                if (frac.length() > 3) {
                    frac = frac.substring(0, 3);
                }
                while (frac.length() < 3) {
                    frac += "0";
                }
                ms = Long.parseLong(frac);
            } else {
                sec = Long.parseLong(rest);
            }
            return ((min * 60) + sec) * 1000 + ms;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Approximate the current spoken word by spreading the line's duration evenly
     * across its whitespace-separated words. LRC only provides line timestamps, so
     * this is an estimate rather than word-level synchronization.
     */
    public static int findCurrentWord(List<Line> lines, int lineIndex, long positionMs) {
        if (lines == null || lines.isEmpty() || lineIndex < 0 || lineIndex >= lines.size()) {
            return -1;
        }
        Line line = lines.get(lineIndex);
        String text = line.text == null ? "" : line.text.trim();
        if (text.isEmpty()) {
            return -1;
        }
        String[] words = text.split("\\s+");
        int wordCount = words.length;
        long startMs = line.timeMs;
        long endMs;
        if (lineIndex + 1 < lines.size()) {
            endMs = lines.get(lineIndex + 1).timeMs;
        } else {
            // No following LRC timestamp: use a modest estimate for the final line.
            endMs = startMs + Math.max(1500L, wordCount * 350L);
        }
        if (endMs <= startMs) {
            endMs = startMs + Math.max(1500L, wordCount * 350L);
        }
        double progress = (double) (positionMs - startMs) / (double) (endMs - startMs);
        progress = Math.max(0.0, Math.min(0.999999, progress));
        return Math.min(wordCount - 1, (int) (progress * wordCount));
    }

    /** Index of the last line whose time <= positionMs, or -1 before the first line. */
    public static int findCurrent(List<Line> lines, long positionMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
