package studio.lightdata

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class OpticsTest {
    @Test
    fun physicalFilmGateSetsFieldAndInches() {
        val full = field(36.0, 24.0, 24.0)
        assertEquals(73.7398, 2 * Math.toDegrees(atan(full.x)), .001)
        assertEquals(53.1301, 2 * Math.toDegrees(atan(full.y)), .001)
        assertEquals(101.6, mm(4.0, true), 1e-9)
        assertTrue(field(44.0, 33.0, 24.0).covers(full))
        assertFalse(full.covers(full.swap()))
    }

    @Test
    fun exposureModesPreserveInputs() {
        val manual = Exposure(1.0 / 125, 8.0, 100.0)
        val ev = manual.ev
        for (mode in listOf("A", "S", "M")) {
            val out = solve(mode, manual, ev)
            assertEquals(manual, out)
        }
        val m = Exposure(1.0 / 60, 4.0, 3.0)
        for (iso in listOf(3.0, 6.25, 100.0, 800.0)) {
            val e = m.copy(iso = iso)
            for (mode in listOf("A", "S")) {
                val result = solve(mode, e, 12.0)
                assertEquals(iso, result.iso, 0.0)
                if (mode == "A") assertEquals(e.a, result.a, 0.0)
                if (mode == "S") assertEquals(e.t, result.t, 0.0)
                assertTrue(abs(result.ev - 12) < .23)
            }
            assertEquals(e, solve("M", e, 15.0))
        }
        assertTrue(speeds.first() == 3.0)
    }

    @Test
    fun compensationPreservesTheLockedControlAndLimits() {
        val initial = Exposure(1.0 / 125, 5.6, 100.0)
        for (thirds in -15..15) {
            for (mode in listOf("A", "S")) {
                val shot = solve(mode, initial, 12.0, thirds)
                assertEquals(12.0 - thirds / 3.0, shot.ev, .20)
                assertEquals(initial.iso, shot.iso, 0.0)
                if (mode == "A") assertEquals(initial.a, shot.a, 0.0)
                else assertEquals(initial.t, shot.t, 0.0)
            }
            assertEquals(initial, solve("M", initial, 12.0, thirds))
        }
        assertEquals("A", storedMode("P"))
        assertEquals("A", storedMode(null))
        assertEquals("S", storedMode("S"))
        val dark = solve("A", initial, -20.0)
        assertEquals(shutters.last(), dark.t, 0.0)
        assertTrue(-20 - dark.ev < -1.0)
    }

    @Test
    fun curvesOutputPositiveAndUseMiddleGray() {
        val presets = films + listOf(customFilm(false, 12.0, 4.0), customFilm(true, 10.0, 3.0))
        for (film in presets) {
            assertEquals(.18, film.positive(0.0), 1e-7)
            assertTrue(film.positive(-2.0) < film.positive(0.0))
            assertTrue(film.positive(2.0) > film.positive(0.0))
            assertEquals(film.dots.first().second, film.density(-100.0), 1e-9)
        }
        assertTrue(films[2].density(3.0) < films[2].density(-3.0))
        assertTrue(films[0].density(3.0) > films[0].density(-3.0))
    }

    @Test
    fun frozenSpotAndHistogramShareCoordinates() {
        val gray = 0xff757575.toInt()
        val f = Frame(20, 20, IntArray(400) { if (it % 20 < 8) 0 else gray }, 12.0, "ultra")
        val h = f.distribution()
        assertEquals(240, h.sum())
        assertEquals(240, h[117])
        assertEquals(12 + defaultResponse[117], f.spot(.5, .5, defaultResponse), 1e-8)
        assertEquals(defaultResponse[117], meter(h, defaultResponse), 1e-8)
        assertTrue(f.spot(.1, .5, defaultResponse).isNaN())
    }

    @Test
    fun narrowBrightStripesCountEquallyAcrossOrientationsAndWidths() {
        for ((w,h) in listOf(4 to 4, 12 to 5, 5 to 12)) {
            for (phase in 0..3) {
                val pixels = IntArray(w * h) { i ->
                    val coordinate = if (w % 4 == 0) i % w else i / w
                    if (coordinate % 4 == phase) -1 else 0xff757575.toInt()
                }
                val hist = Frame(w,h,pixels,0.0,"test").distribution()
                assertEquals(w * h / 4, hist[255])
                assertEquals(w * h * 3 / 4, hist[117])
                assertEquals(1.090915, meter(hist, defaultResponse), .00001)
            }
        }
    }
}
