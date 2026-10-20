package org.telegram.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TrueGramConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** TrueGram "Основные" settings (Material 3 style). */
public class TrueGramBasicSettingsActivity extends BaseFragment {

    // view types
    private static final int T_LABEL = 0;
    private static final int T_ITEM = 1;
    private static final int T_NOTE = 2;
    private static final int T_SPACE = 3;
    private static final int T_TABS = 4;

    // item ids
    private static final int ID_SAVE_DELETED = 1;
    private static final int ID_DELETED_COLOR = 2;
    private static final int ID_SAVE_EDITS = 3;
    private static final int ID_EDITED_COLOR = 4;
    private static final int ID_AUTO_BOLD = 5;
    private static final int ID_BACKGROUND = 7;
    private static final int ID_PHOTO_QUALITY = 8;
    private static final int ID_VIDEO_60 = 9;
    private static final int ID_ID_COLOR = 10;
    private static final int ID_SCREENSHOTS = 11;
    private static final int ID_LOCAL_PIN = 12;
    private static final int ID_AUDIO_BITRATE = 13;
    private static final int ID_LYRICS = 14;
    private static final int ID_LYRICS_COLOR = 15;
    private static final int ID_DEVELOPER = 16;
    private static final int ID_APPLY_TABS = 17;

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

    // ------------------------------------------------------------------ model

    private void label(String text) {
        Entry e = new Entry();
        e.type = T_LABEL;
        e.title = text;
        entries.add(e);
    }

    private void note(String text) {
        Entry e = new Entry();
        e.type = T_NOTE;
        e.title = text;
        entries.add(e);
    }

    private void tabsEditor() {
        Entry e = new Entry();
        e.type = T_TABS;
        entries.add(e);
    }

    private void space() {
        Entry e = new Entry();
        e.type = T_SPACE;
        entries.add(e);
    }

    private void item(int id, String emoji, String title, String subtitle, int trailing) {
        Entry e = new Entry();
        e.type = T_ITEM;
        e.id = id;
        e.emoji = emoji;
        e.title = title;
        e.subtitle = subtitle;
        e.trailing = trailing;
        entries.add(e);
    }

    private void buildEntries() {
        entries.clear();
        final int sw = TrueGramM3.TRAILING_SWITCH;
        final int color = TrueGramM3.TRAILING_COLOR;

        label("Сообщения");
        item(ID_SAVE_DELETED, "👁", "Читать удалённые сообщения", "Остаются в чате с пометкой «удалено»", sw);
        item(ID_DELETED_COLOR, "🎨", "Цвет пометки «удалено»", null, color);
        item(ID_SAVE_EDITS, "✏️", "Сохранять правки", "Значок ↺ рядом со временем, история версий", sw);
        item(ID_EDITED_COLOR, "🎨", "Цвет пометки «изменено» ↺", null, color);
        item(ID_AUTO_BOLD, "🅱️", "Жирный шрифт при отправке", null, sw);
        item(ID_BACKGROUND, "🔄", "Работать в фоне", "Держит соединение, когда приложение закрыто", sw);
        note("Удалённые сообщения остаются в чате с пометкой «удалено» у времени. Если сообщение изменили, рядом со временем появится ↺, нажмите на время, чтобы увидеть прошлые версии. «Работать в фоне» держит соединение с Telegram, когда приложение закрыто (в шторке будет постоянное уведомление). Без него сообщения, которые написали и удалили, пока приложение закрыто, могут не сохраниться. Для надёжной работы отключите ограничение батареи для приложения. Одноразовые сообщения и сообщения с таймером не сохраняются.");

        label("Кнопки нижней панели");
        tabsEditor();
        note("Нажмите на вкладку, чтобы убрать или вернуть её. «Чаты» убрать нельзя, а «Настройки» и «Профиль» нельзя убрать вместе, иначе пропадёт доступ к настройкам.");
        if (TrueGramConfig.appliedTabsMask != -1 && TrueGramConfig.appliedTabsMask != TrueGramConfig.getTabsMask()) {
            item(ID_APPLY_TABS, "🔁", "Применить и перезапустить", "Нужно, чтобы изменения панели вступили в силу", TrueGramM3.TRAILING_CHEVRON);
        }

        label("Фото и видео");
        item(ID_PHOTO_QUALITY, "🖼️", "Улучшать качество фото", "До 2560 px по длинной стороне, меньше сжатие", sw);
        item(ID_VIDEO_60, "🎬", "Съёмка 1080p 60 FPS", "Если камера устройства это поддерживает", sw);

        label("Профиль");
        item(ID_ID_COLOR, "🆔", "Цвет ID и ДЦ", "Как показываются ID и дата-центр в профиле", color);

        label("Приватность");
        item(ID_SCREENSHOTS, "📸", "Скриншоты и запись экрана", "Снимает запрет на скриншоты в приложении", sw);
        item(ID_LOCAL_PIN, "📌", "Бесконечный локальный закреп", "Любое число чатов, хранится только на этом устройстве", sw);

        label("Аудио");
        item(ID_AUDIO_BITRATE, "🎙️", "Увеличенный битрейт аудио", "Голосовые в высоком качестве, файл больше", sw);
        item(ID_LYRICS, "🎵", "Показывать текст песни", "Тексты из LRCLIB, нужен интернет", sw);
        item(ID_LYRICS_COLOR, "🎨", "Цвет спетых строк", null, color);
        note("Пока играет музыка, под плашкой плеера идёт текст песни: спетые строки подсвечиваются выбранным цветом. Тексты берутся из базы LRCLIB по исполнителю и названию трека, нужен интернет. Для редких треков текста может не быть, тогда плашка остаётся обычной.");

        label("О приложении");
        item(ID_DEVELOPER, "👤", "Разработчик", "@og_auctioneer", TrueGramM3.TRAILING_CHEVRON);
        space();

        // compute grouped shapes: consecutive T_ITEM entries form one card group
        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            if (e.type != T_ITEM) {
                continue;
            }
            final boolean prevItem = i > 0 && entries.get(i - 1).type == T_ITEM;
            final boolean nextItem = i < entries.size() - 1 && entries.get(i + 1).type == T_ITEM;
            if (prevItem && nextItem) {
                e.shape = TrueGramM3.SHAPE_MIDDLE;
            } else if (prevItem) {
                e.shape = TrueGramM3.SHAPE_BOTTOM;
            } else if (nextItem) {
                e.shape = TrueGramM3.SHAPE_TOP;
            } else {
                e.shape = TrueGramM3.SHAPE_SINGLE;
            }
        }
    }

    // ------------------------------------------------------------------ bottom bar

    private boolean[] tabHiddenFlags() {
        return new boolean[]{
                false,
                TrueGramConfig.isContactsTabHidden(),
                TrueGramConfig.isMiddleTabHidden(),
                TrueGramConfig.isProfileTabHidden()
        };
    }

    private void onTabToggled(int index) {
        final Context context = listView.getContext();
        if (index == 0) {
            Toast.makeText(context, "Вкладку «Чаты» убрать нельзя", Toast.LENGTH_SHORT).show();
            return;
        }
        if (index == 1) {
            TrueGramConfig.setContactsTabHidden(!TrueGramConfig.isContactsTabHidden());
        } else if (index == 2) {
            final boolean hide = !TrueGramConfig.isMiddleTabHidden();
            if (hide && TrueGramConfig.isProfileTabHidden()) {
                Toast.makeText(context, "Нельзя убрать «Настройки» и «Профиль» вместе", Toast.LENGTH_SHORT).show();
                return;
            }
            TrueGramConfig.setMiddleTabHidden(hide);
        } else if (index == 3) {
            final boolean hide = !TrueGramConfig.isProfileTabHidden();
            if (hide && TrueGramConfig.isMiddleTabHidden()) {
                Toast.makeText(context, "Нельзя убрать «Настройки» и «Профиль» вместе", Toast.LENGTH_SHORT).show();
                return;
            }
            TrueGramConfig.setProfileTabHidden(hide);
        }
        buildEntries();
        listAdapter.notifyDataSetChanged();
    }

    private void confirmRestart(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Перезапуск");
        builder.setMessage("Приложение перезапустится, чтобы применить новую нижнюю панель.");
        builder.setPositiveButton("Перезапустить", (d, w) -> restartApp(context));
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void restartApp(Context context) {
        try {
            final Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent == null) {
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            final PendingIntent pi = PendingIntent.getActivity(context, 7771, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            final AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.set(AlarmManager.RTC, System.currentTimeMillis() + 300, pi);
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Exception ignore) {
        }
    }

    // ------------------------------------------------------------------ config bridge

    private boolean isChecked(int id) {
        switch (id) {
            case ID_SAVE_DELETED:
                return TrueGramConfig.isSaveDeletedEnabled();
            case ID_SAVE_EDITS:
                return TrueGramConfig.isSaveEditsEnabled();
            case ID_AUTO_BOLD:
                return TrueGramConfig.isAutoBoldEnabled();
            case ID_BACKGROUND:
                return TrueGramConfig.isBackgroundEnabled();
            case ID_PHOTO_QUALITY:
                return TrueGramConfig.isPhotoQualityEnabled();
            case ID_VIDEO_60:
                return TrueGramConfig.isVideo60Enabled();
            case ID_SCREENSHOTS:
                return TrueGramConfig.isScreenshotsAllowed();
            case ID_LOCAL_PIN:
                return TrueGramConfig.isLocalPinEnabled();
            case ID_AUDIO_BITRATE:
                return TrueGramConfig.isAudioBitrateEnabled();
            case ID_LYRICS:
                return TrueGramConfig.isLyricsEnabled();
            default:
                return false;
        }
    }

    private void toggle(int id) {
        final boolean enabled = !isChecked(id);
        switch (id) {
            case ID_SAVE_DELETED:
                TrueGramConfig.setSaveDeletedEnabled(enabled);
                break;
            case ID_SAVE_EDITS:
                TrueGramConfig.setSaveEditsEnabled(enabled);
                break;
            case ID_AUTO_BOLD:
                TrueGramConfig.setAutoBoldEnabled(enabled);
                break;
            case ID_BACKGROUND:
                TrueGramConfig.setBackgroundEnabled(enabled);
                break;
            case ID_PHOTO_QUALITY:
                TrueGramConfig.setPhotoQualityEnabled(enabled);
                break;
            case ID_VIDEO_60:
                TrueGramConfig.setVideo60Enabled(enabled);
                break;
            case ID_SCREENSHOTS:
                TrueGramConfig.setScreenshotsAllowed(enabled);
                break;
            case ID_LOCAL_PIN:
                TrueGramConfig.setLocalPinEnabled(enabled);
                break;
            case ID_AUDIO_BITRATE:
                TrueGramConfig.setAudioBitrateEnabled(enabled);
                break;
            case ID_LYRICS:
                TrueGramConfig.setLyricsEnabled(enabled);
                break;
            default:
                break;
        }
    }

    /** 0 = deleted, 1 = edited, 2 = id/dc, 3 = lyrics; -1 if not a color row. */
    private int colorKind(int id) {
        switch (id) {
            case ID_DELETED_COLOR:
                return 0;
            case ID_EDITED_COLOR:
                return 1;
            case ID_ID_COLOR:
                return 2;
            case ID_LYRICS_COLOR:
                return 3;
            default:
                return -1;
        }
    }

    private int currentColor(int id) {
        int c;
        switch (colorKind(id)) {
            case 1:
                c = TrueGramConfig.getEditedColor();
                break;
            case 2:
                c = TrueGramConfig.getIdColor();
                break;
            case 3:
                c = TrueGramConfig.getLyricsColor();
                break;
            default:
                c = TrueGramConfig.getDeletedColor();
                break;
        }
        return c != 0 ? c : 0xFF8E8E93;
    }

    private void applyColor(int which, int color) {
        if (which == 1) {
            TrueGramConfig.setEditedColor(color);
        } else if (which == 2) {
            TrueGramConfig.setIdColor(color);
        } else if (which == 3) {
            TrueGramConfig.setLyricsColor(color);
        } else {
            TrueGramConfig.setDeletedColor(color);
        }
    }

    // ------------------------------------------------------------------ fragment

    @Override
    public boolean onFragmentCreate() {
        buildEntries();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            buildEntries();
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Основные");
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
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutAnimation(null);
        listView.setItemAnimator(null);
        listAdapter = new ListAdapter();
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void onItemClicked(Context context, TrueGramM3.M3Item view, Entry e) {
        if (e.trailing == TrueGramM3.TRAILING_SWITCH) {
            toggle(e.id);
            view.setChecked(isChecked(e.id), true);
        } else if (e.trailing == TrueGramM3.TRAILING_COLOR) {
            showColorDialog(context, colorKind(e.id));
        } else if (e.id == ID_APPLY_TABS) {
            confirmRestart(context);
        } else if (e.id == ID_DEVELOPER) {
            Browser.openUrl(context, "https://t.me/og_auctioneer");
        }
    }

    // ------------------------------------------------------------------ color dialog

    private void showColorDialog(Context context, final int which) {
        final int[] palette = {
                0xFFFF3B30, 0xFFFF6B6B, 0xFFFF9500, 0xFFFFB84D,
                0xFFFFCC00, 0xFFFFE066, 0xFFA3E635, 0xFF34C759,
                0xFF16A34A, 0xFF00C7BE, 0xFF14B8A6, 0xFF5AC8FA,
                0xFF0A84FF, 0xFF2563EB, 0xFF5E5CE6, 0xFF7C3AED,
                0xFFAF52DE, 0xFFD946EF, 0xFFFF2D55, 0xFFF43F5E,
                0xFF8B4513, 0xFFA2845E, 0xFFFFFFFF, 0xFFD1D1D6,
                0xFF8E8E93, 0xFF48484A, 0xFF000000, 0xFFFFD1DC,
                0xFFB5EAD7, 0xFFC7CEEA, 0xFFFFDAC1, 0xFFE2F0CB
        };
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        final int pad = AndroidUtilities.dp(16);
        root.setPadding(pad, pad, pad, 0);

        final EditText hex = new EditText(context);
        hex.setHint("#RRGGBB");
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT);
        hex.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        hex.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (int r = 0; r < palette.length / 4; r++) {
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 4; c++) {
                final int color = palette[r * 4 + c];
                final View dot = new View(context);
                final GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(color);
                shape.setStroke(AndroidUtilities.dp(1), 0x55888888);
                dot.setBackground(shape);
                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), AndroidUtilities.dp(44));
                lp.setMargins(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
                row.addView(dot, lp);
                dot.setOnClickListener(v -> {
                    applyColor(which, color);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                });
            }
            root.addView(row);
        }
        root.addView(hex, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        builder.setTitle(which == 1 ? "Цвет пометки «изменено»" : which == 2 ? "Цвет ID и ДЦ" : which == 3 ? "Цвет спетых строк" : "Цвет пометки «удалено»");
        final ScrollView colorScroll = new ScrollView(context);
        colorScroll.addView(root);
        builder.setView(colorScroll);
        builder.setPositiveButton("OK", (d, w) -> {
            final String text = hex.getText().toString().trim();
            if (!text.isEmpty()) {
                try {
                    final int parsed = Color.parseColor(text.startsWith("#") ? text : "#" + text) | 0xFF000000;
                    applyColor(which, parsed);
                } catch (Exception ignore) {
                }
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Сбросить", (d, w) -> {
            applyColor(which, 0);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        dialogRef[0] = builder.show();
    }

    // ------------------------------------------------------------------ adapter

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false; // clicks are handled by the M3 items themselves
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
                case T_TABS:
                    view = new TrueGramM3.TabsEditorView(context);
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
                case T_TABS:
                    ((TrueGramM3.TabsEditorView) holder.itemView).bind(tabHiddenFlags(), TrueGramBasicSettingsActivity.this::onTabToggled);
                    break;
                case T_ITEM: {
                    final TrueGramM3.M3Item item = (TrueGramM3.M3Item) holder.itemView;
                    item.bind(e.shape, e.emoji, e.title, e.subtitle, e.trailing);
                    if (e.trailing == TrueGramM3.TRAILING_SWITCH) {
                        item.setChecked(isChecked(e.id), false);
                    } else if (e.trailing == TrueGramM3.TRAILING_COLOR) {
                        item.setColor(currentColor(e.id));
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
