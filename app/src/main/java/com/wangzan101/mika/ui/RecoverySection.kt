package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.shell.AutoOnBlock
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.core.shell.PermissionResult
import com.wangzan101.mika.core.shell.RecoveryCapability
import com.wangzan101.mika.core.shell.RecoveryConsent
import com.wangzan101.mika.core.shell.RepairOutcome
import com.wangzan101.mika.core.shell.RestoreOutcome
import com.wangzan101.mika.cap.phone.PhoneServices
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.theme.MikaType

/**
 * 设置 › 恢复与修复 里手机操作的几个授权开关的文字(纯函数,便于测试)。和引导页一样说大白话,
 * 每一项都写清楚打开后 Mika 会做什么、怎么关;说明一直写在开关下面。都默认打开,配好手机操作后 Mika 自己补上。
 */
object RecoveryCopy {
    data class SwitchCopy(val title: String, val subtitle: String, val explain: String)

    fun switch(c: RecoveryCapability): SwitchCopy = when (c) {
        RecoveryCapability.Accessibility -> SwitchCopy(
            "替我打开无障碍",
            "默认打开",
            "Mika 要用「无障碍」读屏幕上的文字和按钮。打开后,Mika 会替你在系统里打开它自己的无障碍服务," +
                "你已经开着的其他无障碍服务不受影响。关掉这里,Mika 会把自己的无障碍服务关掉。",
        )
        RecoveryCapability.BatteryWhitelist -> SwitchCopy(
            "不受省电限制",
            "默认打开",
            "打开后,Mika 会把自己加进系统的「电池优化」例外名单,系统省电时就不会在后台停掉它,手机操作更不容易断开;" +
                "可能会多用一点电。关掉这里就恢复系统默认的省电管理。",
        )
        RecoveryCapability.RestoreAfterReboot -> SwitchCopy(
            "重启后自动恢复",
            "默认打开",
            "手机重启后「无线调试」会自己关掉。打开后,Mika 会在开机时替你重新打开无线调试并自动连上。" +
                "为此 Mika 需要「修改系统设置」的授权,只用来打开无线调试。只在你信任过的 Wi-Fi 上有效。" +
                "关掉这里或解除配对,Mika 都会交还这项授权。",
        )
        RecoveryCapability.AutoWirelessDebugging -> SwitchCopy(
            "自动打开无线调试",
            "默认打开",
            "断开 Wi-Fi 或重启手机后,系统会把「无线调试」关掉。手机操作平时用不着它,只在重新启动时(比如 Mika 更新之后)要它开着。" +
                "打开后,遇到这种时候、又连着 Wi-Fi,Mika 会替你打开无线调试并自动连上,但不会替你关掉它。" +
                "这和「重启后自动恢复」用同一项「修改系统设置」的授权,只在你允许过的 Wi-Fi 上有效。关掉这里,Mika 就不再替你打开。",
        )
    }

    const val NEED_SETUP = "需要先完成手机操作设置"
    const val FAILED = "没能完成,请确认手机操作已就绪后再试"
    const val WORKING = "正在处理…"

    /** 开关拨动后的一行反馈;成功时不多说。 */
    fun result(r: PermissionResult): String? = when (r) {
        PermissionResult.Done -> null
        PermissionResult.NoRunner -> NEED_SETUP
        is PermissionResult.Failed -> FAILED
    }

    /**
     * 默认打开、还没生效的那一项下面说一句:没配好手机操作就提示先完成设置(配好后自动打开),
     * 自动打开没成功就照实说;生效了或用户关掉了不多说。
     */
    fun pending(c: RecoveryCapability, consent: RecoveryConsent, paired: Boolean, autoFailed: Set<RecoveryCapability>): String? = when {
        !consent.wants(c) || consent.isApplied(c) -> null
        !paired -> NEED_SETUP
        c in autoFailed -> FAILED
        else -> null
    }

    /** 「替我打开无障碍」下面,上次自动重启无障碍服务成功的时间;没成功时不说,界面顶上的提示照旧给手动处理的办法。 */
    fun restarted(o: RepairOutcome?, zone: ZoneId = ZoneId.systemDefault()): String? =
        o?.takeIf { it.restarted }?.let { "上次在 ${HH_MM.format(Instant.ofEpochMilli(it.atMillis).atZone(zone))} 自动重启过无障碍服务。" }

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

    /** 「自动打开无线调试」下面,替你打开没成功、先停下的原因;没停下不多说。 */
    fun autoOnBlocked(b: AutoOnBlock?): String? = when (b) {
        null -> null
        AutoOnBlock.NotAllowedHere -> "这个 Wi-Fi 还没允许无线调试,Mika 打不开它。请亲手打开一次,系统问时勾选「始终允许使用此网络」,以后 Mika 就能自己打开。"
        AutoOnBlock.Refused -> "这台手机不让 Mika 打开无线调试。需要时请手动打开,Mika 会自己连上。"
        AutoOnBlock.GaveUp -> "替你打开了几次无线调试,手机操作还是没能启动,先停下了。换个 Wi-Fi 或在手机操作里点重试,Mika 会再试。"
    }

    /** 「重启后自动恢复」下面,上次开机的结果;正常恢复了不多说。 */
    fun lastRestore(o: RestoreOutcome?): String? = when (o) {
        null, RestoreOutcome.Restored -> null
        RestoreOutcome.PermissionMissing -> "上次开机时授权已经不在了,没能自动恢复。请关掉再打开这个开关。"
        RestoreOutcome.SettingUnavailable -> "这台手机不允许 Mika 在开机时打开无线调试。重启后请手动打开,Mika 会自己连上。"
        RestoreOutcome.NotRecovered -> "上次开机没能自动连上。确认连着信任过的 Wi-Fi、无线调试开着,Mika 会自己连上。"
    }
}

/** 接上真实状态的几个开关:选择存在 [com.wangzan101.mika.settings.RecoverySettings],执行走已配对的连接。 */
@Composable
fun RecoverySwitches() {
    val context = LocalContext.current
    val phone = remember { PhoneServices.of(context) }
    val settings = phone.recoverySettings
    val consent by settings.consent.collectAsState()
    val shell by phone.shellControl.status.collectAsState()
    val autoFailed by phone.recovery.permissions.autoFailed.collectAsState()
    val lastRestore by settings.lastRestore.collectAsState()
    val repaired by phone.recovery.accessibilityRepair.last.collectAsState()
    val autoOnBlocked by phone.shellManager.wirelessAutoOn.blocked.collectAsState()
    var busy by remember { mutableStateOf<RecoveryCapability?>(null) }
    var message by remember { mutableStateOf<Pair<RecoveryCapability, String>?>(null) }
    val notes = RecoveryCapability.entries.mapNotNull { cap ->
        val m = message
        (if (m?.first == cap) m.second else RecoveryCopy.pending(cap, consent, shell != BuiltinShellStatus.NotPaired, autoFailed))
            ?.let { cap to it }
    }.toMap()
    RecoverySection(
        consent = consent, busy = busy, notes = notes, lastRestore = lastRestore, restartNote = RecoveryCopy.restarted(repaired),
        autoOnNote = RecoveryCopy.autoOnBlocked(autoOnBlocked),
        onToggle = { cap, on ->
            busy = cap
            message = null
            // 不跟页面走:用户离开设置页时命令也要跑完,选择才和系统一致
            phone.pairing.scope.launch {
                val r = phone.recovery.permissions.set(cap, on)
                message = RecoveryCopy.result(r)?.let { cap to it }
                busy = null
            }
        },
    )
}

/**
 * 不带状态的几个开关,便于测试。开关显示用户要不要这一项(没亲手关掉就是打开),拨动直接执行,不再确认;
 * [notes] 是某一项下面的一行提示(拨动的结果、要先完成设置、自动打开没成功);
 * [restartNote] 是「替我打开无障碍」下面的一行说明(上次自动重启过无障碍服务);
 * [autoOnNote] 是「自动打开无线调试」下面的一行说明(替你打开没成功、先停下了)。
 */
@Composable
fun RecoverySection(
    consent: RecoveryConsent,
    busy: RecoveryCapability?,
    notes: Map<RecoveryCapability, String>,
    lastRestore: RestoreOutcome?,
    onToggle: (RecoveryCapability, Boolean) -> Unit,
    restartNote: String? = null,
    autoOnNote: String? = null,
) {
    GroupCard {
        RecoveryCapability.entries.forEachIndexed { i, cap ->
            val copy = RecoveryCopy.switch(cap)
            val on = consent.wants(cap)
            val icon = iconOf(cap)
            ListRow(icon, copy.title, if (busy == cap) RecoveryCopy.WORKING else copy.subtitle, first = i == 0) {
                MikaSwitch(on, { want -> onToggle(cap, want) }, Modifier.testTag("recovery-${cap.name}"), enabled = busy == null)
            }
            Note(copy.explain, MikaTheme.colors.ink3)
            notes[cap]?.let { Note(it, MikaTheme.colors.bad) }
            if (cap == RecoveryCapability.Accessibility) restartNote?.let { Note(it, MikaTheme.colors.ink3) }
            if (cap == RecoveryCapability.RestoreAfterReboot && consent.isApplied(cap)) {
                RecoveryCopy.lastRestore(lastRestore)?.let { Note(it, MikaTheme.colors.warn) }
            }
            if (cap == RecoveryCapability.AutoWirelessDebugging && consent.wants(cap)) autoOnNote?.let { Note(it, MikaTheme.colors.warn) }
        }
    }
}

private fun iconOf(c: RecoveryCapability): ImageVector = when (c) {
    RecoveryCapability.Accessibility -> MikaIcons.Hand
    RecoveryCapability.BatteryWhitelist -> MikaIcons.Sun
    RecoveryCapability.RestoreAfterReboot -> MikaIcons.Wrench
    RecoveryCapability.AutoWirelessDebugging -> MikaIcons.Refresh
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text, Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 10.dp), style = MikaType.Hint, color = color)
}
