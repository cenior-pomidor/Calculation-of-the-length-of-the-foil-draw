package io.github.ceniorpomidor.foildraw.calc

import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoilCalculatorTest {

    private fun pattern(vararg values: Double): FoilPattern {
        val heights = values.filterIndexed { i, _ -> i % 2 == 0 }
        val gaps = values.filterIndexed { i, _ -> i % 2 == 1 }
        return FoilPattern(heights, gaps)
    }

    /** Наименьший зазор между оттисками разных ударов, найденный прямым перебором ударов. */
    private fun simulatedClearance(pattern: FoilPattern, pull: Double): Double {
        val strikes = (pattern.length / pull).toInt() + 2
        var result = Double.POSITIVE_INFINITY
        for (strike in 1..strikes) {
            for (i in 0 until pattern.size) {
                for (j in 0 until pattern.size) {
                    val start = pattern.starts[j] + strike * pull
                    val end = pattern.ends[j] + strike * pull
                    val gap = max(pattern.starts[i] - end, start - pattern.ends[i])
                    result = minOf(result, gap)
                }
            }
        }
        return result
    }

    private fun randomPattern(random: Random): FoilPattern {
        val count = random.nextInt(2, 5)
        val heights = List(count) { random.nextInt(2, 41) / 2.0 }
        val gaps = List(count - 1) { random.nextInt(0, 241) / 2.0 }
        return FoilPattern(heights, gaps)
    }

    @Test
    fun `two elements from the sketch nest evenly`() {
        val result = FoilCalculator.calculate(pattern(10.0, 60.0, 15.0), minGap = 0.0)

        assertEquals(85.0 / 3, result.minPull, 1e-9)
        assertEquals(30.0, result.maxPull, 1e-9)
        assertEquals(29.0, result.pull, 1e-9)
        assertEquals(2.0, result.clearance, 1e-9)
        assertEquals(85.0, result.plainPull, 1e-9)
        assertTrue(result.isNested)
    }

    @Test
    fun `minimal gap narrows the window but keeps the even pull`() {
        val result = FoilCalculator.calculate(pattern(10.0, 60.0, 15.0), minGap = 1.0)

        assertEquals(86.0 / 3, result.minPull, 1e-9)
        assertEquals(29.5, result.maxPull, 1e-9)
        assertEquals(29.0, result.pull, 1e-9)
        assertEquals(2.0, result.clearance, 1e-9)
        assertEquals(1.0 - 29.0 / 86.0, result.savings, 1e-9)
    }

    @Test
    fun `two elements match the closed form`() {
        val random = Random(7)
        repeat(500) {
            val x = random.nextInt(1, 61) / 2.0
            val y = random.nextInt(1, 61) / 2.0
            val z = random.nextInt(0, 401) / 2.0
            val gap = random.nextInt(0, 7) / 2.0
            val nested = max(0.0, floor((z - gap) / (x + y + 2 * gap)))
            val expected = (x + y + z + gap) / (nested + 1)

            val result = FoilCalculator.calculate(pattern(x, z, y), gap)

            assertEquals("x=$x z=$z y=$y gap=$gap", expected, result.minPull, 1e-9)
        }
    }

    @Test
    fun `two elements get equal gaps around every nested impression`() {
        val random = Random(3)
        repeat(500) {
            val x = random.nextInt(1, 61) / 2.0
            val y = random.nextInt(1, 61) / 2.0
            val z = random.nextInt(0, 401) / 2.0
            val gap = random.nextInt(0, 7) / 2.0
            val nested = floor((z - gap) / (x + y + 2 * gap))
            if (nested < 1) return@repeat
            // В середину ложатся n оттисков x и n оттисков y, между ними и краями 2n + 1 равных зазоров.
            val even = (z - nested * (x + y)) / (2 * nested + 1)

            val result = FoilCalculator.calculate(pattern(x, z, y), gap)

            assertEquals("x=$x z=$z y=$y gap=$gap", even, result.clearance, 1e-9)
            assertEquals("x=$x z=$z y=$y gap=$gap", (x + y + z + even) / (nested + 1), result.pull, 1e-9)
        }
    }

    @Test
    fun `narrow middle gives no savings`() {
        val result = FoilCalculator.calculate(pattern(10.0, 15.0, 10.0), minGap = 0.0)

        assertEquals(35.0, result.pull, 1e-9)
        assertTrue(result.maxPull.isInfinite())
        assertFalse(result.isNested)
        assertEquals(0.0, result.savings, 1e-9)
    }

    @Test
    fun `three and four elements interleave`() {
        val three = FoilCalculator.calculate(pattern(10.0, 30.0, 10.0, 30.0, 10.0), minGap = 0.0)
        assertTrue(three.isNested)
        assertTrue(three.clearance >= 0.0)

        val four = FoilCalculator.calculate(pattern(8.0, 40.0, 12.0, 25.0, 8.0, 40.0, 12.0), minGap = 1.0)
        assertTrue(four.isNested)
        assertTrue(four.clearance >= 1.0 - 1e-9)
    }

    @Test
    fun `clearance agrees with direct simulation`() {
        val random = Random(11)
        repeat(300) {
            val pattern = randomPattern(random)
            val pull = pattern.maxHeight + random.nextDouble() * pattern.length
            assertEquals(simulatedClearance(pattern, pull), FoilCalculator.clearance(pattern, pull), 1e-9)
        }
    }

    @Test
    fun `result is valid, minimal and most even`() {
        val random = Random(42)
        repeat(300) {
            val pattern = randomPattern(random)
            val gap = random.nextInt(0, 5) / 2.0
            val result = FoilCalculator.calculate(pattern, gap)
            val message = "starts=${pattern.starts.toList()} ends=${pattern.ends.toList()} gap=$gap"

            assertTrue(message, simulatedClearance(pattern, result.minPull) >= gap - 1e-9)
            assertTrue(message, simulatedClearance(pattern, result.pull) >= gap - 1e-9)
            assertEquals(message, simulatedClearance(pattern, result.pull), result.clearance, 1e-9)
            assertTrue(message, result.pull >= result.minPull - 1e-9 && result.pull <= result.maxPull + 1e-9)

            var pull = pattern.maxHeight + gap
            while (pull < result.minPull - 1e-3) {
                assertTrue("$message pull=$pull", FoilCalculator.clearance(pattern, pull) < gap)
                pull += 0.01
            }

            if (result.maxPull.isFinite()) {
                assertTrue(message, simulatedClearance(pattern, result.maxPull) >= gap - 1e-9)
                val steps = 200
                for (step in 0..steps) {
                    val candidate = result.minPull + (result.maxPull - result.minPull) * step / steps
                    assertTrue(message, FoilCalculator.clearance(pattern, candidate) <= result.clearance + 1e-9)
                }
            }
        }
    }
}
