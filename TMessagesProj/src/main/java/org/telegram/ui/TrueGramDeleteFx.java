package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.Random;

/**
 * TrueGram: animated removal of chat messages.
 * The removed message cell is snapshotted into a bitmap, cut into tiles and the tiles
 * fly apart in the chosen style, drawn in the RecyclerView overlay.
 */
public final class TrueGramDeleteFx {

    public static final int MODE_DEFAULT = 0;
    public static final int MODE_GLASS = 1;
    public static final int MODE_SQUARES = 2;
    public static final int MODE_JELLY = 3;
    public static final int MODE_SAND = 4;
    public static final int MODE_VAPOR = 5;
    public static final int MODE_WIND = 6;
    public static final int MODE_FIRE = 7;
    public static final int MODE_CONFETTI = 8;
    public static final int MODE_VACUUM = 9;

    public static final String[] NAMES = {
            "Обычное", "Стекло", "Квадратики", "Желе", "Песочница",
            "Испарение", "Ветер", "Огонь", "Конфетти", "Вакуум"
    };

    /** Tile size in dp per mode. */
    private static final int[] TILE_DP = {0, 14, 16, 12, 5, 12, 10, 12, 16, 12};

    private static final String PREFS_NAME = "truegram";
    private static final String KEY_MODE = "msg_delete_anim";

    public static int getMode() {
        try {
            final int v = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_MODE, MODE_DEFAULT);
            return v >= 0 && v < NAMES.length ? v : MODE_DEFAULT;
        } catch (Throwable e) {
            return MODE_DEFAULT;
        }
    }

    public static void setMode(int mode) {
        try {
            ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_MODE, mode >= 0 && mode < NAMES.length ? mode : MODE_DEFAULT)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /** True when the removal should be replaced by one of the effects. */
    public static boolean canPlay(View cell) {
        return getMode() != MODE_DEFAULT && cell != null && cell.getWidth() > 0 && cell.getHeight() > 0;
    }

    /**
     * Plays the effect for a removed cell.
     * @param host    the list view whose overlay draws the pieces
     * @param cell    the removed message view
     * @param onEnd   called when the effect is over (the cell is restored before it is called)
     */
    public static void play(View host, View cell, Runnable onEnd) {
        final int mode = getMode();
        if (mode == MODE_DEFAULT || host == null || cell == null || cell.getWidth() <= 0 || cell.getHeight() <= 0) {
            if (onEnd != null) {
                onEnd.run();
            }
            return;
        }
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
            cell.draw(new Canvas(bitmap));
        } catch (Throwable e) {
            cell.setAlpha(1f);
            if (onEnd != null) {
                onEnd.run();
            }
            return;
        }
        final int[] a = new int[2];
        final int[] b = new int[2];
        cell.getLocationInWindow(a);
        host.getLocationInWindow(b);
        final Effect effect = new Effect(host, cell, bitmap, mode, a[0] - b[0], a[1] - b[1], onEnd);
        cell.setAlpha(0f);
        host.getOverlay().add(effect.drawable);
        effect.drawable.setBounds(0, 0, host.getWidth(), host.getHeight());
        host.postInvalidateOnAnimation();
    }

    private static final class Tile {
        Rect src;
        float cx, cy;     // centre in cell coordinates
        float tw, th;    // tile size in pixels
        float delay;
        float dur;
        float vx, vy;
        float gravity;
        float rot, rotSpeed;
        float wave;
        float r0, theta0;
    }

    private static final class Effect {
        final View host;
        final View cell;
        final Bitmap bmp;
        final int mode;
        final float ox, oy;
        final Runnable onEnd;
        final float dp;
        final int w, h;
        final int cols, rows;
        final long start;
        final float total;
        final ArrayList<Tile> tiles = new ArrayList<>();
        final Random random = new Random();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint firePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Drawable drawable;
        final RectF dst = new RectF();
        boolean done;

        Effect(View host, View cell, Bitmap bmp, int mode, float ox, float oy, Runnable onEnd) {
            this.host = host;
            this.cell = cell;
            this.bmp = bmp;
            this.mode = mode;
            this.ox = ox;
            this.oy = oy;
            this.onEnd = onEnd;
            this.dp = AndroidUtilities.density;
            this.w = bmp.getWidth();
            this.h = bmp.getHeight();
            this.start = SystemClock.uptimeMillis();
            firePaint.setColorFilter(new PorterDuffColorFilter(0xFFFF7A1A, PorterDuff.Mode.SRC_ATOP));

            final int tile = Math.max(2, Math.round(TILE_DP[mode] * dp));
            this.cols = (w + tile - 1) / tile;
            this.rows = (h + tile - 1) / tile;
            build(tile);

            float maxEnd = 0;
            for (Tile t : tiles) {
                maxEnd = Math.max(maxEnd, t.delay + t.dur);
            }
            this.total = maxEnd + 20f;

            this.drawable = new Drawable() {
                @Override
                public void draw(Canvas canvas) {
                    Effect.this.draw(canvas);
                }

                @Override
                public void setAlpha(int alpha) {
                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {
                }

                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSLUCENT;
                }
            };
        }

        private float rnd() {
            return random.nextFloat();
        }

        private float rndSigned(float amount) {
            return (rnd() - 0.5f) * 2f * amount;
        }

        private void build(int tile) {
            final float cx = w / 2f;
            final float cy = h / 2f;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    final Tile t = new Tile();
                    final int sl = c * tile;
                    final int st = r * tile;
                    final int sr = Math.min(w, sl + tile);
                    final int sb = Math.min(h, st + tile);
                    t.src = new Rect(sl, st, sr, sb);
                    t.cx = (sl + sr) / 2f;
                    t.cy = (st + sb) / 2f;
                    t.tw = sr - sl;
                    t.th = sb - st;
                    // normalised position of the tile, -1..1
                    final float nx = (t.cx - cx) / (w / 2f);
                    final float ny = (t.cy - cy) / (h / 2f);
                    final float colT = cols > 1 ? c / (float) (cols - 1) : 0f;
                    final float rowT = rows > 1 ? r / (float) (rows - 1) : 0f;
                    t.wave = rnd();

                    switch (mode) {
                        case MODE_GLASS:
                            t.delay = rnd() * 120f;
                            t.dur = 900f;
                            t.vx = (nx * 120f + rndSigned(40f)) * dp;
                            t.vy = (ny * 80f - 40f - rnd() * 60f) * dp;
                            t.gravity = 900f * dp;
                            t.rotSpeed = rndSigned(240f);
                            break;
                        case MODE_SQUARES:
                            t.delay = rnd() * 90f;
                            t.dur = 700f;
                            t.vx = (nx * 260f + rndSigned(60f)) * dp;
                            t.vy = (ny * 260f + rndSigned(60f)) * dp;
                            t.rotSpeed = rndSigned(120f);
                            break;
                        case MODE_JELLY:
                            t.delay = colT * 260f;
                            t.dur = 900f;
                            break;
                        case MODE_SAND:
                            t.delay = colT * 700f + rnd() * 60f;
                            t.dur = 1000f;
                            t.vx = rndSigned(30f) * dp;
                            t.gravity = 1400f * dp;
                            break;
                        case MODE_VAPOR:
                            t.delay = rnd() * 160f;
                            t.dur = 1100f;
                            t.vx = rndSigned(30f) * dp;
                            t.vy = -(70f + rnd() * 90f) * dp;
                            break;
                        case MODE_WIND:
                            t.delay = rowT * 200f + rnd() * 60f;
                            t.dur = 800f;
                            t.vx = (360f + rnd() * 200f) * dp;
                            break;
                        case MODE_FIRE:
                            t.delay = (1f - colT) * 250f + rnd() * 120f;
                            t.dur = 1000f;
                            t.vx = rndSigned(20f) * dp;
                            t.vy = -(50f + rnd() * 70f) * dp;
                            break;
                        case MODE_CONFETTI:
                            t.delay = rnd() * 100f;
                            t.dur = 1200f;
                            t.vx = rndSigned(120f) * dp;
                            t.vy = -(60f + rnd() * 100f) * dp;
                            t.gravity = 500f * dp;
                            t.rotSpeed = rndSigned(540f);
                            break;
                        case MODE_VACUUM: {
                            final float dx = t.cx - cx;
                            final float dy = t.cy - cy;
                            final float dist = (float) Math.sqrt(dx * dx + dy * dy);
                            t.delay = (dist / Math.max(1f, (float) Math.sqrt(cx * cx + cy * cy))) * 160f;
                            t.dur = 900f;
                            t.r0 = dist;
                            t.theta0 = (float) Math.atan2(dy, dx);
                            break;
                        }
                        default:
                            t.delay = 0f;
                            t.dur = 1f;
                            break;
                    }
                    tiles.add(t);
                }
            }
        }

        private void draw(Canvas canvas) {
            final float ms = SystemClock.uptimeMillis() - start;
            if (ms >= total) {
                if (!done) {
                    done = true;
                    // the overlay list is not changed while it is being drawn
                    host.post(this::finish);
                }
                return;
            }
            canvas.save();
            canvas.translate(ox, oy);
            for (Tile t : tiles) {
                drawTile(canvas, t, ms);
            }
            canvas.restore();
            host.postInvalidateOnAnimation();
        }

        private void drawTile(Canvas canvas, Tile t, float ms) {
            float x = t.cx;
            float y = t.cy;
            float sx = 1f;
            float sy = 1f;
            float rot = 0f;
            float alpha = 1f;
            final float local = (ms - t.delay) / t.dur;
            if (local > 0f) {
                final float p = Math.min(1f, local);
                final float s = (ms - t.delay) / 1000f;
                switch (mode) {
                    case MODE_GLASS:
                        x = t.cx + t.vx * s;
                        y = t.cy + t.vy * s + 0.5f * t.gravity * s * s;
                        rot = t.rotSpeed * s;
                        alpha = 1f - p * p;
                        break;
                    case MODE_SQUARES:
                        x = t.cx + t.vx * s;
                        y = t.cy + t.vy * s;
                        sx = sy = 1f - 0.8f * p;
                        rot = t.rotSpeed * s;
                        alpha = 1f - p;
                        break;
                    case MODE_JELLY:
                        y = t.cy + p * p * 70f * dp;
                        sy = 1f + 0.9f * p;
                        sx = 1f - 0.35f * p + 0.08f * (float) Math.sin(p * 12f + t.wave * 6f);
                        alpha = 1f - p * p * p;
                        break;
                    case MODE_SAND:
                        x = t.cx + t.vx * s;
                        y = t.cy + 0.5f * t.gravity * s * s;
                        alpha = p < 0.7f ? 1f : 1f - (p - 0.7f) / 0.3f;
                        break;
                    case MODE_VAPOR:
                        x = t.cx + t.vx * s;
                        y = t.cy + t.vy * s;
                        sx = sy = 1f + 0.5f * p;
                        alpha = 1f - p;
                        break;
                    case MODE_WIND:
                        x = t.cx + t.vx * s;
                        y = t.cy + (float) Math.sin(p * 6f + t.wave * 6f) * 4f * dp;
                        sx = 1f + 0.4f * p;
                        alpha = 1f - p * p;
                        break;
                    case MODE_FIRE:
                        x = t.cx + t.vx * s + (float) Math.sin(s * 14f + t.wave * 6f) * 2f * dp;
                        y = t.cy + t.vy * s;
                        sx = sy = 1f - 0.5f * p;
                        alpha = 1f - p;
                        break;
                    case MODE_CONFETTI:
                        x = t.cx + t.vx * s;
                        y = t.cy + t.vy * s + 0.5f * t.gravity * s * s;
                        rot = t.rotSpeed * s;
                        sx = (float) Math.cos(s * (2f + t.wave * 4f) * 6.28f);
                        alpha = 1f - p;
                        break;
                    case MODE_VACUUM: {
                        final float r = t.r0 * (1f - p);
                        final float theta = t.theta0 + p * 3.5f;
                        x = w / 2f + r * (float) Math.cos(theta);
                        y = h / 2f + r * (float) Math.sin(theta);
                        sx = sy = 1f - 0.8f * p;
                        rot = p * 360f;
                        alpha = 1f - p * p * p;
                        break;
                    }
                    default:
                        break;
                }
            }
            if (alpha <= 0f || Math.abs(sx) < 0.01f || Math.abs(sy) < 0.01f) {
                return;
            }
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rot);
            canvas.scale(sx, sy);
            dst.set(-t.tw / 2f, -t.th / 2f, t.tw / 2f, t.th / 2f);
            final Paint p = mode == MODE_FIRE && local > 0f ? firePaint : paint;
            p.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
            canvas.drawBitmap(bmp, t.src, dst, p);
            canvas.restore();
        }

        private void finish() {
            host.getOverlay().remove(drawable);
            cell.setAlpha(1f);
            cell.setTranslationX(0f);
            cell.setTranslationY(0f);
            bmp.recycle();
            if (onEnd != null) {
                onEnd.run();
            }
        }
    }
}
