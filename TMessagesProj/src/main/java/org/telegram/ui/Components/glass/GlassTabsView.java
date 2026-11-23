package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;

public class GlassTabsView extends FrameLayout {

    public final LinearLayout linearLayout;

    public GlassTabsView(@NonNull Context context) {
        super(context);
        setPadding(dp(8), dp(8), dp(8), dp(8));

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private float lensVisibility;

    private final Rect lensBounds = new Rect();
    private final Rect lensBoundsForeground = new Rect();
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF lensRect = new RectF();
    private int lensForegroundColor;

    protected void setLensColor(int lensBackgroundColor, int lensForegroundColor) {
        lensPaint.setColor(lensBackgroundColor);
        this.lensForegroundColor = lensForegroundColor;
        lensRimPaint.setStyle(Paint.Style.STROKE);
        lensRimPaint.setStrokeWidth(dp(1));
        lensRimPaint.setColor(lensForegroundColor);
        invalidate();
    }

    protected void setLensBounds(int l, int t, int r, int b) {
        lensBounds.set(l, t, r, b);
        checkBounds();
    }

    protected void setLensVisibility(float visibility) {
        lensVisibility = Math.max(0f, Math.min(1f, visibility));
        checkBounds();
    }

    private void checkBounds() {
        int i = dp(7f * lensVisibility);
        lensBoundsForeground.set(lensBounds);
        lensBoundsForeground.inset(-i, -i);
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (lensVisibility > 0f && !lensBoundsForeground.isEmpty()) {
            final int oldAlpha = lensPaint.getAlpha();
            final int oldRimAlpha = lensRimPaint.getAlpha();
            final int alpha = Math.max(0, Math.min(255, Math.round(255f * lensVisibility)));
            lensPaint.setAlpha(alpha);
            lensRimPaint.setAlpha(alpha);
            lensRect.set(lensBoundsForeground);
            final float radius = Math.min(lensRect.height(), lensRect.width()) * .32f;
            canvas.drawRoundRect(lensRect, radius, radius, lensPaint);
            super.dispatchDraw(canvas);
            lensRimPaint.setColor(lensForegroundColor);
            canvas.drawRoundRect(lensRect, radius, radius, lensRimPaint);
            lensPaint.setAlpha(oldAlpha);
            lensRimPaint.setAlpha(oldRimAlpha);
        } else {
            super.dispatchDraw(canvas);
        }
    }

}
