package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** TrueGram "Оформление": settings style (Material 3 / classic) and bottom bar glass level. */
public class TrueGramAppearanceActivity extends BaseFragment {

    private static final int T_LABEL = 0;
    private static final int T_ITEM = 1;
    private static final int T_NOTE = 2;
    private static final int T_SPACE = 3;
    private static final int T_SLIDER = 4;
    private static final int T_SLIDER_ANGLE = 5;
    private static final int T_SLIDER_WIDTH = 6;
    private static final int T_SLIDER_HEIGHT = 7;
    private static final int T_SLIDER_REACH = 8;
    private static final int T_SLIDER_FADE = 9;
    private static final int T_SLIDER_BEND = 10;
    private static final int T_COLOR = 11;

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
    private static final int ID_SCROLL_3D = 11;
    private static final int ID_IOS_DESIGN = 12;
    private static final int REQUEST_BANNER = 7310;
    private static final long MAX_BANNER_VIDEO_MS = 60_000L;

    private static final String[] GLASS_NAMES = {
            "Максимально прозрачная",
            "Стеклянная",
            "Плотная (стандартная)"
    };

    /** Setter for an int value from a slider. */
    private interface IntSetter {
        void set(int value);
    }

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
        e.title = "Дизайн";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_IOS_DESIGN;
        e.emoji = TrueGramM3.ICON_PALETTE;
        e.title = "Дизайн iOS";
        e.subtitle = "Жидкое стекло и блюр: нижняя панель, шапки чатов и поле ввода. Применяется после перезахода";
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
        e.title = "Текст сообщений";
        entries.add(e);

        e = new Entry();
        e.type = T_COLOR;
        entries.add(e);

        e = new Entry();
        e.type = T_NOTE;
        e.title = "Цвет текста во входящих и исходящих сообщениях. Откройте чат заново, чтобы увидеть изменения.";
        entries.add(e);

        e = new Entry();
        e.type = T_LABEL;
        e.title = "Прокрутка";
        entries.add(e);

        e = new Entry();
        e.type = T_ITEM;
        e.id = ID_SCROLL_3D;
        e.emoji = TrueGramM3.ICON_PALETTE;
        e.title = "3D-прокрутка в чатах";
        e.subtitle = "Строки сообщений наклоняются и сужаются у краёв экрана. Откройте чат заново";
        e.trailing = TrueGramM3.TRAILING_SWITCH;
        e.shape = TrueGramM3.SHAPE_SINGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_SLIDER_ANGLE;
        entries.add(e);

        e = new Entry();
        e.type = T_SLIDER_WIDTH;
        entries.add(e);

        e = new Entry();
        e.type = T_SLIDER_HEIGHT;
        entries.add(e);

        e = new Entry();
        e.type = T_SLIDER_REACH;
        entries.add(e);

        e = new Entry();
        e.type = T_SLIDER_FADE;
        entries.add(e);

        e = new Entry();
        e.type = T_SLIDER_BEND;
        entries.add(e);

        e = new Entry();
        e.type = T_NOTE;
        e.title = "Сила наклона — насколько строка заворачивается. Сужение и сжатие — насколько строки уменьшаются у краёв по ширине и высоте. Охват — с какого места начинается эффект: 100% означает весь экран. Затухание — насколько строки у краёв уходят в прозрачность. Изменения применяются, когда вы откроете чат заново.";
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
        frameLayout.setBackgroundColor(TrueGramM3.pageBackground());

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
        if (fragmentView != null) {
            fragmentView.setBackgroundColor(TrueGramM3.pageBackground());
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void onItemClicked(Context context, TrueGramM3.M3Item view, Entry e) {
        if (e.id == ID_STYLE_M3) {
            TrueGramConfig.setM3Style(!TrueGramConfig.isM3Style());
            refresh();
        } else if (e.id == ID_BUBBLE_STYLE) {
            TrueGramConfig.setBubbleStyle(TrueGramConfig.getBubbleStyle() == 1 ? 0 : 1);
            refresh();
        } else if (e.id == ID_SCROLL_3D) {
            TrueGram3dScroll.setEnabled(!TrueGram3dScroll.isEnabled());
            refresh();
        } else if (e.id == ID_IOS_DESIGN) {
            TrueGramIosDesign.setEnabled(!TrueGramIosDesign.isEnabled());
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
        card.setBackground(TrueGramM3.settingsCardBackground(TrueGramM3.SHAPE_SINGLE));

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
        bar.setContentDescription("Насыщенность пузырей");
        TrueGramM3.styleSlider(bar);
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

    /** Slider for the 3D scroll settings. Saves on every change; the chat applies it on reopen. */
    private View createScrollSlider(Context context, String name, int max, int current, String suffix, IntSetter setter) {
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        card.setBackground(TrueGramM3.settingsCardBackground(TrueGramM3.SHAPE_SINGLE));

        final LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);

        final TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(16);
        title.setTextColor(TrueGramM3.onSurface());
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = new TextView(context);
        value.setTextSize(15);
        value.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        value.setTextColor(TrueGramM3.primary());
        value.setText(current + suffix);
        header.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final android.widget.SeekBar bar = new android.widget.SeekBar(context);
        bar.setMax(max);
        bar.setProgress(current);
        bar.setContentDescription(name);
        TrueGramM3.styleSlider(bar);
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(progress + suffix);
                if (fromUser) {
                    setter.set(progress);
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

    /** Presets for the message text color: soft tones that stay readable on dark bubbles. */
    private static final int[] MSG_TEXT_COLORS = {
            0xFFFFFFFF, 0xFFFFE082, 0xFFFFAB91, 0xFFFF8A80, 0xFFF48FB1, 0xFFCE93D8,
            0xFFB39DDB, 0xFF90CAF9, 0xFF80DEEA, 0xFFA5D6A7, 0xFFE6EE9C, 0xFFBCAAA4
    };
    private static final String[] MSG_TEXT_COLOR_NAMES = {
            "Белый", "Золотой", "Персиковый", "Коралловый", "Розовый", "Сиреневый",
            "Лавандовый", "Небесный", "Аквамарин", "Мятный", "Лаймовый", "Песочный"
    };

    private static String msgTextColorName(int color) {
        if (color == 0) {
            return "По умолчанию (тема)";
        }
        for (int i = 0; i < MSG_TEXT_COLORS.length; i++) {
            if (MSG_TEXT_COLORS[i] == color) {
                return MSG_TEXT_COLOR_NAMES[i];
            }
        }
        return "Свой цвет";
    }

    private static GradientDrawable swatchDrawable(int color, boolean selected) {
        final GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(AndroidUtilities.dp(selected ? 3 : 1), selected ? TrueGramM3.primary() : 0x33000000);
        return d;
    }

    /** Message text color picker: live preview, a grid of soft color swatches and a reset button. */
    private View createColorPickerRow(Context context) {
        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        card.setBackground(TrueGramM3.settingsCardBackground(TrueGramM3.SHAPE_SINGLE));

        final TextView preview = new TextView(context);
        preview.setTextSize(16);
        preview.setText("Так будет выглядеть текст сообщения");
        preview.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        final GradientDrawable bubble = new GradientDrawable();
        bubble.setCornerRadius(AndroidUtilities.dp(16));
        bubble.setColor(0xFF23303D);
        preview.setBackground(bubble);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.setMargins(0, 0, 0, AndroidUtilities.dp(12));
        card.addView(preview, previewLp);

        final TextView caption = new TextView(context);
        caption.setTextSize(14);
        caption.setTextColor(TrueGramM3.onSurface());
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.setMargins(0, 0, 0, AndroidUtilities.dp(10));
        card.addView(caption, captionLp);

        final View[] swatches = new View[MSG_TEXT_COLORS.length];
        final LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < 2; row++) {
            final LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 6; col++) {
                final int index = row * 6 + col;
                final int color = MSG_TEXT_COLORS[index];
                final FrameLayout cell = new FrameLayout(context);
                final View sw = new View(context);
                swatches[index] = sw;
                sw.setOnClickListener(v -> {
                    TrueGramConfig.setMessageTextColor(color);
                    refreshMsgTextPicker(preview, caption, swatches, scopeChips);
                });
                cell.addView(sw, new FrameLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40), Gravity.CENTER));
                line.addView(cell, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1f));
            }
            grid.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView[] scopeChips = {new TextView(context), new TextView(context)};
        final String[] scopeNames = {"Входящие", "Всё сразу"};
        final LinearLayout scopeRow = new LinearLayout(context);
        scopeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            final TextView chip = scopeChips[i];
            final int scopeValue = i == 0 ? 1 : 0;
            chip.setText(scopeNames[i]);
            chip.setTextSize(15);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> {
                TrueGramConfig.setMessageTextScope(scopeValue);
                refreshMsgTextPicker(preview, caption, swatches, scopeChips);
            });
            final LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(40), 1f);
            if (i == 1) {
                chipLp.setMargins(AndroidUtilities.dp(8), 0, 0, 0);
            }
            scopeRow.addView(chip, chipLp);
        }
        final LinearLayout.LayoutParams scopeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scopeLp.setMargins(0, 0, 0, AndroidUtilities.dp(12));
        card.addView(scopeRow, 0, scopeLp);

        final TextView reset = new TextView(context);
        reset.setText("Сбросить цвет");
        reset.setTextSize(15);
        reset.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        reset.setTextColor(TrueGramM3.primary());
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(4));
        reset.setOnClickListener(v -> {
            TrueGramConfig.setMessageTextColor(0);
            refreshMsgTextPicker(preview, caption, swatches, scopeChips);
        });
        card.addView(reset, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        refreshMsgTextPicker(preview, caption, swatches, scopeChips);

        final FrameLayout wrapper = new FrameLayout(context);
        wrapper.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(2));
        wrapper.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    private static void styleScopeChip(TextView chip, boolean selected) {
        final GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(20));
        d.setColor(selected ? TrueGramM3.primary() : 0x1F000000);
        chip.setBackground(d);
        chip.setTextColor(selected ? 0xFFFFFFFF : TrueGramM3.onSurface());
        chip.setTypeface(selected ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
    }

    private static void refreshMsgTextPicker(TextView preview, TextView caption, View[] swatches, TextView[] scopeChips) {
        final int scope = TrueGramConfig.getMessageTextScope();
        styleScopeChip(scopeChips[0], scope == 1);
        styleScopeChip(scopeChips[1], scope == 0);
        final int current = TrueGramConfig.getMessageTextColor();
        preview.setTextColor(current != 0 ? current : 0xFFFFFFFF);
        caption.setText("Выбрано: " + msgTextColorName(current));
        for (int i = 0; i < swatches.length; i++) {
            swatches[i].setBackground(swatchDrawable(MSG_TEXT_COLORS[i], MSG_TEXT_COLORS[i] == current));
        }
    }

    private void bindSliderStyle(View row) {
        if (!(row instanceof FrameLayout)) {
            return;
        }
        final View cardView = ((FrameLayout) row).getChildAt(0);
        if (!(cardView instanceof LinearLayout)) {
            return;
        }
        final LinearLayout card = (LinearLayout) cardView;
        card.setBackground(TrueGramM3.settingsCardBackground(TrueGramM3.SHAPE_SINGLE));
        if (card.getChildCount() > 1 && card.getChildAt(1) instanceof android.widget.SeekBar) {
            TrueGramM3.styleSlider((android.widget.SeekBar) card.getChildAt(1));
        }
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
                case T_SLIDER_ANGLE:
                    view = createScrollSlider(context, "Сила наклона", TrueGram3dScroll.ANGLE_MAX,
                            TrueGram3dScroll.getAngle(), "°", TrueGram3dScroll::setAngle);
                    break;
                case T_SLIDER_WIDTH:
                    view = createScrollSlider(context, "Сужение по ширине", TrueGram3dScroll.SHRINK_MAX,
                            TrueGram3dScroll.getShrinkWidth(), "%", TrueGram3dScroll::setShrinkWidth);
                    break;
                case T_SLIDER_HEIGHT:
                    view = createScrollSlider(context, "Сжатие по высоте", TrueGram3dScroll.SHRINK_MAX,
                            TrueGram3dScroll.getShrinkHeight(), "%", TrueGram3dScroll::setShrinkHeight);
                    break;
                case T_SLIDER_REACH:
                    view = createScrollSlider(context, "Охват эффекта", TrueGram3dScroll.REACH_MAX,
                            TrueGram3dScroll.getReach(), "%", TrueGram3dScroll::setReach);
                    break;
                case T_SLIDER_FADE:
                    view = createScrollSlider(context, "Затухание у краёв", TrueGram3dScroll.FADE_MAX,
                            TrueGram3dScroll.getFade(), "%", TrueGram3dScroll::setFade);
                    break;
                case T_SLIDER_BEND:
                    view = createScrollSlider(context, "Изгиб колонки", TrueGram3dScroll.BEND_MAX,
                            TrueGram3dScroll.getBend(), "%", TrueGram3dScroll::setBend);
                    break;
                case T_COLOR:
                    view = createColorPickerRow(context);
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
                case T_ITEM: {
                    final TrueGramM3.M3Item item = (TrueGramM3.M3Item) holder.itemView;
                    item.bind(e.shape, e.emoji, e.title, e.subtitle, e.trailing);
                    if (e.id == ID_STYLE_M3) {
                        item.setChecked(TrueGramConfig.isM3Style(), false);
                    } else if (e.id == ID_BANNER_FOR_ALL) {
                        item.setChecked(TrueGramConfig.isBannerForAll(), false);
                    } else if (e.id == ID_BUBBLE_STYLE) {
                        item.setChecked(TrueGramConfig.getBubbleStyle() == 1, false);
                    } else if (e.id == ID_SCROLL_3D) {
                        item.setChecked(TrueGram3dScroll.isEnabled(), false);
                    } else if (e.id == ID_IOS_DESIGN) {
                        item.setChecked(TrueGramIosDesign.isEnabled(), false);
                    }
                    item.setOnClickListener(v -> onItemClicked(item.getContext(), item, e));
                    break;
                }
                case T_SLIDER:
                case T_SLIDER_ANGLE:
                case T_SLIDER_WIDTH:
                case T_SLIDER_HEIGHT:
                case T_SLIDER_REACH:
                case T_SLIDER_FADE:
                case T_SLIDER_BEND:
                    bindSliderStyle(holder.itemView);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        final int pageBackgroundKey = TrueGramM3.isM3()
                ? Theme.key_windowBackgroundWhite
                : Theme.key_windowBackgroundGray;
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, pageBackgroundKey));
        return themeDescriptions;
    }
}
