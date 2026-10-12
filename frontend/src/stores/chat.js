import { defineStore } from 'pinia'
import {
    GetSessions,
    SessionMessages,
    SendMessage,
    MarkSessionRead,
    ClearSession
} from '@/api/chat'

/**
 * 消息中心会话状态层（对齐量子端 stores/chat 的职责划分，按本项目接口收敛）：
 *  - 会话列表与消息流都由后端持库，本 store 只做「拉取 + 合并 + 乐观追加」；
 *  - 发送（POST）同步等待服务端完成电影港搜索 / 磁力提交 / 视频解析并返回本轮新增消息，直接并入列表；
 *  - 轮询（POLL_MS）刷新会话列表与当前会话的尾部新消息，覆盖系统通知等外部写入。
 *
 * 与量子端的差异：本项目后端无 WS 通道，实时性由轮询提供；消息不做本地持久化。
 */

const PAGE_SIZE = 50
/** 轮询间隔：兼顾实时感与站点/数据库压力（发送时另有即时刷新，不等待轮询） */
const POLL_MS = 8000

let pollTimer = null

function mergeById(existing, incoming) {
    if (!incoming || !incoming.length) {
        return existing
    }
    const seen = new Set(existing.map((item) => item.id))
    const merged = existing.slice()
    for (const item of incoming) {
        if (!seen.has(item.id)) {
            seen.add(item.id)
            merged.push(item)
        }
    }
    merged.sort((a, b) => a.id - b.id)
    return merged
}

export const useChatStore = defineStore('chat', {
    state: () => ({
        sessions: [],
        currentKey: '',
        messagesBySession: {},
        loadingSessions: false,
        loadingMessages: false,
        loadingEarlier: false,
        hasMoreBySession: {},
        sending: false,
        /** 首屏是否已定位过（会话页进入时瞬时落底，之后新消息才平滑跟随） */
        initialized: false
    }),
    getters: {
        currentSession(state) {
            return state.sessions.find((item) => item.id === state.currentKey) || null
        },
        currentMessages(state) {
            return state.messagesBySession[state.currentKey] || []
        },
        hasMore(state) {
            return !!state.hasMoreBySession[state.currentKey]
        },
        totalUnread(state) {
            return state.sessions.reduce((sum, item) => sum + (item.unread || 0), 0)
        },
        /** 电影港 / 磁力下载 / 视频解析等聊天会话可发送；系统通知为只读 */
        canSend(state) {
            const session = state.sessions.find((item) => item.id === state.currentKey)
            return !!session && session.kind !== 'system'
        },
        /** 判断会话是不是「磁力下载」（页脚状态条 / 配置引导只在该会话出现） */
        isMagnet(state) {
            const session = state.sessions.find((item) => item.id === state.currentKey)
            return !!session && session.kind === 'magnet'
        },
        /** 判断会话是不是「视频解析」（配置引导只在该会话出现） */
        isVideo(state) {
            const session = state.sessions.find((item) => item.id === state.currentKey)
            return !!session && session.kind === 'video'
        }
    },
    actions: {
        async refreshSessions() {
            this.loadingSessions = true
            try {
                const list = await GetSessions()
                this.sessions = Array.isArray(list) ? list : []
            } finally {
                this.loadingSessions = false
            }
        },

        /** 打开会话：拉最近一页消息（已打开过则只刷新，不丢向上翻页的历史） */
        async openSession(sessionId) {
            const key = sessionId || this.sessions[0]?.id || ''
            this.currentKey = key
            if (!key) {
                return
            }
            this.loadingMessages = true
            try {
                const page = await SessionMessages(key, null, PAGE_SIZE)
                const rows = Array.isArray(page) ? page : []
                // 首页结果整页替换（这样删除过的行能正确消失），
                // 但保留此前向上翻页加载的更早历史（id 小于本页最早一条）；
                // 服务端本页为空 = 该会话已无消息，本地历史一并清掉，避免残留已删除的行。
                const oldest = rows.length ? rows[0].id : 0
                const earlier = rows.length
                    ? (this.messagesBySession[key] || []).filter((item) => item.id < oldest)
                    : []
                this.messagesBySession = {
                    ...this.messagesBySession,
                    [key]: earlier.length ? [...earlier, ...rows] : rows
                }
                this.hasMoreBySession = { ...this.hasMoreBySession, [key]: rows.length >= PAGE_SIZE }
                await this.markRead(key)
            } finally {
                this.loadingMessages = false
            }
        },

        /**
         * 重新加载会话首页（替换而非合并）：用于删除系统通知等「条目消失」的场景，
         * mergeById 只增不减，删除后必须整页替换才不会残留旧行。
         */
        async reloadSession(sessionId) {
            const key = sessionId || this.currentKey
            if (!key) {
                return
            }
            const page = await SessionMessages(key, null, PAGE_SIZE)
            const rows = Array.isArray(page) ? page : []
            this.messagesBySession = { ...this.messagesBySession, [key]: rows }
            this.hasMoreBySession = { ...this.hasMoreBySession, [key]: rows.length >= PAGE_SIZE }
        },

        /** 向上翻页：拉 id 小于当前最早一条的更早消息 */
        async loadEarlier() {
            const key = this.currentKey
            const rows = this.messagesBySession[key] || []
            if (!key || !rows.length || this.loadingEarlier || !this.hasMore) {
                return
            }
            this.loadingEarlier = true
            try {
                const page = await SessionMessages(key, rows[0].id, PAGE_SIZE)
                const earlier = Array.isArray(page) ? page : []
                this.messagesBySession = {
                    ...this.messagesBySession,
                    [key]: mergeById(earlier, rows)
                }
                this.hasMoreBySession = { ...this.hasMoreBySession, [key]: earlier.length >= PAGE_SIZE }
            } finally {
                this.loadingEarlier = false
            }
        },

        /**
         * 发送消息：后端同步执行电影港搜索 / 磁力提交并返回「用户气泡 + 机器人回复」，
         * 这里直接并入消息流；磁力会话顺带刷新一次 qB 连接快照，失败由页面提示。
         */
        async send(content) {
            const key = this.currentKey
            const text = (content || '').trim()
            if (!key || !text || this.sending) {
                return
            }
            this.sending = true
            try {
                const produced = await SendMessage(key, text)
                const rows = Array.isArray(produced) ? produced : []
                this.messagesBySession = {
                    ...this.messagesBySession,
                    [key]: mergeById(this.messagesBySession[key] || [], rows)
                }
                await Promise.all([this.refreshSessions(), this.markRead(key)])
            } finally {
                this.sending = false
            }
        },

        async markRead(sessionId) {
            const key = sessionId || this.currentKey
            if (!key) {
                return
            }
            const local = this.messagesBySession[key] || []
            const hasUnread = local.some((item) => item.role === 'bot' && !item.read)
            const sessionUnread = this.sessions.find((item) => item.id === key)?.unread || 0
            if (!hasUnread && !sessionUnread) {
                return
            }
            await MarkSessionRead(key)
            this.messagesBySession = {
                ...this.messagesBySession,
                [key]: local.map((item) => (item.read ? item : { ...item, read: true }))
            }
            this.sessions = this.sessions.map((item) => (item.id === key ? { ...item, unread: 0 } : item))
        },

        async clearSession(sessionId) {
            const key = sessionId || this.currentKey
            if (!key) {
                return
            }
            await ClearSession(key)
            this.messagesBySession = { ...this.messagesBySession, [key]: [] }
            this.hasMoreBySession = { ...this.hasMoreBySession, [key]: false }
            await this.refreshSessions()
        },

        /** 轮询：会话列表 + 当前会话尾部新消息（覆盖系统通知/他端写入） */
        async poll() {
            try {
                await this.refreshSessions()
                const key = this.currentKey
                if (!key || this.loadingMessages) {
                    return
                }
                const rows = this.messagesBySession[key] || []
                const latest = rows.length ? rows[rows.length - 1].id : 0
                if (!latest) {
                    return
                }
                const page = await SessionMessages(key, null, PAGE_SIZE)
                const incoming = (Array.isArray(page) ? page : []).filter((item) => item.id > latest)
                if (incoming.length) {
                    this.messagesBySession = {
                        ...this.messagesBySession,
                        [key]: mergeById(rows, incoming)
                    }
                    await this.markRead(key)
                }
            } catch (e) {
                // 轮询失败静默（拦截器已提示网络异常时避免刷屏）
            }
        },

        startPolling() {
            if (pollTimer) {
                return
            }
            pollTimer = setInterval(() => this.poll(), POLL_MS)
        },

        stopPolling() {
            if (pollTimer) {
                clearInterval(pollTimer)
                pollTimer = null
            }
        }
    }
})
