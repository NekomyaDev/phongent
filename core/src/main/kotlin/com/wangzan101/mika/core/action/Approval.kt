package com.wangzan101.mika.core.action

import kotlinx.serialization.json.JsonObject
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance

/**
 * 用户选的审批模式,由严到松。底线([Risk.floor])在每种模式下都要问。
 * [ALWAYS_ASK] 每个改动都问(纯读不问);[SMART] 对外、不可逆的问,一般改动交轻量模型复核;
 * [AUTO] 只有对外和不可逆的问;[HANDS_OFF] 除底线外都不问。看不清点的是什么([ActionRisk.Unknown])每种模式都问。
 */
enum class ApprovalMode { ALWAYS_ASK, SMART, AUTO, HANDS_OFF;

    /** 两个模式里更严的那个。 */
    fun stricter(other: ApprovalMode): ApprovalMode = if (other.ordinal < ordinal) other else this
}

/**
 * 一个动作的风险等级。[Read] 只看或只在界面间移动;[Change] 在界面上改东西(点、打字);
 * [External] 把东西交给别人(发送、提交);[Irreversible] 做了收不回(付款、下单、删除);
 * [Unknown] 看不清点的是什么(看图得来、屏上没有可判的文字):可能就是底线操作,按底线问。
 */
enum class ActionRisk { Read, Change, External, Irreversible, Unknown;

    /** 会影响屏幕之外的世界(或拿不准会不会)。 */
    val outward: Boolean get() = this == External || this == Irreversible || this == Unknown
}

/**
 * 底线:每种模式都问,只能同意这一次。按动作的类别判,不按 App。[Payment] 含下单(花钱,免密支付时下单就是付款);
 * [Unreadable] 是看不清点的是什么。
 */
enum class FloorKind { Payment, Account, Security, Secret, Unreadable }

data class Risk(val level: ActionRisk, val floor: FloorKind? = null)

/** 放行用的是哪一种同意。[Once] 是一次性同意;[Task] 本任务内;[Standing] 以后都允许。 */
enum class GrantScope { Once, Task, Standing }

/** SMART 模式下复核的结论:[Flag] 这个改动要问,[None] 没复核或不用问。 */
enum class SmartReview { None, Flag }

/** SMART 模式的复核。[target] 是动作实际落点的文字描述，不传整屏或输入内容。 */
fun interface SmartReviewer {
    suspend fun review(tool: String, target: String, risk: Risk): SmartReview
}

sealed interface Approval {
    /** 不用问;[by] 是放行用到的授权,模式本身就放行时为 null。 */
    data class Allow(val by: GrantScope?) : Approval
    /** 要用户这一次的同意(有一次性同意就核对并用掉它)。 */
    data object Ask : Approval
    /** 本次运行不能向用户要同意，动作必须停下。 */
    data object Deny : Approval
}

/** 审批判定:纯函数,所有模式、来源、授权的组合都在 [decide] 里。 */
object ApprovalPolicy {
    /**
     * [covered] 是覆盖这一步的任务内或长期授权(不含一次性同意);只有能用同意的来源才算数,看不清的动作不认授权。
     * 对外的动作按用户模式和来源最低模式里更严的那个判。
     */
    fun decide(risk: Risk, mode: ApprovalMode, origin: OriginPolicy, covered: GrantScope?, review: SmartReview = SmartReview.None): Approval =
        decide(risk, mode, origin, RunProvenance(), covered, review)

    fun decide(risk: Risk, mode: ApprovalMode, invoker: OriginPolicy, run: RunProvenance, covered: GrantScope?, review: SmartReview = SmartReview.None): Approval {
        if (run.origin == RunOrigin.Child && (risk.level.outward || risk.floor != null)) return Approval.Deny
        // 底线和看不清的不看模式,也不认任务内、长期授权
        if (risk.floor != null || risk.level == ActionRisk.Unknown) return askOrDeny(run, floor = true)
        if (risk.level == ActionRisk.Read) return Approval.Allow(null)
        val runMinimum = when (run.origin) {
            RunOrigin.Ui -> ApprovalMode.HANDS_OFF
            RunOrigin.Channel, RunOrigin.Trigger, RunOrigin.Mcp -> ApprovalMode.SMART
            RunOrigin.Child -> ApprovalMode.AUTO
        }
        val effective = mode.stricter(runMinimum).let { if (risk.level.outward) it.stricter(invoker.minMode) else it }
        if (!asks(effective, risk.level, review)) return Approval.Allow(null)
        if (invoker.usesGrant && covered != null && covered != GrantScope.Once && run.origin in setOf(RunOrigin.Ui, RunOrigin.Channel, RunOrigin.Mcp)) return Approval.Allow(covered)
        return askOrDeny(run, floor = false)
    }

    private fun askOrDeny(run: RunProvenance, floor: Boolean): Approval = when {
        run.attendance == Attendance.Unattended -> Approval.Deny
        floor && run.origin != RunOrigin.Ui -> Approval.Deny
        run.origin == RunOrigin.Child -> Approval.Deny
        else -> Approval.Ask
    }

    private fun asks(mode: ApprovalMode, level: ActionRisk, review: SmartReview): Boolean = when (mode) {
        ApprovalMode.ALWAYS_ASK -> true
        ApprovalMode.SMART -> when (level) {
            ActionRisk.External, ActionRisk.Irreversible, ActionRisk.Unknown -> true
            else -> review == SmartReview.Flag
        }
        ApprovalMode.AUTO -> level == ActionRisk.External || level == ActionRisk.Irreversible
        ApprovalMode.HANDS_OFF -> false
    }
}
