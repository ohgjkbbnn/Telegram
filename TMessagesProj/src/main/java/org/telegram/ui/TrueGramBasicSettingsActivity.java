package org.telegram.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import org.telegram.messenger.TrueGramDeletedAlpha;
import org.telegram.messenger.TrueGramDeletedMark;
import org.telegram.messenger.TrueGramVoice;
import org.telegram.messenger.TrueGramAdBlock;
import org.telegram.messenger.TrueGramMute;
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
    private static final int ID_DELETED_ALPHA = 18;
    private static final int ID_DELETED_MARK = 19;
    private static final int ID_MUTE_LS = 20;
    private static final int ID_MUTE_REVOKE = 21;
    private static final int ID_VOICE = 22;
    private static final int ID_AD_BLOCK = 23;

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
        item(ID_SAVE_DELETED, TrueGramM3.ICON_EYE, "Читать удалённые сообщения", "Остаются в чате с пометкой «удалено»", sw);
        item(ID_DELETED_MARK, TrueGramDeletedMark.iconPath(TrueGramDeletedMark.getMode()), "Метка удалёнок", TrueGramDeletedMark.NAMES[TrueGramDeletedMark.getMode()], TrueGramM3.TRAILING_CHEVRON);
        item(ID_DELETED_COLOR, TrueGramM3.ICON_DELETE, "Цвет пометки «удалено»", null, color);
        item(ID_DELETED_ALPHA, TrueGramM3.ICON_GHOST, "Прозрачность удалённых сообщений", TrueGramDeletedAlpha.NAMES[TrueGramDeletedAlpha.getLevel()], TrueGramM3.TRAILING_CHEVRON);
        item(ID_SAVE_EDITS, TrueGramM3.ICON_EDIT, "Сохранять правки", "Значок ↺ рядом со временем, история версий", sw);
        item(ID_EDITED_COLOR, TrueGramM3.ICON_PALETTE, "Цвет пометки «изменено» ↺", null, color);
        item(ID_AUTO_BOLD, TrueGramM3.ICON_BOLD, "Жирный шрифт при отправке", null, sw);
        item(ID_BACKGROUND, TrueGramM3.ICON_SYNC, "Работать в фоне", "Держит соединение, когда приложение закрыто", sw);
        note("Удалённые сообщения остаются в чате с пометкой «удалено» у времени. Если сообщение изменили, рядом со временем появится ↺, нажмите на время, чтобы увидеть прошлые версии. «Работать в фоне» держит соединение с Telegram, когда приложение закрыто (в шторке будет постоянное уведомление). Без него сообщения, которые написали и удалили, пока приложение закрыто, могут не сохраниться. Для надёжной работы отключите ограничение батареи для приложения. Одноразовые сообщения и сообщения с таймером не сохраняются.");

        label("Реклама");
        item(ID_AD_BLOCK, TrueGramM3.ICON_DELETE, "Блокировать рекламу", "Спонсорские сообщения внизу каналов и групп", sw);

        label("Мут в ЛС");
        item(ID_MUTE_LS, TrueGramM3.ICON_MUTE, "Мут в личных сообщениях", "Команды в чате: !mute, !unmute, !status", sw);
        item(ID_MUTE_REVOKE, TrueGramM3.ICON_DELETE, "Удалять и у собеседника", "Сообщения замьюченного удаляются у обоих", sw);
        note("Включите функцию и напишите в личном чате с человеком команду. Она не отправится собеседнику. !mute замутит на 1 час, !mute 30m на 30 минут (s секунды, m минуты, h часы, d дни, w недели), !mute forever навсегда, !unmute снимает мут, !status показывает, в муте ли человек. Пока человек в муте, его новые сообщения сразу удаляются. В группах удаляются только сообщения с вашим тегом или ответом на ваше сообщение.");

        label("Кнопки нижней панели");
        tabsEditor();
        note("Нажмите на вкладку, чтобы убрать или вернуть её. «Чаты» убрать нельзя, а «Настройки» и «Профиль» нельзя убрать вместе, иначе пропадёт доступ к настройкам.");
        if (TrueGramConfig.appliedTabsMask != -1 && TrueGramConfig.appliedTabsMask != TrueGramConfig.getTabsMask()) {
            item(ID_APPLY_TABS, TrueGramM3.ICON_SYNC, "Применить и перезапустить", "Нужно, чтобы изменения панели вступили в силу", TrueGramM3.TRAILING_CHEVRON);
        }

        label("Фото и видео");
        item(ID_PHOTO_QUALITY, TrueGramM3.ICON_PHOTO, "Улучшать качество фото", "До 2560 px по длинной стороне, меньше сжатие", sw);
        item(ID_VIDEO_60, TrueGramM3.ICON_VIDEO, "Съёмка 1080p 60 FPS", "Если камера устройства это поддерживает", sw);

        label("Профиль");
        item(ID_ID_COLOR, TrueGramM3.ICON_INFO, "Цвет ID и ДЦ", "Как показываются ID и дата-центр в профиле", color);

        label("Приватность");
        item(ID_SCREENSHOTS, TrueGramM3.ICON_SCREEN, "Скриншоты и запись экрана", "Снимает запрет на скриншоты в приложении", sw);
        item(ID_LOCAL_PIN, TrueGramM3.ICON_PIN, "Бесконечный локальный закреп", "Любое число чатов, хранится только на этом устройстве", sw);

        label("Аудио");
        item(ID_AUDIO_BITRATE, TrueGramM3.ICON_MIC, "Увеличенный битрейт аудио", "Голосовые в высоком качестве, файл больше", sw);
        item(ID_VOICE, TrueGramM3.ICON_PERSON, "Изменение голоса", TrueGramVoice.currentName(), TrueGramM3.TRAILING_CHEVRON);
        item(ID_LYRICS, TrueGramM3.ICON_MUSIC, "Показывать текст песни", "Тексты из LRCLIB, нужен интернет", sw);
        item(ID_LYRICS_COLOR, TrueGramM3.ICON_PALETTE, "Цвет спетых строк", null, color);
        note("Изменение голоса работает при записи голосовых сообщений: выбранный эффект накладывается прямо во время записи, готовое сообщение уходит уже с изменённым голосом. Звонки и кружки не затрагиваются. «Свой тон» сдвигает высоту от -12 до +12 полутонов.");
        note("Пока играет музыка, под плашкой плеера идёт текст песни: спетые строки подсвечиваются выбранным цветом. Тексты берутся из базы LRCLIB по исполнителю и названию трека, нужен интернет. Для редких треков текста может не быть, тогда плашка остаётся обычной.");

        label("О приложении");
        item(ID_DEVELOPER, TrueGramM3.ICON_PERSON, "Разработчик", "@og_auctioneer", TrueGramM3.TRAILING_CHEVRON);
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
            case ID_MUTE_LS:
                return TrueGramMute.isEnabled();
            case ID_MUTE_REVOKE:
                return TrueGramMute.isRevokeEnabled();
            case ID_AD_BLOCK:
                return TrueGramAdBlock.isEnabled();
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
            case ID_MUTE_LS:
                TrueGramMute.setEnabled(enabled);
                break;
            case ID_MUTE_REVOKE:
                TrueGramMute.setRevokeEnabled(enabled);
                break;
            case ID_AD_BLOCK:
                TrueGramAdBlock.setEnabled(enabled);
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
        } else if (e.id == ID_DELETED_MARK) {
            showMarkDialog(context);
        } else if (e.id == ID_DELETED_ALPHA) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Прозрачность удалённых сообщений");
            builder.setItems(TrueGramDeletedAlpha.NAMES, (dialog, which) -> {
                TrueGramDeletedAlpha.setLevel(which);
                buildEntries();
                listAdapter.notifyDataSetChanged();
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        } else if (e.id == ID_VOICE) {
            TrueGramVoice.showPicker(context, () -> {
                buildEntries();
                listAdapter.notifyDataSetChanged();
            });
        } else if (e.id == ID_APPLY_TABS) {
            confirmRestart(context);
        } else if (e.id == ID_DEVELOPER) {
            Browser.openUrl(context, "https://t.me/og_auctioneer");
        }
    }

    private int dpi(int v) {
        return AndroidUtilities.dp(v);
    }

    /** Rounded tonal rectangle used for dialog rows and fields. */
    private GradientDrawable tonal(int color, int radiusDp) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dpi(radiusDp));
        return d;
    }

    /** M3 pill button: filled (primary) or tonal. */
    private TextView pillButton(Context context, String text, boolean filled) {
        final TextView tv = new TextView(context);
        final int primary = TrueGramM3.primary();
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(dpi(22), dpi(10), dpi(22), dpi(10));
        tv.setTextColor(filled ? Color.WHITE : primary);
        tv.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(filled ? 0x40FFFFFF : ((primary & 0x00FFFFFF) | (64 << 24))),
                tonal(filled ? primary : ((primary & 0x00FFFFFF) | (36 << 24)), 100),
                tonal(Color.WHITE, 100)));
        return tv;
    }

    private LinearLayout buttonRow(Context context, TextView... buttons) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        for (int i = 0; i < buttons.length; i++) {
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.leftMargin = dpi(8);
            }
            row.addView(buttons[i], lp);
        }
        return row;
    }

    // ------------------------------------------------------------------ deleted mark dialog

    private void showMarkDialog(Context context) {
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpi(12), dpi(4), dpi(12), dpi(12));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        final int current = TrueGramDeletedMark.getMode();
        final int primary = TrueGramM3.primary();
        final int rippleColor = (primary & 0x00FFFFFF) | (48 << 24);

        for (int i = 0; i < TrueGramDeletedMark.NAMES.length; i++) {
            final int mode = i;
            final boolean selected = mode == current;
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dpi(12), dpi(6), dpi(14), dpi(6));
            row.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(rippleColor),
                    selected ? tonal((primary & 0x00FFFFFF) | (40 << 24), 20) : null,
                    tonal(Color.WHITE, 20)));

            // the icon preview uses the current "deleted" color (gray by default)
            final TrueGramM3.IconView icon = new TrueGramM3.IconView(context, 8);
            icon.setIcon(TrueGramDeletedMark.iconPath(mode), currentColor(ID_DELETED_COLOR));
            row.addView(icon, new LinearLayout.LayoutParams(dpi(40), dpi(40)));

            final TextView label = new TextView(context);
            label.setTextSize(16);
            label.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            label.setText(TrueGramDeletedMark.NAMES[mode]);
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dpi(12);
            row.addView(label, lp);

            if (selected) {
                final TrueGramM3.IconView check = new TrueGramM3.IconView(context, 0);
                check.setIcon(TrueGramM3.ICON_CHECK, primary);
                row.addView(check, new LinearLayout.LayoutParams(dpi(24), dpi(24)));
            }

            row.setOnClickListener(v -> {
                TrueGramDeletedMark.setMode(mode);
                buildEntries();
                listAdapter.notifyDataSetChanged();
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
            });
            final LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dpi(2);
            root.addView(row, rlp);
        }

        final TextView cancel = pillButton(context, "Отмена", false);
        cancel.setOnClickListener(v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });
        final LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dpi(8);
        root.addView(buttonRow(context, cancel), blp);

        builder.setTitle("Метка удалёнок");
        builder.setView(root);
        dialogRef[0] = builder.show();
    }

    // ------------------------------------------------------------------ color dialog

    private int colorByKind(int which) {
        final int id = which == 1 ? ID_EDITED_COLOR : which == 2 ? ID_ID_COLOR : which == 3 ? ID_LYRICS_COLOR : ID_DELETED_COLOR;
        return currentColor(id);
    }

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
        final int primary = TrueGramM3.primary();
        final int current = colorByKind(which) | 0xFF000000;

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpi(12), dpi(4), dpi(12), dpi(12));

        final EditText hex = new EditText(context);
        hex.setHint("#RRGGBB");
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT);
        hex.setTextSize(16);
        hex.setText(String.format("#%06X", current & 0x00FFFFFF));
        hex.setSelection(hex.getText().length());
        hex.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        hex.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        hex.setBackground(tonal((primary & 0x00FFFFFF) | (28 << 24), 16));
        hex.setPadding(dpi(16), dpi(12), dpi(16), dpi(12));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (int r = 0; r < palette.length / 4; r++) {
            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER);
            for (int c = 0; c < 4; c++) {
                final int color = palette[r * 4 + c];
                final boolean selected = (color | 0xFF000000) == current;

                // 52dp cell: selection ring around a 40dp color disc
                final FrameLayout cell = new FrameLayout(context);
                final GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(Color.TRANSPARENT);
                if (selected) {
                    ring.setStroke(dpi(3), primary);
                }
                cell.setBackground(ring);

                final View disc = new View(context);
                final GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(color);
                shape.setStroke(dpi(1), 0x33888888);
                disc.setBackground(shape);
                cell.addView(disc, LayoutHelper.createFrame(40, 40, android.view.Gravity.CENTER));

                if (selected) {
                    final boolean light = androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.6;
                    final TrueGramM3.IconView check = new TrueGramM3.IconView(context, 0);
                    check.setIcon(TrueGramM3.ICON_CHECK, light ? Color.BLACK : Color.WHITE);
                    cell.addView(check, LayoutHelper.createFrame(22, 22, android.view.Gravity.CENTER));
                }

                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpi(52), dpi(52));
                lp.setMargins(dpi(6), dpi(4), dpi(6), dpi(4));
                row.addView(cell, lp);
                cell.setOnClickListener(v -> {
                    applyColor(which, color);
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                });
            }
            root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dpi(12);
        hlp.leftMargin = dpi(6);
        hlp.rightMargin = dpi(6);
        root.addView(hex, hlp);

        final TextView reset = pillButton(context, "Сбросить", false);
        final TextView done = pillButton(context, "Готово", true);
        reset.setOnClickListener(v -> {
            applyColor(which, 0);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });
        done.setOnClickListener(v -> {
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
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        });
        final LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dpi(14);
        root.addView(buttonRow(context, reset, done), blp);

        builder.setTitle(which == 1 ? "Цвет пометки «изменено»" : which == 2 ? "Цвет ID и ДЦ" : which == 3 ? "Цвет спетых строк" : "Цвет пометки «удалено»");
        final ScrollView colorScroll = new ScrollView(context);
        colorScroll.addView(root);
        builder.setView(colorScroll);
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
