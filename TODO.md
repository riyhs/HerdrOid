# HerdrOid TODO

Updated: 2026-11-01

## Terminal UX

- [x] **Terminal scrollback.** Primary-screen drag scrollback works with
  `history = 1000` and was verified on the emulator and the phone.
  Alternate-screen TUIs have no local scrollback, so HerdrOid tracks DEC mouse
  modes and forwards vertical drags as wheel events; verified against a
  full-screen agent TUI on a physical phone. Apps without mouse reporting still
  use PgUp/PgDn.
- [ ] **Recheck keyboard-open latency on the real phone.** The terminal now
  opens with the IME hidden, shows it only on tap/Keys, writes output off the
  main thread, and suspends terminal resize during the IME animation. Measure
  again on a physical Android phone; investigate the IME animation itself if it
  still feels slow.
- [ ] Verify alternate-screen wheel scrolling with other agent apps (one
  full-screen agent TUI is verified).
- [ ] Consider a first-run gesture hint or a tiny scrollback indicator if users
  still do not discover drag-to-scroll.
- [ ] Optional: font-size controls and a narrow-screen key bar layout.

## Verification

- [x] Recheck the inbox and Hosts sheet on a physical Android phone
  (Galaxy A55, live host).
- [ ] Verify the first-run Add host and host-key trust flow on a phone.
- [ ] Verify blocked-agent notifications and foreground-service behavior under
  Doze/background restrictions.
- [ ] Verify reconnect after phone sleep/network loss, including event `missed`
  and attach reattachment.
- [ ] Verify multiple machines, finished-terminal handling, and host removal.
- [ ] Verify the forced-command path with an absolute `hc` path on a fresh host.
- [ ] Run the Keystore/JSch instrumentation test on the phone again after the
  app-module dependency change.

## Architecture / performance

- [x] Split pure domain, Android data, protocol, mirror, SSH, and app layers.
- [x] Thin `MainViewModel`; no Android `Application` reference in the ViewModel.
- [x] Manual DI container in `HerdrOidApplication`.
- [x] Move machine persistence and key generation off the main thread.
- [x] Encrypt host configuration; migrate and remove the legacy plaintext pref.
- [x] Coalesce adjacent attach input/credit frames; off-main terminal output.
- [x] Disable auto-IME on terminal open; suspend resize during IME animation.
- [x] Add stable lazy-list keys and a single inbox-first navigation flow.
- [x] Enable R8 minification and resource shrinking for release with keep rules
  for JSch, BouncyCastle, and termlib.
- [x] Add a terminal Macrobenchmark harness with stream, idle, scroll, and IME
  workloads.
- [x] Run the terminal Macrobenchmark on a physical phone and record p95/p99
  frame overrun and trace-section timings. Baseline is in
  `benchmark/BASELINE.md`.
- [x] Test single/two/full-screen canvas termlib forks and record the result.
  All variants regressed or matched the control; see
  `benchmark/TERMLIB-FORK.md`.
- [ ] Investigate draw-call cost in termlib (run-based text or a glyph atlas)
  before expecting 120 fps; the 16 ms cadence and per-row canvases are not the
  main blocker.
- [ ] Add baseline profiles.
- [ ] Profile on the real phone with `android profiler systemtrace` or
  Macrobenchmark; check frame timing during IME open and large output.

## Security and release

- [x] Remove remote OSC 52 clipboard writes and restrict terminal hyperlinks.
- [x] Mark terminal selection copies as sensitive.
- [x] Block obscured touches and hide overlay windows on the main activity.
- [x] Load SSH keys in memory; remove the plaintext cache file.
- [x] Bind encrypted preferences to a format version and AAD.
- [x] Add device-key reset, delete-all-data, and a privacy note.
- [x] Surface encrypted-store load failures instead of showing an empty host list.
- [ ] Real-phone verification of clipboard, hyperlink, overlay, key-reset, and
  delete-all behavior. Do this on API 26, 31, and 34+ if possible.
- [ ] Real-phone verification that the release build sets `FLAG_SECURE` and
  removes the recents screenshot.
- [ ] Biometric app lock and optional per-terminal `FLAG_SECURE` policy.
- [ ] Local audit log for connect/trust/command actions.
- [x] Add a tag-triggered signed release workflow and a pull-request CI
  workflow.
- [ ] Add the release keystore secrets and publish the first signed release.
- [ ] F-Droid/Play packaging.
- [ ] Choose and add a LICENSE before making the repository public.
- [ ] Consider a QR/deep-link host import while keeping key handoff manual.
- [ ] Review host-key reset and permission prompts with a fresh user.
