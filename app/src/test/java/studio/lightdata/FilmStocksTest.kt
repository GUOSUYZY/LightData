package studio.lightdata

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class FilmStocksTest {
    @Test fun independentlySampledFomapanCurvesMatchThePrintedMiddleTrace() {
        val stocks = listOf("FOMAPAN 100 Classic", "FOMAPAN 200 Creative", "FOMAPAN 400 Action")
        val curves = stocks.map { name -> filmRecords.first { it.film.name == name }.film }
        // The three published log-relative-exposure plots look almost identical.
        for (curve in curves) {
            assertEquals(56, curve.dots.size)
            val atLogMinusTwo = curve.dots.minBy { kotlin.math.abs(it.first - 1.49487) }
            assertEquals(1.40, atLogMinusTwo.second, .01)
            assertTrue(curve.info.contains("各自采样"))
        }
        assertEquals(3, curves.map { it.dots }.distinct().size)
        for (stock in listOf("KODACHROME 64", "KODACHROME 64 Professional"))
            assertTrue(filmRecords.first { it.film.name == stock }.film.info.contains("相同形状"))
    }

    @Test
    fun spectralConditionsRemainSpecificToEachManufacturerDocument() {
        val expected = mapOf(
            "EKTAR 100" to "1/25 s; E-4046/2010",
            "PORTRA 160" to "1/50 s; E-4051/2016",
            "PORTRA 800" to "1/200 s; E-4040/2016",
            "ULTRAMAX 400" to "1/100 s; Status M",
        )
        val rows = checkNotNull(FilmRecord::class.java.getResourceAsStream("spectral.csv"))
            .bufferedReader(Charsets.UTF_8).use { it.readLines().drop(1).map { row -> row.split(',') } }
        val conditions = rows.groupBy { it[0] }.mapValues { (_, samples) ->
            samples.map { it[6].substringBeforeLast("; density") }.distinct().single()
        }

        assertEquals(expected.keys, conditions.keys)
        assertEquals(428, rows.size)
        for ((stock, prefix) in expected) {
            assertTrue("$stock has wrong capture conditions: ${conditions[stock]}",
                conditions.getValue(stock).startsWith(prefix))
        }
    }

    @Test
    fun eachSelectableStockHasASourceAndUsableDensityScale() {
        assertEquals(filmRecords.size, films.map { it.name }.distinct().size)
        assertTrue(filmRecords.size >= 100)
        for (record in filmRecords) {
            val f = record.film
            val link = URI(filmSourceUrl(record.document))
            assertEquals("https", link.scheme)
            assertTrue(link.host.isNotBlank())
            assertFalse(link.path.endsWith(".apk", ignoreCase = true))
            assertTrue(filmSourceLabel(record.document).endsWith("↗"))
            assertTrue(record.iso > 0 && f.low < 0 && f.high > 0)
            assertTrue(f.dots.zipWithNext().all { (a, b) -> a.first < b.first })
            assertTrue(f.positive(-2.0) < f.positive(2.0))
            assertTrue(f.info.isNotBlank())
        }
        println(
            "Film library: " +
                filmRecords.size +
                " presets; " +
                filmRecords.count { it.film.evidence == FilmEvidence.MANUFACTURER_CURVE } +
                " chart profiles; " +
                filmRecords.count { reciprocity(it.film.name, 10.0).sourceUrl != null } +
                " reciprocity references"
        )
    }

    @Test
    fun readsSpecificManufacturersTimesAndInstructions() {
        fun t(f: String, s: Double) = reciprocity(f, s).exposureSeconds
        assertEquals(80.0, t("FOMAPAN 100 Classic", 10.0)!!, 0.0)
        assertEquals(90.0, t("FOMAPAN 200 Creative", 10.0)!!, 0.0)
        assertEquals(60.0, t("FOMAPAN 400 Action", 10.0)!!, 0.0)
        assertEquals(15.0, t("T-MAX 100", 10.0)!!, 0.0)
        assertEquals(50.0, t("TRI-X 400", 10.0)!!, 0.0)
        assertEquals(18.2, t("FP4 PLUS", 10.0)!!, .05)
        assertEquals(18.2, t("PAN 200 · Kentmere", 10.0)!!, .05)
        assertNull(t("T-MAX 400", 10.0))
        assertTrue(reciprocity("T-MAX 400", 10.0).label.contains("1/3"))
        assertEquals(100.0, t("PROVIA 100F", 100.0)!!, 0.0)
        assertNull(t("FOMAPAN 400 Action", 20.0))
        assertNull(t("Washi E", 10.0))
    }
}
