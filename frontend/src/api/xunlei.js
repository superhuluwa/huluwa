import http from '@/libs/http'

/**
 * 迅雷远程下载配置接口（对应后端 XunleiController）。
 * 飞牛账号密码 / 手动认证值只写不读：查询只回传账号与是否已设置密码。
 */
export function GetXunleiSettings() {
    return http.get('/xunlei/settings')
}

export function SaveXunleiSettings(payload) {
    return http.post('/xunlei/settings', payload, { timeout: 90000 })
}

/** 测试连接：账号密码登录和迅雷 CGI 探测可能需要较长时间 */
export function TestXunleiConnection(payload) {
    return http.post('/xunlei/test', payload || {}, { timeout: 90000 })
}
