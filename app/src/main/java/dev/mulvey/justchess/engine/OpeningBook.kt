package dev.mulvey.justchess.engine

import kotlin.random.Random

/**
 * Tiny weighted opening book for the first 6–12 plies so limited-strength
 * Stockfish does not play nonsense out of the gate. Variety across games.
 */
object OpeningBook {
    private const val MAX_PLY = 12

    data class Weighted(val uci: String, val w: Int = 1)

    private val table: Map<String, List<Weighted>> = buildMap {
        fun add(key: String, vararg moves: Any) {
            val list = mutableListOf<Weighted>()
            var i = 0
            while (i < moves.size) {
                val uci = moves[i] as String
                val w = if (i + 1 < moves.size && moves[i + 1] is Int) {
                    val n = moves[i + 1] as Int
                    i += 2
                    n
                } else {
                    i += 1
                    1
                }
                list += Weighted(uci, w)
            }
            put(key, list)
        }

        // Ply 1: White
        add("", "e2e4", 8, "d2d4", 6, "c2c4", 2, "g1f3", 2)

        // After 1.e4
        add("e2e4", "e7e5", 6, "c7c5", 5, "e7e6", 2, "c7c6", 2, "d7d5", 1, "g8f6", 1)
        // 1.e4 e5
        add("e2e4 e7e5", "g1f3", 8, "b1c3", 2, "f2f4", 1)
        add("e2e4 e7e5 g1f3", "b8c6", 7, "g8f6", 2, "d7d6", 1)
        add("e2e4 e7e5 g1f3 b8c6", "f1b5", 5, "f1c4", 4, "d2d4", 2, "b1c3", 1)
        // Ruy
        add("e2e4 e7e5 g1f3 b8c6 f1b5", "a7a6", 6, "g8f6", 3, "d7d6", 1)
        add("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6", "b5a4", 6, "b5c6", 2)
        add("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4", "g8f6", 5, "b7b5", 2, "d7d6", 1)
        add("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6", "e1g1", 6, "d2d3", 1)
        add("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1", "f8e7", 3, "b7b5", 3, "d7d6", 1)
        // Italian
        add("e2e4 e7e5 g1f3 b8c6 f1c4", "g8f6", 5, "f8c5", 5, "f8e7", 1)
        add("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6", "d2d3", 4, "b1c3", 2, "f3g5", 1)
        add("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5", "c2c3", 4, "d2d3", 3, "e1g1", 2)
        add("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3", "g8f6", 5, "d8e7", 1)
        add("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 d2d3", "g8f6", 5, "d7d6", 2)
        // Scotch
        add("e2e4 e7e5 g1f3 b8c6 d2d4", "e5d4", 8)
        add("e2e4 e7e5 g1f3 b8c6 d2d4 e5d4", "f3d4", 8)
        add("e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4", "g8f6", 4, "f8c5", 3, "d8h4", 1)
        // Four knights / Vienna-ish
        add("e2e4 e7e5 b1c3", "g8f6", 5, "b8c6", 3)
        add("e2e4 e7e5 g1f3 g8f6", "f3e5", 5, "d2d4", 3, "b1c3", 2)
        add("e2e4 e7e5 g1f3 d7d6", "d2d4", 6, "f1c4", 2)

        // Sicilian
        add("e2e4 c7c5", "g1f3", 8, "b1c3", 2, "c2c3", 1)
        add("e2e4 c7c5 g1f3", "d7d6", 5, "b8c6", 4, "e7e6", 3)
        add("e2e4 c7c5 g1f3 d7d6", "d2d4", 8)
        add("e2e4 c7c5 g1f3 d7d6 d2d4", "c5d4", 8)
        add("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4", "f3d4", 8)
        add("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4", "g8f6", 7, "a7a6", 2)
        add("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6", "b1c3", 8)
        add("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3", "a7a6", 5, "b8c6", 3, "g7g6", 2)
        add("e2e4 c7c5 g1f3 b8c6", "d2d4", 7, "f1b5", 2)
        add("e2e4 c7c5 g1f3 b8c6 d2d4", "c5d4", 8)
        add("e2e4 c7c5 g1f3 b8c6 d2d4 c5d4", "f3d4", 8)
        add("e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4", "g8f6", 5, "e7e6", 2, "g7g6", 2)
        add("e2e4 c7c5 g1f3 e7e6", "d2d4", 8)
        add("e2e4 c7c5 g1f3 e7e6 d2d4", "c5d4", 8)
        add("e2e4 c7c5 g1f3 e7e6 d2d4 c5d4", "f3d4", 8)
        add("e2e4 c7c5 g1f3 e7e6 d2d4 c5d4 f3d4", "a7a6", 4, "g8f6", 4, "b8c6", 2)

        // French
        add("e2e4 e7e6", "d2d4", 8, "d2d3", 1)
        add("e2e4 e7e6 d2d4", "d7d5", 8)
        add("e2e4 e7e6 d2d4 d7d5", "b1c3", 4, "e4d5", 3, "e4e5", 3)
        add("e2e4 e7e6 d2d4 d7d5 b1c3", "g8f6", 5, "f8b4", 4)
        add("e2e4 e7e6 d2d4 d7d5 e4e5", "c7c5", 7, "b8c6", 1)
        add("e2e4 e7e6 d2d4 d7d5 e4d5", "e6d5", 8)

        // Caro-Kann
        add("e2e4 c7c6", "d2d4", 8, "b1c3", 1)
        add("e2e4 c7c6 d2d4", "d7d5", 8)
        add("e2e4 c7c6 d2d4 d7d5", "b1c3", 4, "e4d5", 3, "e4e5", 3)
        add("e2e4 c7c6 d2d4 d7d5 e4d5", "c6d5", 8)
        add("e2e4 c7c6 d2d4 d7d5 b1c3", "d5e4", 8)
        add("e2e4 c7c6 d2d4 d7d5 e4e5", "c8f5", 6, "c6c5", 2)

        // Scandinavian
        add("e2e4 d7d5", "e4d5", 8)
        add("e2e4 d7d5 e4d5", "d8d5", 6, "g8f6", 3)
        add("e2e4 d7d5 e4d5 d8d5", "b1c3", 8)
        add("e2e4 d7d5 e4d5 d8d5 b1c3", "d5a5", 6, "d5d6", 2)

        // Alekhine
        add("e2e4 g8f6", "e4e5", 7, "b1c3", 2)
        add("e2e4 g8f6 e4e5", "f6d5", 8)
        add("e2e4 g8f6 e4e5 f6d5", "d2d4", 6, "c2c4", 2)

        // After 1.d4
        add("d2d4", "d7d5", 5, "g8f6", 5, "e7e6", 1, "f7f5", 1)
        add("d2d4 d7d5", "c2c4", 6, "g1f3", 3, "c1f4", 1)
        add("d2d4 d7d5 c2c4", "e7e6", 5, "c7c6", 4, "d5c4", 2)
        add("d2d4 d7d5 c2c4 e7e6", "b1c3", 6, "g1f3", 3)
        add("d2d4 d7d5 c2c4 e7e6 b1c3", "g8f6", 6, "c7c6", 2)
        add("d2d4 d7d5 c2c4 e7e6 b1c3 g8f6", "c1g5", 4, "g1f3", 3, "c4d5", 1)
        add("d2d4 d7d5 c2c4 c7c6", "g1f3", 5, "b1c3", 4)
        add("d2d4 d7d5 c2c4 c7c6 g1f3", "g8f6", 7, "e7e6", 2)
        add("d2d4 d7d5 c2c4 d5c4", "e2e3", 4, "e2e4", 3, "g1f3", 2)
        add("d2d4 d7d5 g1f3", "g8f6", 6, "c7c6", 2, "e7e6", 2)
        add("d2d4 d7d5 g1f3 g8f6", "c2c4", 6, "c1f4", 2)

        // Indian
        add("d2d4 g8f6", "c2c4", 7, "g1f3", 3)
        add("d2d4 g8f6 c2c4", "e7e6", 4, "g7g6", 4, "c7c5", 2)
        add("d2d4 g8f6 c2c4 e7e6", "b1c3", 5, "g1f3", 4)
        add("d2d4 g8f6 c2c4 e7e6 b1c3", "f8b4", 5, "d7d5", 3)
        add("d2d4 g8f6 c2c4 e7e6 g1f3", "d7d5", 4, "b7b6", 3, "f8b4", 2)
        add("d2d4 g8f6 c2c4 g7g6", "b1c3", 6, "g1f3", 3)
        add("d2d4 g8f6 c2c4 g7g6 b1c3", "f8g7", 6, "d7d5", 3)
        add("d2d4 g8f6 c2c4 g7g6 b1c3 f8g7", "e2e4", 6, "g1f3", 3)
        add("d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4", "d7d6", 8)
        add("d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6", "f2f3", 4, "g1f3", 4, "h2h3", 1)
        add("d2d4 g8f6 c2c4 c7c5", "d4d5", 7, "g1f3", 2)
        add("d2d4 g8f6 g1f3", "g7g6", 4, "e7e6", 3, "d7d5", 3)
        add("d2d4 e7e6", "c2c4", 5, "g1f3", 3, "e2e4", 2)
        add("d2d4 f7f5", "c2c4", 4, "g1f3", 3, "g2g3", 2)

        // English / Reti
        add("c2c4", "e7e5", 4, "g8f6", 4, "c7c5", 2, "e7e6", 2)
        add("c2c4 e7e5", "b1c3", 6, "g2g3", 3)
        add("c2c4 e7e5 b1c3", "g8f6", 5, "b8c6", 3)
        add("c2c4 g8f6", "b1c3", 5, "g1f3", 4)
        add("c2c4 c7c5", "b1c3", 4, "g1f3", 4)
        add("g1f3", "d7d5", 5, "g8f6", 4, "c7c5", 1)
        add("g1f3 d7d5", "g2g3", 4, "d2d4", 4, "c2c4", 2)
        add("g1f3 g8f6", "c2c4", 4, "g2g3", 3, "d2d4", 3)
    }

    fun pick(uciMoves: List<String>, random: Random = Random.Default): String? {
        if (uciMoves.size >= MAX_PLY) return null
        val key = uciMoves.joinToString(" ")
        val options = table[key] ?: return null
        val total = options.sumOf { it.w }
        if (total <= 0) return null
        var r = random.nextInt(total)
        for (opt in options) {
            r -= opt.w
            if (r < 0) return opt.uci
        }
        return options.last().uci
    }
}
