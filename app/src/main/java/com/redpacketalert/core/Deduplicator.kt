package com.redpacketalert.core

/**
 * 微信会为同一个会话反复更新同一条通知（每来一条新消息就重新 post 一次），
 * 如果不去重，一个群里连发几条消息就会连续炸响。
 *
 * 去重键 = 包名 + 通知 id + 最新消息文本 + 发送者。
 * 时间来源由调用方注入，方便单元测试。
 */
class Deduplicator(private val capacity: Int = 64) {

    private val seen = LinkedHashMap<String, Long>()
    private var lastAlertAt = 0L

    /** true 表示这条通知在窗口期内出现过，应当跳过。 */
    fun shouldSuppress(key: String, now: Long, windowMs: Long): Boolean {
        if (windowMs <= 0L) return false
        prune(now)
        val previous = seen.remove(key)
        seen[key] = now
        return previous != null && now - previous < windowMs
    }

    fun markAlert(now: Long) {
        lastAlertAt = now
    }

    /** 距离上一次真正提醒是否太近（防止一次消息风暴里连环轰炸）。 */
    fun tooSoon(now: Long, minIntervalMs: Long): Boolean =
        lastAlertAt != 0L && minIntervalMs > 0L && now - lastAlertAt < minIntervalMs

    fun reset() {
        seen.clear()
        lastAlertAt = 0L
    }

    private fun prune(now: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > MAX_AGE_MS) iterator.remove()
        }
        while (seen.size > capacity) {
            val oldest = seen.keys.firstOrNull() ?: break
            seen.remove(oldest)
        }
    }

    private companion object {
        const val MAX_AGE_MS = 5 * 60 * 1000L
    }
}
