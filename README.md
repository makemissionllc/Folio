# Folio

Folio is a premium, distraction-free Android ebook reader by MakeMission LLC (`com.makemission.folio`). It bridges digital convenience and the tactile craft of traditional bookmaking — fluid stylus and finger interactions, magazine-quality typography, and adaptive layouts for phones and tablets.

Jetpack Compose–first. Offline and private by design: every book, highlight, bookmark, search and vocabulary card lives on-device via Room and DataStore, with no network, no account, and no cloud.

## Overview

- **Library** — editorial grid with long-press actions, swipe-right to Settings and swipe-left to Insights, pull-down from the very top (deliberate 180 px drag when already at top) to reveal library-wide search. Search is on-device and prioritizes highlights/bookmarks before titles and full text. Intake is automatic device scanning (Downloads, Documents, external storage or a SAF-granted folder at Settings → Library → Choose books folder) with hash + path dedup and an external `VIEW` intent so Folio appears when opening `.epub` from Files, browser or email. A quiet manual fallback lives at Settings → Library → Add book manually. No FAB.
- **Reading** — native EPUB (ZIP + OPF + Jsoup), single-column on phone and two-page spread on tablet landscape. Progress is saved by paragraph (debounced, `NonCancellable`) so reopening lands exactly where you left off. Top bar shows only Back and a menu (☰); tap the text to hide chrome, or keep the progress bar always visible via Settings → Reading. Volume keys turn pages.
- **Navigation** — `CONTINUOUS` (whole-book vertical scroll) and `CHAPTER_SWIPE` (vertical within a chapter, horizontal swipe between chapters; tablet shows spreads). Persisted via DataStore and switchable from Settings → Reading or the reader menu with `AnimatedContent` transitions.
- **Built-in guide** — the curated sample book “How to use Folio” (5 chapters: Welcome to Folio; Your Library; Reading, Your Way; Make It Yours; Smart, Private, Calm) covers every current feature in plain editorial language.
- **Insights** — a quiet ledger that aggregates existing Room tables (Shelf, Marginalia, Lexicon, Rhythm) with streaks and last-read, no extra tracking. Empty state is encouraging and on-brand.
- **Vocabulary** — double-tap a word (or select → Explain) for a definition from the offline 25k WordNet subset (512 KB gz, real glosses — no fake templates, honest "No definition found" for missing words) and schedule it with on-device SM-2. Review from Insights → Lexicon.

## Reading features

- **Typography — your type, your margins** — font family (Serif Default, Sans, Literary Serif, Monospace), size (Small/Normal/Large/XL), line spacing (Compact/Normal/Relaxed/Loose) and margins (Narrow/Normal/Wide/Extra Wide). Pick from the reader menu (☰ → Typography); choices persist via DataStore and recompute True Pages and Knuth-Plass so pagination and orphan/widow control stay true to what you see.
- **True Pages** — virtual-canvas measurement of the whole book for the current screen, font and palette; `Page X of Y` with `AnimatedContent`, disk-cached per configuration and hash via `TruePageCache` (`totalPages` + `prefixSums` + `perScreenHeightPx`).
- **Knuth-Plass** — orphan/widow control and squared-off paragraphs via micro-kerning and justification, coordinated with True Pages on the same canvas.
- **Guided Reading (Bionic)** — deterministic onset/nucleus/coda syllable splitter that bolds the first syllable; toggle from Settings → Reading or the reader menu (☰), persisted via DataStore, applied to `AnnotatedString`.
- **Highlights — stylus + finger, five colors, two styles** — stylus draws instantly with true-ink `Multiply`, pressure/tilt physics, and lasso extraction (diagram crop via bounding-box or text copy). Finger: long-press select → Highlight in the floating toolbar (Copy/Explain/Highlight) with the same palette. Colors (Amber, Yellow, Green, Pink, Blue) and styles (Fill 28dp × 0.52 / Underline 4dp × 0.88) picked from the reader menu (☰ → Highlights), persisted via DataStore and kept per-highlight; LCS anchoring keeps marks with the right sentence.
- **Bookmarks** — position-only marks (`bookId` + `chapterIndex` + `paragraphIndex` with unique index, `IGNORE` on conflict), distinct from highlights, with a bottom sheet for jump and remove. Current-page indicator and precise restore in both navigation modes.
- **Quick jumps** — Chapters · N, Highlights · N and Bookmarks · N from the reader menu (☰), each tap-to-jump with shared `flatIndexForBookmark` logic for Continuous and Chapter-swipe (phone/tablet spreads).
- **People & Topics (X-Ray)** — on-device TF-IDF per chapter, progressive and prioritized for the chapter you are on, disk-cached and computed chapter-by-chapter in `WorkManager` (`Semaphore(2)` for 50–300 books, atomic renames, per-chapter incremental).
- **Search** — library-wide pull-down and in-book contextual search, phrases matched on title/author/paragraphs with snippet extraction, ranking highlights/bookmarks first. Debounced (280 ms library, 260 ms in-book), up to 40 results, jump-to-position sharing bookmark flat logic. Graceful no-results.
- **Comfort Contrast** — ambient-light WCAG 7:1 via `Sensor.TYPE_LIGHT`, throttled and animated gradually (`animateColorAsState` 800–900 ms, EMA smoothing, color-distance debouncing) and palette-coordinated; lifecycle-aware — sensor only registered while `RESUMED` and Comfort Contrast is ON (unregistered in `onPause`/`DisposableEffect`, never left running); shows “No sensor” when unavailable.
- **Evening warmth** — time-aware tint from the system clock (neutral by day, warmer after sunset), gradual and palette-coordinated, persisted via DataStore; 60-second poll is lifecycle-aware (only while `RESUMED` and Evening warmth ON, cancelled otherwise, no overlapping instances); animated together with contrast so colors never flash.
- **Diagrams** — white-margin detection and full-width expansion for images, cropped copy cached on disk and reused.
- **Time remaining** — Rolling-Weight EMA velocity estimator with outlier rejection and character-density prediction.
- **Polish** — chapter-boundary haptics, iOS-like scroll fades (`TopReadingFade`/`BottomReadingFade` reused for Library, Settings, Insights and Vocabulary), premium micro-animations (`animateItem` stagger, `Crossfade` 360 ms, slide/fade transitions), pleasant loading with pulsing amber dot and minimum-granularity display, `navigationBars` insets for edge-to-edge.

## Smart Features guide

Settings → Smart Features is an editorial guide to **fifteen** quiet helpers, each in plain English (what annoys, how Folio helps, why it matters): Guided Reading (Bionic), honest time left, People & Topics (X-Ray), words that stay (SM-2), real pages (True Pages), paragraphs that breathe (line breaking), marks that never get lost (LCS), comfort in any light (contrast), diagrams that fit, evening warmth, find any line (search), meaning where you are (dictionary), your type/your margins (typography), ink in your colors (finger + multi-color + Fill/Underline), and jump anywhere (chapters/highlights/bookmarks quick-jump). All on-device, all private.

## Library intake — no FAB

Primary intake is auto-scan (when Settings → Library → Auto-scan on launch is on and a storage permission or SAF grant exists) and external `ACTION_VIEW` (`application/epub+zip`, `*/*` + `.epub` pathPattern, `singleTop` + `onNewIntent` in `MainActivity`) reusing the same minimal pipeline (copy → lightweight `EpubParser.extractMetadata` → cover → Room, hash-tracked). Heavy work (full ZIP/Jsoup parse and X-Ray TF-IDF per chapter) is scheduled via `BookProcessingWorker`/`BookProcessingScheduler` as expedited, battery-aware WorkManager jobs that process chapter-by-chapter with a `Semaphore(2)` queue, atomic cache writes and `OutOfMemoryError` handling so 300+ imports do not OOM while you search. A subtle manual fallback at Settings → Library → Add book manually (`ACTION_OPEN_DOCUMENT`) reuses the same pipeline. `EpubScanner` searches the SAF grant first, then common storage locations and `MediaStore.Files` best-effort, with `MAX_DEPTH 8`, `MAX_FILES 5000` (visible truncation notice if hit) and cross-source dedup via path + displayName/size. Invalid EPUBs show a Snackbar and never crash.

## Appearance & Settings

Settings is Folio-themed: `Reading` (Always show progress bar, Haptic feedback, Guided Reading (Bionic), Comfort Contrast, Navigation segmented control), `Library` (Auto-scan on launch, Choose books folder via `ACTION_OPEN_DOCUMENT_TREE` with persisted URI permission + Change/Clear, Scan device, Add book manually), `Insights` and `Smart Features` (single buttons), `Appearance` (Theme Light/Dark/Auto segmented control — Auto follows the system, Cool Slate is the dark default — plus 4 dark palettes: Folio Green, True Black, Warm Sepia, Cool Slate, Evening warmth toggle), `Support / Developer` (local 256 KB rolling log via `FileProvider`, Export/Refresh/Clear, privacy note), `Privacy / Data` and `Content Disclaimer` (verbatim: reading app only, no hosting/distribution, user responsible for legal right). Every toggle is DataStore-backed (`folio_settings`) and reflects the persisted value on load.

## Onboarding

First-launch pager (Welcome; Write like paper — stylus + finger + colors/styles + lasso; Smart, on-device — Guided Reading (Bionic), People & Topics (X-Ray), True Pages, Vocabulary (SM-2), Comfort Contrast, Evening warmth, search and typography your way; Private by design; Content Disclaimer) with Folio illustrations. Requests storage/file access for auto-scan and notifications for future reminders, showing `Granted` status and graceful denial handling (manual SAF import and Add book still work, notifications silent). Final page shows verbatim **Content Disclaimer**: *Folio is a reading application only. It does not provide, host, sell, or distribute any books, and does not include any copyrighted content. Any books you read in Folio come from files you choose to open or import yourself, from your own device or from other apps. You are solely responsible for ensuring you have the legal right to any content you add to Folio, including complying with applicable copyright law in your jurisdiction. Folio's developer is not responsible or liable for how you obtain, use, or possess the content you open in the app.* Gated by `SettingsRepository.hasSeenOnboarding` (`has_seen_onboarding` in `folio_settings`).

## Tech stack

- Kotlin 2.2, Android Gradle Plugin 9.4 (KSP `com.google.devtools.ksp`; version catalog `gradle/libs.versions.toml`)
- `compileSdk` / `targetSdk` 37, `minSdk` 33, Java 17
- Jetpack Compose BOM `2025.09.00` (`ui`, `foundation`, `material3`, `activity-compose`), Navigation Compose 2.8.4, lifecycle `viewmodel-compose` / `runtime-compose`
- Room 2.7.2 (`room-runtime`, `room-ktx`, KSP `room-compiler`) for books, progress, highlights (color + style `FILL`/`UNDERLINE`), bookmarks (unique `(bookId, chapterIndex, paragraphIndex)`), vocabulary (v10 — `fallbackToDestructiveMigration`)
- DataStore Preferences 1.1.1 for user settings (alwaysShowProgressBar, `hasSeenOnboarding`, `autoScanEnabled`, `booksFolderUri`, `hapticsEnabled`, `darkPalette` default Cool Slate, `themeMode` Light/Dark/Auto default Auto, `timeTintEnabled`, `bionicEnabled`, `adaptiveContrastEnabled`, `readingFont`/`readingFontSize`/`readingLineSpacing`/`readingMargin`/`highlightColor`/`highlightStyle`)
- Coil 2.7.0 for cover images (downsampled 440×660, mem/disk cache, crossfade); Jsoup 1.18.3 for EPUB; WorkManager 2.9.1 for background X-Ray + parsed cache; DocumentFile 1.0.1 for SAF; `androidx.datastore:datastore-preferences` + `WorkManager` + `DocumentFile`
- Offline dictionary `assets/dictionary.json.gz` (25k WordNet-derived, 512 KB gz / 1.5 MB json, real WordNet glosses + 110 Folio-specific overrides; honest "No definition found" for words without a WordNet entry — no synthetic templates); on-device search `SearchRepository` (phrase search, highlights/bookmarks ranked first, `ConcurrentHashMap` + per-book isolation, skips still-processing books)
- Debug logging `FolioLogger` + `FolioApp` (`filesDir/logs/folio.log`, 256 KB rolling, half-trim, no network, `UncaughtExceptionHandler`; `LogsScreen` via `FileProvider`)

## Design system

Palette and typography from `Inspiration/Folio_Project.md` §2:

- **Background** — deep green `#004F39` (library + dark mode); **Active / tags** — burgundy `#780116`; **Accent** — xanthous `#F7B538` (highlights, progress, focus)
- UI chrome: system SF-style sans (Android Roboto via `FontFamily.Default`) with weight hierarchy; reading body stays serif (`bodyLarge` 17/27 via `FontFamily.Serif`). `FolioUiFamily` vs `FolioReadingFamily` in `Type.kt`; `Color.kt`/`Type.kt`/`Theme.kt` follow the `book-story-master` reference structure

## Project structure

```
Folio/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── assets/dictionary.json.gz
│       ├── AndroidManifest.xml
│       ├── java/com/makemission/folio/
│       │   ├── FolioApp.kt / MainActivity.kt / navigation/FolioNav.kt
│       │   ├── data/
│       │   │   ├── epub/EpubParser.kt              # ZIP+OPF+Jsoup, metadata fast path, cover, filename sanitization, stray-image strip, sampleFallbackChapters (How to use Folio)
│       │   │   ├── db/ FolioDatabase v10 + dao/* + entity/*  # Book/Progress/Highlight (color+style)/Bookmark (unique index)/Vocabulary (SM-2)
│       │   │   ├── settings/SettingsRepository.kt  # DataStore folio_settings
│       │   │   ├── cache/ ParsedBookCache, TruePageCache  # hash-validated, atomic, LRU
│       │   │   ├── xray/ XRayExtractor (TF-IDF progressive) + XRayCache
│       │   │   ├── search/SearchRepository.kt      # on-device, highlights/bookmarks first
│       │   │   ├── scan/EpubScanner.kt             # SAF > file walk > MediaStore, MAX_DEPTH 8, MAX_FILES 5000
│       │   │   ├── work/ BookProcessingWorker (Semaphore 2) + Scheduler
│       │   │   ├── vocabulary/Sm2.kt, dictionary/DictionaryRepository.kt, anchor/LcsAnchor.kt, image/*, logging/FolioLogger.kt
│       │   │   └── model/Book.kt (curatedSampleBooks)
│       │   └── ui/
│       │       ├── theme/ Color, Type, Theme (FolioPalette SLATE default, ThemeMode Auto), AdaptiveContrastEngine, TimeTintEngine, AmbientLightSensor
│       │       ├── reader/ ReadingScreen (Continuous vs Chapter-swipe, TruePage/Knuth + typography/margins/highlights), ReadingViewModel, BionicReading, VelocityEstimator, TruePageEngine, KnuthPlassEngine, ReaderPageTurnHandler, ReadingAppearance, components/*
│       │       ├── library/ LibraryScreen + LibraryViewModel (minimal import + scan dedup + recently-read sorting + scroll-to-top), components/*
│       │       ├── onboarding/ OnboardingScreen (pager 4, permissions, DataStore gate)
│       │       ├── settings/ SettingsScreen + SmartFeaturesScreen (15 features) + LogsScreen
│       │       ├── insights/ InsightsScreen + InsightsViewModel (read-only journal)
│       │       └── vocabulary/ VocabularyScreen + VocabularyViewModel (SM-2 review)
│       └── res/ mipmap-*, values/{strings,colors,themes,dimens}, xml/{backup_rules,data_extraction_rules,file_paths}
├── Inspiration/ Folio_Project.md, book-story-master/
├── icons/android/ IconKitchen source
├── gradle/ build.gradle.kts settings.gradle.kts
└── Project.md
```

Legacy template fragments (`res/layout/*`, `res/navigation/nav_graph.xml`, `FirstFragment`/`SecondFragment`) and the initial scaffold navigation remain unused — the app is fully Compose (Compose Navigation).

## Requirements

- Android Studio Ladybug or newer
- JDK 17 / 21 (Gradle resolves via `~/.gradle/jdks/` in this repo; CI uses JBR 17)
- Android SDK 37 + build tools
- `minSdk` 33, `targetSdk`/`compileSdk` 37

## Build & run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Useful checks:

```bash
./gradlew :app:lint
./gradlew :app:testDebugUnitTest
```

Open in Android Studio and press **Run**.

## Branching

- `main` — active branch (default). All work lands here.
- `github-original-init` — tag preserving the abandoned initial GitHub `main` commit.

See `Project.md` for the per-session changelog and production-readiness notes.
