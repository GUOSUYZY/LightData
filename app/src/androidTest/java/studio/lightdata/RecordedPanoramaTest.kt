package studio.lightdata
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assume
import org.junit.Assert.assertTrue
import java.util.zip.ZipFile
import org.json.JSONObject
import java.io.File
class RecordedPanoramaTest {
 @Test fun replayOptionalPrivateCaptureAcrossBackends() {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val file=File(ctx.filesDir,"replay.zip")
  Assume.assumeTrue(file.exists())
  ZipFile(file).use { zip ->
   val frames=(1..40).map { i ->
    val json=JSONObject(zip.getInputStream(zip.getEntry("$i.json")).bufferedReader().readText())
    val bitmap=zip.getInputStream(zip.getEntry("$i.jpg")).use { BitmapFactory.decodeStream(it) }
    val pixels=IntArray(bitmap.width*bitmap.height)
    bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    val f=Frame(bitmap.width,bitmap.height,pixels,json.getDouble("cameraEV"),json.getString("camera"))
    bitmap.recycle()
    f to json
   }
   fun axes(j: JSONObject)=FloatArray(9) { j.getJSONArray("axes").getDouble(it).toFloat() }
   val origin=axes(frames[0].second)
   val identity=floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f)
   for(mode in listOf("LK","ORB","ECC","自动")) {
    val first=frames.first()
    val source=first.second.getJSONArray("source")
    val target=first.second.getJSONArray("target")
    val mosaic=Mosaic(Field(target.getDouble(0),target.getDouble(1)),identity,first.first,defaultResponse,OpenCvScanAligner.forMode(mode))
    val session=ScanSession(first.first,Field(target.getDouble(0),target.getDouble(1)),origin,defaultResponse,OpenCvScanAligner.forMode(mode))
    val times=mutableListOf<Double>()
    var accepted=0
    for((f,j) in frames) {
     val sensor=axes(j)
     val pose=FloatArray(9) { i -> (0..2).sumOf { k -> origin[k*3+i/3].toDouble()*sensor[k*3+i%3] }.toFloat() }
     session.capture(Field(source.getDouble(0),source.getDouble(1)),sensor,j.getLong("timeMs"),null,j.getDouble("exposureMs")) { f }
     val before=mosaic.coverage
     val t=System.nanoTime()
     mosaic.add(f,Field(source.getDouble(0),source.getDouble(1)),pose)
     times+=(System.nanoTime()-t)/1e6
     if(mosaic.coverage>before)accepted++
    }
    assertTrue(mode+" failed to expand the provided leftward sequence",mosaic.coverage > .4)
    Log.i("ScanReplay",mode+" accepted="+accepted+" coverage="+mosaic.coverage+" medianMs="+times.sorted()[times.size/2]+" session="+session.guide(0).coverage)
   }
  }
 }
}
