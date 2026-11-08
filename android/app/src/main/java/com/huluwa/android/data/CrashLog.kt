package com.huluwa.android.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 保存最近一次未捕获异常，供闪退后重新打开应用查看。 */
object CrashLog {
    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_CHARS = 96_000
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX")
        .withZone(ZoneId.systemDefault())

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** 由全局异常处理器同步调用；写入失败由调用方隔离，继续系统崩溃流程。 */
    @Synchronized
    fun record(context: Context, thread: Thread, error: Throwable) {
        val text = buildString {
            appendLine("时间：${timeFormat.format(Instant.now())}")
            appendLine(deviceInfo(context))
            appendLine("进程：${context.packageName} · 线程：${thread.name}")
            appendLine()
            appendLine(error.stackTraceToString())
            appendLine()
            appendLine("===== 崩溃前运行日志 =====")
            append(AppLog.recent().takeLast(30).joinToString("\n") { it.render() })
        }
        // 同时保留顶部堆栈与尾部原因，避免深层异常被截掉。
        val saved = if (text.length <= MAX_CHARS) text else
            text.take(MAX_CHARS / 2) + "\n…（中间内容已截断）…\n" + text.takeLast(MAX_CHARS / 2)
        file(context).writeText(saved)
    }

    /** 文件和系统退出记录均在界面层的 IO 线程读取。 */
    fun report(context: Context): String = buildString {
        appendLine(deviceInfo(context))
        appendLine()
        appendLine("===== 上次崩溃 =====")
        appendLine(runCatching {
            val target = file(context)
            if (target.isFile) target.readText() else "（没有崩溃记录）"
        }.getOrElse { "读取失败：${it.userMessage()}" })
        appendLine()
        appendLine("===== 系统最近退出记录 =====")
        append(systemExit(context))
    }

    @Suppress("DEPRECATION")
    private fun deviceInfo(context: Context): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val code = if (Build.VERSION.SDK_INT >= 28) info?.longVersionCode else info?.versionCode?.toLong()
        return "版本：${info?.versionName ?: "未知"}（${code ?: "未知"}）\n" +
            "设备：${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"
    }

    private fun systemExit(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "（Android 11 及以上支持）"
        return runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            manager?.getHistoricalProcessExitReasons(context.packageName, 0, 3)
                .orEmpty().joinToString("\n\n") { exit ->
                    val reason = when (exit.reason) {
                        ApplicationExitInfo.REASON_CRASH -> "未捕获异常"
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> "原生代码崩溃"
                        ApplicationExitInfo.REASON_ANR -> "应用无响应"
                        ApplicationExitInfo.REASON_LOW_MEMORY -> "系统内存不足"
                        ApplicationExitInfo.REASON_SIGNALED -> "进程被信号终止"
                        ApplicationExitInfo.REASON_USER_REQUESTED -> "用户结束应用"
                        else -> "其他原因（${exit.reason}）"
                    }
                    "时间：${timeFormat.format(Instant.ofEpochMilli(exit.timestamp))}\n" +
                        "进程：${exit.processName} · 原因：$reason · 状态：${exit.status}\n" +
                        "说明：${exit.description.orEmpty()}"
                }.ifBlank { "（没有系统退出记录）" }
        }.getOrElse { "读取失败：${it.userMessage()}" }
    }
}
