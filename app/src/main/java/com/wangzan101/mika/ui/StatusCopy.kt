package com.wangzan101.mika.ui

import com.wangzan101.mika.core.agent.RunProgress
import com.wangzan101.mika.settings.AccessibilityState
import com.wangzan101.mika.settings.ChannelLevel
import com.wangzan101.mika.settings.ChannelRow
import com.wangzan101.mika.settings.NotificationSummary
import com.wangzan101.mika.settings.Problem
import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellFix
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.ui.components.ValueTone

/** 设置页能直接做的修复动作。 */
enum class Fix {
    OpenAccessibility, AllowNotifications, AddModel,
    /** 直接打开「需要你处理」/「任务结果」/「任务进度」频道的设置页,打开「在屏幕上弹出」;打不开退回应用通知总页。 */
    OpenAttentionChannel, OpenResultChannel, OpenProgressChannel,
    /** 本 App 的「应用信息」页:系统拦着不让开无障碍时,在这里允许受限制的设置。 */
    OpenAppInfo,
    // 内置助手
    OpenPhoneSetup, RepairPhoneHelper, RetryPhoneHelper, UnpairPhoneHelper,
    OpenAboutPhone, OpenDeveloperOptions, OpenUsbDebugging, OpenWirelessDebugging, OpenWifi,
}

/** 顶部横幅的一行字和右侧链接。 */
data class BannerCopy(val title: String, val detail: String, val link: String)

/** 点开问题时的说明:用大白话讲清楚是什么、怎么办;[fix] 为空表示没有一键修复。 */
data class Explanation(val title: String, val body: String, val action: String?, val fix: Fix?)

object StatusCopy {
    /** 不经模型连做几步时卡片和通知上的说法。 */
    fun progress(p: RunProgress): String = when (p) {
        is RunProgress.Replay -> "按以往的做法快速执行中(第 ${p.step}/${p.total} 步)"
        is RunProgress.Batch -> "一次执行 ${p.steps} 步"
    }

    /** 付款接管的商品、金额、地址,一行一项;都没有时为 null。卡片和通知共用。 */
    fun paymentLines(item: String?, amount: String?, address: String?): String? =
        listOfNotNull(
            item?.takeIf { it.isNotBlank() }?.let { "商品:$it" },
            amount?.takeIf { it.isNotBlank() }?.let { "金额:$it" },
            address?.takeIf { it.isNotBlank() }?.let { "地址:$it" },
        ).joinToString("\n").ifEmpty { null }

    fun accessibilityValue(s: AccessibilityState): Pair<String, ValueTone> = when (s) {
        AccessibilityState.On -> "已开启" to ValueTone.Ok
        AccessibilityState.Off -> "未开启" to ValueTone.Bad
        AccessibilityState.EnabledNotRunning -> "未运行" to ValueTone.Bad
    }

    /** 不用说明、点了直接修的问题。 */
    fun directFix(p: Problem): Fix? = when (p) {
        Problem.NotificationsOff -> Fix.AllowNotifications
        is Problem.AttentionLowered -> headsUpFix(p)
        Problem.NoModel -> Fix.AddModel
        // 引导页把每一步都讲清楚了,不再弹说明
        is Problem.PhoneHelper -> when {
            PhoneSetupCopy.canReconnect(p.status) -> Fix.RetryPhoneHelper
            // 这个 Wi-Fi 还没允许无线调试:直接去亲手打开一次(系统会问要不要允许)
            (p.status as? BuiltinShellStatus.Failed)?.fix == BuiltinShellFix.OpenWirelessDebugging -> Fix.OpenWirelessDebugging
            else -> Fix.OpenPhoneSetup
        }
        else -> null
    }

    // ---- 设置 › 通知 ----
    /** 停止之后、交代还没写好时那一行小字(对话页和悬浮卡片一样)。 */
    const val STOP_SUMMARIZING = "正在整理已做的事…"
    const val NOTIFICATIONS = "通知"
    const val PROGRESS_CHANNEL = "任务进度"

    fun notificationSummary(s: NotificationSummary): Pair<String, ValueTone> = when (s) {
        NotificationSummary.Normal -> "正常" to ValueTone.Ok
        is NotificationSummary.Lowered -> "有 ${s.count} 项不会弹出" to ValueTone.Warn
        NotificationSummary.AllOff -> "已关闭" to ValueTone.Bad
    }

    fun channelLevelValue(row: ChannelRow): Pair<String, ValueTone> = when (row.level) {
        ChannelLevel.Off -> "已关闭" to if (row.warn) ValueTone.Warn else ValueTone.Plain
        ChannelLevel.HeadsUp -> "会弹出" to ValueTone.Ok
        ChannelLevel.Quiet -> "只进通知栏" to if (row.warn) ValueTone.Warn else ValueTone.Plain
    }

    const val NOTIF_APP_SUBTITLE_ON = "允许 Mika 发通知"
    const val NOTIF_APP_SUBTITLE_OFF = "现在不允许 Mika 发通知，进度和提醒都看不到"
    const val NOTIF_ATTENTION_WHEN = "需要你接管、确认、回答时"
    const val NOTIF_RESULT_WHEN = "任务做完时"
    const val NOTIF_PROGRESS_WHEN = "任务进行中，一直显示在通知栏"
    const val NOTIF_FOOTER =
        "每类通知是否弹出、是否响铃，安卓规定只能由你在系统设置里改。点上面任意一项会直接打开它的系统设置页。" +
            "路径：系统设置 › 应用 › Mika › 通知"

    const val ATTENTION_CHANNEL = "需要你处理"
    const val RESULT_CHANNEL = "任务结果"
    const val HEADS_UP_ACTION = "去打开"

    /** 被调低的两个频道里先处理「需要你处理」。 */
    fun headsUpFix(p: Problem.AttentionLowered): Fix = if (p.attention) Fix.OpenAttentionChannel else Fix.OpenResultChannel

    private fun headsUpTitle(p: Problem.AttentionLowered) =
        "「${if (p.attention) ATTENTION_CHANNEL else RESULT_CHANNEL}」的通知不会弹出"

    private fun headsUpBody(p: Problem.AttentionLowered): String {
        val lead = if (p.attention) "要你接手或同意时" else "任务做完时"
        val also = if (p.attention && p.result) "「$RESULT_CHANNEL」的通知也一样。" else ""
        return "$lead，这类通知现在只安静地进通知栏，容易错过。${also}点「$HEADS_UP_ACTION」，打开「在屏幕上弹出」。"
    }

    private fun headsUpPath(p: Problem.AttentionLowered) =
        "系统设置 › 应用 › Mika › 通知 › ${if (p.attention) ATTENTION_CHANNEL else RESULT_CHANNEL} › 在屏幕上弹出（有的系统叫「横幅」「悬浮通知」）"

    fun banner(p: Problem): BannerCopy = when (p) {
        Problem.SystemTooOld -> BannerCopy("系统版本太低", "Mika 需要安卓 13 及以上", "了解")
        is Problem.PhoneHelper -> when (val st = p.status) {
            BuiltinShellStatus.NotPaired -> BannerCopy("手机操作还没设置", "设置后 Mika 才能操作手机 App", "去设置")
            BuiltinShellStatus.WirelessDebuggingOff -> BannerCopy("无线调试关着", "打开后 Mika 会自动连上", "去打开")
            BuiltinShellStatus.Connecting -> BannerCopy("正在连接手机操作", "稍等几秒", "查看")
            is BuiltinShellStatus.Failed ->
                if (st.reason == BuiltinShellFailure.NoWifi) BannerCopy("手机操作暂停了", PhoneSetupCopy.NO_WIFI_LINE, "查看")
                else if (st.reason == BuiltinShellFailure.WifiNotAllowed) BannerCopy(
                    PhoneSetupCopy.failure(st.reason).title, PhoneSetupCopy.WIFI_NOT_ALLOWED_LINE, "去打开",
                )
                else BannerCopy(
                    "手机操作出了问题", PhoneSetupCopy.failure(st.reason).title,
                    if (PhoneSetupCopy.canReconnect(st)) PhoneSetupCopy.RECONNECT else "去处理",
                )
            BuiltinShellStatus.Running -> error("not a problem")
        }
        is Problem.Accessibility -> when (p.state) {
            AccessibilityState.Off -> BannerCopy("无障碍服务未开启", "Mika 要用它读取和操作虚拟屏", "去开启")
            AccessibilityState.EnabledNotRunning -> BannerCopy("无障碍服务没有运行", "需要关掉再打开一次", "去处理")
            AccessibilityState.On -> error("not a problem")
        }
        Problem.NoModel -> BannerCopy("还没有能用的模型", "添加一个支持工具调用的模型", "添加")
        Problem.NotificationsOff -> BannerCopy("通知未允许", "会看不到进度和「需要你」提醒", "允许")
        is Problem.AttentionLowered -> BannerCopy(headsUpTitle(p), headsUpBody(p) + "\n" + headsUpPath(p), HEADS_UP_ACTION)
        Problem.FocusMayLeak -> BannerCopy("安卓 13 上后台操作可能影响主屏焦点", "建议安卓 14+", "了解")
    }

    fun explain(p: Problem, androidRelease: String): Explanation = when (p) {
        Problem.SystemTooOld -> Explanation(
            "需要安卓 13 及以上",
            "Mika 在一块单独的虚拟屏上操作 App，这样你可以照常用手机。这项能力从安卓 13 开始才有，" +
                "这台手机是安卓 $androidRelease，所以暂时不能开始任务。",
            null, null,
        )
        is Problem.PhoneHelper -> Explanation(
            PhoneSetupCopy.TITLE, PhoneSetupCopy.INTRO, PhoneSetupCopy.DEGRADED_ACTION, Fix.OpenPhoneSetup,
        )
        is Problem.Accessibility -> when (p.state) {
            AccessibilityState.Off -> Explanation(
                "打开无障碍服务",
                "Mika 用无障碍服务读取和操作它自己的虚拟屏。在系统设置的「无障碍」里找到 Mika" +
                    "（有的手机放在「已下载的应用」或「已安装的服务」里），把它打开。",
                "去设置", Fix.OpenAccessibility,
            )
            AccessibilityState.EnabledNotRunning -> Explanation(
                "无障碍服务没有运行",
                "系统设置里显示 Mika 的无障碍服务是开着的，但它实际没有在运行（Mika 被强行停止后常会这样）。" +
                    "请到无障碍设置里把 Mika 关掉，再重新打开。",
                "去设置", Fix.OpenAccessibility,
            )
            AccessibilityState.On -> error("not a problem")
        }
        Problem.NoModel -> Explanation(
            "添加模型", "Mika 需要一个支持工具调用的大模型来思考和操作。填上服务商的地址、模型名和 API Key 即可。",
            "添加", Fix.AddModel,
        )
        Problem.FocusMayLeak -> Explanation(
            "建议安卓 14 及以上",
            "安卓 13 上后台操作可能影响主屏焦点：Mika 在虚拟屏上打开 App 或翻页时，你正在主屏输入的地方可能短暂失去焦点，" +
                "需要再点一下。安卓 14 起虚拟屏有自己的焦点，不会这样。这台手机是安卓 $androidRelease，仍然可以正常开始任务。",
            null, null,
        )
        is Problem.AttentionLowered -> Explanation(
            headsUpTitle(p), headsUpBody(p) + "\n\n" + headsUpPath(p), HEADS_UP_ACTION, headsUpFix(p),
        )
        Problem.NotificationsOff -> Explanation(
            "允许通知", "允许后，任务进度和「需要你处理」的提醒会出现在通知栏和锁屏上。不允许也能用，只是看不到这些提醒。",
            "允许", Fix.AllowNotifications,
        )
    }

    /**
     * 任务卡片上跑命令这一步的说明:「正在处理文件:<命令前 40 字>」,联网时注明「可联网」。
     * 运行环境对用户不可见,字面上不说沙箱。
     */
    fun sandboxCommand(command: String, network: Boolean): String = sandboxLine("正在处理文件:", command, 40, network)

    /** 跑技能脚本时:「正在运行脚本」。 */
    fun sandboxScript(network: Boolean): String = "正在运行脚本" + if (network) " · $SANDBOX_ONLINE" else ""

    /** 命令里 `mika progress` 报的进度。 */
    fun sandboxProgress(text: String, network: Boolean): String = sandboxLine("", text, 60, network)

    const val SANDBOX_ONLINE = "可联网"

    /** 工具在等运行环境准备好时。 */
    const val RUNTIME_PREPARING = "正在准备"

    private fun sandboxLine(prefix: String, text: String, max: Int, network: Boolean): String {
        // 和 `mika progress` 一样去掉控制字符、零宽与改写方向的字符,卡片上显示的就是实际的命令
        val one = com.wangzan101.mika.cap.sandbox.BridgeProtocol.cleanProgress(text.replace(Regex("\\s+"), " "))
        val cut = if (one.codePointCount(0, one.length) > max) one.substring(0, one.offsetByCodePoints(0, max)) + "…" else one
        return prefix + cut + if (network) " · $SANDBOX_ONLINE" else ""
    }
}
