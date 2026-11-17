package studio.lightdata

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class SafetyExposureTest {
    private fun testFilm(low: Double = -1.0, high: Double = 3.0) = Film(
        "TEST",
        low,
        high,
        listOf(-5.0 to .1, 0.0 to 1.0, 5.0 to 2.0),
        "test",
        digital = true,
        evidence = FilmEvidence.USER_MODEL,
    )

    private val response = DoubleArray(256) { (it - 128) / 16.0 }
    private val exposure = Exposure(1.0, 1.0, 25.0) // EV100 2

    private fun histogram() = IntArray(256).apply {
        this[63] = 25
        this[80] = 25
        this[128] = 25 // Exactly on the upper boundary: not yet a risk.
        this[129] = 25
    }

    @Test
    fun riskUsesAllPixelsAndTreatsWorkingBoundariesConsistently() {
        val result = analyzeSafety(histogram(), response, 5.0, testFilm(), exposure)!!
        assertEquals(.25, result.current.shadowFraction, 1e-12)
        assertEquals(.25, result.current.highlightFraction, 1e-12)
        assertEquals(0.0, result.current.cameraBlackFraction, 0.0)
        assertEquals(0.0, result.current.cameraWhiteFraction, 0.0)
        assertTrue(result.current.bothEndsNeedCompromise)
        assertFalse(result.current.primaryTonesFit)
        assertFalse(result.current.hasLuminanceLimit)
    }

    @Test
    fun halfStopComparisonsKeepOneSceneReferenceAndMoveBothEnds() {
        val result = analyzeSafety(histogram(), response, 5.0, testFilm(), exposure)!!
        assertEquals(listOf(-.5, 0.0, .5), result.comparisons.map { it.requestedStops })
        val (less, same, more) = result.comparisons.map { it.assessment }
        assertEquals(exposure, same.shooting.metered)
        assertTrue(less.highlightFraction <= same.highlightFraction)
        assertTrue(less.shadowFraction >= same.shadowFraction)
        assertTrue(more.highlightFraction >= same.highlightFraction)
        assertTrue(more.shadowFraction <= same.shadowFraction)
        assertEquals(0.0, less.highlightFraction, 0.0)
        assertEquals(.5, more.highlightFraction, 0.0)
        assertEquals(exposure.t / sqrt(2.0), less.shooting.metered.t, 1e-12)
        assertEquals(exposure.t * sqrt(2.0), more.shooting.metered.t, 1e-12)
    }

    @Test
    fun onlyAnExplicitSubjectDrivesThePlacementSuggestion() {
        val result = analyzeSafety(
            histogram(), response, 5.0, testFilm(), exposure,
            selectedPoint = 7 to 8.0,
            goal = ToneGoal.HIGHLIGHT,
        )!!
        assertEquals(-4.0, recommendOffset(exposure, 8.0, ToneGoal.HIGHLIGHT.zone)!!, 1e-9)
        assertEquals(7, result.referencePoint)
        assertEquals(8.0, result.referenceEv!!, 0.0)
        assertEquals(-4.0, result.recommendedStops!!, 1e-9)
        assertTrue(result.decisionLabel()!!.contains("#7"))
        val applied = shiftedMeteredExposure(exposure, result.recommendedStops!!)
        assertEquals(7.0, zoneAt(shootingPlan(testFilm(), applied), result.referenceEv!!), 1e-9)
    }

    @Test
    fun withoutASubjectThereIsNoAutomaticQuantileAdvice() {
        val histogram = IntArray(256).apply {
            this[64] = 1
            this[96] = 1
            this[128] = 1
        }
        for (goal in ToneGoal.entries) {
            val result = analyzeSafety(histogram, response, 5.0, testFilm(), exposure, goal = goal)!!
            assertNull(result.referencePoint)
            assertNull(result.referenceEv)
            assertNull(result.recommendedStops)
            assertNull(result.decisionLabel())
            assertEquals(3, result.comparisons.size)
        }
    }

    @Test
    fun luminanceCensoringDoesNotMisclassifySaturatedColors() {
        val frame = Frame(5, 1, intArrayOf(
            0xff000000.toInt(), 0xffffffff.toInt(), 0xffff0000.toInt(),
            0xff757575.toInt(), 0,
        ), 5.0, "test")
        assertTrue(frame.cameraLimitedAt(.4, .5))
        val neutral = Frame(5, 1, IntArray(5) { 0xff757575.toInt() }, 5.0, "test")
        assertFalse(neutral.cameraLimitedAt(.5, .5))
        // Saturated RGB endpoints may be gamut/color rendering, not lost scene luminance.
        for (rgb in intArrayOf(0xffff0000.toInt(), 0xffffff00.toInt())) {
            assertFalse(Frame(1, 1, intArrayOf(rgb), 5.0, "color").cameraLimitedAt(.5, .5))
        }
        val histogram = frame.distribution()
        assertEquals(4, histogram.sum())
        val result = analyzeSafety(
            histogram, defaultResponse, 5.0, testFilm(-20.0, 20.0), exposure,
        )!!
        assertEquals(0.0, result.current.shadowFraction, 0.0)
        assertEquals(0.0, result.current.highlightFraction, 0.0)
        assertEquals(.25, result.current.cameraBlackFraction, 0.0)
        assertEquals(.25, result.current.cameraWhiteFraction, 0.0)
        assertTrue(result.current.hasLuminanceLimit)
    }

    @Test
    fun invalidOrEmptyMeasurementsCannotBecomeAFalseZeroRiskRecommendation() {
        assertNull(analyzeSafety(IntArray(256), response, 5.0, testFilm(), exposure))
        val negative = histogram().apply { this[1] = -1 }
        assertNull(analyzeSafety(negative, response, 5.0, testFilm(), exposure))
        val nan = response.copyOf().apply { this[128] = Double.NaN }
        assertNull(analyzeSafety(histogram(), nan, 5.0, testFilm(), exposure))
        val reversed = response.copyOf().apply { this[128] = this[127] - 1 }
        assertNull(analyzeSafety(histogram(), reversed, 5.0, testFilm(), exposure))
        assertNull(analyzeSafety(histogram(), response, Double.NaN, testFilm(), exposure))
        assertNull(analyzeSafety(
            histogram(), response, 5.0, testFilm(), exposure,
            selectedPoint = 0 to 5.0,
        ))
    }

    @Test
    fun reciprocityInstructionsDoNotPretendToBeLongExposureDensityCurves() {
        val hp5 = films.single { it.name == "HP5 PLUS" }
        val metered = Exposure(10.0, 1.0, 100.0)
        val plan = shootingPlan(hp5, metered)
        assertEquals(ReciprocityStatus.CORRECTED, plan.reciprocity.status)
        assertEquals(20.4, plan.reciprocity.exposureSeconds!!, .05)
        // No unsupported t-correction-to-density translation. This anchor remains nominal.
        assertEquals(5.0, zoneAt(plan, metered.ev), 1e-12)
        assertEquals(0.0, recommendOffset(metered, metered.ev, ToneGoal.MIDTONE.zone)!!, 1e-12)
        val phoenix = shootingPlan(films.single { it.name == "Phoenix II" }, metered)
        assertEquals(ReciprocityStatus.ESTIMATED, phoenix.reciprocity.status)
        assertTrue(phoenix.reciprocity.exposureSeconds!! > metered.t)
        assertEquals(5.0, zoneAt(phoenix, metered.ev), 1e-12)
    }

    @Test
    fun apertureAdviceAndUnknownDataDoNotMoveTheOrdinaryCurve() {
        val film = films.single { it.name == "T-MAX 400" }
        val plan = shootingPlan(film, Exposure(10.0, 8.0, 400.0))
        assertEquals(ReciprocityStatus.APERTURE_ADVICE, plan.reciprocity.status)
        assertNull(plan.reciprocity.exposureSeconds)
        assertEquals(1.0 / 3, plan.reciprocity.apertureCompensationStops!!, 0.0)
        assertEquals(5.0, zoneAt(plan, plan.metered.ev), 0.0)
        val e100 = shootingPlan(
            films.single { it.name == "EKTACHROME E100" }, Exposure(120.0, 8.0, 100.0),
        )
        assertTrue(e100.uncertain)
        assertNull(e100.reciprocity.exposureSeconds)
        assertEquals(5.0, zoneAt(e100, e100.metered.ev), 0.0)
    }

    @Test
    fun quantizedCandidatesAreThePairsThatWillActuallyBeAssessed() {
        val calls = mutableListOf<Double>()
        val result = analyzeSafety(
            histogram(), response, 5.0, testFilm(), exposure,
            candidateExposure = { stops ->
                calls += stops
                exposure.copy(t = .125)
            },
        )!!
        assertEquals(listOf(-.5, 0.0, .5), calls)
        assertEquals(exposure, result.current.shooting.metered)
        assertTrue(result.comparisons.all { it.metered.t == .125 })
        assertTrue(result.comparisons.all { it.assessment.shooting.metered.t == .125 })
    }

    @Test
    fun everyComparisonRetainsItsOwnMissingCompensationStatus() {
        val foma = films.single { it.name == "FOMAPAN 100 Classic" }
        val grey = IntArray(256).apply { this[117] = 100 }
        val result = analyzeSafety(
            grey, defaultResponse, 10.0, foma, Exposure(10.0, 8.0, 100.0),
        )!!
        val (less, current, more) = result.comparisons
        assertTrue(less.assessment.shooting.uncertain)
        assertEquals(ReciprocityStatus.CORRECTED, current.assessment.shooting.reciprocity.status)
        assertTrue(more.assessment.shooting.uncertain)
        assertTrue(less.assessment.shooting.reciprocity.meteredSeconds < 10)
        assertEquals(80.0, current.assessment.shooting.reciprocity.exposureSeconds!!, 0.0)
        assertTrue(more.assessment.shooting.reciprocity.meteredSeconds > 10)
    }

    @Test
    fun applyingManualAdviceChoosesTheNearestRealShutterWithoutDirectionReversal() {
        assertEquals(-2, nearestExposureThirds(-.5))
        assertEquals(2, nearestExposureThirds(.5))
        val original = Exposure(1.0 / 100, 8.0, 400.0)
        for (offset in listOf(-10.0, -.5, 0.0, .5, 10.0)) {
            val selected = nearestMeteredShutter(original, offset)
            assertTrue(selected.t in shutters)
            assertEquals(original.a, selected.a, 0.0)
            assertEquals(original.iso, selected.iso, 0.0)
            assertEquals(offset.sign, (original.ev - selected.ev).sign, .0)
        }
    }
}
