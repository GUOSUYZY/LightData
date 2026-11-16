package studio.lightdata

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class FilmStocksTest {
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
                filmRecords.count { !it.film.info.contains("通用近似") } +
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
