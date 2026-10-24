package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
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
