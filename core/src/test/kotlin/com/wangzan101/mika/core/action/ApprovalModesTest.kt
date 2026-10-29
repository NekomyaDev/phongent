package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.agent.AgentLoop
import com.wangzan101.mika.core.agent.FakeModel
import com.wangzan101.mika.core.agent.SpeedFlags
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.gate.ConfirmationGate
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.model.ChatModel
import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.ToolSpec
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.record.RunEvent
import com.wangzan101.mika.core.record.RunSink
import com.wangzan101.mika.core.uistate.ActionResult
import com.wangzan101.mika.core.uistate.ScreenPort
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import com.wangzan101.mika.core.uistate.WebInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wangzan101.mika.core.assembly.toolDispatcher

private fun el(ref: String, role: String, name: String, b: List<Int>, editable: Boolean = false, secret: Boolean = false) =
    UiElement(ref, role, name, 0, 0, b, null, !editable, editable, false, secret = secret)

/** 和 [title] 的聊天页:标题、输入框、「发送」,另有「卸载」按钮、密码框和一个普通按钮。 */
private fun chatPage(title: String = "张三") = listOf(
    el("@e1", "text", title, listOf(0, 0, 999, 80)),
    el("@e2", "edit", "明天见", listOf(0, 900, 790, 999), editable = true),
    el("@e3", "button", "发送", listOf(800, 900, 990, 990)),
    el("@e4", "button", "卸载", listOf(0, 300, 300, 360)),
    el("@e5", "edit", "", listOf(0, 400, 999, 460), editable = true, secret = true),
    el("@e6", "button", "表情", listOf(400, 300, 600, 360)),
)

/** 会话列表:每行一个名字和它的「删除」。 */
private val listPage = listOf(
    el("@e1", "text", "张三", listOf(0, 100, 600, 160)), el("@e2", "button", "删除", listOf(800, 100, 990, 160)),
    el("@e3", "text", "张三丰", listOf(0, 200, 600, 260)), el("@e4", "button", "删除", listOf(800, 200, 990, 260)),
    el("@e5", "text", "李四", listOf(0, 300, 600, 360)), el("@e6", "button", "删除", listOf(800, 300, 990, 360)),
)

private class Chat(var app: String = "com.chat", var web: WebInfo? = null, var page: List<UiElement> = chatPage()) : ScreenPort {
    val taps = mutableListOf<String>()
    val typed = mutableListOf<String>()
    var opened = mutableListOf<String>()
    private var reads = 0
    private fun read(id: String) = ScreenSnapshot(id, app, page, 1080, 2400, web = web)
    override suspend fun snapshot(): ScreenSnapshot = read("s${++reads}")
    override suspend fun peek(): ScreenSnapshot = read("p$reads")
    override suspend fun screenshotPng(): ByteArray? = null
    override suspend fun tapRef(ref: String, snapshotId: String): ActionResult { taps += ref; return ActionResult.Ok(changed = true) }
    override suspend fun tapXY(x: Int, y: Int): ActionResult { taps += "$x,$y"; return ActionResult.Ok(changed = true) }
    override suspend fun typeText(ref: String?, snapshotId: String?, text: String): ActionResult { typed += text; return ActionResult.Ok(changed = true) }
    override suspend fun clearText(ref: String?, snapshotId: String?) = ActionResult.Ok()
    override suspend fun scroll(ref: String?, snapshotId: String?, down: Boolean) = ActionResult.Ok()
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = ActionResult.Ok()
    override suspend fun back() = ActionResult.Ok()
    override suspend fun pressKey(keyCode: Int) = ActionResult.Ok()
    override suspend fun openApp(packageName: String, cold: Boolean): ActionResult { opened += packageName; return ActionResult.Ok() }
    override suspend fun waitStable(timeoutMs: Long) = true
    override suspend fun listApps() = listOf("com.chat" to "Chat")
}

private class Events : RunSink {
    val all = mutableListOf<RunEvent>()
    override fun event(e: RunEvent) { all += e }
}

private var seq = 0
private fun call(name: String, args: String = "{}") = Part.ToolCall("m${++seq}", name, args)
private fun tools(vararg c: Part.ToolCall) = ModelReply(c.toList(), StopReason.ToolUse, null)
private val OBSERVE get() = tools(call("phone_observe"))
private val FINISH get() = com.wangzan101.mika.core.testing.answerReply("好了")
private fun tap(ref: String) = tools(call("phone_tap", """{"ref":"$ref","snapshotId":"s1"}"""))
private fun ask(action: String = "send", target: String = "张三", content: String = "明天见") =
    tools(call("request_confirmation", """{"action":"$action","target":"$target","content":"$content","app":"com.chat"}"""))
private val GIVE_UP get() = tools(call("end_not_done", """{"message":"没做"}"""))

private fun FakeModel.result(i: Int): JsonObject =
    Json.parseToJsonElement(requests[i].last { it.role == Role.Tool }.parts.filterIsInstance<Part.ToolResult>().first().content).jsonObject
private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

private class Settable(var current: ApprovalMode, override val standing: StandingGrants = MemoryStandingGrants()) : ApprovalSettings {
    var auto = false
    override fun mode() = current
    override fun autonomous() = auto
}

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalModesTest {
    private fun loop(
        m: FakeModel, s: Chat, approvals: ApprovalSettings, sink: RunSink = RunSink.NONE, timeout: Long? = null, flags: SpeedFlags = SpeedFlags.OFF,
        self: String? = null,
    ) = AgentLoop(m, toolDispatcher(s), "BASE", sink = sink, confirmations = ConfirmationGate(),
        approvals = approvals, confirmTimeoutMs = timeout, flags = flags, selfPackage = self)

    @Test fun handsOffSendsWithoutAskingAndAudits() = runTest {
        val s = Chat(); val sink = Events()
        val m = FakeModel(OBSERVE, tap("@e3"), FINISH)
        loop(m, s, Settable(ApprovalMode.HANDS_OFF), sink).run("t")
        assertEquals(listOf("@e3"), s.taps)
        val audit = sink.all.filterIsInstance<RunEvent.Approval>().single()
        assertEquals(listOf("model", "send", "External", "HANDS_OFF", "allowed"), listOf(audit.source, audit.action, audit.risk, audit.mode, audit.decision))
        assertEquals(null, audit.grant)
    }

    @Test fun autoStillAsksBeforeSending() = runTest {
        val s = Chat(); val sink = Events()
        val m = FakeModel(OBSERVE, tap("@e3"), GIVE_UP)
        loop(m, s, Settable(ApprovalMode.AUTO), sink).run("t")
        assertTrue(s.taps.isEmpty())
        assertEquals("confirmation_required", m.result(2).s("error"))
        assertEquals("required", sink.all.filterIsInstance<RunEvent.Approval>().single().decision)
    }

    @Test fun alwaysAskAsksForAPlainTapButNotForReads() = runTest {
        val s = Chat()
        val m = FakeModel(OBSERVE, tap("@e6"), GIVE_UP)
        loop(m, s, Settable(ApprovalMode.ALWAYS_ASK)).run("t")
        assertTrue(s.taps.isEmpty())
        assertEquals("true", m.result(1).s("ok"))
        val r = m.result(2)
        assertEquals("confirmation_required", r.s("error"))
        assertEquals("other", r.s("action")); assertEquals("表情", r.s("matched"))
        assertTrue(r.s("message"), "审批模式" in r.s("message"))
    }

    @Test fun switchingToAutonomousMidRunStopsAskingFromTheNextStep() = runTest {
        val s = Chat()
        val approvals = Settable(ApprovalMode.SMART)
        val m = FakeModel(OBSERVE, tap("@e3"), tap("@e3"), FINISH)
        // 第一次点发送时还在询问:被拦下;模型再点之前用户换成了自主
        val flipping = object : ChatModel by m {
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
                if (m.requests.size == 2) approvals.auto = true
                return m.complete(system, messages, tools)
            }
        }
        AgentLoop(flipping, toolDispatcher(s), "BASE", confirmations = ConfirmationGate(), approvals = approvals).run("t")
        assertEquals("confirmation_required", m.result(2).s("error"))
        assertEquals(listOf("@e3"), s.taps)
    }

    @Test fun smartLeavesPlainTapsAlone() = runTest {
        val s = Chat()
        loop(FakeModel(OBSERVE, tap("@e6"), FINISH), s, Settable(ApprovalMode.SMART)).run("t")
        assertEquals(listOf("@e6"), s.taps)
    }

    @Test fun taskGrantCoversTheTaskAndEndsWithIt() = runTest {
        val s = Chat(); val sink = Events()
        val approvals = Settable(ApprovalMode.SMART)
        val m = FakeModel(OBSERVE, ask(), tap("@e3"), tap("@e3"), FINISH, OBSERVE, tap("@e3"), GIVE_UP)
        val l = loop(m, s, approvals, sink)
        val run = async { l.run("t1") }
        advanceUntilIdle()
        l.answerConfirmation(ConfirmChoice.Task)
        run.await()
        assertEquals(listOf("@e3", "@e3"), s.taps)
        assertEquals(listOf("once", "task"), sink.all.filterIsInstance<RunEvent.Approval>().filter { it.decision == "allowed" }.map { it.grant })
        assertTrue(approvals.standing.items.value.isEmpty())
        // 下一个任务里任务内授权已清空
        l.run("t2")
        assertEquals(2, s.taps.size)
        assertEquals("confirmation_required", m.result(7).s("error"))
    }

    @Test fun standingGrantOutlivesTheTaskAndCanBeRevoked() = runTest {
        val s = Chat()
        val approvals = Settable(ApprovalMode.SMART)
        val m = FakeModel(OBSERVE, ask(), tap("@e3"), FINISH, OBSERVE, tap("@e3"), FINISH, OBSERVE, tap("@e3"), GIVE_UP)
        val l = loop(m, s, approvals)
        val run = async { l.run("t1") }
        advanceUntilIdle()
        l.answerConfirmation(ConfirmChoice.Always)
        run.await()
        assertEquals(listOf(GrantKey(IrrevAction.send, "com.chat", "张三", "发送")), approvals.standing.items.value)
        l.run("t2")
        assertEquals(listOf("@e3", "@e3"), s.taps)
        approvals.standing.clear()
        l.run("t3")
        assertEquals(2, s.taps.size)
    }

    @Test fun floorAsksInHandsOffAndIsNeverGrantedBeyondOnce() = runTest {
        val s = Chat()
        val approvals = Settable(ApprovalMode.HANDS_OFF)
        val m = FakeModel(OBSERVE, tap("@e4"), ask(action = "other", target = "卸载", content = "卸载这个 App"), tap("@e4"), tap("@e4"), GIVE_UP)
        val l = loop(m, s, approvals)
        val run = async { l.run("t") }
        advanceUntilIdle()
        assertTrue((l.state.value as TaskState.WaitingConfirmation).floor)
        l.answerConfirmation(ConfirmChoice.Always)
        run.await()
        val r = m.result(2)
        assertEquals("confirmation_required", r.s("error"))
        assertTrue(r.s("message"), "同意只放行这一次" in r.s("message"))
        // 选了「以后都允许」也只放行这一次,不存长期授权
        assertEquals(listOf("@e4"), s.taps)
        assertEquals("confirmation_required", m.result(5).s("error"))
        assertTrue(approvals.standing.items.value.isEmpty())
    }

    @Test fun typingIntoAPasswordFieldIsAFloor() = runTest {
        val s = Chat()
        val m = FakeModel(OBSERVE, tools(call("phone_type_text", """{"text":"x","ref":"@e5","snapshotId":"s1"}""")), GIVE_UP)
        loop(m, s, Settable(ApprovalMode.HANDS_OFF)).run("t")
        assertTrue(s.typed.isEmpty())
        val r = m.result(2)
        assertEquals("confirmation_required", r.s("error"))
        assertTrue(r.s("message"), "hand_to_user" in r.s("message"))
    }

    @Test fun batchStepsStopAtSendEvenHandsOff() = runTest {
        val s = Chat()
        val batch = tools(call("do_steps", """{"steps":[{"tool":"phone_tap","args":{"ref":"@e3","snapshotId":"s1"}}]}"""))
        loop(FakeModel(OBSERVE, batch, GIVE_UP), s, Settable(ApprovalMode.HANDS_OFF), flags = SpeedFlags(batchSteps = true)).run("t")
        assertTrue(s.taps.isEmpty())
    }

    @Test fun unansweredConfirmationIsDeclinedAfterTheTimeout() = runTest {
        val s = Chat(); val sink = Events()
        val m = FakeModel(OBSERVE, ask(), GIVE_UP)
        loop(m, s, Settable(ApprovalMode.SMART), sink, timeout = 60_000).run("t")
        assertEquals("confirmation_declined", m.result(2).s("error"))
        assertEquals(listOf(null, "timed_out"), sink.all.filterIsInstance<RunEvent.Confirmation>().map { it.decision })
    }

    @Test fun standingSendGrantNeedsTheExactChatTitle() = runTest {
        val s = Chat()
        val approvals = Settable(ApprovalMode.SMART)
        approvals.standing.add(GrantKey(IrrevAction.send, "com.chat", "张三", "发送"))
        s.page = chatPage("张三丰")
        loop(FakeModel(OBSERVE, tap("@e3"), GIVE_UP), s, approvals).run("t")
        assertTrue(s.taps.isEmpty())
        s.page = chatPage("张三")
        loop(FakeModel(OBSERVE, tap("@e3"), FINISH), s, approvals).run("t")
        assertEquals(listOf("@e3"), s.taps)
    }

    @Test fun taskGrantBindsToTheRowActedOn() = runTest {
        val s = Chat(page = listPage)
        val m = FakeModel(OBSERVE, tap("@e2"), ask(action = "delete", target = "张三", content = "删除和张三的会话"), tap("@e2"),
            tap("@e2"), tap("@e4"), tap("@e6"), GIVE_UP)
        val l = loop(m, s, Settable(ApprovalMode.SMART))
        val run = async { l.run("t") }
        advanceUntilIdle()
        l.answerConfirmation(ConfirmChoice.Task)
        run.await()
        // 张三那一行可以再删;张三丰(名字包含张三)和李四那一行不在授权里
        assertEquals(listOf("@e2", "@e2"), s.taps)
        assertEquals("confirmation_required", m.result(6).s("error"))
        assertEquals("confirmation_required", m.result(7).s("error"))
    }

    @Test fun webPasswordTypingAsksEvenHandsOff() = runTest {
        val s = Chat(app = "com.browser", web = WebInfo("https://site.example/login", "登录", "t1", 200),
            page = listOf(el("@e1", "textbox", "", listOf(0, 100, 999, 160), editable = true, secret = true)))
        val m = FakeModel(OBSERVE, tools(call("phone_type_text", """{"text":"x","ref":"@e1","snapshotId":"s1"}""")), GIVE_UP)
        loop(m, s, Settable(ApprovalMode.HANDS_OFF)).run("t")
        assertTrue(s.typed.isEmpty())
        assertEquals("confirmation_required", m.result(2).s("error"))
    }

    @Test fun orderRequestIsAFloorCardAndNeverStored() = runTest {
        val s = Chat()
        val order = tools(call("request_confirmation", """{"action":"order","target":"耳机","content":"买一副耳机","amount":"¥25","app":"com.chat"}"""))
        val approvals = Settable(ApprovalMode.SMART)
        val l = loop(FakeModel(OBSERVE, order, GIVE_UP), s, approvals)
        val run = async { l.run("t") }
        advanceUntilIdle()
        assertTrue((l.state.value as TaskState.WaitingConfirmation).floor)
        l.answerConfirmation(ConfirmChoice.Always)
        run.await()
        assertTrue(approvals.standing.items.value.isEmpty())
    }

    @Test fun mikaNeverOperatesItself() = runTest {
        // 屏上是 Mika 自己:不点
        val s = Chat(app = "com.mika")
        val m = FakeModel(OBSERVE, tap("@e6"), GIVE_UP)
        loop(m, s, Settable(ApprovalMode.HANDS_OFF), self = "com.mika").run("t")
        assertTrue(s.taps.isEmpty())
        assertEquals("own_app", m.result(2).s("error"))
        // 从别的 App 打开 Mika、打开它的应用详情:不做
        s.app = "com.chat"
        val m2 = FakeModel(OBSERVE, tools(call("phone_launch_app", """{"package":"com.mika"}""")), tools(call("phone_open_link", """{"uri":"package:com.mika"}""")), GIVE_UP)
        loop(m2, s, Settable(ApprovalMode.HANDS_OFF), self = "com.mika").run("t")
        assertTrue(s.opened.isEmpty())
        assertEquals("own_app", m2.result(2).s("error"))
        assertEquals("own_app", m2.result(3).s("error"))
    }
}
