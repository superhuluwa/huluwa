import http from '@/libs/http'

/**
 * 消息中心会话接口（对应后端 ChatController）。
 * 发送接口只做毫秒级受理（返回落库的用户消息行，requestId 幂等），
 * 搜索 / 提交 / 解析在后台异步执行，机器人回复经 WebSocket 推送（离线时靠轮询）。
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

/** WebSocket 断开时的 HTTP 兜底发送；requestId 与 WS 同源幂等（弱网重试不重复落库） */
export function SendMessage(sessionId, content, requestId) {
    return http.post('/chat/sessions/' + encodeURIComponent(sessionId) + '/messages', { content, requestId })
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

// ------------------------------------------------------------------ 电影港搜索监听

/** 关键字监听状态（含已找到与已取消），用于恢复回复中的按钮。 */
export function GetMovieSearchWatches() {
    return http.get('/chat/movie-search-watches', undefined, { silent: true })
}

export function StartMovieSearchWatch(keyword) {
    return http.post('/chat/movie-search-watches', { keyword }, { silent: true })
}

export function CancelMovieSearchWatch(id) {
    return http.delete('/chat/movie-search-watches/' + id, { silent: true })
}

// ------------------------------------------------------------------ 我的订阅 / 我的下载

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

/** 详情页磁力清单（搜索结果卡片「查看磁力」）：实时抓取，不写基线库 */
export function PreviewMagnets(url, title) {
    return http.get('/chat/subscriptions/magnets/preview', { url, title })
}

/** 手动下载一条磁力：magnet 直传原文，或用 magnetId 指定订阅库记录 */
export function DownloadMagnet(payload) {
    return http.post('/chat/subscriptions/magnets/download', payload, { timeout: 60000 })
}

/** 清空磁力下载记录：只隐藏已结束的历史行，进行中的任务保留 */
export function ClearSubscriptionDownloads() {
    return http.delete('/chat/subscriptions/downloads')
}

/** 电影港默认下载工具与固定保存根目录。 */
export function GetMovieDownloadSettings() {
    return http.get('/chat/subscriptions/settings')
}

export function SaveMovieDownloadSettings(downloader) {
    return http.post('/chat/subscriptions/settings', { downloader })
}

export function GetMovieSubscriptionStatus(url) {
    return http.get('/chat/subscriptions/status', { url })
}

export function UpdateMovieSubscriptionSettings(id, downloader) {
    return http.post('/chat/subscriptions/' + encodeURIComponent(id) + '/settings', { downloader })
}
