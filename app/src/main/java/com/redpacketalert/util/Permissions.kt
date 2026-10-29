package com.redpacketalert.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** 各类系统权限的查询与跳转。 */
object Permissions {

    fun hasNotificationAccess(context: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    } catch (ignored: Throwable) {
        false
    }

    fun canDrawOverlays(context: Context): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (ignored: Throwable) {
        false
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        manager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } catch (ignored: Throwable) {
        false
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getLaunchIntentForPackage(packageName) != null
    } catch (ignored: Throwable) {
        false
    }

    fun openNotificationAccessSettings(context: Context) {
        startSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
        if (!startSafely(context, intent)) {
            startSafely(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName)
        )
        if (!startSafely(context, intent)) {
            startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openApp(context: Context, packageName: String): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            false
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }
    } catch (ignored: Throwable) {
        false
    }

    private fun startSafely(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    } catch (ignored: Throwable) {
        false
    }
}
