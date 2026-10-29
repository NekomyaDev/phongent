package com.wangzan101.mika.ui

import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellFix
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.core.shell.SetupStep
import com.wangzan101.mika.ui.components.ValueTone

/**
 * 「手机操作」引导、设置页和通知上的文字(纯函数,便于测试)。
 * 用大白话:只出现手机设置里本来就有的词(开发者选项、USB 调试、无线调试、配对码),不说 adb、端口、shell 这类说法。
 */
object PhoneSetupCopy {
    const val TITLE = "设置手机操作"
    const val INTRO = "Mika 在一块单独的虚拟屏上替你操作 App,你可以照常用手机。为此要借用安卓自带的「USB 调试」和「无线调试」:" +
        "配对一次就行,之后 Mika 会自动连接,不需要电脑。网页和文件类的事不受影响,不设置也能做。"
    const val LATER = "稍后再说"
    /** 引导页顶栏上的短标题。 */
    const val BAR_TITLE = "手机操作"
    const val SKIP_NOTE = "也可以先跳过,只用上网、文件和聊天;第一次要操作 App 时会再提醒。"

    /** [brief] 是清单里没轮到、或已做完时那一行的短说明;轮到时显示完整的 [body]。 */
    data class StepCopy(val title: String, val body: String, val action: String?, val done: String, val brief: String)

    fun step(s: SetupStep): StepCopy = when (s) {
        SetupStep.DeveloperOptions -> StepCopy(
            "打开开发者选项",
            "打开手机设置里的「关于手机」,连续点「版本号」7 次,看到「你已处于开发者模式」的提示就好。" +
                "有的手机「版本号」在「软件信息」里面。",
            "打开关于手机", "已打开", "在「关于手机」里连点「版本号」7 次",
        )
        SetupStep.UsbDebugging -> StepCopy(
            "打开 USB 调试",
            "在「开发者选项」里找到「USB 调试」并打开,弹出提示时选「确定」。" +
                "不需要插数据线:打开后,断开 Wi-Fi 时 Mika 也能继续操作手机。",
            "打开开发者选项", "已打开", "断开 Wi-Fi 时也能继续操作",
        )
        SetupStep.Accessibility -> StepCopy(
            "打开 Mika 的无障碍",
            "在系统的「无障碍」设置里找到 Mika 并打开。打开后,配对时 Mika 会自己读出配对码,不用手动填。",
            "去无障碍设置", "已打开", "配对时由 Mika 自己读出配对码",
        )
        SetupStep.WirelessDebugging -> StepCopy(
            "打开无线调试",
            "在「开发者选项」里找到「无线调试」并打开。问你是否允许时选「允许」,勾上「始终允许这个网络」以后就不用再点。" +
                "需要连着 Wi-Fi(不用能上网,连上就行)。",
            "打开开发者选项", "已打开", "连着 Wi-Fi 就行,不需要能上网",
        )
        SetupStep.PairingCode -> StepCopy(
            "用配对码配对",
            "点进「无线调试」,再点「使用配对码配对设备」,屏幕上会出现 6 位配对码。",
            "去无线调试", "已配对", "填 6 位配对码,只要配一次",
        )
        SetupStep.Connect -> StepCopy(
            "自动连接",
            "配对好后,Mika 会自己连上并准备好,几秒钟就好。",
            null, "已连接", "配对好后自己连上",
        )
    }

    const val ACCESSIBILITY_SKIP = "跳过,配对时手动填"
    const val ACCESSIBILITY_SKIPPED = "已跳过"
    /** 系统拦着不让打开无障碍时(没从应用商店装的 App 常见)的去处。 */
    const val APP_INFO = "打不开?先去应用信息里允许受限制的设置"

    /** 配对一步:无障碍开着时 Mika 自己读码;没开时教用户去通知里填。 */
    const val AUTO_READ_HINT = "看到配对码后稍等几秒,Mika 会自己读出来并完成配对,不用离开那个页面。"
    const val PULL_DOWN_HINT = "看到 6 位数字后,从屏幕顶部下拉,点 Mika 通知里的「输入配对码」"

    /** 无线调试一步的兜底:有的手机读不到无线调试开关,用户确认打开了就往下走。 */
    const val WIRELESS_ALREADY_ON = "我已经打开了"
    const val PORT_FOUND = "已找到配对窗口,请在通知里填写配对码。"
    const val NEED_NOTIFICATIONS = "配对码要填在 Mika 的通知里,请先允许 Mika 发通知。"
    const val ALLOW_NOTIFICATIONS = "允许通知"
    /** 通知允许了,但「需要你处理」「任务结果」被调成不弹出:不挡后面的步骤,但要说清楚。 */
    const val HEADS_UP_LOWERED = "「需要你处理」和「任务结果」的通知现在不会弹出，要你接手时容易错过。可以先继续，也可以现在就去打开。"
    const val OPEN_HEADS_UP = "去打开"
    const val MANUAL_TOGGLE = "通知没出来?在这里填"
    const val MANUAL_HINT = "离开配对窗口它就会关掉。可以用分屏,一边看配对码一边在这里填。"
    const val CODE_LABEL = "6 位配对码"
    const val PORT_LABEL = "配对窗口里「IP 地址和端口」冒号后面的数字"
    const val PORT_HINT = "Mika 没找到配对窗口时才需要填"
    const val SUBMIT = "配对"
    const val CODE_INVALID = "要填 6 位数字"
    const val PORT_INVALID = "冒号后面的数字不对"

    const val WORKING = "正在配对并连接,几秒钟就好"
    const val SUCCESS_LINE = "手机操作已经设置好了"
    const val SUCCESS_NOTE = "请保持 USB 调试开启;手机重启后打开无线调试即可自动重连"
    /** 首次上手最后一步成功后的底部按钮;从设置进来的是 [SUCCESS_DONE]。 */
    const val START_USING = "开始使用"
    const val SUCCESS_DONE = "完成"

    data class FailureCopy(val title: String, val body: String)

    fun failure(f: BuiltinShellFailure): FailureCopy = when (f) {
        BuiltinShellFailure.WrongCode -> FailureCopy(
            "配对码不对或已过期",
            "每次打开配对窗口,配对码都会换。请重新点「使用配对码配对设备」,填新的 6 位数字。",
        )
        BuiltinShellFailure.PairingWindowClosed -> FailureCopy(
            "配对窗口关掉了",
            "填配对码时,那个显示配对码的窗口要一直开着。请重新打开它再填一次。",
        )
        BuiltinShellFailure.NoWifi -> FailureCopy(
            "没有连上 Wi-Fi",
            "Mika 要借 Wi-Fi 启动手机操作(不用能上网)。连上任意一个 Wi-Fi 后会自动恢复,不用再点。",
        )
        BuiltinShellFailure.WifiNotAllowed -> FailureCopy(
            "这个 Wi-Fi 还没允许无线调试",
            "Mika 替你打开了无线调试,系统没有同意在这个 Wi-Fi 上使用。请亲手打开一次「无线调试」," +
                "系统问要不要允许时勾选「始终允许使用此网络」再点允许;以后在这个 Wi-Fi 上 Mika 就能自己打开。",
        )
        BuiltinShellFailure.ConnectFailed -> FailureCopy(
            "没能连上无线调试",
            "请确认「无线调试」开着、手机连着 Wi-Fi,然后重试。",
        )
        BuiltinShellFailure.AuthorizationRevoked -> FailureCopy(
            "配对失效了",
            "可能很久没用被系统收回了,或者在系统里删掉了。重新配对一次就好。" +
                "在开发者选项里打开「停用 adb 授权超时功能」,以后就不会再失效。",
        )
        BuiltinShellFailure.HelperStartFailed -> FailureCopy(
            "连上了,但没能准备好",
            "通常重试一次就好。",
        )
        BuiltinShellFailure.Unknown -> FailureCopy(
            "出了点问题",
            "先重试一次;还不行就重新配对。",
        )
    }

    fun fixLabel(fix: BuiltinShellFix): String = when (fix) {
        BuiltinShellFix.Retry -> "重试"
        BuiltinShellFix.RePair -> "重新配对"
        BuiltinShellFix.OpenWifi -> "打开 Wi-Fi 设置"
        BuiltinShellFix.OpenWirelessDebugging -> "去打开无线调试"
    }

    // ---------- 设置页 ----------

    const val RECONNECT = "重新连接"

    /** 已配对、只是没连上或助手掉了:点一下用已配对的钥匙再连一次就行,不必回引导页。 */
    fun canReconnect(s: BuiltinShellStatus): Boolean = s is BuiltinShellStatus.Failed && s.fix == BuiltinShellFix.Retry

    const val ROW_TITLE = "手机操作"
    const val ROW_SUBTITLE = "借用无线调试,在虚拟屏上操作 App"

    /** 设置 › 手机操作页底部的完整说明。 */
    const val USAGE_NOTE = "配对一次之后,Mika 的内置助手会常驻后台,断开 Wi-Fi 也能继续操作手机。" +
        "「USB 调试」(在开发者选项里)要一直开着,关掉后手机操作会停下。" +
        "手机重启后,连上 Wi-Fi 并打开「无线调试」,Mika 会自己重新连上,不用再输配对码。"

    fun statusValue(s: BuiltinShellStatus): Pair<String, ValueTone> = when (s) {
        BuiltinShellStatus.Running -> "已就绪" to ValueTone.Ok
        BuiltinShellStatus.NotPaired -> "未设置" to ValueTone.Bad
        BuiltinShellStatus.WirelessDebuggingOff -> "无线调试已关" to ValueTone.Bad
        BuiltinShellStatus.Connecting -> "正在连接" to ValueTone.Plain
        is BuiltinShellStatus.Failed -> "出错了" to ValueTone.Bad
    }

    /** 「高级」里状态那一行的说明。[usbDebugging] 关着时,在运行也提醒一句:断了 Wi-Fi 就会停。 */
    fun statusLine(s: BuiltinShellStatus, usbDebugging: Boolean = true): String = when (s) {
        BuiltinShellStatus.Running ->
            if (usbDebugging) "已连接,能操作手机 App" else "已连接;Wi-Fi 断开后会停止,打开「USB 调试」就不会"
        BuiltinShellStatus.NotPaired -> "还没配对,操作手机 App 前要设置一次"
        BuiltinShellStatus.WirelessDebuggingOff -> "已配对;打开无线调试后会自动连上"
        BuiltinShellStatus.Connecting -> "正在连接"
        is BuiltinShellStatus.Failed -> when (s.reason) {
            BuiltinShellFailure.NoWifi -> NO_WIFI_LINE
            BuiltinShellFailure.WifiNotAllowed -> failure(s.reason).title + ";" + WIFI_NOT_ALLOWED_LINE
            else -> failure(s.reason).title
        }
    }

    const val NO_WIFI_LINE = "没有 Wi-Fi;连上任意 Wi-Fi 后会自动恢复"
    const val WIFI_NOT_ALLOWED_LINE = "亲手打开一次无线调试,勾选「始终允许使用此网络」"

    const val REPAIR = "重新配对"
    const val REPAIR_SUBTITLE = "连不上、或换了手机设置时用"
    const val UNPAIR = "解除配对"
    const val UNPAIR_SUBTITLE = "删除配对,停止手机操作"
    const val UNPAIR_TITLE = "解除配对?"
    const val UNPAIR_BODY = "Mika 会删掉配对信息,停止在虚拟屏上操作 App,并收回下面那几项开关给过的授权。网页和文件类的事不受影响。" +
        "以后要操作手机 App,需要重新配对一次。你也可以到系统的「无线调试 → 已配对的设备」里把 Mika 删掉。"
    const val UNPAIR_CONFIRM = "解除"

    // ---------- 对话页(手机操作不可用时) ----------

    const val DEGRADED_LINE = "手机操作还没准备好,这次没法操作手机 App;网页和文件照常。"
    const val DEGRADED_ACTION = "去设置"

    // ---------- 通知 ----------

    const val CHANNEL_NAME = "配对"
    const val NOTIFY_ENTER_TITLE = "Mika:下拉这里,输入配对码"
    const val NOTIFY_ENTER_TEXT = "点「输入配对码」,填上屏幕上显示的 6 位数字"
    const val NOTIFY_ACTION = "输入配对码"
    const val NOTIFY_INPUT_LABEL = "填写 6 位数字"
    const val NOTIFY_WORKING = "正在配对…"
    const val NOTIFY_DONE_TITLE = "手机操作设置好了"
    const val NOTIFY_DONE_TEXT = "可以回到 Mika 了"
    const val NOTIFY_PROBLEM_TEXT = "回到 Mika 查看怎么处理"
}
