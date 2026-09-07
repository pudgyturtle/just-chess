package dev.justchess.app.data

import android.content.Context
import dev.justchess.app.GameRecord
import dev.justchess.app.Profile
import dev.justchess.app.analysis.AnalysisCache
import dev.justchess.app.analysis.AnalysisSettings
import dev.justchess.app.analysis.AnalysisState
import dev.justchess.app.analysis.GameAnalysis
import dev.justchess.app.analysis.MoveClassifier
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppRepository(context: Context) {
    private val files = context.applicationContext.filesDir
    private val profileFile = File(files, "profile.json")
    private val gamesFile = File(files, "games.json")
    private val gamesDir = File(files, "games").apply { mkdirs() }
    private val analysisDir = File(files, "analysis").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val mutex = Mutex()

    suspend fun loadProfile(): Profile = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!profileFile.exists()) Profile()
            else runCatching { json.decodeFromString<Profile>(profileFile.readText()) }.getOrElse { Profile() }
        }
    }

    suspend fun saveProfile(profile: Profile) = mutex.withLock {
        withContext(Dispatchers.IO) { profileFile.writeText(json.encodeToString(profile)) }
    }

    suspend fun loadGames(): List<GameRecord> = mutex.withLock {
        withContext(Dispatchers.IO) { readGamesUnlocked() }
    }

    suspend fun addGame(record: GameRecord) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = readGamesUnlocked().toMutableList()
            all.add(0, record)
            gamesFile.writeText(json.encodeToString(all))
            File(gamesDir, "${record.id}.pgn").writeText(record.pgn)
        }
    }

    fun analysisKey(gameId: String, engineVersion: String, settings: AnalysisSettings): String {
        val raw = "$gameId|$engineVersion|${settings.movetimeMs}|${settings.depth}|${settings.multiPv}|${settings.bookPlies}|${settings.classifierVersion}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun loadAnalysis(gameId: String, engineVersion: String, settings: AnalysisSettings): AnalysisCache? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = File(analysisDir, "$gameId.json")
            if (!file.exists()) return@withContext null
            val cache = runCatching { json.decodeFromString<AnalysisCache>(file.readText()) }.getOrNull() ?: return@withContext null
            if (cache.key != analysisKey(gameId, engineVersion, settings) || cache.analysis?.classifierVersion != settings.classifierVersion) {
                cache.copy(state = AnalysisState.STALE)
            } else cache
        }
    }

    suspend fun saveAnalysis(cache: AnalysisCache) = mutex.withLock {
        withContext(Dispatchers.IO) { File(analysisDir, "${cache.gameId}.json").writeText(json.encodeToString(cache)) }
    }

    suspend fun exportZip(): ByteArray = mutex.withLock {
        withContext(Dispatchers.IO) {
            val profile = if (profileFile.exists()) profileFile.readText() else json.encodeToString(Profile())
            val games = readGamesUnlocked()
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("profile.json"))
                zip.write(profile.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for (g in games) {
                    zip.putNextEntry(ZipEntry("games/${g.id}.pgn"))
                    zip.write(g.pgn.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("games/${g.id}.json"))
                    zip.write(json.encodeToString(g).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                analysisDir.listFiles()?.filter { it.isFile && it.extension == "json" }?.forEach { file ->
                    zip.putNextEntry(ZipEntry("analysis/${file.name}"))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
            }
            bos.toByteArray()
        }
    }

    suspend fun importZip(bytes: ByteArray): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                var profileJson: String? = null
                val records = mutableListOf<GameRecord>()
                val pgns = mutableListOf<Pair<String, String>>()
                ZipInputStream(bytes.inputStream()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        val data = readEntry(zip).toString(Charsets.UTF_8)
                        when {
                            name == "profile.json" || name.endsWith("/profile.json") -> profileJson = data
                            name.endsWith(".json") && "games/" in name -> {
                                runCatching { records += json.decodeFromString<GameRecord>(data) }
                            }
                            name.startsWith("analysis/") && name.endsWith(".json") -> {
                                val cache = runCatching { json.decodeFromString<AnalysisCache>(data) }.getOrNull()
                                if (cache != null && cache.gameId.matches(Regex("[A-Za-z0-9._-]+"))) File(analysisDir, "${cache.gameId}.json").writeText(json.encodeToString(cache))
                            }
                            name.endsWith(".pgn") -> {
                                val id = File(name).name.removeSuffix(".pgn")
                                pgns += id to data
                            }
                        }
                        zip.closeEntry()
                    }
                }
                val profile = profileJson?.let { json.decodeFromString<Profile>(it) }
                    ?: throw IllegalArgumentException("zip is missing profile.json")
                profileFile.writeText(json.encodeToString(profile))
                gamesDir.listFiles()?.forEach { it.delete() }
                val finalRecords = if (records.isNotEmpty()) records else pgns.map { (id, pgn) ->
                    recordFromPgn(id, pgn, profile)
                }
                gamesFile.writeText(json.encodeToString(finalRecords))
                for (g in finalRecords) {
                    File(gamesDir, "${g.id}.pgn").writeText(g.pgn)
                }
            }
        }
    }

    private fun readEntry(zip: ZipInputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = zip.read(buf)
            if (n <= 0) break
            bos.write(buf, 0, n)
        }
        return bos.toByteArray()
    }

    private fun readGamesUnlocked(): List<GameRecord> {
        if (!gamesFile.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<GameRecord>>(gamesFile.readText()) }
            .getOrElse { emptyList() }
    }

    private fun recordFromPgn(id: String, pgn: String, profile: Profile): GameRecord {
        fun tag(name: String): String? {
            val re = Regex("""\[$name\s+"([^"]*)"\]""")
            return re.find(pgn)?.groupValues?.get(1)
        }
        val result = tag("Result") ?: "*"
        val white = tag("White") ?: profile.name
        val playerIsWhite = !white.contains("Stockfish", ignoreCase = true)
        return GameRecord(
            id = id,
            pgn = pgn,
            result = result,
            playerColor = if (playerIsWhite) "WHITE" else "BLACK",
            engineElo = tag("EngineElo")?.toIntOrNull()
                ?: Regex("""Elo (\d+)""").find(pgn)?.groupValues?.get(1)?.toIntOrNull()
                ?: 1500,
            timeControl = tag("TimeControl") ?: "-",
            dateIso = tag("Date") ?: "",
            playerName = profile.name,
            playerRatingBefore = tag("WhiteElo")?.toDoubleOrNull() ?: profile.rating,
            playerRatingAfter = profile.rating,
            termination = tag("Termination") ?: "",
            plyCount = Regex("""\d+\.""").findAll(pgn).count(),
        )
    }
}
