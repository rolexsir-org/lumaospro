# Performance and stability device validation

This checklist is **not a record of a passed run**. Use a fresh Gradle build on a
machine with JDK 17, Android SDK 36 and the project's pinned dependencies:

```
./build.sh testDebugUnitTest lintDebug assembleDebug assembleRelease
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release is unsigned unless the owner's existing private signing configuration is
available. Never include keys or local properties in reports. The debug ID is
`com.lumaos.pro.launcher.debug`; the release ID is `com.lumaos.pro.launcher`.
No instrumentation sources are currently in this repository; do not mistake JVM
Robolectric tests for device tests.

## Repeatable stress run

On at least one representative low-resource phone and the reported MIUI device,
record model, Android version, build ID, refresh rate and number of installed apps.
Start with a backup of important launcher layouts. Save a logcat capture (after
removing private notification content and other personal information):

```
adb logcat -c
adb logcat -v threadtime > luma-stress-logcat.txt
```

1. Launch the debug app normally and as HOME; verify onboarding, dock, widget
   placements, labels, and launching apps. Rotate or change configuration, then
   background and restore it; verify saved settings and widget bindings.
2. Open/close the drawer 20 times, rapidly scroll both ways and search. Confirm
   icons never land on a different row and remain cached on reopening. While the
   launcher is foregrounded and again while it is stopped, install, update and
   uninstall test apps and an active icon pack; check icons, labels and launch
   targets. Repeat with a work profile if available, including locked/paused.
3. Switch themes and icon styles, visit Settings and return 20 times; check
   layout, persisted preferences and the absence of duplicated registrations.
4. For notification listener access, turn it on, off, and on from Android settings;
   also return without changing it. Open the shade on each return. For context
   notification permission (API 33+), test grant, deny and external revocation;
   verify alert preference behavior. Test Do Not Disturb policy access, widget
   bind/configuration and HOME-role choice similarly, including denial and rapid
   return. Never repeatedly prompt without a user action.
5. From another app or a test notification generator, post a burst including
   repeated updates/removals of the same key, grouped and ongoing notifications.
   Check ordering, no resurrected cards after removal/disconnection, and
   open/dismiss/dismiss-all/reply/other real actions. Repeat while toggling
   listener access and opening/closing the shade.
6. Launch and exit several apps, background/restore the launcher, then repeat
   steps 2–5 for at least 30 minutes. Record any freeze, incorrect state or crash
   and the time and exact steps.

Capture `adb logcat -d` immediately after a failure; look for `FATAL EXCEPTION`,
`AndroidRuntime`, `ANR in`, `Fatal signal`, `OutOfMemoryError`, and binder or
permission failures. A crash dialog alone does not identify the cause. Collect
`adb shell dumpsys meminfo com.lumaos.pro.launcher.debug` after warm-up and after
repeated cycles; compare retained heap and PSS in context, not one-time cache
warming. Use Android Studio Profiler or Perfetto and `adb shell dumpsys gfxinfo
com.lumaos.pro.launcher.debug framestats` for before/after startup, CPU,
allocations and frame timing. Record device refresh rate and workload; 16.7 ms
is a 60 Hz budget, not a measured result. Do not claim these checks passed
without retaining actual measurements and logs.
