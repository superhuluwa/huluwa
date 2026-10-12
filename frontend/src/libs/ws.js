import http from '@/libs/http'

/**
 * 聊天 WebSocket 单例客户端：连接管理 / 应用层心跳 / 指数退避重连 / 帧分发。
 * 协议见后端 ws/ChatWebSocketHandler：上行 chat.send|chat.read|ping，
 * 下行 hello|chat.ack|chat.processing|chat.messages|chat.done|chat.sessions|pong|error。
 *
 * 弱网三板斧：
 *  - 指数退避重连：1s→2s→…→30s 封顶，每次乘 0.8~1.2 抖动（多标签页不齐步冲击）
 *  - 应用层心跳：每 25s 发 ping，连续 50s 未收到 pong 判定假死（NAT 静默超时/半开）主动重连
 *  - visibilitychange：手机切后台 TCP 被杀、回前台 onclose 却不触发的经典假死，变可见即检查重连
 *
 * token 过期闭环：浏览器 WS 握手拿不到 HTTP 401 状态码，无法区分「过期」与「断网」，
 * 连接前先发一个静默探活请求（GET /chat/sessions）——401 时 axios 拦截器已清 token 跳登录，
 * 本模块停止重连；探活网络失败则照常重连（服务不可达属于弱网场景）。
 */
const RECONNECT_BASE_MS = 1000
const RECONNECT_MAX_MS = 30000
const PING_INTERVAL_MS = 25000
const PONG_TIMEOUT_MS = 50000

class ChatSocket {
    constructor() {
        /** idle | connecting | open */
        this.state = 'idle'
        this.handlers = new Map()
        this.socket = null
        this.attempt = 0
        this.reconnectTimer = null
        this.pingTimer = null
        this.lastPongAt = 0
        this.stopped = false
        this.probing = false
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                this.resurrectIfStale()
            }
        })
    }

    on(type, fn) {
        if (!this.handlers.has(type)) {
            this.handlers.set(type, new Set())
        }
        this.handlers.get(type).add(fn)
    }

    off(type, fn) {
        this.handlers.get(type)?.delete(fn)
    }

    emit(type, payload) {
        this.handlers.get(type)?.forEach((fn) => {
            try {
                fn(payload)
            } catch (e) {
                console.warn('[ws] handler error', type, e)
            }
        })
    }

    /** 建立连接（幂等：已连接 / 连接中直接返回）；stopped 状态（401 登出）不再连 */
    connect() {
        if (this.stopped || this.state !== 'idle') {
            return
        }
        this.probeThenOpen()
    }

    /** 探活（区分 401 与断网）后再握手 */
    async probeThenOpen() {
        if (this.probing) {
            return
        }
        this.probing = true
        this.state = 'connecting'
        try {
            await http.get('/chat/sessions', undefined, { silent: true })
        } catch (e) {
            this.probing = false
            this.state = 'idle'
            // 401 时 axios 拦截器已清 token 跳 #/login：停止重连，等重新登录
            if (!localStorage.getItem('huluwa.token')) {
                this.stopped = true
                return
            }
            this.scheduleReconnect()
            return
        }
        this.probing = false
        if (this.stopped || this.state === 'open') {
            this.state = this.stopped ? 'idle' : this.state
            return
        }
        this.open()
    }

    open() {
        const token = localStorage.getItem('huluwa.token') || ''
        const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
        const url = scheme + '://' + location.host + '/ws/chat?token=' + encodeURIComponent(token)
        let socket
        try {
            socket = new WebSocket(url)
        } catch (e) {
            this.scheduleReconnect()
            return
        }
        this.socket = socket
        this.state = 'connecting'
        socket.onopen = () => {
            if (this.socket !== socket) {
                return
            }
            this.state = 'open'
            this.attempt = 0
            this.lastPongAt = Date.now()
            this.startHeartbeat()
            this.emit('open')
        }
        socket.onmessage = (event) => {
            if (this.socket !== socket) {
                return
            }
            let frame = null
            try {
                frame = JSON.parse(event.data)
            } catch (e) {
                return
            }
            if (frame && frame.type === 'pong') {
                this.lastPongAt = Date.now()
            }
            if (frame && frame.type) {
                this.emit(frame.type, frame)
            }
        }
        socket.onclose = (event) => {
            if (this.socket !== socket) {
                return
            }
            this.cleanupSocket()
            this.emit('close', event)
            if (event.code === 4001) {
                // 服务端判定未授权：停止重连，等用户重新登录
                this.stopped = true
                this.state = 'idle'
                return
            }
            this.scheduleReconnect()
        }
        socket.onerror = () => {
            // onclose 随后必到，重连统一在 onclose 处理
        }
    }

    send(payload) {
        if (this.state !== 'open' || !this.socket) {
            return false
        }
        try {
            this.socket.send(JSON.stringify(payload))
            return true
        } catch (e) {
            return false
        }
    }

    /** 登出 / 401 时显式断开并停止重连 */
    disconnect() {
        this.stopped = true
        this.clearTimers()
        if (this.socket) {
            this.socket.onclose = null
            this.socket.onmessage = null
            this.socket.onerror = null
            try {
                this.socket.close()
            } catch (e) { /* 已关闭 */ }
            this.socket = null
        }
        this.state = 'idle'
    }

    /** 重新允许连接（登录成功后由页面调用 connect 前重置） */
    reset() {
        this.stopped = false
        this.attempt = 0
    }

    /** 页面变可见时：连接已断或心跳超时，立即重连（重置退避） */
    resurrectIfStale() {
        if (this.stopped) {
            return
        }
        const pongStale = this.state === 'open' && Date.now() - this.lastPongAt > PONG_TIMEOUT_MS
        if (this.state === 'idle' || pongStale) {
            this.clearTimers()
            this.cleanupSocket()
            this.attempt = 0
            this.state = 'idle'
            this.connect()
        }
    }

    startHeartbeat() {
        this.stopPingTimer()
        this.pingTimer = setInterval(() => {
            if (this.state !== 'open') {
                return
            }
            if (Date.now() - this.lastPongAt > PONG_TIMEOUT_MS) {
                // 假死：主动断开走统一重连路径
                try {
                    this.socket?.close()
                } catch (e) { /* 忽略 */ }
                return
            }
            this.send({ type: 'ping', t: Date.now() })
        }, PING_INTERVAL_MS)
    }

    scheduleReconnect() {
        if (this.stopped || this.reconnectTimer) {
            return
        }
        const backoff = Math.min(RECONNECT_BASE_MS * Math.pow(2, this.attempt), RECONNECT_MAX_MS)
        const jitter = backoff * (0.8 + Math.random() * 0.4)
        this.attempt = Math.min(this.attempt + 1, 10)
        this.state = 'idle'
        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null
            this.connect()
        }, jitter)
    }

    stopPingTimer() {
        if (this.pingTimer) {
            clearInterval(this.pingTimer)
            this.pingTimer = null
        }
    }

    clearTimers() {
        this.stopPingTimer()
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer)
            this.reconnectTimer = null
        }
    }

    cleanupSocket() {
        this.stopPingTimer()
        if (this.socket) {
            this.socket.onclose = null
            this.socket.onmessage = null
            this.socket.onerror = null
            try {
                this.socket.close()
            } catch (e) { /* 已关闭 */ }
            this.socket = null
        }
        this.state = 'idle'
    }
}

export const chatSocket = new ChatSocket()
export default chatSocket
