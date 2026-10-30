package com.wangzan101.mika.ui

/** 对话页上按状态算出来的文字;不碰界面,单测直接测。 */
object ChatCopy {
    /** 空对话的问候,按时段。 */
    fun greeting(hour: Int): String = when (hour) {
        in 5..10 -> "早上好"
        in 11..12 -> "中午好"
        in 13..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }

    const val EMPTY_PROMPT = "想让 Mika 做什么？"

    /** 空对话里的示例;点一下填进输入框,用户改好了再发。[kind] 决定配哪个线条图标。 */
    enum class ExampleKind { App, Web, File, Schedule, Device }
    data class Example(val kind: ExampleKind, val text: String)

    val examples = listOf(
        Example(ExampleKind.App, "打开微信，给王赞发消息，让他拉我进Mika内测体验群"),
        Example(ExampleKind.App, "帮我总结微信群聊，Mika内测体验群今天都聊了些啥"),
        Example(ExampleKind.Web, "在Boss直聘上帮我投递AI产品经理岗位，150个投满"),
        Example(ExampleKind.App, "帮我打开汽水音乐，看广告领取24小时听歌VIP会员"),
        Example(ExampleKind.Device, "帮我订明天早上9:40、9:50、10:00 的闹钟"),
        Example(ExampleKind.App, "打开小红书，帮我总结一下博主王赞不赞的内容"),
    )

    /** 对话页顶栏的标题:固定是 App 名;副标题说此刻在做什么。 */
    const val APP_TITLE = "Mika"

    /** 对话的名字:第一句话(只取第一行,附件那几行不算;只发了屏幕时是「你此刻的屏幕」);空对话为空。侧栏里用它称呼对话。 */
    fun title(exchanges: List<Exchange>): String =
        exchanges.firstOrNull { it.goal.isNotBlank() }?.goal?.let(::plainTitle).orEmpty()

    /**
     * 一句用户的话当标题怎么写:去掉附件那几行(只发了附件时是「照片」「合同.pdf」之类)和屏幕那一行(只发了屏幕时是「你此刻的屏幕」),
     * 只取第一行。
     */
    fun plainTitle(text: String): String =
        ScreenCopy.plain(AttachmentMessage.plain(text)).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    enum class Tone { Accent, Warn }

    /** 顶栏副标题:正在做什么(强调色)或等你(橙色);没有任务在跑时为 null。[place] 是正在操作的 App 或网站。 */
    fun subtitle(ex: Exchange?, place: String?): Pair<String, Tone>? = when (ex?.status) {
        null -> null
        ExchangeStatus.Working -> (if (place.isNullOrBlank()) "正在处理" else "正在操作$place") to Tone.Accent
        ExchangeStatus.WaitingUnlock -> "等你解锁手机" to Tone.Warn
        is ExchangeStatus.NeedsTakeover, is ExchangeStatus.NeedsInput,
        is ExchangeStatus.NeedsConfirmation, is ExchangeStatus.Paused -> "等你处理" to Tone.Warn
        else -> null
    }

    /** 进度那一行:在跑时是此刻的进展,停下来等你时是「已暂停」。 */
    fun progressLine(ex: Exchange): String =
        if (paused(ex.status)) "已暂停" else liveLine(ex) ?: "正在处理"

    /** 任务停下来等你(要你处理、回答、同意、解锁、点继续)。 */
    fun paused(status: ExchangeStatus): Boolean =
        status is ExchangeStatus.NeedsTakeover || status is ExchangeStatus.NeedsInput ||
            status is ExchangeStatus.NeedsConfirmation || status is ExchangeStatus.Paused || status == ExchangeStatus.WaitingUnlock

    /** 进度行右边的用时:「0:24」「12:05」「1:02:03」。 */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** 在跑时步骤只列最近这几步,前面的收成一行「前面还有 n 步」,点开看全部。 */
    const val VISIBLE_STEPS = 5

    fun earlierSteps(n: Int) = "前面还有 $n 步"

    /** 在后台操作手机时步骤下面那行小字。 */
    const val BACKGROUND_NOTE = "在后台进行，可以继续用手机。点小画面可以放大看。"

    fun placeholder(mode: ComposerMode, hasHistory: Boolean): String = when (mode) {
        ComposerMode.Busy -> "补充或改要求…"
        ComposerMode.Answer -> "回答 Mika 的问题"
        ComposerMode.Idle -> if (hasHistory) "继续说点什么…" else EMPTY_PROMPT
    }
}
