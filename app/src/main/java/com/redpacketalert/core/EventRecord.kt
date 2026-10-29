package com.redpacketalert.core

/** 事件日志的一条记录，用于在应用里回看“为什么提醒/为什么没提醒”。 */
data class EventRecord(
    val time: Long,
    val packageName: String,
    val conversation: String?,
    val sender: String?,
    val keyword: String?,
    val text: String,
    val alerted: Boolean,
    val reason: String
)
