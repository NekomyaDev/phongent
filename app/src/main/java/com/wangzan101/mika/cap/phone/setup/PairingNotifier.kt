package com.wangzan101.mika.cap.phone.setup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import com.wangzan101.mika.R
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.core.shell.PairingDiscovery
import com.wangzan101.mika.ui.MainActivity
import com.wangzan101.mika.ui.PhoneSetupCopy
import com.wangzan101.mika.setup.PairingCodeReceiver

/** 配对通知此刻该显示什么。 */
sealed interface PairingNotice {
    /** 带输入框的「填写配对码」;[error] 是上一次没成功的原因。 */
    data class EnterCode(val port: Int?, val error: String? = null) : PairingNotice
    data object Working : PairingNotice
    data object Done : PairingNotice
    /** 配对后没能连上:回 Mika 看怎么处理。 */
    data class Problem(val title: String) : PairingNotice

    companion object {
        /**
         * 只在用户正在配对的这段时间里用([PairingCoordinator] 开着时)。找到配对窗口才弹输入框;
         * 配对阶段的失败(码不对、窗口关了)留在通知里重填,别的失败请用户回 Mika。
         */
        fun of(status: BuiltinShellStatus, discovery: PairingDiscovery): PairingNotice? {
            val port = (discovery as? PairingDiscovery.Found)?.port
            return when (status) {
                BuiltinShellStatus.Running -> Done
                BuiltinShellStatus.Connecting -> Working
                is BuiltinShellStatus.Failed ->
                    if (status.reason.duringPairing) EnterCode(port, PhoneSetupCopy.failure(status.reason).title)
                    else Problem(PhoneSetupCopy.failure(status.reason).title)
                BuiltinShellStatus.NotPaired, BuiltinShellStatus.WirelessDebuggingOff -> port?.let { EnterCode(it) }
            }
        }

        /** 无障碍在读配对码时,填码通知晚这么久再弹,多半已经配上了,不打扰。 */
        const val AUTO_READ_GRACE_MS = 5_000L
        /** 填码通知弹出后没人填,过这么久再提醒一次(只一次)。 */
        const val REALERT_MS = 8_000L

        /** 发出 [notice] 前等多久。 */
        fun delayBefore(notice: PairingNotice, autoRead: Boolean): Long =
            if (notice is EnterCode && autoRead) AUTO_READ_GRACE_MS else 0L

        /** 发出后多久再提醒一次;null 表示不再提醒。 */
        fun realertAfter(notice: PairingNotice): Long? = if (notice is EnterCode) REALERT_MS else null
    }
}

/**
 * 配对通知:高优先级、带声音和振动(勿扰时听系统的),能在系统设置的配对窗口上方弹出来;
 * 输入框用 RemoteInput,用户不用离开配对窗口。输入的码交给 [PairingCodeInput]。
 */
object PairingNotifier {
    /** 频道的振动建好后改不了,换了新 id;[OLD_CHANNEL] 是之前的,删掉免得设置里多一项。 */
    const val CHANNEL = "pairing_code"
    private const val OLD_CHANNEL = "pairing"
    const val KEY_CODE = "pairing_code"
    const val EXTRA_PORT = "pairing_port"
    private const val ID = 4401
    private const val DONE_TIMEOUT_MS = 15_000L

    fun show(context: Context, notice: PairingNotice) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, PhoneSetupCopy.CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
        when (notice) {
            is PairingNotice.EnterCode -> {
                val input = RemoteInput.Builder(KEY_CODE).setLabel(PhoneSetupCopy.NOTIFY_INPUT_LABEL).build()
                // RemoteInput 要往 Intent 里填结果,必须是可变的
                val reply = PendingIntent.getBroadcast(
                    context, 1,
                    Intent(context, PairingCodeReceiver::class.java).apply { notice.port?.let { putExtra(EXTRA_PORT, it) } },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.setContentTitle(PhoneSetupCopy.NOTIFY_ENTER_TITLE)
                    .setContentText(notice.error ?: PhoneSetupCopy.NOTIFY_ENTER_TEXT)
                    .setOngoing(true)
                    // 每次发都要响铃、弹出,包括没人填时的再次提醒
                    .setOnlyAlertOnce(false)
                    .addAction(Notification.Action.Builder(null, PhoneSetupCopy.NOTIFY_ACTION, reply).addRemoteInput(input).build())
            }
            PairingNotice.Working -> b.setContentTitle(PhoneSetupCopy.NOTIFY_WORKING).setOngoing(true).setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
            PairingNotice.Done -> b.setContentTitle(PhoneSetupCopy.NOTIFY_DONE_TITLE).setContentText(PhoneSetupCopy.NOTIFY_DONE_TEXT)
                .setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(DONE_TIMEOUT_MS)
            is PairingNotice.Problem -> b.setContentTitle(notice.title).setContentText(PhoneSetupCopy.NOTIFY_PROBLEM_TEXT).setAutoCancel(true)
        }
        // 没允许通知时系统会丢掉它;引导页在配对这一步之前就请求了权限
        runCatching { nm.notify(ID, b.build()) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
