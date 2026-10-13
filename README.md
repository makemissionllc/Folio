# Folio

Folio is a premium, distraction-free Android ebook reader by MakeMission LLC (`com.makemission.folio`). It bridges digital convenience and the tactile craft of traditional bookmaking — fluid stylus and finger interactions, magazine-quality typography, and adaptive layouts for phones and tablets.

Jetpack Compose–first. Offline and private by design: every book, highlight, bookmark, search and vocabulary card lives on-device via Room and DataStore, with no network, no account, and no cloud.

## Overview

- **Library** — visible local search, persisted All/Reading/Want to Read/Finished shelves and sort order, a three-column phone shelf with a Continue Reading card and progress, and a tablet shelf with a persistent filter sidebar. A small weekly reading strip shows which days you opened a book and offers a one-tap return to the latest title; it is derived locally from existing progress and sends no reminders. Shelf filtering and sorting are reused until the books or selected options change, reducing repeated work during other UI updates. Tapping a cover opens book details with working shelf actions and a short Undo action if you move it to the wrong shelf. The empty library has a direct Choose books action; on tablets, Ctrl+O opens Add Books and Ctrl+1–4 switches shelves. On wide tablets, a quick-look pane keeps cover, progress and shelf actions beside the grid; drag its divider to choose the balance. Add Books imports EPUB, MOBI, FB2 and plain-text TXT files, including multi-select, or starts a device scan when storage access is available. Device scanning finds EPUB, MOBI and FB2; TXT stays manual to avoid importing unrelated documents. On tablets, choose a persistent roomy or compact library grid. Idle-time scanning of shared storage, optional SAF folders, deduplication, and external `VIEW` imports remain available. Heavy text and chapter analysis waits for idle time and healthy battery, runs one book at a time, and backs off if the device is warm.
- **Interface type** — the Android UI and widget controls use bundled Inter for a consistent San Francisco-inspired feel. The reader keeps its own serif, sans, literary-serif or monospace choice for book text.
- **Home-screen widget** — Continue Reading shows book cover art and saved progress, opens the book at its saved place, and includes a Library shortcut. When your library is empty, tapping the main widget card opens Add Books directly; when all books are finished, the widget offers a one-tap route back to the shelf. The widget respects light/dark appearance and limits cover decoding to widget-sized images.
- **Reading** — native EPUB (ZIP + OPF + Jsoup), FB2 XML and plain text, plus readable unencrypted PalmDOC/MOBI6 books; EPUB parsing follows encoded and parent-relative spine paths, extracts prose from converted div/section layouts, and leaves embedded media out of the parsing heap. If a real book cannot be read, the reader offers a direct file-reselection action instead of substituting the sample guide. Reading is single-column on phone and tablet portrait (centered to 720dp), then a two-page spread on tablet landscape; true-page and paragraph measurements follow the selected layout width. On tablets, a collapsible Contents rail sits beside the spread for direct chapter jumps; Ctrl+J opens the chapter list in portrait or toggles the rail in landscape. Tap the text to hide chrome, or keep the progress bar always visible via Settings → Reading. Volume keys turn pages.
- **Navigation** — `CONTINUOUS` (whole-book vertical scroll) and `CHAPTER_SWIPE` (vertical within a chapter, horizontal swipe between chapters; tablet shows spreads). Persisted via DataStore and switchable from Settings → Reading or the reader menu with `AnimatedContent` transitions.
- **Built-in guide** — the curated sample book “How to use Folio” (5 chapters: Welcome to Folio; Your Library; Reading, Your Way; Make It Yours; Smart, Private, Calm) covers every current feature in plain editorial language. Settings also includes a swipeable, animated interactive tour with try-it previews for the main workflows, including shelf Undo, empty-library import, tablet panel resizing and the Ctrl+O shortcut.
- **Insights** — a quiet ledger that aggregates existing Room tables (Shelf, Marginalia, Lexicon, Rhythm) with streaks and last-read, no extra tracking. Empty state is encouraging and on-brand.
- **Vocabulary** — double-tap a word (or select → Explain) for a definition from the offline 25k WordNet subset (512 KB gz, real glosses — no fake templates, honest "No definition found" for missing words) and schedule it with on-device SM-2. Review from Insights → Lexicon.

## Reading features

- **Typography — your type, your margins** — font family (Serif Default, Sans, Literary Serif, Monospace), size (Small/Normal/Large/XL), line spacing (Compact/Normal/Relaxed/Loose) and margins (Narrow/Normal/Wide/Extra Wide). Open `aA` in the reader to choose Paper, White, Sepia, or Night, adjust reader-only brightness, text size and margins, or follow the system with Auto Night Mode. Tablets show a compact settings popover with access to the full controls. Choices persist via DataStore and recompute True Pages and Knuth-Plass so pagination and orphan/widow control stay true to what you see.
- **True Pages** — virtual-canvas measurement of the whole book for the current screen, font and palette; `Page X of Y` with `AnimatedContent`, disk-cached per configuration and hash via `TruePageCache` (`totalPages` + `prefixSums` + `perScreenHeightPx`).
- **Knuth-Plass** — orphan/widow control and squared-off paragraphs via micro-kerning and justification, coordinated with True Pages on the same canvas.
- **Guided Reading (Bionic)** — deterministic onset/nucleus/coda syllable splitter that bolds the first syllable; toggle from Settings → Reading or the reader’s `aA` sheet, persisted via DataStore, applied to `AnnotatedString`.
- **Highlights — stylus + finger, five colors, two styles, now text-attached** — stylus draws instantly with true-ink `Multiply`, pressure/tilt physics, and lasso extraction (diagram crop via bounding-box or text copy) but now also stores a precise text range (paragraph + offsets) alongside the stroke for reflow-safe anchoring. Finger: long-press select → Highlight (prominent) in the floating toolbar (Copy/Explain/Highlight) creates a text-attached highlight with exact character offsets. Rendering uses `TextLayoutResult.getBoundingBox()` per paragraph to draw precise rectangles behind glyphs (merging per-line, handling multi-line) for both Fill and Underline, so highlights stay exactly on the words across font/margin/device changes. New highlights store `paragraphIndex`/`startOffset`/`endOffset` as primary anchor (with `anchorText` as LCS fallback); legacy stroke-only highlights are migrated best-effort via anchorText substring search and otherwise rendered via the legacy `Multiply` canvas path. Phone primary is finger long-press (zero-friction, gesture-conflict fixed via `detectTapGestures` instead of `clickable`); tablet primary remains stylus instant-draw (both platforms keep both methods). Colors/styles picked from reader menu, persisted per-highlight; LCS + offset re-anchoring keeps marks with the right sentence after book updates.
- **Keep screen awake** — an optional DataStore-backed switch in Settings → Reading and the reader menu keeps the display on while the book is open, then clears the window flag when the reader closes.
- **Sleep timer** — choose 15, 30, 45 or 60 minutes from the reader menu. When it ends, Folio dims the reader and releases its keep-awake override so the device's normal screen timeout can turn the display off. Cancel or reset the timer at any time.
- **Tablet Contents rail** — a collapsible, draggable-width chapter list beside the landscape spread shows the current chapter and jumps directly to any chapter in either navigation mode. The selected width survives rotation. A connected keyboard can use Page Up/Page Down to turn through the book, Ctrl+F for in-book search, Ctrl+K for library search, and Escape to clear or close it.
- **Bookmarks** — position-only marks (`bookId` + `chapterIndex` + `paragraphIndex` with unique index, `IGNORE` on conflict), distinct from highlights, with a bottom sheet for jump and remove. Current-page indicator and precise restore in both navigation modes.
- **Notes review** — notes attached to highlights auto-save after a short pause, with a Save now action, share and jump controls. Tablets use a split list and editor layout.
- **Quick jumps** — direct Contents, Bookmarks, and Highlights controls with shared `flatIndexForBookmark` logic for Continuous and Chapter-swipe (phone/tablet spreads).
- **People & Topics (X-Ray)** — on-device TF-IDF per chapter, progressive and prioritized for the chapter you are on, disk-cached and computed chapter-by-chapter in `WorkManager` (one heavy book at a time, atomic renames, per-chapter incremental).
- **Search** — library-wide search and a full-screen in-book search with highlighted snippets and Previous/Next result controls; phrases matched on title/author/paragraphs with snippet extraction, ranking highlights/bookmarks first. Debounced (280 ms library, 260 ms in-book), up to 40 results, jump-to-position sharing bookmark flat logic. Graceful no-results.
- **Notes review** — add editable notes to highlights, filter Highlights and Notes, jump back to a passage, share note text, or remove a highlight. Tablets use a split list and editor layout.
- **Comfort Contrast** — ambient-light WCAG 7:1 via `Sensor.TYPE_LIGHT`, throttled and animated gradually (`animateColorAsState` 800–900 ms, EMA smoothing, color-distance debouncing) and palette-coordinated; lifecycle-aware — sensor only registered while `RESUMED` and Comfort Contrast is ON (unregistered in `onPause`/`DisposableEffect`, never left running); shows “No sensor” when unavailable.
- **Evening warmth** — time-aware tint from the system clock (neutral by day, warmer after sunset), gradual and palette-coordinated, persisted via DataStore; 60-second poll is lifecycle-aware (only while `RESUMED` and Evening warmth ON, cancelled otherwise, no overlapping instances); animated together with contrast so colors never flash.
- **Diagrams** — white-margin detection and full-width expansion for images, cropped copy cached on disk and reused.
- **Time remaining** — Rolling-Weight EMA velocity estimator with outlier rejection and character-density prediction.
- **Polish** — chapter-boundary haptics, iOS-like scroll fades (`TopReadingFade`/`BottomReadingFade` reused for Library, Settings, Insights and Vocabulary), premium micro-animations (`animateItem` stagger, `Crossfade` 360 ms, slide/fade transitions), pleasant loading with pulsing amber dot and minimum-granularity display, `navigationBars` insets for edge-to-edge.

## Smart Features guide

Settings → Smart Features is an editorial guide to **fifteen** quiet helpers, each in plain English (what annoys, how Folio helps, why it matters): Guided Reading (Bionic), honest time left, People & Topics (X-Ray), words that stay (SM-2), real pages (True Pages), paragraphs that breathe (line breaking), marks that never get lost (LCS), comfort in any light (contrast), diagrams that fit, evening warmth, find any line (search), meaning where you are (dictionary), your type/your margins (typography), ink in your colors (finger + multi-color + Fill/Underline), and jump anywhere (chapters/highlights/bookmarks quick-jump). All on-device, all private.

## Library intake

Folio discovers `.epub`, `.mobi` and `.fb2` files across shared storage volumes after the user grants Android’s **All files access**. A persisted Storage Access Framework folder remains an optional narrower alternative, and manual import and external `ACTION_VIEW` remain available. TXT files are only imported when selected manually. Device discovery runs through `DeviceScanWorker` on WorkManager with device-idle, battery-not-low, and storage-not-low constraints. A unique daily periodic job and a due-aware one-time job avoid repeat foreground scans. The worker imports one book at a time, copies and hashes in one pass, and caches source path, size, and modification time so unchanged books do not need another hash or parse. Full chapter/X-Ray indexing for scanned books uses a second idle-constrained job. EPUB, FB2, plain text and unencrypted PalmDOC/MOBI6 books can be read; encrypted or Huff/CDIC MOBI variants are skipped.

## Appearance & Settings

Settings is Folio-themed: `Reading` (Always show progress bar, Haptic feedback, Keep screen awake, Guided Reading (Bionic), Comfort Contrast, Navigation segmented control), `Library` (Scan while device is idle, Whole-device books access, Choose books folder via `ACTION_OPEN_DOCUMENT_TREE` with persisted URI permission + Change/Clear, Scan device, Add book manually), `Insights` and `Smart Features` (single buttons), `Appearance` (Theme Light/Dark/Auto segmented control — Auto follows the system, Cool Slate is the dark default — plus 4 dark palettes: Folio Green, True Black, Warm Sepia, Cool Slate, Evening warmth toggle), `Support / Developer` (local 256 KB rolling log via `FileProvider`, Export/Refresh/Clear, privacy note), `Privacy / Data` and `Content Disclaimer` (verbatim: reading app only, no hosting/distribution, user responsible for legal right). Every toggle is DataStore-backed (`folio_settings`) and reflects the persisted value on load. On **phone** Settings is a single-column `LazyColumn` of card sections; on **tablet (≥840dp landscape, same threshold as `TwoColumnReadingContent`)** it becomes a **master-detail** layout — left nav list (Reading, Library, Appearance, Insights, Privacy, etc.) and right detail pane centered at **max 640dp** so toggle rows, segmented controls and palette pickers never stretch full-bleed. Insights, Vocabulary and Smart Features are similarly capped (Insights/Vocabulary centered `720/640dp`, Smart Features two-column grid on tablet), and Onboarding is centered at `640dp`.

## Onboarding

First-launch pager introduces Folio’s library, reading tools, on-device helpers and privacy with illustrations. It does not interrupt setup with permissions: manual EPUB/MOBI/FB2/TXT picking works immediately, while whole-device scanning can be enabled later from Settings → Library. The final page shows the verbatim **Content Disclaimer**: *Folio is a reading application only. It does not provide, host, sell, or distribute any books, and does not include any copyrighted content. Any books you read in Folio come from files you choose to open or import yourself, from your own device or from other apps. You are solely responsible for ensuring you have the legal right to any content you add to Folio, including complying with applicable copyright law in your jurisdiction. Folio's developer is not responsible or liable for how you obtain, use, or possess the content you open in the app.* Gated by `SettingsRepository.hasSeenOnboarding` (`has_seen_onboarding` in `folio_settings`).

## Permissions — Play Console declaration

* `MANAGE_EXTERNAL_STORAGE` enables whole-device EPUB/MOBI/FB2 discovery in shared storage after the user grants All files access in system settings. Android still excludes other apps’ private and protected locations. Google Play requires an All files access declaration and review for distribution; evaluate the current policy before publishing.
* `POST_NOTIFICATIONS` remains optional for future reminders.
* SAF folder grants and manual document import work without All files access. No photo/video permission is requested.

## Tech stack

- Kotlin 2.2, Android Gradle Plugin 9.4 (KSP `com.google.devtools.ksp`; version catalog `gradle/libs.versions.toml`)
- `compileSdk` / `targetSdk` 37, `minSdk` 33, Java 17
- Jetpack Compose BOM `2025.09.00` (`ui`, `foundation`, `material3`, `activity-compose`), Navigation Compose 2.8.4, lifecycle `viewmodel-compose` / `runtime-compose`
- Room 2.7.2 (`room-runtime`, `room-ktx`, KSP `room-compiler`) for books, progress, highlights (color + style `FILL`/`UNDERLINE`, text anchors, and notes), bookmarks (unique `(bookId, chapterIndex, paragraphIndex)`), vocabulary (v12 — `MIGRATION_10_11` adds text anchors and `MIGRATION_11_12` adds shelf state, note text and progress fraction)
- DataStore Preferences 1.1.1 for user settings (alwaysShowProgressBar, `hasSeenOnboarding`, `autoScanEnabled`, `booksFolderUri`, `hapticsEnabled`, `darkPalette` default Cool Slate, app `themeMode` Light/Dark/Auto, reader page style and brightness, `timeTintEnabled`, `bionicEnabled`, `adaptiveContrastEnabled`, `readingFont`/`readingFontSize`/`readingLineSpacing`/`readingMargin`/`highlightColor`/`highlightStyle`)
- Coil 2.7.0 for cover images (downsampled 440×660, mem/disk cache, crossfade); Jsoup 1.18.3 for EPUB; WorkManager 2.9.1 for background X-Ray + parsed cache; DocumentFile 1.0.1 for SAF; `androidx.core.splashscreen` 1.0.1 for launch branding; `androidx.datastore:datastore-preferences` + `WorkManager` + `DocumentFile`
- Offline dictionary `assets/dictionary.json.gz` (25k WordNet-derived, 512 KB gz / 1.5 MB json, real WordNet glosses + 110 Folio-specific overrides; honest "No definition found" for words without a WordNet entry — no synthetic templates); on-device search `SearchRepository` (phrase search, highlights/bookmarks ranked first, `ConcurrentHashMap` + per-book isolation, skips still-processing books)
- Debug logging `FolioLogger` + `FolioApp` (`filesDir/logs/folio.log`, 256 KB rolling, half-trim, no network, `UncaughtExceptionHandler`; `LogsScreen` via `FileProvider`)

## Splash screen

Branded launch via Android's modern SplashScreen API (`androidx.core.splashscreen`, `Theme.SplashScreen`, `installSplashScreen()` in `MainActivity` — no hand-rolled delay). Reference layout: centered app mark in the middle, small two-line "from MakeMission" attribution near the bottom, on a clean solid background.

- **Background** — `folio_splash_background` `#004F39` (Folio deep green, `Color.kt:FolioDeepGreen` / `FolioDarkColorScheme.background`) rather than white — matches Folio's identity. Cool Slate (`FolioPalette.SLATE`) is the app's dark-mode default, but the splash intentionally uses the brand deep green so the cold open feels Folio-green, not neutral grey. The same deep green is used for both light and dark (`Theme.SplashScreen` parent + `postSplashScreenTheme` → `Theme.Folio` DayNight) — the reference aesthetic is best on brand in dark, and a white splash would break identity.
- **Centered mark** — `res/drawable/folio_splash_icon.png` (512 px, transparent) — a clean rendering of the "Folio" wordmark in the app's heavy sans display weight, styled in amber `#F7B538` (`FolioAmber`). Reuses the existing app logomark identity (`mipmap/ic_launcher` adaptive icon) as a wordmark placeholder; amber keeps the launch recognizable against the deep green. Set as `windowSplashScreenAnimatedIcon` and centered by the system (Android 12+ centers and scales to ≤288 dp).
- **"from MakeMission" attribution** — `res/drawable/folio_splash_branding.png` (560×140 px, transparent) — small muted "from" label above a bolder "MakeMission" word, positioned near the bottom via `android:windowSplashScreenBrandingImage` (shown on API 31-32; platform ignores the attr on 33+ where the centered amber wordmark remains the primary brand moment). Uses the app's UI sans weight hierarchy (lighter muted top, semibold bottom) and off-white `FolioOffWhite` at reduced alpha.
- **No artificial delay** — `MainActivity:34` calls `installSplashScreen()` before `super.onCreate()` and does not set `setKeepOnScreenCondition` with a timer; the splash is kept only until the first Compose frame is ready, then crossfades via `postSplashScreenTheme` to `Theme.Folio` and the normal `FolioNavHost` Library/Onboarding flow. `windowSplashScreenAnimationDuration 400` is only the icon animation, not a hold.
- **Light/dark** — post-splash respects `ThemeMode` Light/Dark/Auto (DataStore `themeMode` + `darkPalette`). The system splash itself stays deep green in both modes (straightforward DayNight via `Theme.SplashScreen` would need a separate `values-night` splash background, but that would be a second brand color for little gain); defaulting to dark matches the reference and avoids a white flash on launch.

## Design system

Palette and layout draw from the phone and tablet mockups in `Inspiration`:

- **Library** — cool gray `#F5F5F7` with clean white cards, blue `#0969DA` interactive controls, and typographic book covers. **Reader** — warm paper `#FBF6EA`, serif text, and a charcoal night palette; warm yellow remains the highlight color.
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
│       │   │   ├── db/ FolioDatabase v12 + dao/* + entity/*  # Book shelf state/Progress/Highlight notes and anchors/Bookmark/Vocabulary
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
│       │       ├── reader/ ReadingScreen (Continuous vs Chapter-swipe, TruePage/Knuth + typography/margins/text-attached highlights via TextLayoutResult), ReadingViewModel (text offsets + LCS fallback), BionicReading, VelocityEstimator, TruePageEngine, KnuthPlassEngine, ReaderPageTurnHandler, ReadingAppearance, components/TextHighlightRenderer + HighlightOverlay + ExplainSelectionContainer/*
│       │       ├── library/ LibraryScreen + LibraryViewModel (minimal import + scan dedup + recently-read sorting + scroll-to-top), components/*
│       │       │       ├── onboarding/ OnboardingScreen (pager 5 with Content Disclaimer, permissions, DataStore gate)
│       │       ├── settings/ SettingsScreen + SmartFeaturesScreen (15 features) + LogsScreen
│       │       ├── insights/ InsightsScreen + InsightsViewModel (read-only journal)
│       │       └── vocabulary/ VocabularyScreen + VocabularyViewModel (SM-2 review)
│       └── res/ mipmap-*, drawable/{folio_splash_icon,folio_splash_branding}, values/{strings,colors,themes,dimens} (+ folio_splash_background), xml/{backup_rules,data_extraction_rules,file_paths}
├── Inspiration/ phone and tablet PNG mockups
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
