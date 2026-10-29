package com.redpacketalert.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val now = 1_700_000_000_000L

    private fun snapshot(
        text: String = "[微信红包] 恭喜发财，大吉大利",
        title: String? = "微信",
        conversationTitle: String? = null,
        sender: String? = "张三"
    ) = NotificationSnapshot(
        packageName = "com.tencent.mm",
        notificationId = 1,
        tag = null,
        key = "0|com.tencent.mm|1|null|10086",
        postTime = now,
        title = title,
        text = text,
        conversationTitle = conversationTitle,
        messages = if (sender == null) emptyList() else listOf(MessageLine(sender, text, now)),
        isGroupConversation = true
    )

    private fun enabled() = AlertSettings(enabled = true)

    @Test
    fun alertsOnRedPacketInFoldedGroup() {
        val decision = RuleEngine.evaluate(snapshot(), enabled(), now, Deduplicator())
        assertTrue(decision.shouldAlert)
        assertEquals("红包", decision.matchedKeyword)
    }

    @Test
    fun skipsWhenMasterSwitchOff() {
        val decision = RuleEngine.evaluate(snapshot(), AlertSettings(enabled = false), now, Deduplicator())
        assertFalse(decision.shouldAlert)
    }

    @Test
    fun skipsOtherPackages() {
        val decision = RuleEngine.evaluate(
            snapshot().copy(packageName = "com.tencent.mobileqq"),
            enabled(),
            now,
            Deduplicator()
        )
        assertFalse(decision.shouldAlert)
    }

    @Test
    fun skipsWhenNoKeywordMatched() {
        val decision = RuleEngine.evaluate(
            snapshot(text = "李四: 晚上一起吃饭吗"),
            enabled(),
            now,
            Deduplicator()
        )
        assertFalse(decision.shouldAlert)
    }

    @Test
    fun toleratesWhitespaceInsideKeyword() {
        val decision = RuleEngine.evaluate(
            snapshot(text = "张三: [微信 红包] 快抢"),
            enabled(),
            now,
            Deduplicator()
        )
        assertTrue(decision.shouldAlert)
    }

    @Test
    fun excludeKeywordWinsOverKeyword() {
        val decision = RuleEngine.evaluate(
            snapshot(text = "张三: 送你一个红包封面"),
            enabled(),
            now,
            Deduplicator()
        )
        assertFalse(decision.shouldAlert)
        assertTrue(decision.reason.contains("排除词"))
    }

    @Test
    fun whitelistHitAlerts() {
        val settings = AlertSettings(
            enabled = true,
            whitelistEnabled = true,
            whitelist = listOf("大学同学群")
        )
        val decision = RuleEngine.evaluate(
            snapshot(conversationTitle = "大学同学群", text = "老王: [微信红包]"),
            settings,
            now,
            Deduplicator()
        )
        assertTrue(decision.shouldAlert)
        assertEquals("大学同学群", decision.conversation)
    }

    @Test
    fun whitelistMissSkips() {
        val settings = AlertSettings(
            enabled = true,
            whitelistEnabled = true,
            whitelist = listOf("大学同学群")
        )
        val decision = RuleEngine.evaluate(
            snapshot(conversationTitle = "工作群", text = "老王: [微信红包]"),
            settings,
            now,
            Deduplicator()
        )
        assertFalse(decision.shouldAlert)
    }

    @Test
    fun whitelistMatchesSenderNameWhenGroupNameHidden() {
        val settings = AlertSettings(
            enabled = true,
            whitelistEnabled = true,
            whitelist = listOf("张三"),
            matchSenderName = true
        )
        val decision = RuleEngine.evaluate(
            snapshot(title = "微信", text = "张三: [微信红包]", sender = "张三"),
            settings,
            now,
            Deduplicator()
        )
        assertTrue(decision.shouldAlert)
    }

    @Test
    fun unknownGroupStillAlertsWhenOptionEnabled() {
        val settings = AlertSettings(
            enabled = true,
            whitelistEnabled = true,
            whitelist = listOf("大学同学群"),
            alertWhenGroupUnknown = true
        )
        val decision = RuleEngine.evaluate(snapshot(), settings, now, Deduplicator())
        assertTrue(decision.shouldAlert)
    }

    @Test
    fun unknownGroupSkippedWhenOptionDisabled() {
        val settings = AlertSettings(
            enabled = true,
            whitelistEnabled = true,
            whitelist = listOf("大学同学群"),
            alertWhenGroupUnknown = false
        )
        val decision = RuleEngine.evaluate(
            snapshot(title = "微信", text = "[微信红包]", sender = null),
            settings,
            now,
            Deduplicator()
        )
        assertFalse(decision.shouldAlert)
    }

    @Test
    fun muteListWinsOverEverything() {
        val settings = AlertSettings(enabled = true, muteList = listOf("家族群"))
        val decision = RuleEngine.evaluate(
            snapshot(conversationTitle = "家族群"),
            settings,
            now,
            Deduplicator()
        )
        assertFalse(decision.shouldAlert)
        assertTrue(decision.reason.contains("免打扰"))
    }

    @Test
    fun repeatedNotificationIsDeduplicated() {
        val settings = enabled()
        val dedup = Deduplicator()
        val sbn = snapshot()
        assertTrue(RuleEngine.evaluate(sbn, settings, now, dedup).shouldAlert)
        // 1 秒后再来：被最小提醒间隔拦下
        assertFalse(RuleEngine.evaluate(sbn, settings, now + 1_000L, dedup).shouldAlert)
        // 5 秒后再来：与首次推送间隔不足去重窗口，仍被去重
        assertFalse(RuleEngine.evaluate(sbn, settings, now + 5_000L, dedup).shouldAlert)
        // 距最近一次相同内容 11 秒：超出 10 秒去重窗口，重新提醒
        assertTrue(RuleEngine.evaluate(sbn, settings, now + 16_000L, dedup).shouldAlert)
    }

    @Test
    fun minIntervalThrottlesMessageBurst() {
        val settings = AlertSettings(enabled = true, minAlertIntervalMs = 5_000L)
        val dedup = Deduplicator()
        assertTrue(
            RuleEngine.evaluate(snapshot(text = "张三: [微信红包] A"), settings, now, dedup).shouldAlert
        )
        val second = snapshot(text = "李四: [微信红包] B", sender = "李四")
        assertFalse(RuleEngine.evaluate(second, settings, now + 1_000L, dedup).shouldAlert)
        assertTrue(RuleEngine.evaluate(second, settings, now + 6_000L, dedup).shouldAlert)
    }

    @Test
    fun ongoingNotificationIsIgnored() {
        val settings = AlertSettings(enabled = true, ignoreOngoing = true)
        val decision = RuleEngine.evaluate(
            snapshot().copy(isOngoing = true),
            settings,
            now,
            Deduplicator()
        )
        assertFalse(decision.shouldAlert)
    }

    @Test
    fun groupMatcherAcceptsPartialName() {
        assertNotNull(GroupMatcher.firstMatch(listOf("大学同学群"), listOf("同学")))
        assertNotNull(GroupMatcher.firstMatch(listOf("大学同学群(3)"), listOf("大学同学群")))
        assertNull(GroupMatcher.firstMatch(listOf("工作群"), listOf("同学")))
    }

    @Test
    fun genericNamesAreNotTreatedAsGroups() {
        assertNull(NotificationSnapshot.cleanName("微信"))
        assertNull(NotificationSnapshot.cleanName("折叠的群聊"))
        assertNull(NotificationSnapshot.cleanName("折叠群消息"))
        assertEquals("大学同学群", NotificationSnapshot.cleanName("大学同学群(3)"))
        assertEquals("大学同学群", NotificationSnapshot.cleanName("大学同学群（12）"))
    }

    @Test
    fun senderIsParsedFromMessageText() {
        assertEquals("张三", NotificationSnapshot.senderFromText("张三: [微信红包]"))
        assertEquals("张三", NotificationSnapshot.senderFromText("张三：[微信红包]"))
        assertNull(NotificationSnapshot.senderFromText("[微信红包]"))
    }
}
