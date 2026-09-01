package dev.justchess.app.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import dev.justchess.app.ui.theme.BlackPieceFill
import dev.justchess.app.ui.theme.BlackPieceStroke
import dev.justchess.app.ui.theme.WhitePieceFill
import dev.justchess.app.ui.theme.WhitePieceStroke

fun DrawScope.drawChessPiece(piece: Piece, size: Float) {
    if (piece == Piece.NONE || piece.pieceType == null) return
    val white = piece.pieceSide == Side.WHITE
    val fill = if (white) WhitePieceFill else BlackPieceFill
    val stroke = if (white) WhitePieceStroke else BlackPieceStroke
    withTransform({
        scale(size / 100f, size / 100f, pivot = Offset.Zero)
    }) {
        val path = piecePath(piece.pieceType)
        drawPath(path, fill, style = Fill)
        drawPath(
            path,
            stroke,
            style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        extraMarks(piece.pieceType, stroke)
    }
}

private fun piecePath(type: PieceType): Path = when (type) {
    PieceType.PAWN -> Path().apply {
        moveTo(50f, 22f)
        cubicTo(58f, 22f, 64f, 28f, 64f, 36f)
        cubicTo(64f, 42f, 60f, 46f, 56f, 50f)
        cubicTo(66f, 54f, 72f, 62f, 72f, 72f)
        lineTo(28f, 72f)
        cubicTo(28f, 62f, 34f, 54f, 44f, 50f)
        cubicTo(40f, 46f, 36f, 42f, 36f, 36f)
        cubicTo(36f, 28f, 42f, 22f, 50f, 22f)
        close()
        addRect(Rect(22f, 74f, 78f, 84f))
        addRect(Rect(18f, 84f, 82f, 92f))
    }
    PieceType.ROOK -> Path().apply {
        moveTo(22f, 20f); lineTo(34f, 20f); lineTo(34f, 30f)
        lineTo(42f, 30f); lineTo(42f, 20f); lineTo(58f, 20f)
        lineTo(58f, 30f); lineTo(66f, 30f); lineTo(66f, 20f)
        lineTo(78f, 20f); lineTo(78f, 34f); lineTo(70f, 42f)
        lineTo(70f, 72f); lineTo(30f, 72f); lineTo(30f, 42f)
        lineTo(22f, 34f); close()
        addRect(Rect(20f, 74f, 80f, 84f))
        addRect(Rect(16f, 84f, 84f, 92f))
    }
    PieceType.KNIGHT -> Path().apply {
        moveTo(28f, 88f)
        lineTo(24f, 76f)
        cubicTo(24f, 58f, 28f, 48f, 40f, 38f)
        cubicTo(36f, 30f, 38f, 20f, 48f, 14f)
        cubicTo(52f, 12f, 54f, 16f, 52f, 20f)
        cubicTo(62f, 16f, 74f, 24f, 76f, 36f)
        cubicTo(78f, 48f, 72f, 58f, 66f, 64f)
        lineTo(70f, 76f)
        lineTo(72f, 88f)
        close()
    }
    PieceType.BISHOP -> Path().apply {
        moveTo(50f, 12f)
        cubicTo(46f, 18f, 46f, 22f, 48f, 26f)
        cubicTo(36f, 34f, 30f, 48f, 32f, 64f)
        lineTo(68f, 64f)
        cubicTo(70f, 48f, 64f, 34f, 52f, 26f)
        cubicTo(54f, 22f, 54f, 18f, 50f, 12f)
        close()
        addRect(Rect(30f, 66f, 70f, 74f))
        addRect(Rect(22f, 76f, 78f, 84f))
        addRect(Rect(18f, 84f, 82f, 92f))
    }
    PieceType.QUEEN -> Path().apply {
        moveTo(20f, 32f); lineTo(28f, 68f); lineTo(72f, 68f); lineTo(80f, 32f)
        lineTo(68f, 48f); lineTo(50f, 20f); lineTo(32f, 48f); close()
        addRect(Rect(26f, 70f, 74f, 78f))
        addRect(Rect(20f, 80f, 80f, 88f))
        addOval(Rect(16f, 24f, 26f, 34f))
        addOval(Rect(45f, 12f, 55f, 22f))
        addOval(Rect(74f, 24f, 84f, 34f))
        addOval(Rect(32f, 18f, 42f, 28f))
        addOval(Rect(58f, 18f, 68f, 28f))
    }
    PieceType.KING -> Path().apply {
        moveTo(28f, 40f)
        cubicTo(28f, 28f, 40f, 24f, 50f, 24f)
        cubicTo(60f, 24f, 72f, 28f, 72f, 40f)
        cubicTo(72f, 52f, 64f, 58f, 64f, 68f)
        lineTo(36f, 68f)
        cubicTo(36f, 58f, 28f, 52f, 28f, 40f)
        close()
        addRect(Rect(26f, 70f, 74f, 78f))
        addRect(Rect(20f, 80f, 80f, 88f))
    }
    else -> Path()
}

private fun DrawScope.extraMarks(type: PieceType, color: Color) {
    when (type) {
        PieceType.KING -> {
            drawLine(color, Offset(50f, 8f), Offset(50f, 22f), 4f, StrokeCap.Round)
            drawLine(color, Offset(43f, 15f), Offset(57f, 15f), 4f, StrokeCap.Round)
        }
        PieceType.BISHOP -> {
            drawLine(color, Offset(50f, 34f), Offset(50f, 54f), 3f, StrokeCap.Round)
            drawLine(color, Offset(44f, 44f), Offset(56f, 44f), 3f, StrokeCap.Round)
        }
        PieceType.KNIGHT -> {
            drawCircle(color, 3.2f, Offset(58f, 30f))
        }
        else -> Unit
    }
}
