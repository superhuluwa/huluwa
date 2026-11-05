package com.huluwa.android.hongguo

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import com.huluwa.android.data.HongguoPlayback
import com.huluwa.android.data.HongguoStream

/**
 * 一个播放页复用一个播放器。相邻集由 Media3 预加载媒体源，保留解密索引和音视频样本队列，
 * 切集直接将同一媒体源交给播放器，不重复下载，也不为后台分集占用解码器。
 */
@UnstableApi
internal class HongguoPlaybackSession(context: Context) {
    private data class Source(
        val preload: HongguoEpisodePreload,
        val page: Int,
        val item: MediaItem,
    )

    private val sources = mutableMapOf<String, Source>()
    private var currentEpisodeId: String? = null
    private var currentRequest: Long? = null
    private var canPreload = false
    private var currentRanking = 0
    private val builder = DefaultPreloadManager.Builder(context) { ranking ->
        sources.values.firstOrNull { it.page * 2 == ranking }?.let { source ->
            DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(
                source.preload.startPositionMs,
                if (source.page * 2 >= currentRanking) 5_000L else 3_000L,
            )
        }
    }.setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setLoadControl(DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 25_000, 250, 750)
            .setTargetBufferBytes(16 * 1024 * 1024)
            .build())
    private val preloadManager = builder.build()
    val player = builder.buildExoPlayer().apply {
        setAudioAttributes(AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_READY) canPreload = false
                preloadWhenBuffered()
            }
        })
    }

    fun isActive(request: Long): Boolean = currentRequest == request

    /** 当前集先积累三秒缓冲，避免相邻集预加载与当前集起播争抢带宽。 */
    fun preloadWhenBuffered() {
        if (!canPreload && currentRequest != null && player.playbackState == Player.STATE_READY &&
            (player.bufferedPosition - player.currentPosition >= 3_000L || player.bufferedPercentage == 100)) {
            canPreload = true
            preloadManager.invalidate()
        }
    }

    fun update(
        episodeId: String,
        page: Int,
        request: Long,
        playback: HongguoPlayback,
        stream: HongguoStream,
        startPositionMs: Long,
        neighbors: List<Pair<Int, HongguoEpisodePreload>>,
        playWhenReady: Boolean,
    ) {
        val switching = currentRequest != request
        if (switching) {
            canPreload = false
            // 重试或切换清晰度时清掉当前媒体源；相邻集切换则完整复用预加载队列。
            if (currentEpisodeId == episodeId) remove(episodeId)
            currentRanking = page * 2 + 1 // 排序位置向下一页偏移，优先准备下一集。
            preloadManager.setCurrentPlayingIndex(currentRanking)
            val source = source(page, HongguoEpisodePreload(episodeId, playback, stream, startPositionMs))
            currentEpisodeId = episodeId
            currentRequest = request
            player.setMediaSource(checkNotNull(preloadManager.getMediaSource(source.item)), startPositionMs)
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        neighbors.forEach { (neighborPage, preload) -> source(neighborPage, preload) }
        val retained = neighbors.mapTo(mutableSetOf(episodeId)) { it.second.episodeId }
        sources.keys.filter { it !in retained }.forEach(::remove)
        // 切换源尚未被播放器接管时不 invalidate，避免清掉即将复用的预加载队列。
        if (canPreload) preloadManager.invalidate()
    }

    private fun source(page: Int, preload: HongguoEpisodePreload): Source {
        val previous = sources[preload.episodeId]
        if (previous != null && previous.preload.stream == preload.stream &&
            previous.preload.playback.userAgent == preload.playback.userAgent) {
            // 回看位置变化时仍复用媒体源及其数据源工厂，避免重新解析解密索引。
            return previous.copy(preload = preload).also { sources[preload.episodeId] = it }
        }
        remove(preload.episodeId)
        val item = MediaItem.Builder().setMediaId(preload.episodeId)
            .setUri(preload.stream.url).setMimeType(MimeTypes.VIDEO_MP4).build()
        val factory = HongguoDataSourceFactory(preload.stream, preload.playback.userAgent)
        val mediaSource = DefaultMediaSourceFactory(factory).createMediaSource(item)
        return Source(preload, page, item).also {
            sources[preload.episodeId] = it
            preloadManager.add(mediaSource, page * 2)
        }
    }

    private fun remove(episodeId: String) {
        sources.remove(episodeId)?.let { preloadManager.remove(it.item) }
    }

    fun release() {
        currentRequest = null
        canPreload = false
        player.release()
        preloadManager.release()
        sources.clear()
    }
}
