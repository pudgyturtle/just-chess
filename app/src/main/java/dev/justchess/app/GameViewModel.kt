package dev.justchess.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import dev.justchess.app.data.AppRepository
import dev.justchess.app.data.Pgn
import dev.justchess.app.engine.OpeningBook
import dev.justchess.app.engine.StockfishEngine
import dev.justchess.app.rating.Elo
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BoardSquare(
    val square: Square,
    val file: Int,
    val rank: Int,
    val piece: Piece,
)

data class UiState(
    val profile: Profile = Profile(),
    val pieces: Map<Square, Piece> = emptyMap(),
    val selected: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val lastFrom: Square? = null,
    val lastTo: Square? = null,
    val playerSide: Side = Side.WHITE,
    val sideToMove: Side = Side.WHITE,
    val engineElo: Int = EngineLevels.DEFAULT,
    val timeControl: TimeControl = TimeControl.TEN,
    val whiteMs: Long = TimeControl.TEN.initialMs ?: 0L,
    val blackMs: Long = TimeControl.TEN.initialMs ?: 0L,
    val inProgress: Boolean = false,
    val engineThinking: Boolean = false,
    val gameOver: Boolean = false,
    val resultTag: String = "*",
    val resultHeadline: String = "",
    val resultDetail: String = "",
    val sans: List<String> = emptyList(),
    val canTakeback: Boolean = false,
    val inCheck: Boolean = false,
    val kingSquare: Square? = null,
    val showNewGame: Boolean = false,
    val pendingPromotion: Pair<Square, Square>? = null,
    val engineAvailable: Boolean = true,
    val engineId: String = "Stockfish ${BuildConfig.STOCKFISH_VERSION}",
    val importMessage: String? = null,
    val plyCount: Int = 0,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as JustChessApp).repository
    private val engine = StockfishEngine(
        File(application.applicationInfo.nativeLibraryDir, "libstockfish.so"),
    )
    private val board = Board()
    private val played = mutableListOf<Move>()
    private val gameLock = Mutex()

    private var playerSide: Side = Side.WHITE
    private var engineElo: Int = EngineLevels.DEFAULT
    private var timeControl: TimeControl = TimeControl.TEN
    private var whiteMs: Long = timeControl.initialMs ?: 0
    private var blackMs: Long = timeControl.initialMs ?: 0
    private var lastTickRt: Long = 0L
    private var clockJob: Job? = null
    private var engineJob: Job? = null
    private var inProgress = false
    private var gameOver = false
    private var finished = false
    private var playerRatingAtStart = 1200.0
    private var startedAtIso: String = Pgn.today()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _history = MutableStateFlow<List<GameRecord>>(emptyList())
    val history: StateFlow<List<GameRecord>> = _history

    init {
        publish()
        _ui.update { it.copy(showNewGame = true) }
        viewModelScope.launch {
            val profile = repo.loadProfile()
            _history.value = repo.loadGames()
            _ui.update {
                it.copy(
                    profile = profile,
                    engineElo = profile.lastEngineElo,
                    timeControl = TimeControl.fromId(profile.lastTimeControlId),
                )
            }
        }
    }

    fun openNewGameSheet() {
        _ui.update { it.copy(showNewGame = true) }
    }

    fun dismissNewGame() {
        _ui.update { it.copy(showNewGame = false) }
    }

    fun startNewGame(color: ColorChoice, control: TimeControl, elo: Int) {
        viewModelScope.launch {
            engineJob?.cancel()
            engine.stopSearch()
            gameLock.withLock {
                val side = when (color) {
                    ColorChoice.WHITE -> Side.WHITE
                    ColorChoice.BLACK -> Side.BLACK
                    ColorChoice.RANDOM -> if (Random.nextBoolean()) Side.WHITE else Side.BLACK
                }
                playerSide = side
                engineElo = elo
                timeControl = control
                whiteMs = control.initialMs ?: 0
                blackMs = control.initialMs ?: 0
                board.loadFromFen(Board().fen)
                played.clear()
                inProgress = true
                gameOver = false
                finished = false
                startedAtIso = Pgn.today()
                val profile = repo.loadProfile()
                playerRatingAtStart = profile.rating
                val saved = profile.copy(
                    lastEngineElo = elo,
                    lastTimeControlId = control.id,
                    lastColor = color.name,
                )
                repo.saveProfile(saved)
                lastTickRt = SystemClock.elapsedRealtime()
            }
            try {
                engine.ensureStarted()
                engine.newGame()
                engine.setElo(elo)
            } catch (_: Exception) {
                _ui.update { it.copy(engineAvailable = false) }
            }
            publish()
            startClock()
            maybeEngineMove()
        }
    }

    fun onSquareTapped(square: Square) {
        val state = _ui.value
        if (!state.inProgress || state.gameOver || state.engineThinking) return
        if (board.sideToMove != playerSide) return
        val selected = state.selected
        if (selected != null && square in state.legalTargets) {
            val moving = board.getPiece(selected)
            val isPromo = moving.pieceType == PieceType.PAWN &&
                ((playerSide == Side.WHITE && square.rank.ordinal == 7) ||
                    (playerSide == Side.BLACK && square.rank.ordinal == 0))
            if (isPromo) {
                _ui.update { it.copy(pendingPromotion = selected to square) }
            } else {
                playUserMove(Move(selected, square))
            }
            return
        }
        val piece = board.getPiece(square)
        if (piece != Piece.NONE && piece.pieceSide == playerSide) {
            val targets = board.legalMoves()
                .filter { it.from == square }
                .map { it.to }
                .toSet()
            _ui.update { it.copy(selected = square, legalTargets = targets) }
        } else {
            _ui.update { it.copy(selected = null, legalTargets = emptySet()) }
        }
    }

    fun illegalFeedbackConsumed() {
        // haptic is fired by the UI when a tap is ignored
    }

    fun promote(type: PieceType) {
        val pending = _ui.value.pendingPromotion ?: return
        val promo = Piece.make(playerSide, type)
        _ui.update { it.copy(pendingPromotion = null) }
        playUserMove(Move(pending.first, pending.second, promo))
    }

    fun cancelPromotion() {
        _ui.update { it.copy(pendingPromotion = null) }
    }

    private fun playUserMove(move: Move) {
        viewModelScope.launch {
            val ok = gameLock.withLock {
                if (!inProgress || gameOver) return@withLock false
                if (board.sideToMove != playerSide) return@withLock false
                val legal = board.legalMoves()
                val match = legal.firstOrNull {
                    it.from == move.from && it.to == move.to &&
                        (move.promotion == Piece.NONE || it.promotion == move.promotion)
                } ?: return@withLock false
                board.doMove(match)
                played += match
                true
            }
            if (!ok) {
                publish()
                return@launch
            }
            afterMove()
        }
    }

    fun takeback() {
        viewModelScope.launch {
            engineJob?.cancel()
            engine.stopSearch()
            gameLock.withLock {
                if (!inProgress || gameOver) return@withLock
                if (played.isEmpty()) return@withLock
                val last = played.last()
                val lastSide = lastSideToHaveMoved()
                if (lastSide != playerSide && played.size >= 2) {
                    board.undoMove()
                    played.removeAt(played.lastIndex)
                    board.undoMove()
                    played.removeAt(played.lastIndex)
                } else if (lastSide == playerSide) {
                    board.undoMove()
                    played.removeAt(played.lastIndex)
                } else if (played.size >= 2) {
                    board.undoMove()
                    played.removeAt(played.lastIndex)
                    board.undoMove()
                    played.removeAt(played.lastIndex)
                }
            }
            publish()
            maybeEngineMove()
        }
    }

    fun resign() {
        viewModelScope.launch {
            val playerWon = false
            finish(
                if (playerSide == Side.WHITE) PgnResult.BLACK_WINS else PgnResult.WHITE_WINS,
                GameEnd.RESIGN,
                if (playerSide == Side.WHITE) "Black wins — resignation" else "White wins — resignation",
                playerWon,
            )
        }
    }

    fun onHostPause() {
        viewModelScope.launch { engine.stopSearch() }
        clockJob?.cancel()
    }

    fun onHostResume() {
        if (inProgress && !gameOver) {
            lastTickRt = SystemClock.elapsedRealtime()
            startClock()
            if (board.sideToMove != playerSide) maybeEngineMove()
        }
    }

    fun clearImportMessage() {
        _ui.update { it.copy(importMessage = null) }
    }

    fun reloadHistory() {
        viewModelScope.launch { _history.value = repo.loadGames() }
    }

    suspend fun exportBackup(): ByteArray = repo.exportZip()

    suspend fun importBackup(bytes: ByteArray): Boolean {
        val result = repo.importZip(bytes)
        val profile = repo.loadProfile()
        _history.value = repo.loadGames()
        _ui.update {
            it.copy(
                profile = profile,
                importMessage = if (result.isSuccess) "Backup restored." else
                    (result.exceptionOrNull()?.message ?: "Import failed"),
            )
        }
        return result.isSuccess
    }

    suspend fun saveName(name: String) {
        val trimmed = name.trim().ifEmpty { "Mark" }
        val profile = repo.loadProfile().copy(name = trimmed)
        repo.saveProfile(profile)
        _ui.update { it.copy(profile = profile) }
    }

    override fun onCleared() {
        super.onCleared()
        clockJob?.cancel()
        engineJob?.cancel()
        viewModelScope.launch { engine.quit() }
    }

    private fun lastSideToHaveMoved(): Side? {
        if (played.isEmpty()) return null
        return if (played.size % 2 == 1) Side.WHITE else Side.BLACK
    }

    private fun afterMove() {
        val ended = detectEnd()
        publish()
        if (!ended) maybeEngineMove()
    }

    private fun detectEnd(): Boolean {
        when {
            board.isMated -> {
                val winnerWhite = board.sideToMove == Side.BLACK
                finish(
                    if (winnerWhite) PgnResult.WHITE_WINS else PgnResult.BLACK_WINS,
                    GameEnd.CHECKMATE,
                    if (winnerWhite) "White wins — checkmate" else "Black wins — checkmate",
                    (playerSide == Side.WHITE) == winnerWhite,
                )
                return true
            }
            board.isStaleMate -> {
                finish(PgnResult.DRAW, GameEnd.STALEMATE, "Draw — stalemate", null)
                return true
            }
            board.isRepetition -> {
                finish(PgnResult.DRAW, GameEnd.THREEFOLD, "Draw — threefold repetition", null)
                return true
            }
            board.halfMoveCounter >= 100 -> {
                finish(PgnResult.DRAW, GameEnd.FIFTY_MOVE, "Draw — 50-move rule", null)
                return true
            }
            board.isInsufficientMaterial -> {
                finish(PgnResult.DRAW, GameEnd.INSUFFICIENT, "Draw — insufficient material", null)
                return true
            }
        }
        return false
    }

    private fun maybeEngineMove() {
        if (!inProgress || gameOver) return
        if (board.sideToMove == playerSide) return
        engineJob?.cancel()
        engineJob = viewModelScope.launch {
            _ui.update { it.copy(engineThinking = true, selected = null, legalTargets = emptySet()) }
            val uciMoves = gameLock.withLock { played.map { it.toString() } }
            val book = OpeningBook.pick(uciMoves)
            val chosen: String? = if (book != null) {
                delay(250L + Random.nextLong(0, 450))
                book
            } else {
                try {
                    engine.ensureStarted()
                    engine.setElo(engineElo)
                    val (w, b) = gameLock.withLock { whiteMs to blackMs }
                    val mv = if (timeControl.isUnlimited) {
                        engine.bestMove(uciMoves, null, null, UNLIMITED_MOVETIME_MS)
                    } else {
                        engine.bestMove(uciMoves, w, b)
                    }
                    mv.takeIf { it.isNotBlank() && it != "(none)" }
                } catch (_: Exception) {
                    _ui.update { it.copy(engineAvailable = false, engineThinking = false) }
                    return@launch
                }
            }
            if (chosen == null) {
                _ui.update { it.copy(engineThinking = false) }
                return@launch
            }
            val applied = gameLock.withLock {
                if (!inProgress || gameOver) return@withLock false
                if (board.sideToMove == playerSide) return@withLock false
                val move = try {
                    Move(chosen, board.sideToMove)
                } catch (_: Exception) {
                    return@withLock false
                }
                val legal = board.legalMoves()
                val match = legal.firstOrNull {
                    it.from == move.from && it.to == move.to &&
                        (move.promotion == Piece.NONE || it.promotion == move.promotion)
                } ?: return@withLock false
                board.doMove(match)
                played += match
                true
            }
            _ui.update { it.copy(engineThinking = false) }
            if (applied) afterMove() else publish()
        }
    }

    private fun startClock() {
        clockJob?.cancel()
        if (timeControl.isUnlimited) return
        lastTickRt = SystemClock.elapsedRealtime()
        clockJob = viewModelScope.launch {
            while (isActive && inProgress && !gameOver) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val dt = now - lastTickRt
                lastTickRt = now
                val flaggedSide: Side? = gameLock.withLock {
                    if (!inProgress || gameOver) return@withLock null
                    if (board.sideToMove == Side.WHITE) {
                        whiteMs = (whiteMs - dt).coerceAtLeast(0)
                        if (whiteMs <= 0) Side.WHITE else null
                    } else {
                        blackMs = (blackMs - dt).coerceAtLeast(0)
                        if (blackMs <= 0) Side.BLACK else null
                    }
                }
                publishClocks()
                if (flaggedSide != null) {
                    val winnerWhite = flaggedSide == Side.BLACK
                    finish(
                        if (winnerWhite) PgnResult.WHITE_WINS else PgnResult.BLACK_WINS,
                        GameEnd.FLAG,
                        if (winnerWhite) "White wins — flag" else "Black wins — flag",
                        (playerSide == Side.WHITE) == winnerWhite,
                    )
                    break
                }
            }
        }
    }

    private fun finish(result: PgnResult, end: GameEnd, headline: String, playerWon: Boolean?) {
        if (finished) return
        finished = true
        inProgress = false
        gameOver = true
        clockJob?.cancel()
        engineJob?.cancel()
        viewModelScope.launch {
            engine.stopSearch()
            val movesSnapshot: List<Move>
            gameLock.withLock { movesSnapshot = played.toList() }
            val profile = repo.loadProfile()
            val score = when (playerWon) {
                true -> 1.0
                false -> 0.0
                null -> 0.5
            }
            val newRating = Elo.update(playerRatingAtStart.toInt(), engineElo, score, profile.gamesPlayed).toDouble()
            val wins = profile.wins + if (playerWon == true) 1 else 0
            val draws = profile.draws + if (playerWon == null) 1 else 0
            val losses = profile.losses + if (playerWon == false) 1 else 0
            val updated = profile.copy(
                rating = newRating,
                wins = wins,
                draws = draws,
                losses = losses,
                gamesPlayed = profile.gamesPlayed + 1,
            )
            repo.saveProfile(updated)
            val whiteName = if (playerSide == Side.WHITE) profile.name else Pgn.enginePlayerName(engineElo)
            val blackName = if (playerSide == Side.BLACK) profile.name else Pgn.enginePlayerName(engineElo)
            val tags = mutableMapOf(
                "Event" to "Just Chess vs Stockfish",
                "Site" to "Just Chess",
                "Date" to startedAtIso,
                "White" to whiteName,
                "Black" to blackName,
                "TimeControl" to if (timeControl.isUnlimited) "-" else timeControl.id.replace("+0", "+0"),
                "Termination" to end.name,
                "EngineElo" to engineElo.toString(),
                "WhiteElo" to if (playerSide == Side.WHITE) playerRatingAtStart.toInt().toString() else engineElo.toString(),
                "BlackElo" to if (playerSide == Side.BLACK) playerRatingAtStart.toInt().toString() else engineElo.toString(),
                "Annotator" to "Just Chess ${BuildConfig.VERSION_NAME}",
            )
            val pgn = Pgn.build(tags, movesSnapshot, result.tag)
            val record = GameRecord(
                id = UUID.randomUUID().toString(),
                pgn = pgn,
                result = result.tag,
                playerColor = playerSide.name,
                engineElo = engineElo,
                timeControl = timeControl.id,
                dateIso = startedAtIso,
                playerName = profile.name,
                playerRatingBefore = playerRatingAtStart,
                playerRatingAfter = newRating,
                termination = end.name,
                plyCount = movesSnapshot.size,
                appVersion = BuildConfig.VERSION_NAME,
                stockfishVersion = BuildConfig.STOCKFISH_VERSION,
            )
            repo.addGame(record)
            _history.value = repo.loadGames()
            _ui.update {
                it.copy(
                    profile = updated,
                    gameOver = true,
                    inProgress = false,
                    engineThinking = false,
                    resultTag = result.tag,
                    resultHeadline = headline,
                    resultDetail = playerResultLine(playerWon, newRating),
                    showNewGame = false,
                )
            }
            publish()
        }
    }

    private fun playerResultLine(playerWon: Boolean?, newRating: Double): String {
        val verb = when (playerWon) {
            true -> "You won"
            false -> "You lost"
            null -> "Draw"
        }
        return "$verb · rating vs these engines ${newRating.toInt()}"
    }

    private fun publishClocks() {
        _ui.update { it.copy(whiteMs = whiteMs, blackMs = blackMs) }
    }

    private fun snapshotBoard(): Map<Square, Piece> = board.occupiedPieces()

    private fun kingSquare(side: Side): Square? {
        val piece = if (side == Side.WHITE) Piece.WHITE_KING else Piece.BLACK_KING
        return board.getPieceLocation(piece).firstOrNull()
    }

    private fun publish() {
        val pieces = snapshotBoard()
        val last = played.lastOrNull()
        val sans = runCatching { Pgn.sanList(played.toList()) }.getOrElse { played.map { it.toString() } }
        val canTb = inProgress && !gameOver && played.isNotEmpty()
        _ui.update {
            it.copy(
                pieces = pieces,
                selected = if (inProgress) it.selected else null,
                legalTargets = if (it.selected != null && inProgress) it.legalTargets else emptySet(),
                lastFrom = last?.from,
                lastTo = last?.to,
                playerSide = playerSide,
                sideToMove = board.sideToMove,
                engineElo = engineElo,
                timeControl = timeControl,
                whiteMs = whiteMs,
                blackMs = blackMs,
                inProgress = inProgress,
                gameOver = gameOver,
                sans = sans,
                canTakeback = canTb,
                inCheck = board.isKingAttacked,
                kingSquare = if (board.isKingAttacked) kingSquare(board.sideToMove) else null,
                plyCount = played.size,
                showNewGame = it.showNewGame && !inProgress,
            )
        }
    }
}
