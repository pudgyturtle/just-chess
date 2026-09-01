package dev.justchess.app.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
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
import dev.justchess.app.ui.theme.WhitePieceFill
import dev.justchess.app.ui.theme.WhitePieceStroke

/**
 * Classic 2D Staunton silhouettes traced from Mark's reference sheet.
 * White: fill + dark outline. Black: solid fill, no extra stroke.
 * Knight is a left-facing horse profile (S-curve neck, ear, snout) — not a blob.
 */
fun DrawScope.drawChessPiece(piece: Piece, size: Float) {
    if (piece == Piece.NONE || piece.pieceType == null) return
    val white = piece.pieceSide == Side.WHITE
    val fill = if (white) WhitePieceFill else BlackPieceFill
    withTransform({
        scale(size / 100f, size / 100f, pivot = Offset.Zero)
    }) {
        val path = piecePath(piece.pieceType)
        drawPath(path, fill, style = Fill)
        if (white) {
            drawPath(
                path,
                WhitePieceStroke,
                style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun piecePath(type: PieceType): Path = when (type) {
    PieceType.PAWN -> pathFromXy(PAWN_XY)
    PieceType.ROOK -> pathFromXy(ROOK_XY)
    PieceType.KNIGHT -> pathFromXy(KNIGHT_XY)
    PieceType.BISHOP -> pathFromXy(BISHOP_XY)
    PieceType.QUEEN -> pathFromXy(QUEEN_XY)
    PieceType.KING -> pathFromXy(KING_XY)
    else -> Path()
}

private fun pathFromXy(xy: FloatArray): Path {
    val p = Path().apply { fillType = PathFillType.EvenOdd }
    if (xy.size < 4) return p
    p.moveTo(xy[0], xy[1])
    var i = 2
    while (i + 1 < xy.size) {
        p.lineTo(xy[i], xy[i + 1])
        i += 2
    }
    p.close()
    return p
}

private val PAWN_XY = floatArrayOf(
        44.82f, 43.09f,
        41.66f, 45.39f,
        39.93f, 49.13f,
        40.51f, 54.88f,
        44.25f, 60.64f,
        44.25f, 61.50f,
        40.51f, 64.37f,
        40.51f, 65.81f,
        41.37f, 66.96f,
        44.25f, 67.54f,
        44.25f, 70.41f,
        42.81f, 74.44f,
        39.93f, 76.45f,
        39.65f, 78.76f,
        36.19f, 80.77f,
        34.47f, 83.36f,
        34.47f, 85.66f,
        35.62f, 87.67f,
        34.47f, 89.97f,
        34.47f, 92.27f,
        35.91f, 93.71f,
        63.81f, 93.71f,
        65.24f, 91.99f,
        65.24f, 90.26f,
        63.81f, 88.54f,
        64.96f, 85.66f,
        64.67f, 82.49f,
        59.78f, 78.76f,
        59.49f, 76.45f,
        56.90f, 74.73f,
        55.18f, 70.13f,
        55.18f, 67.54f,
        58.34f, 66.96f,
        59.49f, 65.53f,
        59.20f, 64.09f,
        55.46f, 61.50f,
        59.20f, 54.02f,
        58.92f, 47.40f,
        56.33f, 44.24f,
        52.59f, 42.52f,
    )

private val ROOK_XY = floatArrayOf(
        39.07f, 30.72f,
        38.21f, 31.59f,
        38.21f, 45.10f,
        39.93f, 48.84f,
        41.37f, 49.71f,
        41.95f, 58.62f,
        39.36f, 70.99f,
        36.77f, 73.58f,
        36.77f, 79.33f,
        33.03f, 83.93f,
        34.47f, 88.54f,
        33.03f, 89.69f,
        32.74f, 91.99f,
        34.76f, 93.71f,
        65.53f, 93.71f,
        66.97f, 92.27f,
        66.97f, 90.26f,
        65.24f, 88.54f,
        66.68f, 83.93f,
        62.94f, 79.04f,
        62.94f, 73.58f,
        59.78f, 69.26f,
        57.77f, 57.76f,
        58.05f, 50.57f,
        61.79f, 44.82f,
        61.79f, 32.16f,
        60.64f, 30.72f,
        56.62f, 30.43f,
        56.04f, 33.89f,
        54.89f, 34.46f,
        53.74f, 30.43f,
        46.26f, 30.43f,
        45.69f, 33.89f,
        44.54f, 34.46f,
        43.96f, 30.72f,
    )

private val KNIGHT_XY = floatArrayOf(
        48.99f, 20.94f,
        54.46f, 24.39f,
        59.06f, 28.71f,
        62.51f, 34.75f,
        64.24f, 39.93f,
        65.39f, 49.13f,
        65.10f, 63.51f,
        64.24f, 70.70f,
        62.22f, 72.14f,
        62.80f, 73.87f,
        61.36f, 76.45f,
        61.65f, 78.76f,
        64.81f, 81.06f,
        66.25f, 83.07f,
        66.25f, 85.95f,
        65.10f, 88.54f,
        66.54f, 89.69f,
        66.83f, 91.70f,
        64.24f, 93.71f,
        35.47f, 93.71f,
        33.46f, 91.99f,
        33.46f, 90.26f,
        35.19f, 88.54f,
        33.75f, 85.37f,
        34.04f, 82.49f,
        38.35f, 78.18f,
        38.35f, 76.45f,
        37.20f, 74.15f,
        37.49f, 72.43f,
        34.32f, 71.28f,
        47.84f, 48.84f,
        47.27f, 47.12f,
        42.38f, 47.40f,
        40.94f, 49.13f,
        37.20f, 49.99f,
        35.19f, 48.56f,
        35.19f, 47.40f,
        33.75f, 47.69f,
        33.46f, 42.52f,
        38.64f, 35.61f,
        42.09f, 26.70f,
        43.24f, 25.83f,
        45.83f, 25.83f,
        46.98f, 21.52f,
    )

private val BISHOP_XY = floatArrayOf(
        46.84f, 20.08f,
        45.40f, 21.81f,
        45.97f, 24.11f,
        41.37f, 29.28f,
        40.22f, 33.02f,
        40.51f, 37.91f,
        43.10f, 43.38f,
        43.38f, 46.83f,
        41.08f, 47.98f,
        39.36f, 51.72f,
        43.96f, 54.31f,
        43.38f, 63.22f,
        40.51f, 70.99f,
        38.21f, 73.29f,
        38.49f, 76.74f,
        34.18f, 82.49f,
        33.61f, 85.95f,
        34.76f, 88.54f,
        33.03f, 91.70f,
        34.76f, 93.71f,
        64.67f, 93.71f,
        66.68f, 91.41f,
        64.96f, 88.82f,
        65.82f, 83.65f,
        61.22f, 77.03f,
        61.51f, 73.58f,
        58.05f, 68.98f,
        55.75f, 59.48f,
        56.04f, 53.73f,
        58.92f, 53.44f,
        60.35f, 51.14f,
        58.63f, 48.27f,
        56.04f, 46.83f,
        59.20f, 38.20f,
        59.20f, 31.59f,
        53.74f, 24.39f,
        53.74f, 20.66f,
    )

private val QUEEN_XY = floatArrayOf(
        48.56f, 14.04f,
        46.55f, 16.05f,
        47.12f, 18.93f,
        38.49f, 24.68f,
        42.52f, 29.57f,
        42.81f, 34.75f,
        41.66f, 36.76f,
        42.23f, 38.78f,
        39.93f, 39.93f,
        38.21f, 44.53f,
        39.65f, 46.54f,
        43.38f, 46.83f,
        43.96f, 53.73f,
        41.95f, 64.09f,
        39.36f, 70.13f,
        36.77f, 72.72f,
        37.34f, 77.03f,
        32.74f, 83.36f,
        34.18f, 88.54f,
        32.45f, 91.70f,
        34.47f, 93.71f,
        64.67f, 93.71f,
        67.26f, 91.12f,
        65.53f, 88.25f,
        66.97f, 83.07f,
        62.37f, 76.74f,
        62.66f, 72.14f,
        60.07f, 69.55f,
        58.05f, 64.95f,
        55.75f, 53.73f,
        56.04f, 46.54f,
        60.07f, 46.25f,
        61.22f, 45.10f,
        60.07f, 40.21f,
        57.48f, 38.49f,
        58.05f, 36.47f,
        56.62f, 33.02f,
        57.48f, 28.42f,
        61.22f, 24.39f,
        59.20f, 22.38f,
        56.62f, 22.38f,
        52.88f, 18.93f,
        52.88f, 15.48f,
        51.73f, 14.33f,
    )

private val KING_XY = floatArrayOf(
        48.42f, 8.00f,
        48.13f, 10.59f,
        45.25f, 11.16f,
        45.25f, 13.75f,
        47.27f, 14.33f,
        46.12f, 17.49f,
        38.06f, 20.37f,
        42.38f, 34.46f,
        42.38f, 38.49f,
        40.08f, 39.93f,
        38.64f, 45.10f,
        43.82f, 47.12f,
        43.82f, 56.03f,
        41.23f, 66.39f,
        36.91f, 72.43f,
        37.20f, 77.32f,
        32.89f, 82.78f,
        34.04f, 88.54f,
        32.60f, 91.70f,
        34.61f, 93.71f,
        64.81f, 93.71f,
        67.11f, 91.99f,
        65.68f, 88.54f,
        67.11f, 83.93f,
        62.51f, 77.03f,
        63.09f, 72.43f,
        61.07f, 70.99f,
        58.48f, 65.81f,
        55.90f, 54.60f,
        56.18f, 46.83f,
        60.21f, 46.25f,
        61.36f, 44.82f,
        59.92f, 39.93f,
        57.62f, 38.49f,
        57.62f, 34.17f,
        61.94f, 20.37f,
        54.17f, 17.78f,
        52.73f, 14.33f,
        54.46f, 13.75f,
        54.46f, 11.16f,
        51.87f, 10.88f,
        51.29f, 8.00f,
    )
