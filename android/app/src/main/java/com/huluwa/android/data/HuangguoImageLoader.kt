package com.huluwa.android.data

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 黄果图床把封面以 AES-128-CBC 加密后下发，浏览器端由 crypto-image 脚本解密再交给 img。
 * App 没有这层脚本，原先 Coil 直接把密文当 JPEG 解码，所以封面始终只剩占位底色。
 *
 * 这里只给黄果页面创建独立 ImageLoader：遇到黄果图床域名时先下载密文并原地解密，
 * 其他图片仍走 Coil 默认链路，不改变消息、竖屏和灯塔等模块的加载缓存行为。
 */
fun huangguoImageLoader(context: Context): ImageLoader {
    val client = OkHttpClient.Builder()
        .addInterceptor(HuangguoImageDecryptInterceptor())
        .build()
    return ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { -> client })) }
        .build()
}

private class HuangguoImageDecryptInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()
        if (host !in HuangguoImageHosts) return chain.proceed(request)
        val response = chain.proceed(request)
        val body = response.body ?: return response
        if (!response.isSuccessful) return response
        val cipherBytes = try {
            body.bytes()
        } catch (error: IOException) {
            response.close()
            throw error
        }
        // 图床有时会直接返回未加密的 WebP/PNG，校验明确文件头后原样交给 Coil。
        val plainType = plainImageType(cipherBytes)
        if (plainType.isNotEmpty()) {
            return response.newBuilder()
                .removeHeader("Content-Length")
                .removeHeader("Content-Encoding")
                .body(cipherBytes.toResponseBody(plainType.toMediaTypeOrNull()))
                .build()
        }
        val plain = try {
            decryptHuangguoImage(cipherBytes)
        } catch (error: Exception) {
            response.close()
            throw IOException("黄果封面解密失败", error)
        }
        val contentType = plainImageType(plain).ifBlank { "image/jpeg" }
        return response.newBuilder()
            .removeHeader("Content-Length")
            .removeHeader("Content-Encoding")
            .body(plain.toResponseBody(contentType.toMediaTypeOrNull()))
            .build()
    }

    private fun decryptHuangguoImage(cipherBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(HuangguoImageKey.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(HuangguoImageIV.toByteArray(Charsets.UTF_8)),
        )
        return cipher.doFinal(cipherBytes)
    }

    private fun plainImageType(bytes: ByteArray): String {
        fun ascii(start: Int, end: Int) = bytes.copyOfRange(start, end).toString(Charsets.US_ASCII)
        if (bytes.size < 12) return ""
        return when {
            bytes[0] == 0x89.toByte() && ascii(1, 8) == "PNG\r\n\u001a\n" -> "image/png"
            ascii(0, 4) == "RIFF" && ascii(8, 12) == "WEBP" -> "image/webp"
            ascii(0, 6) == "GIF87a" || ascii(0, 6) == "GIF89a" -> "image/gif"
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            else -> ""
        }
    }

    private companion object {
        // 与源站静态脚本 /static/web/js/plugins/crypto-image-protocol.js 中的公开凭据保持一致。
        const val HuangguoImageKey = "f5d965df75336270"
        const val HuangguoImageIV = "97b60394abc2fbe1"
        val HuangguoImageHosts = setOf("pic.qnlycc.cn", "new.qnlycc.cn", "image.qnlycc.cn", "pic.aluxvl.cn")
    }
}
