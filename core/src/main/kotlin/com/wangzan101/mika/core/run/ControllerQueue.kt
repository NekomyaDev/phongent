package com.wangzan101.mika.core.run

import kotlinx.serialization.Serializable

/** Durable queue transitions. Dispatching is persisted before invoking an external executor. */
@Serializable
data class ControllerTask(val id:String,val text:String,val conversation:String,val state:String="queued",val error:String="",val result:String="")
class ControllerQueue(initial:List<ControllerTask> = emptyList(),private val save:(List<ControllerTask>)->Unit){
    private var tasks=initial.map{if(it.state in setOf("dispatching","running"))it.copy(state="uncertain",error="执行结果待核实") else it}
    @Synchronized fun snapshot()=tasks.toList()
    @Synchronized fun enqueue(task:ControllerTask):ControllerTask {
        tasks.firstOrNull{it.id==task.id}?.let{return it}
        require(task.id.matches(Regex("[A-Za-z0-9-]{8,80}")) && task.text.isNotBlank() && task.text.length<=8000)
        check(tasks.count{it.state in setOf("queued","dispatching","running","uncertain")}<100){"queue_full"}
        val next=tasks+task.copy(state="queued");val terminal=setOf("completed","failed","cancelled");val retained=next.filter{it.state in terminal}.takeLast(400).map{it.id}.toSet();change(next.filter{it.state !in terminal || it.id in retained});return tasks.last()
    }
    @Synchronized fun claim():ControllerTask? {
        if(tasks.any{it.state in setOf("dispatching","running","uncertain")})return null
        val task=tasks.firstOrNull{it.state=="queued"} ?: return null
        update(task.id,"dispatching");return tasks.first{it.id==task.id}
    }
    @Synchronized fun update(id:String,state:String,error:String="",result:String?=null){
        require(state in setOf("queued","dispatching","running","completed","failed","cancelled","uncertain"))
        change(tasks.map{if(it.id==id)it.copy(state=state,error=error,result=result ?: it.result) else it})
    }
    @Synchronized fun bindConversation(id:String,conversation:String){
        require(conversation.isNotBlank())
        val task=tasks.firstOrNull{it.id==id} ?: error("task_not_found")
        check(task.conversation.isEmpty() || task.conversation==conversation){"conversation_changed"}
        change(tasks.map{if(it.id==id)it.copy(conversation=conversation) else it})
    }
    @Synchronized fun cancel(id:String){val task=tasks.firstOrNull{it.id==id} ?: error("task_not_found");check(task.state=="queued"){"task_not_queued"};update(id,"cancelled")}
    @Synchronized fun acknowledge(id:String){val task=tasks.firstOrNull{it.id==id} ?: error("task_not_found");check(task.state=="uncertain"){"task_not_uncertain"};update(id,"completed")}
    private fun change(value:List<ControllerTask>){save(value);tasks=value}
}
