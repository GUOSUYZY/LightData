package studio.lightdata

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraFailureTest {
    @Before
    fun grantCameraPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA,
        )
    }

    private fun liveSample(): Sample {
        val frame = Frame(2, 2, IntArray(4) { -0x1000000 or 0x808080 }, 1.0, "test")
        return Sample(frame, IntArray(256).apply { this[128] = 4 }, false, null)
    }

    @Test
    fun failureClearsLiveReadingAndEnablesRetryWithoutMessageMatching() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.sample = liveSample()
                it.frozen = true
                it.single = true
                it.held = 7.0
                assertNotNull(it.reading)
                assertEquals(7.0, it.appliedReading!!, 0.0)

                it.invalidateCamera("无法配置镜头：模拟失败")

                assertFalse(it.message.contains("重试"))
                assertTrue(it.cameraRetry)
                assertNull(it.sample)
                assertNull(it.reading)
                assertNull(it.appliedReading)
                assertEquals(it.manual.ev, it.exposure.ev, 0.0)
                assertTrue(it.frozen)
                assertTrue(it.single)
                assertEquals(7.0, it.held!!, 0.0)
            }
        }
    }

    @Test
    fun pauseClearsLiveReadingWithoutRequestingManualRetry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.sample = liveSample()
                assertNotNull(it.reading)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity {
                assertNull(it.sample)
                assertNull(it.reading)
                assertFalse(it.cameraRetry)
            }
        }
    }

    @Test
    fun cancellingScanAlsoReleasesCalibrationInteractionLock() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.runningCal = true
                it.cancelScan()
                assertFalse(it.runningCal)
            }
        }
    }
}
