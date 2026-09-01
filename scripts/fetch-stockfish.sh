#!/usr/bin/env bash
# Download official Stockfish 17.1 Android armv8 into jniLibs as libstockfish.so
# (17.1 is used because the official SF 18 android binary exceeds GitHub's 100MB file cap.)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a/libstockfish.so"
mkdir -p "$(dirname "$DEST")"
if [[ -f "$DEST" ]] && [[ "$(stat -c%s "$DEST" 2>/dev/null || stat -f%z "$DEST")" -gt 1000000 ]]; then
  echo "Already present: $DEST"
  exit 0
fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
URL="https://github.com/official-stockfish/Stockfish/releases/download/sf_17.1/stockfish-android-armv8.tar"
echo "Downloading $URL"
curl -L --fail -o "$TMP/sf.tar" "$URL"
tar -xf "$TMP/sf.tar" -C "$TMP" stockfish/stockfish-android-armv8
cp "$TMP/stockfish/stockfish-android-armv8" "$DEST"
chmod +x "$DEST"
echo "Wrote $DEST"
