# Just Chess

Offline chess for GrapheneOS. Play against on-device **Stockfish 18** at labeled **engine Elo** levels. No accounts, ads, analytics, or network.

`applicationId`: `dev.mulvey.justchess`

## Install on GrapheneOS

1. Build a debug APK (below) or take `app/build/outputs/apk/debug/app-debug.apk`.
2. Copy it to the phone (USB, Syncthing, etc.).
3. Open the APK on the device and install. GrapheneOS will warn that it is not Play-signed; that is expected.
4. Uninstall wipes the local profile and history. Use **You → Export backup zip** first.

There is no Play Store listing and no GitHub Release in v1.

## Play

- Engine Elo: 1320 / 1500 / 1700 / 1900 / 2100 / 2300 (Stockfish `UCI_LimitStrength` + `UCI_Elo`, **not** Chess.com or FIDE).
- Time: 5+0, 10+0 (default), or unlimited. Clocks tick only on that side’s turn; flag loses. Unlimited has no clock; the engine still uses a modest `movetime` with the Elo cap.
- White / Black / random. Takebacks, resign. Draws by FIDE rule only (mate, stalemate, threefold, 50-move, insufficient material).
- Opening book for the first 6–12 plies, with variety.
- History is PGN; tap a game to replay. Export/import a zip of `profile.json` + PGNs.

## Build

JDK 17 is required.

```bash
./scripts/fetch-stockfish.sh   # first time, ~110 MB official armv8 binary
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

`assembleDebug` also runs `fetchStockfish` if `libstockfish.so` is missing. The binary is gitignored (GitHub 100 MB limit). NNUE is embedded in the official binary; nothing is downloaded at runtime.

APK: `app/build/outputs/apk/debug/app-debug.apk`

Do not commit `local.properties`, `*.jks`, or keystore passwords. Copy `local.properties.example` if you need an SDK path template.

The merged manifest must not contain `android.permission.INTERNET`. v1 ships **arm64-v8a** only (Pixel / GrapheneOS).

## Stockfish

Official **Stockfish 18** `android-armv8` binary from the [sf_18](https://github.com/official-stockfish/Stockfish/releases/tag/sf_18) release, renamed to `app/src/main/jniLibs/arm64-v8a/libstockfish.so` and executed from `applicationInfo.nativeLibraryDir`. NNUE nets are **embedded in that official binary**; nothing is downloaded at runtime.

Runtime limits: **1 thread**, **Hash 32 MB**, search stopped on pause/stop and when the game ends. Timed games use `go wtime/btime`. Unlimited uses `go movetime 1000`.

Corresponding source: https://github.com/official-stockfish/Stockfish/tree/sf_18  
License: GPL-3.0 (see `third_party/stockfish/Copying.txt` and the app About screen).

No official Android **x86_64** binary is published, so emulators that are not arm64 will not run the engine.

## Rules library

Vendored [chesslib](https://github.com/bhlangonijr/chesslib) (Apache-2.0) under `app/src/main/java/com/github/bhlangonijr/chesslib/`. See `third_party/chesslib/LICENSE`.

## License

Just Chess is **GPL-3.0-or-later**. Full text in `LICENSE`. About copies the source URL `https://github.com/pudgyturtle/just-chess` (no network).

Chess.com is a UX layout guide only. This app does not use Chess.com logos, Neo pieces, brand colors, or bot names.
