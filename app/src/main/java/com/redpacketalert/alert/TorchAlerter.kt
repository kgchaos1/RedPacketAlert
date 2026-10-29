package com.redpacketalert.alert

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 闪光灯闪烁。注意：CameraManager#setTorchMode 不需要 CAMERA 权限，
 * 但如果相机正在被占用会抛异常，这里全部吞掉，不能因为闪光灯失败影响其他提醒。
 */
class TorchAlerter(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var cameraId: String? = null
    private var running = false
    private var lit = false

    private val blink = object : Runnable {
        override fun run() {
            if (!running) return
            lit = !lit
            setTorch(lit)
            handler.postDelayed(this, if (lit) 260L else 180L)
        }
    }

    fun start() {
        if (running) return
        val id = resolveCameraId() ?: return
        cameraId = id
        running = true
        handler.post(blink)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(blink)
        lit = false
        setTorch(false)
    }

    private fun resolveCameraId(): String? = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        manager?.cameraIdList?.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (throwable: Throwable) {
        null
    }

    private fun setTorch(enabled: Boolean) {
        val id = cameraId ?: return
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            manager.setTorchMode(id, enabled)
        } catch (throwable: Throwable) {
            Log.w(TAG, "闪光灯控制失败", throwable)
        }
    }

    private companion object {
        const val TAG = "TorchAlerter"
    }
}
