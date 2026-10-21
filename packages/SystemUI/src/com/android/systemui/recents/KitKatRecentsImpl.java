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

package com.android.systemui.recents;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.wm.shell.recents.RecentTasks;
import com.android.wm.shell.util.GroupedRecentTaskInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * KitKat UI Project: 4.4-style recents served by SystemUI.
 *
 * With the KitKat Launcher3 port there is no Quickstep overview, so the
 * recents button does nothing under OverviewProxyRecentsImpl (the launcher
 * never binds the overview proxy). This implementation instead opens
 * {@link KitKatRecentsActivity}: a vertically scrolling card list driven by
 * the modern task model (shell {@link RecentTasks} for the list,
 * ActivityManagerWrapper for snapshots, launch and removal), the same way the
 * 4.4 recents panel worked.
 *
 * As in 4.4, recents is not available from the keyguard.
 */
@SysUISingleton
public class KitKatRecentsImpl implements RecentsImplementation {

    private static final String TAG = "KitKatRecentsImpl";
    private static final int MAX_RECENT_TASKS = 20;

    private final Context mContext;
    private final Optional<RecentTasks> mRecentTasks;
    private final KeyguardStateController mKeyguardStateController;
    private final ActivityStarter mActivityStarter;
    private final Executor mMainExecutor;

    @Inject
    public KitKatRecentsImpl(
            Context context,
            Optional<RecentTasks> recentTasks,
            KeyguardStateController keyguardStateController,
            ActivityStarter activityStarter,
            @Main Executor mainExecutor) {
        mContext = context;
        mRecentTasks = recentTasks;
        mKeyguardStateController = keyguardStateController;
        mActivityStarter = activityStarter;
        mMainExecutor = mainExecutor;
    }

    @Override
    public void toggleRecentApps() {
        showRecents();
    }

    @Override
    public void showRecentApps(boolean triggeredFromAltTab) {
        showRecents();
    }

    @Override
    public void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
        // Home key / alt-tab dismisses an open recents, as in 4.4.
        mContext.sendBroadcast(
                new Intent(KitKatRecentsActivity.ACTION_FINISH)
                        .setPackage(mContext.getPackageName()));
    }

    private void showRecents() {
        // 4.4 did not surface recents from the keyguard.
        if (mKeyguardStateController.isShowing()) {
            return;
        }
        if (mRecentTasks.isPresent()) {
            mRecentTasks.get().getRecentTasks(MAX_RECENT_TASKS,
                    ActivityManager.RECENT_IGNORE_UNAVAILABLE,
                    UserHandle.USER_CURRENT,
                    mMainExecutor,
                    tasks -> startRecentsActivity(tasks));
        }
    }

    private void startRecentsActivity(List<GroupedRecentTaskInfo> groupedTasks) {
        ArrayList<ActivityManager.RecentTaskInfo> tasks = new ArrayList<>();
        for (GroupedRecentTaskInfo group : groupedTasks) {
            // Split-screen pairs are listed as their own cards.
            tasks.addAll(group.getTaskInfoList());
        }
        Intent intent = new Intent(mContext, KitKatRecentsActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putParcelableArrayListExtra(
                        KitKatRecentsActivity.EXTRA_RECENT_TASKS, tasks);
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0 /* delay */);
    }
}