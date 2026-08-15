# Benchmarking

`:benchmark` measures startup and frame timing on a real device, against a release-like build.

It exists because `dumpsys gfxinfo` could not be trusted. Four consecutive runs of an *identical*
build varied by 25% at the median and 50% at the 90th percentile — wider than any change worth
making — because there was no warm-up, no repetition, and no control over compilation state.
Macrobenchmark handles all three.

## Running it

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

**This uninstalls ArcX when it finishes**, which takes its database and preferences with it. Do
not run it on a phone whose ArcX data you care about without backing up first:

```bash
adb shell run-as com.arcx.app cat databases/arcx.db > arcx-backup.db   # debug build only
```

To keep the app installed between runs — which is what you want while iterating, and required if
you are seeding data — drive the instrumentation directly instead:

```bash
./gradlew :app:installBenchmark :benchmark:assembleBenchmark
adb install -r -t benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk
adb shell am instrument -w -r \
  -e class com.arcx.benchmark.StartupBenchmark \
  com.arcx.benchmark/androidx.test.runner.AndroidJUnitRunner
```

## What it measures

| Benchmark | Metric | Depends on device data |
|---|---|---|
| `StartupBenchmark.coldStartup` | `timeToInitialDisplayMs` | no |
| `StartupBenchmark.coldStartupNoProfile` | `timeToInitialDisplayMs`, unprofiled | no |
| `ScrollBenchmark.activityScroll` | `frameDurationCpuMs`, `frameOverrunMs` | **yes** |
| `ScrollBenchmark.libraryScroll` | as above | **yes** |
| `ScrollBenchmark.tabSwitching` | as above | mildly |

`frameOverrunMs` is the number to read: how far past its deadline a frame landed. Negative is
inside budget.

## Seeding history first

Activity is the only list in ArcX long enough to fling, and it is bounded by
`RunRecord.HISTORY_LIMIT`. On a phone with three runs on it, `activityScroll` measures a
stationary screen and reports something meaningless — or fails outright with *"Observed no
renderthread slices"*, which is macrobenchmark telling you nothing redrew.

The benchmark build is not debuggable, so `run-as` cannot reach its database. Seed through the
debug build and then upgrade in place — same `applicationId`, same debug key, so the data
survives:

```bash
./gradlew :app:installDebug
adb shell am start -n com.arcx.app/.MainActivity   # let Room create the file
adb shell am force-stop com.arcx.app
adb shell run-as com.arcx.app tee databases/arcx.db < seeded.db > /dev/null
adb shell run-as com.arcx.app rm -f databases/arcx.db-wal
adb shell run-as com.arcx.app rm -f databases/arcx.db-shm
./gradlew :app:installBenchmark                    # upgrade, data preserved
```

Compare only runs seeded the same way.

## Reading a result honestly

Check `Thermal Status` before believing anything:

```bash
adb shell dumpsys thermalservice | grep "Thermal Status"
```

`0` is nominal. Anything higher means the CPU is being held back and the numbers are a floor, not
a measurement. A phone that has been building APKs for an hour will sit at `2` for a long time.

Macrobenchmark reports min/median/max across iterations. A difference smaller than the spread
between iterations is not a result — raise `ITERATIONS` or accept that the change is not
measurable.

## Baseline, 15 Aug 2026

Galaxy S25 (SM-S938B, SDK 36), 1,000 runs of seeded history, **thermal status 2 — so these are a
floor, not a best case.** Recorded here so a future change has something to be compared against.

| benchmark | frameDurationCpuMs P50 / P90 | frameOverrunMs P50 / P90 / P99 |
|---|---|---|
| `activityScroll` | 6.4 / 9.3 | 0.4 / 3.4 / 7.1 |
| `libraryScroll` | 4.9 / 8.6 | −1.3 / 2.7 / 19.1 |
| `tabSwitching` | 4.0 / 8.1 | −0.1 / **15.1** / **29.4** |

| benchmark | timeToInitialDisplayMs median |
|---|---|
| `coldStartup` | 246 |
| `coldStartupNoProfile` | 241 |

Two things worth knowing from this run:

- **Scrolling is fine.** Activity holds a 3.4ms overrun at the 90th percentile with a full
  thousand runs behind it. The long list is not the problem it was assumed to be.
- **Tab switching has the worst tail in the app** — 15ms over at P90, 29ms at P99. That is the
  first composition of a screen the user has not visited yet, and it is where the next
  performance work belongs.
- A baseline profile is not worth adding: `coldStartup` and `coldStartupNoProfile` are within
  5ms of each other, so ART's profile is buying nothing measurable here yet.

## Known sharp edges

- **Onboarding.** A fresh install starts at "Bring your own key", and every measurement past the
  first screen would look for a tab that is not there. `skipOnboarding()` clicks through it.
- **Stale nodes.** A Compose list replaces its accessibility nodes as it recomposes, so a
  `UiObject2` held across a fling throws `StaleObjectException`. `flingList()` re-finds the
  scrollable each time.
- **Gesture margins.** Without one, UiAutomator starts the swipe at the display edge where the
  system back gesture claims it, and the list never moves.
