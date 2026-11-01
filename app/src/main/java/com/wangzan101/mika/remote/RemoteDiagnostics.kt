package com.wangzan101.mika.remote
import com.wangzan101.mika.MikaApp
import org.json.JSONObject
import org.json.JSONArray

class RemoteDiagnostics(private val app:MikaApp){
    fun usage():JSONObject {
        val snapshot=app.usage.snapshot()
        val rows=snapshot.rows.groupBy{it.key}.map{(key,rows)->val sum=rows.map{it.counts}.fold(com.wangzan101.mika.core.usage.UsageCounts()){a,b->a+b};JSONObject().put("provider",snapshot.providerNames[key.providerId] ?: key.providerId).put("model",key.model).put("purpose",key.purpose.name).put("input",sum.input).put("output",sum.output).put("cached",sum.cached).put("calls",sum.calls).put("unknownCalls",sum.callsWithoutUsage)}
        return JSONObject().put("items",JSONArray(rows)).put("since",snapshot.rows.minOfOrNull{it.hour*3600000})
    }
    fun diagnostics():JSONObject {
        val manager=app.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val access=manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.resolveInfo.serviceInfo.packageName==app.packageName}
        return JSONObject().put("version",com.wangzan101.mika.BuildConfig.VERSION_NAME).put("android",android.os.Build.VERSION.RELEASE).put("device",android.os.Build.MODEL).put("accessibility",access)
            .put("notifications",app.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()).put("notificationListener",MessageListener.connected)
            .put("freeBytes",app.filesDir.usableSpace).put("busy",app.taskState.busy.value).put("queue",RemoteTaskQueue.get(app).status())
    }
}
