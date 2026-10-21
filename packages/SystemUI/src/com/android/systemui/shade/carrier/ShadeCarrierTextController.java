/*
 * Copyright (C) 2026 The KitKat UI Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.carrier;

import android.view.View;

import com.android.keyguard.CarrierText;
import com.android.keyguard.CarrierTextManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.util.ViewController;

/**
 * KitKat UI Project: carrier label pinned to the bottom of the notification
 * shade (status_bar_expanded.xml, shade_carrier_text), as in Android 4.4.
 *
 * Owns a private {@link CarrierTextManager} built from the injected
 * {@link CarrierTextManager.Builder} (same pattern as
 * {@link ShadeCarrierGroupController}, so it never contends with the
 * keyguard's manager).
 *
 * The label only shows in the unlocked shade ({@link StatusBarState#SHADE}).
 * On the lockscreen the keyguard status bar already carries its own carrier
 * text, and the shade-over-keyguard bottom area belongs to the keyguard
 * bottom view.
 */
public class ShadeCarrierTextController extends ViewController<CarrierText> {

    private final CarrierTextManager mCarrierTextManager;
    private final StatusBarStateController mStatusBarStateController;

    private final CarrierTextManager.CarrierTextCallback mCarrierTextCallback =
            new CarrierTextManager.CarrierTextCallback() {
                @Override
                public void updateCarrierInfo(CarrierTextManager.CarrierTextCallbackInfo info) {
                    mView.setText(info.carrierText);
                }
            };

    private final StatusBarStateController.StateListener mStateListener =
            new StatusBarStateController.StateListener() {
                @Override
                public void onStateChanged(int newState) {
                    updateVisibility(newState);
                }
            };

    public ShadeCarrierTextController(
            CarrierText view,
            CarrierTextManager.Builder carrierTextManagerBuilder,
            StatusBarStateController statusBarStateController) {
        super(view);
        mCarrierTextManager = carrierTextManagerBuilder
                .setShowAirplaneMode(view.getShowAirplaneMode())
                .setShowMissingSim(view.getShowMissingSim())
                .setDebugLocationString(view.getDebugLocation())
                .build();
        mStatusBarStateController = statusBarStateController;
    }

    @Override
    protected void onInit() {
        updateVisibility(mStatusBarStateController.getState());
    }

    @Override
    protected void onViewAttached() {
        mCarrierTextManager.setListening(mCarrierTextCallback);
        mStatusBarStateController.addCallback(mStateListener);
        updateVisibility(mStatusBarStateController.getState());
    }

    @Override
    protected void onViewDetached() {
        mCarrierTextManager.setListening(null);
        mStatusBarStateController.removeCallback(mStateListener);
    }

    private void updateVisibility(int state) {
        mView.setVisibility(state == StatusBarState.SHADE ? View.VISIBLE : View.GONE);
    }
}