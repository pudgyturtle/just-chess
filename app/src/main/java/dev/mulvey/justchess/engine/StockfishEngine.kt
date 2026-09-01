package dev.mulvey.justchess.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UCI wrapper around the official Stockfish binary shipped as libstockfish.so.
 *
 * Hunch (verified in v1): 1 search thread, Hash 32 MB so a phone does not
 * overheat. Search is stopped in onStop/onPause and when the game ends.
 * UCI_LimitStrength + UCI_Elo are applied before every go.
 */
class StockfishEngine(private val binary: File) {
    private val mutex = Mutex()
    private val writeLock = Any()
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val started = AtomicBoolean(false)
    private val searching = AtomicBoolean(false)

    @Volatile
    var engineId: String = "Stockfish 18"
        private set

    val threads: Int = 1
    val hashMb: Int = 32

    suspend fun ensureStarted() = withContext(Dispatchers.IO) {
        mutex.withLock { startLocked() }
    }

    private fun startLocked() {
        if (started.get() && process?.isAlive == true) return
        if (!binary.exists()) {
            throw IllegalStateException("Stockfish binary missing at ${binary.absolutePath}")
        }
        binary.setExecutable(true, false)
        val pb = ProcessBuilder(binary.absolutePath).redirectErrorStream(true)
        val proc = pb.start()
        process = proc
        reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8), 8 * 1024)
        writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8), 8 * 1024)
        sendRaw("uci")
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val line = reader!!.readLine() ?: break
            if (line.startsWith("id name ")) {
                engineId = line.removePrefix("id name ").trim()
            }
            if (line == "uciok") break
        }
        sendRaw("setoption name Threads value $threads")
        sendRaw("setoption name Hash value $hashMb")
        sendRaw("setoption name Ponder value false")
        sendRaw("isready")
        waitForLocked("readyok", 8000)
        started.set(true)
    }

    suspend fun newGame() = withContext(Dispatchers.IO) {
        mutex.withLock {
            startLocked()
            sendRaw("stop")
            sendRaw("ucinewgame")
            sendRaw("isready")
            waitForLocked("readyok", 8000)
        }
    }

    suspend fun setElo(elo: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            startLocked()
            sendRaw("setoption name UCI_LimitStrength value true")
            sendRaw("setoption name UCI_Elo value $elo")
            sendRaw("isready")
            waitForLocked("readyok", 4000)
        }
    }

    /**
     * Ask Stockfish for a move. Timed games use wtime/btime (ms). Unlimited uses
     * a modest movetime so the Elo limit still applies and the engine does not
     * think forever.
     *
     * The wait for bestmove does not hold [mutex], so [stopSearch] can send `stop`.
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
            val r = reader ?: throw IllegalStateException("engine reader missing")
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline) {
                val line = r.readLine() ?: break
                if (line.startsWith("bestmove ")) {
                    val tok = line.split(Regex("\\s+"))
                    return@withContext tok.getOrNull(1)?.trim().orEmpty()
                }
            }
            throw IllegalStateException("Stockfish did not return bestmove")
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

    private fun waitForLocked(token: String, timeoutMs: Long) {
        val r = reader ?: return
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = r.readLine() ?: break
            if (line.contains(token)) return
        }
    }
}
