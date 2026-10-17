package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.TrueGramConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.messenger.AndroidUtilities;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.messenger.R;

import java.util.ArrayList;

public class TrueGramSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;
    private int headerRow;
    private int saveDeletedRow;
    private int saveEditsRow;
    private int deletedColorRow;
    private int editedColorRow;
    private int saveDeletedInfoRow;
    private int developerRow;

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        saveDeletedRow = rowCount++;
        deletedColorRow = rowCount++;
        saveEditsRow = rowCount++;
        editedColorRow = rowCount++;
        saveDeletedInfoRow = rowCount++;
        developerRow = rowCount++;
    }

    @Override
    public boolean onFragmentCreate() {
        updateRows();
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

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setSections();
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
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == saveDeletedRow) {
                final boolean enabled = !TrueGramConfig.isSaveDeletedEnabled();
                TrueGramConfig.setSaveDeletedEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == saveEditsRow) {
                final boolean enabled = !TrueGramConfig.isSaveEditsEnabled();
                TrueGramConfig.setSaveEditsEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == deletedColorRow) {
                showColorDialog(context, false);
            } else if (position == editedColorRow) {
                showColorDialog(context, true);
            } else if (position == developerRow) {
                Browser.openUrl(context, "https://t.me/og_auctioneer");
            }
        });

        return fragmentView;
    }

    private void showColorDialog(Context context, boolean edited) {
        final int[] palette = {
                0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759,
                0xFF00C7BE, 0xFF0A84FF, 0xFF5E5CE6, 0xFFAF52DE,
                0xFFFF2D55, 0xFFFFFFFF, 0xFF8E8E93, 0xFF000000
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

        for (int r = 0; r < 3; r++) {
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
                    if (edited) {
                        TrueGramConfig.setEditedColor(color);
                    } else {
                        TrueGramConfig.setDeletedColor(color);
                    }
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

        builder.setTitle(edited ? "Цвет пометки «изменено»" : "Цвет пометки «удалено»");
        builder.setView(root);
        builder.setPositiveButton("OK", (d, w) -> {
            final String text = hex.getText().toString().trim();
            if (!text.isEmpty()) {
                try {
                    final int parsed = Color.parseColor(text.startsWith("#") ? text : "#" + text) | 0xFF000000;
                    if (edited) {
                        TrueGramConfig.setEditedColor(parsed);
                    } else {
                        TrueGramConfig.setDeletedColor(parsed);
                    }
                } catch (Exception ignore) {
                }
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton("Сбросить", (d, w) -> {
            if (edited) {
                TrueGramConfig.setEditedColor(0);
            } else {
                TrueGramConfig.setDeletedColor(0);
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        dialogRef[0] = builder.show();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int pos = holder.getAdapterPosition();
            return pos == saveDeletedRow || pos == saveEditsRow || pos == developerRow || pos == deletedColorRow || pos == editedColorRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    break;
                case 3:
                    view = new TextSettingsCell(mContext);
                    break;
                case 4:
                    view = new TextColorCell(mContext);
                    break;
                case 2:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText("Сообщения");
                    break;
                }
                case 1: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == saveEditsRow) {
                        cell.setTextAndCheck("Сохранять правки", TrueGramConfig.isSaveEditsEnabled(), false);
                    } else {
                        cell.setTextAndCheck("Читать удалённые сообщения", TrueGramConfig.isSaveDeletedEnabled(), false);
                    }
                    break;
                }
                case 4: {
                    TextColorCell cell = (TextColorCell) holder.itemView;
                    if (position == editedColorRow) {
                        final int editedColor = TrueGramConfig.getEditedColor();
                        cell.setTextAndColor("Цвет пометки «изменено» ↺", editedColor != 0 ? editedColor : 0xFF8E8E93, true);
                    } else {
                        final int color = TrueGramConfig.getDeletedColor();
                        cell.setTextAndColor("Цвет пометки «удалено»", color != 0 ? color : 0xFF8E8E93, true);
                    }
                    break;
                }
                case 3: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextAndValue("Разработчик", "@og_auctioneer", false);
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText("Удалённые сообщения остаются в чате с пометкой «удалено» у времени. Если сообщение изменили, рядом со временем появится ↺, нажмите на время, чтобы увидеть прошлые версии. Одноразовые сообщения и сообщения с таймером не сохраняются.");
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return 0;
            } else if (position == saveDeletedRow || position == saveEditsRow) {
                return 1;
            } else if (position == developerRow) {
                return 3;
            } else if (position == deletedColorRow || position == editedColorRow) {
                return 4;
            }
            return 2;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        return themeDescriptions;
    }
}
