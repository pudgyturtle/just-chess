package dev.justchess.app.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import dev.justchess.app.GameRecord
import dev.justchess.app.GameViewModel
import dev.justchess.app.data.Pgn
import dev.justchess.app.ui.board.ChessBoard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: GameViewModel, onOpen: (String) -> Unit) {
    val games by vm.history.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("History") })
        if (games.isEmpty()) {
            Text(
                "Finished games show up here as PGN. Play a game first.",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(games, key = { it.id }) { g ->
                    HistoryRow(g, onClick = { onOpen(g.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(g: GameRecord, onClick: () -> Unit) {
    val you = if (g.playerColor == "WHITE") "White" else "Black"
    val result = when (g.result) {
        "1-0" -> if (g.playerColor == "WHITE") "Win" else "Loss"
        "0-1" -> if (g.playerColor == "BLACK") "Win" else "Loss"
        "1/2-1/2" -> "Draw"
        else -> g.result
    }
    val opening = remember(g.pgn) { Pgn.openingSummary(g.pgn) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text("${g.dateIso}  ·  $result", fontWeight = FontWeight.Medium)
        Text(
            "vs Stockfish Elo ${g.engineElo} · you $you · ${g.timeControl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (opening.isNotEmpty()) {
            Text(
                opening,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(vm: GameViewModel, id: String, onBack: () -> Unit) {
    val games by vm.history.collectAsState()
    val game = games.firstOrNull { it.id == id }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val pgnText = game?.pgn.orEmpty()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-chess-pgn"),
    ) { uri: Uri? ->
        if (uri == null || pgnText.isEmpty()) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(pgnText.toByteArray(Charsets.UTF_8))
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Replay") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
        )
        if (game == null) {
            Text("Game not found", Modifier.padding(16.dp))
            return
        }
        val moves = remember(game.pgn) {
            runCatching { Pgn.parseMoves(game.pgn) }.getOrDefault(emptyList())
        }
        var ply by remember(game.id) { mutableIntStateOf(moves.size) }
        val snapshot = remember(ply, moves) { boardAt(moves, ply) }
        ChessBoard(
            pieces = snapshot.pieces,
            flipped = game.playerColor == "BLACK",
            selected = null,
            legalTargets = emptySet(),
            lastFrom = snapshot.lastFrom,
            lastTo = snapshot.lastTo,
            checkSquare = snapshot.check,
            interactive = false,
            onSquare = {},
        )
        Text(
            "${game.dateIso}  ${game.result}  vs Elo ${game.engineElo}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (moves.isNotEmpty()) {
            Slider(
                value = ply.toFloat(),
                onValueChange = { ply = it.toInt() },
                valueRange = 0f..moves.size.toFloat(),
                steps = (moves.size - 1).coerceAtLeast(0),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                "Ply $ply / ${moves.size}",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { exportLauncher.launch("just-chess-${game.id}.pgn") },
                modifier = Modifier.weight(1f),
            ) { Text("Export PGN") }
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(game.pgn))
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Just Chess PGN", game.pgn))
                },
                modifier = Modifier.weight(1f),
            ) { Text("Copy PGN") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            game.pgn,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private data class Snap(
    val pieces: Map<Square, Piece>,
    val lastFrom: Square?,
    val lastTo: Square?,
    val check: Square?,
)

private fun boardAt(moves: List<Move>, ply: Int): Snap {
    val board = Board()
    val n = ply.coerceIn(0, moves.size)
    var last: Move? = null
    for (i in 0 until n) {
        board.doMove(moves[i])
        last = moves[i]
    }
    val pieces = LinkedHashMap<Square, Piece>()
    for (sq in Square.entries) {
        if (sq == Square.NONE) continue
        val p = board.getPiece(sq)
        if (p != Piece.NONE) pieces[sq] = p
    }
    val king = if (board.isKingAttacked) {
        val k = if (board.sideToMove == Side.WHITE) Piece.WHITE_KING else Piece.BLACK_KING
        board.getPieceLocation(k).firstOrNull()
    } else null
    return Snap(pieces, last?.from, last?.to, king)
}
