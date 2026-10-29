package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.settings.ModelOption
import com.wangzan101.mika.settings.ModelOptions
import com.wangzan101.mika.settings.ProviderConfig
import com.wangzan101.mika.settings.ProviderPresets
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.CapabilityTag
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.InputRow
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.ProgressDots
import com.wangzan101.mika.ui.components.SelectChip
import com.wangzan101.mika.ui.components.TagTone
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/**
 * 配置模型:用途(主模型、看图模型、轻量模型)和服务商两块。首次上手时 [onboarding] 为真
 * (服务商在前,底部有分页点和「下一步」);设置 › 模型([ModelsScreen])也是这一页,标题和引导语不同,没有底部按钮。
 * 选用途、服务商详情、服务商的全部模型是这一页里的子页面。上手时「下一步」选好主模型才可点,点了调 [onDone]。
 */
@Composable
fun ModelSetupScreen(store: ProviderStore, onboarding: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val vm = remember { ModelSetupViewModel(store, scope) }
    val ui by vm.state.collectAsState()
    var picking by rememberSaveable { mutableStateOf<ModelRole?>(null) }
    var openProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var allModels by rememberSaveable { mutableStateOf(false) }

    val provider = openProvider?.let { id -> ui.providers.firstOrNull { it.id == id } }
    if (provider != null) {
        LaunchedEffect(provider.id) { vm.ensureListed() }
        val actions = ProviderDetailActions(
            refresh = { vm.refresh(provider.id) }, changeKey = { vm.changeKey(provider.id, it) },
            remove = { vm.removeProvider(provider.id); openProvider = null; allModels = false },
            use = { role, model -> vm.pick(role, provider.id, model) },
            showAll = { allModels = true },
        )
        if (allModels) {
            BackHandler { allModels = false }
            AllModelsContent(ui, provider, actions, onBack = { allModels = false })
        } else {
            BackHandler { openProvider = null }
            ProviderDetailContent(ui, provider, actions, onBack = { openProvider = null })
        }
        return
    }
    picking?.let { role ->
        BackHandler { picking = null }
        LaunchedEffect(role) { vm.ensureListed() }
        RolePickerContent(
            ui, role,
            RolePickerActions(
                pick = { pid, model -> vm.pick(role, pid, model); picking = null },
                choose = { vm.choose(role, it); picking = null },
                addProvider = { vm.openForm(); picking = null },
            ),
            onBack = { picking = null },
        )
        return
    }

    BackHandler(onBack = onBack)
    ModelSetupContent(
        ui, onboarding,
        ModelSetupActions(
            selectPreset = vm::selectPreset, setKind = vm::setKind, setBaseUrl = vm::setBaseUrl, setApiKey = vm::setApiKey,
            testAndSave = vm::testAndSave, openForm = vm::openForm, closeForm = vm::closeForm,
            openProvider = { openProvider = it; allModels = false }, pickRole = { picking = it }, next = onDone,
        ),
        onBack,
    )
}

/** 主页面上的全部动作。 */
class ModelSetupActions(
    val selectPreset: (String) -> Unit = {},
    val setKind: (ProviderConfig.Kind) -> Unit = {},
    val setBaseUrl: (String) -> Unit = {},
    val setApiKey: (String) -> Unit = {},
    val testAndSave: () -> Unit = {},
    val openForm: () -> Unit = {},
    val closeForm: () -> Unit = {},
    val openProvider: (String) -> Unit = {},
    val pickRole: (ModelRole) -> Unit = {},
    val next: () -> Unit = {},
)

/**
 * 不带状态的主页面,便于测试。改动当场生效(测试通过就存下服务商,用途选完就用),
 * 所以设置里进来只有返回;上手时底部留「下一步」,键盘弹出时藏起来,把地方让给表单。
 */
@Composable
fun ModelSetupContent(ui: ModelSetupUi, onboarding: Boolean, actions: ModelSetupActions, onBack: () -> Unit) {
    val c = MikaTheme.colors
    // 表单收起(测试通过存好了,或取消)后页面上没有要打字的地方,顺手收起键盘
    val focus = LocalFocusManager.current
    LaunchedEffect(ui.showForm) { if (!ui.showForm) focus.clearFocus() }
    val onPick: (ModelRole) -> Unit = { role -> if (ui.providers.isEmpty()) actions.openForm() else actions.pickRole(role) }
    PageBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).windowInsetsPadding(WindowInsets.ime)) {
            TopBar(if (onboarding) ModelSetupCopy.TITLE else ModelsCopy.TITLE, MikaIcons.Back, onBack, "返回")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text(
                    if (onboarding) ModelSetupCopy.LEAD else ModelsCopy.LEAD, Modifier.padding(top = 4.dp, bottom = 12.dp),
                    fontSize = 14.5.sp, lineHeight = 23.sp, color = c.ink2,
                )
                // 上手时先连服务商再选模型;设置里先看用途
                if (onboarding) {
                    ProvidersSection(ui, actions)
                    RolesSection(ui, onPick)
                    FooterNote(ModelSetupCopy.ONBOARDING_NOTE)
                } else {
                    RolesSection(ui, onPick)
                    ProvidersSection(ui, actions)
                }
                Spacer(Modifier.height(16.dp))
            }
            // 看键盘实际占的高度;isImeVisible 在系统第一次报告前默认是 true,会把按钮误藏
            if (onboarding && WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                    ProgressDots(Onboarding.DOTS, Onboarding.dot(OnboardingStep.Model), Modifier.padding(bottom = 14.dp))
                    MikaButton(ModelSetupCopy.NEXT, actions.next, Modifier.fillMaxWidth().testTag("setup-next"), enabled = ui.canNext)
                }
            }
        }
    }
}

/** 服务商:每家一行(写明在给哪些用途干活),加「添加服务商」;没有服务商或点了添加时,表单就摊在这里。 */
@Composable
private fun ProvidersSection(ui: ModelSetupUi, actions: ModelSetupActions) {
    val c = MikaTheme.colors
    if (ui.providers.isNotEmpty()) {
        GroupLabel(ModelsCopy.PROVIDERS)
        GroupCard {
            ui.providers.forEachIndexed { i, p ->
                ProviderRow(p, ModelsCopy.providerLine(p, ui.listing[p.id], ui.rolesOf(p)), first = i == 0) { actions.openProvider(p.id) }
            }
            if (!ui.showForm) {
                ListRow(
                    MikaIcons.Plus, ModelsCopy.ADD_PROVIDER, titleColor = c.accent, tone = TileTone.Accent,
                    onClick = actions.openForm,
                )
            }
        }
    }
    if (ui.showForm) AddProviderForm(ui, actions)
    FooterNote(ModelsCopy.KEY_NOTE)
}

/** 添加服务商:预设胶囊 → (自定义时)接口类型和地址 → API Key →「测试并保存」。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddProviderForm(ui: ModelSetupUi, actions: ModelSetupActions) {
    val c = MikaTheme.colors
    val f = ui.form
    var showKey by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GroupLabel(ModelsCopy.ADD_PROVIDER, Modifier.weight(1f))
        if (ui.providers.isNotEmpty()) {
            Text(
                ModelSetupCopy.CANCEL, Modifier.clickable(onClick = actions.closeForm).padding(horizontal = 4.dp, vertical = 6.dp),
                fontSize = 13.sp, color = c.accent,
            )
        }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderPresets.all.forEach { p ->
            SelectChip(p.label, f.preset == p.label, { actions.selectPreset(p.label) }, Modifier.testTag("preset-${p.label}"))
        }
    }
    GroupCard {
        if (f.isCustom) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ModelSetupCopy.KIND, Modifier.padding(end = 12.dp), style = MikaType.Body, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderConfig.Kind.entries.forEach { k -> SelectChip(ModelsCopy.kindLabel(k), f.kind == k, { actions.setKind(k) }, small = true) }
                }
            }
            InputRow(
                ModelSetupCopy.BASE_URL, f.baseUrl, actions.setBaseUrl, "https://…",
                keyboard = KeyboardType.Uri, modifier = Modifier.testTag("setup-base-url"),
            )
        }
        InputRow(
            ModelSetupCopy.API_KEY, f.apiKey, actions.setApiKey,
            if (f.keyRequired) ModelSetupCopy.KEY_HINT else ModelSetupCopy.KEY_OPTIONAL, first = !f.isCustom,
            keyboard = KeyboardType.Password, secret = !showKey, modifier = Modifier.testTag("setup-api-key"),
        ) {
            Text(if (showKey) "隐藏" else "显示", Modifier.clickable { showKey = !showKey }, fontSize = 13.sp, color = c.accent)
        }
    }
    f.urlProblem?.takeIf { f.isCustom && f.baseUrl.isNotBlank() }?.let { FooterNote(it, color = c.bad) }
    MikaButton(
        if (f.test == ListState.Running) ModelSetupCopy.TESTING else ModelSetupCopy.TEST_AND_SAVE,
        actions.testAndSave, Modifier.fillMaxWidth().padding(top = 2.dp).testTag("setup-test"),
        kind = ButtonKind.Secondary, size = ButtonSize.Medium, enabled = f.canTest,
    )
    (f.test as? ListState.Failed)?.let { failed ->
        Text(
            ModelSetupCopy.failed(failed.message), Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).testTag("setup-test-failed"),
            fontSize = 13.sp, lineHeight = 18.sp, color = c.bad,
        )
    }
    FooterNote(ModelSetupCopy.TEST_NOTE, Modifier.padding(top = 6.dp))
}

/** 用途:主模型、看图模型、轻量模型各一张卡,点开是选择页。 */
@Composable
private fun RolesSection(ui: ModelSetupUi, onPick: (ModelRole) -> Unit) {
    GroupLabel(ModelsCopy.ROLES)
    GroupCard {
        ModelRole.entries.forEachIndexed { i, role ->
            RoleCard(ModelsCopy.roleIcon(role), ModelsCopy.roleLabel(role), ModelsCopy.roleLine(ui, role), first = i == 0, tag = "role-${role.name.lowercase()}") {
                onPick(role)
            }
        }
    }
}

/** 模型的能力标签;[accent] 为假时一律中性色(列表行里强调色留给「在用」的胶囊)。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Tags(o: ModelOption, forVision: Boolean = false, accent: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ModelSetupCopy.tags(o, forVision).forEach { (text, strong) -> CapabilityTag(text, if (strong && accent) TagTone.Accent else TagTone.Neutral) }
    }
}

internal val ModelIdStyle = MikaType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)

/** 配置模型页(上手时)和添加服务商表单的文字(纯函数,便于测试)。用途的说法在 [ModelsCopy]。 */
object ModelSetupCopy {
    const val TITLE = "配置模型"
    const val LEAD = "Mika 用你自己的模型服务来思考。先连一家服务商，再给每件事选模型。"
    const val ONBOARDING_NOTE = "只有主模型必须选，另外两个可以以后再说。"
    const val CANCEL = "取消"
    const val KIND = "接口"
    const val BASE_URL = "接口地址"
    const val API_KEY = "API Key"
    const val KEY_HINT = "粘贴服务商给的 Key"
    const val KEY_OPTIONAL = "本地服务可不填"
    const val TEST_AND_SAVE = "测试并保存"
    const val TESTING = "正在测试…"
    const val TEST_NOTE = "只列一次这家的模型，列得出来就保存。"
    const val NEXT = "下一步"

    fun failed(reason: String) = "连接失败：$reason"

    /** 模型的能力标签:(文字, 是否强调色)。接口没给、按预设推断的标「未确认」。只作参考,不拦选择。 */
    fun tags(o: ModelOption, forVision: Boolean = false): List<Pair<String, Boolean>> = buildList {
        val caps = o.capabilities
        if (forVision) {
            add((if (o.visionKnown) "看图" else "看图 · 未确认") to false)
        } else {
            when {
                !caps.toolCalls -> add("不支持工具调用" to false)
                o.toolCallsKnown -> add("工具调用" to true)
                else -> add("工具调用 · 未确认" to false)
            }
            if (caps.vision && o.visionKnown) add("看图" to false)
        }
        if (caps.reasoning) add("推理" to false)
        if (o.contextKnown) add(ModelOptions.contextLabel(caps.contextTokens) to false)
    }
}
