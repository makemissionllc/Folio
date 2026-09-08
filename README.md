# Folio

Folio is a premium, distraction-free Android ebook reader by MakeMission LLC (`com.makemission.folio`). It bridges digital convenience and the tactile craft of traditional bookmaking — fluid stylus interactions, magazine-quality typography, and adaptive layouts for phones and tablets.

Jetpack Compose–first. Library (editorial grid), core Reading (native EPUB, adaptive layouts, Room progress + frictionless navigation), stylus highlighting (zero-friction, true-ink Multiply, pressure/tilt physics, lasso extraction), bionic reading and chapter time remaining are now in place; X-Ray and other algorithmic features come later.

## Tech stack

- Kotlin 2.2, Android Gradle Plugin 9.4 (KSP via `com.google.devtools.ksp`; version catalog in `gradle/libs.versions.toml`)
- `compileSdk` / `targetSdk` 37, `minSdk` 33, Java 17
- Jetpack Compose (BOM `2025.09.00`): `ui`, `foundation`, `material3`, `activity-compose`
- Navigation Compose 2.8.4 (`navigation-compose`), lifecycle `viewmodel-compose` / `runtime-compose`
- Room 2.7.2 (`room-runtime`, `room-ktx`, KSP `room-compiler`) for progress + highlight storage (v3 — pressure/tilt)
- EPUB parsing: native ZIP + `org.jsoup:jsoup:1.18.3` (no network/AI — §6)
- Material Components (`Theme.Material3.DayNight.NoActionBar` for the window)
- Design system in `ui/theme/` — see below

## Design system

The palette and typography come from `Inspiration/Folio_Project.md` §2:

- **Background** — deep green `#004F39` (library + dark mode)
- **Active / tags** — burgundy `#780116`
- **Accent** — xanthous `#F7B538` (highlights, progress, focus)

Typography: heavy sans-serif headers (placeholder for Druk Wide / Helvetica Neue Bold, `FontFamily.SansSerif` Black/ExtraBold) and serif body text (`bodyLarge` 17/27 for reading). Both `Color.kt` / `Type.kt` / `Theme.kt` follow the structure of the `book-story-master` reference app's `ui/theme/` (no code copied).

## Project structure

```
Folio/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/makemission/folio/
│       │   ├── MainActivity.kt                 # host — FolioNavHost + volume-key dispatch
│       │   ├── navigation/FolioNav.kt          # NavHost: library ↔ reader
│       │   ├── data/
│       │   │   ├── db/ { FolioDatabase v3, dao/{ReadingProgressDao, HighlightDao}, entity/{ReadingProgress, Highlight (points + pressures/tilts)} }
│       │   │   ├── epub/EpubParser.kt         # native EPUB3 (ZIP+OPF+Jsoup) + fallback
│       │   │   └── model/Book.kt              # minimal library model + curated seed
│       │   └── ui/
│       │       ├── theme/ { Color, Type, Theme }.kt
│       │       ├── library/
│       │       │   ├── LibraryScreen.kt        # Scaffold + header, onBookClick
│       │       │   └── components/ { BookGrid, BookCoverCard, EmptyLibraryState }
│       │       └── reader/
│       │           ├── ReadingScreen.kt        # serif body + chrome/volume + highlight/lasso + bionic toggle + diagram
│       │           ├── ReadingViewModel.kt     # EPUB, progress + highlights (Flow, pressure/tilt)
│       │           ├── ReadingViewModelFactory.kt
│           ├── BionicReading.kt        # deterministic onset/nucleus/coda syllable splitter
│           ├── VelocityEstimator.kt      # Rolling-Weight EMA + outlier + char-density time-remaining
│       │           ├── ReaderPageTurnHandler.kt# volume-key dispatch bridge
│       │           └── components/ { ReadingProgressBar.kt, HighlightOverlay.kt (pressure/tilt Multiply + lasso) }
│       └── res/
│           ├── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/  # launcher PNGs
│           ├── mipmap-anydpi-v26/                        # adaptive-icon XML
│           ├── values/ { strings, colors, themes, dimens }
│           └── xml/ { backup_rules, data_extraction_rules }
├── Inspiration/
│   ├── Folio_Project.md        # full app spec
│   └── book-story-master/      # structure/layout reference only
├── icons/android/              # IconKitchen source (res/ + play_store_512.png)
├── gradle/  build.gradle.kts  settings.gradle.kts
└── Project.md                  # per-session changelog
```

Legacy template fragments / Navigation graph from the initial scaffold remain in `res/` but are unused — the app is fully Compose (Compose Navigation).

## Library screen (editorial grid)

`LibraryScreen(books, onBookClick)` — inspired by `book-story-master`'s `LibraryScaffold` →
`LibraryGridLayout` layering, rebuilt for Folio:

- **Curated grid** — `LazyVerticalGrid` with `GridCells.Adaptive(148.dp)` so phones show 2 columns and tablets scale naturally; each item is a `BookCoverCard` (2:3 cover, rounded 16dp, spine accent, Folio title chip). Cards are clickable and navigate to the Reader.
- **Empty state** — centered flat illustration (amber sun, burgundy / paper / deep-green books on a shelf) drawn with Compose `Canvas`, plus editorial copy. Shown when `books.isEmpty()`.
- **Header** — weighty sans "Library" title + collection subtitle over the Folio background.

## Reading screen (core + frictionless navigation + stylus engine)

`ReadingScreen(bookId, bookTitle, onBack)` — per §3, §4 Stylus & §6:

- **EPUB parsing** — `EpubParser` is a native engine: `ZipInputStream` → `container.xml` → OPF manifest/spine → `toc.ncx` → Jsoup extraction of paragraphs. No network, no AI. Loads `assets/sample.epub` when present; otherwise renders curated fallback chapters (`sampleFallbackChapters`) so the UI is always usable.
- **Typography** — chapter titles in heavy sans (`headlineSmall` / `titleMedium`), body in Folio serif (`bodyLarge` 17/27, `bodyMedium` on tablet) on the Folio background.
- **Phone (§3)** — single-column, edge-to-edge, immersive; paragraphs in a `LazyColumn` with Folio spacing and amber rule between chapters.
- **Tablet (§3)** — landscape + `screenWidthDp >= 840` triggers a two-column spread: chapters split into left/right `LazyColumn`s with a central gutter (book-spine), mimicking a physical spread. More sophisticated virtual-canvas pagination (§5) can replace this later.
- **Progress (§6)** — `FolioDatabase` (`Room` v3) with `ReadingProgress` (`bookId` PK, `chapterIndex`, `paragraphIndex`, `lastReadMillis`). `ReadingViewModel` observes/saves position via `ReadingProgressDao`; restored on next open.
- **Time remaining (§5 — Rolling-Weight Velocity Estimator)** — `VelocityEstimator` tracks delta between page turns, smooths with an Exponential Moving Average (α=0.35) and discards outliers via σ-threshold (e.g., 15-min idle), then predicts from remaining *character density* (upcoming chars / EMA speed) not just page count; displayed as a small “12 min left in chapter” label near the progress bar (pure on-device, no network).
- **Frictionless navigation (Folio spec §3)** — inspired by `book-story-master`'s `ReaderProgressBar`:
  - *Hardware page turns* — volume up/down advance a page (one-handed phone use). `MainActivity.onKeyDown` forwards to `ReaderPageTurnHandler` → `animateScrollToItem` by a page.
  - *Minimalist progress bar* — thin amber fill on muted track at the bottom. The bar shows `firstVisibleIndex / total` and tapping it seeks (`animateScrollToItem` to tapped fraction).
  - *Tap-to-toggle chrome* — tapping the reading area toggles the top bar + progress bar (with `AnimatedVisibility` slide/fade) for a fully distraction-free immersive view.
- **Stylus engine (§4)** — `HighlightOverlay` captures only stylus (`MotionEvent.TOOL_TYPE_STYLUS`, finger passes through for scroll/tap), so a stylus touch instantly draws with no menu or toolbar (zero-friction) and stores in Room (`highlights`: `bookId`, `chapterIndex`, normalized `pointsData` + `pressuresData`/`tiltsData`, `color`):
  - *True-ink* — strokes render with `BlendMode.Multiply` in Folio amber `#F7B538` so serif text stays crisp.
  - *Organic physics* — `MotionEvent` pressure (0..1) and `AXIS_TILT` (0..π/2) dynamically scale stroke width (`base 28dp * pressureFactor * tiltFactor`) for a natural hand feel.
  - *Lasso extraction* — a closed-loop stylus circle is classified (closure, bounds, circularity) distinctly from a highlight. Over an image it extracts the diagram as a PNG to cache/clipboard; over text it runs on-device OCR (local text copy, no network) to clipboard — both entirely private. A `DiagramPlaceholder` (Fig. 1) in the first chapter demos image lasso; text-lasso copies paragraph text. UI is a small dialog with *Extract Image* / *Copy Text*.
- **Bionic reading (§5)** — `BionicReading` is a deterministic, on-device syllable algorithm (no dictionary, no network) that analyzes each word's onset / nucleus / coda to find the first syllable (vowel-cluster nucleus + optional single-consonant coda, clamped to ~60%) and bolds it via `AnnotatedString` + `SpanStyle(Bold)` for faster scanning. A top-bar toggle (*Bionic On/Off*) applies it to both phone (single-column) and tablet (two-column) layouts, keeping the serif body but adding visual anchors.

Reference: inspected `book-story-master`'s `ReaderLayout` / `ReaderContent` / `ReaderLayoutText`, `ReaderProgressBar`, and `EpubTextParser` for layering and ZIP+Jsoup ideas only — no code copied.

## Launcher icons

Source of truth: `icons/android/` (IconKitchen export).

Installed as `app/src/main/res/mipmap-*` / `mipmap-anydpi-v26/` (`ic_launcher`,
`ic_launcher_round`, layers). See `Project.md` Session 2 for the mapping.
`AndroidManifest.xml` uses `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`.

## Requirements

- Android Studio Ladybug or newer
- JDK 21 (Gradle resolves via `~/.gradle/jdks/` in this repo)
- Android SDK 37 + build tools

## Build & run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Open in Android Studio and press **Run** for the usual flow.

Useful checks:

```bash
./gradlew :app:lint
./gradlew test
```

## Branching

- `main` — active branch (default). All work lands here.
- `github-original-init` — tag preserving the abandoned initial GitHub `main` commit.

See `Project.md` for the per-session changelog.
