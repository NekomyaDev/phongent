package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.ui.*
import org.json.JSONArray
import org.json.JSONObject

object NativeChat {
    fun exchanges(app:MikaApp,id:String):JSONArray = JSONArray(app.chat.remoteExchanges(id).withIndex().toList().takeLast(100).map { (index,ex) ->
        val s=ex.status
        val phase=when(s){ExchangeStatus.Working->"working";ExchangeStatus.WaitingUnlock->"unlock";is ExchangeStatus.NeedsTakeover->"takeover";is ExchangeStatus.NeedsInput->"input";is ExchangeStatus.NeedsConfirmation->"confirmation";is ExchangeStatus.Paused->"paused";is ExchangeStatus.Done->"done";is ExchangeStatus.Failed->"failed";is ExchangeStatus.Cancelled->"cancelled"}
        val body=when(s){is ExchangeStatus.Done->s.answer;is ExchangeStatus.Failed->s.reason;is ExchangeStatus.Cancelled->s.summary ?: "";is ExchangeStatus.NeedsInput->s.prompt;is ExchangeStatus.NeedsTakeover->listOfNotNull(s.message,s.item?.let{"项目：$it"},s.amount?.let{"金额：$it"},s.address?.let{"地址：$it"}).joinToString("\n");is ExchangeStatus.NeedsConfirmation->s.body;is ExchangeStatus.Paused->s.why;else->""}
        JSONObject().put("position",index).put("id",ex.runId ?: "$id-$index").put("conversationId",id).put("goal",ex.goal).put("phase",phase).put("body",body)
            .put("draft",ex.draft ?: "").put("progress",ChatCopy.progressLine(ex)).put("startedAt",ex.startedAt/1000.0).put("elapsed",ex.elapsedMs?.div(1000.0) ?: JSONObject.NULL)
            .put("title",when(s){is ExchangeStatus.NeedsConfirmation->s.title;is ExchangeStatus.NeedsTakeover->s.reason;ExchangeStatus.WaitingUnlock->"等待解锁";is ExchangeStatus.NeedsInput->"需要你回答";is ExchangeStatus.Paused->"已暂停";is ExchangeStatus.Failed->"未完成";is ExchangeStatus.Cancelled->if(s.summarizing)"已停止 · 正在整理已做的事…" else "已停止";else->""})
            .put("verification",(s as? ExchangeStatus.Done)?.unverifiedLine ?: "")
            .put("evidence",(s as? ExchangeStatus.Done)?.evidence ?: "")
            .put("options",JSONArray((s as? ExchangeStatus.NeedsInput)?.options ?: emptyList<String>()))
            .put("steps",JSONArray(ex.steps.map{JSONObject().put("number",it.step).put("title",it.title).put("subtitle",it.subtitle).put("state",it.state.name).put("reason",it.reason)}))
            .put("steers",JSONArray(ex.steers.map{JSONObject().put("text",it.text).put("state",it.state.name)}))
            .put("notes",JSONArray(ex.memoryNotes.map{app.chat.noteText(it)}+ex.skillNotes.map{it.text}))
            .put("files",JSONArray(ex.files.map{JSONObject().put("name",it.name).put("size",it.size).put("savedAs",it.savedAs)}))
    })
}
