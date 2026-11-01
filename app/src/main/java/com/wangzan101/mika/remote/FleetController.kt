package com.wangzan101.mika.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Android controller uses the same approved LAN protocol as iOS. */
class FleetController(context:Context) {
    data class Device(val id:String,val name:String,val url:String,val connected:Boolean=false,val error:String?=null)
    private val context=context.applicationContext
    private val prefs=this.context.getSharedPreferences("fleet-controller",0)
    private val groupState=MutableStateFlow(prefs.all.filterKeys{it.startsWith("group:")}.mapKeys{it.key.removePrefix("group:")}.mapValues{it.value.toString()})
    val groups=groupState.asStateFlow()
    private val windowState=MutableStateFlow(prefs.getBoolean("showWindows",true))
    val showWindows=windowState.asStateFlow()
    fun setShowWindows(value:Boolean){prefs.edit().putBoolean("showWindows",value).apply();windowState.value=value}
    private val connectingState=MutableStateFlow<Set<String>>(emptySet())
    val connecting=connectingState.asStateFlow()
    private val connectLock=Any()

    fun setGroup(id:String,name:String){val clean=name.trim().take(40);if(clean.isEmpty() || clean=="默认组"){prefs.edit().remove("group:$id").apply();groupState.value=groupState.value-id}else{prefs.edit().putString("group:$id",clean).apply();groupState.value=groupState.value+(id to clean)}}
    private val nsd=this.context.getSystemService(NsdManager::class.java)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutable=MutableStateFlow<List<Device>>(listOf(Device("local", prefs.getString("name:local",null) ?: "本机","local",true)))
    val devices=mutable.asStateFlow()
    val history=FleetHistory(this.context)
    private val states=MutableStateFlow<Map<String,JSONObject>>(emptyMap())
    val snapshots=states.asStateFlow()
    @Synchronized private fun saveState(id:String,state:JSONObject){states.value=states.value+(id to state);history.merge(id,mutable.value.firstOrNull{it.id==id}?.name ?: state.optString("device",id),state)}
    private val streams=java.util.concurrent.ConcurrentHashMap<String,Job>()
    private val connections=java.util.concurrent.ConcurrentHashMap<String,HttpURLConnection>()
    private val streaming=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val localBridge by lazy{RemoteBridge(this.context as com.wangzan101.mika.MikaApp)}
    private var discovery:NsdManager.DiscoveryListener?=null
    private val resolving=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val serviceIds=java.util.concurrent.ConcurrentHashMap<String,String>()
    private val resolveFailures=java.util.concurrent.ConcurrentHashMap<String,Int>()
    @Synchronized private fun update(device:Device){
        val prior=mutable.value
        mutable.value=if(prior.any{it.id==device.id})prior.map{if(it.id==device.id)device else it}else prior+device
    }
    suspend fun rename(id:String,name:String){
        val clean=name.trim().take(40);require(clean.isNotEmpty())
        val device=mutable.value.first{it.id==id}
        request(device,"/device/name",JSONObject().put("name",clean))
        prefs.edit().putString("name:$id",clean).apply()
        mutable.value.firstOrNull{it.id==id}?.let{update(it.copy(name=clean))}
    }
    private val nameListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->
        if(key=="name:local")mutable.value.firstOrNull{it.id=="local"}?.let{update(it.copy(name=DeviceIdentity.name(this.context)))}
    }
    init{prefs.registerOnSharedPreferenceChangeListener(nameListener)}
    fun start(){
        if(discovery!=null)return
        val listener=object:NsdManager.DiscoveryListener{
            override fun onDiscoveryStarted(type:String){}
            override fun onDiscoveryStopped(type:String){}
            override fun onStartDiscoveryFailed(type:String,code:Int){discovery=null}
            override fun onStopDiscoveryFailed(type:String,code:Int){}
            override fun onServiceLost(service:NsdServiceInfo){
                resolveFailures.remove(service.serviceName)
                val id=serviceIds.remove(service.serviceName) ?: service.attributes["id"]?.toString(Charsets.UTF_8)
                if(id!=null && id !in serviceIds.values)mutable.value.firstOrNull{it.id==id}?.let{update(it.copy(connected=false))}
            }
            override fun onServiceFound(service:NsdServiceInfo){
                if(!resolving.add(service.serviceName))return
                @Suppress("DEPRECATION")
                nsd.resolveService(service,object:NsdManager.ResolveListener{
                    override fun onResolveFailed(info:NsdServiceInfo,code:Int){
                        resolving.remove(service.serviceName)
                        val failures=(resolveFailures[service.serviceName] ?: 0)+1;resolveFailures[service.serviceName]=failures
                        if(failures<=3)scope.launch{delay(failures*700L);discovery?.onServiceFound(service)}
                    }
                    override fun onServiceResolved(info:NsdServiceInfo){
                        resolving.remove(service.serviceName)
                        val id=info.attributes["id"]?.toString(Charsets.UTF_8) ?: return
                        if(id==context.getSharedPreferences("lan-identity",0).getString("id",null))return
                        serviceIds[service.serviceName]=id;resolveFailures.remove(service.serviceName)
                        val host=info.host?.hostAddress ?: return
                        val address=if(host.contains(':'))"[$host]" else host
                        val name=prefs.getString("name:$id",null) ?: info.attributes["name"]?.toString(Charsets.UTF_8) ?: info.serviceName
                        val device=Device(id,name,"http://$address:${info.port}")
                        val prior=mutable.value.firstOrNull{it.id==id}
                        if(prior!=null && prior.url!=device.url){
                            streams.remove(id)?.cancel();connections.remove(id)?.disconnect();streaming.remove(id)
                        }
                        update(device.copy(connected=prior?.connected==true && prior.url==device.url));scope.launch{refresh(device)}
                    }
                })
            }
        }
        discovery=listener;nsd.discoverServices("_mika-agent._tcp.",NsdManager.PROTOCOL_DNS_SD,listener)
        scope.launch{while(discovery!=null){supervisorScope{devices.value.map{device->async{ensureStream(device);if(device.id !in streaming)refresh(device)}}.awaitAll()};delay(5000)}}
    }
    fun stop(){discovery?.let{runCatching{nsd.stopServiceDiscovery(it)}};discovery=null;connections.values.forEach{it.disconnect()};connections.clear();streams.clear();streaming.clear();scope.coroutineContext.cancelChildren()}
    suspend fun request(device:Device,path:String,body:JSONObject?=null,authenticated:Boolean=true):JSONObject=withContext(Dispatchers.IO){
        if(device.id=="local") {
            val app=context as com.wangzan101.mika.MikaApp
            return@withContext when(path){
                "/browser"->RemoteBrowser(app).let{if(body==null)it.read() else it.update(body)}
                "/models/settings"->RemoteModels(app).let{if(body==null)it.read() else it.update(body)}
                "/settings"->RemoteSettings(app).let{if(body==null)it.read() else it.patch(body)}
                "/knowledge"->RemoteKnowledge(app).let{if(body==null)it.list() else it.add(body)}
                "/plans"->RemoteAutomation(app).let{if(body==null)it.plans() else it.plan(body)}
                "/routes"->RemoteAutomation(app).let{if(body==null)it.routes() else it.route(body)}
                "/workspace"->RemoteWorkspace(app).request(body!!)
                "/usage"->RemoteDiagnostics(app).usage()
                "/diagnostics"->RemoteDiagnostics(app).diagnostics()
                "/device/name"->DeviceIdentity.rename(app,body!!)
                "/state"->localBridge.snapshot()
                "/tasks"->RemoteTaskQueue.get(app).let{if(body==null)it.list() else if(body.optString("action")=="cancel")it.cancel(body.getString("id")) else if(body.optString("action")=="acknowledge")it.acknowledge(body.getString("id")) else if(body.optString("action")=="retry")it.retry(body.getString("id")) else it.enqueue(body)}
                "/attachments"->RemoteAttachments(app).upload(body!!)
                "/files"->RemoteAttachments(app).download(body!!)
                "/notification/open"->{MessageListener.open(body!!.getString("id"));JSONObject().put("status","dispatched")}
                "/command"->withContext(Dispatchers.Main) local@{
                    val command=body ?: error("empty_command")
                    val id=command.getString("id")
                    val history=app.getSharedPreferences("remote-commands",0)
                    if(history.contains(id))return@local JSONObject().put("status",history.getString(id,"uncertain"))
                    val action=command.getString("action")
                    require(action in listOf("task","stop","answer"))
                    if(action=="task"){
                        val expected=command.optString("conversationId")
                        check(expected.isEmpty() || expected==app.chat.ui.value.conversationId){"聊天已切换，请重新发送"}
                        check(!app.taskState.busy.value){"本机正忙"}
                        val conversation=app.chat.ui.value.conversationId ?: com.wangzan101.mika.chat.ConversationStore.shared(app).newConversation().also{app.chat.openConversation(it)}
                        history.edit().putString(id,"uncertain").putString("conversation:$id",conversation).commit()
                        com.wangzan101.mika.task.TaskService.startMcp(app,command.getString("text"),conversation,id,com.wangzan101.mika.core.run.Attendance.Reachable)
                    }else{
                        val key=command.getString("runId")
                        val run=app.taskState.runs.value.firstOrNull{it.key==key} ?: error("任务已结束")
                        if(action=="stop")com.wangzan101.mika.task.TaskService.cancel(app,key)
                        else{check(run.state.value is com.wangzan101.mika.core.agent.TaskState.WaitingInput);com.wangzan101.mika.task.TaskService.answer(app,command.getString("text"),key)}
                    }
                    history.edit().putString(id,"accepted").commit()
                    JSONObject().put("status","accepted")
                }
                else->error("本机不支持此接口")
            }
        }
        val connection=URL(device.url+path).openConnection() as HttpURLConnection
        try{
            connection.connectTimeout=5000;connection.readTimeout=if(path=="/models/settings" && body!=null)30000 else 15000
            if(authenticated){val token=prefs.getString("token:${device.id}",null) ?: error("请先申请连接");connection.setRequestProperty("Authorization","Bearer $token")}
            if(body!=null){connection.requestMethod="POST";connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use{it.write(body.toString().toByteArray())}}
            val code=connection.responseCode
            val text=(if(code in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText()} ?: "{}"
            val result=JSONObject(text)
            check(code in 200..299){result.optString("error","HTTP $code")}
            result
        }finally{connection.disconnect()}
    }
    @Synchronized private fun receiveEvent(device:Device,event:JSONObject){
        if(mutable.value.firstOrNull{it.id==device.id}?.url!=device.url)return
        when(event.optString("kind")){
            "snapshot"->saveState(device.id,event.getJSONObject("snapshot"))
            "state"->{val state=event.getJSONObject("snapshot");state.put("messages",states.value[device.id]?.optJSONArray("messages") ?: org.json.JSONArray());saveState(device.id,state)}
            "messages"->{val previous=states.value[device.id] ?: return;val state=JSONObject(previous.toString());val rows=linkedMapOf<String,JSONObject>();val existing=state.optJSONArray("messages") ?: org.json.JSONArray();for(i in 0 until existing.length()){val row=existing.getJSONObject(i);rows[row.getString("id")]=row};val removed=event.optJSONArray("removed") ?: org.json.JSONArray();for(i in 0 until removed.length())rows.remove(removed.getString(i));val changed=event.optJSONArray("upserts") ?: org.json.JSONArray();for(i in 0 until changed.length()){val row=changed.getJSONObject(i);rows[row.getString("id")]=row};state.put("messages",org.json.JSONArray(rows.values.toList()));saveState(device.id,state)}
        }
        update(device.copy(name=prefs.getString("name:${device.id}",device.name) ?: device.name,connected=true,error=null))
    }
    private fun ensureStream(device:Device){
        if(device.id=="local" || !prefs.contains("token:${device.id}") || streams[device.id]?.isActive==true)return
        streams[device.id]=scope.launch{
            var backoff=1000L
            while(isActive){var connection:HttpURLConnection?=null
                try{
                    connection=(URL(device.url+"/events?heartbeat=30").openConnection() as HttpURLConnection).apply{connectTimeout=5000;readTimeout=40000;setRequestProperty("Authorization","Bearer "+prefs.getString("token:${device.id}",""))}
                    connections[device.id]=connection
                    check(connection.responseCode==200)
                    connection.inputStream.bufferedReader().use{reader->while(isActive){val line=reader.readLine() ?: error("连接已断开");if(line.startsWith("data: ")){receiveEvent(device,JSONObject(line.substring(6)));streaming.add(device.id);backoff=1000}}}
                }catch(e:CancellationException){throw e}catch(_:Exception){streaming.remove(device.id)}finally{streaming.remove(device.id);connection?.let{connections.remove(device.id,it)};connection?.disconnect()}
                delay(backoff);backoff=(backoff*2).coerceAtMost(30000)
            }
        }
    }
    fun videoConnection(device:Device):HttpURLConnection {
        require(device.id!="local")
        val token=prefs.getString("token:${device.id}",null) ?: error("请先连接设备")
        return (URL(device.url+"/virtual/video").openConnection() as HttpURLConnection).apply{connectTimeout=5000;readTimeout=3000;setRequestProperty("Authorization","Bearer $token")}
    }
    suspend fun refresh(device:Device){if(device.id!="local" && !prefs.contains("token:${device.id}"))return;try{val state=request(device,"/state");if(mutable.value.firstOrNull{it.id==device.id}?.url!=device.url)return;saveState(device.id,state);update(device.copy(name=prefs.getString("name:${device.id}",device.name) ?: device.name,connected=true,error=null))}catch(e:CancellationException){throw e}catch(e:Exception){if(mutable.value.firstOrNull{it.id==device.id}?.url!=device.url)return;update(device.copy(name=prefs.getString("name:${device.id}",device.name) ?: device.name,connected=false,error=e.message))}}
    suspend fun connect(device:Device){
        synchronized(connectLock){check(device.id !in connectingState.value){"设备正在连接"};connectingState.value=connectingState.value+device.id}
        try{
            if(prefs.contains("token:${device.id}")){
                refresh(device)
                check(mutable.value.firstOrNull{it.id==device.id}?.connected==true){mutable.value.firstOrNull{it.id==device.id}?.error ?: "重连失败"}
                return
            }
            pairDevice(device)
        }catch(e:CancellationException){throw e}catch(e:Exception){mutable.value.firstOrNull{it.id==device.id && it.url==device.url}?.let{update(it.copy(connected=false,error=e.message))};throw e}
        finally{synchronized(connectLock){connectingState.value=connectingState.value-device.id}}
    }
    suspend fun connectAll(targets:List<Device>):List<String> = coroutineScope {
        val failed=java.util.Collections.synchronizedList(mutableListOf<String>())
        for(batch in targets.chunked(3)){batch.map{device->async{try{connect(device)}catch(e:CancellationException){throw e}catch(_:Exception){failed.add(device.name)}}}.awaitAll()}
        failed.toList()
    }
    private suspend fun pairDevice(device:Device){
        val nonce=UUID.randomUUID().toString()+UUID.randomUUID().toString()
        request(device,"/pair",JSONObject().put("requestId",nonce).put("name",Build.MODEL),false)
        repeat(85){delay(1000);val result=request(device,"/pair/$nonce",authenticated=false)
            when(result.optString("status")){
                "approved"->{prefs.edit().putString("token:${device.id}",result.getString("token")).commit();refresh(device);check(mutable.value.firstOrNull{it.id==device.id}?.connected==true){"已授权，但设备连接失败"};return}
                "denied","expired"->error("连接申请被拒绝或已过期")
            }
        };error("等待授权超时")
    }
    var dispatchModel:String
        get()=prefs.getString("dispatchModel","") ?: ""
        set(value){prefs.edit().putString("dispatchModel",value).apply()}
    data class Dispatch(val tasks:Map<String,String>,val images:Map<String,List<ByteArray>>)
    suspend fun distribute(text:String,targets:List<Device>,imageCount:Int=0):Dispatch {
        val store=com.wangzan101.mika.settings.ProviderStore.get(context)
        val model=store.controllerModel(dispatchModel) ?: error("请在设置中选择任务下发模型")
        val roster=org.json.JSONArray(targets.map{JSONObject().put("id",it.id).put("name",it.name).put("group",groups.value[it.id] ?: "默认组")})
        val reply=model.complete("你是主控任务策划与内容创作 AI。设备清单：$roster。清单包含group分组：组任务分配给组内所有设备，组内点名任务优先，其余成员执行组任务。不同组可以执行不同任务，歧义时澄清。用户附图数量：$imageCount，会随任务下发，不可凭空描述图片内容。输出纯JSON对象，键为设备id，值为对象{task:完整任务文字,cards:[{title:图卡标题,body:图卡正文,accent:十六进制颜色}]}。普通任务cards为空。用户可以说A做什么B做什么，不需要@；结合名称和唯一简称解析对象，@只是快捷方式。其余指尚未指定的设备。无指定对象默认全部。矩阵图文任务由你先按每个账号生成独立完整标题、正文、标签和1至4张图文卡片，然后把各自完整文案和执行要求写入task；主控会将cards渲染成PNG并附给对应设备，不要让执行设备重复创作文案。每张标题最多60字、正文最多400字，可分成多张。只有用户要求图文创作时才生成cards，不添加用户没要求的发布行为。不要声称这些文字图卡是照片；如用户明确要求摄影或真实图片且未附图片，返回error说明需要配图。名字歧义或设备不存在返回{error:说明}。同一设备任务合并，不能给未指派设备发送其他设备专属任务。",listOf(com.wangzan101.mika.core.model.Message(com.wangzan101.mika.core.model.Role.User,listOf(com.wangzan101.mika.core.model.Part.Text(text)),com.wangzan101.mika.core.model.Author.User)),emptyList())
        val raw=reply.parts.filterIsInstance<com.wangzan101.mika.core.model.Part.Text>().joinToString(""){it.text}.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val objectValue=JSONObject(raw)
        if(objectValue.has("error"))error(objectValue.getString("error"))
        val tasks=linkedMapOf<String,String>();val images=linkedMapOf<String,List<ByteArray>>()
        for(id in objectValue.keys()){
            require(targets.any{it.id==id}){"无效设备，未发送"}
            val value=objectValue.optJSONObject(id)
            val task=value?.getString("task") ?: objectValue.getString(id)
            require(task.isNotBlank() && task.length<=8000){"任务内容无效"};tasks[id]=task
            val cards=value?.optJSONArray("cards")
            require((cards?.length() ?: 0)<=4){"单设备图卡最多4张"}
            images[id]=if(cards==null)emptyList() else (0 until cards.length()).map{ContentCard.render(cards.getJSONObject(it))}
        }
        require(tasks.isNotEmpty()){"没有可下发的任务"}
        return Dispatch(tasks,images)
    }

    data class AttachmentBundle(val conversationId:String,val text:String)
    suspend fun attach(device:Device,images:List<ByteArray>):AttachmentBundle {
        if(images.isEmpty())return AttachmentBundle("", "")
        val state=request(device,"/state")
        val conversation=state.optString("currentConversationId")
        require(conversation.isNotEmpty()){"请先在 ${device.name} 打开聊天"}
        return AttachmentBundle(conversation, images.map{bytes->request(device,"/attachments",JSONObject().put("conversationId",conversation).put("data",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP))).getString("line")}.joinToString("\n"))
    }
    suspend fun task(device:Device,text:String,conversationId:String="",id:String=UUID.randomUUID().toString()):JSONObject=request(device,if(states.value[device.id]?.optInt("taskQueueVersion")==1)"/tasks" else "/command",JSONObject().put("id",id).put("action","task").put("text",text).put("conversationId",conversationId))
}
