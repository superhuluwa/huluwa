/**
 * 电影天堂在线线路的展示与默认下载参数：
 *  - dyttm3u8 等 m3u8 线路排在前面，打开弹窗默认展示第一条；
 *  - 剧集按集号倒序（新集在前），方便直接下载最新一集；
 *  - 从播放条目标题识别集号、清洗标准剧名，供「离线下载」直接入队使用。
 * 识别与清洗规则均与后端 HlsSupport / DyttSearchService 对齐。
 */

/** MacCMS 采集接口默认地址（卡片载荷未携带 apiUrl 时使用） */
export const DEFAULT_HLS_API = 'https://dyttzyw.com/api.php/provide/vod/'

const SPECIAL = /预告|花絮|番外|彩蛋|特辑|特别篇|片花|幕后|先导|解说|试看/
const MULTI_EPISODE = /(?:S\d+E\d+(?:[ ._-]*E|[-~])\d+)|(?:第\s*\d+\s*[-~至]\s*\d+\s*集)/i
const SEASON = /第\s*([0-9零〇一二两三四五六七八九十百千]+)\s*季|(?:\bSeason\s+|\bS)(\d{1,3})(?!\d)/gi
const CN_DIGITS = '零一二三四五六七八九'

/** 是否为正片之外的条目（预告、花絮等）：这些条目不按默认集号入队 */
export function isExtra(title) {
    return SPECIAL.test(String(title || ''))
}

/** 是否为 m3u8 直链（与后端 HlsDownloadService.isM3u8 对齐） */
export function isM3u8Url(url) {
    return /\.m3u8(?:[?#]|$)/i.test(url || '')
}

/** 线路顺序：dyttm3u8 最前，其余 m3u8 线路次之，同档保持卡片原始顺序 */
export function sortPlaySources(sources) {
    const list = Array.isArray(sources) ? sources : []
    return list.map((source, index) => ({
        source,
        index,
        rank: /dyttm3u8/i.test(source?.name || '') ? 0
            : (source?.items || []).some(item => isM3u8Url(item?.url)) ? 1 : 2
    })).sort((left, right) => left.rank - right.rank || left.index - right.index)
        .map(entry => entry.source)
}

/** 剧集顺序：按集号倒序；识别不出集号的条目（正片、预告等）保持原顺序排在最后 */
export function sortPlayItems(items) {
    const list = Array.isArray(items) ? items : []
    return list.map((item, index) => ({ item, index, episode: episodeNumber(item?.title) }))
        .sort((left, right) => {
            if (left.episode === null && right.episode === null) return left.index - right.index
            if (left.episode === null) return 1
            if (right.episode === null) return -1
            return right.episode - left.episode || left.index - right.index
        }).map(entry => entry.item)
}

/** 集号识别：第01集 / 第一百二十三集 / S02E12 / EP06 / 01集；预告、花絮、合并集号返回 null */
export function episodeNumber(title) {
    const value = String(title || '').trim()
    if (SPECIAL.test(value) || MULTI_EPISODE.test(value)) {
        return null
    }
    const chinese = value.match(/第\s*([0-9零〇一二两三四五六七八九十百千]+)\s*[集话期]/)
    const episode = value.match(/(?:S\d{1,3}E|(?:^|[ ._-])EP?)(\d{1,4})(?!\d)/i)
    const plain = value.match(/^(\d{1,4})(?:集|话|期)?$/)
    const number = chinese ? chineseNumber(chinese[1]) : episode ? Number(episode[1]) : plain ? Number(plain[1]) : null
    return number && number > 0 && number <= 9999 ? number : null
}

/** 季号识别：第2季 / 第二季 / Season 2 / S02；识别不到返回 null（由调用方回退到 1） */
export function seasonNumber(title) {
    const value = String(title || '')
    const chinese = value.match(/第\s*([0-9零〇一二两三四五六七八九十百千]+)\s*季/)
    const english = value.match(/(?:\bSeason\s+|\bS)(\d{1,3})(?!\d)/i)
    const number = chinese ? chineseNumber(chinese[1]) : english ? Number(english[1]) : null
    return number && number > 0 && number <= 999 ? number : null
}

/** 标准剧名：去掉“更新至第N集”备注与季号，规则与后端 HlsSupport.defaultTitle 对齐 */
export function defaultTitle(title) {
    const raw = String(title || '').trim()
    let name = raw.replace(/[（(\[]?\s*(?:更新至|更新到|全)\s*(?:第\s*)?\d+\s*集.*$/, '')
    name = name.replace(SEASON, '').replace(/[（(\[]\s*[）)\]]/g, '')
        .replace(/^[\s._-]+|[\s._-]+$/g, '')
    return safeName(name || raw)
}

/** 文件与目录名清洗（与后端 HlsSupport.safeName 对齐） */
export function safeName(title) {
    return String(title || '').replace(/[\u0000-\u001F\u007F-\u009F\\/:*?"<>|]/g, '_')
        .replace(/\s+/g, ' ').replace(/^[. ]+|[. ]+$/g, '')
}

/** 中文数字转阿拉伯数字（与后端 HlsSupport.chineseNumber 一致） */
function chineseNumber(value) {
    if (/^[0-9]+$/.test(value)) {
        return Number(value)
    }
    const normalized = [...value].map(character => character === '两' ? '二' : character === '〇' ? '零' : character)
    if (normalized.every(character => CN_DIGITS.includes(character))) {
        return Number(normalized.map(character => CN_DIGITS.indexOf(character)).join(''))
    }
    let result = 0
    let digit = 0
    for (const character of normalized) {
        const unit = character === '十' ? 10 : character === '百' ? 100 : character === '千' ? 1000 : 0
        if (unit > 0) {
            result += (digit === 0 ? 1 : digit) * unit
            digit = 0
        } else {
            digit = CN_DIGITS.indexOf(character)
        }
    }
    return result + Math.max(digit, 0)
}


