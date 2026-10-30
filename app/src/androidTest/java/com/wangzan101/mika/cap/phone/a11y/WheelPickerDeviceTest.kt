package com.wangzan101.mika.cap.phone.a11y

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.cap.phone.a11y.DeviceSupport.TEST_PACKAGE
import com.wangzan101.mika.core.cap.phone.WheelMath
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机测试:在 Agent 虚拟屏上打开 [WheelPickerTestActivity],把原版数字滚轮从 0 转到 40,一次调用做完。
 * 「锁住」那个只能走无障碍滚动(一下一格,从 0 往回走 20 格最近);读屏时两个都标成滚轮。
 */
@RunWith(AndroidJUnit4::class)
class WheelPickerDeviceTest {
    private val agent = DeviceSupport.newAgent()
    private val screen get() = agent.screen

    @After
    fun tearDown() = agent.release()

    private fun open(): ScreenSnapshot {
        DeviceSupport.sh("am start --display ${agent.display.displayId} -n $TEST_PACKAGE/${WheelPickerTestActivity::class.java.name}")
        val s = DeviceSupport.pollUntil(8_000) {
            runBlocking { screen.snapshot() }.takeIf { s -> s.elements.count { it.role == "NumberPicker" } == 2 }
        }
        return s ?: error("wheel page never showed: ${runBlocking { screen.snapshot() }.elements.map { "${it.role}:${it.name}" }}")
    }

    private fun ScreenSnapshot.picker(label: String): UiElement = elements.first { it.role == "NumberPicker" && it.name == label }

    /** 此刻滚轮中间那一格(读屏树里滚轮下面带字、离中线最近的那个节点)。 */
    private fun shown(label: String): String? {
        val s = runBlocking { screen.snapshot() }
        val ref = s.picker(label).ref
        val node = s.windows.flatMap { it.nodes }.flatMap(::flatten).first { it.ref == ref }
        val items = node.children.filter { it.text != null }
        val mid = (node.bounds[1] + node.bounds[3]) / 2
        return items.minByOrNull { kotlin.math.abs((it.bounds[1] + it.bounds[3]) / 2 - mid) }?.text
    }

    private fun flatten(n: com.wangzan101.mika.core.uistate.UiTreeNode): List<com.wangzan101.mika.core.uistate.UiTreeNode> =
        listOf(n) + n.children.flatMap(::flatten)

    @Test
    fun wheelsAreMarkedInTheTree() = runBlocking<Unit> {
        val s = open()
        val wheels = s.windows.flatMap { it.nodes }.flatMap(::flatten).filter { it.wheel }
        assertEquals(2, wheels.size)
    }

    @Test
    fun lockedWheelReaches40From0ByStepping() = runBlocking<Unit> {
        val s = open()
        val o = screen.setWheel(s.picker(WheelPickerTestActivity.LOCKED).ref, s.snapshotId, "40")
        assertTrue("$o", o.reached)
        assertEquals("actions", o.method)
        assertEquals("00", o.from)
        assertTrue("$o", WheelMath.matches(o.value, "40"))
        // 0 → 40 往回走 20 格最近;每格一下,读回不另算
        assertTrue("$o", o.attempts in 20..24)
        assertTrue(WheelMath.matches(shown(WheelPickerTestActivity.LOCKED), "40"))
    }

    @Test
    fun typableWheelReaches40() = runBlocking<Unit> {
        val s = open()
        val o = screen.setWheel(s.picker(WheelPickerTestActivity.MINUTES).ref, s.snapshotId, "40")
        assertTrue("$o", o.reached)
        assertTrue("$o", o.method in setOf("text", "actions"))
        assertTrue(WheelMath.matches(shown(WheelPickerTestActivity.MINUTES), "40"))
        // 另一个滚轮没被动过
        assertTrue(WheelMath.matches(shown(WheelPickerTestActivity.LOCKED), "0"))
    }

    @Test
    fun valueNotOnTheWheelIsReportedNotForced() = runBlocking<Unit> {
        val s = open()
        val o = screen.setWheel(s.picker(WheelPickerTestActivity.LOCKED).ref, s.snapshotId, "75")
        assertTrue("$o", !o.reached)
        assertTrue("$o", o.value != null)
    }
}
