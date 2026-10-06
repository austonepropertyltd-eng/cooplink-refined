package io.cooplink.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.cooplink.app.MainActivity
import io.cooplink.app.R
import io.cooplink.app.core.data.NotificationPreferences
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "CoopLinkMessaging"

@Serializable
private data class DeviceTokenUpsert(
    val user_id: String,
    val token: String,
    // No default — kotlinx.serialization omits a property still sitting at
    // its default value, silently dropping it from the request (the same trap
    // documented on NewRepaymentRequest.type in AdminRepaymentsViewModel).
    val platform: String,
)

@AndroidEntryPoint
class CoopLinkMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationPreferences: NotificationPreferences
    @Inject lateinit var supabase: SupabaseClient

    // onNewToken isn't a suspend function and this Service has no
    // lifecycleScope of its own — a small SupervisorJob-backed scope lets the
    // upload survive independent of any single call, without leaking beyond
    // the process (fire-and-forget, matches the rest of this service's
    // best-effort push handling).
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        serviceScope.launch {
            runCatching {
                // upsert against the unique(user_id, token) constraint rather
                // than select-then-insert: the read/write pair races this
                // callback against MemberShell's launch-time registration into
                // a duplicate-key failure, and re-registering an unchanged
                // token has to be a no-op rather than an error. Matches
                // NotificationSettingsViewModel.saveDeviceToken.
                supabase.db["device_tokens"].upsert(
                    DeviceTokenUpsert(user_id = uid, token = token, platform = "android"),
                ) { onConflict = "user_id,token" }
            }.onFailure {
                // A token that never reaches the backend means this device
                // receives no push at all, with nothing visible to the user to
                // say so. MemberShell re-registers on every launch, so this
                // does recover on next start — logged at error level because
                // it is the first thing to check when a "push never arrived"
                // report comes in, and it is otherwise indistinguishable from
                // a backend delivery problem.
                Log.e(
                    TAG,
                    "Device token upload failed for $uid — no push can reach this device " +
                        "until the next app launch re-registers it",
                    it,
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        createNotificationChannels(this, getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        // `message` is accepted alongside `body` because the app's own senders
        // (e.g. AdminLoansViewModel) post the text under the same key the
        // notifications table uses — without this fallback a push whose data
        // payload says `message` would arrive with an empty body.
        val title   = message.notification?.title ?: message.data["title"] ?: "CoopLink"
        val body    = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: ""
        val channel = message.data["channel"] ?: "announcements"

        // FCM delivers this on a background thread outside a coroutine scope,
        // so a short blocking DataStore read is the pragmatic option here —
        // security alerts always show regardless of preference.
        val allowed = channel == "security" || runBlocking {
            val prefs = notificationPreferences.prefsFlow.first()
            if (!prefs.allEnabled) false else when (channel) {
                "loans" -> prefs.loans
                "reminders" -> prefs.reminders
                "announcements", "messages" -> prefs.announcements
                "transactions" -> prefs.contributions || prefs.repayments
                else -> true
            }
        }
        if (allowed) showNotification(title, body, channel)
    }

    private fun showNotification(title: String, body: String, channelId: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build(),
        )
    }

    companion object {
        /** Also called from [io.cooplink.app.CoopLinkApplication.onCreate] so
         * these channels (and their sounds) exist before any notification —
         * local or push — is ever shown, not just after the first push. */
        fun createNotificationChannels(context: Context, manager: NotificationManager) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val channels = listOf(
                Triple("transactions", "Transactions", R.raw.cooplink_success),
                Triple("loans", "Loans & Disbursements", R.raw.cooplink_alert),
                Triple("announcements", "Announcements", R.raw.cooplink_notification),
                Triple("reminders", "Reminders", R.raw.cooplink_reminder),
                Triple("security", "Security Alerts", R.raw.cooplink_alert),
                Triple("messages", "Admin Messages", R.raw.cooplink_message),
            )

            channels.forEach { (id, name, soundRes) ->
                val soundUri = Uri.parse("android.resource://${context.packageName}/$soundRes")
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = name
                    setSound(soundUri, audioAttrs)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 100, 250)
                    enableLights(true)
                    lightColor = android.graphics.Color.parseColor("#FCB424")
                }

                manager.createNotificationChannel(channel)
            }
        }
    }
}
