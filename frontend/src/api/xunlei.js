import http from '@/libs/http'

/**
 * 迅雷远程下载配置接口（对应后端 XunleiController）。
 * Authorization 认证头只写不读：查询只回传是否已设置，保存时留空表示沿用原值。
 */
export function GetXunleiSettings() {
    return http.get('/xunlei/settings')
}

export function SaveXunleiSettings(payload) {
    return http.post('/xunlei/settings', payload)
}

/** 测试连接：不传参数时用已保存的配置（认证头沿用） */
export function TestXunleiConnection(payload) {
    return http.post('/xunlei/test', payload || {}, { timeout: 30000 })
}
