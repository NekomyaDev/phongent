package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.gate.IrreversibleDetector
import com.wangzan101.mika.core.gate.classify
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import com.wangzan101.mika.core.testing.verb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 元素做的是什么只按它自己的上下文判(名字、附着的标签、同一行、上方最近的小节标题),不按整屏的字。 */
class ElementContextRiskTest {
    private fun text(ref: String, name: String, top: Int, clickable: Boolean = false) =
        UiElement(ref, "TextView", name, 500, 0, listOf(0, top, 999, top + 80), null, clickable, false, false)
    private fun toggle(ref: String, name: String, top: Int, role: String = "Switch") =
        UiElement(ref, role, name, 900, 0, listOf(800, top, 999, top + 80), null, true, false, false)
    private fun screen(vararg e: UiElement) = ScreenSnapshot("s1", "com.any", e.toList(), 1080, 2400)
    private fun tap(ref: String): JsonObject = buildJsonObject { put("ref", ref); put("snapshotId", "s1") }
    private fun floor(ref: String, s: ScreenSnapshot) = ClassifiedRisk.assess(verb("phone_tap"), tap(ref), null, null) { s }.floor

    @Test fun securityFloorFollowsTheTogglesOwnContext() {
        // 页脚有「隐私政策」:与筛选开关无关
        assertNull(floor("@e2", screen(text("@e1", "仅看有货", 100), toggle("@e2", "", 100), text("@e3", "隐私政策", 2200, clickable = true))))
        // 同一页上另有一行「隐私设置」:通知开关不是隐私设置
        assertNull(floor("@e3", screen(text("@e1", "隐私设置", 100, clickable = true), text("@e2", "新消息通知", 300), toggle("@e3", "", 300))))
        // 在「隐私」小节下的开关、名字里讲隐私的开关仍要问
        assertEquals(FloorKind.Security, floor("@e3", screen(text("@e1", "隐私", 100), text("@e2", "允许陌生人查看", 300), toggle("@e3", "", 300))))
        assertEquals(FloorKind.Security, floor("@e1", screen(toggle("@e1", "允许陌生人查看我的隐私资料", 300))))
        assertEquals(FloorKind.Security, floor("@e2", screen(text("@e1", "锁屏密码", 300), toggle("@e2", "", 300))))
    }

    @Test fun consentNeedsTheDocumentInTheElementsOwnLabel() {
        // 勾「同意《隐私政策》」:同意一份文件
        assertNull(floor("@e1", screen(toggle("@e1", "我已阅读并同意《隐私政策》", 300, role = "CheckBox"))))
        assertNull(floor("@e1", screen(toggle("@e1", "", 300, role = "CheckBox"), text("@e2", "我已阅读并同意《用户协议》和《隐私政策》", 300))))
        // 光有书名号、文件在别处、或除了文件名还讲权限:不是同意文件
        assertEquals(FloorKind.Security, floor("@e1", screen(toggle("@e1", "同意开启《定位》", 300, role = "CheckBox"), text("@e2", "隐私设置", 100))))
        assertEquals(FloorKind.Security, floor("@e1", screen(toggle("@e1", "Accept location sharing", 300), text("@e2", "Privacy policy", 2200, clickable = true))))
        assertEquals(FloorKind.Security, floor("@e1", screen(toggle("@e1", "同意《隐私政策》并开启定位权限", 300, role = "CheckBox"))))
    }

    @Test fun aSearchPlaceholderNextToASendButtonIsStillASendBox() {
        val field = UiElement("@e1", "EditText", "你好", 400, 0, listOf(0, 2200, 799, 2300), null, true, true, false, focused = true, placeholder = "搜索或提问")
        val send = UiElement("@e2", "Button", "发送", 900, 0, listOf(800, 2200, 999, 2300), null, true, false, false)
        val enter = Json.parseToJsonElement("""{"text":"你好","pressEnter":true}""").jsonObject
        assertEquals(IrrevAction.send, IrreversibleDetector.classify("phone_type_text", enter, screen(field, send))?.action)
        assertNull(IrreversibleDetector.classify("phone_type_text", enter, screen(field)))
    }
}
