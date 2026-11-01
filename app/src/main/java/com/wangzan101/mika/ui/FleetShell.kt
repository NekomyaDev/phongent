package com.wangzan101.mika.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.wangzan101.mika.remote.FleetController
import com.wangzan101.mika.remote.readLimited
import kotlinx.coroutines.launch
import org.json.JSONObject

private class PendingFleetSend(val device: FleetController.Device, val text: String, val images: List<ByteArray>) {
    val id = java.util.UUID.randomUUID().toString()
    var attachment: FleetController.AttachmentBundle? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FleetShell(localChat:@Composable ()->Unit) {
    val context=LocalContext.current
    val controller=remember{FleetController(context)}
    val approval by com.wangzan101.mika.remote.LanAccess.pending.collectAsState()
    approval?.let{offer->
        fun decide(allow:Boolean){com.wangzan101.mika.remote.LanService.access?.decide(offer.id,allow)}
        AlertDialog(onDismissRequest={decide(false)},title={Text("允许 ${offer.name} 连接？")},
            text={Text("允许后可读取消息、查看画面并下发任务。")},
            confirmButton={TextButton(onClick={decide(true)}){Text("允许连接")}},
            dismissButton={TextButton(onClick={decide(false)}){Text("拒绝")}})
    }
    val devices by controller.devices.collectAsState()
    val connecting by controller.connecting.collectAsState()
    var collapsedGroups by remember{mutableStateOf(setOf<String>())}
    var connectionError by remember{mutableStateOf<String?>(null)}
    connectionError?.let{message->AlertDialog(onDismissRequest={connectionError=null},title={Text("连接提醒")},text={Text(message)},confirmButton={TextButton(onClick={connectionError=null}){Text("好")}})}
    val snapshots by controller.snapshots.collectAsState()
    val history by controller.history.rows.collectAsState()
    var showDeliveries by remember{mutableStateOf(false)}
    val chatList=androidx.compose.foundation.lazy.rememberLazyListState()
    var previousRows by remember{mutableIntStateOf(0)}
    val providers=com.wangzan101.mika.settings.ProviderStore.get(context)
    val configs by providers.configs.collectAsState()
    val connectedProviders by providers.providers.collectAsState()
    var dispatch by remember{mutableStateOf(controller.dispatchModel)}
    val scope=rememberCoroutineScope()
    var tab by remember{mutableIntStateOf(1)}
    var target by remember{mutableStateOf<String?>("all")}
    var activeGroup by remember{mutableStateOf<String?>(null)}
    var input by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("")}
    val historyPrefs=remember{context.getSharedPreferences("fleet-history",0)}
    var log by remember{mutableStateOf(runCatching{val saved=org.json.JSONArray(historyPrefs.getString("messages","[]"));List(saved.length()){saved.getString(it)}}.getOrDefault(emptyList<String>()))}
    LaunchedEffect(log){historyPrefs.edit().putString("messages",org.json.JSONArray(log.takeLast(500)).toString()).apply()}
    var renaming by remember{mutableStateOf<FleetController.Device?>(null)}
    val groups by controller.groups.collectAsState()
    var newGroup by remember{mutableStateOf("")}
    var newName by remember{mutableStateOf("")}
    var templates by remember{mutableStateOf(historyPrefs.getString("templates","{}")?.let{runCatching{val json=JSONObject(it);json.keys().asSequence().associateWith{key->json.getString(key)}}.getOrNull()} ?: emptyMap<String,String>())}
    var savingTemplate by remember{mutableStateOf(false)}
    var templateName by remember{mutableStateOf("")}
    LaunchedEffect(templates){historyPrefs.edit().putString("templates",JSONObject(templates).toString()).apply()}
    var clearHistory by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var photos by remember{mutableStateOf(listOf<ByteArray>())}
    var pendingSends by remember { mutableStateOf(listOf<PendingFleetSend>()) }
    var submittedDraft by remember { mutableStateOf("") }
    suspend fun deliverPending() {
        val submittedImages = pendingSends.firstOrNull()?.images
        val failed = mutableListOf<PendingFleetSend>()
        for (pending in pendingSends) {
            try {
                val device = devices.firstOrNull { it.id == pending.device.id && it.connected }
                    ?: error("设备已断开")
                val attachment = pending.attachment ?: controller.attach(device, pending.images).also { pending.attachment = it }
                val text = pending.text + if (attachment.text.isBlank()) "" else "\n" + attachment.text
                val result = controller.task(device, text, attachment.conversationId, pending.id)
                log = log + "${device.name}：${result.optString("status")}"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                failed += pending
                log = log + "${pending.device.name}：${error.message}"
            }
        }
        pendingSends = failed
        if (failed.isEmpty()) { if(input==submittedDraft)input = ""; if(photos==submittedImages)photos = emptyList(); status = "" }
        else status = "${failed.size} 台设备尚未确认接收，草稿已保留"
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){uris->
        scope.launch{busy=true;try{photos=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){uris.take(4).map{uri->context.contentResolver.openInputStream(uri)?.use{stream->val bytes=stream.readLimited(8*1024*1024);require(bytes.size<=8*1024*1024){"图片超过 8 MB"};bytes} ?: error("图片不可读")}}}catch(e:Exception){status=e.message ?: "无法读取图片"}finally{busy=false}}
    }
    DisposableEffect(Unit){controller.start();onDispose{controller.stop()}}
    fun request(block:suspend ()->Unit){busy=true;scope.launch{try{block()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){status=e.message ?: "请求失败"}finally{busy=false}}}
    renaming?.let{device->AlertDialog(onDismissRequest={renaming=null},title={Text("设备信息")},text={Column{OutlinedTextField(value=newName,onValueChange={newName=it.take(40)},label={Text("名称")},singleLine=true);OutlinedTextField(value=newGroup,onValueChange={newGroup=it.take(40)},label={Text("分组，留空取消")},singleLine=true)}},confirmButton={TextButton(enabled=newName.isNotBlank(),onClick={controller.rename(device.id,newName);controller.setGroup(device.id,newGroup);renaming=null}){Text("保存")}},dismissButton={TextButton(onClick={renaming=null}){Text("取消")}})}
    if(savingTemplate)AlertDialog(onDismissRequest={savingTemplate=false},title={Text("保存任务模板")},text={OutlinedTextField(value=templateName,onValueChange={templateName=it.take(40)},label={Text("名称")})},confirmButton={TextButton(enabled=templateName.isNotBlank(),onClick={templates=templates+(templateName.trim() to input.take(8000));savingTemplate=false}){Text("保存")}},dismissButton={TextButton(onClick={savingTemplate=false}){Text("取消")}})
    if(clearHistory)AlertDialog(onDismissRequest={clearHistory=false},title={Text("删除聊天记录？")},text={Text("删除此主控上的群聊记录。")},confirmButton={TextButton(onClick={log=emptyList();controller.history.clear();clearHistory=false}){Text("删除")}},dismissButton={TextButton(onClick={clearHistory=false}){Text("取消")}})
    Column(Modifier.fillMaxSize().statusBarsPadding()){
        Box(Modifier.weight(1f)){
            when(tab){
                0->FleetRadar(devices,busy,status,onChat={device->target=if(device.id=="local")null else device.id;tab=1},onEdit={device->renaming=device;newName=device.name;newGroup=groups[device.id] ?: ""},onConnect={device->request{try{controller.connect(device);status="已连接 ${device.name}";target=device.id;tab=1}catch(e:Exception){connectionError=e.message ?: "重连失败"}}},connecting=connecting,groups=groups,onGroup={ids,name->ids.forEach{controller.setGroup(it,name)}},onConnectAll={targets->request{val failures=controller.connectAll(targets);if(failures.isNotEmpty())connectionError="连接失败："+failures.joinToString("、")}})
                1->if(target==null)localChat() else Column(Modifier.fillMaxSize()){
                Row(Modifier.weight(1f)){
                    LazyColumn(Modifier.width(76.dp)){
                        item{TextButton(onClick={target="all";activeGroup=null}){Text("全部")}}
                        devices.filter{it.connected}.groupBy{groups[it.id] ?: "默认组"}.toSortedMap().forEach{(group,members)->
                            item(key="group:$group"){Row{TextButton(onClick={target="all";activeGroup=group},modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)){Text(group,style=MaterialTheme.typography.labelSmall)};TextButton(onClick={collapsedGroups=if(group in collapsedGroups)collapsedGroups-group else collapsedGroups+group},modifier=Modifier.width(24.dp),contentPadding=PaddingValues(0.dp)){Text(if(group in collapsedGroups)"›" else "⌄")}}}
                            if(group !in collapsedGroups)items(members,key={it.id}){device->FleetAvatarButton(device.id,device.name,target==device.id,onClick={target=device.id},onMention={target="all";activeGroup=null;input+=" @${device.name} "})}
                        }
                    }
                    if(target==null)localChat() else Column(Modifier.fillMaxSize().padding(16.dp)){

                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(devices.firstOrNull{it.id==target}?.name ?: activeGroup ?: "全部设备",style=MaterialTheme.typography.titleLarge);var menu by remember{mutableStateOf(false)};Box{TextButton(onClick={menu=true}){Text("•••")};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){DropdownMenuItem(text={Text("保存输入为模板")},enabled=input.isNotBlank(),onClick={templateName=input.take(24);savingTemplate=true;menu=false});templates.keys.sorted().forEach{title->DropdownMenuItem(text={Text("模板 · $title")},onClick={input=templates[title] ?: "";target="all";menu=false},trailingIcon={TextButton(onClick={templates=templates-title}){Text("删除")}})};groups.values.distinct().sorted().forEach{group->DropdownMenuItem(text={Text("分组 · $group")},onClick={activeGroup=group;target="all";menu=false})};DropdownMenuItem(text={Text("删除记录")},onClick={menu=false;clearHistory=true})}}}
                    val visibleHistory=history.filter{row->val id=row.getString("deviceId");(target=="all" && (activeGroup==null || groups[id]==activeGroup)) || target==id}
                    LaunchedEffect(visibleHistory.size,target,activeGroup){
                        val nearEnd=previousRows==0 || (chatList.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)>=previousRows-2
                        previousRows=visibleHistory.size
                        if(nearEnd && visibleHistory.isNotEmpty())chatList.scrollToItem((visibleHistory.size-1)+(if(log.isNotEmpty())1 else 0)+(if(showDeliveries)log.size else 0))
                    }
                    LazyColumn(Modifier.weight(1f),state=chatList){
                        if(log.isNotEmpty())item{TextButton(onClick={showDeliveries=!showDeliveries}){Text(if(showDeliveries)"收起发送记录" else "发送记录")}}
                        if(showDeliveries)items(log){Text(it,Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.bodySmall)}
                        items(visibleHistory,key={it.getString("key")}){row->
                            val id=row.getString("deviceId")
                            val device=devices.firstOrNull{it.id==id} ?: FleetController.Device(id,row.getString("deviceName"),"")
                            val exchange=row.getJSONObject("exchange")
                            val goal=exchange.optString("goal")
                            if(goal.isNotBlank())Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(20.dp),modifier=Modifier.widthIn(max=320.dp).padding(top=12.dp)){Text(goal,Modifier.padding(12.dp))}}
                            Card(Modifier.fillMaxWidth().padding(vertical=8.dp)){Column(Modifier.padding(12.dp)){
                                Text(device.name,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                FleetExchange(controller,device,exchange)
                            }}
                        }
                    }

                    FleetVideoWindows(controller,devices.filter{(target=="all" && (activeGroup==null || (groups[it.id] ?: "默认组")==activeGroup)) || it.id==target})
                }}
                Column(Modifier.fillMaxWidth().imePadding().padding(horizontal=14.dp,vertical=8.dp)){
                    if(status.isNotBlank())Text(status,style=MaterialTheme.typography.labelSmall)
                    if(photos.isNotEmpty())Row{Text("${photos.size} 张图片",style=MaterialTheme.typography.labelSmall);TextButton(enabled=!busy && pendingSends.isEmpty(),onClick={photos=emptyList()}){Text("移除")}}
                    if (pendingSends.isNotEmpty()) Row {
                        TextButton(enabled=!busy,onClick={request{deliverPending()}}){Text("重试未确认设备")}
                        TextButton(enabled=!busy,onClick={pendingSends=emptyList();status=""}){Text("结束本次发送")}
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    TextField(value=input,onValueChange={input=it},enabled=!busy && pendingSends.isEmpty(),placeholder={Text("发消息或描述任务")},minLines=1,maxLines=5,shape=RoundedCornerShape(20.dp),colors=TextFieldDefaults.colors(focusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedIndicatorColor=androidx.compose.ui.graphics.Color.Transparent,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.weight(1f))
                    IconButton(enabled=!busy && pendingSends.isEmpty(),onClick={picker.launch("image/*")},modifier=Modifier.size(44.dp)){androidx.compose.foundation.Canvas(Modifier.size(30.dp)){val c=androidx.compose.ui.graphics.Color.Gray;drawCircle(c,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()));drawLine(c,androidx.compose.ui.geometry.Offset(size.width*.25f,center.y),androidx.compose.ui.geometry.Offset(size.width*.75f,center.y),2.dp.toPx());drawLine(c,androidx.compose.ui.geometry.Offset(center.x,size.height*.25f),androidx.compose.ui.geometry.Offset(center.x,size.height*.75f),2.dp.toPx())}}
                    Button(enabled=!busy && pendingSends.isEmpty() && input.isNotBlank(),onClick={
                        val text=input
                        val images=photos.toList()
                        val targets=devices.filter{it.connected && ((target=="all" && (activeGroup==null || (groups[it.id] ?: "默认组")==activeGroup)) || it.id==target)}
                        request{
                            require(targets.isNotEmpty()){"没有可用设备"}
                            val planned=controller.distribute(text,targets,images.size)
                            val tasks=planned.tasks
                            submittedDraft=text
                            pendingSends=targets.mapNotNull{device->tasks[device.id]?.let{PendingFleetSend(device,it,images+planned.images[device.id].orEmpty())}}
                            log=log+"你：$text"
                            deliverPending()
                        }
                    },contentPadding=PaddingValues(0.dp),modifier=Modifier.size(44.dp),shape=androidx.compose.foundation.shape.CircleShape){Text("↑",style=MaterialTheme.typography.titleLarge)}
                    }
                }}

                2->FleetMessages(controller,devices,snapshots)
                3->FleetManagementHome(controller,devices)
            }
        }
        NavigationBar{listOf("设备","agent","消息","设置").forEachIndexed{index,title->NavigationBarItem(selected=tab==index,onClick={tab=index},icon={Icon(listOf(com.wangzan101.mika.ui.theme.MikaIcons.Phone,com.wangzan101.mika.ui.theme.MikaIcons.Spark,com.wangzan101.mika.ui.theme.MikaIcons.Message,com.wangzan101.mika.ui.theme.MikaIcons.Gear)[index],contentDescription=title)},label={Text(title)})}}
    }
}
