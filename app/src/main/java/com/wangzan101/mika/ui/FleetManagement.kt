package com.wangzan101.mika.ui

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
import java.util.UUID

@Composable
fun FleetManagement(controller:FleetController,devices:List<FleetController.Device>,initialModule:String="settings") {
    val scope=rememberCoroutineScope()
    val groups by controller.groups.collectAsState()
    var all by remember{mutableStateOf(true)}
    var selected by remember{mutableStateOf(setOf<String>())}
    val module=initialModule
    var results by remember{mutableStateOf(mapOf<String,String>())}
    var data by remember{mutableStateOf(mapOf<String,JSONObject>())}
    var busy by remember{mutableStateOf(false)}
    var failedWrites by remember{mutableStateOf(mapOf<String,String>())}
    var text by remember{mutableStateOf("")}
    var title by remember{mutableStateOf("")}
    var description by remember{mutableStateOf("")}
    var role by remember{mutableStateOf("Main")}
    var providerName by remember{mutableStateOf("")}
    var providerURL by remember{mutableStateOf("")}
    var providerKey by remember{mutableStateOf("")}
    var providerModel by remember{mutableStateOf("")}
    var providerKind by remember{mutableStateOf("OpenAiCompat")}
    var browserConfirm by remember{mutableStateOf<JSONObject?>(null)}
    var browserHost by remember{mutableStateOf("")}
    var effort by remember{mutableStateOf("")}
    var hours by remember{mutableStateOf("1")}
    var editor by remember{mutableStateOf<Pair<List<FleetController.Device>,JSONObject>?>(null)}
    var requestId by remember{mutableStateOf(UUID.randomUUID().toString())}
    val targets=devices.filter{it.connected && (all || it.id in selected)}
    val path=when(module){"mcp","notifications"->"/settings";"models"->"/models/settings";"memory","skill"->"/knowledge";else->"/$module"}
    fun execute(payload:JSONObject?=null,destinations:List<FleetController.Device> = targets){
        if(busy)return
        busy=true
        val encoded=payload?.toString()
        if(encoded!=null)failedWrites=emptyMap()
        scope.launch{try{for(device in destinations){try{val response=controller.request(device,path,encoded?.let{JSONObject(it)});data=data+(device.id to response);if(encoded!=null)failedWrites=failedWrites-device.id;results=results+(device.id to if(response.has("status"))response.getString("status") else if(response.optBoolean("exactAlarm",true))"已同步" else "已保存，请在设备开启准时闹钟权限")}catch(e:Exception){if(encoded!=null)failedWrites=failedWrites+(device.id to encoded);results=results+(device.id to (e.message ?: "失败"))}}}finally{busy=false}}
    }
    browserConfirm?.let{payload->AlertDialog(onDismissRequest={browserConfirm=null},title={Text("清除所选设备的网站登录？")},text={Text("此操作会退出网站并清除网页数据。")},confirmButton={TextButton(onClick={browserConfirm=null;execute(payload)}){Text("清除")}},dismissButton={TextButton(onClick={browserConfirm=null}){Text("取消")}})}
    editor?.let{(destinations,plan)->FleetPlanEditor(controller,destinations,plan){editor=null;execute()}}
    fun common(key:String):String{val values=targets.map{data[it.id]?.opt(key)?.toString() ?: "未读取"};return if(values.distinct().size==1)values.first() else "配置不同"}
    LaunchedEffect(module,targets.map{it.id}){data=emptyMap();execute()}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("设备设置",style=MaterialTheme.typography.headlineMedium)}
        if(failedWrites.isNotEmpty())item{TextButton(enabled=!busy,onClick={val pending=failedWrites.toMap();busy=true;scope.launch{try{for((id,body) in pending){val device=devices.firstOrNull{it.id==id} ?: continue;try{data=data+(id to controller.request(device,path,JSONObject(body)));failedWrites=failedWrites-id;results=results+(id to "已同步")}catch(e:Exception){results=results+(id to (e.message ?: "失败"))}}}finally{busy=false}}}){Text("重试上次操作失败设备（${failedWrites.size}）")}}
        item{Row{Checkbox(checked=all,onCheckedChange={all=it},enabled=!busy);Text("全部已连接设备",Modifier.padding(top=12.dp))}}
        if(groups.isNotEmpty())item{var expanded by remember{mutableStateOf(false)};Box{TextButton(onClick={expanded=true}){Text("按分组选择")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){groups.values.distinct().sorted().forEach{group->DropdownMenuItem(enabled=!busy,text={Text(group)},onClick={all=false;selected=groups.filterValues{it==group}.keys;expanded=false})}}}}
        if(!all)items(devices.filter{it.connected},key={it.id}){device->Row{Checkbox(checked=device.id in selected,onCheckedChange={selected=if(it)selected+device.id else selected-device.id},enabled=!busy);Text(device.name,Modifier.padding(top=12.dp))}}

        if(module=="notifications"){
            val apps=targets.flatMap{device->data[device.id]?.optJSONArray("notificationApps")?.let{a->(0 until a.length()).map{a.getJSONObject(it)}} ?: emptyList()}.distinctBy{it.getString("package")}.sortedBy{it.getString("name")}
            item{Text("选择同步通知的应用；历史消息保留。系统通知读取权限需在安卓本机开启。",style=MaterialTheme.typography.bodySmall)}
            items(apps,key={it.getString("package")}){app->Column{
                Text(app.getString("name"),style=MaterialTheme.typography.titleMedium)
                val pkg=app.getString("package")
                targets.forEach{device->val a=data[device.id]?.optJSONArray("notificationApps");val value=a?.let{(0 until it.length()).map{n->it.getJSONObject(n)}.firstOrNull{it.optString("package")==pkg}?.optBoolean("enabled")};Text(device.name+" · "+when(value){true->"已开启";false->"已关闭";null->"未读取"},style=MaterialTheme.typography.bodySmall)}
                Row{TextButton(enabled=!busy,onClick={execute(JSONObject().put("notificationApp",JSONObject().put("package",pkg).put("enabled",true)))}){Text("开启")};TextButton(enabled=!busy,onClick={execute(JSONObject().put("notificationApp",JSONObject().put("package",pkg).put("enabled",false)))}){Text("关闭")}}
            }}
        }
        if(module=="browser"){
            item{OutlinedTextField(value=browserHost,onValueChange={browserHost=it},label={Text("网站域名，例如 example.com")});TextButton(enabled=!busy && targets.isNotEmpty() && browserHost.isNotBlank(),onClick={execute(JSONObject().put("action","open").put("host",browserHost.trim()))}){Text("在所选设备打开登录")}}
            item{TextButton(enabled=!busy && targets.isNotEmpty(),onClick={browserConfirm=JSONObject().put("action","clear")}){Text("全部退出并清除网页数据")}}
            items(targets,key={"browser:${it.id}"}){device->
                Text(device.name,style=MaterialTheme.typography.titleMedium)
                val sites=data[device.id]?.optJSONArray("sites")
                if(sites==null || sites.length()==0)Text("暂无网站记录",style=MaterialTheme.typography.bodySmall)
                else (0 until sites.length()).forEach{i->val site=sites.getJSONObject(i);val host=site.getString("host")
                    Text(host+if(site.optBoolean("loggedIn"))" · 已登录" else " · 已访问")
                    Row{TextButton(enabled=!busy,onClick={browserHost=host;execute(JSONObject().put("action","open").put("host",host))}){Text("打开登录")};TextButton(enabled=!busy,onClick={browserConfirm=JSONObject().put("action","logout").put("host",host)}){Text("退出此网站")}}
                }
            }
        }
        if(module=="models"){
            item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("添加服务商",style=MaterialTheme.typography.titleMedium)
                var showKinds by remember{mutableStateOf(false)}
                Box{TextButton(onClick={showKinds=true}){Text(providerKind)};DropdownMenu(expanded=showKinds,onDismissRequest={showKinds=false}){listOf("OpenAiCompat","OpenAiResponses","Anthropic","Gemini").forEach{kind->DropdownMenuItem(text={Text(kind)},onClick={providerKind=kind;showKinds=false})}}}
                OutlinedTextField(value=providerName,onValueChange={providerName=it},label={Text("名称")})
                OutlinedTextField(value=providerURL,onValueChange={providerURL=it},label={Text("服务地址")})
                OutlinedTextField(value=providerKey,onValueChange={providerKey=it},label={Text("API Key，留空保留已有密钥")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
                OutlinedTextField(value=providerModel,onValueChange={providerModel=it},label={Text("测试模型")})
                Text("在所选设备测试并保存；不会修改当前执行模型。",style=MaterialTheme.typography.bodySmall)
                Button(enabled=!busy && targets.isNotEmpty() && providerName.isNotBlank() && providerURL.isNotBlank() && providerModel.isNotBlank(),onClick={execute(JSONObject().put("action","connect_provider").put("name",providerName).put("baseURL",providerURL).put("kind",providerKind).put("apiKey",providerKey).put("model",providerModel))}){Text("测试并保存到所选设备")}
            }}
            item{Row{listOf("Main" to "主模型","Vision" to "看图","Light" to "轻量").forEach{(key,label)->FilterChip(selected=role==key,onClick={role=key},label={Text(label)})}}}
            item{Text("将所选模型应用到上方选中的设备；服务商必须已在目标设备配置。",style=MaterialTheme.typography.bodySmall)}
            if(role!="Main")item{TextButton(enabled=!busy,onClick={execute(JSONObject().put("role",role).put("action","inherit"))}){Text("使用主模型")}}
            if(role=="Vision")item{TextButton(enabled=!busy,onClick={execute(JSONObject().put("role",role).put("action","off"))}){Text("关闭看图模型")}}
            items(targets,key={"models:${it.id}"}){device->
                val state=data[device.id]
                Text(device.name+" · "+(state?.optJSONObject("roles")?.optJSONObject(role)?.optString("model") ?: "未选择"))
                val providers=state?.optJSONArray("providers")
                if(providers!=null)(0 until providers.length()).forEach{i->val provider=providers.getJSONObject(i);val models=provider.optJSONArray("models")
                    if(models!=null)(0 until models.length()).forEach{j->val model=models.getString(j)
                        TextButton(enabled=!busy,onClick={execute(JSONObject().put("role",role).put("action","select").put("providerName",provider.getString("name")).put("baseURL",provider.getString("baseURL")).put("model",model))}){Text(provider.getString("name")+" · "+model)}
                    }
                }
            }
            item{OutlinedTextField(value=effort,onValueChange={effort=it},label={Text("思考档位（留空使用默认）")});TextButton(enabled=!busy,onClick={execute(JSONObject().put("role",role).put("action","reasoning").put("effort",effort.trim()))}){Text("应用思考档位")}}
        }
        if(module=="mcp")items(listOf("mcpLan" to "MCP 局域网连接","mcpUsb" to "MCP USB 连接")){(key,label)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("$label · ${common(key)}");Row{TextButton(enabled=!busy,onClick={execute(JSONObject().put(key,true))}){Text("开启")};TextButton(enabled=!busy,onClick={execute(JSONObject().put(key,false))}){Text("关闭")}}}}}
        if(module=="settings"){
            items(listOf("keepScreenOn" to "保持屏幕常亮","muteTarget" to "操作应用静音")){(key,label)->Card{Column(Modifier.padding(12.dp)){Text("$label · ${common(key)}");Row{TextButton(enabled=!busy,onClick={execute(JSONObject().put(key,true))}){Text("开启")};TextButton(enabled=!busy,onClick={execute(JSONObject().put(key,false))}){Text("关闭")}}}}}
            item{Text("执行优化",style=MaterialTheme.typography.titleMedium)}
            items(listOf("inlineScreen" to "操作后读屏","batchSteps" to "连续操作","replay" to "路线重放","deepLinks" to "应用直达","lightSteps" to "轻量步骤","parallelTasks" to "并行任务","structuredCompaction" to "结构化摘要","adaptiveVision" to "分级读屏")){(key,label)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("$label · ${common(key)}");Row{TextButton(enabled=!busy,onClick={execute(JSONObject().put(key,true))}){Text("开启")};TextButton(enabled=!busy,onClick={execute(JSONObject().put(key,false))}){Text("关闭")}}}}}
            item{Text("同时任务数 · ${common("parallelMax")}");Row{(1..3).forEach{n->TextButton(enabled=!busy,onClick={execute(JSONObject().put("parallelMax",n))}){Text(n.toString())}}}}
            items(listOf("operatingPace" to "paceOptions","capsuleMode" to "capsuleOptions")){(key,options)->
                Text((if(key=="operatingPace")"操作节奏" else "悬浮胶囊")+" · "+common(key))
                val array=data.values.firstOrNull()?.optJSONArray(options)
                Row{if(array!=null)(0 until array.length()).forEach{i->val value=array.getString(i);TextButton(enabled=!busy,onClick={execute(JSONObject().put(key,value))}){Text(value)}}}
            }
        }
        if(module in listOf("memory","skill","plans")){
            item{OutlinedTextField(value=text,onValueChange={text=it;requestId=UUID.randomUUID().toString()},label={Text(if(module=="plans")"任务内容" else "内容")},modifier=Modifier.fillMaxWidth())}
            if(module=="skill")item{OutlinedTextField(value=title,onValueChange={title=it;requestId=UUID.randomUUID().toString()},label={Text("技能名称")});OutlinedTextField(value=description,onValueChange={description=it;requestId=UUID.randomUUID().toString()},label={Text("适用场景")})}
            if(module=="plans")item{TextButton(enabled=!busy && targets.isNotEmpty(),onClick={editor=targets to JSONObject().put("id",requestId).put("goal",text)}){Text("详细设置")};OutlinedTextField(value=hours,onValueChange={hours=it;requestId=UUID.randomUUID().toString()},label={Text("多少小时后执行一次")})}
            item{Button(enabled=!busy && targets.isNotEmpty() && text.isNotBlank(),onClick={
                val body=if(module=="plans")JSONObject().put("action","save").put("id",requestId).put("plan",JSONObject().put("id",requestId).put("goal",text).put("nextAtMs",System.currentTimeMillis()+((hours.toDoubleOrNull() ?: 1.0).coerceIn(0.01,8760.0)*3600000).toLong()))
                else JSONObject().put("id",requestId).put("type",module).put("kind","fact").put("text",text).put("title",title).put("description",description)
                execute(body)
            }){Text("添加到所选设备")}}
        }
        items(targets,key={"result:${it.id}"}){device->
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                Text(device.name,style=MaterialTheme.typography.titleMedium);Text(results[device.id] ?: "未读取",style=MaterialTheme.typography.bodySmall)
                val rows=data[device.id]?.optJSONArray("items")
                if(rows!=null)(0 until rows.length()).forEach{index->val row=rows.getJSONObject(index);val id=row.optString("id")
                    if(module !in listOf("memory","skill") || row.optString("type")==module){
                        Text(row.optString("goal",row.optString("title",row.optString("template",id))),Modifier.padding(top=10.dp))
                        if(module=="plans")Row{TextButton(enabled=!busy,onClick={editor=listOf(device) to row}){Text("编辑")};TextButton(enabled=!busy,onClick={execute(JSONObject().put("action","enable").put("id",id).put("enabled",!row.optBoolean("enabled",true)),listOf(device))}){Text(if(row.optBoolean("enabled",true))"停用" else "启用")};TextButton(enabled=!busy,onClick={execute(JSONObject().put("action","run").put("id",id),listOf(device))}){Text("立即执行")}}
                        if(module=="routes")TextButton(enabled=!busy,onClick={execute(JSONObject().put("action","save").put("route",row),targets.filter{it.id!=device.id})}){Text("复制到所选设备")}
                        if(module in listOf("memory","skill")){TextButton(enabled=!busy,onClick={text=row.optString("text");title=row.optString("title")}){Text("载入内容")};TextButton(enabled=!busy && text.isNotBlank(),onClick={execute(JSONObject().put("action","update").put("id",id).put("type",module).put("text",text),listOf(device))}){Text("用编辑内容更新此项")};TextButton(enabled=!busy,onClick={execute(JSONObject().put("action","delete").put("id",id).put("type",module),listOf(device))}){Text("删除")}}
                        if(module in listOf("plans","routes"))TextButton(enabled=!busy,onClick={execute(JSONObject().put("action","delete").put("id",id),listOf(device))}){Text("删除")}
                    }
                }
            }}
        }
        item{TextButton(enabled=!busy,onClick={execute()}){Text("刷新")}}
    }
}

@Composable
fun FleetManagementHome(controller:FleetController,devices:List<FleetController.Device>){
    val context=androidx.compose.ui.platform.LocalContext.current
    val providers=com.wangzan101.mika.settings.ProviderStore.get(context)
    val connected by providers.providers.collectAsState()
    val configs by providers.configs.collectAsState()
    var dispatch by remember{mutableStateOf(controller.dispatchModel)}
    var page by remember{mutableStateOf<String?>(null)}
    androidx.activity.compose.BackHandler(page!=null){page=null}
    when(val selected=page){
        "localModels"->ModelSetupScreen(providers,false,onBack={page=null},onDone={page=null})
        null->LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text("设置",style=MaterialTheme.typography.headlineMedium)}
            item{Text("当前主控端",style=MaterialTheme.typography.labelLarge)}
            item{TextButton(onClick={page="localModels"}){Text("模型服务商与本机执行模型")}}
            item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
                Text("任务下发模型",style=MaterialTheme.typography.titleMedium)
                var choosing by remember{mutableStateOf(false)}
                Box{TextButton(onClick={choosing=true}){Text(if(dispatch.startsWith("controller|"))dispatch.substringAfterLast("|") else configs.firstOrNull{it.id==dispatch}?.model ?: "选择模型")}
                    DropdownMenu(expanded=choosing,onDismissRequest={choosing=false}){connected.forEach{provider->
                        val models=((provider.models ?: emptyList()).filter{it.usable}.map{it.id}+provider.verifiedModels+configs.filter{it.providerId==provider.id}.map{it.model}).distinct()
                        models.forEach{model->DropdownMenuItem(text={Text("${provider.name} · $model")},onClick={dispatch="controller|${provider.id}|$model";controller.dispatchModel=dispatch;choosing=false})}
                    }}
                }
                Text("仅此主控调用，用于把任务分配给设备。",style=MaterialTheme.typography.bodySmall)
            }}}
            item{Text("设备设置",style=MaterialTheme.typography.labelLarge)}
            items(listOf("notifications" to "通知来源","queue" to "任务队列","browser" to "浏览器与网站登录","files" to "文件","usage" to "模型用量","diagnostics" to "设备诊断","mcp" to "电脑连接 MCP","models" to "设备执行模型","plans" to "定时任务","routes" to "操作路线","settings" to "执行设置","memory" to "记忆","skill" to "技能")){(key,label)->
                Card(onClick={page=key},modifier=Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                        Text("›",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        else->Column{TextButton(onClick={page=null}){Text("返回设置")};key(selected){if(selected=="files")FleetFiles(controller,devices) else if(selected=="queue")FleetQueueView(controller,devices) else if(selected in listOf("usage","diagnostics"))FleetReports(controller,devices,selected=="usage") else FleetManagement(controller,devices,selected)}}
    }
}

@Composable
fun FleetQueueView(controller:FleetController,devices:List<FleetController.Device>){
    val scope=rememberCoroutineScope()
    var records by remember{mutableStateOf(mapOf<String,List<JSONObject>>())}
    var errors by remember{mutableStateOf(mapOf<String,String>())}
    var busy by remember{mutableStateOf(false)}
    var confirming by remember{mutableStateOf<Pair<FleetController.Device,String>?>(null)}
    suspend fun reload(){for(device in devices){try{val array=controller.request(device,"/tasks").getJSONArray("items");records=records+(device.id to (0 until array.length()).map{array.getJSONObject(it)});errors=errors-device.id}catch(e:Exception){errors=errors+(device.id to (e.message ?: "读取失败"))}}}
    fun action(device:FleetController.Device,id:String,action:String){busy=true;scope.launch{try{controller.request(device,"/tasks",JSONObject().put("id",id).put("action",action));reload()}catch(e:Exception){errors=errors+(device.id to (e.message ?: "操作失败"))}finally{busy=false}}}
    LaunchedEffect(devices.map{it.id}){reload()}
    confirming?.let{(device,id)->AlertDialog(onDismissRequest={confirming=null},title={Text("已核实安卓执行结果？")},text={Text("不会重新执行此任务，后续排队任务将继续运行。")},confirmButton={TextButton(onClick={confirming=null;action(device,id,"acknowledge")}){Text("移出待核实队列")}},dismissButton={TextButton(onClick={confirming=null}){Text("返回")}})}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("任务队列",style=MaterialTheme.typography.headlineMedium);TextButton(enabled=!busy,onClick={scope.launch{reload()}}){Text("刷新")}}
        items(devices,key={it.id}){device->Column{Text(device.name,style=MaterialTheme.typography.titleMedium);errors[device.id]?.let{Text(it,color=MaterialTheme.colorScheme.error)};records[device.id]?.forEach{task->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){val state=task.optString("state");Text(task.optString("text"),maxLines=3);Text(mapOf("queued" to "排队中","dispatching" to "正在启动","running" to "执行中","completed" to "已完成","failed" to "失败","cancelled" to "已取消","uncertain" to "待核实")[state] ?: state,style=MaterialTheme.typography.labelSmall);if(task.optString("result").isNotBlank())MarkdownText(task.getString("result"),MaterialTheme.typography.bodyMedium);if(task.optString("error").isNotBlank())Text(task.optString("error"));if(state in listOf("failed","cancelled"))TextButton(enabled=!busy,onClick={action(device,task.getString("id"),"retry")}){Text("重新执行")};if(state=="queued")TextButton(enabled=!busy,onClick={action(device,task.getString("id"),"cancel")}){Text("取消排队")};if(state=="uncertain")TextButton(enabled=!busy,onClick={confirming=device to task.getString("id")}){Text("已核实执行结果")}}}}}}
    }
}

@Composable
fun FleetReports(controller:FleetController,devices:List<FleetController.Device>,usage:Boolean){
    var values by remember{mutableStateOf(mapOf<String,JSONObject>())}
    var errors by remember{mutableStateOf(mapOf<String,String>())}
    val scope=rememberCoroutineScope()
    suspend fun reload(){for(device in devices){try{values=values+(device.id to controller.request(device,if(usage)"/usage" else "/diagnostics"));errors=errors-device.id}catch(e:Exception){errors=errors+(device.id to (e.message ?: "读取失败"))}}}
    LaunchedEffect(devices.map{it.id}){reload()}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text(if(usage)"模型用量" else "设备诊断",style=MaterialTheme.typography.headlineMedium);TextButton(onClick={scope.launch{reload()}}){Text("刷新")}}
        items(devices,key={it.id}){device->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(device.name,style=MaterialTheme.typography.titleMedium);errors[device.id]?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            values[device.id]?.let{value->if(usage){val rows=value.optJSONArray("items");if(rows==null || rows.length()==0)Text("暂无模型用量");if(rows!=null)(0 until rows.length()).forEach{i->val row=rows.getJSONObject(i);Text(row.optString("provider")+" · "+row.optString("model"));Text("调用 ${row.optLong("calls")} 次 · 输入 ${row.optLong("input")} · 输出 ${row.optLong("output")}",style=MaterialTheme.typography.bodySmall);Text("缓存命中 ${row.optLong("cached")} · 未返回用量 ${row.optLong("unknownCalls")} 次",style=MaterialTheme.typography.labelSmall)}}else{Text("版本 "+value.optString("version")+" · Android "+value.optString("android"));listOf("accessibility" to "无障碍服务","notifications" to "通知权限","notificationListener" to "通知监听","busy" to "任务执行中").forEach{(key,label)->Text(label+"："+if(value.optBoolean(key))"是" else "否")};Text("可用存储 ${value.optLong("freeBytes")/1024/1024} MB")}}
        }}}
    }
}
