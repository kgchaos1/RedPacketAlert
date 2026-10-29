package com.redpacketalert.alert

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Toast
import com.redpacketalert.R
import com.redpacketalert.core.AlertDecision
import com.redpacketalert.databinding.OverlayAlertBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 悬浮窗强提醒：即使微信被折叠、通知不显示详情，也能在屏幕上弹出一张卡片告诉你
 * “哪个群、谁发的、什么内容”。
 */
class OverlayAlerter(
    private val context: Context,
    private val onDismiss: () -> Unit
) {

    private var windowManager: WindowManager? = null
    private var binding: OverlayAlertBinding? = null

    fun canDraw(): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (ignored: Throwable) {
        false
    }

    @Suppress("DEPRECATION")
    fun show(targetPackage: String, decision: AlertDecision) {
        if (!canDraw()) return
        hide()
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val themed = ContextThemeWrapper(context, R.style.Theme_RedPacketAlert)
        val inflater = LayoutInflater.from(themed)
        val viewBinding = OverlayAlertBinding.inflate(inflater)

        viewBinding.overlayConversation.text = formatConversation(decision)
        viewBinding.overlayMessage.text = decision.text.ifBlank { "（通知未提供消息内容）" }
        viewBinding.overlayKeyword.text = context.getString(
            R.string.overlay_keyword_format,
            decision.matchedKeyword ?: "-"
        )
        viewBinding.overlayTime.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        viewBinding.overlayOpen.setOnClickListener {
            openTargetApp(targetPackage)
            hide()
            onDismiss()
        }
        viewBinding.overlayDismiss.setOnClickListener {
            hide()
            onDismiss()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        try {
            manager.addView(viewBinding.root, params)
            windowManager = manager
            binding = viewBinding
        } catch (throwable: Throwable) {
            binding = null
            windowManager = null
        }
    }

    fun hide() {
        val view = binding?.root ?: return
        binding = null
        try {
            windowManager?.removeViewImmediate(view)
        } catch (ignored: Throwable) {
        }
        windowManager = null
    }

    private fun openTargetApp(targetPackage: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            if (intent == null) {
                Toast.makeText(context, R.string.toast_app_not_found, Toast.LENGTH_SHORT).show()
                return
            }
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            context.startActivity(intent)
        } catch (throwable: Throwable) {
            Toast.makeText(context, R.string.toast_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatConversation(decision: AlertDecision): String {
        val conversation = decision.conversation
        val sender = decision.sender
        return when {
            sender.isNullOrBlank() -> conversation ?: "未知会话"
            conversation.isNullOrBlank() -> sender
            conversation == sender -> conversation
            else -> conversation + " · " + sender
        }
    }
}
