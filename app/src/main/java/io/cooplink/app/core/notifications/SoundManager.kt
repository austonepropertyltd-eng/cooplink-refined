package io.cooplink.app.core.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cooplink.app.R
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SoundManager"

@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var mediaPlayer: MediaPlayer? = null

    fun playNotification() = play(R.raw.cooplink_notification)
    fun playSuccess()      = play(R.raw.cooplink_success)
    fun playAlert()        = play(R.raw.cooplink_alert)
    fun playMessage()      = play(R.raw.cooplink_message)
    fun playReminder()     = play(R.raw.cooplink_reminder)

    private fun play(resId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed: ${e.message}")
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
