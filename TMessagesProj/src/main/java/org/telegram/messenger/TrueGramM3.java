package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TrueGramConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * TrueGram: small Material 3 style UI kit (tonal grouped cards, M3 switch, section labels).
 * Uses only plain Android views + Telegram theme colors, so it follows light/dark themes.
 */
public class TrueGramM3 {

    public static final int SHAPE_SINGLE = 0;
    public static final int SHAPE_TOP = 1;
    public static final int SHAPE_MIDDLE = 2;
    public static final int SHAPE_BOTTOM = 3;

    public static final int TRAILING_NONE = 0;
    public static final int TRAILING_SWITCH = 1;
    public static final int TRAILING_COLOR = 2;
    public static final int TRAILING_CHEVRON = 3;

    private static int dp(int v) {
        return AndroidUtilities.dp(v);
    }

    public static boolean isM3() {
        return TrueGramConfig.isM3Style();
    }

    private static Drawable classicBackground() {
        final int ripple = ColorUtils.setAlphaComponent(primary(), 48);
        return new RippleDrawable(ColorStateList.valueOf(ripple),
                new android.graphics.drawable.ColorDrawable(surface()),
                new android.graphics.drawable.ColorDrawable(Color.WHITE));
    }

    public static void bindLabel(TextView tv, String text) {
        tv.setText(text);
        tv.setTextColor(primary());
        if (isM3()) {
            tv.setPadding(dp(32), dp(22), dp(32), dp(8));
            tv.setBackgroundColor(Color.TRANSPARENT);
        } else {
            tv.setPadding(dp(20), dp(16), dp(20), dp(6));
            tv.setBackgroundColor(surface());
        }
    }

    public static void bindNote(TextView tv, String text) {
        tv.setText(text);
        tv.setTextColor(onSurfaceVariant());
        if (isM3()) {
            tv.setPadding(dp(32), dp(8), dp(32), dp(4));
        } else {
            tv.setPadding(dp(20), dp(8), dp(20), dp(8));
        }
    }

    public static int primary() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
    }

    public static int surface() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    public static int onSurface() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    public static int onSurfaceVariant() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
    }

    private static GradientDrawable roundedRect(int color, float top, float bottom) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return d;
    }

    /** Grouped card background: big outer corners, small inner corners (M3 expressive lists). */
    public static Drawable cardBackground(int shape) {
        final float outer = dp(24);
        final float inner = dp(6);
        final float top = (shape == SHAPE_TOP || shape == SHAPE_SINGLE) ? outer : inner;
        final float bottom = (shape == SHAPE_BOTTOM || shape == SHAPE_SINGLE) ? outer : inner;
        final GradientDrawable fill = roundedRect(surface(), top, bottom);
        final GradientDrawable mask = roundedRect(Color.WHITE, top, bottom);
        final int ripple = ColorUtils.setAlphaComponent(primary(), 48);
        return new RippleDrawable(ColorStateList.valueOf(ripple), fill, mask);
    }

    // ------------------------------------------------------------------ section label

    public static TextView createSectionLabel(Context context) {
        final TextView tv = new TextView(context);
        tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(primary());
        tv.setPadding(dp(32), dp(22), dp(32), dp(8));
        tv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    public static TextView createSupportingText(Context context) {
        final TextView tv = new TextView(context);
        tv.setTextSize(13);
        tv.setLineSpacing(dp(2), 1f);
        tv.setTextColor(onSurfaceVariant());
        tv.setPadding(dp(32), dp(8), dp(32), dp(4));
        tv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    public static View createSpacer(Context context, int heightDp) {
        final View v = new View(context);
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return v;
    }

    // ------------------------------------------------------------------ hero card

    public static View createHero(Context context, String emoji, String title, String subtitle) {
        final FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(dp(16), dp(8), dp(16), dp(8));
        wrapper.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(24), dp(24), dp(24));
        card.setBackground(roundedRect(ColorUtils.setAlphaComponent(primary(), 38), dp(28), dp(28)));

        final TextView icon = new TextView(context);
        icon.setText(emoji);
        icon.setTextSize(36);
        card.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(26);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(onSurface());
        final LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        card.addView(titleView, tlp);

        final TextView sub = new TextView(context);
        sub.setText(subtitle);
        sub.setTextSize(14);
        sub.setTextColor(onSurfaceVariant());
        final LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        card.addView(sub, slp);

        wrapper.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    // ------------------------------------------------------------------ vector icons (Material, 24x24 path data)

    public static final String ICON_SETTINGS = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    public static final String ICON_PALETTE = "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";
    public static final String ICON_AI = "M12 2C10.9 2 10 2.9 10 4C10 4.74 10.4 5.38 11 5.72V7H8C6.34 7 5 8.34 5 10V11C3.9 11 3 11.9 3 13S3.9 15 5 15V16C5 17.66 6.34 19 8 19H9V20C9 21.1 9.9 22 11 22S13 21.1 13 20V19H14V20C14 21.1 14.9 22 16 22S18 21.1 18 20V19C19.66 19 21 17.66 21 16V15C22.1 15 23 14.1 23 13S22.1 11 21 11V10C21 8.34 19.66 7 18 7H13V5.72C13.6 5.38 14 4.74 14 4C14 2.9 13.1 2 12 2ZM8.5 10C9.33 10 10 10.67 10 11.5S9.33 13 8.5 13 7 12.33 7 11.5 7.67 10 8.5 10ZM15.5 10C16.33 10 17 10.67 17 11.5S16.33 13 15.5 13 14 12.33 14 11.5 14.67 10 15.5 10ZM9 15H15V16C15 16.55 14.55 17 14 17H10C9.45 17 9 16.55 9 16V15Z";
    public static final String ICON_DELETE = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";
    public static final String ICON_EYE = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    public static final String ICON_EDIT = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z";
    public static final String ICON_BOLD = "M15.6 10.79c.97-.67 1.65-1.77 1.65-2.79 0-2.26-1.75-4-4-4H7v14h7.04c2.09 0 3.71-1.7 3.71-3.79 0-1.52-.86-2.82-2.15-3.42zM10 6.5h3c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5h-3v-3zm3.5 9H10v-3h3.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5z";
    public static final String ICON_SYNC = "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z";
    public static final String ICON_PHOTO = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z";
    public static final String ICON_VIDEO = "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z";
    public static final String ICON_INFO = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z";
    public static final String ICON_SCREEN = "M3 5v4h2V5h4V3H5c-1.1 0-2 .9-2 2zm2 10H3v4c0 1.1.9 2 2 2h4v-2H5v-4zm14 4h-4v2h4c1.1 0 2-.9 2-2v-4h-2v4zm0-16h-4v2h4v4h2V5c0-1.1-.9-2-2-2z";
    public static final String ICON_PIN = "M16 9V4h1c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z";
    public static final String ICON_MIC = "M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z";
    public static final String ICON_MUSIC = "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z";
    public static final String ICON_PERSON = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    public static final String ICON_PEOPLE = "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z";
    public static final String ICON_CHAT = "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z";
    public static final String ICON_OPACITY = "M17.66 8L12 2.35 6.34 8C4.78 9.56 4 11.64 4 13.64s.78 4.11 2.34 5.67 3.61 2.35 5.66 2.35 4.1-.79 5.66-2.35S20 15.64 20 13.64 19.22 9.56 17.66 8zM6 14c.01-2 .62-3.27 1.76-4.4L12 5.27l4.24 4.38C17.38 10.77 17.99 12 18 14H6z";
    public static final String ICON_GHOST = "M12 2C7.58 2 4 5.58 4 10V22L6.67 19.5L9.33 22L12 19.5L14.67 22L17.33 19.5L20 22V10C20 5.58 16.42 2 12 2ZM9 8.4A1.6 1.6 0 1 1 9 11.6A1.6 1.6 0 1 1 9 8.4ZM15 8.4A1.6 1.6 0 1 1 15 11.6A1.6 1.6 0 1 1 15 8.4Z";
    public static final String ICON_CLOSE = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
    public static final String ICON_TITLE = "M5 4v3h5.5v12h3V7H19V4z";
    public static final String ICON_MUTE = "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z";

    private static final java.util.HashMap<String, Path> PATH_CACHE = new java.util.HashMap<>();

    private static Path pathFor(String data) {
        Path p = PATH_CACHE.get(data);
        if (p == null) {
            try {
                p = PathParser.createPathFromPathData(data);
            } catch (Exception e) {
                p = null;
            }
            if (p == null) {
                p = new Path();
            }
            PATH_CACHE.put(data, p);
        }
        return p;
    }

    /** Draws a 24x24 Material path scaled to the view (minus inset), tinted with a color. */
    public static class IconView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path scaled = new Path();
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();
        private final int insetPx;
        private Path source;

        public IconView(Context context, int insetDp) {
            super(context);
            insetPx = dp(insetDp);
            paint.setStyle(Paint.Style.FILL);
        }

        public void setIcon(String pathData, int color) {
            source = pathData == null ? null : pathFor(pathData);
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (source == null) {
                return;
            }
            final float size = Math.min(getWidth(), getHeight()) - insetPx * 2f;
            if (size <= 0) {
                return;
            }
            final float scale = size / 24f;
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            scaled.reset();
            source.transform(matrix, scaled);
            canvas.drawPath(scaled, paint);
        }
    }

    // ------------------------------------------------------------------ M3 switch

    public static class M3Switch extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private boolean checked;
        private ValueAnimator animator;

        public M3Switch(Context context) {
            super(context);
        }

        public void setChecked(boolean value, boolean animate) {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            checked = value;
            final float target = value ? 1f : 0f;
            if (!animate) {
                progress = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(220);
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        public boolean isChecked() {
            return checked;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(44), dp(26));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = dp(44);
            final float h = dp(26);
            final float p = progress;

            final int primary = primary();
            final int muted = onSurfaceVariant();
            // off state is deliberately quiet: dark track, thin dim outline, dim thumb
            final int offTrack = ColorUtils.blendARGB(surface(), muted, 0.06f);
            final int offThumb = ColorUtils.blendARGB(surface(), muted, 0.55f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.blendARGB(offTrack, primary, p));
            canvas.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, paint);

            if (p < 1f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(ColorUtils.setAlphaComponent(offThumb, (int) (110 * (1f - p))));
                final float inset = dp(1);
                canvas.drawRoundRect(inset, inset, w - inset, h - inset, h / 2f - inset, h / 2f - inset, paint);
            }

            final float radius = dp(6) + (dp(10) - dp(6)) * p;
            final float cx = dp(13) + (w - dp(13) - dp(13)) * p;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.blendARGB(offThumb, Color.WHITE, p));
            canvas.drawCircle(cx, h / 2f, radius, paint);
        }
    }

    // ------------------------------------------------------------------ list item

    public static class M3Item extends FrameLayout {

        private final LinearLayout card;
        private final IconView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final M3Switch switchView;
        private final View colorDot;
        private final TextView chevron;
        private final View divider;
        private final LinearLayout.LayoutParams textsParams;

        public M3Item(Context context) {
            super(context);
            setPadding(dp(16), 0, dp(16), dp(2));
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setClickable(true);
            addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            iconView = new IconView(context, 8);
            card.addView(iconView, new LinearLayout.LayoutParams(dp(40), dp(40)));

            final LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textsParams.leftMargin = dp(16);
            textsParams.rightMargin = dp(12);
            card.addView(texts, textsParams);

            titleView = new TextView(context);
            titleView.setTextSize(16);
            texts.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(13);
            final LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = dp(2);
            texts.addView(subtitleView, sp);

            switchView = new M3Switch(context);
            card.addView(switchView, new LinearLayout.LayoutParams(dp(44), dp(26)));

            colorDot = new View(context);
            card.addView(colorDot, new LinearLayout.LayoutParams(dp(28), dp(28)));

            chevron = new TextView(context);
            chevron.setText("›");
            chevron.setTextSize(26);
            card.addView(chevron, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            divider = new View(context);
            final FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM);
            dlp.leftMargin = dp(20);
            addView(divider, dlp);
        }

        public void bind(int shape, String emoji, String title, String subtitle, int trailing) {
            final boolean m3 = isM3();
            if (m3) {
                setPadding(dp(16), 0, dp(16), dp(2));
                card.setPadding(dp(16), dp(14), dp(16), dp(14));
                card.setBackground(cardBackground(shape));
                textsParams.leftMargin = dp(16);
                divider.setVisibility(GONE);
            } else {
                setPadding(0, 0, 0, 0);
                card.setPadding(dp(20), dp(12), dp(20), dp(12));
                card.setBackground(classicBackground());
                textsParams.leftMargin = 0;
                divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                divider.setVisibility(shape == SHAPE_TOP || shape == SHAPE_MIDDLE ? VISIBLE : GONE);
            }

            iconView.setVisibility(m3 ? VISIBLE : GONE);
            iconView.setIcon(emoji, primary());
            final GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(ColorUtils.setAlphaComponent(primary(), 40));
            iconView.setBackground(iconBg);

            titleView.setText(title);
            titleView.setTextColor(onSurface());

            if (subtitle == null || subtitle.isEmpty()) {
                subtitleView.setVisibility(GONE);
            } else {
                subtitleView.setVisibility(VISIBLE);
                subtitleView.setText(subtitle);
                subtitleView.setTextColor(onSurfaceVariant());
            }

            switchView.setVisibility(trailing == TRAILING_SWITCH ? VISIBLE : GONE);
            colorDot.setVisibility(trailing == TRAILING_COLOR ? VISIBLE : GONE);
            chevron.setVisibility(trailing == TRAILING_CHEVRON ? VISIBLE : GONE);
            chevron.setTextColor(onSurfaceVariant());
        }

        public void setChecked(boolean checked, boolean animate) {
            switchView.setChecked(checked, animate);
        }

        public void setColor(int color) {
            final GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            dot.setStroke(dp(1), ColorUtils.setAlphaComponent(onSurfaceVariant(), 120));
            colorDot.setBackground(dot);
        }

        @Override
        public void setOnClickListener(OnClickListener l) {
            if (card != null) {
                card.setOnClickListener(l);
            } else {
                super.setOnClickListener(l);
            }
        }
    }

    // ------------------------------------------------------------------ bottom bar editor

    /** Preview of the bottom bar: tap a tab to hide it (dashed + dimmed) or bring it back. */
    public static class TabsEditorView extends FrameLayout {

        public interface Listener {
            void onToggle(int tabIndex);
        }

        private static final String[] ICONS = {ICON_CHAT, ICON_PEOPLE, ICON_SETTINGS, ICON_PERSON};
        private static final String[] NAMES = {"Чаты", "Контакты", "Настройки", "Профиль"};

        private final LinearLayout card;
        private final LinearLayout[] slots = new LinearLayout[4];
        private final IconView[] icons = new IconView[4];
        private final TextView[] labels = new TextView[4];

        public TabsEditorView(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER);
            addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int i = 0; i < 4; i++) {
                final LinearLayout slot = new LinearLayout(context);
                slot.setOrientation(LinearLayout.VERTICAL);
                slot.setGravity(Gravity.CENTER);
                slot.setPadding(dp(4), dp(14), dp(4), dp(12));

                final IconView icon = new IconView(context, 0);
                icon.setIcon(ICONS[i], primary());
                slot.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

                final TextView label = new TextView(context);
                label.setText(NAMES[i]);
                label.setTextSize(12);
                label.setSingleLine(true);
                label.setGravity(Gravity.CENTER);
                final LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = dp(4);
                slot.addView(label, llp);

                final LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                slp.leftMargin = dp(3);
                slp.rightMargin = dp(3);
                card.addView(slot, slp);

                slots[i] = slot;
                icons[i] = icon;
                labels[i] = label;
            }
        }

        public void bind(boolean[] hidden, Listener listener) {
            final boolean m3 = isM3();
            setPadding(m3 ? dp(16) : 0, dp(2), m3 ? dp(16) : 0, dp(2));
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            card.setBackground(roundedRect(surface(), m3 ? dp(28) : 0, m3 ? dp(28) : 0));

            for (int i = 0; i < 4; i++) {
                final boolean isHidden = hidden[i];
                final GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(18));
                if (isHidden) {
                    bg.setColor(Color.TRANSPARENT);
                    bg.setStroke(dp(2), ColorUtils.setAlphaComponent(onSurfaceVariant(), 160), dp(6), dp(4));
                } else {
                    bg.setColor(ColorUtils.setAlphaComponent(primary(), i == 0 ? 56 : 34));
                }
                slots[i].setBackground(bg);

                final float a = isHidden ? 0.4f : 1f;
                icons[i].setIcon(ICONS[i], isHidden ? onSurfaceVariant() : primary());
                icons[i].setAlpha(a);
                labels[i].setAlpha(a);
                labels[i].setTextColor(isHidden ? onSurfaceVariant() : onSurface());

                final int index = i;
                slots[i].setOnClickListener(v -> listener.onToggle(index));
            }
        }
    }

}
