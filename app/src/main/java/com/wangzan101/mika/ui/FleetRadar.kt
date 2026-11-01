package com.wangzan101.mika.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.remote.FleetController
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable
fun FleetRadar(devices:List<FleetController.Device>,busy:Boolean,status:String,onChat:(FleetController.Device)->Unit,onEdit:(FleetController.Device)->Unit,onConnect:(FleetController.Device)->Unit,connecting:Set<String>,groups:Map<String,String>,onGroup:(Set<String>,String)->Unit,onConnectAll:(List<FleetController.Device>)->Unit){
    var page by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<String?>(null)}
    var groupEditor by remember{mutableStateOf(false)}
    var checked by remember{mutableStateOf(setOf<String>())}
    var groupName by remember{mutableStateOf("")}
    var onlyDisconnected by remember{mutableStateOf(false)}
    val remotes=devices.filter{it.id!="local"}
    val pageSize=if(remotes.size>6)10 else 6
    val avatarSize=if(remotes.size>6)44.dp else 66.dp
    val pages=max(1,(remotes.size+pageSize-1)/pageSize)
    LaunchedEffect(pages){page=page.coerceIn(0,pages-1)}
    val visible=remotes.drop(page*pageSize).take(pageSize)
    val color=MaterialTheme.colorScheme.primary
    val line=MaterialTheme.colorScheme.onSurface.copy(alpha=.06f)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Text("agent",style=MaterialTheme.typography.titleLarge,modifier=Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth()){Text("附近",style=MaterialTheme.typography.labelMedium,color=color);Spacer(Modifier.weight(1f));Text("局域网",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Row(Modifier.fillMaxWidth()){Button(enabled=!busy && remotes.any{!it.connected},onClick={onConnectAll(remotes.filter{!it.connected && it.id !in connecting})}){Text(if(busy)"连接中…" else "一键连接")};Spacer(Modifier.weight(1f));TextButton(onClick={checked=emptySet();groupName="";groupEditor=true}){Text("批量分组")}}
        BoxWithConstraints(Modifier.fillMaxWidth().height(425.dp)){
            val centerX=maxWidth/2
            val radius=min(maxWidth.value*.36f,143f)
            Canvas(Modifier.fillMaxSize()){
                val center=Offset(size.width/2,215.dp.toPx())
                listOf(100,190,280,350).forEach{diameter->drawCircle(line,diameter.dp.toPx()/2,center,style=Stroke(1.dp.toPx()))}
            }
            devices.firstOrNull{it.id=="local"}?.let{local->
                Box(Modifier.offset(x=centerX-55.dp,y=165.dp).size(110.dp,120.dp)){
                    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxSize()){
                        FleetAvatar(local.id,Modifier.size(96.dp))
                        Text("本机",style=MaterialTheme.typography.labelLarge)
                    }
                    Box(Modifier.matchParentSize().combinedClickable(onClick={onChat(local)},onLongClick={onEdit(local)}))
                }
            }
            visible.forEachIndexed{index,device->
                val angle=-PI/2+index*2*PI/max(visible.size,3)
                Box(Modifier.offset(x=centerX+(cos(angle)*radius).toFloat().dp-42.dp,y=215.dp+(sin(angle)*154).toFloat().dp-48.dp).size(84.dp,96.dp)){
                    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxSize()){
                        Box(contentAlignment=Alignment.BottomEnd){
                            FleetAvatar(device.id,Modifier.size(avatarSize))
                            Canvas(Modifier.size(8.dp)){drawCircle(if(device.connected)color else line)}
                        }
                        Text(device.name,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
                    }
                    Box(Modifier.matchParentSize().combinedClickable(onClick={if(device.connected)onChat(device)else if(!busy && device.id !in connecting)onConnect(device)},onLongClick={onEdit(device)}))
                }
            }
            if(remotes.isEmpty())Text("暂无附近 agent",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.TopCenter).padding(top=36.dp))
        }
        if(pages>1)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(enabled=page>0,onClick={page--}){Text("上一组")};Spacer(Modifier.weight(1f));Text("${page+1} / $pages");Spacer(Modifier.weight(1f));TextButton(enabled=page+1<pages,onClick={page++}){Text("下一组")}}
        Row(Modifier.fillMaxWidth()){Text("${remotes.count{it.connected}} 已连接",style=MaterialTheme.typography.labelMedium);Spacer(Modifier.weight(1f));Text("${remotes.size} 附近设备",style=MaterialTheme.typography.labelMedium)}
        Text("${connecting.size} 连接中 · ${remotes.count{!it.connected}} 未连接",style=MaterialTheme.typography.labelSmall)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=onlyDisconnected,onCheckedChange={onlyDisconnected=it});Text("只显示未连接设备")}
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp)){
            items(remotes.filter{!onlyDisconnected || !it.connected},key={it.id}){device->
                ListItem(headlineContent={Text(device.name)},supportingContent={Text((groups[device.id] ?: "默认组")+" · "+if(device.id in connecting)"连接中…" else if(device.connected)"已连接" else device.error ?: "未连接")},trailingContent={TextButton(enabled=device.connected || (!busy && device.id !in connecting),onClick={if(device.connected)onChat(device)else onConnect(device)}){Text(if(device.connected)"打开" else "连接")}})
            }
        }
        if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
    }
    if(groupEditor)AlertDialog(onDismissRequest={groupEditor=false},title={Text("批量分组")},text={Column{
        OutlinedTextField(value=groupName,onValueChange={groupName=it},label={Text("组名，留空为默认组")})
        TextButton(onClick={checked=devices.filter{it.connected}.map{it.id}.toSet()}){Text("全选已连接设备")}
        LazyColumn(Modifier.heightIn(max=300.dp)){items(devices.filter{it.connected},key={it.id}){device->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=device.id in checked,onCheckedChange={checked=if(it)checked+device.id else checked-device.id});Text(device.name)}}}
    }},confirmButton={TextButton(enabled=checked.isNotEmpty(),onClick={onGroup(checked,groupName);groupEditor=false}){Text("保存")}},dismissButton={TextButton(onClick={groupEditor=false}){Text("取消")}})
    selected?.let{id->devices.firstOrNull{it.id==id}?.let{device->
        ModalBottomSheet(onDismissRequest={selected=null}){
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
                FleetAvatar(device.id,Modifier.size(90.dp));Text(device.name,style=MaterialTheme.typography.titleLarge)
                Text(if(device.connected)"已连接" else device.error ?: "附近设备",style=MaterialTheme.typography.bodySmall)
                if(device.connected)Button(onClick={selected=null;onChat(device)}){Text("打开聊天")}
                else Button(enabled=!busy,onClick={onConnect(device)}){Text(if(busy)"申请中…" else "申请连接")}
                TextButton(onClick={selected=null;onEdit(device)}){Text("名称与分组")}
                if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
            }
        }
    }}
}
