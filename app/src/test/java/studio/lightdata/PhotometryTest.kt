package studio.lightdata

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class PhotometryTest {
    @Test fun brightMinorityCanPlaceTheDominantToneBelowZoneV() {
        val response = DoubleArray(256).apply { this[210] = 3.0 }
        val scene = IntArray(256).apply { this[117] = 900; this[210] = 100 }
        val metered = 10.0 + meter(scene, response)
        assertEquals(10.0 + log2(1.7), metered, 1e-9)
        val manual = Exposure(1.0 / 32, 8.0, 100.0)
        for (mode in listOf("A", "S")) {
            val neutral = solve(mode, manual, metered)
            val compensated = solve(mode, manual, metered, 3)
            assertTrue(10.0 - neutral.ev < -.5)
            assertEquals(1.0, neutral.ev - compensated.ev, .17)
        }
        for (film in films) assertEquals(.18, film.positive(0.0), 1e-9)
    }

    @Test fun camera2DefaultKeepsShadowDetailAndLimitedStreamsExpandOnlyOnce() {
        val full = YuvEncoding.default
        val limited = YuvEncoding(limited = true)
        for (code in 0..255) assertEquals(code, luma(full.rgb(code, 128, 128)))
        assertEquals(0, luma(limited.rgb(16,128,128)))
        assertEquals(255, luma(limited.rgb(235,128,128)))
        assertEquals(10, luma(full.rgb(10,128,128)))
        assertTrue(defaultResponse[10] > defaultResponse[0] + 4)
        assertEquals(srgbToLinear(10 / 255.0), .18 * 2.0.pow(defaultResponse[10]), 1e-12)
        assertTrue(luma(YuvEncoding(limited = true, bt709 = true, videoTransfer = true).rgb(64,128,128)) >
            luma(limited.rgb(64,128,128)))
    }

    @Test fun lensProfileSurvivesThePreviousVideoRangeDecoder() {
        val old = Calibration(defaultResponse, 9.1, "YUV", 100, legacyDecode = true)
        val converted = old.responseFor(YuvEncoding.default)
        assertSame(converted, old.responseFor(YuvEncoding.default))
        assertEquals(old.response[0], converted[12], 0.0)
        assertEquals(old.response[255], converted[238], 0.0)
        assertEquals(old.response[100], old.responseFor(YuvEncoding(limited = true))[100], 0.0)
        assertSame(defaultResponse, old.copy(legacyDecode = false).responseFor(YuvEncoding.default))
    }

    @Test fun linearPrimariesDoNotCreateFalseShadowStripes() {
        val blueCode = luma(0xff0000ff.toInt())
        val relative = defaultResponse[blueCode]
        assertEquals(log2(.0722 / .18), relative, .025)
        assertEquals(FilmLimit.IN_RANGE, films.last().limit(relative))
        for (film in films) {
            assertEquals(FilmLimit.SHADOW, film.limit(film.low - .001))
            assertEquals(FilmLimit.IN_RANGE, film.limit(film.low))
            assertEquals(FilmLimit.IN_RANGE, film.limit(film.high))
            assertEquals(FilmLimit.HIGHLIGHT, film.limit(film.high + .001))
        }
    }

    @Test fun sceneHistogramAndSpotStayPutWhenTargetExposureMoves() {
        val capture = Exposure(1.0/64, 4.0, 100.0)
        val gray = (linearToSrgb(.18) * 255).roundToInt()
        val pixels = IntArray(100) { YuvEncoding.default.rgb(gray, 128, 128) }
        val frame = Frame(10, 10, pixels, capture.ev, "test")
        val h = frame.distribution()
        val scene = frame.cameraEv + meter(h,defaultResponse)
        val spot = frame.spot(.5,.5,defaultResponse)
        assertEquals(10.0, scene, .02)
        assertEquals(scene, spot, 1e-9)
        val window = HistogramWindow.following(scene)
        val bins = window.bins(h, defaultResponse, capture.ev, 128)
        val bin = (window.fraction(spot)*128).toInt()
        assertEquals(h.sum(), bins[bin])
        for (target in listOf(capture.copy(iso = 200.0), capture.copy(t = 1.0/32))) {
            val zone = 5 + spot - target.ev
            assertEquals(6.0, zone, .02)
            val midpoint = target.ev
            assertEquals(capture.ev - 1, midpoint, 1e-9)
            assertArrayEquals(bins, window.bins(h,defaultResponse,capture.ev,128))
        }
    }

    @Test fun viewportClipsOffScreenBinsAndZoomKeepsTheFingerAnchor() {
        val window = HistogramWindow(3.0, 10.0)
        val anchor = 7.0
        val zoom = window.transform(.1, window.fraction(anchor), 2.0, 3.0)
        assertEquals(.5, zoom.fraction(anchor), 1e-9)
        assertEquals(3.0, zoom.gain, 1e-9)
        assertEquals(5.0, zoom.span, 1e-9)
        val hist = IntArray(256).apply { this[100] = 22; this[0] = 18; this[255] = 30 }
        val response = DoubleArray(256) { it / 10.0 }
        assertEquals(22, window.bins(hist,response,0.0,128).sum())
        assertEquals(0, window.bins(hist,response,0.0,128).last())
    }

    @Test fun phoneAeChangingShutterOrGainDoesNotChangeEstimatedSceneBrightness() {
        // Both capture settings see the same patch. At EV100 11 it records half the light.
        val captureA = Exposure(1.0/64, 4.0, 100.0)
        val reference = captureA.ev
        for (settings in listOf(captureA, captureA.copy(t = 1.0/128), captureA.copy(iso = 200.0))) {
            val gray = (255 * linearToSrgb(.18 * 2.0.pow(reference - settings.ev))).roundToInt()
            val image = Frame(16,16,IntArray(256) { YuvEncoding.default.rgb(gray,128,128) },settings.ev,"wide")
            assertEquals(reference, image.spot(.5,.5,defaultResponse), .025)
            assertEquals(reference, image.cameraEv + meter(image.distribution(),defaultResponse), .025)
        }
    }
}
