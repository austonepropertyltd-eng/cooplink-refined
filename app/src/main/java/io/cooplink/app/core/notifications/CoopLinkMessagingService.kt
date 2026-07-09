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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "CoopLinkMessaging"

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
                val existing = supabase.db["device_tokens"]
                    .select { filter { eq("user_id", uid); eq("token", token) } }
                    .decodeSingleOrNull<Map<String, String>>()
                if (existing == null) {
                    supabase.db["device_tokens"].insert(
                        buildJsonObject {
                            put("user_id", uid)
                            put("token", token)
                            put("platform", "android")
                        },
                    )
                }
            }.onFailure { Log.w(TAG, "Failed to upload device token", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        createNotificationChannels(this, getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        val title   = message.notification?.title ?: message.data["title"] ?: "CoopLink"
        val body    = message.notification?.body  ?: message.data["body"]  ?: ""
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
