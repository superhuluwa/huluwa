package com.huluwa.android.data

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** 下载解析服务返回的原始图片文件，直接写入相册，不使用预览缓存或重新压缩。 */
class ImageGallerySaver(private val context: Context, private val http: OkHttpClient) {
    suspend fun save(url: String) = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) { "图片地址无效，请重新解析" }
        val temporary = File.createTempFile("original-image-", null, context.cacheDir)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "图片下载失败（${response.code}），请重新解析后重试" }
                val body = checkNotNull(response.body) { "图片内容为空，请重新解析后重试" }
                body.byteStream().use { input -> temporary.outputStream().use { output -> copyImage(input, output) } }
            }
            currentCoroutineContext().ensureActive()
            // 只读取格式信息，不解码像素；格式取自实际图片，兼容直链返回 application/octet-stream。
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            val mime = checkNotNull(bounds.outMimeType?.takeIf { it.startsWith("image/") }) {
                "下载内容不是有效图片，请重新解析后重试"
            }
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "img"
            val name = "huluwa_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(temporary, name, mime)
            else saveToLegacyGallery(temporary, name, mime)
        } finally {
            temporary.delete()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveToMediaStore(source: File, name: String, mime: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/葫芦娃")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "无法创建相册图片，请检查手机存储空间"
        }
        try {
            val output = checkNotNull(resolver.openOutputStream(uri)) { "无法写入相册，请稍后重试" }
            output.use { target -> source.inputStream().use { input -> copyImage(input, target) } }
            currentCoroutineContext().ensureActive()
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0) {
                "图片未能保存到相册，请重试"
            }
        } catch (error: Throwable) {
            // 失败或取消时清理尚未发布的图片，避免相册出现残缺文件。
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveToLegacyGallery(source: File, name: String, mime: String) {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "葫芦娃")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建相册目录，请检查存储权限和手机存储空间" }
        val target = File(directory, name)
        try {
            source.inputStream().use { input -> target.outputStream().use { output -> copyImage(input, output) } }
            currentCoroutineContext().ensureActive()
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private suspend fun copyImage(input: InputStream, output: OutputStream) {
        val coroutine = currentCoroutineContext()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            coroutine.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }
}
