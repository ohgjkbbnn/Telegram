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
import android.widget.ScrollView;
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
    private int autoBoldRow;
    private int backgroundRow;
    private int deletedColorRow;
    private int editedColorRow;
    private int saveDeletedInfoRow;
    private int developerRow;
    private int photoHeaderRow;
    private int photoQualityRow;
    private int photoInfoRow;
    private int videoHeaderRow;
    private int video60Row;
    private int videoInfoRow;
    private int idColorHeaderRow;
    private int idColorRow;
    private int idColorInfoRow;
    private int screenHeaderRow;
    private int screenshotsRow;
    private int screenInfoRow;
    private int pinHeaderRow;
    private int localPinRow;
    private int pinInfoRow;
    private int audioHeaderRow;
    private int audioBitrateRow;
    private int audioInfoRow;
    private int lyricsHeaderRow;
    private int lyricsRow;
    private int lyricsColorRow;
    private int lyricsInfoRow;

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        saveDeletedRow = rowCount++;
        deletedColorRow = rowCount++;
        saveEditsRow = rowCount++;
        editedColorRow = rowCount++;
        autoBoldRow = rowCount++;
        backgroundRow = rowCount++;
        saveDeletedInfoRow = rowCount++;
        photoHeaderRow = rowCount++;
        photoQualityRow = rowCount++;
        photoInfoRow = rowCount++;
        videoHeaderRow = rowCount++;
        video60Row = rowCount++;
        videoInfoRow = rowCount++;
        idColorHeaderRow = rowCount++;
        idColorRow = rowCount++;
        idColorInfoRow = rowCount++;
        screenHeaderRow = rowCount++;
        screenshotsRow = rowCount++;
        screenInfoRow = rowCount++;
        pinHeaderRow = rowCount++;
        localPinRow = rowCount++;
        pinInfoRow = rowCount++;
        audioHeaderRow = rowCount++;
        audioBitrateRow = rowCount++;
        audioInfoRow = rowCount++;
        lyricsHeaderRow = rowCount++;
        lyricsRow = rowCount++;
        lyricsColorRow = rowCount++;
        lyricsInfoRow = rowCount++;
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
                
} else if (position == autoBoldRow) {
    final boolean enabled = !TrueGramConfig.isAutoBoldEnabled();
    TrueGramConfig.setAutoBoldEnabled(enabled);
    if (view instanceof TextCheckCell) {
        ((TextCheckCell) view).setChecked(enabled);
    }
} else if (position == saveEditsRow) {
    final boolean enabled = !TrueGramConfig.isSaveEditsEnabled();
                TrueGramConfig.setSaveEditsEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == backgroundRow) {
                final boolean enabled = !TrueGramConfig.isBackgroundEnabled();
                TrueGramConfig.setBackgroundEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == photoQualityRow) {
                final boolean enabled = !TrueGramConfig.isPhotoQualityEnabled();
                TrueGramConfig.setPhotoQualityEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == video60Row) {
                final boolean enabled = !TrueGramConfig.isVideo60Enabled();
                TrueGramConfig.setVideo60Enabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == screenshotsRow) {
                final boolean enabled = !TrueGramConfig.isScreenshotsAllowed();
                TrueGramConfig.setScreenshotsAllowed(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == localPinRow) {
                final boolean enabled = !TrueGramConfig.isLocalPinEnabled();
                TrueGramConfig.setLocalPinEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == audioBitrateRow) {
                final boolean enabled = !TrueGramConfig.isAudioBitrateEnabled();
                TrueGramConfig.setAudioBitrateEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == lyricsRow) {
                final boolean enabled = !TrueGramConfig.isLyricsEnabled();
                TrueGramConfig.setLyricsEnabled(enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            } else if (position == lyricsColorRow) {
                showColorDialog(context, 3);
            } else if (position == deletedColorRow) {
                showColorDialog(context, 0);
            } else if (position == editedColorRow) {
                showColorDialog(context, 1);
            } else if (position == idColorRow) {
                showColorDialog(context, 2);
            } else if (position == developerRow) {
                Browser.openUrl(context, "https://t.me/og_auctioneer");
            }
        });

        return fragmentView;
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

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int pos = holder.getAdapterPosition();
            return pos == saveDeletedRow || pos == saveEditsRow || pos == autoBoldRow || pos == backgroundRow || pos == developerRow || pos == deletedColorRow || pos == editedColorRow || pos == idColorRow || pos == lyricsRow || pos == lyricsColorRow
        || pos == photoQualityRow || pos == video60Row || pos == screenshotsRow || pos == localPinRow || pos == audioBitrateRow;
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
                    if (position == photoHeaderRow) {
                        headerCell.setText("Улучшение качества фото");
                    } else if (position == videoHeaderRow) {
                        headerCell.setText("Съёмка 1080p 60 FPS");
                    } else if (position == idColorHeaderRow) {
                        headerCell.setText("Цвет отображения ID и ДЦ");
                    } else if (position == screenHeaderRow) {
                        headerCell.setText("Скриншоты и запись экрана");
                    } else if (position == pinHeaderRow) {
                        headerCell.setText("Локальный закреп");
                    } else if (position == audioHeaderRow) {
                        headerCell.setText("Аудио");
                    } else if (position == lyricsHeaderRow) {
                        headerCell.setText("Текст песни");
                    } else {
                        headerCell.setText("Сообщения");
                    }
                    break;
                }
                case 1: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == autoBoldRow) {
 cell.setTextAndCheck("Жирный шрифт при отправке", TrueGramConfig.isAutoBoldEnabled(), false);
                    } else if (position == photoQualityRow) {
 cell.setTextAndCheck("Улучшать качество фото", TrueGramConfig.isPhotoQualityEnabled(), false);
                    } else if (position == video60Row) {
                        cell.setTextAndCheck("Съёмка 1080p 60 FPS", TrueGramConfig.isVideo60Enabled(), false);
                    } else if (position == screenshotsRow) {
                        cell.setTextAndCheck("Разрешить скриншоты и запись экрана", TrueGramConfig.isScreenshotsAllowed(), false);
                    } else if (position == localPinRow) {
                        cell.setTextAndCheck("Бесконечный локальный закреп", TrueGramConfig.isLocalPinEnabled(), false);
                    } else if (position == audioBitrateRow) {
                        cell.setTextAndCheck("Увеличенный битрейт аудио", TrueGramConfig.isAudioBitrateEnabled(), false);
                    } else if (position == lyricsRow) {
                        cell.setTextAndCheck("Показывать текст песни", TrueGramConfig.isLyricsEnabled(), true);
                    } else if (position == saveEditsRow) {
                        cell.setTextAndCheck("Сохранять правки", TrueGramConfig.isSaveEditsEnabled(), false);
                    } else if (position == backgroundRow) {
                        cell.setTextAndCheck("Работать в фоне", TrueGramConfig.isBackgroundEnabled(), false);
                    } else {
                        cell.setTextAndCheck("Читать удалённые сообщения", TrueGramConfig.isSaveDeletedEnabled(), false);
                    }
                    break;
                }
                case 4: {
                    TextColorCell cell = (TextColorCell) holder.itemView;
                    if (position == idColorRow) {
                        final int idColor = TrueGramConfig.getIdColor();
                        cell.setTextAndColor("Цвет ID и ДЦ", idColor != 0 ? idColor : 0xFF8E8E93, false);
                    } else if (position == lyricsColorRow) {
                        final int lyricsColor = TrueGramConfig.getLyricsColor();
                        cell.setTextAndColor("Цвет спетых строк", lyricsColor != 0 ? lyricsColor : 0xFF8E8E93, false);
                    } else if (position == editedColorRow) {
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
                    if (position == photoInfoRow) {
                        cell.setText("Фото отправляются крупнее и с меньшим сжатием: до 2560 px по длинной стороне.");
                    } else if (position == videoInfoRow) {
                        cell.setText("Видео снимается в 1080p и 60 кадров/с, если камера устройства это поддерживает.");
                    } else if (position == idColorInfoRow) {
                        cell.setText("Цвет, которым показываются ID и дата-центр в профиле.");
                    } else if (position == screenInfoRow) {
                        cell.setText("Снимает запрет на скриншоты и запись экрана в приложении.");
                    } else if (position == pinInfoRow) {
                        cell.setText("Закрепляйте любое количество чатов. Закреп хранится только на этом устройстве.");
                    } else if (position == lyricsInfoRow) {
                        cell.setText("Пока играет музыка, под плашкой плеера идёт текст песни: спетые строки подсвечиваются выбранным цветом. Тексты берутся из базы LRCLIB по исполнителю и названию трека, нужен интернет. Для редких треков текста может не быть, тогда плашка остаётся обычной.");
                    } else if (position == audioInfoRow) {
                        cell.setText("Голосовые сообщения записываются в режиме высокого качества (полный звук вместо режима для речи). Звук чище, файл больше.");
                    } else {
                        cell.setText(                    "Удалённые сообщения остаются в чате с пометкой «удалено» у времени. Если сообщение изменили, рядом со временем появится ↺, нажмите на время, чтобы увидеть прошлые версии. «Работать в фоне» держит соединение с Telegram, когда приложение закрыто (в шторке будет постоянное уведомление). Без него сообщения, которые написали и удалили, пока приложение закрыто, могут не сохраниться. Для надёжной работы отключите ограничение батареи для приложения. Одноразовые сообщения и сообщения с таймером не сохраняются.");
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == photoHeaderRow || position == videoHeaderRow || position == idColorHeaderRow
                    || position == screenHeaderRow || position == pinHeaderRow || position == audioHeaderRow || position == lyricsHeaderRow) {
                return 0;
            } else if (position == saveDeletedRow || position == saveEditsRow || position == autoBoldRow || position == backgroundRow
                    || position == photoQualityRow || position == video60Row || position == screenshotsRow
                    || position == localPinRow || position == audioBitrateRow || position == lyricsRow) {
                return 1;
            } else if (position == developerRow) {
                return 3;
            } else if (position == deletedColorRow || position == editedColorRow || position == idColorRow || position == lyricsColorRow) {
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
