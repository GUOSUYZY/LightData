package studio.lightdata

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomPresetsTest {
    private val base = CustomPreset.create("自定义 \"135\" / 夜景", false, 12.0, 4.0, 3.0)
    private val next = CustomPreset.create(base.name, true, 16.0, 6.0, 12.5)

    @Test fun escapedNamesFractionalIsoAndDigitalTypeSurviveStorage() {
        val presets = listOf(base, next)
        val json = CustomPresets.encode(presets)
        assertEquals(presets, CustomPresets.decode(StringReader(json)))
        assertEquals(presets, CustomPresets.decode(json))
        assertEquals("自定义 · " + base.name, base.toFilm().name)
        assertEquals(-8.0, base.toFilm().low, 0.0)
        assertTrue(next.toFilm().digital)
        assertEquals(emptyList<CustomPreset>(), CustomPresets.decode(CustomPresets.encode(emptyList())))
    }

    @Test fun repeatedImportsAreIdempotentAndConflictsUpdateInPlace() {
        val other = CustomPreset.create("other", true, 7.0, 3.0, 100.0)
        val updated = base.copy(name = "新名称", iso = 200.0)
        val existing = listOf(base, other)
        val merged = CustomPresets.merge(existing, listOf(updated, next))
        assertEquals(listOf(updated, other, next), merged)
        assertEquals(merged, CustomPresets.merge(merged, listOf(updated, next)))
        assertEquals(listOf(base, other), existing)
    }

    @Test fun malformedExportsAreRejectedAsAWhole() {
        val json = CustomPresets.encode(listOf(base, next))
        listOf(
            json.replace("\"version\": 1", "\"version\": 2"),
            json.replace("\"version\": 1", "\"version\": 1, \"version\": 1"),
            json.replace("\"version\": 1", "\"v\": 1"),
            json.replace(next.id, base.id),
            json.replace(base.id, "arbitrary"),
            json.replace("\"digital\": false", "\"digital\": \"false\""),
            json.replace("\"iso\": 12.5", "\"iso\": \"12.5\""),
            json.replace("\"iso\": 12.5", "\"iso\": 0"),
            json.replace("\"above\": 6.0", "\"above\": 16.0"),
            json.replace("\"iso\": 12.5", "\"iso\": 1e1000"),
            json.replace("\"iso\": 12.5", "\"extra\": 12.5"),
            json.dropLast(1),
            json + "{}",
            " ".repeat(CustomPresets.MAX_BYTES + 1) + json,
        ).forEach { bad -> assertThrows(IllegalArgumentException::class.java) { CustomPresets.decode(StringReader(bad)) } }
        assertThrows(IllegalArgumentException::class.java) { CustomPresets.merge(listOf(base), listOf(next, next)) }
        assertThrows(IllegalArgumentException::class.java) { base.copy(name = "a".repeat(81)) }
        assertThrows(IllegalArgumentException::class.java) { base.copy(above = Double.NaN) }
    }
}
