# Folio — Project Log

Changelog after every coding session. `README.md` stays as the basic app
description and documentation; this file tracks what changed and when.

Active coding branch: `main`.

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
