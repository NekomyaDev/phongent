package com.wangzan101.mika.core.docs

import com.wangzan101.mika.core.knowledge.IndexEntry
import com.wangzan101.mika.core.knowledge.KnowledgeIndex
import java.io.File

/**
 * README 里会变的清单(内置知识、服务商预设)从源头生成,夹在 `<!-- generated:<名>:start -->` 和 `<!-- generated:<名>:end -->` 之间。
 * `./gradlew updateReadme` 改写 README;core 的 `ReadmeSectionsTest` 守着 README 和生成结果一致。
 *
 * 知识清单用构建索引的同一个 [KnowledgeIndex.build],顺序就是索引的顺序。服务商预设在 app 的 `ProviderPresets.kt` 里,
 * core 加载不了安卓模块的类,这里按「`ProviderPreset("名字", ProviderConfig.Kind.<类型>, "地址"`」逐条读源码;
 * app 的 `ReadmePresetsTest` 拿真对象 `ProviderPresets.all` 对照,读法跟不上源码时那边失败。
 */
object ReadmeSections {
    const val README = "README.md"
    const val KNOWLEDGE = "knowledge"
    const val PROVIDERS = "providers"
    const val RESOURCES = "core/src/main/resources"
    const val PRESETS_SOURCE = "app/src/main/java/com/wangzan101/mika/settings/ProviderPresets.kt"
    private const val NOTE = "<!-- 这一段由 ./gradlew updateReadme 生成，不要手改 -->"

    fun start(name: String) = "<!-- generated:$name:start -->"
    fun end(name: String) = "<!-- generated:$name:end -->"

    /** 源码里的一条预设;[kind] 是 `ProviderConfig.Kind` 的枚举名,[baseUrl] 为空的是「自定义」。 */
    data class Preset(val label: String, val kind: String, val baseUrl: String)

    private val PRESET = Regex("""ProviderPreset\(\s*"([^"]+)"\s*,\s*ProviderConfig\.Kind\.(\w+)\s*,\s*"([^"]*)"""")

    fun presets(source: String): List<Preset> =
        PRESET.findAll(source).map { Preset(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()

    private fun kindName(kind: String) = when (kind) { "OpenAiCompat" -> "OpenAI 兼容"; else -> kind }

    fun knowledge(entries: List<IndexEntry>): String {
        fun names(kind: String) = entries.filter { it.kind == kind }.joinToString("、") { it.display }
        return listOf(
            NOTE,
            "| 类别 | 内置 |",
            "|---|---|",
            "| **App** | ${names(KnowledgeIndex.APP)} |",
            "| **网站** | ${names(KnowledgeIndex.SITE)} |",
            "| **通用做法**（技能） | ${names(KnowledgeIndex.SKILL)} |",
        ).joinToString("\n")
    }

    /** 一句话:有哪些预设(地址为空的「自定义」不算),自定义能接哪几类接口(预设里出现过的全部类型)。 */
    fun providers(presets: List<Preset>): String {
        val names = presets.filter { it.baseUrl.isNotEmpty() }.joinToString("、") { it.label }
        val kinds = presets.map { kindName(it.kind) }.distinct().joinToString("、")
        return listOf(NOTE, "内置 $names 等服务商预设；其他服务商选「自定义」，$kinds 接口都能接。").joinToString("\n")
    }

    /** 仓库根目录 [root] 下各段该有的内容。 */
    fun sections(root: File): Map<String, String> {
        val index = KnowledgeIndex.build(File(root, RESOURCES))
        check(index.errors.isEmpty()) { "内置知识校验没通过:\n" + index.errors.joinToString("\n") }
        val presets = presets(File(root, PRESETS_SOURCE).readText())
        check(presets.isNotEmpty()) { "$PRESETS_SOURCE 里没读到 ProviderPreset" }
        return mapOf(KNOWLEDGE to knowledge(index.entries), PROVIDERS to providers(presets))
    }

    /** 把 [readme] 里每段标记之间换成 [sections] 的内容;标记缺了或顺序不对直接失败。 */
    fun render(readme: String, sections: Map<String, String>): String =
        sections.entries.fold(readme) { text, (name, body) ->
            val s = text.indexOf(start(name))
            val e = text.indexOf(end(name))
            check(s >= 0 && e > s) { "$README 里缺少 ${start(name)} … ${end(name)}" }
            text.substring(0, s + start(name).length) + "\n" + body + "\n" + text.substring(e)
        }

    /** `updateReadme` 的入口:`<仓库根目录>`。 */
    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(args[0])
        val file = File(root, README)
        val before = file.readText()
        val after = render(before, sections(root))
        if (after != before) file.writeText(after)
        println(if (after != before) "README.md 已更新" else "README.md 无需更新")
    }
}
