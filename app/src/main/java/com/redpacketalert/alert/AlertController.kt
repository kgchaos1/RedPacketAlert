package com.redpacketalert.alert

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.redpacketalert.core.AlertDecision
import com.redpacketalert.core.AlertSettings
import com.redpacketalert.core.NotificationSnapshot

/**
 * 强提醒调度中心：一次提醒由 声音 + 震动 + 闪光灯 + 悬浮窗 + 亮屏 组合而成，
 * 到点或用户点“我知道了”后统一停止。
 */
object AlertController {

    private val handler = Handler(Looper.getMainLooper())

    private var initialized = false
    private var tone: ToneAlerter? = null
    private var vibration: VibrationAlerter? = null
    private var torch: TorchAlerter? = null
    private var overlay: OverlayAlerter? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var stopRunnable: Runnable? = null
    private var currentKey: String? = null

    private var appContext: Context? = null

    fun init(context: Context) {
        val application = context.applicationContext
        appContext = application
        if (initialized) return
        tone = ToneAlerter(application)
        vibration = VibrationAlerter(application)
        torch = TorchAlerter(application)
        overlay = OverlayAlerter(application) { stop() }
        initialized = true
    }

    fun alert(
        context: Context,
        snapshot: NotificationSnapshot,
        decision: AlertDecision,
        settings: AlertSettings
    ) {
        init(context)
        stop()
        currentKey = snapshot.key
        val durationMs = settings.alertDurationSeconds.coerceIn(3, 120) * 1000L

        if (settings.screenOnEnabled) {
            acquireWakeLock(durationMs + 2_000L)
        }
        if (settings.soundEnabled) {
            tone?.start(settings.soundUri, settings.useAlarmStream)
        }
        if (settings.vibrateEnabled) {
            vibration?.start(settings.vibrateContinuous)
        }
        if (settings.torchEnabled) {
            torch?.start()
        }
        if (settings.overlayEnabled) {
            overlay?.show(snapshot.packageName, decision)
        }

        val runnable = Runnable { stop() }
        stopRunnable = runnable
        handler.postDelayed(runnable, durationMs)
    }

    fun stop() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        currentKey = null
        tone?.stop()
        vibration?.stop()
        torch?.stop()
        overlay?.hide()
        releaseWakeLock()
    }

    fun onNotificationRemoved(key: String?) {
        if (key != null && key == currentKey) stop()
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock(timeoutMs: Long) {
        try {
            val manager = appContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            releaseWakeLock()
            val lock = manager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "RedPacketAlert:screen"
            )
            lock.setReferenceCounted(false)
            lock.acquire(timeoutMs)
            wakeLock = lock
        } catch (ignored: Throwable) {
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        try {
            if (lock.isHeld) lock.release()
        } catch (ignored: Throwable) {
        }
    }
}
