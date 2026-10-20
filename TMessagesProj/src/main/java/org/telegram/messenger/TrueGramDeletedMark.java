package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.style.ReplacementSpan;

import androidx.core.graphics.PathParser;

import org.telegram.ui.TrueGramM3;

/** TrueGram: how the "deleted" mark next to the message time looks (text or a small SVG icon). */
public class TrueGramDeletedMark {

    public static final int MODE_TEXT = 0;
    public static final int MODE_TRASH = 1;
    public static final int MODE_CROSS = 2;
    public static final int MODE_EYE = 3;

    public static final String[] NAMES = {
            "Текст «удалено»",
            "Корзинка",
            "Крестик",
            "Глаз"
    };

    private static int mode = -1;
    private static final java.util.HashMap<Integer, Path> PATHS = new java.util.HashMap<>();

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("truegram_deleted_mark", Context.MODE_PRIVATE);
    }

    public static int getMode() {
        if (mode < 0) {
            final int m = prefs().getInt("mode", MODE_TEXT);
            mode = Math.max(0, Math.min(NAMES.length - 1, m));
        }
        return mode;
    }

    public static void setMode(int m) {
        mode = Math.max(0, Math.min(NAMES.length - 1, m));
        prefs().edit().putInt("mode", mode).apply();
    }

    /** SVG path (24x24) for a mode; the text mode uses a "title" glyph for the settings UI. */
    public static String iconPath(int m) {
        switch (m) {
            case MODE_TRASH:
                return TrueGramM3.ICON_DELETE;
            case MODE_CROSS:
                return TrueGramM3.ICON_CLOSE;
            case MODE_EYE:
                return TrueGramM3.ICON_EYE;
            default:
                return TrueGramM3.ICON_TITLE;
        }
    }

    private static Path pathFor(int m) {
        Path p = PATHS.get(m);
        if (p == null) {
            try {
                p = PathParser.createPathFromPathData(iconPath(m));
            } catch (Exception e) {
                p = null;
            }
            if (p == null) {
                p = new Path();
            }
            PATHS.put(m, p);
        }
        return p;
    }

    public static MarkSpan createSpan(int m, int color, float textSizePx) {
        return new MarkSpan(pathFor(m), color, textSizePx);
    }

    /** Small tinted icon drawn in the time line, replaces one placeholder character. */
    public static class MarkSpan extends ReplacementSpan {

        private final Path source;
        private final Path scaled = new Path();
        private final Matrix matrix = new Matrix();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float textSize;
        private final int sizePx;
        private final int gapPx;

        MarkSpan(Path source, int color, float textSize) {
            this.source = source;
            this.textSize = textSize;
            this.sizePx = Math.max(1, Math.round(textSize * 1.2f));
            this.gapPx = Math.max(1, Math.round(textSize * 0.25f));
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
        }

        public int getWidthPx() {
            return sizePx + gapPx;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return sizePx + gapPx;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            final float scale = sizePx / 24f;
            final float topY = y - textSize * 0.33f - sizePx / 2f;
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(x, topY);
            scaled.reset();
            source.transform(matrix, scaled);
            canvas.drawPath(scaled, fill);
        }
    }
}
