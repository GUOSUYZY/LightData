package studio.lightdata

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.ArrayDeque
import kotlin.math.*

class Pose(ctx: Context) : SensorEventListener {
    private val sensors = ctx.getSystemService(SensorManager::class.java)
    private val sensor =
        sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private data class Reading(val timestamp: Long, val rotation: FloatArray)

    private val history = ArrayDeque<Reading>()
    val available
        get() = sensor != null

    @Volatile
    var matrix: FloatArray? = null
        private set

    fun start() {
        sensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensors.unregisterListener(this)
        synchronized(history) {
            history.clear()
            matrix = null
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, e.values)
        synchronized(history) {
            matrix = rotation
            history.addLast(Reading(e.timestamp, rotation))
            while (history.size > 80) history.removeFirst()
        }
    }

    // Only pass image timestamps from cameras reporting SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME.
    fun axes(rotation: Int, timestampNs: Long = 0L): FloatArray? {
        val device =
            if (timestampNs == 0L) matrix
            else
                synchronized(history) {
                    history.minByOrNull { abs(it.timestamp - timestampNs) }?.rotation
                }
        return device?.let { displayAxes(it, rotation) }
    }
}

// Camera basis uses +X right, +Y up, -Z through the back of the device.
// These column permutations are equivalent to SensorManager.remapCoordinateSystem.
internal fun displayAxes(device: FloatArray, rotation: Int): FloatArray {
    val output = FloatArray(9)
    for (row in 0..2) {
        val i = row * 3
        val x = device[i]
        val y = device[i + 1]
        output[i] =
            when (rotation) {
                1 -> -y
                2 -> -x
                3 -> y
                else -> x
            }
        output[i + 1] =
            when (rotation) {
                1 -> x
                2 -> -y
                3 -> -x
                else -> y
            }
        output[i + 2] = device[i + 2]
    }
    return output
}

fun upright(f: Frame, turn: Int): Frame {
    val angle = Math.floorMod(turn, 360)
    if (angle == 0) return f
    require(angle == 90 || angle == 180 || angle == 270)
    val width = if (angle == 180) f.w else f.h
    val height = if (angle == 180) f.h else f.w
    val buffer = IntArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        val index =
            when (angle) {
                90 -> (f.h - 1 - x) * f.w + y
                180 -> (f.h - 1 - y) * f.w + f.w - 1 - x
                else -> x * f.w + f.w - 1 - y
            }
        buffer[y * width + x] = f.pixels[index]
    }
    return f.copy(w = width, h = height, pixels = buffer)
}

fun crop(f: Frame, source: Field, target: Field): Frame {
    val pixelsWide = f.w * target.x / source.x
    val pixelsHigh = f.h * target.y / source.y
    // A wider-than-available lens shows unobserved areas as transparent. Bound allocations
    // even for a large custom format coupled to a very short focal length.
    val scale = min(1.0, max(f.w, f.h) / max(pixelsWide, pixelsHigh))
    val width = max(1, (pixelsWide * scale).roundToInt())
    val height = max(1, (pixelsHigh * scale).roundToInt())
    if (width == f.w && height == f.h && source == target) return f
    val buffer = IntArray(width * height)
    if (source.covers(target)) {
        val left = (f.w - width) / 2
        val top = (f.h - height) / 2
        for (y in 0 until height) {
            val start = (top + y) * f.w + left
            f.pixels.copyInto(buffer, y * width, start, start + width)
        }
    } else {
        val xs = IntArray(width) { floor(((it + .5) / width - .5) * pixelsWide + f.w * .5).toInt() }
        for (y in 0 until height) {
            val sy = floor(((y + .5) / height - .5) * pixelsHigh + f.h * .5).toInt()
            if (sy !in 0 until f.h) continue
            for (x in xs.indices) if (xs[x] in 0 until f.w)
                buffer[y * width + x] = f.pixels[sy * f.w + xs[x]]
        }
    }
    return f.copy(w = width, h = height, pixels = buffer)
}

// R_to transposed times R_from transforms a ray expressed in the "from" camera into "to".
private fun relative(to: FloatArray, from: FloatArray) =
    DoubleArray(9) { i -> (0..2).sumOf { k -> to[k * 3 + i / 3].toDouble() * from[k * 3 + i % 3] } }

private const val MARGIN = 1.15
private const val LONG_EDGE = 720
internal const val GRID_WIDTH = 9
internal const val GRID_HEIGHT = 7

data class FinderPoint(val x: Double, val y: Double) {
    fun turned(turn: Int): FinderPoint =
        when (Math.floorMod(turn, 360)) {
            90 -> FinderPoint(1 - y, x)
            180 -> FinderPoint(1 - x, 1 - y)
            270 -> FinderPoint(y, 1 - x)
            else -> this
        }
}

data class ScanGuide(
    val captures: Int,
    val coverage: Double,
    val aim: FinderPoint,
    val next: FinderPoint?,
    val needsOverlap: Boolean,
    val tiles: DoubleArray,
    val mapTurn: Int,
    val homeDirection: FinderPoint?,
    val settling: Boolean,
)

class Mosaic(
    val target: Field,
    private val center: FloatArray,
    private val initial: Frame,
    private val response: DoubleArray = defaultResponse,
) {
    private val bounds = Field(target.x * MARGIN, target.y * MARGIN)
    val w = max(1, (LONG_EDGE * min(1.0, bounds.aspect)).roundToInt())
    val h = max(1, (LONG_EDGE * min(1.0, 1 / bounds.aspect)).roundToInt())
    private val atlas = IntArray(w * h)
    private val tileCount = IntArray(GRID_WIDTH * GRID_HEIGHT)
    private val tileSize = IntArray(GRID_WIDTH * GRID_HEIGHT)
    private val xs = DoubleArray(w) { (2 * (it + .5) / w - 1) * bounds.x }
    private val ys = DoubleArray(h) { (1 - 2 * (it + .5) / h) * bounds.y }
    private var count = 0
    val coverage
        get() = count.toDouble() / atlas.size

    val ready
        get() = count == atlas.size

    init {
        for (y in 0 until h) for (x in 0 until w) tileSize[tile(x, y)]++
    }

    private fun tile(x: Int, y: Int) = y * GRID_HEIGHT / h * GRID_WIDTH + x * GRID_WIDTH / w

    fun grid() = BooleanArray(tileSize.size) { tileCount[it] == tileSize[it] }

    fun tileCoverage() = DoubleArray(tileSize.size) {
        if (tileSize[it] == 0) 1.0 else tileCount[it].toDouble()/tileSize[it]
    }

    fun aim(pose: FloatArray): FinderPoint {
        val r = relative(center, pose)
        val z = max(.001, r[8])
        return FinderPoint(.5 - .5 * r[2] / (bounds.x * z), .5 + .5 * r[5] / (bounds.y * z))
    }

    fun nextUncovered(aim: FinderPoint): FinderPoint? {
        if (ready) return null
        var closest = Double.POSITIVE_INFINITY
        var selected = -1
        for (i in tileSize.indices) {
            if (tileSize[i] == tileCount[i]) continue
            val x = (i % GRID_WIDTH + .5) / GRID_WIDTH
            val y = (i / GRID_WIDTH + .5) / GRID_HEIGHT
            val score = hypot((x - aim.x) * bounds.x, (y - aim.y) * bounds.y)
            if (score < closest) {
                closest = score
                selected = i
            }
        }
        if (selected < 0) return null
        val col = selected % GRID_WIDTH
        val row = selected / GRID_WIDTH
        fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
        val left = ceilDiv(w * col, GRID_WIDTH)
        val right = ceilDiv(w * (col + 1), GRID_WIDTH)
        val top = ceilDiv(h * row, GRID_HEIGHT)
        val bottom = ceilDiv(h * (row + 1), GRID_HEIGHT)
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        var best = Int.MAX_VALUE
        var point: FinderPoint? = null
        for (y in top until bottom) for (x in left until right) {
            if (atlas[y * w + x] != 0) continue
            val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if (d < best) {
                best = d
                point = FinderPoint((x + .5) / w, (y + .5) / h)
            }
        }
        return point
    }

    // Incoming code values are brought into the center frame's exposure. The inverse
    // response is calibrated on luminance, so apply its gain to RGB without altering hue.
    private fun gains(delta: Double): DoubleArray {
        if (abs(delta) < .002) return DoubleArray(256) { 1.0 }
        if (response === defaultResponse) {
            val gain = 2.0.pow(delta)
            return DoubleArray(256) { gain }
        }
        return DoubleArray(256) { y ->
            val desired = response[y] + delta
            var l = 0
            var r = 255
            while (l < r) {
                val m = (l + r) / 2
                if (response[m] < desired) l = m + 1 else r = m
            }
            val below = max(0, l - 1)
            val span = response[l] - response[below]
            val fraction = if (span > 0.0) ((desired - response[below]) / span).coerceIn(0.0, 1.0) else 0.0
            val targetCode = below + fraction
            srgbToLinear(targetCode / 255.0) / srgbToLinear(max(.5, y.toDouble()) / 255.0)
        }
    }

    private fun color(c: Int, gain: Double): Int {
        if (gain == 1.0) return c
        val r = scaleSrgbCode(c ushr 16 and 255, gain)
        val g = scaleSrgbCode(c ushr 8 and 255, gain)
        val b = scaleSrgbCode(c and 255, gain)
        return (c and -0x1000000) or (r shl 16) or (g shl 8) or b
    }

    private class Projection(val r: DoubleArray, source: Field, val width: Int, val height: Int) {
        val fx = width / (2 * source.x)
        val fy = height / (2 * source.y)

        fun depth(x: Double, y: Double) = r[8] - r[6] * x - r[7] * y

        fun u(x: Double, y: Double, depth: Double) =
            width * .5 + (r[0] * x + r[1] * y - r[2]) * fx / depth

        fun v(x: Double, y: Double, depth: Double) =
            height * .5 - (r[3] * x + r[4] * y - r[5]) * fy / depth
    }

    // Refine residual image translation in the overlap. Reject ambiguous texture and
    // small improvements; an inertial-only placement is preferable on a blank wall.
    private fun register(f: Frame, p: Projection, gain: DoubleArray): Pair<Int, Int> {
        if (count == 0) return 0 to 0
        val radius = (min(f.w, f.h) / 25).coerceIn(4, 20)
        val locations = ArrayList<Int>()
        val expected = ArrayList<Int>()
        val step = max(4, min(w, h) / 28)
        for (y in step / 2 until h - 2 step step) for (x in step / 2 until w - 2 step step) {
            val i = y * w + x
            val c = atlas[i]
            if (c == 0 || atlas[i + 2] == 0 || atlas[i + 2 * w] == 0) continue
            val value = luma(c)
            if (max(abs(value - luma(atlas[i + 2])), abs(value - luma(atlas[i + 2 * w]))) < 5)
                continue
            val z = p.depth(xs[x], ys[y])
            if (z < .001) continue
            val u = floor(p.u(xs[x], ys[y], z)).toInt()
            val v = floor(p.v(xs[x], ys[y], z)).toInt()
            if (u !in radius until f.w - radius || v !in radius until f.h - radius) continue
            locations.add(v * f.w + u)
            expected.add(value)
        }
        if (locations.size < 12) return 0 to 0
        val normalized = IntArray(256) { scaleSrgbCode(it, gain[it]) }
        fun error(dx: Int, dy: Int): Double {
            val offset = dy * f.w + dx
            var loss = 0
            for (i in locations.indices) {
                val observed = normalized[luma(f.pixels[locations[i] + offset])]
                loss += min(36, abs(observed - expected[i]))
            }
            return loss.toDouble() / locations.size + .05 * (abs(dx) + abs(dy))
        }
        val baseline = error(0, 0)
        var best = baseline
        var dx = 0
        var dy = 0
        fun evaluate(x: Int, y: Int) {
            val score = error(x, y)
            if (score < best) {
                best = score
                dx = x
                dy = y
            }
        }
        for (y in -radius..radius step 3) for (x in -radius..radius step 3) evaluate(x, y)
        val bx = dx
        val by = dy
        for (y in max(-radius, by - 2)..min(radius, by + 2)) for (x in
            max(-radius, bx - 2)..min(radius, bx + 2)) evaluate(x, y)
        return if (best < baseline * .83 && best + 1 < baseline) dx to dy else 0 to 0
    }

    fun add(frame: Frame, source: Field, pose: FloatArray): Boolean {
        if (ready || frame.id != initial.id) return false
        val p = Projection(relative(pose, center), source, frame.w, frame.h)
        // Reject disconnected islands. A sample grid over the atlas checks geometric
        // overlap before feature matching, including in low-texture scenes.
        if (count > 0) {
            var overlap = false
            val stride = max(1,min(w,h)/64)
            outer@ for (y in 0 until h step stride) for (x in 0 until w step stride) {
                if (atlas[y*w+x] == 0) continue
                val z = p.depth(xs[x],ys[y])
                if (z <= .001) continue
                val u = p.u(xs[x],ys[y],z)
                val v = p.v(xs[x],ys[y],z)
                if (u >= frame.w*.03 && u < frame.w*.97 && v >= frame.h*.03 && v < frame.h*.97) {
                    overlap = true
                    break@outer
                }
            }
            if (!overlap) return false
        }
        val gain = gains(frame.cameraEv - initial.cameraEv)
        val (ox, oy) = register(frame, p, gain)
        for (y in ys.indices) for (x in xs.indices) {
            val j = y * w + x
            if (atlas[j] != 0) continue
            val z = p.depth(xs[x], ys[y])
            if (z <= .001) continue
            val u = p.u(xs[x], ys[y], z) + ox
            val v = p.v(xs[x], ys[y], z) + oy
            if (u < 0 || v < 0 || u >= frame.w || v >= frame.h) continue
            val c = frame.pixels[v.toInt() * frame.w + u.toInt()]
            if (c ushr 24 == 0) continue
            atlas[j] = color(c, gain[luma(c)])
            count++
            tileCount[tile(x, y)]++
        }
        return true
    }

    fun homeDirection(pose: FloatArray): FinderPoint? {
        val r = relative(pose, center)
        val aim = aim(pose)
        val x = floor(aim.x*w).toInt()
        val y = floor(aim.y*h).toInt()
        if (r[8] > 0 && x in 0 until w && y in 0 until h && atlas[y*w+x] != 0) return null
        // The center ray in the current camera's right/down coordinates. At the
        // antipode either turn is valid. At the starting center no arrow is needed.
        val dx = -r[2]
        val dy = r[5]
        val length = hypot(dx,dy)
        if (length < 1e-6) return if (r[8] < 0) FinderPoint(1.0,0.0) else null
        return FinderPoint(dx/length,dy/length)
    }

    fun view(pose: FloatArray): Frame {
        val width = max(1, (w / MARGIN).roundToInt())
        val height = max(1, (h / MARGIN).roundToInt())
        val r = relative(center,pose)
        val buffer = IntArray(width * height)
        // The homography numerators and depth advance linearly across a scanline.
        val step = 2*target.x/width
        val left = (.5*step-target.x)
        val fx = w/(2*bounds.x)
        val fy = h/(2*bounds.y)
        val stepU = r[0]*step*fx
        val stepV = -r[3]*step*fy
        val stepZ = -r[6]*step
        for (y in 0 until height) {
            val dy = (1 - 2*(y+.5)/height)*target.y
            var z = r[8]-r[6]*left-r[7]*dy
            var u = (r[0]*left+r[1]*dy-r[2])*fx
            var v = -(r[3]*left+r[4]*dy-r[5])*fy
            for (x in 0 until width) {
                if (z > 0) {
                    val inverse = 1/z
                    val ax = w*.5+u*inverse
                    val ay = h*.5+v*inverse
                    if (ax >= 0 && ax < w && ay >= 0 && ay < h)
                        buffer[y*width+x] = atlas[ay.toInt()*w+ax.toInt()]
                }
                z += stepZ
                u += stepU
                v += stepV
            }
        }
        return initial.copy(w = width, h = height, pixels = buffer)
    }
}

// A scan keeps the display axes that were in use when the center was confirmed.
// Its atlas and angular reference remain valid if Android rotates the window.
class ScanSession(center: Frame, target: Field, origin: FloatArray?, response: DoubleArray) {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val mosaic = Mosaic(target, identity, center, response)
    private var orientation = identity
    private var sensorOrigin = origin
    private var originPose = identity
    private var reference: TrackingImage? = null
    private var accepted = 0
    private var lost = false
    private var anchor: FloatArray? = null
    private var since = 0L
    private var lastTime = -1L
    private var disconnected = false
    private var lastAttempt: FloatArray? = null
    private var settling = true
    private var renderedPose: FloatArray? = null
    private var rendered: Frame? = null

    private fun moved(a: FloatArray, b: FloatArray, degrees: Double): Boolean {
        var trace = 0.0
        for (i in a.indices) trace += a[i].toDouble()*b[i]
        return trace < 1 + 2*cos(Math.toRadians(degrees))
    }

    fun resumeTracking() {
        sensorOrigin = null
        reference = null
        anchor = null
        lastTime = -1L
    }

    fun capture(field: Field, sensor: FloatArray?, nowMs: Long, tracking: (() -> TrackingImage)? = null, frame: () -> Frame): Frame {
        var decoded: Frame? = null
        fun image() = decoded ?: frame().also { decoded = it }
        lost = false
        if (sensor != null) {
            if (sensorOrigin == null) {
                originPose = orientation
                sensorOrigin = sensor
            }
            val delta = relative(sensorOrigin!!, sensor)
            orientation = FloatArray(9) { i ->
                (0..2).sumOf { k -> originPose[i/3*3+k]*delta[k*3+i%3] }.toFloat()
            }
            reference = null
        } else {
            sensorOrigin = null
            val next = tracking?.invoke() ?: TrackingImage(image())
            val delta = reference?.motionTo(next,field)
            lost = reference != null && delta == null
            if (delta != null) {
                val prior = orientation
                orientation = FloatArray(9) { i ->
                    (0..2).sumOf { k -> prior[i/3*3+k]*delta[k*3+i%3] }.toFloat()
                }
            }
            if (!lost) reference = next
        }
        if (lost || anchor == null || nowMs-lastTime !in 0..500 || moved(anchor!!,orientation,.45)) {
            anchor = orientation
            since = nowMs
        }
        lastTime = nowMs
        settling = nowMs-since < 350
        if (!mosaic.ready && !lost && !settling &&
            (lastAttempt == null || moved(lastAttempt!!,orientation,1.0))) {
            val before = mosaic.coverage
            disconnected = !mosaic.add(image(),field,orientation)
            if (mosaic.coverage > before) {
                accepted++
                rendered = null
            }
            lastAttempt = orientation
        }
        if (rendered == null || moved(renderedPose!!,orientation,.06)) {
            rendered = mosaic.view(orientation)
            renderedPose = orientation
        }
        return rendered!!
    }

    fun guide(displayTurn: Int): ScanGuide {
        val aim = mosaic.aim(orientation)
        val home = mosaic.homeDirection(orientation)?.let {
            val p = FinderPoint(.5 + .5*it.x,.5 + .5*it.y).turned(displayTurn)
            FinderPoint(2*p.x-1,2*p.y-1)
        }
        return ScanGuide(accepted,mosaic.coverage,aim.turned(displayTurn),
            mosaic.nextUncovered(aim)?.turned(displayTurn),lost || disconnected,mosaic.tileCoverage(),displayTurn,home,settling)
    }
}

// Frame-to-frame alignment in angular coordinates. Correlation is insensitive to a change
// in exposure and a candidate must agree across the portion common to both real frames.
class TrackingImage(w: Int, h: Int, sample: (Double, Double) -> Int) {
    constructor(frame: Frame) : this(frame.w, frame.h, frame::sample)
    private val width = min(144,w)
    private val height = (width * h.toDouble()/w).roundToInt().coerceIn(1,192)
    private val data = IntArray(width*height) { i ->
        luma(sample((i%width+.5)/width,(i/width+.5)/height))
    }

    fun motionTo(next: TrackingImage, field: Field): DoubleArray? {
        if (width != next.width || height != next.height || min(width, height) < 16) return null
        val fx = width / (2 * field.x)
        val fy = height / (2 * field.y)
        val step = max(2, min(width, height) / 14)
        fun rotation(dx: Double, dy: Double): DoubleArray {
            val yaw = atan(dx / fx)
            val pitch = atan(dy / fy)
            val a = cos(yaw)
            val b = sin(yaw)
            val c = cos(pitch)
            val d = sin(pitch)
            return doubleArrayOf(a, b * d, b * c, 0.0, c, -d, -b, a * d, a * c)
        }
        fun score(dx: Double, dy: Double): Double {
            val r = rotation(dx, dy)
            // Vote by spatial region. A flashing display or a passing person can dominate
            // the total image variance despite occupying a small portion of a static scene.
            val regions = Array(9) { DoubleArray(6) }
            for (y in step / 2 until height step step) for (x in step / 2 until width step step) {
                val u = (x + .5 - width * .5) / fx
                val v = (height * .5 - y - .5) / fy
                val z = r[8] - r[2] * u - r[5] * v
                if (z <= 0) continue
                val px = width * .5 - .5 + (r[0] * u + r[3] * v - r[6]) * fx / z
                val py = height * .5 - .5 - (r[1] * u + r[4] * v - r[7]) * fy / z
                if (px < 0 || py < 0 || px >= width - 1 || py >= height - 1) continue
                val pos = py.toInt() * width + px.toInt()
                val tx = px - px.toInt()
                val ty = py - py.toInt()
                val top = next.data[pos] * (1 - tx) + next.data[pos + 1] * tx
                val bottom = next.data[pos + width] * (1 - tx) + next.data[pos + width + 1] * tx
                val a = data[y * width + x].toDouble()
                val b = top * (1 - ty) + bottom * ty
                val stats = regions[(y * 3 / height) * 3 + x * 3 / width]
                stats[0]++; stats[1] += a; stats[2] += b
                stats[3] += a*a; stats[4] += b*b; stats[5] += a*b
            }
            val scores = regions.mapNotNull { s ->
                val count = s[0]
                if (count < 8) return@mapNotNull null
                val varA = s[3] - s[1]*s[1]/count
                val varB = s[4] - s[2]*s[2]/count
                if (min(varA, varB) < 9*count) null
                else (s[5]-s[1]*s[2]/count)/sqrt(varA*varB)
            }.sorted()
            return if (scores.size >= 4) scores[scores.size/2] else -1.0
        }
        var bx = 0.0
        var by = 0.0
        var best = score(0.0, 0.0)
        if (best > .985) return rotation(0.0, 0.0)
        fun evaluate(x: Double, y: Double) {
            val value = score(x, y) - .0003 * (abs(x) + abs(y))
            if (value > best) {
                best = value
                bx = x
                by = y
            }
        }
        val radius = (min(width, height) / 5).coerceIn(4, 24)
        for (dy in -radius..radius step 3) for (dx in -radius..radius step 3) evaluate(
            dx.toDouble(),
            dy.toDouble(),
        )
        val cx = bx
        val cy = by
        for (dy in -3..3) for (dx in -3..3) evaluate(cx + dx, cy + dy)
        val fineX = bx
        val fineY = by
        for (dy in -2..2) for (dx in -2..2) evaluate(fineX + dx * .35, fineY + dy * .35)
        return if (best > .85) rotation(bx, by) else null
    }
}
