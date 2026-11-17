package studio.lightdata

import java.util.Locale
import kotlin.math.*

/** A subject-placement choice, not a claim that one zone is right for every photograph. */
enum class ToneGoal(val zone: Double, val shortLabel: String, val dialogLabel: String) {
    SHADOW(3.0, "暗部纹理 · III", "暗部保留纹理（目标 III 区）"),
    MIDTONE(5.0, "主体中间调 · V", "主体放在中间调（目标 V 区）"),
    HIGHLIGHT(7.0, "高光层次 · VII", "高光保留层次（目标 VII 区）"),
}

/** Distinguishes supporting evidence from a physical, film-out guarantee. */
enum class FilmEvidence(val label: String) {
    MANUFACTURER_CURVE("厂家曲线采样"),
    FAMILY_APPROXIMATION("乳剂类别近似"),
    USER_MODEL("自定义响应模型"),
}

/**
 * The selected exposure remains a metered-camera instruction. Any published shutter or aperture
 * compensation moves the effective film exposure separately; an unknown compensation never does.
 */
data class ShootingPlan(
    val metered: Exposure,
    val reciprocity: ReciprocityResult,
    val addedStops: Double,
) {
    val effectiveEv get() = metered.ev - addedStops
    val hasCompensation get() = abs(addedStops) >= 1.0 / 60
    val uncertain get() = reciprocity.status == ReciprocityStatus.UNPUBLISHED
}

fun shootingPlan(film: Film, exposure: Exposure): ShootingPlan {
    val correction =
        if (film.digital)
            ReciprocityResult(
                exposure.t,
                exposure.t,
                ReciprocityStatus.UNCHANGED,
                "数字响应模型 · 不作胶片倒易律换算",
                null,
            )
        else reciprocity(film.name, exposure.t)
    val shutterStops =
        correction.exposureSeconds?.takeIf {
            correction.meteredSeconds > 0 && correction.meteredSeconds.isFinite() &&
                it.isFinite() && it > 0
        }?.let { log2(it / correction.meteredSeconds) }
    // Aperture/filter advice is an exposure instruction, not an invented shutter-time multiplier.
    val added = shutterStops ?: correction.apertureCompensationStops ?: 0.0
    return ShootingPlan(exposure, correction, if (added.isFinite()) added else 0.0)
}

/** Compare exact EV offsets first; only the final Apply action quantizes to a camera shutter. */
fun shiftedMeteredExposure(base: Exposure, additionalStops: Double): Exposure =
    base.copy(t = base.t * 2.0.pow(additionalStops))

fun nearestMeteredShutter(base: Exposure, additionalStops: Double): Exposure {
    val target = shiftedMeteredExposure(base, additionalStops)
    return target.copy(t = shutters.minBy { abs(log2(it / target.t)) })
}

/** Symmetric rounding at half steps: ±0.5 EV maps to ±2/3, never a signed-direction bias. */
fun nearestExposureThirds(stops: Double): Int {
    require(stops.isFinite())
    return round(stops * 3).toInt()
}

data class ExposureAssessment(
    val shooting: ShootingPlan,
    val shadowFraction: Double,
    val highlightFraction: Double,
    val cameraBlackFraction: Double,
    val cameraWhiteFraction: Double,
    val cameraChannelFraction: Double,
    val shadowEv: Double,
    val highlightEv: Double,
    val shadowRelativeEv: Double,
    val highlightRelativeEv: Double,
    val filmLow: Double,
    val filmHigh: Double,
) {
    val sampleSpan get() = highlightEv - shadowEv
    val workingSpan get() = filmHigh - filmLow
    val primaryTonesFit
        get() = shadowRelativeEv >= filmLow && highlightRelativeEv <= filmHigh
    val bothEndsNeedCompromise
        get() = shadowRelativeEv < filmLow && highlightRelativeEv > filmHigh
    val cameraUncertainFraction
        get() = max(cameraBlackFraction + cameraWhiteFraction, cameraChannelFraction)
}

private fun sceneQuantile(
    histogram: IntArray,
    response: DoubleArray,
    cameraEv: Double,
    q: Double,
): Double? {
    val total = histogram.sumOf { it.toLong() }
    if (
        histogram.size != 256 || response.size != 256 || total <= 0 || !cameraEv.isFinite() ||
            histogram.any { it < 0 } || response.any { !it.isFinite() } ||
            (1 until response.size).any { response[it - 1] > response[it] }
    ) return null
    val target = q.coerceIn(0.0, 1.0) * (total - 1)
    var before = 0L
    for (code in histogram.indices) {
        val after = before + histogram[code]
        if (target < after) {
            val part = (target - before) / histogram[code]
            val end = if (code == 255) response[code] else response[code + 1]
            return cameraEv + response[code] + (end - response[code]) * part
        }
        before = after
    }
    return cameraEv + response[255]
}

/**
 * Uses the same luminance mapping as the scene histogram and spot meter. The 5th and 95th
 * percentiles summarize ordinary scene tones; raw pixel fractions still report smaller risks.
 * film.low/high are working estimates from the selected curve profile, not certified clip points.
 */
fun assessExposure(
    histogram: IntArray,
    response: DoubleArray,
    cameraEv: Double,
    film: Film,
    shooting: ShootingPlan,
    cameraLimits: CameraLimits? = null,
): ExposureAssessment? {
    if (
        histogram.size != 256 || response.size != 256 || !cameraEv.isFinite() ||
            !shooting.effectiveEv.isFinite() || histogram.any { it < 0 } ||
            !film.low.isFinite() || !film.high.isFinite() || film.low >= film.high
    ) return null
    val total = histogram.sumOf { it.toLong() }
    if (total <= 0) return null
    if (
        cameraLimits != null &&
        (cameraLimits.validPixels != total || cameraLimits.blackPixels !in 0..total ||
            cameraLimits.whitePixels !in 0..total || cameraLimits.channelLimitPixels !in 0..total)
    ) return null
    val delta = cameraEv - shooting.effectiveEv
    fun relative(code: Int) = delta + response[code]
    fun fraction(count: Long) = count.toDouble() / total
    val shadowRelative = sceneQuantile(histogram, response, cameraEv, .05)?.minus(
        shooting.effectiveEv,
    ) ?: return null
    val highlightRelative = sceneQuantile(histogram, response, cameraEv, .95)?.minus(
        shooting.effectiveEv,
    ) ?: return null
    var shadows = 0L
    var highlights = 0L
    for (code in histogram.indices) {
        when (film.limit(relative(code))) {
            FilmLimit.SHADOW -> shadows += histogram[code]
            FilmLimit.HIGHLIGHT -> highlights += histogram[code]
            FilmLimit.IN_RANGE -> Unit
        }
    }
    return ExposureAssessment(
        shooting,
        fraction(shadows),
        fraction(highlights),
        fraction(cameraLimits?.blackPixels ?: histogram[0].toLong()),
        fraction(cameraLimits?.whitePixels ?: histogram[255].toLong()),
        fraction(cameraLimits?.channelLimitPixels ?: 0),
        shooting.effectiveEv + shadowRelative,
        shooting.effectiveEv + highlightRelative,
        shadowRelative,
        highlightRelative,
        film.low,
        film.high,
    )
}

data class ExposureTradeoff(
    val requestedStops: Double,
    val metered: Exposure,
    val assessment: ExposureAssessment,
)

data class SafetyAnalysis(
    val current: ExposureAssessment,
    val referenceEv: Double,
    val referencePoint: Int?,
    val referenceLabel: String,
    val goal: ToneGoal,
    val recommendedStops: Double,
    val comparisons: List<ExposureTradeoff>,
)

/** A selected point wins over percentile fallbacks. A deleted point never leaks into the result. */
fun analyzeSafety(
    histogram: IntArray,
    response: DoubleArray,
    cameraEv: Double,
    film: Film,
    exposure: Exposure,
    cameraLimits: CameraLimits? = null,
    selectedPoint: Pair<Int, Double>? = null,
    goal: ToneGoal = ToneGoal.MIDTONE,
    candidateExposure: (Double) -> Exposure = { shiftedMeteredExposure(exposure, it) },
): SafetyAnalysis? {
    if (selectedPoint != null && (!selectedPoint.second.isFinite() || selectedPoint.first <= 0)) {
        return null
    }
    val shooting = shootingPlan(film, exposure)
    val current = assessExposure(histogram, response, cameraEv, film, shooting, cameraLimits)
        ?: return null
    val fallbackQ = when (goal) {
        ToneGoal.SHADOW -> .05
        ToneGoal.MIDTONE -> .50
        ToneGoal.HIGHLIGHT -> .95
    }
    val fallbackLabel = when (goal) {
        ToneGoal.SHADOW -> "5% 参考暗部"
        ToneGoal.MIDTONE -> "亮度中位数"
        ToneGoal.HIGHLIGHT -> "95% 参考亮部"
    }
    val referenceEv = selectedPoint?.second
        ?: sceneQuantile(histogram, response, cameraEv, fallbackQ)
        ?: return null
    val recommended = recommendOffset(film, exposure, referenceEv, goal.zone) ?: return null
    val comparisons = listOf(-.5, 0.0, .5).map { stops ->
        // Preview the actual quantized pair that Apply will select, including A/S locked controls.
        val metered = candidateExposure(stops)
        ExposureTradeoff(
            stops,
            metered,
            assessExposure(
                histogram,
                response,
                cameraEv,
                film,
                shootingPlan(film, metered),
                cameraLimits,
            ) ?: return null,
        )
    }
    return SafetyAnalysis(
        current,
        referenceEv,
        selectedPoint?.first,
        selectedPoint?.let { "测光点 #" + it.first } ?: fallbackLabel,
        goal,
        recommended,
        comparisons,
    )
}

/** Solve for subject placement after reciprocity, not only for nominal shutter equivalence. */
fun recommendOffset(
    film: Film,
    exposure: Exposure,
    subjectEv: Double,
    targetZone: Double,
): Double? {
    if (!subjectEv.isFinite() || !targetZone.isFinite() || !exposure.ev.isFinite()) return null
    fun zone(offset: Double): Double {
        val candidate = shiftedMeteredExposure(exposure, offset)
        return subjectEv - shootingPlan(film, candidate).effectiveEv + 5
    }
    var low = -30.0
    var high = 30.0
    if (zone(low) > targetZone || zone(high) < targetZone) return null
    repeat(48) {
        val middle = (low + high) / 2
        if (zone(middle) < targetZone) low = middle else high = middle
    }
    return ((low + high) / 2).takeIf { it.isFinite() }
}

fun zoneAt(shooting: ShootingPlan, sceneEv: Double): Double = sceneEv - shooting.effectiveEv + 5

fun percentLabel(fraction: Double): String = when {
    fraction <= 0 -> "0"
    fraction < .001 -> "<0.1"
    else -> "%.1f".format(Locale.US, fraction * 100)
}

/** A compact, non-definitive summary suitable for the safety panel and accessibility text. */
fun SafetyAnalysis.decisionLabel(): String {
    val currentZone = zoneAt(current.shooting, referenceEv)
    return "%s：%.1f 区 → 目标 %.0f 区，建议 %s EV".format(
        Locale.US, referenceLabel, currentZone, goal.zone, signed(recommendedStops),
    )
}
