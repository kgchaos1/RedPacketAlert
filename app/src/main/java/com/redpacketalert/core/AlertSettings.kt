package com.redpacketalert.core

/** 全部可配置项。默认值就是“开箱可用”的一套强提醒配置。 */
data class AlertSettings(
    val enabled: Boolean = false,
    val monitoredPackages: List<String> = DEFAULT_PACKAGES,
    val keywords: List<String> = DEFAULT_KEYWORDS,
    val excludeKeywords: List<String> = DEFAULT_EXCLUDES,
    val whitelistEnabled: Boolean = false,
    val whitelist: List<String> = emptyList(),
    val muteList: List<String> = emptyList(),
    val matchSenderName: Boolean = true,
    val alertWhenGroupUnknown: Boolean = true,
    val ignoreOngoing: Boolean = true,
    val ignoreSuspended: Boolean = false,
    val dedupWindowMs: Long = 10_000L,
    val minAlertIntervalMs: Long = 2_000L,
    val soundEnabled: Boolean = true,
    val soundUri: String? = null,
    val useAlarmStream: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val vibrateContinuous: Boolean = true,
    val torchEnabled: Boolean = false,
    val overlayEnabled: Boolean = true,
    val screenOnEnabled: Boolean = true,
    val alertDurationSeconds: Int = 15
) {
    companion object {
        val DEFAULT_PACKAGES = listOf("com.tencent.mm")
        val DEFAULT_KEYWORDS = listOf("红包", "紅包", "[微信红包]", "微信红包")
        val DEFAULT_EXCLUDES = listOf("红包封面")
    }
}
