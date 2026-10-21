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

package com.android.systemui.statusbar.phone;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.res.R;
import com.android.systemui.shade.ShadeExpansionStateManager;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/**
 * KitKat UI Project: the 4.4 ticker.
 *
 * When a notification is posted while the shade is closed, the status bar
 * contents crossfade out and the notification's icon, title and text scroll
 * (marquee) through the status bar for a few seconds before crossfading
 * back - the classic KitKat alert. Heads-up notifications stay off by default
 * (no seeder writes heads_up_notifications_enabled), so the ticker is the
 * primary alert, as in 4.4.
 *
 * The ticker view and the status bar contents live in status_bar.xml; the
 * notification source is the SystemUI NotificationListenerService
 * (see the wiring in CentralSurfacesImpl). Opening the shade cancels the
 * ticker immediately.
 */
public class Ticker {

    private static final long TICKER_DURATION = 6000;
    private static final long FADE_DURATION = 300;

    private final Context mContext;
    private final View mContents;
    private final LinearLayout mTicker;
    private final ImageView mIcon;
    private final TextView mTitle;
    private final TextView mText;
    private final BooleanSupplier mGate;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final ArrayDeque<Entry> mQueue = new ArrayDeque<>();
    private boolean mTicking;
    // KitKat UI Project (P3 polish): key of the notification currently ticking,
    // so a removal can halt it (see remove()).
    private String mCurrentKey;

    private static final class Entry {
        final String key;
        final Drawable icon;
        final CharSequence title;
        final CharSequence text;

        Entry(String key, Drawable icon, CharSequence title, CharSequence text) {
            this.key = key;
            this.icon = icon;
            this.title = title;
            this.text = text;
        }
    }

    private final Runnable mHalt = this::halt;

    public Ticker(Context context,
            PhoneStatusBarView statusBarView,
            ShadeExpansionStateManager shadeExpansionStateManager,
            BooleanSupplier gate) {
        mContext = context;
        mGate = gate;
        mContents = statusBarView.requireViewById(R.id.status_bar_contents);
        mTicker = statusBarView.requireViewById(R.id.ticker);
        mIcon = statusBarView.requireViewById(R.id.ticker_icon);
        mTitle = statusBarView.requireViewById(R.id.ticker_title);
        mText = statusBarView.requireViewById(R.id.ticker_text);

        // Opening the shade cancels the ticker.
        shadeExpansionStateManager.addExpansionListener(event -> {
            if (event.getFraction() > 0.01f) {
                cancel();
            }
        });
    }

    /** Queue a notification for ticking; starts the ticker if idle. */
    public void add(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) {
            return;
        }
        // KitKat UI Project (P3 polish): group summaries carry no content of
        // their own (the 4.4 ticker never showed them).
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return;
        }
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if ((title == null || title.length() == 0)
                && (text == null || text.length() == 0)) {
            return;
        }
        Drawable icon = null;
        try {
            if (n.getSmallIcon() != null) {
                icon = n.getSmallIcon().loadDrawable(mContext);
            }
        } catch (Exception ignored) {
            // fall through without an icon
        }
        mQueue.add(new Entry(sbn.getKey(), icon, title, text));
        if (!mTicking && mGate.getAsBoolean()) {
            start();
        }
    }

    /**
     * KitKat UI Project (P3 polish): halt the current tick when its
     * notification is removed (4.4 behavior), and drop matching queued
     * entries.
     */
    public void remove(StatusBarNotification sbn) {
        String key = sbn.getKey();
        if (key != null) {
            mQueue.removeIf(entry -> key.equals(entry.key));
            if (key.equals(mCurrentKey)) {
                mQueue.clear();
                cancel();
            }
        }
    }

    private void start() {
        Entry entry = mQueue.poll();
        if (entry == null) {
            stop();
            return;
        }
        mTicking = true;
        mCurrentKey = entry.key;
        mIcon.setVisibility(entry.icon != null ? View.VISIBLE : View.GONE);
        mIcon.setImageDrawable(entry.icon);
        mTitle.setText(entry.title != null ? entry.title : "");
        mText.setText(entry.text != null ? entry.text : "");

        mTicker.setVisibility(View.VISIBLE);
        mTicker.setAlpha(0f);
        mContents.animate().alpha(0f).setDuration(FADE_DURATION).start();
        mTicker.animate().alpha(1f).setDuration(FADE_DURATION).start();

        // marquee
        mTitle.setSelected(true);
        mText.setSelected(true);

        mHandler.postDelayed(mHalt, TICKER_DURATION);
    }

    private void halt() {
        mTicker.animate().alpha(0f).setDuration(FADE_DURATION).withEndAction(() -> {
            mTicker.setVisibility(View.GONE);
            if (!mQueue.isEmpty() && mGate.getAsBoolean()) {
                start();
            } else {
                stop();
            }
        }).start();
    }

    private void stop() {
        mTicking = false;
        mCurrentKey = null;
        mHandler.removeCallbacks(mHalt);
        mContents.animate().alpha(1f).setDuration(FADE_DURATION).start();
    }

    /** Cancel immediately: shade opened, or the ticker is otherwise unwelcome. */
    public void cancel() {
        if (!mTicking) {
            return;
        }
        mTicking = false;
        mCurrentKey = null;
        mHandler.removeCallbacks(mHalt);
        mQueue.clear();
        mTitle.setSelected(false);
        mText.setSelected(false);
        mTicker.animate().cancel();
        mTicker.setVisibility(View.GONE);
        mContents.animate().alpha(1f).setDuration(FADE_DURATION).start();
    }

    /** Whether a notification is currently ticking (for dumps/tests). */
    public boolean isTicking() {
        return mTicking;
    }
}