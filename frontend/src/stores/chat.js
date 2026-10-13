import { defineStore } from 'pinia'
import {
    GetSessions,
    SessionMessages,
    SendMessage,
    MarkSessionRead,
    ClearSession
} from '@/api/chat'
import chatSocket from '@/libs/ws'
import outbox from '@/libs/chatOutbox'
import { message as toast, notification } from '@/libs/discrete'
import { useMovieSearchWatchStore } from '@/stores/movieSearchWatch'

/**
 * 消息中心会话状态层：
 *  - 会话列表与消息流都由后端持库，本 store 只做「拉取 + 合并 + 乐观追加」；
 *  - 发送走 WebSocket（chat.send，requestId 幂等）：本地立即插入 pending 气泡 →
 *    服务端毫秒级回 chat.ack（落库行收编 pending、转圈消失）→ chat.processing 显示 typing →
 *    后台搜索 / 提交 / 解析完成后 chat.messages 推送机器人回复。WS 断开时 HTTP 兜底发送，
 *    未 ack 的请求留在 localStorage outbox，重连后重放（服务端幂等保证不重复落库）；
 *  - 实时性：WS 在线时新消息靠推送、轮询让位；WS 断开自动降级为轮询（POLL_MS），
 *    覆盖系统通知、订阅巡检等外部写入。
 */

const PAGE_SIZE = 50
/** 轮询间隔：仅在 WS 断开的降级期生效 */
const POLL_MS = 8000
/** typing 指示兜底超时：后台任务异常终止（如服务重启）时避免三点动画挂死 */
const TYPING_TIMEOUT_MS = 3 * 60 * 1000

let pollTimer = null
const typingTimers = {}

/** 排序键：数字 id 按值排序；本地 pending 气泡是字符串临时 id，恒排最后（V8 sort 稳定，多个 pending 保持插入序） */
function idKey(item) {
    return typeof item.id === 'number' ? item.id : Number.MAX_SAFE_INTEGER
}

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
    merged.sort((a, b) => idKey(a) - idKey(b))
    return merged
}

function newRequestId() {
    if (window.crypto && crypto.randomUUID) {
        return crypto.randomUUID()
    }
    return 'r-' + Date.now() + '-' + Math.random().toString(36).slice(2)
}

/** 与后端一致的本地时间戳（pending 气泡展示用，ack 后即被服务端时间替换） */
function localStamp() {
    const d = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
        + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
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
        /** WS 实时通道是否在线：在线时轮询让位，发送走 chat.send */
        wsReady: false,
        /** 各会话的 typing 指示（sessionId → requestId），chat.processing 置、消息/终止帧清 */
        typing: {},
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
            if (key === 'movie') useMovieSearchWatchStore().refresh().catch(() => {})
            this.loadingMessages = true
            try {
                const page = await SessionMessages(key, null, PAGE_SIZE)
                const rows = Array.isArray(page) ? page : []
                // 首页结果整页替换（这样删除过的行能正确消失），
                // 但保留此前向上翻页加载的更早历史（id 小于本页最早一条）；
                // 服务端本页为空 = 该会话已无消息，本地历史一并清掉，避免残留已删除的行。
                const oldest = rows.length ? rows[0].id : 0
                const earlier = rows.length
                    ? (this.messagesBySession[key] || []).filter((item) => typeof item.id === 'number' && item.id < oldest)
                    : []
                this.messagesBySession = {
                    ...this.messagesBySession,
                    [key]: this.restorePending(key, earlier.length ? [...earlier, ...rows] : rows)
                }
                this.hasMoreBySession = { ...this.hasMoreBySession, [key]: rows.length >= PAGE_SIZE }
                await this.markRead(key)
            } finally {
                this.loadingMessages = false
            }
        },

        /** 页面刷新后按 outbox 重建尚未 ack 的 pending 气泡（WS 连上后重放 → ack 收编） */
        restorePending(key, rows) {
            const pendings = outbox.bySession(key)
            if (!pendings.length) {
                return rows
            }
            return mergeById(rows, pendings.map((item) => ({
                id: 'tmp-' + item.requestId,
                requestId: item.requestId,
                sessionId: key,
                role: 'user',
                type: 'text',
                content: item.content,
                contentText: null,
                payload: null,
                read: true,
                createdAt: localStamp(),
                status: 'pending'
            })))
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
         * 发送消息（乐观渲染）：本地立即插入 pending 气泡（转圈）→ outbox 持久化 →
         * WS 在线走 chat.send（chat.ack 到达后 pending 收编、转圈消失）；
         * WS 断开走 HTTP 兜底（同一 requestId 幂等），失败把气泡转 failed（可点击重发）。
         */
        async send(content) {
            const key = this.currentKey
            const text = (content || '').trim()
            if (!key || !text) {
                return
            }
            const requestId = newRequestId()
            this.sending = true
            try {
                const pending = {
                    id: 'tmp-' + requestId,
                    requestId,
                    sessionId: key,
                    role: 'user',
                    type: 'text',
                    content: text,
                    contentText: null,
                    payload: null,
                    read: true,
                    createdAt: localStamp(),
                    status: 'pending'
                }
                this.messagesBySession = {
                    ...this.messagesBySession,
                    [key]: [...(this.messagesBySession[key] || []), pending]
                }
                outbox.add({ requestId, sessionId: key, content: text, createdAt: Date.now() })
                if (this.wsReady && chatSocket.send({ type: 'chat.send', requestId, sessionId: key, content: text })) {
                    return
                }
                await this.sendViaHttp(key, requestId, text)
            } finally {
                this.sending = false
            }
        },

        /** HTTP 兜底发送（WS 断开 / send 失败）：成功按 ack 语义收编，失败转 failed 气泡 */
        async sendViaHttp(key, requestId, text) {
            try {
                const produced = await SendMessage(key, text, requestId)
                const rows = Array.isArray(produced) ? produced : []
                this.ingestMessages(key, rows)
                this.adoptPending(key, rows)
                outbox.remove(requestId)
            } catch (e) {
                // 拦截器已 toast；气泡转 failed 等待用户点击重发（WS 恢复后也可由 outbox 重放收编）
                this.setPendingStatus(key, requestId, 'failed')
            }
        },

        /** failed 气泡点击重发：移除旧气泡与 outbox 条目，按新 requestId 重新发送 */
        async resend(bubble) {
            const key = bubble.sessionId || this.currentKey
            if (!key || !bubble.content) {
                return
            }
            if (bubble.requestId) {
                outbox.remove(bubble.requestId)
            }
            this.messagesBySession = {
                ...this.messagesBySession,
                [key]: (this.messagesBySession[key] || []).filter((item) => item.id !== bubble.id)
            }
            await this.send(bubble.content)
        },

        /** 把 pending 气泡置为指定状态（failed / 移除） */
        setPendingStatus(key, requestId, status) {
            const rows = this.messagesBySession[key] || []
            this.messagesBySession = {
                ...this.messagesBySession,
                [key]: rows.map((item) => (item.requestId === requestId && item.status === 'pending'
                    ? { ...item, status }
                    : item))
            }
        },

        /**
         * 「收编」pending 气泡：拉回 / 推回的消息行里出现了同会话同正文的用户行，
         * 说明服务端已落库（HTTP 兜底或轮询路径，ack 可能丢了），移除对应临时气泡与 outbox 条目。
         */
        adoptPending(key, rows) {
            const pendings = (this.messagesBySession[key] || []).filter((item) => item.status === 'pending')
            if (!pendings.length || !rows.length) {
                return
            }
            const known = new Set(rows.filter((item) => item.role === 'user').map((item) => item.content))
            const matched = pendings.filter((item) => known.has(item.content))
            if (!matched.length) {
                return
            }
            const dropIds = new Set(matched.map((item) => item.id))
            matched.forEach((item) => outbox.remove(item.requestId))
            this.messagesBySession = {
                ...this.messagesBySession,
                [key]: (this.messagesBySession[key] || []).filter((item) => !dropIds.has(item.id))
            }
        },

        /** 合并一批消息行（推送 / HTTP 响应共用）：去重、收编 pending、当前会话顺手标记已读 */
        ingestMessages(key, rows) {
            if (!rows.length) {
                return
            }
            this.messagesBySession = {
                ...this.messagesBySession,
                [key]: mergeById(this.messagesBySession[key] || [], rows)
            }
            this.adoptPending(key, rows)
            if (key === this.currentKey) {
                this.markRead(key)
            }
        },

        // ------------------------------------------------------------------ 实时通道

        /** 建立实时通道（mounted / activated 幂等调用）；页面级生命周期里连接常驻不随 keep-alive 断开 */
        initRealtime() {
            if (!this._wsBound) {
                this._wsBound = true
                chatSocket.on('open', () => {
                    this.wsReady = true
                    // 重连三件事：重放 outbox 未 ack 请求（服务端幂等）→ 刷新会话列表 → 补断线窗口漏掉的消息
                    outbox.prune()
                    outbox.all().forEach((item) => {
                        chatSocket.send({
                            type: 'chat.send',
                            requestId: item.requestId,
                            sessionId: item.sessionId,
                            content: item.content
                        })
                    })
                    this.refreshSessions()
                    this.reloadSession(this.currentKey)
                    useMovieSearchWatchStore().refresh().catch(() => {})
                })
                chatSocket.on('close', () => {
                    // 降级：poll() 守卫自动放开，轮询接管
                    this.wsReady = false
                })
                chatSocket.on('chat.ack', (frame) => this.handleAck(frame))
                chatSocket.on('chat.processing', (frame) => this.setTyping(frame.sessionId, frame.requestId))
                chatSocket.on('chat.messages', (frame) => this.handleMessages(frame))
                chatSocket.on('chat.done', (frame) => this.handleDone(frame))
                chatSocket.on('chat.sessions', (frame) => this.handleSessions(frame))
                chatSocket.on('chat.movie-search-watch', (frame) => useMovieSearchWatchStore().apply(frame.watch))
            }
            chatSocket.reset()
            chatSocket.connect()
        },

        handleAck(frame) {
            const { requestId, message: row } = frame
            if (row && row.sessionId) {
                const key = row.sessionId
                this.messagesBySession = {
                    ...this.messagesBySession,
                    [key]: (this.messagesBySession[key] || []).filter(
                        (item) => !(item.requestId === requestId && item.status === 'pending')
                    )
                }
                this.ingestMessages(key, [row])
            }
            outbox.remove(requestId)
        },

        handleMessages(frame) {
            const key = frame.sessionId
            const rows = Array.isArray(frame.messages) ? frame.messages : []
            const knownIds = new Set((this.messagesBySession[key] || []).map(row => row.id))
            this.ingestMessages(key, rows)
            if (key !== 'system') return
            rows.filter(row => row.type === 'notify' && !knownIds.has(row.id)).forEach(row => {
                let meta
                try {
                    meta = JSON.parse(row.payload || '{}')
                } catch (e) {
                    return
                }
                if (meta?.source === '电影港监听') {
                    notification.success({
                        title: meta.title || '电影港监听已找到资源',
                        content: '资源卡片已发送到电影港会话，详情可在系统通知中查看。',
                        duration: 8000
                    })
                }
            })
        },

        handleDone(frame) {
            this.setTyping(frame.sessionId, null)
            if (frame.status === 'failed' && frame.error) {
                toast.warning(frame.error)
            }
        },

        handleSessions(frame) {
            if (Array.isArray(frame.sessions)) {
                this.sessions = frame.sessions
            }
        },

        setTyping(key, requestId) {
            if (typingTimers[key]) {
                clearTimeout(typingTimers[key])
                typingTimers[key] = null
            }
            this.typing = { ...this.typing, [key]: requestId }
            if (requestId) {
                // 兜底：后台任务异常终止（服务重启等）没有终止帧时，超时自动消掉三点动画
                typingTimers[key] = setTimeout(() => {
                    typingTimers[key] = null
                    this.typing = { ...this.typing, [key]: null }
                }, TYPING_TIMEOUT_MS)
            }
        },

        // ------------------------------------------------------------------ 已读 / 清空 / 轮询

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
            const applyLocal = () => {
                this.messagesBySession = {
                    ...this.messagesBySession,
                    [key]: local.map((item) => (item.read ? item : { ...item, read: true }))
                }
                this.sessions = this.sessions.map((item) => (item.id === key ? { ...item, unread: 0 } : item))
            }
            if (this.wsReady && chatSocket.send({ type: 'chat.read', sessionId: key })) {
                applyLocal()
                return
            }
            await MarkSessionRead(key)
            applyLocal()
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

        /** 轮询（仅 WS 断开的降级期生效）：会话列表 + 当前会话尾部新消息（覆盖系统通知/他端写入） */
        async poll() {
            if (this.wsReady) {
                return
            }
            try {
                await this.refreshSessions()
                if (useMovieSearchWatchStore().loaded) {
                    await useMovieSearchWatchStore().refresh().catch(() => {})
                }
                const key = this.currentKey
                if (!key || this.loadingMessages) {
                    return
                }
                const rows = this.messagesBySession[key] || []
                // 增量游标取最后一条「已落库」消息（尾部 pending 气泡是字符串临时 id，不能当游标）
                const numeric = rows.filter((item) => typeof item.id === 'number')
                const latest = numeric.length ? numeric[numeric.length - 1].id : 0
                const page = await SessionMessages(key, null, PAGE_SIZE)
                const incoming = (Array.isArray(page) ? page : []).filter((item) => item.id > latest)
                if (incoming.length) {
                    this.handleMessages({ sessionId: key, messages: incoming })
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
