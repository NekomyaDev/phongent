package com.wangzan101.mika.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wangzan101.mika.remote.FleetController
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
fun FleetExchange(controller:FleetController,device:FleetController.Device,exchange:JSONObject){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        val body=exchange.optString("body").ifBlank{exchange.optString("draft")}
        if(body.isNotBlank())MarkdownText(body,MaterialTheme.typography.bodyMedium)
        val progress=exchange.optString("progress")
        val phase=exchange.optString("phase")
        if(phase !in listOf("done","failed","cancelled") && progress.isNotBlank())Text(if(progress=="Working")"执行中" else progress,style=MaterialTheme.typography.labelSmall)
        val verification=exchange.optString("verification")
        if(verification.isNotBlank())Text(verification,style=MaterialTheme.typography.bodySmall)
        val evidence=exchange.optString("evidence")
        if(evidence.isNotBlank())Text(evidence,style=MaterialTheme.typography.bodySmall)
        var expanded by remember(exchange.optString("id")){mutableStateOf(false)}
        val steps=exchange.optJSONArray("steps")
        if(steps!=null && steps.length()>0){TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起步骤" else "${steps.length()} 个步骤")};if(expanded)(0 until steps.length()).forEach{i->val step=steps.getJSONObject(i);Text("${i+1}. ${step.optString("title")}",style=MaterialTheme.typography.bodySmall)}}
        val files=exchange.optJSONArray("files")
        if(files!=null)(0 until files.length()).forEach{index->key(exchange.optString("id"),index){FleetResultFile(controller,device,exchange,index,files.getJSONObject(index).optString("name"))}}
    }
}

@Composable
private fun FleetResultFile(controller:FleetController,device:FleetController.Device,exchange:JSONObject,index:Int,name:String){
    var bytes by remember{mutableStateOf<ByteArray?>(null)}
    var bitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var expanded by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null){val content=bytes;scope.launch{try{withContext(Dispatchers.IO){requireNotNull(content);context.contentResolver.openOutputStream(uri)?.use{it.write(content)} ?: error("无法保存文件")}}catch(e:Exception){error=e.message}}}}
    fun load(){busy=true;scope.launch{try{
        val result=controller.request(device,"/files",JSONObject().put("conversationId",exchange.getString("conversationId")).put("exchangeId",exchange.getString("id")).put("index",index))
        val content=android.util.Base64.decode(result.getString("data"),android.util.Base64.DEFAULT)
        require(content.size<=8*1024*1024)
        bytes=content
        bitmap=withContext(Dispatchers.IO){val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeByteArray(content,0,content.size,bounds);if(bounds.outWidth>0 && bounds.outHeight>0){val options=android.graphics.BitmapFactory.Options().apply{inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/1600)};android.graphics.BitmapFactory.decodeByteArray(content,0,content.size,options)}else null}
    }catch(e:Exception){error=e.message ?: "文件读取失败"}finally{busy=false}}}
    Row{TextButton(enabled=!busy && (bytes!=null || device.connected),onClick={if(bytes==null)load() else if(bitmap!=null)expanded=true else save.launch(name)}){Text(if(busy)"读取中…" else name)};if(bytes!=null)TextButton(onClick={save.launch(name)}){Text("保存")}}
    bitmap?.let{image->Image(image.asImageBitmap(),name,Modifier.fillMaxWidth().heightIn(max=180.dp).clickable{expanded=true})}
    if(expanded)Dialog(onDismissRequest={expanded=false}){Surface{Column{bitmap?.let{Image(it.asImageBitmap(),name,Modifier.fillMaxWidth())};TextButton(onClick={expanded=false}){Text("关闭")}}}}
    error?.let{message->Text(message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
}
