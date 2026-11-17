package studio.lightdata

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import kotlin.math.*

data class Sample(
    val frame: Frame,
    val hist: IntArray,
    val missing: Boolean,
    val scan: ScanGuide?,
    val still: Boolean = false,
    val captureWidth: Int = frame.w,
    val captureHeight: Int = frame.h,
    val live: LiveCapture? = null,
)

data class Spot(val id: Int, val x: Double, val y: Double, val ev: Double)

fun isoLabel(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

class MainActivity : ComponentActivity() {
    val prefs by lazy { getSharedPreferences("meter", MODE_PRIVATE) }
    private val profiles by lazy { ProfileStore(prefs) }

    fun saved(k: String, v: Double) = prefs.getString(k, null)?.toDoubleOrNull() ?: v

    fun put(k: String, v: Any) {
        prefs.edit().putString(k, v.toString()).apply()
    }

    lateinit var pose: Pose
    lateinit var rig: Rig
    var lenses by mutableStateOf(emptyList<Lens>())
    var current by mutableStateOf<Lens?>(null)
    var cameraChoice by mutableStateOf("自动")
    var format by mutableStateOf(formats[4])
    var focal by mutableDoubleStateOf(35.0)
    var mode by mutableStateOf("A")
    var manual by mutableStateOf(Exposure(1.0 / 125, 8.0, 100.0))
    var film by mutableStateOf(films.first())
        private set
    var filmIso by mutableDoubleStateOf(filmRecords.first().iso.toDouble())
        private set
    var isoLocked by mutableStateOf(true)
        private set
    var style by mutableIntStateOf(0)
    var compensationThirds by mutableIntStateOf(0)
        private set
    var preview by mutableStateOf(false)
    var useLut by mutableStateOf(false)
    var previewScale by mutableDoubleStateOf(.5)
    var scanAlgorithm by mutableStateOf("LK")
    fun chooseScanAlgorithm(value: String) {
        cancelScan()
        scanAlgorithm = value
        prefs.edit().putString("scanAlgorithm",value).apply()
    }
    var recordScan by mutableStateOf(false)
    private data class ScanShot(val jpeg: ByteArray,val meta: String)
    private val scanShots = mutableListOf<ScanShot>()
    private fun recordShot(f: Frame, src: Field, target: Field, axes: FloatArray?, exposureMs: Double,
                           time: Long, guide: ScanGuide, turn: Int, cameraMeta: String?) {

        val bitmap = android.graphics.Bitmap.createBitmap(f.pixels,f.w,f.h,android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream()
        try { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,bytes) }
        finally { bitmap.recycle() }
        val meta = org.json.JSONObject().put("camera",f.id).put("cameraEV",f.cameraEv)
            .put("captureResult",cameraMeta?.let { org.json.JSONObject(it) })
            .put("source",org.json.JSONArray(listOf(src.x,src.y))).put("target",org.json.JSONArray(listOf(target.x,target.y)))
            .put("axes",axes?.let { org.json.JSONArray(it.toList()) }).put("exposureMs",exposureMs)
            .put("timeMs",time).put("displayTurn",turn).put("captures",guide.captures).put("coverage",guide.coverage)
        synchronized(scanShots) {
            // Preserve the center anchor and recent attempts when a direction stalls.
            if (scanShots.size >= 80) scanShots.removeAt(1)
            scanShots.add(ScanShot(bytes.toByteArray(),meta.put("algorithm",scanAlgorithm).toString()))
        }
    }
    private val saveScan = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val shots = synchronized(scanShots) { scanShots.toList() }
            Thread {
                runCatching {
                    java.util.zip.ZipOutputStream(requireNotNull(contentResolver.openOutputStream(uri))).use { zip ->
                        for ((i,shot) in shots.withIndex()) for ((ext,data) in listOf("jpg" to shot.jpeg,"json" to shot.meta.toByteArray())) {
                            zip.putNextEntry(java.util.zip.ZipEntry("${i+1}.$ext")); zip.write(data); zip.closeEntry()
                        }
                    }
                }.onFailure { runOnUiThread { android.widget.Toast.makeText(this,"导出失败",0).show() } }
            }.start()
        }
    }
    fun exportScan() {
        if (synchronized(scanShots) { scanShots.isEmpty() }) android.widget.Toast.makeText(this,"请先开启记录并进行拼接",0).show()
        else saveScan.launch("LightData-panorama.zip")
    }
    var showFps by mutableStateOf(false)
    var renderedFps by mutableIntStateOf(0)
    var previewView = java.lang.ref.WeakReference<Preview>(null)
    var latestLive: LiveCapture? = null
        private set
    private val frameTimes = java.util.ArrayDeque<Long>()
    fun markRendered() {
        synchronized(frameTimes) {
            frameTimes.addLast(android.os.SystemClock.elapsedRealtimeNanos())
            while (frameTimes.size > 240) frameTimes.removeFirst()
        }
    }
    fun updateFps() {
        val now = android.os.SystemClock.elapsedRealtimeNanos()
        renderedFps = synchronized(frameTimes) {
            while (frameTimes.isNotEmpty() && now - frameTimes.first > 1_000_000_000L) frameTimes.removeFirst()
            if (frozen) 0 else frameTimes.size
        }
    }
    private val meterWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val measuring = java.util.concurrent.atomic.AtomicBoolean()
    private data class Measurement(val epoch: Int, val turn: Int, val frame: Frame, val hist: IntArray)
    @Volatile private var lastMeasure: Measurement? = null
    private var lastMeasureAt = 0L
    internal val lutAssets by lazy { LutAssets(assets) }
    var lutEntries by mutableStateOf(emptyList<LutEntry>())
    var lutCatalogStatus by mutableStateOf("正在读取 LUT 目录")
    val chosenLuts = mutableStateMapOf<String, String>()
    val variants get() = if (selectedPresetId == null) lutEntries.filter { it.film == film.name } else emptyList()
    val variant: LutEntry? get() {
        val options = variants
        val file = chosenLuts[film.name] ?: prefs.getString("lutVariant:" + film.name, null)
        return options.find { it.file == file }
            ?: options.find { it.preferred && it.iso.toDouble() == filmIso }
            ?: options.find { it.preferred } ?: options.firstOrNull()
    }
    var activeAtlas by mutableStateOf<LutAtlas?>(null)
    var failedLut by mutableStateOf<String?>(null)
    val lutStatus get() = when {
        lutCatalogStatus.isNotEmpty() -> lutCatalogStatus
        variant == null -> "无对应 LUT · 使用亮度模拟"
        variant?.file == failedLut -> "LUT 加载失败 · 使用亮度模拟"
        activeAtlas?.entry?.file != variant?.file -> "正在加载 LUT · 使用亮度模拟"
        else -> "LUT 预览"
    }
    private val stillDetector = StillDetector()
    var frozen by mutableStateOf(false)
    var single by mutableStateOf(false)
    var held by mutableStateOf<Double?>(null)
    var logCounts by mutableStateOf(true)
    var settings by mutableStateOf(false)
    var resizing by mutableStateOf(false)
    var sample by mutableStateOf<Sample?>(null)
    var cal by mutableStateOf<Calibration?>(null)
    val spots = mutableStateListOf<Spot>()
    private var number = 1
    var message by mutableStateOf("")
    var runningCal by mutableStateOf(false)
    var allowed by mutableStateOf(false)
    private class ScanRequest(val rotation: Int) {
        // Owned by the camera thread. Cancelling replaces the request itself.
        var session: ScanSession? = null
        @Volatile var reanchor = false
    }
    private var scanRequest by mutableStateOf<ScanRequest?>(null)
    val scanActive get() = scanRequest != null
    private var frozenExposure: Exposure? = null
    @Volatile private var epoch = 0
    private var active = false
    var customPresets by mutableStateOf(emptyList<CustomPreset>())
        private set
    var selectedPresetId by mutableStateOf<String?>(null)
        private set
    val layout = mutableStateMapOf<String, Float>()
    val response
        get() = cal?.responseFor(sample?.frame?.encoding ?: YuvEncoding.default) ?: defaultResponse

    private var meteredHist: IntArray? = null
    private var meteredLut: DoubleArray? = null
    private var meteredOffset: Double? = null
    val reading: Double? get() {
        val s = sample ?: return null
        val lut = response
        if (meteredHist !== s.hist || meteredLut !== lut) {
            meteredOffset = if (s.hist.any { it > 0 }) meter(s.hist,lut) else null
            meteredHist=s.hist
            meteredLut=lut
        }
        return meteredOffset?.plus(s.frame.cameraEv)
    }

    val exposure: Exposure
        get() {
            if (frozen) return frozenExposure ?: manual
            return solve(mode, manual, held ?: reading ?: manual.ev, compensationThirds)
        }

    fun selectPair(pair: Exposure) {
        if (frozen) return
        when (mode) {

            "A" -> setValues(manual.copy(a = pair.a))
            "S" -> setValues(manual.copy(t = pair.t))
            else -> setValues(pair)
        }
    }

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = it
            if (it) start()
            else {
                prefs.edit().putBoolean("denied", true).apply()
                message = "需要相机权限才能取景"
            }
        }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pose = Pose(this)
        rig =
            Rig(
                this,
                ::process,
                { msg ->
                    runOnUiThread {
                        message = msg
                        runningCal = msg.startsWith("校准 ")
                    }
                },
                { l, c ->
                    runOnUiThread {
                        profiles.save(l.id, c)
                        if (current?.id == l.id) cal = c
                        runningCal = false
                        message = "已保存镜头 " + l.id + " 校准 · " + n(c.stops) + " EV"
                    }
                },
                { !frozen },
            )
        focal = saved("focal", 35.0)
        cameraChoice = prefs.getString("camera", "自动")!!
        val name = prefs.getString("format", "全幅")
        format =
            formats.find { it.name == name }
                ?: Format("自定义", saved("width", 36.0), saved("height", 24.0))
        mode = storedMode(prefs.getString("mode", null))
        put("mode", mode)
        compensationThirds = prefs.getInt("compensationThirds", 0).coerceIn(-15, 15)
        manual = Exposure(saved("t", 1.0 / 125), saved("a", 8.0), saved("iso", 400.0))
        style = saved("style", 0.0).toInt()
        useLut = prefs.getBoolean("useLut", false)
        previewScale = saved("previewScale", .5).coerceIn(.25, 1.0)
        scanAlgorithm = prefs.getString("scanAlgorithm","LK") ?: "LK"
        showFps = prefs.getBoolean("showFps", false)
        restoreFilm()
        logCounts = prefs.getBoolean("logCounts", true)
        for (prefix in listOf("p", "l", "squarep", "squarel")) {
            for (i in 1..2) {
                val key = prefix + i
                if (prefs.contains(key)) layout[key] = saved(key, .5).toFloat().coerceIn(.22f, .78f)
            }
        }
        setContent { Instrument(this) }
        allowed =
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!allowed) permission.launch(Manifest.permission.CAMERA)
    }

    fun requestCamera() {
        if (allowed) {
            selectCamera(true)
            return
        }
        if (
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) &&
                prefs.getBoolean("denied", false)
        ) {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + packageName),
                )
            )
        } else permission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        allowed =
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        active = true
        pose.start()
        if (allowed) start()
    }

    override fun onPause() {
        active = false
        epoch++
        pose.stop()
        rig.stop()
        scanRequest?.reanchor = true
        runningCal = false
        super.onPause()
    }

    override fun onConfigurationChanged(config: android.content.res.Configuration) {
        super.onConfigurationChanged(config)
        if (!active || !allowed || runningCal) return
        val prior = current ?: return
        val available = runCatching { cameras(this) }.getOrNull() ?: return
        val next = available.find { it.id == prior.id } ?: return
        if (next.size != prior.size) {
            epoch++
            lenses = available
            current = next
            scanRequest?.reanchor = true
            rig.open(next)
        }
    }

    override fun onDestroy() {
        rig.destroy()
        meterWorker.shutdownNow()
        super.onDestroy()
    }

    private fun start() {
        if (!active) return
        runCatching {
                lenses = cameras(this)
                selectCamera(true, resumeScan = scanActive)
            }
            .onFailure { message = "摄像头枚举失败：" + it.localizedMessage }
    }

    fun target() = field(format.w, format.h, focal)

    fun selectCamera(force: Boolean = false, resumeScan: Boolean = false) {
        val goal = target()
        val next =
            lenses.find { it.id == cameraChoice }
                ?: lenses.filter { it.view.covers(goal) }.minByOrNull { it.view.x }
                ?: lenses.firstOrNull()
        if (next == null) {
            message = "未找到可访问的后置摄像头"
            return
        }
        if (resumeScan && next.id == current?.id) {
            current = next
            rig.open(next)
            return
        }
        cancelScan()
        epoch++
        frozen = false
        spots.clear()
        held = null
        sample = null
        if (force || current != next) {
            current = next
            cal = profiles.load(next.id)
            message = "正在连接 " + next.label
            rig.open(next)
        } else message = ""
    }

    private var lastScanView: Frame? = null
    private var lastScanTurn = 0
    private var lastScanMeasurement: Measurement? = null

    private fun process(packet: Rig.Packet, ev: Double, timestamp: Long, l: Lens) {
        val token = epoch
        if (frozen || l.id != current?.id) return
        val rot = windowManager.defaultDisplay.rotation
        val request = scanRequest
        val scanRotation = request?.rotation ?: rot
        val turn = (l.angle - scanRotation * 90 + 360) % 360
        val transpose = turn % 180 != 0
        val src = if (transpose) l.view.swap() else l.view
        val goal = if (transpose) target().swap() else target()
        val live = LiveCapture(packet,ev,l.id,timestamp,turn,src,goal)
        val now = android.os.SystemClock.elapsedRealtime()
        val displayTurn = Math.floorMod(scanRotation - rot, 4) * 90
        val scan = request?.let {
            val frame by lazy(LazyThreadSafetyMode.NONE) { live.panoramaFrame() }
            val axes = pose.axes(scanRotation, timestamp)
            val session = it.session ?: ScanSession(frame, goal, axes, response, OpenCvScanAligner.forMode(scanAlgorithm)).also { s -> it.session = s }
            if (it.reanchor) { session.resumeTracking(); it.reanchor = false }
            val time = if (timestamp > 0) timestamp / 1_000_000 else now
            var candidate: Frame? = null
            val shown = session.capture(src,axes,time,live::trackingImage,packet.exposureMs) { frame.also { candidate = it } }
            val guide = session.guide(displayTurn)
            if (recordScan) candidate?.let { recordShot(it,src,goal,axes,packet.exposureMs,time,guide,displayTurn,packet.scanMetadata) }
            shown to guide
        }
        fun measure(): Measurement {
            val full = live.fullFrame()
            return Measurement(token,turn,full,full.distribution())
        }
        val measurement = if (scan != null) {
            val cached = lastScanMeasurement
            if (cached != null && lastScanView === scan.first && lastScanTurn == displayTurn) cached
            else {
                val displayed = upright(scan.first,displayTurn)
                Measurement(token,turn,displayed,displayed.distribution()).also {
                    lastScanView = scan.first
                    lastScanTurn = displayTurn
                    lastScanMeasurement = it
                }
            }
        } else {
            lastScanMeasurement = null
            lastScanView = null
            val cached = lastMeasure?.takeIf { it.epoch == token && it.turn == turn &&
                it.frame.id == l.id && it.frame.w == live.width && it.frame.h == live.height &&
                it.frame.encoding == packet.encoding }
            if (cached == null) {
                lastMeasureAt = now
                measure().also { lastMeasure = it }
            } else {
                if (now - lastMeasureAt >= 200 && !meterWorker.isShutdown && measuring.compareAndSet(false,true)) {
                    lastMeasureAt = now
                    try {
                        meterWorker.execute {
                            try { if (epoch == token) lastMeasure = measure() }
                            finally { measuring.set(false) }
                        }
                    } catch (_: java.util.concurrent.RejectedExecutionException) { measuring.set(false) }
                }
                cached
            }
        }
        val out = measurement.frame
        val still = if (scan != null) stillDetector.observe(out,pose.matrix,now)
            else stillDetector.observe(live.signature(),Triple(l.id,live.width,live.height),pose.matrix,now)
        val result = Sample(out, measurement.hist, !src.covers(goal), scan?.second,
            still, packet.w, packet.h, if (scan == null) live else null)
        runOnUiThread {
            if (!active || token != epoch || frozen) return@runOnUiThread
            if (message.startsWith("正在连接")) {
                message = ""
                android.util.Log.i(
                    "LightData",
                    "Frame from " + l.label + " via " + l.owner + " " + out.w + "x" + out.h,
                )
            }
            latestLive = result.live
            val old = sample
            val view = previewView.get()
            if (result.live != null && old?.frame === result.frame && old.still == result.still &&
                view != null && view.isAttachedToWindow) {
                // Only the camera texture changed. Sliders and histogram follow the
                // independent full-frame measurement, not the sensor callback rate.
                view.advance(result.live,exposure.ev)
            } else sample = result
            if (single && held == null) held = reading
            if (cal == null && !prefs.getBoolean("warned", false) && !isFinishing) {
                prefs.edit().putBoolean("warned", true).apply()
                AlertDialog.Builder(this)
                    .setTitle("当前镜头尚未校准")
                    .setMessage("曝光与分区使用估算值。可在设置中使用灰卡或阶梯卡校准；每个镜头独立保存。")
                    .setPositiveButton("继续取景", null)
                    .show()
            }
        }
    }

    fun cancelScan() {
        if (scanRequest != null) {
            epoch++
            rig.setPanoramaLock(false)
        }
        scanRequest = null
    }

    fun toggleScan() {
        if (scanActive) {
            cancelScan()
            return
        }
        if (sample?.missing != true || frozen || runningCal) return
        if (recordScan) synchronized(scanShots) { scanShots.clear() }
        rig.setPanoramaLock(true)
        scanRequest = ScanRequest(windowManager.defaultDisplay.rotation)
        epoch++
    }

    fun toggleFreeze() {
        val current = sample ?: return
        if (!frozen) {
            frozenExposure = exposure
            current.live?.let { original ->
                val it = previewView.get()?.renderedLive?.takeIf { capture ->
                    capture.id == original.id && capture.width == original.width &&
                        capture.height == original.height && capture.turn == original.turn
                } ?: latestLive ?: original
                val full = it.fullFrame()
                sample = current.copy(frame = full, hist = full.distribution(), live = null)
            }
            latestLive = null
        }
        frozen = !frozen
        spots.clear()
        number = 1
    }

    fun point(x: Double, y: Double) {
        val f = sample?.frame ?: return
        if (!frozen || f.sample(x, y) ushr 24 == 0) return
        val old = spots.find { hypot(it.x - x, it.y - y) < .045 }
        if (old != null) spots.remove(old)
        else spots += Spot(number++, x, y, f.spot(x, y, response))
    }

    fun setValues(e: Exposure) {
        manual = if (isoLocked) e.copy(iso = filmIso) else e
        prefs.edit().putString("t", manual.t.toString()).putString("a", manual.a.toString())
            .putString("iso", manual.iso.toString()).apply()
    }

    fun toggleIsoLock() {
        if (frozen) return
        isoLocked = !isoLocked
        if (isoLocked) manual = manual.copy(iso = filmIso)
        prefs.edit().putBoolean("filmIsoLocked", isoLocked).putString("iso", manual.iso.toString()).apply()
    }

    fun adjustCompensation(thirds: Int) {
        if (frozen || mode == "M") return
        compensationThirds = thirds.coerceIn(-15, 15)
        prefs.edit().putInt("compensationThirds", compensationThirds).apply()
    }

    fun meterOnce() {
        if (frozen) return
        single = true
        held = reading
    }

    fun modeTo(v: String) {
        setValues(exposure)

        mode = v
        put("mode", v)
    }

    fun applyFormat(v: Format) {
        format = v
        put("format", v.name)
        put("width", v.w)
        put("height", v.h)
        selectCamera()
    }

    private fun restoreFilm() {
        customPresets = CustomPresets.decode(prefs.getString("customPresets", null)
            ?: CustomPresets.encode(emptyList()))
        val previous = prefs.getString("film", films.first().name)
        var preset = customPresets.find { it.id == prefs.getString("customPresetId", null) }
        if (preset == null && previous in listOf("自定义胶片", "自定义数码")) {
            preset = CustomPreset.create(previous!!, previous == "自定义数码", saved("total", 12.0),
                saved("above", 4.0), manual.iso)
            customPresets = customPresets + preset
            prefs.edit().putString("customPresets", CustomPresets.encode(customPresets))
                .putString("customPresetId", preset.id).apply()
        }
        val record = filmRecords.find { it.film.name == previous } ?: filmRecords.first()
        selectedPresetId = preset?.id
        film = preset?.toFilm() ?: record.film
        filmIso = preset?.iso ?: record.iso.toDouble()
        // Keep an existing user's deliberate EI override when upgrading an older installation.
        isoLocked = prefs.getBoolean("filmIsoLocked", !prefs.contains("iso") || manual.iso == filmIso)
        if (isoLocked) manual = manual.copy(iso = filmIso)
        prefs.edit().putBoolean("filmIsoLocked", isoLocked).apply()
    }

    private fun applyFilm(value: Film, speed: Double, id: String?, lock: Boolean) {
        film = value
        filmIso = speed
        selectedPresetId = id
        isoLocked = lock
        if (lock) manual = manual.copy(iso = speed)
        if (frozen) frozenExposure = solve(mode, manual, held ?: reading ?: manual.ev, compensationThirds)
        prefs.edit().putString("film", value.name).putString("customPresetId", id)
            .putBoolean("filmIsoLocked", lock).putString("iso", manual.iso.toString()).apply()
    }

    fun selectFilm(record: FilmRecord) = applyFilm(record.film, record.iso.toDouble(), null, true)
    fun selectPreset(preset: CustomPreset) = applyFilm(preset.toFilm(), preset.iso, preset.id, true)

    fun savePreset(preset: CustomPreset) {
        customPresets = CustomPresets.merge(customPresets, listOf(preset))
        prefs.edit().putString("customPresets", CustomPresets.encode(customPresets)).apply()
        selectPreset(preset)
    }

    private fun importPresets(incoming: List<CustomPreset>) {
        val merged = CustomPresets.merge(customPresets, incoming)
        val selected = merged.find { it.id == selectedPresetId }
        val changed = merged.count { it !in customPresets }
        customPresets = merged
        prefs.edit().putString("customPresets", CustomPresets.encode(merged)).apply()
        if (selected != null && incoming.any { it.id == selected.id })
            applyFilm(selected.toFilm(), selected.iso, selected.id, isoLocked)
        notifyUser("已读取 ${incoming.size} 个预设，新增或更新 ${changed} 个")
    }

    private val openPresets = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::readPresetDocument)
    }

    internal fun readPresetDocument(uri: Uri) = presetIO({
        requireNotNull(contentResolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8)
            .use { CustomPresets.decode(it) }
    }, ::importPresets)

    private val writePresets = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(::writePresetDocument)
    }

    internal fun writePresetDocument(uri: Uri) {
        val json = CustomPresets.encode(customPresets)
        presetIO({
            requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8)
                .use { it.write(json) }
        }, { notifyUser("自定义预设已导出") })
    }

    // Document providers can classify plain JSON as text, binary, or a vendor type.
    fun pickPresetFile() = openPresets.launch(arrayOf("*/*"))
    fun exportPresetFile() = writePresets.launch("LightData-presets.json")

    private fun <T> presetIO(work: () -> T, complete: (T) -> Unit) {
        Thread {
            val result = runCatching(work)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) runCatching { complete(result.getOrThrow()) }
                    .onFailure { notifyUser("预设文件操作失败：" + it.localizedMessage) }
            }
        }.start()
    }

    fun notifyUser(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show()

    fun choose(title: String, items: List<String>, action: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, i -> action(i) }
            .show()
    }

    fun input(
        title: String,
        initial: Double,
        range: ClosedFloatingPointRange<Double>,
        action: (Double) -> Unit,
    ) {
        val edit =
            EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine()
                setText(isoLabel(initial))
                selectAll()
            }
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(title)
                .setView(edit)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val v = edit.text.toString().trim().toDoubleOrNull()
                if (v == null || !v.isFinite() || v !in range)
                    edit.error = "请输入 " + n(range.start) + " 至 " + n(range.endInclusive)
                else {
                    dialog.dismiss()
                    action(v)
                }
            }
        }
        dialog.show()
    }

    fun formatDialog() {
        choose(
            "目标画幅",
            formats.map { it.name + "   " + n(it.w) + " × " + n(it.h) + " mm" } + "自定义…",
        ) { i ->
            if (i < formats.size) applyFormat(formats[i])
            else
                choose("自定义画幅单位", listOf("毫米", "英寸")) { u ->
                    val factor = if (u == 1) 25.4 else 1.0
                    input("画幅宽度", format.w / factor, .1 / factor..1500.0 / factor) { w ->
                        input("画幅高度", format.h / factor, .1 / factor..1500.0 / factor) { h ->
                            applyFormat(Format("自定义", mm(w, u == 1), mm(h, u == 1)))
                        }
                    }
                }
        }
    }

    fun changeFocal() {
        input("镜头实际焦距（mm）", focal, 1.0..5000.0) {
            focal = it
            put("focal", it)
            selectCamera()
        }
    }

    fun calibrationDialog() {
        choose("逐镜头校准", listOf("均匀灰卡（推荐）", "九级阶梯卡（推荐）", "现场估测")) { i ->
            val method = listOf("灰卡", "阶梯卡", "现场")[i]
            fun confirm(step: Double) {
                AlertDialog.Builder(this)
                    .setTitle(method + "校准")
                    .setMessage(
                        listOf(
                            "固定手机，在均匀稳定的光照下让灰卡充满镜头。",
                            "横持手机，将九级灰阶从暗至亮横向排列，对准传感器中央 80% 宽度。",
                            "固定手机，对准同时有阴影和亮部的静止场景。",
                        )[i] + " 采集期间请保持不动。"
                    )
                    .setNegativeButton("取消", null)
                    .setPositiveButton("开始采集") { _, _ ->
                        settings = false
                        frozen = false
                        spots.clear()
                        cancelScan()
                        runningCal = true
                        message = "校准 准备中"
                        rig.calibrate(method, step)
                    }
                    .show()
            }
            if (i == 1) input("相邻灰阶间隔（EV）", 1.0, .1..3.0, ::confirm) else confirm(1.0)
        }
    }
}
