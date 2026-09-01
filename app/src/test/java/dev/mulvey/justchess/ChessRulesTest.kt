package dev.mulvey.justchess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
import dev.mulvey.justchess.data.Pgn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessRulesTest {

    private fun perft(board: Board, depth: Int): Long {
        val moves = board.legalMoves()
        if (depth == 1) return moves.size.toLong()
        var n = 0L
        for (m in moves) {
            board.doMove(m)
            n += perft(board, depth - 1)
            board.undoMove()
        }
        return n
    }

    private fun playSan(board: Board, vararg sans: String) {
        val list = MoveList(board.fen)
        list.loadFromSan(sans.joinToString(" "))
        for (m in list) board.doMove(m)
    }

    @Test
    fun startPositionHasTwentyLegalMoves() {
        assertEquals(20, Board().legalMoves().size)
    }

    @Test
    fun perftStartDepth3() {
        assertEquals(8902L, perft(Board(), 3))
    }

    @Test
    fun perftKiwipeteDepth2() {
        val board = Board()
        board.loadFromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(board, 1))
        board.loadFromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(2039L, perft(board, 2))
    }

    @Test
    fun promotionGeneratesFourChoices() {
        val board = Board()
        board.loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        val promos = board.legalMoves().filter { it.from == Square.A7 && it.to == Square.A8 }
        assertEquals(4, promos.size)
        assertEquals(
            setOf(Piece.WHITE_QUEEN, Piece.WHITE_ROOK, Piece.WHITE_BISHOP, Piece.WHITE_KNIGHT),
            promos.map { it.promotion }.toSet(),
        )
        val q = promos.first { it.promotion.pieceType == PieceType.QUEEN }
        board.doMove(q)
        assertEquals(Piece.WHITE_QUEEN, board.getPiece(Square.A8))
    }

    @Test
    fun enPassantIsLegalAndRemovesPawn() {
        val board = Board()
        board.loadFromFen("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3")
        val ep = board.legalMoves().first { it.toString() == "e5f6" }
        board.doMove(ep)
        assertEquals(Piece.NONE, board.getPiece(Square.F5))
        assertEquals(Piece.WHITE_PAWN, board.getPiece(Square.F6))
    }

    @Test
    fun castlingRightsAndKingsideCastle() {
        val board = Board()
        playSan(board, "e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5")
        val castle = board.legalMoves().first { it.toString() == "e1g1" }
        board.doMove(castle)
        assertEquals(Piece.WHITE_KING, board.getPiece(Square.G1))
        assertEquals(Piece.WHITE_ROOK, board.getPiece(Square.F1))
        val rights = board.fen.split(" ")[2]
        assertFalse(rights.contains("K"))
        assertFalse(rights.contains("Q"))
    }

    @Test
    fun losingCastlingWhenKingMoves() {
        val board = Board()
        playSan(board, "e4", "e5", "Ke2")
        val rights = board.fen.split(" ")[2]
        assertFalse(rights.contains("K"))
        assertFalse(rights.contains("Q"))
        assertTrue(rights.contains("k"))
    }

    @Test
    fun threefoldRepetition() {
        val board = Board()
        playSan(board, "Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8")
        assertTrue(board.isRepetition)
    }

    @Test
    fun fiftyMoveRule() {
        val board = Board()
        // Kings far apart + a rook so this is 50-move, not insufficient material.
        board.loadFromFen("k7/8/8/8/8/8/8/KR6 w - - 99 50")
        assertEquals(99, board.halfMoveCounter)
        val rookMove = board.legalMoves().first { it.from == Square.B1 }
        board.doMove(rookMove)
        assertTrue(board.halfMoveCounter >= 100)
        assertTrue(board.isDraw)
        val already = Board()
        already.loadFromFen("k7/8/8/8/8/8/8/KR6 w - - 100 50")
        assertTrue(already.isDraw)
    }

    @Test
    fun insufficientMaterialKingVsKing() {
        val board = Board()
        board.loadFromFen("8/8/8/4k3/8/4K3/8/8 w - - 0 1")
        assertTrue(board.isInsufficientMaterial)
    }

    @Test
    fun insufficientMaterialKingAndBishop() {
        val board = Board()
        board.loadFromFen("8/8/8/4k3/8/4K3/7B/8 w - - 0 1")
        assertTrue(board.isInsufficientMaterial)
    }

    @Test
    fun kingAndQueenIsSufficient() {
        val board = Board()
        board.loadFromFen("8/8/8/4k3/8/4K3/7Q/8 w - - 0 1")
        assertFalse(board.isInsufficientMaterial)
    }

    @Test
    fun pgnRoundtripShortGame() {
        val board = Board()
        playSan(board, "e4", "e5", "Nf3", "Nc6", "Bb5", "a6")
        val moves = ArrayList<Move>()
        val replay = Board()
        val list = MoveList()
        list.loadFromSan("e4 e5 Nf3 Nc6 Bb5 a6")
        for (m in list) {
            moves += m
            replay.doMove(m)
        }
        val text = Pgn.build(
            mapOf(
                "Event" to "Just Chess",
                "White" to "Mark",
                "Black" to "Stockfish 17.1 (engine Elo 1500)",
            ),
            moves,
            "*",
        )
        val parsed = Pgn.parseMoves(text)
        assertEquals(moves.map { it.toString() }, parsed.map { it.toString() })
        assertTrue(text.contains("[Event \"Just Chess\"]"))
        assertTrue(text.contains("e4"))
        assertTrue(text.contains("Bb5"))
    }

    @Test
    fun checkmateScholars() {
        val board = Board()
        playSan(board, "e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7")
        assertTrue(board.isMated)
        assertTrue(board.isKingAttacked)
        assertTrue(board.legalMoves().isEmpty())
    }
}
