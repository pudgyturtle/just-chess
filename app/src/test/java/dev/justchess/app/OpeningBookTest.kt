package dev.justchess.app

import dev.justchess.app.engine.OpeningBook
import kotlin.random.Random
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningBookTest {
    @Test
    fun startPositionHasVariety() {
        val seen = mutableSetOf<String>()
        repeat(40) { seen += OpeningBook.pick(emptyList(), Random(it)) ?: "" }
        assertTrue(seen.contains("e2e4"))
        assertTrue(seen.contains("d2d4"))
        assertTrue(seen.size >= 2)
    }

    @Test
    fun repliesExistForE4() {
        assertNotNull(OpeningBook.pick(listOf("e2e4")))
    }
}
