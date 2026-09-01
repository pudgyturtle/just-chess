package dev.justchess.app

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square

/** Occupied squares on a chesslib board. Startpos must be 32 pieces. */
fun Board.occupiedPieces(): Map<Square, Piece> {
    val map = LinkedHashMap<Square, Piece>(32)
    for (i in 0 until 64) {
        val sq = Square.squareAt(i)
        if (sq == null || sq == Square.NONE) continue
        val p = getPiece(sq)
        if (p != Piece.NONE) map[sq] = p
    }
    return map
}
