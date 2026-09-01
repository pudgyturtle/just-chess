package dev.justchess.app.data

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import dev.justchess.app.BuildConfig

object Pgn {
    fun build(
        tags: Map<String, String>,
        moves: List<Move>,
        result: String,
    ): String {
        val b = StringBuilder()
        val ordered = linkedMapOf(
            "Event" to (tags["Event"] ?: "Just Chess"),
            "Site" to (tags["Site"] ?: "Just Chess"),
            "Date" to (tags["Date"] ?: today()),
            "Round" to (tags["Round"] ?: "-"),
            "White" to (tags["White"] ?: "?"),
            "Black" to (tags["Black"] ?: "?"),
            "Result" to result,
        )
        tags.forEach { (k, v) -> if (k !in ordered) ordered[k] = v }
        for ((k, v) in ordered) {
            b.append("[").append(k).append(" \"").append(escape(v)).append("\"]\n")
        }
        b.append('\n')
        val list = MoveList()
        for (m in moves) list.add(m)
        val body = try {
            val numbered = list.toSanWithMoveNumbers().trim()
            if (numbered.isEmpty()) result else "$numbered $result"
        } catch (_: Exception) {
            fallbackBody(moves, result)
        }
        b.append(wrap(body, 80))
        b.append('\n')
        return b.toString()
    }

    fun parseMoves(pgn: String): List<Move> {
        val body = stripTags(pgn)
            .replace("1-0", " ")
            .replace("0-1", " ")
            .replace("1/2-1/2", " ")
            .replace("*", " ")
        val list = MoveList()
        list.loadFromSan(body)
        return list.toList()
    }

    fun sanList(moves: List<Move>): List<String> {
        if (moves.isEmpty()) return emptyList()
        val list = MoveList()
        moves.forEach { list.add(it) }
        return try {
            list.toSanArray().toList()
        } catch (_: Exception) {
            moves.map { it.toString() }
        }
    }

    fun replayFenAt(moves: List<Move>, ply: Int): String {
        val board = Board()
        val n = ply.coerceIn(0, moves.size)
        for (i in 0 until n) board.doMove(moves[i])
        return board.fen
    }

    fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE).replace('-', '.')

    fun enginePlayerName(elo: Int): String =
        "Stockfish ${BuildConfig.STOCKFISH_VERSION} (engine Elo $elo)"

    /** First ~6 plies as compact SAN, e.g. `1.e4 e5 2.Nf3`. Derived from stored PGN. */
    fun openingSummary(pgn: String, maxPlies: Int = 6): String {
        val moves = try {
            parseMoves(pgn)
        } catch (_: Exception) {
            return ""
        }
        val sans = sanList(moves).take(maxPlies)
        if (sans.isEmpty()) return ""
        val sb = StringBuilder()
        sans.forEachIndexed { i, san ->
            if (i % 2 == 0) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(i / 2 + 1).append('.').append(san)
            } else {
                sb.append(' ').append(san)
            }
        }
        return sb.toString()
    }

    private fun stripTags(pgn: String): String {
        return pgn.lineSequence()
            .filterNot { it.trim().startsWith("[") }
            .joinToString(" ")
            .replace(Regex("\\{[^}]*\\}"), " ")
            .replace(Regex(";.*"), " ")
    }

    private fun fallbackBody(moves: List<Move>, result: String): String {
        val board = Board()
        val sb = StringBuilder()
        moves.forEachIndexed { i, m ->
            if (board.sideToMove == Side.WHITE) {
                sb.append(i / 2 + 1).append(". ")
            }
            sb.append(m.toString()).append(' ')
            board.doMove(m)
        }
        sb.append(result)
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun wrap(text: String, width: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = StringBuilder()
        var line = 0
        for (w in words) {
            if (line + w.length + 1 > width && line > 0) {
                out.append('\n')
                line = 0
            } else if (line > 0) {
                out.append(' ')
                line += 1
            }
            out.append(w)
            line += w.length
        }
        return out.toString()
    }
}
