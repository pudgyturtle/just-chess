package dev.justchess.app

import dev.justchess.app.analysis.AnalysisScore
import dev.justchess.app.analysis.Classification
import dev.justchess.app.analysis.ClassificationInput
import dev.justchess.app.analysis.MoveClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisClassifierTest {
    @Test fun mateWinToLossOverridesCentipawnBuckets() {
        assertEquals(Classification.BLUNDER, MoveClassifier.classify(ClassificationInput(10, AnalysisScore(mate = 1), AnalysisScore(mate = -1))))
    }
    @Test fun bookMoveIsNotPunishedAsBlunder() {
        assertEquals(Classification.BOOK, MoveClassifier.classify(ClassificationInput(500, inBook = true)))
    }
    @Test fun cplThresholdsAreTransparent() {
        assertEquals(Classification.GOOD, MoveClassifier.classify(30))
        assertEquals(Classification.CONFUSING, MoveClassifier.classify(31))
        assertEquals(Classification.POOR, MoveClassifier.classify(80))
        assertEquals(Classification.BLUNDER, MoveClassifier.classify(200))
    }
    @Test fun missMarksFailureToPunishAnOpponentBlunder() {
        assertEquals(Classification.MISS, MoveClassifier.classify(ClassificationInput(60, opponentBlunderPrevious = true)))
        assertEquals(Classification.BLUNDER, MoveClassifier.classify(ClassificationInput(240, opponentBlunderPrevious = true)))
    }
    @Test fun brilliantMarksBestHangingMaterialSacrifice() {
        assertEquals(Classification.BRILLIANT, MoveClassifier.classify(ClassificationInput(20, sacrifice = true)))
    }
    @Test fun nearBestOnlyMoveIsGreatAndZeroIsBest() {
        assertEquals(Classification.BEST, MoveClassifier.classify(0))
        assertEquals(Classification.GREAT, MoveClassifier.classify(ClassificationInput(31, onlyGoodMove = true)))
    }
}
