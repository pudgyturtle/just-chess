package dev.justchess.app.ui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val WhiteBody = Color(0xFFF8F8F7)
private val WhiteShadow = Color(0xFFC6C8CC)
private val WhiteCream = Color(0xFFF0DCBA)
private val WhiteRecess = Color(0xFF16181C)
private val BlackBody = Color(0xFF303238)
private val BlackHigh = Color(0xFF5C6068)
private val BlackCream = Color(0xFFBAA88C)
private val BlackRecess = Color(0xFF0E0F12)

/**
 * Icon-set pieces: flat-plus, ~75% of the square, shared cream baseline.
 * Knight is the app-icon sprite. Rook has castle merlons; no top notches on bishop/queen.
 */
fun DrawScope.drawChessPiece(
    piece: Piece,
    size: Float,
    knightWhite: ImageBitmap,
    knightBlack: ImageBitmap,
) {
    if (piece == Piece.NONE || piece.pieceType == null) return
    val white = piece.pieceSide == Side.WHITE
    if (piece.pieceType == PieceType.KNIGHT) {
        drawKnight(if (white) knightWhite else knightBlack, size)
        return
    }
    val fill = if (white) WhiteBody else BlackBody
    val shade = if (white) WhiteShadow else BlackHigh
    val cream = if (white) WhiteCream else BlackCream
    val recess = if (white) WhiteRecess else BlackRecess
    withTransform({
        scale(size / 100f, size / 100f, pivot = Offset.Zero)
    }) {
        val body = pieceBody(piece.pieceType)
        body.addPath(baseTop())
        body.addPath(baseMid())
        drawPath(body, fill, style = Fill)
        clipRect(52f, 0f, 100f, 100f) {
            drawPath(body, shade, style = Fill)
        }
        drawRoundRect(
            color = cream,
            topLeft = Offset(14f, 88f),
            size = Size(72f, 5.5f),
            cornerRadius = CornerRadius(1.6f, 1.6f),
        )
        drawRoundRect(
            color = recess,
            topLeft = Offset(22f, 78.5f),
            size = Size(56f, 2.7f),
            cornerRadius = CornerRadius(1.2f, 1.2f),
        )
        drawRoundRect(
            color = recess,
            topLeft = Offset(20f, 87.2f),
            size = Size(60f, 1.8f),
            cornerRadius = CornerRadius(0.9f, 0.9f),
        )
    }
}

private fun DrawScope.drawKnight(bmp: ImageBitmap, size: Float) {
    val h = size * 0.85f
    val scale = h / bmp.height.toFloat()
    val w = bmp.width * scale
    val x = (size - w) / 2f
    val y = size * 0.935f - h
    drawImage(
        image = bmp,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bmp.width, bmp.height),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

private fun pieceBody(type: PieceType): Path = when (type) {
    PieceType.PAWN -> pawnBody()
    PieceType.ROOK -> rookBody()
    PieceType.BISHOP -> bishopBody()
    PieceType.QUEEN -> queenBody()
    PieceType.KING -> kingBody()
    else -> Path()
}

private fun pawnBody() = Path().apply {
    moveTo(34f, 34f)
    cubicTo(32f, 16f, 40f, 12f, 50f, 12f)
    cubicTo(60f, 12f, 68f, 16f, 66f, 34f)
    lineTo(62f, 46f)
    lineTo(60f, 56f)
    lineTo(62f, 72f)
    lineTo(38f, 72f)
    lineTo(40f, 56f)
    lineTo(38f, 46f)
    close()
}

private fun rookBody() = Path().apply {
    // 3 merlons / 2 crenels
    moveTo(26f, 12f)
    lineTo(38f, 12f)
    lineTo(38f, 24f)
    lineTo(44f, 24f)
    lineTo(44f, 12f)
    lineTo(56f, 12f)
    lineTo(56f, 24f)
    lineTo(62f, 24f)
    lineTo(62f, 12f)
    lineTo(74f, 12f)
    lineTo(74f, 24f)
    lineTo(76f, 30f)
    lineTo(68f, 72f)
    lineTo(32f, 72f)
    lineTo(24f, 30f)
    lineTo(26f, 24f)
    close()
}

private fun bishopBody() = Path().apply {
    val n = 28
    for (i in 0..n) {
        val a = Math.toRadians(155.0 + 230.0 * i / n)
        val x = (50 + 16 * cos(a)).toFloat()
        val y = (38 + 20 * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    lineTo(62f, 72f)
    lineTo(38f, 72f)
    close()
    addOval(Rect(50f - 7.2f, 12f - 7.2f, 50f + 7.2f, 12f + 7.2f))
}

private fun queenBody() = Path().apply {
    moveTo(16f, 40f)
    val xs = floatArrayOf(20f, 35f, 50f, 65f, 80f)
    val rs = floatArrayOf(9f, 10f, 12f, 10f, 9f)
    val tops = floatArrayOf(28f, 20f, 14f, 20f, 28f)
    for (i in xs.indices) {
        val x = xs[i]
        val r = rs[i]
        val top = tops[i]
        lineTo(x - r, 40f)
        cubicTo(x - r, top, x + r, top, x + r, 40f)
    }
    lineTo(84f, 40f)
    lineTo(76f, 52f)
    lineTo(70f, 64f)
    lineTo(68f, 72f)
    lineTo(32f, 72f)
    lineTo(30f, 64f)
    lineTo(24f, 52f)
    close()
}

private fun kingBody() = Path().apply {
    moveTo(44f, 8f)
    lineTo(56f, 8f)
    lineTo(56f, 16f)
    lineTo(68f, 16f)
    lineTo(68f, 26f)
    lineTo(56f, 26f)
    lineTo(56f, 38f)
    lineTo(70f, 42f)
    lineTo(74f, 54f)
    lineTo(70f, 64f)
    lineTo(68f, 72f)
    lineTo(32f, 72f)
    lineTo(30f, 64f)
    lineTo(26f, 54f)
    lineTo(30f, 42f)
    lineTo(44f, 38f)
    lineTo(44f, 26f)
    lineTo(32f, 26f)
    lineTo(32f, 16f)
    lineTo(44f, 16f)
    close()
}

private fun baseTop() = Path().apply {
    moveTo(26f, 72f)
    lineTo(74f, 72f)
    lineTo(76f, 80f)
    lineTo(24f, 80f)
    close()
}

private fun baseMid() = Path().apply {
    moveTo(18f, 80f)
    lineTo(82f, 80f)
    lineTo(84f, 88f)
    lineTo(16f, 88f)
    close()
}
