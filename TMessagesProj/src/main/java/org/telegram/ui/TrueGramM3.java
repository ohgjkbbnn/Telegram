package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
            setMeasuredDimension(dp(52), dp(32));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = dp(52);
            final float h = dp(32);
            final float p = progress;

            final int primary = primary();
            final int outline = onSurfaceVariant();
            final int offTrack = ColorUtils.blendARGB(surface(), outline, 0.12f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.blendARGB(offTrack, primary, p));
            canvas.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, paint);

            if (p < 1f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(ColorUtils.setAlphaComponent(outline, (int) (255 * (1f - p))));
                final float inset = dp(1);
                canvas.drawRoundRect(inset, inset, w - inset, h - inset, h / 2f - inset, h / 2f - inset, paint);
            }

            final float radius = dp(8) + (dp(12) - dp(8)) * p;
            final float cx = dp(16) + (w - dp(16) - dp(16)) * p;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.blendARGB(outline, Color.WHITE, p));
            canvas.drawCircle(cx, h / 2f, radius, paint);
        }
    }

    // ------------------------------------------------------------------ list item

    public static class M3Item extends FrameLayout {

        private final LinearLayout card;
        private final TextView iconView;
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

            iconView = new TextView(context);
            iconView.setGravity(Gravity.CENTER);
            iconView.setTextSize(18);
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
            card.addView(switchView, new LinearLayout.LayoutParams(dp(52), dp(32)));

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
            iconView.setText(emoji);
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

        private static final String[] EMOJI = {"💬", "👥", "⚙️", "👤"};
        private static final String[] NAMES = {"Чаты", "Контакты", "Настройки", "Профиль"};

        private final LinearLayout card;
        private final LinearLayout[] slots = new LinearLayout[4];
        private final TextView[] icons = new TextView[4];
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

                final TextView icon = new TextView(context);
                icon.setText(EMOJI[i]);
                icon.setTextSize(24);
                icon.setGravity(Gravity.CENTER);
                slot.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
                icons[i].setAlpha(a);
                labels[i].setAlpha(a);
                labels[i].setTextColor(isHidden ? onSurfaceVariant() : onSurface());

                final int index = i;
                slots[i].setOnClickListener(v -> listener.onToggle(index));
            }
        }
    }

}
