package dev.justchess.app.analysis

import com.github.bhlangonijr.chesslib.Board
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
enum class Classification { BEST, GREAT, GOOD, CONFUSING, POOR, BLUNDER, BOOK }
@Serializable
data class AnalysisSettings(val movetimeMs: Long = 250, val depth: Int? = null, val multiPv: Int = 2, val bookPlies: Int = 8, val classifierVersion: String = MoveClassifier.VERSION)
@Serializable
data class AnalysisScore(val cp: Int? = null, val mate: Int? = null) {
    init { require((cp == null) xor (mate == null)) { "score must be cp or mate" } }
    fun comparable(): Int = mate?.let { if (it >= 0) 100000 - it.coerceAtLeast(0) else -100000 - it.coerceAtMost(0) } ?: cp!!
    fun negated() = if (mate != null) AnalysisScore(mate = -mate!!) else AnalysisScore(cp = -cp!!)
    companion object { fun from(score: EngineScore) = when (score) { is EngineScore.Cp -> AnalysisScore(cp = score.value); is EngineScore.Mate -> AnalysisScore(mate = score.value) } }
}
@Serializable
data class PlyAnalysis(val ply: Int, val beforeFen: String, val afterFen: String, val playedUci: String, val bestUci: String, val bestScore: AnalysisScore, val playedScore: AnalysisScore, val lossCp: Int, val classification: Classification, val pv: List<String> = emptyList())
@Serializable
data class GameAnalysis(val gameId: String, val engineVersion: String, val settings: AnalysisSettings, val plies: List<PlyAnalysis>, val meanCpl: Double, val engineAccuracy: Double, val classifierVersion: String = settings.classifierVersion)

data class ClassificationInput(val lossCp: Int, val bestScore: AnalysisScore? = null, val playedScore: AnalysisScore? = null, val playedRank: Int? = null, val onlyGoodMove: Boolean = false, val swingCp: Int = 0, val inBook: Boolean = false)

object MoveClassifier {
    const val VERSION = "cp-mate-v1"
    fun classify(input: ClassificationInput): Classification {
        if (input.inBook) return Classification.BOOK
        val best = input.bestScore?.comparable()
        val played = input.playedScore?.comparable()
        val mateSwing = input.bestScore?.mate != null && input.playedScore?.mate != null && (input.bestScore.mate!! > 0) != (input.playedScore.mate!! > 0)
        val winLoss = best != null && played != null && best >= 100 && played <= -100
        if (mateSwing || winLoss || input.lossCp >= 200) return Classification.BLUNDER
        if (input.lossCp <= 0 || input.playedRank == 1) return Classification.BEST
        if (input.onlyGoodMove || (input.swingCp >= 150 && input.lossCp <= 30)) return Classification.GREAT
        return when { input.lossCp <= 30 -> Classification.GOOD; input.lossCp < 80 -> Classification.CONFUSING; input.lossCp < 200 -> Classification.POOR; else -> Classification.BLUNDER }
    }
    fun classify(lossCp: Int, inBook: Boolean = false): Classification = classify(ClassificationInput(lossCp = lossCp, inBook = inBook))
    fun accuracy(meanCpl: Double): Double = (100.0 - meanCpl / 5.0).coerceIn(0.0, 100.0)
}

class GameAnalyzer(private val engine: StockfishEngine, private val engineVersion: String = "Stockfish 17.1") {
    suspend fun analyze(gameId: String, pgn: String, settings: AnalysisSettings = AnalysisSettings(), onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }): GameAnalysis {
        val moves = Pgn.parseMoves(pgn)
        val board = Board()
        val out = ArrayList<PlyAnalysis>(moves.size)
        val allUci = ArrayList<String>(moves.size)
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
            val nearBest = before.lines.drop(1).all { bestScore.comparable() - AnalysisScore.from(it.score).comparable() > 80 }
            val inBook = index < settings.bookPlies && OpeningBook.pick(allUci.dropLast(1)) != null
            val label = MoveClassifier.classify(ClassificationInput(loss, bestScore, playedScore, rank, nearBest, loss, inBook))
            out += PlyAnalysis(index + 1, beforeFen, board.fen, actual, bestUci, bestScore, playedScore, loss, label, bestLine.pv.take(8))
        }
        val mean = out.map { it.lossCp }.average().takeIf { it.isFinite() } ?: 0.0
        return GameAnalysis(gameId, engineVersion, settings, out, mean, MoveClassifier.accuracy(mean))
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
