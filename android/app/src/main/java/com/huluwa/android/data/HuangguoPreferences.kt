package com.huluwa.android.data

import android.content.Context

/** 黄果追剧进度：仅保存在本机，记录剧集、集号与当前毫秒位置。 */
class HuangguoPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("huangguo_playback", Context.MODE_PRIVATE)

    data class Progress(val id: String, val title: String, val cover: String, val ep: Int,
                        val positionMs: Long, val durationMs: Long) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    fun last(): Progress? {
        val id = preferences.getString("id", "").orEmpty()
        if (id.isBlank()) return null
        return Progress(
            id = id,
            title = preferences.getString("title", "").orEmpty(),
            cover = preferences.getString("cover", "").orEmpty(),
            ep = preferences.getInt("ep", 1).coerceAtLeast(1),
            positionMs = preferences.getLong("position", 0L).coerceAtLeast(0L),
            durationMs = preferences.getLong("duration", 0L).coerceAtLeast(0L),
        )
    }

    fun save(progress: Progress) {
        preferences.edit()
            .putString("id", progress.id)
            .putString("title", progress.title)
            .putString("cover", progress.cover)
            .putInt("ep", progress.ep)
            .putLong("position", progress.positionMs)
            .putLong("duration", progress.durationMs)
            .apply()
    }

    fun clear() = preferences.edit().clear().apply()
}
