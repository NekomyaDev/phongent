package com.wangzan101.mika.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.wangzan101.mika.core.agent.SpeedFlags
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.components.RowChevron

/** 一个工程开关:[id] 用作测试标签,[change] 算出改过这一项之后的整组开关。 */
data class DevSwitch(
    val id: String, val title: String, val subtitle: String, val checked: Boolean, val change: (Boolean) -> SpeedFlags,
)

/** 开发者选项的两组:速度(已验证、默认多半开着)和实验(默认关)。开关的值与存储([SpeedFlags])不变。 */
object DeveloperSwitches {
    fun speed(flags: SpeedFlags): List<DevSwitch> = listOf(
        DevSwitch("adaptive-vision", "先读元素，必要时看图", "控件信息够用时不把图片交给模型，读不清时再补图", flags.adaptiveVision) { flags.copy(adaptiveVision = it) },
        DevSwitch("inline-screen", "操作后带回屏幕变化", "每次操作后顺带看一眼屏幕,少一次单独读屏", flags.inlineScreen) { flags.copy(inlineScreen = it) },
        DevSwitch("batch-steps", "有把握时连做几步", "确定的几步一次做完,中途不再停下来问模型", flags.batchSteps) { flags.copy(batchSteps = it) },
        DevSwitch("deep-links", "直接打开要去的页面", "能用链接直达 App 里的页面,就不一层层点进去", flags.deepLinks) { flags.copy(deepLinks = it) },
        DevSwitch("light-steps", "简单步骤交给轻量模型", "容易的步骤让更快的轻量模型来做,难的仍由主模型做", flags.lightSteps) { flags.copy(lightSteps = it) },
    )

    fun experimental(flags: SpeedFlags): List<DevSwitch> = listOf(
        DevSwitch("replay", "照着上次的做法做", "做过的事按记住的操作路线快速重做,对不上就交回模型", flags.replay) { flags.copy(replay = it) },
        DevSwitch("parallel-tasks", "不同对话同时进行", "不同对话的任务各占一块虚拟屏,一起做", flags.parallelTasks) { flags.copy(parallelTasks = it) },
        DevSwitch("structured-compaction", "长任务的摘要分条写", "步骤多了要压缩时,按目标、事实、进展、下一步记下来", flags.structuredCompaction) { flags.copy(structuredCompaction = it) },
    )
}

/** 「做事方式」一组:询问 / 自主的全局开关。 */
object AutonomyCopy {
    const val GROUP = "做事方式"
    const val TITLE = "自主模式"
    const val SUBTITLE = "打开后，付款、发送、联系他人这类对外操作不再先问你，Mika 直接做完"
}

const val DEVELOPER_NOTE = "这些开关影响 Mika 做事的方式和速度，一般保持默认。"

/** 当前看图模型的「看图定位已验证」:[model] 是它的模型名。 */
data class GroundingSetting(val model: String, val verified: Boolean)

object GroundingCopy {
    const val GROUP = "看图"
    const val TITLE = "看图定位已验证"

    fun subtitle(model: String) = "看图模型 $model 实测过框得准再打开；关着时，它框出的位置要另有佐证才会去点。"
}

/**
 * 设置 › 开发者选项:最前面是「自主模式」([autonomous],默认关即询问);原来「速度」下的八个开关,分成速度和实验两组;有看图模型时另有它的「看图定位已验证」
 * ([grounding] 为 null 时不显示);最后是「运行统计」的入口([onOpenRunStats] 为 null 时不显示)。
 */
@Composable
fun DeveloperOptionsScreen(
    speed: SpeedFlags,
    onSpeed: (SpeedFlags) -> Unit,
    onBack: () -> Unit,
    autonomous: Boolean = false,
    onAutonomous: (Boolean) -> Unit = {},
    grounding: GroundingSetting? = null,
    onGrounding: (Boolean) -> Unit = {},
    onOpenRunStats: (() -> Unit)? = null,
) {
    PageScaffold("开发者选项", onBack) {
        GroupLabel(AutonomyCopy.GROUP)
        GroupCard {
            ListRow(null, AutonomyCopy.TITLE, AutonomyCopy.SUBTITLE, first = true, onClick = { onAutonomous(!autonomous) }) {
                MikaSwitch(autonomous, onAutonomous, Modifier.testTag("dev-autonomous"))
            }
        }
        GroupLabel("速度")
        SwitchGroup(DeveloperSwitches.speed(speed), onSpeed)
        GroupLabel("实验")
        SwitchGroup(DeveloperSwitches.experimental(speed), onSpeed)
        if (grounding != null) {
            GroupLabel(GroundingCopy.GROUP)
            GroupCard {
                ListRow(null, GroundingCopy.TITLE, GroundingCopy.subtitle(grounding.model), first = true, onClick = { onGrounding(!grounding.verified) }) {
                    MikaSwitch(grounding.verified, onGrounding, Modifier.testTag("dev-grounding-verified"))
                }
            }
        }
        if (onOpenRunStats != null) {
            GroupLabel("运行记录")
            GroupCard {
                ListRow(null, RunMetricsCopy.STATS_TITLE, RunMetricsCopy.STATS_SUBTITLE, first = true, onClick = onOpenRunStats) { RowChevron() }
            }
        }
        FooterNote(DEVELOPER_NOTE)
    }
}

@Composable
private fun SwitchGroup(switches: List<DevSwitch>, onSpeed: (SpeedFlags) -> Unit) {
    GroupCard {
        switches.forEachIndexed { i, s ->
            // 整行都能点:开关小,行大
            ListRow(null, s.title, s.subtitle, first = i == 0, onClick = { onSpeed(s.change(!s.checked)) }) {
                MikaSwitch(s.checked, { onSpeed(s.change(it)) }, Modifier.testTag("dev-${s.id}"))
            }
        }
    }
}
