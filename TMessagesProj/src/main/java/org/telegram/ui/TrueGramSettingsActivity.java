package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/** TrueGram settings landing page (Material 3 style). */
public class TrueGramSettingsActivity extends BaseFragment {

    private static final int T_HERO = 0;
    private static final int T_LABEL = 1;
    private static final int T_ITEM = 2;
    private static final int T_SPACE = 3;

    private static final int ID_BASIC = 1;
    private static final int ID_APPEARANCE = 2;

    private static class Entry {
        final int type;
        final int id;
        final String emoji;
        final String title;
        final String subtitle;
        final int shape;

        Entry(int type, int id, String emoji, String title, String subtitle, int shape) {
            this.type = type;
            this.id = id;
            this.emoji = emoji;
            this.title = title;
            this.subtitle = subtitle;
            this.shape = shape;
        }
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private void buildEntries() {
        entries.clear();
        if (TrueGramM3.isM3()) {
            entries.add(new Entry(T_HERO, 0, "✨", "TrueGram", "Расширенные возможности вашего Telegram", 0));
        }
        entries.add(new Entry(T_LABEL, 0, null, "Разделы", null, 0));
        entries.add(new Entry(T_ITEM, ID_BASIC, "⚙️", "Основные", "Все функции TrueGram", TrueGramM3.SHAPE_TOP));
        entries.add(new Entry(T_ITEM, ID_APPEARANCE, "🎨", "Оформление", "Стиль настроек и нижняя панель", TrueGramM3.SHAPE_BOTTOM));
        entries.add(new Entry(T_SPACE, 0, null, null, null, 0));
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
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listAdapter = new ListAdapter();
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            buildEntries();
            listAdapter.notifyDataSetChanged();
        }
    }

    private void onItemClick(int id) {
        if (id == ID_BASIC) {
            presentFragment(new TrueGramBasicSettingsActivity());
        } else if (id == ID_APPEARANCE) {
            presentFragment(new TrueGramAppearanceActivity());
        }
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
                case T_HERO:
                    view = new FrameLayout(context); // replaced on bind
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                case T_LABEL:
                    view = TrueGramM3.createSectionLabel(context);
                    break;
                case T_ITEM:
                    view = new TrueGramM3.M3Item(context);
                    break;
                case T_SPACE:
                default:
                    view = TrueGramM3.createSpacer(context, 24);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final Entry e = entries.get(position);
            switch (e.type) {
                case T_HERO: {
                    final FrameLayout container = (FrameLayout) holder.itemView;
                    container.removeAllViews();
                    container.addView(TrueGramM3.createHero(container.getContext(), e.emoji, e.title, e.subtitle),
                            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                }
                case T_LABEL:
                    TrueGramM3.bindLabel((TextView) holder.itemView, e.title);
                    break;
                case T_ITEM: {
                    final TrueGramM3.M3Item item = (TrueGramM3.M3Item) holder.itemView;
                    item.bind(e.shape, e.emoji, e.title, e.subtitle, TrueGramM3.TRAILING_CHEVRON);
                    item.setOnClickListener(v -> onItemClick(e.id));
                    break;
                }
                default:
                    break;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        descriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        return descriptions;
    }
}
