package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.content.Intent;
import android.app.Activity;
import android.net.Uri;
import androidx.core.content.FileProvider;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.AvatarDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * "Create quote": a bottom sheet with a preview of the chosen message drawn as a picture,
 * theme circles and toggles, and a send button. The picture is sent as a photo to the current chat.
 */
public class QuoteCreateSheet extends BottomSheet {

    public interface Callback {
        void onSend(File pictureFile);
    }

    private static final int[][] GRADIENTS = {
            {0xFF1B1F2A, 0xFF0E1118}, // dark
            {0xFF7B4DFF, 0xFFB26BFF}, // purple
            {0xFF2BB3C7, 0xFF2D6CDF}, // blue
            {0xFFFF8A3D, 0xFFE0465A}, // orange-red
            {0xFF5FD068, 0xFF2E9B5E}, // green
            {0xFF3A2E6B, 0xFF9B5BD6}  // violet
    };
    private int gradient = 0;
    private boolean lightMode = false;

    private final int account;
    private final MessageObject message;
    private final Callback callback;

    private boolean showAvatar = true, showName = true, showTime = true, showReply = true, showSource = true;

    private final ImageView preview;

    public QuoteCreateSheet(Context context, int account, MessageObject message, Callback callback) {
        super(context, false, null);
        this.account = account;
        this.message = message;
        this.callback = callback;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));

        TextView title = new TextView(context);
        title.setText("Цитата");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        root.addView(title, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260));
        pl.topMargin = dp(12);
        root.addView(preview, pl);

        // theme row: three circles
        LinearLayout themes = new LinearLayout(context);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.setGravity(Gravity.CENTER);
        for (int g = 0; g < GRADIENTS.length; g++) {
            themes.addView(gradientCircle(context, g));
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(12);
        root.addView(themes, tl);

        // toggles
        LinearLayout toggles1 = new LinearLayout(context);
        toggles1.setOrientation(LinearLayout.HORIZONTAL);
        toggles1.addView(toggle(context, "Аватарки", () -> showAvatar = !showAvatar));
        toggles1.addView(toggle(context, "Имена", () -> showName = !showName));
        toggles1.addView(toggle(context, "Время", () -> showTime = !showTime));
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        t1.topMargin = dp(12);
        root.addView(toggles1, t1);

        LinearLayout toggles2 = new LinearLayout(context);
        toggles2.setOrientation(LinearLayout.HORIZONTAL);
        toggles2.addView(toggle(context, "Ответы", () -> showReply = !showReply));
        toggles2.addView(toggle(context, "Источник", () -> showSource = !showSource));
        toggles2.addView(toggle(context, "Светлая", () -> lightMode = !lightMode));
        LinearLayout.LayoutParams t2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        t2.topMargin = dp(8);
        root.addView(toggles2, t2);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(toggle(context, "Сохранить", this::saveToGallery));
        actions.addView(toggle(context, "Поделиться", this::shareSystem));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        al.topMargin = dp(12);
        root.addView(actions, al);

        TextView send = new TextView(context);
        send.setText("Отправить в этот чат");
        send.setTextColor(Color.WHITE);
        send.setTextSize(16);
        send.setGravity(Gravity.CENTER);
        send.setBackgroundColor(Color.parseColor("#3390EC"));
        send.setPadding(dp(16), dp(14), dp(16), dp(14));
        send.setOnClickListener(v -> sendPicture());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(16);
        root.addView(send, sl);

        setCustomView(root);
        redraw();
    }

    private View gradientCircle(Context context, int index) {
        View c = new View(context);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, GRADIENTS[index]);
        gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        c.setBackground(gd);
        c.setOnClickListener(v -> { gradient = index; redraw(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(8), 0, dp(8), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView toggle(Context context, String text, Runnable onChange) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackgroundColor(Color.parseColor("#2B3A4F"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> { onChange.run(); redraw(); });
        return t;
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

    private void redraw() {
        preview.setImageBitmap(renderQuote(900));
    }

    private void sendPicture() {
        Bitmap bmp = renderQuote(1400);
        try {
            File f = new File(getContext().getCacheDir(), "quote_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            dismiss();
            if (callback != null) {
                callback.onSend(f);
            }
        } catch (Exception e) {
            AndroidUtilities.runOnUIThread(() -> android.widget.Toast.makeText(getContext(), "Не удалось создать цитату", android.widget.Toast.LENGTH_SHORT).show());
        }
    }

    /** Draws the quote as a bubble with name, avatar placeholder, reply, text and time. */
    private Bitmap renderQuote(int width) {
        boolean dark = !lightMode;
        int bubble = lightMode ? Color.WHITE : Color.parseColor("#2A2F3B");
        int text = dark ? Color.WHITE : Color.BLACK;
        int sub = dark ? Color.parseColor("#A7B1C2") : Color.parseColor("#7A8594");
        int accent = Color.parseColor("#E08A3C");

        float pad = width * 0.04f;
        float textSize = width * 0.038f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(textSize);
        String body = message.messageText != null ? message.messageText.toString() : "";
        String name = senderName();
        String time = showTime ? new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(message.messageOwner.date * 1000L)) : "";

        float avatar = showAvatar ? width * 0.09f : 0;
        float bubbleLeft = pad + (showAvatar ? avatar + pad : 0);
        float bubbleRight = width - pad;
        float y = pad;

        float h = pad * 4 + textSize * 3 + (showReply && message.replyMessageObject != null ? textSize * 2 : 0);
        Bitmap bmp = Bitmap.createBitmap(width, (int) (h + pad * 2), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        int[] gc = GRADIENTS[gradient];
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new LinearGradient(0, 0, bmp.getWidth(), bmp.getHeight(), gc[0], gc[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, bmp.getWidth(), bmp.getHeight(), bgPaint);

        p.setColor(bubble);
        RectF bubbleRect = new RectF(bubbleLeft, y, bubbleRight, h);
        c.drawRoundRect(bubbleRect, showRound() ? width * 0.05f : 0, showRound() ? width * 0.05f : 0, p);

        if (showAvatar) {
            drawAvatar(c, p, pad, y, avatar, name);
        }

        float tx = bubbleLeft + pad;
        float ty = y + pad + textSize;
        if (showName && name != null) {
            p.setColor(accent);
            p.setFakeBoldText(true);
            c.drawText(name, tx, ty, p);
            p.setFakeBoldText(false);
            ty += textSize * 1.4f;
        }
        if (showReply && message.replyMessageObject != null) {
            p.setColor(sub);
            String reply = message.replyMessageObject.messageText != null ? message.replyMessageObject.messageText.toString() : "";
            c.drawText(reply, tx, ty, p);
            ty += textSize * 1.4f;
        }
        p.setColor(text);
        c.drawText(body, tx, ty, p);
        if (showTime) {
            p.setColor(sub);
            p.setTextSize(textSize * 0.8f);
            c.drawText(time, bubbleRight - pad - p.measureText(time), h - pad, p);
        }
        return bmp;
    }

    /** Always draws a circle: the real avatar when the user is known, otherwise a coloured circle with the initial. */
    private void drawAvatar(Canvas c, Paint p, float x, float y, float size, String name) {
        TLRPC.User user = MessagesController.getInstance(account).getUser(message.getSenderId());
        float r = size / 2f;
        float cx = x + r, cy = y + r;
        if (user != null) {
            AvatarDrawable ad = new AvatarDrawable();
            ad.setInfo(user);
            ad.setBounds((int) x, (int) y, (int) (x + size), (int) (y + size));
            ad.draw(c);
            return;
        }
        p.setColor(0xFFE08A3C);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, r, p);
        String letter = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase(Locale.getDefault()) : "?";
        p.setColor(Color.WHITE);
        p.setTextSize(size * 0.42f);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(letter, cx, cy - (p.descent() + p.ascent()) / 2f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private boolean showRound() { return true; }

    private String senderName() {
        long id = message.getSenderId();
        TLRPC.User user = MessagesController.getInstance(account).getUser(id);
        if (user == null) return null;
        String first = user.first_name != null ? user.first_name : "";
        String last = user.last_name != null ? user.last_name : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    private int dp(int v) { return AndroidUtilities.dp(v); }
}
