package com.wangzan101.mika.ui

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import org.json.JSONObject
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wangzan101.mika.remote.FleetController
import kotlinx.coroutines.*
import java.io.DataInputStream

/** Subscribe only while this UI is visible; one hardware decoder per visible remote window. */
@Composable
fun FleetVideoWindows(controller:FleetController,devices:List<FleetController.Device>){
    val enabled by controller.showWindows.collectAsState()
    if(!enabled)return
    var visible by remember{mutableStateOf(false)}
    androidx.lifecycle.compose.LifecycleStartEffect(Unit){visible=true;onStopOrDispose{visible=false}}
    if(!visible)return
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        items(devices.filter{it.id!="local" && it.connected},key={it.id}){device->FleetVideoWindow(controller,device)}
    }
}

@Composable
private fun FleetVideoWindow(controller:FleetController,device:FleetController.Device){
    var dimensions by remember(device.id){mutableStateOf<Pair<Int,Int>?>(null)}
    var expanded by remember{mutableStateOf(false)}
    var screen by remember{mutableStateOf(JSONObject())}
    var inputError by remember{mutableStateOf("")}
    var editing by remember{mutableStateOf(false)}
    var confirmTakeover by remember{mutableStateOf(false)}
    var text by remember{mutableStateOf("")}
    var inputBusy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun input(action:String,values:JSONObject=JSONObject()){
        if(inputBusy)return
        val current=screen
        values.put("action",action).put("runId",current.optString("runId")).put("conversationId",current.optString("conversationId")).put("displayId",current.optInt("displayId",-1))
        inputBusy=true
        scope.launch{try{controller.request(device,"/virtual/input",values);screen=controller.request(device,"/virtual/status");inputError=""}catch(e:CancellationException){throw e}catch(e:Exception){inputError=when(e.message){"controlled_by_other"->"其他主控正在操作，请确认接管";"control_not_owned"->"控制权已变化，请重新接管";"waiting_for_agent_pause"->"等待 agent 暂停当前操作";"virtual_screen_closed","task_ended"->"虚拟屏已关闭或任务已结束";"screen_changed"->"画面已变化，请等待刷新";"focus_input_first"->"请先点击画面中的输入框";else->e.message ?: "操作失败"}}finally{inputBusy=false}}
    }
    if(confirmTakeover)AlertDialog(onDismissRequest={confirmTakeover=false},title={Text("其他主控正在操作")},text={Text("接管此设备后，原主控将停止操作。")},confirmButton={TextButton(onClick={confirmTakeover=false;input("takeover")}){Text("接管")}},dismissButton={TextButton(onClick={confirmTakeover=false}){Text("取消")}})
    if(editing)AlertDialog(onDismissRequest={editing=false},title={Text("输入文本")},text={OutlinedTextField(value=text,onValueChange={text=it.take(4000)})},confirmButton={TextButton(onClick={editing=false;input("text",JSONObject().put("text",text))}){Text("填入")}},dismissButton={TextButton(onClick={editing=false}){Text("取消")}})
    var surface by remember{mutableStateOf<android.view.Surface?>(null)}
    LaunchedEffect(device.id){while(isActive){
        try{val state=controller.request(device,"/virtual/status");screen=state;dimensions=if(state.optBoolean("active") && state.optInt("width")>0 && state.optInt("height")>0)state.getInt("width") to state.getInt("height") else null}catch(e:CancellationException){throw e}catch(_:Exception){dimensions=null}
        delay(2000)
    }}
    val size=dimensions
    if(size!=null){
        val width=if(expanded)180.dp else 90.dp
        Card{Column{
            TextButton(onClick={expanded=!expanded}){Text(device.name,maxLines=1,style=MaterialTheme.typography.labelSmall)}
            Box {
            AndroidView(modifier=Modifier.width(width).height(width*(size.second.toFloat()/size.first).coerceIn(0.5f,3f)),factory={context->SurfaceView(context).apply{holder.addCallback(object:SurfaceHolder.Callback{
                override fun surfaceCreated(holder:SurfaceHolder){surface=holder.surface}
                override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){surface=holder.surface}
                override fun surfaceDestroyed(holder:SurfaceHolder){surface=null}
            })}})
            if(expanded && screen.optBoolean("controlled"))Box(Modifier.matchParentSize().pointerInput(size,screen.optString("runId"),screen.optInt("displayId")){
                awaitPointerEventScope{
                    while(true){
                        val down=awaitPointerEvent().changes.firstOrNull{it.pressed && !it.previousPressed} ?: continue
                        val start=down.position;val started=down.uptimeMillis;val id=down.id
                        down.consume()
                        var end=start;var duration=80L
                        while(true){val next=awaitPointerEvent().changes.firstOrNull{it.id==id} ?: break;end=next.position;duration=next.uptimeMillis-started;next.consume();if(!next.pressed)break}
                        fun x(p:Offset)=(p.x/this.size.width).coerceIn(0f,1f).toDouble()
                        fun y(p:Offset)=(p.y/this.size.height).coerceIn(0f,1f).toDouble()
                        input("gesture",JSONObject().put("x",x(start)).put("y",y(start)).put("endX",x(end)).put("endY",y(end)).put("duration",duration.coerceIn(80,1500)))
                    }
                }
            })
            }
            if(expanded && screen.optBoolean("canControl"))Row{
                TextButton(enabled=!inputBusy,onClick={if(screen.optBoolean("controlledByOther"))confirmTakeover=true else input(if(screen.optBoolean("controlled"))"release" else "acquire")}){Text(if(screen.optBoolean("controlled"))"交回" else "接管")}
                TextButton(enabled=!inputBusy && screen.optBoolean("controlled"),onClick={editing=true}){Text("输入")}
            }
            if(inputError.isNotEmpty())Text(inputError,style=MaterialTheme.typography.labelSmall)
        }}
    }
    LaunchedEffect(surface,size,device.url){
        val output=surface ?: return@LaunchedEffect
        val geometry=size ?: return@LaunchedEffect
        withContext(Dispatchers.IO){while(isActive){
            var decoder:MediaCodec?=null
            var connection:java.net.HttpURLConnection?=null
            try{
                connection=controller.videoConnection(device)
                connection.connect()
                check(connection.responseCode==200)
                val codec=MediaCodec.createDecoderByType("video/avc");decoder=codec
                codec.configure(MediaFormat.createVideoFormat("video/avc",geometry.first,geometry.second),output,null,0);codec.start()
                val info=MediaCodec.BufferInfo()
                DataInputStream(connection.inputStream.buffered()).use{input->
                    while(isActive){
                        val length=input.readInt();require(length in 1..4_000_000)
                        val packet=ByteArray(length);input.readFully(packet)
                        var slot=codec.dequeueInputBuffer(10000)
                        while(slot<0 && isActive){var out=codec.dequeueOutputBuffer(info,0);while(out>=0){codec.releaseOutputBuffer(out,true);out=codec.dequeueOutputBuffer(info,0)};slot=codec.dequeueInputBuffer(10000)}
                        if(slot>=0){val buffer=codec.getInputBuffer(slot)!!;require(packet.size<=buffer.capacity());buffer.clear();buffer.put(packet);codec.queueInputBuffer(slot,0,packet.size,System.nanoTime()/1000,0)}
                        var out=codec.dequeueOutputBuffer(info,10000);while(out>=0){codec.releaseOutputBuffer(out,true);out=codec.dequeueOutputBuffer(info,0)}
                    }
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){delay(1000)}finally{connection?.disconnect();decoder?.let{runCatching{it.stop()};it.release()}}
        }}
    }
}
