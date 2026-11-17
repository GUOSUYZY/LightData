package studio.lightdata

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
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
)

data class Spot(val id: Int, val x: Double, val y: Double, val ev: Double)

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
    var style by mutableIntStateOf(0)
    var compensationThirds by mutableIntStateOf(0)
        private set
    var preview by mutableStateOf(false)
    var frozen by mutableStateOf(false)
    var single by mutableStateOf(false)
    var held by mutableStateOf<Double?>(null)
    var logCounts by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var resizing by mutableStateOf(false)
    var sample by mutableStateOf<Sample?>(null)
    var cal by mutableStateOf<Calibration?>(null)
    val spots = mutableStateListOf<Spot>()
    private var number = 1
    var message by mutableStateOf("")
    var runningCal by mutableStateOf(false)
    var allowed by mutableStateOf(false)
    var cameraRetry by mutableStateOf(false)
        private set
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
    var total by mutableDoubleStateOf(12.0)
    var above by mutableDoubleStateOf(4.0)
    var customDigital by mutableStateOf(false)
    val layout = mutableStateMapOf<String, Float>()
    val response
        get() = cal?.responseFor(sample?.frame?.encoding ?: YuvEncoding.default) ?: defaultResponse

    val reading
        get() =
            sample
                ?.takeIf { it.hist.any { bin -> bin > 0 } }
                ?.let { it.frame.cameraEv + meter(it.hist, response) }

    // A held value is meaningful only while its measurement session still exists.
    val appliedReading
        get() = if (sample == null) null else held ?: reading

    val exposure: Exposure
        get() {
            if (frozen) return frozenExposure ?: manual
            return solve(mode, manual, appliedReading ?: manual.ev, compensationThirds)
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
                invalidateCamera("需要相机权限才能取景")
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
                { msg ->
                    runOnUiThread { invalidateCamera(msg) }
                },
                { frozen },
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
        total = saved("total", 12.0)
        above = saved("above", 4.0)
        customDigital = prefs.getString("digital", "false") == "true"
        film =
            films.find { it.name == prefs.getString("film", films.first().name) }
                ?: customFilm(customDigital, total, above)
        logCounts = prefs.getBoolean("logCounts", false)
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
        allowed = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            cameraRetry = false
            start() // Re-enumerate after a permission or camera-service failure.
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
        else invalidateCamera("需要相机权限才能取景")
    }

    override fun onPause() {
        active = false
        invalidateCamera(retry = false)
        pose.stop()
        rig.stop()
        scanRequest?.reanchor = true
        super.onPause()
    }

    override fun onDestroy() {
        rig.destroy()
        super.onDestroy()
    }

    fun invalidateCamera(msg: String? = null, retry: Boolean = true) {
        epoch++
        sample = null
        runningCal = false
        cameraRetry = retry
        if (msg != null) message = msg
    }

    private fun start() {
        if (!active) return
        runCatching {
                lenses = cameras(this)
                selectCamera(true, resumeScan = scanActive)
            }
            .onFailure { invalidateCamera("摄像头枚举失败：" + it.localizedMessage) }
    }

    fun target() = field(format.w, format.h, focal)

    fun selectCamera(force: Boolean = false, resumeScan: Boolean = false) {
        val goal = target()
        val next =
            lenses.find { it.id == cameraChoice }
                ?: lenses.filter { it.view.covers(goal) }.minByOrNull { it.view.x }
                ?: lenses.firstOrNull()
        if (next == null) {
            invalidateCamera("未找到可访问的后置摄像头")
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

    private fun process(f: Frame, l: Lens) {
        val token = epoch
        if (frozen || l.id != current?.id) return
        val rot = windowManager.defaultDisplay.rotation
        val request = scanRequest
        val scanRotation = request?.rotation ?: rot
        val turn = (l.angle - scanRotation * 90 + 360) % 360
        val transpose = turn % 180 != 0
        val frame = upright(f, turn)
        val src = if (transpose) l.view.swap() else l.view
        val goal = if (transpose) target().swap() else target()
        val displayTurn = Math.floorMod(scanRotation - rot, 4) * 90
        val scan = request?.let {
            val axes = pose.axes(scanRotation, f.timestampNs)
            val session = it.session ?: ScanSession(frame, goal, axes, response).also { s -> it.session = s }
            if (it.reanchor) { session.resumeTracking(); it.reanchor = false }
            upright(session.capture(frame, src, axes), displayTurn) to session.guide(displayTurn)
        }
        val out = scan?.first ?: crop(frame, src, goal)
        val hist = out.distribution()
        val result = Sample(out, hist, !src.covers(goal), scan?.second)
        runOnUiThread {
            if (!active || !allowed || token != epoch || frozen) return@runOnUiThread
            if (message.startsWith("正在连接")) {
                message = ""
                android.util.Log.i(
                    "LightData",
                    "Frame from " + l.label + " via " + l.owner + " " + out.w + "x" + out.h,
                )
            }
            sample = result
            cameraRetry = false
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
        if (scanRequest != null) epoch++
        scanRequest = null
        runningCal = false
    }

    fun toggleScan() {
        if (scanActive) {
            cancelScan()
            return
        }
        if (sample?.missing != true || frozen || runningCal) return
        scanRequest = ScanRequest(windowManager.defaultDisplay.rotation)
        epoch++
    }

    fun toggleFreeze() {
        if (sample == null) return
        if (!frozen) frozenExposure = exposure
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
        manual = e
        put("t", e.t)
        put("a", e.a)
        put("iso", e.iso)
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

    fun setFilmChoice(v: Film) {
        film = v
        put("film", v.name)
    }

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
                setText(n(initial))
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
