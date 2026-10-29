package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ChatModel
import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.model.ToolSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSmartReviewerTest {
    @Test fun clearOnlyOnExplicitFalseAndSkipsNonChange() = runBlocking {
        var calls = 0
        val model = object : ChatModel {
            override val capabilities = Capabilities(false, false, 4096, false)
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
                calls++
                return ModelReply(listOf(Part.Text(if (calls == 1) "{\"ask\":false}" else "bad")), StopReason.EndTurn, null)
            }
        }
        val reviewer = ModelSmartReviewer(model)
        assertEquals(SmartReview.None, reviewer.review("phone_tap", "普通按钮", Risk(ActionRisk.Change)))
        assertEquals(SmartReview.Flag, reviewer.review("phone_tap", "未知按钮", Risk(ActionRisk.Change)))
        assertEquals(SmartReview.None, reviewer.review("phone_read", "页面", Risk(ActionRisk.Read)))
        assertEquals(2, calls)
    }
}
