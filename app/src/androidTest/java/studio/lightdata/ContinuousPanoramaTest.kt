package studio.lightdata

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class ContinuousPanoramaTest {
    private fun pose(angle: Double): FloatArray {
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        return floatArrayOf(c,0f,s,0f,1f,0f,-s,0f,c)
    }

    @Test fun tracksTexturedPanWithoutAnyStopAndRejectsUncertainOverlap() {
        val cv = requireNotNull(OpenCvScanAligner.available)
        val nativeTimes = mutableListOf<Double>()
        var reliable = 0
        val aligner = ScanAligner { w,h,a,b,mask ->
            val start = System.nanoTime()
            cv.align(w,h,a,b,mask).also {
                if (it != null) reliable++
                nativeTimes += (System.nanoTime()-start)/1e6
            }
        }
        val field = Field(.70,.53)
        fun frame(angle: Double): Frame {
            val w = 480
            val h = 364
            val image = IntArray(w*h) { i ->
                val u = (2*(i%w+.5)/w-1)*field.x
                val z = cos(angle)+u*sin(angle)
                val x = (u*cos(angle)-sin(angle))/z
                val y = (1-2*(i/w+.5)/h)*field.y/z
                val gray = (118 + 36*sin(38*x+12*y) + 35*cos(47*y-17*x) +
                    24*sin(80*x*y+25*x-41*y)).roundToInt()
                -0x1000000 or (gray*0x010101)
            }
            return Frame(w,h,image,0.0,"test")
        }
        val center = frame(0.0)
        val scan = ScanSession(center,Field(1.0,.42),pose(0.0),defaultResponse,aligner)
        val angles = (0..24).map { it*.02 } + (23 downTo -24).map { it*.02 }
        val times = mutableListOf<Double>()
        val keyTimes = mutableListOf<Double>()
        for ((i,angle) in angles.withIndex()) {
            val shot = frame(angle)
            val n = scan.guide(0).captures
            val start = System.nanoTime()
            scan.capture(field,pose(angle),1000L+i*50,null,4.0) { shot }
            val elapsed = (System.nanoTime()-start)/1e6
            times += elapsed
            if (scan.guide(0).captures > n) keyTimes += elapsed
        }
        val guide = scan.guide(0)
        assertTrue("accepted="+guide.captures,guide.captures >= 4)
        assertTrue("coverage="+guide.coverage,guide.coverage > .98)
        assertTrue("no moving frames registered",reliable >= 3)
        assertNull(cv.align(128,96,ByteArray(128*96){100},ByteArray(128*96){95},ByteArray(128*96){-1}))
        fun median(v: List<Double>) = v.sorted()[v.size/2]
        Log.i("LightDataScanBench","captures="+guide.captures+" coverage="+guide.coverage+
            " cvMedianMs="+median(nativeTimes)+" movingMedianMs="+median(times)+" captureMedianMs="+median(keyTimes))
    }
}