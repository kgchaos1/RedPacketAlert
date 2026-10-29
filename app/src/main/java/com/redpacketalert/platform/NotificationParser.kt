package com.redpacketalert.platform

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.redpacketalert.core.MessageLine
import com.redpacketalert.core.NotificationSnapshot

/**
 * 把系统通知里的所有字段“榨干”：标题、正文、大字文本、子文本、InboxStyle 行、
 * MessagingStyle 消息、会话标题（Android 11+ 才有，微信 8.x 会给）等。
 * 折叠群、聚合通知往往把群名放在不同的字段里，全部收集后统一做关键词/白名单判断。
 */
object NotificationParser {

    private const val EXTRA_CONVERSATION_TITLE = "android.conversationTitle"
    private const val EXTRA_MESSAGES = "android.messages"
    private const val EXTRA_IS_GROUP_CONVERSATION = "android.isGroupConversation"
    private const val EXTRA_PEOPLE_LIST = "android.people.list"

    fun parse(
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.Ranking? = null
    ): NotificationSnapshot {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle()

        return NotificationSnapshot(
            packageName = sbn.packageName ?: "",
            notificationId = sbn.id,
            tag = sbn.tag,
            key = sbn.key ?: "",
            postTime = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis(),
            title = text(extras, Notification.EXTRA_TITLE),
            text = text(extras, Notification.EXTRA_TEXT),
            bigText = text(extras, Notification.EXTRA_BIG_TEXT),
            subText = text(extras, Notification.EXTRA_SUB_TEXT),
            infoText = text(extras, Notification.EXTRA_INFO_TEXT),
            summaryText = text(extras, Notification.EXTRA_SUMMARY_TEXT),
            tickerText = notification.tickerText?.toString(),
            conversationTitle = text(extras, EXTRA_CONVERSATION_TITLE),
            channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else null,
            category = notification.category,
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map { it.toString() } ?: emptyList(),
            messages = extractMessages(extras),
            people = extractPeople(extras),
            isGroupConversation = extras.getBoolean(EXTRA_IS_GROUP_CONVERSATION, false),
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isClearable = sbn.isClearable,
            isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ranking?.isSuspended ?: false
            } else {
                false
            }
        )
    }

    private fun text(extras: Bundle, key: String): String? =
        extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }

    /**
     * 解析 MessagingStyle 的消息列表。
     * 注意：Notification.MessagingStyle#extractMessagingStyleFromNotification 是 @hide API，
     * 第三方应用只能用公开的 Message#getMessagesFromBundleArray。
     */
    @Suppress("DEPRECATION")
    private fun extractMessages(extras: Bundle): List<MessageLine> {
        val result = ArrayList<MessageLine>()
        try {
            val raw = extras.getParcelableArray(EXTRA_MESSAGES) ?: return result
            val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw)
            messages.forEach { message ->
                val senderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    message.senderPerson?.name?.toString()
                } else {
                    message.sender?.toString()
                }
                result.add(
                    MessageLine(
                        sender = senderName?.takeIf { it.isNotBlank() },
                        text = message.text?.toString().orEmpty(),
                        timestamp = message.timestamp
                    )
                )
            }
        } catch (ignored: Throwable) {
            // 不同厂商 ROM 的通知结构千奇百怪，解析失败就退回到纯文本匹配
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun extractPeople(extras: Bundle): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
        return try {
            val list = extras.getParcelableArrayList<Person>(EXTRA_PEOPLE_LIST) ?: return emptyList()
            list.mapNotNull { it.name?.toString()?.takeIf { name -> name.isNotBlank() } }
        } catch (ignored: Throwable) {
            emptyList()
        }
    }
}
