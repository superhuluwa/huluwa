package com.huluwa.android.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerAddress {
    fun normalize(input: String): String {
        val url = input.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("请输入完整的服务器地址，例如 http://192.168.31.88:8000")
        require(url.username.isEmpty() && url.password.isEmpty()) { "服务器地址中不能包含账号或密码" }
        require(url.query == null && url.fragment == null) { "服务器地址不能包含查询参数或页面锚点" }
        var path = url.encodedPath.trimEnd('/')
        if (path.endsWith("/api")) path = path.removeSuffix("/api")
        return url.newBuilder().encodedPath(if (path.isBlank()) "/" else "$path/").build().toString()
    }
    fun endpoint(server: String, segments: List<String>, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = normalize(server).toHttpUrlOrNull()!!.newBuilder().addPathSegment("api")
        segments.forEach(builder::addPathSegment)
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }
    fun socket(server: String, token: String): HttpUrl = normalize(server).toHttpUrlOrNull()!!.newBuilder()
        .addPathSegment("ws").addPathSegment("chat").addQueryParameter("token", token).build()

    /** 星球大战对战通道：与聊天通道分开，避免 20Hz 世界快照挤占聊天连接。 */
    fun starwarSocket(server: String, token: String): HttpUrl = normalize(server).toHttpUrlOrNull()!!.newBuilder()
        .addPathSegment("ws").addPathSegment("starwar").addQueryParameter("token", token).build()
    /** 把服务端返回的相对路径（如 /api/public/...）拼到当前服务器地址（含可能的子路径）上。 */
    fun publicUrl(server: String, path: String): HttpUrl {
        val builder = normalize(server).toHttpUrlOrNull()!!.newBuilder()
        path.split('/').filter(String::isNotBlank).forEach(builder::addPathSegment)
        return builder.build()
    }
}
