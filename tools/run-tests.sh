#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$ROOT/dist"
bash "$ROOT/tools/build-helium.sh"
BIN=$ROOT/dist/companion
"$BIN" unit | tee "$ROOT/dist/unit.txt"
export COMPANION_PASSWORD="${COMPANION_PASSWORD:-fixture-password-not-a-secret}"
python3 "$ROOT/tools/mock_server.py" 8766 >"$ROOT/dist/mock.out" 2>"$ROOT/dist/mock.err" &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT
sleep 0.3
"$BIN" contract 8766 | tee "$ROOT/dist/contract.txt"
if grep -q "$COMPANION_PASSWORD" "$ROOT/dist/mock.err" "$ROOT/dist/contract.txt" "$ROOT/dist/unit.txt"; then
  echo "password leaked into logs" >&2
  exit 1
fi
echo "helium tests ok"
