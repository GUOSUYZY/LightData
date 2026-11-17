package studio.lightdata

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilmIsoTest {
    @Test fun choosingAStockRelocksIsoAndRestoresCustomAndManualSelections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.CAMERA)
        val prefs = app.getSharedPreferences("meter", Context.MODE_PRIVATE)
        val keys = listOf("t", "a", "iso", "film", "filmIsoLocked", "customPresets", "customPresetId")
        val original = prefs.all.filterKeys { it in keys }
        val stock = filmRecords.first { it.film.name == "PRO 160S" }
        val custom = CustomPreset.create("测试片", false, 10.0, 4.0, 64.0)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.selectFilm(stock)
                    it.setValues(it.manual.copy(iso = 400.0))
                    assertTrue(it.isoLocked)
                    assertEquals(160.0, it.manual.iso, 0.0)
                    it.toggleIsoLock()
                    it.setValues(it.manual.copy(iso = 100.0))
                }
                scenario.recreate()
                scenario.onActivity {
                    assertFalse(it.isoLocked)
                    assertEquals(100.0, it.manual.iso, 0.0)
                    it.toggleIsoLock()
                    assertEquals(160.0, it.manual.iso, 0.0)
                    it.savePreset(custom)
                }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(custom.id, it.selectedPresetId)
                    assertEquals(64.0, it.manual.iso, 0.0)
                    assertTrue(it.isoLocked)
                }
                val document = java.io.File.createTempFile("presets-", ".json", app.cacheDir)
                val uri = android.net.Uri.fromFile(document)
                try {
                    scenario.onActivity { it.writePresetDocument(uri) }
                    eventually {
                        runCatching { CustomPresets.decode(document.readText()).contains(custom) }.getOrDefault(false)
                    }
                    val updated = custom.copy(iso = 80.0)
                    document.writeText(CustomPresets.encode(listOf(updated)))
                    scenario.onActivity { it.readPresetDocument(uri) }
                    eventually {
                        var imported = false
                        scenario.onActivity {
                            imported = it.customPresets.singleOrNull { p -> p.id == updated.id } == updated &&
                                it.manual.iso == 80.0 && it.isoLocked
                        }
                        imported
                    }
                } finally {
                    document.delete()
                }
                scenario.onActivity {
                    it.toggleIsoLock()
                    it.selectFilm(stock)
                    assertTrue(it.isoLocked)
                    assertEquals(160.0, it.manual.iso, 0.0)
                }
            }
        } finally {
            val edit = prefs.edit()
            keys.forEach { edit.remove(it) }
            original.forEach { (key, value) ->
                when (value) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                }
            }
            edit.commit()
        }
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (!condition()) {
            assertTrue("预设文件操作超时", android.os.SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(30)
        }
    }
}
