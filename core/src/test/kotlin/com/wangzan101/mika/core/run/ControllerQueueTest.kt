package com.wangzan101.mika.core.run
import org.junit.Assert.*
import org.junit.Test
class ControllerQueueTest {
    @Test fun bindsNewConversationBeforeDispatchAndPreservesAcrossRestart(){
        var saved=emptyList<ControllerTask>();val q=ControllerQueue{saved=it}
        q.enqueue(ControllerTask("task-0001","one",""));q.claim();q.bindConversation("task-0001","chat-1")
        assertEquals("chat-1",ControllerQueue(saved){}.snapshot().single().conversation)
        assertThrows(IllegalStateException::class.java){q.bindConversation("task-0001","chat-2")}
    }
    @Test fun idempotencyAndFifo(){var saved=emptyList<ControllerTask>();val q=ControllerQueue{saved=it};q.enqueue(ControllerTask("task-0001","one","chat"));q.enqueue(ControllerTask("task-0001","changed","chat"));q.enqueue(ControllerTask("task-0002","two","chat"));assertEquals(2,saved.size);assertEquals("one",q.claim()?.text);assertNull(q.claim());q.update("task-0001","completed");assertEquals("task-0002",q.claim()?.id)}
    @Test fun restartDoesNotRepeatAmbiguousExecution(){val q=ControllerQueue(listOf(ControllerTask("task-0001","one","chat","dispatching"),ControllerTask("task-0002","two","chat"))){};assertEquals("uncertain",q.snapshot().first().state);assertNull(q.claim())}
    @Test fun trimmingHistoryKeepsPendingTasks(){val old=listOf(ControllerTask("pending-1","pending","chat"))+(1..499).map{ControllerTask("done-$it","done","chat","completed")};val q=ControllerQueue(old){};q.enqueue(ControllerTask("pending-2","new","chat"));assertEquals("pending-1",q.claim()?.id);assertEquals(402,q.snapshot().size)}
    @Test fun cancelQueuedOnly(){val q=ControllerQueue{};q.enqueue(ControllerTask("task-0001","one","chat"));q.cancel("task-0001");assertNull(q.claim());q.enqueue(ControllerTask("task-0002","two","chat"));q.claim();assertThrows(IllegalStateException::class.java){q.cancel("task-0002")}}
    @Test fun failedPersistenceNeverClaimsExecution(){val q=ControllerQueue(listOf(ControllerTask("task-0001","one","chat"))){error("disk full")};assertThrows(IllegalStateException::class.java){q.claim()};assertEquals("queued",q.snapshot().single().state)}
}
