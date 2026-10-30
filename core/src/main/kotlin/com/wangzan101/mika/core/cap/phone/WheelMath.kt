package com.wangzan101.mika.core.cap.phone

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 滚轮选择器(时间、日期、地区这类一格一格转的)的算法,和怎么转无关:认值、算方向和格数、估一格多高。
 * 端口照它在一次工具调用里转到位,不需要模型一格一格看。
 *
 * 「前进」指滚轮上的值往后走(下面那一项转到中间,数字通常变大);是不是变大由读回来的值校正。
 */
object WheelMath {
    private val ONLY_NUMBER = Regex("""^-?\d{1,6}$""")
    private val LEADING_NUMBER = Regex("""^\D{0,3}?(\d{1,6})(?!\d)""")

    /** 比较文字用:去空白、全角数字换半角、忽略大小写。 */
    fun normalize(s: String): String = buildString {
        for (c in s.trim()) when {
            c.isWhitespace() -> Unit
            c in '０'..'９' -> append('0' + (c - '０'))
            else -> append(c.lowercaseChar())
        }
    }

    /**
     * 显示的文字里的整数:"09" → 9,"9时"/"9 点" → 9,"2026年" → 2026,"第3周" → 3;前面的字超过三个、没有数字为 null。
     * 只取开头附近的第一个数,滚轮上一格通常只有一个数。
     */
    fun number(s: String?): Int? {
        val t = s?.let(::normalize)?.ifEmpty { null } ?: return null
        if (ONLY_NUMBER.matches(t)) return t.toIntOrNull()
        return LEADING_NUMBER.find(t)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** 目标本身就是一个数(「9」「09」「２０２６」),不是带数字的文字。 */
    fun numericTarget(target: String): Int? = normalize(target).takeIf(ONLY_NUMBER::matches)?.toIntOrNull()

    /**
     * 滚轮此刻显示的 [shown] 是不是要的 [target]:目标是纯数字时按数比("09" 就是 "9","40分" 就是 "40");
     * 否则文字相同,或显示的以目标开头/结尾(「北京市」对「北京」)。
     */
    fun matches(shown: String?, target: String): Boolean {
        val s = shown?.let(::normalize)?.ifEmpty { null } ?: return false
        val t = normalize(target).ifEmpty { return false }
        numericTarget(target)?.let { return number(s) == it }
        return s == t || s.startsWith(t) || s.endsWith(t)
    }

    /**
     * 从上到下看得到的几项估出循环的长度:数字里出现一次「往下变小」(…58 59 00 01…),循环长度 = 断点前的最大值 − 断点后的最小值 + 1;
     * 看不出循环(全是递增、不是数字、太少)为 null。
     */
    fun modulus(visible: List<String>): Int? {
        val n = visible.map { number(it) ?: return null }
        if (n.size < 2) return null
        val drops = (1 until n.size).filter { n[it] < n[it - 1] }
        if (drops.size != 1) return null
        val i = drops.single()
        return (n.subList(0, i).max() - n.subList(i, n.size).min() + 1).takeIf { it >= 2 }
    }

    /** 从 [current] 到 [target] 要往数大的那边走几格(负数往小的那边):循环长度 [modulus] 已知时取近的那一边。 */
    fun distance(current: Int, target: Int, modulus: Int?): Int {
        val d = target - current
        if (modulus == null || modulus < 2) return d
        val m = ((d % modulus) + modulus) % modulus
        return if (m <= modulus / 2) m else m - modulus
    }

    /** 数从 [before] 变成 [after] 走了几格(循环长度已知时 59→0 是 +1 不是 −59);不是数字为 null。 */
    fun moved(before: String?, after: String?, modulus: Int?): Int? {
        val b = number(before) ?: return null
        val a = number(after) ?: return null
        return distance(b, a, modulus)
    }

    /** 一格多高:相邻几项中心的间距取中位数;不到两项、间距太小为 null。 */
    fun itemHeight(centers: List<Int>): Int? {
        val sorted = centers.sorted()
        val gaps = (1 until sorted.size).map { sorted[it] - sorted[it - 1] }.filter { it > 2 }
        if (gaps.isEmpty()) return null
        return gaps.sorted()[gaps.size / 2]
    }

    /** 中线 [centerY] 上的那一项的下标:中心离中线最近的;离得超过大半格(没有正对中线的)为 -1。 */
    fun selected(centers: List<Int>, centerY: Int, itemHeight: Int?): Int {
        val best = centers.indices.minByOrNull { abs(centers[it] - centerY) } ?: return -1
        if (itemHeight != null && abs(centers[best] - centerY) > itemHeight * 0.75) return -1
        return best
    }

    /**
     * 要往前转几格才到(负数往后):目标就在看得到的几项里时按位置差算(下面的是前进);目标和选中的都是数字时按数差算
     * (看得出循环就走近的那边,[increasingForward] 为 false 时前进是变小);都不行(文字列表、目标不在眼前)为 null。
     */
    fun stepsTo(visible: List<String>, selectedIndex: Int, target: String, increasingForward: Boolean = true): Int? {
        if (selectedIndex !in visible.indices) return null
        visible.indexOfFirst { matches(it, target) }.takeIf { it >= 0 }?.let { return it - selectedIndex }
        val t = numericTarget(target) ?: return null
        val cur = number(visible[selectedIndex]) ?: return null
        val d = distance(cur, t, modulus(visible))
        return if (increasingForward) d else -d
    }

    /**
     * 转过 [steps] 格要拖的距离(像素,正数手指往上拖 = 前进),一格 [itemHeight];一次拖不超过 [maxDrag],超过的分几次。
     * 返回每一次拖的距离。
     */
    fun drags(steps: Int, itemHeight: Int, maxDrag: Int): List<Int> {
        if (steps == 0 || itemHeight <= 0) return emptyList()
        val perDrag = (maxDrag / itemHeight).coerceAtLeast(1)
        val sign = if (steps > 0) 1 else -1
        val out = mutableListOf<Int>()
        var left = abs(steps)
        while (left > 0) {
            val n = minOf(left, perDrag)
            out += sign * n * itemHeight
            left -= n
        }
        return out
    }

    /** 滚轮里看得到的一项:文字和中心(像素)。 */
    data class Item(val text: String, val centerX: Int, val centerY: Int)

    /**
     * 一个能竖着滚的容器看上去是不是滚轮,宁可漏认不错认:三到九项、每项都是短的值(不超过 [MAX_ITEM_CHARS] 个字)、互不相同,
     * 都在容器横向的正中(偏离不到宽度的 15%),从上到下等距排着(最大间距不到最小的 1.5 倍)。
     * 设置页那种靠左排的短标签、聊天列表都不像。
     */
    fun looksLikeWheel(items: List<Item>, left: Int, right: Int): Boolean {
        if (items.size !in 3..9 || right <= left) return false
        val texts = items.map { normalize(it.text) }
        if (texts.any { it.isEmpty() || it.length > MAX_ITEM_CHARS } || texts.toSet().size != texts.size) return false
        val mid = (left + right) / 2.0
        if (items.any { abs(it.centerX - mid) > (right - left) * 0.15 }) return false
        val ys = items.map { it.centerY }
        if (ys.zipWithNext().any { (a, b) -> b <= a }) return false
        val gaps = ys.zipWithNext { a, b -> b - a }
        return gaps.max() < gaps.min() * 1.5
    }

    private const val MAX_ITEM_CHARS = 8

    /** 拖了 [dragged] 像素实际转过 [moved] 格:一格实际多高;没转为 null。 */
    fun calibrated(dragged: Int, moved: Int): Int? =
        if (moved == 0) null else abs((dragged.toDouble() / moved).roundToInt()).takeIf { it > 2 }
}

/**
 * 按无障碍「往前/往后滚」转滚轮时的下一步:每批之后把读回的值交进来,它说再往哪边滚几下,或停。
 * 一下转过的不一定是一格(列表一下滚一页),所以每批之后按读回的值重算:
 * - 目标是数:按数差定方向和下数;第一次读回时看「往前」是变大还是变小;值不动了说明这一边到头、目标不在范围里,停;
 *   来回越过目标好几次(一下不止一格,停不准)也停,交给拖动去校准。
 * - 目标是文字:在眼前就按位置差;不在就一下一下往前扫,到头了反过来扫,两头都到了停。
 */
class WheelSteps(private val target: String, private val cap: Int = 80, private val burst: Int = 5) {
    sealed interface Step {
        data object Done : Step
        /** 往 [forward] 那边滚 [count] 下。 */
        data class Act(val forward: Boolean, val count: Int) : Step
        /** 停下:[reason] 为 at_end(到头了,目标不在这一边)、cap(滚了太多下)、overshoot(停不准)、unknown_value(读不出值)。 */
        data class Stop(val reason: String) : Step
    }

    /** 到目前为止滚了几下。 */
    var actions: Int = 0
        private set
    private val numeric = WheelMath.numericTarget(target)
    private var increasingForward = true
    private var calibrated = false
    private var modulus: Int? = null
    private var last: String? = null
    private var lastAct: Step.Act? = null
    private var reversals = 0
    private var scanForward = true
    private var scanReversed = false

    /** 刚才那批之后:此刻选中的值 [current] 和看得到的几项 [visible](从上到下)。第一次调时还没滚过。 */
    fun next(current: String?, visible: List<String> = emptyList()): Step {
        if (WheelMath.matches(current, target)) return Step.Done
        if (current == null) return Step.Stop("unknown_value")
        WheelMath.modulus(visible)?.let { modulus = it }
        val prev = lastAct
        val unchanged = prev != null && current == last
        if (actions >= cap) return Step.Stop("cap")
        if (numeric != null && WheelMath.number(current) != null) {
            if (prev != null) {
                if (unchanged) return Step.Stop("at_end")
                val moved = WheelMath.moved(last, current, modulus)
                if (!calibrated && moved != null && moved != 0) {
                    calibrated = true
                    if ((moved > 0) != prev.forward) increasingForward = false
                }
            }
            val d = WheelMath.distance(WheelMath.number(current)!!, numeric, modulus).let { if (increasingForward) it else -it }
            val forward = d > 0
            val turned = prev != null && forward != prev.forward
            if (turned && ++reversals > 3) return Step.Stop("overshoot")
            // 刚越过目标往回走:一下一下来
            val n = if (turned) 1 else minOf(abs(d), burst)
            return act(forward, n.coerceAtMost(cap - actions), current)
        }
        val sel = visible.indexOfFirst { it == current }
        val at = visible.indexOfFirst { WheelMath.matches(it, target) }
        if (sel >= 0 && at >= 0) return act(at > sel, minOf(abs(at - sel), burst, cap - actions), current)
        if (unchanged) {
            if (scanReversed) return Step.Stop("at_end")
            scanReversed = true
            scanForward = !scanForward
        }
        return act(scanForward, 1, current)
    }

    private fun act(forward: Boolean, count: Int, current: String?): Step {
        val a = Step.Act(forward, count.coerceAtLeast(1))
        last = current
        lastAct = a
        actions += a.count
        return a
    }
}
