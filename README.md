# GS6 Chart Builder for Android
> [!IMPORTANT]
> This project is not maintained by YumYummity or prodeode. Suggestions, bugs or issues should be submitted to THIS repository instead of GridlessSekai6.

An Android port of the upstream desktop app for packaging custom charts for [GridlessSekai6](https://github.com/UnknownSekai/GridlessSekai6). You fill in the metadata and assets, then the app produces either a project `.zip` or a compiled
`.chart.gs6` file the game loads.

(`mods/CUSTOM_CHARTS/builder` in GridlessSekai6).

License: AGPL-3.0

## App

**Splash**: the app opens with the icon and a hammer-drop launch animation.

**Inputs**
- **Jacket** image, **audio track** and **preview audio** (the preview may be at most 45 seconds).
- **1–6 charts**, one per difficulty (`easy`, `normal`, `hard`, `expert`, `master`, `append`), each with a
  level from 1 to 99. Chart files can be `.sus`, `.usc`, or Next SEKAI / Chart Cyanvas LevelData
  (`.json`, `.json.gz`, `.nsld`). The combo count and chart offset are read from the chart automatically.
  If a chart uses features the converter does not support, the app lists them and lets you continue anyway
  (unsupported parts are skipped or replaced with the closest equivalent).
- **Metadata**: title, charter, lyricist, composer, arranger, artist, vocals, collab, original song, and a
  "full-length (not a game-size cut)" flag.
- **Offsets**: chart offset and additional music-video offset, in milliseconds.
- **Music videos** (optional, up to two): one `ogmv` and one `2dmv`.
- **Alternate vocals / covers** (optional): each has a name, vocals, vocal type (`sekai`, `original_song`,
  `another_vocal`), its own audio, preview and jacket. When you add alt vocals you also name the default
  vocal, which uses the main track and jacket.
- **Identifier**: two v4 UUIDs joined by `_` uniquely names the chart: exporting again with the same
  identifier updates that chart in the game instead of adding a duplicate. You can regenerate or copy it.

**Import**
- An existing project `.zip` (the app's own output) which refills every field.
- An **UntitledCharts** or **CC Archive** level link. The app downloads the level, fills in title, artist,
  charter, cover, song and chart, and cuts a preview from the song if the level does not ship one.

**Output** (saved to `Downloads/GS6Charts/` by default; pick another folder under Settings → Output folder)
- **Build zip**: an editable project zip (`ChartInfo.json`, jacket, track, preview, charts, optional music
  videos and cover files).
- **Compile**: opens a sheet with an *Also build project ZIP* toggle, then produces the game-ready
  `.chart.gs6` files. When a compile succeeds the identifier is copied to the clipboard automatically
  (toggleable in Settings).

The Compile button is greyed out until the required fields are filled in; tapping it while greyed lists
every missing field. While the app works (importing, compiling, building) a non-closable progress popup
locks the UI and shows progress text ("Packing ACB audio…", "Processing video…", "Processing jacket…").

Before building, every input is validated (files exist, the image is valid, audio and video files contain
the expected streams, previews are short enough, difficulties are unique, levels are in range, identifier is
well formed) and any problems are shown in a list. Chart filenames that are clearly labelled (e.g.
`master.sus`, `normal.json`) preselect the difficulty automatically; you can always change it manually.

## Settings

- **Appearance**: UI design — **Material UI** or **Project SEKAI UI**. Project SEKAI is a light-first design
  and carries its own Light/Dark choice, so it does not follow the Material theme. Material UI keeps the
  theme (system/light/dark), dynamic color from the wallpaper, theme color seed swatches, AMOLED dark and
  the liquid glass tab bar (Android 13+, plain bar otherwise). Both designs share the font
  (Intern / Inter / system) and the haptics setting.
- **Project**: copy identifier after compiling, output folder (SAF), new project.
- **Updates**: check on launch, Release (stable only) or Preview (also alpha/beta/pre/rc) channel, and a
  manual check against the newest GitHub release. Release downloads open in the browser; pre-releases ask
  for confirmation first.
- **About**: **App info** — version/versionCode, the matching GitHub release (release time and whether it
  was published as a pre-release/beta), the build attestation signed by the release workflow (matched by
  the APK's SHA-256 digest), the bundled Chaquopy/Python and component versions, and the APK digest.
  There is also the open-source licenses page. Holding "Check for updates" for 10 seconds unlocks a
  Developer Mode section with an in-app private-data browser.

The upstream Python modules (`charts.py`, `encode.py`, `untitledcharts.py`, `ccarchive.py`, `deps.py`) run
unmodified inside the app through [Chaquopy](https://chaquo.com/chaquopy/). The desktop builder's Tkinter
window is replaced by a Jetpack Compose UI with two designs:

- **Material** (default): Material 3 with a large collapsing top app bar, dynamic color and a liquid glass
  tab bar (`MaterialShell.kt`, `MaterialPages.kt`, `MaterialKit.kt`).
- **Project SEKAI**: this app's own teal/mint/sky card design — pill buttons, flat selectable cards, a teal
  header bar and card-based sheets and dialogs (`ProjectSekaiShell.kt`, `ProjectSekaiPages.kt`,
  `ProjectSekaiKit.kt`). Light by default, with its own Light/Dark switch in Settings.

Both designs read the same project state, so switching the design in Settings keeps your work.
[Miuix](https://github.com/compose-miuix-ui/miuix) is used for its icon set only.

## Building

1. `./sync.sh` clones upstream and copies its Python files and `bases/` into the project.
2. Run the **Build App** workflow (Actions → Run workflow). It builds the Android wheels (optionally for
   release too: APK artifact `gs6builder-android`, plus a GitHub release with a build attestation).
   Unzip the `wheels-*` artifacts into `./wheels/` to build locally.
3. Put `libffmpeg_bin.so` and friends in `app/src/main/jniLibs/arm64-v8a/` before building the APK locally.
4. To build locally, open the folder in Android Studio (it creates the Gradle wrapper), sync, and run on a
   device. Chaquopy needs a host `python3` 3.13 on `PATH` to match the app's Python version. The workflow
   pins the app Python to 3.13 and injects the Chaquopy plugin version into `BuildConfig.CHAQUOPY_VERSION`
   so the App info page always reports the true build version.

Requirements: Android 10 (API 29) or newer, arm64-v8a.
Internet access is only used for UntitledCharts / CC Archive imports and update/attestation lookups.

## Project layout

| Path | Contents |
|---|---|
| `app/src/main/java/com/utsk/gs6builder/` | Compose UI: `MaterialShell.kt` (pages, tabs, dialogs), `MaterialPages.kt` / `MaterialKit.kt` (settings rows, widgets), `ProjectSekaiShell.kt` / `ProjectSekaiPages.kt` / `ProjectSekaiKit.kt` (the Project SEKAI design), `AppInfo.kt` (about page + fetchers), `Splash.kt` (launch animation) |
| `app/src/main/python/gs6_api.py`, `bridge.py` | Headless API and the Kotlin bridge |
| `app/requirements-android.txt` | Python packages installed by Chaquopy (native ones come from `./wheels`) |
| `.github/workflows/build-app.yml` | Wheel builds, APK, release + build attestation |
| `sync.sh` | Pulls upstream code and base bundles |

`UnityPy` is pinned to 1.24.2 because upstream's `encode.py` relies on `ObjectReader.stripped` which was
renamed in 1.25.x.
