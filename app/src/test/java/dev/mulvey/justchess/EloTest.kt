package dev.mulvey.justchess

import dev.mulvey.justchess.rating.Elo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EloTest {
    @Test
    fun winRaisesRating() {
        val next = Elo.update(1200, 1500, 1.0, 0)
        assertTrue(next > 1200)
    }

    @Test
    fun drawMovesTowardOpponent() {
        val next = Elo.update(1200, 1500, 0.5, 20)
        assertTrue(next > 1200)
        assertEquals(16, Elo.k(20))
    }
}
