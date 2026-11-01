package com.wangzan101.mika.remote

import android.app.*
import android.content.*
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.MainActivity
import java.net.ServerSocket
import java.util.*

/** Lifetime belongs to a foreground Service, never to an Activity or floating window. */
class LanService : Service() {
    companion object {
        @Volatile var access: LanAccess? = null
            private set
        fun start(context: Context) { context.startForegroundService(Intent(context, LanService::class.java)) }
    }
    private val main=Handler(Looper.getMainLooper())
    private var registrationAttempts=0
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var destroyed = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("mika-lan", "局域网连接服务", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 9200, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(9200, Notification.Builder(this, "mika-lan").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("agent 局域网连接已开启").setContentText("后台和悬浮窗均可连接 · 已授权的主控自动重连")
            .setContentIntent(open).setOngoing(true).build())
        access = LanAccess(this)
        Thread({
            try {
                val listener = ServerSocket(0, 8)
                Handler(Looper.getMainLooper()).post {
                    if (destroyed) { listener.close(); return@post }
                    server = listener
                    RemoteBridge(application as MikaApp).serve(listener, access!!)
                    val prefs = getSharedPreferences("lan-identity", 0)
                    val id = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).commit() }
                    val info = NsdServiceInfo().apply {
                        serviceName = "Mika-${id.take(8)}"; serviceType = "_mika-agent._tcp."; port = listener.localPort
                        setAttribute("id", id); setAttribute("name", DeviceIdentity.name(this@LanService)); setAttribute("transport", "tcp-http-v1")
                    }
                    register(info)
                }
            } catch (_: Exception) { Handler(Looper.getMainLooper()).post { stopSelf() } }
        }, "mika-lan-setup").start()
    }
    private fun register(info:NsdServiceInfo) {
        if(destroyed)return
        val manager=getSystemService(NsdManager::class.java)
        fun retry(){
            if(destroyed)return
            registrationAttempts=(registrationAttempts+1).coerceAtMost(6)
            main.postDelayed({register(info)},(1000L shl registrationAttempts).coerceAtMost(60000L))
        }
        val callback=object:NsdManager.RegistrationListener {
            override fun onServiceRegistered(service:NsdServiceInfo){registrationAttempts=0}
            override fun onRegistrationFailed(service:NsdServiceInfo,error:Int){
                android.util.Log.w("AgentLAN","Discovery registration failed: $error; keeping TCP service alive")
                runCatching{manager.unregisterService(this)}
                retry()
            }
            override fun onServiceUnregistered(service:NsdServiceInfo){}
            override fun onUnregistrationFailed(service:NsdServiceInfo,error:Int){}
        }
        registration=callback
        try{manager.registerService(info,NsdManager.PROTOCOL_DNS_SD,callback)}catch(e:Exception){
            android.util.Log.w("AgentLAN","Discovery registration unavailable",e);retry()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        LanAccess.clearPending()
        registration?.let { runCatching { getSystemService(NsdManager::class.java).unregisterService(it) } }
        runCatching { server?.close() }; server = null; access = null
        super.onDestroy()
    }
}
