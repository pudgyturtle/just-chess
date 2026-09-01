package dev.mulvey.justchess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import dev.mulvey.justchess.ui.theme.BoardCheck
import dev.mulvey.justchess.ui.theme.BoardDark
import dev.mulvey.justchess.ui.theme.BoardHighlight
import dev.mulvey.justchess.ui.theme.BoardLegal
import dev.mulvey.justchess.ui.theme.BoardLight
import dev.mulvey.justchess.ui.theme.BoardSelected
import dev.mulvey.justchess.ui.theme.CoordColor

@Composable
fun ChessBoard(
    pieces: Map<Square, Piece>,
    flipped: Boolean,
    selected: Square?,
    legalTargets: Set<Square>,
    lastFrom: Square?,
    lastTo: Square?,
    checkSquare: Square?,
    interactive: Boolean,
    onSquare: (Square) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val coordStyle = TextStyle(color = CoordColor, fontSize = 11.sp)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(1f)
            .pointerInput(flipped, interactive, pieces, selected) {
                detectTapGestures { offset ->
                    if (!interactive) return@detectTapGestures
                    val sqSize = size.width / 8f
                    val col = (offset.x / sqSize).toInt().coerceIn(0, 7)
                    val row = (offset.y / sqSize).toInt().coerceIn(0, 7)
                    onSquare(displayToSquare(col, row, flipped))
                }
            },
    ) {
        val sq = size.minDimension / 8f
        for (rank in 0..7) {
            for (file in 0..7) {
                val square = Square.squareAt(rank * 8 + file)
                val col = if (flipped) 7 - file else file
                val row = if (flipped) rank else 7 - rank
                val light = (file + rank) % 2 == 1
                val origin = Offset(col * sq, row * sq)
                drawRect(if (light) BoardLight else BoardDark, origin, Size(sq, sq))
                if (square == lastFrom || square == lastTo) drawRect(BoardHighlight, origin, Size(sq, sq))
                if (square == selected) drawRect(BoardSelected, origin, Size(sq, sq))
                if (square == checkSquare) drawRect(BoardCheck, origin, Size(sq, sq))
            }
        }
        for (target in legalTargets) {
            val file = target.file.ordinal
            val rank = target.rank.ordinal
            val col = if (flipped) 7 - file else file
            val row = if (flipped) rank else 7 - rank
            val cx = col * sq + sq / 2
            val cy = row * sq + sq / 2
            val occupied = pieces[target] != null
            if (occupied) {
                drawCircle(BoardLegal, sq * 0.42f, Offset(cx, cy), style = Stroke(width = sq * 0.08f))
            } else {
                drawCircle(BoardLegal, sq * 0.14f, Offset(cx, cy))
            }
        }
        for ((square, piece) in pieces) {
            val file = square.file.ordinal
            val rank = square.rank.ordinal
            val col = if (flipped) 7 - file else file
            val row = if (flipped) rank else 7 - rank
            val pad = sq * 0.08f
            translate(col * sq + pad, row * sq + pad) {
                drawChessPiece(piece, sq - pad * 2)
            }
        }
        val files = "abcdefgh"
        for (i in 0..7) {
            val fileChar = if (flipped) files[7 - i] else files[i]
            val layout = measurer.measure(fileChar.toString(), coordStyle)
            drawText(layout, topLeft = Offset(i * sq + 4f, size.minDimension - layout.size.height - 3f))
        }
        for (i in 0..7) {
            val rankChar = if (flipped) (i + 1).toString() else (8 - i).toString()
            val layout = measurer.measure(rankChar, coordStyle)
            drawText(layout, topLeft = Offset(4f, i * sq + 3f))
        }
    }
}

fun displayToSquare(col: Int, row: Int, flipped: Boolean): Square {
    val file = if (flipped) 7 - col else col
    val rank = if (flipped) row else 7 - row
    return Square.squareAt(rank * 8 + file)
}
