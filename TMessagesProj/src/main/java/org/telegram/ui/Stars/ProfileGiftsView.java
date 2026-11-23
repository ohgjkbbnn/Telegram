package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Camera;
import android.graphics.Matrix;
import android.graphics.Path;
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
import org.telegram.messenger.MediaController;
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
import org.telegram.ui.Components.TrueGramBassMeter;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.TrueGramIosDesign;

import java.util.ArrayList;
import java.util.Collections;
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
        bassMeter.stop();

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

    private static final float TURN_DEG_PER_SEC = 40f;
    private static final float CAMERA_DEPTH = 1.25f;

    /** One NFT to draw this frame, with its depth: the bigger, the closer to the viewer. */
    private static final class Render {
        final Gift gift;
        final float x, y, scale, alpha, glow, depth;

        Render(Gift gift, float x, float y, float scale, float alpha, float glow, float depth) {
            this.gift = gift;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.alpha = alpha;
            this.glow = glow;
            this.depth = depth;
        }
    }

    /**
     * A jelly drop outline: points on a ring joined by springs. The NFT's own acceleration pulls the points back, so the
     * outline stretches and trails behind a fast move, then wobbles back, like a soft drop of liquid.
     */
    static final class SoftBody {
        static final int N = 16;
        final float radius;
        final float[] restX = new float[N], restY = new float[N];
        final float[] dx = new float[N], dy = new float[N];
        final float[] vx = new float[N], vy = new float[N];
        final float[] px = new float[N], py = new float[N];

        SoftBody(float radius) {
            this.radius = radius;
            for (int i = 0; i < N; i++) {
                final double a = Math.PI * 2 * i / N;
                restX[i] = (float) (radius * Math.cos(a));
                restY[i] = (float) (radius * Math.sin(a));
            }
        }

        void step(float ax, float ay, float bass, float dt) {
            final float stiffness = 240f, damping = 11f, cohesion = 170f;
            final float inertia = 0.66f, pulse = 900f * bass;
            final float limit = radius * 0.5f;
            for (int i = 0; i < N; i++) {
                final int p = (i + N - 1) % N, q = (i + 1) % N;
                final float lapX = (dx[p] + dx[q]) * 0.5f - dx[i];
                final float lapY = (dy[p] + dy[q]) * 0.5f - dy[i];
                final float nx = restX[i] / radius, ny = restY[i] / radius;
                final float accX = -dx[i] * stiffness - vx[i] * damping + lapX * cohesion - ax * inertia + nx * pulse;
                final float accY = -dy[i] * stiffness - vy[i] * damping + lapY * cohesion - ay * inertia + ny * pulse;
                vx[i] += accX * dt;
                vy[i] += accY * dt;
                dx[i] = Math.max(-limit, Math.min(limit, dx[i] + vx[i] * dt));
                dy[i] = Math.max(-limit, Math.min(limit, dy[i] + vy[i] * dt));
            }
        }

        /** The outline as a smooth closed curve through the midpoints of the points. */
        void buildPath(Path out) {
            out.reset();
            for (int i = 0; i < N; i++) {
                px[i] = restX[i] + dx[i];
                py[i] = restY[i] + dy[i];
            }
            out.moveTo((px[N - 1] + px[0]) * 0.5f, (py[N - 1] + py[0]) * 0.5f);
            for (int i = 0; i < N; i++) {
                final int q = (i + 1) % N;
                out.quadTo(px[i], py[i], (px[i] + px[q]) * 0.5f, (py[i] + py[q]) * 0.5f);
            }
            out.close();
        }
    }

    /** A damped spring: soft, slightly bouncy motion, the "silicone slime" feel. */
    static final class Spring {
        float x, v;

        Spring(float x0) {
            x = x0;
        }

        void step(float target, float stiffness, float damping, float dt) {
            final float a = (target - x) * stiffness - v * damping;
            v += a * dt;
            x += v * dt;
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
        // Orbit angle around the avatar, degrees: the NFT travels around by itself.
        public float orbitAngle;
        // Soft "slime" motion: the finger moves the targets, springs follow them with lag and wobble.
        public float targetOffX, targetOffY;
        public Spring offSpringX = new Spring(0f), offSpringY = new Spring(0f);
        // 3D turn in degrees: the NFT turns around its vertical axis by itself, the finger adds to it.
        public float turnY, tiltX, targetTurnY, targetTurnX;
        // Orbit radius in px; -1 until first placed. Dropping the NFT somewhere sets it.
        public float orbitRadius = -1f;
        public float accX, accY, prevVX, prevVY;
        public SoftBody soft = new SoftBody(dp(25));
        public final Path jellyPath = new Path();
        public final Camera camera = new Camera();
        public final Matrix tmpMatrix = new Matrix();
        {
            camera.setLocation(0f, 0f, -CAMERA_DEPTH);
        }
        public Spring squash = new Spring(0f);
        public Spring dragSpring = new Spring(1f);
        public float dragScale = 1f;
        public float stretchX = 1f, stretchY = 1f;
        // 0..1 bass level of the playing music: makes the NFT jump.
        public float bass;
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
            orbitAngle = b.orbitAngle;
            targetOffX = b.targetOffX;
            targetOffY = b.targetOffY;
            offSpringX = b.offSpringX;
            offSpringY = b.offSpringY;
            turnY = b.turnY;
            tiltX = b.tiltX;
            targetTurnY = b.targetTurnY;
            targetTurnX = b.targetTurnX;
            orbitRadius = b.orbitRadius;
            accX = b.accX;
            accY = b.accY;
            prevVX = b.prevVX;
            prevVY = b.prevVY;
            soft = b.soft;
            squash = b.squash;
            dragSpring = b.dragSpring;
            bass = b.bass;
            stretchX = b.stretchX;
            stretchY = b.stretchY;
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
            // Real 3D: the NFT is turned around its vertical axis and tilted, seen through a camera with perspective.
            camera.save();
            camera.rotateY(turnY);
            camera.rotateX(tiltX);
            camera.getMatrix(tmpMatrix);
            camera.restore();
            canvas.concat(tmpMatrix);
            canvas.rotate(rotate);
            final float scale = ascale * bounce.getScale(0.1f);
            canvas.scale(scale * stretchX, scale * stretchY);
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

            // The jelly drop: its body is tinted glass, its edge is bright and refracts the backdrop color inside.
            soft.buildPath(jellyPath);
            final float bubbleRadius = soft.radius;
            final int bubbleTint = ColorUtils.setAlphaComponent(color, (int) (alpha * 105));
            giftBubbleShader(bubblePaint, bubbleTint, bubbleRadius);
            bubblePaint.setAlpha((int) (255 * alpha));
            canvas.drawPath(jellyPath, bubblePaint);

            canvas.save();
            canvas.clipPath(jellyPath);
            // Inner band of the backdrop color just inside the edge: the "refraction" of the liquid.
            bubbleRimPaint.setStyle(Paint.Style.STROKE);
            bubbleRimPaint.setStrokeWidth(dp(4f));
            bubbleRimPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (alpha * 110)));
            canvas.drawPath(jellyPath, bubbleRimPaint);
            // The NFT's own patterns, visible through the drop; they turn with it.
            bubblePatternPaint.setStyle(Paint.Style.STROKE);
            bubblePatternPaint.setStrokeWidth(dp(0.8f));
            bubblePatternPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 105)));
            canvas.drawArc(-bubbleRadius * .72f, -bubbleRadius * .48f, bubbleRadius * .72f, bubbleRadius * .96f, 205, 235, false, bubblePatternPaint);
            canvas.drawCircle(-bubbleRadius * .28f, bubbleRadius * .18f, dp(2.1f), bubblePatternPaint);
            canvas.drawCircle(bubbleRadius * .34f, -bubbleRadius * .12f, dp(1.5f), bubblePatternPaint);
            // Specular highlight of the wet surface.
            bubblePaint.setShader(new RadialGradient(-bubbleRadius * .38f, -bubbleRadius * .48f, bubbleRadius * .72f,
                    new int[]{ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 210)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 45)), Color.TRANSPARENT},
                    new float[]{0f, .28f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawPath(jellyPath, bubblePaint);
            bubblePaint.setShader(null);
            canvas.restore();

            // Bright liquid edge.
            bubbleRimPaint.setStyle(Paint.Style.STROKE);
            bubbleRimPaint.setStrokeWidth(dp(1.4f));
            bubbleRimPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (alpha * 170)));
            canvas.drawPath(jellyPath, bubbleRimPaint);

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
                    g.orbitAngle = g.position * 60f;
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

        final long now = SystemClock.uptimeMillis();
        final float dt = lastFrameTime == 0 ? 1f / 60f : Math.min(1f / 30f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        // Keep clear of the profile buttons and the music bar, wherever they are on screen right now.
        if (avoidProvider != null) {
            avoidRects.clear();
            final View[] views = avoidProvider.getViews();
            if (views != null) {
                for (View v : views) {
                    addAvoidRect(v);
                }
            }
        }
        // Bass of the music that is playing; no music, no jumps.
        final MediaController mc = MediaController.getInstance();
        final boolean musicPlaying = mc.isPlayingMessage(mc.getPlayingMessageObject()) && !mc.isMessagePaused();
        if (musicPlaying) {
            bassMeter.start();
        } else {
            bassMeter.stop();
        }
        audioLevel = musicPlaying ? bassMeter.getLevel() : 0f;

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
        final ArrayList<Render> renders = new ArrayList<>();

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

            // Orbit: the NFT goes around the avatar on its own; the bass makes the ring wider.
            if (gift.orbitRadius < 0f) {
                gift.orbitRadius = Math.max(realW, realH) / 2f + dp(34) + dp(7) * (gift.position % 2);
            }
            final float orbitR = gift.orbitRadius + dp(10) * gift.bass;
            final double orbitRad = Math.toRadians(gift.orbitAngle);
            final float[] orbitPos = {realCX + orbitR * (float) Math.cos(orbitRad), realCY + orbitR * 0.82f * (float) Math.sin(orbitRad)};
            for (RectF r : avoidRects) {
                pushOutOf(orbitPos, r, dp(20));
            }
            gx = orbitPos[0];
            gy = orbitPos[1];

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

            // Physics: springs make the NFT lag, stretch, wobble and snap back like silicone.
            gift.bass += (audioLevel - gift.bass) * 0.35f;
            if (!dragged) {
                gift.orbitAngle = (gift.orbitAngle + ORBIT_DEG_PER_SEC * dt) % 360f;
            }
            if (gift != pressedGift || !draggingGift) {
                gift.targetOffX *= 0.94f;
                gift.targetOffY *= 0.94f;
            }
            gift.offSpringX.step(gift.targetOffX, 180f, 12f, dt);
            gift.offSpringY.step(gift.targetOffY, 180f, 12f, dt);
            final float speed = (float) Math.hypot(gift.offSpringX.v, gift.offSpringY.v);
            gift.squash.step(Utilities.clamp01(speed * 0.0006f) * 0.35f, 200f, 13f, dt);
            gift.dragSpring.step(gift == pressedGift && draggingGift ? 1.32f : 1f, 260f, 14f, dt);
            gift.dragScale = gift.dragSpring.x;
            // Accelerations of the NFT: the jelly outline lags behind them.
            gift.accX = (gift.offSpringX.v - gift.prevVX) / dt;
            gift.accY = (gift.offSpringY.v - gift.prevVY) / dt;
            gift.prevVX = gift.offSpringX.v;
            gift.prevVY = gift.offSpringY.v;
            gift.soft.step(gift.accX, gift.accY, gift.bass, dt);
            // The 3D turn: the NFT keeps turning by itself; the finger adds to the turn and tilt.
            if (!dragged) {
                gift.targetTurnY += TURN_DEG_PER_SEC * dt;
                gift.targetTurnX *= 0.98f;
            }
            final float turnBlend = 1f - (float) Math.exp(-dt * 10.0);
            gift.turnY += (gift.targetTurnY - gift.turnY) * turnBlend;
            gift.tiltX += (gift.targetTurnX - gift.tiltX) * turnBlend;
            gift.stretchX = 1f + gift.squash.x;
            gift.stretchY = 1f - 0.6f * gift.squash.x;
            gx += gift.offSpringX.x;
            gy += gift.offSpringY.x;
            scale *= gift.dragScale * (1f + 0.12f * gift.bass);
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
            final float depth = gift == pressedGift && draggingGift ? 2f : (float) Math.sin(orbitRad);
            renders.add(new Render(gift, gx, gy, scale, alpha, gift.glow, depth));
        }
        // Back to front: the NFT behind the avatar is drawn first, the one in front last.
        Collections.sort(renders, (p, q) -> Float.compare(p.depth, q.depth));
        for (Render r : renders) {
            r.gift.draw(canvas, r.x, r.y, r.scale, r.gift.spin, r.alpha, r.glow);
        }

        canvas.restore();
        // keeps the gifts pulsing and spinning while they are on screen
        postInvalidateOnAnimation();
    }

    private static final float SPIN_STEP_DEG = 0.9f;
    private final TrueGramBassMeter bassMeter = new TrueGramBassMeter();
    private AvoidProvider avoidProvider;
    private static final float ORBIT_DEG_PER_SEC = 24f;
    private long lastFrameTime;
    private float audioLevel;
    private final ArrayList<RectF> avoidRects = new ArrayList<>();
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
                        pressedGift.targetOffX += dx;
                        pressedGift.targetOffY += dy;
                        // Turning with the finger: the NFT spins around its own center and tilts toward the finger.
                        final float angleNow = angleTo(pressedGift, x, y);
                        float turnDelta = angleNow - lastTouchAngle;
                        if (turnDelta > 180f) turnDelta -= 360f;
                        if (turnDelta < -180f) turnDelta += 360f;
                        pressedGift.spin += turnDelta;
                        lastTouchAngle = angleNow;
                        pressedGift.targetTurnY += dx * 0.9f;
                        pressedGift.targetTurnX = Math.max(-60f, Math.min(60f, pressedGift.targetTurnX - dy * 0.5f));
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
                                other.targetOffX += ox / safe * force * dx;
                                other.targetOffY += oy / safe * force * dy;
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
                    else placeGift(pressedGift, x, y);
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

    /** Gives the views the NFTs must stay away from (the profile buttons, the music bar). Read on every frame. */
    public interface AvoidProvider {
        View[] getViews();
    }

    public void setAvoidProvider(AvoidProvider provider) {
        avoidProvider = provider;
    }

    private void addAvoidRect(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE || v.getWidth() == 0 || v.getHeight() == 0) {
            return;
        }
        final int[] a = new int[2];
        final int[] b = new int[2];
        v.getLocationInWindow(a);
        getLocationInWindow(b);
        final float left = a[0] - b[0];
        final float top = a[1] - b[1];
        avoidRects.add(new RectF(left, top, left + v.getWidth(), top + v.getHeight()));
    }

    /** Areas (in this view's coordinates) the NFTs must not cover, e.g. the profile buttons and the music bar. */
    public void setAvoidRects(List<RectF> rects) {
        avoidRects.clear();
        if (rects != null) {
            avoidRects.addAll(rects);
        }
        invalidate();
    }

    /** Bass level of the music, 0..1. The NFTs jump with it. Nothing moves until this gets a value. */
    public void setAudioLevel(float level) {
        audioLevel = Utilities.clamp01(level);
    }

    /** Moves point p out of the rect (with padding) through its nearest edge. */
    private static void pushOutOf(float[] p, RectF r, float pad) {
        final float l = r.left - pad, t = r.top - pad, rt = r.right + pad, b = r.bottom + pad;
        if (p[0] <= l || p[0] >= rt || p[1] <= t || p[1] >= b) return;
        final float dl = p[0] - l, dr = rt - p[0], dt = p[1] - t, db = b - p[1];
        final float m = Math.min(Math.min(dl, dr), Math.min(dt, db));
        if (m == dl) p[0] = l;
        else if (m == dr) p[0] = rt;
        else if (m == dt) p[1] = t;
        else p[1] = b;
    }

    /** The NFT stays where the finger let it go: it keeps orbiting from there, in front of the avatar or behind it. */
    private void placeGift(Gift gift, float x, float y) {
        final float cx = avatarContainer.getX() + avatarContainer.getWidth() * avatarContainer.getScaleX() / 2f;
        final float cy = avatarContainer.getY() + avatarContainer.getHeight() * avatarContainer.getScaleY() / 2f;
        final float dx = x - cx;
        final float dy = (y - cy) / 0.82f;
        gift.orbitAngle = (float) Math.toDegrees(Math.atan2(dy, dx)) - globalRotation;
        gift.orbitRadius = Math.max(dp(40), (float) Math.hypot(dx, dy));
        gift.targetOffX = 0f;
        gift.targetOffY = 0f;
        gift.offSpringX.x = 0f;
        gift.offSpringX.v = 0f;
        gift.offSpringY.x = 0f;
        gift.offSpringY.v = 0f;
        gift.prevVX = 0f;
        gift.prevVY = 0f;
    }

    public void onGiftClick(Gift gift) {
        Browser.openUrl(getContext(), "https://t.me/nft/" + gift.slug);
    }
}
