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

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.systemui.res.R;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;

/**
 * KitKat UI Project: the 4.4-style recents panel.
 *
 * A vertically scrolling list of recent task cards (snapshot with a title bar
 * of app icon + label), tapping a card resumes the task, the X button
 * dismisses it, and an empty list shows the centered "No recent apps" state.
 * The task list is provided by {@link KitKatRecentsImpl} (shell
 * RecentTasks); snapshots, launching and removal go through
 * {@link ActivityManagerWrapper}, so modern task/snapshot behavior is kept.
 */
public class KitKatRecentsActivity extends Activity {

    public static final String EXTRA_RECENT_TASKS = "kitkat_recents_tasks";
    public static final String ACTION_FINISH = "com.android.systemui.recents.action.FINISH";

    private static final int CARD_BG = 0xff252525;
    private static final int CARD_CORNER_RADIUS_DP = 2;
    private static final int CARD_SNAPSHOT_RATIO_NUMERATOR = 9;
    private static final int CARD_SNAPSHOT_RATIO_DENOMINATOR = 16;

    private LinearLayout mCardsContainer;
    private TextView mEmptyView;
    private final ArrayList<ActivityManager.RecentTaskInfo> mTasks = new ArrayList<>();
    private final BroadcastReceiver mFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        ArrayList<ActivityManager.RecentTaskInfo> tasks =
                getIntent().getParcelableArrayListExtra(EXTRA_RECENT_TASKS);
        mTasks.clear();
        if (tasks != null) {
            for (ActivityManager.RecentTaskInfo task : tasks) {
                if (isRecentsTask(task)) {
                    continue;
                }
                mTasks.add(task);
            }
        }
        refreshCards();
        registerReceiver(mFinishReceiver,
                new IntentFilter(ACTION_FINISH), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mFinishReceiver);
        super.onDestroy();
    }

    private boolean isRecentsTask(ActivityManager.RecentTaskInfo task) {
        if (task.baseIntent == null) {
            return true;
        }
        if (task.baseIntent.hasCategory(Intent.CATEGORY_HOME)) {
            return true;
        }
        if (task.baseIntent.getComponent() != null
                && "com.android.systemui".equals(task.baseIntent.getComponent().getPackageName())
                && getClass().getName().equals(
                        task.baseIntent.getComponent().getClassName())) {
            return true;
        }
        return false;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xee161616);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        int padding = dp(16);
        scroller.setPadding(padding, padding, padding, padding);

        mCardsContainer = new LinearLayout(this);
        mCardsContainer.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(mCardsContainer,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mEmptyView = new TextView(this);
        mEmptyView.setText(R.string.kitkat_recents_empty);
        mEmptyView.setTextColor(0xffb3b3b3);
        mEmptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mEmptyView.setGravity(Gravity.CENTER);
        root.addView(mEmptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void refreshCards() {
        mCardsContainer.removeAllViews();
        boolean empty = mTasks.isEmpty();
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        mCardsContainer.setVisibility(empty ? View.GONE : View.VISIBLE);
        for (int i = 0; i < mTasks.size(); i++) {
            mCardsContainer.addView(createCard(mTasks.get(i)));
        }
    }

    private View createCard(final ActivityManager.RecentTaskInfo task) {
        int cardWidth = getResources().getDisplayMetrics().widthPixels - 2 * dp(16);
        int cardHeight = cardWidth * CARD_SNAPSHOT_RATIO_NUMERATOR
                / CARD_SNAPSHOT_RATIO_DENOMINATOR;

        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(dp(CARD_CORNER_RADIUS_DP));
        card.setBackground(bg);
        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(cardWidth, cardHeight);
        cardLp.bottomMargin = dp(12);

        ImageView snapshot = new ImageView(this);
        snapshot.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ThumbnailData thumbnail = ActivityManagerWrapper.getInstance()
                .getTaskThumbnail(task.taskId, true /* isLowResolution */);
        Bitmap bitmap = thumbnail != null ? thumbnail.thumbnail : null;
        if (bitmap != null) {
            snapshot.setImageBitmap(bitmap);
        } else {
            int color = task.taskDescription != null
                    ? task.taskDescription.getBackgroundColor() : 0;
            snapshot.setImageDrawable(new ColorDrawable(color != 0 ? color : CARD_BG));
        }
        card.addView(snapshot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card.addView(createTitleBar(task), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        card.setOnClickListener(v -> launchTask(task));

        // KitKat's X button floats at the top-right of each card.
        TextView dismiss = new TextView(this);
        dismiss.setText("✕");
        dismiss.setTextColor(0xffb3b3b3);
        dismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        dismiss.setGravity(Gravity.CENTER);
        dismiss.setOnClickListener(v -> dismissTask(task));
        FrameLayout.LayoutParams dismissLp = new FrameLayout.LayoutParams(dp(40), dp(40),
                Gravity.TOP | Gravity.END);
        card.addView(dismiss, dismissLp);

        card.setLayoutParams(cardLp);
        return card;
    }

    private View createTitleBar(final ActivityManager.RecentTaskInfo task) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xdd000000);
        bg.setCornerRadius(dp(CARD_CORNER_RADIUS_DP));
        bar.setBackground(bg);

        Drawable icon = loadTaskIcon(task);
        if (icon != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(icon);
            iconView.setColorFilter(0xffffffff);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
            iconLp.rightMargin = dp(8);
            bar.addView(iconView, iconLp);
        }

        TextView label = new TextView(this);
        label.setText(loadTaskLabel(task));
        label.setTextColor(0xffffffff);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setSingleLine(true);
        bar.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        return bar;
    }

    private CharSequence loadTaskLabel(ActivityManager.RecentTaskInfo task) {
        if (task.taskDescription != null && task.taskDescription.getLabel() != null) {
            return task.taskDescription.getLabel();
        }
        try {
            PackageManager pm = getPackageManager();
            ActivityInfo info = pm.getActivityInfo(task.baseIntent.getComponent(), 0);
            CharSequence label = info.loadLabel(pm);
            if (label != null) {
                return label;
            }
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
            // fall through to default
        }
        return getPackageManager().getApplicationLabel(getApplicationInfo());
    }

    private Drawable loadTaskIcon(ActivityManager.RecentTaskInfo task) {
        try {
            return getPackageManager().getActivityIcon(task.baseIntent.getComponent());
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
            return null;
        }
    }

    private void launchTask(ActivityManager.RecentTaskInfo task) {
        ActivityManagerWrapper.getInstance()
                .startActivityFromRecents(task.taskId, null /* options */);
        finish();
    }

    private void dismissTask(ActivityManager.RecentTaskInfo task) {
        ActivityManagerWrapper.getInstance().removeTask(task.taskId);
        mTasks.remove(task);
        refreshCards();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}