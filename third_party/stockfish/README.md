# Stockfish 17.1 (official)

- Binary: official `stockfish-android-armv8` from the [sf_17.1](https://github.com/official-stockfish/Stockfish/releases/tag/sf_17.1) GitHub release, renamed to `libstockfish.so` so Android packs it in `jniLibs/arm64-v8a/` and the app can exec it from `applicationInfo.nativeLibraryDir`.
- NNUE networks are **embedded in this official binary**. Nothing is downloaded at runtime.
- Corresponding source: https://github.com/official-stockfish/Stockfish/tree/sf_17.1
- License: GPL-3.0 (see `Copying.txt`)
- ABI shipped in v1: **arm64-v8a** only (Pixel / GrapheneOS). No official Android x86_64 build is published.

Runtime UCI limits used by Just Chess: `Threads=1`, `Hash=32`, `UCI_LimitStrength=true`, `UCI_Elo=<selected>`.
