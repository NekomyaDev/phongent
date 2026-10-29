package com.wangzan101.mika.ui

import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.knowledge.AppKnowledgeStage
import com.wangzan101.mika.ui.components.ActionSheet
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.RowActions
import com.wangzan101.mika.ui.components.SheetAction
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/**
 * 技能相关的两块内容。[pending] 为假是「技能」段:已收下的个人技能(开关即启用/停用)和在用的 App 知识;
 * 为真是「待确认」段里的部分:新技能草稿(看全文、改一改再收下、收下、不要)和 App 知识草稿。
 */
@Composable
internal fun SkillsSection(vm: MemoryViewModel, ui: SkillsUi, pending: Boolean) {
    var viewing by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SkillRow?>(null) }
    var acting by remember { mutableStateOf<SkillRow?>(null) }
    fun toggle(id: String) { viewing = if (viewing == id) null else id }

    if (pending) {
        if (ui.drafts.isNotEmpty()) {
            GroupLabel("新技能草稿，收下后先试用并核实")
            GroupCard {
                ui.drafts.forEachIndexed { i, d ->
                    Column(Modifier.testTag("skill-row")) {
                        ListRow(null, d.display, d.description.ifBlank { null }, first = i == 0, onClick = { toggle(d.id) })
                        SkillBody(d, expanded = viewing == d.id)
                        RowActions {
                            MikaButton("收下试用", { vm.approveSkill(d.id) }, size = ButtonSize.Small)
                            MikaButton("改一改", { editing = d }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                            MikaButton(if (viewing == d.id) "收起全文" else "看全文", { toggle(d.id) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                            MikaButton("不要", { vm.rejectSkill(d.id) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
                        }
                    }
                }
            }
        }
        val drafts = ui.appKnowledge.filter { it.stage == AppKnowledgeStage.Draft }
        if (drafts.isNotEmpty()) {
            GroupLabel("App 知识草稿")
            GroupCard {
                drafts.forEachIndexed { i, row -> AppKnowledgeItem(vm, row, i == 0, viewing == row.id) { toggle(row.id) } }
            }
        }
    } else {
        GroupLabel("我的技能")
        GroupCard {
            if (ui.installed.isEmpty()) EmptyNote("还没有学到的技能")
            ui.installed.forEachIndexed { i, s ->
                val state = when { s.suspended -> "已停用"; s.trial -> "试用中，未核实"; else -> null }
                val detail = listOfNotNull(state, s.recent.takeIf { it.isNotEmpty() }?.let { "最近:" + it.joinToString(" · ") }).joinToString(" · ")
                Column(Modifier.testTag("skill-row")) {
                    ListRow(null, s.display, s.description.ifBlank { null }, first = i == 0, detail = detail, onClick = { acting = s }) {
                        MikaSwitch(!s.suspended, { on -> if (on) vm.restoreSkill(s.name) else vm.suspendSkill(s.name) })
                    }
                    SkillBody(s, expanded = viewing == s.id)
                }
            }
        }
        val inUse = ui.appKnowledge.filter { it.stage != AppKnowledgeStage.Draft }
        if (inUse.isNotEmpty()) {
            GroupLabel("App 知识")
            GroupCard {
                inUse.forEachIndexed { i, row -> AppKnowledgeItem(vm, row, i == 0, viewing == row.id) { toggle(row.id) } }
            }
        }
    }

    acting?.let { s ->
        ActionSheet(
            s.display,
            listOfNotNull(
                SheetAction(if (viewing == s.id) "收起全文" else "看全文") { toggle(s.id) },
                if (s.canRestore) SheetAction("恢复上一版") { vm.restorePreviousSkill(s.name) } else null,
                SheetAction("删除", destructive = true) { vm.deleteSkill(s.name) },
            ),
            onDismiss = { acting = null },
        )
    }

    editing?.let { d ->
        var text by remember(d.id) { mutableStateOf(d.markdown) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("改一改再试用") },
            text = { KeyboardDismissArea { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 240.dp).keepsKeyboard().testTag("skill-markdown")) } },
            confirmButton = { TextButton({ vm.approveSkill(d.id, text); editing = null }) { Text("收下试用") } },
            dismissButton = { TextButton({ editing = null }) { Text("取消") } },
        )
    }
}

/** 一条 App 知识:点开看全文;草稿可收下试用,没核实的可归档。 */
@Composable
private fun AppKnowledgeItem(vm: MemoryViewModel, row: AppKnowledgeRow, first: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val stage = when (row.stage) {
        AppKnowledgeStage.Draft -> "草稿，未使用"
        AppKnowledgeStage.Trial -> "试用中，未核实"
        AppKnowledgeStage.Verified -> "已核实"
        AppKnowledgeStage.Archived -> "已归档"
    }
    Column {
        ListRow(null, row.name, stage, first = first, onClick = onToggle)
        if (expanded) Text(row.markdown, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), style = MikaType.Hint, color = MikaTheme.colors.ink2)
        RowActions {
            MikaButton(if (expanded) "收起全文" else "看全文", onToggle, kind = ButtonKind.Tonal, size = ButtonSize.Small)
            if (row.stage == AppKnowledgeStage.Draft) MikaButton("收下试用", { vm.approveAppDraft(row.id) }, size = ButtonSize.Small)
            if (row.stage != AppKnowledgeStage.Verified) MikaButton("归档", { vm.rejectAppDraft(row.id) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        }
    }
}

/** 技能行下面:更新草稿改了哪几行,展开时再加全文。 */
@Composable
private fun SkillBody(r: SkillRow, expanded: Boolean) {
    val c = MikaTheme.colors
    if (r.changedLines.isEmpty() && !expanded) return
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        if (r.changedLines.isNotEmpty()) {
            Text("改了这几行:", style = MikaType.Hint, color = c.ink3)
            r.changedLines.forEach { Text(it, style = MikaType.Hint, color = if (it.startsWith("+")) c.ok else c.bad) }
        }
        if (expanded) Text(r.markdown, Modifier.padding(top = 6.dp), style = MikaType.Hint, color = c.ink2)
    }
}
