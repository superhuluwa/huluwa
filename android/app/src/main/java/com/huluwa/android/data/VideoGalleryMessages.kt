package com.huluwa.android.data

import kotlinx.serialization.json.*

private data class LegacyGalleryImage(val count: Int, val image: ParsedImage)

private fun ChatMessage.legacyGalleryImage(): LegacyGalleryImage? {
    if (role != "bot" || type !in setOf("image", "video") || content.isBlank()) return null
    val count = payload.number("imageCount") ?: return null
    if (payload.text("kind") != "video" || count < 2 || count > Int.MAX_VALUE || count % 1.0 != 0.0
        || payload.text("video").isNotBlank() || payload.rows("images").isNotEmpty()) return null
    val image = if (type == "video") ParsedImage(payload.text("poster", "cover"), content) else ParsedImage(content)
    return image.takeIf { it.url.isNotBlank() }?.let { LegacyGalleryImage(count.toInt(), it) }
}

/** 旧后端逐张发送的图集在展示时合并，原始消息与缓存保持接口的分页结构。 */
fun mergeVideoImageMessages(messages: List<ChatMessage>): List<ChatMessage> {
    val result = mutableListOf<ChatMessage>()
    var index = 0
    while (index < messages.size) {
        val first = messages[index]
        val start = first.legacyGalleryImage()
        if (start == null) {
            result.add(first)
            index++
            continue
        }
        val images = mutableListOf(start.image)
        var nextIndex = index + 1
        while (nextIndex < messages.size && images.size < start.count) {
            val next = messages[nextIndex]
            val item = next.legacyGalleryImage() ?: break
            // 用户消息、下一条带标题的结果以及不同作品都必须保留各自的气泡。
            if (next.sessionId != first.sessionId || next.contentText.isNotBlank() || item.count != start.count
                || listOf("title", "author", "music").any { next.payload.text(it) != first.payload.text(it) }) break
            images.add(item.image)
            nextIndex++
        }
        result.add(if (images.size > 1) first.copy(type = "image", content = images.first().url,
            payload = JsonObject(first.payload + ("images" to buildJsonArray {
                images.forEach { image -> add(buildJsonObject {
                    put("url", image.url)
                    put("livePhotoUrl", image.livePhotoUrl)
                }) }
            }))) else first)
        index = nextIndex
    }
    return result
}
