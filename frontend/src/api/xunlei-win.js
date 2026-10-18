import http from '@/libs/http'

/**
 * 迅雷下载【Win】配置接口（对应后端 XunleiWinController）。
 * 只保存 xunlei-win-api 服务地址与 Windows 保存根目录，无敏感凭据。
 */
export function GetXunleiWinSettings() {
    return http.get('/xunlei-win/settings')
}

export function SaveXunleiWinSettings(payload) {
    return http.post('/xunlei-win/settings', payload, { timeout: 30000 })
}

export function TestXunleiWinConnection(payload) {
    return http.post('/xunlei-win/test', payload || {}, { timeout: 30000 })
}

/** 迅雷【Win】下载记录；自动轮询时静默处理网络错误，由列表显示重试提示。 */
export function GetXunleiWinDownloads(quiet = false) {
    return http.get('/xunlei-win/downloads', {}, { silent: quiet })
}
