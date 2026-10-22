package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.TrueGramAI;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/** TrueGram: AI chat. Three dots: add API key, settings, clear. Bottom: attach, text, model, send. */
public class TrueGramAIChatActivity extends BaseFragment {

    private static final int REQ_ATTACH = 7411;
    private static final String PLACEHOLDER = "\u2026";

    private final ArrayList<TrueGramAI.Msg> messages = new ArrayList<>();
    private final ArrayList<String> pendingImages = new ArrayList<>();
    private RecyclerView list;
    private Adapter adapter;
    private EditText input;
    private TextView modelChip;
    private TextView attachChip;
    private boolean busy;

    private static void toast(String text) {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show());
    }

    private static int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    // ------------------------------------------------------------ shared dialogs (also used by the settings screen)

    /** Provider picker -> key dialog -> checks the key, loads models and picks one automatically. */
    public static void showAddKeyDialog(final BaseFragment fragment, final Runnable onDone) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final String[] names = new String[TrueGramAI.PROVIDERS.length];
        for (int i = 0; i < names.length; i++) {
            final TrueGramAI.Provider p = TrueGramAI.PROVIDERS[i];
            names[i] = p.name + (TrueGramAI.isConnected(p.id) ? "  ✓" : "");
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle("Выберите нейросеть");
        b.setItems(names, (d, which) -> showKeyDialog(fragment, TrueGramAI.PROVIDERS[which], onDone));
        b.setNegativeButton("Отмена", null);
        fragment.showDialog(b.create());
    }

    public static void showKeyDialog(final BaseFragment fragment, final TrueGramAI.Provider p, final Runnable onDone) {
        final Context ctx = fragment.getParentActivity();
        if (ctx == null) {
            return;
        }
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText url = new EditText(ctx);
        url.setHint("Адрес API, например https://host/v1");
        url.setSingleLine(true);
        url.setText(TrueGramAI.getBaseUrl(p));
        if (p.id.equals("custom")) {
            box.addView(url, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        final EditText key = new EditText(ctx);
        key.setHint("API-ключ");
        key.setSingleLine(true);
        box.addView(key, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(p.name);
        b.setView(box);
        b.setNegativeButton("Отмена", null);
        if (TrueGramAI.isConnected(p.id)) {
            b.setNeutralButton("Удалить ключ", (d, w) -> {
                TrueGramAI.setKey(p.id, "");
                toast(p.name + ": ключ удалён");
                if (onDone != null) onDone.run();
            });
        }
        b.setPositiveButton("Подключить", (d, w) -> {
            final String k = key.getText().toString().trim();
            final String base = p.id.equals("custom") ? url.getText().toString().trim() : TrueGramAI.getBaseUrl(p);
            if (k.isEmpty()) {
                toast("Введите ключ");
                return;
            }
            toast("Проверяю ключ…");
            TrueGramAI.fetchModels(p, k, base.endsWith("/") ? base.substring(0, base.length() - 1) : base, (models, err) -> {
                if (err != null || models == null || models.isEmpty()) {
                    toast(p.name + ": " + (err != null ? err : "нет доступных моделей"));
                    return;
                }
                TrueGramAI.setKey(p.id, k);
                if (p.id.equals("custom")) TrueGramAI.setBaseUrl(p.id, base);
                TrueGramAI.saveModels(p.id, models);
                final String current = TrueGramAI.getModel(p.id);
                if (current.isEmpty() || !models.contains(current)) {
                    TrueGramAI.setModel(p.id, TrueGramAI.pickModel(p, models));
                }
                TrueGramAI.setActiveProvider(p.id);
                toast(p.name + ": подключено, моделей " + models.size() + ", выбрана " + TrueGramAI.getModel(p.id));
                if (onDone != null) onDone.run();
            });
        });
        fragment.showDialog(b.create());
    }

    // ------------------------------------------------------------ screen

    @Override
    public boolean onFragmentCreate() {
        messages.addAll(TrueGramAI.loadChat());
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("ИИ-чат");
        final ActionBarMenu menu = actionBar.createMenu();
        final ActionBarMenuItem other = menu.addItem(0, R.drawable.ic_ab_other);
        other.addSubItem(1, R.drawable.msg_edit, "Добавить API-ключ");
        other.addSubItem(2, R.drawable.msg_info, "Настройки ИИ");
        other.addSubItem(3, R.drawable.msg_delete, "Очистить чат");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    showAddKeyDialog(TrueGramAIChatActivity.this, () -> updateChip());
                } else if (id == 2) {
                    presentFragment(new TrueGramAISettingsActivity());
                } else if (id == 3) {
                    messages.clear();
                    TrueGramAI.saveChat(messages);
                    adapter.notifyDataSetChanged();
                }
            }
        });

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        adapter = new Adapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        attachChip = new TextView(context);
        attachChip.setTextSize(13);
        attachChip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        attachChip.setPadding(dp(16), dp(6), dp(16), dp(6));
        attachChip.setVisibility(View.GONE);
        attachChip.setOnClickListener(v -> {
            pendingImages.clear();
            updateAttachChip();
        });
        root.addView(attachChip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        final ImageView attach = new ImageView(context);
        attach.setImageResource(R.drawable.input_attach);
        attach.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        attach.setScaleType(ImageView.ScaleType.CENTER);
        attach.setOnClickListener(v -> pickAttachment());
        bar.addView(attach, new LinearLayout.LayoutParams(dp(44), dp(44)));

        input = new EditText(context);
        input.setHint("Сообщение");
        input.setMaxLines(5);
        input.setTextSize(16);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        final GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        inBg.setCornerRadius(dp(22));
        input.setBackground(inBg);
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        modelChip = new TextView(context);
        modelChip.setTextSize(12);
        modelChip.setSingleLine(true);
        modelChip.setEllipsize(TextUtils.TruncateAt.END);
        modelChip.setMaxWidth(dp(96));
        modelChip.setGravity(Gravity.CENTER);
        modelChip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        modelChip.setPadding(dp(8), 0, dp(8), 0);
        modelChip.setOnClickListener(v -> showModelPicker());
        bar.addView(modelChip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));

        final ImageView send = new ImageView(context);
        send.setImageResource(R.drawable.msg_send);
        send.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), PorterDuff.Mode.MULTIPLY));
        send.setScaleType(ImageView.ScaleType.CENTER);
        send.setOnClickListener(v -> send());
        bar.addView(send, new LinearLayout.LayoutParams(dp(44), dp(44)));

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        updateChip();
        scrollToEnd();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateChip();
        if (getParentActivity() != null) {
            AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getParentActivity() != null) {
            AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        }
    }

    // ------------------------------------------------------------ logic

    private void updateChip() {
        if (modelChip == null) {
            return;
        }
        final String pid = TrueGramAI.getActiveProvider();
        if (pid.isEmpty() || TrueGramAI.getModel(pid).isEmpty()) {
            modelChip.setText("Модель");
            actionBar.setSubtitle("не настроено: ⋮ → Добавить API-ключ");
            return;
        }
        String m = TrueGramAI.getModel(pid);
        modelChip.setText(m.contains("/") ? m.substring(m.lastIndexOf('/') + 1) : m);
        actionBar.setSubtitle(TrueGramAI.byId(pid).name + " · " + m);
    }

    private void updateAttachChip() {
        if (pendingImages.isEmpty()) {
            attachChip.setVisibility(View.GONE);
        } else {
            attachChip.setText("Вложений: " + pendingImages.size() + " (нажмите, чтобы убрать)");
            attachChip.setVisibility(View.VISIBLE);
        }
    }

    private void scrollToEnd() {
        if (list != null && adapter.getItemCount() > 0) {
            list.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingImages.isEmpty()) {
            return;
        }
        if (!TrueGramAI.isConfigured()) {
            toast("Сначала добавьте API-ключ: ⋮ → Добавить API-ключ");
            return;
        }
        if (busy) {
            return;
        }
        final TrueGramAI.Msg m = new TrueGramAI.Msg("user", text.isEmpty() ? "Что на изображении?" : text);
        m.images.addAll(pendingImages);
        messages.add(m);
        pendingImages.clear();
        updateAttachChip();
        input.setText("");
        final TrueGramAI.Msg placeholder = new TrueGramAI.Msg("assistant", PLACEHOLDER);
        messages.add(placeholder);
        busy = true;
        adapter.notifyDataSetChanged();
        scrollToEnd();

        final ArrayList<TrueGramAI.Msg> history = new ArrayList<>(messages);
        history.remove(placeholder);
        TrueGramAI.chat(TrueGramAI.getStyle(), history, (reply, error) -> {
            busy = false;
            placeholder.text = error != null ? "Ошибка: " + error : reply;
            if (error != null) {
                messages.remove(placeholder);
                toast("Ошибка: " + error);
            } else {
                TrueGramAI.saveChat(messages);
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
                scrollToEnd();
            }
        });
    }

    private void pickAttachment() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        startActivityForResult(intent, REQ_ATTACH);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != REQ_ATTACH || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final Context ctx = ApplicationLoader.applicationContext;
        toast("Обрабатываю вложение…");
        new Thread(() -> {
            try {
                final String type = ctx.getContentResolver().getType(uri);
                final ArrayList<String> out = new ArrayList<>();
                if (type != null && type.startsWith("video/")) {
                    out.addAll(TrueGramAI.videoFrames(ctx, uri, 4));
                    toast("Видео: модели принимают только изображения, отправлю 4 кадра");
                } else {
                    out.add(TrueGramAI.imageToDataUrl(ctx, uri));
                }
                AndroidUtilities.runOnUIThread(() -> {
                    pendingImages.addAll(out);
                    updateAttachChip();
                });
            } catch (Throwable e) {
                toast("Вложение не прочитано: " + e.getMessage());
            }
        }).start();
    }

    private void showModelPicker() {
        final ArrayList<TrueGramAI.Provider> connected = new ArrayList<>();
        for (TrueGramAI.Provider p : TrueGramAI.PROVIDERS) {
            if (TrueGramAI.isConnected(p.id)) {
                connected.add(p);
            }
        }
        if (connected.isEmpty()) {
            showAddKeyDialog(this, this::updateChip);
            return;
        }
        if (connected.size() == 1) {
            showModelList(connected.get(0));
            return;
        }
        final String[] names = new String[connected.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = connected.get(i).name;
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Нейросеть");
        b.setItems(names, (d, which) -> showModelList(connected.get(which)));
        showDialog(b.create());
    }

    private void showModelList(final TrueGramAI.Provider p) {
        final ArrayList<String> models = TrueGramAI.getModels(p.id);
        if (models.isEmpty()) {
            refreshModels(p, true);
            return;
        }
        final String current = TrueGramAI.getModel(p.id);
        final String[] items = new String[models.size() + 1];
        items[0] = "Обновить список моделей";
        for (int i = 0; i < models.size(); i++) {
            items[i + 1] = (models.get(i).equals(current) ? "✓ " : "") + models.get(i);
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(p.name);
        b.setItems(items, (d, which) -> {
            if (which == 0) {
                refreshModels(p, true);
            } else {
                TrueGramAI.setActiveProvider(p.id);
                TrueGramAI.setModel(p.id, models.get(which - 1));
                updateChip();
            }
        });
        showDialog(b.create());
    }

    private void refreshModels(final TrueGramAI.Provider p, final boolean reopen) {
        toast("Загружаю список моделей…");
        TrueGramAI.fetchModels(p, TrueGramAI.getKey(p.id), TrueGramAI.getBaseUrl(p), (models, err) -> {
            if (err != null || models == null) {
                toast(p.name + ": " + err);
                return;
            }
            TrueGramAI.saveModels(p.id, models);
            if (TrueGramAI.getModel(p.id).isEmpty() || !models.contains(TrueGramAI.getModel(p.id))) {
                TrueGramAI.setModel(p.id, TrueGramAI.pickModel(p, models));
            }
            updateChip();
            if (reopen && getParentActivity() != null) {
                showModelList(p);
            }
        });
    }

    // ------------------------------------------------------------ messages list

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final FrameLayout box = new FrameLayout(parent.getContext());
            box.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final TextView tv = new TextView(parent.getContext());
            tv.setTextSize(16);
            tv.setTextIsSelectable(true);
            tv.setPadding(dp(12), dp(8), dp(12), dp(8));
            tv.setMaxWidth((int) (AndroidUtilities.displaySize.x * 0.82f));
            box.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(box) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final TrueGramAI.Msg m = messages.get(position);
            final FrameLayout box = (FrameLayout) holder.itemView;
            final TextView tv = (TextView) box.getChildAt(0);
            final boolean mine = m.role.equals("user");
            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tv.getLayoutParams();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            lp.topMargin = dp(3);
            lp.bottomMargin = dp(3);
            tv.setLayoutParams(lp);
            final GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(mine ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) : Theme.getColor(Theme.key_windowBackgroundGray));
            tv.setBackground(bg);
            tv.setTextColor(mine ? Color.WHITE : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            tv.setText(m.text + (mine && !m.images.isEmpty() ? "\n📎 вложений: " + m.images.size() : ""));
        }
    }
}
