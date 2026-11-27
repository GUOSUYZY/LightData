package studio.lightdata

import org.junit.Assert.*
import org.junit.Test

class LiveCaptureTest {
    @Test fun rotatedLiveAndFrozenCropMatchExistingFullResolutionReference() {
        val w=38; val h=28
        val planes=listOf(
            Rig.Plane(ByteArray(40*h) { ((it*7)%240+10).toByte() },40,1),
            Rig.Plane(ByteArray(40*h/2) { (105+it%38).toByte() },40,2),
            Rig.Plane(ByteArray(24*h/2) { (140+it%22).toByte() },24,1),
        )
        val image=Rig.Packet(w,h,planes,YuvEncoding())
        for (turn in listOf(0,90,180,270)) for (target in listOf(Field(.75,.6),Field(2.0,1.4),Field(1.0,1.0))) {
            val source=Field(1.0,1.0)
            val live=LiveCapture(image,8.0,"test",111,turn,source,target)
            val expected=crop(upright(image.frame(8.0,"test",111),turn),source,target)
            val complete=live.fullFrame()
            assertEquals(expected.w,complete.w)
            assertEquals(expected.h,complete.h)
            assertArrayEquals(expected.pixels,complete.pixels)
            assertArrayEquals(expected.distribution(),complete.distribution())
            assertEquals(expected.spot(.5,.5,defaultResponse),complete.spot(.5,.5,defaultResponse),0.0)
            assertExtremesEquivalent(referenceExtremes(expected, defaultResponse), complete.extremes(defaultResponse))
        }
    }
}
