package studio.lightdata

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

// Half a third-stop interval; extra room accounts for rounded markings such as f/11.
private const val MAX_MARKING_ERROR_EV = 0.18

/** Return the full, aperture-ordered series at the measured ISO and exposure. */
fun equivalentExposures(center: Exposure): List<Exposure> {
    val apertureStops = apertures.associateWith { 2 * log2(it / center.a) }
    val groups = apertures.groupBy { apertureStops.getValue(it).roundToInt() }
    val series = mutableListOf(center)
    for ((step, candidates) in groups) {
        if (step == 0) continue
        val aperture = candidates.minBy { abs(apertureStops.getValue(it) - step) }
        if (abs(apertureStops.getValue(aperture) - step) > MAX_MARKING_ERROR_EV) continue

        val ratio = aperture / center.a
        val idealTime = center.t * ratio * ratio
        val time = shutters.minBy { abs(log2(it / idealTime)) }
        // Missing long-exposure settings and shutter endpoints can leave a full-stop
        // aperture with no equivalent time. Omit that column instead of clamping it.
        if (abs(log2(time / idealTime)) <= MAX_MARKING_ERROR_EV) {
            series += center.copy(t = time, a = aperture)
        }
    }
    return series.sortedBy { it.a }
}
