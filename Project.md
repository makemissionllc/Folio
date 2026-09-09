# Folio — Project Log

Changelog after every coding session. `README.md` stays as the basic app
description and documentation; this file tracks what changed and when.

Active coding branch: `main`.

---

## Session 22 — 2026-09-09 — Automatic device scanning for EPUBs (Downloads/Documents/external, hash dedup, reuse pipeline, non-blocking)

Branch: `main`.

### Built

- **Device storage scanning for EPUBs (§6 — EPUB only, PDF plug-in point)** — `data/scan/EpubScanner.kt`: searches common storage locations (Downloads, Documents, general external storage via `Environment.getExternalStorageDirectory()` + `DIRECTORY_DOWNLOADS`/`DOCUMENTS` walk with `MAX_DEPTH=4` + `MediaStore.Files` `DISPLAY_NAME LIKE %.epub` best-effort) for `.epub` files, capped at `MAX_FILES=80`. Uses the storage permission already requested during onboarding (`READ_EXTERNAL_STORAGE` pre-33 / `READ_MEDIA_IMAGES|VIDEO|AUDIO` on Tiramisu via `ContextCompat.checkSelfPermission`); if permission was denied during onboarding the feature is simply unavailable/disabled (returns empty, no crash, no repeated prompt, manual SAF import via `+` still works). Only EPUB is implemented — PDF parsing doesn't exist yet, but the plug-in point is documented: `walkDir` `|| pdf` check, `queryMediaStore` `OR _display_name LIKE %.pdf`, and `EpubParser` PDF branch. Pure on-device, no network. Structural inspiration from `book-story-master`'s `BrowseScanOption`/`FileSystemRepository` only, no code copied.
- **Reuse of existing import pipeline (don't duplicate)** — `ui/library/LibraryViewModel.kt`: extracted shared `persistParsedEpub(tmpFile, safeName, context, originalPathOrName, sourceHash)` (private copy → `EpubParser.parse` → title/author → cover via `EpubParser.extractCoverToFile` → `BookEntity` + `XRayCache.invalidate`) and made both `importInternal(uri)` (SAF) and `scanDevice(context)` (File) call it. For scan, copies each found `File` to `filesDir/books/scan_tmp_<uuid>_<name>.epub` then reuses the same pipeline. Stores `fileHash` (SHA-256 hex of file content via `MessageDigest` + 8192-byte buffered stream) and `importedFromPath` (original device path/name) on every `BookEntity` for dedup. Legacy rows with null hash are handled by computing the private file's hash at scan time.
- **Deduplication (content + filename/path)** — `scanDevice` loads `bookDao.getAll()`, builds `existingHashes` (stored `fileHash` + computed legacy private-file hash) and `existingPaths` (stored `importedFromPath` + `File(filePath).name`). For each found file, computes `foundHash` via `computeSha256`, then skips if `foundHash in existingHashes` or `absolutePath/name in existingPaths` — avoid duplicate `Book` entries. Title reuse path in `persistParsedEpub` (same title → reuse `id` for LCS reanchor) naturally prevents duplicates even if hash check missed. SHA-256 via `java.security.MessageDigest`, buffered `InputStream.read`.
- **Scan progress (subtle, non-blocking) + auto-scan on launch** — `LibraryViewModel` now exposes `isScanning: StateFlow<Boolean>` + `scanProgress: StateFlow<String?>` + `scanResult: SharedFlow<String>` (`viewModelScope` + `Dispatchers.IO`), updating `_scanProgress` as `Scanning 3/12 · 1 new`. `LibraryScreen` collects these, shows a compact `Card` with `CircularProgressIndicator(16dp)` + `LinearProgressIndicator` + text between `VocabularyTeaser` and the grid when `isScanning`, does not block the UI, and shows a `Snackbar` for `scanResult`/`importError`. Auto-scan is triggered in a `LaunchedEffect(autoScanEnabled)` when `SettingsRepository.autoScanEnabled == true` (default true) and `EpubScanner.hasStoragePermission(context)` — with a 600ms delay, `!isScanning` guard, `Dispatchers.IO` — so the Library scans automatically on app launch; if permission denied it simply does nothing.
- **Settings toggle + manual Scan device button** — `data/settings/SettingsRepository.kt`: added `KEY_AUTO_SCAN_ENABLED = booleanPreferencesKey("auto_scan_enabled")` with `autoScanEnabled: Flow<Boolean>` default true + `setAutoScanEnabled`, same `folio_settings` DataStore (no separate mechanism, survives restart). `ui/settings/SettingsScreen.kt`: added a **Library** `SettingsSection` (Reading / **Library** / Appearance / Privacy) with a `Switch` for “Auto-scan on launch” (`collectAsState` + `repo.setAutoScanEnabled`, disabled text when `!hasPermission`) and a **Scan device** `Row` with a `Button("Scan")` (`enabled = hasPermission`, `CircularProgressIndicator` when `isScanning`, `scanProgress` subtitle). Shares the activity-scoped `LibraryViewModel` (`viewModel(viewModelStoreOwner = activity)`) so Library's subtle progress and Settings' `SnackbarHost` are unified. Includes a PDF plug-in note (“Only EPUB for now — PDF parsing doesn't exist yet… Files are copied to private storage…”). When permission denied, shows “Storage permission denied… Manual import via + still works.” and both controls are unavailable/disabled — no crash or repeated prompt.
- **Room schema** — `data/db/entity/BookEntity.kt`: added `fileHash: String? = null` (SHA-256) + `importedFromPath: String? = null` for scan dedup tracking. `data/db/FolioDatabase.kt`: v6→v7 (`fallbackToDestructiveMigration(true)`). No rewrite of `BookEntity`/`FolioDatabase` structure.

### Changed

- `app/src/main/java/com/makemission/folio/data/db/entity/BookEntity.kt:1-25` — added `fileHash` + `importedFromPath` (nullable, default null, for dedup).
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt:1-43` — v6→v7.
- `app/src/main/java/com/makemission/folio/data/scan/EpubScanner.kt` — new: `hasStoragePermission`, `findEpubFiles` (walk + MediaStore, MAX_FILES/MAX_DEPTH, permission guard), `walkDir`, `queryMediaStore` (epub only, pdf plug-in comments).
- `app/src/main/java/com/makemission/folio/data/settings/SettingsRepository.kt:1-65` — added `KEY_AUTO_SCAN_ENABLED`, `autoScanEnabled` Flow + `setAutoScanEnabled`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:1-205` — extracted `persistParsedEpub` (shared pipeline), added `computeSha256`, `hasStoragePermission`, `isScanning`/`scanProgress`/`scanResult`, `scanDevice(context)` (permission guard, find, dedup via hash/path + legacy hash, copy→persist reuse, progress, cooperative yield, emit result), updated `importInternal` to compute/store `fileHash`/`importedFromPath`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1-310` — added scan `isScanning`/`scanProgress` collect, `autoScanEnabled` collect + `LaunchedEffect(autoScanEnabled)` auto-scan (600ms delay, permission guard), `scanResult` Snackbar, subtle non-blocking `Card` with `CircularProgressIndicator` + `LinearProgressIndicator` when scanning, updated KDoc.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1-340` — added `Button`/`CircularProgressIndicator`/`SnackbarHost` + `ComponentActivity`/`LibraryViewModel`/`EpubScanner` imports, new **Library** section (auto-scan `Switch` + **Scan device** `Button` with permission/disabled handling, shared activity-scoped `LibraryViewModel`, scanResult Snackbar), header now “Reading • Library • Appearance • Privacy”, KDoc updated.
- `README.md:1-185` — intro now lists auto-scan device storage; tech stack Room v7 + DataStore `autoScanEnabled` + EPUB PDF plug-in note + SAF auto-scan note; project structure adds `data/scan/EpubScanner` + `db v7 (fileHash/importedFromPath)` + `library LibraryScreen/ViewModel scan` + `settings Library section`; Library bullet now documents auto-scan (§6 EPUB only, Locations, permission disabled handling, reuse pipeline, hash/path dedup, PDF plug-in, Inspiration), scan progress bullet, Settings toggle bullet; Reading bullet DB v7.
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint, no rewrite).
- Verified permission denied path: `hasStoragePermission` false → `findEpubFiles` returns empty, `scanDevice` emits “Storage permission not granted…”, Library does not auto-scan, Settings toggle/button show disabled + info text, manual `+` SAF import still works (no crash, no repeated prompt).
- Verified dedup: scanning same file twice → second pass skips via `fileHash`/`importedFromPath`, no duplicate `BookEntity`; legacy rows (null hash) deduped via private-file hash fallback.
- Verified reuse: scan path calls same `persistParsedEpub` (copy → `EpubParser.parse` → cover → Room) as SAF import — no duplicated parsing/cover/Room logic.
- Verified non-blocking: scan runs on `Dispatchers.IO` in `viewModelScope`, Library `isScanning` drives a small indicator (not a fullscreen block), Snackbar result after completion.

---

## Session 21 — 2026-09-09 — First-Launch Onboarding (pager + permissions, DataStore-gated, FolioTheme)

Branch: `main`.

### Built

- **Onboarding flow (first-launch only, DataStore reuse, FolioTheme pager + permissions)** — `ui/onboarding/OnboardingScreen.kt`: `HorizontalPager` (4 pages, `rememberPagerState`, dots indicator, Skip/Next/Back/Get started) introduces Folio with FolioTheme visuals (deep green `background`, amber `primary`, burgundy accents, heavy sans `headlineLarge/Medium` + serif `bodyLarge/Medium`, flat `Canvas` illustrations: welcome shelf (amber sun + books), stylus+paper (amber highlight + burgundy stylus), smart chip (green chip + amber core), privacy shield) — not generic templates. Pages: 1) Welcome (premium distraction-free), 2) Stylus highlighting (zero-friction true-ink, lasso), 3) Smart on-device (Bionic, X-Ray, True-Page, Knuth-Plass, SM-2, colorimetric, bounding-box, all local), 4) Privacy-first + upfront permission requests. Reuses existing `SettingsRepository` pattern — extends it with `booleanPreferencesKey("has_seen_onboarding")`, `Flow<Boolean> hasSeenOnboarding` + `setHasSeenOnboarding`, same `folio_settings` DataStore (no separate mechanism). Shown only on first launch via `FolioNavHost` gate (`collectAsState(null)` → loading spinner, `false` → `OnboardingScreen`, `true` → normal `NavHost` Library). Requests storage/file access (`READ_EXTERNAL_STORAGE` pre-33 / `READ_MEDIA_IMAGES|VIDEO|AUDIO` on Tiramisu) for EPUB import + future auto-scan and `POST_NOTIFICATIONS` (Tiramisu+) for future features via `rememberLauncherForActivityResult(RequestMultiplePermissions/RequestPermission)`, checks via `ContextCompat.checkSelfPermission`, shows Granted/Not granted status, and handles denial gracefully (manual SAF import via Library + still works, notifications silent, Skip/Get started always enabled). After completion, `repo.setHasSeenOnboarding(true)` + `onComplete` routes to normal `Library` (NavHost). Built on top of existing `FolioNavHost`/`FolioTheme` — not a rewrite; structural inspiration from `book-story-master`’s `StartScreen` (pager + sections) only.
- **Manifest permissions** — `AndroidManifest.xml`: added `READ_EXTERNAL_STORAGE` (`maxSdkVersion 32`), `READ_MEDIA_IMAGES|VIDEO|AUDIO`, `POST_NOTIFICATIONS` for onboarding upfront requests (still optional, manual import via SAF without them).

### Changed

- `app/src/main/java/com/makemission/folio/data/settings/SettingsRepository.kt:1-55` — added `KEY_HAS_SEEN_ONBOARDING`, `hasSeenOnboarding` Flow + `setHasSeenOnboarding` (reuse same DataStore).
- `app/src/main/java/com/makemission/folio/ui/onboarding/OnboardingScreen.kt` — new: `OnboardingScreen` (pager 4, FolioTheme, illustrations, permission launchers, Skip/Next/Get started, DataStore flag), helpers `storagePermissions()`, `checkStorageGranted()`, `checkNotificationsGranted()`.
- `app/src/main/AndroidManifest.xml:1-38` — added `<uses-permission>` for `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `POST_NOTIFICATIONS`.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-105` — extended `FolioRoute` with `Onboarding`, `FolioNavHost` now gates on `hasSeenOnboarding` Flow (null→loading, false→OnboardingScreen with `scope.launch set true`, true→NavHost Library start), kept existing `Library`/`Reader`/`Vocabulary`/`Settings` routes (extend, not rewrite).
- `README.md:1-175` — intro now lists onboarding, project structure adds `data/settings` hasSeenOnboarding + `ui/onboarding/OnboardingScreen` + navigation onboarding gate, new Onboarding bullet (pager, permissions, graceful, DataStore-gated, FolioTheme).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL`.
- Verified first-launch only: fresh install → `hasSeenOnboarding` false → `OnboardingScreen` shown; after Get started/Skip → flag true → `Library`; relaunch → `Library` directly.
- Verified DataStore reuse: same `folio_settings` file, no second DataStore.
- Verified FolioTheme: onboarding uses `FolioTheme` colors/typography/Canvas illustrations, not generic.
- Verified permissions: Allow → `checkSelfPermission` shows Granted; Deny → status “Not granted — manual import still works”, app remains usable (Library + import via SAF still works); notification permission on <33 auto-granted.
- Verified routing: after onboarding completes, `NavHost` Library shown as normal; no rewrite of `FolioTheme`/`FolioNavHost` structure.

---

## Session 20 — 2026-09-09 — Settings Screen + Swipe Navigation + “Always show progress bar” (DataStore)

Branch: `main`.

### Built

- **Settings screen (Folio-themed, room to grow, DataStore persistence)** — `ui/settings/SettingsScreen.kt`: `Scaffold` with Folio `background` + editorial `headlineLarge` “Settings” header + `LazyColumn` and `SettingsHero` (amber/burgundy/green flat illustration, `RoundedCornerShape(14dp)`), organized into three clear `SettingsSection` cards (`Reading` / `Appearance` / `Privacy/Data`, uppercase `labelMedium` primary + `bodySmall` subtitle, `16dp` `surface` cards with `1dp` elevation). `Reading` holds `SettingsToggleRow` for **“Always show progress bar”** (`Switch` with Folio primary/burgundy track, `collectAsState` from `SettingsRepository.alwaysShowProgressBar` + `scope.launch { repo.setAlwaysShowProgressBar }`); `Appearance` and `Privacy/Data` currently have `SettingsInfoRow` placeholders (“Folio theme”, “Adaptive contrast”, “Local-only reading”, “Caches”) to show structure and allow growth. Uses existing `FolioTheme` (deep green `#004F39`, burgundy `#780116`, amber `#F7B538`, heavy sans + serif) — not a generic Android settings page; structural inspiration from `book-story-master`’s `SettingsScreen`/`SettingsContent` sections only, no code copied.
- **Persistence via DataStore (not in-memory)** — `data/settings/SettingsRepository.kt`: `Context.folioSettingsDataStore by preferencesDataStore("folio_settings")`, `booleanPreferencesKey("always_show_progress_bar")`, `Flow<Boolean>` `alwaysShowProgressBar` (default false) + `suspend setAlwaysShowProgressBar`, singleton `get(context)`, survives app restarts.
- **Swipe-right navigation (Library → Settings, extends navigation)** — `navigation/FolioNav.kt`: added `FolioRoute.Settings("settings")` and `composable(Settings)` → `SettingsScreen(onBack=pop)`, extended existing `FolioNavHost` (did not rewrite). `ui/library/LibraryScreen.kt`: added `onSettingsClick: () -> Unit` param (both overloads) to `LibraryScreen` + `LibraryHeader` now a `Row` with “⚙ Settings” `TextButton` for discoverability, and `Column` wrapping content has `Modifier.pointerInput { detectHorizontalDragGestures(totalDx>120f → onSettingsClick, consume) }` so swiping right on Library (horizontal drag >120px) opens Settings; vertical scroll (LazyVerticalGrid) not affected.
- **Reading progress bar respects setting (overrides tap-to-hide)** — `ui/reader/ReadingScreen.kt`: `ReadingScreenContent` now collects `alwaysShowProgressBar` via `SettingsRepository.get(context).alwaysShowProgressBar.collectAsState(false)` and passes `alwaysShowProgressBar` to `SingleColumnReadingContent`/`TwoColumnReadingContent` (new param). Bottom bar restructured from single `AnimatedVisibility(visible=chromeVisible)` wrapping `Column` to `Column` with two inner `AnimatedVisibility`: `Row` (Page + time) `visible=chromeVisible` and `ReadingProgressBar` `visible=chromeVisible || alwaysShowProgressBar` (both with `slideIn/Out + fade`), so when toggle is on the bar stays visible despite tapping reading area (chromeVisible false), otherwise hides as before. Built on top of existing chrome/velocity/true-page logic.

### Changed

- `gradle/libs.versions.toml:1-55` — added `datastore = "1.1.1"` + `androidx-datastore-preferences` library.
- `app/build.gradle.kts:1-74` — added `implementation(libs.androidx.datastore.preferences)`.
- `app/src/main/java/com/makemission/folio/data/settings/SettingsRepository.kt` — new: DataStore file, singleton, Flow + setter.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt` — new: Folio-themed Scaffold + sections + toggle + hero.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-72` — added `FolioRoute.Settings`, extended `FolioNavHost` with `LibraryScreen(onSettingsClick=navigate Settings)` + `composable(Settings)`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1-270` — added `onSettingsClick` param (both overloads), `LibraryHeader` row with Settings button, `Column` swipe `pointerInput` + `detectHorizontalDragGestures` (>120px), imports `detectHorizontalDragGestures` + `pointerInput`.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1070` — added `SettingsRepository` collect, `alwaysShowProgressBar` param to both column contents, restructured bottom `Column` to two `AnimatedVisibility` (Row chromeVisible, progressBar chromeVisible||alwaysShow).
- `README.md:1-165` — intro now lists swipe to Settings + persistent always-show progress bar + Settings screen sections, tech stack adds DataStore, project structure adds `data/settings/SettingsRepository` + `ui/settings/SettingsScreen` + navigation note + library swipe note + reader DataStore note, Library bullet adds Settings navigation, Reading bullet adds frictionless progress bar override + new Settings screen bullet (sections, DataStore, FolioTheme).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL`.
- Verified swipe: horizontal drag >120px on Library triggers `onSettingsClick` → `navController.navigate(settings)`; vertical scroll unaffected.
- Verified DataStore persistence: toggle on → `SettingsRepository.set...` writes to `folio_settings.preferences_pb`, kill + relaunch → `collectAsState` reads true, progress bar stays after tap; toggle off → hides as before.
- Verified FolioTheme: Settings uses `FolioDark/LightColorScheme`, `FolioTypography`, deep green/burgundy/amber, not generic.
- No rewrite: `FolioNavHost` extended, `FolioTheme` untouched, `ReadingScreen` topBar still `chromeVisible` only.

---

## Session 19 — 2026-09-09 — Bounding-Box Image Expansion (white-margin stripping, on-device, cached)

Branch: `main`.

### Built

- **Bounding-Box Image Expansion (§5 — lightweight contour detection, tap to expand, cached, full-width)** — `data/image/BoundingBoxCropper.kt`: pure on-device algorithm that, instead of rendering diagrams awkwardly small, runs a contour-detection pass on tap to find the bounding box of non-white pixels. Scans `Bitmap`'s `IntArray` pixels (via single `getPixels`), `isWhite` checks `alpha < 10` (transparent) or `R,G,B > 242` (tolerates JPEG artifacts near 255), step-samples every 2nd pixel for >3MP images then refines edge bands at 1px, finds `minX/maxX/minY/maxY`, adds `2px` padding, returns `Rect`. `crop()` returns original gracefully when all-white, content `<8px`, or already tightly cropped (`maxMargin ≤4px` + `coverage ≥92%` or `≥98%`), so tightly cropped images aren't over-cropped or distorted. `data/image/CroppedImageCache.kt`: caches the cropped result to `filesDir/bbox_cache/<sha256>.png` (SHA-256 of `key:WxH`, 24 hex chars, `MAX_DISK_FILES=80` LRU by `lastModified`) plus in-memory `Map`, `getOrCreate()` via `BoundingBoxCropper.crop()` on `Dispatchers.Default`, no network. `ui/reader/components/ExpandableDiagram.kt`: demo diagram for `ReadingScreen` that builds on top of existing `EpubParser`/`DiagramPlaceholder` (not a rewrite). Generates a sample `600×360` bitmap with large baked white margins (`100px` sides, `80px` top/bottom, white `AndroidColor.WHITE` background, inner amber `400×160` diagram with Folio burgundy accents/bars/text), shows `fillMaxWidth` thumbnail with `aspectRatio` + `ContentScale.FillWidth`; on tap (via `clickable`) checks `CroppedImageCache.getBitmap()`, otherwise runs detection/crop off the main thread, caches via `putBitmap()`, then swaps to the cropped `Image(bitmap.asImageBitmap())` which now fills the available width on both phone (single-column `20dp` padding) and tablet (two-column spread) without distortion. Status text shows `600×360 → 404×164 • cached • phone & tablet full-width`. `ExpandableEpubImage` reuses the same cropper/cache for real EPUB `<img>` bytes (extension point for `EpubParser` without rewriting it). Pure on-device, `book-story-master` structure only.

### Changed

- `app/src/main/java/com/makemission/folio/data/image/BoundingBoxCropper.kt` — new: `isWhite`, `findBoundingBox` (O(W×H) with step sampling + refine, padding), `crop` (graceful tightly-cropped handling).
- `app/src/main/java/com/makemission/folio/data/image/CroppedImageCache.kt` — new: `keyFor`, `cacheFile`, `isCached`, `getBitmap`, `putBitmap` (eviction), `getOrCreate`, `getOrCreateForFile`, `clear`, memory + disk cache.
- `app/src/main/java/com/makemission/folio/ui/reader/components/ExpandableDiagram.kt` — new: `ExpandableDiagram` (sample bitmap generation, tap → detection + cache + expand full-width, `LaunchedEffect` cached load, status text, dual-mode original/cropped toggle) + `ExpandableEpubImage` (generic bitmap with same behavior, `aspectRatio`/`FillWidth`).
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1050` — imported `ExpandableDiagram`, replaced `DiagramPlaceholder` items after chapter 0 in both `SingleColumnReadingContent` (`diagram-$chapterIndex`) and `TwoColumnReadingContent` (`L-diagram-$chapterIndex`) with `ExpandableDiagram` (preserves lasso `HighlightOverlay` separately, now diagram is tappable for bounding-box expansion while stylus still lasso-extracts). Kept `DiagramPlaceholder` definition for reference (not removed, built on top).
- `README.md:1-164` — intro now lists bounding-box image expansion, project structure adds `data/image/{BoundingBoxCropper, CroppedImageCache}` + `ui/reader/components/ExpandableDiagram` with note `+ expandable diagram`, Reading-screen section adds Bounding-Box bullet (contour detection, thresholds, graceful, pure on-device, cached, full-width phone/tablet, on top of EpubParser/placeholder).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint).
- Verified tap-to-expand: generated sample has white margins, tap runs `BoundingBoxCropper.crop()` on `Dispatchers.Default`, cached file appears in `filesDir/bbox_cache`, re-tap uses cache without recomputation, cropped fills `fillMaxWidth`.
- Verified tightly cropped: small-margin image returns original (no distortion), full-white returns original.
- Verified phone/tablet: `fillMaxWidth` + `aspectRatio` ensures cropped fills available width in both `SingleColumn` (20dp padding) and `TwoColumn` spread.

---

## Session 18 — 2026-09-09 — Knuth-Plass Line Breaking / Orphan & Widow Control (squared-off)

Branch: `main`.

### Built

- **Knuth-Plass Line Breaking / Orphan & Widow Control (§5 — scoring, micro-kerning, squared-off, coordinated with True-Page)** — `ui/reader/KnuthPlassEngine.kt`: adapts Knuth-Plass to score breaks across a whole paragraph (not greedily). Measures each paragraph on the same virtual canvas as `TruePageEngine` (same column width `40dp` phone / `(screen-52dp)/2` tablet, same `bodyLarge` 17/27 phone / `bodyMedium` tablet + `BionicReading` bold spans, same `lineHeight`/`availableHeight`/`perScreenHeight*2` for tablet spread), computes `linesPerPage = perScreenHeight/lineHeight` and prefix start lines, then for each paragraph detects page splits and orphan (`linesOnFirstPage==1`) / widow (`linesOnLastPage==1`) penalties (`ORPHAN_PENALTY=12000`, `WIDOW_PENALTY=12000`). Tries candidate `letterSpacing` deltas `[-0.4,-0.2,-0.1,0,0.15,0.3,-0.35,0.5,0.6]sp`, re-measures `lineCount` via `textMeasurer.measure(..., styleWithDelta, Constraints(maxWidth))`, scores as `stretchBadness=delta²*600 + orphan/widow penalties + tie-breaker |delta|*2`, picks minimal-score delta. `rememberKnuthAdjustments(chapters, isTablet, bionic)` is `remember`ed on `screenWidthDp/screenHeightDp/orientation/fontScale/density/bionicEnabled/chaptersKey` (same keys as TruePage, cached) and returns `Map<"cIdx-pIdx", KnuthAdjustment(letterSpacingDelta, useJustify)>`; phone and tablet each compute separately so page breaks agree (tablet uses `perScreen*2`). `ReadingScreen` (`SingleColumnReadingContent` + `TwoColumnReadingContent`) now computes `knuthAdjustments` alongside `truePageInfo` and wraps each paragraph `Text`'s `TextStyle` (`bodyLarge`/`bodyMedium`) with `copy(letterSpacing = base.letterSpacing + delta, textAlign = Justify)` when adjustment exists, giving squared-off, printed-book look while eliminating single-line stranded pages. Pure on-device, builds on existing text layout (no rewrite); `book-story-master` structure only.

### Changed

- `app/src/main/java/com/makemission/folio/ui/reader/KnuthPlassEngine.kt` — new: `KnuthPlassEngine` (`ORPHAN_PENALTY`, `WIDOW_PENALTY`, `scoreForDelta`), `KnuthAdjustment`, `rememberKnuthAdjustments(...)` (virtual canvas sharing, TextMeasurer, candidate scoring, Justify).
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1045` — added `Knuth-Plass` to `SingleColumnReadingContent` (computed `knuthAdjustments` for phone, changed `itemsIndexed` to capture `paraIndex`, Text now uses `baseStyle.copy(letterSpacingDelta, textAlign=Justify)` with `readingText`) and `TwoColumnReadingContent` (added `knuthAdjustments` for tablet, fixed `L-c`/`R-c` keys to global `cIdx-pIdx` with `mid` offset, left/right Text likewise wrapped with knuth style, added `import sp`), fixed `TwoColumn` bottom chrome to use `readingBackground`/`readingText` (was still `MaterialTheme` after adaptive addition), imported `sp`.
- `README.md:1-160` — intro now lists Knuth-Plass orphan/widow squared-off, project structure adds `KnuthPlassEngine.kt` + reader note, Reading-screen section adds Knuth-Plass bullet (scoring, micro-kerning, squared-off, coordination with TruePage, phone/tablet, cached).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (fixed `TextUnit` plus `sp` import).
- Verified coordination: `KnuthPlass` uses identical `availableWidthPx`/`availableHeightPx`/`lineHeightPx` math as `TruePageEngine` so page breaks agree, not conflicting.
- Verified phone/tablet: `rememberKnuthAdjustments` called with `isTabletLandscape=false/true` so spread `perScreen*2` correctly avoids single-line widows on spreads.

---

## Session 17 — 2026-09-09 — Colorimetric Contrast Optimization (ambient-light WCAG 7:1)

Branch: `main`.

### Built

- **Colorimetric Contrast Optimization (§5 — ambient-light, WCAG 7:1, smooth, toggle, graceful fallback)** — Builds on top of existing `FolioTheme` (no rewrite): `ui/theme/AdaptiveContrastEngine.kt` implements WCAG relative luminance (`L = 0.2126*Rlin+0.7152*Glin+0.0722*Blin`, `contrast = (Llighter+0.05)/(Ldarker+0.05)`) and `luxToFactor = log10(lux+1)/log10(10001)` to lerp the Folio palette (dark: `#001A12`→`#004F39`→`#0B5C45`; light: `#F1E8D2`→`#FBF6EC`→`#FFFBF0`) then binary-searches text toward white/black via `ensureContrast` until **7:1** is met; `ui/theme/AmbientLightSensor.kt` provides `rememberAmbientLightLux(enabled)` using `SensorManager`/`TYPE_LIGHT` with exponential moving average (`alpha 0.15`) and `SENSOR_DELAY_NORMAL`, returning `State<Float?>` (null on devices without sensor). `ReadingScreen` (`ReadingScreenContent`) adds `adaptiveEnabled` toggle (`rememberSaveable`, TopAppBar `Contrast Auto`/`Contrast Fixed`/`No sensor` button, disabled when `!hasAmbientLightSensor()`), reads `rawLux`, computes `adaptivePair` via `AdaptiveContrastEngine.adaptivePair(baseBg, baseText, lux, isDark)`, and smooths hex shifts with `animateColorAsState(tween 800ms, LinearOutSlowInEasing)` so changes feel gradual, not jarring flicker. Adapted `readingBg`/`readingText` (and `readingSurface`/`readingOnSurface`) are passed to `SingleColumnReadingContent`/`TwoColumnReadingContent` (new `readingText`/`readingBackground` params) and used for `Scaffold containerColor`, `Box background`, `TopAppBar`, and body `Text` colors; when `!adaptiveEnabled` or `rawLux==null` or no sensor, gracefully falls back to fixed `FolioTheme` colors — no crash on emulators. Pure on-device, deterministic; `book-story-master` used for structure only.

### Changed

- `app/src/main/java/com/makemission/folio/ui/theme/AdaptiveContrastEngine.kt` — new: `relativeLuminance`, `contrastRatio`, `luxToFactor`, `adaptiveBackground`, `ensureContrast`, `adaptivePair` (TARGET 7:1).
- `app/src/main/java/com/makemission/folio/ui/theme/AmbientLightSensor.kt` — new: `rememberAmbientLightLux(enabled, alpha=0.15)` (SensorManager + EMA + State<Float?>), `hasAmbientLightSensor(context)` helper, graceful null fallback.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1010` — added imports (`animateColorAsState`, `isSystemInDarkTheme`, `AdaptiveContrastEngine`, `AmbientLightSensor`), `adaptiveEnabled` state + `isDark`/`baseBg`/`baseText`/`hasSensor`/`luxState`/`adaptivePair`/`targetBg`/`targetText`/`animatedBg`/`animatedText`/`readingBg`/`readingText`/`readingSurface` logic inside `ReadingScreenContent`, TopAppBar third action toggle, `Scaffold containerColor` + `Box background` + `TopAppBar` colors now use `readingBg`/`readingSurface`, passed `readingText`/`readingBackground` to both column contents and updated all body `Text` colors (`headlineSmall`/`titleMedium`/`bodyLarge`/`bodyMedium`) and bottom chrome (`surface`/`onSurfaceVariant` → `readingBackground`/`readingText`) to use adapted colors.
- `README.md:1-158` — intro now lists ambient-light adaptive contrast via WCAG 7:1, project structure adds `theme/{AdaptiveContrastEngine, AmbientLightSensor}` + reader toggle note, Reading-screen section adds Colorimetric Contrast Optimization bullet (sensor + WCAG + lux mapping + smoothing + toggle + fallback, on top of FolioTheme).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL`.
- Verified smooth: EMA `alpha 0.15` in sensor + `animateColorAsState(tween 800ms)` prevents flicker; verified WCAG: `contrastRatio` math matches spec 7:1; verified toggle enables/disables; verified graceful fallback: `hasAmbientLightSensor()` check, `luxState` null → fixed theme, no crash on emulator.
- No rewrite of `FolioTheme` — `Color.kt`/`Theme.kt` untouched, adaptive colors applied as overlay in `ReadingScreen`.

---

## Session 16 — 2026-09-09 — True-Page Calculation Engine (virtual canvas, absolute pages)

Branch: `main`.

### Built

- **True-Page Calculation Engine (§5 — off-screen virtual canvas, cached)** — `ui/reader/TruePageEngine.kt`: pre-computes the entire book's text layout for the current screen dimensions and font size on a virtual canvas, then injects synthetic page breaks to produce absolute page numbers (`Page 45 of 312`) instead of vague “location” metrics. Uses `rememberTextMeasurer` constrained to the actual column width (phone: `screenWidth-40dp`, tablet: `(screenWidth-52dp)/2`) with the real Folio styles (`bodyLarge` 17/27 phone, `bodyMedium` tablet for body, `headlineSmall`/`titleMedium` for titles) including `BionicReading` bold spans when `bionicEnabled` — so the measurement matches rendered text. Heights per flat item (titles + paragraphs + diagram 172dp + gap 17dp) are summed via `textMeasurer.measure(...).size.height`; `availableHeightPx` is `screenHeight-140dp` and `perScreen = availableHeight` (phone) or `*2` (tablet spread — physical page turns, not columns), so tablet page count reflects spreads (`ceil(totalHeight / perScreen)`), not individual columns. Page for scroll position is `floor(heightBeforeFlat / perScreen)+1` via prefix sums; current page derives from `firstVisibleItemIndex` via `derivedStateOf` (cheap) while `TruePageInfo` is `remember`ed on `screenWidthDp/screenHeightDp/orientation/fontScale/density/bionicEnabled/chaptersKey` only — recalculates on rotate or typography change, not on every scroll/recomposition. Built on top of existing `SingleColumnReadingContent`/`TwoColumnReadingContent` + `BionicReading`/`VelocityEstimator` layouts — not a rewrite; `book-story-master` consulted for structure only.
- **Display near progress bar (§5)** — Both phone (`SingleColumnReadingContent`) and tablet (`TwoColumnReadingContent`) now show `Page X of Y` in the bottom chrome `Row` alongside `VelocityEstimator`'s time-remaining, directly above `ReadingProgressBar` inside the same `AnimatedVisibility` (hides in immersive mode). Tablet's `rightState` hoisted to top level so spread page tracks earliest visible content; phone uses `listState.firstVisibleItemIndex`. Label is `labelSmall`/`onSurfaceVariant`, e.g., `Page 45 of 312`.

### Changed

- `app/src/main/java/com/makemission/folio/ui/reader/TruePageEngine.kt` — new: `TruePageInfo`, `rememberTruePageState(...)` (virtual canvas + prefix sums + cached), `rememberTruePageStateForTablet`.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-915` — added `True-Page` to `SingleColumnReadingContent` (`truePageInfo` + `currentPage` via `derivedStateOf`, `Row` with `Page X of Y` + timeRemaining above progress bar) and `TwoColumnReadingContent` (hoisted `rightState`, added `truePageInfo` with `isTabletLandscape=true` and `currentPage` derived from `leftState`, `Row` with page label + timeRemaining), kept existing velocity/progress/highlight/X-Ray/dictionary logic.
- `README.md:1-157` — intro now lists true-page numbers via virtual-canvas pre-computation, project structure adds `TruePageEngine.kt` + `+ true pages near progress` note, Reading-screen section updates progress to v6, replaces stale “more sophisticated pagination can replace later” with True-Page bullet (virtual canvas detail, cached rotation/font, phone vs tablet spread).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint issues).
- Verified cached behavior: `remember` keys include screen dimensions/orientation/fontScale/density/bionicEnabled — scrolling does not recompute totalPages, only `derivedStateOf` for current page.
- Verified tablet physical pages: `perScreenHeight*2` in `TruePageEngine` ensures spread counts as one physical turn.

---

## Session 15 — 2026-09-09 — Spaced Repetition Vocabulary (SM-2, on-device)

Branch: `main`.

### Built

- **Spaced Repetition Vocabulary (§5 — SM-2, pure on-device, deterministic)** — Extends dictionary lookup without rewriting `DictionaryRepository`/`DictionaryPopup`. `ReadingViewModel.trackVocabulary(word, definition)` is invoked whenever a double-tap lookup succeeds (definition non-null); `ReadingScreen` wires `onWordDoubleTap` → `DictionaryRepository.lookup` → `dictPopup` + `onTrackVocabulary` (added param to `ReadingScreenContent`). Words saved locally in Room (extended schema, not replaced): `data/db/entity/VocabularyCard.kt` (`word` PK lowercased, `definition`, `easeFactor=2.5`, `intervalDays`, `repetitions`, `dueAt`, `lastReviewedAt`, `reviewCount`, `createdAt`), `data/db/dao/VocabularyDao.kt` (`observeAll`, `observeDue(now)`, `getDue`, `getByWord`, `upsert`, `delete`, `observeDueCount`, `observeTotalCount`), `FolioDatabase` v5→v6 (`VocabularyCard`, `vocabularyDao()`, `fallbackToDestructiveMigration(true)`).
- **SM-2 scheduling (§5 — SuperMemo-2)** — `data/vocabulary/Sm2.kt`: deterministic on-device algorithm, no network. Quality mapping Again=0, Hard=3, Good=4, Easy=5. On `quality<3` resets `repetitions=0, interval=1`; else `interval = 1` (reps 0), `6` (reps 1), otherwise `(interval*ease).roundToInt()`, then `reps++` and `ease += 0.1-(5-q)*(0.08+(5-q)*0.02)` clamped to `MIN_EASE=1.3`. `dueAt = now + interval*24h`. `newCard(word, definition)` creates due-now cards. `LibraryViewModel` now observes `dueCount` non-intrusively; `ReadingViewModel.trackVocabulary` de-duplicates (update definition if changed, keep SM-2 fields) and `VocabularyViewModel` applies `Sm2.schedule` on rating.
- **Review flow (§5)** — `ui/vocabulary/VocabularyViewModel.kt` (`AndroidViewModel`, `allCards`/`dueCards`/`dueCount` via `VocabularyDao`, `currentReview`/`showDefinition` StateFlows, `startReview`/`revealDefinition`/`rateCurrent(q: Int)` → `Sm2.schedule` → `upsert` → next due, `dismissReview`) and `ui/vocabulary/VocabularyScreen.kt` (Folio-themed `Scaffold` + `TopAppBar`, states: due-list with “Start review” + `LazyColumn` cards, `ReviewCard` centered word + amber rule + “Show definition” → reveal → Again/Hard/Good/Easy `TextButton`s → `Sm2.AGAIN/HARD/GOOD/EASY`, empty “No vocabulary yet” hint, “All caught up!” list). No network, no AI.
- **Discreet surfacing (§5 — non-intrusive)** — `ui/library/LibraryScreen.kt` now shows a `VocabularyTeaser` `Card` below the header (above the grid): “Vocabulary — X due for review” with a small amber badge (`primary` rounded `12.dp`) when `dueCount>0`, otherwise “No words due — keep reading” + `Open` button. Tapping navigates via `FolioNav` (`FolioRoute.Vocabulary`) to `VocabularyScreen`. `LibraryViewModel.dueVocabularyCount` is `vocabularyDao.observeDueCount().stateIn(...)` so badge updates reactively. No popups interrupting reading.
- Built on top of existing `DictionaryRepository`/`DictionaryPopup` — not a rewrite; looked at `book-story-master` for structural inspiration only (no code copied).

### Changed

- `app/src/main/java/com/makemission/folio/data/db/entity/VocabularyCard.kt` — new: Room entity for SM-2.
- `app/src/main/java/com/makemission/folio/data/db/dao/VocabularyDao.kt` — new: Room DAO with due/all flows.
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt:1-43` — v5→v6, added `VocabularyCard` + `vocabularyDao()`.
- `app/src/main/java/com/makemission/folio/data/vocabulary/Sm2.kt` — new: `Sm2.schedule`/`newCard`, constants AGAIN/HARD/GOOD/EASY, MIN_EASE, interval/ease logic.
- `app/src/main/java/com/makemission/folio/ui/vocabulary/VocabularyViewModel.kt` — new: `AndroidViewModel` with `allCards`/`dueCards`/`currentReview`/`showDefinition`, `startReview`/`rateCurrent`/`dismissReview`.
- `app/src/main/java/com/makemission/folio/ui/vocabulary/VocabularyScreen.kt` — new: `VocabularyScreen` + `ReviewCard` (states: review, due-list, empty, all-caught-up), `width` import fix.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-69` — added `FolioRoute.Vocabulary` (`vocabulary`), `LibraryScreen(onVocabularyClick)` → navigate, `composable(Vocabulary)` → `VocabularyScreen(onBack=pop)`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1-240` — added `VocabularyTeaser` discreet badge card (imports `Alignment`, `width` handling), wired `dueVocabularyCount` from ViewModel, added `onVocabularyClick` param to both overloads, column layout (teaser above grid).
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:1-150` — added `vocabularyDao` + `dueVocabularyCount` Flow.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-234` — added `vocabularyDao`, `trackVocabulary(word, definition)` (lowercased de-dupe, `Sm2.newCard` on new, definition-update otherwise).
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-887` — added `onTrackVocabulary` param to `ReadingScreenContent`, wired `viewModel::trackVocabulary`, `onWordDoubleTap` now also calls `onTrackVocabulary(word, def)` when `def != null` (extends, doesn't rewrite dictionary popup).
- `README.md:1-150` — intro now lists SM-2 vocabulary, tech stack Room v6, project structure adds `vocabulary/Sm2` + `db/VocabularyCard/VocabularyDao` + `ui/vocabulary/` + navigation vocabulary route + library teaser/badge notes, Library-screen section adds Vocabulary teaser bullet (non-intrusive badge), Reading-screen section adds Spaced Repetition Vocabulary bullet (SM-2 algorithm, tracking, review flow, on-device).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (fixed missing `Alignment` import in `LibraryScreen.kt` and `width` import in `VocabularyScreen.kt`).
- Verified `DictionaryRepository`/`DictionaryPopup` not rewritten — extended via `ReadingViewModel.trackVocabulary` + `ReadingScreen` callback.
- Verified SM-2 determinism: `Sm2.schedule` pure function, no network, interval/ease math matches SuperMemo-2 spec (min EF 1.3).
- Verified discreet surfacing: badge only on Library, review via separate `Vocabulary` route, reading never interrupted.

---

## Session 14 — 2026-09-08 — Offline dictionary (WordNet-style, double-tap)

Branch: `main`.

### Built

- **Offline dictionary (§6 — on-device, no network)** — `assets/dictionary.json` (compact open-source WordNet-style, ~120 entries, permissively-licensed, `{"word": "definition"}` lowercased keys) bundled as an app asset; `data/dictionary/DictionaryRepository.kt` loads it once via `assets.open` + `JSONObject` and caches in memory (synchronized, case-insensitive, punctuation-stripped, singular fallback). Lookup is `lowercase().trim(punctuation)` with no network calls.
- **Double-tap lookup (§6)** — `ui/reader/components/DictionaryPopup.kt`: sleek `Dialog`-based popup (Folio-themed `Card`, amber rule, `titleMedium` word + `bodyMedium` definition, “No definition found” graceful state). `ui/reader/ReadingScreen.kt` now captures `TextLayoutResult` per paragraph (`onTextLayout`) and adds `Modifier.pointerInput { detectTapGestures(onDoubleTap) }` that uses `layout.getOffsetForPosition` + letter-boundary expansion to extract the tapped word, then `DictionaryRepository.lookup(word, context)` and shows the popup. Works with `PointerType.Stylus` highlighting (stylus-only `pointerInteropFilter` lets finger pass through) and single-tap chrome toggle (double-tap consumes, single-tap still toggles) without conflict, on both phone (single-column) and tablet (two-column) layouts. Handled for both `bionicEnabled` annotated and plain `AnnotatedString` cases.
- Built on top of existing text rendering — `BionicReading` paragraph `Text` not rewritten, just wrapped with double-tap handling and `onTextLayout`.

### Changed

- `app/src/main/assets/dictionary.json` — new: ~120 WordNet-style entries (library, folio, typography, bionic, etc., original definitions, MIT-permissive).
- `app/src/main/java/com/makemission/folio/data/dictionary/DictionaryRepository.kt` — new: asset load, `lookup` (case-insensitive, punctuation + singular fallback).
- `app/src/main/java/com/makemission/folio/ui/reader/components/DictionaryPopup.kt` — new: `Dialog` + `Card` (16dp, surface, amber rule) with word/definition/Close.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-832` — added `dictPopup` state + `onWordDoubleTap` (lookup → popup), `DictionaryPopup` display after lasso dialog, updated `SingleColumnReadingContent` + `TwoColumnReadingContent` paragraph `Text` to capture `TextLayoutResult` and `pointerInput` double-tap (works with `bionicEnabled` via `AnnotatedString`), added imports for `TextLayoutResult`/`detectTapGestures`/`DictionaryRepository`.
- `README.md:1-142` — intro now lists offline dictionary, tech stack adds `assets/dictionary.json`, project structure adds `dictionary/` + `assets/dictionary.json` + `DictionaryPopup` in reader components, Reading-screen section adds offline dictionary bullet (§6) and updates structure.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 13 — 2026-09-08 — On-device X-Ray (TF-IDF per-chapter index)

Branch: `main`.

### Built

- **X-Ray (§5 — TF-IDF, pure on-device)** — `data/xray/XRayExtractor.kt`: local deterministic TF-IDF that tokenizes each chapter, extracts capitalized proper-noun candidates via Regex `\b[A-Z][a-z]{2,}\b` filtered by a stopword set (no NLP tagger, no dictionary, no network), computes `tf = count/totalWords`, `idf = ln(totalChapters/df)`, `score = tf*idf`, and keeps top 8 per chapter. `data/xray/XRayTerm.kt` holds `term`/`score`/`chapterIndices`/`totalFrequency`. `data/xray/XRayCache.kt` persists the per-book, per-chapter index to `filesDir/xray/<bookId>.json` (org.json) so it is computed once on first open/import and reused, plus in-memory via `ReadingViewModel.xrayIndex` (`StateFlow<Map<Int, List<XRayTerm>>>`).
- **Bottom sheet (§5)** — `ui/reader/components/XRayBottomSheet.kt`: minimalist `ModalBottomSheet` (skipPartiallyExpanded) opened via an *X-Ray* top-bar button in `ReadingScreen`; shows per-chapter distinctive terms with score and frequency, tap to expand and see which chapters the term appears in. Handles loading and empty states.
- **Integration** — `ui/reader/ReadingViewModel.kt` now exposes `xrayIndex` + `isXRayLoading`, launches `XRayCache.load` then `XRayExtractor.extract` on chapters load and caches; `ui/library/LibraryViewModel.kt` invalidates the X-Ray cache on import/reimport so the new book's index recomputes. Built on top of existing `EpubParser` output — parser not rewritten; looked at `book-story-master` bottom-sheet structure for inspiration only.

### Changed

- `app/src/main/java/com/makemission/folio/data/xray/XRayTerm.kt` — new.
- `app/src/main/java/com/makemission/folio/data/xray/XRayExtractor.kt` — new: stopwords, capitalizedWord regex, `extract` (tf-idf).
- `app/src/main/java/com/makemission/folio/data/xray/XRayCache.kt` — new: file cache `filesDir/xray/<bookId>.json` (load/save/invalidate).
- `app/src/main/java/com/makemission/folio/ui/reader/components/XRayBottomSheet.kt` — new: per-chapter `ModalBottomSheet` with single-chapter and whole-book overloads.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-175` — added `xrayIndex`/`isXRayLoading`, `launchXRayIfNeeded` (cache load → extract → save).
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:1-143` — invalidates `XRayCache` on import/reimport.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-832` — added `showXRay` state, `X-Ray` top-bar button, `XRayBottomSheet` (whole-book per-chapter) wired to `xrayIndex`/`isXRayLoading`, collects `xrayIndex` from ViewModel.
- `README.md:1-142` — intro now lists on-device X-Ray, tree adds `xray/{...}` + `XRayBottomSheet`, Reading-screen section adds X-Ray bullet (§5) and updates structure.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 12 — 2026-09-08 — LCS annotation anchoring (highlights survive EPUB updates)

Branch: `main`.

### Built

- **LCS anchors (§5 — pure on-device)** — `data/anchor/LcsAnchor.kt`: deterministic Longest Common Subsequence diff that stores each highlight's surrounding paragraph snippet (`anchorText`, ~80 chars) as a contextual anchor instead of just raw stroke coordinates. On book reopen or reimport (updated EPUB file), `ReadingViewModel.reanchorHighlightsIfNeeded` scans the newly parsed chapters for the closest anchor match via LCS similarity (`lcsLength / anchorLen`, threshold 0.55, sliding window for long paragraphs) and reattaches the highlight to the correct `chapterIndex`; if no reasonable match it is left orphaned (`isOrphaned=true`) rather than crashing or guessing. Highlights therefore survive publisher typo-fix updates that shift byte offsets.
- **Capture + storage (extend, not rewrite)** — `data/db/entity/Highlight.kt` now carries `anchorText` + `isOrphaned`; `dao/HighlightDao.kt` adds `@Update`; `FolioDatabase` v4→v5 (`Highlight` + `BookEntity`, `fallbackToDestructiveMigration(true)`). `ui/reader/ReadingViewModel.kt` now captures the anchor via `LcsAnchor.snippetForHighlight(chapters, chapterIndex)` on `addHighlight` and runs the LCS scan on init; `LibraryViewModel` was taught reimport handling — importing an EPUB whose title already exists reuses the existing `BookEntity` id (updates `filePath`/`coverImagePath` in place) so the same `bookId` highlights are reanchored on next open instead of orphaning to a new book.
- Built on top of existing `Highlight`/`HighlightOverlay` with pressure/tilt Multiply true-ink — not a rewrite; looked at `book-story-master` annotation handling for structural inspiration only.

### Changed

- `app/src/main/java/com/makemission/folio/data/db/entity/Highlight.kt:1-44` — added `anchorText` + `isOrphaned`.
- `app/src/main/java/com/makemission/folio/data/db/dao/HighlightDao.kt:1-28` — added `@Update update`.
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt:1-37` — v4→v5.
- `app/src/main/java/com/makemission/folio/data/anchor/LcsAnchor.kt` — new: `lcsLength`, `similarity`, `findBestMatch`, `snippetForHighlight`, `MATCH_THRESHOLD`.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-175` — captures `anchorText` on `addHighlight`, runs `reanchorHighlightsIfNeeded` on init (LCS scan, update or orphan).
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:1-115` — reimport path: copy to temp, parse, title-match reuse existing `BookEntity` id, update cover/file, otherwise create new.
- `app/src/main/java/com/makemission/folio/data/db/dao/BookDao.kt:1-28` — added `getAll()` for title-match lookup.
- `README.md:1-142` — intro + tech stack (Room v5), project structure (`anchor/LcsAnchor`, `Highlight(anchorText)`), Reading-screen stylus bullet now documents LCS anchors.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 11 — 2026-09-08 — EPUB Import (SAF, private storage, cover extraction)

Branch: `main`.

### Built

- **Import button (§6 — Technical Foundation)** — `ui/library/LibraryScreen.kt` now has a FloatingActionButton (“+”, `primary`/`onPrimary`) that launches the Storage Access Framework (`ActivityResultContracts.OpenDocument` with `application/epub+zip` + `*/*`). The returned URI is copied into `filesDir/books/<uuid>_name.epub` (private storage — survives if the user moves/deletes the original), validated by parsing with the existing `EpubParser`, and persisted. Invalid/corrupted EPUBs show a `Snackbar` (“Could not parse EPUB…”) and do not crash.
- **Private storage + parsing** — `EpubParser` extended with `parse(File)` and `extractCoverToFile` (OPF `meta[name=cover]` → `properties="cover-image"` → id contains “cover” → save bytes to `covers/<id>.jpg/png`). Cover image (if any) is stored as `coverImagePath` alongside `filePath`.
- **Room schema (§6 — extend, not replace)** — `data/db/entity/BookEntity.kt` + `dao/BookDao.kt` (`observeAll`, `getById`, `insert`, `delete`); `FolioDatabase` v3→v4 (`BookEntity` added, `bookDao()`, `fallbackToDestructiveMigration(true)`). `data/model/Book.kt` now carries `filePath`/`coverImagePath` (nullable, curated samples keep `null`).
- **Library grid update** — `LibraryViewModel` (`AndroidViewModel`) exposes `books: Flow<List<Book>>` as `bookDao.observeAll().map { imported + curatedSampleBooks() }` (imported first, `FolioCoverPalette` fallback for color, `Snackbar` error flow, `isImporting` state) and `importEpub(uri, context)` (query `DISPLAY_NAME`, copy via `ContentResolver`, parse, cover extract, `insert`). `LibraryScreen` is now ViewModel-driven (`viewModel()` + `collectAsState`, `rememberLauncherForActivityResult`, `SnackbarHost`, `takePersistableUriPermission`).
- **Cover rendering** — `ui/library/components/BookCoverCard.kt` now shows the extracted cover via `coil.compose.AsyncImage` (`File(coverImagePath)`, `ContentScale.Crop`) with a scrim for legibility; falls back to palette color when no cover.
- **Reading per imported book** — `ui/reader/ReadingViewModel.kt` now checks `bookDao.getById(bookId).filePath` first (parse that `File` if present), then `assets/sample.epub`, then `sampleFallbackChapters`; display title prefers stored title. `navigation/FolioNav.kt` now uses the ViewModel-driven `LibraryScreen` (no longer passes `curatedSampleBooks()` directly), so tapping an imported cover opens it in the existing `ReadingScreen` with progress/highlights still working.
- Structure inspired by `book-story-master`’s file import flow (SAF → copy → parse) but Folio-specific and minimal.

### Changed

- `gradle/libs.versions.toml:20-46` — added `coil 2.7.0` + `coil-compose` library.
- `app/build.gradle.kts:60-68` — added `coil.compose` implementation.
- `app/src/main/java/com/makemission/folio/data/db/entity/BookEntity.kt` — new.
- `app/src/main/java/com/makemission/folio/data/db/dao/BookDao.kt` — new.
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt:1-37` — v3→v4, added `BookEntity`, `bookDao()`.
- `app/src/main/java/com/makemission/folio/data/model/Book.kt:17-22` — added `filePath`/`coverImagePath` nullable.
- `app/src/main/java/com/makemission/folio/data/epub/EpubParser.kt:1-326` — added `parse(File)`, `extractCoverToFile(InputStream/File)` (manifest cover lookup + save to `covers/`).
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt` — new: SAF copy→parse→cover→Room, `books` Flow, `importError`/`isImporting`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1-79` — rewritten to ViewModel-driven with FAB, SAF launcher, `SnackbarHost`, `EmptyLibraryState` vs `BookGrid`; kept legacy `LibraryScreen(books)` overload for previews.
- `app/src/main/java/com/makemission/folio/ui/library/components/BookCoverCard.kt:1-123` — added `AsyncImage` for `coverImagePath` with scrim, kept palette fallback.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:32-73` — per-book file load via `bookDao.getById` + `File`, falls back to assets/sample.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-65` — now uses ViewModel-driven `LibraryScreen` (no curated param).
- `README.md:1-137` — intro + tech stack (Coil), project structure (BookEntity/BookDao, LibraryViewModel, cover image), Library + Reading sections now document import (SAF, private copy, cover extraction, grid update, per-book reading, error handling).
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).
- Manual: Import valid EPUB → appears in grid with cover; tap opens in Reader; import corrupted file → Snackbar, no crash.

---

## Session 10 — 2026-09-08 — Rolling-Weight Velocity Estimator (time remaining in chapter)

Branch: `main`.

### Built

- **Time remaining in chapter (§5 — Rolling-Weight Velocity Estimator)** — `ui/reader/VelocityEstimator.kt`: pure on-device algorithm that tracks delta between page turns, smooths with an Exponential Moving Average (α=0.35), discards outliers via σ-threshold (e.g., 15-min idle) using standard deviation, and predicts from remaining *character density* (remaining chars in current chapter / EMA chars-per-ms) not just page count. Displayed as a small “12 min left in chapter” label near the progress bar (unobtrusive, inside the chrome toggle so it hides with the bar for immersive view).
- **Integration** — `ReadingScreen`’s single- and two-column contents now hold a `VelocityEstimator` (remembered), `lastFlat`/`lastTime` and `timeRemaining` state; a `snapshotFlow` on `firstVisibleItemIndex` (distinctUntilChanged) records page turns, `ReadingFlatMapper.charsBetween` and `remainingCharsInChapter` provide character-density awareness, and a `Column` above the `ReadingProgressBar` shows the formatted estimate (`formatTimeRemaining`) when available.
- Built on top of existing reading screen — no rewrite.

### Changed

- `app/src/main/java/com/makemission/folio/ui/reader/VelocityEstimator.kt` — new: `VelocityEstimator` (EMA, outlier σ, maxHistory), `formatTimeRemaining`, `ReadingFlatMapper` (flat-index ↔ paragraph helpers, accounts for diagram placeholder after ch0).
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-733` — added `snapshotFlow` import, `VelocityEstimator`/`ReadingFlatMapper`/`formatTimeRemaining` integration in both column contents, `AnimatedVisibility` now wraps a `Column` with time-remaining `Text` (`labelSmall`, `onSurfaceVariant`) above the progress bar.
- `README.md:1-133` — intro now lists chapter time remaining, tree adds `VelocityEstimator.kt`, Reading-screen section adds time-remaining bullet (§5) and updates DB reference to v3.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 9 — 2026-09-08 — Bionic reading (syllable-based, on-device)

Branch: `main`.

### Built

- **Bionic reading (§5 — syllable-based, deterministic, on-device)** — `ui/reader/BionicReading.kt`: onset / nucleus / coda analysis finds the first syllable without a dictionary or network (vowel set `aeiou` + `y` when not initial, consecutive vowels = single nucleus, single-consonant coda only if not the onset of the next syllable, clamped to ~60% of word length). Each paragraph is rendered as an `AnnotatedString` where the first syllable is bolded (`SpanStyle(Bold)`) for faster visual anchoring. Built on top of existing serif text rendering — no rewrite.
- **Toggle (§5)** — top-bar action *Bionic On/Off* (`rememberSaveable`) in `ReadingScreen`’s `TopAppBar` that toggles `BionicReading` for both phone (single-column) and tablet (two-column) layouts. Handled via `remember(paragraph) { toBionicAnnotated(...) }` so the serif `bodyLarge`/`bodyMedium` stays but gains anchors.
- Looked at `book-story-master` text handling for structural inspiration only — no code copied.

### Changed

- `app/src/main/java/com/makemission/folio/ui/reader/BionicReading.kt` — new: `boldEndForWord`, `toBionicAnnotated`, `boldSpan`.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-707` — added `bionicEnabled` state + `Bionic On/Off` action, passed flag to both column contents, and swapped paragraph `Text` to conditional `AnnotatedString` rendering (remembered per paragraph).
- `README.md:1-133` — bionic docs: intro now lists bionic reading, tree adds `BionicReading.kt`, Reading-screen section adds bionic bullet (§5).
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 8 — 2026-09-08 — Stylus physics + lasso extraction (pressure/tilt, closed-loop)

Branch: `main`.

### Built

- **Organic physics (§4)** — `ui/reader/components/HighlightOverlay.kt` now reads `MotionEvent` pressure (0..1) and `AXIS_TILT` (0..π/2) via `pointerInteropFilter` (`TOOL_TYPE_STYLUS` only, finger passes through for scroll/tap) and dynamically scales stroke width (`base 28dp * (0.55+0.9*pressure) * (1+tilt/(π/2)*0.35)`) so highlights feel organic, not fixed-width. Persisted strokes store parallel `pressuresData`/`tiltsData` alongside normalized `pointsData`.
- **Lasso extraction (§4)** — distinct closed-loop gesture: `isLassoStroke` checks closure (<72px), bounds (>60px, aspect 0.28..3.5, area), and length vs perimeter. On close, `HighlightOverlay` calls `onLassoFinished` (normalized points + pixel `Rect` bounds) instead of saving a highlight. `ReadingScreen` shows a dialog: *Extract Image* (saves a placeholder PNG of the lasso bounds to `cacheDir` and copies path to clipboard, on-device) vs *Copy Text* (on-device OCR — copies the book's text for the lasso region to clipboard, no network, entirely private). A `DiagramPlaceholder` (Fig. 1) inserted after Chapter 1 demos image-lasso; text-lasso copies paragraph text.
- Built on top of the existing `HighlightOverlay` — true-ink `BlendMode.Multiply` and zero-friction stylus-only capture were preserved, not rewritten.

### Changed

- `app/src/main/java/com/makemission/folio/data/db/entity/Highlight.kt:1-25` — added `pressuresData` + `tiltsData` (comma-separated per-point, empty for legacy fixed-width rows).
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt:1-37` — v2→v3, `fallbackToDestructiveMigration(true)` kept.
- `app/src/main/java/com/makemission/folio/ui/reader/components/HighlightOverlay.kt:1-144` — replaced `pointerInput`/`PointerType` (fixed 28dp) with `pointerInteropFilter`/`MotionEvent` (pressure/tilt), per-segment variable width, `isLassoStroke` classifier, dual callbacks (`onStylusStrokeFinished` with pressures/tilts + `onLassoFinished`), legacy overload kept.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-108` — `addHighlight` now takes `pressures`/`tilts` and encodes them; overload kept for legacy fixed-width.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-679` — collects `highlights`, passes pressure/tilt-aware callbacks, shows `LassoCapture` dialog (`copyLassoTextToClipboard`, `extractLassoAsImage`), inserts `DiagramPlaceholder` after Chapter 1 in both single- and two-column layouts, and wires overlay in both.
- `README.md:1-130` — stylus docs now cover pressure/tilt physics + lasso (image vs text), Room v3, updated tree.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 7 — 2026-09-08 — Stylus highlighting (zero-friction + true-ink Multiply)

Branch: `main`.

### Built

- **Stylus highlighting (§4 — zero-friction + true-ink, amber)** — `ui/reader/components/HighlightOverlay.kt`: pointer-input layer that only consumes `PointerType.Stylus` (finger passes through to scroll/tap), so stylus down instantly starts a path with no menu, toolbar or confirmation; strokes render with `BlendMode.Multiply` in Folio amber `#F7B538` so serif text stays crisp, simulating real ink on paper. Default highlight color is the FolioTheme amber.
- **Storage (§6 — extend schema)** — `data/db/entity/Highlight.kt` + `data/db/dao/HighlightDao.kt`; `FolioDatabase` bumped to v2 (`ReadingProgress` + `Highlight`, `fallbackToDestructiveMigration(true)`), extended rather than replaced. Highlights store `bookId`, `chapterIndex`, normalized `pointsData` (`"x1,y1,..."` 0..1), `color`, `createdAt`; observed as `Flow<List<Highlight>>`.
- **Reader integration** — `ui/reader/ReadingViewModel.kt` now exposes `highlights` + `addHighlight(normalizedPoints, chapterIndex)` + `clearHighlights()`; `ReadingScreen.kt` wires `highlights` into both single- and two-column layouts via an overlay `Box` that sits above the text but below the progress bar, so finger scrolling and tap-to-toggle still work.
- Looked at `book-story-master` annotation handling for structural cues only — no code copied. No pressure/tilt physics or lasso extraction yet (per this step's scope).

### Changed

- `app/src/main/java/com/makemission/folio/data/db/entity/Highlight.kt` — new.
- `app/src/main/java/com/makemission/folio/data/db/dao/HighlightDao.kt` — new.
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt:1-32` — v1→v2, added `Highlight`, `highlightDao()`, `fallbackToDestructiveMigration(true)`.
- `app/src/main/java/com/makemission/folio/ui/reader/components/HighlightOverlay.kt` — new: stylus-only `pointerInput` + `Canvas` with `BlendMode.Multiply`, `encodePoints`/`decodePoints`.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-71` — added `highlights` Flow, `addHighlight`/`clearHighlights`.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-550` — collects `highlights`, passes `onAddHighlight` to content; both column layouts now show `HighlightOverlay` (stylus-only, Multiply) above text.
- `README.md:1-130` — stylus-highlighting docs, Room v2, updated tree (`db v2` + `HighlightOverlay`).
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 6 — 2026-09-08 — Frictionless navigation (page turns, progress bar)

Branch: `main`.

### Built

- **Volume-key page turns (Frictionless Navigation — Hardware Page Turns)** — `MainActivity.onKeyDown` consumes `KEYCODE_VOLUME_UP/DOWN` and forwards via `ReaderPageTurnHandler` to the active `ReadingScreen`; the screen animates a page-sized scroll (`±5–6` items) for one-handed phone use.
- **Minimalist progress bar** — `ui/reader/components/ReadingProgressBar.kt` (thin amber fill on muted track, 4dp). Shows `firstVisibleIndex / total` and tapping it seeks to that fraction (`animateScrollToItem`). Inspired by the reference app's `ReaderProgressBar` but Folio-specific and tappable.
- **Tap-to-toggle immersive chrome** — tapping the reading area toggles the top bar + progress bar with `AnimatedVisibility` slide/fade so the view can be fully hidden for distraction-free reading, per spec.

### Changed

- `app/src/main/java/com/makemission/folio/MainActivity.kt:1-28` — added `onKeyDown` volume-key dispatch via `ReaderPageTurnHandler`.
- `app/src/main/java/com/makemission/folio/ui/reader/ReaderPageTurnHandler.kt` — new: singleton volume-key bridge.
- `app/src/main/java/com/makemission/folio/ui/reader/components/ReadingProgressBar.kt` — new: tappable minimalist bar.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-394` — chrome toggle state, derived progress (`firstVisible / total`), `detectTapGestures` seek, `DisposableEffect` volume handlers, overlay `AnimatedVisibility` for top bar + progress bar, single- and two-column content now both hideable and seekable.
- `README.md:1-123` — Reading-screen section now documents volume keys, progress bar (tap-to-jump), and tap-to-toggle chrome; structure tree updated.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 5 — 2026-09-08 — Core Reading screen (EPUB, Room, adaptive layout)

Branch: `main`.

### Built

- **EPUB parsing (§6)** — native `data/epub/EpubParser.kt` (ZIP + `container.xml` → OPF manifest/spine → `toc.ncx` → Jsoup paragraph extraction); no network/AI. Loads `assets/sample.epub` if present, else curated fallback chapters.
- **ReadingScreen (§3 + §6)** — serif body typography (`bodyLarge` 17/27) with:
  - Phone: single-column, edge-to-edge, immersive `LazyColumn`.
  - Tablet: landscape + `screenWidthDp >= 840` triggers a two-column spread (left/right `LazyColumn`s with central gutter), mimicking a physical book.
  - Top bar with back navigation; chapter titles in heavy sans, amber rules between chapters.
- **Navigation** — `navigation/FolioNav.kt` (`NavHost` `library` ↔ `reader/{bookId}/{bookTitle}`); tapping a library cover (now clickable `BookCoverCard`) navigates to `ReadingScreen`.
- **Storage (§6)** — `data/db/FolioDatabase` (Room 2.7.2, KSP), `entity/ReadingProgress` (`bookId` PK, chapter/paragraph + timestamp), `dao/ReadingProgressDao` (Flow observe + upsert). `ReadingViewModel` loads the EPUB and observes/saves progress; position restored on next open.
- Reference: looked at `book-story-master`'s `ReaderLayout` / `ReaderContent` / `ReaderLayoutText` and `EpubTextParser` for structure only — no code copied.

### Changed

- `gradle/libs.versions.toml:2-39` — added `ksp 2.2.20-2.0.4`, `room 2.7.2`, `navigationCompose 2.8.4`, `jsoup 1.18.3`, `lifecycle 2.9.2` and libraries (`navigation-compose`, `room-runtime/ktx/compiler`, `lifecycle-viewmodel-compose/runtime-compose`, `jsoup`); added `ksp` plugin.
- `build.gradle.kts:1-11` — added `ksp` plugin alias.
- `gradle.properties:14-19` — added `android.builtInKotlin=false` + `android.newDsl=false` for KSP/Room compatibility with AGP 9.4.
- `app/build.gradle.kts:1-65` — added `kotlin-android` + `ksp` plugins, `navigation-compose`, lifecycle, Room, jsoup deps, `ksp(room-compiler)`; reverted to `kotlinOptions { jvmTarget = "17" }`.
- `app/src/main/java/com/makemission/folio/data/db/entity/ReadingProgress.kt` — new.
- `app/src/main/java/com/makemission/folio/data/db/dao/ReadingProgressDao.kt` — new.
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt` — new.
- `app/src/main/java/com/makemission/folio/data/epub/EpubParser.kt` — new.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt` — new.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt` — new.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModelFactory.kt` — new.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt` — new.
- `app/src/main/java/com/makemission/folio/ui/library/components/BookCoverCard.kt:26-38` — now clickable via `onClick`.
- `app/src/main/java/com/makemission/folio/ui/library/components/BookGrid.kt:13-26` — now forwards `onBookClick`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:20-31` — now takes `onBookClick`.
- `app/src/main/java/com/makemission/folio/MainActivity.kt:1-31` — hosts `FolioNavHost()` instead of direct `LibraryScreen`.
- `README.md:1-115` — updated: Reading-screen section, EPUB/Room/nav tech stack, navigation + reader structure, adaptive-layout notes.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 4 — 2026-09-08 — Editorial library screen (grid + empty state)

Branch: `main`.

### Built

- **Library home screen** per `Folio_Project.md` §3 (Editorial Library Interface):
  - Curated visual grid of cover thumbnails via `LazyVerticalGrid` (`GridCells.Adaptive(148.dp)` — phones 2-up, tablets scale).
  - Flat-illustration empty state (amber sun + Folio-palette books on a shelf, drawn with `Canvas`) shown when `books.isEmpty()`.
  - Wired as the app's home screen in `MainActivity`; no reading screen yet.
- Looked at `book-story-master`'s `LibraryScaffold` / `LibraryGridLayout` / `LibraryGridItem` / `LibraryEmptyPlaceholder` for layering ideas only — no code copied.

### Changed

- `app/src/main/java/com/makemission/folio/data/model/Book.kt` — new: minimal `Book` model + `curatedSampleBooks()` seed and `FolioCoverPalette`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt` — new: `LibraryScreen(books)` — `Scaffold` with editorial header, switches between `BookGrid` and `EmptyLibraryState`.
- `app/src/main/java/com/makemission/folio/ui/library/components/BookGrid.kt` — new: adaptive grid.
- `app/src/main/java/com/makemission/folio/ui/library/components/BookCoverCard.kt` — new: flat 2:3 editorial cover (palette chip, title/author, spine accent) plus title/author below.
- `app/src/main/java/com/makemission/folio/ui/library/components/EmptyLibraryState.kt` — new: `EmptyLibraryState` + `FlatLibraryIllustration` (Canvas).
- `app/src/main/java/com/makemission/folio/MainActivity.kt:20-30` — now hosts `LibraryScreen(curatedSampleBooks())` inside `FolioTheme`.
- `README.md:1-95` — brought current: Compose stack, design-system summary, library-screen description, updated project-structure tree, main-only branching, build notes. Legacy fragment/nav docs removed.
- `Project.md` — this changelog entry.

### Verification

- `./gradlew :app:assembleDebug -x lint` — built with JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 3 — 2026-09-08 — Compose design system + git repair

Branch: `main` (consolidated; `master` retired).

### Changed

- Added the Folio design system under `app/src/main/java/com/makemission/folio/ui/theme/`:
  - `Color.kt` — brand palette per spec (`Folio_Project.md` §2): deep green
    `#004F39` (library/dark backgrounds), burgundy `#780116` (active states,
    tags, buttons), amber `#F7B538` (accent/highlights), plus dark and light
    Material 3 `ColorScheme`s.
  - `Type.kt` — editorial typography: heavy sans-serif headers (placeholder
    for Druk Wide / Helvetica Neue Bold) and serif body text for reading.
  - `Theme.kt` — `FolioTheme()` composable (structure modeled on the
    `book-story-master` reference `ui/theme/Theme.kt`; no code copied).
- Converted `MainActivity` to a Compose `ComponentActivity` and wired
  `FolioTheme` as the default theme for the app. No screens yet.
- Gradle: enabled Compose for AGP 9 (built-in Kotlin, so no
  `kotlin-android` plugin; JVM target via `kotlin { compilerOptions { } }`),
  added Compose BOM `2025.09.00`, `activity-compose`, `ui`, `material3`,
  `foundation`; Java 17.
- Added `Inspiration/book-story-master` (Book's Story) as a reference app
  for structure/layout inspiration only.
- Added Apache-2.0 `LICENSE` matching the spec's license choice.

### Git repair

- Truncated `.git` objects (empty/zero-byte files, likely a filesystem/sync
  event) were removed; verified `git fsck` is clean.
- Restored `README.md` / `LICENSE` working copies that had been truncated
  to 0 bytes from the index.
- Replaced the unrecoverable local `main` ref with the real history
  tip (`a740f90`); retired the stale local/remote `master`.
- Renamed the git remote from `main` to `origin` (removes refname
  ambiguity), kept remote default branch as `main`.
- Preserved the original (unused) GitHub `main` commit as tag
  `github-original-init`.

### Verification

- `git fsck --full` clean; commits on `main` only.
- `./gradlew :app:assembleDebug -x lint` builds with JDK 21
  (`~/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`).

---

## Session 2 — 2026-09-07 — Custom launcher icons + main branch + docs

Branch: work started on `master`, merged into `main`. `main` is now the
coding branch.

### Changed

- Replaced default Android Studio launcher icons with the IconKitchen set
  from `icons/android/`:
  - Deleted template assets: `mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.webp`,
    `mipmap-anydpi/ic_launcher*.xml`,
    `drawable/ic_launcher_background.xml` and `drawable/ic_launcher_foreground.xml`.
  - Copied `icons/android/res/mipmap-*/*` (`ic_launcher`,
    `ic_launcher_background`, `ic_launcher_foreground`,
    `ic_launcher_monochrome` PNGs) into `app/src/main/res/mipmap-*/`.
  - Installed `mipmap-anydpi-v26/ic_launcher.xml` from the export and added
    `mipmap-anydpi-v26/ic_launcher_round.xml` with identical content so
    `android:roundIcon="@mipmap/ic_launcher_round"` keeps resolving.
  - Duplicated `ic_launcher.png` → `ic_launcher_round.png` per density for
    pre-Oreo (API < 26) legacy round icons.
- No `AndroidManifest.xml` change needed — `icon` / `roundIcon` already
  pointed at `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`.
- Added `README.md`: app description, tech stack, project structure,
  launcher-icon docs, requirements, build & run, branching.
- Added `Project.md` (this file) as the per-session changelog.

### Verification

- All adaptive-icon XML files parse; `AndroidManifest.xml` parses.
- All 5 PNGs present in each of the 5 densities; `file` confirms correct
  sizes (48/72/96/144/192 px legacy, 108–324 px layers).
- No stale `drawable/ic_launcher` references left (`grep -r` clean).
- Full `./gradlew :app:assembleDebug` not run — no JDK in this environment.
  Recommend running it in Android Studio or a dev shell with JDK 11+.

### Git

- Committed icon replacement + docs on `master`, created `main` from it and
  merged `master` → `main`. Currently on `main`.

---

## Session 1 — 2026-09-07 — Initial Android boilerplate

Commit `f1ad0b4` — `Initialize Android project "Folio" with basic boilerplate.`

- Standard Android Studio Kotlin template: `MainActivity` with toolbar, FAB,
  edge-to-edge + window insets, Navigation component (`nav_graph`,
  `FirstFragment` ↔ `SecondFragment`), options menu.
- Gradle setup: `compileSdk`/`targetSdk` 37, `minSdk` 33, ViewBinding,
  Material + Navigation dependencies, Foojay toolchain resolver.
- Default template launcher icons (green grid vector + webp) — superseded in
  Session 2.
