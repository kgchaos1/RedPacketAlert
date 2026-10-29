package com.redpacketalert.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.redpacketalert.RedAlert

/**
 * 系统级通知监听。只要用户在“通知使用权”里勾选本应用，
 * 无论微信群是否被折叠、通知是否显示详情，微信 post 出来的每一条通知都会到这里。
 */
class WeChatNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        RedAlert.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        RedAlert.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        super.onNotificationPosted(sbn, rankingMap)
        val ranking = Ranking()
        val hasRanking = rankingMap.getRanking(sbn.key ?: "", ranking)
        RedAlert.onNotification(this, sbn, if (hasRanking) ranking else null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        super.onNotificationRemoved(sbn, rankingMap)
        RedAlert.onNotificationRemoved(sbn)
    }
}
