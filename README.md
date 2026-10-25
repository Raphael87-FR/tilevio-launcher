# Tilevio Launcher

[![CI](https://github.com/Raphael87-FR/tilevio-launcher/actions/workflows/ci.yml/badge.svg)](https://github.com/Raphael87-FR/tilevio-launcher/actions/workflows/ci.yml)

A minimalist home screen for Android TV and Google TV, focused on one thing: opening your apps. On the device it appears as **Tilevio**.

## Features

- Icon grid of installed apps, navigable with the remote
- Can be set as the default TV home screen
- Solid-color or imported local-image Wallpaper

Low resource use is a permanent design rule: no resident services, no background work, no unnecessary animations or dependencies.

## Requirements

- Android TV or Google TV device running Android 10 (API 29) or later
- JDK 17 or later and the Android SDK (compile SDK 36) to build from source

## Build and test

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew test            # run the unit tests (JUnit + Robolectric, no emulator needed)
```

The APK is written to `app/build/outputs/apk/debug/`.

## Tech stack

Kotlin and Jetpack Compose for TV. See [ADR 0001](docs/adr/0001-use-compose-for-tv.md) for the reasoning.

## Project docs

- [GLOSSARY.md](GLOSSARY.md): domain vocabulary (Launcher, App, Tile, Wallpaper…)
- [docs/adr/](docs/adr/): architecture decisions
- Issues and specs are tracked in [GitHub Issues](https://github.com/Raphael87-FR/tilevio-launcher/issues)

## License

[MIT](LICENSE)
