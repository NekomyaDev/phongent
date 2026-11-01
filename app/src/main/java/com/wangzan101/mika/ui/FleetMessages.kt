package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.remote.FleetController
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun FleetMessages(controller:FleetController,devices:List<FleetController.Device>,snapshots:Map<String,JSONObject>){
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{context.getSharedPreferences("notification-read",0)}
    var read by remember{mutableStateOf(prefs.getString("keys","[]")?.let{raw->runCatching{val a=org.json.JSONArray(raw);List(a.length()){a.getString(it)}}.getOrNull()} ?: emptyList<String>())}
    fun key(device:String,row:JSONObject):String {
        val raw=org.json.JSONArray(listOf(device,row.optString("id"),row.optString("time"),row.optString("text"))).toString()
        return java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)}
    }
    fun unread(device:String,rows:List<JSONObject>)=rows.count{key(device,it) !in read}
    fun rows(device:String):List<JSONObject>{val a=snapshots[device]?.optJSONArray("messages") ?: return emptyList();return (0 until a.length()).map{a.getJSONObject(it)}.sortedByDescending{it.optDouble("time")}}
    var deviceId by remember{mutableStateOf<String?>(null)}
    var app by remember{mutableStateOf<String?>(null)}
    var source by remember{mutableStateOf<String?>(null)}
    var replying by remember{mutableStateOf<JSONObject?>(null)}
    var reply by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val device=devices.firstOrNull{it.id==deviceId}
    val messages=deviceId?.let{rows(it)} ?: emptyList()
    val visible=if(source==null)emptyList() else messages.filter{it.optString("app")==app && it.optString("conversationId",it.optString("title"))==source}
    val visibleKeys=visible.map{key(deviceId.orEmpty(),it)}
    LaunchedEffect(visibleKeys){
        val fresh=visibleKeys.filter{it !in read}
        if(fresh.isNotEmpty()){read=(read+fresh).takeLast(10000);prefs.edit().putString("keys",org.json.JSONArray(read).toString()).apply()}
    }
    fun back(){when{source!=null->source=null;app!=null->app=null;else->deviceId=null};status="";reply=""}
    BackHandler(deviceId!=null){back()}
    fun action(block:suspend ()->Unit){busy=true;scope.launch{try{block()}catch(e:Exception){status=e.message ?: "失败"}finally{busy=false}}}
    replying?.let{row->device?.let{target->FleetReplyWindow(controller,target,row,onDismiss={replying=null})}}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{if(deviceId!=null)TextButton(onClick={back()}){Text("返回")};Text(source?.let{messages.firstOrNull{it.optString("conversationId",it.optString("title"))==source}?.optString("title")} ?: app ?: device?.name ?: "消息",style=MaterialTheme.typography.headlineMedium)}
        when{
            deviceId==null->items(devices,key={it.id}){row->Card(Modifier.fillMaxWidth().clickable{deviceId=row.id}){Row(Modifier.padding(16.dp)){Text(row.name,Modifier.weight(1f));Text("${unread(row.id,rows(row.id))} 未读")}}}
            app==null->items(messages.groupBy{it.optString("app")}.entries.toList(),key={it.key}){(name,rows)->Card(Modifier.fillMaxWidth().clickable{app=name}){Column(Modifier.padding(16.dp)){Text("$name · ${unread(deviceId.orEmpty(),rows)} 未读");Text(rows.first().optString("text"),maxLines=1,style=MaterialTheme.typography.bodySmall)}}}
            source==null->items(messages.filter{it.optString("app")==app}.groupBy{it.optString("conversationId",it.optString("title"))}.entries.toList(),key={it.key}){(id,rows)->Card(Modifier.fillMaxWidth().clickable{source=id}){Column(Modifier.padding(16.dp)){Text(rows.first().optString("title"));Text(rows.first().optString("text"),maxLines=2);Text("${rows.size} 条通知 · ${unread(deviceId.orEmpty(),rows)} 未读",style=MaterialTheme.typography.labelSmall)}}}
            else->{
                val rows=messages.filter{it.optString("app")==app && it.optString("conversationId",it.optString("title"))==source}
                items(rows,key={it.optString("id")}){row->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(row.optString("text"));TextButton(enabled=!busy && row.optBoolean("canOpen") && device?.connected==true,onClick={device?.let{target->action{controller.request(target,"/notification/open",JSONObject().put("id",row.getString("id")));status="已请求打开原通知"}}}){Text("在设备打开原通知")}}}}
                item{TextButton(enabled=device?.connected==true,onClick={replying=rows.firstOrNull()}){Text("通过 agent 回复")}}
            }
        }
        item{if(status.isNotEmpty())Text(status)}
    }
}
