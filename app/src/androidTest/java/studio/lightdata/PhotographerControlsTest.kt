package studio.lightdata

import android.Manifest
import android.os.Bundle
import android.app.UiAutomation
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotographerControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiAutomation
        get() = instrumentation.uiAutomation

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        device.rootInActiveWindow?.let(queue::add)
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            // Compose and AdapterView can report recycled accessibility properties after the
            // pixels have already changed. Refresh before reading text, enabled state or bounds.
            if (!runCatching { next.refresh() }.getOrDefault(false)) continue
            if (next.isVisibleToUser) result += next
            for (i in 0 until next.childCount) next.getChild(i)?.let(queue::add)
        }
        return result
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? =
        allNodes().firstOrNull(predicate)

    private fun failureScreenshot(): String {
        val directory = instrumentation.targetContext.getExternalFilesDir("ui-failures")
            ?: instrumentation.targetContext.filesDir
        directory.mkdirs()
        val target = java.io.File(directory, "timeout-${SystemClock.uptimeMillis()}.png")
        return runCatching {
            val bitmap = device.takeScreenshot()
            try {
                java.io.FileOutputStream(target).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
            target.absolutePath
        }.getOrElse { "无法保存截图：" + it.message }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 6000
        do {
            findNode(predicate)?.let { return it }
            SystemClock.sleep(40)
        } while (SystemClock.uptimeMillis() < deadline)
        error("控件未出现；截图=" + failureScreenshot() + "；当前可见文本：" +
            allNodes().mapNotNull { it.text ?: it.contentDescription }.joinToString(" | ").take(1000))
    }

    private fun scrollTo(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val directions = listOf(
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
        )
        for (direction in directions) for (attempt in 0 until 30) {
            findNode(predicate)?.let { return it }
            val scrollable = findNode {
                it.isScrollable && it.isEnabled && it.className?.toString() == "android.widget.ListView"
            } ?: break
            if (!scrollable.performAction(direction)) break
            instrumentation.waitForIdleSync()
            SystemClock.sleep(50)
        }
        error("双向滚动后仍未找到控件；当前可见文本：" +
            allNodes().mapNotNull { it.text ?: it.contentDescription }.joinToString(" | ").take(1400))
    }

    private fun awaitActivityState(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3000
        do {
            var matched = false
            scenario.onActivity { matched = predicate(it) }
            if (matched) return
            SystemClock.sleep(30)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Activity 状态未按操作更新")
    }

    private fun clickTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var target = node
        while (true) {
            runCatching { target.refresh() }
            if (target.isClickable) return target
            target = target.parent ?: error("控件没有可点击的父节点")
        }
    }

    private fun click(node: AccessibilityNodeInfo) {
        val target = clickTarget(node)
        assertTrue("控件不可点击", target.isEnabled)
        target.refresh()
        assertTrue("点击未执行", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun assertComfortableHeight(node: AccessibilityNodeInfo) {
        val clickable = clickTarget(node)
        val bounds = Rect().also(clickable::getBoundsInScreen)
        val density = instrumentation.targetContext.resources.displayMetrics.density
        assertTrue("触控高度只有 ${bounds.height() / density} dp", bounds.height() >= 44 * density)
    }

    private fun liveSample(): Sample {
        val frame = Frame(2, 2, intArrayOf(0xff303030.toInt(), 0xff808080.toInt(),
            0xffa0a0a0.toInt(), 0xffd0d0d0.toInt()), 8.0, "layout-test")
        val hist = IntArray(256)
        listOf(48, 128, 160, 208).forEach { hist[it] = 1 }
        return Sample(frame, hist, false, null)
    }

    private fun preservingPreferences(keys: List<String>, test: () -> Unit) {
        val prefs = instrumentation.targetContext.getSharedPreferences("meter", Context.MODE_PRIVATE)
        val savedKeys = keys + "warned"
        val original = prefs.all.filterKeys { it in savedKeys }
        try {
            device.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
            // Keep the first-run calibration notice from hiding the controls under test.
            prefs.edit().putBoolean("warned", true).commit()
            test()
        } finally {
            val edit = prefs.edit()
            savedKeys.forEach(edit::remove)
            original.forEach { (key, value) ->
                when (value) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Long -> edit.putLong(key, value)
                }
            }
            edit.commit()
        }
    }

    private fun description(fragment: String): (AccessibilityNodeInfo) -> Boolean =
        { it.contentDescription?.contains(fragment) == true }

    @Test
    fun compensationStepsAreExactAndFreezeDisablesThem() =
        preservingPreferences(listOf("t", "a", "iso", "mode", "compensationThirds")) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.modeTo("A")
                    it.adjustCompensation(0)
                }
                val plus = awaitNode(description("增加 1/3 EV 曝光补偿"))
                val minus = awaitNode(description("减少 1/3 EV 曝光补偿"))
                assertComfortableHeight(plus)
                assertComfortableHeight(minus)
                click(plus)
                click(plus)
                awaitActivityState(scenario) { it.compensationThirds == 2 }
                click(minus)
                awaitActivityState(scenario) { it.compensationThirds == 1 }
                awaitActivityState(scenario) { it.sample != null }
                click(awaitNode { it.text?.toString() == "测光点" })
                awaitNode { it.text?.toString() == "退出冻结" }
                val disabled = clickTarget(awaitNode {
                    description("增加 1/3 EV 曝光补偿")(it) && !clickTarget(it).isEnabled
                })
                assertFalse(disabled.isEnabled)
                disabled.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                scenario.onActivity { assertEquals(1, it.compensationThirds) }
            }
        }

    @Test
    fun narrowIsoCardOpensChoicesAndLocksBackToTheFilm() =
        preservingPreferences(listOf("t", "a", "iso", "filmIsoLocked")) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    if (!it.isoLocked) it.toggleIsoLock()
                }
                instrumentation.waitForIdleSync()
                val locked = findNode(description("已锁定至胶片，点击解锁"))
                // This behavior is specific to the compact dock; wide windows retain the ISO wheel.
                assumeTrue("当前窗口使用宽屏 ISO 拨轮", locked != null)
                assertComfortableHeight(locked!!)
                click(locked)
                scenario.onActivity { assertFalse(it.isoLocked) }
                click(awaitNode(description("选择 ISO，当前 ISO ")))
                click(scrollTo { it.text?.toString() == "ISO 200" })
                awaitActivityState(scenario) { it.manual.iso == 200.0 }
                click(awaitNode(description("选择 ISO，当前 ISO ")))
                click(scrollTo { it.text?.toString() == "自定义…" })
                val editor = awaitNode { it.className?.toString() == "android.widget.EditText" }
                assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "90")
                }))
                click(awaitNode { it.text?.toString() == "应用" })
                scenario.onActivity { assertEquals(90.0, it.manual.iso, 0.0) }
                val lock = awaitNode(description("锁定 ISO 至胶片"))
                assertComfortableHeight(lock)
                click(lock)
                scenario.onActivity {
                    assertTrue(it.isoLocked)
                    assertEquals(it.filmIso, it.manual.iso, 0.0)
                }
                awaitActivityState(scenario) { it.sample != null }
                click(awaitNode { it.text?.toString() == "测光点" })
                awaitNode { it.text?.toString() == "退出冻结" }
                val frozen = clickTarget(awaitNode {
                    description("画面已冻结")(it) && !clickTarget(it).isEnabled
                })
                assertFalse(frozen.isEnabled)
            }
        }

    @Test
    fun wideIsoWheelStillSelectsAndLocksWithoutFrozenAffordances() =
        preservingPreferences(listOf("t", "a", "iso", "filmIsoLocked")) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    if (it.isoLocked) it.toggleIsoLock()
                    it.setValues(it.manual.copy(iso = 100.0))
                }
                instrumentation.waitForIdleSync()
                val lock = findNode { it.text?.toString()?.startsWith("锁定至 ") == true }
                assumeTrue("当前窗口使用窄屏 ISO 卡片", lock != null)
                // ISO 200 is one displayed three-stop slot to the right of ISO 100.
                click(awaitNode { it.text?.toString() == "200" })
                scenario.onActivity { assertEquals(200.0, it.manual.iso, 0.0) }
                click(awaitNode { it.text?.toString()?.startsWith("锁定至 ") == true })
                scenario.onActivity {
                    assertTrue(it.isoLocked)
                    assertEquals(it.filmIso, it.manual.iso, 0.0)
                }
                awaitActivityState(scenario) { it.sample != null }
                click(awaitNode { it.text?.toString() == "测光点" })
                awaitNode { it.text?.toString() == "退出冻结" }
                val frozen = awaitNode {
                    it.text?.toString() == "画面冻结" && !clickTarget(it).isEnabled
                }
                assertFalse(clickTarget(frozen).isEnabled)
            }
        }

    @Test
    fun narrowSavedSplitAtCriticalHeightKeepsAnExposureTouchArea() =
        preservingPreferences(listOf("t", "a", "iso", "mode", "p1", "p2")) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.modeTo("A")
                    it.sample = liveSample()
                    // About a 200–250 dp meter on a portrait phone, with a very narrow saved split.
                    it.layout["p1"] = .68f
                    it.layout["p2"] = .78f
                }
                val pair = clickTarget(awaitNode {
                    it.contentDescription?.startsWith("快门 ") == true
                })
                assertComfortableHeight(pair)
                awaitNode(description("减少 1/3 EV 曝光补偿"))
                awaitNode(description("增加 1/3 EV 曝光补偿"))
                awaitNode(description("已锁定至胶片，点击解锁"))
            }
        }

    private fun chartState(node: AccessibilityNodeInfo): String =
        if (Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString().orEmpty()
        else node.extras.getCharSequence(
            "androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY"
        )?.toString().orEmpty()

    private fun touch(down: Long, action: Int, x: Float, y: Float) {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = .02f
        }
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            assertTrue(device.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
        SystemClock.sleep(15)
    }

    private fun chartHasArea(node: AccessibilityNodeInfo): Boolean {
        if (!description("场景 EV100 直方图")(node)) return false
        val bounds = Rect().also(node::getBoundsInScreen)
        return bounds.width() > 0 && bounds.height() > 0
    }

    private fun panChart(node: AccessibilityNodeInfo) {
        node.refresh()
        val bounds = Rect().also(node::getBoundsInScreen)
        assertTrue("图表触控区域为空：$bounds", bounds.width() > 0 && bounds.height() > 0)
        val y = bounds.exactCenterY()
        val down = SystemClock.uptimeMillis()
        touch(down, MotionEvent.ACTION_DOWN, bounds.left + bounds.width() * .2f, y)
        for (i in 1..6) {
            touch(down, MotionEvent.ACTION_MOVE,
                bounds.left + bounds.width() * (.2f + .08f * i), y)
        }
        touch(down, MotionEvent.ACTION_UP, bounds.left + bounds.width() * .68f, y)
        instrumentation.waitForIdleSync()
    }

    @Test
    fun safetyTabsKeepThePannedChartAndDoNotMergeFreezeWithSingleMetering() =
        preservingPreferences(listOf("safety", "t", "a", "iso", "mode")) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { if (it.safety) it.toggleSafety() }
                awaitActivityState(scenario) { it.sample != null }
                click(awaitNode { it.text?.toString() == "单次" })
                awaitActivityState(scenario) { it.single && it.held != null }
                click(awaitNode { it.text?.toString() == "安全" })
                click(awaitNode { it.text?.toString() == "测光点" })
                awaitNode { it.text?.toString() == "退出冻结" }
                click(awaitNode { it.text?.toString() == "直方图" })
                val original = awaitNode(::chartHasArea)
                val before = chartState(original)
                assertTrue(before.isNotEmpty())
                panChart(original)
                val panned = chartState(awaitNode {
                    chartHasArea(it) && chartState(it) != before
                })
                assertNotEquals(before, panned)
                click(awaitNode { it.text?.toString() == "主体与曝光" })
                awaitNode(description("胶片安全曝光估算"))
                assertNull(findNode(description("场景 EV100 直方图")))
                scenario.onActivity {
                    assertTrue(it.frozen)
                    assertTrue(it.single)
                    assertNotNull(it.held)
                }
                click(awaitNode { it.text?.toString() == "直方图" })
                assertEquals(panned, chartState(awaitNode(::chartHasArea)))
                click(awaitNode { it.text?.toString() == "退出冻结" })
                scenario.onActivity {
                    assertFalse(it.frozen)
                    assertTrue(it.single)
                    assertNotNull(it.held)
                }
                click(awaitNode { it.text?.toString() == "连续" && clickTarget(it).isEnabled })
                scenario.onActivity {
                    assertFalse(it.single)
                    assertNull(it.held)
                }
            }
        }
}
