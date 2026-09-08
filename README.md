# Folio

Folio is a premium, distraction-free Android ebook reader by MakeMission LLC (`com.makemission.folio`). It bridges digital convenience and the tactile craft of traditional bookmaking — fluid stylus interactions, magazine-quality typography, and adaptive layouts for phones and tablets.

The app is Jetpack Compose–first and currently ships the curated editorial library (cover grid + flat empty state); the reading experience lands next.

## Tech stack

- Kotlin 2.2, Android Gradle Plugin 9.4 (built-in Kotlin; version catalog in `gradle/libs.versions.toml`)
- `compileSdk` / `targetSdk` 37, `minSdk` 33, Java 17
- Jetpack Compose (BOM `2025.09.00`): `ui`, `foundation`, `material3`, `activity-compose`
- Material Components (`Theme.Material3.DayNight.NoActionBar` for the window)
- Design system in `ui/theme/` — see below

## Design system

The palette and typography come from `Inspiration/Folio_Project.md` §2:

- **Background** — deep green `#004F39` (library + dark mode)
- **Active / tags** — burgundy `#780116`
- **Accent** — xanthous `#F7B538` (highlights, progress, focus)

Typography: heavy sans-serif headers (placeholder for Druk Wide / Helvetica Neue Bold, `FontFamily.SansSerif` Black/ExtraBold) and serif body text. Both `Color.kt` / `Type.kt` / `Theme.kt` follow the structure of the `book-story-master` reference app's `ui/theme/` (no code copied).

## Project structure

```
Folio/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/makemission/folio/
│       │   ├── MainActivity.kt                 # Compose host — shows LibraryScreen
│       │   ├── data/model/Book.kt             # minimal library model + curated seed
│       │   └── ui/
│       │       ├── theme/ { Color, Type, Theme }.kt
│       │       └── library/
│       │           ├── LibraryScreen.kt        # Scaffold + header + content switch
│       │           └── components/
│       │               ├── BookGrid.kt         # LazyVerticalGrid (adaptive)
│       │               ├── BookCoverCard.kt    # flat editorial cover (2:3)
│       │               └── EmptyLibraryState.kt# flat-illustration empty state
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

Legacy template fragments / Navigation graph from the initial scaffold remain in `res/` but are unused — the app is fully Compose.

## Library screen (editorial grid)

`LibraryScreen(books)` — inspired by `book-story-master`'s `LibraryScaffold` →
`LibraryGridLayout` layering, rebuilt for Folio:

- **Curated grid** — `LazyVerticalGrid` with `GridCells.Adaptive(148.dp)` so phones show 2 columns and tablets scale naturally; each item is a `BookCoverCard` (2:3 cover, rounded 16dp, spine accent, Folio title chip).
- **Empty state** — centered flat illustration (amber sun, burgundy / paper / deep-green books on a shelf) drawn with Compose `Canvas`, plus editorial copy. Shown when `books.isEmpty()`.
- **Header** — weighty sans "Library" title + collection subtitle over the Folio background.
- **Home wiring** — `MainActivity` renders `LibraryScreen(curatedSampleBooks())`; the flat empty state is ready to show once real storage replaces the in-memory seed.

No reading screen yet — that comes next.

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
