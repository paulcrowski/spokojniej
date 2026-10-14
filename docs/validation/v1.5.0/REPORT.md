# Spokojniej 1.5.0 — local release checks

- `bash scripts/build-apk.sh`: PASS; 12 time and streak checks passed, Android resources and Java compiled, APK aligned and signed.
- `apksigner verify`: v2 and v3 signatures valid.
- `aapt dump badging`: package `pl.spokojniej.app`, versionCode `6`, versionName `1.5.0`, minSdk `26`, targetSdk `35`.
- Certificate SHA-256: `fe54ab2b36364dd14c002fecf1fb8fd38b04afb9c48b416a71aaf821189826d5`, matching the 1.4.0 certificate.
- APK SHA-256: `644ee1b5e9690a0f07972936645e8035ae07bbec43fc14f049118077757395be`.
- `git diff --check`: PASS.

No Android device or emulator was available for this release. Widget layout, notification delivery, and upgrade behavior have not been verified on a device.
