package com.huluwa.android.data

import android.content.Context

/** 按葫芦娃服务地址记住构建服务器，避免切换服务时串用节点。 */
class AndroidBuildPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("huluwa_android_build", Context.MODE_PRIVATE)

    fun selectedServer(server: String): String =
        preferences.getString("server:$server", "local").orEmpty().ifBlank { "local" }

    fun selectServer(server: String, id: String) {
        preferences.edit().putString("server:$server", id).apply()
    }
}
