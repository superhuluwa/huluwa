package com.huluwa.android.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.huluwa.android.R

/**
 * 星球大战背景音乐：循环播放原版 {@code backmusic.mid}。
 *
 * <p>原曲名 "Clipping"，12.4 秒、314 个音符、8 个 program change。
 * 因为 Android 的 MediaPlayer 不直接支持 MIDI（依赖设备音色库，效果不可控），
 * 这里用 MIDI 合成成 OGG 放在 {@code res/raw/starwar_backmusic.ogg}，
 * 谐波构成按原曲的 program 号（弦乐/铜管/贝斯）近似还原。</p>
 */
@Composable
fun StarwarMusic(enabled: Boolean, volume: Float = 0.35f) {
    val context = LocalContext.current
    val player = remember {
        runCatching {
            MediaPlayer.create(context, R.raw.starwar_backmusic)?.apply {
                isLooping = true
                setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f))
            }
        }.getOrNull()
    }
    // 音量变化实时生效
    DisposableEffect(volume) {
        runCatching { player?.setVolume(volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f)) }
        onDispose { }
    }
    DisposableEffect(enabled) {
        if (enabled) {
            runCatching { player?.start() }
        } else {
            runCatching { if (player?.isPlaying == true) player.pause() }
        }
        onDispose {
            runCatching {
                if (player?.isPlaying == true) player.pause()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
        }
    }
}

/**
 * 一次性音效（沿用同一合成思路，未单独导出时按需扩展）。
 * 目前原版只内嵌了一首 BGM，没有独立音效文件。
 */
@Composable
fun StarwarSfx(id: Int, trigger: Long) {
    val context: Context = LocalContext.current
    DisposableEffect(trigger) {
        if (trigger > 0) {
            runCatching {
                MediaPlayer.create(context, id)?.apply {
                    setVolume(0.6f, 0.6f)
                    setOnCompletionListener { it.release() }
                    start()
                }
            }
        }
        onDispose { }
    }
}
