# TODO — LAN device scanner for ScrcpyForAndroid

Feature: Scan the local network (Wi-Fi + phone hotspot) for devices listening on
the ADB port (5555) and present them in a selectable dropdown on the main screen.
On selection, fill the host field so the existing `adb connect` flow takes over.

## Status

| Step | State |
|------|-------|
| Fork/build/architecture analysis | Done |
| Design decision: threading (Java executors vs Kotlin coroutines) | In progress |
| Design approval | Pending |
| Design doc + this TODO committed | Pending |
| `LanScanner` — interface enumeration, candidate generation, parallel probe | Pending |
| `AdbHelper` — parse `adb devices`, merge/dedupe | Pending |
| UI — scan button + grouped dropdown (activity_main.xml, MainActivity.java) | Pending |
| Unit tests (candidate gen, adb output parse) | Pending |
| Verify: `./gradlew :app:assembleDebug`, unit tests, lint | Pending |
| Commit + push | Pending |

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