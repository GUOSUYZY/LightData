package studio.lightdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun assertExtremesEquivalent(expected: FrameExtremes?, actual: FrameExtremes?) {
    if (expected == null || actual == null) {
        assertEquals(expected, actual)
        return
    }
    for ((want, measured) in listOf(expected.darkest to actual.darkest, expected.brightest to actual.brightest)) {
        assertEquals(want.x, measured.x, 0.0)
        assertEquals(want.y, measured.y, 0.0)
        assertEquals(want.ev, measured.ev, 1e-9)
        assertEquals(want.cameraLimited, measured.cameraLimited)
    }
}

/** Independent, deliberately slow reference using the pre-existing spot implementation. */
internal fun referenceExtremes(frame: Frame, response: DoubleArray): FrameExtremes? {
    var dark: ExtremalSpot? = null
    var bright: ExtremalSpot? = null
    for (y in 0 until frame.h) for (x in 0 until frame.w) {
        val normalizedX = (x + .500001) / frame.w
        val normalizedY = (y + .500001) / frame.h
        if (frame.sample(normalizedX, normalizedY) ushr 24 == 0) continue
        val ev = frame.spot(normalizedX, normalizedY, response)
        if (!ev.isFinite()) continue
        val candidate = ExtremalSpot(normalizedX, normalizedY, ev, frame.cameraLimitedAt(normalizedX, normalizedY))
        if (dark == null || ev < dark.ev) dark = candidate
        if (bright == null || ev > bright.ev) bright = candidate
    }
    return dark?.let { d -> bright?.let { b -> FrameExtremes(d, b) } }
}

class FrameExtremesTest {
    @Test
    fun markerAnchorIsExactAndOnlyLabelMovesInsideEdges() {
        val point = ExtremalSpot(0.0, 1.0, 9.0, false)
        val anchor = markerAnchor(point, 360f, 200f)
        assertEquals(0f, anchor.x, 0f)
        assertEquals(200f, anchor.y, 0f)
        assertEquals(24f, markerLabelCenter(point, 360f, 200f, 24f).x, 0f)
        assertEquals(176f, markerLabelCenter(point, 360f, 200f, 24f).y, 0f)
        assertEquals(12f, markerLabelCenter(point, 24f, 24f, 24f).x, 0f)
        assertEquals(270f, markerLabelCenter(point.copy(x = .75, y = .5), 360f, 200f, 24f).x, 0f)
    }

    @Test
    fun contrastingRegionsReturnTheirActualPositionAndSpotReading() {
        val pixels = IntArray(21 * 13) { 0xff808080.toInt() }
        for (dy in -2..2) for (dx in -2..2) {
            pixels[(6 + dy) * 21 + 5 + dx] = 0xff202020.toInt()
            pixels[(6 + dy) * 21 + 15 + dx] = 0xfff0f0f0.toInt()
        }
        val frame = Frame(21, 13, pixels, 11.0, "contrast")
        val extremes = frame.extremes(defaultResponse)

        assertNotNull(extremes)
        assertEquals(5.5 / 21, extremes!!.darkest.x, .000002)
        assertEquals(6.5 / 13, extremes.darkest.y, .000002)
        assertEquals(15.5 / 21, extremes.brightest.x, .000002)
        assertEquals(6.5 / 13, extremes.brightest.y, .000002)
        assertEquals(frame.spot(extremes.darkest.x, extremes.darkest.y, defaultResponse),
            extremes.darkest.ev, 1e-9)
        assertEquals(frame.spot(extremes.brightest.x, extremes.brightest.y, defaultResponse),
            extremes.brightest.ev, 1e-9)
        assertExtremesEquivalent(referenceExtremes(frame, defaultResponse), extremes)
    }

    @Test
    fun missingPixelsNeverCreateExtrema() {
        val missing = Frame(7, 5, IntArray(35), 10.0, "missing")
        assertNull(missing.extremes(defaultResponse))

        val pixels = IntArray(9 * 9)
        pixels[4 * 9 + 3] = 0xff808080.toInt()
        pixels[9 + 2] = 0x00ffffff
        pixels[4 * 9 + 4] = 0x00000000
        val partlyMissing = Frame(9, 9, pixels, 9.0, "partial")
        val extremes = partlyMissing.extremes(defaultResponse)
        assertNotNull(extremes)
        assertEquals(3.5 / 9, extremes!!.darkest.x, .000002)
        assertEquals(4.5 / 9, extremes.darkest.y, .000002)
        assertEquals(extremes.darkest, extremes.brightest)
        assertTrue(partlyMissing.sample(extremes.darkest.x, extremes.darkest.y) ushr 24 != 0)
        assertTrue(partlyMissing.sample(extremes.brightest.x, extremes.brightest.y) ushr 24 != 0)
        assertEquals(partlyMissing.spot(extremes.darkest.x, extremes.darkest.y, defaultResponse), extremes.darkest.ev, 1e-9)
    }

    @Test
    fun uniformAndSmallFramesReturnStableCenters() {
        for (w in 1..4) for (h in 1..4) {
            val frame = Frame(w, h, IntArray(w * h) { 0xff808080.toInt() }, 8.0, "uniform")
            val extremes = frame.extremes(defaultResponse)
            assertNotNull(extremes)
            assertEquals(.5 / w, extremes!!.darkest.x, .000002)
            assertEquals(.5 / h, extremes.darkest.y, .000002)
            assertEquals(extremes.darkest, extremes.brightest)
            assertEquals(frame.spot(.5 / w, .5 / h, defaultResponse), extremes.darkest.ev, 1e-9)
        }
    }

    @Test
    fun aggregateMatchesTheOriginalSpotAtEdgesAndCodedLimits() {
        for ((w, h) in listOf(1 to 19, 19 to 1, 3 to 5, 12 to 7, 21 to 17)) {
            val pixels = IntArray(w * h) { i ->
                val gray = (i * 47 + w * 29 + h * 17) % 256
                if (i % 11 == 0 || i % 13 == 0) gray ushr 8 else 0xff000000.toInt() or gray
            }
            for ((x, y) in listOf(0 to h - 1, w - 1 to 0, w - 1 to h - 1, w / 2 to h / 2)) {
                if (w * h % 3 != 1) pixels[y * w + x] = 0xff000000.toInt()
            }
            val frame = Frame(w, h, pixels, 5.0, "edge-$w-$h")
            assertExtremesEquivalent(referenceExtremes(frame, defaultResponse), frame.extremes(defaultResponse))
        }
    }
}
