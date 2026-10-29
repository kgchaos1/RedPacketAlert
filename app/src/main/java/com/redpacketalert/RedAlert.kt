package com.redpacketalert

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.redpacketalert.alert.AlertController
import com.redpacketalert.core.AlertDecision
import com.redpacketalert.core.Deduplicator
import com.redpacketalert.core.EventRecord
import com.redpacketalert.core.MessageLine
import com.redpacketalert.core.NotificationSnapshot
import com.redpacketalert.core.RuleEngine
import com.redpacketalert.data.EventStore
import com.redpacketalert.data.SettingsStore
import com.redpacketalert.platform.NotificationParser

/** 应用级单例：通知进来 -> 解析 -> 规则判定 -> 记录 -> 强提醒。 */
object RedAlert {

    private const val TAG = "RedAlert"
    private const val DEMO_PACKAGE = "com.redpacketalert.demo"
    private const val DEMO_CONVERSATION = "同学群"
    private const val DEMO_SENDER = "张三"
    private const val DEMO_MESSAGE = "张三: [微信红包] 恭喜发财，大吉大利"

    private val deduplicator = Deduplicator()

    @Volatile
    var listenerConnected: Boolean = false
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        SettingsStore.ensureListener(appContext)
        AlertController.init(appContext)
    }

    fun onListenerConnected() {
        listenerConnected = true
    }

    fun onListenerDisconnected() {
        listenerConnected = false
    }

    fun onNotification(
        context: Context,
        sbn: StatusBarNotification,
        ranking: NotificationListenerService.Ranking?
    ) {
        val appContext = context.applicationContext
        val settings = SettingsStore.settings(appContext)
        if (!settings.enabled) return

        val snapshot = try {
            NotificationParser.parse(sbn, ranking)
        } catch (throwable: Throwable) {
            Log.w(TAG, "解析通知失败", throwable)
            return
        }

        val decision = RuleEngine.evaluate(snapshot, settings, System.currentTimeMillis(), deduplicator)
        recordIfNeeded(appContext, snapshot, decision)
        if (decision.shouldAlert) {
            AlertController.alert(appContext, snapshot, decision, settings)
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        AlertController.onNotificationRemoved(sbn.key)
    }

    fun stopAll() {
        AlertController.stop()
    }

    /** 设置页里的“测试提醒”，不经过规则判定，直接走完整的提醒链路。 */
    fun triggerTest(context: Context) {
        val appContext = context.applicationContext
        val settings = SettingsStore.settings(appContext)
        val now = System.currentTimeMillis()
        val snapshot = NotificationSnapshot(
            packageName = DEMO_PACKAGE,
            notificationId = -1,
            tag = null,
            key = "demo:test",
            postTime = now,
            title = DEMO_CONVERSATION + "(3)",
            text = DEMO_MESSAGE,
            bigText = DEMO_MESSAGE,
            subText = "微信",
            conversationTitle = DEMO_CONVERSATION,
            messages = listOf(MessageLine(DEMO_SENDER, DEMO_MESSAGE, now)),
            isGroupConversation = true
        )
        val decision = AlertDecision(
            shouldAlert = true,
            reason = "测试提醒",
            matchedKeyword = "红包",
            conversation = snapshot.displayTitle,
            sender = DEMO_SENDER,
            text = snapshot.latestText
        )
        AlertController.alert(appContext, snapshot, decision, settings)
        EventStore.add(
            appContext,
            EventRecord(
                time = now,
                packageName = DEMO_PACKAGE,
                conversation = decision.conversation,
                sender = decision.sender,
                keyword = decision.matchedKeyword,
                text = decision.text,
                alerted = true,
                reason = decision.reason
            )
        )
    }

    private fun recordIfNeeded(
        context: Context,
        snapshot: NotificationSnapshot,
        decision: AlertDecision
    ) {
        // 只记录“命中了关键词”的通知，避免把无关应用的通知也灌进日志
        if (!decision.shouldAlert && decision.matchedKeyword == null) return
        EventStore.add(
            context,
            EventRecord(
                time = System.currentTimeMillis(),
                packageName = snapshot.packageName,
                conversation = decision.conversation,
                sender = decision.sender,
                keyword = decision.matchedKeyword,
                text = decision.text.take(300),
                alerted = decision.shouldAlert,
                reason = decision.reason
            )
        )
    }
}
