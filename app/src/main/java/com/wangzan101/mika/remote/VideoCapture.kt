package com.wangzan101.mika.remote

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.*
import android.media.projection.*
import android.os.*
import com.wangzan101.mika.R
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject

/** H.264 access units, framed as a four-byte big-endian length followed by Annex B bytes. */
object VideoStream {
    @Volatile var active=false
    @Volatile var width=0
    @Volatile var height=0
    @Volatile var screenWidth=0
    @Volatile var screenHeight=0
    @Volatile var error=""
    @Volatile var config:ByteArray?=null
    @Volatile var lastViewer=SystemClock.elapsedRealtime()
    var requestKeyframe:(()->Unit)?=null
    val viewers=CopyOnWriteArrayList<ArrayBlockingQueue<ByteArray>>()
    fun status()=JSONObject().put("active",active).put("width",width).put("height",height).put("error",error)
    fun publish(bytes:ByteArray) {for(q in viewers)if(!q.offer(bytes)){q.clear();q.offer(ByteArray(0));viewers.remove(q)}}
    fun stop(){active=false;config=null;requestKeyframe=null;for(q in viewers){q.clear();q.offer(ByteArray(0))};viewers.clear()}
    fun request(context:Context) {
        if(active)return
        val nm=context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("agent-screen","屏幕共享",NotificationManager.IMPORTANCE_HIGH))
        val launch=PendingIntent.getActivity(context,9301,Intent(context,VideoConsentActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(9301,Notification.Builder(context,"agent-screen").setSmallIcon(R.drawable.ic_notification).setContentTitle("iPhone 请求查看安卓屏幕").setContentText("点击后允许共享整个屏幕").setContentIntent(launch).setAutoCancel(true).build())
    }
}
class VideoConsentActivity:Activity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);if(state==null){
        val manager=getSystemService(MediaProjectionManager::class.java)
        val consent=if(Build.VERSION.SDK_INT>=34)manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
        startActivityForResult(consent,1)
    }}
    override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data)
        if(result==RESULT_OK && data!=null)startForegroundService(Intent(this,VideoCaptureService::class.java).putExtra("code",result).putExtra("data",data))
        else VideoStream.error="屏幕共享未获允许"
        finish()
    }
}
class VideoCaptureService:Service(){
    private var projection:MediaProjection?=null
    private var encoder:MediaCodec?=null
    private var display:android.hardware.display.VirtualDisplay?=null
    private var surface:android.view.Surface?=null
    private val handler=Handler(Looper.getMainLooper())
    @Volatile private var running=false
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="stop"){stopSelf();return START_NOT_STICKY}
        if(running)return START_NOT_STICKY
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("agent-screen-live","正在共享屏幕",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,9302,Intent(this,VideoCaptureService::class.java).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(9302,Notification.Builder(this,"agent-screen-live").setSmallIcon(R.drawable.ic_notification).setContentTitle("正在向 iPhone 共享屏幕").addAction(Notification.Action.Builder(null,"停止共享",stop).build()).setOngoing(true).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        try {
            @Suppress("DEPRECATION") val data=intent?.getParcelableExtra<Intent>("data") ?: error("重新授权屏幕共享")
            val bounds=getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds
            VideoStream.screenWidth=bounds.width();VideoStream.screenHeight=bounds.height()
            val scale=minOf(1.0,720.0/bounds.width());val w=((bounds.width()*scale).toInt()/2)*2;val h=((bounds.height()*scale).toInt()/2)*2
            VideoStream.width=w;VideoStream.height=h;VideoStream.error="";VideoStream.lastViewer=SystemClock.elapsedRealtime()
            val codec=MediaCodec.createEncoderByType("video/avc");encoder=codec
            val format=MediaFormat.createVideoFormat("video/avc",w,h).apply{
                setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE,2_000_000);setInteger(MediaFormat.KEY_FRAME_RATE,24);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES,0)
            }
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);surface=codec.createInputSurface();codec.start()
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("code",Activity.RESULT_OK),data)
            projection!!.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}
                override fun onCapturedContentResize(width:Int,height:Int){if(width!=VideoStream.screenWidth || height!=VideoStream.screenHeight){VideoStream.error="屏幕方向已变化，请重新开启共享";stopSelf()}}
            },handler)
            display=projection!!.createVirtualDisplay("agent-video",w,h,resources.displayMetrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,handler)
            running=true;VideoStream.active=true
            VideoStream.requestKeyframe={runCatching{codec.setParameters(Bundle().apply{putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,0)})};Unit}
            Thread({val info=MediaCodec.BufferInfo()
                try{while(running){
                    if(VideoStream.viewers.isNotEmpty())VideoStream.lastViewer=SystemClock.elapsedRealtime()
                    if(SystemClock.elapsedRealtime()-VideoStream.lastViewer>30000){handler.post{stopSelf()};break}
                    val index=codec.dequeueOutputBuffer(info,100000)
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){val f=codec.outputFormat;val out=java.io.ByteArrayOutputStream();for(key in listOf("csd-0","csd-1")){f.getByteBuffer(key)?.let{val b=ByteArray(it.remaining());it.get(b);out.write(b)}};VideoStream.config=out.toByteArray();VideoStream.publish(out.toByteArray())}
                    else if(index>=0){val buffer=codec.getOutputBuffer(index);if(buffer!=null && info.size>0){buffer.position(info.offset);buffer.limit(info.offset+info.size);val bytes=ByteArray(info.size);buffer.get(bytes);if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG!=0)VideoStream.config=bytes;VideoStream.publish(bytes)};codec.releaseOutputBuffer(index,false)}
                }}catch(e:Exception){if(running){VideoStream.error="视频编码已停止";handler.post{stopSelf()}}}
            },"agent-video-encode").start()
        }catch(e:Exception){VideoStream.error=e.message ?: "无法开启共享";stopSelf()}
        return START_NOT_STICKY
    }
    override fun onDestroy(){running=false;VideoStream.stop();runCatching{display?.release()};runCatching{projection?.stop()};runCatching{encoder?.stop()};runCatching{encoder?.release()};runCatching{surface?.release()};super.onDestroy()}
}
