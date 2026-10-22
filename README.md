# KitKat UI Mod - Build & Test Guide

A from-scratch guide to building and testing the KitKat UI mod for
LineageOS 21 (Android 14) on the x86_64 emulator, on Ubuntu 26.

---

## 1. How the mod is put together

There is no device tree and no installer. The mod is entirely:

- A base tree: LineageOS 21.0 (`lineage-21.0` branch).
- One local manifest: `.repo/local_manifests/kitkat_ui_mods.xml`.
  It swaps nine Lineage projects for pinned forks hosted at
  `github.com/EHowardHill/*`:

  | Path                        | Fork                                   | Branch       | Carries                                    |
  |-----------------------------|----------------------------------------|--------------|--------------------------------------------|
  | frameworks/base             | android_frameworks_base                | lineage-21.0 | Holo framework theme, Roboto 2013, UI sounds, SystemUI mods (status bar, navbar, recents, keyguard, volume, power menu) |
  | packages/apps/Settings      | android_packages_apps_Settings         | lineage-21.0 | Settings Holo reskin                       |
  | packages/apps/Trebuchet     | Launcher3-Modern                       | main         | KitKat Launcher3 port                      |
  | packages/apps/ExactCalculator | android_packages_apps_ExactCalculator | lineage-21.0 | 4.4 Calculator                            |
  | packages/apps/Recorder      | android_packages_apps_Recorder         | lineage-21.0 | 4.4 SoundRecorder                          |
  | packages/inputmethods/LatinIME | android_packages_inputmethods_LatinIME | lineage-21.0 | Holo keyboard theme (forced default)    |
  | packages/apps/Eleven        | android_packages_apps_Eleven           | lineage-21.0 | 4.4 Music (com.android.music)             |
  | packages/apps/DeskClock    | android_packages_apps_DeskClock         | lineage-21.0 | 4.4 DeskClock                              |
  | packages/apps/Etar         | android_packages_apps_Etar              | lineage-21.0 | 4.4 Calendar                               |

- The target device is the AOSP x86_64 emulator product
  (`lineage_sdk_phone_x86_64`, device `emu64x`), which already lives
  in the LineageOS tree under `device/generic` - no device fork
  needed, and the result runs on the Android emulator.

A full `repo sync` fetches the pinned forks like any other project,
so after the manifest is installed there is nothing special left to
do: sync, lunch, build, boot.

---

## 2. Host requirements

### Hardware (reference machine the mod is built on)

- 8+ cores / 16+ threads (reference: 24 threads)
- 32 GB RAM (16 GB works if you add ~16 GB swap; soong/Kotlin and
  the emulator like memory)
- 400-500 GB free on a fast SSD (source is roughly 200 GB, the
  first build adds 100-250 GB of output)
- Any GPU; software rendering works (see the emulator flags below)

### Ubuntu 26 host setup

```bash
sudo apt update
sudo apt install bc bison build-essential ccache curl flex \
  g++-multilib gcc-multilib git gnupg gperf imagemagick \
  liblz4-tool libncurses-dev libssl-dev libtinfo-dev \
  libxml2 libxml2-utils lzop pngcrush rsync squashfs-tools \
  xsltproc zip zlib1g-dev repo
```

Notes for Ubuntu 26, since the LineageOS 21 host-support matrix
predates it:

- The list above is the LineageOS 21 package set with the legacy
  names (`libncurses5-dev`, `libtinfo5-dev`, ...) already swapped
  for the modern ones. If some tool still wants a 32-bit ncurses,
  enable multilib: `sudo dpkg --add-architecture i386` and install
  the `lib32*` set (`lib32z1`, and `lib32ncurses-dev` /
  `lib32readline-dev` if your archive carries them).
- `libsdl1.2-dev`, `libwxgtk3.0-gtk3-dev` and `schedtool` appear in
  older guides; Android 14's soong build does not require them.
  If a host tool of yours wants wxWidgets, the modern package is
  `libwxgtk3.2-dev`.
- `repo` comes from apt on any modern Ubuntu. If you prefer the
  upstream launcher:

  ```bash
  mkdir -p ~/bin
  curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
  chmod a+x ~/bin/repo   # make sure ~/bin is on your PATH
  ```

- The tree uses ~200k+ inodes and paths longer than some filesystems
  allow - use plain ext4. Do NOT build from NTFS/exFAT mounts or
  case-insensitive filesystems.
- Git identity is required before `repo init`:

  ```bash
  git config --global user.name  "Your Name"
  git config --global user.email "you@example.com"
  ```

- All remotes are public https; no GitHub credentials are needed to
  sync or build. You only need credentials to push to the forks.

---

## 3. Step 1 - Initialize the tree

```bash
mkdir -p ~/android/lineage
cd ~/android/lineage
repo init -u https://github.com/LineageOS/android.git -b lineage-21.0
```

(Add `--git-depth=1` if you want to save disk space at the cost of
shallow history - the forks carry their own tags either way.)

## 4. Step 2 - Install the local manifest

Create `.repo/local_manifests/kitkat_ui_mods.xml` with exactly this
content (this one file is the entire mod declaration):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  KitKat UI Project

  All KitKat changes live in two existing forks:

  frameworks/base (android_frameworks_base):
    Phases 1-6 and 8, all in-tree: framework Holo theme (core/res),
    KitKat 2013 Roboto (data/fonts/font_fallback.xml + TTFs), SystemUI
    (status bar, navbar, volume, power menu, recents, keyguard; the
    notification shade and quick settings stay STOCK LineageOS - rolled
    back 2026-10-22, see frameworks/base TASKS.md Phase 21), UI sounds
    (data/sounds), and code changes (clock position, lockscreen
    behavior, monet pinning; the shade/QS restructure was rolled back).
    Track phases with KITKAT-P<n>: commit prefixes and kitkat/p<n> tags.

  packages/apps/Settings (android_packages_apps_Settings):
    Phase 7: Settings reskin (layouts, icons, Holo styling).

  Target device: x86_64 dev testing device (Android emulator; no
  device tree fork needed). Boot animation stays untouched.
-->
<manifest>
  <remote name="my_github" fetch="https://github.com/EHowardHill" />

  <remove-project name="LineageOS/android_frameworks_base" />
  <project path="frameworks/base" name="android_frameworks_base" remote="my_github" revision="lineage-21.0" />

  <remove-project name="LineageOS/android_packages_apps_Settings" />
  <project path="packages/apps/Settings" name="android_packages_apps_Settings" remote="my_github" revision="lineage-21.0" />

  <!-- Launcher replacement: KitKat 4.4 Launcher3 port
       (github.com/EHowardHill/Launcher3-Modern) taking over the
       packages/apps/Trebuchet slot. Registers as TrebuchetQuickStep, the
       module name vendor/lineage expects. Recents with this launcher is
       SystemUI's job (see TASKS.md, Phase 5). -->
  <remove-project name="LineageOS/android_packages_apps_Trebuchet" />
  <project path="packages/apps/Trebuchet" name="Launcher3-Modern" remote="my_github" revision="main" />

  <!-- Phase 16 Tier 1 ports: android-4.4.4_r1 Calculator and SoundRecorder
       installed verbatim into the ExactCalculator / Recorder trees with
       modernized build layers (soong Android.bp keeping the module names,
       vendored support-v4 viewpager, runtime permissions, scoped storage).
       See frameworks/base/TASKS.md Phase 16. -->
  <remove-project name="LineageOS/android_packages_apps_ExactCalculator" />
  <project path="packages/apps/ExactCalculator" name="android_packages_apps_ExactCalculator" remote="my_github" revision="lineage-21.0" />

  <remove-project name="LineageOS/android_packages_apps_Recorder" />
  <project path="packages/apps/Recorder" name="android_packages_apps_Recorder" remote="my_github" revision="lineage-21.0" />

  <!-- Phase 13: LatinIME carrying the KitKat Holo keyboard theme
       (KeyboardTheme.KitKat forced default; see frameworks/base/TASKS.md
       P13, and 13.4 for the key-text-size + flat-color fixes). -->
  <remove-project name="LineageOS/android_packages_inputmethods_LatinIME" />
  <project path="packages/inputmethods/LatinIME" name="android_packages_inputmethods_LatinIME" remote="my_github" revision="lineage-21.0" />

  <!-- Phase 16 Tier 1: android-4.4.4_r1 Music (com.android.music) in the
       Eleven slot, module name kept (see frameworks/base/TASKS.md P16). -->
  <remove-project name="LineageOS/android_packages_apps_Eleven" />
  <project path="packages/apps/Eleven" name="android_packages_apps_Eleven" remote="my_github" revision="lineage-21.0" />

  <!-- Phase 16 Tier 1 (final): android-4.4.4_r1 DeskClock (com.android.deskclock,
       datetimepicker vendored in-tree) in the DeskClock slot, module name kept. -->
  <remove-project name="LineageOS/android_packages_apps_DeskClock" />
  <project path="packages/apps/DeskClock" name="android_packages_apps_DeskClock" remote="my_github" revision="lineage-21.0" />

  <!-- Phase 16 Tier 2: android-4.4.4_r1 Calendar (com.android.calendar,
       chips/colorpicker/datetimepicker/timezonepicker/common/calendarcommon2
       vendored in-tree) in the Etar slot, module name kept. -->
  <remove-project name="LineageOS/android_packages_apps_Etar" />
  <project path="packages/apps/Etar" name="android_packages_apps_Etar" remote="my_github" revision="lineage-21.0" />
</manifest>
```

This is byte-for-byte the manifest on the build machine. The
`<remove-project>` lines swap the Lineage checkout for the fork; the
fork branch `lineage-21.0` carries every mod commit. Keep a copy of
this file with your dotfiles - `local_manifests/` is not under
version control, so the manifest is the one thing a fresh machine
cannot re-derive from the tree.

## 5. Step 3 - Sync

```bash
cd ~/android/lineage
repo sync -c -j8
```

Expect a few hours and ~200 GB the first time. `-c` syncs only the
needed branches. If a project fails, re-run the command - repo
resumes.

## 6. Step 4 - Build

```bash
cd ~/android/lineage
source build/envsetup.sh
lunch lineage_sdk_phone_x86_64-ap2a-userdebug
m -j8
```

- The lunch combo is `<product>-<release>-<variant>`. If
  `Invalid lunch combo` appears (release names drift as the branch
  moves), run bare `lunch` and pick the combo starting with
  `lineage_sdk_phone_x86_64`; on the mod snapshot the exact combo is
  `lineage_sdk_phone_x86_64-ap2a-userdebug`.
- First build: 1-3 hours on an 8-core/16-thread class machine
  (the reference machine builds with `m -j8` on 24 threads / 32 GB).
  Later builds are incremental and usually take minutes.
- If the build gets `Killed` (exit 137), you ran out of RAM: lower
  the job count (`m -j4`) or add swap.
- Optional speed-up on rebuilds: `export USE_CCACHE=1` with
  `ccache -M 50G` (soong's ccache support works but is considered
  experimental; it is off by default).

When it finishes, the images are in `out/target/product/emu64x/`
(`super.img`, `system.img`, `vendor.img`, `ramdisk-qemu.img`,
`kernel-ranchu`, ...).

## 7. Step 5 - Boot the emulator

After lunch, the tree's emulator binary is on PATH and picks the
built images up automatically - no AVD, no SDK download:

```bash
cd ~/android/lineage
source build/envsetup.sh
lunch lineage_sdk_phone_x86_64-ap2a-userdebug

# windowed, software rendering (works on any host, incl. VNC/X2Go)
emulator -gpu swiftshader_indirect -no-snapshot -no-audio \
  -skin 1080x2220 -dpi-device 440
```

That skin/density is the canonical QA profile (Pixel 3a class, 440
dpi). Variants:

- Headless/CI: add `-no-window` and inspect with screencaps.
- Stuck after experiments: add `-wipe-data`.
- The GPU flag matters: on SwiftShader software rendering use
  `-gpu swiftshader_indirect`; on a real GPU the default is fine.
- Audio: `-no-audio` is the norm for UI batteries; drop the flag
  (and accept the slower, flakier emulated HAL) to test ringtones,
  alarms and playback sounds.

First boot takes a few minutes; watch `adb wait-for-device` and then
the boot completed property:

```bash
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 2; done
echo booted
```

Two setup conveniences on first boot:

```bash
# skip the Setup Wizard (the pm-enable path is blocked for shell,
# so set the provisioned bits directly)
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1

# show first-crash dialogs during QA (off by default)
adb shell settings put global show_first_crash_dialog 1
```

## 8. Step 6 - Verify the mod

Everything below was implemented and device-verified against the
donor sources; the emulator checks are quick eyeballs.

### Must look/act KitKat

- **Framework theme**: dialogs, alerts, menus, toasts, buttons,
  switches, spinners, text fields render Holo (dark by default,
  square corners, Holo blue #33b5e5 accents). Roboto everywhere is
  the 2013 cut (light/condensed variants included).
- **Status bar**: opaque black, 25 dp tall, right-hand clock, white
  KitKat icon set (arc-fan wifi, triangle cellular, squared battery
  outline).
- **Ticker**: with the shade closed, post a notification and watch
  the 4.4 ticker scroll title/text through the status bar:

  ```bash
  adb shell cmd notification post -t "KitKat" kitkat "Hello from 2013"
  ```

- **Navigation bar**: outlined triangle/circle/square glyphs, glow +
  brighten feedback on press.
- **Recents**: the overview/recents button opens SystemUI's 4.4
  vertical card list (the Launcher3 port has no Quickstep).
- **Keyguard**: KitKat clock typeface and PIN pad restyle, Holo blue
  accents.
- **Volume panel**: dark slim rail with square ends, Holo blue fill.
- **Power menu**: flat dark panel, single-column rows, Holo blue
  press feedback.
- **Screenshot overlay**: dark 4.4-style action chips.
- **Keyboard**: dark Holo LatinIME with visible key letters,
  suggestion strip; the KitKat theme is the forced default.
- **Apps**: Calculator, SoundRecorder, Music, DeskClock, Calendar
  all render the android-4.4.4 UI; Settings is dark Holo end to
  end; the launcher is the KitKat Launcher3.
- **Sounds**: lock/unlock, keypress, camera click etc. are the 4.4
  effects.
- **Easter egg**: Settings > About phone > tap the Android version.

### Must look STOCK (the Phase 21 rollback - this is a feature)

- Pull the shade down: the notification panel and quick settings
  must be ordinary modern LineageOS - monet colors, rounded tiles,
  the stock header/footer. No KitKat gradient, no 48 dp header row,
  no clear-all X, no carrier label, no drag handle, no donor tile
  set, no brightness tile/dialog. Notification rows use stock
  radius and stock 14 sp titles.

### ADB battery tips (how the project does QA)

```bash
adb exec-out screencap -p > /tmp/screen.png   # pixel-check chrome
adb shell dumpsys activity activities | head  # what's on screen
adb logcat -b crash                          # crash log
```

Drive tests with screencaps, not uiautomator - its view dumps go
stale on this emulator. Software-rendered screencaps lag ~1-2 s, so
sub-second effects (ticker start, navbar glow, overscroll) need
patience or a real-GPU host.

### Optional: widget playground

The fork ships a dev tool APK exercising every Holo widget:

```bash
m KitKatWidgetPlayground
find out -name "KitKatWidgetPlayground.apk" -print
adb install -r <path-from-above>
```

## 9. Step 7 - Development loop

To change mod code and retest:

- SystemUI-only change (status bar, navbar, recents, volume, power
  menu):

  ```bash
  m SystemUI        # ~4 minutes incremental on the reference machine
  ```

  then restart the emulator (fastest reliable refresh), or, for the
  brave: `emulator -writable-system`, `adb root && adb remount`, push
  the apk over `/system_ext/priv-app/SystemUI/SystemUI.apk`, then
  `adb shell stop && adb shell start`.

- Framework resources (`frameworks/base/core/res`): `m framework-res`
  plus a reboot; `m SystemUI` also relinks against it.
- Anything else: plain incremental `m -j8` picks up whatever moved.
- Kernel/ramdisk-level changes: full `m` then a fresh emulator boot
  with `-wipe-data` when in doubt.

To push mod changes upstream (maintainers), the project uses a
helper script that lives on the original build machine
(`~/push-kitkat-changes.sh` - copy it across, or replicate what it
does: in each fork, `git pull --rebase --autostash`, commit, then
`git push my_github HEAD:refs/heads/<branch>`). It covers
frameworks/base (`kitkat-mods` -> fork `lineage-21.0`), Settings and
Trebuchet. Tags: `kitkat/p<n>` per phase, pushed with
`git push my_github --tags` in frameworks/base.

## 10. Step 8 - Pull future mod updates

The forks move forward on `lineage-21.0` (and `main` for
Launcher3-Modern). To pick up new mod commits:

```bash
cd ~/android/lineage
repo sync -c -j8 frameworks/base packages/apps/Settings \
  packages/apps/Trebuchet packages/apps/ExactCalculator \
  packages/apps/Recorder packages/inputmethods/LatinIME \
  packages/apps/Eleven packages/apps/DeskClock packages/apps/Etar
m -j8
```

Nothing else changes - the manifest pins branches, not commits, so
`repo sync` is the whole update mechanism. To pin an exact known
state instead, edit the manifest's `revision=` attributes to a tag
(`kitkat/p21` is the shade/QS rollback snapshot).

## 11. Troubleshooting

| Symptom | Fix |
|---|---|
| `Invalid lunch combo` | Run `lunch` alone and list combos; the release segment (`ap2a` today) drifts over time. Use the `lineage_sdk_phone_x86_64-*` product. |
| Build `Killed` / exit 137 | Out of RAM. Use `m -j4`, close the emulator during builds, add swap. |
| Missing host lib (ncurses/wx) | Install the modern package name (see section 2 notes); enable i386 multilib for 32-bit libs. |
| Emulator window black / no display | Use `-gpu swiftshader_indirect`, and a real X display (`DISPLAY=:0`) or `-no-window`. |
| Emulator boots stock-looking image | You launched an SDK AVD, not the build. Run `emulator` from a shell that has lunched (it reads `$ANDROID_PRODUCT_OUT`). |
| Shade/QS look KitKat | You are on a pre-P21 snapshot. Sync the forks to `kitkat/p21` or later. |
| Setup Wizard keeps running | `adb shell settings put global device_provisioned 1` and `... put secure user_setup_complete 1`, then reboot. |
| Crash dialogs never appear | `adb shell settings put global show_first_crash_dialog 1`. |
| uiautomator dumps stale/empty | Known emulator quirk - drive with `adb exec-out screencap`. |
| Media playback errors in Music/DeskClock | That is the emulated audio HAL. Test with `-no-audio` removed and, ideally, a real GPU host. |
| `repo sync` SSL/proxy failures | Standard git/repo proxy configuration; retry - resume is safe. |

## 12. Where things live (map for the curious)

- `~/android/lineage/.repo/local_manifests/kitkat_ui_mods.xml` - the
  mod declaration (this file is NOT under version control by
  default; keep a copy).
- `frameworks/base/TASKS.md` - the complete engineering log: phases,
  donor comparisons, every accept-and-document ruling, and the
  Phase 21 rollback record. Read this to understand any pixel.
- Forks: `github.com/EHowardHill/android_frameworks_base`
  (KITKAT-P* commit prefixes, kitkat/p* tags), plus the eight app
  forks listed in section 1.
- The 4.4 donor reference trees (optional, not needed to build) are
  shallow clones of tag `android-4.4.4_r1` under `~/kitkat-src/` on
  the original build machine.
- `~/push-kitkat-changes.sh` - maintainer push script.
