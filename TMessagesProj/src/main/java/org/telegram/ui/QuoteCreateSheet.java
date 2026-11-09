package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.AvatarDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * "Create quote" sheet: a preview of the chosen message (drawn here as a picture),
 * gradient themes, toggles and actions (save, share, send to the current chat).
 */
public class QuoteCreateSheet extends BottomSheet {

    public interface Callback {
        void onSend(File pictureFile);
    }

    private interface ToggleListener {
        void onChanged(boolean value);
    }

    // gradient backgrounds: top-left and bottom-right colours
    private static final int[][] GRADIENTS = {
            {0xFF1B1F2A, 0xFF0A0D14}, // dark
            {0xFF7B4DFF, 0xFFB26BFF}, // purple
            {0xFF2BB3C7, 0xFF2D6CDF}, // blue
            {0xFFFF8A3D, 0xFFE0465A}, // orange-red
            {0xFF5FD068, 0xFF2E9B5E}, // green
            {0xFF3A2E6B, 0xFF9B5BD6}  // violet
    };

    private final int account;
    private final MessageObject message;
    private final Callback callback;

    private int gradient = 0;
    private boolean lightMode = false;
    private boolean showAvatar = true, showName = true, showTime = true, showReply = true;

    private final View preview;
    private Bitmap previewBitmap;

    public QuoteCreateSheet(Context context, int account, MessageObject message, Callback callback) {
        super(context, false, null);
        this.account = account;
        this.message = message;
        this.callback = callback;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(20));
        root.setBackgroundColor(TrueGramM3.pageBackground());

        TextView title = new TextView(context);
        title.setText("Цитата");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TrueGramM3.onSurface());
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // preview card: the picture is painted by the view, clipped to rounded corners
        preview = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (previewBitmap != null) {
                    canvas.drawBitmap(previewBitmap, null, new android.graphics.Rect(0, 0, getWidth(), getHeight()), null);
                }
            }
        };
        preview.setClipToOutline(true);
        preview.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
            }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260));
        pl.topMargin = dp(16);
        root.addView(preview, pl);

        // gradient themes: scrollable row, the selected one has a ring
        HorizontalScrollView themeScroll = new HorizontalScrollView(context);
        themeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout themes = new LinearLayout(context);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.setGravity(Gravity.CENTER_VERTICAL);
        for (int g = 0; g < GRADIENTS.length; g++) {
            themeViews[g] = gradientCircle(context, g);
            themes.addView(themeViews[g]);
        }
        themeScroll.addView(themes);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(16);
        root.addView(themeScroll, tl);
        refreshThemeRing();

        // option chips
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(chip(context, "Аватарки", showAvatar, v -> showAvatar = v));
        row1.addView(chip(context, "Имена", showName, v -> showName = v));
        row1.addView(chip(context, "Время", showTime, v -> showTime = v));
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r1.topMargin = dp(16);
        root.addView(row1, r1);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(chip(context, "Ответы", showReply, v -> showReply = v));
        row2.addView(chip(context, "Светлая", lightMode, v -> lightMode = v));
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r2.topMargin = dp(8);
        root.addView(row2, r2);

        // secondary actions (tonal)
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(tonalButton(context, "Сохранить", this::saveToGallery));
        actions.addView(tonalButton(context, "Поделиться", this::shareSystem));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        al.topMargin = dp(12);
        root.addView(actions, al);

        // primary action (filled)
        TextView send = new TextView(context);
        send.setText("Отправить в этот чат");
        send.setTextSize(16);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setGravity(Gravity.CENTER);
        send.setTextColor(TrueGramM3.onColor(TrueGramM3.primary()));
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setColor(TrueGramM3.primary());
        sendBg.setCornerRadius(dp(28));
        send.setBackground(sendBg);
        send.setPadding(dp(16), dp(16), dp(16), dp(16));
        send.setOnClickListener(v -> sendPicture());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(20);
        root.addView(send, sl);

        setCustomView(root);
        redraw();
    }

    private final View[] themeViews = new View[GRADIENTS.length];
    private int ringedTheme = -1;

    private View gradientCircle(Context context, int index) {
        View c = new View(context);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, GRADIENTS[index]);
        gd.setShape(GradientDrawable.OVAL);
        c.setBackground(gd);
        c.setOnClickListener(v -> {
            gradient = index;
            refreshThemeRing();
            redraw();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMargins(dp(6), 0, dp(6), 0);
        c.setLayoutParams(lp);
        return c;
    }

    /** Shows a ring around the selected gradient circle. */
    private void refreshThemeRing() {
        for (int g = 0; g < themeViews.length; g++) {
            if (themeViews[g] == null) continue;
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, GRADIENTS[g]);
            gd.setShape(GradientDrawable.OVAL);
            if (g == gradient) {
                gd.setStroke(dp(3), TrueGramM3.primary());
            }
            themeViews[g].setBackground(gd);
        }
    }

    /** M3 filter chip: tonal when off, primary container when on. */
    private TextView chip(Context context, String text, boolean initial, ToggleListener onChange) {
        TextView t = new TextView(context);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setText(text);
        t.setPadding(dp(8), dp(12), dp(8), dp(12));
        final boolean[] on = {initial};
        Runnable paint = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            bg.setColor(on[0] ? TrueGramM3.primaryContainer() : TrueGramM3.surfaceContainerHigh());
            t.setBackground(bg);
            t.setTextColor(on[0] ? TrueGramM3.onPrimaryContainer() : TrueGramM3.onSurfaceVariant());
        };
        paint.run();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> {
            on[0] = !on[0];
            onChange.onChanged(on[0]);
            paint.run();
            redraw();
        });
        return t;
    }

    /** M3 tonal button for secondary actions. */
    private TextView tonalButton(Context context, String text, Runnable onClick) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(TrueGramM3.onSurface());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(TrueGramM3.surfaceContainer());
        t.setBackground(bg);
        t.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private void redraw() {
        previewBitmap = renderQuote(900);
        preview.invalidate();
    }

    private void sendPicture() {
        try {
            File f = writePicture(renderQuote(1400));
            dismiss();
            if (callback != null) {
                callback.onSend(f);
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "Не удалось создать цитату", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToGallery() {
        try {
            File f = writePicture(renderQuote(1400));
            if (getContext() instanceof Activity) {
                MediaController.saveFile(f.getAbsolutePath(), (Activity) getContext(), 0, null, null);
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "Не удалось сохранить", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSystem() {
        try {
            File f = writePicture(renderQuote(1400));
            Uri uri = FileProvider.getUriForFile(getContext(), ApplicationLoader.getApplicationId() + ".provider", f);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(Intent.createChooser(intent, "Поделиться"));
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "Не удалось поделиться", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private File writePicture(Bitmap bmp) throws Exception {
        File f = new File(getContext().getCacheDir(), "quote_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return f;
    }

    // ---------------------------------------------------------------- rendering

    /** Draws the quote: gradient background, avatar, name, reply, text, time. Width is in px. */
    private Bitmap renderQuote(int width) {
        final float pad = width * 0.045f;
        final float textSize = width * 0.042f;
        final float nameSize = width * 0.040f;
        final float smallSize = width * 0.032f;
        final float avatarSize = width * 0.105f;
        final float gap = width * 0.03f;
        final float radius = width * 0.05f;

        final int bubbleColor = lightMode ? Color.WHITE : Color.parseColor("#2A2F3B");
        final int textColor = lightMode ? Color.BLACK : Color.WHITE;
        final int subColor = lightMode ? Color.parseColor("#7A8594") : Color.parseColor("#A7B1C2");
        final int accent = lightMode ? Color.parseColor("#2B6CD9") : Color.parseColor("#E08A3C");

        String name = senderName();
        String body = message.messageText != null ? message.messageText.toString() : "";
        String replyText = message.replyMessageObject != null && message.replyMessageObject.messageText != null
                ? message.replyMessageObject.messageText.toString() : null;
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(message.messageOwner.date * 1000L));

        float leftEdge = pad + (showAvatar ? avatarSize + gap : 0);
        float bubbleWidth = width - leftEdge - pad;
        float innerWidth = bubbleWidth - pad * 2;

        TextPaint nameP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        nameP.setTextSize(nameSize);
        nameP.setTypeface(Typeface.DEFAULT_BOLD);
        nameP.setColor(accent);
        TextPaint replyP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        replyP.setTextSize(smallSize);
        replyP.setColor(subColor);
        TextPaint bodyP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyP.setTextSize(textSize);
        bodyP.setColor(textColor);
        TextPaint timeP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        timeP.setTextSize(smallSize);
        timeP.setColor(subColor);

        StaticLayout nameL = (showName && name != null) ? makeLayout(name, nameP, innerWidth, 1) : null;
        StaticLayout replyL = (showReply && replyText != null) ? makeLayout(replyText, replyP, innerWidth, 2) : null;
        StaticLayout bodyL = makeLayout(body.isEmpty() ? " " : body, bodyP, innerWidth, 40);
        float timeH = showTime ? smallSize * 1.6f : 0;

        float bubbleH = pad
                + (nameL != null ? nameL.getHeight() + gap * 0.5f : 0)
                + (replyL != null ? replyL.getHeight() + gap * 0.5f : 0)
                + bodyL.getHeight()
                + (showTime ? timeH : 0)
                + pad;
        float contentH = Math.max(bubbleH, showAvatar ? avatarSize : 0) + pad * 2;

        Bitmap bmp = Bitmap.createBitmap(width, (int) contentH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        // background gradient
        int[] gc = GRADIENTS[gradient];
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, bmp.getWidth(), bmp.getHeight(), gc[0], gc[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), bg);

        float top = pad;
        // bubble
        Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubble.setColor(bubbleColor);
        RectF rect = new RectF(leftEdge, top, width - pad, top + bubbleH);
        c.drawRoundRect(rect, radius, radius, bubble);

        // avatar, vertically centred on the bubble's top part
        if (showAvatar) {
            drawAvatar(c, pad, top + (bubbleH - avatarSize) / 2f, avatarSize, name, accent);
        }

        float x = leftEdge + pad;
        float y = top + pad;
        if (nameL != null) {
            c.save();
            c.translate(x, y);
            nameL.draw(c);
            c.restore();
            y += nameL.getHeight() + gap * 0.5f;
        }
        if (replyL != null) {
            c.save();
            c.translate(x, y);
            replyL.draw(c);
            c.restore();
            y += replyL.getHeight() + gap * 0.5f;
        }
        c.save();
        c.translate(x, y);
        bodyL.draw(c);
        c.restore();
        if (showTime) {
            String t = time;
            float tw = timeP.measureText(t);
            c.drawText(t, width - pad - tw, top + bubbleH - pad * 0.6f, timeP);
        }
        return bmp;
    }

    private StaticLayout makeLayout(CharSequence text, TextPaint paint, float width, int maxLines) {
        CharSequence s = text;
        return StaticLayout.Builder.obtain(s, 0, s.length(), paint, (int) Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    /** Real avatar when the user is known, otherwise a coloured circle with the first letter. */
    private void drawAvatar(Canvas c, float x, float y, float size, String name, int fallbackColor) {
        TLRPC.User user = MessagesController.getInstance(account).getUser(message.getSenderId());
        if (user != null) {
            AvatarDrawable ad = new AvatarDrawable();
            ad.setInfo(user);
            ad.setBounds((int) x, (int) y, (int) (x + size), (int) (y + size));
            ad.draw(c);
            return;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(fallbackColor);
        float r = size / 2f;
        c.drawCircle(x + r, y + r, r, p);
        String letter = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase(Locale.getDefault()) : "?";
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size * 0.45f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(letter, x + r, y + r - (p.descent() + p.ascent()) / 2f, p);
    }

    private String senderName() {
        TLRPC.User user = MessagesController.getInstance(account).getUser(message.getSenderId());
        if (user == null) {
            return null;
        }
        String first = user.first_name != null ? user.first_name : "";
        String last = user.last_name != null ? user.last_name : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    private int dp(int v) {
        return AndroidUtilities.dp(v);
    }
}
