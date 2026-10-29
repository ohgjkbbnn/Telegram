package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.Random;

/**
 * TrueGram: falling particles over a screen (leaves, sakura, snow).
 * Self-contained. The overlay ignores touches, so the list under it works as usual.
 *
 * Hook: TrueGramParticles.attach(parentFrameLayout) once when the screen is created.
 */
public class TrueGramParticles {

    public static final int OFF = 0;
    public static final int LEAVES = 1;
    public static final int SAKURA = 2;
    public static final int SNOW = 3;
    public static final String[] NAMES = {"Выкл", "Листья", "Сакура", "Снег"};

    private static final String PREFS = "truegram_particles";
    private static final String KEY_MODE = "mode";
    private static final int MAX_PARTICLES = 36;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getMode() {
        final int m = prefs().getInt(KEY_MODE, OFF);
        return m >= 0 && m < NAMES.length ? m : OFF;
    }

    public static void setMode(int mode) {
        prefs().edit().putInt(KEY_MODE, mode).apply();
    }

    /** Adds the overlay on top of the given layout. Does nothing when the effect is off. */
    public static View attach(FrameLayout parent) {
        if (parent == null || getMode() == OFF) {
            return null;
        }
        final ParticleView view = new ParticleView(parent.getContext(), getMode());
        parent.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // views added after attach() would otherwise cover the overlay; move it to the top once the screen is built
        parent.post(view::bringToFront);
        return view;
    }

    /** Picker dialog for the settings row. onChanged runs after the choice is saved. */
    public static void showPicker(Context context, Runnable onChanged) {
        final String[] items = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            items[i] = (i == getMode() ? "✓ " : "") + NAMES[i];
        }
        new AlertDialog.Builder(context)
                .setTitle("Падающие элементы")
                .setItems(items, (d, which) -> {
                    setMode(which);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ------------------------------------------------------------ view

    private static final class Particle {
        float x, y, speed, size, rot, rotSpeed, swayPhase, swayAmp;
    }

    private static final class ParticleView extends View {
        private final int mode;
        private final ArrayList<Particle> particles = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private final RectF oval = new RectF();
        private long lastFrame;

        ParticleView(Context context, int mode) {
            super(context);
            this.mode = mode;
            setClickable(false);
            setFocusable(false);
            setWillNotDraw(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        private Particle spawn(boolean anywhere) {
            final Particle p = new Particle();
            final float w = Math.max(1, getWidth());
            final float h = Math.max(1, getHeight());
            p.x = rnd.nextFloat() * w;
            p.y = anywhere ? rnd.nextFloat() * h : -dp(20);
            final float dpScale = dp(1);
            p.size = (mode == SNOW ? 2.5f + rnd.nextFloat() * 3f : 6f + rnd.nextFloat() * 5f) * dpScale;
            p.speed = (mode == SNOW ? 0.04f + rnd.nextFloat() * 0.05f : 0.05f + rnd.nextFloat() * 0.07f) * dpScale;
            p.rot = rnd.nextFloat() * 360f;
            p.rotSpeed = (rnd.nextFloat() - 0.5f) * 0.12f;
            p.swayPhase = rnd.nextFloat() * 6.28f;
            p.swayAmp = (10f + rnd.nextFloat() * 20f) * dpScale;
            return p;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final long now = SystemClock.uptimeMillis();
            final float dt = lastFrame == 0 ? 16f : Math.min(64f, now - lastFrame);
            lastFrame = now;
            final int h = getHeight();
            if (h <= 0) {
                return;
            }
            while (particles.size() < MAX_PARTICLES) {
                particles.add(spawn(particles.isEmpty()));
            }
            for (Particle p : particles) {
                p.y += p.speed * dt;
                p.swayPhase += 0.0015f * dt;
                p.rot += p.rotSpeed * dt;
                final float x = p.x + (float) Math.sin(p.swayPhase) * p.swayAmp;
                if (p.y > h + dp(20)) {
                    final Particle fresh = spawn(false);
                    p.x = fresh.x;
                    p.y = fresh.y;
                    p.speed = fresh.speed;
                    p.size = fresh.size;
                    p.swayAmp = fresh.swayAmp;
                    p.swayPhase = fresh.swayPhase;
                    p.rot = fresh.rot;
                    p.rotSpeed = fresh.rotSpeed;
                }
                draw(canvas, x, p);
            }
            postInvalidateOnAnimation();
        }

        private void draw(Canvas c, float x, Particle p) {
            c.save();
            c.rotate(p.rot, x, p.y);
            if (mode == LEAVES) {
                paint.setColor(p.size > 8f * dp(1) ? 0xCCD2691E : 0xCC8BC34A);
                oval.set(x - p.size, p.y - p.size * 0.55f, x + p.size, p.y + p.size * 0.55f);
                c.drawOval(oval, paint);
            } else if (mode == SAKURA) {
                paint.setColor(p.size > 8f * dp(1) ? 0xCCFFB7C5 : 0xCCFF9EB5);
                for (int i = 0; i < 5; i++) {
                    final double a = Math.toRadians(i * 72.0);
                    final float px = x + (float) Math.cos(a) * p.size * 0.6f;
                    final float py = p.y + (float) Math.sin(a) * p.size * 0.6f;
                    oval.set(px - p.size * 0.45f, py - p.size * 0.3f, px + p.size * 0.45f, py + p.size * 0.3f);
                    c.drawOval(oval, paint);
                }
            } else {
                paint.setColor(0xDDFFFFFF);
                c.drawCircle(x, p.y, p.size * 0.5f, paint);
            }
            c.restore();
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }
    }

    private static float dp(float v) {
        return v * ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
    }
}
