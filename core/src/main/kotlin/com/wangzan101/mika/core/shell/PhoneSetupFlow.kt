package com.wangzan101.mika.core.shell

/** 引导的六步,按顺序。 */
enum class SetupStep { DeveloperOptions, UsbDebugging, Accessibility, WirelessDebugging, PairingCode, Connect }

/** [Skipped]:用户跳过了这一步(只有无障碍能跳过)。 */
enum class StepMark { Done, Current, Todo, Failed, Skipped }

enum class SetupPhase {
    /** 等用户做某一步。 */
    Guide,
    /** 正在配对、连接、启动:显示进度。 */
    Working,
    Success,
    /** 出错:[SetupView.failure] 给原因和修复。 */
    Failed,
}

/**
 * 引导页的输入:系统设置里读到的开关和助手的状态。
 * [wirelessDebugging] 为 null 表示这台手机读不到这个开关(不按原版写设置项):不拦着,当作开着往下走。
 * [repair] 是用户在设置页点了「重新配对」:即便已在运行,也回到配对那一步,直到新的配对开始。
 * [usbDebugging] 开着时,Wi-Fi 断开、无线调试被系统关掉后助手也不会停。
 * [accessibility] 是 Mika 的无障碍服务在运行:配对时由它读出配对码;用户可以跳过([accessibilitySkipped]),退回在通知里填。
 * [restrictedSettings] 是系统可能拦着不让打开无障碍(没从应用商店装的 App 要先在应用信息里允许受限制的设置)。
 */
data class SetupInputs(
    val developerOptions: Boolean,
    val wirelessDebugging: Boolean?,
    val status: BuiltinShellStatus,
    val discovery: PairingDiscovery = PairingDiscovery.Idle,
    val notificationsAllowed: Boolean = true,
    val repair: Boolean = false,
    val usbDebugging: Boolean = true,
    val accessibility: Boolean = true,
    val accessibilitySkipped: Boolean = false,
    val restrictedSettings: Boolean = false,
)

data class SetupView(
    val phase: SetupPhase,
    val marks: Map<SetupStep, StepMark>,
    /** 此刻该做的一步(Guide 和 Failed 时);Working/Success 时为 null。 */
    val current: SetupStep?,
    val failure: BuiltinShellStatus.Failed? = null,
    /** 配对窗口已经找到:提示用户去通知里填码。 */
    val portFound: Boolean = false,
    /** 停在配对一步但通知没允许:配对码要填在通知里,先请求通知权限。 */
    val needsNotifications: Boolean = false,
    /** 配对一步时无障碍开着:Mika 自己读配对码,不用去通知里填。 */
    val autoRead: Boolean = false,
    /** 无障碍一步时给出「去应用信息允许受限制的设置」的入口。 */
    val showAppInfo: Boolean = false,
)

/**
 * 引导页的状态机:每次输入变化(系统设置开关、助手状态、找端口的进度)都重新算一遍,不存中间状态。
 * 先看系统开关,再看助手:开发者选项、USB 调试或无线调试被关掉时,不管助手之前怎样,都退回那一步。
 */
object PhoneSetupFlow {
    fun view(i: SetupInputs): SetupView {
        val status = effectiveStatus(i.status, i.repair)
        if (status == BuiltinShellStatus.Running) return SetupView(SetupPhase.Success, marks(SetupStep.entries.size, i), null)
        if (!i.developerOptions) return guide(SetupStep.DeveloperOptions, i)
        if (!i.usbDebugging) return guide(SetupStep.UsbDebugging, i)
        if (!i.accessibility && !i.accessibilitySkipped) return guide(SetupStep.Accessibility, i)
        // 读不到开关时以助手的说法为准
        val wirelessOff = i.wirelessDebugging == false || (i.wirelessDebugging == null && status == BuiltinShellStatus.WirelessDebuggingOff)
        if (wirelessOff) return guide(SetupStep.WirelessDebugging, i)
        return when (status) {
            BuiltinShellStatus.Running -> error("handled above")
            // 配对过、无线调试开着而状态还没跟上:实现马上会自动连,先显示「正在连接」
            BuiltinShellStatus.Connecting, BuiltinShellStatus.WirelessDebuggingOff ->
                SetupView(SetupPhase.Working, marks(SetupStep.Connect.ordinal, i, current = SetupStep.Connect), null)
            BuiltinShellStatus.NotPaired -> guide(SetupStep.PairingCode, i)
            is BuiltinShellStatus.Failed -> {
                val step = if (status.reason.duringPairing || status.fix == BuiltinShellFix.RePair) SetupStep.PairingCode else SetupStep.Connect
                SetupView(
                    SetupPhase.Failed, marks(step.ordinal, i, current = step, mark = StepMark.Failed), step, failure = status,
                    portFound = step == SetupStep.PairingCode && i.discovery is PairingDiscovery.Found,
                    needsNotifications = step == SetupStep.PairingCode && !i.notificationsAllowed,
                    autoRead = step == SetupStep.PairingCode && i.accessibility,
                )
            }
        }
    }

    private fun guide(step: SetupStep, i: SetupInputs) = SetupView(
        SetupPhase.Guide, marks(step.ordinal, i, current = step), step,
        portFound = step == SetupStep.PairingCode && i.discovery is PairingDiscovery.Found,
        needsNotifications = step == SetupStep.PairingCode && !i.notificationsAllowed,
        autoRead = step == SetupStep.PairingCode && i.accessibility,
        showAppInfo = step == SetupStep.Accessibility && i.restrictedSettings,
    )

    /** 前 [done] 步做完(跳过的无障碍记成跳过),[current] 这一步打 [mark],其余待做。 */
    private fun marks(done: Int, i: SetupInputs, current: SetupStep? = null, mark: StepMark = StepMark.Current): Map<SetupStep, StepMark> =
        SetupStep.entries.associateWith { s ->
            when {
                s.ordinal < done && s == SetupStep.Accessibility && !i.accessibility -> StepMark.Skipped
                s.ordinal < done -> StepMark.Done
                s == current -> mark
                else -> StepMark.Todo
            }
        }

    /** 「重新配对」时,配对开始([BuiltinShellStatus.Connecting])之前助手在不在运行都当作还没配对。 */
    fun effectiveStatus(status: BuiltinShellStatus, repair: Boolean): BuiltinShellStatus =
        if (repair && status !is BuiltinShellStatus.Connecting) BuiltinShellStatus.NotPaired else status
}
