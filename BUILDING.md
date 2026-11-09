# Building Companion 0.6.4

Unofficial Android client for an OpenCode server you run. Originally built by Amniscient.
This is not the OpenCode app. See NOTICE. This tree is MIT. The Helium compiler it downloads is Apache-2.0. The fonts are SIL OFL 1.1.

The session model is Helium (`helium/app.amni`). The Android package compiles that file to C with the public Helium compiler, then links it through a small JNI shell. Gold is not used.

## What you need

- Linux
- JDK 17
- A C compiler (`cc`)
- Android SDK with platform 35, build-tools 35.0.0, and NDK 27.2.12479018
- `git`, so the script can fetch the Helium compiler

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
```

## Build the debug APK

```bash
bash tools/build-apk.sh
```

The script clones https://github.com/Amnibro/helium at commit `38a5e04940d85e27e26cf55cc3c7c4d90800cb0c`, builds the seed compiler, compiles `helium/app.amni`, runs the unit tests, and assembles the debug APK.

The APK is `dist/companion-debug.apk`. It is signed with the Android debug keystore Gradle creates on this machine. No release key is in this tree.

`versionName` is `0.6.4` and `versionCode` is `10`.

## Tests without an APK

```bash
bash tools/run-tests.sh
```

That builds the Helium test driver and runs it against `tools/mock_server.py`.

## Run it

Install `dist/companion-debug.apk`. On the computer:

```bash
opencode serve --hostname 0.0.0.0 --port 4096
opencode pair --url http://<lan-ip>:4096
```

`opencode pair` with no `--url` often prints `127.0.0.1`, which the phone cannot open. Paste the link, or type the computer's address in the app and paste the code. The debug APK allows cleartext HTTP to a private address. A release build does not.

See `REBRANDING.md` to change the name, package id, icon, and colors.
