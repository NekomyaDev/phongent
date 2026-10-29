package com.wangzan101.mika.core.shell

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSetupFlowTest {
    private val fresh = SetupInputs(developerOptions = false, wirelessDebugging = false, status = BuiltinShellStatus.NotPaired, usbDebugging = false, accessibility = false)
    private val switchesOn = fresh.copy(developerOptions = true, usbDebugging = true, accessibility = true, wirelessDebugging = true)

    private fun SetupView.markList() = SetupStep.entries.map { marks.getValue(it) }

    @Test
    fun walksTheStepsInOrderAsSettingsChange() {
        val v1 = PhoneSetupFlow.view(fresh)
        assertEquals(SetupPhase.Guide, v1.phase)
        assertEquals(SetupStep.DeveloperOptions, v1.current)
        assertEquals(listOf(StepMark.Current, StepMark.Todo, StepMark.Todo, StepMark.Todo, StepMark.Todo, StepMark.Todo), v1.markList())

        val v2 = PhoneSetupFlow.view(fresh.copy(developerOptions = true))
        assertEquals(SetupStep.UsbDebugging, v2.current)
        assertEquals(listOf(StepMark.Done, StepMark.Current, StepMark.Todo, StepMark.Todo, StepMark.Todo, StepMark.Todo), v2.markList())

        val v2a = PhoneSetupFlow.view(fresh.copy(developerOptions = true, usbDebugging = true))
        assertEquals(SetupStep.Accessibility, v2a.current)
        assertEquals(listOf(StepMark.Done, StepMark.Done, StepMark.Current, StepMark.Todo, StepMark.Todo, StepMark.Todo), v2a.markList())

        val v2b = PhoneSetupFlow.view(fresh.copy(developerOptions = true, usbDebugging = true, accessibility = true))
        assertEquals(SetupStep.WirelessDebugging, v2b.current)
        assertEquals(listOf(StepMark.Done, StepMark.Done, StepMark.Done, StepMark.Current, StepMark.Todo, StepMark.Todo), v2b.markList())

        val v3 = PhoneSetupFlow.view(switchesOn)
        assertEquals(SetupStep.PairingCode, v3.current)
        assertTrue(v3.autoRead)
        assertFalse(v3.portFound)
        assertTrue(PhoneSetupFlow.view(switchesOn.copy(discovery = PairingDiscovery.Found(37000))).portFound)

        val v4 = PhoneSetupFlow.view(switchesOn.copy(status = BuiltinShellStatus.Connecting))
        assertEquals(SetupPhase.Working, v4.phase)
        assertNull(v4.current)
        assertEquals(listOf(StepMark.Done, StepMark.Done, StepMark.Done, StepMark.Done, StepMark.Done, StepMark.Current), v4.markList())

        val v5 = PhoneSetupFlow.view(switchesOn.copy(status = BuiltinShellStatus.Running))
        assertEquals(SetupPhase.Success, v5.phase)
        assertTrue(v5.markList().all { it == StepMark.Done })
    }

    @Test
    fun accessibilityCanBeSkippedAndPairingFallsBackToTheNotification() {
        val off = switchesOn.copy(accessibility = false)
        assertEquals(SetupStep.Accessibility, PhoneSetupFlow.view(off).current)
        val skipped = PhoneSetupFlow.view(off.copy(accessibilitySkipped = true))
        assertEquals(SetupStep.PairingCode, skipped.current)
        assertFalse(skipped.autoRead)
        assertEquals(StepMark.Skipped, skipped.marks[SetupStep.Accessibility])
        // 跳过后又在系统里打开了:照样自动读
        val laterOn = PhoneSetupFlow.view(switchesOn.copy(accessibilitySkipped = true))
        assertTrue(laterOn.autoRead)
        assertEquals(StepMark.Done, laterOn.marks[SetupStep.Accessibility])
        // 码不对重填时也看无障碍
        assertTrue(PhoneSetupFlow.view(switchesOn.copy(status = BuiltinShellStatus.Failed(BuiltinShellFailure.WrongCode))).autoRead)
    }

    @Test
    fun restrictedSettingsHintOnlyAtTheAccessibilityStep() {
        val off = switchesOn.copy(accessibility = false, restrictedSettings = true)
        assertTrue(PhoneSetupFlow.view(off).showAppInfo)
        assertFalse(PhoneSetupFlow.view(off.copy(restrictedSettings = false)).showAppInfo)
        assertFalse(PhoneSetupFlow.view(off.copy(accessibility = true)).showAppInfo)
        assertFalse(PhoneSetupFlow.view(off.copy(developerOptions = false)).showAppInfo)
    }

    @Test
    fun usbDebuggingOffBlocksSetupButNotARunningHelper() {
        // USB 调试关着:配对过、无线调试开着也停在这一步
        val v = PhoneSetupFlow.view(switchesOn.copy(usbDebugging = false, status = BuiltinShellStatus.Connecting))
        assertEquals(SetupStep.UsbDebugging, v.current)
        assertEquals(SetupPhase.Guide, v.phase)
        // 已经在运行就算设置好了(设置页另有提示)
        assertEquals(SetupPhase.Success, PhoneSetupFlow.view(switchesOn.copy(usbDebugging = false, status = BuiltinShellStatus.Running)).phase)
    }

    @Test
    fun turningASwitchOffGoesBackToThatStep() {
        // 配对过、无线调试被关掉:回到那一步,不要求重新配对
        val v = PhoneSetupFlow.view(SetupInputs(true, false, BuiltinShellStatus.WirelessDebuggingOff))
        assertEquals(SetupStep.WirelessDebugging, v.current)
        // 开发者选项被关:哪怕之前失败过,也先回第一步
        val f = PhoneSetupFlow.view(SetupInputs(false, true, BuiltinShellStatus.Failed(BuiltinShellFailure.ConnectFailed)))
        assertEquals(SetupStep.DeveloperOptions, f.current)
        assertEquals(SetupPhase.Guide, f.phase)
    }

    @Test
    fun pairedAndSwitchOnMeansConnectingEvenIfStatusLags() {
        val v = PhoneSetupFlow.view(SetupInputs(true, true, BuiltinShellStatus.WirelessDebuggingOff))
        assertEquals(SetupPhase.Working, v.phase)
    }

    @Test
    fun unreadableWirelessSwitchDoesNotBlock() {
        // 读不到开关的手机:不卡在无线调试一步,按助手的状态走
        assertEquals(SetupStep.PairingCode, PhoneSetupFlow.view(SetupInputs(true, null, BuiltinShellStatus.NotPaired)).current)
        assertEquals(SetupStep.WirelessDebugging, PhoneSetupFlow.view(SetupInputs(true, null, BuiltinShellStatus.WirelessDebuggingOff)).current)
        assertEquals(SetupPhase.Success, PhoneSetupFlow.view(SetupInputs(true, null, BuiltinShellStatus.Running)).phase)
    }

    @Test
    fun failuresLandOnTheStepTheUserMustRedo() {
        val wrong = PhoneSetupFlow.view(SetupInputs(true, true, BuiltinShellStatus.Failed(BuiltinShellFailure.WrongCode)))
        assertEquals(SetupPhase.Failed, wrong.phase)
        assertEquals(SetupStep.PairingCode, wrong.current)
        assertEquals(StepMark.Failed, wrong.marks[SetupStep.PairingCode])
        assertEquals(BuiltinShellFix.RePair, wrong.failure?.fix)

        val revoked = PhoneSetupFlow.view(SetupInputs(true, true, BuiltinShellStatus.Failed(BuiltinShellFailure.AuthorizationRevoked)))
        assertEquals(SetupStep.PairingCode, revoked.current)

        val helper = PhoneSetupFlow.view(SetupInputs(true, true, BuiltinShellStatus.Failed(BuiltinShellFailure.HelperStartFailed)))
        assertEquals(SetupStep.Connect, helper.current)
        assertEquals(StepMark.Done, helper.marks[SetupStep.PairingCode])
        assertEquals(BuiltinShellFix.Retry, helper.failure?.fix)

        assertEquals(BuiltinShellFix.OpenWifi, BuiltinShellStatus.Failed(BuiltinShellFailure.NoWifi).fix)
    }

    @Test
    fun repairShowsPairingStepUntilNewPairingStarts() {
        val running = SetupInputs(true, true, BuiltinShellStatus.Running, repair = true)
        assertEquals(SetupStep.PairingCode, PhoneSetupFlow.view(running).current)
        assertEquals(SetupPhase.Working, PhoneSetupFlow.view(running.copy(status = BuiltinShellStatus.Connecting)).phase)
    }

    @Test
    fun repairWhileRunningStaysOnPairingStepAndFinishesAfterNewPairing() {
        val running = SetupInputs(true, true, BuiltinShellStatus.Running, repair = true, discovery = PairingDiscovery.Found(4000))
        val waiting = PhoneSetupFlow.view(running)
        assertEquals(SetupPhase.Guide, waiting.phase)
        assertEquals(SetupStep.PairingCode, waiting.current)
        assertTrue(waiting.portFound)
        assertTrue(waiting.autoRead)
        assertEquals(BuiltinShellStatus.NotPaired, PhoneSetupFlow.effectiveStatus(BuiltinShellStatus.Running, repair = true))
        assertEquals(BuiltinShellStatus.Connecting, PhoneSetupFlow.effectiveStatus(BuiltinShellStatus.Connecting, repair = true))
        // 新配对开始后重新配对结束,助手继续运行即成功
        assertEquals(SetupPhase.Working, PhoneSetupFlow.view(running.copy(status = BuiltinShellStatus.Connecting)).phase)
        assertEquals(SetupPhase.Success, PhoneSetupFlow.view(running.copy(repair = false)).phase)
        assertEquals(BuiltinShellStatus.Running, PhoneSetupFlow.effectiveStatus(BuiltinShellStatus.Running, repair = false))
    }

    @Test
    fun asksForNotificationsOnlyAtThePairingStep() {
        val off = fresh.copy(notificationsAllowed = false)
        assertFalse(PhoneSetupFlow.view(off).needsNotifications)
        assertTrue(PhoneSetupFlow.view(off.copy(developerOptions = true, usbDebugging = true, accessibility = true, wirelessDebugging = true)).needsNotifications)
    }

    @Test
    fun pairingCodeNormalization() {
        assertEquals("123456", PairingCode.normalize("123456"))
        assertEquals("123456", PairingCode.normalize(" 123 456 "))
        assertEquals("123456", PairingCode.normalize("123-456"))
        assertEquals("123456", PairingCode.normalize("１２３４５６"))
        assertNull(PairingCode.normalize("12345"))
        assertNull(PairingCode.normalize("1234567"))
        assertNull(PairingCode.normalize("12a456"))
        assertEquals(37123, PairingCode.port(" 37123 "))
        assertNull(PairingCode.port("0"))
        assertNull(PairingCode.port("70000"))
    }

    @Test
    fun fakeControlDrivesStatus() = runTest {
        val fake = FakeBuiltinShellControl(pairingResult = BuiltinShellStatus.Failed(BuiltinShellFailure.WrongCode))
        fake.startDiscovery()
        assertEquals(PairingDiscovery.Searching, fake.pairingDiscovery.value)
        fake.startPairing("123456", 40000)
        assertEquals(BuiltinShellStatus.Failed(BuiltinShellFailure.WrongCode), fake.status.value)
        fake.retry()
        assertEquals(BuiltinShellStatus.Running, fake.status.value)
        fake.unpair()
        assertEquals(BuiltinShellStatus.NotPaired, fake.status.value)
        assertEquals(listOf("discover", "pair 123456 40000", "retry", "unpair"), fake.calls)
    }
}
