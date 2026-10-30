package com.wangzan101.mika.core.cap.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelMathTest {
    @Test fun numbersOnWheelItems() {
        assertEquals(9, WheelMath.number("09"))
        assertEquals(9, WheelMath.number("9时"))
        assertEquals(9, WheelMath.number(" 9 点 "))
        assertEquals(2026, WheelMath.number("2026年"))
        assertEquals(40, WheelMath.number("４０"))
        assertEquals(3, WheelMath.number("第3周"))
        assertNull(WheelMath.number("上午"))
        assertNull(WheelMath.number(""))
        assertNull(WheelMath.number(null))
    }

    @Test fun matchingNumbersIgnoresPaddingAndUnits() {
        assertTrue(WheelMath.matches("09", "9"))
        assertTrue(WheelMath.matches("9", "09"))
        assertTrue(WheelMath.matches("40分", "40"))
        assertTrue(WheelMath.matches("2026年", "2026"))
        assertFalse(WheelMath.matches("19", "9"))
        assertFalse(WheelMath.matches("4", "40"))
        assertFalse(WheelMath.matches(null, "9"))
    }

    @Test fun matchingTextIsWholeOrAffix() {
        assertTrue(WheelMath.matches("北京市", "北京"))
        assertTrue(WheelMath.matches("PM", "pm"))
        assertTrue(WheelMath.matches("下午", "下午"))
        assertFalse(WheelMath.matches("上午", "下午"))
        assertFalse(WheelMath.matches("南京", "北京市"))
    }

    @Test fun wrapLengthFromVisibleItems() {
        assertEquals(60, WheelMath.modulus(listOf("58", "59", "00", "01", "02")))
        assertEquals(24, WheelMath.modulus(listOf("22", "23", "0", "1")))
        assertEquals(12, WheelMath.modulus(listOf("11", "12", "1", "2")))
        assertNull(WheelMath.modulus(listOf("07", "08", "09")))
        assertNull(WheelMath.modulus(listOf("上午", "下午")))
        assertNull(WheelMath.modulus(listOf("5")))
    }

    @Test fun distanceTakesTheShortWayRoundWhenItWraps() {
        assertEquals(40, WheelMath.distance(0, 40, null))
        assertEquals(-20, WheelMath.distance(0, 40, 60))
        assertEquals(2, WheelMath.distance(59, 1, 60))
        assertEquals(-3, WheelMath.distance(2, 23, 24))
        assertEquals(10, WheelMath.distance(2026, 2036, null))
        assertEquals(0, WheelMath.distance(7, 7, 60))
    }

    @Test fun movedAcrossTheWrapIsOneStep() {
        assertEquals(1, WheelMath.moved("59", "00", 60))
        assertEquals(-59, WheelMath.moved("59", "00", null))
        assertNull(WheelMath.moved("上午", "下午", null))
    }

    @Test fun itemHeightIsTheMedianGap() {
        assertEquals(100, WheelMath.itemHeight(listOf(300, 100, 200, 400)))
        assertEquals(100, WheelMath.itemHeight(listOf(100, 200, 300, 600)))
        assertNull(WheelMath.itemHeight(listOf(100)))
        assertNull(WheelMath.itemHeight(emptyList()))
    }

    @Test fun selectedIsTheItemOnTheCenterLine() {
        assertEquals(2, WheelMath.selected(listOf(100, 200, 300, 400, 500), 310, 100))
        // 没有正对中线的(整个滚轮在别处)
        assertEquals(-1, WheelMath.selected(listOf(100, 200), 900, 100))
        assertEquals(-1, WheelMath.selected(emptyList(), 0, null))
    }

    @Test fun stepsToVisibleTargetFollowsPosition() {
        val visible = listOf("上午", "下午")
        assertEquals(1, WheelMath.stepsTo(visible, 0, "下午"))
        assertEquals(-1, WheelMath.stepsTo(visible, 1, "上午"))
        // 数字:目标在眼前按位置,不在按数差(循环看得出来就走近的一边)
        assertEquals(2, WheelMath.stepsTo(listOf("07", "08", "09", "10", "11"), 2, "11"))
        assertEquals(31, WheelMath.stepsTo(listOf("07", "08", "09", "10", "11"), 2, "40"))
        assertEquals(-19, WheelMath.stepsTo(listOf("58", "59", "00", "01", "02"), 2, "41"))
        assertEquals(-31, WheelMath.stepsTo(listOf("07", "08", "09", "10", "11"), 2, "40", increasingForward = false))
        // 文字列表、目标不在眼前:不知道
        assertNull(WheelMath.stepsTo(listOf("北京", "天津", "上海"), 1, "广州"))
        assertNull(WheelMath.stepsTo(listOf("07"), 5, "9"))
    }

    @Test fun dragsSplitIntoWheelSizedPieces() {
        assertEquals(listOf(300), WheelMath.drags(3, 100, 400))
        assertEquals(listOf(400, 400, 200), WheelMath.drags(10, 100, 450))
        assertEquals(listOf(-200), WheelMath.drags(-2, 100, 400))
        assertEquals(emptyList<Int>(), WheelMath.drags(0, 100, 400))
        // 一次至少拖一格
        assertEquals(listOf(100, 100), WheelMath.drags(2, 100, 50))
    }

    @Test fun calibrationFromWhatADragActuallyMoved() {
        assertEquals(120, WheelMath.calibrated(600, 5))
        assertEquals(120, WheelMath.calibrated(-600, -5))
        assertNull(WheelMath.calibrated(600, 0))
    }

    @Test fun wheelShapeIsShortCenteredEvenlySpacedValues() {
        fun items(vararg t: String, x: Int = 500, gap: Int = 100) = t.mapIndexed { i, s -> WheelMath.Item(s, x, 200 + i * gap) }
        assertTrue(WheelMath.looksLikeWheel(items("08", "09", "10", "11", "12"), 400, 600))
        assertTrue(WheelMath.looksLikeWheel(items("上午", "下午", "晚上"), 400, 600))
        // 靠左排的设置项、太长的文字、重复的、太少的、间距乱的都不算
        assertFalse(WheelMath.looksLikeWheel(items("Wi-Fi", "蓝牙", "显示", x = 120), 0, 1000))
        assertFalse(WheelMath.looksLikeWheel(items("今天下午三点开会别忘了", "好的", "收到"), 400, 600))
        assertFalse(WheelMath.looksLikeWheel(items("1", "1", "1"), 400, 600))
        assertFalse(WheelMath.looksLikeWheel(items("1", "2"), 400, 600))
        assertFalse(WheelMath.looksLikeWheel(listOf(WheelMath.Item("1", 500, 100), WheelMath.Item("2", 500, 200), WheelMath.Item("3", 500, 500)), 400, 600))
    }
}
