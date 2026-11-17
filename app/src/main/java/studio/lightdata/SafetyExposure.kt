package studio.lightdata

import kotlin.math.*



enum class ToneGoal(val zone: Double, val shortLabel: String, val dialogLabel: String) {
    SHADOW(3.0, "暗部优先", "保留暗部纹理（III 区仅作参考）"),
    MIDTONE(5.0, "主体优先", "主体按中间调呈现（V 区仅作参考）"),
    HIGHLIGHT(7.0, "高光优先", "保留高光层次（VII 区仅作参考）"),
}

/** Distinguishes supporting evidence from a physical, film-out guarantee. */
enum class FilmEvidence(val label: String) {
    MANUFACTURER_CURVE("厂家曲线采样"),
    FAMILY_APPROXIMATION("乳剂类别近似"),
    USER_MODEL("自定义响应模型"),
}

/**
 * Reciprocity is a shooting instruction, not a universal translation of the ordinary
 * characteristic curve. Sparse time/aperture guidance does not establish final density.
 */
data class ShootingPlan(
    val metered: Exposure,
    val reciprocity: ReciprocityResult,
) {
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
    return ShootingPlan(exposure, correction)
}

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
    val shadowRelativeEv: Double,
    val highlightRelativeEv: Double,
    val filmLow: Double,
    val filmHigh: Double,
) {
    val primaryTonesFit
        get() = shadowRelativeEv >= filmLow && highlightRelativeEv <= filmHigh
    val bothEndsNeedCompromise
        get() = shadowRelativeEv < filmLow && highlightRelativeEv > filmHigh
    val hasLuminanceLimit get() = cameraBlackFraction > 0 || cameraWhiteFraction > 0
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
 * Uses the same luminance mapping as the histogram and spot meter. Quantiles support the scene
 * envelope internally; they do not silently become a subject or a shooting recommendation.
 * film.low/high are working estimates from the selected profile, not certified clip points.
 */
fun assessExposure(
    histogram: IntArray,
    response: DoubleArray,
    cameraEv: Double,
    film: Film,
    shooting: ShootingPlan,
): ExposureAssessment? {
    if (
        histogram.size != 256 || response.size != 256 || !cameraEv.isFinite() ||
            !shooting.metered.ev.isFinite() || histogram.any { it < 0 } ||
            !film.low.isFinite() || !film.high.isFinite() || film.low >= film.high
    ) return null
    val total = histogram.sumOf { it.toLong() }
    if (total <= 0) return null
    val delta = cameraEv - shooting.metered.ev
    fun fraction(count: Long) = count.toDouble() / total
    val shadowRelative = sceneQuantile(histogram, response, cameraEv, .05)?.minus(
        shooting.metered.ev,
    ) ?: return null
    val highlightRelative = sceneQuantile(histogram, response, cameraEv, .95)?.minus(
        shooting.metered.ev,
    ) ?: return null
    var shadows = 0L
    var highlights = 0L
    for (code in histogram.indices) {
        when (film.limit(delta + response[code])) {
            FilmLimit.SHADOW -> shadows += histogram[code]
            FilmLimit.HIGHLIGHT -> highlights += histogram[code]
            FilmLimit.IN_RANGE -> Unit
        }
    }
    return ExposureAssessment(
        shooting,
        fraction(shadows),
        fraction(highlights),
        fraction(histogram[0].toLong()),
        fraction(histogram[255].toLong()),
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
    val referenceEv: Double?,
    val referencePoint: Int?,
    val goal: ToneGoal,
    val recommendedStops: Double?,
    val comparisons: List<ExposureTradeoff>,
)

fun analyzeSafety(
    histogram: IntArray,
    response: DoubleArray,
    cameraEv: Double,
    film: Film,
    exposure: Exposure,
    selectedPoint: Pair<Int, Double>? = null,
    goal: ToneGoal = ToneGoal.MIDTONE,
    candidateExposure: (Double) -> Exposure = { shiftedMeteredExposure(exposure, it) },
): SafetyAnalysis? {
    if (selectedPoint != null && (!selectedPoint.second.isFinite() || selectedPoint.first <= 0)) {
        return null
    }
    val current = assessExposure(
        histogram, response, cameraEv, film, shootingPlan(film, exposure),
    ) ?: return null
    val recommended = selectedPoint?.let {
        recommendOffset(exposure, it.second, goal.zone) ?: return null
    }
    val comparisons = listOf(-.5, 0.0, .5).map { stops ->
        // Preview the actual quantized pair that Apply will select, including A/S locked controls.
        val metered = candidateExposure(stops)
        ExposureTradeoff(
            stops,
            metered,
            assessExposure(histogram, response, cameraEv, film, shootingPlan(film, metered))
                ?: return null,
        )
    }
    return SafetyAnalysis(
        current,
        selectedPoint?.second,
        selectedPoint?.first,
        goal,
        recommended,
        comparisons,
    )
}

/** Solve continuous subject placement. No automatic compensation is made without a subject. */
fun recommendOffset(
    exposure: Exposure,
    subjectEv: Double,
    targetZone: Double,
): Double? {
    if (!subjectEv.isFinite() || !targetZone.isFinite() || !exposure.ev.isFinite()) return null
    val offset = exposure.ev - subjectEv + targetZone - 5
    return offset.takeIf { it.isFinite() }
}

fun zoneAt(shooting: ShootingPlan, sceneEv: Double): Double = sceneEv - shooting.metered.ev + 5

/** A subject-specific suggestion; no invented automatic subject when a point is not selected. */
fun SafetyAnalysis.decisionLabel(): String? {
    val point = referencePoint ?: return null
    val sceneEv = referenceEv ?: return null
    val offset = recommendedStops ?: return null
    val currentZone = zoneAt(current.shooting, sceneEv)
    return "#%d：%.1f 区 → %s · 建议 %s EV".format(
        java.util.Locale.US, point, currentZone, goal.shortLabel, signed(offset),
    )
}
