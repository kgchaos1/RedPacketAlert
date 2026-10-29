package com.redpacketalert.data

import android.content.Context
import android.content.SharedPreferences
import com.redpacketalert.core.AlertSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设置读写。读路径带内存缓存，微信连发通知时不会每次去解析 SharedPreferences。
 */
object SettingsStore {

    const val PREFS_NAME = "red_alert_settings"

    object Keys {
        const val ENABLED = "enabled"
        const val MONITORED_PACKAGES = "monitored_packages"
        const val KEYWORDS = "keywords"
        const val EXCLUDE_KEYWORDS = "exclude_keywords"
        const val WHITELIST_ENABLED = "whitelist_enabled"
        const val WHITELIST = "whitelist"
        const val MUTE_LIST = "mute_list"
        const val MATCH_SENDER = "match_sender"
        const val ALERT_UNKNOWN_GROUP = "alert_unknown_group"
        const val IGNORE_ONGOING = "ignore_ongoing"
        const val IGNORE_SUSPENDED = "ignore_suspended"
        const val DEDUP_WINDOW_MS = "dedup_window_ms"
        const val MIN_INTERVAL_MS = "min_interval_ms"
        const val SOUND_ENABLED = "sound_enabled"
        const val SOUND_URI = "sound_uri"
        const val USE_ALARM_STREAM = "use_alarm_stream"
        const val VIBRATE_ENABLED = "vibrate_enabled"
        const val VIBRATE_CONTINUOUS = "vibrate_continuous"
        const val TORCH_ENABLED = "torch_enabled"
        const val OVERLAY_ENABLED = "overlay_enabled"
        const val SCREEN_ON_ENABLED = "screen_on_enabled"
        const val ALERT_DURATION_SECONDS = "alert_duration_seconds"
    }

    private val changeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> invalidate() }

    @Volatile
    private var cached: AlertSettings? = null

    @Volatile
    private var listenerRegistered = false

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ensureListener(context: Context) {
        if (listenerRegistered) return
        synchronized(this) {
            if (listenerRegistered) return
            prefs(context).registerOnSharedPreferenceChangeListener(changeListener)
            listenerRegistered = true
        }
    }

    fun invalidate() {
        cached = null
    }

    fun settings(context: Context): AlertSettings {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val loaded = read(context)
            cached = loaded
            return loaded
        }
    }

    fun update(context: Context, block: (SharedPreferences.Editor) -> Unit) {
        val editor = prefs(context).edit()
        block(editor)
        editor.apply()
        invalidate()
    }

    fun readList(context: Context, key: String, default: List<String>): List<String> =
        readList(prefs(context), key, default)

    fun writeList(context: Context, key: String, values: List<String>) {
        update(context) { it.putString(key, JSONArray(values).toString()) }
    }

    private fun readList(prefs: SharedPreferences, key: String, default: List<String>): List<String> {
        val raw = prefs.getString(key, null) ?: return default
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        } catch (ignored: Throwable) {
            default
        }
    }

    private fun read(context: Context): AlertSettings {
        val prefs = prefs(context)
        val defaults = AlertSettings()
        return AlertSettings(
            enabled = prefs.getBoolean(Keys.ENABLED, defaults.enabled),
            monitoredPackages = readList(prefs, Keys.MONITORED_PACKAGES, defaults.monitoredPackages),
            keywords = readList(prefs, Keys.KEYWORDS, defaults.keywords),
            excludeKeywords = readList(prefs, Keys.EXCLUDE_KEYWORDS, defaults.excludeKeywords),
            whitelistEnabled = prefs.getBoolean(Keys.WHITELIST_ENABLED, defaults.whitelistEnabled),
            whitelist = readList(prefs, Keys.WHITELIST, defaults.whitelist),
            muteList = readList(prefs, Keys.MUTE_LIST, defaults.muteList),
            matchSenderName = prefs.getBoolean(Keys.MATCH_SENDER, defaults.matchSenderName),
            alertWhenGroupUnknown = prefs.getBoolean(Keys.ALERT_UNKNOWN_GROUP, defaults.alertWhenGroupUnknown),
            ignoreOngoing = prefs.getBoolean(Keys.IGNORE_ONGOING, defaults.ignoreOngoing),
            ignoreSuspended = prefs.getBoolean(Keys.IGNORE_SUSPENDED, defaults.ignoreSuspended),
            dedupWindowMs = prefs.getLong(Keys.DEDUP_WINDOW_MS, defaults.dedupWindowMs),
            minAlertIntervalMs = prefs.getLong(Keys.MIN_INTERVAL_MS, defaults.minAlertIntervalMs),
            soundEnabled = prefs.getBoolean(Keys.SOUND_ENABLED, defaults.soundEnabled),
            soundUri = prefs.getString(Keys.SOUND_URI, defaults.soundUri),
            useAlarmStream = prefs.getBoolean(Keys.USE_ALARM_STREAM, defaults.useAlarmStream),
            vibrateEnabled = prefs.getBoolean(Keys.VIBRATE_ENABLED, defaults.vibrateEnabled),
            vibrateContinuous = prefs.getBoolean(Keys.VIBRATE_CONTINUOUS, defaults.vibrateContinuous),
            torchEnabled = prefs.getBoolean(Keys.TORCH_ENABLED, defaults.torchEnabled),
            overlayEnabled = prefs.getBoolean(Keys.OVERLAY_ENABLED, defaults.overlayEnabled),
            screenOnEnabled = prefs.getBoolean(Keys.SCREEN_ON_ENABLED, defaults.screenOnEnabled),
            alertDurationSeconds = prefs.getInt(Keys.ALERT_DURATION_SECONDS, defaults.alertDurationSeconds)
        )
    }
}
