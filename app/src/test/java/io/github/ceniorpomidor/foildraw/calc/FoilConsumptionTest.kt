package io.github.ceniorpomidor.foildraw.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class FoilConsumptionTest {

    @Test
    fun `run and make-ready lengths`() {
        val usage = FoilConsumption(pullMm = 29.0, run = 10_000, rollLength = 120.0)

        assertEquals(290.0, usage.runLength, 1e-9)
        assertEquals(58.0, usage.makeReadyLength, 1e-9)
        assertEquals(348.0, usage.totalLength, 1e-9)
        assertEquals(290.0 / 120, usage.runRolls, 1e-9)
        assertEquals(58.0 / 120, usage.makeReadyRolls, 1e-9)
        assertEquals(3, usage.rollsNeeded)
    }

    @Test
    fun `partly used roll counts as a whole one`() {
        assertEquals(2, FoilConsumption(pullMm = 12.0, run = 10_000, rollLength = 120.0).rollsNeeded)
    }

    @Test
    fun `exactly one roll is not rounded up`() {
        assertEquals(1, FoilConsumption(pullMm = 10.0, run = 10_000, rollLength = 120.0).rollsNeeded)
    }
}
