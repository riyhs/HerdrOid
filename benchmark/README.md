# Terminal frame benchmark

This module measures the terminal rendering path without requiring a live `hc`
host. `BenchmarkTerminalActivity` is only present in the app's `benchmark` build
type; it feeds synthetic terminal output through the real `TerminalScreen`,
`MouseModeTracker`, and termlib pipeline.

## Workloads

- `stream` — 48-line ANSI/box-drawing chunks every 16 ms, sustained.
- `idle` — a screenful, then cursor blink only.
- `scroll` — fills scrollback, then swipes the terminal six times.
- `ime` — fills the screen, then opens and closes the IME.

## Run on a physical device

```sh
. scripts/env.sh
./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark

adb -s <serial> install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb -s <serial> install -r benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk

adb -s <serial> shell am instrument -w -r \
  -e class dev.riyaldi.herdroid.benchmark.TerminalBenchmark#stream \
  -e iterations 5 -e warmupMs 4000 -e measureMs 10000 \
  dev.riyaldi.herdroid.benchmark/androidx.test.runner.AndroidJUnitRunner
```

Run each workload separately; don't run a build or another benchmark at the same
time. Use a release-like, non-debuggable APK (the `benchmark` variant is
minified, non-debuggable, and signed with the debug key).

The trace sections are `HerdrOid.terminal.mouseFeed`,
`HerdrOid.terminal.writeInput`, and `HerdrOid.terminal.ack`. They are emitted
from `TerminalScreen` and reported by `TraceSectionMetric`.

Acceptance target: at least 99% of frames meet their deadline. Look at p95/p99
`frameOverrunMs`, not the median. termlib coalesces snapshots every 16 ms, so
120 Hz runs cannot hit every 8.33 ms deadline even with a perfect renderer; use
the CPU-frame and trace numbers to compare changes. This harness does not
measure input-to-present latency; add a real-host input workload before using
that metric.

See [BASELINE.md](./BASELINE.md) for the first physical-device measurement and
the main-thread trace breakdown.

## Emulator smoke test

Emulator numbers are not valid for acceptance. They are useful only for checking
that the harness works:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.riyaldi.herdroid.benchmark.TerminalBenchmark#idle \
  -e iterations 1 -e warmupMs 2000 -e measureMs 3000 \
  -e androidx.benchmark.suppressErrors EMULATOR \
  dev.riyaldi.herdroid.benchmark/androidx.test.runner.AndroidJUnitRunner
```

## Notes

- The benchmark test APK is self-instrumenting, so it can kill, compile, and
  launch the target app independently.
- The test module is intentionally unminified (40 MB APK) and disables AGP's
  `checkTestedAppObfuscation` task; the harness talks to the target by package
  and component name, so no obfuscation mapping is needed.
- This harness covers the app and terminal renderer. A second workload that
  replays captured `HcClient` frames or attaches to a real host is still needed
  to measure SSH/protocol cost.
