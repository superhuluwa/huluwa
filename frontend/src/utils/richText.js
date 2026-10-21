/**
 * 消息富文本标记解析（自 quantum-web 的 utils/richText.js 迁移，规则逐条对齐）：
 *
 * 语法：{{颜色|文字}} 彩色粗体文字、{{tag:颜色|文字}} 胶囊标签、
 * {{link:文字|URL}} 命名链接（只显示文字，点击跳 URL，URL 非 http(s) 开头按字面）。
 * 颜色固定六色小写枚举。
 * 规则：
 *  - 未知颜色 / 残缺未闭合 / 跨行 / 空文本 一律按原文字面输出（脚本笔误直接可见，不静默吞掉）；
 *  - 文本段禁花括号与换行 → 天然拒绝嵌套（外层非法时内层合法标记仍会被识别）；
 *  - 单条消息标记上限 MAX_MARKS（彩色/胶囊/链接合计），超出部分按字面渲染，防超长消息渲染开销失控。
 */
export const MAX_MARKS = 50

/**
 * 颜色枚举 → 实际色值：与 styles/tokens.css 的浅色松柏绿色板对齐
 * （红 / 绿 / 橙 / 蓝 / 紫 / 灰在雾白底上均保持足够对比度）。
 */
export const RICH_COLORS = {
    red: '#B44452',
    green: '#206C54',
    orange: '#956223',
    blue: '#4E78A7',
    purple: '#8260A1',
    gray: '#63746A'
}

export const SPAN_PLAIN = 'plain'
export const SPAN_STYLED = 'styled'
export const SPAN_LINK = 'link'

const TOKEN_REGEX = /\{\{(tag:)?([a-z]+)\|([^{}\n\r]+)\}\}|\{\{link:([^{}|\n\r]+)\|([^{}|\n\r]+)\}\}/g
const ANY_MARK_REGEX = /\{\{(tag:)?(red|green|orange|blue|purple|gray)\|/
const LINK_PREFIX = '{{link:'

/** 是否含合法标记 */
export function hasMarkup(raw) {
    const text = raw || ''
    return text.includes('{{') && (ANY_MARK_REGEX.test(text) || text.includes(LINK_PREFIX))
}

/**
 * 解析为片段序列
 * @param {string} raw 消息原文
 * @returns {Array<{type:'plain'|'styled'|'link', text:string, color?:string, pill?:boolean, url?:string}>}
 */
export function parseRich(raw) {
    const text = raw == null ? '' : String(raw)
    if (!hasMarkup(text)) {
        return [{ type: SPAN_PLAIN, text }]
    }
    const spans = []
    let last = 0
    let marks = 0
    TOKEN_REGEX.lastIndex = 0
    let match
    while ((match = TOKEN_REGEX.exec(text)) !== null) {
        if (match[4] !== undefined || match[5] !== undefined) {
            if (!/^https?:/i.test(match[5]) || marks >= MAX_MARKS) {
                continue
            }
            if (match.index > last) {
                spans.push({ type: SPAN_PLAIN, text: text.slice(last, match.index) })
            }
            spans.push({ type: SPAN_LINK, text: match[4], url: match[5] })
            marks += 1
            last = match.index + match[0].length
            continue
        }
        const isPill = !!match[1]
        const color = match[2]
        const body = match[3]
        if (!Object.prototype.hasOwnProperty.call(RICH_COLORS, color) || marks >= MAX_MARKS) {
            continue
        }
        if (match.index > last) {
            spans.push({ type: SPAN_PLAIN, text: text.slice(last, match.index) })
        }
        spans.push({ type: SPAN_STYLED, text: body, color, pill: isPill })
        marks += 1
        last = match.index + match[0].length
    }
    if (last < text.length) {
        spans.push({ type: SPAN_PLAIN, text: text.slice(last) })
    }
    return spans.length ? spans : [{ type: SPAN_PLAIN, text }]
}

/** 长文折叠阈值（换行数） */
export const FOLD_LINES = 10

/** 是否需要折叠（按换行符计数） */
export function needFold(raw) {
    const text = raw || ''
    let lines = 0
    for (let i = 0; i < text.length; i++) {
        if (text[i] === '\n') {
            lines += 1
        }
    }
    return lines > FOLD_LINES
}
