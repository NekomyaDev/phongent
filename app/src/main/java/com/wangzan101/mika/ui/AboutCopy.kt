package com.wangzan101.mika.ui

import com.wangzan101.mika.about.CheckStatus
import com.wangzan101.mika.about.MikaLinks
import com.wangzan101.mika.about.UpdateState

/** 关于页的文字,以及「检查更新」一行、设置首页「关于 Mika」一行、侧栏入口上的提示怎么显示。 */
object AboutCopy {
    const val TITLE = "关于 Mika"
    const val TAGLINE = "你手机上的私人助理，替你把事办成"
    const val BETA = "Beta"

    const val CHECK = "检查更新"
    const val CHECK_SUB = "从 GitHub 发布页获取新版本"
    const val CHECK_SUB_NEW = "有新版本"
    const val UP_TO_DATE = "已是最新"
    const val UP_TO_DATE_TOAST = "已是最新版本"
    const val OFFLINE = "连不上 GitHub"
    const val OFFLINE_TOAST = "暂时连不上 GitHub，稍后再试"
    const val CHANGELOG = "更新记录"
    const val CHANGELOG_SUB = "每个版本改了什么"
    const val CHANGELOG_EMPTY = "还没取到更新记录，连上网后点「检查更新」"
    const val CHANGELOG_ALL = "在 GitHub 上看全部"

    const val FEEDBACK = "反馈问题"
    const val FEEDBACK_SUB = "GitHub、微信、X 都可以"
    const val FEEDBACK_SHEET_SUB = "遇到问题、有想法，选一个方便的地方告诉作者"
    const val FEEDBACK_GITHUB = "去 GitHub 提问题"
    const val FEEDBACK_GITHUB_SUB = "报 Bug、提需求，能跟踪进度"
    const val FEEDBACK_WECHAT = "加作者微信"
    const val FEEDBACK_WECHAT_SUB = "进内测群，聊用法、快速反馈"
    const val FEEDBACK_X = "在 X 上找作者"
    const val FEEDBACK_X_SUB = "${MikaLinks.X_HANDLE} · 私信或发帖 @ 都行"

    const val GROUP = "加入内测群"
    const val GROUP_SUB = "加作者微信，拉你进 Mika 内测体验群"
    const val GROUP_SHEET_TITLE = "加入 Mika 内测体验群"
    const val GROUP_SHEET_SUB = "扫码加作者微信，备注「Mika」，拉你进群"
    const val GROUP_HINT = "长按图片保存 · 或在微信里扫一扫"
    const val SAVE_QR = "保存二维码"
    const val QR_SAVED = "二维码已保存到相册"
    const val QR_SAVE_FAILED = "没能保存，可以截屏保存"

    const val FOLLOW = "关注作者"
    const val FOLLOW_SUB = "X ${MikaLinks.X_HANDLE}，第一时间看新功能"
    const val REPO = "开源仓库"
    const val REPO_SUB = "${MikaLinks.REPO_LABEL} · 欢迎点星"
    const val GUIDE = "使用说明与参与方式"
    const val GUIDE_SUB = "上手指南；不会写代码也能补充 App 知识"

    const val DATA_NOTE = "你发给 Mika 的话、屏幕内容和截图，只发给你自己设置的模型服务商。" +
        "Mika 没有自己的服务器，不收集你的数据；检查更新时只访问 GitHub。"
    const val LICENSE_LINK = "Apache-2.0 开源许可"
    const val AUTHOR = "由 王赞 开发 · © 2026"
    const val LICENSE_TITLE = "Apache-2.0 许可证"

    const val UPDATE_DOWNLOAD = "从 GitHub 下载"
    const val UPDATE_MIRROR = "国内备用下载"
    const val UPDATE_LATER = "以后再说"
    const val NO_BROWSER = "没有能打开链接的浏览器"

    fun version(versionName: String): String = "版本 $versionName"

    fun buildLabel(build: String): String = "构建 $build"

    fun available(version: String): String = "$version 可更新"
    fun updateTitle(version: String): String = "发现新版本 $version"

    /** 「当前 0.1.0 Beta · 2026-11-05 发布」;不知道发布日期时只写当前版本。 */
    fun updateSub(current: String, beta: Boolean, date: String?): String =
        "当前 $current${if (beta) " $BETA" else ""}" + (date?.let { " · $it 发布" } ?: "")

    /** 「检查更新」一行右边:正在查转圈,有新版写版本号带红点,查过一次写结果;从没查过什么都不写。 */
    data class CheckValue(val text: String?, val dot: Boolean = false, val spinner: Boolean = false, val highlight: Boolean = false)

    fun checkValue(s: UpdateState): CheckValue = when {
        s.status == CheckStatus.Checking -> CheckValue(null, spinner = true)
        s.update != null -> CheckValue(available(s.update.version), dot = true, highlight = true)
        s.status == CheckStatus.Failed -> CheckValue(OFFLINE)
        s.latest != null -> CheckValue(UP_TO_DATE)
        else -> CheckValue(null)
    }

    /** 设置首页「关于 Mika」一行右边:有新版写「x 可更新」带红点,不然写当前版本号。 */
    fun settingsValue(updateVersion: String?, versionName: String): CheckValue =
        if (updateVersion != null) CheckValue(available(updateVersion), dot = true, highlight = true)
        else CheckValue(versionName.takeIf { it.isNotEmpty() })

    /** 侧栏「关于 Mika」上的红点。 */
    fun drawerDot(s: UpdateState): Boolean = s.hasUpdate
}
