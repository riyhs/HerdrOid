# Terminal baseline — physical device

Measured 2026-11-02 on a Samsung SM-A556E (Galaxy A55), Android API 37,
1080 × 2340 at 120 Hz, using the benchmark variant (minified, non-debuggable,
debug-signed). Five iterations, 4 s warmup. `stream` and `idle` measure for
10 s; `scroll` and `ime` run their fixed action blocks.

## Results

| workload | frames | CPU p50/p95 ms | overrun p50/p90/p95/p99 ms |
| --- | ---: | ---: | ---: |
| `stream` | 389–437 | 30.0 / 35.1 | 44.3 / 53.1 / 55.1 / 59.2 |
| `idle` | 20–21 | 25.6 / 34.9 | 14.0 / 22.9 / 25.3 / 29.3 |
| `scroll` | 6–7 | 21.0 / 34.3 | 15.5 / 21.8 / 29.2 / 42.9 |
| `ime` | 71–79 | 12.0 / 20.7 | 7.6 / 19.5 / 21.3 / 24.1 |

At 120 Hz the frame deadline is 8.33 ms. Every measured `stream` frame missed
it, and the median frame took about 30 ms of CPU. The same run only produced
about 40 frames per second. Even against a 60 Hz deadline, the median
`stream` frame would still be roughly 36 ms late.

The scroll and IME workloads produce too few frames to treat their percentiles
as stable. Their numbers are useful as a smoke check, not as acceptance data.
The idle frame count is low because it only renders cursor blinks.

termlib also coalesces snapshots on a fixed 16 ms cadence, so a 120 Hz display
cannot hit every 8.33 ms deadline even with a perfect renderer. The actionable
signal is the 30 ms of CPU work per stream frame, not the 100% miss rate alone.

## Where the time goes

`stream`, iteration 2, app main thread:

| slice | total | per frame |
| --- | ---: | ---: |
| `traversal` | 7.06 s | 17.5 ms |
| `Record View#draw()` | 6.72 s | 16.7 ms |
| `AndroidOwner:measureAndLayout` | 3.78 s | 9.4 ms |
| `AndroidOwner:draw` | 2.91 s | 7.2 ms |
| `animation` | 2.88 s | 7.1 ms |
| `Compose:recompose` | 1.63 s | 4.0 ms |
| `Recomposer:recompose` | 1.46 s | 3.6 ms |

The main thread was in the running state for 9.96 s of the 10 s window. It is
CPU-bound, not waiting on vsync or SSH.

`Compose:recompose` ran 43,951 times across 403 frames (about 109 per frame).
A sampled measure pass contained 54 `Compose:recompose` calls, consistent with
two passes over 27 visible rows. This matches termlib 0.3.14's `TerminalRows`:
it creates `2 * rows` full-screen `Canvas` composables, one pass for backgrounds
and one for glyphs.

`writeInput` is not the bottleneck. The stream sends about 628 chunks in 10 s:

| section | total | average per chunk |
| --- | ---: | ---: |
| `HerdrOid.terminal.writeInput` | 721–811 ms | 1.15–1.29 ms |
| `HerdrOid.terminal.mouseFeed` | 30–37 ms | 0.05 ms |
| `HerdrOid.terminal.ack` | 3–4 ms | 0.005 ms |

`writeInput` consumes about 8% of the measured window. `MouseModeTracker` costs
about 50 µs per chunk here.

The remaining cost is Compose measure/layout and draw of the terminal rows,
plus URL-mask maintenance. `TerminalScreen` passes `autoDetectUrls = true`, and
termlib rebuilds a `rows × cols` hyperlink mask for every snapshot.

## Next step

The first change to test is replacing the per-row full-screen canvases with a
single terminal canvas (or taking an upstream termlib fix that does the same).
That is a renderer-level change, not an SSH/credit-window change.

After that, make URL detection lazy per line, then re-run `stream` on this phone
with the same five-iteration setup. Keep `writeInput` tracing in place to confirm
the parser is still off the main thread and not regressing.

## Caveats

- This harness uses a synthetic `TerminalHandle`; it does not include `hc.read`,
  SSH, or input-to-present latency.
- The test APK is unminified, but it runs in its own process. The target app is
  the release-derived benchmark variant.
- The five stream traces, the benchmark JSON, and text results for all four
  workloads are in `artifacts/benchmark/` and are gitignored.
