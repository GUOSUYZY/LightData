package studio.lightdata

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class CalibrationTest {
    private val count = 32 * 24

    private fun bracket(
        stops: IntRange = -10..6,
        gamma: Double = 2.2,
        irradiance: (Int) -> Double = { .18 },
    ) =
        stops.map { ev ->
            val seconds = 2.0.pow(ev) / 125
            val grid =
                DoubleArray(count) { i ->
                    val encoded = (irradiance(i) * 2.0.pow(ev)).coerceIn(0.0, 1.0).pow(1 / gamma)
                    (encoded * 255).roundToInt() / 255.0
                }
            Bracket(seconds, grid, grid.copyOf())
        }

    private fun expectIssue(frames: List<Bracket>, method: String, step: Double = 1.0) {
        assertThrows(CalibrationIssue::class.java) { fitResponse(frames, method, step) }
    }

    @Test
    fun grayCardRecoversResponseAndMeasuredRange() {
        val frames = bracket(gamma = 1.8)
        val (lut, range) = fitResponse(frames.reversed(), "灰卡")
        assertEquals(0.0, lut[117], 1e-9)
        assertEquals(1.8, lut[150] - lut[75], .1)
        assertTrue((1 until 256).all { lut[it] >= lut[it - 1] })
        assertTrue("measured range = $range", range in 10.0..13.0)
    }

    @Test
    fun fieldTracksRanksAcrossActualExposureTimes() {
        val frames = bracket(gamma = 2.0) { .025 * 2.0.pow(4.0 * it / count) }
        val (lut, range) = fitResponse(frames, "现场")
        assertEquals(2.0, lut[160] - lut[80], .15)
        assertTrue(range > 9)
    }

    @Test
    fun stepCardUsesEnteredIntervalAndChecksOrientation() {
        val frames = bracket { .01125 * 2.0.pow((it % 32 * 9 / 32).toDouble()) }
        val (lut, _) = fitResponse(frames, "阶梯卡", 1.0)
        assertEquals(2.2, lut[160] - lut[80], .12)
        expectIssue(frames, "阶梯卡", 2.0)
        val reversed =
            frames.map {
                it.copy(samples = it.samples.reversedArray(), repeat = it.repeat.reversedArray())
            }
        expectIssue(reversed, "阶梯卡")
    }

    @Test
    fun rejectsMovingOrUnevenSceneAndInsufficientExposure() {
        expectIssue(bracket(), "现场")
        expectIssue(bracket(stops = -3..0), "灰卡")
        expectIssue(bracket(stops = -12..0), "灰卡")
        val scene = bracket { .015 * 2.0.pow(5.0 * it / count) }
        expectIssue(scene, "灰卡")
        val moved =
            scene.mapIndexed { index, b ->
                if (index == 10)
                    b.copy(samples = b.samples.reversedArray(), repeat = b.repeat.reversedArray())
                else b
            }
        expectIssue(moved, "现场")
        val flicker =
            scene.mapIndexed { index, b ->
                if (index == 10)
                    b.copy(repeat = DoubleArray(count) { (b.samples[it] + .09).coerceAtMost(1.0) })
                else b
            }
        expectIssue(flicker, "现场")
    }

    @Test
    fun rejectsBadFrameGeometryAndMetadataWithoutIndexErrors() {
        val original = bracket()
        expectIssue(original.map { it.copy(seconds = 0.0) }, "灰卡")
        expectIssue(original.map { it.copy(repeat = doubleArrayOf(.1)) }, "现场")
        expectIssue(original.map { it.copy(samples = DoubleArray(count) { Double.NaN }) }, "灰卡")
        expectIssue(
            original.map {
                Bracket(
                    it.seconds,
                    it.samples.take(64).toDoubleArray(),
                    it.repeat.take(64).toDoubleArray(),
                )
            },
            "阶梯卡",
        )
    }

    @Test
    fun rawRangeRejectsRepeatedAndNarrowExposureTimes() {
        val scene = DoubleArray(count) { i -> when (i % 4) {
            0 -> .004
            1 -> .15
            2 -> .5
            else -> 1.0
        } }
        fun frame(t: Double): Bracket = Bracket(t, scene,
            DoubleArray(count) { i -> (scene[i] + .0005).coerceAtMost(1.0) })
        assertNull(estimateRawStops(List(6) { frame(.1) }))
        assertNull(estimateRawStops(List(6) { frame(.1 + .001 * it) }))
    }

    @Test
    fun rawNoiseIsMeasuredInLinearSamplesAndRejectsMissingCoverage() {
        fun noisy(noise: Double) =
            (-12..5).map { ev ->
                val base = .18 * 2.0.pow(ev)
                fun values(sign: Int) =
                    DoubleArray(count) { i ->
                        (.004 + base + (if (i % 2 == 0) 1 else -1) * sign * noise).coerceIn(
                            0.0,
                            1.0,
                        )
                    }
                Bracket(2.0.pow(ev) / 100, values(1), values(-1))
            }
        val clean = noisy(.0004)
        val cleanRange = estimateRawStops(clean)
        val noisyRange = estimateRawStops(noisy(.003))
        assertNotNull(cleanRange)
        assertNotNull(noisyRange)
        assertTrue(cleanRange!! > noisyRange!! + 1)
        assertNull(estimateRawStops(clean.take(9)))
        assertNull(estimateRawStops(noisy(0.0)))
    }
}
