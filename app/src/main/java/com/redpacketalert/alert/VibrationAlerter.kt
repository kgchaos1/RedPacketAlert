package com.redpacketalert.alert

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** 震动提醒：可持续循环震动，直到提醒结束或被手动关闭。 */
class VibrationAlerter(context: Context) {

    private val vibrator: Vibrator? =
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun start(continuous: Boolean) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        stop()
        try {
            // repeatIndex = 0 表示从头无限循环，由 AlertController 在提醒时长结束后 cancel
            val timings = if (continuous) {
                longArrayOf(0L, 600L, 350L, 600L, 350L)
            } else {
                longArrayOf(0L, 700L, 200L, 700L)
            }
            val effect = VibrationEffect.createWaveform(timings, if (continuous) 0 else -1)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            device.vibrate(effect, attributes)
        } catch (ignored: Throwable) {
        }
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (ignored: Throwable) {
        }
    }
}
