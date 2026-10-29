package com.redpacketalert.data

import android.content.Context
import android.content.SharedPreferences
import com.redpacketalert.core.EventRecord
import org.json.JSONArray
import org.json.JSONObject

/** 最近 200 条事件，持久化在 SharedPreferences 里，方便回看“为什么没提醒我”。 */
object EventStore {

    private const val PREFS_NAME = "red_alert_events"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 200

    fun add(context: Context, record: EventRecord) {
        val prefs = prefs(context)
        val list = loadFrom(prefs).toMutableList()
        list.add(0, record)
        while (list.size > MAX_EVENTS) {
            list.removeAt(list.size - 1)
        }
        save(prefs, list)
    }

    fun load(context: Context): List<EventRecord> = loadFrom(prefs(context))

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_EVENTS).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadFrom(prefs: SharedPreferences): List<EventRecord> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                EventRecord(
                    time = json.optLong("time"),
                    packageName = json.optString("pkg"),
                    conversation = json.optString("conversation").takeIf { it.isNotEmpty() },
                    sender = json.optString("sender").takeIf { it.isNotEmpty() },
                    keyword = json.optString("keyword").takeIf { it.isNotEmpty() },
                    text = json.optString("text"),
                    alerted = json.optBoolean("alerted"),
                    reason = json.optString("reason")
                )
            }
        } catch (ignored: Throwable) {
            emptyList()
        }
    }

    private fun save(prefs: SharedPreferences, list: List<EventRecord>) {
        val array = JSONArray()
        list.forEach { record ->
            array.put(JSONObject().apply {
                put("time", record.time)
                put("pkg", record.packageName)
                put("conversation", record.conversation.orEmpty())
                put("sender", record.sender.orEmpty())
                put("keyword", record.keyword.orEmpty())
                put("text", record.text)
                put("alerted", record.alerted)
                put("reason", record.reason)
            })
        }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply()
    }
}
