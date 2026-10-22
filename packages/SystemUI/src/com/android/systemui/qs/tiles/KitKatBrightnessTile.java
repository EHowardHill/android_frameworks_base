package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import javax.inject.Inject;
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

/**
 * KitKat UI Project (P20.8): donor 4.4 brightness TILE. The 4.4 phone QS
 * (QuickSettings.setupQuickSettings) had no inline brightness slider - a
 * tile opened the brightness dialog on tap. This tile opens the SystemUI
 * BrightnessDialog (donor-restyled in the P4 batch).
 */
public class KitKatBrightnessTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "brightness";

    private static final Intent BRIGHTNESS_DIALOG_INTENT =
            new Intent("com.android.systemui.settings.brightness.BrightnessDialog")
                    .setPackage("com.android.systemui")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    @Inject
    public KitKatBrightnessTile(
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
        // Donor behavior: open the brightness dialog.
        mActivityStarter.postStartActivityDismissingKeyguard(BRIGHTNESS_DIALOG_INTENT, 0);
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_DISPLAY_SETTINGS);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_brightness_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = true;
        state.state = Tile.STATE_INACTIVE;
        state.label = mContext.getString(R.string.quick_settings_brightness_label);
        state.icon = ResourceIcon.get(R.drawable.ic_brightness);
    }
}