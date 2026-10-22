package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TrueGramAI;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Set;

/** TrueGram: AI settings (connections, reply style, auto-reply chats). */
public class TrueGramAISettingsActivity extends BaseFragment {

    private static final int T_LABEL = 0, T_ITEM = 1, T_NOTE = 2, T_SPACE = 3;
    private static final int ID_STYLE = 200, ID_EXTRA = 201, ID_CHATS = 202, ID_OPEN = 203;

    private static class Entry {
        int type;
        int id;
        String icon, title, subtitle;
        int trailing;
        int shape;
        TrueGramAI.Provider provider;
    }

    private final ArrayList<Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private void add(int type, String title) {
        final Entry e = new Entry();
        e.type = type;
        e.title = title;
        entries.add(e);
    }

    private Entry item(int id, String icon, String title, String subtitle) {
        final Entry e = new Entry();
        e.type = T_ITEM;
        e.id = id;
        e.icon = icon;
        e.title = title;
        e.subtitle = subtitle;
        e.trailing = TrueGramM3.TRAILING_CHEVRON;
        entries.add(e);
        return e;
    }

    private static String formatTokens(long n) {
        if (n < 1000) {
            return String.valueOf(n);
        }
        if (n < 1000000) {
            return String.format(java.util.Locale.getDefault(), "%.1fк", n / 1000.0);
        }
        return String.format(java.util.Locale.getDefault(), "%.2fм", n / 1000000.0);
    }

    private static String cut(String s, int n) {
        s = s.replace('\n', ' ');
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private void buildEntries() {
        entries.clear();
        add(T_LABEL, "Нейросети");
        item(ID_OPEN, TrueGramM3.ICON_AI, "Открыть чат с ИИ", "Общение один на один с нейросетью");
        for (TrueGramAI.Provider p : TrueGramAI.PROVIDERS) {
            String sub;
            if (TrueGramAI.isConnected(p.id)) {
                sub = "Подключено · " + (TrueGramAI.getModel(p.id).isEmpty() ? "модель не выбрана" : TrueGramAI.getModel(p.id));
                final long used = TrueGramAI.getUsedTokens(p.id);
                if (used > 0) {
                    sub += " · потрачено " + formatTokens(used) + " ток.";
                }
            } else {
                sub = "Нажмите, чтобы добавить ключ";
            }
            item(100, TrueGramM3.ICON_PERSON, p.name, sub).provider = p;
        }
        add(T_LABEL, "Ответы");
        item(ID_STYLE, TrueGramM3.ICON_PALETTE, "Свой стиль ответа", cut(TrueGramAI.getStyle(), 60));
        item(ID_EXTRA, TrueGramM3.ICON_EDIT, "Инструкция для автоответов", cut(TrueGramAI.getAutoReplyExtra(), 60));
        item(ID_CHATS, TrueGramM3.ICON_INFO, "Чаты с автоответом", TrueGramAI.getAutoReplyDialogs().size() + " шт.");
        add(T_NOTE, "Автоответ включается в нужном чате: ⋮ → «ИИ-автоответ». В группах ИИ отвечает только когда вас упомянули. Лимит: не чаще раза в 8 секунд и до 20 ответов в час на чат.");
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

    private void refresh() {
        buildEntries();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
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
        actionBar.setTitle("Настройки ИИ");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        fragmentView = new FrameLayout(context);
        final FrameLayout frame = (FrameLayout) fragmentView;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listAdapter = new ListAdapter();
        listView.setAdapter(listAdapter);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void onClicked(Context context, Entry e) {
        if (e.provider != null) {
            TrueGramAIChatActivity.showKeyDialog(this, e.provider, this::refresh);
        } else if (e.id == ID_STYLE) {
            textDialog(context, "Свой стиль ответа", TrueGramAI.getStyle(), TrueGramAI::setStyle);
        } else if (e.id == ID_EXTRA) {
            textDialog(context, "Инструкция для автоответов", TrueGramAI.getAutoReplyExtra(), TrueGramAI::setAutoReplyExtra);
        } else if (e.id == ID_CHATS) {
            showAutoReplyChats(context);
        } else if (e.id == ID_OPEN) {
            presentFragment(new TrueGramAIChatActivity());
        }
    }

    private interface Saver {
        void save(String s);
    }

    private void textDialog(Context context, String title, String current, final Saver saver) {
        final EditText input = new EditText(context);
        input.setText(current);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        final AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(title);
        b.setView(input);
        b.setNegativeButton("Отмена", null);
        b.setPositiveButton("Сохранить", (d, w) -> {
            saver.save(input.getText().toString());
            refresh();
        });
        showDialog(b.create());
    }

    private void showAutoReplyChats(Context context) {
        final Set<Long> ids = TrueGramAI.getAutoReplyDialogs();
        if (ids.isEmpty()) {
            final AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle("Чаты с автоответом");
            b.setMessage("Пока пусто. Откройте нужный чат, нажмите ⋮ и выберите «ИИ-автоответ».");
            b.setPositiveButton("Понятно", null);
            showDialog(b.create());
            return;
        }
        final ArrayList<Long> list = new ArrayList<>(ids);
        final String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            final long id = list.get(i);
            String name = String.valueOf(id);
            if (id > 0) {
                final TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(id);
                if (u != null) name = UserObject.getUserName(u);
            } else {
                final TLRPC.Chat c = MessagesController.getInstance(currentAccount).getChat(-id);
                if (c != null) name = c.title;
            }
            names[i] = name;
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Нажмите на чат, чтобы выключить автоответ");
        b.setItems(names, (d, which) -> {
            TrueGramAI.removeAutoReply(list.get(which));
            refresh();
        });
        b.setNegativeButton("Закрыть", null);
        showDialog(b.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
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
            final View view;
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
                    item.setOnClickListener(v -> onClicked(item.getContext(), e));
                    break;
                }
                default:
                    break;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> list = new ArrayList<>();
        list.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        return list;
    }
}
