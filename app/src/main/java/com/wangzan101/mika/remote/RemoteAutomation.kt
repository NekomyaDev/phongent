package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.replay.FileRouteStore
import com.wangzan101.mika.core.replay.RouteCodec
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

class RemoteAutomation(private val app:MikaApp) {
    private val json=Json { encodeDefaults=true;ignoreUnknownKeys=false }
    fun plans()=JSONObject().put("items",JSONArray(TriggerRuntime.list(app).map{JSONObject(json.encodeToString(TriggerDefinition.serializer(),it))}))
        .put("exactAlarm",TriggerRuntime.canScheduleExactly(app))
    @Synchronized fun plan(body:JSONObject):JSONObject {
        val id=body.getString("id");require(id.length in 1..128)
        when(body.getString("action")) {
            "save" -> {
                val raw=JSONObject(body.getJSONObject("plan").toString())
                require(raw.getString("id")==id)
                if(!raw.has("threadId"))raw.put("threadId",app.chat.ui.value.conversationId ?: error("no_current_conversation"))
                if(raw.optString("calendarTime").isNotBlank() && !raw.isNull("calendarTime")){
                    val days=raw.optJSONArray("calendarDays")
                    raw.put("nextAtMs",com.wangzan101.mika.core.trigger.nextCalendarAt(raw.getString("calendarTime"),raw.getString("calendarZone"),if(days==null)emptyList() else (0 until days.length()).map{days.getInt(it)},System.currentTimeMillis()))
                }
                val definition=json.decodeFromString(TriggerDefinition.serializer(),raw.toString())
                require(definition.goal.length<=8000)
                require(definition.nextAtMs>System.currentTimeMillis()){"time_must_be_future"}
                val existing=TriggerRuntime.list(app).firstOrNull{it.id==id}
                if(existing!=definition)TriggerRuntime.schedule(app,definition)
            }
            "enable" -> TriggerRuntime.setEnabled(app,id,body.getBoolean("enabled"))
            "delete" -> TriggerRuntime.remove(app,id)
            "run" -> TriggerRuntime.runNow(app,id)
            else -> error("unknown_action")
        }
        return plans()
    }
    fun routes()=JSONObject().put("items",JSONArray(FileRouteStore.shared(app).all().map{JSONObject(RouteCodec.encode(it))}))
    @Synchronized fun route(body:JSONObject):JSONObject {
        val store=FileRouteStore.shared(app)
        when(body.getString("action")) {
            "save" -> store.put(RouteCodec.decode(body.getJSONObject("route").toString()))
            "delete" -> store.remove(body.getString("id"))
            "clear" -> store.clear()
            else -> error("unknown_action")
        }
        return routes()
    }
}
