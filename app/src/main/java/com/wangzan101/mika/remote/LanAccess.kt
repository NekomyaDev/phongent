package com.wangzan101.mika.remote

import android.app.*
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import com.wangzan101.mika.R
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** One physical approval per trusted iPhone, independent of the main Activity. */
class LanAccess(private val context: Context) {
    private val prefs = context.getSharedPreferences("lan-peers", 0)
    private data class Offer(val id: String, val name: String, val deadline: Long, var status: String = "pending", var token: String? = null)
    private val offers = mutableMapOf<String, Offer>()
    private var lastRequest = -30_000L
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel("mika-lan-approval", "iPhone 连接申请", NotificationManager.IMPORTANCE_HIGH)) }
    @Synchronized fun authorized(header: String): Boolean = prefs.all.values.filterIsInstance<String>().any {
        MessageDigest.isEqual(header.toByteArray(), "Bearer $it".toByteArray())
    }
    @Synchronized fun request(body: JSONObject): Pair<Int, JSONObject> {
        val id = body.getString("requestId")
        require(id.matches(Regex("[A-Za-z0-9-]{64,80}")))
        val name = body.optString("name", "iPhone").filter { !it.isISOControl() }.take(60)
        val now = SystemClock.elapsedRealtime()
        offers.entries.removeAll { it.value.deadline < now }
        offers[id]?.let { return 200 to JSONObject().put("status", it.status) }
        if (now - lastRequest < 15_000 || offers.values.any { it.status == "pending" })
            return 429 to JSONObject().put("error", "approval_pending")
        if (!manager.areNotificationsEnabled()) return 409 to JSONObject().put("error", "enable_android_notifications")
        lastRequest = now
        offers[id] = Offer(id, name, now + 90_000)
        val open = PendingIntent.getActivity(context, 9201, Intent(context, LanApprovalActivity::class.java).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(allow: Boolean) = PendingIntent.getBroadcast(context, if (allow) 9202 else 9203,
            Intent(context, LanApprovalReceiver::class.java).setAction(if (allow) "allow" else "deny").putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(context, "mika-lan-approval").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$name 请求连接 Mika")
            .setContentText("允许后可读取消息预览、查看状态并控制此安卓助手")
            .setStyle(Notification.BigTextStyle().bigText("允许后，$name 可读取消息预览、查看状态并控制此安卓助手。仅允许你自己的 iPhone。"))
            .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(90_000)
            .addAction(Notification.Action.Builder(null, "拒绝", action(false)).build())
            .addAction(Notification.Action.Builder(null, "允许连接", action(true)).build()).build()
        manager.notify(9201, notice)
        return 202 to JSONObject().put("status", "pending")
    }
    @Synchronized fun name(id: String): String? = offers[id]?.takeIf { it.deadline > SystemClock.elapsedRealtime() && it.status == "pending" }?.name
    @Synchronized fun decide(id: String, allow: Boolean) {
        val offer = offers[id]?.takeIf { it.deadline > SystemClock.elapsedRealtime() && it.status == "pending" } ?: return
        if (allow) {
            val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            if (prefs.edit().putString(id, token).commit()) { offer.token = token; offer.status = "approved" }
            else offer.status = "denied"
        } else offer.status = "denied"
        manager.cancel(9201)
    }
    @Synchronized fun poll(id: String): JSONObject {
        val offer = offers[id]?.takeIf { it.deadline > SystemClock.elapsedRealtime() }
            ?: return JSONObject().put("status", "expired")
        return JSONObject().put("status", offer.status).apply { if (offer.status == "approved") put("token", offer.token) }
    }
}
class LanApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf("allow", "deny")) return
        LanService.access?.decide(intent.getStringExtra("id") ?: return, intent.action == "allow")
    }
}
class LanApprovalActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: return finish()
        val access = LanService.access ?: return finish()
        val name = access.name(id) ?: return finish()
        AlertDialog.Builder(this).setTitle("允许 $name 连接？")
            .setMessage("允许后可读取消息预览、查看状态并控制此安卓助手。之后会自动重连，无需配对码。")
            .setPositiveButton("允许连接") { _, _ -> access.decide(id, true); finish() }
            .setNegativeButton("拒绝") { _, _ -> access.decide(id, false); finish() }
            .setOnCancelListener { finish() }.show()
    }
}
