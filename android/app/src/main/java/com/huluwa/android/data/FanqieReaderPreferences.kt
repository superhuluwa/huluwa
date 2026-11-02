package com.huluwa.android.data

import android.content.Context

/** 阅读字号在本机跨书籍保存，统一以 sp 表示。 */
class FanqieReaderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("fanqie_reader", Context.MODE_PRIVATE)
    fun fontSize(): Int = preferences.getInt("font_size", 18).coerceIn(14, 32)
    fun setFontSize(size: Int) { preferences.edit().putInt("font_size", size.coerceIn(14, 32)).apply() }
}
