package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.remote.FleetController
import com.wangzan101.mika.remote.readLimited
import kotlinx.coroutines.*
import org.json.JSONObject
import android.util.Base64

@Composable
fun FleetFiles(controller:FleetController,devices:List<FleetController.Device>){
    var selectedDevice by remember{mutableStateOf<FleetController.Device?>(null)}
    var all by remember{mutableStateOf(true)}
    var selected by remember{mutableStateOf(setOf<String>())}
    var results by remember{mutableStateOf(mapOf<String,String>())}
    var busy by remember{mutableStateOf(false)}
    var pending by remember{mutableStateOf(mapOf<String,String>())}
    val groups by controller.groups.collectAsState()
    val scope=rememberCoroutineScope();val context=LocalContext.current
    suspend fun upload(targets:List<FleetController.Device>){
        for(device in targets){try{
            val payload=JSONObject(pending[device.id] ?: continue)
            if(payload.optString("conversationId").isBlank()){
                payload.put("conversationId",controller.request(device,"/state").optString("currentConversationId"))
                pending=pending+(device.id to payload.toString())
            }
            require(payload.optString("conversationId").isNotBlank()){"请先打开一个聊天"}
            val response=controller.request(device,"/workspace",payload)
            pending=pending-device.id;results=results+(device.id to ("已保存："+response.getString("path")))
        }catch(e:Exception){results=results+(device.id to (e.message ?: "上传失败"))}}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){val targets=devices.filter{it.connected && (all || it.id in selected)};busy=true;scope.launch{try{
        val (name,bytes)=withContext(Dispatchers.IO){val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null} ?: "file";name to (context.contentResolver.openInputStream(uri)?.use{it.readLimited(8*1024*1024)} ?: error("无法读取文件"))}
        val id=java.util.UUID.randomUUID().toString()
        val payload=JSONObject().put("action","upload").put("id",id).put("name",name).put("data",Base64.encodeToString(bytes,Base64.NO_WRAP)).toString()
        pending=targets.associate{it.id to payload};upload(targets)
    }catch(e:Exception){results=results+("error" to (e.message ?: "无法读取文件"))}finally{busy=false}}}}
    BackHandler(selectedDevice!=null){selectedDevice=null}
    selectedDevice?.let{device->FleetWorkspaceFiles(controller,device){selectedDevice=null};return}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("文件",style=MaterialTheme.typography.headlineMedium);Row{Checkbox(checked=all,onCheckedChange={all=it},enabled=!busy);Text("上传到全部已连接设备",Modifier.padding(top=12.dp))}}
        if(groups.isNotEmpty())item{var choosing by remember{mutableStateOf(false)};Box{TextButton(enabled=!busy,onClick={choosing=true}){Text("按分组选择")};DropdownMenu(expanded=choosing,onDismissRequest={choosing=false}){groups.values.distinct().sorted().forEach{group->DropdownMenuItem(text={Text(group)},onClick={all=false;selected=groups.filterValues{it==group}.keys;choosing=false})}}}}
        if(!all)items(devices.filter{it.connected},key={"select:${it.id}"}){device->Row{Checkbox(checked=device.id in selected,onCheckedChange={selected=if(it)selected+device.id else selected-device.id},enabled=!busy);Text(device.name,Modifier.padding(top=12.dp))}}
        item{TextButton(enabled=!busy && devices.any{it.connected && (all || it.id in selected)},onClick={picker.launch(arrayOf("*/*"))}){Text("上传文件")};results["error"]?.let{Text(it,color=MaterialTheme.colorScheme.error)}}
        if(pending.isNotEmpty())item{TextButton(enabled=!busy,onClick={busy=true;scope.launch{try{upload(devices.filter{it.id in pending})}finally{busy=false}}}){Text("重试失败设备（${pending.size}）")}}
        items(devices,key={it.id}){device->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){TextButton(onClick={selectedDevice=device}){Text(device.name+" · 工作区")};results[device.id]?.let{Text(it,style=MaterialTheme.typography.bodySmall)}}}}
    }
}

@Composable
private fun FleetWorkspaceFiles(controller:FleetController,device:FleetController.Device,onBack:()->Unit){
    var conversation by remember{mutableStateOf("")}
    var path by remember{mutableStateOf("")}
    var rows by remember{mutableStateOf(listOf<JSONObject>())}
    var error by remember{mutableStateOf<String?>(null)}
    var pendingDelete by remember{mutableStateOf<JSONObject?>(null)}
    var bytes by remember{mutableStateOf<ByteArray?>(null)}
    var busy by remember{mutableStateOf(false)}
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    suspend fun call(body:JSONObject)=controller.request(device,"/workspace",body.put("conversationId",conversation))
    suspend fun load(){val requested=path;try{val response=call(JSONObject().put("action","list").put("path",requested));if(path==requested){val array=response.getJSONArray("items");rows=(0 until array.length()).map{array.getJSONObject(it)};error=null}}catch(e:Exception){error=e.message}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null){val content=bytes;scope.launch{try{withContext(Dispatchers.IO){requireNotNull(content);context.contentResolver.openOutputStream(uri)?.use{it.write(content)} ?: error("无法保存文件")}}catch(e:Exception){error=e.message}}}}
    LaunchedEffect(device.id){try{conversation=controller.request(device,"/state").optString("currentConversationId");if(conversation.isNotBlank())load() else error="请先在设备打开一个聊天"}catch(e:Exception){error=e.message ?: "设备未连接"}}
    pendingDelete?.let{file->AlertDialog(onDismissRequest={pendingDelete=null},title={Text("删除文件？")},text={Text(file.optString("name"))},confirmButton={TextButton(onClick={pendingDelete=null;scope.launch{try{call(JSONObject().put("action","delete").put("path",file.getString("path")));load()}catch(e:Exception){error=e.message}}}){Text("删除")}},dismissButton={TextButton(onClick={pendingDelete=null}){Text("取消")}})}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{TextButton(onClick=onBack){Text("返回文件")};Text(device.name,style=MaterialTheme.typography.headlineSmall);Text(path.ifEmpty{"工作区"});error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(path.isNotEmpty())TextButton(onClick={path=path.substringBeforeLast('/',"");scope.launch{load()}}){Text("上一级")};TextButton(onClick={scope.launch{load()}}){Text("刷新")}}
        items(rows,key={it.getString("path")}){file->Row{TextButton(enabled=!busy,onClick={if(file.getBoolean("directory")){path=file.getString("path");scope.launch{load()}}else{busy=true;scope.launch{try{val response=call(JSONObject().put("action","read").put("path",file.getString("path")));bytes=Base64.decode(response.getString("data"),Base64.DEFAULT);save.launch(file.getString("name"))}catch(e:Exception){error=e.message}finally{busy=false}}}}){Text((if(file.getBoolean("directory"))"▸ " else "")+file.getString("name"),Modifier.weight(1f))};if(!file.getBoolean("directory"))TextButton(enabled=!busy,onClick={pendingDelete=file}){Text("删除")}}}
    }
}
