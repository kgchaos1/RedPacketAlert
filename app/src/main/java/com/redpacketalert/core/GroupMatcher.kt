package com.redpacketalert.core

/**
 * 白名单 / 免打扰名单匹配。
 *
 * 微信的群名不一定出现在通知标题里（折叠群、消息聚合时尤其如此），
 * 所以我们对“所有可能的名字候选”逐个比较：
 *   - 完全相等，或
 *   - 候选名字里包含名单项（名单项至少 2 个字，避免“群”这种字误伤）
 */
object GroupMatcher {

    fun firstMatch(candidates: List<String>, entries: List<String>): String? {
        if (candidates.isEmpty() || entries.isEmpty()) return null
        for (candidate in candidates) {
            val c = TextTools.normalize(candidate)
            if (c.isEmpty()) continue
            for (entry in entries) {
                val e = TextTools.normalize(entry)
                if (e.isEmpty()) continue
                if (c == e) return entry
                if (e.length >= 2 && c.contains(e)) return entry
            }
        }
        return null
    }
}
