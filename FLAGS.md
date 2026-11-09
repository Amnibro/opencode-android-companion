# Flagged items

These files are part of the tree on purpose. They are not release behavior and they are not credentials.

- `android/app/src/debug/res/xml/network_security_config.xml` is used only by the debug APK. It allows cleartext HTTP so a private-address OpenCode server can be reached. The main config (`android/app/src/main/res/xml/network_security_config.xml`) does not. There is no trust-all certificate bypass. The app still refuses public HTTP itself.
- `tools/pair.py` is an optional helper that prints a one-time QR beside a server you already run. The APK build does not use it. Official pairing is `opencode pair`.

No machine passwords, API tokens, or private hostnames are included. Example addresses in tests and on-screen hints are documentation values (`192.168.1.20`, `10.0.2.2` for the Android emulator, `100.64.0.8` inside the carrier-grade NAT range).
