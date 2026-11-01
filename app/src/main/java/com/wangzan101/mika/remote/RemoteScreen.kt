package com.wangzan101.mika.remote

import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.wangzan101.mika.cap.phone.a11y.ScreenAccessibility
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Authenticated LAN screen access; commands are never retried automatically. */
object RemoteScreen {
    val controlLease=com.wangzan101.mika.core.run.ControlLease({android.os.SystemClock.elapsedRealtime()})
    private val virtualInputLock=Mutex()
    fun peer(header:String)=java.security.MessageDigest.getInstance("SHA-256").digest(header.toByteArray()).joinToString(""){"%02x".format(it)}
    suspend fun virtualInput(app:com.wangzan101.mika.MikaApp,data:JSONObject,peer:String):Boolean = virtualInputLock.withLock { withContext(Dispatchers.Main) {
        val run=app.taskState.shownRun ?: error("task_ended")
        check(run.key==data.getString("runId") && app.chat.ui.value.conversationId==data.getString("conversationId")){"screen_changed"}
        val action=data.getString("action")
        if(action=="release"){check(controlLease.release(run.key,peer)){"control_not_owned"};run.releaseRemoteControl();return@withContext true}
        check(VirtualVideo.frame(app)!=null && !app.tasks.webScreenOpen.value){"virtual_screen_closed"}
        val displayId=app.tasks.agentDisplayId.value
        check(displayId>=0 && displayId==data.getInt("displayId")){"screen_changed"}
        if(action=="acquire" || action=="takeover"){
            check(controlLease.claim(run.key,peer,action=="takeover")){"controlled_by_other"}
            val ready=run.requestRemoteControl()
            if(!ready)controlLease.release(run.key,peer)
            return@withContext ready
        }
        check(controlLease.owns(run.key,peer,true)){"control_not_owned"}
        check((run.state.value as? com.wangzan101.mika.core.agent.TaskState.Held)?.reason?.code=="remote_control"){"waiting_for_agent_pause"}
        val service=ScreenAccessibility.instance.value ?: error("accessibility_required")
        when(action){
            "gesture"->{
                val display=app.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId) ?: error("screen_changed")
                val metrics=android.util.DisplayMetrics();display.getRealMetrics(metrics)
                fun coord(key:String,max:Int):Float{val v=data.getDouble(key);require(v.isFinite() && v in 0.0..1.0);return (v*(max-1)).toFloat()}
                val x=coord("x",metrics.widthPixels);val y=coord("y",metrics.heightPixels)
                val ex=coord("endX",metrics.widthPixels);val ey=coord("endY",metrics.heightPixels)
                service.gesture(displayId,Path().apply{moveTo(x,y);if(x!=ex || y!=ey)lineTo(ex,ey)},data.optLong("duration",100).coerceIn(50,1500))
            }
            "text"->{
                val text=data.getString("text");require(text.length<=4000)
                val roots=service.windowsOn(displayId).mapNotNull{it.root}
                try{
                    val node=roots.firstNotNullOfOrNull{it.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)} ?: error("focus_input_first")
                    try{check(node.isEditable){"focus_input_first"};node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)})}finally{node.recycle()}
                }finally{roots.forEach{it.recycle()}}
            }
            else->error("unsupported_action")
        }
    }}
    suspend fun input(data:JSONObject): Boolean = withContext(Dispatchers.Main) {
        val service = ScreenAccessibility.instance.value ?: error("accessibility_required")
        check(VideoStream.active) { "screen_stale" }
        val width=VideoStream.screenWidth;val height=VideoStream.screenHeight
        val bounds=service.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
        check(bounds.width()==width && bounds.height()==height) { "screen_changed" }
        when (data.getString("action")) {
            "gesture" -> {
                fun coord(key:String, max:Int):Float { val v=data.getDouble(key);require(v.isFinite() && v in 0.0..1.0);return (v*(max-1)).toFloat() }
                val x=coord("x",width);val y=coord("y",height);val endX=coord("endX",width);val endY=coord("endY",height)
                val path=Path().apply {moveTo(x,y);if(x!=endX || y!=endY)lineTo(endX,endY)}
                service.gesture(Display.DEFAULT_DISPLAY,path,data.optLong("duration",100).coerceIn(50,1500))
            }
            "text" -> {
                val text=data.getString("text");require(text.length<=4000)
                val node=service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: error("focus_input_first")
                try {check(node.isEditable) {"focus_input_first"};node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)})} finally {node.recycle()}
            }
            "back" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            else -> error("unsupported_action")
        }
    }
}
