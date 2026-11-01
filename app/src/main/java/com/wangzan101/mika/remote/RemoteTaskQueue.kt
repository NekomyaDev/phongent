package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.core.run.ControllerQueue
import com.wangzan101.mika.core.run.ControllerTask
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.task.TaskService
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.json.JSONArray

class RemoteTaskQueue private constructor(private val app:MikaApp){
    private val prefs=app.getSharedPreferences("controller-task-queue",0)
    private val codec=Json{encodeDefaults=true;ignoreUnknownKeys=true}
    private val serializer=ListSerializer(ControllerTask.serializer())
    private val queue=ControllerQueue(runCatching{codec.decodeFromString(serializer,prefs.getString("tasks","[]")!!)}.getOrDefault(emptyList())){tasks->check(prefs.edit().putString("tasks",codec.encodeToString(serializer,tasks)).commit());BridgeEvents.signal()}
    private val awaiting=mutableMapOf<String,Long>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    init{scope.launch{delay(3000);while(isActive){runCatching{tick()};delay(500)}}}
    fun list()=JSONObject().put("items",JSONArray(codec.encodeToString(serializer,queue.snapshot())))
    fun status():JSONArray {
        val tasks=queue.snapshot();val active=setOf("queued","dispatching","running","uncertain")
        val recent=tasks.filter{it.state !in active}.takeLast(20).map{it.id}.toSet()
        return JSONArray(codec.encodeToString(serializer,tasks.filter{it.state in active || it.id in recent}.map{it.copy(text="")}))
    }
    fun enqueue(body:JSONObject):JSONObject {
        queue.snapshot().firstOrNull{it.id==body.getString("id")}?.let{return JSONObject().put("id",it.id).put("status",it.state).put("conversationId",it.conversation)}
        val selected=app.chat.ui.value.conversationId ?: ""
        val expected=body.optString("conversationId")
        check(expected.isEmpty() || expected==selected){"conversation_changed"}
        val task=queue.enqueue(ControllerTask(body.getString("id"),body.getString("text"),expected.ifEmpty{selected}))
        return JSONObject().put("id",task.id).put("status",task.state).put("conversationId",task.conversation)
    }
    fun retry(id:String):JSONObject {
        val task=queue.snapshot().firstOrNull{it.id==id} ?: error("task_not_found")
        check(task.state in setOf("failed","cancelled")){"task_cannot_retry"}
        queue.enqueue(ControllerTask(java.util.UUID.randomUUID().toString(),task.text,task.conversation));return list()
    }
    private fun ended(id:String,state:TaskState){
        if(queue.snapshot().none{it.id==id})return
        when(state){is TaskState.Succeeded->queue.update(id,"completed",result=state.answer);is TaskState.Failed->queue.update(id,"failed",state.reason);TaskState.Cancelled->queue.update(id,"cancelled");else->{}}
    }
    fun acknowledge(id:String):JSONObject{queue.acknowledge(id);return list()}
    fun cancel(id:String):JSONObject{queue.cancel(id);return list()}
    private fun tick(){
        for(task in queue.snapshot().filter{it.state in setOf("dispatching","running","uncertain")}){
            val current=app.taskState.runs.value.firstOrNull{it.mcpExternalId==task.id} ?: app.taskState.shownRun?.takeIf{it.mcpExternalId==task.id}
            if(current==null && task.state=="dispatching"){
                val since=awaiting.getOrPut(task.id){android.os.SystemClock.elapsedRealtime()}
                if(android.os.SystemClock.elapsedRealtime()-since>30000)queue.update(task.id,"uncertain","设备尚未确认启动，请核实后继续")
            }
            if(current!=null){
                awaiting.remove(task.id)
                val state=current.state.value
                val phase=when(state){is TaskState.Succeeded->"completed";is TaskState.Failed->"failed";TaskState.Cancelled->"cancelled";else->"running"}
                if(task.state!=phase)queue.update(task.id,phase)
            }
        }
        if(app.taskState.busy.value)return
        val next=queue.claim() ?: return
        try{
            val store=com.wangzan101.mika.chat.ConversationStore.shared(app)
            val conversation=next.conversation.ifEmpty{store.newConversation()}
            check(next.conversation.isEmpty() || app.chat.ui.value.conversationId==conversation || store.list().any{it.id==conversation}){"conversation_not_found"}
            app.chat.openConversation(conversation)
            check(app.chat.ui.value.conversationId==conversation){"conversation_not_selected"}
            queue.bindConversation(next.id,conversation)
            TaskService.startMcp(app,next.text,conversation,next.id,Attendance.Reachable)
        }catch(e:Exception){queue.update(next.id,"failed",e.message ?: "dispatch_failed")}
    }
    companion object{
        fun reportEnd(id:String,state:TaskState){instance?.let{runCatching{it.ended(id,state)}}}
        @Volatile private var instance:RemoteTaskQueue?=null
        fun get(app:MikaApp):RemoteTaskQueue=instance ?: synchronized(this){instance ?: RemoteTaskQueue(app).also{instance=it}}
    }
}
