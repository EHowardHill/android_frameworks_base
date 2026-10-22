package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
 * KitKat UI Project (P20.10): donor 4.4 QS SETTINGS tile - QuickSettings
 * added a tile that opens the Settings app from inside the grid
 * (startSettingsActivity(Settings.ACTION_SETTINGS)).
 */
public class KitKatSettingsTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "settings";

    @Inject
    public KitKatSettingsTile(
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
        mActivityStarter.postStartActivityDismissingKeyguard(
                new Intent(Settings.ACTION_SETTINGS), 0);
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_SETTINGS);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_settings_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = true;
        state.state = Tile.STATE_INACTIVE;
        state.label = mContext.getString(R.string.quick_settings_settings_label);
        state.icon = ResourceIcon.get(R.drawable.ic_settings);
    }
}