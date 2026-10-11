import http from '@/libs/http'

/**
 * qBittorrent 连接配置接口（对应后端 QBittorrentController）。
 * 密码只写不读：查询只回传是否已设置，保存时密码留空表示沿用原密码。
 */
export function GetQbSettings() {
    return http.get('/qbittorrent/settings')
}

export function SaveQbSettings(payload) {
    return http.post('/qbittorrent/settings', payload)
}

/** 测试连接：不传参数时用已保存的配置（密码沿用） */
export function TestQbConnection(payload) {
    return http.post('/qbittorrent/test', payload || {}, { timeout: 30000 })
}

/** 解析文本里的磁力链接（仅做格式识别，不访问下载器） */
export function ParseMagnets(content) {
    return http.post('/qbittorrent/parse', { content })
}
