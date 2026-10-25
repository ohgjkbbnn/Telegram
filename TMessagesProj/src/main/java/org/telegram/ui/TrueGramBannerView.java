package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Outline;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;

/** TrueGram profile banner: a photo or a looping video behind the profile header card. */
public class TrueGramBannerView extends FrameLayout {

    private final ImageView imageView;
    private final TextureView textureView;
    private final View scrim;

    private MediaPlayer player;
    private Surface surface;
    private String currentPath;
    private boolean currentVideo;
    private boolean soundOn;

    /** Corner radius in dp; 0 means a square banner (used for the full-width profile header). */
    public void setCornerRadius(int dp) {
        if (dp <= 0) {
            setClipToOutline(false);
        } else {
            setClipToOutline(true);
            cornerRadiusDp = dp;
            invalidateOutline();
        }
    }

    private int cornerRadiusDp = 28;

    public TrueGramBannerView(Context context) {
        super(context);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(cornerRadiusDp));
            }
        });

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int width, int height) {
                surface = new Surface(st);
                startVideo();
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) {
                releasePlayer();
                if (surface != null) {
                    surface.release();
                    surface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {
            }
        });
        addView(textureView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // soft darkening at the bottom so the name and subtitle stay readable
        scrim = new View(context);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x66000000}));
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setVisibility(GONE);
    }

    /** Shows the banner from a local file. Does nothing if the same file is already shown. */
    public void setSource(String path, boolean video, boolean sound) {
        if (path == null || path.isEmpty() || !new File(path).exists()) {
            releasePlayer();
            currentPath = null;
            imageView.setImageDrawable(null);
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        soundOn = sound;
        if (path.equals(currentPath) && video == currentVideo) {
            if (player != null) {
                float volume = sound ? 1f : 0f;
                player.setVolume(volume, volume);
            }
            return;
        }
        currentPath = path;
        currentVideo = video;
        releasePlayer();
        if (video) {
            imageView.setVisibility(GONE);
            textureView.setVisibility(VISIBLE);
            startVideo();
        } else {
            textureView.setVisibility(GONE);
            imageView.setVisibility(VISIBLE);
            imageView.setImageBitmap(decodeScaled(path, 1080));
        }
    }

    private void startVideo() {
        if (!currentVideo || surface == null || currentPath == null) {
            return;
        }
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(currentPath);
            player.setSurface(surface);
            player.setLooping(true);
            float volume = soundOn ? 1f : 0f;
            player.setVolume(volume, volume);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnErrorListener((mp, what, extra) -> {
                releasePlayer();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignore) {
            }
            player = null;
        }
    }

    private static Bitmap decodeScaled(String path, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > maxSize * 2 || bounds.outHeight / sample > maxSize * 2) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, options);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (currentVideo && surface != null) {
            startVideo();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        releasePlayer();
        super.onDetachedFromWindow();
    }
}
