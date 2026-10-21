package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

/**
 * TrueGram YouTube: keeps one WebView alive and moves it between the full screen
 * (TrueGramYouTubeActivity) and a small draggable window that floats above every screen of the app.
 */
public class TrueGramYouTube {

    public static final String HOME_URL = "https://m.youtube.com/";

    // Makes the page believe it is always visible, so YouTube does not auto-pause the video
    // when the WebView is moved between the full screen and the floating window.
    private static final String JS_ALWAYS_VISIBLE = "(function(){if(window.__tgVis){return;}window.__tgVis=true;try{"
            + "var def=function(n,v){Object.defineProperty(document,n,{configurable:true,get:function(){return v;}});};"
            + "def('hidden',false);def('webkitHidden',false);def('visibilityState','visible');def('webkitVisibilityState','visible');"
            + "var stop=function(e){e.stopImmediatePropagation();};"
            + "document.addEventListener('visibilitychange',stop,true);"
            + "document.addEventListener('webkitvisibilitychange',stop,true);"
            + "}catch(e){}})();";
    private static final String JS_IS_PLAYING = "(function(){var v=document.querySelector('video');return !!(v&&!v.paused&&!v.ended);})()";
    private static final String JS_PLAY = "(function(){var v=document.querySelector('video');if(v&&v.paused){var p=v.play();if(p&&p.catch){p.catch(function(){});}}})();";

    private static TrueGramYouTube instance;

    public static TrueGramYouTube getInstance() {
        if (instance == null) {
            instance = new TrueGramYouTube();
        }
        return instance;
    }

    private MutableContextWrapper webContext;
    private WebView webView;
    private FrameLayout fullHost;
    private FrameLayout floating;
    private float posX = -1;
    private float posY = -1;

    // fullscreen video (WebChromeClient custom view)
    private FrameLayout fullscreenLayer;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private int prevOrientation;
    private int prevSystemUi;

    public boolean hasSession() {
        return webView != null;
    }

    // ------------------------------------------------------------------ full screen host

    /**
     * Runs a re-parenting of the WebView and, if a video was playing, makes sure it keeps playing
     * afterwards (moving a WebView between windows can make the page pause itself).
     */
    private void runKeepingPlayback(Runnable move) {
        final WebView wv = webView;
        if (wv == null) {
            move.run();
            return;
        }
        wv.evaluateJavascript(JS_IS_PLAYING, value -> {
            if ("true".equals(value) && webView == wv) {
                wv.postDelayed(() -> wv.evaluateJavascript(JS_PLAY, null), 300);
                wv.postDelayed(() -> wv.evaluateJavascript(JS_PLAY, null), 1000);
            }
        });
        move.run();
    }

    /** Attaches the shared WebView to the full-screen page. Removes the floating window if shown. */
    public void attachToHost(Context context, FrameLayout host) {
        ensureWebView(context);
        runKeepingPlayback(() -> {
            hideFloating();
            detachWebView();
            webContext.setBaseContext(context);
            host.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fullHost = host;
        });
        webView.onResume();
    }

    /** Called when the full-screen page goes away (back, minimize button, ...). */
    public void onHostClosed(FrameLayout host) {
        if (webView == null || fullHost != host) {
            return;
        }
        if (isWatching()) {
            runKeepingPlayback(() -> {
                detachWebView();
                fullHost = null;
                showFloating();
            });
        } else {
            detachWebView();
            fullHost = null;
            destroy();
        }
    }

    public boolean isFullscreen() {
        return fullscreenLayer != null;
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public boolean goBackIfPossible() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    /** Stops the video and frees the WebView. */
    public void destroy() {
        exitFullscreen();
        hideFloating();
        if (webView != null) {
            detachWebView();
            try {
                webView.loadUrl("about:blank");
                webView.onPause();
                webView.destroy();
            } catch (Throwable ignore) {
            }
            webView = null;
            webContext = null;
        }
        fullHost = null;
    }

    private boolean isWatching() {
        if (webView == null) {
            return false;
        }
        final String url = webView.getUrl();
        return url != null && (url.contains("/watch") || url.contains("/shorts/") || url.contains("youtu.be/") || url.contains("/live/"));
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint({"SetJavaScriptEnabled"})
    private void ensureWebView(Context context) {
        if (webView != null) {
            return;
        }
        webContext = new MutableContextWrapper(context);
        webView = new WebView(webContext);
        webView.setBackgroundColor(Color.BLACK);
        final WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        try {
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable ignore) {
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                view.evaluateJavascript(JS_ALWAYS_VISIBLE, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(JS_ALWAYS_VISIBLE, null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                final Uri uri = request.getUrl();
                final String scheme = uri.getScheme();
                // keep everything web inside; block app-open schemes (intent:, vnd.youtube:, ...)
                return !("http".equals(scheme) || "https".equals(scheme));
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
        webView.loadUrl(HOME_URL);
    }

    private void detachWebView() {
        if (webView != null && webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }
    }

    // ------------------------------------------------------------------ fullscreen video

    @SuppressWarnings("deprecation")
    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        final LaunchActivity activity = LaunchActivity.instance;
        if (activity == null || activity.frameLayout == null) {
            callback.onCustomViewHidden();
            return;
        }
        if (fullscreenLayer != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenCallback = callback;
        fullscreenLayer = new FrameLayout(activity);
        fullscreenLayer.setBackgroundColor(Color.BLACK);
        fullscreenLayer.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenLayer.setFocusableInTouchMode(true);
        fullscreenLayer.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                exitFullscreen();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_BACK;
        });
        activity.frameLayout.addView(fullscreenLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenLayer.requestFocus();

        prevOrientation = activity.getRequestedOrientation();
        activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        final View decor = activity.getWindow().getDecorView();
        prevSystemUi = decor.getSystemUiVisibility();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** @return true if fullscreen was active and has been closed. */
    @SuppressWarnings("deprecation")
    public boolean exitFullscreen() {
        if (fullscreenLayer == null) {
            return false;
        }
        final LaunchActivity activity = LaunchActivity.instance;
        final FrameLayout layer = fullscreenLayer;
        fullscreenLayer = null;
        if (layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) layer.getParent()).removeView(layer);
        }
        layer.removeAllViews();
        if (fullscreenCallback != null) {
            try {
                fullscreenCallback.onCustomViewHidden();
            } catch (Throwable ignore) {
            }
            fullscreenCallback = null;
        }
        if (activity != null) {
            activity.setRequestedOrientation(prevOrientation);
            activity.getWindow().getDecorView().setSystemUiVisibility(prevSystemUi);
        }
        return true;
    }

    // ------------------------------------------------------------------ floating window

    private void showFloating() {
        final LaunchActivity activity = LaunchActivity.instance;
        if (activity == null || activity.frameLayout == null || webView == null || floating != null) {
            return;
        }
        final FrameLayout parent = activity.frameLayout;
        final Context ctx = parent.getContext();
        webContext.setBaseContext(ctx);
        detachWebView();

        final int w = AndroidUtilities.dp(250);
        final int headerH = AndroidUtilities.dp(34);
        final int videoH = AndroidUtilities.dp(141);

        final FrameLayout box = new FrameLayout(ctx);
        box.setBackgroundColor(0xFF000000);
        box.setElevation(AndroidUtilities.dp(10));
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(12));
            }
        });
        box.setClipToOutline(true);

        final FrameLayout header = new FrameLayout(ctx);
        header.setBackgroundColor(0xFF1B1B1F);
        box.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerH, Gravity.TOP));

        final TextView title = new TextView(ctx);
        title.setText("YouTube");
        title.setTextColor(0xFFE6E1E5);
        title.setTextSize(12);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        title.setPadding(AndroidUtilities.dp(12), 0, 0, 0);

        final IconView expand = new IconView(ctx, IconView.EXPAND, 0xFFE6E1E5);
        expand.setOnClickListener(v -> {
            final LaunchActivity a = LaunchActivity.instance;
            if (a != null) {
                a.presentFragment(new TrueGramYouTubeActivity(), false, false);
            }
        });
        header.addView(expand, new FrameLayout.LayoutParams(AndroidUtilities.dp(40), headerH, Gravity.RIGHT | Gravity.TOP));
        ((FrameLayout.LayoutParams) expand.getLayoutParams()).rightMargin = AndroidUtilities.dp(40);

        final IconView close = new IconView(ctx, IconView.CLOSE, 0xFFE6E1E5);
        close.setOnClickListener(v -> destroy());
        header.addView(close, new FrameLayout.LayoutParams(AndroidUtilities.dp(40), headerH, Gravity.RIGHT | Gravity.TOP));

        header.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY, startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = box.getTranslationX();
                        startY = box.getTranslationY();
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        posX = startX + (e.getRawX() - downX);
                        posY = startY + (e.getRawY() - downY);
                        applyPosition(parent, box);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return false;
                }
            }
        });

        final FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        wl.topMargin = headerH;
        box.addView(webView, wl);

        parent.addView(box, new FrameLayout.LayoutParams(w, headerH + videoH, Gravity.LEFT | Gravity.TOP));
        floating = box;
        webView.onResume();
        box.post(() -> {
            if (posX < 0 || posY < 0) {
                posX = parent.getWidth() - box.getWidth() - AndroidUtilities.dp(8);
                posY = parent.getHeight() - box.getHeight() - AndroidUtilities.dp(110);
            }
            applyPosition(parent, box);
        });
    }

    private void applyPosition(View parent, View box) {
        final float maxX = Math.max(0, parent.getWidth() - box.getWidth());
        final float maxY = Math.max(0, parent.getHeight() - box.getHeight());
        posX = Math.max(0, Math.min(posX, maxX));
        posY = Math.max(0, Math.min(posY, maxY));
        box.setTranslationX(posX);
        box.setTranslationY(posY);
    }

    private void hideFloating() {
        if (floating == null) {
            return;
        }
        if (webView != null && webView.getParent() == floating) {
            floating.removeView(webView);
        }
        if (floating.getParent() instanceof ViewGroup) {
            ((ViewGroup) floating.getParent()).removeView(floating);
        }
        floating = null;
    }

    // ------------------------------------------------------------------ icons

    /** Small vector-style icons drawn with strokes (no drawable resources needed). */
    public static class IconView extends View {
        public static final int CLOSE = 0;
        public static final int EXPAND = 1;
        public static final int MINIMIZE = 2;
        public static final int BACK = 3;

        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public IconView(Context context, int type, int color) {
            super(context);
            this.type = type;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(AndroidUtilities.dpf2(1.8f));
            paint.setColor(color);
        }

        public void setColor(int color) {
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            final float s = AndroidUtilities.dpf2(6f);
            final float l = s * 0.8f;
            switch (type) {
                case CLOSE:
                    c.drawLine(cx - s, cy - s, cx + s, cy + s, paint);
                    c.drawLine(cx + s, cy - s, cx - s, cy + s, paint);
                    break;
                case EXPAND:
                    c.drawLine(cx - s, cy - s + l, cx - s, cy - s, paint);
                    c.drawLine(cx - s, cy - s, cx - s + l, cy - s, paint);
                    c.drawLine(cx + s - l, cy - s, cx + s, cy - s, paint);
                    c.drawLine(cx + s, cy - s, cx + s, cy - s + l, paint);
                    c.drawLine(cx - s, cy + s - l, cx - s, cy + s, paint);
                    c.drawLine(cx - s, cy + s, cx - s + l, cy + s, paint);
                    c.drawLine(cx + s - l, cy + s, cx + s, cy + s, paint);
                    c.drawLine(cx + s, cy + s, cx + s, cy + s - l, paint);
                    break;
                case MINIMIZE:
                    c.drawLine(cx - s, cy - s * 0.5f, cx, cy + s * 0.5f, paint);
                    c.drawLine(cx, cy + s * 0.5f, cx + s, cy - s * 0.5f, paint);
                    break;
                case BACK:
                default:
                    c.drawLine(cx + s * 0.4f, cy - s, cx - s * 0.6f, cy, paint);
                    c.drawLine(cx - s * 0.6f, cy, cx + s * 0.4f, cy + s, paint);
                    break;
            }
        }
    }
}
