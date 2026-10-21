/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.navigationbar.buttons;

import static android.view.Display.INVALID_DISPLAY;
import static android.view.KeyEvent.KEYCODE_UNKNOWN;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerGlobal;
import android.media.AudioManager;
import android.metrics.LogMaker;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.logging.UiEventLoggerImpl;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.navigationbar.NavBarButtonClickLogger;
import com.android.systemui.recents.OverviewProxyService;
import com.android.systemui.res.R;
import com.android.systemui.shared.navigationbar.KeyButtonRipple;
import com.android.systemui.shared.system.QuickStepContract;

public class KeyButtonView extends ImageView implements ButtonInterface {
    private static final String TAG = KeyButtonView.class.getSimpleName();
    private static final int CURSOR_REPEAT_FLAGS = KeyEvent.FLAG_SOFT_KEYBOARD
            | KeyEvent.FLAG_KEEP_TOUCH_MODE;

    private final boolean mPlaySounds;
    private final UiEventLogger mUiEventLogger;
    private int mContentDescriptionRes;
    private long mDownTime;
    private int mCode;
    private int mTouchDownX;
    private int mTouchDownY;
    private boolean mIsVertical;
    private AudioManager mAudioManager;
    private boolean mGestureAborted;
    @VisibleForTesting boolean mLongClicked;
    private OnClickListener mOnClickListener;
    private final KeyButtonRipple mRipple;
    private final OverviewProxyService mOverviewProxyService;
    private final MetricsLogger mMetricsLogger = Dependency.get(MetricsLogger.class);
    private final InputManagerGlobal mInputManagerGlobal;
    private final Paint mOvalBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private float mDarkIntensity;
    private boolean mHasOvalBg = false;
    private NavBarButtonClickLogger mNavBarButtonClickLogger;

    // KitKat UI Project (P9): the 4.4 press glow, ported from
    // android-4.4.4_r1 frameworks/base SystemUI KeyButtonView. Draws the
    // ic_sysbar_highlight glow behind the icon, animating alpha/scale on
    // press (GLOW_MAX_SCALE_FACTOR), in place of the modern ripple.
    // KitKat UI Project (11.4): donor deltas that were missing from the
    // first port - icons render at QUIESCENT ALPHA 0.7 and brighten to
    // full alpha on press (the strongest visible 4.4 feedback cue), the
    // glow alpha is multiplied by the drawing alpha, and the release
    // animation fades drawingAlpha back to quiescent alongside the glow.
    private static final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    public static final float DEFAULT_QUIESCENT_ALPHA = 0.70f;
    private final Drawable mGlowBG;
    private final int mGlowWidth;
    private final int mGlowHeight;
    private float mGlowAlpha = 0f;
    private float mGlowScale = 1f;
    private float mDrawingAlpha = 1f;
    private float mQuiescentAlpha = DEFAULT_QUIESCENT_ALPHA;
    private Animator mAnimateToQuiescent = new ObjectAnimator();
    private AnimatorSet mGlowAnim;
    // KitKat UI Project (11.4 fix): redraw trigger for the release fade -
    // even if every per-frame invalidation is lost (detached window,
    // cancelled animator), this forces one more draw shortly after the
    // fade window, which the onDraw self-healing guard then renders at
    // the quiescent state.
    private final Runnable mGlowFailSafe = new Runnable() {
        @Override
        public void run() {
            if (!isPressed()) {
                if (getParent() instanceof View parent) {
                    parent.invalidate();
                }
                invalidate();
            }
        }
    };

    @VisibleForTesting
    public enum NavBarButtonEvent implements UiEventLogger.UiEventEnum {

        @UiEvent(doc = "The home button was pressed in the navigation bar.")
        NAVBAR_HOME_BUTTON_TAP(533),

        @UiEvent(doc = "The back button was pressed in the navigation bar.")
        NAVBAR_BACK_BUTTON_TAP(534),

        @UiEvent(doc = "The overview button was pressed in the navigation bar.")
        NAVBAR_OVERVIEW_BUTTON_TAP(535),

        @UiEvent(doc = "The ime switcher button was pressed in the navigation bar.")
        NAVBAR_IME_SWITCHER_BUTTON_TAP(923),

        @UiEvent(doc = "The home button was long-pressed in the navigation bar.")
        NAVBAR_HOME_BUTTON_LONGPRESS(536),

        @UiEvent(doc = "The back button was long-pressed in the navigation bar.")
        NAVBAR_BACK_BUTTON_LONGPRESS(537),

        @UiEvent(doc = "The overview button was long-pressed in the navigation bar.")
        NAVBAR_OVERVIEW_BUTTON_LONGPRESS(538),

        NONE(0);  // an event we should not log

        private final int mId;

        NavBarButtonEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }
    private final Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                // Log.d("KeyButtonView", "longpressed: " + this);
                if (mCode == KeyEvent.KEYCODE_DPAD_LEFT || mCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    sendEvent(KeyEvent.ACTION_UP, CURSOR_REPEAT_FLAGS,
                            System.currentTimeMillis(), false);
                    sendEvent(KeyEvent.ACTION_DOWN, CURSOR_REPEAT_FLAGS,
                            System.currentTimeMillis(), false);
                    postDelayed(mCheckLongPress, ViewConfiguration.getKeyRepeatDelay());
                } else if (isLongClickable()) {
                    // Just an old-fashioned ImageView
                    performLongClick();
                    mLongClicked = true;
                } else {
                    if (mCode != KEYCODE_UNKNOWN) {
                        sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_LONG_PRESS);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                    }
                    mLongClicked = true;
                }
            }
        }
    };

    public KeyButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyButtonView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, InputManagerGlobal.getInstance(), new UiEventLoggerImpl());
    }

    @VisibleForTesting
    public KeyButtonView(Context context, AttributeSet attrs, int defStyle,
            InputManagerGlobal manager, UiEventLogger uiEventLogger) {
        super(context, attrs);
        mUiEventLogger = uiEventLogger;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.KeyButtonView,
                defStyle, 0);

        mCode = a.getInteger(R.styleable.KeyButtonView_keyCode, KEYCODE_UNKNOWN);

        mPlaySounds = a.getBoolean(R.styleable.KeyButtonView_playSound, true);

        TypedValue value = new TypedValue();
        if (a.getValue(R.styleable.KeyButtonView_android_contentDescription, value)) {
            mContentDescriptionRes = value.resourceId;
        }

        a.recycle();

        setClickable(true);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // KitKat UI Project (P9): load the 4.4 glow highlight and drop the
        // modern ripple background in its favor.
        mGlowBG = context.getDrawable(R.drawable.ic_sysbar_highlight);
        if (mGlowBG != null) {
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        } else {
            mGlowWidth = 0;
            mGlowHeight = 0;
        }
        mRipple = new KeyButtonRipple(context, this, R.dimen.key_button_ripple_max_width);
        mOverviewProxyService = Dependency.get(OverviewProxyService.class);
        mInputManagerGlobal = manager;
        setBackground(null);
        setWillNotDraw(false);
        forceHasOverlappingRendering(false);
        // KitKat UI Project (11.4): 4.4 buttons rest at 70% icon alpha
        // (donor constructor setDrawingAlpha(mQuiescentAlpha)).
        setDrawingAlpha(mQuiescentAlpha);
    }

    // KitKat UI Project (11.4): donor drawingAlpha machinery. ImageView's
    // setAlpha(int) overload sets the alpha on this view's drawable
    // directly (kept verbatim from the 4.4 donor).
    public void setDrawingAlpha(float x) {
        setAlpha((int) (x * 255));
        mDrawingAlpha = x;
    }

    public float getDrawingAlpha() {
        return mDrawingAlpha;
    }

    // KitKat UI Project (P9): the 4.4 glow drawing, ported from 4.4
    // KeyButtonView.onDraw (draw the glow scaled/alpha'd behind the icon).
    @Override
    protected void onDraw(Canvas canvas) {
        // KitKat UI Project (11.4 fix): self-healing guard. If the button
        // is not pressed and no glow animation is running, the glow MUST
        // be at its quiescent state - force it before drawing so a lost,
        // cancelled or non-invalidating fade can never freeze the halo on
        // screen. The guard is a no-op whenever the animations behave
        // (during the fade mGlowAnim.isRunning() is true; after it,
        // glowAlpha is already 0).
        if (!isPressed()
                && (mGlowAnim == null || !mGlowAnim.isRunning())
                && (mGlowAlpha != 0f || mGlowScale != 1f
                        || mDrawingAlpha != mQuiescentAlpha)) {
            mGlowAlpha = 0f;
            mGlowScale = 1f;
            // ImageView.setAlpha(int) invalidates when the value changes;
            // postInvalidateOnAnimation covers the case where it does not.
            setDrawingAlpha(mQuiescentAlpha);
            postInvalidateOnAnimation();
        }
        if (mGlowBG != null) {
            canvas.save();
            final int w = getWidth();
            final int h = getHeight();
            final float aspect = (float) mGlowWidth / mGlowHeight;
            final int drawW = (int) (h * aspect);
            final int drawH = h;
            final int margin = (drawW - w) / 2;
            canvas.scale(mGlowScale, mGlowScale, w * 0.5f, h * 0.5f);
            mGlowBG.setBounds(-margin, 0, drawW - margin, drawH);
            // KitKat UI Project (11.4): donor multiplies the glow by the
            // drawing alpha (mDrawingAlpha * mGlowAlpha * 255).
            mGlowBG.setAlpha((int) (mDrawingAlpha * mGlowAlpha * 255));
            mGlowBG.draw(canvas);
            canvas.restore();
        }
        super.onDraw(canvas);
    }

    private void invalidateGlow() {
        // KitKat UI Project (11.4 fix): the first port's dirty-rect
        // invalidation never produced reliable per-frame redraws on the
        // modern render pipeline (the glow did not appear at all before
        // the quiescent-alpha work, and froze at full alpha after release
        // with it, because only state-change redraws ever happened). The
        // donor invalidates the whole view per frame (setGlowAlpha ->
        // invalidate()); the parent is invalidated too because the 1.8x
        // scaled glow draws outside the view bounds and the parent has
        // clipChildren=false.
        if (getParent() instanceof View parent) {
            parent.invalidate();
        }
        invalidate();
    }

    @Override
    public void setPressed(boolean pressed) {
        // KitKat UI Project (P9): 4.4 press glow animation (ported).
        if (mGlowBG != null) {
            if (pressed != isPressed()) {
                if (mGlowAnim != null && mGlowAnim.isRunning()) {
                    mGlowAnim.cancel();
                }
                final AnimatorSet as = mGlowAnim = new AnimatorSet();
                if (pressed) {
                    removeCallbacks(mGlowFailSafe);
                    if (mGlowScale < GLOW_MAX_SCALE_FACTOR) mGlowScale = GLOW_MAX_SCALE_FACTOR;
                    if (mGlowAlpha < mQuiescentAlpha) mGlowAlpha = mQuiescentAlpha;
                    // KitKat UI Project (11.4): donor snaps the icon to
                    // full alpha on press - the icon brighten plus the glow
                    // halo are the complete 4.4 press feedback.
                    setDrawingAlpha(1f);
                    as.playTogether(
                            ObjectAnimator.ofPropertyValuesHolder(this,
                                    PropertyValuesHolder.ofFloat("glowAlpha", 1f)),
                            ObjectAnimator.ofPropertyValuesHolder(this,
                                    PropertyValuesHolder.ofFloat("glowScale",
                                            GLOW_MAX_SCALE_FACTOR)));
                    as.setDuration(50);
                } else {
                    // KitKat UI Project (11.4): donor release animation fades
                    // the icon back to quiescent alpha alongside the glow.
                    mAnimateToQuiescent.cancel();
                    mAnimateToQuiescent =
                            ObjectAnimator.ofFloat(this, "drawingAlpha", mQuiescentAlpha);
                    as.playTogether(
                            ObjectAnimator.ofPropertyValuesHolder(this,
                                    PropertyValuesHolder.ofFloat("glowAlpha", 0f)),
                            ObjectAnimator.ofPropertyValuesHolder(this,
                                    PropertyValuesHolder.ofFloat("glowScale", 1f)),
                            mAnimateToQuiescent);
                    as.setDuration(500);
                    postDelayed(mGlowFailSafe, 600);
                }
                as.start();
            }
        }
        super.setPressed(pressed);
    }

    // Property accessors for the glow animators (PropertyValuesHolder lookups).
    public void setGlowAlpha(float alpha) {
        mGlowAlpha = alpha;
        invalidateGlow();
    }

    public float getGlowAlpha() {
        return mGlowAlpha;
    }

    public void setGlowScale(float scale) {
        mGlowScale = scale;
        invalidateGlow();
    }

    public float getGlowScale() {
        return mGlowScale;
    }

    @Override
    public boolean isClickable() {
        return mCode != KEYCODE_UNKNOWN || super.isClickable();
    }

    public void setCode(int code) {
        mCode = code;
    }

    @Override
    public void setOnClickListener(OnClickListener onClickListener) {
        super.setOnClickListener(onClickListener);
        mOnClickListener = onClickListener;
    }

    public void setNavBarButtonClickLogger(NavBarButtonClickLogger navBarButtonClickLogger) {
        mNavBarButtonClickLogger = navBarButtonClickLogger;
    }

    public void loadAsync(Icon icon) {
        new AsyncTask<Icon, Void, Drawable>() {
            @Override
            protected Drawable doInBackground(Icon... params) {
                return params[0].loadDrawable(mContext);
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                setImageDrawable(drawable);
            }
        }.execute(icon);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mContentDescriptionRes != 0) {
            setContentDescription(mContext.getString(mContentDescriptionRes));
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (mCode != KEYCODE_UNKNOWN) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null));
            if (isLongClickable()) {
                info.addAction(
                        new AccessibilityNodeInfo.AccessibilityAction(ACTION_LONG_CLICK, null));
            }
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            jumpDrawablesToCurrentState();
        }
    }

    @Override
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        if (action == ACTION_CLICK && mCode != KEYCODE_UNKNOWN) {
            sendEvent(KeyEvent.ACTION_DOWN, 0, SystemClock.uptimeMillis());
            sendEvent(KeyEvent.ACTION_UP, 0);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        } else if (action == ACTION_LONG_CLICK && mCode != KEYCODE_UNKNOWN) {
            sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_LONG_PRESS);
            sendEvent(KeyEvent.ACTION_UP, 0);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            return true;
        }
        return super.performAccessibilityActionInternal(action, arguments);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final boolean showSwipeUI = mOverviewProxyService.shouldShowSwipeUpUI();
        final int action = ev.getAction();
        int x, y;
        if (action == MotionEvent.ACTION_DOWN) {
            mGestureAborted = false;
        }
        if (mGestureAborted) {
            setPressed(false);
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = SystemClock.uptimeMillis();
                mLongClicked = false;
                setPressed(true);

                mTouchDownX = (int) ev.getX();
                mTouchDownY = (int) ev.getY();
                if (mCode == KeyEvent.KEYCODE_DPAD_LEFT || mCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_VIRTUAL_HARD_KEY
                            | KeyEvent.FLAG_KEEP_TOUCH_MODE, mDownTime, false);
                } else if (mCode != KEYCODE_UNKNOWN) {
                    sendEvent(KeyEvent.ACTION_DOWN, 0, mDownTime);
                } else {
                    // Provide the same haptic feedback that the system offers for virtual keys.
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                if (!showSwipeUI) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                }
                removeCallbacks(mCheckLongPress);
                postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                x = (int) ev.getX();
                y = (int) ev.getY();

                // KitKat UI Project (11.4): the stock quickstep touch-slop
                // cancel killed the 50ms glow fade-in on small pointer jitter
                // (especially mouse input on the emulator). 4.4 kept the
                // button pressed while the pointer stayed within bounds
                // (classic View behavior); with the KitKat launcher there is
                // no gesture nav to protect here, and abortCurrentGesture()
                // remains the system-side cancel path.
                if (x < 0 || x > getWidth() || y < 0 || y > getHeight()) {
                    setPressed(false);
                    removeCallbacks(mCheckLongPress);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (mCode != KEYCODE_UNKNOWN) {
                    sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                }
                removeCallbacks(mCheckLongPress);
                break;
            case MotionEvent.ACTION_UP:
                final boolean doIt = isPressed() && !mLongClicked;
                setPressed(false);
                final boolean doHapticFeedback = (SystemClock.uptimeMillis() - mDownTime) > 150;
                if (showSwipeUI) {
                    if (doIt) {
                        // Apply haptic feedback on touch up since there is none on touch down
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        playSoundEffect(SoundEffectConstants.CLICK);
                    }
                } else if (doHapticFeedback && !mLongClicked) {
                    // Always send a release ourselves because it doesn't seem to be sent elsewhere
                    // and it feels weird to sometimes get a release haptic and other times not.
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE);
                }
                if (mCode != KEYCODE_UNKNOWN) {
                    if (doIt) {
                        sendEvent(KeyEvent.ACTION_UP, 0);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                    } else {
                        sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                    }
                } else {
                    // no key code, just a regular ImageView
                    if (doIt && mOnClickListener != null) {
                        mOnClickListener.onClick(this);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                    }
                }
                removeCallbacks(mCheckLongPress);
                break;
        }

        return true;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);

        if (drawable == null) {
            return;
        }
        KeyButtonDrawable keyButtonDrawable = (KeyButtonDrawable) drawable;
        keyButtonDrawable.setDarkIntensity(mDarkIntensity);
        mHasOvalBg = keyButtonDrawable.hasOvalBg();
        if (mHasOvalBg) {
            mOvalBgPaint.setColor(keyButtonDrawable.getDrawableBackgroundColor());
        }
        mRipple.setType(keyButtonDrawable.hasOvalBg() ? KeyButtonRipple.Type.OVAL
                : KeyButtonRipple.Type.ROUNDED_RECT);
    }

    public void playSoundEffect(int soundConstant) {
        if (!mPlaySounds) return;
        mAudioManager.playSoundEffect(soundConstant, ActivityManager.getCurrentUser());
    }

    public void sendEvent(int action, int flags) {
        sendEvent(action, flags, SystemClock.uptimeMillis());
    }

    private void logSomePresses(int action, int flags) {
        boolean longPressSet = (flags & KeyEvent.FLAG_LONG_PRESS) != 0;
        NavBarButtonEvent uiEvent = NavBarButtonEvent.NONE;
        if (action == MotionEvent.ACTION_UP && mLongClicked) {
            return;  // don't log the up after a long press
        }
        if (action == MotionEvent.ACTION_DOWN && !longPressSet) {
            return;  // don't log a down unless it is also the long press marker
        }
        if ((flags & KeyEvent.FLAG_CANCELED) != 0
                || (flags & KeyEvent.FLAG_CANCELED_LONG_PRESS) != 0) {
            return;  // don't log various cancels
        }
        switch(mCode) {
            case KeyEvent.KEYCODE_BACK:
                uiEvent = longPressSet
                        ? NavBarButtonEvent.NAVBAR_BACK_BUTTON_LONGPRESS
                        : NavBarButtonEvent.NAVBAR_BACK_BUTTON_TAP;

                if (mNavBarButtonClickLogger != null) {
                    mNavBarButtonClickLogger.logBackButtonClick();
                }
                break;
            case KeyEvent.KEYCODE_HOME:
                uiEvent = longPressSet
                        ? NavBarButtonEvent.NAVBAR_HOME_BUTTON_LONGPRESS
                        : NavBarButtonEvent.NAVBAR_HOME_BUTTON_TAP;

                if (mNavBarButtonClickLogger != null) {
                    mNavBarButtonClickLogger.logHomeButtonClick();
                }
                break;
            case KeyEvent.KEYCODE_APP_SWITCH:
                uiEvent = longPressSet
                        ? NavBarButtonEvent.NAVBAR_OVERVIEW_BUTTON_LONGPRESS
                        : NavBarButtonEvent.NAVBAR_OVERVIEW_BUTTON_TAP;
                break;
        }
        if (uiEvent != NavBarButtonEvent.NONE) {
            mUiEventLogger.log(uiEvent);
        }
    }

    private void sendEvent(int action, int flags, long when) {
        sendEvent(action, flags, when, true);
    }

    private void sendEvent(int action, int flags, long when, boolean applyDefaultFlags) {
        mMetricsLogger.write(new LogMaker(MetricsEvent.ACTION_NAV_BUTTON_EVENT)
                .setType(MetricsEvent.TYPE_ACTION)
                .setSubtype(mCode)
                .addTaggedData(MetricsEvent.FIELD_NAV_ACTION, action)
                .addTaggedData(MetricsEvent.FIELD_FLAGS, flags));
        logSomePresses(action, flags);
        if (mCode == KeyEvent.KEYCODE_BACK && flags != KeyEvent.FLAG_LONG_PRESS) {
            Log.i(TAG, "Back button event: " + KeyEvent.actionToString(action));
        }
        final int repeatCount = (flags & KeyEvent.FLAG_LONG_PRESS) != 0 ? 1 : 0;
        if (applyDefaultFlags) {
            flags |= KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
        }
        final KeyEvent ev = new KeyEvent(mDownTime, when, action, mCode, repeatCount,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags,
                InputDevice.SOURCE_KEYBOARD);

        int displayId = INVALID_DISPLAY;

        // Make KeyEvent work on multi-display environment
        if (getDisplay() != null) {
            displayId = getDisplay().getDisplayId();
        }
        if (displayId != INVALID_DISPLAY) {
            ev.setDisplayId(displayId);
        }
        mInputManagerGlobal.injectInputEvent(ev,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    @Override
    public void abortCurrentGesture() {
        Log.d("b/63783866", "KeyButtonView.abortCurrentGesture");
        if (mCode != KeyEvent.KEYCODE_UNKNOWN) {
            sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
        }
        // When aborting long-press home and Launcher has requested to override it, fade out the
        // ripple more quickly.
        if (mCode == KeyEvent.KEYCODE_HOME && Dependency.get(AssistManager.class)
                .shouldOverrideAssist(AssistManager.INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS)) {
            mRipple.speedUpNextFade();
        }
        setPressed(false);
        mRipple.abortDelayedRipple();
        mGestureAborted = true;
    }

    /** Run when the ripple for this button is next invisible. Only used once. */
    public void setOnRippleInvisibleRunnable(Runnable onRippleInvisibleRunnable) {
        mRipple.setOnInvisibleRunnable(onRippleInvisibleRunnable);
    }

    @Override
    public void setDarkIntensity(float darkIntensity) {
        mDarkIntensity = darkIntensity;

        Drawable drawable = getDrawable();
        if (drawable != null) {
            ((KeyButtonDrawable) drawable).setDarkIntensity(darkIntensity);
            // Since we reuse the same drawable for multiple views, we need to invalidate the view
            // manually.
            invalidate();
        }
        mRipple.setDarkIntensity(darkIntensity);
    }

    @Override
    public void setDelayTouchFeedback(boolean shouldDelay) {
        mRipple.setDelayTouchFeedback(shouldDelay);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mHasOvalBg) {
            int d = Math.min(getWidth(), getHeight());
            canvas.drawOval(0, 0, d, d, mOvalBgPaint);
        }
        super.draw(canvas);
    }

    @Override
    public void setVertical(boolean vertical) {
        mIsVertical = vertical;
    }
}
