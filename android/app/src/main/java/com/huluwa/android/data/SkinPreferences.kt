package com.huluwa.android.data

import android.content.Context

/** 皮肤属于本机外观偏好，独立于服务器、账号及登录状态。 */
class SkinPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("huluwa_appearance", Context.MODE_PRIVATE)

    fun selectedSkin(): String? = preferences.getString("skin", null)

    fun selectSkin(id: String) {
        preferences.edit().putString("skin", id).apply()
    }
}
