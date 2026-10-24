package com.huluwa.android.data

import android.content.Context

/** 按葫芦娃服务地址记住构建服务器，避免切换服务时串用节点。 */
class AndroidBuildPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("huluwa_android_build", Context.MODE_PRIVATE)

    private fun key(server: String, project: AndroidBuildProject) = if (project == AndroidBuildProject.Huluwa) "server:$server" else "server:$server:${project.id}"

    fun selectedServer(server: String, project: AndroidBuildProject = AndroidBuildProject.Huluwa): String =
        preferences.getString(key(server, project), "local").orEmpty().ifBlank { "local" }

    fun selectServer(server: String, id: String, project: AndroidBuildProject = AndroidBuildProject.Huluwa) {
        preferences.edit().putString(key(server, project), id).apply()
    }
}
