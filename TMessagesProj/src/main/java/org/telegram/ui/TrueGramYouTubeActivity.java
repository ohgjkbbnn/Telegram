package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** TrueGram "YouTube": web YouTube that can be minimized into a floating window above the chats. */
public class TrueGramYouTubeActivity extends BaseFragment {

    private FrameLayout host;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("YouTube");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // minimize / close buttons drawn inside the action bar
        final int iconColor = Theme.getColor(Theme.key_actionBarDefaultIcon);
        final int top = actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
        final int barH = ActionBar.getCurrentActionBarHeight();

        final TrueGramYouTube.IconView close = new TrueGramYouTube.IconView(context, TrueGramYouTube.IconView.CLOSE, iconColor);
        close.setOnClickListener(v -> {
            TrueGramYouTube.getInstance().destroy();
            finishFragment();
        });
        final FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(AndroidUtilities.dp(48), barH, Gravity.TOP | Gravity.RIGHT);
        closeLp.topMargin = top;
        actionBar.addView(close, closeLp);

        final TrueGramYouTube.IconView minimize = new TrueGramYouTube.IconView(context, TrueGramYouTube.IconView.MINIMIZE, iconColor);
        minimize.setOnClickListener(v -> finishFragment());
        final FrameLayout.LayoutParams minLp = new FrameLayout.LayoutParams(AndroidUtilities.dp(48), barH, Gravity.TOP | Gravity.RIGHT);
        minLp.topMargin = top;
        minLp.rightMargin = AndroidUtilities.dp(48);
        actionBar.addView(minimize, minLp);

        host = new FrameLayout(context);
        host.setBackgroundColor(0xFF000000);
        fragmentView = host;
        TrueGramYouTube.getInstance().attachToHost(context, host);
        return fragmentView;
    }

    @Override
    public boolean onBackPressed() {
        final TrueGramYouTube yt = TrueGramYouTube.getInstance();
        if (yt.exitFullscreen()) {
            return false;
        }
        if (yt.goBackIfPossible()) {
            return false;
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        TrueGramYouTube.getInstance().onHostClosed(host);
    }
}
