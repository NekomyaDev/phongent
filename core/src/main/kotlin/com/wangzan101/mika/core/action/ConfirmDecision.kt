package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.gate.ConfirmationGate
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.gate.Irreversible
import com.wangzan101.mika.core.gate.IrreversibleDetector
import com.wangzan101.mika.core.gate.Permit
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.interfaces
import com.wangzan101.mika.core.tool.ToolDispatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.run.RunProvenance

/** 一次动作执行前的审批结论。 */
sealed interface Decision {
    /** 不用判,或判过放行(含用户的同意、授权对上了)。 */
    data object Clear : Decision
    /** 放行,但找不到点在哪个元素上:运行记录里留痕。 */
    data class Traced(val detail: String) : Decision
    /** 找不到点在哪个元素上,这一来源不盲点。 */
    data object Unresolved : Decision
    /** 要用户这一次的同意,没有可用的。[floor] 是底线类别;[change] 为 true 时是模式要求才问的改动(不是不可逆判定命中的)。 */
    data class Required(val hit: Irreversible, val floor: FloorKind? = null, val change: Boolean = false) : Decision
    /** 有同意,但和这一步对不上;那次同意已作废。 */
    data class Mismatch(val hit: Irreversible, val reason: String) : Decision
    /** 本次运行不可提问或需要在手机上确认，直接拒绝。 */
    data object Denied : Decision
    /** 作用在 Mika 自己身上,不做。 */
    data object OwnApp : Decision
}

/**
 * 不可逆判定、审批与同意核对。整个代码库里只有这里调用 [IrreversibleDetector] 的判定和 [ConfirmationGate.permits]
 * (有测试守着),各来源只在 [OriginPolicy] 里写明哪里不同;问不问由 [ApprovalPolicy.decide] 定。
 */
class ConfirmDecision(
    private val tools: ToolDispatcher,
    private val confirmations: ConfirmationGate?,
    private val approvals: ApprovalSettings = FixedApprovals(),
    private val risks: RiskJudge = ClassifiedRisk,
    private val recorder: ActionRecorder? = null,
    private val reviewer: SmartReviewer? = null,
    private val provenance: RunProvenance = RunProvenance(),
    /** 开工就是自主执行(没有确认关卡):审批、风险等级和界面目标猜测都不阻断动作;中途换成自主看 [ApprovalSettings.autonomous]。 */
    private val autonomous: Boolean = false,
    /** Mika 自己的包名:作用在它上面的写操作一律不做(不自己给自己点同意、改设置)。 */
    private val selfPackage: String? = null,
) {
    /**
     * 刚判过的这一步是对外动作(发送、提交:判定命中、放行后要做的)时它所在的网站或 App(见 [OutwardPacing.siteOf]),
     * 执行入口据此和上一次对外动作隔开;不是为 null。自主执行时也认(按模型手里那一屏判,不另读屏),节奏不因此失效。
     */
    var outwardSite: String? = null
        private set

    /** 判定放在锁屏等待之后:按此刻重新读的一屏判。 */
    suspend fun judge(tool: String, args: JsonObject, origin: Origin): Decision {
        outwardSite = null
        if (autonomous || approvals.autonomous()) {
            val target = tools.gateTarget(tool)
            if (IrreversibleDetector.needsCheck(target, args)) {
                val aim = tools.interfaces.actionTarget(tools.declared(tool), args)
                outwardSite = outward(IrreversibleDetector.classify(target, aim.args, aim.aimed), aim.aimed)
            }
            return Decision.Clear
        }
        val policy = origin.policy
        val target = tools.gateTarget(tool)
        // 这一步落在哪里由界面一侧给出;点看图找到的 @vN 时参数已换成它登记的坐标和找的文字
        val aim = tools.interfaces.actionTarget(tools.declared(tool), args)
        val checked = aim.args
        if (ownApp(tool, args, aim.aimed)) return Decision.OwnApp
        tools.approvalRisk(tool)?.let { declared ->
            return approve(tool, args, checked, origin, null, declared, null, null, null) ?: Decision.Clear
        }
        if (confirmations == null && !policy.judgeWithoutGate) return Decision.Clear
        val verb = tools.verb(tool)
        val typing = if (verb == Verb.TypeText) (args["text"] as? JsonPrimitive)?.contentOrNull else null
        if (!IrreversibleDetector.needsCheck(target, args)) {
            val snap = aim.aimed
            val risk = risks.assess(verb, checked, null, null) { snap }
            if (risk.level == ActionRisk.Read && risk.floor == null) return Decision.Clear
            return approve(tool, args, checked, origin, null, risk, snap, typing, null) ?: Decision.Clear
        }
        val bySight = aim.bySight
        val snap = aim.aimed
        val fresh = aim.now()
        if (ownApp(tool, args, fresh)) return Decision.OwnApp
        // 复看确认过的所见文字(@vN 才有):读屏里没有可判的文字时(自绘界面)就按它判
        val seen = aim.seen
        val hit = IrreversibleDetector.classify(target, checked, snap, fresh)
            // 看图得来的点:读屏里没有可判的文字、又没有复看确认过的所见文字,不知道点下去是什么,不放行
            ?: when {
                !bySight || seen != null -> null
                fresh == null -> Irreversible(IrrevAction.other, "point found by sight, screen unreadable")
                !IrreversibleDetector.hasLabelAt(target, checked, null, fresh) -> Irreversible(IrrevAction.other, "point found by sight, no element or label there to judge")
                else -> null
            }
        val risk = risks.assess(verb, checked, hit, seen) { fresh ?: snap }
        outwardSite = outward(hit, fresh ?: snap)
        approve(tool, args, checked, origin, hit, risk, fresh ?: snap, typing, seen)?.let { return it }
        if (hit != null || !IrreversibleDetector.unresolved(target, checked, snap, fresh)) return Decision.Clear
        return if (policy.refuseUnresolved) Decision.Unresolved else Decision.Traced(unresolvedDetail(args, aim.readAgeMs, fresh != null))
    }

    /** 命中的是发送、提交时这一屏的网站或 App。 */
    private fun outward(hit: Irreversible?, screen: ScreenSnapshot?): String? =
        if (hit?.action == IrrevAction.send || hit?.action == IrrevAction.submit) OutwardPacing.siteOf(screen?.packageName, screen?.web != null) else null

    /** 写操作作用在 Mika 自己身上:屏上是 Mika,或要打开 Mika(含它的应用详情页)。 */
    private fun ownApp(tool: String, args: JsonObject, screen: ScreenSnapshot?): Boolean {
        val self = selfPackage ?: return false
        if (!tools.writesScreen(tool)) return false
        if (screen?.packageName == self) return true
        return listOf("package", "uri").any { k -> (args[k] as? JsonPrimitive)?.contentOrNull?.let { self in it } == true }
    }

    /** 按模式、来源和授权判;放行为 null。 */
    private suspend fun approve(
        tool: String, args: JsonObject, checked: JsonObject, origin: Origin, hit: Irreversible?, risk: Risk, screen: ScreenSnapshot?,
        typing: String?, seen: String?,
    ): Decision? {
        val policy = origin.policy
        val mode = approvals.mode()
        val targetText = screen?.let { ClassifiedRisk.target(tools.verb(tool), checked, it)?.name }?.takeIf { it.isNotBlank() }
            ?: hit?.matched?.takeIf { it.isNotBlank() } ?: tool
        val review = if (reviewer != null && mode == ApprovalMode.SMART && risk.floor == null && risk.level == ActionRisk.Change)
            reviewer.review(tool, targetText.take(200), risk) else SmartReview.None
        // 没命中不可逆判定、却要问的(底线、模式要求):按动作落在的元素记成「其他」
        val asked = hit ?: Irreversible(IrrevAction.other, when {
            risk.floor == FloorKind.Secret -> "password field"
            else -> screen?.let { ClassifiedRisk.target(tools.verb(tool), checked, it) }?.name?.trim()?.takeIf { it.isNotEmpty() } ?: tool
        })
        val acted = if (policy.usesGrant) ClassifiedRisk.acted(tools.verb(tool), checked, asked, risk, screen, typing) else null
        var approval = ApprovalPolicy.decide(risk, mode, policy, provenance, null, review)
        if (approval == Approval.Ask && acted != null) approval = ApprovalPolicy.decide(risk, mode, policy, provenance, covered(acted), review)
        // 问用户的、对外或底线的、靠授权放行的都记下;界面上的改动按模式放行的不记
        fun audit(decision: String, by: GrantScope?) {
            if (decision != "allowed" || by != null || risk.level.outward || risk.floor != null) recorder?.approval(origin, tool, asked, risk, mode, decision, by)
        }
        if (approval is Approval.Allow) {
            audit("allowed", approval.by)
            return null
        }
        if (approval == Approval.Deny) return Decision.Denied.also { audit("denied", null) }
        val required = Decision.Required(asked, risk.floor, change = hit == null && risk.floor == null)
        val gate = confirmations?.takeIf { policy.usesGrant } ?: return required.also { audit("required", null) }
        return when (val p = gate.permits(asked, screen, origin.step, typing, listOfNotNull(seen))) {
            Permit.Allowed -> {
                gate.bind(acted)?.let { (key, scope) ->
                    if (scope == GrantScope.Standing) approvals.standing.add(key)
                    recorder?.grant("added", key, scope)
                }
                null.also { audit("allowed", GrantScope.Once) }
            }
            Permit.NoGrant -> required.also { audit("required", null) }
            is Permit.Mismatch -> Decision.Mismatch(asked, p.reason).also { audit("mismatch", null) }
        }
    }

    /** 覆盖这一步的任务内或长期授权。 */
    private fun covered(acted: Acted): GrantScope? = when {
        confirmations?.taskCovers(acted) == true -> GrantScope.Task
        approvals.standing.items.value.any { it.covers(acted) } -> GrantScope.Standing
        else -> null
    }

    /** 执行了一个写操作:同意按它的规则作废或保留。 */
    fun noteWrite(tool: String) {
        confirmations?.noteWrite(tools.verb(tool))
    }

    /** 留痕:点的是哪个编号、那次读屏多久以前,或点的坐标。 */
    private fun unresolvedDetail(args: JsonObject, readAgeMs: Long?, freshRead: Boolean): String {
        fun arg(k: String) = args[k]?.let { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
        val ref = arg("ref")
        return if (ref != null) {
            val sid = arg("snapshotId")
            "ref=$ref snapshotId=$sid age=${readAgeMs?.let { "${it}ms" } ?: "unknown"}"
        } else "x=${arg("x")},y=${arg("y")} freshRead=$freshRead"
    }

    companion object {
        /**
         * 不读屏的预判(大小模型分工用):按上一次读屏,这一步可能要确认,或不知道点的是什么(看图得来、找不到元素)。
         */
        fun mayNeedConfirmation(tools: ToolDispatcher, tool: String, args: JsonObject): Boolean {
            val target = tools.gateTarget(tool)
            if (!IrreversibleDetector.needsCheck(target, args)) return false
            val aim = tools.interfaces.actionTarget(tools.declared(tool), args)
            val checked = aim.args
            val snap = aim.aimed
            return aim.bySight || IrreversibleDetector.classify(target, checked, snap) != null ||
                IrreversibleDetector.unresolved(target, checked, snap)
        }
    }
}
