package com.wangzan101.mika.ui

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
fun FleetRadar(devices:List<FleetController.Device>,busy:Boolean,status:String,onChat:(FleetController.Device)->Unit,onEdit:(FleetController.Device)->Unit,onConnect:(FleetController.Device)->Unit){
    var page by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<String?>(null)}
    val remotes=devices.filter{it.id!="local"}
    val pages=max(1,(remotes.size+5)/6)
    LaunchedEffect(pages){page=page.coerceIn(0,pages-1)}
    val visible=remotes.drop(page*6).take(6)
    val color=MaterialTheme.colorScheme.primary
    val line=MaterialTheme.colorScheme.onSurface.copy(alpha=.06f)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Text("agent",style=MaterialTheme.typography.titleLarge,modifier=Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth()){Text("附近",style=MaterialTheme.typography.labelMedium,color=color);Spacer(Modifier.weight(1f));Text("局域网",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
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
                            FleetAvatar(device.id,Modifier.size(66.dp))
                            Canvas(Modifier.size(8.dp)){drawCircle(if(device.connected)color else line)}
                        }
                        Text(device.name,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
                    }
                    Box(Modifier.matchParentSize().combinedClickable(onClick={if(device.connected)onChat(device)else selected=device.id},onLongClick={onEdit(device)}))
                }
            }
            if(remotes.isEmpty())Text("暂无附近 agent",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.align(Alignment.TopCenter).padding(top=36.dp))
        }
        if(pages>1)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(enabled=page>0,onClick={page--}){Text("上一组")};Spacer(Modifier.weight(1f));Text("${page+1} / $pages");Spacer(Modifier.weight(1f));TextButton(enabled=page+1<pages,onClick={page++}){Text("下一组")}}
        Row(Modifier.fillMaxWidth()){Text("${remotes.count{it.connected}} 已连接",style=MaterialTheme.typography.labelMedium);Spacer(Modifier.weight(1f));Text("${remotes.size} 附近设备",style=MaterialTheme.typography.labelMedium)}
        if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
    }
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
