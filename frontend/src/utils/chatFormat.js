/**
 * 会话展示格式化（自 quantum-web 的 utils/chatFormat.js 迁移）。
 * 服务端时间串为 yyyy-MM-dd HH:mm:ss，解析失败时一律不显示。
 * 与量子端的差异：不引入 dayjs，用原生 Date 解析同一格式。
 */
import { parseRich } from '@/utils/richText'

/** 时间分隔插入阈值（分钟） */
export const TIME_LABEL_GAP_MINUTES = 5

const SERVER_TIME = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/

/** 服务端时间串 → Date；解析失败返回 null */
export function parseServerTime(value) {
    if (!value) {
        return null
    }
    const match = SERVER_TIME.exec(String(value))
    if (!match) {
        const fallback = new Date(value)
        return Number.isNaN(fallback.getTime()) ? null : fallback
    }
    return new Date(
        Number(match[1]), Number(match[2]) - 1, Number(match[3]),
        Number(match[4]), Number(match[5]), Number(match[6] || 0)
    )
}

const pad = (n) => String(n).padStart(2, '0')

/**
 * 时间分隔文案：当天 HH:mm / 昨天 HH:mm / 同年 MM-DD HH:mm / 跨年 YYYY-MM-DD HH:mm
 * @returns {string} 解析失败返回空串（调用方不渲染分隔行）
 */
export function formatTimeLabel(value, now = new Date()) {
    const time = parseServerTime(value)
    if (!time) {
        return ''
    }
    const hhmm = pad(time.getHours()) + ':' + pad(time.getMinutes())
    if (sameDay(time, now)) {
        return hhmm
    }
    const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
    if (sameDay(time, yesterday)) {
        return '昨天 ' + hhmm
    }
    if (time.getFullYear() === now.getFullYear()) {
        return pad(time.getMonth() + 1) + '-' + pad(time.getDate()) + ' ' + hhmm
    }
    return time.getFullYear() + '-' + pad(time.getMonth() + 1) + '-' + pad(time.getDate()) + ' ' + hhmm
}

function sameDay(a, b) {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

/** 悬浮提示用的完整时间 */
export function formatFullTime(value) {
    const time = parseServerTime(value)
    if (!time) {
        return ''
    }
    return time.getFullYear() + '-' + pad(time.getMonth() + 1) + '-' + pad(time.getDate()) + ' '
        + pad(time.getHours()) + ':' + pad(time.getMinutes()) + ':' + pad(time.getSeconds())
}

/** 相邻消息是否该插时间分隔：首条必插；间隔 > 5 分钟插；无法解析时间则不插 */
export function shouldInsertTimeSeparator(prevTime, currentTime) {
    const current = parseServerTime(currentTime)
    if (!current) {
        return false
    }
    const prev = parseServerTime(prevTime)
    if (!prev) {
        return true
    }
    return current.getTime() - prev.getTime() > TIME_LABEL_GAP_MINUTES * 60 * 1000
}

/** 富文本标记剥离为纯文本（保留标记内的文字，只去掉语法，列表预览/搜索摘要用） */
export function stripMarkup(raw) {
    return parseRich(raw)
        .map((span) => span.text)
        .join('')
        .trim()
}

/**
 * 会话列表预览文案：
 * image→[图片]、error→错误提示，其余取正文首行并剥离富文本标记。
 * 系统通知（notify）正文优先展示标题（服务端把标题放在 contentText）。
 */
export function sessionPreview(last) {
    if (!last) {
        return ''
    }
    const type = last.type || 'text'
    if (type === 'image') {
        return '[图片]'
    }
    if (type === 'video') {
        return '[视频]'
    }
    if (type === 'audio') {
        return '[音频]'
    }
    if (type === 'file') {
        return '[文件]'
    }
    if (type === 'notify' && last.contentText) {
        return String(last.contentText).trim()
    }
    const firstLine = (last.content || '').split('\n')[0] || ''
    return stripMarkup(firstLine)
}
