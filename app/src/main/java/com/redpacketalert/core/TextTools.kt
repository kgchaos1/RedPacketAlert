package com.redpacketalert.core

import java.util.Locale

/**
 * 文本归一化工具：微信通知里的文字经常夹带空格、全角空格、零宽字符，
 * 直接做 contains 会漏判，所以统一压缩空白并转小写后再比较。
 */
object TextTools {

    private val WHITESPACE = Regex("[\\s\\u3000\\u00A0\\u200B\\u200C\\u200D\\uFEFF]+")

    fun compact(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return WHITESPACE.replace(value, "")
    }

    fun normalize(value: String?): String = compact(value).lowercase(Locale.ROOT)
}
