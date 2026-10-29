package com.redpacketalert.core

import java.util.Locale

/** Notification.MessagingStyle 里的一条消息（微信多数情况不用它，但很多应用会用）。 */
data class MessageLine(
    val sender: String?,
    val text: String,
    val timestamp: Long
)

/**
 * 一条系统通知的可比较快照。
 *
 * 这个类刻意不依赖任何 Android 类型，方便在 JVM 单元测试里直接构造断言；
 * 从 StatusBarNotification 到本类的转换放在 platform/NotificationParser.kt。
 */
data class NotificationSnapshot(
    val packageName: String,
    val notificationId: Int,
    val tag: String?,
    val key: String,
    val postTime: Long,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val infoText: String? = null,
    val summaryText: String? = null,
    val tickerText: String? = null,
    val conversationTitle: String? = null,
    val channelId: String? = null,
    val category: String? = null,
    val textLines: List<String> = emptyList(),
    val messages: List<MessageLine> = emptyList(),
    val people: List<String> = emptyList(),
    val isGroupConversation: Boolean = false,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val isSuspended: Boolean = false
) {

    /** 所有可能承载关键词的文本，关键词匹配只看这个字段。 */
    val combinedText: String by lazy(LazyThreadSafetyMode.NONE) {
        val sb = StringBuilder()
        listOf(title, text, bigText, subText, infoText, summaryText, tickerText, conversationTitle)
            .forEach { appendLine(sb, it) }
        textLines.forEach { appendLine(sb, it) }
        messages.forEach { appendLine(sb, it.text) }
        people.forEach { appendLine(sb, it) }
        sb.toString()
    }

    /** 真正“最新”的一条消息内容，用于展示和去重。 */
    val latestText: String by lazy(LazyThreadSafetyMode.NONE) {
        messages.maxByOrNull { it.timestamp }?.text?.takeIf { it.isNotBlank() }
            ?: bigText?.takeIf { it.isNotBlank() }
            ?: text?.takeIf { it.isNotBlank() }
            ?: textLines.lastOrNull()?.takeIf { it.isNotBlank() }
            ?: ""
    }

    val latestSender: String? by lazy(LazyThreadSafetyMode.NONE) {
        messages.maxByOrNull { it.timestamp }?.sender?.takeIf { it.isNotBlank() }
            ?: senderFromText(latestText)
            ?: senderFromText(bigText)
            ?: senderFromText(text)
    }

    /** 可能是“群聊名/会话名”的候选值，按可信度排序。 */
    val groupCandidates: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        val result = LinkedHashSet<String>()
        result.addName(conversationTitle)
        result.addName(title)
        result.addName(subText)
        result.addName(summaryText)
        result.toList()
    }

    /** 可能是“发送者昵称”的候选值。 */
    val senderCandidates: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        val result = LinkedHashSet<String>()
        result.addName(latestSender)
        messages.forEach { result.addName(it.sender) }
        people.forEach { result.addName(it) }
        result.toList()
    }

    val displayTitle: String by lazy(LazyThreadSafetyMode.NONE) {
        conversationTitle?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: subText?.takeIf { it.isNotBlank() }
            ?: "未知会话"
    }

    /** 同一条通知内容重复推送时用来去重。 */
    val dedupKey: String by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            packageName,
            notificationId.toString(),
            TextTools.normalize(latestText),
            TextTools.normalize(latestSender)
        ).joinToString("|")
    }

    companion object {
        private val GENERIC_NAMES = setOf(
            "微信", "wechat", "weixin", "微信团队", "订阅号", "订阅号消息", "服务通知",
            "微信支付", "折叠的群聊", "折叠群聊", "折叠群消息", "折叠消息", "新消息", "消息",
            "群聊", "微信运动", "文件传输助手", "android系统", "系统界面"
        )

        private val UNREAD_SUFFIX = Regex("[（(]\\s*\\d+\\s*[）)]$")

        /** 过滤掉“微信”“折叠的群聊”这类无信息量的通用名，并去掉未读数后缀。 */
        fun cleanName(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var value = raw.trim().replace('\uFF1A', ':')
            value = UNREAD_SUFFIX.replace(value, "").trim()
            if (value.isEmpty()) return null
            if (GENERIC_NAMES.contains(value.lowercase(Locale.ROOT))) return null
            return value
        }

        /** 从 "张三: [微信红包]" 这种文本里抠出发送者。 */
        fun senderFromText(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val idx = raw.indexOfFirst { it == ':' || it == '\uFF1A' }
            if (idx <= 0) return null
            val name = raw.substring(0, idx).trim()
            if (name.isEmpty() || name.length > 32) return null
            return name
        }
    }
}

private fun appendLine(sb: StringBuilder, value: String?) {
    if (!value.isNullOrBlank()) sb.append(value).append('\n')
}

private fun MutableSet<String>.addName(raw: String?) {
    val cleaned = NotificationSnapshot.cleanName(raw) ?: return
    add(cleaned)
}
