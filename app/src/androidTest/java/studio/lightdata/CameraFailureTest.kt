package studio.lightdata

import android.Manifest
import android.content.pm.ActivityInfo
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
    fun safetyAdviceRespectsAutoThirdsAndManualShutterChoices() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.sample = liveSample()
                it.modeTo("A")
                it.adjustCompensation(0)
                it.applySafetyOffset(.5) // Half a stop becomes the nearest supported two-thirds.
                assertEquals(2, it.compensationThirds)
                it.modeTo("M")
                it.setValues(Exposure(1.0 / 125, 8.0, 400.0))
                it.applySafetyOffset(1.0)
                assertEquals(1.0 / 60, it.manual.t, 1e-12)
                assertEquals(8.0, it.manual.a, 0.0)
                assertEquals(400.0, it.manual.iso, 0.0)
                it.modeTo("A")
                it.adjustCompensation(0)
                it.toggleFreeze()
                it.applySafetyOffset(.5)
                val frozenTarget = it.exposure
                assertEquals(0, it.compensationThirds) // Deferred until leaving HOLD.
                it.toggleFreeze()
                assertEquals(frozenTarget, it.exposure)
                assertEquals(2, it.compensationThirds)
            }
        }
    }

    @Test
    fun frozenSafetyComparisonKeepsItsSubjectUntilReturningLive() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.sample = liveSample()
                it.modeTo("M")
                it.setValues(Exposure(1.0 / 125, 8.0, 400.0))
                it.toggleFreeze()
                if (!it.safety) it.toggleSafety()
                it.point(.5, .5)
                val point = it.spots.single()
                assertEquals(it.safetyPointId, point.id)
                it.applySafetyOffset(-.5)
                assertTrue(it.frozen)
                assertEquals(1, it.spots.size)
                assertEquals(it.safetyPointId, point.id)
                assertEquals(Exposure(1.0 / 160, 8.0, 400.0).ev, it.exposure.ev, 0.0)
                assertEquals(1.0 / 125, it.manual.t, 0.0)
                it.applySafetyOffset(-.5)
                assertEquals(Exposure(1.0 / 250, 8.0, 400.0).ev, it.exposure.ev, 0.0)
                it.toggleFreeze()
                assertFalse(it.frozen)
                assertEquals(1.0 / 250, it.manual.t, 1e-12)
                assertTrue(it.spots.isEmpty())
            }
        }
    }

    @Test
    fun manifestAllowsFullSensorRotationBeyondTheSystemToggle() {
        // Configuration check only: it does not prove a particular OEM's rotation behavior.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val info = it.packageManager.getActivityInfo(it.componentName, 0)
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, info.screenOrientation)
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
