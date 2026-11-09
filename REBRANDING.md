# Rebranding

This app is MIT, copyright 2026 Amniscient. OpenCode can take it and badge it as their own. Please keep the credit line `Originally built by Amniscient`.

The Helium compiler cloned by `tools/build-apk.sh` stays Apache-2.0. It is not in this tree. The fonts stay SIL OFL 1.1. See NOTICE.

Nothing here is the OpenCode logo. Swap these files. Do not copy logo files from opencode.ai/brand into this tree unless Anomaly says the brand rules allow it.

## Name

| What the person sees | File |
|---|---|
| Launcher name `Companion` | `android/app/src/main/AndroidManifest.xml` `android:label` |
| Header word `Companion` | `android/app/src/main/java/com/amniscient/occompanion/CompanionApp.kt` in `TopBar` |
| Subtitle `Unofficial prototype for OpenCode` | same `TopBar` |
| About lines, including the credit | `helium/app.amni` `render` and `fn credit` |
| Kotlin copy of the credit | `android/app/src/main/java/com/amniscient/occompanion/Model.kt` `CREDIT` |
| HTTP User-Agent | `android/app/src/main/java/com/amniscient/occompanion/HttpClient.kt` `UA` |

`CREDIT` must match `fn credit`. Tests check both.

## Package id

`com.amniscient.occompanion` is set in two places that must match:

- `android/app/build.gradle.kts` `namespace` and `applicationId`
- the directory `android/app/src/main/java/com/amniscient/occompanion/` and the same path under `src/test`

The manifest activity name is `.MainActivity`, so it follows the namespace. The JNI library name is `companion` in `android/app/src/main/cpp/CMakeLists.txt`.

## Icon

- `android/app/src/main/res/drawable/ic_mark.xml` is the launcher art. It is an original mark, not the OpenCode logo.
- `AndroidManifest.xml` points at it with `android:icon="@drawable/ic_mark"`.

## Colors

Palettes are ARGB longs in `android/app/src/main/java/com/amniscient/occompanion/Theme.kt`: `darkPalette` and `lightPalette`. Each is `bg, surface, text, muted, line, accent, code, warn`. Ink is the default. Phosphor, Amber, and Cyan are the other four.

The window behind Compose is `android/app/src/main/res/values/themes.xml` (`Theme.Companion`: status bar, navigation bar, window background). Those three are `#0B0B0B`, the Ink dark background. If the default palette changes, change them too.

Hairline borders use `TerminalColors.hair()` in `CompanionApp.kt`. They follow `line` from the palette.
