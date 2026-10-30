package com.wangzan101.mika.ui

import com.wangzan101.mika.R
import androidx.compose.ui.res.stringResource
import com.wangzan101.mika.ui.components.keepsKeyboard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.core.shell.BuiltinShellFix
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.core.shell.PairingCode
import com.wangzan101.mika.core.shell.PhoneSetupFlow
import com.wangzan101.mika.core.shell.SetupInputs
import com.wangzan101.mika.core.shell.SetupPhase
import com.wangzan101.mika.core.shell.SetupStep
import com.wangzan101.mika.core.shell.SetupView
import com.wangzan101.mika.core.shell.StepMark
import com.wangzan101.mika.settings.AccessibilityState
import com.wangzan101.mika.settings.SystemStatus
import com.wangzan101.mika.cap.phone.PhoneServices
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.ValueTone
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.cardSurface
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.ProgressDots
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaShapes
import androidx.compose.runtime.setValue

/**
 * 「设置手机操作」引导页:接上实时状态。每一步都按系统开关和助手状态自动判断;
 * 停在配对一步时开始找配对窗口:无障碍开着就由 Mika 自己读配对码,否则发带输入框的通知。
 * [repair] 是从设置页「重新配对」进来的。
 */
@Composable
fun PhoneSetupScreen(status: SystemStatus, repair: Boolean, onFix: (Fix) -> Unit, onboarding: Boolean = false, onClose: () -> Unit) {
    val context = LocalContext.current
    val phone = remember { PhoneServices.of(context) }
    val pairing = phone.pairing
    val discovery by phone.shellControl.pairingDiscovery.collectAsState()
    val shell = status.builtinShell
    var repairStarted by rememberSaveable { mutableStateOf(false) }
    if (repair && !repairStarted) { repairStarted = true; pairing.startRepair() }
    val repairing by pairing.repairing.collectAsState()
    var wirelessAssumed by rememberSaveable { mutableStateOf(false) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    var accessibilitySkipped by rememberSaveable { mutableStateOf(false) }

    val view = PhoneSetupFlow.view(
        SetupInputs(
            developerOptions = status.developerOptions,
            wirelessDebugging = if (wirelessAssumed && !status.wirelessDebugging) null else status.wirelessDebugging,
            status = shell, discovery = discovery, notificationsAllowed = status.notificationsAllowed, repair = repairing,
            usbDebugging = status.usbDebugging, accessibility = status.accessibility == AccessibilityState.On,
            accessibilitySkipped = accessibilitySkipped, restrictedSettings = status.restrictedSettings,
        ),
    )
    // 到了配对一步:开始找配对窗口;通知没允许就在这时请求(配对码要填在通知里)
    LaunchedEffect(view.current) {
        if (view.current == SetupStep.PairingCode) pairing.begin()
    }
    LaunchedEffect(view.needsNotifications) {
        if (view.needsNotifications && !askedNotifications) {
            askedNotifications = true
            onFix(Fix.AllowNotifications)
        }
    }
    LaunchedEffect(view.phase) { if (view.phase == SetupPhase.Success) phone.setupPrompt.clear() }

    val close = { pairing.cancelRepair(); onClose() }
    BackHandler(onBack = close)
    PhoneSetupContent(
        view = view,
        onStepAction = { step ->
            when (step) {
                SetupStep.DeveloperOptions -> onFix(Fix.OpenAboutPhone)
                SetupStep.UsbDebugging -> onFix(Fix.OpenUsbDebugging)
                SetupStep.Accessibility -> onFix(Fix.OpenAccessibility)
                SetupStep.WirelessDebugging -> onFix(Fix.OpenDeveloperOptions)
                SetupStep.PairingCode -> { pairing.begin(); onFix(Fix.OpenWirelessDebugging) }
                SetupStep.Connect -> Unit
            }
        },
        onFailureFix = { fix ->
            when (fix) {
                BuiltinShellFix.Retry -> onFix(Fix.RetryPhoneHelper)
                BuiltinShellFix.RePair -> { pairing.begin(); onFix(Fix.OpenWirelessDebugging) }
                BuiltinShellFix.OpenWifi -> onFix(Fix.OpenWifi)
                BuiltinShellFix.OpenWirelessDebugging -> onFix(Fix.OpenWirelessDebugging)
            }
        },
        onAllowNotifications = { onFix(Fix.AllowNotifications) },
        headsUpLowered = status.notificationsAllowed && (status.attentionLowered || status.resultLowered),
        onOpenHeadsUp = { onFix(if (status.attentionLowered) Fix.OpenAttentionChannel else Fix.OpenResultChannel) },
        onWirelessAlreadyOn = { wirelessAssumed = true },
        onSubmit = { code, port -> pairing.submit(code, port) },
        onClose = close,
        onAppInfo = { onFix(Fix.OpenAppInfo) },
        onSkipAccessibility = { accessibilitySkipped = true },
        onboarding = onboarding,
    )
}

/**
 * 不带状态的引导页,便于测试。步骤是一张编号清单:做完的打勾,当前一步亮起并带按钮,后面的变灰。
 * [onboarding] 为真时是首次上手的最后一步,底部多一排分页点。[onSubmit] 收到的码已经校验过是 6 位数字。
 */
@Composable
fun PhoneSetupContent(
    view: SetupView,
    onStepAction: (SetupStep) -> Unit,
    onFailureFix: (BuiltinShellFix) -> Unit,
    onAllowNotifications: () -> Unit,
    onWirelessAlreadyOn: () -> Unit,
    onSubmit: (code: String, port: Int?) -> Unit,
    onClose: () -> Unit,
    headsUpLowered: Boolean = false,
    onOpenHeadsUp: () -> Unit = {},
    onAppInfo: () -> Unit = {},
    onSkipAccessibility: () -> Unit = {},
    onboarding: Boolean = false,
) {
    val c = MikaTheme.colors
    PageBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            TopBar(PhoneSetupCopy.BAR_TITLE, MikaIcons.Back, onClose, stringResource(R.string.back))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (view.phase == SetupPhase.Success) {
                    Text(
                        PhoneSetupCopy.SUCCESS_LINE, Modifier.padding(top = 4.dp, bottom = 16.dp).testTag("setup-success"),
                        style = MikaType.Body, fontWeight = FontWeight.SemiBold, color = c.ok,
                    )
                } else {
                    Text(
                        PhoneSetupCopy.INTRO, Modifier.padding(top = 4.dp, bottom = 16.dp),
                        fontSize = 14.5.sp, lineHeight = 23.sp, color = c.ink2,
                    )
                }
                GroupCard {
                    SetupStep.entries.forEach { step ->
                        StepRow(step, view.marks.getValue(step), current = step == view.current, first = step.ordinal == 0) {
                            if (step == view.current) CurrentStepBody(
                                step, view, onStepAction, onFailureFix, onAllowNotifications, headsUpLowered, onOpenHeadsUp, onWirelessAlreadyOn, onSubmit, onAppInfo, onSkipAccessibility,
                            )
                            if (step == SetupStep.Connect && view.phase == SetupPhase.Working) WorkingLine()
                        }
                    }
                }
                if (view.phase == SetupPhase.Guide) FooterNote(PhoneSetupCopy.SKIP_NOTE)
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                if (view.phase == SetupPhase.Success) {
                    Text(
                        PhoneSetupCopy.SUCCESS_NOTE, Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        style = MikaType.Caption, color = c.ink3, textAlign = TextAlign.Center,
                    )
                }
                if (onboarding) ProgressDots(Onboarding.DOTS, Onboarding.dot(OnboardingStep.Phone), Modifier.padding(bottom = 8.dp))
                when (view.phase) {
                    SetupPhase.Success -> MikaButton(
                        if (onboarding) PhoneSetupCopy.START_USING else PhoneSetupCopy.SUCCESS_DONE, onClose,
                        Modifier.fillMaxWidth().testTag("setup-done"),
                    )
                    SetupPhase.Guide -> MikaButton(
                        PhoneSetupCopy.LATER, onClose, Modifier.fillMaxWidth().testTag("setup-later"),
                        kind = ButtonKind.Text, size = ButtonSize.Medium,
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: SetupStep, mark: StepMark, current: Boolean, first: Boolean, body: @Composable () -> Unit) {
    val copy = PhoneSetupCopy.step(step)
    val c = MikaTheme.colors
    Box(Modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(c.hair))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepBadge(step.ordinal + 1, mark)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        copy.title, Modifier.weight(1f), style = MikaType.Body,
                        fontWeight = if (mark == StepMark.Current || mark == StepMark.Failed) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (mark == StepMark.Todo || mark == StepMark.Skipped) c.ink3 else c.ink,
                    )
                    if (mark == StepMark.Done) RowValue(copy.done, ValueTone.Ok)
                    if (mark == StepMark.Skipped) RowValue(PhoneSetupCopy.ACCESSIBILITY_SKIPPED)
                }
                if (!current) Text(copy.brief, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = if (mark == StepMark.Todo) c.ink3 else c.ink2)
                body()
            }
        }
    }
}

@Composable
private fun StepBadge(n: Int, mark: StepMark) {
    val c = MikaTheme.colors
    val bg = when (mark) {
        StepMark.Done -> c.ok
        StepMark.Current -> c.accent
        StepMark.Failed -> c.bad
        StepMark.Todo, StepMark.Skipped -> c.greySoft
    }
    Box(Modifier.size(28.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        when (mark) {
            StepMark.Done -> Icon(MikaIcons.Check, null, Modifier.size(15.dp), tint = c.onAccent)
            StepMark.Failed -> Icon(MikaIcons.Exclaim, null, Modifier.size(15.dp), tint = c.onAccent)
            else -> Text("$n", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (mark == StepMark.Todo || mark == StepMark.Skipped) c.ink3 else c.onAccent)
        }
    }
}

@Composable
private fun CurrentStepBody(
    step: SetupStep,
    view: SetupView,
    onStepAction: (SetupStep) -> Unit,
    onFailureFix: (BuiltinShellFix) -> Unit,
    onAllowNotifications: () -> Unit,
    headsUpLowered: Boolean,
    onOpenHeadsUp: () -> Unit,
    onWirelessAlreadyOn: () -> Unit,
    onSubmit: (String, Int?) -> Unit,
    onAppInfo: () -> Unit,
    onSkipAccessibility: () -> Unit,
) {
    val copy = PhoneSetupCopy.step(step)
    Text(copy.body, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = MikaTheme.colors.ink2)
    view.failure?.let { f ->
        val fc = PhoneSetupCopy.failure(f.reason)
        Column(
            Modifier.padding(top = 10.dp).fillMaxWidth().background(MikaTheme.colors.badSoft, RoundedCornerShape(12.dp)).padding(12.dp),
        ) {
            Text(fc.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MikaTheme.colors.bad)
            Text(fc.body, Modifier.padding(top = 2.dp), fontSize = 13.sp, lineHeight = 18.sp, color = MikaTheme.colors.ink2)
        }
        PillButton(PhoneSetupCopy.fixLabel(f.fix), primary = true, tag = "setup-fix") { onFailureFix(f.fix) }
    }
    if (step == SetupStep.PairingCode) {
        if (view.needsNotifications) {
            Text(PhoneSetupCopy.NEED_NOTIFICATIONS, Modifier.padding(top = 8.dp), fontSize = 13.sp, lineHeight = 18.sp, color = MikaTheme.colors.warn)
            PillButton(PhoneSetupCopy.ALLOW_NOTIFICATIONS, primary = false) { onAllowNotifications() }
        }
        if (headsUpLowered && !view.needsNotifications) {
            Column(
                Modifier.padding(top = 10.dp).fillMaxWidth().background(MikaTheme.colors.warnSoft, RoundedCornerShape(12.dp)).padding(12.dp)
                    .testTag("setup-heads-up"),
            ) {
                Text(PhoneSetupCopy.HEADS_UP_LOWERED, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = MikaTheme.colors.warn)
            }
            PillButton(PhoneSetupCopy.OPEN_HEADS_UP, primary = false, tag = "setup-heads-up-open") { onOpenHeadsUp() }
        }
        PairingHint(view.autoRead)
        if (view.portFound && !view.autoRead) Text(PhoneSetupCopy.PORT_FOUND, Modifier.padding(top = 8.dp), fontSize = 13.sp, color = MikaTheme.colors.ok)
    }
    if (view.failure == null && copy.action != null) PillButton(copy.action, primary = true, tag = "setup-action") { onStepAction(step) }
    if (step == SetupStep.Accessibility) {
        if (view.showAppInfo) PillButton(PhoneSetupCopy.APP_INFO, primary = false, tag = "setup-app-info", onClick = onAppInfo)
        LinkText(PhoneSetupCopy.ACCESSIBILITY_SKIP, "setup-skip-a11y", onSkipAccessibility)
    }
    if (step == SetupStep.WirelessDebugging) LinkText(PhoneSetupCopy.WIRELESS_ALREADY_ON, null, onWirelessAlreadyOn)
    if (step == SetupStep.PairingCode) ManualCode(askPort = !view.portFound, onSubmit = onSubmit)
}

@Composable
private fun LinkText(text: String, tag: String?, onClick: () -> Unit) {
    Text(
        text, Modifier.padding(top = 6.dp).clickable(onClick = onClick).padding(vertical = 4.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        fontSize = 13.sp, color = MikaTheme.colors.accent,
    )
}

/** 去系统设置前先说清楚配对码怎么交给 Mika:自己读,或者从顶部下拉到通知里填。 */
@Composable
private fun PairingHint(autoRead: Boolean) {
    val c = MikaTheme.colors
    val tint = if (autoRead) c.ok else c.accent
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().background(if (autoRead) c.okSoft else c.accentSoft, RoundedCornerShape(12.dp))
            .padding(12.dp).testTag("setup-pairing-hint"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (autoRead) MikaIcons.Check else MikaIcons.Bell, null, Modifier.size(20.dp), tint = tint)
        Text(
            if (autoRead) PhoneSetupCopy.AUTO_READ_HINT else PhoneSetupCopy.PULL_DOWN_HINT,
            fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = MikaTheme.colors.ink,
        )
    }
}

/** 通知没弹出来时的兜底:在 App 里直接填码(找不到配对窗口时连带填端口)。 */
@Composable
private fun ManualCode(askPort: Boolean, onSubmit: (String, Int?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Text(
        PhoneSetupCopy.MANUAL_TOGGLE, Modifier.padding(top = 8.dp).clickable { open = !open }.padding(vertical = 4.dp).testTag("setup-manual"),
        fontSize = 13.sp, color = MikaTheme.colors.accent,
    )
    if (!open) return
    Text(PhoneSetupCopy.MANUAL_HINT, fontSize = 12.5.sp, lineHeight = 17.sp, color = MikaTheme.colors.ink3)
    OutlinedTextField(
        code, { code = it; error = null }, Modifier.fillMaxWidth().padding(top = 6.dp).keepsKeyboard().testTag("setup-code"),
        label = { Text(PhoneSetupCopy.CODE_LABEL) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    if (askPort) {
        OutlinedTextField(
            port, { port = it; error = null }, Modifier.fillMaxWidth().padding(top = 6.dp).keepsKeyboard().testTag("setup-port"),
            label = { Text(PhoneSetupCopy.PORT_LABEL) }, supportingText = { Text(PhoneSetupCopy.PORT_HINT) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    error?.let { Text(it, Modifier.padding(top = 4.dp), fontSize = 12.5.sp, color = MikaTheme.colors.bad) }
    PillButton(PhoneSetupCopy.SUBMIT, primary = true, tag = "setup-submit") {
        val c = PairingCode.normalize(code)
        val p = if (askPort && port.isNotBlank()) PairingCode.port(port) else null
        error = when {
            c == null -> PhoneSetupCopy.CODE_INVALID
            askPort && port.isNotBlank() && p == null -> PhoneSetupCopy.PORT_INVALID
            else -> null
        }
        if (error == null && c != null) onSubmit(c, p)
    }
}

@Composable
private fun WorkingLine() {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), color = MikaTheme.colors.accent, strokeWidth = 2.dp)
        Text(PhoneSetupCopy.WORKING, fontSize = 13.5.sp, color = MikaTheme.colors.ink2)
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, tag: String? = null, onClick: () -> Unit) {
    MikaButton(
        text, onClick, Modifier.padding(top = 10.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        kind = if (primary) ButtonKind.Primary else ButtonKind.Tonal, size = ButtonSize.Small,
    )
}

// ---------- 设置页「高级」 ----------

/** 「高级」分组里内置助手的三行:状态、重新配对、解除配对(要确认)。没配对过时不能解除。 */
@Composable
fun PhoneHelperSection(status: BuiltinShellStatus, onRepair: () -> Unit, onUnpair: () -> Unit, usbDebugging: Boolean = true) {
    var confirming by remember { mutableStateOf(false) }
    GroupCard {
        val (value, tone) = PhoneSetupCopy.statusValue(status)
        ListRow(MikaIcons.Phone, PhoneSetupCopy.ROW_TITLE, PhoneSetupCopy.statusLine(status, usbDebugging), first = true) {
            RowValue(value, tone)
        }
        ListRow(MikaIcons.Link, PhoneSetupCopy.REPAIR, PhoneSetupCopy.REPAIR_SUBTITLE, onClick = onRepair) {
            RowChevron()
        }
        val paired = status != BuiltinShellStatus.NotPaired
        ListRow(
            MikaIcons.Close, PhoneSetupCopy.UNPAIR, PhoneSetupCopy.UNPAIR_SUBTITLE,
            titleColor = if (paired) MikaTheme.colors.bad else MikaTheme.colors.ink3, tone = if (paired) TileTone.Bad else TileTone.Neutral, onClick = if (paired) ({ confirming = true }) else null,
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(PhoneSetupCopy.UNPAIR_TITLE) },
            text = { Text(PhoneSetupCopy.UNPAIR_BODY) },
            confirmButton = { TextButton({ confirming = false; onUnpair() }) { Text(PhoneSetupCopy.UNPAIR_CONFIRM, color = MikaTheme.colors.bad) } },
            dismissButton = { TextButton({ confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------- 对话页 ----------

/** 任务要用手机而手机操作没准备好时,任务卡片下面的一行。 */
@Composable
fun PhoneSetupLine(onOpen: () -> Unit) {
    val shape = MikaShapes.Card
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).cardSurface(MikaTheme.colors, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(PhoneSetupCopy.DEGRADED_LINE, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, color = MikaTheme.colors.ink2)
        Text(
            PhoneSetupCopy.DEGRADED_ACTION, Modifier.clickable(onClick = onOpen).padding(4.dp).testTag("phone-setup-open"),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MikaTheme.colors.accent,
        )
    }
}
