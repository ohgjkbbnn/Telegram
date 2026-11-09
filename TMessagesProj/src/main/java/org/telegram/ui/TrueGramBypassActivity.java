package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.th3nekit.finegram.net.bypass.FGBypassConfig;
import com.th3nekit.finegram.net.bypass.FGBypassController;
import com.th3nekit.finegram.net.bypass.FGBypassLocations;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

/** TrueGram: «Обход блокировок» (built-in bypass, Material 3 style). */
public class TrueGramBypassActivity extends BaseFragment {

    private static final int T_HERO = 0;
    private static final int T_LABEL = 1;
    private static final int T_ITEM = 2;
    private static final int T_NOTE = 3;
    private static final int T_SPACE = 4;

    private static final int ID_ENABLE = 1;
    private static final int ID_LOCATION = 2;
    private static final int ID_SERVER = 3;
    private static final int ID_SUSPEND_VPN = 4;
    private static final int ID_PROXY = 5;

    private static class Entry {
        final int type;
        final int id;
        final String emoji;
        final String title;
        final String subtitle;
        final int shape;
        final boolean checked;
        final int trailing;

        Entry(int type, int id, String emoji, String title, String subtitle, int shape, boolean checked, int trailing) {
            this.type = type;
            this.id = id;
            this.emoji = emoji;
            this.title = title;
            this.subtitle = subtitle;
            this.shape = shape;
            this.checked = checked;
            this.trailing = trailing;
        }
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private void buildEntries() {
        entries.clear();
        final FGBypassController controller = FGBypassController.getInstance();
        entries.add(new Entry(T_HERO, 0, TrueGramM3.ICON_SHIELD, "Обход блокировок", statusText(controller), 0, false, TrueGramM3.TRAILING_NONE));
        entries.add(new Entry(T_LABEL, 0, null, "Подключение", null, 0, false, TrueGramM3.TRAILING_NONE));
        entries.add(new Entry(T_ITEM, ID_ENABLE, TrueGramM3.ICON_BOLT, "Включить обход", "Telegram через сервер обхода", TrueGramM3.SHAPE_TOP, FGBypassConfig.enabled, TrueGramM3.TRAILING_SWITCH));
        entries.add(new Entry(T_ITEM, ID_LOCATION, TrueGramM3.ICON_LOCATION, "Локация", locationTitle(), TrueGramM3.SHAPE_MIDDLE, false, TrueGramM3.TRAILING_CHEVRON));
        entries.add(new Entry(T_ITEM, ID_SERVER, TrueGramM3.ICON_DEVICES, "Сервер", serverTitle(), TrueGramM3.SHAPE_BOTTOM, false, TrueGramM3.TRAILING_CHEVRON));
        entries.add(new Entry(T_LABEL, 0, null, "Поведение", null, 0, false, TrueGramM3.TRAILING_NONE));
        entries.add(new Entry(T_ITEM, ID_SUSPEND_VPN, TrueGramM3.ICON_LOCK, "Отключать при VPN", "При VPN обход приостанавливается", TrueGramM3.SHAPE_TOP, FGBypassConfig.suspendOnVpn, TrueGramM3.TRAILING_SWITCH));
        entries.add(new Entry(T_ITEM, ID_PROXY, TrueGramM3.ICON_SETTINGS, "Настройки прокси", "Свои прокси Telegram", TrueGramM3.SHAPE_BOTTOM, false, TrueGramM3.TRAILING_CHEVRON));
        entries.add(new Entry(T_NOTE, 0, null, "Подключение к Telegram идёт через выбранный сервер обхода. Звонки пока идут напрямую.", null, 0, false, TrueGramM3.TRAILING_NONE));
        entries.add(new Entry(T_SPACE, 0, null, null, null, 0, false, TrueGramM3.TRAILING_NONE));
    }

    private static String statusText(FGBypassController controller) {
        if (!FGBypassConfig.enabled) {
            return "Выключено";
        }
        if (controller.isRunning()) {
            return "Подключено";
        }
        if (controller.isStarting()) {
            return "Подключение…";
        }
        String error = controller.getLastError();
        return error == null || error.isEmpty() ? "Нет соединения" : "Нет соединения: " + error;
    }

    private static String locationTitle() {
        final String id = FGBypassLocations.selectedId();
        if (id == null || FGBypassLocations.AUTO.equals(id)) {
            return "Автоматически";
        }
        for (FGBypassLocations.Location location : FGBypassLocations.all()) {
            if (location.id.equals(id)) {
                return location.label();
            }
        }
        return "Автоматически";
    }

    private static String serverTitle() {
        switch (FGBypassConfig.source) {
            case FGBypassConfig.SOURCE_TGWS:
                return "Flowseal";
            case FGBypassConfig.SOURCE_MIXED:
                return "Смешанный";
            case FGBypassConfig.SOURCE_RELAY:
            default:
                return "Наш сервер";
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
        actionBar.setTitle("Обход блокировок");
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
        refresh();
    }

    private void refresh() {
        buildEntries();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void restartIfEnabled() {
        final FGBypassController controller = FGBypassController.getInstance();
        if (FGBypassConfig.enabled) {
            controller.setEnabled(false);
            controller.setEnabled(true);
        }
    }

    private void onItemClick(int id) {
        if (id == ID_LOCATION) {
            showLocationPicker();
        } else if (id == ID_SERVER) {
            showServerPicker();
        } else if (id == ID_PROXY) {
            presentFragment(new ProxyListActivity());
        }
    }

    private void onSwitchChanged(int id, boolean value) {
        if (id == ID_ENABLE) {
            FGBypassController.getInstance().setEnabled(value);
        } else if (id == ID_SUSPEND_VPN) {
            FGBypassConfig.setSuspendOnVpn(value);
            FGBypassController.getInstance().reevaluateForVpnToggle();
        }
        refresh();
    }

    private void showLocationPicker() {
        final List<FGBypassLocations.Location> locations = FGBypassLocations.all();
        final String[] labels = new String[locations.size() + 1];
        final String[] ids = new String[locations.size() + 1];
        labels[0] = "Автоматически";
        ids[0] = FGBypassLocations.AUTO;
        for (int i = 0; i < locations.size(); i++) {
            labels[i + 1] = locations.get(i).label();
            ids[i + 1] = locations.get(i).id;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Локация");
        builder.setItems(labels, (dialog, which) -> {
            FGBypassLocations.select(ids[which]);
            restartIfEnabled();
            refresh();
        });
        showDialog(builder.create());
    }

    private void showServerPicker() {
        final int[] values = {FGBypassConfig.SOURCE_RELAY, FGBypassConfig.SOURCE_TGWS, FGBypassConfig.SOURCE_MIXED};
        final String[] labels = {"Наш сервер", "Flowseal", "Смешанный"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Сервер");
        builder.setItems(labels, (dialog, which) -> {
            FGBypassConfig.setSource(values[which]);
            restartIfEnabled();
            refresh();
        });
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
                case T_NOTE:
                    view = TrueGramM3.createSupportingText(context);
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
                    item.bind(e.shape, e.emoji, e.title, e.subtitle, e.trailing);
                    item.setAccent(TrueGramM3.accent(e.id));
                    if (e.trailing == TrueGramM3.TRAILING_SWITCH) {
                        item.setChecked(e.checked, false);
                        item.setOnClickListener(v -> onSwitchChanged(e.id, !e.checked));
                    } else {
                        item.setOnClickListener(v -> onItemClick(e.id));
                    }
                    break;
                }
                case T_NOTE:
                    TrueGramM3.bindNote((TextView) holder.itemView, e.title);
                    break;
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
