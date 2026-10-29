package com.wangzan101.mika.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wangzan101.mika.confirm.ConfirmationCopy
import com.wangzan101.mika.core.action.GrantKey
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.ValueTone
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.components.TileTone

/** 设置页「长期授权」一节的数据与操作;[appLabel] 把包名换成显示名。 */
class StandingGrantsState(
    val items: List<GrantKey>,
    val appLabel: (String) -> String,
    val onRevoke: (GrantKey) -> Unit,
    val onRevokeAll: () -> Unit,
)

/** 设置页的「长期授权」:你选过「以后都允许」的操作,可逐条撤销或全部撤销。 */
@Composable
fun StandingGrantsSection(state: StandingGrantsState, labeled: Boolean = true) {
    var confirmAll by remember { mutableStateOf(false) }
    if (labeled) GroupLabel("长期授权")
    GroupCard {
        if (state.items.isEmpty()) {
            ListRow(MikaIcons.Shield, "还没有长期授权", "确认时选「以后都允许」的操作会列在这里", first = true)
        }
        state.items.forEachIndexed { i, g ->
            ListRow(
                MikaIcons.Shield, "${ConfirmationCopy.actionLabel(g.action)} · ${g.target}",
                subtitle = "在 ${state.appLabel(g.app)} · 点「${g.element.removePrefix("field:")}」", first = i == 0,
                onClick = { state.onRevoke(g) },
            ) { RowValue("撤销", ValueTone.Link) }
        }
        if (state.items.isNotEmpty()) {
            ListRow(MikaIcons.Close, "全部撤销", titleColor = MikaTheme.colors.bad, tone = TileTone.Bad, onClick = { confirmAll = true })
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("撤销全部长期授权?") },
            text = { Text("之后这些操作会照审批模式重新问你。") },
            confirmButton = { TextButton({ confirmAll = false; state.onRevokeAll() }) { Text("全部撤销", color = MikaTheme.colors.bad) } },
            dismissButton = { TextButton({ confirmAll = false }) { Text("取消") } },
        )
    }
}
