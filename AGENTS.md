# HerdrOid Agent Guide

HerdrOid is a native Android client for an SSH-reachable machine running
`herdr-core` (`hc`). Kotlin + Jetpack Compose + ConnectBot termlib. No WebView,
xterm.js, analytics, or cloud account.

Read `README.md` for the product and `TODO.md` for the current backlog.

## Current state

- Protocol, read-only `herdr-app` mirror, SSH transport, encrypted data layer,
  inbox UI, and native terminal are implemented.
- Transport verified on a physical Android phone against a live hc host:
  connect, attach, typing, Back/reopen, timeout, and Cancel.
- New since that pass: single-activity inbox UI, encrypted host store, domain /
  data modules, off-main terminal output, IME transition protection,
  scrollback controls, and the heap-buffer terminal input fix. These need a
  fresh-device recheck.
- This is a development build; release signing and packaging are not done.

## Build and test

Use the project environment script from Git Bash on Windows:

```sh
. scripts/env.sh
./gradlew test :app:assembleDebug
```

Targeted checks:

```sh
./gradlew :core:domain:test :core:data:test :core:protocol:test \
  :core:mirror:test :core:ssh:test
./gradlew :app:lintDebug
./scripts/check-invariants.sh
```

Install and launch on a connected device:

```sh
android install --device=<serial> --apks=app/build/outputs/apk/debug/app-debug.apk
android layout --device=<serial>
mkdir -p artifacts
android screen capture --device=<serial> -o artifacts/screen.png
adb -s <serial> shell am start -n dev.riyaldi.herdroid/.MainActivity
```

For the instrumentation test on Windows, run it directly. Gradle's UTP cannot
create its result directory from a device serial containing `:`:

```sh
./gradlew :app:assembleDebugAndroidTest
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w \
  dev.riyaldi.herdroid.test/androidx.test.runner.AndroidJUnitRunner
```

## Architecture

Layered, unidirectional flow. The UI never talks to SSH, protocol, or
SharedPreferences directly.

- `:core:domain` — pure Kotlin: `Machine`, `Agent`, `MachineState`,
  `AppSnapshot`, `TerminalHandle`, `MachineRepository` interface,
  `AddMachineUseCase`, `BuildInboxUseCase`. No Android dependencies.
- `:core:data` — `MachineRepositoryImpl` (connections, attach, events,
  persistence), `HcTerminalHandle`, `MachineStore`, `KeystoreCrypto`, mappings.
- `:core:protocol` — framed wire protocol, JSON-RPC, events, attach/credit,
  method allowlist.
- `:core:mirror` — defensive read-only `herdr-app` parser.
- `:core:ssh` — JSch transport, Ed25519 key generation, Keystore AES-GCM,
  host-key pinning, `TCP_NODELAY`.
- `:app` — `HerdrOidApplication` → `AppContainer` (manual DI) →
  `MainViewModel` → Compose screens and the foreground watcher.

Key details:

- The phone never writes the desktop layout. Do not add `doc.set` or any
  `herdr-app` write path. Layout adoption uses labels (`herdr.space`,
  `herdr.tab`) passed to `terminal.run`.
- Attach input, resize, focus, credit, and close frames are queued in
  `HcClient.AttachStream` and drained on `Dispatchers.IO`. Adjacent data/credit
  frames are coalesced. Do not write SSH frames directly from Compose callbacks.
- `TerminalScreen` writes incoming terminal bytes on `Dispatchers.Default`;
  keep output off the main thread. Pass a `ByteArray` to
  `TerminalEmulator.writeInput`; the `ByteBuffer` overload requires a direct
  buffer, and `ByteBuffer.wrap(...)` is a heap buffer that throws
  `Invalid direct buffer range`.
- The keyboard is intentionally hidden when a terminal opens; a tap or the Keys
  button shows it. `resizeSuspended` avoids terminal reflow during the IME
  animation. Do not reintroduce auto-show without measuring.
- The primary screen supports drag scrollback (1000 lines, termlib); attach
  requests `history = 1000` so pre-existing output is included. Alternate
  screen/TUI apps have no local scrollback. `MouseModeTracker` watches output
  for DEC mouse modes; when enabled, `TerminalScreen` turns vertical drags into
  SGR (or legacy X10) wheel events so full-screen agent TUIs scroll in place.
  Apps without mouse reporting still need the key bar's PgUp/PgDn.
- Ed25519 private keys use JSch's OpenSSH-v1 writer. Its legacy PEM writer throws
  on Android.
- `terminal.attach` CLOSE controls the stream, not the terminal itself.
- A normal `hc` session is `default` unless `HC_SESSION` names another one.

## Security and invariants

Run `./scripts/check-invariants.sh` before finishing. It enforces:

- no `doc.set` in app/core main sources
- no WebView or xterm.js
- no cleartext traffic
- no analytics/telemetry SDKs
- host-key checking is never disabled
- Android backup is disabled

The host config is encrypted in `MachineStore` with a Keystore AES-GCM key.
Private key material is decrypted only into a private cache file, zeroed after
use, and deleted. Do not add plaintext persistence. Release builds set
`FLAG_SECURE`; debug builds intentionally allow screenshots for QA.

Never read, copy, print, or modify real private keys, `authorized_keys`,
`docs.json`, or secrets. The user adds the app's public key to the host
themselves. Do not add password/keyboard-interactive authentication.

Use `scripts/android-safe.sh` for Android CLI operations. It denies destructive
actions and requires `ANDROID_SAFE_CONFIRM_SDK=1` for SDK installs. Use
`scripts/hc-test-env.sh` for any scratch `hc` testing so the real session is not
touched.

Do not commit unless the user asks. When committing, include the required
co-author trailer.

## UI rules

- First launch goes directly to Add host.
- Home is the inbox; do not reintroduce a Machines tab or a chain of dialogs to
  connect.
- Host management belongs in the top-bar **Hosts** sheet.
- Inbox rows are one tap to the terminal. Needs-you rows are visually distinct.
- Use short labels, 48dp touch targets, `contentDescription` on icon-only
  actions, and stable lazy-list keys.
- Keep the terminal keyboard hidden until the user asks for it.

## Real-device testing

Wireless ADB:

```sh
adb devices -l
adb mdns services
adb pair <ip>:<pairing-port> <code>
adb connect <ip>:<connect-port>
```

Use `android layout` and `android screen capture` to inspect UI. Use
`--device=<serial>` when more than one transport is listed. Stop emulators and
background servers when finished. Do not use `pm clear` or remove app data
without confirming; encrypted-store testing can be done by removing the test
host through the Hosts sheet.

Host checks:

```sh
command -v hc
echo "$HC_SESSION"
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"hello","params":{"framed":true,"generation":1}}' \
  | /absolute/path/to/hc connect --session default
```

The forced command must use an absolute `hc` path when `~/.local/bin` is not in
the non-interactive SSH `PATH`.

## Current TODO

See `TODO.md`. Priorities: real-phone check of the new inbox/IME path, then
notification/Doze/reconnect, then release hardening.
