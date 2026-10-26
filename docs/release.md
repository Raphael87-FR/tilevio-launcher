# Release a signed APK

These instructions are for a maintainer publishing Tilevio Launcher. Never commit a keystore, passwords, or files containing signing credentials. Publication requires explicit approval and must wait until the [real-TV validation checklist](real-tv-validation.md) passes.

## Create a release keystore

Create one dedicated keystore outside the repository if a Tilevio release keystore does not already exist. Keep its backups and passwords in separate secure locations. Losing the keystore prevents publishing updates under the same signing identity.

Run the following command from outside the repository, replacing the path with a private location on your machine:

```bash
mkdir -p "$HOME/.tilevio/signing"
chmod 700 "$HOME/.tilevio/signing"

keytool -genkeypair \
  -keystore "$HOME/.tilevio/signing/tilevio-release.jks" \
  -alias tilevio \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Choose strong, unique passwords when prompted. Do not put passwords in the command line, shell history, environment files, source files, issues, or release notes. Do not copy the keystore into the checkout.

## Build, sign, and verify

Run the standard checks and build the unsigned release APK from the commit being released:

```bash
./gradlew test assembleDebug assembleRelease
```

Use the `zipalign` and `apksigner` tools from your installed Android SDK build-tools. Replace `$ANDROID_SDK/build-tools/VERSION` with an installed version. The signing command prompts for the keystore and key passwords without exposing them in shell history.

```bash
"$ANDROID_SDK/build-tools/VERSION/zipalign" -p -f 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  app/build/outputs/apk/release/tilevio-0.1.0-aligned.apk

"$ANDROID_SDK/build-tools/VERSION/apksigner" sign \
  --ks "$HOME/.tilevio/signing/tilevio-release.jks" \
  --ks-key-alias tilevio \
  --out app/build/outputs/apk/release/tilevio-0.1.0.apk \
  app/build/outputs/apk/release/tilevio-0.1.0-aligned.apk

"$ANDROID_SDK/build-tools/VERSION/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/tilevio-0.1.0.apk
```

Confirm that verification succeeds and that the printed certificate belongs to the dedicated Tilevio release key. Keep the unsigned and aligned intermediate APKs out of GitHub Releases. Generated APKs are already excluded by the repository's build-output rules; do not force-add them.

Install the signed APK on the validation TV and complete every item in the [real-TV validation checklist](real-tv-validation.md). Do not publish the release if any item fails.

## Publish v0.1.0

After validation passes and the user explicitly approves public publication, replace `<validated-commit-full-SHA>` below with the full 40-character SHA of the validated commit. The command creates the `v0.1.0` tag at that explicit target and publishes only the signed APK with installation notes:

```bash
gh release create v0.1.0 \
  app/build/outputs/apk/release/tilevio-0.1.0.apk \
  --target "<validated-commit-full-SHA>" \
  --title "Tilevio Launcher v0.1.0" \
  --notes "Install on Android TV or Google TV running Android 10 or later. Download the APK, allow your file manager to install unknown Apps when Android prompts you, and open the APK. After installation, open Tilevio, use the settings button to choose a Wallpaper and App visibility, and select Tilevio as your default HOME. If no HOME chooser appears, use the TV manufacturer's home-screen settings. See the README for details."
```

Confirm that the release contains the intended signed APK and installation notes. Publishing a release does not by itself change repository visibility; do not change visibility as part of this process.
