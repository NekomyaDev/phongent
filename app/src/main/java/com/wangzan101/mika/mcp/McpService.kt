package com.wangzan101.mika.mcp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.task.TaskService

/**
 * 电脑连接打开期间的前台服务:只为让进程在后台活着、端口一直开着;收到的任务照旧交给 [TaskService] 去跑。
 * 任务服务没任务时会自己退出,所以监听不放在它里面。常驻通知上能直接关掉电脑连接。
 * 被系统回收后会重建(START_STICKY);开关已经关了就当场退出。
 */
class McpService : Service() {
    private val runtime get() = (application as MikaApp).mcp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TaskService.ensureChannels(this)
        // startForegroundService 之后必须尽快进前台,哪怕马上就要退出
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        if (intent?.action == ACTION_TURN_OFF) runtime.settings.setEnabled(false)
        if (!runtime.settings.enabled.value || !runtime.startServer()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runtime.stopServer()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val port = runtime.settings.port
        val open = TaskService.openApp(this)
        val off = PendingIntent.getService(this, 1, Intent(this, McpService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, TaskService.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr(R.string.mcp_notif_title))
            .setContentText(tr(R.string.mcp_notif_body, port))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, tr(R.string.mcp_turn_off), off).build())
            .build()
    }

    companion object {
        /** 和任务通知(槽位编号、结果编号段)错开。 */
        private const val NOTIFICATION_ID = 30_001
        private const val ACTION_TURN_OFF = "com.wangzan101.mika.mcp.TURN_OFF"
    }
}
