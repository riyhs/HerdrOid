# termlib renderer experiment

Tested 2026-11-03 on the same Galaxy A55 (SM-A556E, API 37, 1080 × 2340) used
for `BASELINE.md`. The display is pinned to 60 Hz. Each run uses the existing
`stream` workload: 48 lines every 16 ms, five iterations, 4 s warmup, 10 s
measure.

## Fork setup

The local fork is termlib 0.3.14 with its Kotlin sources built by HerdrOid's
AGP/Kotlin/Compose versions. It reuses the four native `libjni_cb_term.so`
libraries from the published AAR, so no NDK install or C++ rebuild is needed.

`settings.gradle.kts` accepts an optional composite build:

```sh
./gradlew -PtermlibFork=/path/to/termlib-fork \
  :app:assembleBenchmark :benchmark:assembleBenchmark
```

The dependency substitution maps `org.connectbot:termlib` to the fork's `:lib`.
Without the property, the app still uses the published Maven artifact.

## Variants

| variant | change |
| --- | --- |
| control | unmodified `2 × rows` canvases |
| single | one full-screen `Canvas`; all backgrounds, then all glyphs |
| two | one background `Canvas` and one glyph `Canvas` |
| hybrid | one background `Canvas` plus one `Canvas` per row for glyphs |

All variants use the same `drawLine` function and the same terminal data.

## Results

p50 / p95 `frameDurationCpuMs`, with the starting skin temperature where it was
recorded:

| variant | start skin | p50 / p95 ms | frames |
| --- | ---: | ---: | ---: |
| control | not recorded | 26.7 / 31.6 | 443–466 |
| control | 35.2 °C | 30.4 / 36.2 | 378–437 |
| control (hot repeat) | not recorded | 32.4 / 38.5 | 363–404 |
| single | not recorded | 28.3 / 33.7 | 405–473 |
| two | not recorded | 29.7 / 35.1 | 387–443 |
| hybrid | 33.9 °C | 28.7 / 33.5 | 410–458 |
| hybrid | 35.0 °C | 31.8 / 37.6 | 371–403 |

The device's first control run was an outlier. Repeating the control after the
other runs moved p50 from 26.7 to 32.4 ms without any code change. The A55
crosses its first skin-temperature threshold at 38 °C, and benchmark runs end
around 37.7–37.9 °C.

At a matched starting temperature, the hybrid is slower than the control
(31.8 / 37.6 vs 30.4 / 36.2). The single and two-canvas runs were not cooled to
the same start, so their small differences are not treated as wins. None is
below the 16.7 ms budget.

## Trace result

The control and hybrid traces have nearly identical main-thread structure:

| slice | control | hybrid |
| --- | ---: | ---: |
| `traversal` | 7.18 s / 405 | 7.05 s / 406 |
| `Record View#draw()` | 6.83 s / 405 | 6.72 s / 406 |
| `AndroidOwner:measureAndLayout` | 3.73 s / 405 | 3.69 s / 406 |
| `Compose:recompose` | 44,148 | 44,309 |
| recompositions inside measure/layout | 21,870 | 21,924 |
| `animation` | 2.78 s | 2.90 s |

Consolidating canvases did **not** reduce the 54-per-frame measure-time
recompositions. The original assumption that `2 × rows` canvases caused those
recompositions was wrong. Draw recording remains about 16.9 ms per frame, and
the same per-cell `drawText` and image operations run in every variant.

The control's per-row display lists are also better for partial damage: only
changed rows re-record. A full-screen display list re-records the whole viewport
whenever any row changes.

## 8.33 ms cadence

Not attempted. A p50 of 28–32 ms is still far above the 8.33 ms frame budget;
changing termlib's 16 ms snapshot interval to 8.33 ms would only submit more
work and increase jank. It cannot create 120 fps by itself.

## Conclusion

Do not ship a single/two/hybrid canvas renderer. The next useful renderer work
would have to reduce the cost of the drawing operations themselves, for example
run-based text drawing, a glyph atlas, or a different drawing backend. That is a
larger change than repartitioning the existing canvas nodes.

The fork source is restored to the unmodified 0.3.14 renderer. Variant APKs,
text results, and traces are in the gitignored `artifacts/termlib/` directory.
