/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.shade;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.view.animation.LinearInterpolator;

/**
 * KitKat UI Project (15.2): the android-4.4.4_r1 PanelView fling physics,
 * ported from the donor (packages/SystemUI/src/com/android/systemui/statusbar/
 * phone/PanelView.java, animationTick + loadDimens).
 *
 * <p>The donor integrates the panel height every frame with a signed
 * velocity under constant acceleration (2000dp/s^2 in either direction),
 * a minimum collapse speed (100dp/s - the panel never stalls while
 * closing), and an opening speed cap (3000dp/s), stopping exactly at the
 * target with no overshoot. Releasing mid-animation simply resumes
 * tracking (the modern touch handler already cancels the animator on
 * ACTION_DOWN, which is the donor's "catch release").
 *
 * <p>That trajectory is constant-acceleration (plus one linear cruise
 * segment for the opening cap), which is expressible in closed form - so
 * it is applied as interpolator + duration on the modern ValueAnimator,
 * preserving all the machinery that hangs off the shade fling (listeners,
 * jank monitoring, fade-out bookkeeping in
 * NotificationPanelViewController.flingToHeight and
 * QuickSettingsController.flingQs) and replacing only the FEEL of
 * FlingAnimationUtils' bezier curves.
 */
final class KitKatPanelFling {

    // Donor dimens (android-4.4.4_r1 SystemUI res/values/dimens.xml).
    /** expand_accel / collapse_accel: 2000dp/s^2 in either direction. */
    private static final float ACCEL_DP = 2000f;
    /** fling_collapse_min_velocity: closing speed floor. */
    private static final float COLLAPSE_MIN_VELOCITY_DP = 100f;
    /** fling_gesture_max_output_velocity: opening speed cap. */
    private static final float MAX_OUTPUT_VELOCITY_DP = 3000f;
    /** Smallest sane animation; the donor had no floor but a sub-frame
     * ValueAnimator flickers on the modern pipeline. */
    private static final long MIN_DURATION_MS = 16L;

    private KitKatPanelFling() {
    }

    /**
     * Applies the donor fling trajectory to the animator: sets its duration
     * and interpolator so the animated value follows the 4.4
     * constant-acceleration integration from {@code currentHeight} to
     * {@code targetHeight} with the release velocity {@code velocity}
     * (signed: positive expands).
     */
    static void apply(Resources res, ValueAnimator animator, float currentHeight,
            float targetHeight, float velocity) {
        final float density = res.getDisplayMetrics().density;
        final float accel = ACCEL_DP * density;
        final float minCollapseVel = COLLAPSE_MIN_VELOCITY_DP * density;
        final float maxOutputVel = MAX_OUTPUT_VELOCITY_DP * density;

        final boolean expanding = targetHeight > currentHeight;
        final float deltaH = Math.abs(targetHeight - currentHeight);
        if (deltaH <= 0f) {
            // No distance: the callers early-return in this case, but be
            // safe and run a minimal linear animation.
            animator.setDuration(MIN_DURATION_MS);
            animator.setInterpolator(new LinearInterpolator());
            return;
        }

        // Effective initial speed toward the target, per the donor tick:
        //  - expanding: v0 + accel*t, capped at maxOutputVel; a negative
        //    release velocity toward an expanding target is zeroed by the
        //    callers and never happens in practice.
        //  - closing: |v0| + accel*t with no cap; a release slower than
        //    minVelocity starts at minVelocity on the very first tick (no
        //    dead time), then accelerates.
        final float v0 = expanding
                ? Math.max(0f, Math.min(velocity, maxOutputVel))
                : Math.max(Math.abs(Math.min(velocity, 0f)), minCollapseVel);

        float totalTime;
        float dCap;
        float tCap;
        float capSpeed;
        if (expanding) {
            final float tToCap = (maxOutputVel - v0) / accel;
            final float dToCap = v0 * tToCap + 0.5f * accel * tToCap * tToCap;
            if (deltaH <= dToCap) {
                // Pure constant-acceleration run to the target: the
                // positive root of accel/2*T^2 + v0*T - deltaH = 0.
                totalTime = (float) ((-v0 + Math.sqrt(v0 * v0 + 2f * accel * deltaH))
                        / accel);
                dCap = 0f;
                tCap = Float.MAX_VALUE;
                capSpeed = 0f;
            } else {
                // Accelerate to the cap, then cruise at maxOutputVel.
                totalTime = tToCap + (deltaH - dToCap) / maxOutputVel;
                dCap = dToCap;
                tCap = tToCap;
                capSpeed = maxOutputVel;
            }
        } else {
            // Closing only accelerates (the donor floors, never caps).
            totalTime = (float) ((-v0 + Math.sqrt(v0 * v0 + 2f * accel * deltaH))
                    / accel);
            dCap = 0f;
            tCap = Float.MAX_VALUE;
            capSpeed = 0f;
        }

        animator.setDuration(Math.max(MIN_DURATION_MS, (long) (totalTime * 1000f)));
        animator.setInterpolator(
                new AccelInterpolator(v0, accel, deltaH, totalTime, dCap, tCap, capSpeed));
    }

    /**
     * Normalized donor trajectory s(t) = v0*t + accel*t^2/2, switching to a
     * linear cruise at capSpeed after tCap, mapped over the total distance
     * deltaH. The animator feeds uniform animation time; t is recovered as
     * input * totalTime.
     */
    private static final class AccelInterpolator implements TimeInterpolator {
        private final float mV0;
        private final float mAccel;
        private final float mDeltaH;
        private final float mTotalTime;
        private final float mDCap;
        private final float mTCap;
        private final float mCapSpeed;

        AccelInterpolator(float v0, float accel, float deltaH, float totalTime,
                float dCap, float tCap, float capSpeed) {
            mV0 = v0;
            mAccel = accel;
            mDeltaH = deltaH;
            mTotalTime = totalTime;
            mDCap = dCap;
            mTCap = tCap;
            mCapSpeed = capSpeed;
        }

        @Override
        public float getInterpolation(float input) {
            final float t = input * mTotalTime;
            final float s;
            if (t >= mTCap) {
                s = mDCap + mCapSpeed * (t - mTCap);
            } else {
                s = mV0 * t + 0.5f * mAccel * t * t;
            }
            // Clamp against float drift at the endpoints.
            final float p = s / mDeltaH;
            return p < 0f ? 0f : (p > 1f ? 1f : p);
        }
    }
}
