# HerdrOid

Android phone client for an SSH-reachable machine running `herdr-core` (`hc`).

- Kotlin + Jetpack Compose, single-activity inbox-first UI
- native terminal via `org.connectbot:termlib` (libvterm/JNI), no WebView
- framed herdr-core protocol over `ssh -T host 'hc connect'`
- read-only mirror of the desktop `herdr-app` document
- Ed25519 device key, encrypted with an Android Keystore AES-GCM key
- encrypted host configuration, host-key pinning, key-only SSH
- no cleartext, no analytics, no backup

Status: development build. The protocol, mirror, SSH transport, encrypted data
layer, inbox UI, and native terminal are implemented. The transport has been
verified on a physical Android phone against a live hc host over SSH. The
current UI and terminal changes still need a fresh-device recheck. See
`TODO.md`.

## UX model

The app is deliberately shallow:

1. First launch opens **Add host** directly.
2. Copy the phone's public key, add the host, and connect.
3. The home screen is the inbox: **Needs you**, **Working**, **Ready**, and
   **Finished** across all hosts.
4. Tapping an agent opens the terminal; Back returns to the inbox.
5. **Hosts** in the top bar is the only host-management surface: connect,
   disconnect, reset trust, remove, and copy the public key.

There is no separate “Machines” tab and no dialog chain to get connected. The
machine chips at the top filter the inbox or select a host to connect. The
**New agent** action is a bottom sheet and never hides the inbox.

## Build and run

```sh
. scripts/env.sh
./gradlew test :app:assembleDebug
android install --device=<serial> --apks=app/build/outputs/apk/debug/app-debug.apk
android run --device=<serial>
```

Use an AVD with Android API 34 or newer:

```sh
android emulator list
android emulator start <avd-name>
```

Useful checks:

```sh
./scripts/check-invariants.sh
./gradlew :core:domain:test :core:data:test :core:protocol:test \
  :core:mirror:test :core:ssh:test :app:lintDebug
android layout
mkdir -p artifacts
android screen capture -o artifacts/screen.png
```

Android Studio is optional. The Gradle wrapper and the Android SDK command-line
tools are enough to build, install, and inspect the app.

## Host setup

1. Install `hc` on the SSH host and make sure `hc connect` works there.
2. Open **Add host** in HerdrOid and copy the public key.
3. Add it to the host's `~/.ssh/authorized_keys`, ideally restricted:

```text
restrict,command="/absolute/path/to/hc connect --session default" ssh-ed25519 AAAA... herdroid@phone
```

4. Add the host in the app: host, port, SSH user, `hc` session, and `hc` path.
5. On first connection, verify the displayed SHA-256 host-key fingerprint against
   the host before trusting it.

`session` is the named herdr-core state (terminals plus the `herdr-app`
document). Without `HC_SESSION`, the normal session is `default`; use the same
name in `authorized_keys` and in HerdrOid. Change it only if you deliberately
run a named session such as `HC_SESSION=work`.

`hc` must be reachable from a non-interactive SSH command. On many Linux hosts
it lives in `~/.local/bin/hc`, which is not on sshd's default `PATH`; use the
absolute path in `authorized_keys` (and in the app's `hc` path when no forced
command is used). A custom Windows absolute path is not supported yet; put
`hc` on the remote `PATH` there.

`restrict` disables PTY, agent forwarding, port forwarding, and X11. The forced
command still exposes the full `hc` API; it is not a sandbox. Use a dedicated
account or a container/VM if you need stronger isolation.

## Security model

- SSH is key-only; passwords and keyboard-interactive auth are not enabled.
- The Ed25519 private key is encrypted with an AES-256-GCM key held by Android
  Keystore. Decrypted key material is written only to a private cache file,
  zeroed after use, and deleted in `finally`.
- Host configuration, including the pinned host key, is encrypted at rest with
  a separate Keystore AES-GCM key. The legacy plaintext preference is migrated
  and removed.
- Host keys are pinned on first trust. A changed key fails closed and requires
  an explicit, verified reset.
- `StrictHostKeyChecking=yes`; cleartext traffic is disabled; Android backup and
  device-transfer rules exclude app data.
- Release builds set `FLAG_SECURE` and disable the recents screenshot. Debug
  builds leave screenshots enabled for QA.
- Notifications are `VISIBILITY_PRIVATE`; terminal content is never included.
- The method allowlist excludes `doc.set`. HerdrOid never writes the desktop
  layout.

## Layout ownership

HerdrOid never calls `doc.set`. It reads `herdr-app` with `doc.get` and renders
spaces/tabs/panes read-only. Phone-started terminals can be adopted by the
desktop through labels:

```sh
hc run --keep --label herdr.space=<id> --label herdr.tab=<id> -- claude
```

The New agent sheet exposes those two label fields. Unknown ids become a new
space/tab when the desktop app next runs; HerdrOid does not generate or write
layout ids itself.

The inbox is the primary grouping. A collapsible **Desktop layout** card is a
read-only view of the same desktop document.

## Modules (clean architecture)

- `:core:domain` — pure Kotlin models, `MachineRepository` interface, validation
  use case, inbox grouping use case, terminal-handle interface. No Android
  dependencies.
- `:core:data` — repository/session implementation, SSH handshake and reconnect
  lifecycle, terminal handles, encrypted `MachineStore`, mapping to domain.
- `:core:protocol` — JSON-RPC, framed transport, channel demux, credit, attach,
  events, method allowlist (no `doc.set`).
- `:core:mirror` — defensive `herdr-app` parser.
- `:core:ssh` — JSch transport, Ed25519 key, Keystore encryption, host pinning,
  `TCP_NODELAY`.
- `:app` — manual DI container, thin `MainViewModel`, Compose screens, termlib
  terminal, foreground watcher, notifications.

Data flows one way: Compose → `MainViewModel` → domain repository → data layer.
The UI does not touch protocol, SSH, or SharedPreferences directly.

## Terminal behavior

- termlib renders through Compose Canvas/libvterm; there is no WebView.
- Attach input, resize, focus, credit, and close frames are queued and drained
  on IO. Adjacent input/credit frames are coalesced.
- Terminal output is written to the emulator off the main thread, then
  acknowledged to the core.
- The SSH socket sets `TCP_NODELAY`.
- The keyboard is hidden when the terminal opens. Tap the terminal (or the
  **Keys** button) to show it, so opening a terminal never forces an IME
  transition. Terminal resize is suspended during the IME animation.
- Drag vertically in the primary screen to scroll back through up to 1000
  lines. Long-press selects text.
- Full-screen TUI applications use the alternate screen and have no local
  scrollback. When the app enables mouse reporting, a vertical drag is forwarded
  as SGR wheel events, so the app scrolls in place. Apps that do not enable
  mouse reporting still need **PgUp**/**PgDn** or their own keys.

## Tests

25 JVM tests:

- `:core:protocol:test` — frame codec, hello, RPC, doc/security checks, fake-core
  handshake, blocking-read timeout
- `:core:domain:test` — host validation and cross-machine inbox grouping
- `:core:data:test` — terminal/status mapping to domain agents
- `:core:mirror:test` — spaces/tabs/panes, unknown fields, malformed documents
- `:core:ssh:test` — command injection checks, fingerprint, OpenSSH-v1 Ed25519
  round-trip
- `:app:test` — mouse-mode parsing and SGR/legacy wheel encodings
- `:app` instrumentation — Keystore/JSch key load, live terminal attach input,
  acknowledgement, and drag-to-wheel forwarding

For the instrumentation test on Windows, run it directly because Gradle's UTP
cannot create a results directory from a device serial containing `:`:

```sh
./gradlew :app:assembleDebugAndroidTest
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell am instrument -w dev.riyaldi.herdroid.test/androidx.test.runner.AndroidJUnitRunner
```

Every integration test that launches `hc` must use a scratch session:

```sh
. scripts/hc-test-env.sh
```

## Verified on hardware

- Physical Android phone (arm64): install, launch, Ed25519 key generation,
  Keystore AES-GCM decrypt, and JSch OpenSSH-v1 key load all pass. This caught
  and fixed a JSch Ed25519 legacy-PEM bug that only appeared on the phone.
- A live hc host over SSH: connect, attach multiple terminals, type into the
  shell, go Back, and reopen a terminal. The 20s handshake timeout and Cancel
  button were verified on-device.
- Android emulator: onboarding, encrypted host persistence across restart,
  inbox grouping, demo long-scrollback drag, keyboard open/typing, key bar, and
  Back navigation.
- A physical Android phone plus a full-screen agent TUI: drag over the alternate
  screen scrolls the agent in both directions through forwarded wheel events.

## Known issues

- The new inbox/UI and keyboard transition still need a real-phone recheck.
- Alternate-screen TUIs without mouse reporting have no terminal-emulator
  scrollback; use PgUp/PgDn or the app's own keys. This is a terminal-app
  convention, not a failed attach.
- Terminal rendering is Canvas/CPU text shaping, not a GPU glyph atlas. It is
  native and damage-region based, but not identical to Termius' renderer.

## Not yet verified

- real-phone keyboard-open latency after the latest no-auto-IME change
- blocked-agent notifications and foreground-service behavior under Doze
- reconnect after phone sleep/network loss, including event `missed`
- release signing, R8, F-Droid/Play packaging, biometric app lock, audit log

## Agent notes

`AGENTS.md` contains build, testing, safety, protocol, and architecture guidance
for coding agents working on this repository. `TODO.md` is the current backlog.
