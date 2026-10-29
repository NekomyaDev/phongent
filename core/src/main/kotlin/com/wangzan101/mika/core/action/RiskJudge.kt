package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.gate.ConfirmationRequest
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.gate.Irreversible
import com.wangzan101.mika.core.uistate.ElementContext
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.wangzan101.mika.core.tool.Verb

/**
 * 一个动作的风险。[hit] 是不可逆判定的结果(不用判或没命中为 null);[seen] 是看图找到的点复看确认过的所见文字;
 * [screen] 是判定所依据的那一屏,用到时才读。工具自己声明风险之后换掉实现即可,判定和审批不用改。
 */
fun interface RiskJudge {
    fun assess(verb: Verb?, args: JsonObject, hit: Irreversible?, seen: String?, screen: () -> ScreenSnapshot?): Risk
}

/** 按现有的不可逆判定和读屏信号分级;底线按动作类别的字样和输入框的性质认,不按 App。 */
object ClassifiedRisk : RiskJudge {
    /** 改界面上东西的动作;其余写操作(返回、滑动、打开 App、打开网址)只是在界面间移动。 */
    private val CHANGE_VERBS = setOf(Verb.Tap, Verb.TypeText, Verb.ClearText, Verb.PressKey, Verb.Upload, Verb.Interact)

    /** 这个动作会改界面上的东西(不只是在界面间移动);不可逆、对外的动作也都是这几种动作做出来的。 */
    fun changesState(verb: Verb?): Boolean = verb in CHANGE_VERBS

    /** 注销账号、卸载一类:不论有没有被判成删除。 */
    val ACCOUNT_WORDS = listOf("注销", "删除账号", "删除帐号", "卸载", "uninstall", "delete account", "close account")
    /** 改密码的入口按钮。 */
    val PASSWORD_WORDS = listOf("修改密码", "重置密码", "更改密码", "设置密码", "change password", "reset password")
    /** 开关上有这些字:安全、隐私设置。 */
    val SECURITY_WORDS = listOf("密码", "安全", "隐私", "权限", "锁屏", "指纹", "面容", "定位", "位置信息",
        "password", "security", "privacy", "permission", "location", "screen lock")
    private val TOGGLE_ROLES = listOf("switch", "checkbox", "toggle")
    /** 同意、接受的字样。 */
    private val AGREE_WORDS = listOf("同意", "接受", "已阅读", "已读", "agree", "accept", "i have read")
    /** 一份文件(协议、政策、条款)的字样。 */
    private val DOCUMENT_WORDS = listOf("协议", "政策", "条款", "须知", "policy", "terms", "agreement")
    /** 文件的标题(《……》,或 xx 政策 / xx 协议 / privacy policy 一类):里面的「隐私」是文件名,不是设置。 */
    private val DOCUMENT_TITLE = Regex("《[^》]*》|[\\u4e00-\\u9fff]{0,4}(协议|政策|条款|须知)|(?:[a-z]+ )?(policy|terms|agreement)")
    /** 屏上带币种的金额:这时对外、不可逆的动作都按花钱算。 */
    private val AMOUNT = Regex("[¥￥]\\s*\\d|\\d+(\\.\\d+)?\\s*元")
    /** 发送对象要在这条线(0–999)以上:屏幕顶部的标题栏。 */
    private const val TITLE_BAND = 150

    override fun assess(verb: Verb?, args: JsonObject, hit: Irreversible?, seen: String?, screen: () -> ScreenSnapshot?): Risk {
        val level = when (hit?.action) {
            null -> if (verb in CHANGE_VERBS) ActionRisk.Change else ActionRisk.Read
            IrrevAction.send, IrrevAction.submit -> ActionRisk.External
            IrrevAction.pay, IrrevAction.order, IrrevAction.delete -> ActionRisk.Irreversible
            IrrevAction.other -> ActionRisk.Unknown
        }
        if (level == ActionRisk.Unknown) return Risk(level, FloorKind.Unreadable)
        if (hit?.action == IrrevAction.pay || hit?.action == IrrevAction.order) return Risk(level, FloorKind.Payment)
        if (level == ActionRisk.Read) return Risk(level)
        val s = screen()
        val target = s?.let { target(verb, args, it) }
        val label = listOfNotNull(hit?.matched, target?.name, seen).joinToString(" ").lowercase()
        val floor = when {
            // 打字:落在密码框里;找不到落在哪个框时,屏上有密码框就算
            verb == Verb.TypeText && (target?.secret ?: (s?.elements?.any { it.secret } == true)) -> FloorKind.Secret
            hit != null && s != null && s.elements.any { !it.editable && AMOUNT.containsMatchIn(it.name) } -> FloorKind.Payment
            ACCOUNT_WORDS.any { it in label } -> FloorKind.Account
            PASSWORD_WORDS.any { it in label } -> FloorKind.Security
            target != null && TOGGLE_ROLES.any { it in target.role.lowercase() } && !consent(target, s) &&
                SECURITY_WORDS.any { it in label || it in toggleContext(target, s) } -> FloorKind.Security
            else -> null
        }
        return Risk(level, floor)
    }

    /** 开关自己的上下文:名字、同一行的标签、上方最近的小节标题(见 [ElementContext]);不看整屏。 */
    private fun toggleContext(e: UiElement, s: ScreenSnapshot?): String =
        listOfNotNull(ElementContext.label(e, s), s?.let { ElementContext.heading(e, it) }?.name?.lowercase()).joinToString(" ")

    /**
     * 同意一份文件(勾「我已阅读并同意《隐私政策》」):元素自己(连同附着的标签)说同意或接受,说的是一份协议、政策、条款;
     * 去掉文件名之后不再提安全、隐私、权限一类的东西(「Accept location sharing」是开定位,不是同意文件)。
     * 这是可以照常做的改动,不是改安全、隐私设置的状态。
     */
    fun consent(e: UiElement, s: ScreenSnapshot?): Boolean {
        val label = ElementContext.label(e, s)
        if (AGREE_WORDS.none { it in label } || DOCUMENT_WORDS.none { it in label }) return false
        val rest = DOCUMENT_TITLE.replace(label, " ")
        return SECURITY_WORDS.none { it in rest }
    }

    /** 申报本身是不是底线(确认卡片据此只给「允许这一次」):付款、下单,或对象是账号、改密码一类。 */
    fun requestFloor(r: ConfirmationRequest): Boolean {
        if (r.action == IrrevAction.pay || r.action == IrrevAction.order) return true
        val t = r.target.lowercase()
        return (ACCOUNT_WORDS + PASSWORD_WORDS).any { it in t }
    }

    /** 动作落在哪个元素上:带编号的按编号找,打字落在有焦点的输入框,坐标点击取包含该点的最小元素。 */
    fun target(verb: Verb?, args: JsonObject, s: ScreenSnapshot): UiElement? {
        fun str(k: String) = (args[k] as? JsonPrimitive)?.contentOrNull
        str("ref")?.let { ref -> return s.elements.firstOrNull { it.ref == ref } }
        if (verb == Verb.TypeText || verb == Verb.ClearText) return s.elements.firstOrNull { it.focused && it.editable }
        val x = str("x")?.toDoubleOrNull() ?: return null
        val y = str("y")?.toDoubleOrNull() ?: return null
        return s.elements.filter { e -> e.bounds.size == 4 && x >= e.bounds[0] && x <= e.bounds[2] && y >= e.bounds[1] && y <= e.bounds[3] }
            .minByOrNull { (it.bounds[2] - it.bounds[0]).toLong() * (it.bounds[3] - it.bounds[1]) }
    }

    /**
     * 这一步实际作用在什么上(授权按它绑定、按它覆盖);拿不准时为 null,授权不覆盖。
     * 发送要看得到要发的字([typing] 或输入框里的字),对象身份是标题栏的名字;其余动作是元素自己和同一行的文字。
     */
    fun acted(verb: Verb?, args: JsonObject, hit: Irreversible, risk: Risk, s: ScreenSnapshot?, typing: String?): Acted? {
        if (s == null || risk.floor != null || risk.level == ActionRisk.Unknown) return null
        val e = target(verb, args, s) ?: return null
        val element = if (e.editable) "field:" + (e.resourceId ?: e.role) else Acted.norm(e.name).ifEmpty { return null }
        val web = s.web
        if (web != null) {
            val page = Regex("^([a-z][a-z0-9+.-]*://[^/?#]+)([^?#]*)", RegexOption.IGNORE_CASE).find(web.url) ?: return null
            return Acted(hit.action, page.groupValues[1].lowercase(), element, setOf(page.value.lowercase()), web = true)
        }
        val app = s.packageName ?: return null
        val shown = s.elements.filter { !it.editable && !it.hint && it.bounds.size == 4 }
        val names = if (hit.action == IrrevAction.send) {
            val content = typing ?: s.elements.firstOrNull { it.editable && !it.hint && !it.secret && it.name.isNotBlank() }?.name
            if (content.isNullOrBlank()) return null
            shown.filter { it.bounds[1] <= TITLE_BAND }.map { it.name }
        } else if (e.bounds.size == 4) {
            shown.filter { o -> val cy = (o.bounds[1] + o.bounds[3]) / 2; cy in e.bounds[1]..e.bounds[3] }.map { it.name } + e.name
        } else listOf(e.name)
        val identities = names.map(Acted::norm).filter { it.length >= 2 }.toSet()
        return if (identities.isEmpty()) null else Acted(hit.action, app, element, identities, web = false)
    }
}
