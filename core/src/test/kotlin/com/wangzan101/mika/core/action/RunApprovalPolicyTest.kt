package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RunApprovalPolicyTest {
    private val invokers = listOf(Origin.Model(1).policy, Origin.Batch(1).policy, Origin.Script(1).policy, Origin.Replay.policy)

    @Test fun uiPresentPreservesEveryOldDecision() {
        for (level in ActionRisk.entries) for (floor in listOf(null) + FloorKind.entries)
            for (mode in ApprovalMode.entries) for (invoker in invokers)
                for (grant in listOf(null) + GrantScope.entries) for (review in SmartReview.entries) {
                    val risk = Risk(level, floor)
                    assertEquals("$risk $mode ${invoker.source} $grant $review",
                        ApprovalPolicy.decide(risk, mode, invoker, grant, review),
                        ApprovalPolicy.decide(risk, mode, invoker, RunProvenance(), grant, review))
                }
    }

    @Test fun allOriginsAndAttendanceHaveNoUnanswerableAsk() {
        for (origin in RunOrigin.entries) for (attendance in Attendance.entries)
            for (level in ActionRisk.entries) for (floor in listOf(null) + FloorKind.entries)
                for (mode in ApprovalMode.entries) for (invoker in invokers)
                    for (grant in listOf(null) + GrantScope.entries) {
                        val result = ApprovalPolicy.decide(Risk(level, floor), mode, invoker,
                            RunProvenance(origin, attendance), grant)
                        if (attendance == Attendance.Unattended || origin == RunOrigin.Child)
                            assertNotEquals("$origin $attendance $level $floor $mode ${invoker.source} $grant", Approval.Ask, result)
                        if (floor != null && origin != RunOrigin.Ui)
                            assertEquals(Approval.Deny, result)
                    }
    }

    @Test fun remoteOriginsDoNotSilentlyRunOutwardActions() {
        for (origin in listOf(RunOrigin.Channel, RunOrigin.Trigger, RunOrigin.Mcp))
            for (level in listOf(ActionRisk.External, ActionRisk.Irreversible)) {
                val result = ApprovalPolicy.decide(Risk(level), ApprovalMode.HANDS_OFF, Origin.Model(1).policy,
                    RunProvenance(origin, Attendance.Reachable), null)
                assertEquals("$origin $level", Approval.Ask, result)
            }
    }

    @Test fun triggerStandingGrantCannotAuthorizeAnUnattendedSend() {
        assertEquals(Approval.Deny, ApprovalPolicy.decide(Risk(ActionRisk.External), ApprovalMode.SMART,
            Origin.Model(1).policy, RunProvenance(RunOrigin.Trigger, Attendance.Unattended), GrantScope.Standing))
    }
}
