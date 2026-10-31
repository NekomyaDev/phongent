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
