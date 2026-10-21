package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.ui.ActionBar.AlertDialog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TrueGram voice changer for recorded voice messages. Fully self-contained: settings, DSP and the picker dialog live here.
 *
 * Hook (one place): after AudioRecord.read(...) in the voice-message recorder call
 *     TrueGramVoice.process(buffer, bytesRead, sampleRate);
 * and call TrueGramVoice.onRecordingStart() when a new recording begins.
 */
public class TrueGramVoice {

    private static final String PREFS = "truegram_voice";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_SEMITONES = "semitones";

    public static final int PRESET_OFF = 0;
    public static final int PRESET_CUSTOM = 10;

    public static final String[] NAMES = {
            "Выкл",
            "Женский",
            "Мужской",
            "Бурундук",
            "Низкий",
            "Ребёнок",
            "Робот",
            "Демон",
            "Эхо",
            "Пещера",
            "Свой тон"
    };

    private static final String[] SUBTITLES = {
            "Без обработки",
            "Выше тон",
            "Ниже тон",
            "Мультяшный высокий голос",
            "Низкий голос",
            "Детский голос",
            "Металлический эффект",
            "Глубокий и жуткий",
            "Эхо",
            "Гулкий зал",
            "Сдвиг тона от -12 до +12 полутонов"
    };

    // pitch ratio, ring Hz, ring depth, drive, echo ms, echo feedback, echo mix, reverb
    private static final float[][] PARAMS = {
            {1.00f, 0, 0, 0, 0, 0, 0, 0},
            {1.28f, 0, 0, 0, 0, 0, 0, 0},
            {0.82f, 0, 0, 0, 0, 0, 0, 0},
            {1.50f, 0, 0, 0, 0, 0, 0, 0},
            {0.68f, 0, 0, 0.10f, 0, 0, 0, 0},
            {1.38f, 0, 0, 0, 0, 0, 0, 0},
            {1.00f, 55, 0.75f, 0.20f, 0, 0, 0, 0},
            {0.58f, 0, 0, 0.30f, 0, 0, 0, 0.30f},
            {1.00f, 0, 0, 0, 220, 0.40f, 0.35f, 0},
            {0.92f, 0, 0, 0, 120, 0.30f, 0.25f, 0.55f},
            {1.00f, 0, 0, 0, 0, 0, 0, 0}
    };

    private static volatile SharedPreferences prefs;

    private static SharedPreferences prefs() {
        SharedPreferences p = prefs;
        if (p == null) {
            synchronized (TrueGramVoice.class) {
                p = prefs;
                if (p == null) {
                    p = prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                }
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ settings

    public static int getPreset() {
        final int p = prefs().getInt(KEY_PRESET, PRESET_OFF);
        return p >= 0 && p < NAMES.length ? p : PRESET_OFF;
    }

    public static void setPreset(int preset) {
        prefs().edit().putInt(KEY_PRESET, preset).apply();
    }

    public static int getSemitones() {
        return Math.max(-12, Math.min(12, prefs().getInt(KEY_SEMITONES, 0)));
    }

    public static void setSemitones(int semitones) {
        prefs().edit().putInt(KEY_SEMITONES, Math.max(-12, Math.min(12, semitones))).apply();
    }

    public static boolean isEnabled() {
        return getPreset() != PRESET_OFF;
    }

    public static String currentName() {
        final int p = getPreset();
        if (p == PRESET_CUSTOM) {
            final int st = getSemitones();
            return "Свой тон (" + (st > 0 ? "+" : "") + st + ")";
        }
        return NAMES[p];
    }

    // ------------------------------------------------------------------ picker dialog

    public static void showPicker(Context context, Runnable onChanged) {
        final String[] items = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            items[i] = (i == getPreset() ? "✓ " : "") + NAMES[i] + " — " + SUBTITLES[i];
        }
        new AlertDialog.Builder(context)
                .setTitle("Изменение голоса")
                .setItems(items, (dialog, which) -> {
                    setPreset(which);
                    if (which == PRESET_CUSTOM) {
                        showSemitonesPicker(context, onChanged);
                    } else if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static void showSemitonesPicker(Context context, Runnable onChanged) {
        final String[] items = new String[25];
        for (int i = 0; i < items.length; i++) {
            final int st = i - 12;
            items[i] = (st > 0 ? "+" : "") + st + " полутонов" + (st == 0 ? " (без сдвига)" : "");
        }
        new AlertDialog.Builder(context)
                .setTitle("Сдвиг тона")
                .setItems(items, (dialog, which) -> {
                    setSemitones(which - 12);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton("Отмена", (d, w) -> {
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ recording hook

    private static Engine engine;
    private static boolean failed;

    /** Call when a new voice recording starts. */
    public static synchronized void onRecordingStart() {
        engine = null;
        failed = false;
    }

    /**
     * Processes 16-bit little-endian mono PCM in place.
     * @param buffer     buffer filled by AudioRecord.read
     * @param byteCount  number of valid bytes starting at index 0
     */
    public static synchronized void process(ByteBuffer buffer, int byteCount, int sampleRate) {
        if (failed || buffer == null || byteCount < 2 || sampleRate <= 0) {
            return;
        }
        final int preset = getPreset();
        if (preset == PRESET_OFF) {
            return;
        }
        try {
            if (engine == null || engine.sampleRate != sampleRate || engine.preset != preset) {
                engine = new Engine(sampleRate, preset, getSemitones());
            }
            final ByteBuffer b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            final int samples = byteCount / 2;
            for (int i = 0; i < samples; i++) {
                final float in = b.getShort(i * 2) / 32768f;
                float out = engine.tick(in);
                if (out > 1f) {
                    out = 1f;
                } else if (out < -1f) {
                    out = -1f;
                }
                b.putShort(i * 2, (short) (out * 32767f));
            }
        } catch (Throwable t) {
            failed = true;
        }
    }

    // ------------------------------------------------------------------ DSP

    private static class Engine {
        final int sampleRate;
        final int preset;

        // pitch shifter (two crossfaded delay taps)
        private final float ratio;
        private final boolean shift;
        private final float[] pbuf;
        private final int pmask;
        private final float grain;
        private int pw;
        private float phase;

        // ring modulator
        private final float ringInc;
        private final float ringDepth;
        private float ringPhase;

        private final float drive;

        // echo
        private final float[] ebuf;
        private final int elen;
        private int ew;
        private final float efb;
        private final float emix;

        // reverb (Freeverb-lite)
        private final float reverb;
        private final float[][] comb;
        private final int[] combPos;
        private final float[] combDamp;
        private final float[][] allp;
        private final int[] allpPos;

        Engine(int sampleRate, int preset, int semitones) {
            this.sampleRate = sampleRate;
            this.preset = preset;
            final float[] p = PARAMS[preset];

            float r = p[0];
            if (preset == PRESET_CUSTOM) {
                r = (float) Math.pow(2.0, semitones / 12.0);
            }
            ratio = r;
            shift = Math.abs(r - 1f) > 0.001f;
            int n = 1;
            while (n < sampleRate / 4) {
                n <<= 1;
            }
            pbuf = new float[n];
            pmask = n - 1;
            grain = Math.max(64, sampleRate * 0.04f);

            ringInc = (float) (2.0 * Math.PI * p[1] / sampleRate);
            ringDepth = p[2];
            drive = p[3];

            final int delaySamples = (int) (p[4] * sampleRate / 1000f);
            elen = Math.max(1, delaySamples);
            ebuf = new float[elen];
            efb = p[5];
            emix = p[6];

            reverb = p[7];
            final int[] combLen = {1116, 1188, 1277, 1356};
            final int[] allpLen = {556, 441};
            comb = new float[combLen.length][];
            combPos = new int[combLen.length];
            combDamp = new float[combLen.length];
            for (int i = 0; i < combLen.length; i++) {
                comb[i] = new float[Math.max(8, Math.round(combLen[i] * sampleRate / 44100f))];
            }
            allp = new float[allpLen.length][];
            allpPos = new int[allpLen.length];
            for (int i = 0; i < allpLen.length; i++) {
                allp[i] = new float[Math.max(8, Math.round(allpLen[i] * sampleRate / 44100f))];
            }
        }

        float tick(float x) {
            float y = x;

            if (shift) {
                pbuf[pw] = y;
                final float phaseB = phase + 0.5f >= 1f ? phase - 0.5f : phase + 0.5f;
                final float outA = readTap(phase * grain);
                final float outB = readTap(phaseB * grain);
                final double sa = Math.sin(Math.PI * phase);
                final double sb = Math.sin(Math.PI * phaseB);
                y = (float) (outA * sa * sa + outB * sb * sb);
                pw = (pw + 1) & pmask;
                phase += (1f - ratio) / grain;
                phase -= (float) Math.floor(phase);
            }

            if (ringDepth > 0f) {
                y *= (1f - ringDepth) + ringDepth * (float) Math.sin(ringPhase);
                ringPhase += ringInc;
                if (ringPhase > 6.2831853f) {
                    ringPhase -= 6.2831853f;
                }
            }

            if (drive > 0f) {
                final float g = 1f + drive * 6f;
                y = (1f - drive) * y + drive * (float) Math.tanh(y * g);
            }

            if (emix > 0f) {
                final int ri = ew;
                final float echo = ebuf[ri];
                ebuf[ri] = y + echo * efb;
                ew = ri + 1 >= elen ? 0 : ri + 1;
                y += echo * emix;
            }

            if (reverb > 0f) {
                float wet = 0f;
                for (int i = 0; i < comb.length; i++) {
                    final float[] c = comb[i];
                    final int pos = combPos[i];
                    final float d = c[pos];
                    combDamp[i] = d * 0.8f + combDamp[i] * 0.2f;
                    c[pos] = y * 0.3f + combDamp[i] * 0.84f;
                    combPos[i] = pos + 1 >= c.length ? 0 : pos + 1;
                    wet += d;
                }
                wet *= 0.25f;
                for (int i = 0; i < allp.length; i++) {
                    final float[] a = allp[i];
                    final int pos = allpPos[i];
                    final float buf = a[pos];
                    final float out = -wet + buf;
                    a[pos] = wet + buf * 0.5f;
                    allpPos[i] = pos + 1 >= a.length ? 0 : pos + 1;
                    wet = out;
                }
                y = y * (1f - reverb * 0.5f) + wet * reverb * 1.5f;
            }

            return y;
        }

        private float readTap(float delay) {
            final float pos = pw - delay;
            final int i0 = (int) Math.floor(pos);
            final float frac = pos - i0;
            final float a = pbuf[i0 & pmask];
            final float b = pbuf[(i0 + 1) & pmask];
            return a + (b - a) * frac;
        }
    }
}
