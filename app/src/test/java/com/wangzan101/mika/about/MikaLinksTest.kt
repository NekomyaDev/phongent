package com.wangzan101.mika.about

import org.junit.Rule
import com.wangzan101.mika.i18n.ChineseTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class MikaLinksTest {
    @get:Rule val texts = ChineseTexts()

    private val device = MikaLinks.Device("Google", "Pixel 8", "14", 34, "0.1.0", "65fcf92")

    @Test
    fun bodyHasThreePrefilledLines() {
        val lines = MikaLinks.issueBody(device).lines()
        assertEquals("- 机型：Google Pixel 8", lines[0])
        assertEquals("- 安卓版本：14（SDK 34）", lines[1])
        assertEquals("- Mika 版本：0.1.0（构建 65fcf92）", lines[2])
        assertTrue(lines.contains("### 遇到了什么问题"))
        assertTrue(lines.contains("### 做了哪几步"))
        assertTrue(lines.contains("### 期望是什么样"))
    }

    /** 型号里已经带厂商名时不重复写。 */
    @Test
    fun modelNotDoubled() {
        val body = MikaLinks.issueBody(device.copy(manufacturer = "samsung", model = "SAMSUNG SM-S9180"))
        assertTrue(body.startsWith("- 机型：SAMSUNG SM-S9180\n"))
    }

    @Test
    fun issueUrlIsEncoded() {
        val url = MikaLinks.issueUrl(device)
        assertTrue(url.startsWith("https://github.com/wangzan101/Mika/issues/new?title=&body="))
        val query = url.substringAfter('?')
        assertFalse("no raw spaces or newlines", query.any { it == ' ' || it == '\n' || it == '+' })
        assertFalse("no raw non-ASCII", query.any { it.code > 127 })
        // 能被当成合法 URI 解析,正文解码回原样
        URI(url)
        val body = URLDecoder.decode(query.substringAfter("body="), "UTF-8")
        assertEquals(MikaLinks.issueBody(device), body)
    }

    @Test
    fun linksPointAtTheRepo() {
        assertEquals("https://github.com/wangzan101/Mika#readme", MikaLinks.README)
        assertEquals("https://x.com/wangzan101", MikaLinks.X)
        assertTrue(MikaLinks.LATEST_RELEASE_API.endsWith("/repos/wangzan101/Mika/releases/latest"))
    }

    /** 交给 Mika 的原话:按微信号找作者、发那句话;找不到时不加好友、不发给别人。 */
    @Test
    fun groupTaskText() {
        val t = MikaLinks.GROUP_TASK
        assertTrue(t.contains("微信号 ${MikaLinks.AUTHOR_WECHAT_ID}"))
        assertEquals("wangzan101", MikaLinks.AUTHOR_WECHAT_ID)
        assertTrue(t.contains("「你好，我是 Mika 的用户，想加入 Mika 内测体验群，麻烦拉我一下，谢谢！」"))
        assertTrue(t.contains("不要添加好友，也不要发给别人"))
        assertTrue(t.contains("先在「关于 Mika」里扫码加作者微信"))
        assertEquals(2, t.lines().size)
    }
}
