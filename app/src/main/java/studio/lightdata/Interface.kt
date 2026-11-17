package studio.lightdata

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val ink = Color(0xff101112)
private val panel = Color(0xff191a1c)
private val line = Color(0xff3a3c3e)
private val paper = Color(0xfff0efea)
private val muted = Color(0xffa5a6a3)
private val amber = Color(0xffffbd59)
private val red = Color(0xffec756a)
private val green = Color(0xff6dd8a0)
private val spotColors = listOf(Color(0xffffce58), Color(0xff55ccff), Color(0xffff85bb),
    Color(0xff68e3a8), Color(0xffba9dff), Color(0xffff9864))
private fun spotColor(id: Int): Color = spotColors.getOrNull(id - 1)
    ?: Color.hsv(((id * 137.508) % 360).toFloat(), .55f, 1f)

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
    BackHandler(a.settings || a.resizing || a.frozen || a.scanActive) {
        when {
            a.settings -> a.settings = false
            a.resizing -> a.resizing = false
            a.frozen -> a.toggleFreeze()
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
            Panels(a, sideBySide, prefix, if (square) .5f else .6f, if (square) .5f else .46f)
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Toolbar(a, true)
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Toolbar(a, false)
                Box(Modifier.weight(1f)) { content() }
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
private fun Panels(a: MainActivity, sideBySide: Boolean, prefix: String, defaultP: Float, defaultQ: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val p = a.layout[prefix + "1"] ?: defaultP
        val q = a.layout[prefix + "2"] ?: defaultQ
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(p).fillMaxHeight()) { Finder(a) }
                Split(a, prefix + "1", true, w, defaultP)
                Column(Modifier.weight(1 - p).fillMaxHeight()) {
                    Box(Modifier.weight(q)) { Histogram(a) }
                    Split(a, prefix + "2", false, h, defaultQ)
                    Box(Modifier.weight(1 - q)) { Meter(a) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(p)) { Finder(a) }
                Split(a, prefix + "1", false, h, defaultP)
                Row(Modifier.fillMaxWidth().weight(1 - p)) {
                    Box(Modifier.weight(q)) { Histogram(a) }
                    Split(a, prefix + "2", true, w, defaultQ)
                    Box(Modifier.weight(1 - q)) { Meter(a) }
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
private fun Toolbar(a: MainActivity, land: Boolean) {
    @Composable
    fun brand() {
        Label("LightData", if (land) 10 else 20, paper, bold = true)
    }
    @Composable
    fun buttons() {
        Tool("预览", 0, a.preview) { a.preview = !a.preview }
        Tool("测光点", 1, a.frozen) { if (!a.runningCal) a.toggleFreeze() }
        Tool(if (a.resizing) "完成" else "设置", 2, a.resizing) {
            if (a.resizing) a.resizing = false else a.settings = true
        }
    }
    if (land)
        Column(
            Modifier.width(55.dp).fillMaxHeight().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            brand()
            Spacer(Modifier.weight(1f))
            buttons()
        }
    else
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            brand()
            Spacer(Modifier.weight(1f))
            buttons()
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
                else -> {
                    for (i in 0..2) {
                        val y = .18f + i * .32f
                        drawLine(c, p(.04f, y), p(.96f, y), 2f)
                        drawCircle(c, w * .105f, p(if (i == 1) .68f else .32f, y))
                    }
                }
            }
        }
        Label(title, 10, c)
    }
}

@Composable
private fun Finder(a: MainActivity) {
    val s = a.sample
    val f = s?.frame
    Column(Modifier.fillMaxSize().background(Color(0xff0a0b0c))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Action(a.format.name + " ▾") { if (!a.frozen) a.formatDialog() }
            Action(n(a.focal) + " mm") { if (!a.frozen) a.changeFocal() }
            Spacer(Modifier.weight(1f))
            Label(
                when {
                    a.frozen -> "● HOLD"
                    s?.scan?.coverage == 1.0 -> "● 拼接构图"
                    a.scanActive -> "● 自动采集"
                    s?.missing == true -> "○ 待确认中心"
                    f == null -> "○ WAIT"
                    else -> "● LIVE"
                },
                10,
                if (a.frozen) amber else muted,
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
                    val look =
                        Look(
                            f,
                            a.response,
                            f.cameraEv - a.exposure.ev,
                            a.film,
                            a.preview,
                            a.mode == "M",
                            a.style,
                        )
                    AndroidView(
                        factory = { Preview(it) },
                        modifier = Modifier.fillMaxSize(),
                        update = {
                            it.look = look
                            it.requestRender()
                        },
                    )
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(a.frozen) {
                            detectTapGestures {
                                if (a.frozen)
                                    a.point(
                                        it.x / size.width.toDouble(),
                                        it.y / size.height.toDouble(),
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
                        val guide = s.scan?.takeIf { a.scanActive && it.coverage < 1 }
                        val aim = guide?.aim?.let {
                            Offset(it.x.toFloat().coerceIn(.02f,.98f)*w, it.y.toFloat().coerceIn(.02f,.98f)*h)
                        } ?: center
                        drawLine(edge, aim - Offset(8.dp.toPx(), 0f), aim + Offset(8.dp.toPx(), 0f), 2f)
                        drawLine(edge, aim - Offset(0f, 8.dp.toPx()), aim + Offset(0f, 8.dp.toPx()), 2f)
                        guide?.next?.let {
                            val to = Offset(it.x.toFloat()*w, it.y.toFloat()*h)
                            drawLine(amber.copy(alpha=.65f), aim, to, 1.dp.toPx())
                            drawCircle(amber, 6.dp.toPx(), to, style=Stroke(2.dp.toPx()))
                        }
                        for (pt in a.spots) {
                            val at = Offset((pt.x * w).toFloat(), (pt.y * h).toFloat())
                            drawCircle(Color.Black, 9.dp.toPx(), at, style = Stroke(4.dp.toPx()))
                            drawCircle(spotColor(pt.id), 9.dp.toPx(), at, style = Stroke(2.dp.toPx()))
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
                }
            } else Label("正在等待相机", 13, muted)
        }
        if (a.frozen) {
            val summary =
                a.spots.lastOrNull()?.let {
                    "#%d   %.1f EV100   ·   Zone %.1f".format(
                        java.util.Locale.US, it.id, it.ev, it.ev - a.exposure.ev + 5
                    )
                } ?: "点按画面添加测光点"
            Label(summary + "   ·   再点同一位置删除", 12,
                a.spots.lastOrNull()?.let { spotColor(it.id) } ?: amber, Modifier.padding(horizontal = 10.dp))
        }
        if ((s?.missing == true || a.scanActive) && !a.frozen && !a.runningCal)
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
                        guide.needsOverlap -> "请移回已拍区域，保留重叠"
                        guide.coverage >= 1 -> "已完成 · 转动设备二次构图"
                        else -> "向黄圈缓慢转动 · 自动采集"
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
            Label(if (a.preview) "胶片预览" else "取景", 10, if (a.preview) amber else muted)
        }
    }
}

@Composable
private fun Histogram(a: MainActivity) {
    val sample = a.sample
    val film = a.film
    val middle = a.exposure.ev
    val response = a.response
    val reading = a.reading
    var window by remember { mutableStateOf<HistogramWindow?>(null) }
    val chart = window ?: HistogramWindow.following(reading)
    val currentChart by rememberUpdatedState(chart)
    val bins = remember(sample, response, chart.left, chart.span) {
        sample?.let { chart.bins(it.hist, response, it.frame.cameraEv, 128) } ?: IntArray(128)
    }
    val peak = bins.max().coerceAtLeast(1)
    val densityMax = ceil(film.dots.maxOf { it.second })
    Column(
        Modifier.fillMaxSize().background(ink).padding(5.dp)
            .border(1.dp, if (a.cal == null) red else line).padding(7.dp)
    ) {
        Label("曝光直方图", 15, bold = true)
        Label(
            if (reading == null) "等待测光" else
                "%.1f EV100".format(java.util.Locale.US, reading) + if (a.cal == null) " · 估测" else "",
            11, if (a.cal == null) red else paper, monoType = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label((if (a.logCounts) "像素 log " else "像素 ") + ceil(peak / chart.gain).toInt(), 9, muted)
            Label("密度 D " + n(round(densityMax / chart.gain * 10) / 10), 9, amber)
        }
        Canvas(
            Modifier.weight(1f).fillMaxWidth()
                .semantics {
                    contentDescription = "场景 EV100 直方图，可横向拖动与双指缩放，双击恢复自动"
                    stateDescription = "%.1f 至 %.1f EV100，纵轴 %.2f 倍".format(
                        java.util.Locale.US, chart.left, chart.right, chart.gain
                    )
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { window = null }) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        var travel = 0f
                        do {
                            val event = awaitPointerEvent()
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
                for ((ev, c) in listOf(middle + film.low to green, middle to paper, middle + film.high to red))
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
                    drawLine(spotColor(pt.id), Offset(xx, 0f), Offset(xx, h), 1.dp.toPx(), pathEffect = pattern)
                    val label = "#" + pt.id
                    val labelX = min(xx + 2, w - paint.measureText(label)).coerceAtLeast(0f)
                    val row = labelRows.indexOfFirst { it < labelX }.let { if (it < 0) labelRows.size else it }
                    if (row == labelRows.size) labelRows.add(0f)
                    labelRows[row] = labelX + paint.measureText(label) + 2.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(label, labelX, (12 + 13 * row).dp.toPx(), paint)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val w = size.width
            val paint = android.graphics.Paint(1).apply {
                textSize = 10.dp.toPx()
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
                drawContext.canvas.nativeCanvas.drawText(label, xx, 11.dp.toPx(), paint)
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
                        drawContext.canvas.nativeCanvas.drawText(label, xx, 26.dp.toPx(), paint)
                }
            }
        }
        if (sample != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("欠 %.1f".format(java.util.Locale.US, middle + film.low), 10, green, monoType = true)
            Label("过 %.1f".format(java.util.Locale.US, middle + film.high), 10, red, monoType = true)
        }
    }
}

@Composable
private fun Meter(a: MainActivity) {
    val e = a.exposure
    Column(Modifier.fillMaxSize().background(panel).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for (mode in listOf("A", "S", "M")) {
                MeterButton(mode, a.mode == mode, !a.frozen, Modifier.weight(1f)) { a.modeTo(mode) }
            }
            Spacer(Modifier.width(3.dp))
            MeterButton("连续", !a.single, !a.frozen, Modifier.weight(1.5f)) {
                a.single = false
                a.held = null
            }
            MeterButton("单次", a.single, !a.frozen, Modifier.weight(1.5f)) { a.meterOnce() }
        }
        if (a.mode == "M") {
            Label(if (a.sample == null) "— EV" else "曝光偏差  " + signed((a.appliedReading ?: e.ev) - e.ev) + " EV",
                12, amber, Modifier.padding(vertical = 4.dp), monoType = true)
        } else {
            val requested = a.compensationThirds / 3.0
            val residual = a.appliedReading?.let { it - e.ev - requested }
            ExposureCompensation(a.compensationThirds, residual, !a.frozen, a::adjustCompensation)
        }
        ExposureTable(a, e, Modifier.weight(2.25f))
        val iso = (speeds + a.manual.iso).distinct().sorted()
        Wheel("ISO", iso, e.iso, !a.frozen, ::n, Modifier.weight(1f)) {
            a.setValues(a.manual.copy(iso = it))
        }
    }
}

@Composable
private fun MeterButton(text: String, selected: Boolean, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    Box(modifier.padding(1.dp).height(31.dp).clip(RoundedCornerShape(4.dp))
        .background(if (selected) amber else ink).clickable(enabled = enabled, onClick = action),
        contentAlignment = Alignment.Center) {
        Label(text, 12, if (selected) ink else paper, bold = true)
    }
}

@Composable
private fun ExposureCompensation(thirds: Int, residual: Double?, enabled: Boolean, update: (Int) -> Unit) {
    val change by rememberUpdatedState(update)
    Column(Modifier.fillMaxWidth().semantics {
        contentDescription = "曝光补偿"
        progressBarRangeInfo = ProgressBarRangeInfo(thirds / 3f, -5f..5f, 29)
        setProgress { value -> if (enabled) change((value * 3).roundToInt()); enabled }
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(if (residual != null && abs(residual) > .25) "超限 " + signed(residual) + " EV" else "曝光补偿",
                10, if (residual != null && abs(residual) > .25) red else muted)
            Label(signed(thirds / 3.0) + " EV", 11, amber, monoType = true)
        }
        Canvas(Modifier.fillMaxWidth().height(25.dp).pointerInput(enabled) {
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
    }
}

@Composable
private fun ExposureTable(a: MainActivity, e: Exposure, m: Modifier) {
    val pairs = remember(e) { equivalentExposures(e) }
    val selected = pairs.indexOf(e)
    val correction = remember(e.t, a.film.name) { reciprocity(a.film.name, e.t) }
    BoxWithConstraints(m.fillMaxWidth()) {
        val count =
            when {
                maxWidth >= 570.dp -> 9
                maxWidth >= 380.dp -> 7
                maxWidth >= 235.dp -> 5
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
        val size = if (maxWidth < 170.dp) 11 else if (maxWidth < 280.dp) 13 else 17
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(if (showTimes) "等效曝光 · → 实拍" else "等效曝光", if (compact) 10 else 12, muted)
                Label("ISO " + n(e.iso), if (compact) 10 else 12, muted, monoType = true)
            }
            Row(
                Modifier.weight(1f).fillMaxWidth().pointerInput(a.mode, a.frozen) {
                    if (a.frozen) return@pointerInput
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
                            .clickable(enabled = !a.frozen) { a.selectPair(pair) }
                            .semantics {
                                contentDescription =
                                    "快门 " +
                                        timeLabel(pair.t) +
                                        "，光圈 " +
                                        n(pair.a) +
                                        "，ISO " +
                                        n(pair.iso)
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
                            Label(timeLabel(pair.t), size, color, bold = active, monoType = true)
                        }
                        if (showTimes) {
                            val actual = corrections[idx]
                            Label(
                                if (
                                    actual.status == ReciprocityStatus.CORRECTED ||
                                        actual.status == ReciprocityStatus.ESTIMATED
                                ) "→" + n(round(actual.exposureSeconds!! * 10) / 10) + "″"
                                else "·",
                                if (compact) 9 else 11,
                                amber,
                                monoType = true,
                            )
                        }
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Label("ƒ/" + n(pair.a), size, color, bold = active, monoType = true)
                        }
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
            Label(title, 11, muted)
            Row(
                Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in shown) {
                    val active = i == selected
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled) {
                            cb(values[i])
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Label(
                            fmt(values[i]),
                            if (count >= 5) 17 else 12,
                            if (active) amber else muted,
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
        Setting("自定义 ISO", n(a.manual.iso)) {
            a.input("目标胶片 ISO", a.manual.iso, .001..10000000.0) {
                a.setValues(a.manual.copy(iso = it))
            }
        }
        Setting("曝光曲线", a.film.name) {
            val brands = filmRecords.map { it.brand }.distinct().sorted()
            a.choose("曝光－密度曲线 · "+films.size+" 款", brands + listOf("自定义数码", "自定义胶片")) { i ->
                if (i < brands.size) {
                    val records=filmRecords.filter{it.brand==brands[i]}.sortedBy{it.film.name}
                    a.choose(brands[i],records.map{it.film.name+"  ·  EI "+it.iso}){j->a.setFilmChoice(records[j].film)}
                }
                else {
                    a.input("总动态范围（EV）", a.total, 1.0..32.0) { t ->
                        a.input("中灰以上（EV）", min(a.above, t - .1), .1..t - .1) { v ->
                            a.total = t
                            a.above = v
                            a.customDigital = i == brands.size
                            a.put("digital", a.customDigital)
                            a.put("total", t)
                            a.put("above", v)
                            a.setFilmChoice(customFilm(a.customDigital, t, v))
                        }
                    }
                }
            }
        }
        Label(a.film.info + " 上下限为预览用估算值。", 11, muted)
        if (colorCurves.containsKey(a.film.name)) Label("分层色彩预览 · 手机 RGB 光谱近似，日光中性校正。", 11, muted)
        val record=filmRecords.find{it.film.name==a.film.name}
        if(record!=null && record.iso.toDouble()!=a.manual.iso) Action("使用参考 EI "+record.iso){a.setValues(a.manual.copy(iso=record.iso.toDouble()))}
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
