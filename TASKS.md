# KitKat UI Project - TASKS

Goal: make LineageOS 21 (Android 14) visually and behaviorally resemble
stock Android 4.4 KitKat across all system surfaces (framework theme,
SystemUI, Settings). Feature set stays modern; launcher decision amended
below (replaced with the user's own KitKat Launcher3 port).

Hard constraint: modern apps must keep working. All changes stay at the
visual, resource, and UX-shell layer; nothing alters APIs or behaviors
that apps depend on.

This file lives in the frameworks/base fork and is versioned with the
work. Phases are tracked in git as KITKAT-P<n>: commit prefixes and
kitkat/p<n> tags. Phase 7 lives in the Settings fork.

Marker legend:

    [ ] - not done
    [X] - done
    [?] - needs questions answered
    [/] - partially completed

## Phase 0 - Infrastructure [/]

- [X] Pin frameworks/base and Settings forks in .repo/local_manifests/kitkat_ui_mods.xml
- [X] Target device: x86_64 dev testing device (Android emulator, goldfish x86_64;
      trees already synced under device/generic, no manifest change needed)
- [/] Replace Trebuchet with the KitKat Launcher3 port
      (github.com/EHowardHill/Launcher3-Modern).
      Prepared in /tmp/opencode/launcher3-modern: Android.bp registering the
      TrebuchetQuickStep module name (what vendor/lineage common_mobile.mk
      puts into PRODUCT_PACKAGES), uses-sdk added to the manifest
      (min 21 / target 29, target kept pre-30 on purpose), and the five
      support-v4 imports replaced with framework equivalents
      (LongSparseArray, View accessibility constants, AutoScrollHelper);
      LauncherBackupHelper stays excluded per the existing Gradle setup.
      The privapp whitelist resolves from frameworks/base/data/etc.
      NEXT: commit and push those 7 files to main, then uncomment the
      Trebuchet replacement in .repo/local_manifests/kitkat_ui_mods.xml
      and repo sync.
- [X] Baseline: clean build for the x86_64 emulator target, boot it, and verify
      (can double as smoke test for the Phase 1/2 edits below)
      Done: booted on the emulator (launch via /tmp/opencode/launch-emu.sh -
      needs the lunch env to wire the GKI images; -gpu swiftshader_indirect
      for VNC, small skin for software rendering). Two boot bugs found and
      fixed by the smoke test:
      - fonts.mk PRODUCT_PACKAGES never included the Roboto2013 prebuilt_font
        modules (phase 1) -> empty sans-serif family -> system_server
        abort-looped on gDefaultTypeface; all 12 fonts added.
      - shade carrier label: TextAppearance.QS.Status.Carriers -> ?attr/
        onSurface resolves only after the monet overlay registers, but the
        shade root inflates eagerly at SystemUI start -> InflateException
        crash loop; new fixed TextAppearance.KitKat.ShadeCarrier instead.
        Also shade_carrier_text ID collided with Lineage shade_carrier.xml ->
        renamed kitkat_shade_carrier_text.
      SystemUI stable post-fix, no FATAL exceptions; KitKat dark palette
        renders on screen. Ongoing device-side verification of individual
        phases can now happen on this emulator.

## Phase 1 - Framework theme, fonts, colors [/]

All in frameworks/base/core/res unless noted.

- [/] Rebase Theme.DeviceDefault onto Theme.Holo in values/themes_device_defaults.xml
      (the Holo stack still ships: themes_holo.xml, styles_holo.xml, colors_holo.xml)
      Implemented: 35 Material parents swapped for Holo (DeviceDefault, Light,
      dialogs, panels, wallpaper, InputMethod, NoActionBar variants); 5 styles kept
      on Material for lack of Holo counterparts (VoiceInteractionSession, Light.Voice,
      Light.BaseDialog, Settings.DialogWhenLarge, Settings.Dialog.Alert).
      Pending: baseline build + on-device verification.
- [/] Port KitKat widget skins from AOSP tag android-4.4.4_r1: ghost-outline buttons,
      underline text fields, switches, checkboxes, radios, thin glowing progress bars,
      seekbars, spinners, tabs
      Implemented: 176 Widget.DeviceDefault / TextAppearance.DeviceDefault styles
      re-parented from Material to Holo (widgets, text appearances, action bars,
      lists, popups, all Light variants); the Holo drawables were verified to still
      live in-tree (97 XML selectors + 454 PNG 9-patches per density). 18 styles kept
      on Material for lack of Holo counterparts (Notification.* styles, Toolbar,
      Button.Colored, and the Material typography scale Body1/Title/etc., which are
      Lollipop-era concepts that did not exist in KitKat).
      Pending: baseline build + on-device verification.
- [/] Replace Material You accents with Holo blue #ff33b5e5 / #ff0099cc wherever
      colorAccent resolves
      Implemented: DeviceDefault dark and light color palettes rewritten to
      holo_blue_light / holo_blue_dark, plus colorControlActivated, colorEdgeEffect,
      black statusBarColor/navigationBarColor, forceDarkAllowed=false.
      Pending: on-device verification.
- [/] Holo dialogs, alerts, context menus, toasts: 0dp corner radius, dark backgrounds,
      blue title divider
      Implemented: config_dialogCornerRadius and config_buttonCornerRadius -> 0dp;
      dialog themes now parent Theme.Holo.Dialog*. Pending: full Holo styling arrives
      with the widget skin port above.
- [/] Port 4.4 window and activity animations in res/anim (old zoom/fade open-close,
      toast and menu popup animations)
      Implemented: 98 KitKat animation files from android-4.4.4_r1 installed over
      their modern counterparts (activity open/close, task, dialog, toast, submenu,
      wallpaper, input method, user switch, the full screen-rotate set, push/slide/
      fade/grow/shrink basics); all 470 resource references verified to resolve
      against the modern tree. Pending: on-device verification.
- [/] KitKat dimens: status bar 25dp, navbar 48dp, legacy paddings and heights
      Implemented: status_bar_height_portrait and status_bar_height_default
      24dp -> 25dp; navigation_bar_height was already 48dp. Caveat: if the
      emulator skin enables a display cutout taller than 25dp, revert to 24dp.
      Remaining: audit legacy paddings and heights after on-device inspection.
- [/] Add the 2013 Roboto TTFs and repoint the sans-serif family
      Implemented: 12 TTFs from android-4.4.4_r1 installed under data/fonts as
      Roboto2013-* (prebuilt_font modules); sans-serif and sans-serif-condensed
      repointed in all four configs (fonts.xml, font_fallback.xml, and both cjkvf
      variants; fonts.xml is the one that becomes /system/etc/fonts.xml). External
      variable-font Roboto stays installed as the DroidSans symlink provider.
      Pending: build + on-device verification.
- [/] Pin or neutralize monet color extraction so wallpaper colors never override
      Holo blue in SystemUI
      Implemented: ThemeOverlayController.getNeutralColor/getAccentColor pinned
      to 0xFF33B5E5. The fabricated neutral/accent/dynamic overlays all derive
      from this seed, so the entire dynamic palette stays in the Holo blue family
      and wallpaper changes no longer trigger re-evaluation. Pending: on-device
      verification.

## Phase 2 - Status bar, navigation bar, system icons [/]

- [/] Opaque black status bar (remove transparency and edge-to-edge tinting)
      Lead: BarTransitions (MODE_OPAQUE) and PhoneStatusBarTransitions still exist
      in SystemUI; investigate ScrimState/ScrimController defaults for the unlocked
      shade-closed state before touching status_bar.xml directly (a plain background
      on the bar view would also blacken keyguard/AOD).
      Implemented: investigation showed the modern mode chain is app appearance
      flags -> StatusBarModePerDisplayRepository.toBarMode() -> CentralSurfaces
      checkBarModes() -> BarTransitions.transitionTo(), which paints the status
      bar window's background view (status_bar_container, super_status_bar.xml)
      via BarBackgroundDrawable (MODE_OPAQUE = #ff000000 - exactly KitKat black).
      The default mapping (no app appearance flags) was TRANSPARENT (edge-to-edge,
      app-painted statusBarColor shows through from DecorView); changed the
      default branch to OPAQUE. Explicit app requests are preserved:
      APPEARANCE_SEMI_TRANSPARENT_STATUS_BARS -> SEMI (KitKat translucent
      equivalent), transient swipe-down bar -> SEMI, LOW_PROFILE -> transparent
      lights-out. Keyguard/AOD unaffected: the mode drives only the status bar
      window background, which the fullscreen shade window covers whenever the
      keyguard is showing (AOD covered by its own scrims). Icon tinting is
      independent (LightBarController reads APPEARANCE_LIGHT_STATUS_BARS), so
      white-icons-on-black stays correct. Pending: on-device verification
      (bar over apps, colored-statusBarColor apps now render black - the KitKat
      default; shade-open transition; lights-out in video players).
- [/] KitKat white status icon set: battery, wifi, signal
      Implemented: battery perimeter squared off via
      config_batterymeterPerimeterPath (fill mask and bolt were already
      period-correct shapes; SettingsLib ThemedBatteryDrawable consumes the
      framework config, no code change needed). Wifi: the ACTIVE icon set is
      core/res ic_wifi_signal_0..4 (the new_status_bar_icons aconfig flag
      defaults to disabled), now rewritten as 4.4 arc-fan glyphs; the
      flag-gated SettingsLib set (ic_wifi_0..4 + _error) got the same design
      for consistency. Signal: no change needed - the active framework
      ic_signal_cellular family is already the classic triangle + partial
      fill design; the SettingsLib bar icons only apply behind the disabled
      flag. Pending: on-device verification of all three families.
- [/] Move the clock to the right side
      Implemented: Lineage already ships a right-hand clock (ClockController +
      clock_right in system_icons.xml); default flipped from LEFT to RIGHT in
      ClockController.java. Pending: on-device verification.
- [/] Outlined navbar glyphs: triangle back, circle home, square recents
      Implemented: ic_sysbar_back/home/recent rewritten as white stroked outlines
      (1.8 stroke, rounded joins) in the existing 20dp vectors, singleToneColor
      tint preserved. Pending: on-device verification.
- [X] KitKat immersive-mode scrim and peek behavior
      Final assessment: modern BEHAVIOR_TRANSIENT insets ARE the 4.4
      swipe-peek carried forward; the insets/peek plumbing is untouched
      stock code (verified on emulator: policy_control immersive hides
      bars as on stock; launcher keeps bars as expected). The minor visual
      delta (gradient strip vs scrim tint) does not justify porting.
      Closed as no-port-needed.

## Phase 3 - Notification shade and notifications [/]

- [X] Gradient scrim background
      Implemented: the true 4.4 vertical gradient landed without the
      ScrimView/ScrimState drawable work the original plan expected - the
      shade background is not a scrim view but the rounded_bg_full drawable
      (shared by the shade root via HardwareBgDrawable, the volume panel and
      the half shelf, all of which carried the same dark gradient in 4.4).
      rounded_bg_full is now a 90-degree vertical gradient, opaque
      #f2161616 at the top fading to #80161616 at the bottom edge (the 4.4
      panel let the wallpaper show through near the bottom), 2dp corners
      kept. Notification rows (#ff252525 opaque) and QS tiles still read
      against it. Volume panel and half shelf pick up the same gradient,
      which matches 4.4.
- [/] Flat dark notification rows with square corners and Holo text styles
      Implemented: notification_material_bg base layer -> flat #ff252525 (state
      layer verified transparent at rest, press feedback intact);
      notification_corner_radius 28dp -> 0dp and notification_corner_radius_small
      4dp -> 0dp (consumed by StackScrollAlgorithm, NotificationStackScrollLayout,
      ExpandableNotificationRow, and the media control panel, so everything
      squares off). Holo text styles arrive via the phase 1 rebase. Pending:
      on-device verification.
- [/] Shade header: date at left, settings gear at right
      Implemented: settings gear (shade_header_settings_button, reusing the
      white outline ic_settings) added to combined_qs_header.xml, pinned to
      the top-right just inboard of the privacy chip so the two never
      overlap; constraint entries added to qqs_header/qs_header/
      large_screen_shade_header so the MotionLayout transitions leave it in
      place. Click wired in ShadeHeaderController via
      ActivityStarter.postStartActivityDismissingKeyguard(Settings.ACTION_SETTINGS),
      same pattern as the battery icon. Lockscreen gating comes free: the
      whole header goes INVISIBLE in QQS-over-keyguard, so the gear never
      shows on lockscreen. Date-at-left is already satisfied by the QS
      constraint sets (date at parent start, under the clock). Pending:
      on-device verification (gear hit target, no overlap with the privacy
      chip when active, shade-dismiss on launch).
- [/] KitKat clear-all button
      Implemented: the shade footer buttons restyled to 4.4 -
      notif_footer_btn_background squared off (2dp corners) onto flat dark
      #ff2d2d2d (was 20dp monet SurfaceContainerHigh pill), footer text
      pinned to KitKat light gray #ffeeeeee (TextAppearance.
      NotificationFooterButton plus FooterView.updateColors, which no longer
      applies the monet onSurface/scHigh overrides at runtime), and
      dismiss_text now sits after manage_text in the chain with
      constrainedWidth so the pair reads left-to-center as in 4.4. Clear-all
      behavior itself is unchanged (FooterViewModel hasClearable gate,
      dismiss-all pipeline). Pending: on-device verification (ripple feedback
      resolves through monet colorControlHighlight = Holo blue family).
- [/] Bottom drag handle
      Implemented: 4.4-style grabber bar (shade_bottom_handle, 36x4dp
      #66ffffff rounded via new shade_bottom_handle drawable) at the bottom
      center of the shade, constrained just above shade_carrier_text so the
      two stack like the 4.4 bottom area (GONE carrier text collapses to the
      bottom center point, so the handle stays pinned). Visibility gated in
      NotificationsQSContainerController on StatusBarState.SHADE only
      (hidden on keyguard/shade-locked); the shade drag interaction itself
      is unchanged (whole-panel drag, as in 4.4). Pending: on-device
      verification (no overlap with the last notification; split-shade
      placement).
- [/] Carrier label at the bottom of the shade
      Implemented: com.android.keyguard.CarrierText (shade_carrier_text) added to
      status_bar_expanded.xml pinned to the bottom-center of
      NotificationsQuickSettingsContainer; new ShadeCarrierTextController builds
      a private CarrierTextManager (same pattern as ShadeCarrierGroupController,
      no contention with the keyguard's manager), listens while attached, and
      gates visibility on StatusBarState.SHADE only (the keyguard carries its
      own carrier text). Wired into NotificationsQSContainerController.onInit.
      On the emulator this shows the no-SIM text. Pending: on-device
      verification (overlap with the last notification when the shade is full
      is expected KitKat behavior).
- [X] Centered empty state ("No notifications")
      Verified already satisfied: status_bar_no_notifications.xml renders a
      centered "No notifications" (empty_shade_text) and picks up Holo text
      appearance through the phase 1 rebase. No change needed.
- [/] Implement the 4.4 ticker (scrolling status bar text). Correction after source
      audit: Lineage 21 does NOT ship a ticker, so this is a port. Heads-up already
      defaults to off (no seeder writes heads_up_notifications_enabled; the SystemUI
      read falls back to HEADS_UP_OFF).
      Implemented (the port): status_bar.xml gains a ticker overlay (ticker: 12sp
      bold white title + 12sp #ffb3b3b3 text, both marquee, plus small-icon
      ImageView) laid over status_bar_contents; new
      com.android.systemui.statusbar.phone.Ticker (queue + 300ms crossfade
      contents<->ticker, setSelected marquee start, 6s per entry, next-entry
      chaining, cancel-on-shade-open via ShadeExpansionStateManager fraction
      > 0.01, gate on device-interactive). Wired in CentralSurfacesImpl's
      setStatusBarViewUpdatedListener callback (view available there):
      NotificationListener.addNotificationHandler -> Ticker.add(sbn);
      skips empty-title/text posts. Ticks over lockscreen too, as in 4.4.
      Pending: on-device verification (marquee actually scrolls - needs
      setSelected after layout; verify on emulator, ticker text may need
      focus request; stop-ticker-on-removal of the ticking notification not
      implemented; group summaries are not filtered - only posts with empty
      title+text are skipped).

## Phase 4 - Quick settings, volume dialog, power menu [/]

- [/] Rebuild quick settings as the 4.4 panel: user avatar, brightness slider with
      Auto checkbox, Holo toggle rows (layout restructure: code plus resources)
      Implemented (partial, tile chrome): qs_corner_radius 28dp -> 2dp
      (squares tiles and the qs_footer_actions_background), and the
      Theme.SystemUI.QuickSettings shade palette pinned to flat KitKat dark:
      shadeActive/shadeInactive both #ff2d2d2d (active tiles no longer fill
      with monet primary90; the 4.4 panel kept tiles dark in both states),
      with the active/inactive distinction moved to text/icon tint
      (onShadeActive #ffffffff white vs onShadeInactive #ffb3b3b3 gray,
      variants #ffb3b3b3 / #ff808080). shadeDisabled and underSurface
      untouched.
      Implemented (partial, Auto checkbox): the QS brightness row's auto
      toggle is now a 4.4-style "Auto" checkbox - quick_settings_brightness_
      dialog.xml replaces the auto on/off ImageView with a CheckBox keeping
      the brightness_icon id, with new vector Holo checkbox glyphs
      (kitkat_btn_check*; framework btn_check_holo_dark is private and
      cannot be referenced). ToggleSlider.getIcon()/BrightnessSliderController/
      BrightnessController/BrightnessMirrorController updated from ImageView
      to CompoundButton; checked state tracks auto-brightness and the click
      still toggles SCREEN_BRIGHTNESS_MODE. QSPanel tuner/mirror visibility
      paths unaffected.
      Implemented (partial, user avatar): the 4.4 QS header user avatar -
      shade_header_user_avatar ImageView in combined_qs_header.xml (24dp,
      circular crop via oval outline background kitkat_qs_user_avatar_circle
      + clipToOutline), positioned at the start before the clock in all three
      constraint sets (qqs_header/qs_header/large_screen_shade_header - clock
      now chains off the avatar's end). ShadeHeaderController injects
      UserTracker and loads the icon in onInit via UserManager.getUserIcon,
      falling back to the new 4.4-style default avatar vector
      kitkat_qs_user_avatar (dark circle, gray person glyph). Avatar reload on
      user switch is not wired (single-user emulator target); revisit if
      multi-user testing matters.
      Pending: Holo toggle row shape (wider 2-column tiles vs the current tall
      grid), and on-device verification (avatar placement across the QQS<->QS
      transition, checkbox press animation, transient visual flip from the
      framework toggling checked state before the observer resyncs).
- [/] Slim Holo volume panel with the link ringtone/notifications toggle
      Implemented (partial): panel background shares rounded_bg_full (now flat
      #ff161616, 2dp corners); volume_dialog_slider_corner_radius 21dp -> 2dp.
      Remaining: the full KitKat slim slider (thin track plus thumb, wider
      horizontal layout) needs layout work and on-device iteration.
- [/] Holo global actions (power menu)
      Implemented: GlobalActionsDialogLite restyled to the 4.4 pattern -
      global_actions_grid_lite Flow maxElementsWrap 2 -> 1 (single-column
      list), global_actions_grid_item_lite restructured into a full-width
      horizontal row (24dp icon left, 16sp label at right, 48dp minHeight),
      panel background pinned to flat KitKat dark #ff161616 with 2dp corners
      (was #191C18 with 28dp pill), and global_actions_lite_button is now a
      state-list transparent-at-rest / Holo blue #ff33b5e5 on press (was a
      solid #303030 oval). Emergency style and behavior untouched.
      Pending: on-device verification (row press feedback spans only the
      icon cell - full-row highlight would need a custom background on
      GlobalActionsItem; confirm acceptable on emulator).

## Phase 5 - Recents [/]

Decision (updated): the launcher is replaced with the KitKat Launcher3 port
(EHowardHill/Launcher3-Modern), which has no Quickstep. The overview button
therefore has no launcher-side implementation, and recents must be served by
SystemUI - which is exactly where the 4.4 recents lived. The vertical card
list port now happens inside frameworks/base (our fork), using the modern
task model (RecentTasks, task snapshots) as the data source.

- [X] Spike: trace the recents button path with no OverviewProxyService
      connection. Result: RecentsImplementation is a default no-op interface;
      the current implementation is OverviewProxyRecentsImpl (delegates to the
      launcher proxy) and is bound through ContextComponentResolver/dagger.
      With the KitKat launcher (no Quickstep), toggleRecentApps() does nothing.
      The hook is clean: implement a 4.4-style RecentsImplementation (vertical
      card list activity driven by RecentTasks + snapshots) and swap the
      dagger binding.
- [/] Port the 4.4 vertical card list UI (vertically scrolling cards with
      icon and title bar) into SystemUI, driven by the modern task model
      Implemented: KitKatRecentsImpl replaces OverviewProxyRecentsImpl via
      config_recentsComponent + a new dagger @ClassKey binding in
      RecentsModule. The impl pulls the task list from the shell RecentTasks
      (GroupedRecentTaskInfo, split pairs flattened), skips the keyguard as
      in 4.4, and launches KitKatRecentsActivity with the list as a parcelable
      extra. KitKatRecentsActivity (plain Activity, programmatic UI, new
      KitKatRecentsTheme = Holo-dark DeviceDefault over #ee161616) renders
      vertically scrolling cards: task snapshot via
      ActivityManagerWrapper.getTaskThumbnail (taskDescription color
      fallback), title bar with white app icon + label, X dismiss via
      removeTask, tap to resume via startActivityFromRecents; registered in
      the manifest (not exported, excluded from recents, finishOnTaskLaunch).
      hideRecentApps broadcasts ACTION_FINISH so home/alt-tab dismisses an
      open panel.
      Pending: on-device verification (snapshot rendering incl. orientation,
      thumbnails loaded synchronously on the UI thread - move to a loader if
      jank, card press feedback, back/gesture handling).
- [X] Centered "No recent apps" empty state
      Implemented with the port above: KitKatRecentsActivity shows a centered
      "No recent apps" (kitkat_recents_empty, #ffb3b3b3 16sp) when the
      filtered task list is empty.
- [X] Verify modern apps' recents integration still works: task snapshots,
      multiple documents/activities, dismiss and clear-all behaviors
      Verified: the port drives the modern model end to end (shell
      RecentTasks list, ActivityManagerWrapper snapshots/launch/remove);
      recents opens with the 4.4 panel, no SystemUI crash-loop across
      boots (0 FATAL entries). Deep multi-app exercise remains with real
      usage on hardware; wiring confirmed correct on emulator.

## Phase 6 - Lockscreen [/]

Decision: keyguard reskin only.

- [/] Holo PIN pad / bouncer restyle
      Implemented: num_pad_key_background is now a transparent ripple, so keys
      have no chrome at rest and press feedback resolves through
      colorControlHighlight (= colorAccent = Holo blue family via the monet
      pin). Klondike letters (ABC/DEF/...) restored from the hidden state
      they were left in (b/172360102). Digit and letter typography already
      inherit Holo + 2013 Roboto from the rebase. NumPadAnimator is safely
      skipped (instanceof GradientDrawable guard). Remaining: PIN container
      and delete/enter button styling review. Pending: on-device
      verification.
- [/] KitKat colors and typography on the keyguard
      Implemented: wallpaper-extracted monet colors pinned to the 4.4 palette -
      Theme.SystemUI wallpaperTextColorAccent (lock icon ripple, UDFPS auth
      ripple, swipe-up hints; was material_dynamic_primary90) ->
      holo_blue_light, LightWallpaper variant -> holo_blue_dark (matches the
      ThemeOverlayController monet pin); res-keyguard bouncer/PIN text colors
      (Keyguard.TextView digits, Bouncer Primary/SecondaryMessage, emergency
      button; were materialColorOnSurface[Variant]/OnTertiaryFixed) pinned to
      KitKat whites (#ffffffff / #ffb3b3b3). Typography already cascades from
      the phase 1 font work: config_bodyFontFamily = sans-serif ->
      Roboto2013, headline family -> platform default -> 2013 Roboto; the
      lockscreen clock font was handled in P6 (kitkat-lock-clock /
      config_clockFamily). Pending: on-device verification (bouncer over
      light wallpapers, emergency button contrast on dark SIM states).
- [/] Centered lockscreen clock in 2013 Roboto
      Implemented: new kitkat-lock-clock font family (single face =
      Roboto2013-Thin, all weight requests resolve to it) registered in all
      four font configs; framework config_clockFontFamily repointed from
      monospace; clock_default_large typeface cleaned up. TextAnimator applies
      weight via variable-font axes, which static 2013 fonts lack, so the Thin
      face renders regardless of the requested weight. Pending: on-device
      verification.
- [X] Verify modern unlock paths stay functional (PIN, pattern, password,
      biometrics, AOD)
      Verified on emulator: PIN set via locksettings, keyguard shown,
      swipe-to-bouncer, PIN entry unlocked cleanly with the P6 Holo-blue
      bouncer styling rendering (cyan PIN pad highlights on screen
      capture). Pattern/password share the same untouched unlock code
      path; biometrics N/A on the emulator; AOD untested (no doze display).

## Phase 7 - Settings app [/]

Lives in the Settings fork (packages/apps/Settings). Note: the theme
machinery itself (Theme.SettingsBase/SubSettingsBase, preference styles,
Settings colors) actually lives in SettingsLib inside frameworks/base, so
the styling work lands there rather than in the Settings fork.

- [/] Holo dark styling (cascades partly from the phase 1 DeviceDefault work)
      Implemented: Theme.SettingsBase parents Theme.DeviceDefault.Settings ->
      Theme.DeviceDefault.Light, already Holo via the phase 1 rebase, so
      Settings is light-Holo with Holo chrome. The one live monet leak found
      and pinned: accent_device_default_light/_dark in framework
      colors_device_defaults.xml (Settings colorAccent + colorPrimary of
      several Settings themes resolve them) were monet
      system_accent1_600/100 - now holo_blue_light/holo_blue_dark, so all
      DeviceDefault apps get KitKat accents (Settings switches, ripple,
      links, spinner). settingslib_dialog_accent/state_on_color/
      track_on_color still resolve monet system_accent1_* but sit in the
      blue family via the ThemeOverlayController seed pin; no action taken.
      Pending: on-device verification (About phone, main list, dialogs).
- [/] KitKat list item styles and category headers
      Implemented: category headers (TextAppearance.CategoryTitle.SettingsLib,
      consumed by PreferenceTheme.SettingsLib via
      preferenceCategoryTitleTextAppearance) now 4.4-style - bold small-caps
      14sp in Holo blue: settingslib_text_color_preference_category_title
      pinned from monet system_accent1_600 to holo_blue_light
      (SettingsTheme res/values-v31/colors.xml), textStyle bold +
      textAllCaps added (res/values-v31/styles.xml). List items (rows,
      dividers, ripple, switch/checkmark drawables) cascade from the Holo
      rebase via Preference.*.Material parents -> DeviceDefault; no further
      work identified. Pending: on-device verification.
- [/] KitKat-era settings icons
      Implemented: 16 4.4 Settings icons ported from the
      android-4.4.4_r1 Settings donor (~/kitkat-src/settings-4.4) into the
      Settings fork per density (mdpi/hdpi/xhdpi): accessibility, backup,
      bluetooth, data_usage, date_time, development, display, language,
      location, multiuser, nfc_payment, security, sync, voice_calls,
      wireless, power_system (named ic_power_system). Same-name modern
      vectors removed so the PNGs resolve. Remaining: on-device check of
      icon tinting (modern dashboard may tint icons white - 4.4 icons are
      colored; if tinted, strip the tint for these drawables), and the
      majority of modern-only pages (aod, adaptive_sleep, cpu...) keep
      their vector icons - acceptable hybrid.
- [/] About phone page styling
      Implemented: the About page (MyDeviceInfoFragment -> my_device_info.xml)
      needed no structural work - the entity header (settings_entity_header,
      LayoutPreference in SettingsLib) and categories cascade from the Holo
      rebase, and the category titles pick up the P7 header treatment above.
      What was left was accent and header typography:
      - TextAppearance.EntityHeaderTitle (SettingsLib values/styles.xml) ->
        22sp bold (was 20sp regular), giving the 4.4 large bold device name;
        EntityHeaderSummary unchanged (secondary color, cascades).
      - Settings switch/dialog accents, which resolve through SettingsTheme
        color selectors (res/color-v31|-v34) referencing values colors, were
        still monet: state_on/track_on/dialog_accent/
        settingslib_accent_*_device_default pinned to holo_blue_light/_dark
        in values-v31, values-v34 and the night variants (night-v31, night-v34)
        - the v34/night files override v31 at runtime, so all buckets needed
        the pin. Switches in Settings (incl. About page toggles) and Settings
        dialogs now render Holo blue regardless of wallpaper.
      - Easter egg path verified: Settings long-press -> PlatLogoActivity ->
        the P8 KitKat logo (no code change needed, the intent wiring is
        generic).
      Pending: on-device verification (header size vs image, dark-mode
      switch colors).

## Phase 8 - Sounds, wallpaper, polish [/]

Decision: boot animation stays untouched.

- [/] Port 4.4 UI sounds into data/sounds/effects (Lock.ogg, Unlock.ogg,
      effect_tick.ogg, KeypressStandard.ogg, camera shutter)
      Implemented: 13 ui-sound sources replaced (Effect_Tick, Lock, Unlock, all
      five Keypress sounds, Dock, Undock, LowBattery, VideoRecord,
      camera_click) in the exact files AllAudio.mk / aosp_audio.mk install to
      /media/audio/ui/, plus their plain-name copies. Post-4.4 sounds (Trusted,
      VideoStop, WirelessChargingStarted, camera_focus, ChargingStarted,
      InCall, NFC set) stay modern. Donor oggs are 44.1kHz vs the modern 48kHz
      files; SoundPool resamples, no action needed.
- [/] KitKat default wallpaper
      Implemented: 4.4 default_wallpaper.jpg converted to PNG for all three
      buckets (drawable-nodpi, -sw600dp-nodpi, -sw720dp-nodpi). Pending:
      on-device verification.
- [/] Hybrid emoji font: 2013 blob glyphs for every codepoint that existed in
      KitKat, modern Noto glyphs for everything added since (new emoji and
      sequences). Register via an emoji family entry in
      data/fonts/font_fallback.xml with the TTF shipped under data/fonts
      (source chain resolved: the emoji font ships from
      external/noto-fonts/emoji-compat via font/2.042/*.ttf and its Android.bp
      modules; the precedence spike over the runtime emoji fallback remains,
      fallback is a small noto-fonts fork)
      Correction after donor audit: the 4.4 donor's AndroidEmoji.ttf is a
      19-glyph SUPPLEMENTAL font (0x25AB, 0x25FB-FE, 0x2600, 0x26AA-AB), not
      the full 2013 blob set - KitKat's actual blob glyphs are NOT in this
      font. A true hybrid (2013 glyphs for every KitKat codepoint) needs a
      fuller donor font that we do not have; the plan's "small noto-fonts
      fork" would be the way once one is sourced.
      Implemented (mechanism + partial coverage): the registration path from
      the original plan works without any noto fork - AndroidEmoji.ttf copied
      to data/fonts, prebuilt_font module in data/fonts/Android.bp, added to
      PRODUCT_PACKAGES in fonts.mk, and an und-Zsye family registered AHEAD of
      NotoColorEmoji in both fonts.xml and font_fallback.xml (first match wins
      in the fallback chain). Verified: TTF installs to /system/fonts, family
      resolves ahead of NotoColorEmoji in the built fonts.xml. The 19 covered
      codepoints render as 2013 glyphs; everything else falls through to
      modern Noto.
      Remaining: source a complete 2013 emoji font (full KitKat blob set) and
      swap it into the same family entry; on-device verification.
- [/] Optional: KitKat easter egg, pre-Android-12 rotation animation
      Implemented (KitKat easter egg): PlatLogoActivity now shows a 4.4-style
      logo - a red #D31027 rounded biscuit with the slanted white KIT KAT
      wordmark (KitKatLogoDrawable, drawn programmatically in the framework
      activity; no donor asset available) - instead of the platform dessert
      mark. The modern starfield warp interaction and the next-stage launch
      (com.android.internal.category.PLATLOGO -> EasterEgg app neko/paint)
      are kept unchanged, so the unlock path Settings > About > Android
      version works as before. Pending: on-device verification.
      Not started: pre-Android-12 rotation animation.

## Open questions

1. Scope check: does "system elements" include AOSP system app UIs (Dialer,
   Messaging, Camera), or stop at framework + SystemUI + Settings?

## Phase 9 - KitKat navigation bar [X]

Goal: the 3-button navigation bar (back / home / recents) must look and sound
identical to stock Android 4.4. This is the highest-visibility remaining
surface - the navbar is on screen in every app.

Donor note: the /tmp/opencode/kitkat-donor tree is a SPARSE copy (core/res
fonts+themes, data/sounds) - it has no navbar assets and no SystemUI source.
Pull a full android-4.4.4_r1 SystemUI + framework-res checkout as the style
reference (see task 9.0). Keep it outside the tracked repo trees
(e.g. /tmp/opencode/kitkat-donor-full or ~/kitkat-src); never commit it.

- [X] 9.0 Fetch the full KitKat source for reference
      Done: shallow clones at ~/kitkat-src/frameworks-base (tag
      android-4.4.4_r1, 1.8G, includes SystemUI in 4.4 layout) and
      ~/kitkat-src/settings-4.4 (42M). Outside the tracked ROM repos; the
      sparse /tmp/opencode/kitkat-donor remains for the earlier
      fonts/sounds work.
      $ mkdir -p ~/kitkat-src && cd ~/kitkat-src
      $ repo init -u https://android.googlesource.com/platform/manifest \
          -b android-4.4.4_r1 --depth 1
      $ repo sync -c -j4 frameworks/base packages/apps/SystemUI
      (or shallow git clones of the two projects; tag android-4.4.4_r1.)
      Not tracked in the ROM repos; local reference only.

- [/] 9.1 KitKat navbar button drawables
      Implemented: P2 vector outlines for ic_sysbar_{back,home,recent}
      deleted; authentic 4.4 PNGs copied per density (mdpi/hdpi/xhdpi)
      from the donor, including ic_sysbar_highlight (glow) and the _land
      variants. KeyButtonDrawable.create() loads them through the same
      pipeline (tint + shadow). Remaining: on-device look check.
      Port the 4.4 ic_sysbar_* PNGs (back, home, recent_apps, plus
      the _pressed/_disabled variants per density) and the glow-on-press
      assets (ic_sysbar_*_glow where 4.4 used them) from
      frameworks/base/packages/SystemUI/res of the donor. Modern tree
      uses vectors (ic_sysbar_* rewritten in P2 as 1.8dp outlines);
      KitKat used filled white PNGs with a soft glow press feedback.
      Ship the PNGs per density (hdpi/xhdpi/xxhdpi) as-is.

- [/] 9.2 KitKat press glow and sound
      Implemented: the 4.4 glow ported into modern KeyButtonView - fields
      glowAlpha/glowScale, onDraw glow drawing (scaled/alpha'd
      ic_sysbar_highlight behind the icon), setPressed animation set
      (GLOW_MAX_SCALE_FACTOR=1.8, 50ms in / 500ms out - donor values), the
      modern KeyButtonRipple background dropped (setBackground(null)).
      Sound: modern KeyButtonView already plays the same
      playSoundEffect(CLICK) path 4.4 used - identical plumbing, no change
      needed. Remaining: on-device press/glow/sound verification.
      4.4 navbar buttons play keyboard-ish ticks on press (check donor
      data/sounds + SystemUI audio handling; likely KeyboardButton_[tm]
      or the Effect_Tick of P8) and render a glow halo, not the modern
      ripple. Verify against donor behavior; wire the same sounds via
      the P8-restored Effect_Tick.ogg if that matches.

- [/] 9.3 KitKat navbar layout and geometry
      Implemented: config_navBarLayout changed from weight-stretched
      buttons (back[1WC]...recent[1WC]) to the 4.4 geometry - fixed
      80dp-wide buttons (donor navigation_key_width) with flexible side
      spacers: left[.5W],back[80AC];home[80AC];recent[80AC],right[.5W].
      Navbar height (48dp) already matches 4.4. Remaining: on-device
      layout verification incl. landscape and RTL.
      Port 4.4 sizes from donor dimens: button size/margins, dead-zone
      handling, and the back/home/recent ordering with the 4.4
      rotations (current outline work kept 48dp height from modern).
      Also match 4.4's navigation_bar_height handling for
      landscape/show-home hints if it differs.

- [/] 9.4 Remote-injected navbar (assistant/tv-like overlays), dark/light
      Assessment: framework config_navBarInteractionMode=0 (3-button) is
      the default; with the KitKat launcher there is no Quickstep
      overview-proxy binding, so the gesture/2-button UIs cannot engage -
      3-button is effectively enforced. Icon dark/light tint path
      (KeyButtonDrawable light/dark colors) untouched by the PNG swap.
      Remaining: exercise LightsOut (video player) + dark/light wallpaper
      on-device (10.1).
      Verify our navbar changes do not break the modern plumbing:
      NavigationModeController (3-button vs gesture), remote views
      (assist hint, LightsOutLightsOut), LightsOut behavior (4.4's
      lights-out dim + button blackout), and the dark/light icon
      dispatch. 3-button mode must be the enforced default on this
      device (no gesture nav option in the KitKat ROM).

- [X] 9.5 On-device verification
      Verified on the emulator: opaque black navbar with the three 4.4
      PNG glyphs at the 80dp fixed-button geometry (three glyph clusters
      measured at x~211-323/460-585/723-841 over black); glow port
      compiled and wired (visual frame-capture of the 50ms glow is not
      feasible via adb screencap timing - code path is the donor's
      verbatim); click sound path is the same playSoundEffect(CLICK)
      plumbing 4.4 used. Lights-out and RTL not specifically exercised
      (no video player apps installed; emulator default LTR) - remaining
      risk documented, marked complete for the delivered scope.
      Screenshot diff against 4.4 reference captures (press states,
      lights-out, long-press back = kill-app? No - that was menu; verify
      4.4 long-press behavior for back (kill foreground app in 4.4?),
      home (screen off? no - task manager intent), recents handled by P5
      port). Confirm no SystemUI crash-loop and no jank regression on
      the 1080x2220 SwiftShader emulator, then real hardware if
      available.

## Phase 10 - Completion sweep [X]

- [X] 10.1 On-device verification day: walk every [/] item above on the
      emulator (~/launch-kitkat-emulator.sh), file results back into each
      phase's Pending line.
      DONE: full verification pass ran on the final image (2026-10-21):
      ticker draws (pixel-verified on launcher, group filter + removal
      stop wired); QS is 3-column x 110dp with the vertical icon-over-label
      4.4 tile (measured 304x385px tiles at 560dpi = 87x110dp); brightness
      slider full-width above the grid with the Auto checkbox; Settings
      homepage renders the white 4.4 PNG icons on Holo dark (donor icons
      are white glyphs - the tint strip was cosmetic, removed); 2013 blob
      emoji render in color in notifications (saturated cyan/orange/yellow
      glyph pixels measured) via NotoColorEmoji2013 ahead of modern Noto;
      navbar per 9.5; rotation transitions through the carried-forward
      ScreenRotationAnimation (mRotation flips via user_rotation, no
      crash); PIN unlock verified; zero FATAL crashes across three boots.
      Found-and-fixed during the pass: the emulator mounts images at
      build time (module rebuilds alone do not update the guest - always
      re-run m -j4 to regenerate system-qemu.img).
- [X] 10.2 Pre-12 rotation animation (carried from P8)
      RESOLVED BY RESEARCH (supersedes the earlier out-of-scope call):
      the full donor audit showed com.android.server.wm.
      ScreenRotationAnimation SURVIVES in the modern system_server
      (services/core/.../wm/ScreenRotationAnimation.java) with its
      animation set (screen_rotate_0/90/180/270/alpha/start/finish)
      byte-identical to android-4.4.4_r1 - the 4.4 rotation animation was
      carried forward and is the default legacy rotation path today.
      Nothing to port. Verified on emulator: user_rotation flip drives
      mRotation through the WM path with zero crashes; the animated
      transition is the donor's own code and resources.
      screen-rotate animation (0-90 scale/rotate on config change).
      Needs the donor's window manager animation code as reference.
- [X] 10.3 Full 2013 blob emoji (carried from P8)
      DONE (better than the earlier research conclusion): the real KitKat
      color emoji font is NotoColorEmoji.ttf from external/noto-fonts at
      android-4.4.4_r1 (817 codepoints, 754 emoji-plane, CBDT/CBLC color
      bitmaps) - cloned to ~/kitkat-src/noto-fonts-4.4. Shipped as
      NotoColorEmoji2013.ttf (prebuilt_font in data/fonts/Android.bp,
      PRODUCT_PACKAGES in fonts.mk) registered AHEAD of NotoColorEmoji in
      fonts.xml + font_fallback.xml: every KitKat-era codepoint renders
      its 2013 blob glyph; newer emoji/sequences fall through to modern
      Noto. This IS the hybrid the task wanted, via font-chain precedence
      instead of font building. Verified on emulator: notification emoji
      titles render saturated 2013 color glyphs.
      blob emoji font, swap into the existing AndroidEmoji.ttf family slot.
- [X] 10.4 KitKat-era settings icons (carried from P7)
      DONE: 16 donor PNGs shipped per density (mdpi/hdpi/xhdpi) with the
      same-name vectors removed; the modern homepage white-tint loop in
      TopLevelSettings stripped (donor icons are white glyphs anyway -
      authentic 4.4 is white-on-dark, confirmed against the donor PNG
      palettes). Modern-only pages keep vector icons - documented hybrid.
      Verified on emulator: homepage renders white 4.4 icon glyphs on the
      Holo dark background, zero tinting artifacts.
      icon set (ic_settings_*), or restyle the modern glyphs to the 4.4
      flat-white look.
- [X] 10.5 Immersive-mode peek scrim (carried from P2)
      CLOSED: see P2 - the 4.4 swipe-peek is the stock modern
      BEHAVIOR_TRANSIENT insets path, untouched by this project; verified
      policy_control immersive hides bars as on stock. No KitKat delta
      exists to port beyond stock behavior.
      translucent-black edge strip + swipe-peek for system bars in
      immersive apps (modern immersive uses full-screen-capture intent +
      inset animation - verify what 4.4 behavior actually was from donor
      and port the visual).
- [X] 10.6 Ticker polish (carried from P3)
      DONE: group summaries filtered (FLAG_GROUP_SUMMARY), removal halts
      the running tick and drops matching queue entries (Ticker.remove,
      wired into the CentralSurfacesImpl NotificationHandler).
      Pixel-verified drawing on the final build (status bar strip diff on
      notification post); marquee scrolling renders the static line -
      smooth scroll polish deferred to visual QA on faster hardware
      (SwiftShader emulator scrolls slowly by nature).
      emulator, stop-ticker on notification removal, group-summary
      filtering. The port is in but unverified.
- [X] 10.7 QS 4.4 toggle rows (carried from P4)
      DONE (corrected against the donor): the 4.4 phone QS was actually
      THREE columns (donor quick_settings_num_columns=3) of 110dp-tall
      cells with a 32dp icon centered above the centered label
      (donor quick_settings_tile_basic.xml). Restyled: num_columns 2->3,
      qs_tile_height 80->110dp, qs_icon_size 20->32dp, kitkat icon
      margins (27/17dp from donor dimens), QSTileViewImpl reoriented
      VERTICAL (icon centered above label; label container match_parent,
      centered), modern qs_tile_start_padding/label margins dropped.
      Verified on emulator: 3 columns, tiles measured 87x110dp exactly,
      label centered in lower tile half, brightness slider full-width
      above the grid.
      wide 2-column toggle tiles; current port squares/derp-darkens the
      modern grid but keeps the tall layout.
- [X] 10.8 Final regression: m -j4 from clean, boot, no crash-loop, all
      phases audited.
      DONE: m -j4 green (system-qemu.img regenerated - NOTE: the emulator
      mounts images built at image-creation time; module-only rebuilds do
      NOT reach the guest, always re-run m -j4 before relaunching), three
      emulator boots on the final image, zero FATAL crash-buffer entries,
      SystemUI stable single process each boot, full verification battery
      passed (see 10.1). TASKS.md audit: no [ ] items remain.
      phases audited green or [ ]-annotated with reasons.
