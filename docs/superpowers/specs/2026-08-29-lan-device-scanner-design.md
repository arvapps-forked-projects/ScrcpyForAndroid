# LAN Device Scanner — Design

Date: 2026-08-29
Status: Approved (user) — implementation follows

## Goal

Scan the local network for Android devices listening on the ADB port (5555) —
covering both devices on the same Wi-Fi and devices tethered to this phone's
hotspot — and present them in a selectable dropdown so the user can pick one to
connect to. Selection fills the existing host `EditText`; all connection logic
(`adb connect`, push scrcpy-server, `adb forward`) is untouched and stays in the
existing `SendCommands` flow behind the Start button.

## Constraints / decisions (user-confirmed)

- GUI dropdown picker (ListPopupWindow), not CLI.
- Probe local IPv4 interfaces (Wi-Fi + tether), port 5555, fast parallel TCP connects.
- Merge with `adb devices` output; dedupe.
- All probing + adb commands run off the UI thread.
- Threading: **Java ExecutorService** (project is 100% Java; no Kotlin/coroutines introduced).
- Permissions: `INTERNET` + `ACCESS_WIFI_STATE` already in
  `app/src/main/AndroidManifest.xml:5-6` — both normal, auto-granted. **No manifest change.**
- v1 scope: grouped dropdown, `IP:PORT` + friendly name, good empty/first-run states.

## Components

### 1. `model/DiscoveredDevice.java` (new)
POJO: `host`, `port`, `name` (nullable friendly name), `interfaceLabel`,
`adbState` (`device`/`offline`/`unauthorized`/null), `source`
(`ADB_LIST` | `SCAN`). `getAddress()` → `host:port`. Equality by address.

### 2. `utils/LanScanner.java` (new — pure Java, JVM-testable)
- `getActiveRanges()` → `List<NetRange>`:
  `NetworkInterface.getNetworkInterfaces()` filtered to up / non-loopback /
  non-virtual interfaces with an IPv4 `InterfaceAddress` and prefix 1–30;
  known-virtual/point-to-point names skipped (`lo`, `dummy`, `tun`, `tap`,
  `p2p`, `rmnet`, `ccmni`, `sit`, `vpn`, `ppp`, `bond`). Labels: Wi-Fi
  (`wlan*`), Hotspot (`ap*`), USB tether/Ethernet (`rndis*`/`usb*`/`eth*`),
  Bluetooth tether (`bt*`), fallback "Network (name)".
- `buildCandidates(ranges, maxHosts=254)` → list of IP strings: for each range
  sweep `networkBase+1 … +254` (covers a full /24; for narrower prefixes the
  first 254 neighbours), skip the broadcast address and all local addresses
  across ranges; dedupe.
- `probe(hosts, port, timeoutMs=300)` → `Set<String>` reachable: fixed pool (64
  threads), `Socket.connect(new InetSocketAddress(host, port), timeoutMs)` with
  try-with-resources, CountDownLatch with a 30 s ceiling, `shutdownNow` after.
- `resolveHostname(host, timeoutMs=250)` → best-effort bounded reverse-DNS;
  null on timeout/failure.

### 3. `utils/AdbHelper.java` (extend)
- `static Map<String,String> parseAdbDevices(String output)` (package-visible,
  pure Java — unit-testable): parse `serial\tstate` lines.
- `static boolean isTcpSerial(String serial)` (package-visible): serial ends in
  `:digits` (accepts `[v6]:port`).
- `public static Map<String,String> getAdbDevicesMap()`: `adb devices` → parse.

### 4. UI — `MainActivity.java` + `activity_main.xml`
- New row below the IP field: **Scan network for devices** button
  (`btn_selector` styling) + hint `TextView` (first-run + empty state).
- Scan flow (button click):
  1. Guard: refuse while a mirror session is active.
  2. `Progress.showDialog` → `ThreadUtils.execute` (background):
     `getActiveRanges` → adb section from `getAdbDevicesMap` (TCP serials only)
     → per-range `buildCandidates` + `probe` → build grouped item list
     (`String` headers + `DiscoveredDevice` rows); dedupe by `host:port` with
     adb entries first. Reverse-DNS names resolved in background (250 ms bound).
  3. `ThreadUtils.post` (main): empty-state handling or grouped
     `ListPopupWindow` (custom `BaseAdapter` with disabled header rows, 2-line
     device rows: `host:port` + friendly name/state), anchored to the Scan
     button, height capped at ~60% screen. Item click fills `editText_server_host`
     and dismisses.
- Empty states:
  - No active network range → hint + toast: enable Wi-Fi / hotspot.
  - Scan found nothing → hint + toast: enable ADB-over-network on target,
    same network, run `adb tcpip 5555` once over USB.
  - First run → persistent hint under the button.

### 5. Resources (`values/strings.xml`)
New strings only in default (English) locale: `scan_network`,
`scanning_network`, `scan_first_run_hint`, `scan_no_network_hint`,
`scan_no_devices_hint`, `scan_adb_section`, `scan_android_device`,
`scan_ready`, `scan_offline`, `scan_unauthorized`, `scan_busy`.
(`values-zh` / `values-ja` fall back to default.)

## Tests (new, JVM unit tests)

`app/src/test/java/org/client/scrcpy/utils/`:
- `LanScannerTest`: `networkAddress` mask math; /24 candidate sweep (count,
  no `.0`/`.255`/self); /16 cap ≤ 254; dedupe across overlapping ranges; skip
  own IPs across ranges; `probe` against a real localhost `ServerSocket`
  (hit and closed-port miss); empty-hosts probe.
- `AdbHelperTest`: `parseAdbDevices` (normal/multi-state/empty/null);
  `isTcpSerial` (v4, bracketed v6, plain serials, emulator, malformed).

## Verification

1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug`
   (system Java 26 is unsupported by AGP 8.0/Gradle 8.6; compileSdk 31 must be
   downloaded by AGP — blocked if the SDK download fails, flagged rather than
   silently bumping compileSdk).
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:lintDebug`
4. Manual on-device checklist (user): Wi-Fi scan finds a phone with
   `adb tcpip 5555`; hotspot scan finds a tethered phone; selection fills the
   host field; Start connects as before.

## Risks / non-goals (v1)

- IPv6 probing deferred (candidates are IPv4; v6 serials from `adb devices`
  still listed).
- Reverse-DNS may be slow on broken networks → 250 ms bound, fallback label.
- Probe churn is bounded: ≤ 508 connects on Wi-Fi + hotspot (64 threads, 300 ms).
- No auto-connect on scan; no state mutation of target devices.
- Android 11+ wireless-debugging pairing (QR/code) is deferred (v2).
- Target device model names, latency/signal per candidate, bookmarks,
  DataStore persistence — deferred.