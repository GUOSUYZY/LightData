package studio.lightdata

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class FinderTest {
    private val id = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun yaw(a: Double): FloatArray {
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        return floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)
    }

    private fun gray(v: Int) = -0x1000000 or ((v and 255) * 0x010101)

    private val field = Field(0.70, 0.6)
    private val target = Field(1.0, 0.4)

    private fun render(view: Field, yaw: Double, ev: Double = 0.0, bias: Int = 0): Frame {
        val width = 288
        val height = (width / view.aspect).roundToInt()
        val colors =
            IntArray(width * height) { i ->
                val u = ((i % width - bias + 0.5) * 2 / width - 1) * view.x
                val v = (1 - (i / width + 0.5) * 2 / height) * view.y
                // Infinite textured wall at world Z=-1, camera rotated about its optical center.
                val z = cos(yaw) + sin(yaw) * u
                val x = (cos(yaw) * u - sin(yaw)) / z
                val y = v / z
                val value =
                    128 +
                        30 * sin(36 * x + 4 * y) +
                        24 * cos(55 * y + 12 * x) +
                        18 * sin(17 * x * y)
                gray((value * 2.0.pow(-ev / 2.2)).roundToInt().coerceIn(0, 255))
            }
        return Frame(width, height, colors, ev, "rear")
    }

    @Test
    fun sensorMountAndDisplayRotationsAreInverses() {
        val f = Frame(3, 2, intArrayOf(1, 2, 3, 4, 5, 6), 0.0, "rear")
        assertArrayEquals(intArrayOf(4, 1, 5, 2, 6, 3), upright(f, 90).pixels)
        assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), upright(f, 180).pixels)
        assertArrayEquals(intArrayOf(3, 6, 2, 5, 1, 4), upright(f, 270).pixels)
        assertArrayEquals(f.pixels, upright(upright(f, 90), 270).pixels)
        assertSame(f, upright(f, 0))
        val landscape = displayAxes(id, 1)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f), landscape, 0f)
        assertArrayEquals(id, displayAxes(landscape, 3), 0f)
        assertArrayEquals(displayAxes(id, 2), displayAxes(landscape, 1), 0f)
    }

    @Test
    fun overlappingFramesContinueTheScanWithoutAnyRotationSensor() {
        val initial = render(field, 0.0)
        val session = ScanSession(initial, target, null, defaultResponse)
        session.capture(initial, field, null)
        val center = session.guide(0)
        assertEquals(1, center.captures)
        assertNotNull(center.next)
        // Each exposure overlaps the previous one. No shutter or sensor event is required.
        val angles = (1..12).map { it * 0.04 } + (11 downTo -12).map { it * 0.04 }
        for (angle in angles) session.capture(render(field, angle), field, null)
        val result = session.guide(0)
        assertTrue("captures " + result.captures, result.captures > 3)
        assertEquals(1.0, result.coverage, 0.0)
        assertNull(result.next)
    }

    @Test
    fun texturelessFrameRequestsOverlapAndDoesNotEraseAcquiredCoverage() {
        val initial = render(field, 0.0)
        val session = ScanSession(initial, target, null, defaultResponse)
        session.capture(initial, field, null)
        val before = session.guide(0)
        session.capture(
            initial.copy(pixels = IntArray(initial.pixels.size) { gray(62) }),
            field,
            null,
        )
        val after = session.guide(90)
        assertTrue(after.needsOverlap)
        assertEquals(before.coverage, after.coverage, 0.0)
        assertEquals(before.next!!.turned(90), after.next)
        session.capture(render(field, 0.045), field, null)
        assertFalse(session.guide(0).needsOverlap)
    }

    @Test
    fun movingForegroundOnStationaryTripodDoesNotExpandTheAtlas() {
        val initial = render(field, 0.0)
        val session = ScanSession(initial, target, id, defaultResponse)
        session.capture(initial, field, id)
        val before = session.guide(0)
        for (t in 1..8) {
            val rgb = initial.pixels.copyOf()
            for (y in 0 until initial.h/3) for (x in 0 until initial.w/3)
                rgb[y*initial.w+x] = gray(if ((x/3 + y/3+t)%2 == 1) 255 else 0)
            session.capture(initial.copy(pixels = rgb),field,id)
        }
        val after = session.guide(0)
        assertEquals(before.coverage,after.coverage,0.0)
        assertEquals(before.captures,after.captures)
    }

    @Test
    fun resumedSensorWithNewWorldReferenceKeepsRegisteredCoverage() {
        val first = render(field, 0.0)
        val session = ScanSession(first, target, id, defaultResponse)
        session.capture(first,field,id)
        val last = render(field,.16)
        session.capture(last,field,yaw(.16))
        val before = session.guide(0)
        session.resumeTracking()
        session.capture(last,field,yaw(2.0))
        val after = session.guide(0)
        assertEquals(before.coverage, after.coverage,0.0)
        assertEquals(before.aim, after.aim)
        assertEquals(before.captures, after.captures)
        session.capture(render(field,.30),field,yaw(2.14))
        assertTrue(session.guide(0).coverage > before.coverage)
    }

    @Test
    fun stalePoseAndDisplayRotationPreserveTheSameScan() {
        val initial = render(field, 0.0)
        val session = ScanSession(initial, target, id, defaultResponse)
        session.capture(initial, field, id)
        val first = session.guide(0)
        for (i in 1..6) session.capture(render(field, i * 0.04), field, id)
        val upright = session.guide(0)
        val rotated = session.guide(90)
        assertTrue(upright.captures > first.captures)
        assertTrue(upright.coverage > first.coverage)
        assertEquals(upright.coverage, rotated.coverage, 0.0)
        assertEquals(upright.aim.turned(90), rotated.aim)
        assertEquals(upright.next?.turned(90), rotated.next)
    }

    @Test
    fun missingFieldIsTransparentAndDimensionsHonorTheRequestedGate() {
        val f = Frame(8, 6, IntArray(48) { gray(it + 30) }, 0.0, "rear")
        val src = Field(4.0, 3.0)
        val small = crop(f, src, Field(2.0, 1.0))
        assertArrayEquals(
            intArrayOf(18, 19, 20, 21, 26, 27, 28, 29).map { gray(it + 30) }.toIntArray(),
            small.pixels,
        )
        val wide = crop(f, src, Field(8.0, 4.0))
        assertEquals(8, wide.w)
        assertEquals(4, wide.h)
        assertEquals(0, wide.sample(0.03, 0.5))
        assertNotEquals(0, wide.sample(0.5, 0.5))
        assertSame(f, crop(f, src, src))
    }

    @Test
    fun coverageCountsAllPixelsAndNeverTreatsMissingAsBlack() {
        val first = render(field, 0.0)
        val m = Mosaic(target, id, first)
        m.add(first, field, id)
        val partial = m.view(id)
        assertEquals(0, partial.sample(0.01, 0.5))
        assertTrue(m.coverage in 0.58..0.64)
        assertTrue(m.grid().any { !it })
        val initialCoverage = m.coverage
        m.add(first.copy(pixels = IntArray(first.w * first.h)), field, yaw(0.45))
        m.add(first.copy(id = "different"), field, yaw(-0.45))
        assertEquals(initialCoverage, m.coverage, 0.0)
        assertFalse(m.ready)
    }

    @Test
    fun compensatedScanAndReframingAgreeWithAnIndependentCamera() {
        val first = render(field, 0.0)
        val m = Mosaic(target, id, first)
        m.add(first, field, id)
        val previous = m.coverage
        for (angle in listOf(0.24, 0.43, -0.24, -0.43)) {
            val source = render(field, angle, ev = 1.0, bias = 3)
            m.add(source, field, yaw(angle))
        }
        assertTrue(m.coverage > previous)
        assertEquals(1.0, m.coverage, 0.0)
        assertTrue(m.ready)
        assertTrue(m.grid().all { it })
        for (angle in listOf(-0.035, 0.0, 0.035)) {
            val result = m.view(yaw(angle))
            val expected = render(target, angle)
            assertEquals(target.aspect, result.w.toDouble() / result.h, 0.015)
            assertFalse(result.pixels.any { it == 0 })
            assertEquals(0.0, result.cameraEv, 0.0)
            val differences =
                (1..19).flatMap { x ->
                    (1..9).map { y ->
                        abs(
                            luma(result.sample(x / 20.0, y / 10.0)) -
                                luma(expected.sample(x / 20.0, y / 10.0))
                        )
                    }
                }
            assertTrue("Mean residual " + differences.average(), differences.average() < 4.0)
        }
        // Beyond the safety margin the crop settles at the last fully acquired orientation.
        val far = m.view(yaw(0.7))
        val farther = m.view(yaw(1.2))
        assertFalse(far.pixels.any { it == 0 })
        assertEquals(
            luma(far.sample(0.8, 0.4)).toDouble(),
            luma(farther.sample(0.8, 0.4)).toDouble(),
            1.0,
        )
    }

    @Test
    fun colorCompensationUsesLinearLightAndDefinesBlackAndClipping() {
        val blue = 0xff000020.toInt()
        val reference = Frame(4, 4, IntArray(16) { blue }, 0.0, "rear")
        val exposed = reference.copy(cameraEv = 3.0)
        val targetGray = (0..255).minBy {
            abs(defaultResponse[it] - (defaultResponse[luma(blue)] + 3.0))
        }
        val compensated = Mosaic(Field(0.1, 0.1), id, reference)
        compensated.add(exposed, Field(1.0, 1.0), id)
        assertEquals(targetGray, luma(compensated.view(id).sample(.5, .5)))

        val black = Mosaic(Field(0.1, 0.1), id, reference.copy(pixels = IntArray(16) { gray(0) }))
        black.add(reference.copy(pixels = IntArray(16) { gray(0) }, cameraEv = 3.0), Field(1.0, 1.0), id)
        assertEquals(0, black.view(id).sample(.5, .5) and 0xffffff)

        val nearClip = reference.copy(pixels = IntArray(16) { 0xff800020.toInt() })
        val clipped = Mosaic(Field(0.1, 0.1), id, nearClip)
        clipped.add(nearClip.copy(cameraEv = 20.0), Field(1.0, 1.0), id)
        val output = clipped.view(id).sample(.5, .5)
        assertEquals(255, output ushr 16 and 255)
        val expectedGain = 1 / srgbToLinear(128 / 255.0)
        assertEquals(
            expectedGain,
            srgbToLinear((output and 255) / 255.0) / srgbToLinear(32 / 255.0),
            expectedGain * .01, // Allow rounding to 8-bit channel codes near the clip boundary.
        )
    }
}
