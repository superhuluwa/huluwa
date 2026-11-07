package com.huluwa.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.BrowserVideo
import com.huluwa.android.data.CastDevice
import com.huluwa.android.data.DlnaCastClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 解析视频与网页视频共用投屏会话；离开播放页面后仍能重新打开控制面板。 */
data class VideoCastState(
    val devices: List<CastDevice> = emptyList(), val scanning: Boolean = false, val working: Boolean = false,
    val selected: CastDevice? = null, val media: BrowserVideo? = null, val playing: Boolean = false, val error: String? = null,
)

class VideoCastViewModel(application: Application) : AndroidViewModel(application) {
    private val client = DlnaCastClient(application)
    private val mutable = MutableStateFlow(VideoCastState())
    val state = mutable.asStateFlow()
    private var discovery: Job? = null
    private var operation: Job? = null

    fun scan() {
        discovery?.cancel()
        mutable.update { it.copy(scanning = true, error = null) }
        discovery = viewModelScope.launch {
            try {
                val devices = client.discover()
                mutable.update { it.copy(devices = devices, scanning = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(scanning = false, error = "搜索失败，请连接与电视相同的 Wi-Fi，并打开电视的 DLNA 投屏功能") } }
        }
    }
    fun cancelScan() { discovery?.cancel(); discovery = null; mutable.update { it.copy(scanning = false) } }
    fun cast(device: CastDevice, media: BrowserVideo, onStarted: () -> Unit = {}) = act {
        val previous = mutable.value.selected
        client.cast(device, media)
        if (previous != null && previous.id != device.id) {
            try { client.stop(previous) } catch (error: CancellationException) { throw error } catch (_: Exception) { }
        }
        mutable.update { it.copy(selected = device, media = media, playing = true) }
        onStarted()
    }
    fun togglePlayback() = act {
        val current = mutable.value
        val device = current.selected ?: return@act
        if (current.playing) client.pause(device) else client.play(device)
        mutable.update { it.copy(playing = !current.playing) }
    }
    fun stop() = act {
        val device = mutable.value.selected ?: return@act
        client.stop(device)
        mutable.update { it.copy(selected = null, media = null, playing = false) }
    }
    private fun act(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        mutable.update { it.copy(working = true, error = null) }
        operation = viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = if (error is IllegalStateException || error is IllegalArgumentException) error.message else null
                mutable.update { it.copy(error = message?.takeIf { text -> !text.contains("://") }
                    ?: "投屏连接失败，请检查电视和网络后重试") }
            }
            finally { mutable.update { it.copy(working = false) } }
        }
    }
}
