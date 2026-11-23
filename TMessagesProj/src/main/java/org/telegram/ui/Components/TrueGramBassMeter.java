package org.telegram.ui.Components;

import android.media.audiofx.Visualizer;

/**
 * TrueGram: the bass level of the music that is playing, 0..1. It reads the system's audio output mix
 * (Visualizer on session 0), so it needs the android.permission.MODIFY_AUDIO_SETTINGS permission. If the system does not
 * allow it, the level simply stays 0 and nothing moves.
 */
public final class TrueGramBassMeter {

    // Everything at or below this frequency counts as bass.
    private static final double BASS_HZ = 160.0;

    private Visualizer visualizer;
    private volatile float level;

    public void start() {
        if (visualizer != null) {
            return;
        }
        try {
            final Visualizer v = new Visualizer(0);
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    onFft(fft, samplingRate);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            v.setEnabled(true);
            visualizer = v;
        } catch (Throwable e) {
            stop();
        }
    }

    public void stop() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
            } catch (Throwable ignored) {
            }
            try {
                visualizer.release();
            } catch (Throwable ignored) {
            }
            visualizer = null;
        }
        level = 0f;
    }

    /** Bass level now: jumps up instantly on a beat and falls back slowly. */
    public float getLevel() {
        return level;
    }

    private void onFft(byte[] fft, int samplingRateMilliHz) {
        final int n = fft.length;
        if (n < 4) {
            return;
        }
        // The FFT holds n/2 complex bins (real, imaginary pairs), each sr/n Hz wide.
        final double hzPerBin = (samplingRateMilliHz / 1000.0) / n;
        double sum = 0;
        int count = 0;
        for (int k = 1; 2 * k + 1 < n; k++) {
            if (k * hzPerBin > BASS_HZ) {
                break;
            }
            final double re = fft[2 * k];
            final double im = fft[2 * k + 1];
            sum += Math.sqrt(re * re + im * im);
            count++;
        }
        if (count == 0) {
            return;
        }
        final float value = (float) Math.min(1.0, sum / count / 60.0);
        level = Math.max(value, level * 0.88f);
    }
}
