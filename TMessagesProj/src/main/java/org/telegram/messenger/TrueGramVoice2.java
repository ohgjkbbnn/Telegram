package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TrueGram voice changer, mode 2: presets, sliders (pitch, boost, gate, bass, treble) and four sound effects.
 * Self-contained: settings, DSP and the editor dialog live here. It uses the same PCM hook as {@link TrueGramVoice}
 * (voice messages), so TrueGramVoice.process() hands the buffer over to this class when mode 2 is selected.
 */
public class TrueGramVoice2 {

    private static final String PREFS = "truegram_voice2";
    private static final String K_PRESET = "preset";
    private static final String K_PITCH = "pitch";
    private static final String K_BOOST = "boost";
    private static final String K_SENS = "sens";
    private static final String K_BASS = "bass";
    private static final String K_TREBLE = "treble";
    private static final String K_DRY = "dry";
    private static final String K_HALL = "fx_hall";
    private static final String K_CAVE = "fx_cave";
    private static final String K_CHORUS = "fx_chorus";
    private static final String K_GHOST = "fx_ghost";

    public static final String[] PRESET_NAMES = {"Выкл", "Alien", "Atari", "Mutation", "Male", "Female", "Helium", "Baby", "Radio", "Robot"};
    /** Pitch in semitones that a preset sets on the pitch slider. */
    private static final int[] PRESET_PITCH = {0, 0, 2, -4, -5, 5, 9, 7, 0, 0};
    /** Ring modulator frequency and depth (0 = off). */
    private static final float[] PRESET_RING_HZ = {0, 35, 0, 8, 0, 0, 0, 0, 0, 55};
    private static final float[] PRESET_RING_DEPTH = {0, 0.7f, 0, 0.4f, 0, 0, 0, 0, 0, 0.85f};
    private static final float[] PRESET_DRIVE = {0, 0, 0.5f, 0, 0, 0, 0, 0, 0.4f, 0.2f};
    /** Telephone-style low-pass cut in Hz (0 = off). */
    private static final float[] PRESET_LPCUT = {0, 0, 0, 0, 0, 0, 0, 0, 2800f, 0};

    private static volatile SharedPreferences prefs;

    private static SharedPreferences prefs() {
        SharedPreferences p = prefs;
        if (p == null) {
            synchronized (TrueGramVoice2.class) {
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
        final int p = prefs().getInt(K_PRESET, 0);
        return p >= 0 && p < PRESET_NAMES.length ? p : 0;
    }

    public static void setPreset(int preset) {
        prefs().edit().putInt(K_PRESET, preset).apply();
    }

    /** Pitch shift in semitones, -12..+12. */
    public static int getPitch() {
        return Math.max(-12, Math.min(12, prefs().getInt(K_PITCH, 0)));
    }

    public static void setPitch(int semitones) {
        prefs().edit().putInt(K_PITCH, Math.max(-12, Math.min(12, semitones))).apply();
    }

    /** Microphone boost in percent, 0..500 (0 = no boost). */
    public static int getBoost() {
        return Math.max(0, Math.min(500, prefs().getInt(K_BOOST, 0)));
    }

    public static void setBoost(int percent) {
        prefs().edit().putInt(K_BOOST, Math.max(0, Math.min(500, percent))).apply();
    }

    /** Noise gate level, 0..100 (0 = gate off). */
    public static int getSens() {
        return Math.max(0, Math.min(100, prefs().getInt(K_SENS, 0)));
    }

    public static void setSens(int value) {
        prefs().edit().putInt(K_SENS, Math.max(0, Math.min(100, value))).apply();
    }

    /** Bass boost in dB, 0..30. */
    public static int getBass() {
        return Math.max(0, Math.min(30, prefs().getInt(K_BASS, 0)));
    }

    public static void setBass(int db) {
        prefs().edit().putInt(K_BASS, Math.max(0, Math.min(30, db))).apply();
    }

    /** Treble boost in dB, 0..30. */
    public static int getTreble() {
        return Math.max(0, Math.min(30, prefs().getInt(K_TREBLE, 0)));
    }

    public static void setTreble(int db) {
        prefs().edit().putInt(K_TREBLE, Math.max(0, Math.min(30, db))).apply();
    }

    public static boolean isDry() {
        return prefs().getBoolean(K_DRY, false);
    }

    public static boolean isHall() {
        return prefs().getBoolean(K_HALL, false);
    }

    public static boolean isCave() {
        return prefs().getBoolean(K_CAVE, false);
    }

    public static boolean isChorus() {
        return prefs().getBoolean(K_CHORUS, false);
    }

    public static boolean isGhost() {
        return prefs().getBoolean(K_GHOST, false);
    }

    public static void setFlag(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    public static String flagKeyDry() { return K_DRY; }
    public static String flagKeyHall() { return K_HALL; }
    public static String flagKeyCave() { return K_CAVE; }
    public static String flagKeyChorus() { return K_CHORUS; }
    public static String flagKeyGhost() { return K_GHOST; }

    public static void applyPreset(int preset) {
        setPreset(preset);
        setPitch(PRESET_PITCH[preset]);
    }

    /** "Clear effect": everything back to the defaults. */
    public static void clearEffects() {
        prefs().edit()
                .putInt(K_PRESET, 0)
                .putInt(K_PITCH, 0)
                .putInt(K_BOOST, 0)
                .putInt(K_SENS, 0)
                .putInt(K_BASS, 0)
                .putInt(K_TREBLE, 0)
                .putBoolean(K_DRY, false)
                .putBoolean(K_HALL, false)
                .putBoolean(K_CAVE, false)
                .putBoolean(K_CHORUS, false)
                .putBoolean(K_GHOST, false)
                .apply();
    }

    /** True if anything changes the voice. "Append original" alone does not count. */
    public static boolean isActive() {
        return getPreset() != 0 || getPitch() != 0 || getBoost() > 0 || getSens() > 0
                || getBass() > 0 || getTreble() > 0 || isHall() || isCave() || isChorus() || isGhost();
    }

    public static String currentName() {
        if (!isActive()) {
            return "Выкл";
        }
        final int st = getPitch();
        return PRESET_NAMES[getPreset()] + (st != 0 ? " (" + (st > 0 ? "+" : "") + st + ")" : "");
    }

    // ------------------------------------------------------------------ DSP hook

    private static Engine engine;

    /** Call when a new voice recording starts. */
    public static synchronized void onRecordingStart() {
        engine = null;
    }

    /**
     * Processes 16-bit little-endian mono PCM in place.
     * Settings are read once per recording, so a change applies to the next voice message.
     */
    public static synchronized void process(ByteBuffer buffer, int byteCount, int sampleRate) {
        try {
            if (buffer == null || byteCount < 2 || sampleRate <= 0 || !isActive()) {
                return;
            }
            if (engine == null || engine.sampleRate != sampleRate) {
                engine = new Engine(sampleRate);
            }
            final ByteBuffer b = buffer.duplicate();
            b.clear();
            b.order(ByteOrder.LITTLE_ENDIAN);
            final int samples = Math.min(byteCount, b.capacity()) / 2;
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
            engine = null;
            FileLog.e(t);
        }
    }

    private static class Engine {
        final int sampleRate;

        // gain and gate
        final float gain;
        final float gate;

        // pitch shifter (two crossfaded delay taps)
        final float ratio;
        final boolean shift;
        final float[] pbuf;
        final int pmask;
        final float grain;
        int pw;
        float phase;

        // ring modulator and drive
        final float ringInc;
        final float ringDepth;
        float ringPhase;
        final float drive;

        // radio low-pass
        final boolean lowpass;
        final float lpCoef;
        float lpState;

        // bass and treble
        final float bassGain;
        final float trebleGain;
        final float lpBass;
        final float lpTreble;
        float lowState;
        float highState;

        // chorus
        final boolean chorus;
        final float[] cbuf;
        int cw;
        float cphase;
        final float cInc;
        final float cBase;
        final float cDepth;

        // echo (ghost, cave)
        final boolean echo;
        final float[] ebuf;
        int ew;
        final float efb;
        final float emix;

        // reverb (hall, cave, ghost)
        final float rev;
        final boolean reverb;
        final float[][] comb;
        final int[] combPos;
        final float[] combDamp;
        final float[][] allp;
        final int[] allpPos;

        // append the original voice on top
        final boolean dry;

        Engine(int sampleRate) {
            this.sampleRate = sampleRate;

            gain = 1f + getBoost() / 100f;
            gate = getSens() / 100f * 0.08f;

            final int semis = getPitch();
            ratio = (float) Math.pow(2.0, semis / 12.0);
            shift = semis != 0;
            int n = 1;
            while (n < sampleRate / 4) {
                n <<= 1;
            }
            pbuf = new float[n];
            pmask = n - 1;
            grain = Math.max(64, sampleRate * 0.04f);

            final int p = getPreset();
            ringInc = (float) (2.0 * Math.PI * PRESET_RING_HZ[p] / sampleRate);
            ringDepth = PRESET_RING_DEPTH[p];
            drive = PRESET_DRIVE[p];
            lowpass = PRESET_LPCUT[p] > 0f;
            lpCoef = lowpass ? (float) (1.0 - Math.exp(-2.0 * Math.PI * PRESET_LPCUT[p] / sampleRate)) : 0f;

            bassGain = (float) Math.pow(10.0, getBass() / 60.0) - 1f;
            trebleGain = (float) Math.pow(10.0, getTreble() / 60.0) - 1f;
            lpBass = (float) (1.0 - Math.exp(-2.0 * Math.PI * 250.0 / sampleRate));
            lpTreble = (float) (1.0 - Math.exp(-2.0 * Math.PI * 2500.0 / sampleRate));

            chorus = isChorus();
            cbuf = new float[Math.max(64, sampleRate / 25)];
            cBase = 0.012f * sampleRate;
            cDepth = 0.004f * sampleRate;
            cInc = (float) (2.0 * Math.PI * 0.6 / sampleRate);

            final boolean ghost = isGhost();
            echo = ghost || isCave();
            final int delayMs = ghost ? 260 : 140;
            ebuf = new float[Math.max(1, delayMs * sampleRate / 1000)];
            efb = ghost ? 0.45f : 0.35f;
            emix = ghost ? 0.4f : 0.3f;

            float r = 0f;
            if (isHall()) {
                r = Math.max(r, 0.4f);
            }
            if (isCave()) {
                r = Math.max(r, 0.7f);
            }
            if (ghost) {
                r = Math.max(r, 0.3f);
            }
            rev = r;
            reverb = r > 0f;
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

            dry = isDry();
        }

        float tick(float x) {
            float y = x * gain;
            if (gate > 0f && Math.abs(y) < gate) {
                y = 0f;
            }

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

            if (lowpass) {
                lpState += lpCoef * (y - lpState);
                y = lpState;
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

            if (chorus) {
                cbuf[cw] = y;
                final float d = cBase + cDepth * (float) Math.sin(cphase);
                cphase += cInc;
                if (cphase > 6.2831853f) {
                    cphase -= 6.2831853f;
                }
                float rp = cw - d;
                if (rp < 0f) {
                    rp += cbuf.length;
                }
                final int i0 = (int) rp;
                final float fr = rp - i0;
                final int i1 = i0 + 1 >= cbuf.length ? 0 : i0 + 1;
                final float wet = cbuf[i0] + (cbuf[i1] - cbuf[i0]) * fr;
                cw = cw + 1 >= cbuf.length ? 0 : cw + 1;
                y = y * 0.6f + wet * 0.6f;
            }

            if (echo) {
                final float e = ebuf[ew];
                ebuf[ew] = y + e * efb;
                ew = ew + 1 >= ebuf.length ? 0 : ew + 1;
                y += e * emix;
            }

            if (reverb) {
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
                y = y * (1f - rev * 0.5f) + wet * rev * 1.5f;
            }

            if (bassGain > 0f) {
                lowState += lpBass * (y - lowState);
                y += bassGain * lowState;
            }
            if (trebleGain > 0f) {
                highState += lpTreble * (y - highState);
                y += trebleGain * (y - highState);
            }

            if (dry) {
                y = 0.5f * y + 0.5f * x;
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

    // ------------------------------------------------------------------ editor dialog

    private interface IntSink {
        void put(int v);
    }

    private interface BoolSink {
        void put(boolean v);
    }

    /** Dialog with presets, sliders, effects and buttons (Clear effect / Close). */
    public static void showEditor(final Context context, final Runnable onChanged) {
        final int textColor = Theme.getColor(Theme.key_dialogTextBlack);
        final int grayColor = Theme.getColor(Theme.key_dialogTextGray2);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        final int gap = AndroidUtilities.dp(4);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        final int pad = AndroidUtilities.dp(16);
        root.setPadding(pad, AndroidUtilities.dp(4), pad, 0);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        final Runnable changed = () -> {
            if (onChanged != null) {
                onChanged.run();
            }
        };
        // presets and "clear" rebuild the dialog so every slider and checkbox shows the new values
        final Runnable reopen = () -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showEditor(context, onChanged);
        };

        addLabel(context, root, "Эффекты голоса", grayColor);
        final int current = getPreset();
        LinearLayout row = null;
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final int preset = i;
            final boolean selected = i == current;
            final TextView btn = new TextView(context);
            btn.setText(PRESET_NAMES[i]);
            btn.setTextSize(14);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
            btn.setTextColor(selected ? 0xFFFFFFFF : textColor);
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(12));
            bg.setColor(selected ? accent : 0x22888888);
            btn.setBackground(bg);
            btn.setOnClickListener(v -> {
                applyPreset(preset);
                changed.run();
                reopen.run();
            });
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(btn, lp);
        }

        addLabel(context, root, "Микрофон", grayColor);
        seek(context, root, "Высота тона", "", -12, 12, getPitch(), v -> {
            setPitch(v);
            changed.run();
        }, textColor);
        seek(context, root, "Усиление", "%", 0, 500, getBoost(), v -> {
            setBoost(v);
            changed.run();
        }, textColor);
        seek(context, root, "Чувствительность", "", 0, 100, getSens(), v -> {
            setSens(v);
            changed.run();
        }, textColor);
        seek(context, root, "Басы", " дБ", 0, 30, getBass(), v -> {
            setBass(v);
            changed.run();
        }, textColor);
        seek(context, root, "Высокие", " дБ", 0, 30, getTreble(), v -> {
            setTreble(v);
            changed.run();
        }, textColor);

        addLabel(context, root, "Звуковые эффекты", grayColor);
        check(context, root, "Добавить свой голос", isDry(), v -> {
            setFlag(flagKeyDry(), v);
            changed.run();
        }, textColor);
        check(context, root, "Town hall", isHall(), v -> {
            setFlag(flagKeyHall(), v);
            changed.run();
        }, textColor);
        check(context, root, "Cave", isCave(), v -> {
            setFlag(flagKeyCave(), v);
            changed.run();
        }, textColor);
        check(context, root, "Chorus", isChorus(), v -> {
            setFlag(flagKeyChorus(), v);
            changed.run();
        }, textColor);
        check(context, root, "Ghost", isGhost(), v -> {
            setFlag(flagKeyGhost(), v);
            changed.run();
        }, textColor);

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Голос 2 · " + currentName());
        builder.setView(scroll);
        builder.setPositiveButton("Закрыть", null);
        builder.setNegativeButton("Очистить эффект", (d, w) -> {
            clearEffects();
            changed.run();
        });
        dialogRef[0] = builder.show();
    }

    private static void addLabel(Context context, LinearLayout root, String text, int color) {
        final TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(color);
        tv.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        root.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void seek(Context context, LinearLayout root, final String title, final String unit, final int min, final int max,
                             int value, final IntSink sink, int textColor) {
        final TextView label = new TextView(context);
        label.setTextSize(14);
        label.setTextColor(textColor);
        label.setText(title + ": " + value + unit);
        root.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final SeekBar bar = new SeekBar(context);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                final int v = progress + min;
                label.setText(title + ": " + v + unit);
                sink.put(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void check(Context context, LinearLayout root, String title, boolean checked, final BoolSink sink, int textColor) {
        final CheckBox cb = new CheckBox(context);
        cb.setText(title);
        cb.setTextColor(textColor);
        cb.setChecked(checked);
        cb.setOnCheckedChangeListener((button, isChecked) -> sink.put(isChecked));
        root.addView(cb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
