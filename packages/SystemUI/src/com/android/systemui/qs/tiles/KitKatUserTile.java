package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.service.quicksettings.Tile;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

import javax.inject.Inject;

/**
 * KitKat UI Project (P20.10): donor 4.4 QS USER tile - QuickSettings put
 * the user avatar/photo first in the grid; tap opens the quick-contact
 * card for the owner profile (multi-user devices opened the user
 * switcher; this single-user build follows the donor single-user path).
 */
public class KitKatUserTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "user";

    @Inject
    public KitKatUserTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view) {
        // Donor single-user path: quick-contact card for the owner profile.
        final Intent intent = ContactsContract.QuickContact.composeQuickContactsIntent(
                mContext, view != null ? view : new View(mContext),
                ContactsContract.Profile.CONTENT_URI,
                ContactsContract.QuickContact.MODE_LARGE, null);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0);
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(android.provider.Settings.ACTION_USER_SETTINGS);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_user_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = true;
        state.state = Tile.STATE_INACTIVE;
        state.label = mContext.getString(R.string.quick_settings_user_label);
        state.icon = new com.android.systemui.qs.tileimpl.QSTileImpl.DrawableIcon(getUserIcon());
    }

    private Drawable getUserIcon() {
        try {
            final android.os.UserManager um = mContext.getSystemService(android.os.UserManager.class);
            final Bitmap icon = um != null ? um.getUserIcon(mContext.getUserId()) : null;
            if (icon != null) {
                return new BitmapDrawable(mContext.getResources(), icon);
            }
        } catch (Exception e) {
            // fall through to the default avatar
        }
        return mContext.getResources().getDrawable(R.drawable.kitkat_qs_user_avatar,
                mContext.getTheme());
    }

}