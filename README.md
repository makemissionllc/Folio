# Folio

Folio is a premium, distraction-free Android ebook reader by MakeMission LLC (`com.makemission.folio`). It bridges digital convenience and the tactile craft of traditional bookmaking — fluid stylus interactions, magazine-quality typography, and adaptive layouts for phones and tablets.

Jetpack Compose–first. Library (editorial grid + import + Vocabulary badge), core Reading (native EPUB, adaptive layouts, Room progress + frictionless navigation, true-page numbers via virtual-canvas pre-computation), stylus highlighting (zero-friction, true-ink Multiply, pressure/tilt physics, lasso extraction), bionic reading, chapter time remaining, LCS-anchored highlights, on-device X-Ray, offline dictionary, and spaced-repetition vocabulary (SM-2, pure on-device) are now in place; other algorithmic features come later.

## Tech stack

- Kotlin 2.2, Android Gradle Plugin 9.4 (KSP via `com.google.devtools.ksp`; version catalog in `gradle/libs.versions.toml`)
- `compileSdk` / `targetSdk` 37, `minSdk` 33, Java 17
- Jetpack Compose (BOM `2025.09.00`): `ui`, `foundation`, `material3`, `activity-compose`
- Navigation Compose 2.8.4 (`navigation-compose`), lifecycle `viewmodel-compose` / `runtime-compose`
- Room 2.7.2 (`room-runtime`, `room-ktx`, KSP `room-compiler`) for books, progress, highlights + vocabulary (v6 — SM-2 vocabulary)
- EPUB parsing: native ZIP + `org.jsoup:jsoup:1.18.3` (no network/AI — §6), cover extraction via OPF manifest
- Offline dictionary: bundled `assets/dictionary.json` (compact WordNet-style, permissively-licensed, ~120 entries) via `DictionaryRepository` — no network
- Coil 2.7.0 (`io.coil-kt:coil-compose`) for cover images
- Storage Access Framework (SAF) for import — system file picker, copy to private storage
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
│       ├── assets/dictionary.json        # offline WordNet-style dictionary (no network)
│       ├── AndroidManifest.xml
│       ├── java/com/makemission/folio/
│       │   ├── MainActivity.kt                 # host — FolioNavHost + volume-key dispatch
│       │   ├── navigation/FolioNav.kt          # NavHost: library ↔ reader ↔ vocabulary
│       │   ├── data/
│       │   │   ├── dictionary/DictionaryRepository.kt # offline asset lookup (WordNet-style)
│       │   │   ├── vocabulary/Sm2.kt           # SuperMemo-2 scheduling (pure on-device, deterministic)
│       │   │   ├── xray/{XRayTerm, XRayExtractor (TF-IDF), XRayCache (file)} 
│       │   │   ├── anchor/LcsAnchor.kt         # LCS diff + anchor relocation (pure on-device)
│       │   │   ├── db/ { FolioDatabase v6, dao/{BookDao, ReadingProgressDao, HighlightDao, VocabularyDao}, entity/{BookEntity, ReadingProgress, Highlight (anchorText), VocabularyCard (SM-2)} }
│       │   │   ├── epub/EpubParser.kt         # native EPUB3 (ZIP+OPF+Jsoup) + cover extraction + fallback
│       │   │   └── model/Book.kt              # UI model (coverColor + filePath/coverImagePath) + curated seed
│       │   └── ui/
│       │       ├── theme/ { Color, Type, Theme }.kt
│       │       ├── library/
│       │       │   ├── LibraryScreen.kt        # Scaffold + header + FAB import + SAF picker + Vocabulary teaser/badge
│       │       │   ├── LibraryViewModel.kt     # import (copy→parse→cover→Room), books Flow, error Snackbar, due vocabulary count
│       │       │   └── components/ { BookGrid, BookCoverCard (AsyncImage), EmptyLibraryState }
│       │       ├── reader/
│       │       │   ├── ReadingScreen.kt        # serif body + chrome/volume + highlight/lasso + bionic toggle + diagram + dictionary→vocabulary + true pages near progress
│       │       │   ├── ReadingViewModel.kt     # per-book file load, progress + highlights + vocabulary tracking (Flow, pressure/tilt, SM-2 save)
│       │       │   ├── ReadingViewModelFactory.kt
│       │       │   ├── BionicReading.kt        # deterministic onset/nucleus/coda syllable splitter
│       │       │   ├── VelocityEstimator.kt      # Rolling-Weight EMA + outlier + char-density time-remaining
│       │       │   ├── TruePageEngine.kt       # virtual canvas measurement pass → absolute page numbers (cached on rotate/font)
│       │       │   ├── ReaderPageTurnHandler.kt# volume-key dispatch bridge
│       │       │   └── components/ { ReadingProgressBar.kt, HighlightOverlay.kt (pressure/tilt Multiply + lasso), XRayBottomSheet.kt, DictionaryPopup.kt }
│       │       └── vocabulary/
│       │           ├── VocabularyScreen.kt     # review flow: show word → reveal → Again/Hard/Good/Easy → SM-2 schedule
│       │           └── VocabularyViewModel.kt  # due/all flows, currentReview, Sm2.schedule on rate
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

## Library screen (editorial grid + import)

`LibraryScreen` + `LibraryViewModel` — inspired by `book-story-master`'s `LibraryScaffold` →
`LibraryGridLayout` layering, rebuilt for Folio and extended for import:

- **Curated grid** — `LazyVerticalGrid` with `GridCells.Adaptive(148.dp)` so phones show 2 columns and tablets scale naturally; each item is a `BookCoverCard` (2:3 cover, rounded 16dp, spine accent, Folio title chip, cover image via Coil when available). Cards are clickable and navigate to the Reader.
- **Empty state** — centered flat illustration (amber sun, burgundy / paper / deep-green books on a shelf) drawn with Compose `Canvas`, plus editorial copy. Shown when both imported and curated are empty (curated ensures the grid is never empty before first import).
- **Import (§6)** — FloatingActionButton (“+”) launches the Storage Access Framework (`ActivityResultContracts.OpenDocument` with `application/epub+zip` + `*/*`). The returned URI is copied into `filesDir/books/<uuid>_name.epub` (private storage — survives if the user moves/deletes the original), parsed with the existing `EpubParser` (title/author + cover via OPF `meta[name=cover]` / `properties="cover-image"` → `covers/<id>.jpg`), and saved as a `BookEntity` (`filePath`, `coverImagePath`) in Room. Invalid/corrupted EPUBs show a Snackbar (“Could not parse EPUB…”) and do not crash.
- **Grid update** — `LibraryViewModel.books` is `bookDao.observeAll().map { imported + curatedSampleBooks() }` so imported books appear first in the grid alongside the curated samples; covers show the extracted image when present.
- **Vocabulary teaser (§5 — SM-2, non-intrusive)** — a discreet `Card` below the header shows due-for-review count (`LibraryViewModel.dueVocabularyCount` via `VocabularyDao.observeDueCount()`). When `dueCount > 0` a small amber badge displays the number; otherwise it shows “No words due — keep reading” with an `Open` button. Tapping navigates to the `Vocabulary` review screen (`FolioNav` `vocabulary` route). No reading popups or interruptions — the badge surfaces only on the Library screen.
- **Header** — weighty sans "Library" title + collection subtitle over the Folio background.

## Reading screen (core + frictionless navigation + stylus engine)

`ReadingScreen(bookId, bookTitle, onBack)` — per §3, §4 Stylus & §6:

- **EPUB parsing** — `EpubParser` is a native engine: `ZipInputStream` → `container.xml` → OPF manifest/spine → `toc.ncx` → Jsoup extraction of paragraphs, plus cover extraction as above. No network, no AI. Loads the per-book private file (`bookDao.getById(bookId).filePath`) when present; falls back to `assets/sample.epub` and then `sampleFallbackChapters` so the UI is always usable.
- **Typography** — chapter titles in heavy sans (`headlineSmall` / `titleMedium`), body in Folio serif (`bodyLarge` 17/27, `bodyMedium` on tablet) on the Folio background.
- **Phone (§3)** — single-column, edge-to-edge, immersive; paragraphs in a `LazyColumn` with Folio spacing and amber rule between chapters.
- **Tablet (§3)** — landscape + `screenWidthDp >= 840` triggers a two-column spread: chapters split into left/right `LazyColumn`s with a central gutter (book-spine), mimicking a physical spread.
- **Progress (§6)** — `FolioDatabase` (`Room` v6) with `ReadingProgress` (`bookId` PK, `chapterIndex`, `paragraphIndex`, `lastReadMillis`). `ReadingViewModel` observes/saves position via `ReadingProgressDao`; restored on next open.
- **True-Page Calculation Engine (§5 — virtual canvas, cached)** — `ui/reader/TruePageEngine.kt`: off-screen measurement pass that pre-computes the entire book's layout for the current screen dimensions and font size (`rememberTextMeasurer` constrained to the column width — 40dp phone / (screen-52dp)/2 tablet — measuring titles with `headlineSmall`/`titleMedium` and body with `bodyLarge`/`bodyMedium` including `BionicReading` bold spans when enabled). Heights per flat item (title + paragraphs + diagram 172dp + gap 17dp) are summed; page breaks are injected every `availableHeightPx` (phone) or `availableHeightPx*2` (tablet spread — physical page turns, not columns), producing `Page X of Y` (e.g., `Page 45 of 312`). The `TruePageInfo` is `remember`ed on `screenWidthDp`/`screenHeightDp`/`orientation`/`fontScale`/`density`/`bionicEnabled`/`chaptersKey` only — not on every scroll — and the current page is derived via `derivedStateOf { info.pageFor(firstVisibleItemIndex) }` so scrolling is cheap while totalPages stays stable; bionic toggle or rotation recalculates. Displayed near the existing progress bar as a `Page X of Y` label in a `Row` alongside `VelocityEstimator`'s time-remaining (inside the chrome-visibility `AnimatedVisibility` so it hides in immersive mode), working for both phone (single-column page) and tablet (spread page count reflects physical turns). Built on top of existing layouts — not a rewrite; reference `book-story-master` consulted for structure only.
- **Time remaining (§5 — Rolling-Weight Velocity Estimator)** — `VelocityEstimator` tracks delta between page turns, smooths with an Exponential Moving Average (α=0.35) and discards outliers via σ-threshold (e.g., 15-min idle), then predicts from remaining *character density* (upcoming chars / EMA speed) not just page count; displayed as a small “12 min left in chapter” label near the progress bar (pure on-device, no network).
- **Frictionless navigation (Folio spec §3)** — inspired by `book-story-master`'s `ReaderProgressBar`:
  - *Hardware page turns* — volume up/down advance a page (one-handed phone use). `MainActivity.onKeyDown` forwards to `ReaderPageTurnHandler` → `animateScrollToItem` by a page.
  - *Minimalist progress bar* — thin amber fill on muted track at the bottom. The bar shows `firstVisibleIndex / total` and tapping it seeks (`animateScrollToItem` to tapped fraction).
  - *Tap-to-toggle chrome* — tapping the reading area toggles the top bar + progress bar (with `AnimatedVisibility` slide/fade) for a fully distraction-free immersive view.
- **Stylus engine (§4)** — `HighlightOverlay` captures only stylus (`MotionEvent.TOOL_TYPE_STYLUS`, finger passes through for scroll/tap), so a stylus touch instantly draws with no menu or toolbar (zero-friction) and stores in Room (`highlights`: `bookId`, `chapterIndex`, normalized `pointsData` + `pressuresData`/`tiltsData`, `anchorText`, `color`):
  - *True-ink* — strokes render with `BlendMode.Multiply` in Folio amber `#F7B538` so serif text stays crisp.
  - *Organic physics* — `MotionEvent` pressure (0..1) and `AXIS_TILT` (0..π/2) dynamically scale stroke width (`base 28dp * pressureFactor * tiltFactor`) for a natural hand feel.
  - *Lasso extraction* — a closed-loop stylus circle is classified (closure, bounds, circularity) distinctly from a highlight. Over an image it extracts the diagram as a PNG to cache/clipboard; over text it runs on-device OCR (local text copy, no network) to clipboard — both entirely private. A `DiagramPlaceholder` (Fig. 1) in the first chapter demos image lasso; text-lasso copies paragraph text. UI is a small dialog with *Extract Image* / *Copy Text*.
  - *LCS anchors (§5)* — each highlight stores the surrounding paragraph snippet (`anchorText`, ~80 chars) as a contextual anchor; on reopen/reimport, `LcsAnchor` runs a pure on-device Longest Common Subsequence scan over the newly parsed chapters to relocate the highlight to the closest matching paragraph (threshold 0.55), or leaves it orphaned if no reasonable match — so highlights survive EPUB typo-fix updates.
- **Bionic reading (§5)** — `BionicReading` is a deterministic, on-device syllable algorithm (no dictionary, no network) that analyzes each word's onset / nucleus / coda to find the first syllable (vowel-cluster nucleus + optional single-consonant coda, clamped to ~60%) and bolds it via `AnnotatedString` + `SpanStyle(Bold)` for faster scanning. A top-bar toggle (*Bionic On/Off*) applies it to both phone (single-column) and tablet (two-column) layouts, keeping the serif body but adding visual anchors.
- **Offline dictionary (§6 — on-device)** — `DictionaryRepository` loads a compact open-source `assets/dictionary.json` (WordNet-style, ~120 entries, permissively-licensed, no network) once and caches it. Double-tapping any word in the reading text (via `TextLayoutResult.getOffsetForPosition` + `detectTapGestures` onDoubleTap) looks up the lowercased word and shows a sleek `Dialog`-based popup (`DictionaryPopup`) near the tap with the definition, or a graceful “No definition found” state. Works with `PointerType.Stylus` highlighting (stylus-only) and single-tap chrome toggle without conflict, on both phone and tablet layouts. Built on top of existing text rendering — `BionicReading` not rewritten.
- **Spaced Repetition Vocabulary (§5 — SM-2, pure on-device)** — `ReadingViewModel.trackVocabulary(word, definition)` is called whenever a dictionary lookup succeeds (extended without rewriting `DictionaryRepository`/`DictionaryPopup`). Words are saved locally in Room (`vocabulary` table: `word` PK lowercased, `definition`, `easeFactor` 2.5, `intervalDays`, `repetitions`, `dueAt`, `lastReviewedAt`) via `VocabularyDao`/`FolioDatabase` v6 (extended schema). `data/vocabulary/Sm2.kt` implements SuperMemo-2 deterministically: Again=0, Hard=3, Good=4, Easy=5 → update ease (`EF' = EF + 0.1 − (5−q)(0.08+(5−q)*0.02)`, min 1.3), interval (0→1, 1→6, else `interval*EF`), repetitions, and `dueAt = now + interval*24h`. No network calls. Review flow (`ui/vocabulary/VocabularyScreen` + `VocabularyViewModel`) shows a word, lets the user reveal the definition, self-rate Again/Hard/Good/Easy, then applies SM-2 to schedule the next review; due words are observed via `VocabularyDao.observeDue()` and surfaced discreetly via the Library Vocabulary badge/teaser — never interrupting reading with popups.
- **X-Ray (§5 — TF-IDF)** — `XRayExtractor` is a local, deterministic TF-IDF index (no network, no external dictionary): for each chapter it counts capitalized proper-noun candidates (filtered by stopwords) and scores them by `tf * idf` (`tf = count/totalWords`, `idf = ln(totalChapters/df)`) to surface distinctive terms (characters, locations, jargon). The per-book, per-chapter index is computed once on first open (or import) and cached to `filesDir/xray/<bookId>.json` (also in-memory via `ReadingViewModel.xrayIndex`), then surfaced as a minimalist `ModalBottomSheet` via an *X-Ray* top-bar button; tapping a term shows which chapters it appears in.

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
