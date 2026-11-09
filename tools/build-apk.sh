#!/usr/bin/env bash
# Debug APK only. Uses the Android debug keystore Gradle creates locally.
# No release key is created or embedded.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
REV=38a5e04940d85e27e26cf55cc3c7c4d90800cb0c
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
SRC=${HELIUM_SRC:-$ROOT/.cache/helium}
bash "$ROOT/tools/build-helium.sh" >/dev/null
GEN=$ROOT/android/app/src/main/cpp/generated
mkdir -p "$GEN"
ln -sfn "$ROOT/helium/app.amni" "$SRC/hc/lib/app.amni"
HC_LIB="$SRC/hc/lib" "$SRC/build/hc0" build "$ROOT/helium/android_main.amni" -o "$GEN/companion.c"
cp "$SRC/hc/hrt.h" "$GEN/hrt.h"
cat >>"$GEN/companion.c" <<'EOF'

int companion_exchange(const uint8_t *in, int64_t n, uint8_t *out, int64_t cap) {
  static int booted = 0;
  if (!booted) { hr_init_globals(); booted = 1; }
  hsl s = { (uint8_t *)in, n };
  hrv buf = {0};
  companion_handle(s, &buf);
  int64_t k = buf.n;
  if (k < 0) k = 0;
  if (k > cap) k = cap;
  if (k > 0 && buf.b && out) memcpy(out, buf.b, (size_t)k);
  hr_rfree(&buf, 1);
  return (int)k;
}
EOF
printf 'sdk.dir=%s\n' "$ANDROID_HOME" >"$ROOT/android/local.properties"
cd "$ROOT/android"
chmod +x ./gradlew
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
APK=$ROOT/android/app/build/outputs/apk/debug/app-debug.apk
mkdir -p "$ROOT/dist"
cp "$APK" "$ROOT/dist/companion-debug.apk"
sha256sum "$ROOT/dist/companion-debug.apk" | tee "$ROOT/dist/companion-debug.apk.sha256"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$ROOT/dist/companion-debug.apk" | tee "$ROOT/dist/apk-badging.txt" | head -n 20
echo "helium $REV" | tee "$ROOT/dist/helium-rev.txt"
echo "APK $ROOT/dist/companion-debug.apk"
