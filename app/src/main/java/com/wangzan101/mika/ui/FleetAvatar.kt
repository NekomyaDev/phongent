package com.wangzan101.mika.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun FleetAvatar(id:String,modifier:Modifier=Modifier,active:Boolean=true) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val stableId=if(id=="local")context.getSharedPreferences("lan-identity",0).getString("id",id) ?: id else id
    val index=stableId.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8).fold(2166136261L){hash,byte->((hash xor (byte.toInt() and 255).toLong())*16777619L) and 0xffffffffL}
    val shape=listOf("blob","wedge","gem")[(index%3).toInt()]
    val hue=(index%360000)/1000.0
    fun script()="var element=document.getElementById('bot');element.style.width=(innerWidth*0.8)+'px';element.style.height=(innerHeight*0.8)+'px';element.style.top=(innerHeight*0.1)+'px';window.setMikaShape('$shape');document.getElementById('bot').style.filter='sepia(1) saturate(2) hue-rotate(${hue}deg) brightness(0.84)';window.setMikaState('02',false);window.setMikaState('02',$active)"
    AndroidView(modifier=modifier,factory={context->WebView(context).apply{
        setBackgroundColor(android.graphics.Color.TRANSPARENT);settings.javaScriptEnabled=true;settings.allowFileAccess=true;webChromeClient=android.webkit.WebChromeClient()
        settings.allowContentAccess=false;isClickable=false;isFocusable=false;setOnTouchListener{_,_->true}
        webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView,url:String){view.tag=script();view.evaluateJavascript(script(),null)}}
        loadUrl("file:///android_asset/agent/index.html")
    }},update={if(it.tag!=null && it.tag!=script()){it.tag=script();it.evaluateJavascript(script(),null)}},onRelease={it.evaluateJavascript("window.destroyMika()",null);it.destroy()})
}


@Composable
fun FleetAvatarButton(id:String,name:String,selected:Boolean,onClick:()->Unit,onMention:()->Unit){
    var pressing by androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf(false)}
    val progress by androidx.compose.animation.core.animateFloatAsState(if(pressing)1f else 0f,animationSpec=androidx.compose.animation.core.tween(if(pressing)450 else 100),label="mention-fill")
    val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.Box(Modifier.width(72.dp).height(94.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(if(selected)androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)){
        androidx.compose.foundation.layout.Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,modifier=Modifier.fillMaxSize()){
            FleetAvatar(id,Modifier.size(60.dp));androidx.compose.material3.Text(name,style=androidx.compose.material3.MaterialTheme.typography.labelSmall,maxLines=2)
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().fillMaxHeight(progress).background(androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha=0.18f)))
        androidx.compose.foundation.layout.Box(Modifier.matchParentSize().pointerInput(id){detectTapGestures(onTap={onClick()},onLongPress={haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress);onMention()},onPress={pressing=true;tryAwaitRelease();pressing=false})})
    }
}
