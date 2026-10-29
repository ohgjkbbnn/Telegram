package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Random;

/**
 * TrueGram: animated text input.
 * New letters appear with an animation (rise / spring / drop),
 * and deleted letters break apart into particles.
 * Call {@link #attach(EditText)} once for the message input field.
 */
public final class TrueGramTypingFx {

    public static final int STYLE_OFF = 0;
    public static final int STYLE_RISE = 1;
    public static final int STYLE_POP = 2;
    public static final int STYLE_DROP = 3;
    public static final String[] STYLE_NAMES = {"Выключена", "Всплытие", "Пружина", "Падение"};

    private static final String PREFS_NAME = "truegram";
    private static final String KEY_STYLE = "typing_anim";
    private static final int MAX_GLYPHS_PER_INPUT = 12;
    private static final int MAX_PARTICLE_CHARS = 24;

    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator(1.6f);
    private static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator(2.2f);
    private static final BounceInterpolator BOUNCE = new BounceInterpolator();

    public static int getStyle() {
        try {
            final int v = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_STYLE, STYLE_OFF);
            return v >= 0 && v < STYLE_NAMES.length ? v : STYLE_OFF;
        } catch (Throwable e) {
            return STYLE_OFF;
        }
    }

    public static final int CURSOR_OFF = 0;
    public static final int CURSOR_GLIDE = 1;
    public static final int CURSOR_GLIDE_BLINK = 2;
    public static final String[] CURSOR_NAMES = {"Обычный", "Плавное движение", "Плавное движение и мягкое мигание"};
    private static final String KEY_CURSOR = "smooth_cursor";

    public static int getCursorMode() {
        try {
            final int v = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_CURSOR, CURSOR_OFF);
            return v >= 0 && v < CURSOR_NAMES.length ? v : CURSOR_OFF;
        } catch (Throwable e) {
            return CURSOR_OFF;
        }
    }

    public static void setCursorMode(int mode) {
        try {
            ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_CURSOR, mode >= 0 && mode < CURSOR_NAMES.length ? mode : CURSOR_OFF)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static void setStyle(int style) {
        try {
            ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_STYLE, style >= 0 && style < STYLE_NAMES.length ? style : STYLE_OFF)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /** A newly typed letter: its character is hidden with a transparent span while the overlay draws it. */
    private static final class Glyph {
        ForegroundColorSpan span;
        long start;
        long delay;
        long duration;
        int style;
    }

    /** A deleted letter (ch != null) or a small dot of dust (ch == null). */
    private static final class Particle {
        String ch;
        float x0, y0;
        float vx, vy;
        float gravity;
        float rot, rotSpeed;
        float size;
        int color;
        long start;
        long life;
    }

    private final EditText editText;
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Glyph> glyphs = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final float[] pos = new float[2];
    private final Drawable overlay;
    private final TextWatcher watcher;
    private int pendingStart;
    private int pendingCount;

    // smooth cursor state
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean cursorOn;
    private boolean cursorBlink;
    private boolean cursorReady;
    private float curX, curTop, curBottom;
    private long lastFrame;
    private long lastTypeTime;

    private TrueGramTypingFx(EditText editText) {
        this.editText = editText;

        overlay = new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                drawFx(canvas);
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

        watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                final int style = getStyle();
                if (count <= 0 || style == STYLE_OFF) {
                    return;
                }
                final Layout layout = TrueGramTypingFx.this.editText.getLayout();
                if (layout == null) {
                    return;
                }
                prepPaint();
                final Paint.FontMetrics fm = paint.getFontMetrics();
                final long now = SystemClock.uptimeMillis();
                final int color = TrueGramTypingFx.this.editText.getCurrentTextColor();
                final int end = Math.min(start + count, s.length());
                final int limit = Math.min(end, start + MAX_PARTICLE_CHARS);
                for (int i = start; i < limit; i++) {
                    final char c = s.charAt(i);
                    if (Character.isWhitespace(c) || Character.isSurrogate(c)) {
                        continue;
                    }
                    if (!locate(layout, i)) {
                        continue;
                    }
                    final String ch = String.valueOf(c);
                    final float cx = pos[0] + paint.measureText(ch) / 2f;
                    final float cy = pos[1] + fm.ascent / 2f;
                    spawnDeleted(ch, cx, cy, style, color, now);
                }
                if (!particles.isEmpty()) {
                    TrueGramTypingFx.this.editText.postInvalidateOnAnimation();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                pendingStart = start;
                pendingCount = count;
                lastTypeTime = SystemClock.uptimeMillis();
            }

            @Override
            public void afterTextChanged(Editable s) {
                final int start = pendingStart;
                final int count = pendingCount;
                pendingCount = 0;
                final int style = getStyle();
                if (count <= 0 || style == STYLE_OFF) {
                    return;
                }
                final long now = SystemClock.uptimeMillis();
                int added = 0;
                for (int i = start; i < start + count && i < s.length(); i++) {
                    if (added >= MAX_GLYPHS_PER_INPUT) {
                        break;
                    }
                    final char c = s.charAt(i);
                    if (Character.isWhitespace(c) || Character.isSurrogate(c)) {
                        continue;
                    }
                    final Glyph g = new Glyph();
                    g.span = new ForegroundColorSpan(Color.TRANSPARENT);
                    s.setSpan(g.span, i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    g.start = now;
                    g.delay = added * 30L;
                    g.duration = style == STYLE_POP ? 340L : 380L;
                    g.style = style;
                    glyphs.add(g);
                    added++;
                }
                if (added > 0) {
                    TrueGramTypingFx.this.editText.postInvalidateOnAnimation();
                }
            }
        };
    }

    /** Attaches the animation to an input field. Call once per field. */
    public static void attach(EditText editText) {
        if (editText == null) {
            return;
        }
        final TrueGramTypingFx fx = new TrueGramTypingFx(editText);
        final int cursorMode = getCursorMode();
        fx.cursorOn = cursorMode != CURSOR_OFF;
        fx.cursorBlink = cursorMode == CURSOR_GLIDE_BLINK;
        if (fx.cursorOn) {
            // the native cursor is replaced by the smooth one drawn in the overlay
            editText.setCursorVisible(false);
        }
        editText.addTextChangedListener(fx.watcher);
        editText.getOverlay().add(fx.overlay);
        fx.overlay.setBounds(0, 0, editText.getWidth(), editText.getHeight());
        editText.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                fx.overlay.setBounds(0, 0, r - l, b - t));
    }

    private void prepPaint() {
        paint.set(editText.getPaint());
    }

    /** Sets pos[0] = left x and pos[1] = baseline y of the character at offset, in view coordinates. */
    private boolean locate(Layout layout, int offset) {
        final CharSequence text = layout.getText();
        if (offset < 0 || offset >= text.length()) {
            return false;
        }
        final int line = layout.getLineForOffset(offset);
        pos[0] = layout.getPrimaryHorizontal(offset) + editText.getTotalPaddingLeft() - editText.getScrollX();
        pos[1] = layout.getLineBaseline(line) + editText.getTotalPaddingTop() - editText.getScrollY();
        return true;
    }

    private void spawnDeleted(String ch, float cx, float cy, int style, int color, long now) {
        final float dp = AndroidUtilities.density;

        final Particle letter = new Particle();
        letter.ch = ch;
        letter.x0 = cx;
        letter.y0 = cy;
        letter.color = color;
        letter.start = now;
        letter.life = 520;
        letter.rot = 0f;
        letter.rotSpeed = (random.nextFloat() - 0.5f) * 12f;
        setVelocity(letter, style, dp, 1f);
        particles.add(letter);

        final float charWidth = paint.measureText(ch);
        for (int i = 0; i < 6; i++) {
            final Particle dot = new Particle();
            dot.ch = null;
            dot.x0 = cx + (random.nextFloat() - 0.5f) * charWidth;
            dot.y0 = cy + (random.nextFloat() - 0.5f) * dp * 6f;
            dot.color = color;
            dot.start = now;
            dot.life = 420 + random.nextInt(220);
            dot.size = dp * (1.2f + random.nextFloat() * 1.4f);
            setVelocity(dot, style, dp, 1.3f);
            particles.add(dot);
        }
    }

    private void setVelocity(Particle p, int style, float dp, float speedK) {
        switch (style) {
            case STYLE_POP: {
                final double angle = random.nextDouble() * Math.PI * 2;
                final float v = (60f + random.nextFloat() * 70f) * dp * speedK;
                p.vx = (float) Math.cos(angle) * v;
                p.vy = (float) Math.sin(angle) * v;
                p.gravity = 0f;
                break;
            }
            case STYLE_DROP:
                p.vx = (random.nextFloat() - 0.5f) * 60f * dp * speedK;
                p.vy = (random.nextFloat() - 0.5f) * 30f * dp * speedK;
                p.gravity = 320f * dp;
                break;
            default:
                p.vx = (random.nextFloat() - 0.5f) * 60f * dp * speedK;
                p.vy = -(40f + random.nextFloat() * 50f) * dp * speedK;
                p.gravity = 0f;
                break;
        }
    }

    private void drawFx(Canvas canvas) {
        final long now = SystemClock.uptimeMillis();
        prepPaint();
        final Layout layout = editText.getLayout();
        final Editable text = editText.getText();
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final int color = editText.getCurrentTextColor();
        boolean busy = false;

        for (int i = glyphs.size() - 1; i >= 0; i--) {
            final Glyph g = glyphs.get(i);
            final int offset = text != null ? text.getSpanStart(g.span) : -1;
            if (offset < 0) {
                glyphs.remove(i);
                continue;
            }
            final float t = (now - g.start - g.delay) / (float) g.duration;
            if (t >= 1f) {
                glyphs.remove(i);
                final ForegroundColorSpan done = g.span;
                // removed after the frame, so the span is not changed during drawing
                editText.post(() -> {
                    final Editable e = editText.getText();
                    if (e != null) {
                        e.removeSpan(done);
                    }
                });
                continue;
            }
            busy = true;
            if (t < 0f || layout == null || !locate(layout, offset)) {
                continue;
            }
            final float tc = Math.max(0f, t);
            float scale = 1f;
            float dy = 0f;
            float alpha;
            if (g.style == STYLE_POP) {
                scale = Math.max(0f, OVERSHOOT.getInterpolation(tc));
                alpha = Math.min(1f, tc * 3f);
            } else if (g.style == STYLE_DROP) {
                dy = -(1f - BOUNCE.getInterpolation(tc)) * AndroidUtilities.dp(16);
                alpha = Math.min(1f, tc * 4f);
            } else {
                final float p = DECELERATE.getInterpolation(tc);
                dy = (1f - p) * AndroidUtilities.dp(9);
                alpha = p;
            }
            final String ch = String.valueOf(text.charAt(offset));
            final float cx = pos[0] + paint.measureText(ch) / 2f;
            final float cy = pos[1] + fm.ascent / 2f + dy;
            drawChar(canvas, ch, cx, cy, scale, 0f, (int) (alpha * 255f), color);
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            final Particle p = particles.get(i);
            final long age = now - p.start;
            if (age >= p.life) {
                particles.remove(i);
                continue;
            }
            busy = true;
            final float seconds = age / 1000f;
            final float k = age / (float) p.life;
            final float x = p.x0 + p.vx * seconds;
            final float y = p.y0 + p.vy * seconds + 0.5f * p.gravity * seconds * seconds;
            final int alpha = (int) ((1f - k) * 255f);
            if (p.ch != null) {
                drawChar(canvas, p.ch, x, y, 1f - 0.35f * k, p.rot + p.rotSpeed * seconds, alpha, p.color);
            } else {
                dotPaint.setColor(p.color);
                dotPaint.setAlpha(Math.max(0, alpha));
                canvas.drawCircle(x, y, p.size * (1f - 0.5f * k), dotPaint);
            }
        }

        if (drawCursor(canvas, now, layout)) {
            busy = true;
        }

        if (busy) {
            editText.postInvalidateOnAnimation();
        }
    }

    /** Smooth cursor: glides to the caret position and optionally breathes when idle. */
    private boolean drawCursor(Canvas canvas, long now, Layout layout) {
        final long dtMs = lastFrame == 0 ? 16 : Math.min(64, now - lastFrame);
        lastFrame = now;
        if (!cursorOn || layout == null || !editText.hasFocus()) {
            cursorReady = false;
            return false;
        }
        final int sel = editText.getSelectionStart();
        if (sel < 0 || sel != editText.getSelectionEnd() || sel > layout.getText().length()) {
            cursorReady = false;
            return false;
        }

        final int line = layout.getLineForOffset(sel);
        final float dy = editText.getTotalPaddingTop() - editText.getScrollY();
        final float targetX = layout.getPrimaryHorizontal(sel) + editText.getTotalPaddingLeft() - editText.getScrollX();
        final float targetTop = layout.getLineTop(line) + dy + AndroidUtilities.dp(2);
        final float targetBottom = layout.getLineBottom(line) + dy - AndroidUtilities.dp(2);

        if (!cursorReady) {
            curX = targetX;
            curTop = targetTop;
            curBottom = targetBottom;
            cursorReady = true;
        } else {
            // frame-rate independent exponential easing (time constant about 55 ms)
            final float k = 1f - (float) Math.exp(-dtMs / 55.0);
            curX += (targetX - curX) * k;
            curTop += (targetTop - curTop) * k;
            curBottom += (targetBottom - curBottom) * k;
        }

        int alpha = 255;
        if (cursorBlink && now - lastTypeTime > 600) {
            // soft breathing while idle, one cycle per 1.1 s; solid while typing
            final double phase = 2.0 * Math.PI * (now % 1100) / 1100.0;
            final float wave = 0.5f + 0.5f * (float) Math.cos(phase);
            alpha = (int) (255f * (0.15f + 0.85f * wave));
        }

        final float half = AndroidUtilities.dp(1);
        cursorPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        cursorPaint.setAlpha(alpha);
        canvas.drawRoundRect(curX - half, curTop, curX + half, curBottom, half, half, cursorPaint);
        return true;
    }

    /** Draws one character centred on (cx, cy). */
    private void drawChar(Canvas c, String ch, float cx, float cy, float scale, float rot, int alpha, int color) {
        if (alpha <= 0 || scale <= 0f) {
            return;
        }
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float w = paint.measureText(ch);
        paint.setColor(color);
        paint.setAlpha(Math.min(255, alpha));
        c.save();
        c.translate(cx, cy);
        c.rotate(rot);
        c.scale(scale, scale);
        c.drawText(ch, -w / 2f, -fm.ascent / 2f, paint);
        c.restore();
    }
}
