package com.wangzan101.mika.settings

import com.wangzan101.mika.confirm.ConfirmationCopy
import com.wangzan101.mika.core.action.ApprovalMode
import com.wangzan101.mika.core.action.GrantKey
import com.wangzan101.mika.core.gate.ConfirmationRequest
import com.wangzan101.mika.core.risk.IrrevAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApprovalStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key = GrantKey(IrrevAction.send, "com.chat", "张三", "发送")

    @Test fun defaultsToSmartWithoutGrants() {
        val s = ApprovalStore(tmp.root.resolve("a.json"))
        assertEquals(ApprovalMode.SMART, s.mode.value)
        assertEquals(emptyList<GrantKey>(), s.standing.items.value)
    }

    @Test fun modeAndGrantsSurviveARestartAndChangesAreAudited() {
        val f = tmp.root.resolve("a.json")
        ApprovalStore(f).apply { setMode(ApprovalMode.AUTO); standing.add(key); standing.add(key) }
        val again = ApprovalStore(f)
        assertEquals(ApprovalMode.AUTO, again.mode.value)
        assertEquals(listOf(key), again.standing.items.value)
        again.standing.revoke(key)
        assertEquals(emptyList<GrantKey>(), ApprovalStore(f).standing.items.value)
        assertFalse(tmp.root.resolve("a.json.tmp").exists())
        val audit = tmp.root.resolve("a-audit.jsonl").readLines()
        assertEquals(3, audit.size)
        assertTrue(audit.last(), "\"op\":\"revoked\"" in audit.last())
    }

    @Test fun unreadableFileFallsBackToDefaults() {
        val f = tmp.root.resolve("a.json").apply { writeText("{not json") }
        assertEquals(ApprovalMode.SMART, ApprovalStore(f).mode.value)
    }

    private fun control() = ApprovalControl(ApprovalStore(tmp.root.resolve("a.json")))

    @Test fun autonomousIsOffByDefaultAndSurvivesARestart() {
        val f = tmp.root.resolve("a.json")
        assertFalse(ApprovalStore(f).autonomous.value)
        ApprovalControl(ApprovalStore(f)).setAutonomous(true)
        val again = ApprovalStore(f)
        assertTrue(again.autonomous.value)
        // 审批模式和长期授权不受影响
        assertEquals(ApprovalMode.SMART, again.mode.value)
        ApprovalControl(again).setAutonomous(false)
        assertFalse(ApprovalStore(f).autonomous.value)
    }

    @Test fun runsInAskHaveAGateAndRunsInAutonomousDoNot() {
        val c = control()
        val ask = c.start()
        assertFalse(ask.startedAutonomous)
        assertFalse(ask.autonomous())
        assertNotNull(ask.gate())
        assertEquals(ApprovalMode.SMART, ask.mode())
        c.setAutonomous(true)
        val auto = c.start()
        assertTrue(auto.startedAutonomous)
        assertTrue(auto.autonomous())
        assertNull(auto.gate())
    }

    @Test fun switchingFollowsEveryRunningTask() {
        val c = control()
        val a = c.start()
        val b = c.start()
        c.setAutonomous(true)
        // 正在跑的任务从下一步起都不再问;它们开工时有关卡,这一点不变
        assertTrue(a.autonomous())
        assertTrue(b.autonomous())
        assertFalse(a.startedAutonomous)
        c.setAutonomous(false)
        assertFalse(a.autonomous())
        assertFalse(b.autonomous())
        // 结束后再换,不再碰那个任务;下一个任务按新的开关开工
        c.end(a)
        c.setAutonomous(true)
        assertFalse(a.autonomous())
        assertTrue(b.autonomous())
        assertTrue(c.start().startedAutonomous)
    }

    @Test fun scopeTextNamesAppTargetAndAction() {
        val r = ConfirmationRequest(IrrevAction.send, "张三", "明天见", null, "com.chat")
        assertEquals("在「Chat」里对「张三」的发送", ConfirmationCopy.scope(r, "Chat"))
    }
}
