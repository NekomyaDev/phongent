package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.action.ActionRisk.Change
import com.wangzan101.mika.core.action.ActionRisk.External
import com.wangzan101.mika.core.action.ActionRisk.Irreversible
import com.wangzan101.mika.core.action.ActionRisk.Read
import com.wangzan101.mika.core.action.ActionRisk.Unknown
import com.wangzan101.mika.core.action.ApprovalMode.ALWAYS_ASK
import com.wangzan101.mika.core.action.ApprovalMode.AUTO
import com.wangzan101.mika.core.action.ApprovalMode.HANDS_OFF
import com.wangzan101.mika.core.action.ApprovalMode.SMART
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalPolicyTest {
    private val model = Origin.Model(1).policy
    private val batch = Origin.Batch(1).policy
    private val script = Origin.Script(1).policy
    private val replay = Origin.Replay.policy
    private val origins = listOf(model, batch, script, replay)
    private val ask = Approval.Ask
    private val free = Approval.Allow(null)

    /** 模型单步、没有授权:每个等级在每个模式下问不问(A = 问,- = 不问)。列顺序:ALWAYS_ASK SMART AUTO HANDS_OFF。 */
    private val modelTable = mapOf(
        Read to "- - - -",
        Change to "A - - -",
        External to "A A A -",
        Irreversible to "A A A -",
        // 看不清点的是什么:可能是底线,每种模式都问
        Unknown to "A A A A",
    )

    /** 一次多步、页面脚本、经验回放:对外的动作至少照 SMART 问。 */
    private val unattendedTable = mapOf(
        Read to "- - - -",
        Change to "A - - -",
        External to "A A A A",
        Irreversible to "A A A A",
        Unknown to "A A A A",
    )

    private val modes = listOf(ALWAYS_ASK, SMART, AUTO, HANDS_OFF)

    private fun check(origin: OriginPolicy, table: Map<ActionRisk, String>) {
        for ((level, row) in table) {
            row.split(' ').zip(modes).forEach { (cell, mode) ->
                val expected = if (cell == "A") ask else free
                assertEquals("${origin.source} $level $mode", expected, ApprovalPolicy.decide(Risk(level), mode, origin, null))
            }
        }
    }

    @Test fun modelTableWithoutGrants() = check(model, modelTable)

    @Test fun unattendedOriginsNeverRunBelowSmartForOutwardActions() {
        check(batch, unattendedTable)
        check(script, unattendedTable)
        check(replay, unattendedTable)
    }

    @Test fun smartWithoutReviewerIsTodaysBehaviour() {
        // 今天:对外、不可逆、看不清的问;界面上的改动不问
        for (level in ActionRisk.entries) {
            val expected = if (level.outward) ask else free
            assertEquals("$level", expected, ApprovalPolicy.decide(Risk(level), SMART, model, null))
        }
    }

    @Test fun everyCombination() {
        val covers = listOf(null, GrantScope.Once, GrantScope.Task, GrantScope.Standing)
        val reviews = SmartReview.entries
        for (level in ActionRisk.entries) for (floor in listOf(null) + FloorKind.entries) for (mode in modes)
            for (origin in origins) for (cover in covers) for (review in reviews) {
                val got = ApprovalPolicy.decide(Risk(level, floor), mode, origin, cover, review)
                val name = "$level floor=$floor $mode ${origin.source} cover=$cover review=$review"
                val effective = if (level.outward) mode.stricter(origin.minMode) else mode
                val asks = when (effective) {
                    ALWAYS_ASK -> level != Read
                    SMART -> when (level) {
                        External, Irreversible, Unknown -> true
                        Change -> review == SmartReview.Flag
                        Read -> false
                    }
                    AUTO -> level == External || level == Irreversible
                    HANDS_OFF -> false
                }
                val expected = when {
                    // 底线和看不清的:每种模式、每个来源都问,任务内和长期授权都不算
                    floor != null || level == Unknown -> ask
                    !asks -> free
                    // 只有能用同意的来源认授权;一次性同意不在这里算(要核对并用掉)
                    origin.usesGrant && (cover == GrantScope.Task || cover == GrantScope.Standing) -> Approval.Allow(cover)
                    else -> ask
                }
                assertEquals(name, expected, got)
            }
    }

    @Test fun floorsAskEvenHandsOffWithStandingGrant() {
        for (floor in FloorKind.entries) {
            assertEquals(ask, ApprovalPolicy.decide(Risk(Change, floor), HANDS_OFF, model, GrantScope.Standing))
        }
    }

    @Test fun grantsNeverCoverUnreadableActions() {
        assertEquals(ask, ApprovalPolicy.decide(Risk(Unknown), HANDS_OFF, model, GrantScope.Standing))
    }

    @Test fun grantsCoverOnlyForTheModel() {
        assertEquals(Approval.Allow(GrantScope.Task), ApprovalPolicy.decide(Risk(External), SMART, model, GrantScope.Task))
        assertEquals(Approval.Allow(GrantScope.Standing), ApprovalPolicy.decide(Risk(Irreversible), ALWAYS_ASK, model, GrantScope.Standing))
        assertEquals(ask, ApprovalPolicy.decide(Risk(External), SMART, batch, GrantScope.Standing))
        assertEquals(ask, ApprovalPolicy.decide(Risk(External), SMART, model, GrantScope.Once))
    }

    @Test fun stricterPicksTheStricterMode() {
        assertEquals(SMART, HANDS_OFF.stricter(SMART))
        assertEquals(SMART, SMART.stricter(HANDS_OFF))
        assertEquals(ALWAYS_ASK, AUTO.stricter(ALWAYS_ASK))
    }
}
