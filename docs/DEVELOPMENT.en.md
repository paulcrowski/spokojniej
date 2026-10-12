# Building Spokojniej

The Android app is native Java with no third-party runtime libraries. Android 8 (API 26) is the minimum; the project compiles and targets Android SDK 35. The public APK interface is Polish.

## Requirements

- A macOS or Linux shell, Python 3 for the UI helper scripts, and a JDK compatible with the Android SDK tools.
- Android SDK Platform 35 and Build Tools 35.0.0.
- For device checks: Android Platform Tools and a dedicated test emulator.

The local validation used Android Studio’s bundled JDK. The shell build supports `JAVA_HOME` and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`). Its default paths are the usual Android Studio locations on macOS.

```sh
export JAVA_HOME="/path/to/your/jdk"
export ANDROID_HOME="/path/to/your/android-sdk"
bash scripts/build-apk.sh
```

The script runs 12 time-logic checks, compiles resources and Java, generates DEX, aligns and signs the APK, then verifies its signature. Output: `dist/Spokojniej-1.4.0.apk` and its SHA-256 file. The Gradle project files are also provided; there is no Gradle wrapper in this repository.

## Signing and updates

The published APK is signed with the author’s local key. That private key is excluded from the repository. If the build script does not find `signing/local.keystore`, it creates your own development key. The fixed password in the script is for this local development workflow; it is not an uploaded private key or a hosted-service credential.

Keep your own signing directory private. An APK signed with your new key cannot update the author’s installed APK in place. The official 1.4.0 release uses the same author key as previous builds, so it can update them without uninstalling or losing app data.

Version code 5 upgrades SQLite schemas 1–4 to schema 5 while preserving the original event IDs and data.

## Storage and backups

SQLite v5 stores moment types and helpful actions alongside existing completed items and circumstances. JSON export uses format 4; import accepts formats 1, 2 and 3 as well. Import validates the whole file before writing in a transaction, adds missing entries, preserves existing gratitude and completed-item text, and adds missing circumstance labels. Separate conversations at the same time are matched by ordinal position. Importing an earlier backup after changing a conversation’s timestamp may add the older record as a separate event. Older app versions cannot read backup format 4.

## Android checks

The scripts target **only `emulator-5580`**, a dedicated disposable emulator. They clear its app data; the upgrade script also uninstalls its test app. Do not point them at a personal device. The Python helpers currently default to the macOS SDK location (`~/Library/Android/sdk`); adjust that path for another platform.

```sh
bash scripts/build-device-tests.sh
python3 scripts/verify-android.py
python3 scripts/diary-ui-smoke.py
python3 scripts/moment-ui-smoke.py
```

The Android checks exercise real SQLite storage, validation, backup merging, notification actions, small steps, backdated calm check-ins, done items, optional conversation circumstances and pause state. The UI checks use the system document picker and a real exported file to simulate changing phones. The older UI helper scripts are retained as historical test code for earlier versions.

`verify-upgrade.py 1.0.0`, `verify-upgrade.py 1.1.0`, `verify-upgrade.py 1.2.0` and `verify-upgrade.py 1.3.0` require the respective historical APK in `dist/`. Those historical packages are not included in this public repository. Their recorded results are available in [the validation evidence](validation/v1.4.0/).

See [the bilingual validation report](VALIDATION.md) for confirmed behavior and device limits. Source files are in `app/src/main/java/pl/spokojniej/app`; the manifest declares only notifications and reboot permissions.

SQLite v5: `moment_details` stores the moment type while preserving original event IDs; `event_help` stores optional helpful actions. Missing moment details on legacy events mean an escalated conversation. `event_contexts` now also accepts `surprise`. The counter and day status skip tension entries; editing a type recalculates both. Backup format 4 includes types and helpful actions. Import matches the effective moment type, timestamp and ordinal; changing a type or timestamp before reimporting an old backup may add the earlier state as a separate event.
