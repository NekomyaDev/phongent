package com.wangzan101.mika.ui

import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.settings.ModelOption
import com.wangzan101.mika.settings.Provider
import com.wangzan101.mika.settings.ProviderConfig
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.RoleChoice
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.Hairline
import com.wangzan101.mika.ui.components.InputRow
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.PillTone
import com.wangzan101.mika.ui.components.RadioRow
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.SearchField
import com.wangzan101.mika.ui.components.StatusPill
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.components.ValueTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaRadius
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/** 设置 › 模型:和上手时的配置模型是同一页([ModelSetupScreen]),不在引导流程里。 */
@Composable
fun ModelsScreen(store: ProviderStore, onBack: () -> Unit) {
    ModelSetupScreen(store, onboarding = false, onBack = onBack, onDone = onBack)
}

/** 子页面共用的骨架:顶栏 + 可滚动内容。 */
@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    PageBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).windowInsetsPadding(WindowInsets.ime)) {
            TopBar(title, MikaIcons.Back, onBack, "返回")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                content()
            }
        }
    }
}

/** 用途卡:大图标块、「名字 · 做什么」、选了什么(模型名用等宽字)、说明。 */
@Composable
internal fun RoleCard(icon: ImageVector, label: String, line: ModelsCopy.RoleLine, first: Boolean = false, tag: String, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Column {
        if (!first) Hairline(start = 0.dp)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).background(c.neutralSoft, RoleTileShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(21.dp), tint = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.5.sp, lineHeight = 17.sp, color = c.ink3)
                Text(
                    line.title, Modifier.padding(top = 1.dp), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = if (line.isModel) FontFamily.Monospace else null, color = if (line.unset) c.ink3 else c.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(line.subtitle, Modifier.padding(top = 1.dp), style = MikaType.Caption, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RowChevron()
        }
    }
}

private val RoleTileShape = RoundedCornerShape(12.dp)

/** 服务商一行:左边是名字首字的图标块。 */
@Composable
internal fun ProviderRow(p: Provider, line: String, first: Boolean, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Column {
        if (!first) Hairline()
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("provider-${p.name}").padding(horizontal = 16.dp).padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(MikaSize.Tile).background(c.neutralSoft, MikaShapes.Tile), contentAlignment = Alignment.Center) {
                Text(p.name.take(1).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MikaType.Body, color = c.ink, maxLines = 1)
                Text(line, Modifier.padding(top = 2.dp), style = MikaType.Caption, color = c.ink3)
            }
            RowChevron()
        }
    }
}

// ---------- 选某个用途用哪个模型 ----------

class RolePickerActions(
    /** (服务商 id, 模型名) */
    val pick: (String, String) -> Unit = { _, _ -> },
    /** 看图、轻量的固定选项(用主模型、不用)。 */
    val choose: (RoleChoice) -> Unit = {},
    val addProvider: () -> Unit = {},
)

/** 选择页顶上的固定选项:看图「用主模型」「不用」,轻量「用主模型」;主模型没有。 */
internal fun fixedChoices(role: ModelRole): List<RoleChoice> = when (role) {
    ModelRole.Main -> emptyList()
    ModelRole.Vision -> listOf(RoleChoice.SameAsMain, RoleChoice.Off)
    ModelRole.Light -> listOf(RoleChoice.SameAsMain)
}

/**
 * 选用途:看图、轻量顶上是固定选项;下面按服务商分组列出所有已连接服务商的全部模型(用记住的列表),
 * 标签只作参考,不按能力过滤或置灰;服务不提供列表的那家可以手动填模型名。点一下就生效并返回。
 */
@Composable
fun RolePickerContent(ui: ModelSetupUi, role: ModelRole, actions: RolePickerActions, onBack: () -> Unit) {
    val c = MikaTheme.colors
    var filter by rememberSaveable { mutableStateOf("") }
    val groups = ui.providers.map { it to ui.options(it) }
    val total = groups.sumOf { it.second.size }
    val q = filter.trim()
    Page(ModelsCopy.roleName(role), onBack) {
        Text(ModelsCopy.roleHint(role), Modifier.padding(top = 4.dp, bottom = 12.dp), fontSize = 14.5.sp, lineHeight = 22.sp, color = c.ink2)
        val fixed = fixedChoices(role)
        if (fixed.isNotEmpty()) {
            GroupCard {
                fixed.forEachIndexed { i, choice ->
                    RadioRow(
                        ModelsCopy.choiceTitle(choice), selected = ui.roles[role] == choice, onClick = { actions.choose(choice) },
                        subtitle = ModelsCopy.choiceNote(role, choice), first = i == 0,
                    )
                }
            }
        }
        if (total > FILTER_FROM) {
            GroupLabel(ModelsCopy.PICK_FROM, Modifier.padding(top = 8.dp))
            Box(Modifier.padding(bottom = 4.dp)) {
                SearchField(filter, { filter = it }, ModelsCopy.FILTER_HINT, Modifier.testTag("model-filter"))
            }
        }
        var anyShown = false
        groups.forEach { (p, options) ->
            val shown = options.filter { q.isEmpty() || it.id.contains(q, ignoreCase = true) }
            val manual = p.models?.isEmpty() == true
            if (q.isNotEmpty() && shown.isEmpty()) return@forEach
            anyShown = true
            val state = ui.listing[p.id]
            GroupLabel(p.name)
            if (shown.isNotEmpty() || manual) {
                GroupCard {
                    shown.take(PICK_AT_MOST).forEachIndexed { i, o ->
                        RadioRow(
                            o.id, selected = ui.picked(role, p.id, o.id), onClick = { actions.pick(p.id, o.id) }, first = i == 0,
                            titleStyle = ModelIdStyle, below = { Tags(o, forVision = role == ModelRole.Vision) },
                        )
                    }
                    if (manual) ManualModel(first = shown.isEmpty()) { actions.pick(p.id, it) }
                }
            }
            when {
                state == ListState.Running -> FooterNote(ModelsCopy.LISTING)
                state is ListState.Failed -> FooterNote(ModelSetupCopy.failed(state.message), color = c.bad)
                shown.size > PICK_AT_MOST -> FooterNote(ModelsCopy.more(shown.size - PICK_AT_MOST))
            }
        }
        if (q.isNotEmpty() && !anyShown) FooterNote(ModelsCopy.NO_MATCH)
        if (ui.providers.isEmpty()) FooterNote(ModelsCopy.ROLE_EMPTY)
        GroupCard(Modifier.padding(top = 8.dp)) {
            ListRow(MikaIcons.Plus, ModelsCopy.ADD_PROVIDER, first = true, titleColor = c.accent, tone = TileTone.Accent, onClick = actions.addProvider)
        }
        FooterNote(ModelsCopy.PICKER_NOTE)
    }
}

/** 服务不提供模型列表时:照服务商文档填模型名。 */
@Composable
private fun ManualModel(first: Boolean, onUse: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    InputRow(ModelsCopy.MODEL_NAME, name, { name = it }, ModelsCopy.MODEL_NAME_HINT, first = first, modifier = Modifier.testTag("manual-model")) {
        Text(
            ModelsCopy.USE, Modifier.clickable(enabled = name.isNotBlank()) { onUse(name.trim()) },
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (name.isNotBlank()) MikaTheme.colors.accent else MikaTheme.colors.ink3,
        )
    }
}

private const val FILTER_FROM = 8
private const val PICK_AT_MOST = 80

/** 服务商详情里最多放几个模型;其余进「全部模型」页。 */
internal const val MODEL_PREVIEW = 3

// ---------- 一个服务商 ----------

class ProviderDetailActions(
    val refresh: () -> Unit = {},
    val changeKey: (String) -> Unit = {},
    val remove: () -> Unit = {},
    /** 把这家的一个模型用作某个用途(「用作」面板里点的)。 */
    val use: (ModelRole, String) -> Unit = { _, _ -> },
    val showAll: () -> Unit = {},
)

/**
 * 一个服务商:连接(状态、接口、地址、Key 可更换)、几个模型(在用的在前,最多 [MODEL_PREVIEW] 个,其余点「查看全部」)、删除。
 * 点一个模型从底部弹出「用作」。页面长度基本固定,模型再多也不会把删除挤到很远。
 */
@Composable
fun ProviderDetailContent(ui: ModelSetupUi, p: Provider, actions: ProviderDetailActions, onBack: () -> Unit) {
    val c = MikaTheme.colors
    var changingKey by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var using by remember { mutableStateOf<ModelOption?>(null) }
    val state = ui.listing[p.id]
    val list = ui.sortedOptions(p)
    Page(p.name, onBack) {
        GroupLabel(ModelsCopy.CONNECTION)
        GroupCard {
            ListRow(null, ModelsCopy.STATUS, first = true) {
                when (state) {
                    ListState.Running -> RowValue(ModelsCopy.LISTING)
                    is ListState.Failed -> RowValue(ModelsCopy.LIST_FAILED, ValueTone.Bad)
                    else -> RowValue(ModelsCopy.CONNECTED, ValueTone.Ok)
                }
            }
            ListRow(null, ModelsCopy.KIND) { RowValue(ModelsCopy.kindLabel(p.kind)) }
            ListRow(null, ModelsCopy.BASE_URL) { RowValue(ModelsCopy.shortUrl(p.baseUrl)) }
            ListRow(null, ModelsCopy.API_KEY, onClick = { changingKey = true }) {
                RowValue(if (p.id in ui.keyed) ModelsCopy.KEY_SAVED else ModelsCopy.KEY_NONE)
                RowValue(ModelsCopy.CHANGE, ValueTone.Link)
            }
        }
        (state as? ListState.Failed)?.let { FooterNote(ModelSetupCopy.failed(it.message), color = c.bad) }

        GroupLabel(ModelsCopy.MODELS)
        if (list.isNotEmpty()) {
            GroupCard {
                list.take(MODEL_PREVIEW).forEachIndexed { i, o -> ModelRow(o, ui.rolesOf(p.id, o.id), first = i == 0) { using = o } }
                if (list.size > MODEL_PREVIEW) {
                    ListRow(null, ModelsCopy.viewAll(list.size), titleColor = c.accent, onClick = actions.showAll) { RowChevron() }
                }
            }
            FooterNote(ModelsCopy.MODELS_NOTE)
        }
        when {
            p.models?.isEmpty() == true -> FooterNote(ModelsCopy.NO_LIST_NOTE)
            p.models == null && list.isEmpty() && state != ListState.Running -> FooterNote(ModelsCopy.NOT_LISTED)
        }

        GroupCard(Modifier.padding(top = 8.dp)) {
            ListRow(MikaIcons.Trash, ModelsCopy.DELETE, titleColor = c.bad, tone = TileTone.Bad, first = true, onClick = { confirmDelete = true })
        }
    }

    using?.let { o -> UseAsSheet(ui, p, o.id, onUse = { role -> actions.use(role, o.id) }, onDismiss = { using = null }) }
    if (changingKey) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changingKey = false },
            title = { Text(ModelsCopy.CHANGE_KEY) },
            text = {
                KeyboardDismissArea {
                    Box(Modifier.fillMaxWidth().keepsKeyboard().background(c.fill, MikaShapes.Button).padding(12.dp)) {
                        if (key.isEmpty()) Text(ModelsCopy.NEW_KEY_HINT, style = MikaType.Body, color = c.ink3)
                        BasicTextField(
                            key, { key = it }, Modifier.fillMaxWidth().testTag("new-key"), singleLine = true,
                            textStyle = MikaType.Body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton({
                    changingKey = false
                    if (key.isNotBlank()) actions.changeKey(key)
                }) { Text(ModelsCopy.SAVE) }
            },
            dismissButton = { TextButton({ changingKey = false }) { Text(ModelSetupCopy.CANCEL) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(ModelsCopy.deleteTitle(p.name)) },
            text = { Text(ModelsCopy.deleteBody(ui.rolesOf(p))) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    actions.remove()
                }, Modifier.testTag("confirm-delete")) { Text(ModelsCopy.DELETE_CONFIRM, color = c.bad) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(ModelSetupCopy.CANCEL) } },
        )
    }
}

/**
 * 「<服务商> · 全部模型」:搜索框固定在顶栏下面,列表只在下面滚(模型多时按需排版);右上角刷新重新列一次。
 * 点模型同样弹出「用作」。
 */
@Composable
fun AllModelsContent(ui: ModelSetupUi, p: Provider, actions: ProviderDetailActions, onBack: () -> Unit) {
    val c = MikaTheme.colors
    var filter by rememberSaveable { mutableStateOf("") }
    var using by remember { mutableStateOf<ModelOption?>(null) }
    val state = ui.listing[p.id]
    val all = ui.sortedOptions(p)
    val q = filter.trim()
    val shown = all.filter { q.isEmpty() || it.id.contains(q, ignoreCase = true) }
    PageBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).windowInsetsPadding(WindowInsets.ime)) {
            TopBar(ModelsCopy.allModelsTitle(p.name), MikaIcons.Back, onBack, "返回", right = {
                BarIconButton(MikaIcons.Refresh, ModelsCopy.REFRESH, actions.refresh, enabled = state != ListState.Running)
            })
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
                SearchField(filter, { filter = it }, ModelsCopy.FILTER_HINT, Modifier.testTag("all-models-filter"))
            }
            when (state) {
                ListState.Running -> FooterNote(ModelsCopy.LISTING)
                is ListState.Failed -> FooterNote(ModelSetupCopy.failed(state.message), color = c.bad)
                else -> Unit
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                itemsIndexed(shown, key = { _, o -> o.id }) { i, o ->
                    Box(Modifier.fillMaxWidth().clip(rowShape(i, shown.size)).background(c.surface)) {
                        ModelRow(o, ui.rolesOf(p.id, o.id), first = i == 0) { using = o }
                    }
                }
                item {
                    FooterNote(
                        if (q.isNotEmpty() && shown.isEmpty()) ModelsCopy.NO_MATCH else ModelsCopy.allModelsNote(all.size),
                        Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
    using?.let { o -> UseAsSheet(ui, p, o.id, onUse = { role -> actions.use(role, o.id) }, onDismiss = { using = null }) }
}

/** 一张卡拆成懒加载的行时,每行的圆角:首行上圆、末行下圆。 */
private fun rowShape(i: Int, n: Int): RoundedCornerShape {
    val r = MikaRadius.Card
    return RoundedCornerShape(topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp,
        bottomStart = if (i == n - 1) r else 0.dp, bottomEnd = if (i == n - 1) r else 0.dp)
}

/** 模型一行:模型名(等宽)和标签,右边标着它在给哪些用途干活。 */
@Composable
private fun ModelRow(o: ModelOption, roles: List<ModelRole>, first: Boolean, onClick: () -> Unit) {
    Column {
        if (!first) Hairline(start = 16.dp)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("model-${o.id}").padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(o.id, style = ModelIdStyle, color = MikaTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 4.dp)) { Tags(o, accent = false) }
            }
            roles.forEach { StatusPill(ModelsCopy.roleName(it), PillTone.Accent) }
            RowChevron()
        }
    }
}

/** 点一个模型后从底部升起:「<服务商> · 用作」主模型 / 看图模型 / 轻量模型,点了马上生效。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UseAsSheet(ui: ModelSetupUi, p: Provider, model: String, onUse: (ModelRole) -> Unit, onDismiss: () -> Unit) {
    val c = MikaTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp).testTag("use-as")) {
            Text(model, Modifier.padding(horizontal = 16.dp), style = ModelIdStyle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(ModelsCopy.useAsSubtitle(p.name), Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp), style = MikaType.Caption, color = c.ink3)
            ModelRole.entries.forEachIndexed { i, role ->
                val on = ui.picked(role, p.id, model)
                RadioRow(
                    ModelsCopy.roleName(role), selected = on, first = i == 0,
                    subtitle = if (on) ModelsCopy.IN_USE else ModelsCopy.roleWhat(role),
                    onClick = { onUse(role); onDismiss() },
                )
            }
            ListRow(null, ModelSetupCopy.CANCEL, titleColor = c.ink2, onClick = onDismiss)
        }
    }
}

/** 模型页(用途、选择页、服务商详情、全部模型)的文字(纯函数,便于测试)。三个用途的说法全 App 只在这里。 */
object ModelsCopy {
    const val TITLE = "模型"
    const val LEAD = "每件事用哪个模型，在「用途」里选；模型来自下面连好的服务商。"
    const val ROLES = "用途"
    const val PROVIDERS = "服务商"
    const val ADD_PROVIDER = "添加服务商"
    const val KEY_NOTE = "API Key 只保存在这台手机上，加密存放。"
    const val UNSET = "未设置"
    const val MAIN_ADD_FIRST = "先添加一个服务商"
    const val MAIN_PICK = "点这里选一个"
    const val SAME_AS_MAIN = "用主模型"
    const val MAIN_NOT_PICKED = "主模型还没选"
    const val OFF = "不用"
    const val OFF_NOTE = "只靠文字读屏"
    const val VISION_SAME_NOTE = "主模型能看图时选这个"
    const val LIGHT_SAME_NOTE = "和主模型同一个，不用另外选"
    const val PICK_FROM = "从已连接的服务商里选"
    const val FILTER_HINT = "输入模型名的一部分"
    const val NO_MATCH = "没有名字对得上的模型。"
    const val MODEL_NAME = "模型名"
    const val MODEL_NAME_HINT = "这家不提供列表，照文档填"
    const val USE = "使用"
    const val ROLE_EMPTY = "还没有可选的模型。先添加一个服务商。"
    const val PICKER_NOTE = "标签是服务商给的信息，只作参考；选哪个由你定。"
    const val LISTING = "正在列出模型…"
    const val CONNECTION = "连接"
    const val STATUS = "状态"
    const val CONNECTED = "已连接"
    const val LIST_FAILED = "列模型失败"
    const val KIND = "接口"
    const val BASE_URL = "地址"
    const val API_KEY = "API Key"
    const val KEY_SAVED = "已保存"
    const val KEY_NONE = "没有填"
    const val CHANGE = "更换"
    const val CHANGE_KEY = "更换 API Key"
    const val NEW_KEY_HINT = "粘贴新的 Key"
    const val SAVE = "保存"
    const val MODELS = "模型"
    const val MODELS_NOTE = "点一个模型，可以把它用作主模型、看图或轻量模型。"
    const val NO_LIST_NOTE = "这家不提供模型列表。要用它的模型，在「用途」里照文档填模型名。"
    const val NOT_LISTED = "还没列出这家的模型。"
    const val REFRESH = "刷新"
    const val IN_USE = "正在用这个"
    const val DELETE = "删除这个服务商"
    const val DELETE_CONFIRM = "删除"

    /** 用途卡上的一行:[title] 是模型名([isModel])、「用主模型」「不用」或「未设置」([unset]),[subtitle] 是补充说明。 */
    data class RoleLine(val title: String, val subtitle: String, val isModel: Boolean = false, val unset: Boolean = false)

    fun roleName(role: ModelRole) = when (role) {
        ModelRole.Main -> "主模型"
        ModelRole.Vision -> "看图模型"
        ModelRole.Light -> "轻量模型"
    }

    /** 这个用途做什么。 */
    fun roleWhat(role: ModelRole) = when (role) {
        ModelRole.Main -> "思考和办事"
        ModelRole.Vision -> "读屏时看截图"
        ModelRole.Light -> "判断、记东西这类小事"
    }

    fun roleLabel(role: ModelRole) = "${roleName(role)} · ${roleWhat(role)}"

    /** 选择页顶上的说明。 */
    fun roleHint(role: ModelRole) = when (role) {
        ModelRole.Main -> "思考和办事都靠它。选支持工具调用的模型，办事才靠得住。"
        ModelRole.Vision -> "读屏时把截图给它看，界面复杂时更准。不选也能用，只靠文字读屏。"
        ModelRole.Light -> "判断任务有没有办完、整理记忆这类小事用它。选便宜、快的更合适。"
    }

    fun roleIcon(role: ModelRole): ImageVector = when (role) {
        ModelRole.Main -> MikaIcons.Spark
        ModelRole.Vision -> MikaIcons.Eye
        ModelRole.Light -> MikaIcons.Leaf
    }

    /** 用途卡上写什么:选了模型写模型名和服务商;「用主模型」写主模型是谁;「不用」写只靠文字读屏。 */
    fun roleLine(ui: ModelSetupUi, role: ModelRole): RoleLine {
        fun modelLine(cfg: ProviderConfig) = RoleLine(cfg.model, ui.providerOf(cfg)?.name ?: cfg.name, isModel = true)
        val main = ui.configFor(ModelRole.Main)
        return when (val choice = ui.roles[role]) {
            null -> RoleLine(UNSET, if (ui.providers.isEmpty()) MAIN_ADD_FIRST else MAIN_PICK, unset = true)
            RoleChoice.SameAsMain -> RoleLine(SAME_AS_MAIN, main?.model ?: MAIN_NOT_PICKED)
            RoleChoice.Off -> RoleLine(OFF, OFF_NOTE)
            is RoleChoice.Model -> ui.configs.firstOrNull { it.id == choice.configId }?.let(::modelLine)
                ?: RoleLine(UNSET, MAIN_PICK, unset = true)
        }
    }

    fun choiceTitle(choice: RoleChoice) = if (choice == RoleChoice.Off) OFF else SAME_AS_MAIN

    fun choiceNote(role: ModelRole, choice: RoleChoice) = when {
        choice == RoleChoice.Off -> OFF_NOTE
        role == ModelRole.Vision -> VISION_SAME_NOTE
        else -> LIGHT_SAME_NOTE
    }

    fun roleNames(roles: List<ModelRole>) = roles.joinToString("、") { roleName(it) }

    fun deleteTitle(name: String) = "删除「$name」？"

    /** 删除前说清会影响哪些用途。 */
    fun deleteBody(roles: List<ModelRole>) =
        "这家的 API Key 会一起删掉。" + if (roles.isEmpty()) "它没在给任何用途干活。" else "${roleNames(roles)}正在用它，删除后要重新选。"

    fun more(n: Int) = "还有 $n 个，输入名字筛选"

    fun viewAll(n: Int) = "查看全部 $n 个模型"

    fun allModelsTitle(name: String) = "$name · 全部模型"

    fun allModelsNote(n: Int) = "共 $n 个，在用的排在最前面。右上角刷新会重新向服务商要一次列表。"

    fun useAsSubtitle(name: String) = "$name · 用作"

    fun kindLabel(kind: ProviderConfig.Kind) = when (kind) {
        ProviderConfig.Kind.OpenAiCompat -> "OpenAI 兼容"
        ProviderConfig.Kind.Anthropic -> "Anthropic"
        ProviderConfig.Kind.Gemini -> "Gemini"
    }

    fun shortUrl(url: String) = url.removePrefix("https://").removePrefix("http://")

    /** 记住的模型数;还没列过时为空,服务不提供列表时说明。 */
    fun modelCount(p: Provider): String? = when {
        p.models == null -> null
        p.models.isEmpty() -> "不提供模型列表"
        else -> "${p.models.size} 个模型"
    }

    /** 服务商一行的说明:已连接 · N 个模型 · 哪些用途在用;刷新失败时说明。 */
    fun providerLine(p: Provider, state: ListState?, roles: List<ModelRole>): String =
        if (state is ListState.Failed) LIST_FAILED
        else listOfNotNull(CONNECTED, modelCount(p), roles.takeIf { it.isNotEmpty() }?.let { "${roleNames(it)}在用" }).joinToString(" · ")
}
