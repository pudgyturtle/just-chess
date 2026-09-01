package dev.justchess.app.ui.play

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import dev.justchess.app.ColorChoice
import dev.justchess.app.EngineLevels
import dev.justchess.app.GameViewModel
import dev.justchess.app.TimeControl
import dev.justchess.app.ui.board.ChessBoard
import dev.justchess.app.ui.theme.ClockActive
import dev.justchess.app.ui.theme.ClockIdle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(vm: GameViewModel) {
    val state by vm.ui.collectAsState()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> vm.onHostPause()
                Lifecycle.Event.ON_RESUME -> vm.onHostResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var confirmResign by remember { mutableStateOf(false) }
    var setupColor by remember { mutableStateOf(ColorChoice.WHITE) }
    var setupTime by remember { mutableStateOf(TimeControl.TEN) }
    var setupElo by remember { mutableStateOf(EngineLevels.DEFAULT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Just Chess", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            actions = {
                TextButton(onClick = {
                    setupColor = runCatching { ColorChoice.valueOf(state.profile.lastColor) }
                        .getOrDefault(ColorChoice.WHITE)
                    setupTime = state.timeControl
                    setupElo = state.engineElo
                    vm.openNewGameSheet()
                }) { Text("New") }
            },
        )

        val topIsPlayer = state.playerSide == Side.BLACK
        ClockRow(
            name = if (topIsPlayer) state.profile.name else "Stockfish",
            subtitle = if (topIsPlayer) {
                "You · ${state.profile.ratingLabel()}"
            } else {
                "engine Elo ${state.engineElo}"
            },
            ms = if (topIsPlayer) {
                if (state.playerSide == Side.WHITE) state.whiteMs else state.blackMs
            } else {
                if (state.playerSide == Side.WHITE) state.blackMs else state.whiteMs
            },
            unlimited = state.timeControl.isUnlimited,
            active = state.inProgress && !state.gameOver &&
                state.sideToMove == if (topIsPlayer) state.playerSide else state.playerSide.flip(),
        )

        ChessBoard(
            pieces = state.pieces,
            flipped = state.playerSide == Side.BLACK,
            selected = state.selected,
            legalTargets = state.legalTargets,
            lastFrom = state.lastFrom,
            lastTo = state.lastTo,
            checkSquare = state.kingSquare,
            interactive = state.inProgress && !state.gameOver && !state.engineThinking,
            onSquare = { sq: Square ->
                val before = state.selected
                vm.onSquareTapped(sq)
                val own = state.pieces[sq]?.pieceSide == state.playerSide
                if (before != null && sq !in state.legalTargets && !own) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            },
        )

        ClockRow(
            name = if (topIsPlayer) "Stockfish" else state.profile.name,
            subtitle = if (topIsPlayer) {
                "engine Elo ${state.engineElo}"
            } else {
                "You · ${state.profile.ratingLabel()}"
            },
            ms = if (topIsPlayer) {
                if (state.playerSide == Side.WHITE) state.blackMs else state.whiteMs
            } else {
                if (state.playerSide == Side.WHITE) state.whiteMs else state.blackMs
            },
            unlimited = state.timeControl.isUnlimited,
            active = state.inProgress && !state.gameOver &&
                state.sideToMove == if (topIsPlayer) state.playerSide.flip() else state.playerSide,
        )

        MoveStrip(state.sans)
        if (state.engineThinking) {
            Text(
                "Engine thinking…",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.gameOver) {
            Text(
                state.resultHeadline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                state.resultDetail,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!state.engineAvailable) {
            Text(
                "Stockfish binary not found on this ABI (arm64-v8a required).",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { vm.takeback() },
                enabled = state.canTakeback,
                modifier = Modifier.weight(1f),
            ) { Text("Takeback") }
            OutlinedButton(
                onClick = { confirmResign = true },
                enabled = state.inProgress && !state.gameOver,
                modifier = Modifier.weight(1f),
            ) { Text("Resign") }
        }
        Text(
            "Ratings are Stockfish engine strength, not Chess.com or FIDE ratings.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (state.showNewGame) {
        ModalBottomSheet(
            onDismissRequest = { if (state.inProgress || state.gameOver) vm.dismissNewGame() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("New game", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Play as", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorChoice.entries.forEach { c ->
                        FilterChip(
                            selected = setupColor == c,
                            onClick = { setupColor = c },
                            label = { Text(c.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Time control", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeControl.entries.forEach { tc ->
                        FilterChip(
                            selected = setupTime == tc,
                            onClick = { setupTime = tc },
                            label = { Text(tc.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Engine Elo", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EngineLevels.all.forEach { elo ->
                        FilterChip(
                            selected = setupElo == elo,
                            onClick = { setupElo = elo },
                            label = { Text(elo.toString()) },
                        )
                    }
                }
                Text(
                    "These numbers are Stockfish UCI_Elo, not Chess.com ratings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Button(
                    onClick = {
                        vm.dismissNewGame()
                        vm.startNewGame(setupColor, setupTime, setupElo)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                ) { Text("Start") }
            }
        }
    }

    state.pendingPromotion?.let {
        AlertDialog(
            onDismissRequest = { vm.cancelPromotion() },
            title = { Text("Promote to") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PieceType.QUEEN to "Q",
                        PieceType.ROOK to "R",
                        PieceType.BISHOP to "B",
                        PieceType.KNIGHT to "N",
                    ).forEach { (type, label) ->
                        Button(onClick = { vm.promote(type) }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.cancelPromotion() }) { Text("Cancel") } },
        )
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text("Resign?") },
            text = { Text("This counts as a loss.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmResign = false
                    vm.resign()
                }) { Text("Resign") }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun ClockRow(
    name: String,
    subtitle: String,
    ms: Long,
    unlimited: Boolean,
    active: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = if (unlimited) "∞" else formatClock(ms),
            color = if (active) ClockActive else ClockIdle,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MoveStrip(sans: List<String>) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sans.isEmpty()) {
            Text("Moves", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            sans.chunked(2).forEachIndexed { i, pair ->
                val body = buildString {
                    append(i + 1).append('.')
                    append(pair[0])
                    if (pair.size > 1) append(' ').append(pair[1])
                }
                Text(body, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}
