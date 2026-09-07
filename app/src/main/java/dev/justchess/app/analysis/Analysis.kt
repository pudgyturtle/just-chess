package dev.justchess.app.analysis

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.move.Move
import dev.justchess.app.data.Pgn
import dev.justchess.app.engine.EngineScore
import dev.justchess.app.engine.OpeningBook
import dev.justchess.app.engine.StockfishEngine
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable

@Serializable
enum class AnalysisState { NONE, RUNNING, COMPLETE, FAILED, STALE }
@Serializable
enum class Classification { BEST, GREAT, GOOD, CONFUSING, POOR, BLUNDER, MISS, BRILLIANT, BOOK }
@Serializable
enum class AnalysisPreset(val label: String, val movetimeMs: Long, val depth: Int?, val multiPv: Int) { FAST("Fast", 120, null, 2), STANDARD("Standard", 300, null, 2), DEEP("Deep", 700, 16, 3); fun settings(bookPlies: Int = 8) = AnalysisSettings(this, movetimeMs, depth, multiPv, bookPlies) }


@Serializable
data class AnalysisSettings(val preset: AnalysisPreset = AnalysisPreset.FAST, val movetimeMs: Long = 120, val depth: Int? = null, val multiPv: Int = 2, val bookPlies: Int = 8, val classifierVersion: String = MoveClassifier.VERSION)
@Serializable
data class AnalysisScore(val cp: Int? = null, val mate: Int? = null) {
    init { require((cp == null) xor (mate == null)) { "score must be cp or mate" } }
    fun comparable(): Int = mate?.let { if (it >= 0) 100000 - it.coerceAtLeast(0) else -100000 - it.coerceAtMost(0) } ?: cp!!
    fun negated() = if (mate != null) AnalysisScore(mate = -mate!!) else AnalysisScore(cp = -cp!!)
    companion object { fun from(score: EngineScore) = when (score) { is EngineScore.Cp -> AnalysisScore(cp = score.value); is EngineScore.Mate -> AnalysisScore(mate = score.value) } }
}
@Serializable
data class AnalysisLine(val score: AnalysisScore, val pv: List<String> = emptyList())

@Serializable
enum class AnalysisPhase { OPENING, MIDDLEGAME, ENDGAME }


@Serializable
data class PlyAnalysis(val ply: Int, val beforeFen: String, val afterFen: String, val playedUci: String, val bestUci: String, val bestScore: AnalysisScore, val playedScore: AnalysisScore, val lossCp: Int, val classification: Classification, val pv: List<String> = emptyList(), val multiPv: List<AnalysisLine> = emptyList(), val phase: AnalysisPhase = AnalysisPhase.MIDDLEGAME)
@Serializable
data class GameAnalysis(val gameId: String, val engineVersion: String, val settings: AnalysisSettings, val plies: List<PlyAnalysis>, val meanCpl: Double, val engineAccuracy: Double, val classifierVersion: String = settings.classifierVersion)

data class ClassificationInput(val lossCp: Int, val bestScore: AnalysisScore? = null, val playedScore: AnalysisScore? = null, val playedRank: Int? = null, val onlyGoodMove: Boolean = false, val swingCp: Int = 0, val inBook: Boolean = false, val opponentBlunderPrevious: Boolean = false, val sacrifice: Boolean = false)

object MoveClassifier {
    const val VERSION = "cp-mate-v2-2d"
    fun classify(input: ClassificationInput): Classification {
        if (input.inBook) return Classification.BOOK
        val best = input.bestScore?.comparable()
        val played = input.playedScore?.comparable()
        val mateSwing = input.bestScore?.mate != null && input.playedScore?.mate != null && (input.bestScore.mate!! > 0) != (input.playedScore.mate!! > 0)
        val winLoss = best != null && played != null && best >= 100 && played <= -100
        if (mateSwing || winLoss || input.lossCp >= 200) return Classification.BLUNDER
        if (input.sacrifice && input.lossCp <= 30) return Classification.BRILLIANT
        if (input.opponentBlunderPrevious && input.lossCp > 30) return Classification.MISS
        if (input.lossCp <= 0 || input.playedRank == 1) return Classification.BEST
        if (input.onlyGoodMove || (input.swingCp >= 150 && input.lossCp <= 30)) return Classification.GREAT
        return when { input.lossCp <= 30 -> Classification.GOOD; input.lossCp < 80 -> Classification.CONFUSING; input.lossCp < 200 -> Classification.POOR; else -> Classification.BLUNDER }
    }
    fun classify(lossCp: Int, inBook: Boolean = false): Classification = classify(ClassificationInput(lossCp = lossCp, inBook = inBook))
    fun accuracy(meanCpl: Double): Double = (100.0 - meanCpl / 5.0).coerceIn(0.0, 100.0)
}

class GameAnalyzer(private val engine: StockfishEngine, private val engineVersion: String = "Stockfish 17.1") {
    suspend fun analyze(gameId: String, pgn: String, settings: AnalysisSettings = AnalysisPreset.FAST.settings(), playerColor: String = "WHITE", onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }): GameAnalysis {
        val moves = Pgn.parseMoves(pgn)
        val board = Board()
        val out = ArrayList<PlyAnalysis>(moves.size)
        val allUci = ArrayList<String>(moves.size)
        val playerIsWhite = playerColor == "WHITE"
        for ((index, move) in moves.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(index + 1, moves.size)
            val beforeFen = board.fen
            val before = engine.analyze(allUci, settings.movetimeMs, settings.depth, settings.multiPv)
            val actual = move.toString()
            board.doMove(move)
            allUci += actual
            val after = engine.analyze(allUci, settings.movetimeMs, settings.depth, 1)
            val bestLine = before.bestLine ?: throw IllegalStateException("analysis returned no score")
            val afterLine = after.bestLine ?: throw IllegalStateException("analysis returned no played score")
            val bestScore = AnalysisScore.from(bestLine.score)
            val playedScore = AnalysisScore.from(afterLine.score).negated()
            val loss = (bestScore.comparable() - playedScore.comparable()).coerceAtLeast(0)
            val bestUci = bestLine.pv.firstOrNull() ?: before.bestmove
            val rank = before.lines.indexOfFirst { it.pv.firstOrNull() == actual }.takeIf { it >= 0 }?.plus(1)
            val nearBest = before.lines.size > 1 && before.lines.drop(1).all { bestScore.comparable() - AnalysisScore.from(it.score).comparable() > 80 }
            val inBook = index < settings.bookPlies && OpeningBook.pick(allUci.dropLast(1)) != null
            val playerMove = (index % 2 == 0) == playerIsWhite
            val opponentBlunder = playerMove && out.lastOrNull()?.classification == Classification.BLUNDER
            val sacrifice = playerMove && isHangingSacrifice(beforeFen, actual, rank, loss)
            val label = MoveClassifier.classify(ClassificationInput(loss, bestScore, playedScore, rank, nearBest, loss, inBook, opponentBlunder, sacrifice))
            val lines = before.lines.take(settings.multiPv.coerceAtLeast(2)).map { AnalysisLine(AnalysisScore.from(it.score), it.pv.take(8)) }
            out += PlyAnalysis(index + 1, beforeFen, board.fen, actual, bestUci, bestScore, playedScore, loss, label, bestLine.pv.take(8), lines, phaseFor(beforeFen, index + 1))
        }
        val mean = out.map { it.lossCp }.average().takeIf { it.isFinite() } ?: 0.0
        return GameAnalysis(gameId, engineVersion, settings, out, mean, MoveClassifier.accuracy(mean))
    }
    private fun isHangingSacrifice(fen: String, actual: String, rank: Int?, loss: Int): Boolean {
        if (rank !in 1..2 || loss > 30) return false
        val before = runCatching { Board().apply { loadFromFen(fen) } }.getOrNull() ?: return false
        val wanted = runCatching { Move(actual, before.sideToMove) }.getOrNull() ?: return false
        val after = Board().apply { loadFromFen(fen) }
        val legal = after.legalMoves().firstOrNull { it.from == wanted.from && it.to == wanted.to && it.promotion == wanted.promotion } ?: return false
        after.doMove(legal)
        val piece = after.getPiece(legal.to)
        if (piece == Piece.NONE || piece.pieceType == PieceType.KING) return false
        return after.legalMoves().any { it.to == legal.to && after.getPiece(it.to) == piece && pieceValue(piece.pieceType) >= 3 }
    }

    private fun phaseFor(fen: String, ply: Int): AnalysisPhase {
        if (ply <= 12) return AnalysisPhase.OPENING
        val board = runCatching { Board().apply { loadFromFen(fen) } }.getOrNull() ?: return AnalysisPhase.MIDDLEGAME
        val nonKing = Piece.entries.count { it != Piece.NONE && it.pieceType != PieceType.KING && board.getPieceLocation(it).isNotEmpty() }
        return if (nonKing <= 6) AnalysisPhase.ENDGAME else AnalysisPhase.MIDDLEGAME
    }

    private fun pieceValue(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 1
        PieceType.KNIGHT, PieceType.BISHOP -> 3
        PieceType.ROOK -> 5
        PieceType.QUEEN -> 9
        else -> 0
    }
}
@Serializable
data class AnalysisCache(
    val gameId: String,
    val key: String,
    val state: AnalysisState = AnalysisState.NONE,
    val analysis: GameAnalysis? = null,
    val error: String? = null,
)
@Serializable
data class AnalysisUiState(
    val gameId: String? = null,
    val state: AnalysisState = AnalysisState.NONE,
    val completed: Int = 0,
    val total: Int = 0,
    val cache: AnalysisCache? = null,
    val error: String? = null,
)
