package com.wangzan101.mika.remote

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock
import com.wangzan101.mika.MikaApp
import org.json.JSONObject
import java.io.DataOutputStream

/** Uses exactly the preview frames shown by Android AgentPip; never captures the main screen. */
object VirtualVideo {
    @Volatile private var last:Bitmap?=null
    @Volatile private var conversation:String?=null
    @Synchronized fun frame(app:MikaApp):Bitmap? {
        val ui=app.chat.ui.value
        if(conversation!=ui.conversationId){last=null;conversation=ui.conversationId}
        if(!ui.pip.visible){last=null;return null}
        app.taskState.frames.value?.let{last=it}
        return last?.takeUnless{it.isRecycled}
    }
    fun status(app:MikaApp,peer:String=""):JSONObject {
        val f=frame(app)
        val run=app.taskState.shownRun?.key ?: ""
        val owned=RemoteScreen.controlLease.owns(run,peer,true)
        return JSONObject().put("active",f!=null).put("width",f?.width ?: 0).put("height",f?.height ?: 0).put("error","")
            .put("conversationId",app.chat.ui.value.conversationId ?: "")
            .put("runId",app.taskState.shownRun?.key ?: "")
            .put("displayId",app.tasks.agentDisplayId.value)
            .put("canControl",f!=null && app.tasks.agentDisplayId.value>=0 && !app.tasks.webScreenOpen.value)
            .put("controlledByOther",RemoteScreen.controlLease.occupiedByOther(run,peer))
            .put("controlled",owned && (app.taskState.shownRun?.state?.value as? com.wangzan101.mika.core.agent.TaskState.Held)?.reason?.code=="remote_control")
    }
    fun serve(app:MikaApp,out:DataOutputStream) {
        val first=frame(app) ?: return
        val selected=app.chat.ui.value.conversationId
        val w=first.width/2*2;val h=first.height/2*2
        val codec=MediaCodec.createEncoderByType("video/avc")
        try {
            val colors=codec.codecInfo.getCapabilitiesForType("video/avc").colorFormats
            val planar=colors.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
            val color=if(planar)MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar else MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            check(colors.contains(color)) {"video_encoder_unavailable"}
            codec.configure(MediaFormat.createVideoFormat("video/avc",w,h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,color);setInteger(MediaFormat.KEY_BIT_RATE,800000)
                setInteger(MediaFormat.KEY_FRAME_RATE,10);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES,0)
            },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info=MediaCodec.BufferInfo();val pixels=IntArray(w*h);val yuv=ByteArray(w*h*3/2)
            val started=SystemClock.elapsedRealtime()
            fun packet(bytes:ByteArray){out.writeInt(bytes.size);out.write(bytes);out.flush()}
            while(app.chat.ui.value.conversationId==selected){
                val tick=SystemClock.elapsedRealtime();val bitmap=frame(app) ?: break
                if(bitmap.width/2*2!=w || bitmap.height/2*2!=h)break
                val index=codec.dequeueInputBuffer(20000)
                if(index>=0){
                    bitmap.getPixels(pixels,0,w,0,0,w,h)
                    for(y in 0 until h)for(x in 0 until w){
                        val p=pixels[y*w+x];val r=p shr 16 and 255;val g=p shr 8 and 255;val b=p and 255
                        yuv[y*w+x]=(((66*r+129*g+25*b+128) shr 8)+16).coerceIn(0,255).toByte()
                        if(y%2==0 && x%2==0){val u=(((-38*r-74*g+112*b+128) shr 8)+128).coerceIn(0,255).toByte();val v=(((112*r-94*g-18*b+128) shr 8)+128).coerceIn(0,255).toByte();val n=y/2*(w/2)+x/2
                            if(planar){yuv[w*h+n]=u;yuv[w*h+w*h/4+n]=v}else{yuv[w*h+n*2]=u;yuv[w*h+n*2+1]=v}
                        }
                    }
                    codec.getInputBuffer(index)!!.apply{clear();put(yuv)}
                    codec.queueInputBuffer(index,0,yuv.size,(tick-started)*1000,0)
                }
                while(true){val n=codec.dequeueOutputBuffer(info,10000)
                    if(n==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){val bytes=java.io.ByteArrayOutputStream();for(k in listOf("csd-0","csd-1"))codec.outputFormat.getByteBuffer(k)?.let{val b=ByteArray(it.remaining());it.get(b);bytes.write(b)};packet(bytes.toByteArray())}
                    else if(n>=0){if(info.size>0){val buffer=codec.getOutputBuffer(n)!!;buffer.position(info.offset);buffer.limit(info.offset+info.size);val b=ByteArray(info.size);buffer.get(b);packet(b)};codec.releaseOutputBuffer(n,false)}else break
                }
                Thread.sleep((100-(SystemClock.elapsedRealtime()-tick)).coerceAtLeast(1))
            }
        }finally{runCatching{codec.stop()};codec.release()}
    }
}
