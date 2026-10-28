# GS6 Chart Builder for Android

An Android port of the upstream desktop app for packaging custom charts for [GridlessSekai6](https://github.com/UnknownSekai/GridlessSekai6). You fill in the metadata and assets, them the app produces either a project `.zip` or a compiled
`.chart.gs6` file the game loads. 

(`mods/CUSTOM_CHARTS/builder` in GridlessSekai6).

License: AGPL-3.0

## App

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
- **Identifier**: two v4 UUIDs joined by _ uniquely names the chart: exporting again with the same
  identifier updates that chart in the game instead of adding a duplicate. You can regenerate or copy it.

**Import**
- An existing project `.zip` (the app's own output) which refills every field.
- An **UntitledCharts** or **CC Archive** level link. The app downloads the level, fills in title, artist,
  charter, cover, song and chart, and cuts a preview from the song if the level does not ship one.

**Output** (saved to `Downloads/GS6Charts/`)
- **Build zip**: an editable project zip (`ChartInfo.json`, jacket, track, preview, charts, optional music
  videos and cover files).
- **Compile**: the game-ready `.chart.gs6` for Android. Tick *Also compile iOS version* to produce the iOS
  file as well.

Before building, every input is validated (files exist, the image is valid, audio and video files contain
the expected streams, previews are short enough, difficulties are unique, levels are in range, identifier is
well formed) and any problems are shown in a list.

The Tkinter window of the desktop builder is replaced by a Jetpack Compose UI. The upstream Python modules
(`charts.py`, `encode.py`, `untitledcharts.py`, `ccarchive.py`, `deps.py`) run unmodified inside the app
through [Chaquopy](https://chaquo.com/chaquopy/).

## Building

1. `./sync.sh` clones upstream and copies its Python files and `bases/` (84 MB) into the project.
2. Run the GitHub workflow **Build Android APK (native wheels + debug APK)** (Actions > Run workflow). It
   builds every wheel and only if all of them succeed, builds a debug APK. GitHub provides artifacts as zips:
   - `gs6-debug-apk`: the debug APK
   - `wheels-all`: all `.whl` files; unzip into `./wheels/` to build locally in Android Studio
3. Put `libffmpeg.so` in `app/src/main/jniLibs/arm64-v8a/` (grab from repository) before building the APK locally.
4. To build locally, open the folder in Android Studio (it creates the Gradle wrapper), sync, and run on a
   device. Chaquopy needs a host `python3` 3.13 on `PATH` to match the app's Python version.

Requirements: Android 10 (API 29) or newer.
Internet access is only used for UntitledCharts and
CC Archive imports.

## Project layout

| Path | Contents |
|---|---|
| `app/src/main/java/com/utsk/gs6builder/MainActivity.kt` | Compose UI |
| `app/src/main/python/gs6_api.py`, `bridge.py` | Headless API and the Kotlin bridge |
| `app/requirements-android.txt` | Python packages installed by Chaquopy (native ones come from `./wheels`) |
| `.github/workflows/build-app.yml` | Wheel builds and debug APK |
| `sync.sh` | Pulls upstream code and base bundles |

`UnityPy` is pinned to 1.24.2 because upstream's `encode.py` relies on `ObjectReader.stripped` which was
renamed in 1.25.x.