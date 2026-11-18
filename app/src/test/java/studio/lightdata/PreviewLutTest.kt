package studio.lightdata

import org.junit.Assert.*
import org.junit.Test

class PreviewLutTest {
    @Test fun cameraPrefersFullGateDisplaySizeAndRespectsHeapOnSmallDevices() {
        val outputs=listOf(640 to 480,1280 to 720,1280 to 960,1920 to 1440,4000 to 3000)
        assertEquals(1280 to 960, previewDimensions(outputs,1440,4.0/3,256L shl 20))
        assertEquals(1920 to 1440, previewDimensions(outputs,2150,4.0/3,384L shl 20))
        assertEquals(640 to 480, previewDimensions(outputs,2150,4.0/3,64L shl 20))
    }

    @Test fun atlasContainsEveryGridNodeWithRedFastest() {
        val size = CUBE_SIZE
        val rgb = ByteArray(size*size*size*3) { i ->
            val cell = i/3
            when (i%3) { 0 -> cell%size; 1 -> cell/size%size; else -> cell/(size*size) }.toByte()
        }
        val atlas = packCube(rgb)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val i = 3*((g*size+b)*size+r)
            assertEquals(r, atlas.get(i).toInt())
            assertEquals(g, atlas.get(i+1).toInt())
            assertEquals(b, atlas.get(i+2).toInt())
        }
        assertThrows(IllegalArgumentException::class.java) { packCube(ByteArray(rgb.size-1)) }
    }

    @Test fun cameraOrSceneMotionRestartsFullResolutionDelay() {
        val still = StillDetector()
        val f = Frame(12,8,IntArray(96) { 0xff666666.toInt() },0.0,"test")
        val id = floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f)
        assertFalse(still.observe(f,id,1000))
        assertFalse(still.observe(f,id,1500))
        assertTrue(still.observe(f,id,1700))
        val turn = floatArrayOf(0f,-1f,0f,1f,0f,0f,0f,0f,1f)
        assertFalse(still.observe(f,turn,1720))
        assertTrue(still.observe(f,turn,2450))
        assertFalse(still.observe(f.copy(pixels=IntArray(96) { -1 }),turn,2470))
        assertFalse(still.observe(f,turn,5500))
    }
}
