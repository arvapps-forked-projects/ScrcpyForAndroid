# TODO — LAN device scanner for ScrcpyForAndroid

Feature: Scan the local network (Wi-Fi + phone hotspot) for devices listening on
the ADB port (5555) and present them in a selectable dropdown on the main screen.
On selection, fill the host field so the existing `adb connect` flow takes over.

## Status

| Step | State |
|------|-------|
| Fork/build/architecture analysis | Done |
| Design decision: threading (Java executors vs Kotlin coroutines) | Done — Java executors |
| Design approval | Done — 2026-08-29 |
| Design doc + this TODO committed | Done — aed3481 |
| `LanScanner` — interface enumeration, candidate generation, parallel probe | Done |
| `AdbHelper` — parse `adb devices`, merge/dedupe | Done |
| UI — scan button + grouped dropdown (activity_main.xml, MainActivity.java) | Done |
| Unit tests (candidate gen, adb output parse, probe) | Done — 11/11 pass |
| Verify: `assembleDebug`, unit tests, lint | Done — all green (JDK 17) |
| Commit + push | Done |

## Session wrap-up (2026-08-29) — pending / in progress

Current status at end of session 2026-08-29. Code is committed and pushed
(`d278f8e` feature, `4dbf6e1` SDK migration); implementation work is PAUSED.
The open items below are verification and housekeeping, not new code:

| # | Item | State | Owner |
|---|------|-------|-------|
| 1 | Confirm the "old version of Android" warning is gone on the Vivo V2503i after the v1.5.2 (targetSdk 34) install | **Pending** — user to check on device | User |
| 2 | LAN scanner on-device walkthrough: scan network → pick device → Start → mirror connects (see "Manual device test" below) | **Pending** — feature verified only by unit tests so far | User |
| 3 | Back up `app/scrcpy.jks` (sole signing key, 10,000-day validity, gitignored) to a safe location outside this repo | **Pending** — critical: losing it prevents future updates in place | User |
| 4 | If item 1 shows the warning persists: escalate to targetSdk/compileSdk 35/36 (requires AGP 8.6+/8.9+, Gradle wrapper bump, Android 15 edge-to-edge handling) | **Blocked** on item 1 | Agent |
| 5 | Wi-Fi adb reliability: wedged shell once behind an install prompt, one truncated push. Switch to USB pairing for installs if it recurs | **Pending** — maintenance | Both |

Everything in the status table above (design, implementation, tests, release,
install) is Done and committed. The "Deferred" feature ideas below remain
deferred by design — revisit in a future session.

## Android version migration (2026-08-29)

Installer on the Vivo V2503i (Android 16) flagged the app as "built for an old
Android version" while targetSdk was 31. Fixed by migrating the build:

- AGP 8.0.0 -> 8.2.2 (`build.gradle`); compileSdk/targetSdk 31 -> 34 in `app` and
  `server` modules; app version 1.5.1 (code 14) -> 1.5.2 (code 15).
- No manifest changes needed: the app has no foreground services, no
  notifications, and all components already carry `android:exported` where
  required (targetSdk 31+ rule).
- Side benefit: AGP 8.2's R8/D8 handles javac-21 class files, so the JDK-17
  build requirement from the D8 8.0 crash is gone in principle (still used, it
  is the supported combination).
- Rebuilt, lint+unit tests green (11/11), signature verified, released as
  `release/ScrcpyForAndroid-1.5.2.apk`, installed on device (targetSdk 34
  confirmed via `dumpsys package`).

## Verification (2026-08-29)

- `./gradlew :app:assembleDebug` — SUCCESS. APK ~10.2 MB (classes*.dex + 4 ABIs
  of libadb.so + asset scrcpy-server.jar).
- `./gradlew :app:testScrcpyDebugUnitTest` — 11 tests, 0 failures
  (`LanScannerTest` 8, `AdbHelperTest` 3).
- `./gradlew :app:lintScrcpyDebug` — 0 errors. (Fixed during verification:
  `ConcurrentHashMap.newKeySet()` → API-21-safe `Collections.newSetFromMap`;
  added ja/zh translations for all 11 scan strings.)

### Build environment note (important)

This machine's default JDKs are 21/25/26. The repo pins AGP 8.0.0 + Gradle 8.6,
whose bundled D8 (R8 8.0.35) crashes with
`NullPointerException: Cannot invoke "String.length()"...` when dexing classes
compiled by **javac 21+** (reproduced standalone; D8 8.2.2 and javac-17-built
classes both work). Build with the vended JDK 17 instead:

```
JAVA_HOME=/home/arunvariyath/jdk17 PATH=/home/arunvariyath/jdk17/bin:$PATH ./gradlew :app:assembleDebug
```

No repository file needed changing (local `gradle-wrapper`, `local.properties`,
and the JDK 17 install are all outside the repo / gitignored). Alternative if
this ever bites CI: bump AGP to 8.2+ (its R8 handles javac-21 class files).

## Decisions (user-confirmed)

- GUI dropdown picker, not CLI.
- Probe local IPv4 interfaces (Wi-Fi + tether), port 5555, parallel TCP.
- Merge with `adb devices` output, dedupe.
- On selection → `adb connect ip:5555` (existing flow via Start button).
- Network probing + adb off the UI thread.
- Permissions: INTERNET + ACCESS_WIFI_STATE (already present, no manifest change).
- v1: grouped dropdown, IP:PORT + friendly name, empty/first-run states.

## Deferred

- Bookmarking remembered devices.
- DataStore persistence of recent devices.
- Probing beyond 5555.
- Device model names in picker.
- Latency/signal per candidate.
- Android 11+ wireless-debugging pairing (QR/code) flow.

## Manual device test (for the user)

1. Install `app/build/outputs/apk/scrcpy/debug/scrcpy-scrcpy-debug.apk` on the
   controller phone.
2. Target phone: enable wireless debugging / network ADB, run
   `adb tcpip 5555` once over USB, ensure same Wi-Fi (or connected to the
   controller's hotspot).
3. Open app → "Scan network for devices" → pick the device from the dropdown →
   Start. Verify the mirror connects.