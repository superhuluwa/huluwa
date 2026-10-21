package com.huluwa.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 只保存服务器、用户名和加密令牌；不保存密码。密钥留在 Android Keystore。 */
class SessionStore(context: Context, namespace: String = "huluwa_connection", private val keyAlias: String = "huluwa_session_v1") {
    private val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    @Volatile var server: String = preferences.getString("server", "").orEmpty(); private set
    @Volatile var username: String = preferences.getString("username", "admin").orEmpty(); private set
    @Volatile var token: String = readToken(); private set
    val loggedIn get() = server.isNotBlank() && token.isNotBlank()

    @Synchronized fun save(server: String, username: String, token: String) {
        require(token.isNotBlank()) { "服务器没有返回有效登录令牌" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val normalized = ServerAddress.normalize(server)
        check(preferences.edit().putString("server", normalized).putString("username", username).putString("token", encoded).commit()) { "无法保存登录信息" }
        this.server = normalized; this.username = username; this.token = token
    }
    @Synchronized fun clear() { token = ""; preferences.edit().remove("token").apply() }
    private fun readToken(): String = runCatching {
        val pieces = preferences.getString("token", null)?.split(':') ?: return@runCatching ""
        if (pieces.size != 2) return@runCatching ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrElse { preferences.edit().remove("token").apply(); "" }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
