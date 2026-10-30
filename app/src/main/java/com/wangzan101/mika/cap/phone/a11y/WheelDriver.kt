package com.wangzan101.mika.cap.phone.a11y

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.wangzan101.mika.core.cap.phone.WheelMath
import com.wangzan101.mika.core.cap.phone.WheelSteps
import com.wangzan101.mika.core.uistate.VisibleTextLine
import com.wangzan101.mika.core.uistate.WheelOutcome
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 把一个滚轮转到一个值,在一次调用里做完。按控件能做什么依次试(能做就做,做不到退到下一种):
 * 1. 滚轮里有输入框(原版的数字滚轮有):直接把值写进去,让它失去焦点确认,读回来;
 * 2. 滚轮能无障碍「往前/往后滚」(原版的数字滚轮一下一格):读出当前值,按 [WheelSteps] 定方向和下数,一批一批滚,每批读回;
 * 3. 都不行(App 自己画的滚轮):读看得到的几项(无障碍读不到就认屏上的字)估一格多高和当前值,慢慢拖一次(不甩,免得转过头),
 *    读回来再小拖一两次校正。
 * 读值、拖动、等界面安静都由 [AndroidScreen] 注入,这里只管步骤。
 */
internal class WheelDriver(
    private val width: Int,
    private val height: Int,
    /** 等到界面连续安静 quietMs,最多等 capMs。 */
    private val quiet: suspend (quietMs: Long, capMs: Long) -> Unit,
    /** 一根手指沿这几点慢慢拖(像素),松手前停住、不甩。 */
    private val drag: suspend (List<Pair<Int, Int>>) -> Boolean,
    /** 认屏上的字(0–999 的坐标);没有为 null。 */
    private val ocr: suspend () -> List<VisibleTextLine>?,
    /** 这块屏上弹着输入法。 */
    private val imeShown: () -> Boolean,
    /** 按一下返回(收起输入法)。 */
    private val back: () -> Boolean,
) {
    private var attempts = 0

    /** 一次读值:选中的那一项 [current](读不出为 null)和看得到的几项(从上到下);[fromOcr] 是认字认出来的。 */
    private class Reading(val current: String?, val items: List<WheelMath.Item>, val fromOcr: Boolean) {
        val texts get() = items.map { it.text }
    }

    suspend fun set(start: AccessibilityNodeInfo, value: String): WheelOutcome {
        val wheel = wheelOf(start) ?: start
        val box = Rect().also(wheel::getBoundsInScreen)
        if (box.isEmpty) return outcome(false, null, null, "none", "not_a_wheel")
        var now = read(wheel, box)
        val from = now.current
        if (from == null && now.items.size < 2) return outcome(false, null, null, "none", if (scrollable(wheel)) "unknown_value" else "not_a_wheel")
        if (WheelMath.matches(from, value)) return outcome(true, from, from, "none")

        // 1. 直接输入
        editableOf(wheel)?.let { field ->
            if (typeInto(field, value)) {
                now = read(wheel, box)
                if (WheelMath.matches(now.current, value)) return outcome(true, now.current, from, "text")
            }
        }

        // 2. 无障碍滚动
        var stopped: String? = null
        if (canStep(wheel)) {
            val steps = WheelSteps(value, cap = MAX_STEPS)
            loop@ while (true) {
                when (val s = steps.next(now.current, now.texts)) {
                    WheelSteps.Step.Done -> return outcome(true, now.current, from, "actions")
                    is WheelSteps.Step.Stop -> { stopped = s.reason; break@loop }
                    is WheelSteps.Step.Act -> {
                        val action = if (s.forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        var done = 0
                        repeat(s.count) { if (wheel.performAction(action)) done++ }
                        attempts += s.count
                        quiet(STEP_QUIET_MS, STEP_CAP_MS)
                        wheel.refresh()
                        now = read(wheel, box)
                        // 一下都没滚动:这一边到头了(或这一边不让滚)
                        if (done == 0 && !WheelMath.matches(now.current, value)) { stopped = "at_end"; break@loop }
                    }
                }
            }
            if (stopped == "at_end" || stopped == "unknown_value") return outcome(false, now.current, from, "actions", stopped)
        }

        // 3. 拖动
        return dragTo(wheel, box, value, from, now)
    }

    private suspend fun dragTo(wheel: AccessibilityNodeInfo, box: Rect, value: String, from: String?, first: Reading): WheelOutcome {
        var now = first
        var itemHeight = WheelMath.itemHeight(now.items.map { it.centerY }) ?: (box.height() / ASSUMED_ROWS)
        var increasingForward = true
        var scanForward = true
        var scanReversed = false
        repeat(MAX_DRAGS) {
            if (WheelMath.matches(now.current, value)) return outcome(true, now.current, from, "drag")
            val sel = WheelMath.selected(now.items.map { it.centerY }, box.centerY(), itemHeight)
            val planned = WheelMath.stepsTo(now.texts, sel, value, increasingForward)
            // 文字列表、目标不在眼前:一次翻过看得到的那几项,往一个方向扫
            val scanning = planned == null
            val steps = planned ?: ((now.items.size - 1).coerceAtLeast(1) * if (scanForward) 1 else -1)
            val before = now.current
            var dragged = 0
            for (d in WheelMath.drags(steps, itemHeight, (box.height() - itemHeight).coerceAtLeast(itemHeight))) {
                // 手指往上拖 = 下面的项转上来 = 前进
                val x = box.centerX()
                val y0 = (box.centerY() + d / 2).coerceIn(box.top + 1, box.bottom - 1)
                val y1 = (y0 - d).coerceIn(box.top + 1, box.bottom - 1)
                if (!drag(listOf(x to y0, x to y1))) return outcome(false, now.current, from, "drag", "not_reached")
                attempts++
                dragged += y0 - y1
                quiet(DRAG_QUIET_MS, DRAG_CAP_MS)
            }
            wheel.refresh()
            now = read(wheel, box)
            val moved = WheelMath.moved(before, now.current, WheelMath.modulus(now.texts))
            when {
                before == now.current && scanning -> {
                    // 扫到头了:反过来扫,两头都到了就停
                    if (scanReversed) return outcome(false, now.current, from, "drag", "at_end")
                    scanReversed = true
                    scanForward = !scanForward
                }
                // 拖了没转:这里拖不动它,再拖也一样
                before == now.current -> return outcome(false, now.current, from, "drag", "not_reached")
                moved != null && moved != 0 -> {
                    if (WheelMath.numericTarget(value) != null && (moved > 0) != (dragged > 0) && it == 0) increasingForward = false
                    WheelMath.calibrated(dragged, moved)?.let { h -> itemHeight = h }
                }
            }
        }
        val reached = WheelMath.matches(now.current, value)
        return outcome(reached, now.current, from, "drag", if (reached) null else "not_reached")
    }

    /** 往滚轮里的输入框写值并确认(失去焦点时原版滚轮按写进去的值定下来);因此弹出的输入法收起来。 */
    private suspend fun typeInto(field: AccessibilityNodeInfo, value: String): Boolean {
        val imeBefore = imeShown()
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        val set = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        attempts++
        field.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
        quiet(STEP_QUIET_MS, STEP_CAP_MS)
        if (!imeBefore && imeShown()) {
            back()
            quiet(STEP_QUIET_MS, STEP_CAP_MS)
        }
        return set
    }

    // ---------- 读 ----------

    private suspend fun read(wheel: AccessibilityNodeInfo, box: Rect): Reading {
        val items = treeItems(wheel, box)
        val h = WheelMath.itemHeight(items.map { it.centerY })
        if (items.isNotEmpty()) {
            val i = WheelMath.selected(items.map { it.centerY }, box.centerY(), h)
            return Reading(items.getOrNull(i)?.text ?: ScreenReader.ownLabel(wheel), items, fromOcr = false)
        }
        ScreenReader.ownLabel(wheel)?.takeIf { it.length <= MAX_ITEM_CHARS }?.let { return Reading(it, emptyList(), fromOcr = false) }
        // 自己画的滚轮:认这一块里的字
        val lines = ocr().orEmpty().mapNotNull { l ->
            val cx = px((l.bounds[0] + l.bounds[2]) / 2, width)
            val cy = px((l.bounds[1] + l.bounds[3]) / 2, height)
            if (!box.contains(cx, cy) || l.text.isBlank() || l.text.trim().length > MAX_ITEM_CHARS) null
            else WheelMath.Item(l.text.trim(), cx, cy)
        }.sortedBy { it.centerY }
        val oh = WheelMath.itemHeight(lines.map { it.centerY })
        val i = WheelMath.selected(lines.map { it.centerY }, box.centerY(), oh)
        return Reading(lines.getOrNull(i)?.text, lines, fromOcr = true)
    }

    /** 滚轮里看得到、带字、在滚轮范围里的几项(往下找三层),从上到下;同一项不重复。 */
    private fun treeItems(wheel: AccessibilityNodeInfo, box: Rect): List<WheelMath.Item> {
        val out = mutableListOf<WheelMath.Item>()
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                if (!c.isVisibleToUser) continue
                c.getBoundsInScreen(r)
                val label = ScreenReader.ownLabel(c)?.takeIf { it.length <= MAX_ITEM_CHARS }
                if (label != null && box.contains(r.centerX(), r.centerY())) {
                    if (out.none { it.text == label && abs(it.centerY - r.centerY()) < 4 }) out += WheelMath.Item(label, r.centerX(), r.centerY())
                } else if (depth < 3) walk(c, depth + 1)
            }
        }
        walk(wheel, 0)
        return out.sortedBy { it.centerY }
    }

    // ---------- 认滚轮 ----------

    /** 给的节点自己、往上几层里能滚的那个(给的是滚轮里的一项时);都不能滚时往下找一个能滚的。 */
    private fun wheelOf(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = n
        var depth = 0
        while (p != null && depth++ <= MAX_UP) {
            if (scrollable(p)) return p
            p = p.parent
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(n) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 60) {
            val c = queue.removeFirst()
            if (scrollable(c)) return c
            for (i in 0 until c.childCount) c.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun scrollable(n: AccessibilityNodeInfo): Boolean = n.isScrollable || canStep(n)

    private fun canStep(n: AccessibilityNodeInfo): Boolean = n.actionList.any {
        it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
    }

    /** 滚轮自己或里面一层的输入框(原版数字滚轮中间那一格就是)。 */
    private fun editableOf(wheel: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (wheel.isEditable) return wheel
        for (i in 0 until wheel.childCount) {
            val c = wheel.getChild(i) ?: continue
            if (c.isEditable && c.isVisibleToUser) return c
        }
        return null
    }

    private fun px(n: Int, size: Int) = (n * (size - 1) / 999.0).roundToInt()

    private fun outcome(reached: Boolean, value: String?, from: String?, method: String, error: String? = null) =
        WheelOutcome(reached, value, from, attempts, method, if (reached) null else error ?: "not_reached")

    private companion object {
        const val MAX_STEPS = 80
        const val MAX_DRAGS = 4
        const val MAX_UP = 4
        const val MAX_ITEM_CHARS = 12
        /** 一格多高估不出来时按一屏五行算。 */
        const val ASSUMED_ROWS = 5
        const val STEP_QUIET_MS = 150L
        const val STEP_CAP_MS = 700L
        const val DRAG_QUIET_MS = 300L
        const val DRAG_CAP_MS = 1_500L
    }
}
