package studio.lightdata

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class ExposurePairsTest {
    @Test
    fun fullStopsExtendPastTheVisibleWindowAndKeepTheExactAnchor() {
        val center = Exposure(1.0 / 125, 8.0, 37.5)
        val pairs = equivalentExposures(center)
        assertEquals(
            listOf(1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0, 32.0, 45.0, 64.0, 90.0),
            pairs.map { it.a },
        )
        assertSame(center, pairs.single { it.a == 8.0 })
        assertEquals(1.0 / 250, pairs.single { it.a == 5.6 }.t, 0.0)
        assertEquals(1.0 / 60, pairs.single { it.a == 11.0 }.t, 0.0)
        assertTrue(pairs.all { it.iso == 37.5 && abs(it.ev - center.ev) <= .18 })
    }

    @Test
    fun thirdStopAnchorDefinesThePhaseOfTheWholeSeries() {
        val center = Exposure(1.0 / 50, 6.3, 3.0)
        val pairs = equivalentExposures(center)
        val anchor = pairs.indexOf(center)
        assertEquals(listOf(4.5, 6.3, 9.0), pairs.subList(anchor - 1, anchor + 2).map { it.a })
        assertEquals(
            listOf(1.0 / 100, 1.0 / 50, 1.0 / 25),
            pairs.subList(anchor - 1, anchor + 2).map { it.t },
        )
        assertTrue(pairs.zipWithNext().all { (a, b) -> a.a < b.a && a.t < b.t })
    }

    @Test
    fun impossiblePairsAreOmittedAtBothShutterEndpointsAndAcrossBulbGaps() {
        for (center in
            listOf(
                Exposure(shutters.first(), apertures.last(), 100.0),
                Exposure(shutters.last(), apertures.first(), 100.0),
            )) {
            assertEquals(listOf(center), equivalentExposures(center))
        }
        val center = Exposure(50.0, 8.0, 100.0)
        val pairs = equivalentExposures(center)
        assertFalse(pairs.any { it.a == 11.0 }) // 94.5 s falls in the gap between 60 and 120 s.
        assertEquals(25.0, pairs.single { it.a == 5.6 }.t, 0.0)
    }

    @Test
    fun everySupportedCenterSurvivesAndNoPairExceedsRoundingTolerance() {
        for (aperture in apertures) for (shutter in shutters) {
            val center = Exposure(shutter, aperture, 3.0)
            val pairs = equivalentExposures(center)
            assertSame(center, pairs.single { it.a == center.a })
            assertEquals(pairs.size, pairs.map { it.a }.distinct().size)
            for (pair in pairs) {
                val step = 2 * log2(pair.a / center.a)
                assertEquals(step.roundToInt().toDouble(), step, .180001)
                assertEquals(center.ev, pair.ev, .180001)
                assertEquals(center.iso, pair.iso, 0.0)
            }
        }
    }
}
