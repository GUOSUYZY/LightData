package studio.lightdata

import org.junit.Assert.*
import org.junit.Test

class ColorCurvesTest {
    @Test fun manufacturerLayersPreserveNeutralAndExposureOrder() {
        assertEquals(setOf("EKTAR 100","PORTRA 160","PORTRA 800"),colorCurves.keys)
        for (profile in colorCurves.values) {
            for (row in 0..2) {
                assertEquals(1.0, (0..2).sumOf { profile.matrix[it*3+row].toDouble() }, .00001)
            }
            for (channel in listOf("red","green","blue")) {
                assertEquals(.18,profile.positive(channel,0.0),.00001)
                assertTrue(profile.positive(channel,-2.0)<profile.positive(channel,2.0))
                assertTrue(profile.spectral.getValue(channel).zipWithNext().all { (a,b)->a.first<b.first })
            }
        }
    }
}
