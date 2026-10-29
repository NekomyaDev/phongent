package com.wangzan101.mika.settings

import com.wangzan101.mika.core.action.ApprovalMode
import com.wangzan101.mika.core.action.GrantKey
import com.wangzan101.mika.core.action.StandingGrants
import com.wangzan101.mika.core.risk.IrrevAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 全局审批模式、询问 / 自主(新任务开工时取它们)与长期授权,存成一个小 JSON 文件;读不懂按默认值(SMART、询问、没有授权)。
 * 先写临时文件再改名,写一半断电不会留下坏文件。长期授权的每次增减另记一行到 [auditFile]。
 */
class ApprovalStore(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val auditFile = File(file.parentFile, file.nameWithoutExtension + "-audit.jsonl")
    private val _mode: MutableStateFlow<ApprovalMode>
    private val _autonomous: MutableStateFlow<Boolean>
    private val _grants: MutableStateFlow<List<GrantKey>>

    init {
        val o = runCatching { Json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull()
        val m = (o?.get("mode") as? JsonPrimitive)?.contentOrNull
        _mode = MutableStateFlow(ApprovalMode.entries.firstOrNull { it.name == m } ?: ApprovalMode.SMART)
        _autonomous = MutableStateFlow((o?.get("autonomous") as? JsonPrimitive)?.booleanOrNull ?: false)
        _grants = MutableStateFlow((o?.get("grants") as? JsonArray).orEmpty().mapNotNull { g ->
            val e = g as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (e[k] as? JsonPrimitive)?.contentOrNull
            val action = IrrevAction.entries.firstOrNull { it.name == s("action") } ?: return@mapNotNull null
            GrantKey(action, s("app") ?: return@mapNotNull null, s("target") ?: return@mapNotNull null, s("element") ?: return@mapNotNull null)
        })
    }

    val mode: StateFlow<ApprovalMode> = _mode.asStateFlow()

    @Synchronized fun setMode(mode: ApprovalMode) {
        _mode.value = mode
        save()
    }

    /** 自主模式:打开时付款、发送这类对外操作不先问你。换它请走 [ApprovalControl.setAutonomous],正在跑的任务才会跟着换。 */
    val autonomous: StateFlow<Boolean> = _autonomous.asStateFlow()

    @Synchronized fun setAutonomous(on: Boolean) {
        _autonomous.value = on
        save()
    }

    val standing: StandingGrants = object : StandingGrants {
        override val items: StateFlow<List<GrantKey>> = _grants.asStateFlow()
        override fun add(key: GrantKey) = update("added", listOf(key)) { if (key in it) it else it + key }
        override fun revoke(key: GrantKey) = update("revoked", listOf(key)) { it - key }
        override fun clear() = update("cleared", _grants.value) { emptyList() }
    }

    @Synchronized private fun update(op: String, keys: List<GrantKey>, transform: (List<GrantKey>) -> List<GrantKey>) {
        _grants.value = transform(_grants.value)
        save()
        runCatching {
            auditFile.appendText(keys.joinToString("") { k -> json(k).let { JsonObject(it + ("op" to JsonPrimitive(op)) + ("atMs" to JsonPrimitive(clock()))) }.toString() + "\n" })
        }
    }

    private fun json(g: GrantKey) = buildJsonObject { put("action", g.action.name); put("app", g.app); put("target", g.target); put("element", g.element) }

    @Synchronized private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(buildJsonObject {
            put("mode", _mode.value.name)
            put("autonomous", _autonomous.value)
            put("grants", buildJsonArray { _grants.value.forEach { add(json(it)) } })
        }.toString())
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
