/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.footer.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shared.notifications.domain.interactor.NotificationSettingsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.SeenNotificationsInteractor
import com.android.systemui.statusbar.notification.footer.shared.FooterViewRefactor
import com.android.systemui.statusbar.notification.footer.ui.view.FooterView
import com.android.systemui.util.kotlin.sample
import com.android.systemui.util.ui.AnimatableEvent
import com.android.systemui.util.ui.AnimatedValue
import com.android.systemui.util.ui.toAnimatedValueFlow
import dagger.Module
import dagger.Provides
import java.util.Optional
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** ViewModel for [FooterView]. */
class FooterViewModel(
    activeNotificationsInteractor: ActiveNotificationsInteractor,
    notificationSettingsInteractor: NotificationSettingsInteractor,
    seenNotificationsInteractor: SeenNotificationsInteractor,
    shadeInteractor: ShadeInteractor,
) {
    // KitKat UI Project (12.2): clear-all moved into the shade header as
    // the 4.4 X button (ShadeHeaderController); the footer copy is retired.
    val clearAllButton: FooterButtonViewModel =
        FooterButtonViewModel(
            labelId = flowOf(R.string.clear_all_notifications_text),
            accessibilityDescriptionId = flowOf(R.string.accessibility_clear_all),
            isVisible = flowOf(AnimatedValue.NotAnimating(false)),
        )

    val manageButtonShouldLaunchHistory =
        notificationSettingsInteractor.isNotificationHistoryEnabled

    private val manageOrHistoryButtonText: Flow<Int> =
        manageButtonShouldLaunchHistory.map { shouldLaunchHistory ->
            if (shouldLaunchHistory) R.string.manage_notifications_history_text
            else R.string.manage_notifications_text
        }

    val manageOrHistoryButton: FooterButtonViewModel =
        FooterButtonViewModel(
            labelId = manageOrHistoryButtonText,
            accessibilityDescriptionId = manageOrHistoryButtonText,
            // KitKat UI P18.3: the donor shade had NO manage/history footer
            // button (clear-all lives in the header X, ShadeHeaderController).
            // Full-donor-fidelity ruling: footer row hidden; the binder keeps
            // wiring the click/label paths so re-enabling is a one-line flip.
            isVisible = flowOf(AnimatedValue.NotAnimating(false)),
        )

    val message: FooterMessageViewModel =
        FooterMessageViewModel(
            messageId = R.string.unlock_to_see_notif_text,
            iconId = R.drawable.ic_friction_lock_closed,
            isVisible = seenNotificationsInteractor.hasFilteredOutSeenNotifications,
        )
}

@Module
object FooterViewModelModule {
    @Provides
    @SysUISingleton
    fun provideOptional(
        activeNotificationsInteractor: Provider<ActiveNotificationsInteractor>,
        notificationSettingsInteractor: Provider<NotificationSettingsInteractor>,
        seenNotificationsInteractor: Provider<SeenNotificationsInteractor>,
        shadeInteractor: Provider<ShadeInteractor>,
    ): Optional<FooterViewModel> {
        return if (FooterViewRefactor.isEnabled) {
            Optional.of(
                FooterViewModel(
                    activeNotificationsInteractor.get(),
                    notificationSettingsInteractor.get(),
                    seenNotificationsInteractor.get(),
                    shadeInteractor.get()
                )
            )
        } else {
            Optional.empty()
        }
    }
}
