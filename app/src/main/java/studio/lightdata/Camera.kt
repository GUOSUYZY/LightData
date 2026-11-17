package studio.lightdata

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.DataSpace
import android.hardware.camera2.*
import android.hardware.camera2.params.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Size
import java.util.concurrent.Executor
import kotlin.math.*

data class Lens(
    val owner: String,
    val id: String,
    val c: CameraCharacteristics,
    val size: Size,
    val view: Field,
    val angle: Int,
    val compactSize: Size = size,
) {
    val label
        get() = "镜头 " + id + " · " + (2 * Math.toDegrees(atan(view.x))).roundToInt() + "°"
}

// Prefer a stream close to the display's longest edge and the sensor's native aspect.
// Reserve room for synchronized YUV packets, rotation, crop, GL upload and the mosaic.
internal fun previewDimensions(
    sizes: List<Pair<Int, Int>>, edge: Int, sensorAspect: Double, heapBytes: Long,
): Pair<Int, Int> {
    val budget = max(640L * 480, heapBytes / 80)
    val usable = sizes.filter { (w,h) -> w.toLong()*h <= budget }
    if (usable.isEmpty()) return sizes.minBy { (w,h) -> w.toLong()*h }
    return usable.minBy { (w,h) ->
        abs(log2(max(w,h).toDouble()/edge.coerceAtLeast(640))) +
            1.5*abs(log2(w.toDouble()/h/sensorAspect))
    }
}

// A capture result's crop is in active-array coordinates. The output stream
// may crop it once more to satisfy the requested YUV aspect ratio.
private fun cameraField(c: CameraCharacteristics, size: Size, focal: Double, crop: Rect?): Field? {
    val physical = c[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE] ?: return null
    val pixels = c[CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE] ?: return null
    val active = c[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE] ?: return null
    val region = crop?.let { Rect(it).takeIf { r -> r.intersect(active) } } ?: active
    val width = physical.width.toDouble() * region.width() / pixels.width
    val height = physical.height.toDouble() * region.height() / pixels.height
    val aspect = size.width.toDouble() / size.height
    val fittedWidth = min(width, height * aspect)
    return field(fittedWidth, fittedWidth / aspect, focal)
}

fun cameras(ctx: Context): List<Lens> {
    val m = ctx.getSystemService(CameraManager::class.java)
    val result = linkedMapOf<String, Lens>()
    for (owner in m.cameraIdList) {
        val logical = m.getCameraCharacteristics(owner)
        if (logical[CameraCharacteristics.LENS_FACING] != CameraCharacteristics.LENS_FACING_BACK)
            continue
        for (id in logical.physicalCameraIds.ifEmpty { setOf(owner) }) {
            val c = runCatching { m.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val sizes =
                c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.getOutputSizes(
                    ImageFormat.YUV_420_888
                ) ?: continue
            if (sizes.isEmpty()) continue
            val compact = sizes.minBy { abs(it.width - 640) + abs(it.height - 480) * 2 }
            val active = c[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE]
            val aspect = active?.let { it.width().toDouble()/it.height() } ?: 4.0 / 3
            val metrics = ctx.resources.displayMetrics
            val (w,h) = previewDimensions(sizes.map { it.width to it.height },
                max(metrics.widthPixels,metrics.heightPixels), aspect, Runtime.getRuntime().maxMemory())
            val size = Size(w,h)
            val f =
                c[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.firstOrNull()
                    ?: continue
            val view = cameraField(c, size, f.toDouble(), null) ?: continue
            val lens =
                Lens(owner, id, c, size, view, c[CameraCharacteristics.SENSOR_ORIENTATION] ?: 90, compact)
            if (owner == id || id !in result) result[id] = lens
        }
    }
    return result.values.sortedByDescending { it.view.x }
}

class Rig(
    val ctx: Context,
    val onFrame: (Packet, Double, Long, Lens) -> Unit,
    val status: (String) -> Unit,
    val calibrated: (Lens, Calibration) -> Unit,
    val previewEnabled: () -> Boolean,
) {
    private val thread = HandlerThread("LightData camera").apply { start() }
    private val h = Handler(thread.looper)
    private val executor = Executor { h.post(it) }
    private val worker = HandlerThread("LightData image").apply { start() }
    private val processor = Handler(worker.looper)
    @Volatile private var generation = 0
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var yuv: ImageReader? = null
    private var raw: ImageReader? = null
    private var lens: Lens? = null
    private var baseTime = 10_000_000L
    private var baseIso = 100
    private var scan: Scan? = null
    private var panoramaLock = false
    fun setPanoramaLock(enabled: Boolean) {
        h.post {
            if (panoramaLock != enabled) {
                panoramaLock = enabled
                if (scan == null) request()
            }
        }
    }

    private var processingPreview = false

    private class Scan(val method: String, val step: Double, val times: List<Long>, val iso: Int) {
        var index = 0
        val previews = mutableListOf<Bracket>()
        val raws = mutableListOf<Bracket>()
        var first: DoubleArray? = null
        var firstRaw: DoubleArray? = null
    }

    data class Plane(val data: ByteArray, val rowStride: Int, val pixelStride: Int)
    class Packet(val w: Int, val h: Int, val planes: List<Plane>, val encoding: YuvEncoding) {
        var exposureMs = 0.0
        fun pixel(x: Int, y: Int): Int {
            fun code(p: Plane, col: Int, row: Int) = p.data[row*p.rowStride+col*p.pixelStride].toInt() and 255
            return encoding.rgb(code(planes[0],x,y),code(planes[1],x/2,y/2),code(planes[2],x/2,y/2))
        }
        fun grid(): DoubleArray {
            val (y,u,v) = planes
            return DoubleArray(32*24) { i ->
                val col = ((.1 + .8*((i%32+.5)/32))*w).toInt()
                val row = ((.1 + .8*((i/32+.5)/24))*h).toInt()
                val rgb = encoding.rgb(
                    y.data[row*y.rowStride + col*y.pixelStride].toInt() and 255,
                    u.data[row/2*u.rowStride + col/2*u.pixelStride].toInt() and 255,
                    v.data[row/2*v.rowStride + col/2*v.pixelStride].toInt() and 255,
                )
                luma(rgb)/255.0
            }
        }
        fun frame(ev: Double, id: String, timestamp: Long): Frame {
            val (y,u,v) = planes
            val rgb = IntArray(w * h)
            for (row in 0 until h) for (col in 0 until w) {
                rgb[row*w+col] = encoding.rgb(
                    y.data[row*y.rowStride+col*y.pixelStride].toInt() and 255,
                    u.data[row/2*u.rowStride+col/2*u.pixelStride].toInt() and 255,
                    v.data[row/2*v.rowStride+col/2*v.pixelStride].toInt() and 255,
                )
            }
            return Frame(w,h,rgb,ev,id,timestamp,encoding)
        }
    }
    private var lastDataSpace: Int? = null
    private var encoding = YuvEncoding.default

    private val images = linkedMapOf<Long, Packet>()
    private val results = linkedMapOf<Long, Pair<CaptureResult, Int>>()
    private val rawGrid = linkedMapOf<Long, DoubleArray>()

    private fun clear() {
        images.clear()
        results.clear()
        rawGrid.clear()
        processingPreview = false
    }

    private fun release() {
        generation++
        scan = null
        session?.close()
        session = null
        camera?.close()
        camera = null
        yuv?.close()
        yuv = null
        raw?.close()
        raw = null
        clear()
    }

    fun stop() {
        h.post { release() }
    }

    fun destroy() {
        h.post {
            release()
            worker.quitSafely()
            thread.quitSafely()
        }
    }

    @SuppressLint("MissingPermission")
    fun open(target: Lens) {
        h.post {
            release()
            lens = target
            val gen = generation
            try {
                ctx.getSystemService(CameraManager::class.java)
                    .openCamera(
                        target.owner,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(d: CameraDevice) {
                                if (gen != generation) {
                                    d.close()
                                    return
                                }
                                camera = d
                                configure(false)
                            }

                            override fun onDisconnected(d: CameraDevice) {
                                d.close()
                                if (camera === d || gen == generation) {
                                    release()
                                    status("摄像头已断开，请重试")
                                }
                            }

                            override fun onError(d: CameraDevice, e: Int) {
                                d.close()
                                if (camera === d || gen == generation) {
                                    release()
                                    status("摄像头不可用（" + e + "），请重试")
                                }
                            }
                        },
                        h,
                    )
            } catch (e: Exception) {
                status("无法打开镜头：" + e.localizedMessage)
            }
        }
    }

    private fun configure(withRaw: Boolean, compact: Boolean = false) {
        val d = camera ?: return
        val l = lens ?: return
        val size = if (compact) l.compactSize else l.size
        val gen = ++generation
        session?.close()
        session = null
        yuv?.close()
        raw?.close()
        raw = null
        clear()
        try {
            val y = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 4)
            yuv = y
            y.setOnImageAvailableListener(
                { r ->
                    runCatching {
                        r.acquireLatestImage()?.use { im ->
                            if (gen == generation && (scan != null || !processingPreview && previewEnabled())) {
                                images[im.timestamp] = snapshot(im)
                                match(im.timestamp)
                                trim()
                            }
                        }
                    }.onFailure { if (gen == generation) android.util.Log.w("LightData", "YUV image unavailable", it) }
                },
                h,
            )
            val rawSize =
                l.c[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                    ?.getOutputSizes(ImageFormat.RAW_SENSOR)
                    ?.minByOrNull { it.width.toLong() * it.height }
            if (withRaw && rawSize != null) {
                raw =
                    ImageReader.newInstance(
                            rawSize.width,
                            rawSize.height,
                            ImageFormat.RAW_SENSOR,
                            3,
                        )
                        .apply {
                            setOnImageAvailableListener(
                                { r ->
                                    runCatching {
                                        r.acquireNextImage()?.use { im ->
                                            if (gen == generation) {
                                                rawGrid[im.timestamp] = sampleRaw(im, l)
                                                match(im.timestamp)
                                                trim()
                                            }
                                        }
                                    }
                                },
                                h,
                            )
                        }
            }
            val outputs =
                listOfNotNull(yuv, raw).map {
                    OutputConfiguration(it.surface).apply {
                        if (l.id != l.owner) setPhysicalCameraId(l.id)
                    }
                }
            d.createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    executor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            if (gen != generation) {
                                s.close()
                                return
                            }
                            session = s
                            request()
                        }

                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            s.close()
                            if (gen != generation) return
                            if (raw != null && !compact && size != l.compactSize) configure(true, true)
                            else if (raw != null) configure(false)
                            else if (!compact && size != l.compactSize) configure(false, true)
                            else {
                                scan = null
                                status("该镜头不支持此采集组合，请切换镜头")
                            }
                        }
                    },
                )
            )
        } catch (e: Exception) {
            if (withRaw && !compact && size != l.compactSize) configure(true, true)
            else if (withRaw) configure(false)
            else if (!compact && size != l.compactSize) configure(false, true)
            else {
                scan = null
                status("无法配置镜头：" + e.localizedMessage)
            }
        }
    }

    private fun trim() {
        val packet = images.values.firstOrNull()
        if (packet != null) {
            val bytes = packet.planes.sumOf { it.data.size }.coerceAtLeast(1)
            val limit = (Runtime.getRuntime().maxMemory() / 8 / bytes).coerceIn(3,12)
            while (images.size > limit) images.remove(images.keys.first())
        }
        for (m in listOf(results, rawGrid)) while (m.size > 12) m.remove(m.keys.first())
    }

    fun calibrate(method: String, step: Double) {
        h.post {
            val l = lens ?: return@post
            if (session == null || scan != null) {
                status("镜头尚未就绪")
                return@post
            }
            val range = l.c[CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE]
            val isoRange = l.c[CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE]
            if (
                range == null ||
                    isoRange == null ||
                    l.c[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]?.contains(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                    ) != true
            ) {
                status("此镜头未开放手动包围曝光")
                return@post
            }
            val longest = min(range.upper, 1_000_000_000L)
            // Leave three stops above the metered midtone even on devices with
            // a short manual shutter ceiling. Hold this sensor ISO for the run.
            val needed = baseTime.toDouble() * baseIso * 8 / longest
            val maxAnalog =
                l.c[CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY] ?: isoRange.upper
            val iso =
                isoRange.clamp(max(100.0, needed).coerceAtMost(maxAnalog.toDouble()).roundToInt())
            val center = baseTime.toDouble() * baseIso / iso
            val times =
                (listOf(range.lower) +
                        (-12..12).map {
                            (center * 2.0.pow(it)).toLong().coerceIn(range.lower, longest)
                        } +
                        longest)
                    .distinct()
            scan = Scan(method, step, times, iso)
            configure(true, yuv?.width == l.compactSize.width && yuv?.height == l.compactSize.height)
        }
    }

    private fun request() {
        val d = camera ?: return
        val s = session ?: return
        val l = lens ?: return
        val b = scan
        val gen = generation
        try {
            val req =
                d.createCaptureRequest(
                    if (b == null) CameraDevice.TEMPLATE_PREVIEW
                    else CameraDevice.TEMPLATE_STILL_CAPTURE
                )
            req.addTarget(yuv!!.surface)
            req.set(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            )
            if (panoramaLock && b == null) {
                if (l.c[CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE] == true)
                    req.set(CaptureRequest.CONTROL_AE_LOCK, true)
                if (l.c[CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE] == true)
                    req.set(CaptureRequest.CONTROL_AWB_LOCK, true)
            }
            if (b != null) {
                raw?.let { req.addTarget(it.surface) }
                req.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                req.set(CaptureRequest.SENSOR_EXPOSURE_TIME, b.times[b.index])
                req.set(CaptureRequest.SENSOR_SENSITIVITY, b.iso)
                if (l.c[CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE] == true)
                    req.set(CaptureRequest.CONTROL_AWB_LOCK, true)
                status("校准 " + (b.index + 1) + " / " + b.times.size)
            }
            val cb =
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        s: CameraCaptureSession,
                        r: CaptureRequest,
                        total: TotalCaptureResult,
                    ) {
                        if (gen != generation) return
                        val physical = total.physicalCameraResults[l.id] ?: total
                        val ts = physical[CaptureResult.SENSOR_TIMESTAMP] ?: return
                        results[ts] = physical to (if (b == null) -1 else b.index)
                        match(ts)
                        trim()
                    }

                    override fun onCaptureFailed(
                        s: CameraCaptureSession,
                        r: CaptureRequest,
                        f: CaptureFailure,
                    ) {
                        if (gen == generation && b != null) abort("包围曝光失败，请重试")
                    }
                }
            if (b == null) s.setRepeatingRequest(req.build(), cb, h)
            else {
                s.capture(req.build(), cb, h)
                val index = b.index
                val first = b.first
                h.postDelayed(
                    {
                        if (
                            gen == generation && scan === b && b.index == index && b.first === first
                        )
                            abort("未收到完整曝光帧，请重试")
                    },
                    4500,
                )
            }
        } catch (e: Exception) {
            if (b != null) abort("曝光请求失败：" + e.localizedMessage)
            else status("预览请求失败：" + e.localizedMessage)
        }
    }

    private fun abort(msg: String) {
        scan = null
        status(msg)
        configure(false)
    }

    private fun match(ts: Long) {
        val (meta, index) = results[ts] ?: return
        val image = images[ts] ?: return
        val b = scan
        if (b != null && raw != null && rawGrid[ts] == null) return
        images.remove(ts)
        results.remove(ts)
        val time = meta[CaptureResult.SENSOR_EXPOSURE_TIME] ?: return
        val iso = meta[CaptureResult.SENSOR_SENSITIVITY] ?: return
        val l = lens ?: return
        val a =
            meta[CaptureResult.LENS_APERTURE]?.toDouble()
                ?: l.c[CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES]
                    ?.firstOrNull()
                    ?.toDouble()
                ?: return
        val boost = (meta[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST] ?: 100) / 100.0
        val synced =
            l.c[CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE] ==
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
        image.exposureMs = time/1e6
        val ev = Exposure(time / 1e9, a, iso * boost).ev
        val sensorTime = if (synced) ts else 0L
        if (b == null) {
            if (processingPreview) return
            baseTime = time
            baseIso = iso
            val focal = (meta[CaptureResult.LENS_FOCAL_LENGTH]
                ?: l.c[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.firstOrNull())?.toDouble()
            val view =
                focal?.let { cameraField(l.c, Size(image.w,image.h), it, meta[CaptureResult.SCALER_CROP_REGION]) }
                    ?: l.view
            // Keep Camera2 callbacks responsive even if conversion or panorama registration takes
            // longer than the frame interval. Only hand a timestamp-matched packet to the worker.
            processingPreview = true
            val gen = generation
            processor.post {
                try {
                    if (gen == generation)
                        onFrame(image,ev,sensorTime, if (view == l.view) l else l.copy(view = view))
                } finally {
                    h.post { if (gen == generation) processingPreview = false }
                }
            }
            return
        }
        if (index != b.index) return
        if (
            abs(log2(time.toDouble() / b.times[index])) > .35 ||
                abs(log2(iso.toDouble() / b.iso)) > .2
        ) {
            abort("镜头未执行指定的曝光参数")
            return
        }
        val grid = image.grid()
        val rg = rawGrid.remove(ts)
        if (b.first == null) {
            b.first = grid
            b.firstRaw = rg
            request()
            return
        }
        b.previews += Bracket(time / 1e9, b.first!!, grid)
        if (rg != null && b.firstRaw != null) b.raws += Bracket(time / 1e9, b.firstRaw!!, rg)
        b.first = null
        b.firstRaw = null
        b.index++
        if (b.index < b.times.size) {
            request()
            return
        }
        try {
            val (lut, yuvStops) = fitResponse(b.previews, b.method, b.step)
            var stops = yuvStops
            var channel = "YUV"
            estimateRawStops(b.raws)?.let {
                stops = it
                channel = "RAW+YUV"
            }
            calibrated(l, Calibration(lut, stops, "$channel ${image.w}×${image.h}", b.iso))
        } catch (e: CalibrationIssue) {
            status(e.message ?: "校准失败")
        }
        scan = null
        configure(false)
    }

    private fun sampleRaw(im: Image, l: Lens): DoubleArray {
        val p = im.planes[0]
        val buf = p.buffer.order(java.nio.ByteOrder.nativeOrder())
        val white = l.c[CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL] ?: 65535
        val black = l.c[CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN]
        val cfa = l.c[CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT]
        val dx = if (cfa == 0 || cfa == 3) 1 else 0
        val offset = black?.getOffsetForIndex(dx, 0) ?: 0
        return DoubleArray(768) { i ->
            val x = (((.1 + .8 * ((i % 32 + .5) / 32)) * im.width).toInt() and -2) + dx
            val y = ((.1 + .8 * ((i / 32 + .5) / 24)) * im.height).toInt() and -2
            val v = buf.getShort(y * p.rowStride + x * p.pixelStride).toInt() and 65535
            ((v - offset).toDouble() / (white - offset)).coerceIn(0.0, 1.0)
        }
    }

    private fun snapshot(im: Image): Packet {
        if (Build.VERSION.SDK_INT >= 33 && im.dataSpace != lastDataSpace) {
            val space = im.dataSpace
            lastDataSpace = space
            // ImageReader is configured for YUV_420_888. Image.dataSpace's wider annotation
            // also lists depth formats, although these cannot occur on this stream.
            @Suppress("WrongConstant")
            val yuvEncoding = YuvEncoding(
                limited = DataSpace.getRange(space) == DataSpace.RANGE_LIMITED,
                bt709 = DataSpace.getStandard(space) == DataSpace.STANDARD_BT709,
                videoTransfer = space != DataSpace.DATASPACE_JFIF &&
                    DataSpace.getTransfer(space) == DataSpace.TRANSFER_SMPTE_170M,
            )
            encoding = yuvEncoding
        }
        val planes = im.planes.mapIndexed { i, plane ->
            val buffer = plane.buffer.duplicate()
            val height = if (i == 0) im.height else (im.height + 1) / 2
            val data = ByteArray(maxOf(plane.rowStride*height, buffer.remaining()))
            buffer.get(data, 0, buffer.remaining())
            Plane(data, plane.rowStride, plane.pixelStride)
        }
        return Packet(im.width, im.height, planes, encoding)
    }
}
