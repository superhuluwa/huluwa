/**
 * 聊天发送 outbox（localStorage 持久化）：
 * 用户消息发出但尚未收到服务端 ack 时留在箱内；弱网重连或页面刷新后按同一 requestId 重放，
 * 服务端（chat_request 表）保证幂等——同一 requestId 只落一条用户消息。
 * ack 到达（chat.ack 帧）即 remove；prune 清理超过 24h 的陈年条目（服务端幂等本就兜底，仅卫生）。
 */
const KEY = 'huluwa.chat.outbox'
const MAX_AGE_MS = 24 * 60 * 60 * 1000

function readAll() {
    try {
        const raw = localStorage.getItem(KEY)
        if (!raw) {
            return []
        }
        const list = JSON.parse(raw)
        return Array.isArray(list) ? list : []
    } catch (e) {
        return []
    }
}

function writeAll(list) {
    try {
        localStorage.setItem(KEY, JSON.stringify(list))
    } catch (e) {
        // 存储写满等异常不阻塞发送流程
    }
}

export default {
    all() {
        return readAll()
    },

    add(entry) {
        const list = readAll().filter((item) => item.requestId !== entry.requestId)
        list.push({ requestId: entry.requestId, sessionId: entry.sessionId, content: entry.content, createdAt: entry.createdAt || Date.now() })
        writeAll(list)
    },

    remove(requestId) {
        writeAll(readAll().filter((item) => item.requestId !== requestId))
    },

    bySession(sessionId) {
        return readAll().filter((item) => item.sessionId === sessionId)
    },

    /** 清理陈年条目：重连重放前调用，避免箱子无限膨胀 */
    prune(maxAgeMs = MAX_AGE_MS) {
        const deadline = Date.now() - maxAgeMs
        writeAll(readAll().filter((item) => (item.createdAt || 0) > deadline))
    }
}
