package com.wangzan101.mika.ui

import org.junit.Rule
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellFix
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.core.shell.SetupStep
import com.wangzan101.mika.settings.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import com.wangzan101.mika.ui.components.ValueTone
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSetupCopyTest {
    @get:Rule val texts = ChineseTexts()

    /** 给用户看的字里不出现这些技术说法(系统设置里本来就有的选项名除外,见下)。 */
    private val jargon = listOf("shell", "端口", "mDNS", "TLS", "Binder", "uid", "进程")

    private fun allText(): List<String> = buildList {
        add(PhoneSetupCopy.INTRO)
        SetupStep.entries.forEach { val c = PhoneSetupCopy.step(it); add(c.title); add(c.body); c.action?.let(::add); add(c.done); add(c.brief) }
        BuiltinShellFailure.entries.forEach { val f = PhoneSetupCopy.failure(it); add(f.title); add(f.body) }
        BuiltinShellFix.entries.forEach { add(PhoneSetupCopy.fixLabel(it)) }
        statuses.forEach { add(PhoneSetupCopy.statusLine(it)); add(PhoneSetupCopy.statusLine(it, usbDebugging = false)); add(PhoneSetupCopy.statusValue(it).first) }
        addAll(
            listOf(
                PhoneSetupCopy.SUCCESS_LINE, PhoneSetupCopy.SUCCESS_NOTE, PhoneSetupCopy.USAGE_NOTE, PhoneSetupCopy.UNPAIR_BODY, PhoneSetupCopy.SKIP_NOTE, PhoneSetupCopy.DEGRADED_LINE, PhoneSetupCopy.PORT_FOUND,
                PhoneSetupCopy.NEED_NOTIFICATIONS, PhoneSetupCopy.MANUAL_HINT, PhoneSetupCopy.NOTIFY_ENTER_TEXT,
                PhoneSetupCopy.NOTIFY_ENTER_TITLE, PhoneSetupCopy.NOTIFY_ACTION, PhoneSetupCopy.NOTIFY_INPUT_LABEL,
                PhoneSetupCopy.AUTO_READ_HINT, PhoneSetupCopy.PULL_DOWN_HINT, PhoneSetupCopy.ACCESSIBILITY_SKIP,
                PhoneSetupCopy.ACCESSIBILITY_SKIPPED, PhoneSetupCopy.APP_INFO,
            ),
        )
    }

    private val statuses = listOf(
        BuiltinShellStatus.NotPaired, BuiltinShellStatus.WirelessDebuggingOff, BuiltinShellStatus.Connecting, BuiltinShellStatus.Running,
    ) + BuiltinShellFailure.entries.map { BuiltinShellStatus.Failed(it) }

    @Test
    fun copyIsPlainChinese() {
        allText().forEach { t ->
            assertTrue("empty copy", t.isNotBlank())
            jargon.forEach { j -> assertFalse("「$j」 in: $t", t.contains(j, ignoreCase = true)) }
            // 只有系统里那个选项名本身带 adb
            if (t.contains("adb")) assertTrue(t, t.contains("「停用 adb 授权超时功能」"))
        }
    }

    @Test
    fun eachStepPointsToTheRightPlace() {
        assertEquals("打开关于手机", PhoneSetupCopy.step(SetupStep.DeveloperOptions).action)
        assertTrue(PhoneSetupCopy.step(SetupStep.DeveloperOptions).body.contains("版本号"))
        assertTrue(PhoneSetupCopy.step(SetupStep.WirelessDebugging).body.contains("无线调试"))
        assertTrue(PhoneSetupCopy.step(SetupStep.PairingCode).body.contains("使用配对码配对设备"))
        // 配对码怎么交给 Mika:无障碍开着时自己读,没开时从顶部下拉到通知里填
        assertTrue(PhoneSetupCopy.PULL_DOWN_HINT.contains("下拉"))
        assertTrue(PhoneSetupCopy.PULL_DOWN_HINT.contains("「${PhoneSetupCopy.NOTIFY_ACTION}」"))
        assertTrue(PhoneSetupCopy.AUTO_READ_HINT.contains("自己读"))
        assertNull(PhoneSetupCopy.step(SetupStep.Connect).action)
        // USB 调试一步讲清好处:不插线、断开 Wi-Fi 也能用
        val usb = PhoneSetupCopy.step(SetupStep.UsbDebugging)
        assertTrue(usb.body.contains("「USB 调试」"))
        assertTrue(usb.body.contains("Wi-Fi"))
        assertTrue(usb.body.contains("数据线"))
    }

    @Test
    fun accessibilityStepExplainsTheBenefitAndTheWayOut() {
        val a11y = PhoneSetupCopy.step(SetupStep.Accessibility)
        assertTrue(a11y.body.contains("不用手动填"))
        assertTrue(a11y.body.contains("「无障碍」"))
        assertTrue(PhoneSetupCopy.APP_INFO.contains("受限制的设置"))
    }

    @Test
    fun notificationReadsAsAnInstruction() {
        assertEquals("Mika:下拉这里,输入配对码", PhoneSetupCopy.NOTIFY_ENTER_TITLE)
        assertEquals("输入配对码", PhoneSetupCopy.NOTIFY_ACTION)
        assertEquals("填写 6 位数字", PhoneSetupCopy.NOTIFY_INPUT_LABEL)
    }

    @Test
    fun runningWithoutUsbDebuggingGetsAGentleHint() {
        assertEquals("已就绪" to ValueTone.Ok, PhoneSetupCopy.statusValue(BuiltinShellStatus.Running))
        assertEquals("已连接,能操作手机 App", PhoneSetupCopy.statusLine(BuiltinShellStatus.Running))
        assertTrue(PhoneSetupCopy.statusLine(BuiltinShellStatus.Running, usbDebugging = false).contains("Wi-Fi 断开后会停止"))
    }

    @Test
    fun noWifiSaysItRecoversByItself() {
        val noWifi = BuiltinShellStatus.Failed(BuiltinShellFailure.NoWifi)
        assertTrue(PhoneSetupCopy.failure(BuiltinShellFailure.NoWifi).body.contains("自动恢复"))
        assertTrue(PhoneSetupCopy.statusLine(noWifi).contains("自动恢复"))
        assertTrue(StatusCopy.banner(Problem.PhoneHelper(noWifi)).detail.contains("自动恢复"))
    }

    @Test
    fun failuresAreDistinctAndActionable() {
        val titles = BuiltinShellFailure.entries.map { PhoneSetupCopy.failure(it).title }
        assertEquals(titles.size, titles.toSet().size)
        assertEquals("重新配对", PhoneSetupCopy.fixLabel(BuiltinShellStatus.Failed(BuiltinShellFailure.WrongCode).fix))
        assertEquals("重试", PhoneSetupCopy.fixLabel(BuiltinShellStatus.Failed(BuiltinShellFailure.HelperStartFailed).fix))
    }

    @Test
    fun statusValuesForSettings() {
        assertEquals("已就绪" to ValueTone.Ok, PhoneSetupCopy.statusValue(BuiltinShellStatus.Running))
        assertEquals("未设置" to ValueTone.Bad, PhoneSetupCopy.statusValue(BuiltinShellStatus.NotPaired))
        assertEquals("配对码不对或已过期", PhoneSetupCopy.statusLine(BuiltinShellStatus.Failed(BuiltinShellFailure.WrongCode)))
    }

    @Test
    fun reconnectOnlyWhenRetryingIsTheFix() {
        val reconnectable = statuses.filter { PhoneSetupCopy.canReconnect(it) }
        assertEquals(
            listOf(BuiltinShellFailure.ConnectFailed, BuiltinShellFailure.HelperStartFailed, BuiltinShellFailure.Unknown),
            reconnectable.map { (it as BuiltinShellStatus.Failed).reason },
        )
        reconnectable.forEach { s ->
            assertEquals("重新连接", StatusCopy.banner(Problem.PhoneHelper(s)).link)
            assertEquals(Fix.RetryPhoneHelper, StatusCopy.directFix(Problem.PhoneHelper(s)))
        }
        // 没配对要走引导;无线调试关着要先去打开;没 Wi-Fi 会自己恢复;配对失效要重新配对
        listOf(
            BuiltinShellStatus.NotPaired, BuiltinShellStatus.WirelessDebuggingOff, BuiltinShellStatus.Connecting,
            BuiltinShellStatus.Running, BuiltinShellStatus.Failed(BuiltinShellFailure.NoWifi),
            BuiltinShellStatus.Failed(BuiltinShellFailure.AuthorizationRevoked),
        ).forEach { assertFalse(PhoneSetupCopy.canReconnect(it)) }
    }

    @Test
    fun bannerAndFixForPhoneHelperProblem() {
        statuses.filter { it != BuiltinShellStatus.Running }.forEach { s ->
            val p = Problem.PhoneHelper(s)
            val fix = when {
                PhoneSetupCopy.canReconnect(s) -> Fix.RetryPhoneHelper
                // 这个 Wi-Fi 还没允许无线调试:直接去亲手打开一次
                s == BuiltinShellStatus.Failed(BuiltinShellFailure.WifiNotAllowed) -> Fix.OpenWirelessDebugging
                else -> Fix.OpenPhoneSetup
            }
            assertEquals(fix, StatusCopy.directFix(p))
            val b = StatusCopy.banner(p)
            jargon.forEach { j -> assertFalse(b.title.contains(j) || b.detail.contains(j)) }
            assertEquals(Fix.OpenPhoneSetup, StatusCopy.explain(p, "14").fix)
        }
        assertEquals("手机操作还没设置", StatusCopy.banner(Problem.PhoneHelper(BuiltinShellStatus.NotPaired)).title)
    }
}
