import http from '@/libs/http'

/**
 * 视频解析服务接口（对应后端 VideoParseController）。
 * 密码只写不读：查询只回传是否已设置，保存时密码留空表示沿用原密码。
 */
export function GetVideoSettings() {
    return http.get('/video/settings')
}

export function SaveVideoSettings(payload) {
    return http.post('/video/settings', payload)
}

/** 测试连接：账号留空视为匿名调用；密码留空表示沿用已保存的密码 */
export function TestVideoConnection(payload) {
    return http.post('/video/test', payload || {}, { timeout: 30000 })
}

/** 试解析一条分享口令（解析耗时较长，超时放宽到 120s） */
export function TestVideoParse(content) {
    return http.post('/video/parse', { content }, { timeout: 120000 })
}
