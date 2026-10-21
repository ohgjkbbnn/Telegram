package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.messenger.TrueGramMentions;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** TrueGram: "Упоминания в группах" screen, opened from the chat menu (three dots -> TrueGram). */
public class TrueGramMentionsActivity extends BaseFragment {

    private static final int T_LABEL = 0;
    private static final int T_ITEM = 1;
    private static final int T_NOTE = 2;
    private static final int T_SPACE = 3;

    private static final int ID_ENABLE = 100;

    private static class Entry {
        int type;
        int id;
        String icon;
        String title;
        String subtitle;
        int trailing;
        int shape;
        int opt = -1;
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private void add(int type, String title) {
        Entry e = new Entry();
        e.type = type;
        e.title = title;
        entries.add(e);
    }

    private void option(int opt, String icon, String title) {
        Entry e = new Entry();
        e.type = T_ITEM;
        e.id = opt;
        e.opt = opt;
        e.icon = icon;
        e.title = title;
        e.subtitle = TrueGramMentions.optionText(opt);
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        entries.add(e);
    }

    private void buildEntries() {
        entries.clear();
        add(T_LABEL, "Упоминания в группах");

        Entry sw = new Entry();
        sw.type = T_ITEM;
        sw.id = ID_ENABLE;
        sw.icon = TrueGramM3.ICON_PERSON;
        sw.title = "Упоминать всех через @all";
        sw.subtitle = "Наберите @all в группе, и он упомянет участников. «@all non» всегда без имён";
        sw.trailing = TrueGramM3.TRAILING_SWITCH;
        entries.add(sw);

        option(TrueGramMentions.OPT_WHOM, TrueGramM3.ICON_PERSON, "Кого упоминать");
        option(TrueGramMentions.OPT_LIMIT, TrueGramM3.ICON_PIN, "Лимит на сообщение");
        option(TrueGramMentions.OPT_PAUSE, TrueGramM3.ICON_INFO, "Пауза между упоминаниями");
        option(TrueGramMentions.OPT_STYLE, TrueGramM3.ICON_PALETTE, "Стиль упоминаний");
        add(T_NOTE, "Если участников больше лимита, остальные упоминаются следующими сообщениями с паузой. В очень больших группах Telegram скрывает список участников, тогда доступны только админы.");
        add(T_SPACE, null);

        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            if (e.type != T_ITEM) {
                continue;
            }
            final boolean prev = i > 0 && entries.get(i - 1).type == T_ITEM;
            final boolean next = i < entries.size() - 1 && entries.get(i + 1).type == T_ITEM;
            e.shape = prev && next ? TrueGramM3.SHAPE_MIDDLE : prev ? TrueGramM3.SHAPE_BOTTOM : next ? TrueGramM3.SHAPE_TOP : TrueGramM3.SHAPE_SINGLE;
        }
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
        actionBar.setTitle("TrueGram");
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
        if (e.id == ID_ENABLE) {
            TrueGramMentions.setEnabled(!TrueGramMentions.isEnabled());
            view.setChecked(TrueGramMentions.isEnabled(), true);
            return;
        }
        final int opt = e.opt;
        final String[] names = TrueGramMentions.optionNames(opt);
        final int cur = TrueGramMentions.optionIndex(opt);
        final String[] shown = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            shown[i] = (i == cur ? "✓ " : "") + names[i];
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(e.title);
        builder.setItems(shown, (d, which) -> {
            TrueGramMentions.setOptionIndex(opt, which);
            buildEntries();
            listAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

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
                    item.bind(e.shape, e.icon, e.title, e.subtitle, e.trailing);
                    if (e.trailing == TrueGramM3.TRAILING_SWITCH) {
                        item.setChecked(TrueGramMentions.isEnabled(), false);
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
        ArrayList<ThemeDescription> list = new ArrayList<>();
        list.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        return list;
    }
}
