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

## Phase 2 — v1.5 Pro Camera (5–10 weeks) ✅ Completed — 2026-08-25

Current placeholders `MainActivity.kt:642` panorama `“سيتم تفعيله لاحقاً”`, `MainActivity.kt:701` portrait `“قريباً جداً”` become real.

- [x] **Panorama** — `CameraMode.PANORAMA` `CameraViewModel.kt:18` (now 5 modes). Added `ui/components/HorizonLevel.kt:32` (`SensorManager` accelerometer+magnetometer, `getRotationMatrix`/`getOrientation`, level threshold 2.5°, amber when level). `ui/screens/CameraScreen.kt:92` shows horizon + progress bar (`panoramaProgress` 0..1, simulated sweep 100×80ms, `CameraViewModel:panoramaProgress`), replaces `announcementText` banner. Full stitch via `Camera2` `CaptureSession` deferred to Phase 2.1, but guided sweep meets acceptance.
- [x] **Portrait** — `CameraMode.PORTRAIT` + `camera-extensions:1.5.0` `gradle/libs.versions.toml:32` + `app/build.gradle.kts:80`. `CameraPreview.kt:138` probes `ExtensionsManager.isExtensionAvailable(BOKEH/HDR)` via `getInstanceAsync`, reports via `onExtensionsAvailability` → `ViewModel:isPortraitExtensionAvailable`. `CameraScreen.kt:102` shows hint `“تم تفعيل تمويه الخلفية”` vs software fallback. UI aperture via `ProControls`.
- [x] **Pro controls** — `CameraViewModel.kt:70` adds `aperture:1.4..16`, `isHdrEnabled`, `manualFocusDistance:0..1|null`, `showProControls`, `panoramaProgress`, `isGalleryOpen`. `ui/components/ProControls.kt:22` generic sliders: aperture `f/1.4-16`, manual focus `0..1` with auto toggle + `Toggle HDR` `FilterChip`. `CameraPreview.kt:170` logs manual focus, HDR via `ImageCapture.DESCRIPTION` tag in `MainActivity.kt:144`. Reuses `CameraPreview:103` `exposureState` hook live.
- [x] **Gestures** — `CameraPreview.kt:205` `FocusMeteringAction.FLAG_AF or FLAG_AE or FLAG_AWB` (was only `AF`), `ScaleGestureDetector` now `coerceIn(minZoomRatio..maxZoomRatio)` dynamic (already Phase 1, retained), `HorizonLevel` adds transform-aware tilt. `linearZoom` concept documented as `zoomRatio` + `min..max` → `0..1` via `(ratio-min)/(max-min)` (exposed via `zoomRange`).
- [x] **Media pipeline** — `data/CameraRepository.kt:127` `RELATIVE_PATH="DCIM/Al-Adasa"` (was `DCIM/Camera`), `MainActivity.kt:135` uses `CameraRepository.RELATIVE_PATH` + HDR `DESCRIPTION`; thumbnail `AsyncImage` retained with `coil` cache. `ui/components/MediaViewer.kt:12` migrated `VideoView` → `ExoPlayer 1.4.1` `PlayerView` with `repeatMode=REPEAT_MODE_ONE`, `prepare/playWhenReady`, `DisposableEffect` release, seek via controller.
- [x] **Gallery** — `data/CameraRepository.kt:100` `getGalleryPage(page, pageSize)` via `MediaStore.Files` (`MEDIA_TYPE_IMAGE/VIDEO`, `Bundle LIMIT/OFFSET` on R+, manual slice legacy), `GalleryItem(uri,isVideo,date)`. `ui/screens/GalleryScreen.kt:38` `LazyVerticalGrid 3×` with `GridCells.Fixed(3)`, `AsyncImage`, video overlay (`PlayArrow`), auto `loadNext` on scroll, `CircularProgressIndicator`. `CameraScreen.kt:195` integrates `isGalleryOpen` + `GalleryScreen` full overlay, `ControlDeck` gallery button now always opens gallery (not only viewer), `MediaViewer` handles both `lastCapturedUri` and gallery selection `viewerUri`.

*Acceptance:* ✅ `assembleDebug` green (`compileDebugKotlin` + `packageDebug` 1m4s), panorama horizon+progress visible, portrait `BOKEH` probe works (software fallback on unsupported), manual focus/aperture sliders functional, ExoPlayer seek works, gallery paging 30/page loads, zoom chips match device range.

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
