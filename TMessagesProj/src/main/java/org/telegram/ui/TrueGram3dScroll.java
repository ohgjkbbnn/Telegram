package org.telegram.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * TrueGram: 3D-прокрутка в чатах. Строки, которые ближе к краю экрана, наклоняются
 * и немного уменьшаются, как в барабане. Включается в «Оформлении» → «3D-прокрутка».
 */
public final class TrueGram3dScroll {

    private static final String PREFS = "truegram";
    private static final String KEY = "scroll_3d";
    private static final float MAX_ANGLE = 14f;
    private static final float MIN_SCALE = 0.94f;
    private static final float CAMERA_DISTANCE_DP = 8000f;

    private TrueGram3dScroll() {
    }

    public static boolean isEnabled() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return false;
            }
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Called on every scroll of the chat list. Does nothing when the option is off. */
    public static void apply(RecyclerView recyclerView) {
        if (recyclerView == null || !isEnabled()) {
            return;
        }
        final int height = recyclerView.getHeight();
        if (height <= 0) {
            return;
        }
        final float half = height / 2f;
        final float cameraDistance = CAMERA_DISTANCE_DP * recyclerView.getResources().getDisplayMetrics().density;
        final int count = recyclerView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = recyclerView.getChildAt(i);
            if (child == null || child.getHeight() <= 0) {
                continue;
            }
            final float childCenter = (child.getTop() + child.getBottom()) / 2f;
            float t = (childCenter - half) / half;
            if (t > 1f) {
                t = 1f;
            } else if (t < -1f) {
                t = -1f;
            }
            child.setCameraDistance(cameraDistance);
            child.setPivotX(child.getWidth() / 2f);
            child.setPivotY(child.getHeight() / 2f);
            child.setRotationX(t * MAX_ANGLE);
            final float scale = 1f - (1f - MIN_SCALE) * Math.abs(t);
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }
}
