import http from '@/libs/http'

/**
 * 消息中心会话接口（对应后端 ChatController）。
 * 发送接口同步等待站点搜索完成（约 1-2 秒），返回本轮新增的全部消息，前端直接追加渲染。
 */
export function GetSessions() {
    return http.get('/chat/sessions')
}

export function SessionMessages(sessionId, beforeId, limit = 50) {
    return http.get('/chat/sessions/' + encodeURIComponent(sessionId) + '/messages', {
        beforeId: beforeId || undefined,
        limit
    })
}

export function SendMessage(sessionId, content) {
    return http.post('/chat/sessions/' + encodeURIComponent(sessionId) + '/messages', { content })
}

export function MarkSessionRead(sessionId) {
    return http.post('/chat/sessions/' + encodeURIComponent(sessionId) + '/read')
}

export function ClearSession(sessionId) {
    return http.delete('/chat/sessions/' + encodeURIComponent(sessionId) + '/messages')
}

export function SearchMessages(keyword, limit = 20) {
    return http.get('/chat/search', { keyword, limit })
}
