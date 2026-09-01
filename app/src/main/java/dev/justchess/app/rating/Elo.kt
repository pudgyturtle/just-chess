package dev.justchess.app.rating

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Simple Elo vs the on-device Stockfish ladder only. Not Chess.com, not FIDE.
 * Starts at 1500 (provisional). Bot ratings are treated as fixed.
 */
object Elo {
    const val INITIAL = 1500
    const val PROVISIONAL_GAMES = 10

    fun expected(player: Int, opponent: Int): Double {
        return 1.0 / (1.0 + 10.0.pow((opponent - player) / 400.0))
    }

    fun k(ratedGames: Int): Int = if (ratedGames < PROVISIONAL_GAMES) 32 else 16

    /**
     * @param score 1.0 win, 0.5 draw, 0.0 loss
     */
    fun update(player: Int, opponent: Int, score: Double, ratedGames: Int): Int {
        val exp = expected(player, opponent)
        val next = player + k(ratedGames) * (score - exp)
        return next.roundToInt().coerceIn(100, 3200)
    }
}
