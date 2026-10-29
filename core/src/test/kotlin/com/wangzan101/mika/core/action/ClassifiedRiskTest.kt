package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.gate.Irreversible
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import com.wangzan101.mika.core.testing.verb

class ClassifiedRiskTest {
    private fun el(ref: String, role: String, name: String, b: List<Int>, editable: Boolean = false, secret: Boolean = false, focused: Boolean = false) =
        UiElement(ref, role, name, 0, 0, b, null, !editable, editable, false, focused = focused, secret = secret)
    private val screen = ScreenSnapshot("s1", "com.any", listOf(
        el("@e1", "Switch", "隐私保护", listOf(0, 0, 999, 100)),
        el("@e2", "TextView", "隐私政策", listOf(0, 100, 999, 200)),
        el("@e3", "Button", "修改密码", listOf(0, 200, 999, 300)),
        el("@e4", "EditText", "", listOf(0, 300, 999, 400), editable = true, secret = true, focused = true),
        el("@e5", "Button", "Uninstall", listOf(0, 400, 999, 500)),
        el("@e6", "Button", "好的", listOf(0, 500, 999, 600)),
    ), 1080, 2400)

    private fun ref(r: String): JsonObject = buildJsonObject { put("ref", r); put("snapshotId", "s1") }
    private fun assess(tool: String, args: JsonObject, hit: Irreversible? = null, seen: String? = null, on: ScreenSnapshot? = screen) =
        ClassifiedRisk.assess(verb(tool), args, hit, seen) { on }

    @Test fun levelsFollowTheClassifier() {
        assertEquals(Risk(ActionRisk.Read), assess("phone_back", JsonObject(emptyMap())))
        assertEquals(Risk(ActionRisk.Read), assess("phone_scroll", JsonObject(emptyMap())))
        assertEquals(Risk(ActionRisk.Change), assess("phone_tap", ref("@e6")))
        assertEquals(Risk(ActionRisk.External), assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.send, "发送")))
        assertEquals(Risk(ActionRisk.External), assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.submit, "提交")))
        assertEquals(Risk(ActionRisk.Irreversible), assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.delete, "删除")))
    }

    @Test fun floorsByClass() {
        assertEquals(FloorKind.Payment, assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.pay, "付款")).floor)
        // 下单就是花钱
        assertEquals(Risk(ActionRisk.Irreversible, FloorKind.Payment), assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.order, "提交订单")))
        // 看不清点的是什么:可能是底线
        assertEquals(Risk(ActionRisk.Unknown, FloorKind.Unreadable), assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.other, "point found by sight")))
        // 复看确认过的所见文字也按底线字样判
        assertEquals(FloorKind.Account, assess("phone_tap", buildJsonObject { put("x", 1); put("y", 990) }, seen = "卸载").floor)
        assertEquals(FloorKind.Account, assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.delete, "注销账号")).floor)
        assertEquals(FloorKind.Account, assess("phone_tap", ref("@e5")).floor)
        assertEquals(FloorKind.Security, assess("phone_tap", ref("@e3")).floor)
        assertEquals(FloorKind.Security, assess("phone_tap", ref("@e1")).floor)
        assertEquals(FloorKind.Secret, assess("phone_type_text", buildJsonObject { put("text", "x") }).floor)
        // 找不到落在哪个框:屏上有密码框就算
        assertEquals(FloorKind.Secret, assess("phone_type_text", buildJsonObject { put("text", "x"); put("ref", "@e9") }).floor)
        // 屏上有金额时对外的动作按花钱算
        val shop = screen.copy(elements = screen.elements + el("@e7", "TextView", "合计 ¥25", listOf(0, 600, 999, 700)))
        assertEquals(FloorKind.Payment, assess("phone_tap", ref("@e6"), Irreversible(IrrevAction.submit, "提交"), on = shop).floor)
        // 隐私政策是链接,不是设置开关
        assertEquals(null, assess("phone_tap", ref("@e2")).floor)
        assertEquals(null, assess("phone_tap", ref("@e6")).floor)
    }

    /** 同意一份协议、政策是照常的改动;改安全、隐私设置的开关仍是底线。 */
    @Test fun agreeingToAPolicyIsNotASecurityFloor() {
        val dialog = ScreenSnapshot("s1", "com.any", listOf(
            el("@e1", "TextView", "欢迎使用。请阅读《用户协议》和《隐私政策》", listOf(0, 0, 999, 200)),
            el("@e2", "CheckBox", "我已阅读并同意《隐私政策》", listOf(0, 300, 999, 400)),
            el("@e3", "Button", "同意", listOf(0, 500, 499, 600)),
            el("@e4", "Button", "不同意", listOf(500, 500, 999, 600)),
        ), 1080, 2400)
        assertEquals(Risk(ActionRisk.Change), assess("phone_tap", ref("@e2"), on = dialog))
        assertEquals(Risk(ActionRisk.Change), assess("phone_tap", ref("@e3"), on = dialog))
        val privacy = ScreenSnapshot("s1", "com.any", listOf(
            UiElement("@e1", "TextView", "隐私设置", 0, 0, listOf(0, 0, 999, 100), null, false, false, false),
            el("@e2", "Switch", "允许陌生人查看", listOf(0, 200, 999, 300)),
        ), 1080, 2400)
        assertEquals(FloorKind.Security, assess("phone_tap", ref("@e2"), on = privacy).floor)
        val lock = ScreenSnapshot("s1", "com.android.settings", listOf(el("@e1", "Switch", "锁屏密码", listOf(0, 0, 999, 100))), 1080, 2400)
        assertEquals(FloorKind.Security, assess("phone_tap", ref("@e1"), on = lock).floor)
    }

    @Test fun coordinateTapsUseTheSmallestElementThere() {
        assertEquals("修改密码", ClassifiedRisk.target(verb("phone_tap"), buildJsonObject { put("x", 500); put("y", 250) }, screen)?.name)
    }
}
