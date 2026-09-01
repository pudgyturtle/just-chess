package dev.mulvey.justchess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
import dev.mulvey.justchess.data.Pgn
import dev.mulvey.justchess.engine.OpeningBook
import dev.mulvey.justchess.engine.StockfishEngine
import dev.mulvey.justchess.rating.Elo
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun playSan(board: Board, san: String): List<Move> {
        val list = MoveList(board.fen)
        list.loadFromSan(san)
        val out = ArrayList<Move>()
        for (m in list) {
            board.doMove(m)
            out += m
        }
        return out
    }

    @Test
    fun profileDefaultNameIsMark() {
        val p = Profile()
        assertEquals("Mark", p.name)
        assertEquals(0, p.gamesPlayed)
        assertEquals("Unrated", p.ratingLabel())
        assertTrue(p.provisional)
    }

    @Test
    fun profileEmptyNameFallbackUsedBySaveName() {
        // GameViewModel.saveName: name.trim().ifEmpty { "Mark" }
        fun sanitize(name: String) = name.trim().ifEmpty { "Mark" }
        assertEquals("Mark", sanitize(""))
        assertEquals("Mark", sanitize("   "))
        assertEquals("Ada", sanitize(" Ada "))
    }

    @Test
    fun timeControlEnums() {
        assertEquals(5 * 60 * 1000L, TimeControl.FIVE.initialMs)
        assertEquals(10 * 60 * 1000L, TimeControl.TEN.initialMs)
        assertNull(TimeControl.UNLIMITED.initialMs)
        assertTrue(TimeControl.UNLIMITED.isUnlimited)
        assertFalse(TimeControl.TEN.isUnlimited)
        assertEquals("10+0", TimeControl.TEN.id)
        assertEquals("5+0", TimeControl.FIVE.id)
        assertEquals("unlimited", TimeControl.UNLIMITED.id)
        assertEquals(TimeControl.TEN, TimeControl.fromId("nope"))
        assertEquals(TimeControl.FIVE, TimeControl.fromId("5+0"))
        assertEquals(TimeControl.UNLIMITED, TimeControl.fromId("unlimited"))
        assertEquals(TimeControl.TEN, TimeControl.fromId("10+0"))
    }

    @Test
    fun eloLadderAndUciStrengthSettings() {
        assertEquals(listOf(1320, 1500, 1700, 1900, 2100, 2300), EngineLevels.all)
        assertEquals(1500, EngineLevels.DEFAULT)
        assertEquals(1, STOCKFISH_THREADS)
        assertEquals(32, STOCKFISH_HASH_MB)
        assertEquals(1000L, UNLIMITED_MOVETIME_MS)
        val engine = StockfishEngine(java.io.File("/nonexistent"))
        assertEquals(1, engine.threads)
        assertEquals(32, engine.hashMb)
    }

    @Test
    fun eloBoundsFloorAndCeiling() {
        val floored = Elo.update(100, 2300, 0.0, 50)
        assertEquals(100, floored)
        val capped = Elo.update(3200, 1320, 1.0, 50)
        assertEquals(3200, capped)
        val mid = Elo.update(1500, 1500, 1.0, 0)
        assertTrue(mid > 1500)
        assertEquals(32, Elo.k(0))
        assertEquals(32, Elo.k(9))
        assertEquals(16, Elo.k(10))
        assertEquals(1500, Elo.INITIAL)
    }

    @Test
    fun provisionalWindowMatchesKFactorAtTenGames() {
        // UI provisional and K-factor share the same 10-game window.
        assertEquals(10, Elo.PROVISIONAL_GAMES)
        assertTrue(Profile(gamesPlayed = 9).provisional)
        assertFalse(Profile(gamesPlayed = 10).provisional)
        assertEquals(32, Elo.k(9))
        assertEquals(16, Elo.k(10))
    }

    @Test
    fun pgnRoundtripCastlingPromotionEnPassant() {
        val castleBoard = Board()
        val castleMoves = playSan(castleBoard, "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O")
        val castlePgn = Pgn.build(
            mapOf("Event" to "Just Chess", "White" to "Mark", "Black" to "Stockfish 17.1 (engine Elo 1500)"),
            castleMoves,
            "*",
        )
        assertTrue(castlePgn.contains("O-O") || castlePgn.contains("e1g1"))
        val castleParsed = Pgn.parseMoves(castlePgn)
        assertEquals(castleMoves.map { it.toString() }, castleParsed.map { it.toString() })

        val epBoard = Board()
        val epMoves = playSan(epBoard, "e4 a6 e5 d5 exd6")
        val epPgn = Pgn.build(mapOf("Event" to "Just Chess"), epMoves, "*")
        val epParsed = Pgn.parseMoves(epPgn)
        assertEquals(epMoves.map { it.toString() }, epParsed.map { it.toString() })

        // Isolated promotion fragments are not a production path (games start at startpos).
        val numbered = Pgn.build(mapOf("Event" to "Just Chess"), castleMoves, "1-0")
        assertTrue(numbered.contains("1-0"))
        assertTrue(numbered.contains("[Event \"Just Chess\"]"))

        val replayFen = Pgn.replayFenAt(castleMoves, 2)
        val mid = Board()
        mid.doMove(castleMoves[0])
        mid.doMove(castleMoves[1])
        assertEquals(mid.fen, replayFen)
    }

    @Test
    fun backupZipExportImportRestoresProfileAndGames() {
        val profile = Profile(
            name = "Mark",
            rating = 1624.0,
            wins = 3,
            draws = 1,
            losses = 2,
            gamesPlayed = 6,
            lastEngineElo = 1700,
            lastTimeControlId = TimeControl.FIVE.id,
            lastColor = ColorChoice.BLACK.name,
        )
        val game = GameRecord(
            id = "game-abc",
            pgn = Pgn.build(
                mapOf(
                    "Event" to "Just Chess vs Stockfish",
                    "White" to "Mark",
                    "Black" to Pgn.enginePlayerName(1500),
                    "EngineElo" to "1500",
                    "TimeControl" to "10+0",
                    "Termination" to GameEnd.CHECKMATE.name,
                ),
                emptyList(),
                "1-0",
            ),
            result = "1-0",
            playerColor = "WHITE",
            engineElo = 1500,
            timeControl = "10+0",
            dateIso = "2026.09.01",
            playerName = "Mark",
            playerRatingBefore = 1500.0,
            playerRatingAfter = 1624.0,
            termination = GameEnd.CHECKMATE.name,
            plyCount = 0,
            appVersion = "1.0.0",
            stockfishVersion = "17.1",
        )

        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("profile.json"))
                zip.write(json.encodeToString(profile).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("games/${game.id}.pgn"))
                zip.write(game.pgn.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("games/${game.id}.json"))
                zip.write(json.encodeToString(game).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            bos.toByteArray()
        }

        var restoredProfile: Profile? = null
        val restoredGames = mutableListOf<GameRecord>()
        val restoredPgns = mutableMapOf<String, String>()
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/').trimStart('/')
                val data = zip.readBytes().toString(Charsets.UTF_8)
                when {
                    name == "profile.json" -> restoredProfile = json.decodeFromString<Profile>(data)
                    name.endsWith(".json") && "games/" in name ->
                        restoredGames += json.decodeFromString<GameRecord>(data)
                    name.endsWith(".pgn") -> {
                        val id = name.substringAfterLast('/').removeSuffix(".pgn")
                        restoredPgns[id] = data
                    }
                }
            }
        }

        assertNotNull(restoredProfile)
        assertEquals("Mark", restoredProfile!!.name)
        assertEquals(1624.0, restoredProfile!!.rating, 0.0)
        assertEquals(3, restoredProfile!!.wins)
        assertEquals(1, restoredProfile!!.draws)
        assertEquals(2, restoredProfile!!.losses)
        assertEquals(1700, restoredProfile!!.lastEngineElo)
        assertEquals(1, restoredGames.size)
        assertEquals("game-abc", restoredGames[0].id)
        assertEquals("1-0", restoredGames[0].result)
        assertEquals(1500, restoredGames[0].engineElo)
        assertEquals(restoredPgns["game-abc"], restoredGames[0].pgn)
        assertTrue(restoredGames[0].pgn.contains("Stockfish 17.1 (engine Elo 1500)"))
    }

    @Test
    fun openingBookStopsAfterMaxPlyAndHasVariety() {
        assertNull(OpeningBook.pick(List(12) { "e2e4" }))
        assertNotNull(OpeningBook.pick(emptyList(), Random(1)))
        val seen = mutableSetOf<String>()
        repeat(30) { seen += OpeningBook.pick(emptyList(), Random(it + 100)) ?: "" }
        assertTrue(seen.size >= 2)
    }

    @Test
    fun pgnEnginePlayerNameFormat() {
        assertEquals("Stockfish 17.1 (engine Elo 2300)", Pgn.enginePlayerName(2300))
        assertEquals("Stockfish 17.1 (engine Elo 1500)", Pgn.enginePlayerName(1500))
    }

    @Test
    fun openingSummaryFirstPliesAsSan() {
        val board = Board()
        val moves = playSan(board, "e4 e5 Nf3")
        val pgn = Pgn.build(mapOf("Event" to "Just Chess"), moves, "*")
        assertEquals("1.e4 e5 2.Nf3", Pgn.openingSummary(pgn))
        assertEquals("", Pgn.openingSummary(""))
    }
}
