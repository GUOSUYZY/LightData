package studio.lightdata

import org.junit.Assert.*
import org.junit.Test

class ColorCurvesTest {
    @Test fun spectralGridMatchesPrintedAxesAndExposureTimes() {
        val expected = mapOf(
            "EKTAR 100" to Triple(89, 25, 2.15),
            "PORTRA 160" to Triple(103, 50, 2.03),
            "PORTRA 800" to Triple(118, 200, 2.71),
            "ULTRAMAX 400" to Triple(118, 100, 2.70),
        )
        val rows = javaClass.getResourceAsStream("spectral.csv")!!.bufferedReader().use {
            it.readLines().drop(1).map { row -> row.split(',') }.groupBy { row -> row[0] }
        }
        assertEquals(expected.keys, rows.keys)
        for ((stock, chart) in expected) {
            val samples = rows.getValue(stock)
            assertEquals(chart.first, samples.size)
            assertTrue(samples.all { it[6].startsWith("1/${chart.second} s;") })
            val bluePeak = samples.filter { it[3] == "blue" }.maxOf { it[5].toDouble() }
            assertEquals(stock, chart.third, bluePeak, .035)
        }
        val layers = javaClass.getResourceAsStream("layer-density.csv")!!.bufferedReader().use {
            it.readLines().drop(1).map { row -> row.split(',') }.groupBy { row -> row[0] to row[3] }
        }
        assertEquals(9, layers.size)
        assertTrue(layers.values.all { it.size == 53 && it.first()[4] == "-6.5" && it.last()[4] == "6.5" })
    }

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
