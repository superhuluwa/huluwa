/**
 * 配置引导判定：会话里自动弹「qBittorrent / 视频解析 / 迅雷连接设置」的公共条件。
 *
 * 只有同时满足下面三条才会引导用户去填配置：
 *  1. 引导已武装 —— 会话进入 / 刷新期间的历史回放不弹（那些消息早就存在，用户多半早已配置好）；
 *  2. 消息是基线之后新到达的 —— id 大于进入会话时最后一条机器人消息，历史卡片只展示不打扰；
 *  3. 载荷声明服务端确实还没有配置（settings.configured 为假）——
 *     「下载失败」之类的通知也可能带 status=config，但连接配置是好的，不该让用户重填。
 *
 * @param {object|null} message 机器人消息行（含 payload JSON 字符串）
 * @param {{ baseline?: number, armed?: boolean }} options 当前会话的引导基线 / 是否武装
 * @returns {string|null} 需要弹出的配置表单种类（magnet / video / xunlei），不需要时返回 null
 */
export function configPromptKind(message, { baseline = 0, armed = false } = {}) {
    if (!armed || !message || !message.payload) {
        return null
    }
    const id = typeof message.id === 'number' ? message.id : 0
    if (id <= baseline) {
        return null
    }
    let payload
    try {
        payload = JSON.parse(message.payload)
    } catch (e) {
        // 损坏的载荷按无引导处理
        return null
    }
    if (!payload || payload.status !== 'config') {
        return null
    }
    if (payload.settings && payload.settings.configured) {
        return null
    }
    return payload.kind || null
}
