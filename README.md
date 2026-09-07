# Folio

Folio is an Android portfolio app by MakeMission LLC (`com.makemission.folio`).

It is currently in early scaffolding: a Kotlin Android app based on the
Android Studio Basic Views template with a single activity, a navigation
graph, and two placeholder fragments. Feature development happens on the
`main` branch.

## Tech stack

- Kotlin, Android Gradle Plugin (version catalog in `gradle/libs.versions.toml`)
- `compileSdk` / `targetSdk` 37, `minSdk` 33
- AndroidX: appcompat, activity-ktx, core-ktx, constraintlayout
- Material Components, Navigation Fragment / UI
- ViewBinding enabled

## Project structure

```
Folio/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/makemission/folio/
│       │   ├── MainActivity.kt
│       │   ├── FirstFragment.kt
│       │   └── SecondFragment.kt
│       └── res/
│           ├── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/  # launcher PNGs
│           ├── mipmap-anydpi-v26/                        # adaptive-icon XML
│           ├── layout/, navigation/, menu/
│           └── values/, values-night/, xml/
├── icons/
│   └── android/            # IconKitchen source export (res/ + play_store_512.png)
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## Launcher icons

Source of truth: `icons/android/` (IconKitchen export).

Installed into the app as:

- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`
  - `ic_launcher.png` (legacy)
  - `ic_launcher_round.png` (legacy round, copied from `ic_launcher.png`)
  - `ic_launcher_background.png`, `ic_launcher_foreground.png`,
    `ic_launcher_monochrome.png` (adaptive-icon layers)
- `app/src/main/res/mipmap-anydpi-v26/`
  - `ic_launcher.xml` and `ic_launcher_round.xml` (adaptive icons
    referencing `@mipmap/ic_launcher_background/foreground/monochrome`)
- `icons/android/play_store_512.png` is kept for the Play Store listing only
  and is not bundled in the APK.

`AndroidManifest.xml` references them as:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

To replace icons again, export from IconKitchen into `icons/android/` and
copy `icons/android/res/` over `app/src/main/res/` (keeping the
`ic_launcher_round` duplicates described above).

## Requirements

- Android Studio (Ladybug or newer recommended)
- JDK 11+ (Gradle toolchain via Foojay resolver)
- Android SDK 37 + build tools

## Build & run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Or open the project in Android Studio and press **Run**.

Useful checks:

```bash
./gradlew :app:lint
./gradlew test
```

## Branching

- `main` — active coding branch. All sessions merge here.
- `master` — legacy initial branch, kept for history; merged into `main`.

See `Project.md` for the per-session changelog.
