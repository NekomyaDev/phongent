package com.wangzan101.mika.ui

import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.core.trigger.TriggerWindow
import com.wangzan101.mika.core.trigger.TriggerWindowState
import com.wangzan101.mika.task.ExactAlarmGuide
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.ui.components.ActionSheet
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.SegmentedControl
import com.wangzan101.mika.ui.components.SheetAction
import com.wangzan101.mika.ui.components.StateCard
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 已保存的定时任务:分「进行中」「已停用」两段,每条三行(做什么、什么时候与下次、上次结果),右边开关。
 * 点一行升起操作面板(看对话、立即执行、改时间、删除)。页面只读账本,修改后让调度器重新布置系统闹钟。
 */
@Composable
fun PlansScreen(context: Context, onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    BackHandler(onBack = onBack)
    var snapshot by remember { mutableStateOf(TriggerRuntime.snapshot(context)) }
    var editing by remember { mutableStateOf<TriggerDefinition?>(null) }
    var deleting by remember { mutableStateOf<TriggerDefinition?>(null) }
    var acting by remember { mutableStateOf<TriggerDefinition?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    // 从系统设置回来时重读:刚开了权限,提示就该消失
    var exact by remember { mutableStateOf(TriggerRuntime.canScheduleExactly(context)) }
    LifecycleResumeEffect(Unit) {
        exact = TriggerRuntime.canScheduleExactly(context)
        onPauseOrDispose {}
    }
    fun refresh() { snapshot = TriggerRuntime.snapshot(context) }
    fun act(block: () -> Unit) {
        runCatching { block(); refresh(); error = null }
            .onFailure { error = it.message ?: "操作失败" }
    }

    val (running, stopped) = snapshot.triggers.sortedBy { it.nextAtMs }.partition { it.enabled }
    val shown = if (segment == 0) running else stopped

    SubPage("定时任务", onBack) {
        if (!exact) {
            StateCard(
                MikaIcons.Alarm, ExactAlarmGuide.TITLE, "没有这个权限,定时任务到点可能不准时,甚至不会启动。开一次就行。",
                tone = TileTone.Warn,
            ) { MikaButton("去开启", { act { context.startActivity(TriggerRuntime.exactAlarmSettings(context)) } }, kind = ButtonKind.Primary, size = ButtonSize.Small) }
        }
        SegmentedControl(
            listOf("进行中 ${running.size}", "已停用 ${stopped.size}"), segment, { segment = it },
            tags = listOf("plans-segment-running", "plans-segment-stopped"),
        )
        error?.let { Text(it, Modifier.padding(top = 8.dp), style = MikaType.Caption, color = MikaTheme.colors.bad) }
        if (shown.isEmpty()) {
            GroupLabel("")
            EmptyNote(
                if (snapshot.triggers.isEmpty()) "还没有定时任务\n在对话里告诉 Mika 什么时候提醒你，或什么时候帮你做事。"
                else if (segment == 0) "没有进行中的定时任务" else "没有已停用的定时任务",
            )
        } else {
            GroupLabel("${shown.size} 个")
            GroupCard {
                shown.forEachIndexed { i, plan ->
                    val last = snapshot.windows.filter { it.triggerId == plan.id }.maxByOrNull { it.dueAtMs }
                    PlanRow(plan, last, first = i == 0, onClick = { acting = plan }, onToggle = { on -> act { TriggerRuntime.setEnabled(context, plan.id, on) } })
                }
            }
        }
        FooterNote("在对话里说「每天 8 点…」「降价了告诉我」也能建，结果会发回建它的那个对话。定时触发可能受系统省电影响；停用或删除只影响后续触发。")
    }

    acting?.let { plan ->
        ActionSheet(
            plan.reminderText ?: plan.goal,
            listOf(
                SheetAction("看对话") { onOpenConversation(plan.threadId) },
                SheetAction("立即执行") { act { TriggerRuntime.runNow(context, plan.id) } },
                SheetAction("改时间") { editing = plan },
                SheetAction("删除", destructive = true) { deleting = plan },
            ),
            onDismiss = { acting = null },
        )
    }

    editing?.let { plan ->
        var value by remember(plan.id) { mutableStateOf(formatPlanTime(plan.nextAtMs)) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("修改下次时间") },
            text = { KeyboardDismissArea { OutlinedTextField(value, { value = it }, Modifier.keepsKeyboard(), label = { Text("本地时间：yyyy-MM-dd HH:mm") },
                singleLine = true) } },
            confirmButton = { TextButton(onClick = {
                val at = runCatching { LocalDateTime.parse(value, PLAN_TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    .getOrNull()
                if (at == null || at <= System.currentTimeMillis()) error = "请输入未来的有效时间"
                else { act { TriggerRuntime.reschedule(context, plan.id, at) }; editing = null }
            }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
    deleting?.let { plan ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这条定时任务？") },
            text = { Text(plan.goal) },
            confirmButton = { TextButton(onClick = { act { TriggerRuntime.remove(context, plan.id) }; deleting = null }) {
                Text("删除", color = MikaTheme.colors.bad)
            } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}


@Composable
private fun PlanRow(plan: TriggerDefinition, last: TriggerWindow?, first: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val status = when {
        plan.enabled -> "进行中"
        last?.state == TriggerWindowState.Delivered -> "已完成"
        last?.state == TriggerWindowState.Skipped -> "已检查"
        last?.state == TriggerWindowState.Failed -> "检查失败"
        last?.state == TriggerWindowState.Unknown -> "结果待确认"
        else -> "已暂停"
    }
    val repeat = when {
        plan.calendarTime != null -> {
            val days = if (plan.calendarDays.isEmpty()) "每天" else plan.calendarDays.joinToString("、") {
                "周" + listOf("一", "二", "三", "四", "五", "六", "日").getOrElse(it - 1) { "?" }
            }
            "$days ${plan.calendarTime} (${plan.calendarZone})"
        }
        plan.intervalMs != null -> "每 ${plan.intervalMs!! / 60_000} 分钟"
        else -> "仅一次"
    }
    val when_ = if (plan.enabled) "$repeat · 下次 ${formatPlanTime(plan.nextAtMs)}" else "$repeat · $status"
    val subtitle = plan.conditionGoal?.let { "满足条件才执行：$it · $when_" } ?: when_
    val c = MikaTheme.colors
    val delivered = last?.state == TriggerWindowState.Delivered
    // 一次性的任务做完后不能再打开;循环的和从没跑过的可以
    val canToggle = plan.enabled || plan.intervalMs != null || plan.calendarTime != null || last == null
    ListRow(
        null, plan.reminderText ?: plan.goal, subtitle, first = first,
        detail = last?.let { "上次 ${formatPlanTime(it.dueAtMs)} · ${windowLabel(it.state)}" } ?: "还没执行过",
        detailColor = if (delivered) c.ok else c.ink3,
        onClick = onClick,
    ) { MikaSwitch(plan.enabled, onToggle, enabled = canToggle) }
}

private val PLAN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private fun formatPlanTime(ms: Long): String = PLAN_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
private fun windowLabel(state: TriggerWindowState): String = when (state) {
    TriggerWindowState.Delivered -> "已送达"
    TriggerWindowState.Skipped -> "条件未变化"
    TriggerWindowState.Failed -> "检查失败"
    TriggerWindowState.Unknown -> "结果待确认"
    TriggerWindowState.Claimed, TriggerWindowState.Dispatched, TriggerWindowState.ActionDispatched -> "执行中"
}
