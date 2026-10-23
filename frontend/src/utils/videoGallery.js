function payloadOf(message) {
    try {
        const payload = typeof message.payload === 'string' ? JSON.parse(message.payload) : message.payload
        return payload && typeof payload === 'object' && !Array.isArray(payload) ? payload : {}
    } catch {
        return {}
    }
}

function legacyImage(message) {
    if (message.role !== 'bot' || !['image', 'video'].includes(message.type) || !message.content) return null
    const payload = payloadOf(message)
    const count = Number(payload.imageCount)
    if (payload.kind !== 'video' || !Number.isSafeInteger(count) || count < 2 || payload.video
        || (Array.isArray(payload.images) && payload.images.length)) return null
    const image = message.type === 'video'
        ? { url: payload.poster || payload.cover, livePhotoUrl: message.content }
        : { url: message.content, livePhotoUrl: '' }
    return image.url ? { payload, count, image } : null
}

/** 兼容旧后端逐张发送的图集，只在展示时合并，保留原始消息用于分页和实时同步。 */
export function mergeVideoImageMessages(messages) {
    const result = []
    for (let index = 0; index < messages.length;) {
        const first = messages[index]
        const start = legacyImage(first)
        if (!start) {
            result.push(first)
            index++
            continue
        }
        const images = [start.image]
        let nextIndex = index + 1
        while (nextIndex < messages.length && images.length < start.count) {
            const next = messages[nextIndex]
            const item = legacyImage(next)
            // 用户消息、下一条带标题的结果以及不同作品都必须保留各自的气泡。
            if (!item || next.sessionId !== first.sessionId || String(next.contentText || '').trim()
                || item.count !== start.count
                || ['title', 'author', 'music'].some(key => item.payload[key] !== start.payload[key])) break
            images.push(item.image)
            nextIndex++
        }
        result.push(images.length > 1 ? {
            ...first,
            type: 'image',
            content: images[0].url,
            payload: JSON.stringify({ ...start.payload, images })
        } : first)
        index = nextIndex
    }
    return result
}
