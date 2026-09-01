package dev.justchess.app

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec v1 full-rules edge cases. Mirrors GameViewModel: only [Board.legalMoves]
 * are accepted (illegal tap is dropped, never sent to the engine).
 */
class ChessRulesEdgeCasesTest {

    private fun playSan(board: Board, vararg sans: String) {
        val list = MoveList(board.fen)
        list.loadFromSan(sans.joinToString(" "))
        for (m in list) board.doMove(m)
    }

    /** Same match GameViewModel.playUserMove uses. */
    private fun appAccepts(board: Board, move: Move): Boolean {
        return board.legalMoves().any {
            it.from == move.from && it.to == move.to &&
                (move.promotion == Piece.NONE || it.promotion == move.promotion)
        }
    }

    private fun uciSet(board: Board): Set<String> =
        board.legalMoves().map { it.toString() }.toSet()

    @Test
    fun whiteAndBlackKingsideAndQueensideCastle() {
        val w = Board()
        w.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val wLegal = uciSet(w)
        assertTrue("white O-O", "e1g1" in wLegal)
        assertTrue("white O-O-O", "e1c1" in wLegal)

        w.doMove(w.legalMoves().first { it.toString() == "e1g1" })
        assertEquals(Piece.WHITE_KING, w.getPiece(Square.G1))
        assertEquals(Piece.WHITE_ROOK, w.getPiece(Square.F1))

        val b = Board()
        b.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1")
        val bLegal = uciSet(b)
        assertTrue("black O-O", "e8g8" in bLegal)
        assertTrue("black O-O-O", "e8c8" in bLegal)

        b.doMove(b.legalMoves().first { it.toString() == "e8c8" })
        assertEquals(Piece.BLACK_KING, b.getPiece(Square.C8))
        assertEquals(Piece.BLACK_ROOK, b.getPiece(Square.D8))
    }

    @Test
    fun cannotCastleThroughCheck() {
        // Black rook on f8 attacks f1; white O-O walks through check. O-O-O is free.
        val board = Board()
        board.loadFromFen("4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val legal = uciSet(board)
        assertFalse("O-O through check", "e1g1" in legal)
        assertTrue("O-O-O still legal", "e1c1" in legal)
    }

    @Test
    fun cannotCastleOutOfCheck() {
        val board = Board()
        board.loadFromFen("4k3/8/8/8/8/8/4r3/R3K2R w KQ - 0 1")
        assertTrue(board.isKingAttacked)
        val legal = uciSet(board)
        assertFalse("O-O while in check", "e1g1" in legal)
        assertFalse("O-O-O while in check", "e1c1" in legal)
    }

    @Test
    fun cannotCastleAfterRookMovedEvenIfItReturns() {
        val board = Board()
        board.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        board.doMove(Move(Square.H1, Square.H2))
        board.doMove(Move(Square.A8, Square.A7))
        board.doMove(Move(Square.H2, Square.H1))
        board.doMove(Move(Square.A7, Square.A8))
        val rights = board.fen.split(" ")[2]
        assertFalse("kingside right lost after rook moved", rights.contains("K"))
        assertTrue("queenside still available", rights.contains("Q"))
        val legal = uciSet(board)
        assertFalse("e1g1" in legal)
        assertTrue("e1c1" in legal)
    }

    @Test
    fun cannotCastleAfterKingMovedEvenIfItReturns() {
        val board = Board()
        board.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        board.doMove(Move(Square.E1, Square.E2))
        board.doMove(Move(Square.A8, Square.A7))
        board.doMove(Move(Square.E2, Square.E1))
        board.doMove(Move(Square.A7, Square.A8))
        val rights = board.fen.split(" ")[2]
        assertFalse(rights.contains("K"))
        assertFalse(rights.contains("Q"))
        val legal = uciSet(board)
        assertFalse("e1g1" in legal)
        assertFalse("e1c1" in legal)
    }

    @Test
    fun enPassantExpiresNextMoveIfNotTaken() {
        val board = Board()
        board.loadFromFen("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3")
        assertTrue("ep available this ply", "e5f6" in uciSet(board))
        board.doMove(Move(Square.H2, Square.H3))
        assertEquals("-", board.fen.split(" ")[3])
        board.doMove(Move(Square.H7, Square.H6))
        assertFalse("white ep expired next move", "e5f6" in uciSet(board))
    }

    @Test
    fun enPassantCaptureRemovesPawnFromFifth() {
        val board = Board()
        playSan(board, "e4", "a6", "e5", "d5")
        assertTrue("e5d6" in uciSet(board))
        board.doMove(board.legalMoves().first { it.toString() == "e5d6" })
        assertEquals(Piece.NONE, board.getPiece(Square.D5))
        assertEquals(Piece.WHITE_PAWN, board.getPiece(Square.D6))
    }

    @Test
    fun promotionToRookBishopKnightAndQueen() {
        val start = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val expected = mapOf(
            PieceType.QUEEN to Piece.WHITE_QUEEN,
            PieceType.ROOK to Piece.WHITE_ROOK,
            PieceType.BISHOP to Piece.WHITE_BISHOP,
            PieceType.KNIGHT to Piece.WHITE_KNIGHT,
        )
        for ((type, piece) in expected) {
            val board = Board()
            board.loadFromFen(start)
            val move = board.legalMoves().first {
                it.from == Square.A7 && it.to == Square.A8 && it.promotion.pieceType == type
            }
            assertTrue(appAccepts(board, move))
            board.doMove(move)
            assertEquals(piece, board.getPiece(Square.A8))
            assertEquals(Piece.NONE, board.getPiece(Square.A7))
        }
    }

    @Test
    fun blackPromotionFourChoices() {
        val board = Board()
        board.loadFromFen("4k3/8/8/8/8/8/p7/4K3 b - - 0 1")
        val promos = board.legalMoves().filter { it.from == Square.A2 && it.to == Square.A1 }
        assertEquals(4, promos.size)
        assertEquals(
            setOf(Piece.BLACK_QUEEN, Piece.BLACK_ROOK, Piece.BLACK_BISHOP, Piece.BLACK_KNIGHT),
            promos.map { it.promotion }.toSet(),
        )
    }

    @Test
    fun stalemateIsDrawNotMate() {
        val board = Board()
        board.loadFromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(board.isStaleMate)
        assertFalse(board.isMated)
        assertFalse(board.isKingAttacked)
        assertTrue(board.legalMoves().isEmpty())
        assertTrue(board.isDraw)
    }

    @Test
    fun insufficientMaterialKingAndKnight() {
        val board = Board()
        board.loadFromFen("8/8/8/4k3/8/4K3/7N/8 w - - 0 1")
        assertTrue(board.isInsufficientMaterial)
        assertTrue(board.isDraw)
    }

    @Test
    fun insufficientMaterialSameColorBishops() {
        // c1 and f8 are both dark squares.
        val same = Board()
        same.loadFromFen("k4b2/8/8/8/8/8/8/K1B5 w - - 0 1")
        assertTrue(
            "K+B vs K+B same color should be insufficient",
            same.isInsufficientMaterial,
        )

        // c1 dark vs h1 light: opposite-color bishops. Mate is possible (helpmate),
        // so chesslib is allowed not to call this insufficient. Spec only requires same-color.
        val opposite = Board()
        opposite.loadFromFen("k7/8/8/8/8/8/8/K1B4b w - - 0 1")
        assertFalse(
            "opposite-color K+B vs K+B is not a forced dead position",
            opposite.isInsufficientMaterial,
        )
    }

    @Test
    fun twoKnightsVsKingIsNotDeadPosition() {
        // Mate exists with help, so this must not auto-draw via insufficient material.
        val board = Board()
        board.loadFromFen("k7/8/8/8/8/8/8/K2N1N2 w - - 0 1")
        assertFalse(board.isInsufficientMaterial)
    }

    @Test
    fun kingAndRookIsSufficient() {
        val board = Board()
        board.loadFromFen("8/8/8/4k3/8/4K3/7R/8 w - - 0 1")
        assertFalse(board.isInsufficientMaterial)
    }

    @Test
    fun fiftyMoveResetsOnPawnMoveOrCapture() {
        val pawnReset = Board()
        pawnReset.loadFromFen("k7/8/8/8/8/8/P7/K7 w - - 99 50")
        pawnReset.doMove(Move(Square.A2, Square.A3))
        assertEquals(0, pawnReset.halfMoveCounter)
        assertFalse(pawnReset.isDraw)

        val captureReset = Board()
        captureReset.loadFromFen("k6r/8/8/8/8/8/8/K6R w - - 99 50")
        val cap = captureReset.legalMoves().first { it.from == Square.H1 && it.to == Square.H8 }
        captureReset.doMove(cap)
        assertEquals(0, captureReset.halfMoveCounter)
    }

    @Test
    fun illegalMovesRejectedByAppLegalFilter() {
        val board = Board()
        assertFalse("two-square pawn leap from e2 to e5", appAccepts(board, Move(Square.E2, Square.E5)))
        assertFalse("knight does not jump like that", appAccepts(board, Move(Square.G1, Square.G3)))
        assertFalse("capture own piece", appAccepts(board, Move(Square.A1, Square.A2)))
        assertTrue("legal pawn double", appAccepts(board, Move(Square.E2, Square.E4)))

        val check = Board()
        check.loadFromFen("4k3/4r3/8/8/8/8/8/4K3 w - - 0 1")
        assertFalse("king into/staying in check", appAccepts(check, Move(Square.E1, Square.E2)))
        assertTrue("king flees", appAccepts(check, Move(Square.E1, Square.D1)))
    }

    @Test
    fun foolsmateIsCheckmate() {
        val board = Board()
        playSan(board, "f3", "e5", "g4", "Qh4")
        assertTrue(board.isMated)
        assertTrue(board.isKingAttacked)
        assertTrue(board.legalMoves().isEmpty())
        assertFalse(board.isStaleMate)
    }
}
