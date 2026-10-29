package com.wangzan101.mika.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.record.RunMetrics
import com.wangzan101.mika.core.record.RunMetricsStore
import com.wangzan101.mika.core.record.RunStats
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.components.RowActions
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.SegmentedControl
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToLong

/** 运行小结和运行统计的说法。token 数用 Token 用量页同一种写法([UsageCopy.tokens])。 */
object RunMetricsCopy {
    const val MODEL = "模型思考"
    const val OBSERVE = "读屏"
    const val ACT = "操作"
    const val OTHER = "其他（等待、重试）"
    const val STATS_TITLE = "运行统计"
    const val STATS_SUBTITLE = "最近几次运行的用时、来回次数和 Token"
    const val STATS_EMPTY = "还没有运行统计。让 Mika 办件事，这里就会记下。"
    const val STATS_NOTE = "只统计这台手机上最近 ${RunMetricsStore.DEFAULT_KEEP} 次运行；时间从 Mika 开始处理算起，第一次反馈是第一次看到它在做什么的时候。"
    const val COPY = "复制 JSON"
    const val SHARE = "分享 JSON"

    /** 统计范围:最近 10 / 30 / 100 次,或存下的全部(null)。 */
    val RANGES: List<Int?> = listOf(10, 30, 100, null)
    val RANGE_LABELS = listOf("最近 10 次", "30 次", "100 次", "全部")

    /** 时长:一分钟以内到 0.1 秒(「3.2 秒」「45 秒」),往上「2 分 13 秒」,一小时以上「1 小时 5 分」。 */
    fun duration(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        if (t < 59_950) {
            val tenths = Math.round(t / 100.0)
            return if (tenths % 10 == 0L) "${tenths / 10} 秒" else String.format(Locale.ROOT, "%.1f 秒", tenths / 10.0)
        }
        val s = Math.round(t / 1000.0)
        if (s < 3600) return "${s / 60} 分 ${s % 60} 秒"
        return "${s / 3600} 小时 ${s % 3600 / 60} 分"
    }

    /** 时间线顶上那一行:「共 2 分 13 秒 · 和模型来回 14 次 · 8.6 万 Token · 第一次反馈 3.2 秒」。 */
    fun summaryLine(m: RunMetrics): String = listOfNotNull(
        "共 ${duration(m.totalMs)}",
        "和模型来回 ${m.modelCalls} 次",
        "${UsageCopy.tokens(m.tokens)} ${UsageCopy.UNIT}",
        m.firstFeedbackMs?.let { "第一次反馈 ${duration(it)}" },
    ).joinToString(" · ")

    /** 用时和占总时长的比例。 */
    fun share(ms: Long, totalMs: Long): String =
        if (totalMs <= 0) duration(ms) else "${duration(ms)} · ${UsageCopy.percent(ms.toDouble() / totalMs)}"

    /** 点开后的细目:时间去向、token、步数与次数。 */
    fun breakdown(m: RunMetrics): List<Pair<String, String>> = buildList {
        add(MODEL to share(m.modelMs, m.totalMs))
        add(OBSERVE to share(m.observeMs, m.totalMs))
        add(ACT to share(m.actMs, m.totalMs))
        add(OTHER to share(m.otherMs, m.totalMs))
        if (m.retryWaitMs > 0) add("其中重试等待" to duration(m.retryWaitMs))
        add("输入 Token" to UsageCopy.tokens(m.inputTokens))
        add("输出 Token" to UsageCopy.tokens(m.outputTokens))
        add("缓存命中" to UsageCopy.tokens(m.cachedTokens))
        if (m.callsWithoutUsage > 0) add("没报用量" to "${m.callsWithoutUsage} 次")
        m.allCalls?.let { add("全部模型请求" to "$it 次（含旁路调用和重试）") }
        add("步数" to "${m.steps} 步")
        add("读屏 / 操作" to "${m.observeCalls} 次 / ${m.actCalls} 次")
    }

    /** 运行统计的各行。 */
    fun statsRows(s: RunStats): List<Pair<String, String>> = listOf(
        "统计了" to "${s.runs} 次运行",
        "办成率" to "${UsageCopy.percent(s.successRate)}（${s.succeeded}/${s.runs}）",
        "用时中位数" to duration(s.medianTotalMs),
        "第一次反馈中位数" to (s.medianFirstFeedbackMs?.let(::duration) ?: "—"),
        "平均和模型来回" to String.format(Locale.ROOT, "%.1f 次", s.avgModelCalls),
        "平均 Token" to UsageCopy.tokens(s.avgTokens.roundToLong()),
    )

    /** 时间去向:各项之和占总时长之和。 */
    fun splitRows(s: RunStats): List<Pair<String, String>> = listOf(
        MODEL to UsageCopy.percent(ratio(s.modelMs, s.totalMs)),
        OBSERVE to UsageCopy.percent(ratio(s.observeMs, s.totalMs)),
        ACT to UsageCopy.percent(ratio(s.actMs, s.totalMs)),
        OTHER to UsageCopy.percent(ratio(s.otherMs, s.totalMs)),
    )

    fun copied(n: Int) = "已复制 $n 次运行的小结"
    const val EXPORT_FAILED = "没能导出，数据可能太大，换个小一点的范围试试。"

    private fun ratio(a: Long, b: Long) = if (b <= 0) 0.0 else a.toDouble() / b
}

/** 「查看过程」顶上的一行小结;点一下展开时间去向、token 和步数。 */
@Composable
fun RunMetricsSummary(m: RunMetrics) {
    val c = MikaTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.testTag("run-metrics-summary")
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Text(RunMetricsCopy.summaryLine(m), style = MikaType.Caption, color = c.ink2)
        Text(if (open) "收起" else "看时间花在哪", Modifier.padding(top = 2.dp), style = MikaType.Hint, color = c.accent)
    }
    if (open) RunMetricsCopy.breakdown(m).forEach { (k, v) -> ListRow(null, k) { RowValue(v) } }
}

/** 开发者选项 › 运行统计:从小结存储里读最近几次,算平均和中位数;能把这几次的小结复制或分享成 JSON。 */
@Composable
fun RunStatsScreen(store: RunMetricsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var range by rememberSaveable { mutableIntStateOf(1) }
    val all by produceState<List<RunMetrics>?>(null, store) { value = withContext(Dispatchers.IO) { store.all() } }
    val picked = remember(all, range) { all?.let { list -> RunMetricsCopy.RANGES[range]?.let { list.takeLast(it) } ?: list } }
    val stats = remember(picked) { picked?.takeIf { it.isNotEmpty() }?.let(RunStats::of) }
    RunStatsContent(
        stats, range, { range = it },
        onCopy = { picked?.let { copyJson(context, it) } },
        onShare = { picked?.let { shareJson(context, it) } },
        onBack = onBack,
        loaded = all != null,
    )
}

@Composable
fun RunStatsContent(
    stats: RunStats?,
    range: Int,
    onRange: (Int) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
    loaded: Boolean = true,
) {
    PageScaffold(RunMetricsCopy.STATS_TITLE, onBack) {
        SegmentedControl(RunMetricsCopy.RANGE_LABELS, range, onRange, tags = RunMetricsCopy.RANGES.map { "run-stats-range-${it ?: "all"}" })
        if (!loaded) return@PageScaffold
        if (stats == null) {
            EmptyNote(RunMetricsCopy.STATS_EMPTY, Modifier.padding(top = 48.dp))
            return@PageScaffold
        }
        GroupLabel("概况")
        GroupCard { RunMetricsCopy.statsRows(stats).forEachIndexed { i, (k, v) -> ListRow(null, k, first = i == 0) { RowValue(v) } } }
        GroupLabel("时间花在哪")
        GroupCard { RunMetricsCopy.splitRows(stats).forEachIndexed { i, (k, v) -> ListRow(null, k, first = i == 0) { RowValue(v) } } }
        GroupLabel("导出")
        GroupCard {
            ListRow(null, "这 ${stats.runs} 次运行的小结", "每次一项，可以拿去自己分析", first = true)
            RowActions {
                MikaButton(RunMetricsCopy.COPY, onCopy, Modifier.testTag("run-stats-copy"), size = ButtonSize.Small)
                MikaButton(RunMetricsCopy.SHARE, onShare, Modifier.testTag("run-stats-share"), kind = ButtonKind.Tonal, size = ButtonSize.Small)
            }
        }
        FooterNote(RunMetricsCopy.STATS_NOTE)
    }
}

private fun copyJson(context: Context, list: List<RunMetrics>) {
    val ok = runCatching {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(RunMetricsCopy.STATS_TITLE, RunMetricsStore.export(list)))
    }.isSuccess
    Toast.makeText(context, if (ok) RunMetricsCopy.copied(list.size) else RunMetricsCopy.EXPORT_FAILED, Toast.LENGTH_SHORT).show()
}

private fun shareJson(context: Context, list: List<RunMetrics>) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, RunMetricsStore.export(list))
        context.startActivity(Intent.createChooser(send, RunMetricsCopy.STATS_TITLE))
    }.onFailure { Toast.makeText(context, RunMetricsCopy.EXPORT_FAILED, Toast.LENGTH_SHORT).show() }
}
