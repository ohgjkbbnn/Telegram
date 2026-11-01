package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * TrueGram: 3D-прокрутка в чатах. Строки у краёв экрана наклоняются, сужаются, немного
 * опускаются вниз и тускнеют. Все параметры настраиваются слайдерами в «Оформлении» → «Прокрутка».
 *
 * Производительность: эффект делается на аппаратном слое (LAYER_TYPE_HARDWARE), поэтому
 * GPU переносит уже готовую картинку строки, а содержимое не перерисовывается.
 * Значения меняются только если реально изменились.
 */
public final class TrueGram3dScroll {

    private static final String PREFS = "truegram";
    private static final String KEY_ENABLED = "scroll_3d";
    private static final String KEY_ANGLE = "scroll_3d_angle";
    private static final String KEY_WIDTH = "scroll_3d_width";
    private static final String KEY_HEIGHT = "scroll_3d_height";
    private static final String KEY_REACH = "scroll_3d_reach";
    private static final String KEY_FADE = "scroll_3d_fade";

    public static final int ANGLE_MAX = 45;
    public static final int SHRINK_MAX = 30;
    public static final int REACH_MIN = 10;
    public static final int REACH_MAX = 100;
    public static final int FADE_MAX = 80;

    // значения по умолчанию подобраны по видео: края сужаются, наклоняются и тускнеют
    private static final int DEF_ANGLE = 16;
    private static final int DEF_WIDTH = 12;
    private static final int DEF_HEIGHT = 5;
    private static final int DEF_REACH = 70;
    private static final int DEF_FADE = 50;

    /** Максимальный сдвиг вниз у краёв (dp) при полном затухании. */
    private static final float SINK_DP = 14f;
    private static final float CAMERA_DISTANCE_DP = 6000f;
    private static final float EPS = 0.01f;

    private TrueGram3dScroll() {
    }

    private static SharedPreferences prefs() {
        Context context = ApplicationLoader.applicationContext;
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int readInt(String key, int def) {
        try {
            SharedPreferences p = prefs();
            return p == null ? def : p.getInt(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static void writeInt(String key, int value) {
        try {
            SharedPreferences p = prefs();
            if (p != null) {
                p.edit().putInt(key, value).apply();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isEnabled() {
        try {
            SharedPreferences p = prefs();
            return p != null && p.getBoolean(KEY_ENABLED, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            SharedPreferences p = prefs();
            if (p != null) {
                p.edit().putBoolean(KEY_ENABLED, enabled).apply();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Max tilt angle in degrees, 0..ANGLE_MAX. */
    public static int getAngle() {
        return clamp(readInt(KEY_ANGLE, DEF_ANGLE), 0, ANGLE_MAX);
    }

    public static void setAngle(int value) {
        writeInt(KEY_ANGLE, clamp(value, 0, ANGLE_MAX));
    }

    /** Narrowing at the edges (width), in percent, 0..SHRINK_MAX. */
    public static int getShrinkWidth() {
        return clamp(readInt(KEY_WIDTH, DEF_WIDTH), 0, SHRINK_MAX);
    }

    public static void setShrinkWidth(int value) {
        writeInt(KEY_WIDTH, clamp(value, 0, SHRINK_MAX));
    }

    /** Squeeze at the edges (height), in percent, 0..SHRINK_MAX. */
    public static int getShrinkHeight() {
        return clamp(readInt(KEY_HEIGHT, DEF_HEIGHT), 0, SHRINK_MAX);
    }

    public static void setShrinkHeight(int value) {
        writeInt(KEY_HEIGHT, clamp(value, 0, SHRINK_MAX));
    }

    /** Reach of the effect in percent of half the screen, REACH_MIN..REACH_MAX. 100 = whole screen. */
    public static int getReach() {
        return clamp(readInt(KEY_REACH, DEF_REACH), REACH_MIN, REACH_MAX);
    }

    public static void setReach(int value) {
        writeInt(KEY_REACH, clamp(value, REACH_MIN, REACH_MAX));
    }

    /** Fade and downward drift at the edges, in percent, 0..FADE_MAX. */
    public static int getFade() {
        return clamp(readInt(KEY_FADE, DEF_FADE), 0, FADE_MAX);
    }

    public static void setFade(int value) {
        writeInt(KEY_FADE, clamp(value, 0, FADE_MAX));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
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
        final float density = recyclerView.getResources().getDisplayMetrics().density;
        final float half = height / 2f;
        final float cameraDistance = CAMERA_DISTANCE_DP * density;
        final float angle = getAngle();
        final float shrinkW = getShrinkWidth() / 100f;
        final float shrinkH = getShrinkHeight() / 100f;
        final float reach = getReach() / 100f;
        final float fade = getFade() / 100f;
        final float sinkPx = AndroidUtilities.dp(SINK_DP);
        final boolean active = angle > 0f || shrinkW > 0f || shrinkH > 0f || fade > 0f;

        final int count = recyclerView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = recyclerView.getChildAt(i);
            if (child == null || child.getHeight() <= 0) {
                continue;
            }
            final float childCenter = (child.getTop() + child.getBottom()) / 2f;
            float t = ((childCenter - half) / half) / reach;
            if (t > 1f) {
                t = 1f;
            } else if (t < -1f) {
                t = -1f;
            }
            final float at = Math.abs(t);
            final float targetRot = t * angle;
            final float targetScaleX = Math.max(0.2f, 1f - shrinkW * at);
            final float targetScaleY = Math.max(0.2f, 1f - shrinkH * at);
            final float targetAlpha = Math.max(0f, 1f - fade * at);
            // ряды у краёв немного опускаются вниз, пока тускнеют
            final float targetTransY = fade * at * sinkPx;

            // аппаратный слой: GPU переносит готовую строку, содержимое не перерисовывается
            final boolean needsLayer = active && at > 0.01f;
            final int wantLayer = needsLayer ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
            if (child.getLayerType() != wantLayer) {
                child.setLayerType(wantLayer, null);
            }

            if (child.getCameraDistance() != cameraDistance) {
                child.setCameraDistance(cameraDistance);
            }
            final float pivotX = child.getWidth() / 2f;
            final float pivotY = child.getHeight() / 2f;
            if (child.getPivotX() != pivotX) {
                child.setPivotX(pivotX);
            }
            if (child.getPivotY() != pivotY) {
                child.setPivotY(pivotY);
            }
            if (Math.abs(child.getRotationX() - targetRot) > EPS) {
                child.setRotationX(targetRot);
            }
            if (Math.abs(child.getScaleX() - targetScaleX) > EPS) {
                child.setScaleX(targetScaleX);
            }
            if (Math.abs(child.getScaleY() - targetScaleY) > EPS) {
                child.setScaleY(targetScaleY);
            }
            if (Math.abs(child.getAlpha() - targetAlpha) > EPS) {
                child.setAlpha(targetAlpha);
            }
            if (Math.abs(child.getTranslationY() - targetTransY) > 0.5f) {
                child.setTranslationY(targetTransY);
            }
        }
    }
}
