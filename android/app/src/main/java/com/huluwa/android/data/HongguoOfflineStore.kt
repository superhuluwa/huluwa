package com.huluwa.android.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/** 单集离线档案：只记录文件位置与清晰度，不保存播放地址、spade 或内容密钥。 */
@Serializable
data class HongguoOfflineEpisode(
    val episodeId: String, val number: Int, val definition: String, val size: Long,
)

/** 一部剧的离线档案；total 是发起下载时可见的未锁定集数，用于恢复展示与增量补下。 */
@Serializable
data class HongguoOfflineSeries(
    val title: String, val cover: String = "", val total: Int = 0,
    val episodes: List<HongguoOfflineEpisode> = emptyList(),
)

/** 离线库全局共享（不按账号隔离）：文件保存在应用私有目录，索引只记录剧集到文件的映射。 */
class HongguoOfflineStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("hongguo-offline", Context.MODE_PRIVATE)
    val root = File(context.applicationContext.getExternalFilesDir(null)
        ?: context.applicationContext.filesDir, "hongguo-offline")

    fun episodeFile(seriesId: String, number: Int, episodeId: String): File =
        File(File(root, seriesId), "%03d_%s.mp4".format(number, episodeId))

    fun partFile(target: File) = File(target.parentFile, target.name + ".part")

    /** 读取全部档案；文件已丢失的分集记录被过滤，避免出现播放失败的幽灵离线。 */
    @Synchronized fun readAll(): Map<String, HongguoOfflineSeries> {
        val saved = runCatching {
            ApiJson.decodeFromString<Map<String, HongguoOfflineSeries>>(preferences.getString("library", "{}").orEmpty())
        }.getOrDefault(emptyMap())
        return saved.mapValues { (id, series) ->
            series.copy(episodes = series.episodes.filter { episodeFile(id, it.number, it.episodeId).exists() })
        }
    }

    @Synchronized fun write(seriesId: String, series: HongguoOfflineSeries) {
        preferences.edit()
            .putString("library", ApiJson.encodeToString(readAll() + (seriesId to series)))
            .commit()
    }

    /** 删除一部剧的离线档案与全部文件（含未完成的临时文件）。 */
    @Synchronized fun remove(seriesId: String) {
        val dir = File(root, seriesId)
        dir.listFiles()?.forEach(File::delete)
        dir.delete()
        preferences.edit().putString("library", ApiJson.encodeToString(readAll() - seriesId)).commit()
    }
}
