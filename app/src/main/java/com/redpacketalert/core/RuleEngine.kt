package com.redpacketalert.core

/** 规则引擎的判定结果。 */
data class AlertDecision(
    val shouldAlert: Boolean,
    val reason: String,
    val matchedKeyword: String? = null,
    val conversation: String? = null,
    val sender: String? = null,
    val text: String = ""
)

/**
 * 纯逻辑判定：给定一条通知快照和当前设置，决定“要不要强提醒”。
 * 不依赖 Android，可直接跑 JVM 单元测试。
 */
object RuleEngine {

    fun evaluate(
        snapshot: NotificationSnapshot,
        settings: AlertSettings,
        now: Long,
        deduplicator: Deduplicator
    ): AlertDecision {
        if (!settings.enabled) {
            return skip("提醒总开关未打开", snapshot)
        }
        if (settings.monitoredPackages.none { it.equals(snapshot.packageName, ignoreCase = true) }) {
            return skip("应用不在监控列表: " + snapshot.packageName, snapshot)
        }
        if (settings.ignoreOngoing && snapshot.isOngoing) {
            return skip("常驻通知已忽略", snapshot)
        }
        if (settings.ignoreSuspended && snapshot.isSuspended) {
            return skip("系统已静音这条通知", snapshot)
        }

        val haystack = TextTools.normalize(snapshot.combinedText)
        if (haystack.isEmpty()) {
            return skip("通知没有任何可读文本", snapshot)
        }

        val excluded = settings.excludeKeywords.firstOrNull { word ->
            val normalized = TextTools.normalize(word)
            normalized.isNotEmpty() && haystack.contains(normalized)
        }
        if (excluded != null) {
            return skip("命中排除词: " + excluded, snapshot)
        }

        val keyword = settings.keywords.firstOrNull { word ->
            val normalized = TextTools.normalize(word)
            normalized.isNotEmpty() && haystack.contains(normalized)
        } ?: return skip("未命中任何关键词", snapshot)

        val nameCandidates = nameCandidates(snapshot, settings)

        val muted = GroupMatcher.firstMatch(nameCandidates, settings.muteList)
        if (muted != null) {
            return skip("会话在免打扰名单: " + muted, snapshot, keyword)
        }

        if (settings.whitelistEnabled) {
            val hit = GroupMatcher.firstMatch(nameCandidates, settings.whitelist)
            if (hit == null) {
                val unknownConversation = snapshot.groupCandidates.isEmpty()
                if (!(unknownConversation && settings.alertWhenGroupUnknown)) {
                    return skip("会话不在白名单内", snapshot, keyword)
                }
            }
        }

        if (deduplicator.tooSoon(now, settings.minAlertIntervalMs)) {
            return skip("低于最小提醒间隔", snapshot, keyword)
        }

        if (deduplicator.shouldSuppress(snapshot.dedupKey, now, settings.dedupWindowMs)) {
            return skip("重复推送已去重", snapshot, keyword)
        }

        deduplicator.markAlert(now)
        return AlertDecision(
            shouldAlert = true,
            reason = "命中关键词: " + keyword,
            matchedKeyword = keyword,
            conversation = snapshot.displayTitle,
            sender = snapshot.latestSender,
            text = snapshot.latestText
        )
    }

    private fun nameCandidates(snapshot: NotificationSnapshot, settings: AlertSettings): List<String> =
        if (settings.matchSenderName) snapshot.groupCandidates + snapshot.senderCandidates
        else snapshot.groupCandidates

    private fun skip(
        reason: String,
        snapshot: NotificationSnapshot,
        keyword: String? = null
    ): AlertDecision = AlertDecision(
        shouldAlert = false,
        reason = reason,
        matchedKeyword = keyword,
        conversation = snapshot.displayTitle,
        sender = snapshot.latestSender,
        text = snapshot.latestText
    )
}
