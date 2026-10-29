package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wangzan101.mika.cap.web.SiteEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.ValueTone
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.components.FooterNote

/**
 * 设置页「网站登录」一节的数据与操作。[onOpenLogin] 为 null 时不提供「打开登录」。
 * [onLogout] 返回过期掉的 Cookie 个数。
 */
class WebSitesState(
    val sites: List<SiteEntry>,
    val onOpenLogin: ((String) -> Unit)?,
    val onLogout: (String) -> Int,
    val onLogoutAll: () -> Unit,
)

const val WEB_LOGOUT_PARTIAL = "个别网站的登录可能仍在,如需彻底清除请用「全部退出」。"
const val WEB_CHALLENGED_NOTE = "Mika 最近在这个网站上碰到过人机验证或访问拦截。如果一直这样,可以清除这个网站的数据(Cookie 与网页存储)后重新登录。"
const val WEB_DATA_NOTE = "网站的登录数据只存在这台手机上 Mika 的私有目录里,没有另行加密,和系统浏览器的保护程度相同。"

/** 设置页的「网站登录」:列出内置浏览器访问过的网站(登录过的在前),可以打开登录、退出单个网站、全部退出。 */
@Composable
fun WebSitesSection(state: WebSitesState, labeled: Boolean = true) {
    var picked by remember { mutableStateOf<SiteEntry?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    if (labeled) GroupLabel("网站登录")
    GroupCard {
        if (state.sites.isEmpty()) {
            ListRow(MikaIcons.Globe, "还没有访问过的网站", "Mika 在网页上办过事、或你在这里登录过之后会列在这里", first = true)
        }
        val now = System.currentTimeMillis()
        state.sites.forEachIndexed { i, s ->
            ListRow(
                MikaIcons.Globe, s.host,
                subtitle = listOfNotNull(
                    "在 Mika 里登录过".takeIf { s.loggedInByUser },
                    "碰到过验证或拦截".takeIf { s.challenged(now) },
                    "最近访问 ${day(s.lastVisitMs)}",
                ).joinToString(" · "),
                first = i == 0,
                onClick = { picked = s },
            ) { RowValue("管理", ValueTone.Link) }
        }
        ListRow(
            MikaIcons.Close, "全部退出并清除网页数据", titleColor = MikaTheme.colors.bad, tone = TileTone.Bad,
            onClick = { confirmAll = true },
        )
    }
    FooterNote(WEB_DATA_NOTE)

    picked?.let { s ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(s.host) },
            text = {
                val base = if (s.loggedInByUser) "你在 Mika 里登录过这个网站。" else "Mika 访问过这个网站。"
                Text(if (s.challenged(System.currentTimeMillis())) "$base\n\n$WEB_CHALLENGED_NOTE" else base)
            },
            confirmButton = {
                Column {
                    state.onOpenLogin?.let { open ->
                        TextButton({ picked = null; open(s.host) }) { Text("打开登录") }
                    }
                    val clearing = s.challenged(System.currentTimeMillis())
                    TextButton({
                        picked = null
                        state.onLogout(s.host)
                        notice = if (clearing) "已清除 ${s.host} 的 Cookie 与网页存储,下次用要重新登录。个别数据可能仍在,如需彻底清除请用「全部退出」。" else "已退出 ${s.host}。$WEB_LOGOUT_PARTIAL"
                    }) { Text(if (clearing) "清除这个网站的数据" else "退出这个网站", color = MikaTheme.colors.bad) }
                }
            },
            dismissButton = { TextButton({ picked = null }) { Text("取消") } },
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("全部退出并清除网页数据?") },
            text = { Text("会退出内置浏览器里所有网站的登录,并清除它们存在 Mika 里的网页数据。之后 Mika 用网页办事时要重新登录。") },
            confirmButton = {
                TextButton({ confirmAll = false; state.onLogoutAll(); notice = "已全部退出并清除网页数据。" }) {
                    Text("全部退出", color = MikaTheme.colors.bad)
                }
            },
            dismissButton = { TextButton({ confirmAll = false }) { Text("取消") } },
        )
    }

    notice?.let { n ->
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(n) },
            confirmButton = { TextButton({ notice = null }) { Text("知道了") } },
        )
    }
}

private fun day(ms: Long): String = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ms))
