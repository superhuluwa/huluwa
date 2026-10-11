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

// ------------------------------------------------------------------ 电影港订阅

/** 已订阅的详情页地址集合（搜索结果卡片据此渲染「已订阅」） */
export function GetSubscriptionUrls() {
    return http.get('/chat/subscriptions/urls')
}

/** 订阅清单（含已取消，可选） */
export function GetSubscriptions(includeDisabled = false) {
    return http.get('/chat/subscriptions', { includeDisabled })
}

/** 订阅（结果卡片按钮）：字段与影视卡片载荷同形 */
export function SubscribeMovie(payload) {
    return http.post('/chat/subscriptions', payload)
}

/** 取消订阅（保留磁力基线与下载记录） */
export function UnsubscribeMovie(id) {
    return http.post('/chat/subscriptions/' + encodeURIComponent(id) + '/unsubscribe')
}

/** 立即巡检一次，返回本轮新增磁力条数 */
export function CheckSubscription(id) {
    return http.post('/chat/subscriptions/' + encodeURIComponent(id) + '/check')
}

/** 删除订阅及其磁力 / 下载记录 */
export function DeleteSubscription(id) {
    return http.delete('/chat/subscriptions/' + encodeURIComponent(id))
}

/** 某订阅已采集的磁力 */
export function SubscriptionMagnets(id) {
    return http.get('/chat/subscriptions/' + encodeURIComponent(id) + '/magnets')
}

/** 订阅触发的下载任务（id=0 表示全部） */
export function SubscriptionDownloads(id = 0) {
    return http.get('/chat/subscriptions/downloads', { id })
}
