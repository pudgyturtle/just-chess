package dev.justchess.app.engine

import java.io.File

/**
 * Android's app lib dir can be mounted noexec (Graphene / API 29+). Copy the
 * packaged `libstockfish.so` into [cacheDir] and run that file.
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
