package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.gate.ConfirmationRequest
import com.wangzan101.mika.core.risk.IrrevAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 用户在确认卡片上的选择。 */
enum class ConfirmChoice { Once, Task, Always, Deny;

    val approved: Boolean get() = this != Deny
}

/**
 * 任务内或长期授权覆盖的范围:同一 App(网页是同一网站)、同一类动作、同一个对象、同一个元素。
 * [target] 是对象身份,要和这一步作用对象的身份完全相同:手机上发送看标题栏的名字,其余看元素自己和同一行的文字;
 * 网页上不信页面画出来的字,是页面地址(网站加路径)。[element] 是作用的元素自己的名字(输入框是它的资源 ID)。
 * 授权在用户选了之后第一次真的放行时才按那一步绑定([Acted]),不按模型申报的字样绑定。
 */
data class GrantKey(val action: IrrevAction, val app: String, val target: String, val element: String) {
    fun covers(a: Acted): Boolean = action == a.action && app == a.app && element == a.element && target in a.identities
}

/** 这一步实际作用在什么上;名字都已规整(去空白、小写)。[web] 为 true 时 [identities] 只有页面地址。 */
data class Acted(val action: IrrevAction, val app: String, val element: String, val identities: Set<String>, val web: Boolean) {
    /** 把用户同意的申报绑定到这一步:对象身份对得上才成;网页上绑页面地址。 */
    fun bind(r: ConfirmationRequest): GrantKey? {
        if (r.action != action) return null
        if (web) return GrantKey(action, app, identities.single(), element)
        val t = norm(r.target)
        return if (r.app == app && t in identities) GrantKey(action, app, t, element) else null
    }

    companion object {
        fun norm(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
    }
}

/** 长期授权(以后都允许):由设置页列出、逐条撤销或全部撤销。实现负责持久化。 */
interface StandingGrants {
    val items: StateFlow<List<GrantKey>>
    fun add(key: GrantKey)
    fun revoke(key: GrantKey)
    fun clear()
}

/** 审批设置:此刻的模式和长期授权。由设置提供,测试里换成固定值。 */
interface ApprovalSettings {
    fun mode(): ApprovalMode
    val standing: StandingGrants
    /** 此刻是自主模式:不可逆、对外的操作都不先问(中途换成自主时,正在跑的任务从下一步起照它)。 */
    fun autonomous(): Boolean = false
}

/** 只在内存里的长期授权。 */
class MemoryStandingGrants : StandingGrants {
    private val _items = MutableStateFlow<List<GrantKey>>(emptyList())
    override val items: StateFlow<List<GrantKey>> = _items.asStateFlow()

    @Synchronized override fun add(key: GrantKey) { if (key !in _items.value) _items.value = _items.value + key }
    @Synchronized override fun revoke(key: GrantKey) { _items.value = _items.value - key }
    @Synchronized override fun clear() { _items.value = emptyList() }
}

/** 固定模式(默认 SMART,即没有审批设置时的行为)。 */
class FixedApprovals(
    private val fixed: ApprovalMode = ApprovalMode.SMART,
    override val standing: StandingGrants = MemoryStandingGrants(),
) : ApprovalSettings {
    override fun mode() = fixed
}
