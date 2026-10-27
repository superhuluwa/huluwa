package com.huluwa.android.data

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 轻量运行日志：同时写 Logcat 与内存环形缓冲。
 *
 * <p>App 面向家人使用，出问题时没法连电脑看 Logcat，所以这里保留最近若干条在内存里，
 * 界面上可以直接查看与复制。只在内存中，退出 App 即清空，不会写文件占空间。
 *
 * <p>用法：`AppLog.d("懂片帝", "载入线路：$line")`，tag 用短中文便于肉眼筛选。
 */
object AppLog {

    /** 保留的最近条数（够定位一次操作即可） */
    private const val CAPACITY = 300

    private const val LOGCAT_TAG = "Huluwa"

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)

    /** 环形缓冲：超出容量就丢掉最旧的 */
    private val entries = CopyOnWriteArrayList<Entry>()

    data class Entry(val at: Long, val level: String, val tag: String, val message: String) {
        fun render(): String = "${timeFormat.format(Date(at))} [$level] $tag: $message"
    }

    fun d(tag: String, message: String) = append("D", tag, message)

    fun w(tag: String, message: String, error: Throwable? = null) =
        append("W", tag, if (error == null) message else "$message | ${describe(error)}")

    fun e(tag: String, message: String, error: Throwable? = null) =
        append("E", tag, if (error == null) message else "$message | ${describe(error)}")

    /** 最近的全部日志（旧 → 新），用于界面展示与复制 */
    fun recent(): List<Entry> = entries.toList()

    fun clear() = entries.clear()

    /** 导出为可直接粘贴的文本 */
    fun dump(): String = if (entries.isEmpty()) "（暂无日志）"
    else entries.joinToString("\n") { it.render() }

    private fun append(level: String, tag: String, message: String) {
        // Logcat 单条上限约 4KB，超长内容截断，避免刷屏
        val text = if (message.length > 3500) message.take(3500) + "…(截断)" else message
        when (level) {
            "E" -> Log.e(LOGCAT_TAG, "[$tag] $text")
            "W" -> Log.w(LOGCAT_TAG, "[$tag] $text")
            else -> Log.d(LOGCAT_TAG, "[$tag] $text")
        }
        entries.add(Entry(System.currentTimeMillis(), level, tag, text))
        // 超容量时移除最旧的，保留最近的
        while (entries.size > CAPACITY) {
            entries.removeAt(0)
        }
    }

    /** 异常转一行描述：类型 + 消息，不打印完整堆栈（内存里放不下） */
    private fun describe(error: Throwable): String {
        val message = error.message
        return if (message.isNullOrBlank()) error.javaClass.simpleName
        else "${error.javaClass.simpleName}: $message"
    }
}
