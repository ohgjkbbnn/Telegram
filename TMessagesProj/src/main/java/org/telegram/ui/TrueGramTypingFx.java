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
 * - new letters appear with an animation (insert style);
 * - deleted letters break apart with a chosen effect (delete style);
 * - smooth cursor (cursor mode).
 * Call {@link #attach(EditText)} once for the message input field.
 */
public final class TrueGramTypingFx {

    // insert (typing) styles
    public static final int STYLE_OFF = 0;
    public static final int STYLE_RISE = 1;
    public static final int STYLE_POP = 2;
    public static final int STYLE_DROP = 3;
    public static final String[] STYLE_NAMES = {"Выключена", "Всплытие", "Пружина", "Падение"};

    // delete styles
    public static final int DEL_OFF = 0;
    public static final int DEL_DUST = 1;
    public static final int DEL_GLASS = 2;
    public static final int DEL_SQUARES = 3;
    public static final int DEL_BUBBLES = 4;
    public static final int DEL_SMOKE = 5;
    public static final String[] DELETE_NAMES = {"Выключена", "Частицы", "Стекло", "Квадратики", "Пузырьки", "Дым"};

    // cursor modes
    public static final int CURSOR_OFF = 0;
    public static final int CURSOR_GLIDE = 1;
    public static final int CURSOR_GLIDE_BLINK = 2;
    public static final String[] CURSOR_NAMES = {"Обычный", "Плавное движение", "Плавное движение и мягкое мигание"};

    private static final String PREFS_NAME = "truegram";
    private static final String KEY_STYLE = "typing_anim";
    private static final String KEY_DELETE = "delete_anim";
    private static final String KEY_CURSOR = "smooth_cursor";
    private static final int MAX_GLYPHS_PER_INPUT = 12;

    private static final DecelerateInterpolator DECELERATE = new DecelerateInterpolator(1.6f);
    private static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator(2.2f);
    private static final BounceInterpolator BOUNCE = new BounceInterpolator();

    private static final int K_LETTER = 0;
    private static final int K_DOT = 1;
    private static final int K_SQUARE = 2;
    private static final int K_SHARD = 3;
    private static final int K_SMOKE = 4;

    private static int readInt(String key, int def, int count) {
        try {
            final int v = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(key, def);
            return v >= 0 && v < count ? v : def;
        } catch (Throwable e) {
            return def;
        }
    }

    private static void writeInt(String key, int value) {
        try {
            ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(key, value)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static int getStyle() {
        return readInt(KEY_STYLE, STYLE_OFF, STYLE_NAMES.length);
    }

    public static void setStyle(int style) {
        writeInt(KEY_STYLE, style >= 0 && style < STYLE_NAMES.length ? style : STYLE_OFF);
    }

    public static int getDeleteStyle() {
        return readInt(KEY_DELETE, DEL_OFF, DELETE_NAMES.length);
    }

    public static void setDeleteStyle(int style) {
        writeInt(KEY_DELETE, style >= 0 && style < DELETE_NAMES.length ? style : DEL_OFF);
    }

    public static int getCursorMode() {
        return readInt(KEY_CURSOR, CURSOR_OFF, CURSOR_NAMES.length);
    }

    public static void setCursorMode(int mode) {
        writeInt(KEY_CURSOR, mode >= 0 && mode < CURSOR_NAMES.length ? mode : CURSOR_OFF);
    }

    /** A newly typed letter: hidden with a transparent span while the overlay draws it. */
    private static final class Glyph {
        ForegroundColorSpan span;
        long start;
        long delay;
        long duration;
        int style;
    }

    /** A piece of the deletion effect. */
    private static final class Particle {
        int kind;
        String ch;
        int color;
        long start;
        long life;
        float x0, y0;
        float vx, vy;
        float gravity;
        float rot, rotSpeed;
        float size;
        float wobble;
        float clipL, clipT, clipR, clipB;
    }

    private final EditText editText;
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Glyph> glyphs = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final float[] pos = new float[2];
    private final Drawable overlay;
    private final TextWatcher watcher;

    private int pendingStart;
    private int pendingCount;
    private int pendingBefore;

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
                final int del = getDeleteStyle();
                // only a real small deletion (Backspace, Delete): not a replacement, emoji insert or whole-text setText
                if (count <= 0 || count > 3 || after != 0 || del == DEL_OFF) {
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
                for (int i = start; i < end; i++) {
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
                    spawnDeleted(ch, cx, cy, del, color, now);
                }
                if (!particles.isEmpty()) {
                    TrueGramTypingFx.this.editText.postInvalidateOnAnimation();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                pendingStart = start;
                pendingCount = count;
                pendingBefore = before;
                lastTypeTime = SystemClock.uptimeMillis();
            }

            @Override
            public void afterTextChanged(Editable s) {
                final int start = pendingStart;
                final int count = pendingCount;
                pendingCount = 0;
                final int style = getStyle();
                // only typing a letter or two: emoji, pasted text and whole-text replacement are left alone
                if (count <= 0 || count > 2 || pendingBefore != 0 || style == STYLE_OFF) {
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
                    g.duration = style == STYLE_POP ? 320L : 300L;
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

    /** Attaches the effects to an input field. Call once per field. */
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

    private float rndSigned(float amount) {
        return (random.nextFloat() - 0.5f) * 2f * amount;
    }

    private Particle newParticle(int kind, String ch, int color, long now, long life) {
        final Particle p = new Particle();
        p.kind = kind;
        p.ch = ch;
        p.color = color;
        p.start = now;
        p.life = life;
        return p;
    }

    /** Gives the particle a speed in the given direction; a zero direction means a random one. */
    private void launch(Particle p, float dirX, float dirY, float minSpeed, float maxSpeed) {
        final double len = Math.sqrt(dirX * dirX + dirY * dirY);
        float nx;
        float ny;
        if (len < 1e-3) {
            final double a = random.nextDouble() * Math.PI * 2;
            nx = (float) Math.cos(a);
            ny = (float) Math.sin(a);
        } else {
            nx = (float) (dirX / len);
            ny = (float) (dirY / len);
        }
        final float speed = minSpeed + random.nextFloat() * (maxSpeed - minSpeed);
        p.vx = nx * speed;
        p.vy = ny * speed;
    }

    /** Breaks a deleted character (centre cx, cy) into pieces for the chosen effect. */
    private void spawnDeleted(String ch, float cx, float cy, int mode, int color, long now) {
        final float dp = AndroidUtilities.density;
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float w = paint.measureText(ch);
        // glyph box in local coordinates (origin = centre, baseline at -ascent / 2)
        final float left = -w / 2f;
        final float right = w / 2f;
        final float top = fm.ascent / 2f;
        final float bottom = -fm.ascent / 2f + fm.descent;
        final float midY = (top + bottom) / 2f;

        switch (mode) {
            case DEL_GLASS:
                for (int q = 0; q < 4; q++) {
                    final int qx = q & 1;
                    final int qy = q >> 1;
                    final Particle s = newParticle(K_SHARD, ch, color, now, 520 + random.nextInt(120));
                    s.x0 = cx;
                    s.y0 = cy;
                    s.clipL = qx == 0 ? left : 0f;
                    s.clipR = qx == 0 ? 0f : right;
                    s.clipT = qy == 0 ? top : midY;
                    s.clipB = qy == 0 ? midY : bottom;
                    launch(s, (qx - 0.5f) * w, (qy - 0.5f) * (bottom - top), 50f * dp, 110f * dp);
                    s.gravity = 380f * dp;
                    s.rotSpeed = rndSigned(10f);
                    particles.add(s);
                }
                break;

            case DEL_SQUARES: {
                final int cols = 4;
                final int rows = 3;
                final float cellW = w / cols;
                final float cellH = (bottom - top) / rows;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        final Particle s = newParticle(K_SQUARE, null, color, now, 440 + random.nextInt(160));
                        final float lx = left + cellW * (c + 0.5f);
                        final float ly = top + cellH * (r + 0.5f);
                        s.x0 = cx + lx;
                        s.y0 = cy + ly;
                        s.size = Math.min(cellW, cellH) * 0.8f;
                        launch(s, lx + rndSigned(cellW), ly + rndSigned(cellH), 60f * dp, 180f * dp);
                        s.gravity = 200f * dp;
                        particles.add(s);
                    }
                }
                break;
            }

            case DEL_BUBBLES: {
                final Particle letter = newParticle(K_LETTER, ch, color, now, 700);
                letter.x0 = cx;
                letter.y0 = cy;
                letter.vy = -20f * dp;
                particles.add(letter);
                for (int i = 0; i < 6; i++) {
                    final Particle b = newParticle(K_DOT, null, color, now, 650 + random.nextInt(300));
                    b.x0 = cx + rndSigned(w * 0.4f);
                    b.y0 = cy + rndSigned((bottom - top) * 0.4f);
                    b.size = dp * (1.5f + random.nextFloat() * 2.5f);
                    b.vy = -(50f + random.nextFloat() * 70f) * dp;
                    b.wobble = dp * (2f + random.nextFloat() * 3f);
                    b.rotSpeed = random.nextFloat() * 6.28f;
                    particles.add(b);
                }
                break;
            }

            case DEL_SMOKE: {
                final Particle smoke = newParticle(K_SMOKE, ch, color, now, 720);
                smoke.x0 = cx;
                smoke.y0 = cy;
                smoke.vx = rndSigned(10f) * dp;
                smoke.vy = -18f * dp;
                particles.add(smoke);
                break;
            }

            default: {
                // DEL_DUST: the letter floats up, dust scatters around it
                final Particle letter = newParticle(K_LETTER, ch, color, now, 520);
                letter.x0 = cx;
                letter.y0 = cy;
                letter.vx = rndSigned(60f) * dp;
                letter.vy = -(40f + random.nextFloat() * 50f) * dp;
                letter.rotSpeed = rndSigned(12f);
                particles.add(letter);
                for (int i = 0; i < 8; i++) {
                    final Particle d = newParticle(K_DOT, null, color, now, 420 + random.nextInt(220));
                    d.x0 = cx + rndSigned(w / 3f);
                    d.y0 = cy + rndSigned((bottom - top) / 3f);
                    d.size = dp * (1.2f + random.nextFloat() * 1.4f);
                    launch(d, rndSigned(1f), rndSigned(1f), 60f * dp, 140f * dp);
                    particles.add(d);
                }
                break;
            }
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

        // typed letters
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
            // alpha eases in with a smoothstep curve, so the letter never pops in hard
            final float alpha = tc * tc * (3f - 2f * tc);
            if (g.style == STYLE_POP) {
                scale = Math.max(0f, OVERSHOOT.getInterpolation(tc));
            } else if (g.style == STYLE_DROP) {
                dy = -(1f - BOUNCE.getInterpolation(tc)) * AndroidUtilities.dp(14);
                scale = 0.85f + 0.15f * tc;
            } else {
                final float p = DECELERATE.getInterpolation(tc);
                dy = (1f - p) * AndroidUtilities.dp(10);
                scale = 0.7f + 0.3f * p;
            }
            final String ch = String.valueOf(text.charAt(offset));
            final float cx = pos[0] + paint.measureText(ch) / 2f;
            final float cy = pos[1] + fm.ascent / 2f + dy;
            drawChar(canvas, ch, cx, cy, scale, 0f, (int) (alpha * 255f), color);
        }

        // deleted pieces
        for (int i = particles.size() - 1; i >= 0; i--) {
            final Particle p = particles.get(i);
            final long age = now - p.start;
            if (age >= p.life) {
                particles.remove(i);
                continue;
            }
            busy = true;
            final float t = age / 1000f;
            final float k = age / (float) p.life;
            final float fade = 1f - k;
            final int alpha = (int) (fade * fade * 255f);
            float x = p.x0 + p.vx * t;
            final float y = p.y0 + p.vy * t + 0.5f * p.gravity * t * t;
            switch (p.kind) {
                case K_LETTER:
                    drawChar(canvas, p.ch, x, y, 1f - 0.5f * k, p.rot + p.rotSpeed * t, alpha, p.color);
                    break;
                case K_SHARD:
                    drawCharClipped(canvas, p.ch, x, y, 1f - 0.2f * k, p.rot + p.rotSpeed * t, alpha, p.color,
                            p.clipL, p.clipT, p.clipR, p.clipB);
                    break;
                case K_SMOKE:
                    drawChar(canvas, p.ch, x, y, 1f + 0.9f * k, 0f, (int) (fade * 0.6f * 255f), p.color);
                    break;
                case K_SQUARE: {
                    final float h = p.size / 2f;
                    dotPaint.setColor(p.color);
                    dotPaint.setAlpha(alpha);
                    canvas.drawRect(x - h, y - h, x + h, y + h, dotPaint);
                    break;
                }
                default: {
                    if (p.wobble > 0f) {
                        x += (float) Math.sin(t * 8f + p.rotSpeed) * p.wobble;
                    }
                    dotPaint.setColor(p.color);
                    dotPaint.setAlpha(alpha);
                    canvas.drawCircle(x, y, p.size * (1f - 0.5f * k), dotPaint);
                    break;
                }
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

    /** Draws one character centred on (cx, cy), visible only inside the clip box given in local coordinates. */
    private void drawCharClipped(Canvas c, String ch, float cx, float cy, float scale, float rot, int alpha, int color,
                                 float clipL, float clipT, float clipR, float clipB) {
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
        c.clipRect(clipL, clipT, clipR, clipB);
        c.drawText(ch, -w / 2f, -fm.ascent / 2f, paint);
        c.restore();
    }
}
