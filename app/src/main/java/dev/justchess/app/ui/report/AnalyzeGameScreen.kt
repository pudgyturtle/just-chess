package dev.justchess.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import dev.justchess.app.GameViewModel
import dev.justchess.app.analysis.AnalysisScore
import dev.justchess.app.analysis.AnalysisState
import dev.justchess.app.analysis.Classification
import dev.justchess.app.analysis.GameAnalysis
import dev.justchess.app.analysis.PlyAnalysis
import dev.justchess.app.ui.board.BoardArrow
import dev.justchess.app.ui.board.ChessBoard

private val KeyClassifications = setOf(
    Classification.GREAT,
    Classification.BRILLIANT,
    Classification.MISS,
    Classification.CONFUSING,
    Classification.POOR,
    Classification.BLUNDER,
)

private data class ReviewSnapshot(
    val pieces: Map<Square, Piece>,
    val lastFrom: Square?,
    val lastTo: Square?,
    val check: Square?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeGameScreen(vm: GameViewModel, id: String, onBack: () -> Unit, onHome: () -> Unit) {
    val games by vm.history.collectAsState()
    val analysisState by vm.analysis.collectAsState()
    val game = games.firstOrNull { it.id == id }
    val analysis = analysisState.cache?.analysis?.takeIf { analysisState.gameId == id }

    LaunchedEffect(id, game?.id, analysisState.state) {
        if (game != null && analysis == null && (analysisState.gameId != id || analysisState.state == AnalysisState.NONE || analysisState.state == AnalysisState.STALE)) {
            vm.startAnalysis(id)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Guided review") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            actions = { TextButton(onClick = onHome) { Text("Done") } },
        )
        when {
            game == null -> Text("Game not found", Modifier.padding(20.dp))
            analysis == null -> ReviewLoading(analysisState, onRetry = { vm.startAnalysis(id, force = true) })
            else -> GuidedReview(game.playerColor == "BLACK", analysis)
        }
    }
}

@Composable
private fun ReviewLoading(state: dev.justchess.app.analysis.AnalysisUiState, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Preparing your review", style = MaterialTheme.typography.headlineMedium)
        if (state.state == AnalysisState.RUNNING) {
            val total = state.total.coerceAtLeast(1)
            Text("Analyzing move ${state.completed.coerceAtMost(total)}/$total")
            LinearProgressIndicator(
                progress = { (state.completed.toFloat() / total).coerceIn(0f, 1f) },
                Modifier.fillMaxWidth(),
            )
        } else {
            Text(if (state.state == AnalysisState.FAILED) "Analysis unavailable." else "Load the cached engine analysis to start the guided review.")
            if (state.state == AnalysisState.FAILED) Button(onClick = onRetry) { Text("Retry analysis") }
        }
    }
}

@Composable
private fun GuidedReview(flipped: Boolean, analysis: GameAnalysis) {
    val moves = remember(analysis.gameId, analysis.plies.size) {
        // The analysis stores FENs, so deriving the move list from those is not
        // needed for display; UCI moves are enough to reconstruct each board.
        analysis.plies.map { it.playedUci }
    }
    val keyPlies = remember(analysis) {
        analysis.plies.filter { it.classification in KeyClassifications }.map { it.ply }
    }
    var currentPly by remember(analysis.gameId) { mutableIntStateOf(0) }
    var playing by remember(analysis.gameId) { androidx.compose.runtime.mutableStateOf(true) }
    var speed by remember(analysis.gameId) { mutableFloatStateOf(1f) }
    val keySet = remember(keyPlies) { keyPlies.toSet() }
    val snapshot = remember(analysis, currentPly) { snapshotAt(analysis, currentPly) }
    val moment = analysis.plies.firstOrNull { it.ply == currentPly }
    val isPlayerWhite = !flipped
    val isPlayerMove = moment != null && ((moment.ply % 2 == 1) == isPlayerWhite)
    val playedMove = moment?.let { parseMove(it.beforeFen, it.playedUci) }
    val bestMove = moment?.let { parseMove(it.beforeFen, it.bestUci) }
    val arrows = buildList {
        // Green = best/good; yellow = milder played mistake; red = Poor/Blunder (UX #2).
        val sameMove = playedMove != null && bestMove != null &&
            playedMove.toString() == bestMove.toString()
        if (sameMove) {
            playedMove?.let { add(BoardArrow(it.from, it.to, Color(0xCC4CAF50))) }
        } else {
            val playedColor = when (moment?.classification) {
                Classification.BLUNDER, Classification.POOR -> Color(0xCCD32F2F)
                else -> Color(0xCCE0A83A)
            }
            playedMove?.let { add(BoardArrow(it.from, it.to, playedColor)) }
            bestMove?.let { add(BoardArrow(it.from, it.to, Color(0xCC4CAF50))) }
        }
    }

    LaunchedEffect(playing, analysis.gameId, keyPlies, speed) {
        if (!playing) return@LaunchedEffect
        var cursor = currentPly
        while (cursor < moves.size) {
            val next = cursor + 1
            kotlinx.coroutines.delay(if (next in keySet) (500L / speed).toLong() else (220L / speed).toLong())
            cursor = next
            currentPly = cursor
            if (cursor in keySet || cursor >= moves.size) {
                playing = false
                break
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (keyPlies.isEmpty()) "Quiet replay" else "${keyPlies.size} key moment${if (keyPlies.size == 1) "" else "s"}",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChessBoard(
            pieces = snapshot.pieces,
            flipped = flipped,
            selected = null,
            legalTargets = emptySet(),
            lastFrom = snapshot.lastFrom,
            lastTo = snapshot.lastTo,
            checkSquare = snapshot.check,
            arrows = arrows,
            interactive = false,
            onSquare = {},
        )
        Text(
            if (currentPly == 0) "Starting position" else "Move ${moveNumber(currentPly)} · ${currentPly}/${moves.size}",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { currentPly = previousKey(currentPly, keyPlies); playing = false }) {
                Text("Previous")
            }
            Button(onClick = {
                if (currentPly >= moves.size) currentPly = 0
                playing = !playing
            }, modifier = Modifier.weight(1f)) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text(if (playing) "Pause" else "Play")
            }
            OutlinedButton(onClick = { currentPly = nextKey(currentPly, keyPlies, moves.size); playing = false }) {
                Text("Next")
            }
        }
        Text("Review speed · ${"%.1f".format(speed)}×", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = speed,
            onValueChange = { speed = it },
            valueRange = 0.5f..3f,
            steps = 4,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (moves.isNotEmpty()) {
            Slider(
                value = currentPly.toFloat(),
                onValueChange = { currentPly = it.toInt(); playing = false },
                valueRange = 0f..moves.size.toFloat(),
                steps = (moves.size - 1).coerceAtLeast(0),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (moment == null) {
            Text(
                if (currentPly == 0) "Press Play to skim the game. The review pauses on key moments." else "Quiet move — continuing the skim.",
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ReviewMomentCard(moment, isPlayerMove, currentPly)
        }
        if (!playing && moment != null && moment.multiPv.size > 1) MultiPvPanel(moment.multiPv)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MultiPvPanel(lines: List<dev.justchess.app.analysis.AnalysisLine>) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Free engine lines", style = MaterialTheme.typography.titleMedium)
            lines.take(3).forEachIndexed { index, line ->
                Text((index + 1).toString() + ". " + scoreText(line.score) + "  " + line.pv.take(5).joinToString("  ") { prettyUci(it) }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable
private fun ReviewMomentCard(moment: PlyAnalysis, isPlayerMove: Boolean, currentPly: Int) {
    val badge = moment.classification.name.lowercase().replaceFirstChar { it.uppercase() }
    val played = prettyUci(moment.playedUci)
    val best = prettyUci(moment.bestUci)
    val actor = if (isPlayerMove) "You" else "Stockfish"
    val coach = when (moment.classification) {
        Classification.BRILLIANT -> actor + " sacrificed material and the engine agrees: " + best + " is best."
        Classification.MISS -> actor + " missed the chance to punish the opponent's mistake. Better was " + best + " (" + lossText(moment.lossCp) + ")."
        Classification.GREAT -> "$actor played $played. Only move that keeps the advantage: $best."
        Classification.CONFUSING -> "$actor played $played. Better was $best (${lossText(moment.lossCp)})."
        Classification.POOR, Classification.BLUNDER -> "$actor played $played. Better was $best (${lossText(moment.lossCp)})."
        else -> "$actor played $played; the engine's top choice was $best."
    }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(badge, Modifier.background(classificationColor(moment.classification)).padding(horizontal = 9.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
            Text(coach, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Eval before → after: ${scoreText(moment.bestScore)} → ${scoreText(moment.playedScore)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Move ${moveNumber(currentPly)} · loss ${lossText(moment.lossCp)}", style = MaterialTheme.typography.bodySmall)
            Text("Green = best/good · Yellow = played (milder) · Red = played (Poor/Blunder)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun snapshotAt(analysis: GameAnalysis, ply: Int): ReviewSnapshot {
    val selected = analysis.plies.firstOrNull { it.ply == ply }
    val fen = selected?.afterFen ?: analysis.plies.firstOrNull()?.beforeFen ?: Board().fen
    val board = Board().apply { loadFromFen(fen) }
    val move = selected?.let { parseMove(it.beforeFen, it.playedUci) }
    val pieces = LinkedHashMap<Square, Piece>()
    for (square in Square.entries) {
        if (square != Square.NONE) board.getPiece(square).takeIf { it != Piece.NONE }?.let { pieces[square] = it }
    }
    val check = if (board.isKingAttacked) {
        val king = if (board.sideToMove == Side.WHITE) Piece.WHITE_KING else Piece.BLACK_KING
        board.getPieceLocation(king).firstOrNull()
    } else null
    return ReviewSnapshot(pieces, move?.from, move?.to, check)
}

private fun parseMove(fen: String, uci: String): Move? = runCatching {
    val board = Board().apply { loadFromFen(fen) }
    val wanted = Move(uci, board.sideToMove)
    board.legalMoves().firstOrNull { it.from == wanted.from && it.to == wanted.to && it.promotion == wanted.promotion }
}.getOrNull()

private fun previousKey(current: Int, keys: List<Int>): Int = keys.lastOrNull { it < current } ?: 0
private fun nextKey(current: Int, keys: List<Int>, total: Int): Int = keys.firstOrNull { it > current } ?: total
private fun moveNumber(ply: Int): String = if (ply % 2 == 1) "${(ply + 1) / 2}." else "${ply / 2}..."
private fun prettyUci(uci: String): String = if (uci.length >= 4) "${uci.substring(0, 2)}–${uci.substring(2, 4)}${uci.drop(4).takeIf { it.isNotEmpty() }?.let { "=$it" } ?: ""}" else uci
private fun lossText(cp: Int): String = if (cp < 100) "$cp cp" else "${"%.2f".format(cp / 100.0)} pawns"
private fun scoreText(score: AnalysisScore): String = score.mate?.let { if (it > 0) "M$it" else "−M${-it}" } ?: run {
    val cp = score.cp ?: 0
    "${if (cp >= 0) "+" else "−"}${"%.2f".format(kotlin.math.abs(cp) / 100.0)}"
}
private fun classificationColor(classification: Classification): Color = when (classification) {
    Classification.BRILLIANT -> Color(0xFF246B8F)
    Classification.MISS -> Color(0xFF8A4F9E)
    Classification.GREAT -> Color(0xFF347A52)
    Classification.CONFUSING -> Color(0xFF8B6A26)
    Classification.POOR -> Color(0xFF9A552F)
    Classification.BLUNDER -> Color(0xFF9D3B38)
    else -> Color(0xFF53606D)
}
