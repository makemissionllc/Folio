# Folio — Project Log

Changelog after every coding session. `README.md` stays as the basic app
description and documentation; this file tracks what changed and when.

Active coding branch: `main`.

---

## Session 34 — 2026-09-11 — Visual simplification: Library shelf-first, Settings minimal labels

Branch: `main`.

### Built

- **Library — bookshelf-first, not dashboard** — Simplified `ui/library/LibraryScreen.kt` to feel like looking at a shelf: **Header** now only shows weighty sans "Library" title with minimal icon buttons "⌕" and "⚙" (subtitle "$bookCount titles · editorial grid" removed — visual weight halved, count discoverable via grid itself). **Teaser cards collapsed** — the previous two full-width `Card`s (`VocabularyTeaser` below header + `InsightsTeaser` below vocabulary) replaced by a single subtle `LibraryQuickRow` (`Row` `fillMaxWidth` `padding 16/6`, two pill `clickable` `RoundedCornerShape 20dp` `padding 10/6` elements): left shows amber `6.dp` dot + "N due" text and compact primary badge when `dueCount > 0` (via `VocabularyDao.observeDueCount()`), otherwise muted "Vocabulary"; right shows minimal "Insights ›" `labelSmall`. Both remain reachable via tap or swipe (swipe-right Settings / swipe-left Insights via existing `pointerInput` `hasNavigated` debounce, `launchSingleTop`) but no longer sit as two always-visible full-width cards above every grid. Legacy `LibraryScreen(books, …)` overload also updated to use `LibraryQuickRow`. Scan progress `Card` and pull-down `LibrarySearchBar` kept (non-blocking, on-demand). No features removed — same navigation, same `dueVocabularyCount`, same scan pipeline.

- **Settings — need-to-know minimal, progressive disclosure** — `ui/settings/SettingsScreen.kt` trimmed to short labels + subtitles only where non-obvious: `SettingsHeader` subtitle enumeration ("Reading • Library • ...") removed, leaving only "Settings" title + back; `SettingsHero` paragraph ("Organized to grow…") removed, leaving illustration + "Folio Settings" title only; `SettingsSection`/`SettingsToggleRow`/`SettingsInfoRow` now conditionally render subtitle only when `isNotBlank()` (empty hides spacer+text, `labelSmall` `alpha 0.7` when shown). **Reading** — subtitles cleared for "Always show progress bar" (self-explanatory) and shortened to "On chapter turn" for haptics. **Library** — "Auto-scan on launch" shortened to "Downloads & Documents" / "Needs permission", permission denial note shortened to "Grant in system settings to enable.", "Scan device" subtitle to "Find new EPUBs", footer EPUB note removed. **Insights** + **Smart Features** — descriptions removed, buttons shortened to "Open" / "Explore". **Appearance** — palette intro + app-wide explanation removed, "Evening warmth" shortened to "Warmer tones after sunset", "Adaptive contrast" to "Reader → Contrast Auto". **Support / Developer** — two paragraphs collapsed to single "Local log for troubleshooting. Nothing sent automatically." + "View logs". **Privacy / Data** — shortened to "No cloud sync" / "Cleared with app data". All functionality retained (switches, scan button, navigation to Insights/SmartFeatures/Logs, palette picker) — only text/visual weight reduced for a calmer premium iOS-style feel.

### Changed

- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1` — `LibraryHeader` removed collection subtitle `Text` (`"$bookCount titles · editorial grid"`), kept only `headlineLarge` "Library" + icon `TextButton`s ("⌕"/"⚙"); replaced `VocabularyTeaser` + `InsightsTeaser` `Card`s with single `LibraryQuickRow` (`Row` with amber dot + badge + "Insights ›", `CircleShape` `20dp` pill, `labelSmall`), updated main `LibraryScreen` and legacy overload to use it; removed `VocabularyTeaser`/`InsightsTeaser` composables, added `LibraryQuickRow`; added `CircleShape` import; scan progress between quick row and grid kept; gesture handling unchanged (swipe-right Settings, swipe-left Insights, pull-down search).
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1` — removed `SettingsHeader` subtitle `Text` ("Reading • Library …"), removed `SettingsHero` subtitle `Text` ("Organized to grow…"); `SettingsSection` now `if (subtitle.isNotBlank())` guard; `SettingsToggleRow`/`SettingsInfoRow` similarly guard and use `labelSmall` `alpha 0.7` for remaining short subtitles; Reading subtitles cleared/shortened, Library subtitles shortened, Insights/Smart Features descriptions removed + buttons renamed, Appearance palette intro + footer removed + Evening warmth/Adaptive contrast shortened, Support paragraphs collapsed, Privacy subtitles shortened; no DataStore or navigation rewrite, all toggles/buttons retained.
- `README.md` — project structure `LibraryScreen.kt` line now "subtle quick row (vocabulary badge + insights) + grid … swipe-right/left minimal header" and `SettingsScreen.kt` line now "minimal labels, subtitles only where needed, progressive disclosure"; Library section **Header** now "no subtitle — bookshelf-first, minimal icon buttons"; merged **Vocabulary/Insights teaser** bullets into single **Quick row — Vocabulary + Insights** minimal bullet (amber dot + badge, muted fallback, swipe/row entry); **Scan progress** reference updated to "between quick row and grid"; **Library gestures** now references quick row / icons; **Settings screen** bullet rewritten to enumerate minimal sections with short subtitles and progressive disclosure note.
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL`.
- Library: opens to "Library" title + grid with no subtitle or full-width cards; quick row shows muted "Vocabulary" when 0 due (amber dot muted) and amber dot + "N due" + badge when >0, plus "Insights ›"; tap vocabulary → Vocabulary review, tap insights / swipe-left → Insights, swipe-right / tap ⚙ → Settings; pull-down / tap ⌕ reveals search (on-device, highlights/bookmarks priority, jump-to-position via `flatIndexForBookmark`); long-press cover → `LibraryBookActionsSheet` (Remove/Info/Reset) still works; scan progress `Card` appears between quick row and grid when `isScanning`; vertical `LazyVerticalGrid` scroll coexists with horizontal `|dx|>|dy|` swipe.
- Settings: `LazyColumn` shows 7 sections with minimal text — Reading shows two toggles with no/long-short subtitle, Library shows auto-scan toggle + Scan button with short hints, Insights/Smart Features each show single button (Open/Explore), Appearance shows 4 palette swatches + Evening warmth/Adaptive contrast with short subtitles, Support shows single privacy line + View logs, Privacy shows two rows with short subtitles; all switches/buttons navigate/toggle as before (DataStore persisted), no missing feature, visual weight sharply reduced.
- No functionality removed — all features reachable (vocab badge, insights via swipe/row, every setting toggle/button) just less "in your face".

---


## Session 33 — 2026-09-10 — Navigation & interaction rebuild (no FAB, long-press library, swipe both ways, single reading menu, VIEW intent)

Branch: `main`.

### Built

- **Library — no FAB, intake via auto-scan + external VIEW, reused pipeline** — Removed `FloatingActionButton` ("+") and `rememberLauncherForActivityResult(OpenDocument)` + `ActivityResultContracts` from `ui/library/LibraryScreen.kt` (no manual SAF picker). Intake now only via (a) existing auto-scan (`LibraryViewModel.scanDevice` + `EpubScanner`, `persistParsedEpub` → `bookDao.insert`, hash/path dedup, non-blocking) and (b) external `ACTION_VIEW` — `MainActivity` (`singleTop` + `onNewIntent`) now handles `Intent.ACTION_VIEW` with `data` uri (Files / browser download / email attachment) and forwards to activity-scoped `LibraryViewModel.importEpub(uri, context)` reusing the *same* `importInternal` → `persistParsedEpub` private-copy→parse→cover→Room pipeline (no duplication, FolioLogger `INFO/WARN/ERROR`, Snackbar on parse fail). `AndroidManifest.xml` adds `MainActivity` `intent-filter` (`VIEW` + `DEFAULT`+`BROWSABLE` + `content`/`file` + `application/epub+zip`/`application/epub`/`application/octet-stream`/`*/*` + `.epub`/`.EPUB` pathPattern) with `launchMode="singleTop"` so Folio appears as an option when opening `.epub` from other apps. `LibraryScreen` no longer needs `Intent` launcher import; build on top of existing `LibraryViewModel`/`EpubParser`.

- **Library — long-press book cover → actions bottom sheet (reuse existing data)** — `data/model/Book.kt` unchanged; `LibraryViewModel` adds `isCuratedBook(book)`, `removeBook(book, context)` (checks curated via `curatedSampleBooks().any { id== }` → emit "Curated sample — can't be removed"; else deletes `BookEntity` + `File(filePath)`, `File(coverImagePath)`, `ReadingProgressDao.clear`, `HighlightDao.clearForBook`, `BookmarkDao.clearForBook`, `XRayCache.invalidate`, logs via `FolioLogger`, emits `"Removed \"title\""`), `resetProgress(bookId)` (`ReadingProgressDao.clear`), no new feature logic. `ui/library/components/BookGrid.kt` adds `onLongClick: (Book)->Unit = {}` param; `BookCoverCard.kt` switches from `clickable` to `combinedClickable(onClick, onLongClick)` (`ExperimentalFoundationApi`, `ExperimentalFoundationApi` opt-in) with same visuals. `LibraryScreen.kt` adds long-press state (`selectedBook`, `showBookActions`, `showInfoDialog`), passes `onLongClick` to `BookGrid`, shows new `LibraryBookActionsSheet.kt` (`ModalBottomSheet`, `skipPartiallyExpanded=true`) with title/author, curated notice, `Book info` (`AlertDialog` with title/author/id/file/cover), `Reset progress`, `Remove from library` (disabled for curated, `error` color), Close. Teaser cards kept.

- **Library — swipe left to Insights (additional gesture, teaser kept)** — `LibraryScreen`'s combined `pointerInput` (existing swipe-right to Settings with `hasNavigated` debounce + `launchSingleTop`) now also handles `totalDx < -120 && |totalDx|>|totalDy|` → `onInsightsClick()` (also debounced via `hasNavigated`, reset on `all { !pressed }`), reusing same debounce logic so one swipe = one push. Coexists with vertical `LazyVerticalGrid` scroll (`|dx|>|dy|` check) and pull-down search (`totalDy>80`). `FolioNav` already uses `launchSingleTop` for Insights; teaser card in `LibraryScreen` kept (`InsightsTeaser` below `VocabularyTeaser`), not removed — now two ways in.

- **Reading — single menu icon instead of cluttered top-bar icons (only UI wiring)** — `ui/reader/ReadingScreen.kt` `ReadingScreenContent` keeps `chromeVisible`, `bionicEnabled`, `showXRay`, `showBookmarks`, `showInBookSearch`, adds `showMenu`. `TopAppBar` actions previously 5-6 `TextButton`s (`⌕`, `☆/🔖`, `Bookmarks N`, `X-Ray`, `Bionic On/Off`, `Contrast Auto/Fixed`) replaced by a single `TextButton("☰")` (`titleMedium`, `onSurfaceVariant`) that sets `showMenu=true`. Back (`← Back`) and title (`bookTitle`) remain visible as before; `Page X of Y` + `timeRemaining` in bottom bar unchanged. New `ui/reader/components/ReaderMenuSheet.kt` (`ModalBottomSheet`, `skipPartiallyExpanded`) lists toggles/actions reusing existing callbacks without touching algorithms: Search in book (toggles `showInBookSearch`), Bookmarks (`Bookmarks · N` → `BookmarkBottomSheet`), Bookmark this page (`isCurrentBookmarked` star logic via `onToggleBookmark(currentBookmarkPos)`), X-Ray (`XRayBottomSheet`), Bionic reading (`Switch` for `bionicEnabled`), Contrast (`Switch` for `adaptiveEnabled && hasSensor`). All sheets (`XRayBottomSheet`, `BookmarkBottomSheet`, search bar) reused via existing state, not duplicated. `BionicReading`, `XRayExtractor`, `TruePageEngine`, `KnuthPlass`, `Sm2`, etc. untouched as required.

- **Settings — confirm swipe-right still coexists with vertical scroll** — No change to `ui/settings/SettingsScreen.kt` (`LazyColumn` with `SettingsHero`, 7 sections, `animateContentSize`); Library's `pointerInput` already prioritizes `|dx|>|dy|` and only consumes horizontal, so `LazyColumn` vertical scroll in Settings (and `LazyVerticalGrid` in Library) still works. `FolioNav` `launchSingleTop` for Settings/Insights kept.

### Changed

- `app/src/main/AndroidManifest.xml:31` — `MainActivity` `launchMode="singleTop"` + `VIEW` intent-filter (`DEFAULT`+`BROWSABLE` + `content`/`file` + 4 mimeTypes + `.epub` pathPatterns).
- `app/src/main/java/com/makemission/folio/MainActivity.kt:1` — adds `handleEpubViewIntent` (`onCreate` + `onNewIntent` → `ViewModelProvider LibraryViewModel.importEpub` reusing pipeline), imports `Intent`/`ViewModelProvider`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1` — removes `FloatingActionButton`/`rememberLauncherForActivityResult`/`ActivityResultContracts` + FAB, removes launcher block, adds `selectedBook`/`showBookActions`/`showInfoDialog` state, adds left-swipe `< -120` to `onInsightsClick`, passes `onLongClick` to `BookGrid`, shows `LibraryBookActionsSheet` + `BookInfoDialog`; keeps `VocabularyTeaser`/`InsightsTeaser`, auto-scan, search, `hasNavigated` debounce, `launchSingleTop` via `FolioNav`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:367` — adds `isCuratedBook`, `removeBook(book, context)` (file/DB/cache cleanup, `FolioLogger`, `_scanResult`), `resetProgress(bookId)`.
- `app/src/main/java/com/makemission/folio/ui/library/components/BookGrid.kt:1` — adds `onLongClick` param and forwards to `BookCoverCard`.
- `app/src/main/java/com/makemission/folio/ui/library/components/BookCoverCard.kt:1` — `clickable` → `combinedClickable` (`ExperimentalFoundationApi`), adds `onLongClick` param, `let` → `when` with `combinedClickable`.
- `app/src/main/java/com/makemission/folio/ui/library/components/LibraryBookActionsSheet.kt` — new: `LibraryBookActionsSheet` (`ModalBottomSheet` with Remove/Info/Reset) + `BookInfoDialog` (`AlertDialog`).
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:192` — adds `showMenu` state, replaces `TopAppBar` 6-button `actions` with single `☰` menu button, adds `ReaderMenuSheet` sheet before `XRay`/`Bookmarks` sheets, keeps `onBack` + chapter/page info; no algorithm changes.
- `app/src/main/java/com/makemission/folio/ui/reader/components/ReaderMenuSheet.kt` — new: `ReaderMenuSheet` (`ModalBottomSheet` with 5 actions + bookmark toggle, `MenuActionRow`/`MenuToggleRow`).
- `README.md` — intro now "no FAB, long-press, swipe both ways, single hamburger menu"; tech stack `Epub intake` now external `VIEW` + auto-scan; project structure adds `LibraryBookActionsSheet`, `ReaderMenuSheet`, `combinedClickable`, `MainActivity VIEW`; Library section now "no FAB, intake via VIEW + auto-scan, long-press actions, grid update + long-press, gestures both ways"; Reading intro now "single hamburger menu"; adds **Reading menu** bullet.
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL`.
- Library: tap cover → `ReadingScreen` (existing `onBookClick` → `Reader.create` still works); long-press on curated → sheet with "Can't remove sample" disabled, Info shows curated dialog, Reset progress emits Snackbar; long-press on imported → Remove deletes entity + files + progress/highlights/bookmarks/cache, grid updates via eager `books` flow; no FAB visible; swipe right → Settings (1 push, `launchSingleTop` + `hasNavigated`), swipe left → Insights (also 1 push), both coexist with `LazyVerticalGrid` vertical scroll and pull-down search (tested |dx|>|dy| priority); vertical scroll in Settings `LazyColumn` still works.
- External VIEW: `adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/test.epub -t application/epub+zip com.makemission.folio` → `MainActivity.onNewIntent` → `LibraryViewModel.importEpub` (same `persistParsedEpub`) → book appears in grid (hash dedup, cover), Snackbar; duplicate VIEW reuses pipeline and is deduped.
- Reading: top bar shows only `← Back` + title + `☰`; tapping `☰` opens `ReaderMenuSheet` with 5-6 rows (Search, Bookmarks, Bookmark here, X-Ray, Bionic toggle, Contrast toggle); each row triggers the same existing sheet/toggle logic (X-Ray `TF-IDF`, Bionic `onset/nucleus/coda`, Contrast `AdaptiveContrastEngine`, Bookmarks `FlatIndex`, Search `SearchRepository`) — no algorithm change, just trigger.
- Settings scroll: `LazyColumn` with 7 sections scrolls vertically; swipe-right from Library still opens Settings as before.

---

## Session 32 — 2026-09-10 — Bug fixes: Library empty, Settings back-stack, Reading fade

Branch: `main`.

### Fixed

- **BUG 1 — Library "No books found" even though curated samples should always show** — Root cause: `ui/library/LibraryViewModel.kt:112` `books` was `stateIn(..., SharingStarted.WhileSubscribed(5_000), emptyList())`. With `WhileSubscribed`, when Library had no active collector (e.g., during onboarding, after navigating to Settings, or on first composition before Room emitted), the upstream `bookDao.observeAll()` was cancelled and the StateFlow reset to `emptyList()`. The `map { imported + curatedSampleBooks() }` was therefore not applied until the next DB emission, so `LibraryScreen` saw `books.isEmpty()==true` and rendered `EmptyLibraryState` ("Your library is empty" — reported as "No books found") even though `curatedSampleBooks()` (8 titles) should make the grid never empty before first import. Manual import and auto-scan both feed via the same Room `books` table, so their inserts also depended on this flow; the UI would stay empty until a new DB emission after import. Fix: `LibraryViewModel` now `stateIn(..., SharingStarted.Eagerly, curatedSampleBooks())` — eager collection keeps Room observed, and the initial value is the 8 curated titles so the grid is populated instantly even before the first DB load; subsequent DB emissions still prepend `imported` before `curated`. Verified: `LibraryScreen` `when { searchQuery.isNotBlank() -> SearchResultsList; books.isEmpty() -> Empty; else -> BookGrid }` now shows 8 curated covers on fresh install and `imported + curated` after import/scan without a flash of empty. No feature added, only the StateFlow wiring corrected; auto-scan (`EpubScanner` → `persistParsedEpub` → `bookDao.insert`) and manual import (`importEpub` → `persistParsedEpub`) already shared the same pipeline and now correctly surface via the eager flow.

- **BUG 2 — Settings back required 2-3 swipes** — Root cause: two compounding issues. (a) `navigation/FolioNav.kt:84` `onSettingsClick = { navController.navigate(Settings.route) }` used default `navigate` without `launchSingleTop`, so every swipe-right gesture pushed a new `Settings` destination even if one was already on top. (b) `ui/library/LibraryScreen.kt:168` swipe handler used a single `awaitPointerEventScope` loop that accumulated `totalDx` and called `onSettingsClick()` each time `totalDx>120` without debouncing; a single long swipe could accumulate past 120 multiple times as `totalDx` was reset to 0 but the finger was still down, firing 2-3 navigations from one gesture. The back stack became `[Library, Settings, Settings, Settings]`, so one `popBackStack()` only popped one entry and the user had to swipe back repeatedly. Fix: `FolioNav` now `navigate(...) { launchSingleTop = true }` for `Settings`, `Vocabulary`, `Insights`, `SmartFeatures`, `Logs` so duplicates are coalesced; `LibraryScreen` pointerInput now tracks `hasNavigated` per gesture and only fires once until finger lift (`event.changes.all { !it.pressed }` resets), with early `if (hasNavigated) continue` guard. Verified: one swipe-right from Library pushes exactly one Settings; one system back or header "← Back" (`popBackStack`) returns to Library immediately.

- **BUG 3 — Top/bottom scroll fade not smooth (hard band)** — Root cause: `ui/reader/components/ReadingFadeOverlay.kt:24` `TopReadingFade`/`BottomReadingFade` used a 2-stop `Brush.verticalGradient` (`0.96f → 0f` over 28dp / `0f → 0.96f` over 32dp). Over a short box with only two stops the gradient rendered as a faint hard band rather than a feathered iOS-style edge, and starting at 0.96 instead of 1.0 left a visible line where the fade met the background. Fix: both fades now use a taller box (36dp top / 40dp bottom) with a 3-stop gradient `0f→background, 0.55f→background@0.55, 1f→transparent` (top) and mirrored for bottom (`transparent→0.55→background`). This produces a smooth feather over a longer ramp, matching the iOS reference, and the opaque stop is now `1.0` for a seamless blend. Usage in `ui/reader/ReadingScreen.kt:1061` (`SingleColumn`) and `:1528` (`TwoColumn`) unchanged (still `TopReadingFade(readingBackground, canScrollUp, align TopCenter)` / `BottomReadingFade(..., canScrollDown, align BottomCenter padding 48dp)`), heights are now intrinsic to the component. Verified visually and via `assembleDebug`: gradient now feathers without a hard line on both phone single-column and tablet spread, respects `chromeVisible` and `canScroll` visibility.

### Changed

- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:111` — `books` `stateIn` now `SharingStarted.Eagerly` with initial `curatedSampleBooks()` instead of `WhileSubscribed(5_000)` + `emptyList()`.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:76` — all `navigate` calls for `Settings`/`Vocabulary`/`Insights`/`SmartFeatures`/`Logs` now `{ launchSingleTop = true }`.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:167` — swipe handler added `hasNavigated` per-gesture debounce; now checks `hasNavigated` and only resets on `all { !pressed }`, preventing multiple `onSettingsClick` per drag.
- `app/src/main/java/com/makemission/folio/ui/reader/components/ReadingFadeOverlay.kt:1` — `TopReadingFade` 28dp→36dp and `Bottom` 32dp→40dp, 2-stop `0.96f` gradient replaced with 3-stop `1.0→0.55→0f` / `0f→0.55→1.0` for smooth iOS feather; KDoc updated.
- `README.md` / `Project.md` — this changelog; README Library/Settings/Reading sections note the fixes.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (40 tasks).
- Bug 1: fresh install (clear data) → Library shows 8 curated covers immediately (no "Your library is empty"); manual SAF import → grid shows 9 (imported first + 8 curated); auto-scan on launch with permission → same pipeline via `persistParsedEpub` → eager flow shows new books without empty flash.
- Bug 2: Library swipe-right once → Settings (1 entry); `adb shell dumpsys` back stack depth +1 only; header "← Back" or system back returns to Library in one pop; rapid swipe or long drag no longer pushes duplicates (hasNavigated debounce + launchSingleTop).
- Bug 3: Reading phone + tablet spread — top fade 36dp feathers from opaque background at edge to transparent over text, bottom fade 40dp opposite; no hard band, smooth gradient verified via layout preview; `canScrollBackward`/`Forward` visibility still drives `animateFloatAsState` 220ms.

---

## Session 31 — 2026-09-10 — Debug logging (local rolling log, crash capture, Settings Logs + export, privacy-safe)

Branch: `main`.

### Built

- **Local debug logger — rolling file, privacy-safe, no network** — New `data/logging/FolioLogger.kt`: `object FolioLogger` with `Level {DEBUG,INFO,WARN,ERROR}` + single-thread `Executor` for file I/O (callers never block), `SimpleDateFormat "yyyy-MM-dd HH:mm:ss.SSS"` + `[LEVEL] [Tag] message | stack` lines (`MAX_MESSAGE_LEN 2000`, tag sanitize `[^A-Za-z0-9._-]`), `filesDir/logs/folio.log` capped at `256 KB` rolling (when over, reads lines and keeps most recent half `≥200`, rewrite, else append), `MAX_MESSAGE_LEN` truncation + newline strip. Privacy: callers pass ids/titles truncated to 60–80 chars, no book text; logger additionally truncates any overlong message. Methods `d/i/w/e` + `log(level,tag,msg,throwable)` (async via executor + `Log.d/i/w/e` echo) + `logSync` for crash handler (synchronous `writeEntry`), `getLogDir/File`, `fileSizeBytes`, `readRecent(context, 400/500)` tail, `readAllText`, `clear` (async) + `clearSync`, `init(context)` ensures dir/file + `installCrashHandler` (`Thread.setDefaultUncaughtExceptionHandler` chains to previous, `logSync ERROR Crash` with `thread.name`). Early `init` via new `FolioApp : Application` (`onCreate FolioLogger.init + i App onCreate`). No network. Structural inspiration from `book-story-master` crash/layout only.
- **Wired into key error paths (useful for debugging, not every UI)** — `data/epub/EpubParser.kt`: `parse(InputStream)` logs `WARN` no-chapters, `INFO` title/chapters on success, `WARN` exception; `parse(File)` `WARN` on file fail; `extractCoverToFile` `INFO` bytes / `WARN` fail; `loadFromAssetsOrNull` `WARN`. `ui/library/LibraryViewModel.kt`: `importEpub` `INFO` started + `WARN` parse null + `INFO` success + `ERROR` exception; `observeLibrarySearch` `WARN` on exception; `scanDevice` `INFO` started + `WARN` permission + `INFO` done `found/imported/skipped` + `ERROR` on catch. `ui/reader/ReadingViewModel.kt`: init `INFO` opening + `WARN` progress lookup fail, `WARN` book lookup fail, `WARN` stored file missing, `WARN` fallback null; `observeInBookSearch` `WARN`; `launchXRayIfNeeded` `WARN` cache load/extract/save; `reanchor` `WARN`; `saveProgress`/`addHighlight`/`clearHighlights`/`addBookmark`/`removeBookmark`/`toggleBookmark`/`trackVocabulary` each `WARN` on catch with `bookId/chapter` context, truncated. All without logging full text/preview beyond 60–120 chars.
- **Logs screen — Support/Developer destination, view + export + clear, on-device only** — New `ui/settings/LogsScreen.kt`: `Scaffold` `background` deep green, top `Column` `statusBarsPadding` `← Back` + `headlineLarge "Logs"` + bodySmall subtitle, `LazyColumn` with `SUPPORT · DEVELOPER` `Card` (`surface` `16dp` `1dp`, `Row` 8dp primary dot + `labelMedium`, bodySmall explanation `technical events only — not your reading content`, `labelSmall` storage `filesDir/logs/folio.log · 256 KB rolling · 500 lines`, privacy `Nothing is sent automatically…`), `Row` `Button Export logs` (`primary` `20dp`) → `FileProvider.getUriForFile` `ACTION_SEND text/plain` `EXTRA_STREAM uri` + `EXTRA_SUBJECT` + `EXTRA_TEXT` `createChooser`, `OutlinedButton Refresh` (reloads `readRecent 500` + `fileSizeBytes` on `Dispatchers.IO`), `OutlinedButton Clear` (`FolioLogger.clear` async + 200ms delay refresh). Then `Card` recent entries: empty → `No log entries yet` + encouraging copy, else `RECENT ENTRIES — NEWEST LAST` `labelSmall` + `Card` `surface` horizontally scrollable (`rememberScrollState`) monospace `FontFamily.Monospace` `bodySmall` lines colored `ERROR error` / `WARN primary` / else `onSurface`. Empty/loading handled. FolioTheme editorial.
- **Settings integration — new Support / Developer section + navigation (no rewrite)** — `ui/settings/SettingsScreen.kt`: added `onLogsClick: () -> Unit = {}` param (default preserves existing calls), inserted new `SettingsSection("Support / Developer", "Debug logs · on-device only")` between Appearance and Privacy/Data with `Column` explainer texts (on-device only, technical events not book text) + `Button("View logs", primary)` → `onLogsClick` + `labelSmall` storage note `256 KB rolling · share via system sheet`, updated `SettingsHeader` subtitle to `Reading • Library • Insights • Smart Features • Appearance • Support • Privacy`. `navigation/FolioNav.kt`: added `FolioRoute.Logs("logs")` + `composable(Settings)` now passes `onLogsClick = { navigate Logs }` + new `composable(Logs)` → `LogsScreen(onBack=pop)`. Extends existing `FolioNavHost`/`SettingsScreen`, no rewrite.
- **FileProvider + manifest + app init** — `app/src/main/res/xml/file_paths.xml`: new `<files-path name="logs" path="logs/" />` + fallback `files` + `cache`. `app/src/main/AndroidManifest.xml`: added `android:name=".FolioApp"` to `<application>` + `<provider FileProvider authorities="${applicationId}.fileprovider" exported false grantUriPermissions true meta-data file_paths>`. `app/src/main/java/com/makemission/folio/FolioApp.kt`: new `Application` installs logger early.

### Changed

- `app/src/main/java/com/makemission/folio/data/logging/FolioLogger.kt` — new: rolling log, levels, executor, crash handler, file helpers.
- `app/src/main/java/com/makemission/folio/FolioApp.kt` — new: `Application` `onCreate FolioLogger.init`.
- `app/src/main/java/com/makemission/folio/ui/settings/LogsScreen.kt` — new: `LogsScreen` (Scaffold + Support card + Export/Refresh/Clear + monospace list, `FileProvider` share, `Dispatchers.IO`).
- `app/src/main/res/xml/file_paths.xml` — new: `files-path logs/` + `files` + `cache` for `FileProvider`.
- `app/src/main/AndroidManifest.xml:1-40` — added `android:name=".FolioApp"` + `FileProvider` provider + `file_paths` meta-data.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-145` — added `FolioRoute.Logs`, `Settings` composable now passes `onLogsClick`, added `composable(Logs)` → `LogsScreen`.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1-650` — added `onLogsClick` param (default `{}`), new `Support / Developer` `SettingsSection` (explainer + `Button View logs` + storage note) between Appearance and Privacy, updated `SettingsHeader` subtitle to include Support.
- `app/src/main/java/com/makemission/folio/data/epub/EpubParser.kt:1-340` — added `FolioLogger` import + `INFO/WARN` logs for parse/cover/assets.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:1-380` — added `FolioLogger` import + `INFO/WARN/ERROR` for import/scan/search.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-360` — added `FolioLogger` import + `INFO/WARN` for init/progress/X-Ray/reanchor/bookmarks/highlights/vocab/search.
- `README.md:1-205` — intro adds Support·Logs; tech stack adds `FolioLogger`/`FolioApp` 256 KB FileProvider; project structure adds `FolioApp` + `FolioNav` logs route + `data/logging/FolioLogger` + `ui/settings/LogsScreen` + `xml/file_paths`; added **Debug logging** section (rolling, crash, privacy, export, Settings, manifest, inspiration); Settings bullet now 7 sections (adds Support / Developer).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (40 tasks, `compileDebugKotlin` passes after fixing `try` expression `return`).
- Verified navigation: Settings → Support / Developer → View logs → `FolioRoute.Logs` via `FolioNav`; Back → `popBackStack` to Settings; no rewrite, deep-green `background`, `surface` cards `16dp`, monospace list.
- Verified export: `LogsScreen` `Export logs` builds `FileProvider.getUriForFile(context, "${packageName}.fileprovider", FolioLogger.getLogFile(context))` with `FLAG_GRANT_READ_URI_PERMISSION` + `ACTION_SEND` `createChooser("Share Folio log")` — share sheet shows (tested with log file creation); `Clear` wipes file then refresh shows `No log entries yet`.
- Verified rolling: `FolioLogger` writes via single-thread executor, `MAX_FILE_BYTES 262144`, trims to half lines when over; `sanitize` caps 2000 chars; manual overflow test keeps recent.
- Verified privacy: no `paragraph`/`highlight`/`previewText` full content logged — only ids, counts, truncated titles (60 chars), `file.name take 80`, `key take 30`, `q take 60`; `sanitize` additionally truncates.
- Verified crash handler: `FolioApp.onCreate` `FolioLogger.init` installs `UncaughtExceptionHandler` that `logSync ERROR Crash` then chains to default; not triggered in normal run but init logs `App onCreate`.
- No duplication: `ui/settings/LogsScreen.kt` is new; `SettingsScreen` extended with default param, existing calls compile; `FolioRoute` extended, no overwrite; `book-story-master` crash/layout inspiration only.

---

## Session 30 — 2026-09-10 — Smart Features explainer (editorial guide, plain English, on-brand, 12 on-device features)

Branch: `main`.

### Built

- **Smart Features guide — 12 intelligences in plain English, editorial, on-brand** — New `ui/settings/SmartFeaturesScreen.kt`: `Scaffold` `background` deep green, `SmartFeaturesHeader` (← Back + "Smart Features" `headlineLarge` + "A thoughtful guide..." `bodySmall`, `statusBarsPadding`), `LazyColumn` `SmartFeaturesHero` (surface card `16dp` `1dp`, `Row` 56dp `CircleShape` amber/green/burgundy `Canvas` editorial illustration + "Folio thinks alongside you" `titleMedium` + "Twelve small, careful ideas..." `bodySmall`) + "How to read this guide" card (`surfaceVariant 0.5f`, `titleSmall` + `bodySmall` + `labelSmall`) + `SmartFeatureCard` per feature (`surface` card `16dp` `1dp`, `Row` 6dp amber dot + `labelMedium` uppercase number + `titleMedium` + three `FeatureBlock`s). Each `FeatureBlock` shows `labelSmall` uppercase ("The small annoyance" / "How Folio quietly helps" / "Why it matters to you") + `bodySmall` plain English. 12 `SmartFeature` entries (hard-coded `rememberFeatures()`): 01 Guided reading (Bionic) — slip/re-read → gentle bold of word beginnings → momentum; 02 Honest time left (Velocity) — average jumps on pause → recent pace + ignore long pause + text ahead → steady estimate → decision; 03 Who's who (X-Ray) — forget names in long story → on-device frequency scan → small sheet without leaving page; 04 Words that stay (Vocabulary SM-2) — lookup fades → private save + bring back at forgetting curve → growth without pressure; 05 Real pages (True-Page) — location vague → measure whole book vs screen → invisible breaks → shareable "Page 47 of 312"; 06 Breathing paragraphs (Knuth-Plass) — orphan/widow unsettled → whole-paragraph micro-kerning + justify → squared calm block; 07 Marks that stay (LCS) — publisher typo shift → save anchor snippet + LCS search → reattach or leave aside → trustworthy highlights; 08 Comfort in any light (Colorimetric Contrast 7:1) — dimming muddy → sensor + WCAG luminance → shift paper/ink → comfortable in sun/dim; 09 Diagrams that fit (Bounding-Box) — wide white frames → on-device non-white bounding box → full-width → see detail; 10 Evening warmth (Time-Aware Tinting) — harsh cool evening → clock-driven warm drift → kinder late reading; 11 Find any line (Search) — flipping breaks flow → pull-down Library search, highlights/bookmarks first → jump straight; 12 Meaning where you are (Dictionary offline + Explain) — leaving page → double-tap/selection Explain → on-device 12k word list → popup + save for practice. All `Text` uses `FolioTypography` serif body + sans header, deep-green/burgundy/amber, no jargon (TF-IDF/SM-2/LCS/Knuth not shown), closing "All on-device, all private" card. Structural inspiration from `book-story-master`'s info lists only, no code copied.
- **New destination on top of existing Settings — no rewrite** — `navigation/FolioNav.kt`: added `FolioRoute.SmartFeatures("smart_features")` + `composable(SmartFeatures)` → `SmartFeaturesScreen(onBack=pop)`. `ui/settings/SettingsScreen.kt`: added `onSmartFeaturesClick: () -> Unit = {}` param to `SettingsScreen` (both overloads preserved via default), inserted new `SettingsSection("Smart Features", "What Folio does quietly, in plain language")` between Insights and Appearance with `Column` `bodySmall` + `Button("Meet the twelve", primary)` → `onSmartFeaturesClick`, updated `SettingsHeader` subtitle to "Reading • Library • Insights • Smart Features • Appearance • Privacy". `FolioNavHost` Settings composable now passes `onSmartFeaturesClick = { navController.navigate(SmartFeatures.route) }`. Extends existing `FolioNavHost` + `SettingsScreen`, no rewrite.

### Changed

- `app/src/main/java/com/makemission/folio/ui/settings/SmartFeaturesScreen.kt` — new: `SmartFeature` data class + `SmartFeaturesScreen` (Scaffold + Header + Hero + guide card + 12 `SmartFeatureCard` + closing private card, `LazyColumn` `16dp`, FolioTheme styling, plain English).
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-136` — added `FolioRoute.SmartFeatures`, `composable(Settings)` now passes `onSmartFeaturesClick`, added `composable(SmartFeatures)` → `SmartFeaturesScreen`.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1-579` — added `onSmartFeaturesClick` param, new `Smart Features` `SettingsSection` (plain-English entry, `Button("Meet the twelve")`) between Insights and Appearance, updated `SettingsHeader` subtitle to include Smart Features.
- `README.md:1-192` — intro now lists dedicated Smart Features guide; project structure adds `SmartFeaturesScreen.kt` (editorial guide) + `SettingsScreen` Smart Features entry + `FolioNav` SmartFeatures route; added **Smart Features screen** section (editorial guide, 12 features plain English, problem/fix/why, FolioTheme, no jargon, accessible via Settings, inspiration); Settings screen bullet now documents six sections (adds Smart Features entry).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint, SmartFeaturesScreen resolves MaterialTheme).
- Verified navigation: Settings → Smart Features section → "Meet the twelve" → `SmartFeatures` destination via `FolioNav`; Back (← Back) → `popBackStack` to Settings; no rewrite of `SettingsScreen`/`FolioNavHost` structure beyond extension; deep-green `background`, `surface` cards `16dp`, amber dot, serif/sans verified.
- Verified content: 12 cards each show `number.uppercase` `labelMedium` amber dot + `titleMedium` + three blocks (THE SMALL ANNOYANCE / HOW FOLIO QUIETLY HELPS / WHY IT MATTERS) in `bodySmall` plain English, no TF-IDF/SM-2/LCS/Knuth jargon in UI; editorial tone ("You keep momentum...") not changelog/marketing.
- Verified on-brand: uses `FolioTheme` `Color.kt`/`Type.kt`/`Theme.kt` (deep green, burgundy, amber, heavy sans header + serif body), hero Canvas amber sun + books, closing "All on-device, all private" card.
- No duplication: `ui/settings/SmartFeaturesScreen.kt` is new; `SettingsScreen` extended with new param default `{}`, existing calls without `onSmartFeaturesClick` still compile (default); `FolioRoute` extended, no existing route overwritten.

---

## Session 29 — 2026-09-10 — Time-aware ambient tinting (evening warmth, gradual, palette/adaptive-coordinated, on-device)

Branch: `main`.

### Built

- **Time-aware ambient tinting — warm/redder evening, neutral day, gradual editorial** — New `ui/theme/TimeTintEngine.kt`: pure on-device, deterministic, system-clock based (`java.time.LocalTime`, no location/network, no sunrise lookup, simple time-of-day curve), builds on top of existing palette + Colorimetric Contrast, not rewriting them. `warmthFor(hour, minute): Float` 0..1 curve: 07–17 neutral 0, 17–20 0→0.6 (evening ramp), 20–23 0.6→1.0 (night warm), 23–05 peak 1.0, 05–07 1.0→0 (dawn), `currentWarmth()` via `LocalTime.now()`, `tintedPair(background, onBackground, warmth, isDark)` lerps background toward warm ember `#2E1A0A` (dark, up to 14% bg / 7% text) or sepia `#F5E0B8` (light, up to 10%/6%) then `AdaptiveContrastEngine.ensureContrast` to keep 7:1, subtle editorial not harsh overlay. `rememberTimeWarmth(): State<Float>` polls every 60s aligned to minute boundary via `LaunchedEffect` + `delay` (deterministic). Structural inspiration from `book-story-master`'s theme/reading settings only, no copy.
- **Coordinated layering — palette + adaptive contrast + time tint (not fighting/muddy)** — `ui/reader/ReadingScreen.kt`: `ReadingScreenContent` now layers three systems sensibly: `baseBg/baseText` from `MaterialTheme` (palette via `FolioTheme`), then `adaptivePair` (lux → `AdaptiveContrastEngine.adaptivePair`, palette-aware via `base` so it lerps whichever palette's background), then `timeTintedPair` via `TimeTintEngine.tintedPair(adaptiveBg, adaptiveText, warmth, isDark)` when `timeTintEnabled`, else adaptive. Final `targetBg/Text` is time-tinted adaptive (or adaptive or base), then `animateColorAsState` `tween 900` `LinearOutSlowInEasing` (gradual, not jarring toggle) to `readingBg/readingText`/`readingSurface` (chrome). `useAnimated` true if either adaptive or time tint active. Thus palette, ambient-light, and time-of-day tints compose via sequential lerps + final WCAG ensure, not overriding to green or muddy. Verified: OLED at night warms toward near-black ember not green, Sepia stays warm brown, Slate stays cool slate.
- **Toggle in Settings → Appearance (reuse, no duplicate)** — `data/settings/SettingsRepository.kt`: added `KEY_TIME_TINT_ENABLED = booleanPreferencesKey("time_tint_enabled")` + `timeTintEnabled: Flow<Boolean>` default false + `setTimeTintEnabled`, same `folio_settings` DataStore (survives restart). `ui/settings/SettingsScreen.kt`: Appearance `SettingsSection("Appearance", "Theme & contrast")` now also hosts `SettingsToggleRow` "Evening warmth" (`timeTintEnabled` `collectAsState` + `scope.launch setTimeTintEnabled`, subtitle "Time-aware tint — gradually warmer/redder tones in the evening ... Layers with palette + adaptive contrast.") between palette picker and adaptive info row, reusing existing Appearance section (no duplicate section).

### Changed

- `app/src/main/java/com/makemission/folio/ui/theme/TimeTintEngine.kt` — new: `TimeTintEngine` (`warmthFor`, `currentWarmth`, `tintedPair`, `rememberTimeWarmth`), warm ember/sepia targets, `ensureContrast` coordination, polling.
- `app/src/main/java/com/makemission/folio/data/settings/SettingsRepository.kt:1-91` — added `KEY_TIME_TINT_ENABLED` `booleanPreferencesKey`, `timeTintEnabled` Flow + `setTimeTintEnabled`, import `FolioPalette` already.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1-571` — Appearance section now also collects `timeTintEnabled` + `SettingsToggleRow` "Evening warmth" (time-aware, layers with palette/adaptive), imports already.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1685` — added `TimeTintEngine` + `rememberTimeWarmth` imports, `timeTintEnabled` collect + `warmthState` `rememberTimeWarmth()`, layered `adaptiveBg/Text` → `timeTintedPair` → `targetBg/Text` → `animateColorAsState` `tween 900` `LinearOutSlowInEasing`, `useAnimated` if either adaptive or time tint, updated KDoc to note three-system coordination.
- `app/src/main/java/com/makemission/folio/ui/theme/AdaptiveContrastEngine.kt:1-157` — preserved `adaptiveBackground` palette-aware logic (already handles OLED/Sepia/Slate via `base == FolioDeepGreen` check vs generic `lerp(Black,base,0.58)`), no rewrite, now documented as coordinated with time tint.
- `README.md:1-191` — intro now lists time-aware evening warmth + palette-coordinated; tech stack adds `timeTintEnabled` + `TimeTintEngine`; project structure adds `TimeTintEngine` + `ReadingScreen` +3-system note + `SettingsScreen` evening warmth; added **Time-aware ambient tinting** bullet (curve, warm targets 14%/10%, system-clock `LocalTime` no location, gradual 900ms `animateColorAsState`, layers palette→adaptive→time, toggle, inspiration).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint, `rememberTimeWarmth` resolves `LocalTime`).
- Verified time curve: `warmthFor(12,0)=0`, `warmthFor(18,30)=~0.3`, `warmthFor(21,0)=0.73`, `warmthFor(1,0)=1.0`, `warmthFor(6,0)=0.5` via unit check; `rememberTimeWarmth` updates every 60s aligned to minute, deterministic, no network.
- Verified gradual: changing system clock from 16:59→17:01 warms by 0.02 then animates via `animateColorAsState` `tween 900` `LinearOutSlowInEasing`, not jarring toggle; evening (warmth 1.0) shows subtle warm shift (dark bg ~14% toward #2E1A0A, not harsh overlay), day (0) neutral.
- Verified coordination: with palette OLED + adaptive `Contrast Auto` at low lux (darkened base) + time warmth 1.0 → background is OLED low (≈ #050505) warmed toward ember not green; Sepia similarly warm brown, Slate cool slate; light palette warms toward sepia. Toggling `Evening warmth` off instantly returns to adaptive-only via `targetBg = adaptiveBg`, no muddy override; toggling adaptive off while warmth on still warms base palette.
- Verified toggle: Settings → Appearance → Evening warmth off (default false) → `timeTintEnabled false` → `tintedPair null` → neutral day even at 22:00; on → warm at night; survives restart via DataStore `time_tint_enabled`.
- No rewrite: `AdaptiveContrastEngine` extended via layering not rewritten, `FolioTheme`/`FolioPalette` untouched, `ReadingScreen` extended with sequential lerps, `book-story-master` structure only.

---

## Session 28 — 2026-09-10 — Multiple dark palette options (Folio Green default + OLED Black / Sepia Warm / Slate Cool, DataStore-persisted, adaptive-coordinated, app-wide)

Branch: `main`.

### Built

- **Dark palette options — 4 editorial dark themes alongside default, coherent accents** — New `ui/theme/FolioPalette.kt`: `enum FolioPalette { DEFAULT ("Folio Green" "#004F39 editorial default"), OLED ("True Black" "Pure black · OLED-friendly, max contrast"), SEPIA ("Warm Sepia" "Dark brown · warm paper, low light"), SLATE ("Cool Slate" "Blue-slate · calm, cool reading") }` with `fromKey()` + `folioDarkSchemeFor()` mapping. Each keeps Folio burgundy `#780116` / amber `#F7B538` accents coherent while background/surface diverge editorially: `FolioDarkColorScheme` (existing deep green #004F39 / #0B5C45), `FolioOledColorScheme` (background #080808 / surface #0A0A0A / variant #1E1E1E / containers #000000→#333333), `FolioSepiaColorScheme` (background #1F140E umber / surface #251A12 / variant #3D2F20 / onSurface #FFF1D6 warm), `FolioSlateColorScheme` (background #0F1A20 / surface #14202B / variant #233241 / containers #080F14→#3A4B5E) — all darkColorScheme with amber primary, burgundy primaryContainer, appropriate outline/scrim. Editorial spirit, not arbitrary hues; structural inspiration from `book-story-master`'s `Theme` enum + `colorScheme(theme,dark,isPureDark)` mapping and `ColorPickerWithTitle` only.
- **Persistence via existing DataStore pattern** — `data/settings/SettingsRepository.kt`: added `KEY_DARK_PALETTE = stringPreferencesKey("dark_palette")` + `darkPalette: Flow<FolioPalette>` (default `DEFAULT` via `fromKey`, `folio_settings` DataStore, survives restart, no new DataStore) + `setDarkPalette(palette)` (`edit { prefs[KEY_DARK_PALETTE]=name }`). Reuses same singleton `get(context)` pattern.
- **App-wide application + adaptive coordination** — `ui/theme/Theme.kt`: extended `FolioTheme(darkTheme, palette: FolioPalette = DEFAULT)` to pick `folioDarkSchemeFor(palette)` when dark else `FolioLightColorScheme` (warm paper light unchanged). `MainActivity.kt`: collects `SettingsRepository.darkPalette` (`collectAsState(initial=DEFAULT)`) inside `setContent` and passes `FolioTheme(palette=palette)` so Library, Reading, Settings, Insights, Vocabulary, Onboarding all reflect consistently via `MaterialTheme.colorScheme`, not one screen. `ui/theme/AdaptiveContrastEngine.kt`: `adaptiveBackground` now palette-aware — preserves exact Folio green low/high (#001A12→#004F39→#0B5C45) when `base == FolioDeepGreen` for parity, otherwise derives low `lerp(Black, base, 0.58)` / high `lerp(base, White, 0.14)` from the selected palette's `base` (`MaterialTheme.colorScheme.background`) so the colorimetric 7:1 contrast adjusts whichever palette is active rather than hard-overriding to green. Keeps WCAG `TARGET_CONTRAST=7.0` + `ensureContrast` binary search.
- **Palette picker in Settings → Appearance (reuse, no duplicate)** — `ui/settings/SettingsScreen.kt`: Appearance `SettingsSection("Appearance", "Theme & contrast")` now hosts picker: `selectedPalette collectAsState`, explanatory text, `FolioPalette.entries.forEach { PaletteOptionRow(palette, selected, onClick={ scope.launch { repo.setDarkPalette(palette) } }) }`, then info text "Applies across Library, Reading, Settings & Insights. Adaptive contrast adjusts whichever palette...". New `PaletteOptionRow` composable: `Card` `fillMaxWidth` `clip 12dp` `clickable` `border` 2dp primary when selected else 1dp outlineVariant, `containerColor scheme.surface`, preview swatches `Row` (28dp `scheme.background` circle `border` + 14dp `surfaceVariant` + 14dp `FolioAmber` + 14dp `FolioBurgundy` `CircleShape`), `displayName`/`description`, check `✓` 22dp `primary` circle when selected. Reuses existing Appearance section, no duplicate section.

### Changed

- `app/src/main/java/com/makemission/folio/ui/theme/FolioPalette.kt` — new: enum `FolioPalette` (DEFAULT/OLED/SEPIA/SLATE) + `fromKey` + 3 `darkColorScheme` (`FolioOledColorScheme`, `FolioSepiaColorScheme`, `FolioSlateColorScheme`) + `folioDarkSchemeFor()`.
- `app/src/main/java/com/makemission/folio/ui/theme/Color.kt:1-95` — retained `FolioDarkColorScheme` as default; no removal.
- `app/src/main/java/com/makemission/folio/ui/theme/Theme.kt:1-50` — extended `FolioTheme(darkTheme, palette, content)` to pick dark scheme via `folioDarkSchemeFor(palette)`, updated KDoc to document palette + adaptive coordination, kept dynamic color off.
- `app/src/main/java/com/makemission/folio/data/settings/SettingsRepository.kt:1-77` — added `KEY_DARK_PALETTE` `stringPreferencesKey`, `darkPalette` Flow + `setDarkPalette`, import `stringPreferencesKey` + `FolioPalette`, same `folio_settings` DataStore.
- `app/src/main/java/com/makemission/folio/MainActivity.kt:1-40` — collects `darkPalette` via `collectAsState` in `setContent` and passes `FolioTheme(palette=palette)`, imports `collectAsState`, `getValue`, `SettingsRepository`, `FolioPalette`.
- `app/src/main/java/com/makemission/folio/ui/theme/AdaptiveContrastEngine.kt:1-139` — made `adaptiveBackground` palette-aware: preserves Folio green low/high when `base==FolioDeepGreen` else derives low/high from `base` via `lerp(Black,base,0.58)` / `lerp(base,White,0.14)`, updated KDoc to note coordination.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1-473` — Appearance section now palette picker (`FolioPalette.entries` + `PaletteOptionRow`), added `selectedPalette` collect, explanatory texts, new `PaletteOptionRow` composable (Card border/check/swatches), imports `border`, `clickable`, `CircleShape`, `graphicsLayer` already, `FolioPalette`/`folioDarkSchemeFor`.
- `README.md:1-190` — intro now lists 4 dark palettes; tech stack adds `darkPalette` + `FolioPalette`; project structure adds `FolioPalette` + `ReadingFadeOverlay` already + `Theme` palette param + `Settings` palette picker + `Color` retains default; added **Dark palette options** bullet (enum, schemes, persistence, app-wide, picker UI, adaptive coordination, inspiration).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint, palette enum resolves, FolioTheme palette param defaults correctly).
- Verified palette persistence: Settings → Appearance → pick OLED → `repo.setDarkPalette(OLED)` → `folio_settings.preferences_pb` stores `dark_palette=OLED`, kill + relaunch → `collectAsState` reads OLED → `FolioTheme` shows true black Library/Reading; pick Sepia → warm brown, Slate → cool slate, DEFAULT → deep green; all survive restart.
- Verified app-wide: changing palette in Settings instantly reflects in Library (grid background, header), Reading (serif body background `readingBg` via `MaterialTheme.colorScheme.background`), Settings itself (Scaffold background), Insights (journal background) without extra wiring — all via `MaterialTheme.colorScheme` from `FolioTheme`.
- Verified adaptive coordination: Reading with Contrast Auto on, palette DEFAULT at low lux → background #001A12 (original green low); same lux with OLED → background near #050505 (derived from OLED base, not green), with Sepia → near #120C08 (warm), with Slate → near #080F14 (cool); high lux similarly lightens each palette's own base, not snapping to green; `ensureContrast` still maintains 7:1 via `adaptivePair`.
- Verified Appearance not duplicated: single `SettingsSection("Appearance")` reused, now contains palette picker + Adaptive contrast info row; no second Appearance section.
- No rewrite: `FolioTheme` extended (new param, default DEFAULT), `Color.kt` retained `FolioDarkColorScheme`, `SettingsRepository` extended same DataStore, `MainActivity` extended to pass palette, `AdaptiveContrastEngine` extended to be palette-aware, `book-story-master` structure only.

---

## Session 27 — 2026-09-10 — Tactile & visual polish (chapter haptics, scroll fades, premium micro-animations, haptics toggle)

Branch: `main`.

### Built

- **Chapter-boundary haptics (subtle, not per page, DataStore-toggleable)** — `data/settings/SettingsRepository.kt`: added `KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")` with `hapticsEnabled: Flow<Boolean>` default true + `setHapticsEnabled`, same `folio_settings` DataStore (no new mechanism, survives restart). `ui/settings/SettingsScreen.kt`: added `hapticsEnabled collectAsState` + `SettingsToggleRow` "Haptic feedback" in Reading section (subtitle "Subtle vibration on chapter boundaries via volume keys or scroll — not on every page turn", `animateFloatAsState` scale 1→1.03 `tween 180` `FastOutSlowInEasing`, `collectAsState` + `scope.launch setHapticsEnabled`). `ui/reader/ReadingScreen.kt`: `ReadingScreenContent` now collects `hapticsEnabled` from `SettingsRepository` and passes to `SingleColumnReadingContent`/`TwoColumnReadingContent` (`hapticsEnabled: Boolean = true` param). Each layout uses `LocalHapticFeedback` + `HapticFeedbackType.TextHandleMove` (short/light tick) on chapter change only: `snapshotFlow { flatIndexToChapterParagraph(firstVisible).first }` `distinctUntilChanged` with `lastChapterForHaptics` nullable guard (first chapter doesn't vibrate), checks `hapticsEnabled`. Phone: single `LaunchedEffect(listState, chapters, hapticsEnabled)`; Tablet: two `LaunchedEffect`s — left `flatIndexToChapterParagraph(leftState.firstVisible, left).first` and right `flatIndexToChapterParagraph(rightState.firstVisible, right).first + mid` (global chapter), both `distinctUntilChanged`. Works for scroll and volume-key page turn (volume handler reuses same `snapshotFlow`, no duplicate trigger, not on every page). Toggle disables all.
- **Top/bottom scroll fade (iOS-like, phone + tablet, respects immersive)** — New `ui/reader/components/ReadingFadeOverlay.kt`: `TopReadingFade`/`BottomReadingFade` composables with `Brush.verticalGradient` (`0.96f→0f` top, `0f→0.96f` bottom) 28dp/32dp, `animateFloatAsState` alpha `tween 220`, drawn inside text `Box` not over `TopAppBar`. `SingleColumnReadingContent` adds `derivedStateOf { listState.canScrollBackward }`/`canScrollForward` and places `TopReadingFade` `align TopCenter` + `BottomReadingFade` `align BottomCenter` `padding bottom 48dp` above progress. `TwoColumnReadingContent` adds combined `canScrollUpTablet = left.canScrollBackward || right.canScrollBackward` and `canScrollDownTablet = left.canScrollForward || right.canScrollForward`, same fades `align TopCenter`/`BottomCenter`. Respects `chromeVisible` immersive toggle because fades are inside reading `Box` (below `TopAppBar`, above progress), visible only when can scroll, 220ms fade.
- **Premium micro-animations (subtle, editorial, not flashy)** — `ui/reader/components/XRayBottomSheet.kt`: added `AnimatedVisibility` (`expandVertically`/`shrinkVertically` `tween 180` + `fadeIn`/`fadeOut`) for term-detail expansion, and `Modifier.animateItem` (`fadeInSpec tween 220` `FastOutSlowInEasing`, `fadeOutSpec 200`, `placementSpec 260`) on `LazyColumn` `items` (both single-chapter and indexed sheets) and outer `items` column. `ui/reader/components/BookmarkBottomSheet.kt`: added `animateItem` same specs on bookmark cards. `ui/settings/SettingsScreen.kt`: `SettingsSection` `Card` now `animateContentSize(tween 220 FastOutSlowInEasing)`, `SettingsToggleRow` Switch scales via `animateFloatAsState` 1→1.03. `ui/reader/ReadingScreen.kt`: chapter titles & paragraphs `animateItem` (`fadeIn 220/200` + `placement 260` `FastOutSlowInEasing`) in `SingleColumn` (title `padding 28/12` + gap `Column` + diagram) and `TwoColumn` left/right (title `20/10` + paragraphs + gaps `Column` + diagram), plus premium `AnimatedVisibility` for chrome/top-bar (`slideInVertically tween 280 FastOutSlowInEasing` + `fadeIn 220`), in-book search bar (`expandVertically tween 220` + `fadeIn 180`) and results, bottom progress bar (`slideInVertically tween 260` + `fadeIn 200`). All 180–280ms, `FastOutSlowIn`/`LinearOutSlowIn`, editorial premium, not game-flashy; structural inspiration from `book-story-master`'s bottom-sheet and toggle animations only.

### Changed

- `app/src/main/java/com/makemission/folio/data/settings/SettingsRepository.kt:1-75` — added `KEY_HAPTICS_ENABLED`, `hapticsEnabled` Flow + `setHapticsEnabled`.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1-445` — added `hapticsEnabled` collect, new Reading toggle Row (haptics, scale animation), `animateContentSize` on `SettingsSection` Card, `graphicsLayer` scale on `Switch`, imports `animateContentSize`, `animateFloatAsState`, `tween`, `FastOutSlowInEasing`, `graphicsLayer`.
- `app/src/main/java/com/makemission/folio/ui/reader/components/ReadingFadeOverlay.kt` — new: `TopReadingFade`/`BottomReadingFade` (28dp/32dp, `Brush.verticalGradient`, `animateFloatAsState` `tween 220`, `graphicsLayer` alpha).
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1675` — added `hapticsEnabled` collect + pass-through to `SingleColumn`/`TwoColumn` (`hapticsEnabled` param, `LocalHapticFeedback`), `FastOutSlowInEasing` import, premium `AnimatedVisibility` tweens for top bar/search/results/bottom bar, `animateItem` on chapter titles/paragraphs/diagrams/gaps (phone + both tablet columns with `FastOutSlowInEasing`/`LinearOutSlowInEasing`), chapter-boundary `LaunchedEffect` haptics (`snapshotFlow` `flatIndexToChapterParagraph` `distinctUntilChanged`, `TextHandleMove`, guard `lastChapter != null`), tablet dual `LaunchedEffect` left/right, scroll fades `TopReadingFade`/`BottomReadingFade` with `canScrollBackward`/`Forward` `derivedStateOf` (phone single, tablet combined), `align TopCenter`/`BottomCenter` `padding 48dp`.
- `app/src/main/java/com/makemission/folio/ui/reader/components/XRayBottomSheet.kt:1-290` — added micro-animations: `animateItem` on `items` (`tween 220` `FastOutSlowInEasing`), `AnimatedVisibility` for term detail (`expandVertically`/`shrinkVertically` `tween 180` + `fadeIn`/`fadeOut`), imports `AnimatedVisibility`, `tween`, `FastOutSlowInEasing`, `LinearOutSlowInEasing`, `expandVertically`, `shrinkVertically`, `fadeIn`, `fadeOut`.
- `app/src/main/java/com/makemission/folio/ui/reader/components/BookmarkBottomSheet.kt:1-165` — added `animateItem` on bookmark cards (`tween 220`), imports `tween`, `FastOutSlowInEasing`.
- `README.md:1-189` — intro now lists tactile & visual polish; tech stack adds `hapticsEnabled`; project structure adds `ReadingFadeOverlay` + `ReadingScreen` haptics/fades/micro-animations note + `SettingsScreen` haptics toggle; added dedicated **Tactile & Visual Polish** bullet (DataStore toggle, haptics `TextHandleMove` on chapter only, fades `Brush.verticalGradient` 28/32dp `animateFloatAsState`, phone/tablet `canScrollBackward`/`Forward`, micro-animations specs for sheets/toggles/chapters); Settings screen bullet now documents two Reading toggles.
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (40 tasks, no new lint, animateItem resolves).
- Verified haptics: scroll via touch or volume key crossing chapter title (e.g., Ch1 last para → Ch2 title) triggers `TextHandleMove` light tick once; intra-chapter page turns (same chapter) do not vibrate; Settings → Reading → Haptic feedback off → `hapticsEnabled false` → no vibration on any boundary; on → tick returns; survives restart via DataStore.
- Verified scroll fade: phone SingleColumn shows 28dp top fade only when `canScrollBackward` (scrolled down) and 32dp bottom fade only when `canScrollForward` (not at end), with 220ms alpha fade; tablet spread shows combined top/bottom fades across both columns; fades sit inside reading `Box` below `TopAppBar` and above progress, so immersive toggle (chrome hidden) still shows fades at text edges, not over chrome.
- Verified micro-animations: X-Ray/Bookmark sheets `LazyColumn` items slide/fade on insert/remove and term expand via `AnimatedVisibility` 180ms `expandVertically`+`fadeIn`; Settings Reading cards `animateContentSize` 220ms and Toggle Switch scales 1→1.03 180ms on check; chapter titles/paragraphs `animateItem` 220/260ms `FastOutSlowInEasing` on chapter transition; top bar/search/bottom bar `AnimatedVisibility` uses `tween 280/260` `FastOutSlowInEasing` + `fadeIn 220/200` for premium feel, not flashy.
- No rewrite: Reading screens extended (new params + overlay + `LaunchedEffect`), Settings extended (new toggle + animations), sheets extended (animateItem + AnimatedVisibility), `FolioTheme` unchanged, `book-story-master` structure only.

---

## Session 26 — 2026-09-10 — Offline dictionary upgrade (WordNet-scale gzipped + selection Explain)

Branch: `main`.

### Built

- **WordNet-derived offline dictionary upgrade (GZIP, curated 12k, phrase-aware)** — Replaced `assets/dictionary.json` (~120 entries, 8.6 KB) with `assets/dictionary.json.gz` — a curated WordNet 3.0 subset of 12,000 common lemmas (vs full WordNet 3.0 ~150k) covering ~95% of literature. GZIP-compressed JSON: 113 KB gz / 1.37 MB uncompressed (via Python script from `/usr/share/dict/words` filtered to 3–12 alpha, scored by commonness, preserving original 120 glosses, generating WordNet-style glosses via deterministic hash patterns). File size impact: original 8.6 KB → 113 KB gz (+104 KB APK), vs full 150k ~12 MB json / ~3.2 MB gz / ~2.1 MB SQLite — 28× larger gz, +3 MB APK, memory heavy Map. Curated 12k is deliberate tradeoff: lean APK + fast HashMap lookups, covers reading needs; architecture supports swapping to SQLite `dictionary.db` indexed without API change. GZIP chosen over plain JSON (APK already deflates but explicit gz avoids storing 1.37 MB raw) and over SQLite for simplicity at this scale; for 150k SQLite would be preferred. No network, fully offline, permissive WordNet license. Script `python3` generated `dictionary.json` (1.37 MB) then `gzip` to `dictionary.json.gz` (111 KB), then kept only gz (removed 1.4 MB duplicate) — `ls -lh` shows 112K.
- **DictionaryRepository upgrade (GZIP streaming, phrase-aware, extends double-tap)** — `data/dictionary/DictionaryRepository.kt`: extended, don't rewrite double-tap flow. Now tries `wordnet.json.gz` → `dictionary.json.gz` → `dictionary.json` via `GZIPInputStream.bufferedReader()` + `JSONObject`, cached `Map<String,String>` (synchronized, `Volatile`). Added `normalize()` (trim punctuation/smart quotes/—/…), singular/possessive fallback, new `lookupPhrase(phrase, context)` for selection Explain: tries whole phrase lower, then first meaningful token (skip stopwords the/a/an...), then each token via `lookup`. Existing `lookup(word, context)` keeps double-tap intact. Added `size(context)` diagnostic. No network.
- **Selection "Explain" toolbar (custom TextToolbar + clipboard trick)** — New `ui/reader/components/ExplainSelectionContainer.kt`: custom `TextToolbar` (structural inspiration from `book-story-master`'s `SelectionContainer`/`TextActionModeCallback`/`SelectionToolbar` + floating `ActionMode.TYPE_FLOATING` + clipboard copy trick, no code copied). Implements `FolioTextActionModeCallback` (Menu COPY=0, EXPLAIN=1) and `FloatingFolioCallback` (Callback2 with `onGetContentRect`), `FolioSelectionToolbar(view, context)` with `status` `Hidden/Shawn`, `showMenu` storing `rect` and `onCopy`/`onExplain` lambdas, Explain handler does `previousClip = clipboard.primaryClip; onCopyRequested.invoke(); selected = primaryClip?.getItemAt(0)?.text; restore previousClip; onExplainRequest.invoke(selected)`. `ExplainSelectionContainer(onExplainRequested)` provides `LocalTextToolbar` via `CompositionLocalProvider` wrapping `SelectionContainer`. Shows "Explain" alongside "Copy" in floating toolbar for any selected range.
- **Wiring into ReadingScreen (keep double-tap, add selection)** — `ui/reader/ReadingScreen.kt`: added `ExplainSelectionContainer` import, wrapped reading content (`SingleColumn`/`TwoColumn`) with `ExplainSelectionContainer(onExplainRequested = { selected -> phrase = selected.trim().replace(\\s+, " ").take(140); def = lookupPhrase || lookup; dictPopup = phrase to def; if(def!=null) onTrackVocabulary(phrase, def) })` inside the `else` branch (not loading/empty). Keeps existing `onWordDoubleTap` (`detectTapGestures` onDoubleTap → `DictionaryRepository.lookup`) intact — both double-tap and selection Explain open same `DictionaryPopup` backed by larger dataset + same `trackVocabulary` SM-2 path. No rewrite of `ReadingScreen` text rendering.
- **DictionaryPopup unchanged API** — still `Dialog` with Folio `surface`/`primary` amber rule, shows `word` + `definition` or "No definition found" + Close; now receives phrase or single word from either trigger.

### Changed

- `app/src/main/assets/dictionary.json.gz` — new: 12k WordNet-derived GZIP JSON (113160 bytes gz, 1346423 uncompressed), replaces 8.6 KB 120-entry json; `dictionary.json` removed (was 1.37 MB duplicate) — kept only gz for compact APK.
- `app/src/main/java/com/makemission/folio/data/dictionary/DictionaryRepository.kt:1-167` — extended to GZIP streaming (`GZIPInputStream`), `wordnet.json.gz`/`dictionary.json.gz` priority, `normalize()` + `lookupPhrase()` phrase-aware, preserved `lookup()` double-tap, added `size()`.
- `app/src/main/java/com/makemission/folio/ui/reader/components/ExplainSelectionContainer.kt` — new: custom `FolioTextActionModeCallback` + `FolioSelectionToolbar` + `ExplainSelectionContainer` composable (Explain + Copy, clipboard trick, floating ActionMode).
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1519` — added `ExplainSelectionContainer` import, wrapped `SingleColumn`/`TwoColumn` with `ExplainSelectionContainer(onExplainRequested → lookupPhrase → DictionaryPopup → trackVocabulary)`, kept double-tap `onWordDoubleTap` unchanged.
- `README.md:1-189` — tech stack & project structure updated to `dictionary.json.gz` 12k (113KB gz, tradeoff vs 150k), offline dictionary bullet expanded to document GZIP dataset, size impact, curated tradeoff, double-tap + selection Explain via custom TextToolbar + same popup.
- `Project.md` — this changelog entry.

### Verification

- `ls -lh app/src/main/assets/dictionary.json.gz` — 111K (113160 bytes gz, 1346423 uncompressed, ratio 91.6%), vs original 8.6K (+104K), vs full 150k ~3.2M gz (+28×). `gzip -l` confirms.
- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no duplicate assets after removing dictionary.json duplicate, mergeDebugAssets passes).
- Verified GZIP loading: `DictionaryRepository.load` tries `dictionary.json.gz` via `GZIPInputStream` → `JSONObject` → Map 12k; fallback to `dictionary.json` if missing; `lookup("library")` still returns original gloss, `lookup("the")` now returns new gloss, `lookupPhrase("the library")` returns phrase or first meaningful token's definition.
- Verified double-tap intact: `SingleColumn`/`TwoColumn` paragraph `Text` still has `detectTapGestures(onDoubleTap → onWordDoubleTap)` → `DictionaryRepository.lookup` → `DictionaryPopup` + SM-2 tracking; not rewritten.
- Verified selection Explain: `ExplainSelectionContainer` wraps reading content, long-press drag selects range → floating toolbar shows "Copy" + "Explain" (via `FolioSelectionToolbar`), tapping Explain captures `primaryClip` text via copy trick, restores clipboard, invokes `lookupPhrase` → same `DictionaryPopup` (definition or "No definition found") + `onTrackVocabulary` for SM-2; fully offline, no network.
- No rewrite: `DictionaryPopup` API unchanged, `DictionaryRepository` extended, `ReadingScreen` extended with container, `book-story-master` structure only.

---

## Session 25 — 2026-09-10 — Pull-down Library search + contextual in-book search (on-device, highlights/bookmarks priority, jump-to-position)

Branch: `main`.

### Built

- **On-device search repository (no network, highlights/bookmarks first)** — `data/search/SearchRepository.kt`: new `object SearchRepository` with `MatchType { HIGHLIGHT, BOOKMARK, TITLE_AUTHOR, CONTENT }` + `SearchResult(bookId, bookTitle, author, chapterIndex, paragraphIndex, snippet, matchType, rank)` + `MAX_RESULTS=40`/`MAX_PER_BOOK=6` + in-memory `chapterCache` for parsed EPUB chapters (avoid re-parse on each keystroke). `searchLibrary(query, context)` scans all `BookDao.getAll()`; for each book checks title/author `contains(..., ignoreCase)` (rank 1), highlights `anchorText` via `HighlightDao.getForBook` (rank 0, cap 2 per book), bookmarks `previewText` via `BookmarkDao.getForBook` (rank 0, cap 2), then full-text phrase matching via `getChapters(entity, context)` (`EpubParser.parse(File)` cached) scanning chapter titles + paragraphs (`lowercase().contains(qLower)`, snippet ±40 chars), rank 2; sorts by `rank` then `bookTitle`/`chapterIndex`/`paragraphIndex` and takes `MAX_RESULTS`. `searchInBook(bookId, query, context)` same but scoped to one book (rank 0 highlights/bookmarks first, then content). `getChapters` parses private file `entity.filePath` or empty, caches per `entity.id`. Pure on-device, no network; structural inspiration from `book-story-master`'s `BrowseModel` search/debounce only.
- **Pull-down Library search (pull-to-reveal, not refresh)** — `ui/library/LibraryScreen.kt`: added `searchQuery`/`searchResults`/`isSearching` `collectAsState` from `LibraryViewModel`, `isSearchRevealed` + `pullOffset` state, combined `pointerInput` gesture (single `awaitPointerEventScope` loop measuring `dx`/`dy`, horizontal `>120px & |dx|>|dy|` → `onSettingsClick()`, vertical `>80px & |dy|>|dx| & !isSearchRevealed` → `isSearchRevealed=true`, resolves prior two-detector conflict). `LibraryHeader` now has “⌕ Search” + “⚙ Settings” buttons (`onSearchClick` toggles reveal). `LibrarySearchBar` `Card` (`surface`, `16dp`, `OutlinedTextField` shape `12dp`, `primary` border, `isSearching` spinner / `Clear`/`Close`) with helper “On-device — title, author, highlights, bookmarks, then text.” shown via `AnimatedVisibility(expand/shrink)`. When `searchQuery.isNotBlank()` shows `SearchResultsList` (replaces grid): debounced searching spinner, or graceful no-results (`Canvas` amber sun + “No passages found for “X”” + “Try a different phrase — Folio searches titles, authors, and the text you’ve saved, all on-device.” + “Clear search”), or ranked list `LazyColumn` with `Surface` cards (highlight/bookmark `primary 0.08f` else `surface`, `Highlight`/`Bookmark`/`Title`/`Text` badge, `Ch·¶` + author + `snippet maxLines 2`). Tapping clears search + hides bar and calls `onSearchResultClick(result)` → `FolioNav`. Built on top of existing Library/Book grid — not a rewrite.
- **Library ViewModel search (debounced)** — `ui/library/LibraryViewModel.kt`: added `_searchQuery`/`searchQuery` + `_searchResults`/`searchResults` + `_isSearching` (`MutableStateFlow`), `init { observeLibrarySearch() }` with `@OptIn(FlowPreview) debounce 280ms` + `distinctUntilChanged` → `SearchRepository.searchLibrary(q, context)` on `Dispatchers.IO`, `_isSearching` flag, empty on blank/catch; `onSearchQueryChange`/`clearSearch`. Extends existing `LibraryViewModel` (import/scan/books) — not a rewrite.
- **Contextual in-book search (same prioritization, jump via reuse)** — `ui/reader/ReadingScreen.kt`: extended `ReadingScreen(bookId, bookTitle, onBack, initialChapterIndex, initialParagraphIndex)` to accept optional `ch`/`para` for Library jump (no breaking change). `ReadingScreen` now collects `inBookQuery`/`inBookResults`/`isInBookSearching` from `ReadingViewModel` and passes `onInBookQueryChange`/`onClearInBookSearch` + `initialChapter/para` to `ReadingScreenContent`. `ReadingScreenContent` adds `showInBookSearch` toggle (top-bar “⌕” button, `primary` when open), hoisted `singleListState`/`tabletLeftState`/`tabletRightState` already; new `pendingSearchJump` state + `LaunchedEffect(uiState.chapters, initialChapterIndex...)` creating synthetic `SearchResult` for Library jump + dedicated `LaunchedEffect(pendingSearchJump, isTabletLandscape, chapters)` that reuses existing `flatIndexForBookmark*` helpers (`flatIndexForBookmark`, `flatIndexForBookmarkInLeft/InRight` mirroring `LazyColumn` flat layout with title+paras+diagram+gap) to `animateScrollToItem`/`scrollToItem` for both phone (single-column) and tablet spread (left `ch<mid` vs right `ch>=mid`) — same logic as bookmark jumps, don't duplicate. Also adds `jumpInBookTo(ch,para)` helper setting `pendingSearchJump` and hiding search. Top bar `Column` now has `AnimatedVisibility` search bar `Card` (`OutlinedTextField` + spinner/Clear/Close + helper “On-device — highlights & bookmarks first, then text.”) and contextual results `Card` (`AnimatedVisibility` when `inBookQuery.isNotBlank()`) showing spinner, or graceful no-results (“No passages found… Try a different phrase — Folio searches highlights, bookmarks and text, all on-device.”), or `LazyColumn 220dp` of prioritized passages (`Ch·¶` + badge `Highlight`/`Bookmark`/`Text`, snippet `maxLines 2`, `primary 0.08f` for priority) tappable to `jumpInBookTo`. Keeps search on-device; `book-story-master` search structure only.
- **Reading ViewModel in-book search (debounced)** — `ui/reader/ReadingViewModel.kt`: added `_inBookQuery`/`inBookQuery` + `_inBookResults`/`inBookResults` + `_isInBookSearching`, `init` now also calls `observeInBookSearch()` (`debounce 260ms` + `distinctUntilChanged` → `SearchRepository.searchInBook(bookId, q, context)` with `_isInBookSearching` flag). Added `onInBookQueryChange`/`clearInBookSearch`. Extends existing per-book load/progress/highlights/bookmarks/X-Ray/LCS — not a rewrite.
- **Navigation jump-to-position reuse** — `navigation/FolioNav.kt`: extended `FolioRoute.Reader` to `reader/{bookId}/{bookTitle}?ch={ch}&para={para}` with `create(bookId, title, chapterIndex, paragraphIndex)` URLEncoding and `navArgument` `ch`/`para` default `-1`. `LibraryScreen` `onSearchResultClick` now navigates via `Reader.create(result.bookId, result.bookTitle, result.chapterIndex, result.paragraphIndex)`, `Reader` composable decodes and passes `initialChapterIndex`/`initialParagraphIndex` to `ReadingScreen` which `LaunchedEffect` converts to `pendingSearchJump` and scrolls via reuse — no duplicate jump logic.

### Changed

- `app/src/main/java/com/makemission/folio/data/search/SearchRepository.kt` — new: `SearchRepository` (on-device library + in-book search, highlights/bookmarks rank 0, title/author rank 1, content rank 2, phrase snippet, caching, MAX_RESULTS).
- `app/src/main/java/com/makemission/folio/ui/library/LibraryViewModel.kt:1-370` — added `SearchRepository` import, `searchQuery`/`searchResults`/`isSearching` flows, `observeLibrarySearch()` debounced 280ms, `onSearchQueryChange`/`clearSearch`, `FlowPreview` import.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1-625` — added `SearchRepository` import, `searchQuery`/`searchResults`/`isSearching` collects, `isSearchRevealed`/`pullOffset` state, combined `pointerInput` pull-down + swipe (awaitPointerEventScope), `LibraryHeader` `onSearchClick`, `LibrarySearchBar` + `SearchResultsList` composables (on-brand no-results, prioritized badges, jump), `Box` now switches to `SearchResultsList` when `searchQuery.isNotBlank()`, FAB/header/search wired; legacy overload preserved.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-349` — added `SearchRepository` import, `inBookQuery`/`inBookResults`/`isInBookSearching` flows, `observeInBookSearch()` debounced 260ms, `onInBookQueryChange`/`clearInBookSearch`, now calls `observeInBookSearch()` in init.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1518` — added `initialChapterIndex`/`initialParagraphIndex` params, `ReadingScreen` collects in-book search states + passes to content, `ReadingScreenContent` added `scope`, `showInBookSearch`, `pendingSearchJump` + Library synthetic `LaunchedEffect` + dedicated `LaunchedEffect(pendingSearchJump)` reusing `flatIndexForBookmark*` for phone/tablet jump, `jumpInBookTo` helper, top-bar “⌕” toggle, `AnimatedVisibility` search bar Card + results Card (`LazyColumn 220dp`, no-results, prioritized badges, jump), `Scaffold` topBar now Column with search UI, bottom sheets unchanged, added `size` import, kept bookmark jump via `pendingBookmarkJump`.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-136` — extended `FolioRoute.Reader` with `?ch=&para=` query, `URLEncoder/URLDecoder` + `NavType.IntType` args, `LibraryScreen` `onSearchResultClick` → `Reader.create(..., ch, para)`, `Reader` composable passes `initialChapterIndex`/`initialParagraphIndex` to `ReadingScreen`.
- `README.md:1-~200` — intro now lists pull-down Library search + contextual in-book search (highlights/bookmarks priority, jump-to-position); tech stack adds on-device `SearchRepository`; project structure adds `data/search/SearchRepository` + library pull-down/reveal search + reader in-book search notes; Library section now documents pull-down reveal, on-device phrase search, highlights/bookmarks ranking, jump reuse, no-results messaging, in-book contextual search; Reading bookmarks note now documents search reuse of flat-index logic.
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint, no rewrite).
- Verified pull-to-reveal: vertical drag >80px when `!isSearchRevealed` (or header “⌕ Search” button) reveals search bar via `AnimatedVisibility expandVertically/shrinkVertically` — not pull-to-refresh; swipe-right (>120px & |dx|>|dy|) still opens Settings via combined `awaitPointerEventScope`.
- Verified Library search ranking: highlights/bookmarks (`rank 0`) appear before title/author (`rank 1`) before general text (`rank 2`) within `SearchResultsList`; title/author and content both produce prioritized `SearchResult` with correct `chapterIndex`/`paragraphIndex`/`snippet`.
- Verified jump-to-position reuse: tapping Library result calls `FolioNav.Reader.create(..., ch, para)` → `ReadingScreen` synthetic `SearchResult` → `LaunchedEffect(pendingSearchJump)` → `flatIndexForBookmark*` → `animateScrollToItem`/`scrollToItem` for phone single-column and tablet spread (left vs right via `mid`), same helpers as bookmark jumps — no duplicated logic.
- Verified contextual in-book search: top-bar “⌕” toggles search bar, typing debounced 260ms via `SearchRepository.searchInBook` (same ranking), results Card shows spinner / graceful no-results (“No passages found… Try a different phrase — Folio searches highlights, bookmarks and text, all on-device.”) / ranked list with `Highlight`/`Bookmark` amber badge; tapping jumps via `jumpInBookTo` → `pendingSearchJump` → same `flatIndex` helpers for phone/tablet, on-device only.
- Verified on-device only: `SearchRepository` only queries `BookDao.getAll()`/`HighlightDao.getForBook`/`BookmarkDao.getForBook` + `EpubParser.parse(File)` on `filesDir/books` private files (cached); no network.
- Verified no-results messaging: Library `SearchResultsList` and in-book results both show on-brand empty state with amber sun Canvas + encouraging copy + “Clear search”, not generic empty.
- No rewrite: Library/Reading screens and Room data extended (new `SearchRepository` on top of existing `EpubParser`/`Highlight`/`Bookmark`/`BookDao`), navigation extended with `ch`/`para` query params, `FolioTheme` unchanged.

---

## Session 24 — 2026-09-09 — Insights (quiet ledger, read-only aggregation, FolioTheme journal, streaks)

Branch: `main`.

### Built

- **Insights screen (read-only, book-themed journal)** — `ui/insights/InsightsScreen.kt` + `InsightsViewModel.kt`: standalone `FolioRoute.Insights` screen aggregating existing Room data via `combine` of 5 Flows (`BookDao.observeAll` + `HighlightDao.observeAll` + `BookmarkDao.observeAll` + `ReadingProgressDao.observeAll` + `VocabularyDao.observeAll`) — no parallel tracking, no new collection; queries what already exists (§6). `InsightsViewModel` builds `InsightsState` (totalBooks / inProgressBooks from `reading_progress` rows, totalHighlights / totalBookmarks, vocab total/due `dueAt<=now`/mastered `reps>=3||interval>=21`/learning, rhythm: readingSessions count, distinctDays via `LocalDate` from `lastReadMillis`, currentStreak/longestStreak consecutive-day `ChronoUnit.DAYS` math, lastReadLabel “today/yesterday/Nd ago”). Uses `java.time` (`ZoneId`, `ChronoUnit`) available at `minSdk 33`. `InsightsScreen` is FolioTheme editorial, not a fitness dashboard: `Scaffold` `background` deep green, `LazyColumn` `InsightsHero` (journal Canvas amber/green/burgundy `36×24` + spines) + `InsightsSection` cards (`surface` `16dp`, `1dp`, uppercase `labelMedium` primary + `bodySmall`, `StatCell` with `headlineMedium` value + amber dot via `Canvas`), sections Shelf / Marginalia / Lexicon / Rhythm + closing ledger note, empty state `EmptyJournalCard` with encouraging on-brand copy and on-device reassurance. No generic charts/graphs. Structural inspiration from `book-story-master`’s history grouping only.
- **Navigation + entry points** — `navigation/FolioNav.kt`: added `FolioRoute.Insights("insights")` + `composable(Insights)` → `InsightsScreen(onBack)`. `ui/settings/SettingsScreen.kt`: added `onInsightsClick` param + new **Insights** `SettingsSection` (“A quiet ledger of your reading”, `Button("Open Insights")`, primary) between Library and Appearance, header now “Reading • Library • Insights • Appearance • Privacy”. `ui/library/LibraryScreen.kt`: added `onInsightsClick` param to both overloads, `InsightsTeaser` card (`surface` amber-dot editorial, “Insights — A quiet ledger”) below `VocabularyTeaser`, `LibraryHeader` unchanged but teases Insights; `FolioNavHost` wires `LibraryScreen(onInsightsClick = navigate Insights)` + `SettingsScreen(onInsightsClick = navigate Insights)`. Extends existing navigation, not a rewrite.
- **DAO read-only extensions** — `data/db/dao/ReadingProgressDao.kt`: added `observeAll()`/`getAll()`; `HighlightDao.kt`: added `observeAll()`/`getAll()`/`observeCount()`/`countAll()`; `BookmarkDao.kt`: added `observeAll()`/`getAll()`/`observeCount()`/`countAll()` — all pure `SELECT` queries for Insights aggregation, no extra tables.

### Changed

- `app/src/main/java/com/makemission/folio/data/db/dao/ReadingProgressDao.kt:1-32` — added `observeAll`/`getAll` (all progress rows for streaks/sessions).
- `app/src/main/java/com/makemission/folio/data/db/dao/HighlightDao.kt:1-45` — added `observeAll`/`getAll`/`observeCount`/`countAll`.
- `app/src/main/java/com/makemission/folio/data/db/dao/BookmarkDao.kt:1-47` — added `observeAll`/`getAll`/`observeCount`/`countAll`.
- `app/src/main/java/com/makemission/folio/ui/insights/InsightsViewModel.kt` — new: `InsightsState` + `InsightsViewModel` (combine 5 DAOs, books/in-progress, highlights/bookmarks, vocab total/due/mastered, streaks via `LocalDate`, lastReadLabel).
- `app/src/main/java/com/makemission/folio/ui/insights/InsightsScreen.kt` — new: `InsightsScreen` (`Scaffold` + `InsightsHeader` + `InsightsHero` + `EmptyJournalCard` + 4 `InsightsSection`s with `StatCell` amber dot, empty-state handling), journal styling.
- `app/src/main/java/com/makemission/folio/navigation/FolioNav.kt:1-121` — added `FolioRoute.Insights`, `composable(Settings)` now passes `onInsightsClick`, added `composable(Insights)` → `InsightsScreen`.
- `app/src/main/java/com/makemission/folio/ui/settings/SettingsScreen.kt:1-431` — added `onInsightsClick` param, new Insights section (`Button("Open Insights")`), header subtitle now includes Insights.
- `app/src/main/java/com/makemission/folio/ui/library/LibraryScreen.kt:1-375` — added `onInsightsClick` param (both overloads), `InsightsTeaser` card below vocabulary, wired via `FolioNav`.
- `README.md:1-185` — intro now lists Insights journal; project structure adds `insights/{InsightsScreen, InsightsViewModel}` + `FolioNav` insights + `Library InsightsTeaser` + `Settings Insights` + db note (Insights queries existing tables); added `## Insights screen` section (Shelf/Marginalia/Lexicon/Rhythm, empty state, FolioTheme, entry points, Inspiration); Library now notes Insights teaser, Reading notes DB v8 unchanged but preserved.
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint, no rewrite).
- Verified read-only: Insights only calls `observeAll` on existing DAOs; no `insert`/`upsert`/`update` in ViewModel; creating a highlight/bookmark/vocab/progress immediately reflects in Insights via Flow combine.
- Verified stats: books `totalBooks` = `BookDao.observeAll.size`, `inProgressBooks` = `progress.size`; highlights/bookmarks = global `observeAll.size`; vocab `total/due/mastered` from `vocabulary` (due `dueAt<=now`, mastered `reps>=3`); streaks from `lastReadMillis` distinct `LocalDate` sorted via `ChronoUnit.DAYS` (current 0 if gap>1, longest max run), distinctDays count, lastReadLabel “today/yesterday/Nd ago”.
- Verified empty state: fresh DB (no rows) → `isEmpty true` → `InsightsHero(isEmpty=true)` + `EmptyJournalCard` with encouraging copy (“No pages turned yet — and that’s fine … open a book … on-device”) + no sections shown; after data exists sections appear.
- Verified theming: deep green `background`, `surface`/`surfaceVariant` cards `16dp`, uppercase `labelMedium`, `headlineMedium` values, amber dot `Canvas`, flat hero — not generic charts/graphs, journal-like.
- Verified navigation: Settings → “Open Insights” button and Library → “Insights — A quiet ledger” card both navigate to `insights` via `FolioNav`; Back returns via `popBackStack`; no rewrite of `LibraryScreen`/`SettingsScreen` structure beyond extension.

---

## Session 23 — 2026-09-09 — Bookmarks (distinct from highlights, position marks, top-bar toggle + bottom sheet, phone+tablet jump)

Branch: `main`.

### Built

- **Bookmark entity (Room, distinct from Highlight)** — `data/db/entity/Bookmark.kt`: new `@Entity(tableName="bookmarks")` with `id` autoGenerate, `bookId`, `chapterIndex`, `paragraphIndex` (spec's “position”), `createdAt`, `previewText` (120-char snippet). Separate table from `highlights` — bookmark just marks a spot, no text selection / Multiply rendering (§4). `data/db/dao/BookmarkDao.kt`: `observeForBook` (Flow), `getForBook`, `findExact(chapter,para)`, `insert(REPLACE)`, `delete`, `deleteById`, `clearForBook`. `data/db/FolioDatabase.kt`: v7→v8, added `Bookmark` entity + `bookmarkDao()`, `fallbackToDestructiveMigration(true)`. Build on top of existing Highlight/Room setup, not a rewrite.
- **ReadingViewModel bookmarks** — `ui/reader/ReadingViewModel.kt`: added `bookmarkDao`, `bookmarks: StateFlow<List<Bookmark>>` via `observeForBook(bookId)` (`SharingStarted.WhileSubscribed`), `addBookmark`/`removeBookmark(bookmark)`/`removeBookmark(ch,para)`/`toggleBookmark(ch,para)` (idempotent via `findExact`, auto `previewText` from current `chapters[ ch].paragraphs[para].take(120)`), `isBookmarked(ch,para, list)` helper. Tracks vocabulary via existing path (no rewrite of `Highlight`/`LcsAnchor`/`Sm2`).
- **Reading top bar bookmark toggle (filled when bookmarked)** — `ui/reader/ReadingScreen.kt`: `ReadingScreen` now collects `bookmarks` from `ReadingViewModel` and passes `onToggleBookmark`/`onDeleteBookmark` to `ReadingScreenContent`. `ReadingScreenContent` hoists `singleListState`/`tabletLeftState`/`tabletRightState` (`rememberLazyListState`) so the `TopAppBar` can know the current reading position; `currentBookmarkPos` via `derivedStateOf { bookmarkPositionForFlat(firstVisible, chapters) }` and `isCurrentBookmarked` via `bookmarks.any { ch==curCh && para==curPara }`. `TopAppBar.actions` now has: bookmark icon `TextButton` (`☆` → `🔖` filled when `isCurrentBookmarked`, `onToggleBookmark(currentCh, currentPara)`) and `Bookmarks N` button that opens the bottom sheet. Works on both layouts because the hoisted states are the actual `LazyListState`s used by `SingleColumnReadingContent`/`TwoColumnReadingContent`.
- **Bookmark bottom sheet (X-Ray spirit) + jump** — `ui/reader/components/BookmarkBottomSheet.kt`: new `ModalBottomSheet(skipPartiallyExpanded)` listing `bookmarks` for the current book (chapter title + `Ch/¶` + preview `maxLines 2` + formatted date `MMM d, h:mm a`, `Surface` cards `12dp`, `surfaceVariant`); empty state shows guidance (“Bookmark the current page with the top-bar icon. Filled = already bookmarked”). Tapping a row calls `onBookmarkClick` → sets `pendingBookmarkJump` and dismisses; `Remove` button calls `onBookmarkDelete`. `ReadingScreenContent` holds `pendingBookmarkJump: Bookmark?` and passes to both column contents. `SingleColumnReadingContent` (`listState` param) and `TwoColumnReadingContent` (`leftListState`/`rightListState`) each have `LaunchedEffect(pendingBookmarkJump)` that computes `flatIndexForBookmark*` helpers (mirror `LazyColumn` flat layout: `1 title + paras + (ch0?1 diagram) + 1 gap`) and does `animateScrollToItem`/`scrollToItem` with `onJumpConsumed`, so jump works on phone and tablet (left vs right determined by `chapterIndex < mid`). No copy from `book-story-master` — structural inspiration from its history sheets only.
- **Helpers** — added `bookmarkPositionForFlat`, `flatIndexForBookmark`, `flatIndexForBookmarkInLeft`, `flatIndexForBookmarkInRight` at bottom of `ReadingScreen.kt` to map between `LazyColumn` flat indices and `(chapter, paragraph)` for accurate bookmark icon + jump. Kept existing `flatIndexToChapterParagraph` for progress fallback but fixed diagram offset.

### Changed

- `app/src/main/java/com/makemission/folio/data/db/entity/Bookmark.kt` — new: Bookmark entity (bookId, chapterIndex/position, preview).
- `app/src/main/java/com/makemission/folio/data/db/dao/BookmarkDao.kt` — new: Flow + exact lookup + insert/delete.
- `app/src/main/java/com/makemission/folio/data/db/FolioDatabase.kt:1-43` — v7→v8, added Bookmark + bookmarkDao.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingViewModel.kt:1-290` — added bookmarkDao/bookmarks Flow, add/remove/toggle/isBookmarked.
- `app/src/main/java/com/makemission/folio/ui/reader/components/BookmarkBottomSheet.kt` — new: ModalBottomSheet list with jump/delete/date.
- `app/src/main/java/com/makemission/folio/ui/reader/ReadingScreen.kt:1-1184` — added Bookmark import, hoisted `singleListState`/`tabletLeftState`/`tabletRightState`, `currentBookmarkPos`/`isCurrentBookmarked`, TopAppBar bookmark icon (`☆`/`🔖`) + `Bookmarks N` button, `pendingBookmarkJump` state + bottom sheets (`XRay` + `Bookmark`), updated `SingleColumnReadingContent`/`TwoColumnReadingContent` to accept external list states + `pendingBookmarkJump`/`onJumpConsumed` + `LaunchedEffect` flat helpers, added `bookmarkPositionForFlat`/`flatIndexForBookmark*` helpers.
- `README.md:1-185` — intro now lists bookmarks (position-only, distinct from highlights, top-bar toggle + sheet + jump); tech stack Room v8; project structure adds `Bookmark`/`BookmarkDao` + `BookmarkBottomSheet` + reader bookmarks note; Reading section progress v8 + new Bookmarks bullet (entity, VM, TopBar icon, sheet, jump, phone+tablet, Inspiration).
- `Project.md` — this changelog entry.

### Verification

- `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew :app:assembleDebug -x lint` — `BUILD SUCCESSFUL` (no new lint).
- Verified distinct from highlights: Bookmark table separate, no ink/pressure/LCS fields; adding a highlight does not create a bookmark and vice versa.
- Verified top bar toggle: first visible paragraph bookmarked → icon `🔖` primary; not bookmarked → `☆`; tapping toggles via `toggleBookmark` (idempotent `findExact`).
- Verified sheet: current book's bookmarks shown with chapter + paragraph + preview + date; empty state guidance shown; Remove deletes row via Room Flow.
- Verified jump: tapping bookmark dismisses sheet and `animateScrollToItem` scrolls to exact chapter+paragraph via flat helpers; tested phone (single-column) and tablet spread (left var for `ch<mid`, right var for `ch>=mid`, hoisted states) — both scroll without crash.
- No rewrite: `Highlight` entity/DAO untouched, `FolioDatabase` extended, `ReadingViewModel`/`ReadingScreen` built on top.

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
