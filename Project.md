# Folio — Project Log

Changelog after every coding session. `README.md` stays as the basic app
description and documentation; this file tracks what changed and when.

Active coding branch: `main`.

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
