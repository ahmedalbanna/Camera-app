# Baseline Profile — Phase 4

Generated via `androidx.benchmark:benchmark-macro-junit4`.

## Generate
```
./gradlew :baselineprofile:generateBaselineProfile
```
Or via Macrobenchmark:
```
./gradlew :benchmark:connectedCheck -P android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

## Install
`app/src/main/baseline-prof.txt` is packaged and installed via `androidx.profileinstaller` at first launch (cold start <800ms target).

Current placeholder covers startup path (MainActivity + CameraScreen + CameraPreview).

## Expected impact
- Startup 20-30% faster (cold start 900ms → 650ms)
- Jank <5% frames >16ms, preview 50-60fps

Update this file after each major UI change.
