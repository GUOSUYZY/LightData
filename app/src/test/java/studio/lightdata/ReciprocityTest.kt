package studio.lightdata

import org.junit.Assert.*
import org.junit.Test

class ReciprocityTest {
    @Test
    fun hp5UsesManufacturersExampleAndKeepsTheMeteredTime() {
        val result = reciprocity("HP5 PLUS", 10.0)
        assertEquals(ReciprocityStatus.CORRECTED, result.status)
        assertEquals(10.0, result.meteredSeconds, 0.0)
        assertEquals(20.4, result.exposureSeconds!!, .05)
        assertTrue(result.label.contains("20.4"))
        assertTrue(result.sourceUrl!!.contains("ilfordphoto.com"))
        assertEquals(ReciprocityStatus.UNCHANGED, reciprocity("HP5 PLUS", 1.0).status)
        assertEquals(ReciprocityStatus.CORRECTED, reciprocity("HP5 PLUS", 1.01).status)
    }

    @Test
    fun portraHasNoPublishedLongExposureCorrection() {
        for (t in listOf(.0001, .2, 1.0)) {
            val result = reciprocity("PORTRA 400", t)
            assertEquals(ReciprocityStatus.UNCHANGED, result.status)
            assertEquals(t, result.exposureSeconds!!, 0.0)
        }
        for (t in listOf(.00009, 1.01, 60.0)) {
            val result = reciprocity("PORTRA 400", t)
            assertEquals(ReciprocityStatus.UNPUBLISHED, result.status)
            assertNull(result.exposureSeconds)
        }
    }

    @Test
    fun e100KeepsItsDiscreteDaylightFilterAdviceOutOfTheShutterCalculation() {
        assertEquals(ReciprocityStatus.UNCHANGED, reciprocity("EKTACHROME E100", 10.0).status)
        for (t in listOf(10.01, 119.0, 120.0, 121.0)) {
            val result = reciprocity("EKTACHROME E100", t)
            assertEquals(ReciprocityStatus.UNPUBLISHED, result.status)
            assertNull(result.exposureSeconds)
            assertEquals(t == 120.0, result.label.contains("CC10R"))
        }
    }

    @Test
    fun customFilmAndInvalidInputsNeverProduceACompensatedShutter() {
        val custom = reciprocity("自定义胶片", 60.0)
        assertNull(custom.exposureSeconds)
        assertNull(custom.sourceUrl)
        for (t in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertNull(reciprocity("HP5 PLUS", t).exposureSeconds)
        }
    }

    @Test
    fun apertureInstructionsAreDiscreteExposureGuidanceRatherThanShutterTime() {
        val advice = reciprocity("T-MAX 400", 10.0)
        assertEquals(ReciprocityStatus.APERTURE_ADVICE, advice.status)
        assertNull(advice.exposureSeconds)
        assertEquals(1.0 / 3, advice.apertureCompensationStops!!, 0.0)
        assertEquals("开大 1/3 档", advice.label)
        assertNull(reciprocity("T-MAX 400", 10.01).apertureCompensationStops)
        assertEquals(ReciprocityStatus.UNPUBLISHED, reciprocity("EKTACHROME E100", 120.0).status)
        assertNull(reciprocity("EKTACHROME E100", 120.0).apertureCompensationStops)
    }

    @Test
    fun phoenixLabelsBorrowedExponentAsEstimateInsteadOfManufacturerCorrection() {
        val result = reciprocity("Phoenix II", 10.0)
        assertEquals(ReciprocityStatus.ESTIMATED, result.status)
        assertEquals(20.417, result.exposureSeconds!!, .05)
        assertTrue(result.label.contains("估算"))
        assertEquals(ReciprocityStatus.UNCHANGED, reciprocity("Phoenix II", 1.0).status)
    }
}
