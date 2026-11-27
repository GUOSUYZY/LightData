package studio.lightdata

import kotlin.math.log2
import kotlin.math.pow

/** An automatic view-only measurement, deliberately separate from a numbered manual [Spot]. */
data class ExtremalSpot(
    val x: Double,
    val y: Double,
    val ev: Double,
    val cameraLimited: Boolean,
)

data class FrameExtremes(val darkest: ExtremalSpot, val brightest: ExtremalSpot)

private fun luminanceWindow(
    values: DoubleArray, width: Int, offset: Int, first: Double, last: Double, left: Int, right: Int,
): Double {
    val inside = if (right < 0 || left >= width) 0.0 else
        values[offset + minOf(width - 1, right) + 1] - values[offset + maxOf(0, left)]
    return inside - minOf(0, left) * first + maxOf(0, right - width + 1) * last
}
private fun countWindow(
    values: IntArray, width: Int, offset: Int, first: Int, last: Int, left: Int, right: Int,
): Int {
    val inside = if (right < 0 || left >= width) 0 else
        values[offset + minOf(width - 1, right) + 1] - values[offset + maxOf(0, left)]
    return inside - minOf(0, left) * first + maxOf(0, right - width + 1) * last
}

/** Absolute extrema of the same local linear-luminance measurement used by [Frame.spot]. */
fun Frame.extremes(response: DoubleArray): FrameExtremes? {
    if (w <= 0 || h <= 0 || pixels.size < w * h || response.size != 256) return null
    val stride = w + 1
    // Only five prefix rows are retained, rather than three frame-sized integral images.
    // Clamped supports reproduce spot's border handling; invisible pixels remain excluded.
    val luminance = DoubleArray(5 * stride)
    val coverage = IntArray(luminance.size)
    val limited = IntArray(luminance.size)
    val firstLuminance = DoubleArray(5)
    val lastLuminance = DoubleArray(5)
    val firstCoverage = IntArray(5)
    val lastCoverage = IntArray(5)
    val firstLimited = IntArray(5)
    val lastLimited = IntArray(5)
    val linear = DoubleArray(256) { 2.0.pow(response[it]) }
    var darkest: ExtremalSpot? = null
    var brightest: ExtremalSpot? = null
    var darkEv = Double.POSITIVE_INFINITY
    var brightEv = Double.NEGATIVE_INFINITY
    for (y in 0 until h) {
        for (slot in 0..4) {
            val sourceY = (y + slot - 2).coerceIn(0, h - 1)
            val offset = slot * stride
            val firstPixel = pixels[sourceY * w]
            val lastPixel = pixels[sourceY * w + w - 1]
            val firstGray = luma(firstPixel)
            val lastGray = luma(lastPixel)
            firstLuminance[slot] = if (firstPixel ushr 24 != 0) linear[firstGray] else 0.0
            lastLuminance[slot] = if (lastPixel ushr 24 != 0) linear[lastGray] else 0.0
            firstCoverage[slot] = if (firstPixel ushr 24 != 0) 1 else 0
            lastCoverage[slot] = if (lastPixel ushr 24 != 0) 1 else 0
            firstLimited[slot] = if (firstPixel ushr 24 != 0 && (firstGray == 0 || firstGray == 255)) 1 else 0
            lastLimited[slot] = if (lastPixel ushr 24 != 0 && (lastGray == 0 || lastGray == 255)) 1 else 0
            for (x in 0 until w) {
                val pixel = pixels[sourceY * w + x]
                val visible = pixel ushr 24 != 0
                val gray = luma(pixel)
                luminance[offset + x + 1] = luminance[offset + x] +
                    if (visible) linear[gray] else 0.0
                coverage[offset + x + 1] = coverage[offset + x] + if (visible) 1 else 0
                limited[offset + x + 1] = limited[offset + x] +
                    if (visible && (gray == 0 || gray == 255)) 1 else 0
            }
        }
        for (x in 0 until w) {
            if (pixels[y * w + x] ushr 24 == 0) continue
            var sum = 0.0
            var count = 0
            var atLimit = 0
            for (slot in 0..4) {
                val offset = slot * stride
                sum += luminanceWindow(luminance, w, offset, firstLuminance[slot], lastLuminance[slot], x - 2, x + 2)
                count += countWindow(coverage, w, offset, firstCoverage[slot], lastCoverage[slot], x - 2, x + 2)
                atLimit += countWindow(limited, w, offset, firstLimited[slot], lastLimited[slot], x - 2, x + 2)
            }
            if (count == 0) continue
            val ev = cameraEv + log2(sum / count)
            // Stay a millionth of a pixel off Float's integer boundary. This keeps local
            // support aligned with the existing spot sampler without changing a drawn position.
            val pixelX = (x + .500001) / w
            val pixelY = (y + .500001) / h
            // Prefix arithmetic must not move a visually identical tie to another corner.
            // The tolerance is far below both the camera's levels and a displayed 0.1 EV.
            if (darkest == null || ev < darkEv - 1e-9) {
                darkest = ExtremalSpot(pixelX, pixelY, ev, atLimit > 0)
                darkEv = ev
            }
            if (brightest == null || ev > brightEv + 1e-9) {
                brightest = ExtremalSpot(pixelX, pixelY, ev, atLimit > 0)
                brightEv = ev
            }
        }
    }
    val dark = darkest ?: return null
    val bright = brightest ?: return null
    return FrameExtremes(dark, bright)
}
