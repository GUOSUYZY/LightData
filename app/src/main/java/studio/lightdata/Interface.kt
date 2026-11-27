package studio.lightdata

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ink = Color(0xff101112)
private val panel = Color(0xff191a1c)
private val line = Color(0xff3a3c3e)
private val paper = Color(0xfff0efea)
private val muted = Color(0xffa5a6a3)
private val amber = Color(0xffffbd59)
private val red = Color(0xffec756a)
private val green = Color(0xff6dd8a0)
private val blue = Color(0xff60bfff)
private val unknown = Color(0xffcd83ff)
private val spotColors = listOf(Color(0xffffce58), Color(0xff55ccff), Color(0xffff85bb),
    Color(0xff68e3a8), Color(0xffba9dff), Color(0xffff9864))
private fun spotColor(id: Int): Color = spotColors.getOrNull(id - 1)
    ?: Color.hsv(((id * 137.508) % 360).toFloat(), .55f, 1f)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.extremeMarker(
    point: ExtremalSpot, title: String, color: Color, limited: Boolean,
) {
    val at = markerAnchor(point, size.width, size.height)
    val labelAt = markerLabelCenter(point, size.width, size.height, 12.dp.toPx())
    drawCircle(ink, 9.dp.toPx(), at, style = Stroke(4.dp.toPx()))
    drawCircle(if (limited) unknown else color, 9.dp.toPx(), at, style = Stroke(2.dp.toPx()))
    val paint = android.graphics.Paint(1).apply {
        this.color = color.toArgb()
        setShadowLayer(2.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = 11.dp.toPx()
    }
    val margin = 3.dp.toPx()
    val tx = (labelAt.x + 11.dp.toPx()).coerceIn(margin, (size.width - paint.measureText(title) - margin).coerceAtLeast(margin))
    val ty = (labelAt.y - 10.dp.toPx()).coerceIn(paint.textSize + margin, (size.height - margin).coerceAtLeast(paint.textSize + margin))
    drawContext.canvas.nativeCanvas.drawText(title, tx, ty, paint)
}

private val mono = FontFamily.Monospace

@Composable
private fun Label(
    s: String,
    sz: Int = 12,
    color: Color = paper,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    monoType: Boolean = false,
) {
    BasicText(
        s,
        modifier,
        style =
            TextStyle(
                color = color,
                fontSize = sz.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (monoType) mono else FontFamily.SansSerif,
            ),
    )
}

@Composable
private fun Action(s: String, on: Boolean = false, doIt: () -> Unit) {
    Box(
        Modifier.padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (on) amber else panel)
            .clickable(onClick = doIt)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Label(s, 12, if (on) ink else paper, bold = true)
    }
}

@Composable
fun Instrument(a: MainActivity) {
    // Read transient flags at this level, rather than retaining them in a local
    // composable closure or letting each independently composed panel choose its own state.
    val frozen = a.frozen
    val safety = a.safety
    val preview = a.preview
    val resizing = a.resizing
    LaunchedEffect(a.showFps) {
        while (a.showFps) {
            a.updateFps()
            kotlinx.coroutines.delay(500)
        }
    }
    LaunchedEffect(a.useLut) {
        if (a.useLut && a.lutEntries.isEmpty()) {
            val result = withContext(Dispatchers.IO) { runCatching { a.lutAssets.entries() } }
            a.lutEntries = result.getOrDefault(emptyList())
            a.lutCatalogStatus = if (result.isFailure) "LUT 目录读取失败 · 使用亮度模拟" else ""
        }
    }
    val variant = if (a.useLut && a.preview) a.variant else null
    LaunchedEffect(variant) {
        a.activeAtlas = null
        a.failedLut = null
        if (variant != null) {
            val atlas = withContext(Dispatchers.IO) { runCatching { a.lutAssets.load(variant) }.getOrNull() }
            a.activeAtlas = atlas
            if (atlas == null) a.failedLut = variant.file
        }
    }
    BackHandler(a.settings || resizing || frozen || a.scanActive || safety) {
        when {
            a.settings -> a.settings = false
            resizing -> a.resizing = false
            frozen -> a.toggleFreeze()
            safety -> a.toggleSafety()
            else -> a.cancelScan()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(ink)) {
        val landscape = maxWidth > maxHeight
        val shortSide = minOf(maxWidth, maxHeight)
        val square = shortSide >= 540.dp && maxOf(maxWidth, maxHeight) <= shortSide * 1.30f
        val sideBySide = if (square) !landscape else landscape
        val prefix = (if (square) "square" else "") + if (landscape) "l" else "p"
        val content = @Composable {
            Panels(a, sideBySide, prefix,
                if (square) .5f else if (landscape) .6f else .55f,
                if (square) .5f else if (landscape) .46f else .42f,
                frozen = frozen)
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Toolbar(a, true, frozen, safety, preview, resizing)
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Box(Modifier.weight(1f)) { content() }
                Toolbar(a, false, frozen, safety, preview, resizing)
            }
        }
        if (a.settings)
            Settings(
                a,
                Modifier.align(Alignment.Center).windowInsetsPadding(WindowInsets.safeDrawing)
                    .widthIn(max = 500.dp).fillMaxWidth().padding(12.dp),
            )
    }
}

@Composable
private fun Panels(
    a: MainActivity,
    sideBySide: Boolean,
    prefix: String,
    defaultP: Float,
    defaultQ: Float,
    frozen: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val p = a.layout[prefix + "1"] ?: defaultP
        val q = a.layout[prefix + "2"] ?: defaultQ
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(p).fillMaxHeight()) { Finder(a, frozen) }
                Split(a, prefix + "1", true, w, defaultP)
                Column(Modifier.weight(1 - p).fillMaxHeight()) {
                    Box(Modifier.weight(q)) { Histogram(a) }
                    Split(a, prefix + "2", false, h, defaultQ)
                    Box(Modifier.weight(1 - q)) { Meter(a, frozen) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(p)) { Finder(a, frozen) }
                Split(a, prefix + "1", false, h, defaultP)
                Row(Modifier.fillMaxWidth().weight(1 - p)) {
                    Box(Modifier.weight(q)) { Histogram(a) }
                    Split(a, prefix + "2", true, w, defaultQ)
                    Box(Modifier.weight(1 - q)) { Meter(a, frozen) }
                }
            }
        }
    }
}

@Composable
private fun Split(a: MainActivity, key: String, vertical: Boolean, span: Float, initial: Float) {
    val width = if (a.resizing) 14.dp else 1.dp
    var m =
        if (vertical) Modifier.fillMaxHeight().width(width)
        else Modifier.fillMaxWidth().height(width)
    if (a.resizing)
        m =
            m.pointerInput(key, span) {
                detectDragGestures { change, delta ->
                    change.consume()
                    val v =
                        ((a.layout[key] ?: initial) + (if (vertical) delta.x else delta.y) / span)
                            .coerceIn(.22f, .78f)
                    a.layout[key] = v
                    a.put(key, v)
                }
            }
    Box(m.background(if (a.resizing) amber else line))
}

@Composable
private fun Toolbar(
    a: MainActivity,
    land: Boolean,
    frozen: Boolean,
    safety: Boolean,
    preview: Boolean,
    resizing: Boolean,
) {
    @Composable
    fun brand() {
        Label("LightData", if (land) 10 else 20, paper, bold = true)
    }
    if (land)
        Column(
            Modifier.width(55.dp).fillMaxHeight().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            brand()
            Spacer(Modifier.weight(1f))
            ToolbarButtons(a, frozen, safety, preview, resizing)
        }
    else
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            brand()
            Spacer(Modifier.weight(1f))
            ToolbarButtons(a, frozen, safety, preview, resizing)
        }
}

@Composable
private fun ToolbarButtons(
    a: MainActivity,
    frozen: Boolean,
    safety: Boolean,
    preview: Boolean,
    resizing: Boolean,
) {
    Tool("安全", 3, safety) { a.toggleSafety() }
    Tool("预览", 0, preview) { a.preview = !a.preview }
    Tool(if (frozen) "退出冻结" else "测光点", 1, frozen) {
        if (!a.runningCal) a.toggleFreeze()
    }
    Tool(if (resizing) "完成" else "设置", 2, resizing) {
        if (resizing) a.resizing = false else a.settings = true
    }
}

@Composable
private fun Tool(title: String, type: Int, active: Boolean, click: () -> Unit) {
    Column(
        Modifier.width(48.dp).height(48.dp).clickable(onClick = click),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val c = if (active) amber else paper
        Canvas(Modifier.size(19.dp)) {
            val w = size.width
            fun p(x: Float, y: Float) = Offset(w * x, w * y)
            when (type) {
                0 -> {
                    drawOval(
                        c,
                        p(.05f, .23f),
                        Size(.9f * w, .54f * w),
                        style = Stroke(1.6.dp.toPx()),
                    )
                    drawCircle(c, .14f * w)
                }
                1 -> {
                    drawCircle(c, .29f * w, style = Stroke(1.5.dp.toPx()))
                    drawLine(c, p(0f, .5f), p(.3f, .5f), 2f)
                    drawLine(c, p(.7f, .5f), p(1f, .5f), 2f)
                    drawLine(c, p(.5f, 0f), p(.5f, .3f), 2f)
                    drawLine(c, p(.5f, .7f), p(.5f, 1f), 2f)
                }
                2 -> {
                    for (i in 0..2) {
                        val y = .18f + i * .32f
                        drawLine(c, p(.04f, y), p(.96f, y), 2f)
                        drawCircle(c, w * .105f, p(if (i == 1) .68f else .32f, y))
                    }
                }
                else -> {
                    // A compact luminance range with a film boundary and a protected anchor.
                    drawLine(c, p(.15f, .88f), p(.85f, .12f), 1.5f)
                    drawLine(c, p(.15f, .12f), p(.15f, .88f), 1.5f)
                    drawLine(c, p(.15f, .88f), p(.92f, .88f), 1.5f)
                    drawCircle(c, w * .16f, p(.65f, .35f))
                    drawLine(c, p(.65f, .12f), p(.65f, .60f), 1.5f)
                }
            }
        }
        Label(title, 10, c)
    }
}

@Composable
private fun Finder(a: MainActivity, frozen: Boolean) {
    val s = a.sample
    val f = s?.frame
    Column(Modifier.fillMaxSize().background(Color(0xff0a0b0c))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Action(a.format.name + " ▾") { if (!frozen) a.formatDialog() }
            Action(n(a.focal) + " mm") { if (!frozen) a.changeFocal() }
            Spacer(Modifier.weight(1f))
            Label(
                when {
                    frozen -> "● HOLD"
                    s?.scan?.coverage == 1.0 -> "● 拼接构图"
                    a.scanActive -> "● 扫描采集"
                    s?.missing == true -> "○ 待确认中心"
                    f == null -> "○ WAIT"
                    else -> "● LIVE"
                },
                10,
                if (frozen) amber else muted,
                monoType = true,
            )
        }
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (!a.allowed || a.cameraRetry)
                Action("启用相机 / 重试", true) { a.requestCamera() }
            else if (f != null) {
                val aspect = f.w.toFloat() / f.h
                val fitted =
                    if (maxWidth / maxHeight > aspect) Modifier.fillMaxHeight().aspectRatio(aspect)
                    else Modifier.fillMaxWidth().aspectRatio(aspect)
                Box(fitted) {
                    val shooting = shootingPlan(a.film, a.exposure)
                    val live = s.live?.let { a.latestLive ?: it }
                    val look =
                        Look(
                            f,
                            a.response,
                            (live?.ev ?: f.cameraEv) - shooting.metered.ev,
                            a.film,
                            a.preview,
                            a.mode == "M",
                            a.style,
                            a.safety,
                            if (a.useLut && a.preview) a.activeAtlas?.takeIf { it.entry.file == a.variant?.file } else null,
                            live,
                        )
                    AndroidView(
                        factory = { ctx -> Preview(ctx).apply {
                            a.previewView = java.lang.ref.WeakReference(this)
                            onCubeUnavailable = { file ->
                                if (a.variant?.file == file) {
                                    a.failedLut = file
                                    a.activeAtlas = null
                                }
                            }
                        } },
                        modifier = Modifier.fillMaxSize(),
                        update = {
                            it.onRendered = if (a.showFps) a::markRendered else null
                            it.show(look, if (frozen || s.still) 1.0 else a.previewScale)
                        },
                    )
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(frozen) {
                            detectTapGestures {
                                if (frozen)
                                    a.point(
                                        it.x / size.width.toDouble(),
                                        it.y / size.height.toDouble(),
                                    )
                            }
                        }.semantics {
                            val marks = if (frozen) a.extremes else null
                            if (marks != null) {
                                contentDescription = "自动极值，仅当前画面估计的最暗位置 %.1f EV100，最亮位置 %.1f EV100；不作为曝光主体".format(
                                    java.util.Locale.US, marks.darkest.ev, marks.brightest.ev,
                                )
                            }
                        }
                    ) {
                        val edge = Color.White.copy(alpha = .75f)
                        val k = 12.dp.toPx()
                        val w = size.width
                        val h = size.height
                        for (x in listOf(0f, w)) for (y in listOf(0f, h)) {
                            drawLine(edge, Offset(x, y), Offset(if (x == 0f) k else w - k, y), 3f)
                            drawLine(edge, Offset(x, y), Offset(x, if (y == 0f) k else h - k), 3f)
                        }
                        val guide = s.scan?.takeIf { a.scanActive && !frozen }
                        drawLine(edge, center - Offset(k,0f), center + Offset(k,0f), 2f)
                        drawLine(edge, center - Offset(0f,k), center + Offset(0f,k), 2f)
                        guide?.homeDirection?.let {
                            val dir = Offset(it.x.toFloat(),it.y.toFloat())
                            val tip = center + dir * (k*3)
                            val base = tip - dir*k
                            val side = Offset(-dir.y,dir.x) * (k*.55f)
                            drawLine(amber,center + dir*k,tip,3.dp.toPx())
                            drawLine(amber,tip,base + side,3.dp.toPx())
                            drawLine(amber,tip,base - side,3.dp.toPx())
                        }
                        if (guide != null) {
                            val rotated = guide.mapTurn % 180 != 0
                            val cols = if (rotated) GRID_HEIGHT else GRID_WIDTH
                            val rows = if (rotated) GRID_WIDTH else GRID_HEIGHT
                            val cell = min(8.dp.toPx(),min(w*.32f/cols,h*.25f/rows))
                            val origin = Offset(6.dp.toPx(),6.dp.toPx())
                            val map = Size(cell*cols,cell*rows)
                            fun at(p: FinderPoint) = origin + Offset(
                                p.x.toFloat().coerceIn(0f,1f)*map.width,
                                p.y.toFloat().coerceIn(0f,1f)*map.height)
                            drawRect(Color.Black.copy(alpha=.85f),origin,map)
                            for (i in guide.tiles.indices) {
                                val pos = FinderPoint((i%GRID_WIDTH+.5)/GRID_WIDTH,
                                    (i/GRID_WIDTH+.5)/GRID_HEIGHT).turned(guide.mapTurn)
                                drawRect(lerp(Color.DarkGray,green,guide.tiles[i].toFloat()),
                                    at(pos)-Offset(cell*.44f,cell*.44f),Size(cell*.88f,cell*.88f))
                            }
                            guide.next?.let { drawCircle(amber,cell*.56f,at(it),style=Stroke(1.5.dp.toPx())) }
                            val pos = at(guide.aim)
                            drawLine(Color.White,pos-Offset(cell,0f),pos+Offset(cell,0f),2f)
                            drawLine(Color.White,pos-Offset(0f,cell),pos+Offset(0f,cell),2f)
                        }
                        if (frozen) a.extremes?.let {
                            extremeMarker(it.darkest, "最暗", blue, a.safety && it.darkest.cameraLimited)
                            extremeMarker(it.brightest, "最亮", amber, a.safety && it.brightest.cameraLimited)
                        }
                        for (pt in a.spots) {
                            val at = Offset((pt.x * w).toFloat(), (pt.y * h).toFloat())
                            drawCircle(Color.Black, 9.dp.toPx(), at, style = Stroke(4.dp.toPx()))
                            drawCircle(
                                if (a.safety && pt.cameraLimited) unknown else spotColor(pt.id),
                                9.dp.toPx(), at, style = Stroke(2.dp.toPx()),
                            )
                            if (a.safety && a.safetyPointId == pt.id) {
                                drawCircle(amber, 13.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                pt.id.toString(),
                                at.x + 10.dp.toPx(),
                                at.y,
                                android.graphics.Paint(1).apply {
                                    color = spotColor(pt.id).toArgb()
                                    setShadowLayer(2.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
                                    textSize = 14.dp.toPx()
                                },
                            )
                        }
                    }
                    if (a.showFps) Label("${if (frozen) 0 else a.renderedFps} FPS", 11, paper,
                        Modifier.align(Alignment.TopEnd).padding(5.dp).background(ink.copy(alpha=.78f))
                            .padding(horizontal=5.dp,vertical=2.dp), monoType=true)
                }
            } else Label("正在等待相机", 13, muted)
        }
        if (frozen) {
            val summary =
                a.spots.lastOrNull()?.let {
                    "#%d   %.1f EV100   ·   Zone %.1f".format(
                        java.util.Locale.US, it.id, it.ev,
                        zoneAt(shootingPlan(a.film, a.exposure), it.ev)
                    )
                } ?: if (a.safety) "点按重要高光或暗部，设为曝光参考"
                else if (a.extremes != null) "最暗 / 最亮仅为画面估计 · 点按添加测光点"
                else "点按画面添加测光点"
            val removal = if (a.extremes != null && a.spots.isEmpty()) "" else "   ·   再点同一位置删除"
            Label(summary + removal, 12,
                a.spots.lastOrNull()?.let { spotColor(it.id) } ?: amber, Modifier.padding(horizontal = 10.dp))
        }
        if ((s?.missing == true || a.scanActive) && !frozen && !a.runningCal)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Action(if (!a.scanActive) "确认中心 · 开始拼接" else "退出拼接", !a.scanActive) { a.toggleScan() }
                val guide = s?.scan
                Label(
                    when {
                        !a.scanActive -> "目标超出镜头视角，请对准画面中心"
                        guide == null -> "正在采集中心画面"
                        guide.homeDirection != null && guide.coverage >= 1 -> "按箭头转回拼接中心"
                        guide.needsOverlap -> "请移回已拍区域，保留重叠"
                        guide.coverage >= 1 -> "已完成 · 转动设备二次构图"
                        guide.settling -> "请放慢转动"
                        else -> "将白十字缓慢移向黄圈"
                    },
                    11, muted, Modifier.weight(1f),
                )
                if (guide != null && a.scanActive) {
                    Column(horizontalAlignment=Alignment.End) {
                        Label("${(guide.coverage * 100).toInt()}%", 13, amber, monoType=true)
                        Label("${guide.captures} 帧", 10, muted)
                    }
                }
            }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(
                if (a.message.isNotEmpty()) a.message else a.current?.label ?: "等待镜头",
                10,
                if (a.message.isNotEmpty()) amber else muted,
                Modifier.weight(1f),
            )
            Label(
                if (a.safety) "安全曝光 · 估算"
                else if (!a.preview) "取景"
                else if (a.useLut) a.lutStatus
                else "亮度模拟",
                10,
                if (a.safety || a.preview) amber else muted,
            )
        }
    }
}

@Composable
private fun Histogram(a: MainActivity) {
    val sample = a.sample
    val film = a.film
    val shooting = shootingPlan(film, a.exposure)
    val middle = shooting.metered.ev
    val response = a.response
    val reading = a.reading
    val safetyAnalysis = if (a.safety) {
        remember(sample, film, a.exposure, response, a.toneGoal, a.safetyPointId, a.spots.size) {
            a.safetyAnalysis()
        }
    } else null
    var window by remember { mutableStateOf<HistogramWindow?>(null) }
    val chart = window ?: HistogramWindow.following(reading)
    val currentChart by rememberUpdatedState(chart)
    val bins = remember(sample?.hist, response, chart.left, chart.span) {
        sample?.let { chart.bins(it.hist, response, it.frame.cameraEv, 128) } ?: IntArray(128)
    }
    val peak = bins.max().coerceAtLeast(1)
    val densityMax = ceil(film.dots.maxOf { it.second })
    val histogramTitle = if (a.safety) "安全曝光" else "曝光直方图"
    val histogramReading = if (reading == null) "等待测光" else
        "%.1f EV100".format(java.util.Locale.US, reading) + if (a.cal == null) " · 估测" else ""
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compactHistogram = maxHeight < 200.dp
    var safetyChart by remember(a.safety) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(ink).padding(5.dp)
            .border(1.dp, if (a.cal == null) red else line).padding(7.dp)
    ) {
        if (compactHistogram) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(histogramTitle, 13, bold = true)
                Spacer(Modifier.weight(1f))
                Label(histogramReading, 10, if (a.cal == null) red else paper, monoType = true)
            }
        } else {
            Label(histogramTitle, 15, bold = true)
            Label(histogramReading, 11, if (a.cal == null) red else paper, monoType = true)
        }
        if (a.safety) {
            Row(Modifier.fillMaxWidth()) {
                MeterButton("直方图", safetyChart, true, Modifier.weight(1f), height = 44.dp) {
                    safetyChart = true
                }
                MeterButton("主体与曝光", !safetyChart, true, Modifier.weight(1.4f), height = 44.dp) {
                    safetyChart = false
                }
            }
        }
        if (!a.safety || safetyChart) {
        if (!compactHistogram) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label((if (a.logCounts) "像素 log " else "像素 ") + ceil(peak / chart.gain).toInt(), 9, muted)
                Label("密度 D " + n(round(densityMax / chart.gain * 10) / 10), 9, amber)
            }
        }
        Canvas(
            Modifier.weight(1f).fillMaxWidth()
                .semantics {
                    contentDescription = "场景 EV100 直方图，可横向拖动与双指缩放，双击恢复自动"
                    stateDescription = "%.1f 至 %.1f EV100，纵轴 %.2f 倍".format(
                        java.util.Locale.US, chart.left, chart.right, chart.gain
                    )
                }
                .pointerInput(Unit) {
                    var lastTapUp: Long? = null
                    var lastTapPosition = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var tap = true
                        var moved = false
                        var travel = 0f
                        do {
                            val event = awaitPointerEvent()
                            val first = event.changes.first()
                            if (event.changes.size > 1 || first.isConsumed ||
                                (first.position - down.position).getDistance() > viewConfiguration.touchSlop) tap = false
                            if (!event.changes.any { it.pressed }) {
                                if (tap && !moved && first.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                    // Input timestamps remain accurate when a busy GL or main thread
                                    // delivers both taps together. A pinch cannot start a tap pair.
                                    val interval = lastTapUp?.let { down.uptimeMillis - it }
                                    if (interval != null &&
                                        interval in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                                        (down.position - lastTapPosition).getDistance() <= 40.dp.toPx()) {
                                        window = null
                                        lastTapUp = null
                                    } else {
                                        lastTapUp = first.uptimeMillis
                                        lastTapPosition = first.position
                                    }
                                    first.consume()
                                } else lastTapUp = null
                                break
                            }
                            val fingers = event.changes.filter { it.pressed && it.previousPressed }
                            if (fingers.isEmpty()) continue
                            val panX = fingers.map { it.position.x - it.previousPosition.x }.average()
                            travel += abs(panX).toFloat()
                            if (fingers.size >= 2 || travel > viewConfiguration.touchSlop) moved = true
                            if (!moved) continue
                            val w = size.width.coerceAtLeast(1)
                            val anchor = fingers.map { it.previousPosition.x }.average() / w
                            var zx = 1.0
                            var zy = 1.0
                            if (fingers.size >= 2) {
                                val oldGap = fingers[0].previousPosition - fingers[1].previousPosition
                                val gap = fingers[0].position - fingers[1].position
                                val minGap = 18.dp.toPx()
                                if (abs(oldGap.x) >= minGap && abs(gap.x) >= minGap)
                                    zx = abs(gap.x / oldGap.x).toDouble()
                                if (abs(oldGap.y) >= minGap && abs(gap.y) >= minGap)
                                    zy = abs(gap.y / oldGap.y).toDouble()
                            }
                            window = (window ?: currentChart).transform(panX / w, anchor, zx, zy)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            fun x(ev: Double) = (chart.fraction(ev) * w).toFloat()
            clipRect {
                val grid = if (chart.span > 24) 4 else if (chart.span > 12) 2 else 1
                for (tick in ceil(chart.left / grid).toInt()..floor(chart.right / grid).toInt()) {
                    val xx = x((tick * grid).toDouble())
                    drawLine(line, Offset(xx, 0f), Offset(xx, h))
                }
                val topCount = peak / chart.gain
                val logTop = ln1p(topCount)
                if (!a.logCounts) {
                    for (i in 1..3) drawLine(line, Offset(0f, h * i / 4), Offset(w, h * i / 4))
                }
                for (i in bins.indices) {
                    val fraction = if (a.logCounts) ln1p(bins[i].toDouble()) / logTop else bins[i] / topCount
                    val bh = (h * fraction).toFloat()
                    drawRect(muted, Offset(w * i / bins.size, h - bh), Size(w / bins.size + 1, bh))
                }
                if (a.logCounts) {
                    val axisPaint = android.graphics.Paint(1).apply {
                        color = paper.toArgb()
                        textSize = 9.dp.toPx()
                        setShadowLayer(2.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
                    }
                    var count = 1.0
                    while (count < topCount) {
                        val yy = (h * (1 - ln1p(count) / logTop)).toFloat()
                        drawLine(line, Offset(0f, yy), Offset(w, yy))
                        if (yy >= axisPaint.textSize) {
                            drawContext.canvas.nativeCanvas.drawText(count.toInt().toString(), 1.dp.toPx(), yy, axisPaint)
                        }
                        count *= 10
                    }
                }
                val path = Path()
                for (i in 0..128) {
                    val ev = chart.left + chart.span * i / 128
                    val y = (h * (1 - chart.gain * film.density(ev - middle) / densityMax)).toFloat()
                    if (i == 0) path.moveTo(0f, y) else path.lineTo(w * i / 128, y)
                }
                drawPath(path, amber, style = Stroke(1.5.dp.toPx()))
                for ((ev, c) in listOf(
                    middle + film.low to (if (a.safety) blue else green),
                    middle to paper,
                    middle + film.high to red,
                ))
                    drawLine(c, Offset(x(ev), 0f), Offset(x(ev), h), 1.5.dp.toPx())
                val paint = android.graphics.Paint(1).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 11.dp.toPx()
                }
                val labelRows = mutableListOf<Float>()
                // If several readings map to the same pixel, share the vertical stroke.
                val columns = a.spots.sortedBy { it.ev }.groupBy { x(it.ev).roundToInt() }
                for (group in columns.values) for ((index, pt) in group.withIndex()) {
                    val xx = x(pt.ev)
                    if (xx !in 0f..w) continue
                    paint.color = spotColor(pt.id).toArgb()
                    val segment = 5.dp.toPx()
                    val pattern = if (group.size == 1) null else PathEffect.dashPathEffect(
                        floatArrayOf(segment, segment * (group.size - 1)), segment * (group.size - index))
                    drawLine(
                        spotColor(pt.id), Offset(xx, 0f), Offset(xx, h),
                        if (a.safety && a.safetyPointId == pt.id) 2.dp.toPx() else 1.dp.toPx(),
                        pathEffect = pattern,
                    )
                    val label = "#" + pt.id
                    val labelX = min(xx + 2, w - paint.measureText(label)).coerceAtLeast(0f)
                    val row = labelRows.indexOfFirst { it < labelX }.let { if (it < 0) labelRows.size else it }
                    if (row == labelRows.size) labelRows.add(0f)
                    labelRows[row] = labelX + paint.measureText(label) + 2.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(label, labelX, (12 + 13 * row).dp.toPx(), paint)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(if (compactHistogram) 24.dp else 32.dp)) {
            val w = size.width
            val paint = android.graphics.Paint(1).apply {
                textSize = (if (compactHistogram) 9.dp else 10.dp).toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val maxLabels = (w / 33.dp.toPx()).toInt().coerceAtLeast(2)
            val step = listOf(1, 2, 3, 5, 8, 10, 16, 32).first { it * maxLabels >= chart.span }
            paint.color = android.graphics.Color.LTGRAY
            for (tick in ceil(chart.left / step).toInt()..floor(chart.right / step).toInt()) {
                val ev = tick * step
                val label = ev.toString()
                val width = paint.measureText(label)
                val xx = (chart.fraction(ev.toDouble()) * w).toFloat().coerceIn(width / 2, max(width / 2,w - width / 2))
                drawContext.canvas.nativeCanvas.drawText(label, xx,
                    (if (compactHistogram) 10.dp else 11.dp).toPx(), paint)
            }
            if (sample != null) {
                val zones = listOf("0","I","II","III","IV","V","VI","VII","VIII","IX","X")
                val gap = max(1, ceil(28.dp.toPx() * chart.span / max(w,1f)).toInt())
                paint.color = android.graphics.Color.rgb(243,183,96)
                for (zone in zones.indices) {
                    if ((zone - 5) % gap != 0) continue
                    val xx = (chart.fraction(middle + zone - 5) * w).toFloat()
                    val label = if (zone == 5) "Zone V" else zones[zone]
                    val half = paint.measureText(label) / 2
                    if (xx - half >= 0 && xx + half <= w)
                        drawContext.canvas.nativeCanvas.drawText(label, xx,
                            (if (compactHistogram) 21.dp else 26.dp).toPx(), paint)
                }
            }
        }
        if (sample != null && !a.safety) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("欠 %.1f".format(java.util.Locale.US, middle + film.low), 10, green, monoType = true)
                Label("过 %.1f".format(java.util.Locale.US, middle + film.high), 10, red, monoType = true)
            }
        }
        } else {
            SafetyPanel(a, safetyAnalysis, Modifier.weight(1f).fillMaxHeight())
        }
    }
    }
}

@Composable
private fun SafetyPanel(a: MainActivity, analysis: SafetyAnalysis?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .semantics {
                contentDescription = "胶片安全曝光估算，红色高光风险，蓝色暗部风险"
            },
    ) {
        Label(
            a.film.evidence.label + " · 工作范围为估算，非底片保证",
            9,
            if (a.film.evidence == FilmEvidence.MANUFACTURER_CURVE) muted else amber,
        )
        if (analysis == null) {
            Label(
                if (a.sample?.missing == true) "目标视场未覆盖完整，暂不出全画面结论"
                else "等待有效测光；先确认固定手机与重要区域",
                10, muted, Modifier.padding(vertical = 4.dp),
            )
            return@Column
        }
        val current = analysis.current
        Label(
            when {
                current.bothEndsNeedCompromise -> "主要明暗超出估计范围 · 请选择优先保留的一侧"
                current.shadowFraction > 0 && current.highlightFraction > 0 ->
                    "高光和暗部都有风险 · 改变曝光会同时移动两端"
                current.highlightFraction > 0 -> "有高光细节风险 · 请确认取景中的红色区域"
                current.shadowFraction > 0 -> "有暗部细节风险 · 请确认取景中的蓝色区域"
                current.primaryTonesFit -> "主要明暗在估计范围内 · 仍需确认重要主体"
                else -> "部分明暗接近或越过估计边界 · 需要取舍"
            },
            11,
            if (current.bothEndsNeedCompromise) red else amber,
        )
        if (current.hasLuminanceLimit) {
            Label("画面含黑／白亮度触限 · 真实亮度可能超出受测范围", 9, unknown)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MeterButton(a.toneGoal.shortLabel, true, true, Modifier.weight(1.35f), height = 44.dp) {
                a.toneGoalDialog()
            }
            MeterButton(
                analysis.referencePoint?.let { "点 #$it" } ?: "选择主体",
                analysis.referencePoint != null,
                true,
                Modifier.weight(1f),
                height = 44.dp,
            ) {
                if (a.spots.isNotEmpty()) a.safetyPointDialog()
                else if (!a.frozen) a.toggleFreeze()
                else a.message = "点按取景画面添加主体测光点"
            }
        }
        val decision = analysis.decisionLabel()
        val recommendation = analysis.recommendedStops
        if (decision != null && recommendation != null) {
            Column(Modifier.fillMaxWidth()) {
                Label(decision, 10, amber, Modifier.fillMaxWidth())
                if (abs(recommendation) <= 5) {
                    MeterButton("采用 " + signed(recommendation), true, true,
                        Modifier.fillMaxWidth(), height = 44.dp) {
                        a.applySafetyOffset(recommendation)
                    }
                } else Label("请手动设置", 10, red)
            }
        }
        if (a.spots.find { it.id == analysis.referencePoint }?.cameraLimited == true) {
            Label("参考点含手机触限 · 真实亮度可能超出受测范围", 9, unknown)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            for (candidate in analysis.comparisons) {
                val selected = candidate.requestedStops == 0.0
                val result = candidate.assessment
                val correction = result.shooting.reciprocity
                Column(
                    Modifier.weight(1f).padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) Color(0xff342c22) else panel)
                        .clickable { a.applySafetyOffset(candidate.requestedStops) }
                        .semantics {
                            contentDescription = "采用" +
                                (if (selected) "当前" else signed(candidate.requestedStops) + "EV") +
                                "，快门" + timeLabel(candidate.metered.t) +
                                "，光圈" + n(candidate.metered.a)
                        }
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Label(
                        when {
                            candidate.requestedStops < 0 -> "−1/2 EV"
                            candidate.requestedStops > 0 -> "+1/2 EV"
                            else -> "当前"
                        },
                        10, if (selected) amber else paper, bold = selected, monoType = true,
                    )
                    Label(timeLabel(candidate.metered.t) + " · ƒ/" + n(candidate.metered.a),
                        9, muted, monoType = true)
                    if (correction.exposureSeconds != null &&
                        (correction.status == ReciprocityStatus.CORRECTED ||
                            correction.status == ReciprocityStatus.ESTIMATED)
                    ) Label("→" + timeLabel(correction.exposureSeconds), 9, amber, monoType = true)
                    if (correction.status == ReciprocityStatus.APERTURE_ADVICE) {
                        Label("光圈指令", 9, amber)
                    }
                    if (correction.status == ReciprocityStatus.UNPUBLISHED &&
                        correction.meteredSeconds >= 1.0
                    ) {
                        Label("倒易律 ?", 9, red)
                    }
                }
            }
        }
        val correction = current.shooting.reciprocity
        val instruction = if (!a.film.digital) when (correction.status) {
            ReciprocityStatus.CORRECTED ->
                "实拍指令 · " + correction.label + " · 额外密度变化未建模"
            ReciprocityStatus.ESTIMATED ->
                "经验估算 · " + correction.label + " · 额外密度变化未建模"
            ReciprocityStatus.APERTURE_ADVICE ->
                "实拍指令 · " + correction.label + " · 不换算成曲线平移"
            else -> null
        } else null
        if (instruction != null) Label(instruction, 9, muted)
        if (!a.film.digital && current.shooting.uncertain && current.shooting.metered.t >= 1.0) {
            Label("该快门未公开倒易律补偿 · 普通曲线仅作参考", 9, red)
        }
        if (analysis.comparisons.any {
                it.assessment.shooting.uncertain && it.assessment.shooting.metered.t >= 1.0
            }
        ) {
            Label("带 ? 的候选没有该快门的补偿资料，不借用相邻档位", 9, red)
        }
        Label("红 高光风险 · 蓝 暗部风险 · 估算参考，请用灰卡与实片复核", 9, muted)
    }
}

@Composable
private fun Meter(a: MainActivity, frozen: Boolean) {
    val e = a.exposure
    BoxWithConstraints(Modifier.fillMaxSize().background(panel)) {
        val meterWidth = maxWidth
        // A stacked narrow dock needs another 42 dp. Switch it to the short,
        // full-width ISO layout before the fixed controls consume the exposure table.
        val short = maxHeight < if (meterWidth < 180.dp) 260.dp else 200.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for (mode in listOf("A", "S", "M")) {
                    MeterButton(mode, a.mode == mode, !frozen, Modifier.weight(1f),
                        height = if (short) 34.dp else 44.dp) {
                        a.modeTo(mode)
                    }
                }
                if (short) {
                    Spacer(Modifier.width(3.dp))
                    MeterButton("连续", !a.single, !frozen, Modifier.weight(1.5f), height = 34.dp) {
                        a.single = false
                        a.held = null
                    }
                    MeterButton("单次", a.single, !frozen, Modifier.weight(1.5f), height = 34.dp) {
                        a.meterOnce()
                    }
                } else {
                    Column(
                        Modifier.weight(1.35f).height(44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Label(a.reading?.let { "%.1f".format(java.util.Locale.US, it) } ?: "—", 18,
                            if (a.cal == null) red else paper, monoType = true)
                        Label("EV100", 8, muted)
                    }
                }
            }
            if (a.mode == "M") {
                Label(if (a.sample == null) "— EV" else "曝光偏差  " + signed((a.appliedReading ?: e.ev) - e.ev) + " EV",
                    12, amber, Modifier.padding(vertical = 4.dp), monoType = true)
            } else {
                val requested = a.compensationThirds / 3.0
                val residual = a.appliedReading?.let { it - e.ev - requested }
                ExposureCompensation(a.compensationThirds, residual, !frozen,
                    if (short) 40.dp else 48.dp, a::adjustCompensation, a::stepCompensation)
            }
            ExposureTable(a, e, Modifier.weight(2.25f), frozen)
            if (short) {
                if (meterWidth >= 340.dp) {
                    val iso = (speeds + a.manual.iso).distinct().sorted()
                    Wheel("ISO", iso, e.iso, !frozen && !a.isoLocked, ::isoLabel,
                        Modifier.fillMaxWidth().height(42.dp).padding(top = 2.dp),
                        lockLabel = if (frozen) "画面冻结" else if (a.isoLocked) "已锁定 · 解锁" else "锁定至 " + isoLabel(a.filmIso),
                        lockEnabled = !frozen,
                        onLock = { if (!frozen) a.toggleIsoLock() }) {
                        a.setValues(a.manual.copy(iso = it))
                    }
                } else {
                    CompactIso(a, e, frozen, Modifier.fillMaxWidth().height(42.dp).padding(top = 2.dp))
                }
            } else {
                MeasurementDock(a, e, meterWidth >= 340.dp, meterWidth < 180.dp, frozen,
                    Modifier.fillMaxWidth().height(if (meterWidth < 180.dp) 100.dp else 58.dp))
            }
        }
    }
}

@Composable
private fun MeasurementDock(
    a: MainActivity,
    e: Exposure,
    wide: Boolean,
    stacked: Boolean,
    frozen: Boolean,
    modifier: Modifier,
) {
    @Composable
    fun meteringButtons(continuousModifier: Modifier, singleModifier: Modifier) {
        MeterButton("连续", !a.single, !frozen, continuousModifier, height = 48.dp) {
            a.single = false
            a.held = null
        }
        MeterButton("单次", a.single, !frozen, singleModifier,
            height = 48.dp, fontSize = 14) { a.meterOnce() }
    }
    if (stacked) {
        Column(modifier.padding(top = 3.dp)) {
            CompactIso(a, e, frozen, Modifier.fillMaxWidth().height(44.dp))
            Row(Modifier.fillMaxWidth()) {
                meteringButtons(Modifier.weight(1f), Modifier.weight(1f))
            }
        }
    } else {
        Row(modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                val iso = (speeds + a.manual.iso).distinct().sorted()
                Wheel("ISO", iso, e.iso, !frozen && !a.isoLocked, ::isoLabel, Modifier.weight(1.6f).fillMaxHeight(),
                    lockLabel = if (frozen) "画面冻结" else if (a.isoLocked) "已锁定 · 解锁" else "锁定至 " + isoLabel(a.filmIso),
                    lockEnabled = !frozen,
                    onLock = { if (!frozen) a.toggleIsoLock() }) {
                    a.setValues(a.manual.copy(iso = it))
                }
            } else {
                CompactIso(a, e, frozen, Modifier.weight(1.9f).fillMaxHeight())
            }
            meteringButtons(
                Modifier.weight(if (wide) .72f else 1f),
                Modifier.weight(if (wide) .84f else 1.15f),
            )
        }
    }
}

@Composable
private fun CompactIso(a: MainActivity, e: Exposure, frozen: Boolean, modifier: Modifier) {
    val m = modifier.clip(RoundedCornerShape(4.dp)).background(ink)
    if (a.isoLocked || frozen) {
        Column(
            m.clickable(enabled = !frozen, onClick = a::toggleIsoLock).semantics(mergeDescendants = true) {
                contentDescription = "ISO " + isoLabel(e.iso) +
                    if (frozen) "，画面已冻结" else "，已锁定至胶片，点击解锁"
                if (frozen) disabled()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Label("ISO " + isoLabel(e.iso), 14, muted, bold = true, monoType = true)
            Label(if (frozen) "已冻结" else "解锁", 10, if (frozen) muted else amber)
        }
    } else {
        Row(m, verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(onClick = a::chooseIso)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "选择 ISO，当前 ISO " + isoLabel(e.iso)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Label("ISO ▾", 9, muted)
                Label(isoLabel(e.iso), 14, paper, bold = true, monoType = true)
            }
            Box(
                Modifier.width(48.dp).fillMaxHeight().background(panel)
                    .clickable(onClick = a::toggleIsoLock).semantics(mergeDescendants = true) {
                        contentDescription = "锁定 ISO 至胶片 ISO " + isoLabel(a.filmIso)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Label("锁定", 11, amber)
            }
        }
    }
}

@Composable
private fun MeterButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    height: Dp = 31.dp,
    fontSize: Int = 12,
    action: () -> Unit,
) {
    Box(modifier.padding(1.dp).height(height).clip(RoundedCornerShape(4.dp))
        .background(if (selected && enabled) amber else if (selected) amber.copy(alpha = .18f) else ink)
        .clickable(enabled = enabled, onClick = action)
        .semantics(mergeDescendants = true) {
            this.selected = selected
            if (!enabled) disabled()
        },
        contentAlignment = Alignment.Center) {
        Label(text, fontSize,
            if (selected && enabled) ink else if (selected) amber.copy(alpha = .65f)
            else if (enabled) paper else muted,
            bold = true)
    }
}

@Composable
private fun ExposureCompensation(
    thirds: Int,
    residual: Double?,
    enabled: Boolean,
    height: Dp = 48.dp,
    update: (Int) -> Unit,
    step: (Int) -> Unit,
) {
    val change by rememberUpdatedState(update)
    val stepChange by rememberUpdatedState(step)
    val warning = residual != null && abs(residual) > .25
    @Composable
    fun stepButton(
        text: String,
        delta: Int,
        description: String,
        buttonModifier: Modifier = Modifier.width(44.dp),
    ) {
        Box(
            buttonModifier.fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(4.dp))
                .background(ink).clickable(enabled = enabled) {
                    stepChange(delta)
                }.semantics {
                    contentDescription = description
                    if (!enabled) disabled()
                },
            contentAlignment = Alignment.Center,
        ) {
            Label(text, 18, if (enabled) paper else line, bold = true)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).semantics {
        contentDescription = "曝光补偿"
        progressBarRangeInfo = ProgressBarRangeInfo(thirds / 3f, -5f..5f, 29)
        setProgress { value -> if (enabled) change((value * 3).roundToInt()); enabled }
        if (!enabled) disabled()
    }) {
        if (maxWidth < 146.dp) {
            // Keep both exact actions present even in a deliberately narrow saved split.
            // A sub-pixel pseudo-slider is less useful than simply omitting the scale.
            Column(Modifier.fillMaxSize()) {
                Label(
                    (if (warning) "超限 " + signed(residual!!) else "补偿 ") +
                        " " + signed(thirds / 3.0) + " EV",
                    9, if (warning) red else amber, monoType = true,
                )
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    stepButton("−", -1, "减少 1/3 EV 曝光补偿", Modifier.weight(1f))
                    stepButton("+", 1, "增加 1/3 EV 曝光补偿", Modifier.weight(1f))
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(58.dp)) {
                    Label(if (warning) "超限 " + signed(residual!!) + " EV" else "曝光补偿",
                        9, if (warning) red else muted)
                    Label(signed(thirds / 3.0) + " EV", 11, amber, monoType = true)
                }
                stepButton("−", -1, "减少 1/3 EV 曝光补偿")
                Canvas(Modifier.weight(1f).fillMaxHeight().pointerInput(enabled) {
                    if (enabled) awaitEachGesture {
                        val down = awaitFirstDown()
                        fun selectAt(x: Float) {
                            val inset = 6.dp.toPx()
                            val fraction = ((x-inset)/(size.width-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f)
                            change((fraction*30).roundToInt()-15)
                        }
                        selectAt(down.position.x)
                        down.consume()
                        do {
                            val pointer = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (pointer.pressed) selectAt(pointer.position.x)
                            pointer.consume()
                        } while (pointer.pressed)
                    }
                }) {
                    val inset = 6.dp.toPx()
                    val length = size.width - 2*inset
                    fun x(i:Int) = inset + (i+15)/30f*length
                    val mid=size.height/2
                    drawLine(line, Offset(inset, mid), Offset(size.width-inset, mid), 1.dp.toPx())
                    drawLine(amber, Offset(x(0),mid), Offset(x(thirds),mid), 1.5.dp.toPx())
                    for (i in -15..15) {
                        val half = (if (i==0) 5.dp else if (i%3==0) 4.dp else 2.dp).toPx()
                        drawLine(if (i==0) paper else muted, Offset(x(i),mid-half), Offset(x(i),mid+half))
                    }
                    drawCircle(amber, 4.dp.toPx(), Offset(x(thirds),mid))
                }
                stepButton("+", 1, "增加 1/3 EV 曝光补偿")
            }
        }
    }
}

@Composable
private fun ExposureTable(a: MainActivity, e: Exposure, m: Modifier, frozen: Boolean) {
    val pairs = remember(e) { equivalentExposures(e) }
    val selected = pairs.indexOf(e)
    val correction = remember(e.t, a.film.name) { reciprocity(a.film.name, e.t) }
    BoxWithConstraints(m.fillMaxWidth()) {
        val count =
            when {
                maxWidth >= 720.dp -> 9
                maxWidth >= 420.dp -> 7
                maxWidth >= 280.dp -> 5
                else -> 3
            }.coerceAtMost(pairs.size)
        val first = (selected - count / 2).coerceIn(0, pairs.size - count)
        val shown = pairs.subList(first, first + count)
        val corrections =
            remember(shown, a.film.name) { shown.map { reciprocity(a.film.name, it.t) } }
        val showTimes = corrections.any {
            it.status == ReciprocityStatus.CORRECTED || it.status == ReciprocityStatus.ESTIMATED
        }
        val compact = maxHeight < 100.dp
        val veryCompact = maxHeight < 56.dp
        val columnWidth = maxWidth / count
        val baseSize = if (columnWidth < 56.dp) 11 else if (columnWidth < 72.dp) 13 else 17
        val size = if (veryCompact) minOf(baseSize, 12) else baseSize
        Column(Modifier.fillMaxSize()) {
            if (!veryCompact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label(if (showTimes) "等效曝光 · → 实拍" else "等效曝光", if (compact) 10 else 12, muted)
                }
            }
            Row(
                Modifier.weight(1f).fillMaxWidth().pointerInput(a.mode, frozen) {
                    if (frozen) return@pointerInput
                    var apertureRow = false
                    var remainder = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            apertureRow = it.y > this.size.height * .5
                            remainder = 0f
                        }
                    ) { change, delta ->
                        change.consume()
                        remainder -= delta
                        val pitch = 20.dp.toPx()
                        val steps = (remainder / pitch).toInt()
                        if (steps != 0) {
                            remainder -= steps * pitch
                            val current = a.exposure
                            when (a.mode) {

                                "S" ->
                                    a.setValues(
                                        a.manual.copy(
                                            t =
                                                shutters[
                                                    (shutters.indexOf(current.t) + steps).coerceIn(
                                                        shutters.indices
                                                    )]
                                        )
                                    )
                                "A" ->
                                    a.setValues(
                                        a.manual.copy(
                                            a =
                                                apertures[
                                                    (apertures.indexOf(current.a) + steps).coerceIn(
                                                        apertures.indices
                                                    )]
                                        )
                                    )
                                else ->
                                    if (apertureRow)
                                        a.setValues(
                                            a.manual.copy(
                                                a =
                                                    apertures[
                                                        (apertures.indexOf(current.a) + steps)
                                                            .coerceIn(apertures.indices)]
                                            )
                                        )
                                    else
                                        a.setValues(
                                            a.manual.copy(
                                                t =
                                                    shutters[
                                                        (shutters.indexOf(current.t) + steps)
                                                            .coerceIn(shutters.indices)]
                                            )
                                        )
                            }
                        }
                    }
                }
            ) {
                for ((idx, pair) in shown.withIndex()) {
                    val active = pair == e
                    val color = if (active) paper else muted
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .background(if (active) Color(0xff342c22) else Color.Transparent)
                            .clickable(enabled = !frozen) { a.selectPair(pair) }
                            .semantics {
                                contentDescription =
                                    "快门 " +
                                        timeLabel(pair.t) +
                                        "，光圈 " +
                                        n(pair.a) +
                                        "，ISO " +
                                        isoLabel(pair.iso)
                                this.selected = active
                                if (frozen) disabled()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(
                            Modifier.fillMaxWidth()
                                .height(2.dp)
                                .background(if (active) amber else line)
                        )
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Label(timeLabel(pair.t), if (active) size + (if (veryCompact) 1 else 3) else size,
                                color, bold = active, monoType = true)
                        }
                        if (showTimes && !veryCompact) {
                            val actual = corrections[idx]
                            Label(
                                if (
                                    actual.status == ReciprocityStatus.CORRECTED ||
                                        actual.status == ReciprocityStatus.ESTIMATED
                                ) "→" + n(round(actual.exposureSeconds!! * 10) / 10) + "″"
                                else "",
                                if (compact) 9 else 11,
                                amber,
                                monoType = true,
                            )
                        }
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Label("ƒ/" + n(pair.a), if (active) size + (if (veryCompact) 1 else 3) else size,
                                color, bold = active, monoType = true)
                        }
                        if (!veryCompact) {
                            Canvas(Modifier.fillMaxWidth().height(if (compact) 4.dp else 7.dp)) {
                                for (i in 0..2) {
                                    val x = this.size.width * (i + .5f) / 3
                                    drawLine(
                                        if (active && i == 1) amber else muted,
                                        Offset(x, if (i == 1) 0f else this.size.height * .5f),
                                        Offset(x, this.size.height),
                                        1.dp.toPx(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (
                e.t < 1 &&
                    !a.film.digital &&
                    corrections.any {
                        it.meteredSeconds > 1 && it.status == ReciprocityStatus.UNPUBLISHED
                    }
            )
                Label("长曝光 · 厂家未给补偿值", 10, muted)
            if (e.t >= 1 && !a.film.digital)
                Label(
                    correction.label,
                    if (compact) 10 else 12,
                    if (correction.status == ReciprocityStatus.CORRECTED) amber else muted,
                )
        }
    }
}

@Composable
private fun Wheel(
    title: String,
    values: List<Double>,
    value: Double,
    enabled: Boolean,
    fmt: (Double) -> String,
    m: Modifier,
    lockLabel: String,
    lockEnabled: Boolean = true,
    onLock: () -> Unit,
    change: (Double) -> Unit,
) {
    val selected = values.indexOf(value).coerceAtLeast(0)
    val cb by rememberUpdatedState(change)
    val current by rememberUpdatedState(selected)
    BoxWithConstraints(
        m.fillMaxWidth().semantics { contentDescription = title + " " + fmt(value) }
    ) {
        val count =
            when {
                maxWidth >= 570.dp -> 9
                maxWidth >= 380.dp -> 7
                maxWidth >= 235.dp -> 5
                else -> 3
            }
        val slots =
            ((-values.size)..values.size).map { selected + it * 3 }.filter { it in values.indices }
        val center = slots.indexOf(selected)
        val first = (center - count / 2).coerceIn(0, max(0, slots.size - count))
        val shown = slots.drop(first).take(count)
        Column(
            Modifier.fillMaxSize().pointerInput(values, enabled) {
                if (!enabled) return@pointerInput
                var remainder = 0f
                var index = 0
                detectHorizontalDragGestures(
                    onDragStart = {
                        index = current
                        remainder = 0f
                    }
                ) { c, dx ->
                    c.consume()
                    val pitch = this.size.width.toFloat() / (shown.size * 3)
                    remainder -= dx
                    val steps = (remainder / pitch).toInt()
                    if (steps != 0) {
                        index = (index + steps).coerceIn(values.indices)
                        cb(values[index])
                        remainder -= steps * pitch
                    }
                }
            }
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(title, 11, muted, Modifier.weight(1f))
                Label(lockLabel, 11, if (lockEnabled) amber else muted,
                    Modifier.clickable(enabled = lockEnabled, onClick = onLock)
                        .semantics { if (!lockEnabled) disabled() }
                        .padding(horizontal = 3.dp, vertical = 2.dp))
            }
            Row(
                Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in shown) {
                    val active = i == selected
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled) {
                            cb(values[i])
                        }.semantics { if (!enabled) disabled() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(
                            fmt(values[i]),
                            if (count >= 5) 17 else 12,
                            if (active) amber else if (enabled) muted else line,
                            bold = active,
                            monoType = true,
                        )
                        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp)) {
                            for (j in 0..2) {
                                val x = size.width * (j + .5f) / 3
                                drawLine(
                                    if (active && j == 1) amber else muted,
                                    Offset(x, if (j == 1) 0f else size.height / 2),
                                    Offset(x, size.height),
                                    1.dp.toPx(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Settings(a: MainActivity, m: Modifier) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)).clickable {
            a.settings = false
        }
    )
    Column(
        m.background(panel, RoundedCornerShape(8.dp))
            .border(1.dp, line, RoundedCornerShape(8.dp))
            .clickable(enabled = false) {}
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("设置", 22, paper, Modifier.weight(1f), true)
            Action("完成") { a.settings = false }
        }
        fun open(url: String) {
            try {
                a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(a, "请安装可打开网页的浏览器", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("直方图像素纵轴", 14, paper, Modifier.weight(1f))
            for ((index, label) in listOf("线性", "对数").withIndex()) {
                Action(label, a.logCounts == (index == 1)) {
                    a.logCounts = index == 1
                    a.prefs.edit().putBoolean("logCounts", a.logCounts).apply()
                }
            }
        }
        Setting("组件比例", "拖动分界线") {
            a.settings = false
            a.resizing = true
        }
        Setting("预览按钮", if (a.useLut) "LUT 色彩模拟" else "亮度模拟") {
            a.choose("拍摄预览", listOf("亮度模拟", "LUT 色彩模拟")) { i ->
                a.useLut = i == 1
                a.prefs.edit().putBoolean("useLut", a.useLut).apply()
            }
        }
        Setting("实时预览分辨率", n(a.previewScale) + "×") {
            val scales = listOf(.25, .5, .75, 1.0)
            a.choose("实时预览分辨率", scales.map { "${it}×" }) { i ->
                a.previewScale = scales[i]
                a.put("previewScale", scales[i])
            }
        }
        Label("拼接配准算法",14,paper)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            for (mode in listOf("LK","ORB","ECC","自动"))
                Action(mode,a.scanAlgorithm == mode) { a.chooseScanAlgorithm(mode) }
        }
        Row {
            Action(if (a.recordScan) "关闭拼接诊断记录" else "开启拼接诊断记录",a.recordScan) { a.recordScan = !a.recordScan }
            Action("导出拼接诊断 ZIP") { a.exportScan() }
        }
        Row(Modifier.fillMaxWidth().toggleable(a.showFps,role=Role.Switch) { enabled ->
            a.showFps = enabled
            a.prefs.edit().putBoolean("showFps",enabled).apply()
        }.padding(vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
            Label("显示实际预览 FPS",14,paper,Modifier.weight(1f))
            Box(Modifier.size(36.dp,20.dp).background(if(a.showFps) amber else line,RoundedCornerShape(10.dp))
                .padding(3.dp),contentAlignment=if(a.showFps) Alignment.CenterEnd else Alignment.CenterStart) {
                Spacer(Modifier.size(14.dp).background(if(a.showFps) ink else muted,RoundedCornerShape(7.dp)))
            }
        }
        Label("画面静止或冻结时恢复完整取景分辨率。", 11, muted)
        a.sample?.let { Label("镜头输入 ${it.captureWidth}×${it.captureHeight} · 取景裁框 ${it.frame.w}×${it.frame.h}", 11, muted) }
        if (a.useLut) {
            Label("Andrea Volpato / spektrafilm", 11, muted)
            Action("CC BY-SA 4.0 ↗") { open("https://creativecommons.org/licenses/by-sa/4.0/") }
            val variants = a.variants
            val entry = a.variant
            if (entry == null) Label(a.lutStatus, 11, amber) else {
                Setting("LUT 版本", "${variants.indexOf(entry) + 1} / ${variants.size}") {
                    a.choose("选择 LUT 烘焙版本", variants.map { it.label }) { i ->
                        a.chosenLuts[a.film.name] = variants[i].file
                        a.put("lutVariant:" + a.film.name, variants[i].file)
                    }
                }
                Label(entry.label, 11, muted)
                if (entry.approximation.isNotBlank()) Label(entry.approximation, 11, muted)
                if (entry.source.startsWith("https://")) Action("原始配置 ↗") { open(entry.source) }
                if (a.failedLut == entry.file) Label(a.lutStatus, 11, amber)
            }
        }
        Setting(
            "手机镜头",
            if (a.cameraChoice == "自动") "自动 · " + (a.current?.id ?: "—")
            else a.current?.label ?: "—",
        ) {
            a.choose("手机镜头", listOf("自动选择") + a.lenses.map { it.label }) { i ->
                a.cameraChoice = if (i == 0) "自动" else a.lenses[i - 1].id
                a.put("camera", a.cameraChoice)
                a.selectCamera(true)
            }
        }
        Setting("曝光曲线", a.film.name + " · ISO " + isoLabel(a.filmIso)) { a.chooseFilm() }
        Setting("测光 ISO", isoLabel(a.manual.iso) + if (a.isoLocked) " · 已锁定" else " · 手动") {
            if (a.frozen) a.notifyUser("请先退出点测光")
            else if (a.isoLocked) a.choose("跟随胶片 ISO " + isoLabel(a.filmIso), listOf("解锁并设置 ISO")) {
                a.input("测光 ISO", a.manual.iso, .001..10000000.0) { value ->
                    a.toggleIsoLock()
                    a.setValues(a.manual.copy(iso = value))
                }
            }
            else a.choose("测光 ISO", listOf("设置 ISO", "恢复胶片 ISO " + isoLabel(a.filmIso))) { i ->
                if (i == 1) a.toggleIsoLock()
                else a.input("测光 ISO", a.manual.iso, .001..10000000.0) { a.setValues(a.manual.copy(iso = it)) }
            }
        }
        FlowRow {
            Action("新建预设") { a.editCustomPreset() }
            a.customPresets.find { it.id == a.selectedPresetId }?.let { preset ->
                Action("编辑") { a.editCustomPreset(preset) }
            }
            Action("导入 JSON") { a.pickPresetFile() }
            if (a.customPresets.isNotEmpty()) Action("导出 JSON") { a.exportPresetFile() }
        }
        Label(a.film.info + " 上下限为预览用估算值。", 11, muted)
        if (colorCurves.containsKey(a.film.name)) Label("分层色彩预览 · 手机 RGB 光谱近似，日光中性校正。", 11, muted)
        val record=filmRecords.find{it.film.name==a.film.name}
        Row {
            if (record != null) Action(filmSourceLabel(record.document)) { open(filmSourceUrl(record.document)) }
            reciprocity(a.film.name, 10.0).sourceUrl?.let { url -> Action("倒易律 ↗") { open(url) } }
        }
        val styles = listOf("彩色", "黑白", "Aerochrome", "Lomo Purple")
        Setting("色彩映射", styles[a.style]) {
            a.choose("色彩映射", styles) {
                a.style = it
                a.put("style", it)
            }
        }
        if (a.style >= 2) Label("可见光近似配色" + if (a.style == 2) "，无法还原真实红外反射。" else "。", 11, muted)
        Setting("镜头校准", a.cal?.let { n(it.stops) + " EV" } ?: "未校准") { a.calibrationDialog() }
        Label(
            a.cal?.let { it.channel + " · ISO " + it.iso + " · 镜头 " + a.current?.id }
                ?: "推荐使用实体灰卡或九级阶梯卡。也可对静止场景估测。",
            11,
            muted,
        )
    }
}

@Composable
private fun Setting(name: String, value: String, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label(name, 14, paper)
        Label(value + " ›", 13, amber)
    }
}
