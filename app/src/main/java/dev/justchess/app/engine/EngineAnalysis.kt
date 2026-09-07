package dev.justchess.app.engine

sealed class EngineScore {
    data class Cp(val value: Int) : EngineScore()
    data class Mate(val value: Int) : EngineScore()
}

data class EngineLine(
    val multipv: Int,
    val score: EngineScore,
    val pv: List<String>,
)

data class EngineAnalysis(
    val lines: List<EngineLine>,
    val bestmove: String,
) { 
    val bestLine: EngineLine? get() = lines.firstOrNull()
}
