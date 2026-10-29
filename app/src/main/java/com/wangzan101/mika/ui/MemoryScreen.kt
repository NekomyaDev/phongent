package com.wangzan101.mika.ui

import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.memory.MemoryKind
import com.wangzan101.mika.ui.components.ActionSheet
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.RowActions
import com.wangzan101.mika.ui.components.SearchField
import com.wangzan101.mika.ui.components.SegmentedControl
import com.wangzan101.mika.ui.components.SheetAction
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

private val SEGMENT_TAGS = Segment.entries.map { "segment-${it.name}" }

/** 记忆按类别分组的顺序和小标题。 */
private val KIND_GROUPS = listOf(MemoryKind.fact to "关于你", MemoryKind.preference to "偏好", MemoryKind.procedure to "做法")
private val KIND_NAMES = listOf("事实", "偏好", "做法")

/** 设置 →「记忆与技能」:看得见、改得了、删得掉。分三段:记忆、技能(内容在 [SkillsSection])、待确认。 */
@Composable
fun MemoryScreen(vm: MemoryViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ui by vm.ui.collectAsState()
    var editing by remember { mutableStateOf<Row?>(null) }
    var acting by remember { mutableStateOf<Row?>(null) }
    var adding by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val searchable = ui.segment != Segment.Skills

    SubPage(
        "记忆与技能", onBack,
        right = if (searchable) ({
            BarIconButton(MikaIcons.Search, "搜索", { searching = !searching; if (!searching) vm.search("") })
        }) else null,
    ) {
        ui.brokenNotice?.let {
            GroupCard { Text("之前的记忆读不出来,已另存,现在从空开始。", Modifier.padding(16.dp), style = MikaType.Caption, color = MikaTheme.colors.warn) }
        }

        val pendingLabel = if (ui.pendingCount > 0) "待确认 ${ui.pendingCount}" else "待确认"
        SegmentedControl(
            listOf("记忆", "技能", pendingLabel), Segment.entries.indexOf(ui.segment), { vm.select(Segment.entries[it]) },
            tags = SEGMENT_TAGS,
        )
        if (searchable && (searching || ui.query.isNotEmpty())) {
            SearchField(ui.query, vm::search, "搜索", Modifier.testTag("memory-search"))
        }

        when (ui.segment) {
            Segment.Memories -> {
                if (ui.rows.isEmpty()) {
                    GroupLabel("")
                    EmptyNote(if (ui.query.isBlank()) "还没有记住的事" else "没有找到")
                }
                KIND_GROUPS.forEach { (kind, label) ->
                    val rows = ui.rows.filter { it.kind == kind }
                    if (rows.isNotEmpty()) {
                        GroupLabel(label)
                        GroupCard {
                            rows.forEachIndexed { i, r ->
                                MemoryRow(r, first = i == 0, onClick = { acting = r })
                            }
                        }
                    }
                }
                GroupLabel("")
                GroupCard {
                    ListRow(MikaIcons.Plus, "手写一条", first = true, titleColor = MikaTheme.colors.accent, tone = TileTone.Accent, onClick = { adding = true })
                }
            }
            Segment.Skills -> SkillsSection(vm, ui.skills, pending = false)
            Segment.Pending -> {
                if (ui.pendingCount == 0) {
                    GroupLabel("")
                    EmptyNote("没有等你确认的东西")
                }
                if (ui.pending.isNotEmpty()) {
                    GroupLabel("等你批准的记忆")
                    GroupCard {
                        ui.pending.forEachIndexed { i, r ->
                            Column {
                                MemoryRow(r, first = i == 0, onClick = null)
                                RowActions {
                                    MikaButton("好", { vm.approve(r.id) }, size = ButtonSize.Small)
                                    MikaButton("不要", { vm.reject(r.id) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                                }
                            }
                        }
                    }
                }
                SkillsSection(vm, ui.skills, pending = true)
            }
        }

        GroupLabel("开关")
        GroupCard {
            SwitchRow("使用记忆", "关掉后不读也不记", ui.switches.useMemory, first = true) { on -> vm.setSwitches { it.copy(useMemory = on) } }
            SwitchRow("自动记住我说过的事", "任务结束后从你的原话里整理", ui.switches.autoRemember) { on -> vm.setSwitches { it.copy(autoRemember = on) } }
            SwitchRow("从办成的任务里学技能", "学到的技能要你收下才会用", ui.switches.learnSkills) { on -> vm.setSwitches { it.copy(learnSkills = on) } }
        }
        GroupCard {
            ListRow(MikaIcons.Close, "全部清除", "删掉所有记忆和学到的技能", first = true, titleColor = MikaTheme.colors.bad, tone = TileTone.Bad, onClick = vm::requestClear)
        }
    }

    acting?.let { r ->
        ActionSheet(
            r.content,
            listOf(
                SheetAction("改一改") { editing = r },
                SheetAction(if (r.pinned) "取消置顶" else "置顶") { vm.setPinned(r.id, !r.pinned) },
                SheetAction("删除", destructive = true) { vm.delete(r.id) },
            ),
            onDismiss = { acting = null },
        )
    }
    editing?.let { r ->
        EditDialog("改一改", r.content, r.keywords, onDismiss = { editing = null }) { content, keywords, _ -> vm.edit(r.id, content, keywords); editing = null }
    }
    if (adding) {
        EditDialog("手写一条", "", emptyList(), onDismiss = { adding = false }, withKind = true) { content, keywords, kind -> vm.add(kind, content, keywords); adding = false }
    }
    ui.message?.let { m ->
        AlertDialog(onDismissRequest = vm::dismissMessage, text = { Text(m) }, confirmButton = { TextButton(vm::dismissMessage) { Text("知道了") } })
    }
    if (ui.clearStage > 0) {
        AlertDialog(
            onDismissRequest = vm::cancelClear,
            title = { Text(if (ui.clearStage == 1) "全部清除?" else "真的要清除吗?") },
            text = { Text(if (ui.clearStage == 1) "所有记忆、学到的技能和草稿都会删掉。" else "清除后不能恢复。") },
            confirmButton = { TextButton(vm::confirmClear, Modifier.testTag("confirm-clear")) { Text(if (ui.clearStage == 1) "继续" else "全部清除") } },
            dismissButton = { TextButton(vm::cancelClear) { Text("取消") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, on: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit) {
    ListRow(null, title, subtitle, first = first) { MikaSwitch(on, onChange) }
}

@Composable
private fun MemoryRow(r: Row, first: Boolean, onClick: (() -> Unit)?) {
    val subtitle = listOfNotNull(
        (if (r.pinned) "已置顶 · " else "") + r.source,
        r.replaces?.let { "原来:$it" },
    ).joinToString("\n")
    androidx.compose.foundation.layout.Box(Modifier.testTag("memory-row")) {
        ListRow(null, r.content, subtitle, first = first, onClick = onClick)
    }
}

@Composable
internal fun Link(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp), style = MikaType.Hint, fontWeight = FontWeight.Medium, color = MikaTheme.colors.accent)
}

@Composable
private fun EditDialog(
    title: String, content: String, keywords: List<String>, onDismiss: () -> Unit,
    withKind: Boolean = false, onSave: (String, List<String>, MemoryKind) -> Unit,
) {
    var kind by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf(content) }
    var words by remember { mutableStateOf(keywords.joinToString(" ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            KeyboardDismissArea { Column {
                if (withKind) SegmentedControl(KIND_NAMES, kind, { kind = it }, Modifier.padding(bottom = 8.dp), tags = listOf("memory-kind-fact", "memory-kind-preference", "memory-kind-procedure"))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().keepsKeyboard().testTag("memory-content"), label = { Text("内容") })
                OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth().padding(top = 8.dp).keepsKeyboard().testTag("memory-keywords"), label = { Text("检索词(空格分开)") })
            } }
        },
        confirmButton = { TextButton({ onSave(text, words.split(Regex("\\s+")).filter { it.isNotBlank() }, KIND_GROUPS[kind].first) }, Modifier.testTag("memory-save")) { Text("保存") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
