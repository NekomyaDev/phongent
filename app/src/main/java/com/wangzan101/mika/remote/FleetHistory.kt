package com.wangzan101.mika.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Controller-local history. Clearing it never deletes chats on execution devices. */
class FleetHistory(context:Context){
    private val prefs=context.getSharedPreferences("fleet-exchanges",0)
    private fun array(raw:String):List<JSONObject> = runCatching{val a=JSONArray(raw);List(a.length()){a.getJSONObject(it)}}.getOrDefault(emptyList())
    private val mutable=MutableStateFlow(array(prefs.getString("rows","[]") ?: "[]"))
    val rows=mutable.asStateFlow()
    private val seen=prefs.getStringSet("seen",emptySet()).orEmpty().toMutableSet()
    private val hidden=prefs.getStringSet("hidden",emptySet()).orEmpty().toMutableSet()
    @Synchronized fun merge(device:String,name:String,snapshot:JSONObject){
        val current=mutable.value.associateBy{it.getString("key")}.toMutableMap()
        val exchanges=snapshot.optJSONArray("exchanges") ?: return
        var changed=false
        for(i in 0 until exchanges.length()){
            val exchange=exchanges.getJSONObject(i)
            val identity=JSONArray(listOf(device,exchange.optString("conversationId"),exchange.opt("position") ?: exchange.optString("id"))).toString()
            seen.add(identity)
            if(identity in hidden)continue
            val previous=current[identity]
            val row=JSONObject().put("key",identity).put("deviceId",device).put("deviceName",name).put("exchange",exchange)
                .put("time",previous?.optDouble("time") ?: exchange.optDouble("startedAt",0.0).takeIf{it>0} ?: System.currentTimeMillis()/1000.0)
            if(previous?.toString()!=row.toString()){current[identity]=row;changed=true}
        }
        if(changed){mutable.value=current.values.sortedBy{it.optDouble("time")}.takeLast(500);prefs.edit().putString("rows",JSONArray(mutable.value).toString()).putStringSet("seen",seen.toSet()).apply()}
    }
    @Synchronized fun clear(){
        hidden.addAll(seen)
        hidden.addAll(mutable.value.map{it.getString("key")})
        mutable.value=emptyList()
        prefs.edit().putString("rows","[]").putStringSet("hidden",hidden.toSet()).apply()
    }
}
