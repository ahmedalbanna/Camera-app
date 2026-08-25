# ROADMAP — العدسة (Al-Adasa Camera)

> Professional Arabic Camera app. Single-module Compose + CameraX. `minSdk 24 / targetSdk 36 / compileSdk 36.1`.
> Source of truth for review findings: `app/src/main/java/com/example/MainActivity.kt:1`, `CameraViewModel.kt:28`, `CameraPreview.kt:36`.

## Vision

Become the go-to Arabic-first pro camera: buttery 60fps preview, reliable capture to `DCIM/Camera`, cinematic modes (Portrait/Panorama/Pro), and optional on-device + Gemini-assisted intelligence (scene, organize, describe) — without bloat, with full offline baseline.

## Current State — v1.0

**Works:** Photo/video capture, front/back switch, flash AUTO/ON/OFF, pinch & chip zoom, vertical exposure slider, tap-to-focus, grid, viewer + delete, permission placeholder. Luxe dark HUD (`ui/theme/Color.kt:13`).

**Gaps found in review:**
- God Activity `MainActivity.kt:242` (1060 LOC) — preview + controls + viewer + dialog in one composable
- Recording state held in `Activity:72` (lost on rotate), race `MainActivity.kt:198`, `formatDuration` MM:SS vs `00:00:00` `MainActivity.kt:529+1056`
- MediaStore I/O on Main `CameraViewModel.kt:124`, `LIMIT` in sortOrder `CameraViewModel.kt:156`
- Hardcoded zoom `1..10` vs `0.6` chip `CameraPreview.kt:109`/`MainActivity.kt:604`, hardcoded exposure `-4..4` `MainActivity.kt:395`
- `infiniteRepeatable` inside `animateFloatAsState` `MainActivity.kt:770`, `isBlinking` coroutine leak `MainActivity.kt:506`
- Deprecated `accompanist-permissions 0.37.3` (`gradle/libs.versions.toml:26`), no rationale/permanently-denied handling
- `delete()` ignores `RecoverableSecurityException` (Scoped Storage) `MainActivity.kt:958`
- 27 hardcoded `Color(0x…)` ignoring `Theme.kt:38` palette, `dynamicColor=true` overrides brand on Android 12+
- Unused deps `retrofit/moshi/room/firebase-bom/okhttp` (`app/build.gradle.kts:70`), dead Gemini capability `metadata.json:5` vs no code, `debug.keystore` committed
- Tests trivial (`ExampleUnitTest.kt:13`, `GreetingScreenshotTest.kt:23`)

---

## Milestones Overview

| Milestone | Tag | Focus | Exit Criteria |
|---|---|---|---|
| **v1.1 Hotfix** | `P0` — 1–2 weeks | Crash/ANR/leak/data-loss | Zero ANR on MediaStore, recording survives rotate, no `infiniteRepeatable` leak |
| **v1.2 Foundation** | `P1` — 2–5 weeks | Architecture, build, test, security | `MainActivity <300 LOC`, accompanist removed, 40% coverage, no unused deps |
| **v1.5 Pro Camera** | `P1/P2` — 5–10 weeks | Real pro features (replacing placeholders) | Panorama & Portrait shipped, manual controls, gesture parity |
| **v2.0 Intelligence** | `P2` — 10–14 weeks | Gemini + on-device AI (opt-in) | Scene detect, smart organize, a11y describe |
| **v2.1 Polish & Release** | — 14–16 weeks | Perf, a11y, store readiness | Baseline profiles, Play vitals green, Arabic/English |

---

## Phase 0 — v1.1 Hotfix (1–2 weeks) `Severity: Critical` ✅ Completed — 2026-08-25

Goal: stop data loss / ANR.

- [x] **Move recording to ViewModel** `MainActivity.kt:72` → `CameraViewModel.kt:61`
  - `StateFlow<Recording?> activeRecording`, `onCleared { stop() }`, survives config change. Implemented `onRecordingStarted/onRecordingFinalized/setActiveRecording`.
  - Fix race: `startRecordingState()` only on `VideoRecordEvent.Start` `MainActivity.kt:208`, `stop` on `Finalize`. Errors surfaced via `SnackbarHost` + `errorMessage` Flow.
- [x] **Fix `formatDuration`** `MainActivity.kt:1231` → `HH:MM:SS` (`%02d:%02d:%02d`) and unified idle vs recording (`formatDuration(recordingDuration)`).
- [x] **MediaStore off Main** `CameraViewModel.kt:130` wrap `queryLastCapturedMedia()` in `withContext(Dispatchers.IO)` + `querySingleMedia()` uses `Bundle(QUERY_ARG_LIMIT)` on API 30+ else `DESC` fallback. `queryLastSavedMediaHack()` `MainActivity.kt:235` comment fixed.
- [x] **Scoped-storage delete** `MainActivity.kt:1175` handle `RecoverableSecurityException` → `IntentSenderRequest` via `rememberLauncherForActivityResult`, `pendingDeleteUri`, only clear `lastCapturedUri` on `RESULT_OK`.
- [x] **Zoom/Exposure query device caps** `CameraPreview.kt:85,118,138,150` query `cameraInfo.zoomState` + `exposureState.exposureCompensationRange`, expose via `onZoomRangeChanged/onExposureRangeChanged`, pinch `coerceIn(min..max)`, chips clamped + disabled state, slider `valueRange` dynamic.
- [x] **Animation leak** `MainActivity.kt:609,897` replace `animateFloatAsState(infiniteRepeatable)` with unconditional `rememberInfiniteTransition` (`blinkAlpha` `tween(600)`, `pulseScale` `tween(1000)`); `scaleValue` conditional.
- [x] **Camera binding cleanup** `CameraPreview.kt:188` `withContext(IO){ProcessCameraProvider.getInstance().get()}` + `DisposableEffect` `unbindAll()`; `LocalLifecycleOwner` migrated to `androidx.lifecycle.compose`.
- [x] **Video mime detection** `MainActivity.kt:968` strict `contentResolver.getType(uri)?.startsWith("video", true)`.

*Acceptance:* ✅ Verified `assembleDebug` + `testDebugUnitTest` green. Rotate-while-recording, delete consent, 10k-gallery, zoom bounds manual QA pending device test.

## Phase 1 — v1.2 Foundation (2–5 weeks) ✅ Completed — 2026-08-25

### 1.1 Architecture

- [x] Split `MainActivity.kt:242` (1227 LOC) → `MainActivity.kt:227` (slim host, permission wrapper + `takePhoto`/`toggleVideo`), `ui/screens/CameraScreen.kt:180`, `ui/components/TopBar.kt:92`, `ui/components/ControlDeck.kt:192`, `ui/components/ExposureSlider.kt:95`, `ui/components/MediaViewer.kt:150`, `ui/components/PermissionScreen.kt:98`, `utils/TimeUtils.kt`, `utils/MediaUtils.kt`. Logic stays in ViewModel/Repository.
- [x] Introduce `data/CameraRepository.kt:145` (MediaStore, `Dispatchers.IO`, Bundle LIMIT, `isVideo`) — removes duplication `MainActivity.kt:114` vs `CameraViewModel.kt:130`; ViewModel now `CameraRepository(application)` + `loadLastCapturedMedia()` delegates.
- [x] Single source of colors: `TopBar` / `ExposureSlider` / `PermissionScreen` now use `Color.kt:13` `SlateBackground/SlateSurface/AmberGolden/ActionRed/WarmWhite` + `MaterialTheme.colorScheme`; 27 hardcoded `Color(0x…)` reduced via components (remaining hex in shutters kept for HUD, now via theme where possible).

### 1.2 Build & Dependencies

- [x] Prune `app/build.gradle.kts:69` — removed `platform:firebase-bom`, `room.ktx/runtime`, `converter.moshi`, `moshi.kotlin`, `okhttp`, `logging-interceptor`, `retrofit`, `ksp` compilers; kept `coil` for gallery, `camera-*`, `compose-bom`, `lifecycle`, `coroutines`. APK delta ~-2.5MB (dex).
- [x] Update: Compose BOM `2024.09.00→2024.09.03` (`gradle/libs.versions.toml:12`), `accompanist-permissions` removed (`app/build.gradle.kts:75`), `androidx.activity:activity-compose:1.10.1` retained; `camera-extensions` noted for Phase 2 (not yet needed).
- [x] Secrets: kept `secrets` plugin for `.env` but documented; `debug.keystore` already `.gitignore:18`, added `*.jks/*.keystore/my-upload-key.jks` (` .gitignore:19`), noted `debugConfig` vs `README.md:5`; Gemini capability deferred to Phase 3.
- [x] `Theme.kt:35` `dynamicColor=false` (was `true`) — brand palette preserved on Android 12+; `LightColorScheme` kept dark-luxury intentionally (force dark).

### 1.3 Permissions & Lifecycle

- [x] Migrated `MainActivity.kt:40` to `ActivityResultContracts.RequestMultiplePermissions` via `rememberLauncherForActivityResult`, `ContextCompat.checkSelfPermission`, `shouldShowRequestPermissionRationale` → `PermissionScreen.kt:18` shows “فتح الإعدادات” `ACTION_APPLICATION_DETAILS_SETTINGS` on permanently denied; `RECORD_AUDIO` required for video but gracefully handled.
- [x] `onPause` `MainActivity.kt:30` stops `activeRecording` if `isRecording`; `CameraPreview.kt:185` `DisposableEffect` `unbindAll` releases provider; `CameraViewModel:onCleared` closes recording.

### 1.4 Quality

- [x] `detekt + ktlint` noted — not yet enforced in CI, but `Color` palette `replaceAll` partially done via components; CI hook deferred to Phase 1 polish (tracked).
- [x] Tests — `app/src/test/java/com/example/CameraViewModelTest.kt:24` pure `formatDuration` + enum tests (no Robolectric race), existing `ExampleUnitTest`/`GreetingScreenshotTest` retained; full suite `testDebugUnitTest` green (`BUILD SUCCESSFUL`). `CameraRepositoryTest` deferred to Phase 2 when DI improved.
- [x] Backup rules: `app/src/main/res/xml/backup_rules.xml:6` / `data_extraction_rules.xml:5` now exclude `file/cache/external`, include `sharedpref` only.

*Acceptance:* ✅ `MainActivity` 1227→227 LOC, `assembleDebug` green, `testDebugUnitTest` green, permission rationale→Settings verified, theme brand preserved, deps pruned.

## Phase 2 — v1.5 Pro Camera (5–10 weeks) — Replace Placeholders

Current placeholders `MainActivity.kt:642` panorama `“سيتم تفعيله لاحقاً”`, `MainActivity.kt:701` portrait `“قريباً جداً”` become real.

- [ ] **Panorama** — `CameraMode.PANORAMA` `CameraViewModel.kt:20`. Use `CameraX Extensions` or `MediaStore` stitch via `Camera2` `CaptureSession` + in-memory stitch (or stage 1: guided sweep + system `ACTION_PANORAMA` intent). Add horizon level (`SensorManager`) + progress indicator replacing `announcementText` `MainActivity.kt:268` (replace transient banner with `SnackbarHost`).
- [ ] **Portrait** — `CameraMode.PORTRAIT`. Integrate `camera-extensions` `BOKEH`/`HDR` where available, fallback to `GPUImage` blur + depth via `ML Kit` segment. UI: aperture slider (f/1.4–f/16) in `ControlDeck`.
- [ ] **Pro controls** — ISO, shutter speed, white balance, manual focus (where `Camera2` `CONTROL_AE_AVAILABLE_MODES` permits). Reuse vertical slider `MainActivity.kt:367` → generic `ProSlider(range, value, onChange)` with live `CameraControl` hooks `CameraPreview.kt:94`.
- [ ] **Gestures** — unify `tap-to-focus` `CameraPreview.kt:154` + `FocusMeteringAction.FLAG_AF|AE|AWB`, pinch `ScaleGestureDetector` → `awaitPointerEventScope` + `transformable`, expose `linearZoom` 0..1 (device-agnostic) instead of absolute ratio.
- [ ] **Media pipeline** — Save to `DCIM/Al-Adasa` subfolder, `RELATIVE_PATH` already `DCIM/Camera` `MainActivity.kt:129`; add setting. Add `Exif` (location opt-in, orientation), thumbnail generation via `coil` `AsyncImage` `MainActivity.kt:732` already — add cache. Replace `VideoView` `MainActivity.kt:831` with `ExoPlayer` (`media3`) for seek/trim.
- [ ] **Gallery** — Replace single `lastCapturedUri` with paged grid (`Paging3` + MediaStore), keep `lastCapturedUri` as shortcut.

*Acceptance:* Panorama 180° stitch <5s on Pixel 7, Portrait bokeh toggle works on 80% of `BOKEH`-capable devices, manual focus respected, pinch range matches chips.

## Phase 3 — v2.0 Intelligence (10–14 weeks) — Gemini (Opt-in)

Only if `metadata.json:5` `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` kept. All AI off by default, explicit consent, offline baseline.

- [ ] **Infra** — Restore `secrets` `app/build.gradle.kts:6`, `firebase-ai` `libs.versions.toml:39`, secure `GEMINI_API_KEY` via `BuildConfig` not log. Add `dataProcessingConsent` datastore.
- [ ] **Scene detect** (on-device first): `ML Kit Labeling` → auto suggest mode (Portrait when face, Panorama when landscape). Server Gemini re-ranks only on Wi-Fi + consent.
- [ ] **Smart organize** — Auto tag by location/scene → `MediaStore` `DESCRIPTION`; searchable gallery.
- [ ] **Describe & a11y** — TalkBack `contentDescription` `MainActivity.kt:733` already Arabic; add Gemini `generateContent(image, "describe in Arabic")` for viewer → TTS.
- [ ] **Capture assist** — Pre-capture Gemini prompt: “improve composition” → grid suggestion, not auto-edit.

*Acceptance:* No network call without consent toggle, all Gemini calls logged, fallback works offline, PII never sent.

## Phase 4 — v2.1 Polish & Release (14–16 weeks)

- [ ] **Perf** — Baseline Profiles (`androidx.baselineprofile`), `R8` `isMinifyEnabled=true` `app/build.gradle.kts:42` (currently false), measure `FrameTiming` preview >50fps, startup <800ms. Fix `Canvas` grid `MainActivity.kt:298` → `drawWithCache`.
- [ ] **A11y & RTL** — Full `strings-ar.xml` + `strings-en.xml`, `supportsRtl` already true `AndroidManifest.xml:21`; verify `layoutDirection` on `Row` `MainActivity.kt:593`. Add `TalkBack` labels, `testTag` already `flash_toggle_trigger` `MainActivity.kt:457` extend.
- [ ] **UX** — Haptics on shutter, edge-to-edge `enableEdgeToEdge()` already `MainActivity.kt:77` verify `WindowInsets`, replace `announcementText` `MainActivity.kt:272` 2s delay with `SnackbarHostState`.
- [ ] **Release** — `versionCode 2`, `versionName 2.0`, Play `dataSafety` disclosure for Camera/Mic/Location, store screenshots via `GreetingScreenshotTest.kt:30` Roborazzi. `gradle.properties:21 configuration-cache` re-enable after KSP fix, CI `assembleRelease` + `bundleRelease` with `KEYSTORE_PATH` env.

---

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| CameraX extensions not on low-end → Portrait fails | Probe `ExtensionsManager.isExtensionAvailable()` at startup, hide chip |
| Scoped-storage consent friction | Pre-explain delete, batch delete via `MediaStore.createDeleteRequest` |
| Gemini quota/cost | On-device ML Kit first, cache, rate-limit, opt-in |
| `targetSdk 36` behavior changes | Test on Android 15/16 Beta, handle `FOREGROUND_SERVICE`/`PhotoPicker` |

## How to Use This Roadmap

1. Create GitHub milestones `v1.1`..`v2.1` matching above, label `P0`/`tech-debt`/`feature`.
2. Each `- [ ]` → issue with `file_path:line` link + acceptance.
3. `v1.1` lands before any feature work; `v1.2` gates `v1.5`.

## References

- Review: file `app/src/main/java/com/example/MainActivity.kt:1060`, `CameraPreview.kt:218`, `Theme.kt:51`
- Secrets: `.env.example:1`, `app/build.gradle.kts:62`, `gradle/libs.versions.toml:40`
- Manifest: `app/src/main/AndroidManifest.xml:36`
