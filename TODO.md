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