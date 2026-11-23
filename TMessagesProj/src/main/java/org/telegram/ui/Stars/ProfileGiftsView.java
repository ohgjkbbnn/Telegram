package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.graphics.ColorUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.TrueGramIosDesign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProfileGiftsView extends View implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long dialogId;
    private final View avatarContainer;
    private final ProfileActivity.AvatarImageView avatarImage;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean active = true;
    public void setActive(boolean f) {
        this.active = f;
    }

    public ProfileGiftsView(Context context, int currentAccount, long dialogId, @NonNull View avatarContainer, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;

        this.avatarContainer = avatarContainer;
        this.avatarImage = avatarImage;

        this.resourcesProvider = resourcesProvider;
    }

    public float expandProgress, collapseProgress;
    public boolean isOpening;

    public void setExpandProgress(float progress) {
        if (this.expandProgress != progress) {
            this.expandProgress = progress;
            invalidate();
        }
    }

    public void setCollapseProgress(float progress, boolean isOpening) {
        this.isOpening = isOpening;
        float newProgress = Utilities.clamp01((progress - 0.3f) / 0.7f);
        if (this.collapseProgress != newProgress) {
            this.collapseProgress = newProgress;
            invalidate();
        }
    }

    private float actionBarProgress;

    public void setActionBarActionMode(float progress) {
//        if (Theme.isCurrentThemeDark()) {
//            return;
//        }
        actionBarProgress = progress;
        invalidate();
    }


    private float left, right, cy;
    private float expandY, maxExpandY;
    private final AnimatedFloat rightAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setBounds(float left, float right, float cy, boolean animated, int maxExpandY) {
        boolean changed = Math.abs(left - this.left) > 0.1f || Math.abs(right - this.right) > 0.1f || Math.abs(cy - this.cy) > 0.1f;
        this.left = left;
        this.right = right;
        if (!animated) {
            this.rightAnimated.set(this.right, true);
        }
        this.cy = cy;
        this.maxExpandY = maxExpandY + cy;
        if (changed) {
            invalidate();
        }
    }

    public void setExpandCoords(float y) {
        this.expandY = y;
        invalidate();
    }

    private float progressToInsets = 1f;

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (this.progressToInsets == progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.addView(this);
        }

        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            gift.emojiDrawable.removeView(this);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                update();
            }
        }
    }

    public final class Gift {

        public final long id;
        public final TLRPC.Document document;
        public final long documentId;
        public final int color;
        public final String slug;
        private StarsReactionsSheet.Particles particles;

        public int position = -1;
        /** Current rotation of the gift in degrees: grows by itself (NFT spin) and changes when the gift is turned by hand. */
        public float spin;
        /** Soft colored halo behind the gift (its backdrop color). */
        public Paint haloPaint;
        /** 0..1 brightness: pulses by itself, grows while the gift is dragged. */
        public float glow = 0.5f;
        // User-controlled position and a spring-like scale while this NFT is being moved.
        public float offsetX, offsetY;
        public float dragScale = 1f;
        public final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final Paint bubbleRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public final Paint bubblePatternPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Gift(TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            document = gift.getDocument();
            documentId = document == null ? 0 : document.id;
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            color = backdrop.center_color | 0xFF000000;
            slug = gift.slug;
            initParticles();
        }

        public Gift(TLRPC.TL_emojiStatusCollectible status) {
            id = status.collectible_id;
            document = null;
            documentId = status.document_id;
            color = status.center_color | 0xFF000000;
            slug = status.slug;
            initParticles();
        }

        private void initParticles() {
            particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 6);
            final float gsz = dp(36);
            particles.bounds.set(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f);
        }

        public boolean equals(Gift b) {
            return b != null && b.id == id;
        }

        public RadialGradient gradient;
        public final Matrix gradientMatrix = new Matrix();
        public Paint gradientPaint;
        public AnimatedEmojiDrawable emojiDrawable;
        public AnimatedFloat animatedFloat;

        public final RectF bounds = new RectF();
        public final ButtonBounce bounce = new ButtonBounce(ProfileGiftsView.this);

        public void copy(Gift b) {
            gradient = b.gradient;
            emojiDrawable = b.emojiDrawable;
            gradientPaint = b.gradientPaint;
            animatedFloat = b.animatedFloat;
            particles = b.particles;
            position = b.position;
            spin = b.spin;
            haloPaint = b.haloPaint;
            glow = b.glow;
            offsetX = b.offsetX;
            offsetY = b.offsetY;
            dragScale = b.dragScale;
        }

        public void draw(
                Canvas canvas,
                float cx, float cy,
                float ascale, float rotate,
                float alpha,
                float gradientAlpha
        ) {
            if (alpha <= 0.0f) return;
            final float gsz = dp(45);
            bounds.set(cx - gsz / 2, cy - gsz / 2, cx + gsz / 2, cy + gsz / 2);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotate);
            final float scale = ascale * bounce.getScale(0.1f);
            canvas.scale(scale, scale);
            particles.process();
            particles.draw(canvas, color, alpha);
            if (haloPaint != null) {
                haloPaint.setAlpha((int) (0xFF * alpha * (0.4f + 0.6f * glow)));
                canvas.drawCircle(0, 0, dp(34) * (1f + 0.35f * glow), haloPaint);
            }
            if (gradientPaint != null) {
                gradientPaint.setAlpha((int) (0xFF * alpha * gradientAlpha));
                canvas.drawRect(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f, gradientPaint);
            }
            // Hand-painted 3D glass bubble: tinted volume, dark lower edge and a crisp specular highlight.
            final float bubbleRadius = dp(25);
            final int bubbleTint = ColorUtils.setAlphaComponent(color, (int) (alpha * 105));
            giftBubbleShader(bubblePaint, bubbleTint, bubbleRadius);
            bubblePaint.setAlpha((int) (255 * alpha));
            canvas.drawCircle(0, 0, bubbleRadius, bubblePaint);
            bubbleRimPaint.setStyle(Paint.Style.STROKE);
            bubbleRimPaint.setStrokeWidth(dp(0.8f));
            bubbleRimPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 145)));
            canvas.drawCircle(0, 0, bubbleRadius - dp(0.5f), bubbleRimPaint);
            canvas.save();
            android.graphics.Path bubbleClip = new android.graphics.Path();
            bubbleClip.addCircle(0, 0, bubbleRadius - dp(1), android.graphics.Path.Direction.CW);
            canvas.clipPath(bubbleClip);
            bubblePatternPaint.setStyle(Paint.Style.STROKE);
            bubblePatternPaint.setStrokeWidth(dp(0.8f));
            bubblePatternPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 105)));
            // A subtle orbit pattern inherits the NFT backdrop color instead of using a generic white badge.
            canvas.drawArc(-bubbleRadius * .72f, -bubbleRadius * .48f, bubbleRadius * .72f, bubbleRadius * .96f, 205, 235, false, bubblePatternPaint);
            canvas.drawCircle(-bubbleRadius * .28f, bubbleRadius * .18f, dp(2.1f), bubblePatternPaint);
            canvas.drawCircle(bubbleRadius * .34f, -bubbleRadius * .12f, dp(1.5f), bubblePatternPaint);
            canvas.restore();
            bubblePaint.setShader(new RadialGradient(-bubbleRadius * .38f, -bubbleRadius * .48f, bubbleRadius * .72f,
                    new int[]{ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 210)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 45)), Color.TRANSPARENT},
                    new float[]{0f, .28f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(0, 0, bubbleRadius, bubblePaint);
            bubblePaint.setShader(null);
            if (emojiDrawable != null) {
                final int sz = dp(24);
                emojiDrawable.setBounds(-sz / 2, -sz / 2, sz / 2, sz / 2);
                emojiDrawable.setAlpha((int) (0xFF * alpha));
                emojiDrawable.draw(canvas);
            }
            canvas.restore();
        }
    }

    private static void giftBubbleShader(Paint paint, int tint, float radius) {
        final int mid = ColorUtils.setAlphaComponent(tint, Color.alpha(tint));
        final int dark = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tint, Color.BLACK, .48f), Color.alpha(tint));
        paint.setShader(new RadialGradient(-radius * .30f, -radius * .36f, radius * 1.35f,
                new int[]{ColorUtils.setAlphaComponent(Color.WHITE, 105), mid, dark},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
    }

    private StarsController.GiftsList list;

    public final ArrayList<Gift> oldGifts = new ArrayList<>();
    public final ArrayList<Gift> gifts = new ArrayList<>();
    public final HashSet<Long> giftIds = new HashSet<>();
    public int maxCount;

    public void update() {
        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) {
            return;
        }

        maxCount = MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit;
        oldGifts.clear();
        oldGifts.addAll(gifts);
        gifts.clear();
        giftIds.clear();

        final TLRPC.EmojiStatus emojiStatus;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            emojiStatus = user == null ? null : user.emoji_status;
        } else {
            final TLRPC.User chat = MessagesController.getInstance(currentAccount).getUser(-dialogId);
            emojiStatus = chat == null ? null : chat.emoji_status;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            giftIds.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }
        list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved && savedGift.pinned_to_top && savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final Gift gift = new Gift((TL_stars.TL_starGiftUnique) savedGift.gift);
                    if (!giftIds.contains(gift.id)) {
                        gifts.add(gift);
                        giftIds.add(gift.id);
                    }
                }
            }
        }

        boolean changed = false;
        if (gifts.size() != oldGifts.size()) {
            changed = true;
        } else for (int i = 0; i < gifts.size(); i++) {
            if (!gifts.get(i).equals(oldGifts.get(i))) {
                changed = true;
                break;
            }
        }

        for (int i = 0; i < gifts.size(); i++) {
            final Gift g = gifts.get(i);
            Gift oldGift = null;
            for (int j = 0; j < oldGifts.size(); ++j) {
                if (oldGifts.get(j).id == g.id) {
                    oldGift = oldGifts.get(j);
                    break;
                }
            }

            if (oldGift != null) {
                g.copy(oldGift);
            } else {
                g.gradient = new RadialGradient(0, 0, dp(22.5f), new int[]{g.color, Theme.multAlpha(g.color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                g.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.gradientPaint.setShader(g.gradient);
                g.haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.haloPaint.setShader(new RadialGradient(0, 0, dp(34), new int[]{ColorUtils.setAlphaComponent(g.color, 0xB0), ColorUtils.setAlphaComponent(g.color, 0)}, new float[]{0.3f, 1f}, Shader.TileMode.CLAMP));
                if (g.document != null) {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.document);
                } else {
                    g.emojiDrawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, g.documentId);
                }
                g.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                g.animatedFloat.force(0.0f);
                if (isAttachedToWindow()) {
                    g.emojiDrawable.addView(this);
                }
            }
        }

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            positions.add(i);
        }

        for (int i = 0; i < oldGifts.size(); i++) {
            final Gift g = oldGifts.get(i);
            Gift newGift = null;
            for (int j = 0; j < gifts.size(); ++j) {
                if (gifts.get(j).id == g.id) {
                    newGift = gifts.get(j);
                    break;
                }
            }
            if (newGift == null) {
                g.emojiDrawable.removeView(this);
                g.emojiDrawable = null;
                g.gradient = null;
            } else {
                positions.remove((Object) g.position);
            }
        }

        if (!positions.isEmpty()) {
            BagRandomizer<Integer> bagRandomizer = new BagRandomizer<>(positions);
            for (int i = 0; i < gifts.size(); ++i) {
                final Gift g = gifts.get(i);
                if (g.position == -1) {
                    g.position = bagRandomizer.next();
                }
            }
        }

        if (changed)
            invalidate();
    }

    private final TimeInterpolator giftCollapseXInterpolator = new DecelerateInterpolator();
    private final TimeInterpolator giftCollapseYInterpolator = new LinearInterpolator();

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (gifts.isEmpty() || expandProgress >= 1.0f || collapseProgress <= 0f) return;

        final float realX = avatarContainer.getX();
        final float realY = avatarContainer.getY();
        final float realW = (avatarContainer.getWidth()) * avatarContainer.getScaleX();
        final float realH = (avatarContainer.getHeight()) * avatarContainer.getScaleY();

        final float sz = dpf2(96);
        final float ax = Math.min(realX, (getWidth() - sz) / 2f);
        final float ay = Math.max(realY, (maxExpandY - sz) / 2f);
        final float aw = Math.max(realW, sz);
        final float ah = Math.max(realH, sz);


        canvas.save();
        canvas.clipRect(0, 0, getWidth(), expandY);

        final float acx = ax + aw / 2.0f;
        final float acy = ay + ah / 2.0f;

        final float realCX = realX + realW / 2.0f;
        final float realCY = realY + realH / 2.0f;

        float expandScale = expandY / maxExpandY;
        float closedAlpha = Utilities.clamp01((expandY - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())) / dp(50));

        // Thin white orbit. Only the arc nearest each NFT gets a stronger, localized glow.
        final float ringAlpha = (1f - expandProgress) * (1f - actionBarProgress) * closedAlpha;
        if (ringAlpha > 0f) {
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1f));
            ringPaint.setColor(Color.WHITE);
            ringPaint.setAlpha((int) (105 * ringAlpha));
            final RectF orbit = new RectF(realCX - realW / 2f - dp(2), realCY - realH / 2f - dp(2), realCX + realW / 2f + dp(2), realCY + realH / 2f + dp(2));
            canvas.drawOval(orbit, ringPaint);
            ringPaint.setStrokeWidth(dp(2.2f));
            for (int i = 0; i < gifts.size(); i++) {
                Gift near = gifts.get(i);
                float a = (float) Math.toDegrees(Math.atan2(near.bounds.centerY() - realCY, near.bounds.centerX() - realCX));
                float glowAlpha = ringAlpha * (near == pressedGift ? 1f : .72f);
                ringPaint.setAlpha((int) (220 * glowAlpha));
                canvas.drawArc(orbit, a - 19f, 38f, false, ringPaint);
            }
        }

        final float pulse = 0.5f + 0.5f * (float) Math.sin(SystemClock.uptimeMillis() / 600.0);

        for (int i = 0; i < gifts.size(); ++i) {
            final Gift gift = gifts.get(i);
            final float enter = gift.animatedFloat.set(1.0f);
            final boolean dragged = gift == pressedGift && rotatingGift;
            final float glowTarget = dragged ? 1f : 0.5f + 0.4f * pulse;
            gift.glow += (glowTarget - gift.glow) * 0.2f;
            float scale = lerp(0.5f, 1.0f, enter) * (1f + 0.25f * Math.max(0f, gift.glow - 0.5f) * 2f);
            final float alpha = enter * (1.0f - expandProgress) * (1.0f - actionBarProgress) * (closedAlpha);

            float gx, gy;
            float delayValue;

            switch (gift.position) {
                case 0: // left
                    gx = acx / 2f - (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 1: // top left
                    gx = acx * 2f / 3f - (dp(6) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0;
                    break;
                case 2: // bottom left
                    gx = acx * 2f / 3f - (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0.9f;
                    break;
                case 3: // right
                    gx = acx * 1.5f + (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 4: // top right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0.9f;
                    break;
                default: // bottom right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0;
                    break;
            }

            final float collapseProgressWithEnter;
            if (isOpening || enter >= 1f) {
                collapseProgressWithEnter = collapseProgress;
            } else {
                collapseProgressWithEnter = Math.min(enter, collapseProgress);
            }

            final float delayFraction = 0.2f;
            final float maxDelayFraction = 1.6f * delayFraction;
            final float intervalFraction = 1f - maxDelayFraction;

            float delay = delayValue * delayFraction;
            float collapse = collapseProgressWithEnter >= 1f - delay ? 1f
                    : Utilities.clamp01((collapseProgressWithEnter - maxDelayFraction + delay) / intervalFraction);
            if (collapse < 1f) {
                gx = AndroidUtilities.lerp(realCX, gx, giftCollapseXInterpolator.getInterpolation(collapse));
                gy = AndroidUtilities.lerp(realCY, gy, giftCollapseYInterpolator.getInterpolation(collapse));
                scale = AndroidUtilities.lerp(scale / 2f, scale, collapse);
            }

            gx += gift.offsetX;
            gy += gift.offsetY;
            gift.dragScale += ((gift == pressedGift && draggingGift ? 1.32f : 1f) - gift.dragScale) * .22f;
            scale *= gift.dragScale;
            if (globalRotation != 0f) {
                double radians = Math.toRadians(globalRotation);
                float dx = gx - realCX;
                float dy = gy - realCY;
                float rx = (float) (dx * Math.cos(radians) - dy * Math.sin(radians));
                float ry = (float) (dx * Math.sin(radians) + dy * Math.cos(radians));
                gx = realCX + rx;
                gy = realCY + ry;
            }

            // NFT spin: the gift turns endlessly, unless the user is moving this one by hand.
            if (TrueGramIosDesign.isNftSpinEnabled() && !dragged) {
                gift.spin = (gift.spin + SPIN_STEP_DEG) % 360f;
            }
            gift.draw(canvas, gx, gy, scale, gift.spin, alpha, gift.glow);
        }

        canvas.restore();
        // keeps the gifts pulsing and spinning while they are on screen
        postInvalidateOnAnimation();
    }

    private static final float SPIN_STEP_DEG = 0.9f;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Gift getGiftUnder(float x, float y) {
        for (int i = 0; i < gifts.size(); ++i) {
            if (gifts.get(i).bounds.contains(x, y))
                return gifts.get(i);
        }
        return null;
    }

    private Gift pressedGift;
    private boolean rotatingGift;
    private boolean draggingGift;
    private boolean rotatingAll;
    private float touchDownX, touchDownY, lastTouchAngle;
    private float lastTouchX, lastTouchY;
    private float globalRotation;
    private int touchSlop = -1;

    /** Angle of the finger around the gift center, in degrees. */
    private static float angleTo(Gift gift, float x, float y) {
        return (float) Math.toDegrees(Math.atan2(y - gift.bounds.centerY(), x - gift.bounds.centerX()));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!active || gifts.isEmpty()) return false;
        if (touchSlop < 0) {
            touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        }
        final float x = event.getX();
        final float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                pressedGift = getGiftUnder(x, y);
                rotatingGift = false;
                draggingGift = false;
                rotatingAll = false;
                touchDownX = lastTouchX = x;
                touchDownY = lastTouchY = y;
                if (pressedGift != null) {
                    pressedGift.bounce.setPressed(true);
                    lastTouchAngle = angleTo(pressedGift, x, y);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                // Empty-space gesture rotates the whole NFT constellation around the avatar.
                float avatarX = avatarContainer.getX() + avatarContainer.getWidth() * avatarContainer.getScaleX() / 2f;
                float avatarY = avatarContainer.getY() + avatarContainer.getHeight() * avatarContainer.getScaleY() / 2f;
                if (Math.hypot(x - avatarX, y - avatarY) <= dp(132)) {
                    rotatingAll = true;
                    lastTouchAngle = (float) Math.toDegrees(Math.atan2(y - avatarY, x - avatarX));
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pressedGift == null && !rotatingAll) return false;
                if (pressedGift != null) {
                    if (!draggingGift && Math.hypot(x - touchDownX, y - touchDownY) > touchSlop) {
                        draggingGift = true;
                        rotatingGift = true;
                        pressedGift.bounce.setPressed(false);
                    }
                    if (draggingGift) {
                        float dx = x - lastTouchX;
                        float dy = y - lastTouchY;
                        pressedGift.offsetX += dx;
                        pressedGift.offsetY += dy;
                        // Gently nudge nearby gifts out of the way; the dragged NFT remains fully controlled.
                        for (Gift other : gifts) {
                            if (other == pressedGift) continue;
                            float ox = other.bounds.centerX() - x;
                            float oy = other.bounds.centerY() - y;
                            float distance = (float) Math.hypot(ox, oy);
                            float pushRadius = dp(62);
                            if (distance < pushRadius) {
                                float force = (pushRadius - distance) / pushRadius * .42f;
                                float safe = Math.max(dp(1), distance);
                                other.offsetX += ox / safe * force * dx;
                                other.offsetY += oy / safe * force * dy;
                            }
                        }
                        invalidate();
                    }
                } else if (rotatingAll) {
                    float avatarX = avatarContainer.getX() + avatarContainer.getWidth() * avatarContainer.getScaleX() / 2f;
                    float avatarY = avatarContainer.getY() + avatarContainer.getHeight() * avatarContainer.getScaleY() / 2f;
                    float angle = (float) Math.toDegrees(Math.atan2(y - avatarY, x - avatarX));
                    float delta = angle - lastTouchAngle;
                    if (delta > 180f) delta -= 360f;
                    if (delta < -180f) delta += 360f;
                    globalRotation = (globalRotation + delta) % 360f;
                    lastTouchAngle = angle;
                    invalidate();
                }
                lastTouchX = x;
                lastTouchY = y;
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (pressedGift != null) {
                    pressedGift.bounce.setPressed(false);
                    if (!draggingGift) onGiftClick(pressedGift);
                }
                pressedGift = null;
                rotatingGift = false;
                draggingGift = false;
                rotatingAll = false;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (pressedGift != null) pressedGift.bounce.setPressed(false);
                pressedGift = null;
                rotatingGift = false;
                draggingGift = false;
                rotatingAll = false;
                invalidate();
                return true;
        }
        return pressedGift != null || rotatingAll;
    }

    public void onGiftClick(Gift gift) {
        Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug);
    }
}
