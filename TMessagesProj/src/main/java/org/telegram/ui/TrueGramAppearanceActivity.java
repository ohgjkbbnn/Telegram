package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TrueGramConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** TrueGram "Оформление": settings style (Material 3 / classic) and bottom bar glass level. */
public class TrueGramAppearanceActivity extends BaseFragment {

    private static final int T_LABEL = 0;
    private static final int T_ITEM = 1;
    private static final int T_NOTE = 2;
    private static final int T_SPACE = 3;
    private static final int T_SLIDER = 4;

    private static final int ID_STYLE_M3 = 1;
    private static final int ID_GLASS = 2;
    private static final int ID_BANNER = 3;
    private static final int ID_BANNER_RESET = 4;
    private static final int ID_BANNER_FOR_ALL = 5;
    private static final int ID_BUBBLE_STYLE = 6;
    private static final int ID_TYPING_ANIM = 7;
    private static final int ID_SMOOTH_CURSOR = 8;
    private static final int ID_DELETE_ANIM = 9;
    private static final int ID_MSG_DELETE = 10;
    private static final int REQUEST_BANNER = 7310;
    private static final long MAX_BANNER_VIDEO_MS = 60_000L;

    private static final String[] GLASS_NAMES = {
            "Максимально прозрачная",
            "Стеклянная",
            "Плотная (стандартная)"
    };

    private static class Entry {
        int type;
        int id;
        String emoji;
        String title;
        String subtitle;
        int trailing;
        int shape;
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private void buildEntries() {
        entries.clear();

        Entry e = new Entry();
        e.type = T_LABEL;
        e.title = "Цвет пузырей";
        entries.add(e);

        e = new Entry();
        e.type = T_SLIDER;
        entries.add(e);

        e = new Entry();
        e.type = T_NOTE;
        e.title = "Двигайте ползунок, чтобы сделать пузыри насыщеннее или спокойнее. Откройте чат заново, чтобы увидеть изменения.";
        entries.add(e);

        e = new Entry();
        e.type = T_LABEL;
        e.title = "Стиль настроек";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_STYLE_M3;
        e.emoji = TrueGramM3.ICON_PALETTE;
        e.title = "Стиль Material 3";
        e.subtitle = "Скруглённые карточки и значки. Выключите для классического вида";
        e.trailing = TrueGramM3.TRAILING_SWITCH;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_LABEL;
        e.title = "Нижняя панель";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_GLASS;
        e.emoji = TrueGramM3.ICON_OPACITY;
        e.title = "Прозрачность стекла";
        e.subtitle = GLASS_NAMES[TrueGramConfig.getTabGlassLevel()];
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_NOTE;
        e.title = "Эффект стекла зависит от режима «Жидкое стекло» в настройках энергосбережения Telegram. Изменения применяются, когда вы возвращаетесь на главный экран. Какие кнопки показывать в панели, настраивается в разделе «Основные».";
        entries.add(e);

        e = new Entry();
        e.type = T_LABEL;
        e.title = "Стиль пузырей";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_BUBBLE_STYLE;
        e.emoji = TrueGramM3.ICON_PALETTE;
        e.title = "Тёмные пузыри с рамкой";
        e.subtitle = "Пунктирная цветная рамка и плашка с именем. Откройте чат заново";
        e.trailing = TrueGramM3.TRAILING_SWITCH;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_LABEL;
        e.title = "Анимации текста";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_TYPING_ANIM;
        e.emoji = TrueGramM3.ICON_PALETTE;
        e.title = "Анимация букв";
        e.subtitle = "Сейчас: " + TrueGramTypingFx.STYLE_NAMES[TrueGramTypingFx.getStyle()];
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_NOTE;
        e.title = "Новые буквы появляются с анимацией, а стёртые разлетаются выбранным эффектом. Работает в поле ввода сообщения.";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_DELETE_ANIM;
        e.emoji = TrueGramM3.ICON_DELETE;
        e.title = "Анимация удаления";
        e.subtitle = "Сейчас: " + TrueGramTypingFx.DELETE_NAMES[TrueGramTypingFx.getDeleteStyle()];
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_MSG_DELETE;
        e.emoji = TrueGramM3.ICON_DELETE;
        e.title = "Анимация удаления сообщений";
        e.subtitle = "Сейчас: " + TrueGramDeleteFx.NAMES[TrueGramDeleteFx.getMode()];
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_SMOOTH_CURSOR;
        e.emoji = TrueGramM3.ICON_PALETTE;
        e.title = "Плавный курсор";
        e.subtitle = "Сейчас: " + TrueGramTypingFx.CURSOR_NAMES[TrueGramTypingFx.getCursorMode()] + ". Откройте чат заново";
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_LABEL;
        e.title = "Баннер профиля";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_BANNER;
        e.emoji = TrueGramM3.ICON_PHOTO;
        e.title = "Выбрать фото или видео";
        e.subtitle = "Хранится на сервере, видно пользователям мода";
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        e.shape = TrueGramM3.SHAPE_TOP;
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_BANNER_RESET;
        e.emoji = TrueGramM3.ICON_DELETE;
        e.title = "Сбросить баннер";
        e.subtitle = "Вернуть обычный цвет карточки";
        e.trailing = TrueGramM3.TRAILING_NONE;
        e.shape = TrueGramM3.SHAPE_BOTTOM;
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_BANNER_FOR_ALL;
        e.emoji = TrueGramM3.ICON_PERSON;
        e.title = "Применить для всех профилей";
        e.subtitle = "Баннер будет показываться на профилях всех пользователей в вашем клиенте";
        e.trailing = TrueGramM3.TRAILING_SWITCH;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_SPACE;
        entries.add(e);
    }

    @Override
    public boolean onFragmentCreate() {
        buildEntries();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Оформление");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        actionBar.setAdaptiveBackground(listView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listAdapter = new ListAdapter();
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void refresh() {
        buildEntries();
        listAdapter.notifyDataSetChanged();
    }

    private void onItemClicked(Context context, TrueGramM3.M3Item view, Entry e) {
        if (e.id == ID_STYLE_M3) {
            TrueGramConfig.setM3Style(!TrueGramConfig.isM3Style());
            refresh();
        } else if (e.id == ID_BUBBLE_STYLE) {
            TrueGramConfig.setBubbleStyle(TrueGramConfig.getBubbleStyle() == 1 ? 0 : 1);
            refresh();
        } else if (e.id == ID_TYPING_ANIM) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Анимация букв");
            builder.setItems(TrueGramTypingFx.STYLE_NAMES, (dialog, which) -> {
                TrueGramTypingFx.setStyle(which);
                refresh();
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        } else if (e.id == ID_MSG_DELETE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Анимация удаления сообщений");
            builder.setItems(TrueGramDeleteFx.NAMES, (dialog, which) -> {
                TrueGramDeleteFx.setMode(which);
                refresh();
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        } else if (e.id == ID_DELETE_ANIM) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Анимация удаления");
            builder.setItems(TrueGramTypingFx.DELETE_NAMES, (dialog, which) -> {
                TrueGramTypingFx.setDeleteStyle(which);
                refresh();
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        } else if (e.id == ID_SMOOTH_CURSOR) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Плавный курсор");
            builder.setItems(TrueGramTypingFx.CURSOR_NAMES, (dialog, which) -> {
                TrueGramTypingFx.setCursorMode(which);
                refresh();
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        } else if (e.id == ID_BANNER) {
            pickBanner();
        } else if (e.id == ID_BANNER_RESET) {
            resetBanner();
        } else if (e.id == ID_BANNER_FOR_ALL) {
            TrueGramConfig.setBannerForAll(!TrueGramConfig.isBannerForAll());
            refresh();
        } else if (e.id == ID_GLASS) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Прозрачность стекла");
            builder.setItems(GLASS_NAMES, (dialog, which) -> {
                TrueGramConfig.setTabGlassLevel(which);
                refresh();
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        }
    }

    private View createSliderRow(Context context) {
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        card.setBackground(TrueGramM3.cardBackground(TrueGramM3.SHAPE_SINGLE));

        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);

        final TextView title = new TextView(context);
        title.setText("Насыщенность пузырей");
        title.setTextSize(16);
        title.setTextColor(TrueGramM3.onSurface());
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = new TextView(context);
        value.setTextSize(15);
        value.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        value.setTextColor(TrueGramM3.primary());
        value.setText(TrueGramConfig.getBubbleStrength() + "%");
        header.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final android.widget.SeekBar bar = new android.widget.SeekBar(context);
        bar.setMax(100);
        bar.setProgress(TrueGramConfig.getBubbleStrength());
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(progress + "%");
                if (fromUser) {
                    TrueGramConfig.setBubbleStrength(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
            }
        });
        card.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(2));
        wrapper.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    private void pickBanner() {
        if (getParentActivity() == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        try {
            getParentActivity().startActivityForResult(intent, REQUEST_BANNER);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_BANNER || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        saveBanner(data.getData());
    }

    private void saveBanner(Uri uri) {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final String mime = activity.getContentResolver().getType(uri);
        final boolean video = mime != null && mime.startsWith("video");
        // unique name: a playing banner keeps its old file, so the new one never overwrites it
        final File target = new File(activity.getFilesDir(), "truegram_banner_" + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"));
        try (InputStream in = activity.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                return;
            }
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            FileLog.e(e);
            target.delete();
            Toast.makeText(activity, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
            return;
        }
        if (video) {
            long durationMs = 0;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(target.getAbsolutePath());
                String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                durationMs = d != null ? Long.parseLong(d) : 0;
            } catch (Exception ignore) {
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignore) {
                }
            }
            if (durationMs > MAX_BANNER_VIDEO_MS) {
                target.delete();
                Toast.makeText(activity, "Видео должно быть не длиннее 60 секунд", Toast.LENGTH_LONG).show();
                return;
            }
        }
        // the picked file goes to the server only; the local copy is removed after upload
        uploadOwnBanner(target, video);
        refresh();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces);
    }

    private void resetBanner() {
        final String token = TrueGramConfig.getBannerToken();
        if (!token.isEmpty()) {
            TrueGramBannerApi.delete(getUserConfig().getClientUserId(), token, (code, msg) -> AndroidUtilities.runOnUIThread(() -> {
                if (code != 200) {
                    Toast.makeText(getParentActivity() != null ? getParentActivity() : org.telegram.messenger.ApplicationLoader.applicationContext, "Не удалось удалить баннер на сервере (" + code + ")", Toast.LENGTH_SHORT).show();
                }
            }));
        }
        refresh();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces);
    }

    private void uploadOwnBanner(final File file, final boolean video) {
        final long me = getUserConfig().getClientUserId();
        final String token = TrueGramConfig.getBannerToken();
        if (token.isEmpty()) {
            loginThenUpload(me, file, video);
        } else {
            sendBanner(me, token, file, video);
        }
    }

    /** Sends a confirm button to this account in the bot, then waits for the press and gets a token. */
    private void loginThenUpload(final long me, final File file, final boolean video) {
        TrueGramBannerApi.startAuth(me, (code, session) -> AndroidUtilities.runOnUIThread(() -> {
            if (code == 409) {
                toast("Открой бота TrueGram в Telegram, нажми Start и повтори");
                return;
            }
            if (code != 200 || session == null || session.isEmpty()) {
                toast("Сервер недоступен (" + code + ")");
                return;
            }
            toast("Нажми «Подтвердить вход» в боте TrueGram");
            TrueGramBannerApi.waitToken(session, (c2, newToken) -> AndroidUtilities.runOnUIThread(() -> {
                if (c2 == 200 && newToken != null && !newToken.isEmpty()) {
                    TrueGramConfig.setBannerToken(newToken);
                    sendBanner(me, newToken, file, video);
                } else {
                    toast("Вход не подтверждён, попробуй ещё раз");
                }
            }));
        }));
    }

    private void sendBanner(final long me, final String token, final File file, final boolean video) {
        TrueGramBannerApi.upload(me, token, file, video, (code, msg) -> AndroidUtilities.runOnUIThread(() -> {
            if (code == 401 || code == 403) {
                TrueGramConfig.setBannerToken("");
                loginThenUpload(me, file, video);
                return;
            }
            file.delete();
            if (code == 200) {
                toast("Баннер загружен, его увидят пользователи мода");
            } else if (code == 422) {
                toast("Видео должно быть не длиннее 60 секунд");
            } else if (code == 413) {
                toast("Файл слишком большой");
            } else {
                toast("Ошибка загрузки (" + code + ")");
            }
        }));
    }

    private void toast(String text) {
        Toast.makeText(getParentActivity() != null ? getParentActivity() : org.telegram.messenger.ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false; // clicks are handled by the items themselves
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        @Override
        public int getItemViewType(int position) {
            return entries.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            View view;
            switch (viewType) {
                case T_LABEL:
                    view = TrueGramM3.createSectionLabel(context);
                    break;
                case T_NOTE:
                    view = TrueGramM3.createSupportingText(context);
                    break;
                case T_ITEM:
                    view = new TrueGramM3.M3Item(context);
                    break;
                case T_SLIDER:
                    view = createSliderRow(context);
                    break;
                case T_SPACE:
                default:
                    view = TrueGramM3.createSpacer(context, 32);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final Entry e = entries.get(position);
            switch (e.type) {
                case T_LABEL:
                    TrueGramM3.bindLabel((TextView) holder.itemView, e.title);
                    break;
                case T_NOTE:
                    TrueGramM3.bindNote((TextView) holder.itemView, e.title);
                    break;
                case T_SLIDER:
                    break;
                case T_ITEM: {
                    final TrueGramM3.M3Item item = (TrueGramM3.M3Item) holder.itemView;
                    item.bind(e.shape, e.emoji, e.title, e.subtitle, e.trailing);
                    if (e.id == ID_STYLE_M3) {
                        item.setChecked(TrueGramConfig.isM3Style(), false);
                    } else if (e.id == ID_BANNER_FOR_ALL) {
                        item.setChecked(TrueGramConfig.isBannerForAll(), false);
                    } else if (e.id == ID_BUBBLE_STYLE) {
                        item.setChecked(TrueGramConfig.getBubbleStyle() == 1, false);
                    }
                    item.setOnClickListener(v -> onItemClicked(item.getContext(), item, e));
                    break;
                }
                default:
                    break;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        return themeDescriptions;
    }
}
