package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.Random;

/**
 * TrueGram: a few small flat 2D particles (leaves, sakura, snow) drifting down behind the chat messages.
 * Self-contained. Touches pass through. Attach only to chat screens.
 *
 * Hook: TrueGramParticles.attach(contentView) once, after the chat view is built.
 */
public class TrueGramParticles {

    public static final int OFF = 0;
    public static final int LEAVES = 1;
    public static final int SAKURA = 2;
    public static final int SNOW = 3;
    public static final String[] NAMES = {"Выкл", "Листья", "Сакура", "Снег"};

    private static final String PREFS = "truegram_particles";
    private static final String KEY_MODE = "mode";

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

    /**
     * Adds the overlay under all other children (so it stays behind messages).
     * Does nothing when the effect is off.
     */
    public static View attach(FrameLayout parent) {
        if (parent == null || getMode() == OFF) {
            return null;
        }
        final ParticleView view = new ParticleView(parent.getContext(), getMode());
        parent.addView(view, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return view;
    }

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

    private static float dp(float v) {
        return v * ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
    }

    // ------------------------------------------------------------ view

    private static final class Particle {
        float x, y, speed, size, rot, rotSpeed, swayPhase, swayAmp;
    }

    private static final class ParticleView extends View {
        private final int mode;
        private final int count;
        private final ArrayList<Particle> particles = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path leaf = new Path();
        private final RectF oval = new RectF();
        private final Random rnd = new Random();
        private long lastFrame;

        ParticleView(Context context, int mode) {
            super(context);
            this.mode = mode;
            this.count = mode == SNOW ? 14 : 9;
            setClickable(false);
            setFocusable(false);
            setWillNotDraw(false);
            paint.setStyle(Paint.Style.FILL);
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
            p.y = anywhere ? rnd.nextFloat() * h : -dp(12);
            // very slow: a few dp per second
            final float base = mode == SNOW ? 0.006f : 0.009f;
            p.speed = (base + rnd.nextFloat() * base) * ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density;
            p.size = mode == SNOW ? dp(0.8f + rnd.nextFloat() * 0.5f) : dp(2.6f + rnd.nextFloat() * 1.2f);
            p.rot = rnd.nextFloat() * 360f;
            p.rotSpeed = (rnd.nextFloat() - 0.5f) * 0.03f;
            p.swayPhase = rnd.nextFloat() * 6.28f;
            p.swayAmp = dp(4f + rnd.nextFloat() * 6f);
            return p;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final long now = SystemClock.uptimeMillis();
            final float dt = lastFrame == 0 ? 16f : Math.min(50f, now - lastFrame);
            lastFrame = now;
            final int h = getHeight();
            if (h <= 0) {
                return;
            }
            while (particles.size() < count) {
                particles.add(spawn(true));
            }
            for (Particle p : particles) {
                p.y += p.speed * dt;
                p.swayPhase += 0.0008f * dt;
                p.rot += p.rotSpeed * dt;
                if (p.y > h + dp(12)) {
                    final Particle fresh = spawn(false);
                    p.x = fresh.x;
                    p.y = fresh.y;
                    p.speed = fresh.speed;
                    p.size = fresh.size;
                    p.rot = fresh.rot;
                    p.rotSpeed = fresh.rotSpeed;
                    p.swayPhase = fresh.swayPhase;
                    p.swayAmp = fresh.swayAmp;
                }
                final float x = p.x + (float) Math.sin(p.swayPhase) * p.swayAmp;
                draw(canvas, x, p);
            }
            postInvalidateOnAnimation();
        }

        private void draw(Canvas c, float x, Particle p) {
            c.save();
            c.translate(x, p.y);
            c.rotate(p.rot);
            if (mode == LEAVES) {
                drawLeaf(c, p.size);
            } else if (mode == SAKURA) {
                drawSakura(c, p.size);
            } else {
                paint.setColor(0x99FFFFFF);
                c.drawCircle(0, 0, p.size, paint);
            }
            c.restore();
        }

        private void drawLeaf(Canvas c, float len) {
            final float w = len * 0.42f;
            leaf.reset();
            leaf.moveTo(-len, 0);
            leaf.quadTo(0, -w, len, 0);
            leaf.quadTo(0, w, -len, 0);
            leaf.close();
            paint.setColor(0xB3A8C05A);
            c.drawPath(leaf, paint);
        }

        private void drawSakura(Canvas c, float r) {
            paint.setColor(0xB3FFB7C5);
            for (int i = 0; i < 5; i++) {
                final double a = Math.toRadians(i * 72.0);
                final float px = (float) Math.cos(a) * r;
                final float py = (float) Math.sin(a) * r;
                oval.set(px - r * 0.55f, py - r * 0.55f, px + r * 0.55f, py + r * 0.55f);
                c.drawOval(oval, paint);
            }
        }
    }
}
