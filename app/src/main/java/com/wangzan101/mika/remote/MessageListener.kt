package com.wangzan101.mika.remote

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Notifications are previews, not authoritative conversation/message identifiers. */
class MessageListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        synchronized(lock) {
            storage = getSharedPreferences("notification-history", 0)
            if (items.isEmpty()) runCatching {
                val saved = JSONArray(storage?.getString("records", "[]") ?: "[]")
                for (i in 0 until saved.length()) items.add(saved.getJSONObject(i).put("canOpen", false))
            }
        }
    }
    override fun onListenerConnected() {
        connected = true
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
    }
    override fun onListenerDisconnected() { connected = false }
    override fun onDestroy() { connected = false; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val appName = apps[sbn.packageName] ?: return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val rich = (extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? android.os.Bundle } ?: emptyList())
        val entries = rich.mapNotNull { entry ->
            val text = entry.getCharSequence("text")?.toString().orEmpty()
            val time = entry.getLong("time", sbn.postTime)
            if (text.isBlank()) null else Pair(text, time)
        }.ifEmpty {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            val text = lines?.takeIf { it.isNotBlank() } ?: (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) emptyList() else listOf(Pair(text, sbn.postTime))
        }
        synchronized(lock) {
            for ((body, time) in entries) {
                val conversation = sbn.notification.shortcutId?.takeIf { it.isNotBlank() } ?: title
                val id = MessageDigest.getInstance("SHA-256").digest("${sbn.key}|$time|$title|$body".toByteArray()).joinToString("") { "%02x".format(it) }
                sbn.notification.contentIntent?.let { targets[id] = it }
                val existing = items.firstOrNull { it.optString("id") == id }
                if (existing != null) { existing.put("canOpen", targets.containsKey(id)); continue }
                val link = android.util.Patterns.WEB_URL.matcher(body).let { match ->
                    if (match.find()) match.group().takeIf { it.startsWith("https://") || it.startsWith("http://") } else null
                }
                items.add(0, JSONObject().put("conversationId", sbn.packageName + ":" + conversation)
                    .put("canOpen", targets.containsKey(id)).put("link", link ?: JSONObject.NULL)
                    .put("receivedAt", System.currentTimeMillis() / 1000.0).put("id", id).put("app", appName)
                    .put("package", sbn.packageName).put("title", title.take(300)).put("text", body.take(8000)).put("time", time / 1000.0))
            }
            while (items.size > 1000) { targets.remove(items.removeAt(items.lastIndex).optString("id")) }
            storage?.edit()?.putString("records", JSONArray(items.toList()).toString())?.apply()
            revision++; lock.notifyAll(); BridgeEvents.signal()
        }
    }

    companion object {
        @Volatile var connected = false
            private set
        private val apps = mapOf("com.tencent.mm" to "微信", "com.tencent.mobileqq" to "QQ", "com.xingin.xhs" to "小红书")
        private val lock = java.lang.Object()
        private var revision = System.currentTimeMillis()
        private val targets = mutableMapOf<String, android.app.PendingIntent>()
        fun open(id: String) {
            val target = synchronized(lock) { targets[id] } ?: error("notification_expired")
            val options = android.app.ActivityOptions.makeBasic()
            if (android.os.Build.VERSION.SDK_INT >= 34) options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            target.send(null, 0, null, null, null, null, options.toBundle())
        }
        fun changes(after: Long, waitSeconds: Long = 20): JSONObject = synchronized(lock) {
            val deadline = android.os.SystemClock.elapsedRealtime() + waitSeconds.coerceIn(0, 60) * 1000
            while (revision == after) {
                val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                lock.wait(remaining)
            }
            JSONObject().put("revision", revision).put("messages", JSONArray(items.toList()))
        }
        private var storage: android.content.SharedPreferences? = null
        private val items = mutableListOf<JSONObject>()
        fun snapshot(): JSONArray = synchronized(lock) { JSONArray(items.toList()) }
    }
}
