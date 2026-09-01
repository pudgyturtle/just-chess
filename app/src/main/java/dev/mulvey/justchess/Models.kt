package dev.mulvey.justchess

import dev.mulvey.justchess.rating.Elo
import kotlinx.serialization.Serializable

object EngineLevels {
    val all = listOf(1320, 1500, 1700, 1900, 2100, 2300)
    const val DEFAULT = 1500
}

enum class TimeControl(
    val id: String,
    val label: String,
    val initialMs: Long?,
) {
    FIVE("5+0", "5+0", 5 * 60 * 1000L),
    TEN("10+0", "10+0", 10 * 60 * 1000L),
    UNLIMITED("unlimited", "Off", null);

    val isUnlimited: Boolean get() = initialMs == null

    companion object {
        fun fromId(id: String): TimeControl =
            entries.firstOrNull { it.id == id } ?: TEN
    }
}

enum class ColorChoice {
    WHITE, BLACK, RANDOM;
    val label: String
        get() = when (this) {
            WHITE -> "White"
            BLACK -> "Black"
            RANDOM -> "Random"
        }
}

enum class GameEnd {
    CHECKMATE,
    STALEMATE,
    THREEFOLD,
    FIFTY_MOVE,
    INSUFFICIENT,
    RESIGN,
    FLAG,
}

enum class PgnResult(val tag: String) {
    WHITE_WINS("1-0"),
    BLACK_WINS("0-1"),
    DRAW("1/2-1/2"),
    STAR("*");
}

@Serializable
data class Profile(
    val name: String = "Mark",
    val rating: Double = 1500.0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val gamesPlayed: Int = 0,
    val lastEngineElo: Int = EngineLevels.DEFAULT,
    val lastTimeControlId: String = TimeControl.TEN.id,
    val lastColor: String = ColorChoice.WHITE.name,
) {
    val provisional: Boolean get() = gamesPlayed < Elo.PROVISIONAL_GAMES
    fun ratingLabel(): String {
        if (gamesPlayed == 0) return "Unrated"
        val n = rating.toInt()
        return if (provisional) "$n?" else n.toString()
    }
}

@Serializable
data class GameRecord(
    val id: String,
    val pgn: String,
    val result: String,
    val playerColor: String,
    val engineElo: Int,
    val timeControl: String,
    val dateIso: String,
    val playerName: String,
    val playerRatingBefore: Double,
    val playerRatingAfter: Double,
    val termination: String,
    val plyCount: Int,
    val appVersion: String = "1.0.0",
    val stockfishVersion: String = "17.1",
)

data class ClockState(
    val whiteMs: Long,
    val blackMs: Long,
    val running: Boolean,
)

const val STOCKFISH_THREADS = 1
const val STOCKFISH_HASH_MB = 32
const val UNLIMITED_MOVETIME_MS = 1000L
const val SOURCE_URL = "https://github.com/pudgyturtle/just-chess"
