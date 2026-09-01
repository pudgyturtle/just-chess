package dev.justchess.app.engine

import java.io.File

/**
 * Fallback only. Playtest showed nativeLibraryDir/libstockfish.so already
 * started on Graphene; copying to codeCacheDir as "stockfish" broke New game.
 * [StockfishEngine] execs the packaged path first.
 */
object StockfishBinary {
    const val PACKAGED_NAME = "libstockfish.so"
    const val CACHE_NAME = "stockfish"

    fun resolve(packaged: File, cacheDir: File): File {
        cacheDir.mkdirs()
        val dest = File(cacheDir, CACHE_NAME)
        if (packaged.exists() && packaged.length() > 1_000_000L) {
            if (!dest.exists() || dest.length() != packaged.length()) {
                packaged.copyTo(dest, overwrite = true)
            }
            dest.setReadable(true, false)
            dest.setExecutable(true, false)
            return dest
        }
        if (dest.exists() && dest.length() > 1_000_000L) {
            dest.setExecutable(true, false)
            return dest
        }
        throw IllegalStateException(
            "Stockfish binary missing at ${packaged.absolutePath}",
        )
    }
}
