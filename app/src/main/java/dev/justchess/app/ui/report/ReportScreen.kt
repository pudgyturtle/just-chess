package dev.justchess.app.ui.report

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.justchess.app.GameRecord
import dev.justchess.app.GameViewModel
import dev.justchess.app.analysis.AnalysisState
import dev.justchess.app.analysis.Classification
import dev.justchess.app.analysis.GameAnalysis
import dev.justchess.app.analysis.PlyAnalysis
import dev.justchess.app.analysis.MoveClassifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    vm: GameViewModel,
    id: String,
    onBack: () -> Unit,
    onAnalyzeGame: () -> Unit,
) {
    val games by vm.history.collectAsState()
    val analysisState by vm.analysis.collectAsState()
    val game = games.firstOrNull { it.id == id }

    LaunchedEffect(game?.id) {
        if (game != null && (analysisState.gameId != id || analysisState.state == AnalysisState.NONE || analysisState.state == AnalysisState.STALE)) {
            vm.startAnalysis(id)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Game report") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
        )
        if (game == null) {
            Text("Game not found", Modifier.padding(20.dp))
            return
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            Text(gameTitle(game), style = MaterialTheme.typography.titleLarge)
            Text(
                "${game.dateIso} · ${game.timeControl} · Stockfish Elo ${game.engineElo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                analysisState.gameId != id -> {
                    Text("Preparing engine analysis…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                analysisState.state == AnalysisState.RUNNING -> {
                    val total = analysisState.total.coerceAtLeast(1)
                    Text("Analyzing move ${analysisState.completed.coerceAtMost(total)}/$total")
                    LinearProgressIndicator(
                        progress = { (analysisState.completed.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = vm::cancelAnalysis) { Text("Cancel") }
                }
                analysisState.state == AnalysisState.FAILED || analysisState.state == AnalysisState.STALE -> {
                    Text(
                        "Analysis unavailable: ${analysisState.error ?: "try again"}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { vm.startAnalysis(id) }) { Text("Analyze Game") }
                }
                analysisState.state == AnalysisState.COMPLETE -> {
                    val result = analysisState.cache?.analysis
                    if (result == null) {
                        Text("Analysis result is unavailable.")
                    } else {
                        ReportSummary(game, result, onAnalyzeGame, onReanalyze = { vm.startAnalysis(id, force = true) })
                    }
                }
                else -> {
                    Button(onClick = { vm.startAnalysis(id) }) { Text("Analyze Game") }
                }
            }
            Text(
                "Classifications use on-device Stockfish and a transparent centipawn-loss model; they are not Chess.com accuracy or labels.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReportSummary(game: GameRecord, result: GameAnalysis, onAnalyzeGame: () -> Unit, onReanalyze: () -> Unit) {
    val playerIsWhite = game.playerColor == "WHITE"
    val playerMoves = result.plies.filter { (it.ply % 2 == 1) == playerIsWhite }
    val counts = Classification.entries.associateWith { classification ->
        playerMoves.count { it.classification == classification }
    }
    val playerMeanCpl = playerMoves.map { it.lossCp }.average().takeIf { it.isFinite() } ?: 0.0
    val playerAccuracy = MoveClassifier.accuracy(playerMeanCpl)
    val bestCount = (counts[Classification.BEST] ?: 0) + (counts[Classification.BOOK] ?: 0)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Engine accuracy", style = MaterialTheme.typography.labelLarge)
            Text("${playerAccuracy.toInt()}%", style = MaterialTheme.typography.headlineLarge)
            Text("Based on your mean ${playerMeanCpl.toInt()} centipawn loss", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("Your moves", style = MaterialTheme.typography.titleMedium)
    CountsGrid(bestCount, counts)
    Text("Evaluation over the game", style = MaterialTheme.typography.titleMedium)
    EvalSparkline(result.plies, playerIsWhite)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAnalyzeGame, modifier = Modifier.weight(1f)) { Text("Analyze Game") }
        OutlinedButton(onClick = onReanalyze) {
            Text("Re-analyze")
        }
    }
    TextButton(onClick = onAnalyzeGame) { Text("Start guided review") }
}

@Composable
private fun CountsGrid(best: Int, counts: Map<Classification, Int>) {
    val items = listOf(
        "Best" to best,
        "Great" to (counts[Classification.GREAT] ?: 0),
        "Good" to (counts[Classification.GOOD] ?: 0),
        "Confusing" to (counts[Classification.CONFUSING] ?: 0),
        "Poor" to (counts[Classification.POOR] ?: 0),
        "Blunder" to (counts[Classification.BLUNDER] ?: 0),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, count) ->
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(count.toString(), style = MaterialTheme.typography.titleLarge)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EvalSparkline(plies: List<PlyAnalysis>, playerIsWhite: Boolean) {
    val values = plies.map { ply ->
        val whiteScore = if ((ply.ply % 2 == 1) == playerIsWhite) {
            ply.playedScore.comparable()
        } else {
            -ply.playedScore.comparable()
        }
        whiteScore.coerceIn(-1000, 1000).toFloat()
    }
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(76.dp),
    ) {
        val center = size.height / 2f
        drawLine(outline, Offset(0f, center), Offset(size.width, center), 1f)
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * size.width / (values.size - 1).toFloat()
            val y = center - value / 1000f * (size.height / 2f - 4f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(width = 3f))
    }
}

private fun gameTitle(game: GameRecord): String {
    val outcome = when (game.result) {
        "1-0" -> if (game.playerColor == "WHITE") "Win" else "Loss"
        "0-1" -> if (game.playerColor == "BLACK") "Win" else "Loss"
        "1/2-1/2" -> "Draw"
        else -> game.result
    }
    return "$outcome · ${if (game.playerColor == "WHITE") "White" else "Black"}"
}
