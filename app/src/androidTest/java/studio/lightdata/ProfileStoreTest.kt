package studio.lightdata

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileStoreTest {
    @Test
    fun reloadsAnExistingLensCurveAgainstTheFramesActualRange() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val filename = "calibration-legacy-decoder-fixture"
        val prefs = context.getSharedPreferences(filename, Context.MODE_PRIVATE)
        val key = "rear/YUV/100"
        val original = defaultResponse.joinToString(",")
        try {
            prefs.edit()
                .putString("profile/rear", key)
                .putString("lut/" + key, original)
                .putString("range/" + key, "8.25")
                .commit()
            val saved = ProfileStore(prefs).load("rear")!!
            assertTrue(saved.legacyDecode)
            assertEquals(defaultResponse[0], saved.responseFor(YuvEncoding.default)[12], 0.0)
            assertEquals(defaultResponse[12], saved.responseFor(YuvEncoding(limited = true))[12], 0.0)
            assertEquals(original, prefs.getString("lut/" + key, null))
        } finally {
            context.deleteSharedPreferences(filename)
        }
    }

    @Test
    fun separateLensProfilesSurviveReloadAndBadCapture() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val file = "calibration-fixture"
        val prefs = testContext.getSharedPreferences(file, Context.MODE_PRIVATE)
        try {
            val store = ProfileStore(prefs)
            val data = Calibration(defaultResponse, 8.25, "YUV", 100)
            store.save("wide", data)
            store.save("tele", data.copy(stops = 7.0, iso = 200, channel = "RAW+YUV"))
            val reloaded =
                ProfileStore(testContext.getSharedPreferences(file, Context.MODE_PRIVATE))
            assertNull(reloaded.load("ultra"))
            assertEquals(7.0, reloaded.load("tele")!!.stops, 0.0)
            assertEquals(200, reloaded.load("tele")!!.iso)
            assertTrue(
                runCatching { store.save("wide", data.copy(response = doubleArrayOf())) }.isFailure
            )
            assertArrayEquals(data.response, reloaded.load("wide")!!.response, 0.0)
            assertEquals("YUV", reloaded.load("wide")!!.channel)
        } finally {
            testContext.deleteSharedPreferences(file)
        }
    }
}
