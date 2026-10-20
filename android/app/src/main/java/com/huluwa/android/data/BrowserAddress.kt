package com.huluwa.android.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 隐藏浏览器地址栏输入：缺省补全 https，只接受 http(s) 网址。 */
object BrowserAddress {
    fun normalize(input: String): String {
        val raw = input.trim()
        require(raw.isNotEmpty()) { "请输入网址" }
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        return (candidate.toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入有效的网址，例如 example.com"))
            .toString()
    }
}
