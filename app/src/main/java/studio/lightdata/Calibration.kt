package studio.lightdata

import kotlin.math.*

data class Bracket(val seconds: Double, val samples: DoubleArray, val repeat: DoubleArray)
data class Calibration(
    val response: DoubleArray, val stops: Double, val channel: String, val iso: Int,
    val legacyDecode: Boolean = false,
) {
    private val adapted = mutableMapOf<YuvEncoding, DoubleArray>()
    fun responseFor(encoding: YuvEncoding): DoubleArray =
        if (!legacyDecode) response else synchronized(adapted) {
            adapted.getOrPut(encoding) { encoding.legacyLut(response) }
        }
}
class CalibrationIssue(message: String) : Exception(message)

private fun DoubleArray.quantile(q: Double) = this[(q * lastIndex).roundToInt()]
private fun median(values: List<Double>) = values.sorted()[values.size / 2]
private fun validFrames(frames: List<Bracket>): Boolean {
    val count = frames.firstOrNull()?.samples?.size ?: return false
    return count >= 32 && frames.all { frame ->
        frame.seconds.isFinite() && frame.seconds > 0 &&
            frame.samples.size == count && frame.repeat.size == count &&
            frame.samples.all { it in 0.0..1.0 } && frame.repeat.all { it in 0.0..1.0 }
    }
}

private fun motionCheck(grids: List<DoubleArray>, distributions: List<DoubleArray>) {
    // The same patch keeps its brightness rank as shutter time changes. Compare only
    // midtones, where floor noise and clipping cannot conceal or create a rank change.
    for (i in 1 until grids.size) {
        val before = distributions[i - 1]
        val after = distributions[i]
        if (before.quantile(.9) - before.quantile(.1) < .15 ||
            after.quantile(.9) - after.quantile(.1) < .15) continue
        var common = 0
        var shifted = 0
        for (j in grids[i].indices) {
            val left = grids[i - 1][j]
            val right = grids[i][j]
            if (left !in .08.. .88 || right !in .08.. .88) continue
            common++
            // Compare ranks at a tolerance of three 8-bit codes to allow for ties
            // (e.g. a card has hundreds of equal pixels at each step).
            val low = before.lowerBound(left - .012)
            val high = before.lowerBound(left + .012)
            val lowAfter = after.lowerBound(right - .012)
            val highAfter = after.lowerBound(right + .012)
            if (max(low - highAfter, lowAfter - high) > .16 * before.size) shifted++
        }
        if (common >= grids[i].size / 8 && shifted > .22 * common)
            throw CalibrationIssue("相邻曝光间画面发生移动，请固定手机后重试")
    }
}

private fun DoubleArray.lowerBound(value: Double): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (this[middle] < value) low = middle + 1 else high = middle
    }
    return low
}

/** Weighted isotonic regression keeps a noisy knot from flattening all subsequent knots. */
private fun monotone(means: DoubleArray, weights: IntArray): DoubleArray {
    val levels = DoubleArray(means.size)
    val mass = IntArray(means.size)
    val end = IntArray(means.size)
    var last = -1
    for (index in means.indices) {
        last++
        levels[last] = means[index]
        mass[last] = weights[index]
        end[last] = index
        while (last > 0 && levels[last] < levels[last - 1]) {
            val combined = mass[last - 1] + mass[last]
            levels[last - 1] += (levels[last] - levels[last - 1]) * mass[last] / combined
            mass[last - 1] = combined
            end[last - 1] = index
            last--
        }
    }
    val result = DoubleArray(means.size)
    var start = 0
    for (i in 0..last) {
        result.fill(levels[i], start, end[i] + 1)
        start = end[i] + 1
    }
    return result
}

/** A fixed track follows either a chart patch or a quantile through the exposure bracket. */
private fun tracks(method: String, grids: List<DoubleArray>, sorted: List<DoubleArray>): List<DoubleArray> {
    if (method != "阶梯卡") {
        val count = if (method == "灰卡") 1 else 19
        return List(count) { j ->
            DoubleArray(grids.size) { sorted[it].quantile((j + 1.0) / (count + 1)) }
        }
    }
    if (grids.first().size != 32 * 24) throw CalibrationIssue("阶梯卡取样网格不完整")
    return List(9) { j ->
        val col = ((j + .5) * 32 / 9).toInt()
        DoubleArray(grids.size) { i ->
            median((8..15).map { row -> grids[i][row * 32 + col] })
        }
    }
}

/**
 * Fits the inverse preview transfer in stops using actual exposure durations. Reported
 * preview range is restricted to the measured response above the three-sigma dark floor.
 */
fun fitResponse(frames: List<Bracket>, method: String, step: Double = 1.0): Pair<DoubleArray, Double> {
    if (method !in setOf("灰卡", "阶梯卡", "现场") || (method == "阶梯卡" && step !in .1..3.0))
        throw CalibrationIssue("校准参数无效")
    if (!validFrames(frames) || frames.distinctBy { it.seconds }.size < 5)
        throw CalibrationIssue("有效曝光样本不足，请重新采集")

    val ordered = frames.sortedBy { it.seconds }
    if (log2(ordered.last().seconds / ordered.first().seconds) < 4)
        throw CalibrationIssue("实际曝光跨度不足，请调整照明")
    val grids = ordered.map { b -> DoubleArray(b.samples.size) { i -> (b.samples[i] + b.repeat[i]) / 2 } }
    val sorted = grids.map { it.sortedArray() }
    val ref = sorted.indices.minBy { abs(sorted[it].quantile(.5) - .46) }
    if (sorted.first().quantile(.05) > .08 || sorted.last().quantile(.95) < .97)
        throw CalibrationIssue("未同时覆盖暗部和饱和高光，请调整照明或场景")
    if (method == "现场" && sorted[ref].quantile(.95) - sorted[ref].quantile(.05) < .18)
        throw CalibrationIssue("现场明暗跨度不足，请选择同时包含阴影和亮部的场景")
    if (method == "灰卡" && sorted[ref].quantile(.85) - sorted[ref].quantile(.15) > .17)
        throw CalibrationIssue("灰卡受光不均或未充满画面")
    for (b in ordered) {
        val changed = b.samples.indices.count { abs(b.samples[it] - b.repeat[it]) > .065 }
        if (changed > .12 * b.samples.size)
            throw CalibrationIssue("检测到移动或光源闪烁，请固定手机")
    }
    if (method != "灰卡") motionCheck(grids, sorted)

    val tracks = tracks(method, grids, sorted)
    val times = DoubleArray(ordered.size) { log2(ordered[it].seconds / ordered[ref].seconds) }
    val valid = tracks.map { t -> t.indices.filter { t[it] in (2.0 / 255).. .985 } }
    if (method == "阶梯卡") {
        if (valid.any { it.size < 2 } || (0 until 8).any { j ->
                tracks[j].indices.none { i ->
                    tracks[j][i] in .04.. .88 && tracks[j + 1][i] > tracks[j][i] + .008
                }
            }) throw CalibrationIssue("请将九级阶梯自暗至亮对齐取景框")
    }
    var lut = defaultResponse.copyOf()
    var firstCode = 0
    var lastCode = 255
    repeat(12) {
        val bins = Array(256) { mutableListOf<Double>() }
        for ((j, track) in tracks.withIndex()) {
            if (valid[j].size < 2) continue
            val offset = if (method == "阶梯卡") j * step else
                median(valid[j].map { i -> lut[(track[i] * 255).roundToInt()] - times[i] })
            for (i in valid[j]) bins[(track[i] * 255).roundToInt()].add(offset + times[i])
        }
        val codes = bins.indices.filter { bins[it].isNotEmpty() }
        if (codes.size < 5 || codes.first() > 80 || codes.last() < 150)
            throw CalibrationIssue("有效灰阶未覆盖中灰两侧，请调整照明")
        firstCode = codes.first()
        lastCode = codes.last()
        val values = monotone(DoubleArray(codes.size) { median(bins[codes[it]]) },
            IntArray(codes.size) { bins[codes[it]].size })
        // Interpolate in log code space. This models a power-law preview without
        // inventing an unbounded tail below the lowest measured signal.
        lut = DoubleArray(256) { b ->
            val pos = codes.binarySearch(b)
            if (pos >= 0) values[pos] else {
                val r = (-pos - 1).coerceIn(1, codes.lastIndex)
                val l = r - 1
                val width = ln(codes[r].toDouble() / codes[l])
                val fraction = ln(max(1, b).toDouble() / codes[l]) / width
                val v = values[l] + fraction * (values[r] - values[l])
                v.coerceIn(values.first() - 1.0, values.last() + 1.0)
            }
        }
        val zero = lut[117]
        for (code in lut.indices) lut[code] -= zero
    }
    val residuals = mutableListOf<Double>()
    val chartOffsets = mutableListOf<Double>()
    for ((j, track) in tracks.withIndex()) {
        val offsets = valid[j].filter { track[it] in .04.. .95 }
            .map { i -> lut[(track[i] * 255).roundToInt()] - times[i] }
        if (offsets.size < 2) continue
        val center = median(offsets)
        residuals.addAll(offsets.map { abs(it - center) })
        if (method == "阶梯卡") chartOffsets.add(center - j * step)
    }
    if (residuals.isEmpty() || residuals.toDoubleArray().sortedArray().quantile(.9) > .65)
        throw CalibrationIssue("曝光序列响应不一致，请固定手机并使用稳定光源")
    if (chartOffsets.isNotEmpty() && chartOffsets.max() - chartOffsets.min() > .65)
        throw CalibrationIssue("阶梯间隔与输入的 EV 不符，请确认参考卡")

    val darkest = ordered.first()
    val darkCut = sorted.first().quantile(.25)
    val darkPixels = grids.first().indices.filter { grids.first()[it] <= darkCut }
    val variance = darkPixels.sumOf { (darkest.samples[it] - darkest.repeat[it]).pow(2) } / (2 * darkPixels.size)
    val floor = sorted.first().quantile(.05) + 3 * sqrt(variance)
    val floorCode = max(firstCode, ceil(floor * 255).toInt()).coerceIn(1, 254)
    val range = lut[lastCode] - lut[floorCode]
    if (range !in 3.0..22.0)
        throw CalibrationIssue("可用范围无法可靠拟合，请调整场景重试")
    return lut to range
}

/** RAW is linear after black subtraction. Use temporal differences of dim pixels only. */
fun estimateRawStops(frames: List<Bracket>): Double? {
    if (!validFrames(frames) || frames.size < 5) return null
    val ordered = frames.sortedBy { it.seconds }
    val first = ordered.first()
    val darkest = DoubleArray(first.samples.size) { (first.samples[it] + first.repeat[it]) / 2 }
    val threshold = darkest.sortedArray().quantile(.25)
    if (threshold > .025 || ordered.last().samples.sortedArray().quantile(.98) < .97) return null
    val indices = darkest.indices.filter { darkest[it] <= threshold }
    val rms = sqrt(indices.sumOf { (first.samples[it] - first.repeat[it]).pow(2) } / (2 * indices.size))
    if (rms < 1.0 / 65535) return null
    // An unlit capture can verify the read floor. If the scene still contributes light,
    // its dimmest measured region is a conservative bound on the achieved range.
    val floor = darkest.sortedArray().quantile(.05) + 3 * rms
    val checkLow = max(.02, 5 * floor)
    var consistentPairs = 0
    for ((a, b) in ordered.zipWithNext()) {
        val expected = log2(b.seconds / a.seconds)
        val errors = a.samples.indices.mapNotNull { i ->
            val signalA = (a.samples[i] + a.repeat[i]) / 2
            val signalB = (b.samples[i] + b.repeat[i]) / 2
            if (signalA in checkLow.. .8 && signalB in checkLow.. .92)
                abs(log2(signalB / signalA) - expected) else null
        }
        if (errors.size < max(16, first.samples.size / 32)) continue
        if (median(errors) > .3) return null
        consistentPairs++
    }
    if (consistentPairs < 2) return null
    return log2(1 / floor).takeIf { it in 3.0..22.0 }
}
