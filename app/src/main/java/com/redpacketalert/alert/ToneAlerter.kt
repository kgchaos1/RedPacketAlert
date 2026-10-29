package com.redpacketalert.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

/** 铃声提醒：循环播放选定的铃声，默认走闹钟音量通道，静音/勿扰下也尽量响。 */
class ToneAlerter(private val context: Context) {

    private var player: MediaPlayer? = null

    fun start(uriString: String?, useAlarmStream: Boolean) {
        stop()
        val uri = resolveUri(uriString, useAlarmStream) ?: return
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (useAlarmStream) AudioAttributes.USAGE_ALARM
                        else AudioAttributes.USAGE_NOTIFICATION_EVENT
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mediaPlayer.setDataSource(context, uri)
            mediaPlayer.isLooping = true
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mediaPlayer.prepareAsync()
            player = mediaPlayer
        } catch (throwable: Throwable) {
            Log.w(TAG, "播放铃声失败", throwable)
            stop()
        }
    }

    fun stop() {
        val mediaPlayer = player ?: return
        player = null
        try {
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
        } catch (ignored: Throwable) {
        }
        try {
            mediaPlayer.release()
        } catch (ignored: Throwable) {
        }
    }

    private fun resolveUri(uriString: String?, useAlarmStream: Boolean): Uri? {
        uriString?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        val primary = if (useAlarmStream) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
        return RingtoneManager.getDefaultUri(primary)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    private companion object {
        const val TAG = "ToneAlerter"
    }
}
