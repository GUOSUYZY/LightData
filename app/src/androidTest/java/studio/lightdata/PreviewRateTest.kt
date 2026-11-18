package studio.lightdata

import android.Manifest
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewRateTest {
    @Test fun wideTargetStartsSettleCaptureAndShowsPartialCoverage() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.uiAutomation.grantRuntimePermission(inst.targetContext.packageName,Manifest.permission.CAMERA)
        ActivityScenario.launch(MainActivity::class.java).use { run ->
            fun awaitState(predicate: (MainActivity) -> Boolean) {
                val deadline = SystemClock.elapsedRealtime()+10000
                do {
                    var ready = false
                    run.onActivity { ready = predicate(it) }
                    if (ready) return
                    Thread.sleep(80)
                } while (SystemClock.elapsedRealtime()<deadline)
                fail("Camera did not reach expected scan state")
            }
            awaitState { it.sample != null }
            run.onActivity { it.focal = 8.0; it.selectCamera() }
            awaitState { it.sample?.missing == true }
            run.onActivity { it.toggleScan() }
            awaitState { (it.sample?.scan?.captures ?: 0) > 0 }
            run.onActivity { a ->
                val scan = a.sample!!.scan!!
                assertTrue(scan.coverage > 0 && scan.coverage < 1)
                assertTrue(scan.tiles.any { it > 0 })
                assertTrue(scan.tiles.any { it < 1 })
                assertNotNull(scan.next)
                a.toggleScan()
                assertFalse(a.scanActive)
            }
        }
    }

    @Test fun realCameraDrawsNewFramesAcrossPreviewModesAndStopsOnFreeze() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val app=inst.targetContext
        inst.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.CAMERA)
        val prefs=app.getSharedPreferences("meter",Context.MODE_PRIVATE)
        val keys=listOf("film","customPresetId","filmIsoLocked","iso","warned")
        val original=prefs.all.filterKeys { it in keys }
        prefs.edit().putBoolean("warned",true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { run ->
                run.onActivity { a ->
                    a.selectFilm(filmRecords.first { it.film.name=="PORTRA 400" })
                    a.showFps=true
                }
                for (mode in listOf("finder","tone","lut")) {
                    run.onActivity { a -> a.preview=mode!="finder"; a.useLut=mode=="lut" }
                    val timeout=SystemClock.elapsedRealtime()+10000
                    var ready=false
                    do {
                        Thread.sleep(100)
                        run.onActivity { a -> ready=a.sample?.live!=null &&
                            (mode!="lut" || a.activeAtlas!=null) }
                    } while(!ready && SystemClock.elapsedRealtime()<timeout)
                    assertTrue("Camera/LUT failed to start for $mode",ready)
                    Thread.sleep(1300)
                    val rates=mutableListOf<Int>()
                    var still=0
                    var dimensions=""
                    repeat(12) {
                        Thread.sleep(250)
                        run.onActivity { a ->
                            a.updateFps()
                            rates+=a.renderedFps
                            if(a.sample?.still==true) still++
                            dimensions=a.previewView.get()?.holder?.surfaceFrame?.toShortString().orEmpty()
                        }
                    }
                    assertTrue("$mode did not render new camera frames: $rates",rates.sum()>0)
                    Log.i("LightDataPerformance","%s %.2f FPS, still %d/12, GL %s".format(Locale.US,mode,rates.average(),still,dimensions))
                }
                run.onActivity { a ->
                    a.toggleFreeze()
                    a.updateFps()
                    assertEquals(0,a.renderedFps)
                    assertNull(a.sample?.live)
                }
            }
        } finally {
            val edit=prefs.edit()
            keys.forEach { edit.remove(it) }
            original.forEach { (key,value) -> when(value) {
                is String -> edit.putString(key,value)
                is Boolean -> edit.putBoolean(key,value)
            } }
            edit.commit()
        }
    }
}
