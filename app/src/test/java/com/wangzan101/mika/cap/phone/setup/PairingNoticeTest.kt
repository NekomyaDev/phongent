package com.wangzan101.mika.cap.phone.setup

import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.core.shell.PairingDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingNoticeTest {
    @Test
    fun codeEntryAppearsOnlyOnceThePairingWindowIsFound() {
        assertNull(PairingNotice.of(BuiltinShellStatus.NotPaired, PairingDiscovery.Searching))
        assertEquals(PairingNotice.EnterCode(37001), PairingNotice.of(BuiltinShellStatus.NotPaired, PairingDiscovery.Found(37001)))
    }

    @Test
    fun progressAndResult() {
        assertEquals(PairingNotice.Working, PairingNotice.of(BuiltinShellStatus.Connecting, PairingDiscovery.Found(1)))
        assertEquals(PairingNotice.Done, PairingNotice.of(BuiltinShellStatus.Running, PairingDiscovery.Idle))
    }

    @Test
    fun pairingFailuresStayInTheNotificationOthersSendBackToMika() {
        assertEquals(
            PairingNotice.EnterCode(5, "配对码不对或已过期"),
            PairingNotice.of(BuiltinShellStatus.Failed(BuiltinShellFailure.WrongCode), PairingDiscovery.Found(5)),
        )
        assertEquals(
            PairingNotice.Problem("连上了,但没能准备好"),
            PairingNotice.of(BuiltinShellStatus.Failed(BuiltinShellFailure.HelperStartFailed), PairingDiscovery.Found(5)),
        )
    }

    @Test
    fun codeEntryWaitsForAutoReadAndRealertsOnce() {
        val enter = PairingNotice.EnterCode(5)
        assertEquals(0L, PairingNotice.delayBefore(enter, autoRead = false))
        assertEquals(PairingNotice.AUTO_READ_GRACE_MS, PairingNotice.delayBefore(enter, autoRead = true))
        assertEquals(0L, PairingNotice.delayBefore(PairingNotice.Working, autoRead = true))
        assertEquals(PairingNotice.REALERT_MS, PairingNotice.realertAfter(enter))
        listOf(PairingNotice.Working, PairingNotice.Done, PairingNotice.Problem("x")).forEach { assertNull(PairingNotice.realertAfter(it)) }
    }
}
