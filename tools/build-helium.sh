#!/usr/bin/env bash
# Build the Helium companion tests with the checked-in hc seed compiler.
# Does not run Gold (that path calls a paid model) and does not rewrite the seed.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
REV=38a5e04940d85e27e26cf55cc3c7c4d90800cb0c
SRC=${HELIUM_SRC:-$ROOT/.cache/helium}
if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 https://github.com/Amnibro/helium.git "$SRC"
fi
if ! git -C "$SRC" rev-parse HEAD | grep -q "$REV"; then
  git -C "$SRC" fetch --depth 1 origin "$REV"
  git -C "$SRC" checkout --detach "$REV"
fi
mkdir -p "$SRC/build"
if [ ! -x "$SRC/build/hc0" ]; then
  cc -O1 -fwrapv -w -I"$SRC/hc" "$SRC/hc/seed/hc.c" -o "$SRC/build/hc0" -lm
fi
ln -sfn "$ROOT/helium/app.amni" "$SRC/hc/lib/app.amni"
mkdir -p "$ROOT/dist"
HC_LIB="$SRC/hc/lib" "$SRC/build/hc0" build "$ROOT/helium/main.amni" -o "$ROOT/dist/companion"
echo "$ROOT/dist/companion"
