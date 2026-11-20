# Galleria

A local-only gallery for Android: no cloud, no accounts, no network access at all.
Your folders, your files, and a video player done right.

> Work in progress. The name is provisional.

## Principles

- **No network.** The `INTERNET` permission is removed from the manifest, and the build fails
  if it ever appears in the final merged manifest.
- **Your files stay where they are.** The app reads them through MediaStore and never hides,
  renames or moves anything unless you ask it to.
- **Nothing is lost.** Move is always copy → verify → delete; Delete uses the system trash.

## Building

Requirements: Android Studio (with its bundled JDK) and the Android SDK, API 37.

```
./gradlew testDebugUnitTest   # logic tests, run on the PC
./gradlew assembleDebug       # "Galleria (prova)", installs next to the real app
./gradlew assembleRelease     # the real app
```

Release builds are signed only if `~/.keys/galleria-keystore.properties` exists
(or the path given with `-Pgalleria.keystoreProperties=...`), with
`storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Keys never go into this repository.

Minimum Android version: 14 (API 34).

## License

[GPL-3.0](LICENSE)
