# Tilevio Launcher

[![CI](https://github.com/Raphael87-FR/tilevio-launcher/actions/workflows/ci.yml/badge.svg)](https://github.com/Raphael87-FR/tilevio-launcher/actions/workflows/ci.yml)

A minimalist home screen for Android TV and Google TV, focused on one thing: opening your apps. On the device it appears as **Tilevio**.

## Features

- Icon grid of installed apps, navigable with the remote
- Per-profile controls to show and hide Apps from settings
- Can be set as the default TV home screen
- Solid-color or imported local-image Wallpaper

Low resource use is a permanent design rule: no resident services, no background work, no unnecessary animations or dependencies.

## Requirements

- Android TV or Google TV device running Android 10 (API 29) or later
- JDK 17 or later and the Android SDK (compile SDK 37.2) to build from source

## Build and test

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew test            # run the unit tests (JUnit + Robolectric, no emulator needed)
```

The APK is written to `app/build/outputs/apk/debug/`.

To build an unsigned release APK, run `./gradlew assembleRelease`. The APK is written to `app/build/outputs/apk/release/`. Maintainers can follow the [release guide](docs/release.md) to create a private release key, sign and verify the APK, and publish it after validation.

## Install

Download a signed APK from [GitHub Releases](https://github.com/Raphael87-FR/tilevio-launcher/releases) and copy it to the TV. Open it with a file manager and allow that App to install unknown Apps when Android prompts you. Alternatively, with ADB enabled on the TV, run `adb install path/to/tilevio.apk` from a connected computer.

## Select Tilevio as HOME

Open Tilevio, select the settings button in the top-right corner, and activate the default-home option. Accept Tilevio in the Android HOME chooser. Press HOME to confirm that Tilevio opens.

If the chooser does not appear or the selection does not persist, use the default-home action again to open the available settings route. In the TV manufacturer's settings, look for **Home screen**, **Home app**, or **Default apps**, then select Tilevio. The exact name and location vary by manufacturer.

## Configure the Launcher

Open the top-right settings button to:

- choose a dark solid Wallpaper or import a local image;
- show or hide installed Apps for the current Android profile;
- activate Tilevio as the default HOME.

Changes apply immediately. The installed version appears at the bottom of settings.

Maintainers must complete the [real-TV validation checklist](docs/real-tv-validation.md) before publishing a release.

## Tech stack

Kotlin and Jetpack Compose for TV. See [ADR 0001](docs/adr/0001-use-compose-for-tv.md) for the reasoning.

## Project docs

- [GLOSSARY.md](GLOSSARY.md): domain vocabulary (Launcher, App, Tile, Wallpaper…)
- [docs/adr/](docs/adr/): architecture decisions
- Issues and specs are tracked in [GitHub Issues](https://github.com/Raphael87-FR/tilevio-launcher/issues)

## License

[MIT](LICENSE)
