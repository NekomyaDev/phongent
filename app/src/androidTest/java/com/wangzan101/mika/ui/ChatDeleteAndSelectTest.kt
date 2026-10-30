package com.wangzan101.mika.ui

import com.wangzan101.mika.ChineseUi
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.chat.DeleteImpact
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.agent.Turn
import com.wangzan101.mika.settings.Prerequisites
import com.wangzan101.mika.takeover.Takeover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** 侧栏长按删对话(确认弹窗、删掉、列表当场更新),和对话页里回答、用户的话可以选中复制。不用真机的任务环境。 */
@RunWith(AndroidJUnit4::class)
class ChatDeleteAndSelectTest {
    @get:Rule(order = Int.MIN_VALUE) val chinese = ChineseUi()

    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ConversationStore(File(context.cacheDir, "chat-delete-test-" + UUID.randomUUID()))
    private val removed = mutableListOf<String>()

    private val actions = object : TaskActions {
        override fun start(goal: String, conversationId: String) {}
        override fun cancel() {}
        override fun resume() {}
        override fun takeOver() {}
        override fun handBack() {}
        override fun finishedMyself() {}
        override fun skip() {}
        override fun answer(text: String) {}
        override fun approve(key: Int) {}
        override fun decline(key: Int) {}
    }

    private fun vm(impact: DeleteImpact = DeleteImpact(runningTask = false, schedules = 0)) = ChatViewModel(
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        MutableStateFlow(TaskState.Idle), MutableStateFlow(Takeover.Stage.None), actions, store,
        deletionImpact = { impact },
        removeConversation = { removed += it; store.delete(it) },
    )

    private fun show(vm: ChatViewModel, drawerOpen: Boolean) {
        compose.setContent {
            ChatWithDrawer(
                vm, Prerequisites.Check(emptyList()), reduceMotion = true,
                conversations = { store.list() }, conversationChanges = store.changes, conversationTitle = store::title,
                onPlans = {}, onMemory = {}, onBrowser = {}, onSettings = {}, reopen = drawerOpen,
            ) { open ->
                ChatScreen(vm, Prerequisites.Check(emptyList()), MutableStateFlow(null), MutableStateFlow(false), reduceMotion = true,
                    onOpenDrawer = open, openSettings = {})
            }
        }
        compose.waitForIdle()
    }

    @Test fun longPressARowAsksThenDeletes() {
        store.append("a", Turn("查天气", "晴", "succeeded"))
        store.setTitle("a", "天气")
        store.append("b", Turn("订票", "好了", "succeeded"))
        val vm = vm(DeleteImpact(runningTask = true, schedules = 2))
        show(vm, drawerOpen = true)

        compose.onNodeWithText("天气").performTouchInput { longClick() }
        compose.onNodeWithText("删除这个对话？").assertExists()
        val body = compose.onNodeWithTag("delete-conversation-body").fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        assertTrue(body, "天气" in body && "停止" in body && "2" in body)

        // 取消:什么都不删
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertTrue(removed.isEmpty())

        compose.onNodeWithText("天气").performTouchInput { longClick() }
        compose.onNodeWithTag("confirm-delete-conversation").performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) { removed.isNotEmpty() }
        assertEquals(listOf("a"), removed)
        assertEquals(0, compose.onAllNodesWithText("天气").fetchSemanticsNodes().size)
        compose.onNodeWithText("订票").assertExists()
    }

    @Test fun answerAndUserTextAreSelectable() {
        store.append("s", Turn("今天天气怎么样", "上海今天**晴**,详见 [天气](https://example.com)", "succeeded"))
        val vm = vm()
        compose.runOnUiThread { vm.openConversation("s") }
        show(vm, drawerOpen = false)

        compose.onNode(hasText("今天天气怎么样") and hasAnyAncestor(hasTestTag("selectable-user"))).assertExists()
        val answer = compose.onNode(hasText("上海今天", substring = true) and hasAnyAncestor(hasTestTag("selectable-answer")))
        answer.assertExists()
        // 长按选中:侧栏不跟着打开
        answer.performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("drawer").assertIsNotDisplayed()
    }
}
