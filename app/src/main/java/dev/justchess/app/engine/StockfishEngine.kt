package dev.justchess.app.engine

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * UCI wrapper around the official Stockfish binary shipped as libstockfish.so.
 *
 * 1 search thread, Hash 32 MB. Search is stopped on pause/stop and takeback.
 * UCI_LimitStrength + UCI_Elo are applied before every go.
 */
class StockfishEngine(
    private val packaged: File,
    private val cacheDir: File? = null,
) {
    private val mutex = Mutex()
    private val writeLock = Any()
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val started = AtomicBoolean(false)
    private val searching = AtomicBoolean(false)

    @Volatile
    var engineId: String = "Stockfish 17.1"
        private set

    val threads: Int = 1
    val hashMb: Int = 32

    suspend fun ensureStarted() = withContext(Dispatchers.IO) {
        mutex.withLock { startLocked() }
    }

    private fun startLocked() {
        if (started.get() && process?.isAlive == true) return
        val binary = if (cacheDir != null) {
            StockfishBinary.resolve(packaged, cacheDir)
        } else {
            packaged
        }
        if (!binary.exists()) {
            throw IllegalStateException("Stockfish binary missing at ${binary.absolutePath}")
        }
        binary.setExecutable(true, false)
        val pb = ProcessBuilder(binary.absolutePath)
            .directory(binary.parentFile)
            .redirectErrorStream(true)
        val proc = pb.start()
        process = proc
        reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8), 8 * 1024)
        writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8), 8 * 1024)
        sendRaw("uci")
        if (!waitForLocked("uciok", 8000)) {
            throw IllegalStateException("Stockfish did not complete UCI handshake")
        }
        sendRaw("setoption name Threads value $threads")
        sendRaw("setoption name Hash value $hashMb")
        sendRaw("setoption name Ponder value false")
        sendRaw("isready")
        if (!waitForLocked("readyok", 8000)) {
            throw IllegalStateException("Stockfish not ready after handshake")
        }
        started.set(true)
    }

    suspend fun newGame() = withContext(Dispatchers.IO) {
        mutex.withLock {
            startLocked()
            sendRaw("stop")
            sendRaw("ucinewgame")
            sendRaw("isready")
            if (!waitForLocked("readyok", 8000)) {
                throw IllegalStateException("Stockfish not ready after ucinewgame")
            }
        }
    }

    suspend fun setElo(elo: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            startLocked()
            sendRaw("setoption name UCI_LimitStrength value true")
            sendRaw("setoption name UCI_Elo value $elo")
            sendRaw("isready")
            if (!waitForLocked("readyok", 4000)) {
                throw IllegalStateException("Stockfish not ready after Elo $elo")
            }
        }
    }

    /**
     * Ask Stockfish for a move. Timed games use wtime/btime (ms). Unlimited uses
     * a modest movetime so the Elo limit still applies.
     *
     * Waiting for bestmove does not hold [mutex], so [stopSearch] can send `stop`.
     * The wait is cancellable (no blocking readLine without yield).
     */
    suspend fun bestMove(
        uciMovesFromStart: List<String>,
        whiteMs: Long?,
        blackMs: Long?,
        unlimitedMovetimeMs: Long = 1500,
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            startLocked()
            val pos = if (uciMovesFromStart.isEmpty()) {
                "position startpos"
            } else {
                "position startpos moves ${uciMovesFromStart.joinToString(" ")}"
            }
            sendRaw(pos)
            val go = when {
                whiteMs == null || blackMs == null -> "go movetime $unlimitedMovetimeMs"
                else -> {
                    val w = whiteMs.coerceAtLeast(50)
                    val b = blackMs.coerceAtLeast(50)
                    "go wtime $w btime $b"
                }
            }
            searching.set(true)
            sendRaw(go)
        }
        try {
            readUntilBestmove(12_000)
        } catch (e: CancellationException) {
            sendRaw("stop")
            drainBestmove(1500)
            throw e
        } finally {
            searching.set(false)
        }
    }

    suspend fun stopSearch() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) {
            sendRaw("stop")
        }
    }

    suspend fun quit() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                sendRaw("quit")
            } catch (_: Exception) {
            }
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
            process = null
            started.set(false)
            searching.set(false)
        }
    }

    private fun sendRaw(cmd: String) {
        val w = writer ?: return
        synchronized(writeLock) {
            w.write(cmd)
            w.write("\n")
            w.flush()
        }
    }

    /** @return true if [token] was seen */
    private fun waitForLocked(token: String, timeoutMs: Long): Boolean {
        val r = reader ?: return false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (r.ready()) {
                val line = r.readLine() ?: return false
                if (line.contains(token)) return true
            } else {
                Thread.sleep(20)
            }
        }
        return false
    }

    private suspend fun readUntilBestmove(timeoutMs: Long): String {
        val r = reader ?: throw IllegalStateException("engine reader missing")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            yield()
            if (!coroutineContext.isActive) {
                sendRaw("stop")
                throw CancellationException()
            }
            val line = if (r.ready()) {
                r.readLine()
            } else {
                delay(20)
                null
            }
            if (line != null && line.startsWith("bestmove ")) {
                return line.split(Regex("\\s+")).getOrNull(1)?.trim().orEmpty()
            }
        }
        sendRaw("stop")
        return drainBestmove(2000)
            ?: throw IllegalStateException("Stockfish did not return bestmove")
    }

    private suspend fun drainBestmove(timeoutMs: Long): String? {
        val r = reader ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            yield()
            val line = if (r.ready()) r.readLine() else {
                delay(20)
                null
            }
            if (line != null && line.startsWith("bestmove ")) {
                return line.split(Regex("\\s+")).getOrNull(1)?.trim().orEmpty()
            }
        }
        return null
    }
}
