import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useMovieSearchWatchStore } from '@/stores/movieSearchWatch'
import { useChatStore } from '@/stores/chat'
import MessageBubble from '@/views/message/components/MessageBubble.vue'
import WatchAction from '@/views/message/components/MovieSearchWatchAction.vue'
import * as api from '@/api/chat'
import { notification } from '@/libs/discrete'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
vi.mock('@/libs/ws', () => ({ default: { on: vi.fn(), send: vi.fn(), reset: vi.fn(), connect: vi.fn() } }))
vi.mock('@/libs/chatOutbox', () => ({ default: { bySession: vi.fn(() => []) } }))
vi.mock('@/libs/discrete', () => ({ message: { warning: vi.fn(), error: vi.fn() }, notification: { success: vi.fn() } }))
vi.mock('@/api/chat', () => ({
    GetSessions: vi.fn(), SessionMessages: vi.fn(), SendMessage: vi.fn(), MarkSessionRead: vi.fn(), ClearSession: vi.fn(),
    GetMovieSearchWatches: vi.fn(), StartMovieSearchWatch: vi.fn(), CancelMovieSearchWatch: vi.fn(),
    GetMovieDownloadSettings: vi.fn(), SaveMovieDownloadSettings: vi.fn(), GetMovieSubscriptionStatus: vi.fn(),
    UpdateMovieSubscriptionSettings: vi.fn(), SubscribeMovie: vi.fn(), PreviewMagnets: vi.fn(), DownloadMagnet: vi.fn()
}))

const watching = { id: 7, keyword: 'Dune', status: 'watching', intervalMinutes: 30, version: 1 }

function bubble(message) {
    const vm = { message }
    Object.defineProperty(vm, 'payload', { get: () => MessageBubble.computed.payload.call(vm) })
    Object.defineProperty(vm, 'movieWatchKeyword', { get: () => MessageBubble.computed.movieWatchKeyword.call(vm) })
    return vm
}

beforeEach(() => {
    vi.resetAllMocks()
    setActivePinia(createPinia())
    vi.stubGlobal('window', { $message: { success: vi.fn(), error: vi.fn() } })
    api.GetMovieSearchWatches.mockResolvedValue([])
    api.StartMovieSearchWatch.mockResolvedValue(watching)
})

describe('电影港搜索监听', () => {
    it('无结果回复提供关键字，网络错误和电影天堂回复不展示监听', () => {
        const vm = bubble({ sessionId: 'movie', role: 'bot', type: 'error', payload: JSON.stringify({
            kind: 'movie-search-watch', keyword: 'Dune', noResults: true
        }) })
        expect(vm.movieWatchKeyword).toBe('Dune')
        expect(MessageBubble.computed.isMovieSearchWatch.call(vm)).toBe(true)
        expect(bubble({ sessionId: 'movie', role: 'bot', content: '搜索失败：连接超时' }).movieWatchKeyword).toBe('')
        expect(bubble({ sessionId: 'dytt', role: 'bot', content: '未搜索到「Dune」相关资源' }).movieWatchKeyword).toBe('')
        expect(bubble({ sessionId: 'movie', role: 'user', content: '未搜索到「Dune」相关资源' }).movieWatchKeyword).toBe('')
    })

    it('已有无结果回复也可以开启监听', () => {
        expect(bubble({ sessionId: 'movie', role: 'bot', type: 'error',
            content: '未搜索到「沙丘」相关资源，换个片名试试（支持中英文片名）。' }).movieWatchKeyword).toBe('沙丘')
    })

    it('启动监听在请求完成前阻止同关键字重复点击', async () => {
        const store = useMovieSearchWatchStore()
        let complete
        api.StartMovieSearchWatch.mockImplementation(() => new Promise(resolve => { complete = resolve }))
        const first = store.start('Dune')
        await store.start(' dune ')
        expect(api.StartMovieSearchWatch).toHaveBeenCalledTimes(1)
        expect(store.busy.dune).toBe(true)
        complete(watching)
        await first
        expect(store.tasks.dune.status).toBe('watching')
        expect(store.busy.dune).toBe(false)
    })

    it('取消监听通过任务 ID 调用接口，并更新所有同关键字气泡', async () => {
        const store = useMovieSearchWatchStore()
        store.apply(watching)
        api.CancelMovieSearchWatch.mockResolvedValue({ ...watching, status: 'cancelled', version: 2 })
        await store.cancel('dune')
        expect(api.CancelMovieSearchWatch).toHaveBeenCalledWith(7)
        expect(store.tasks.dune.status).toBe('cancelled')
    })

    it('重连读取恢复状态，旧响应不能覆盖已完成的监听', async () => {
        const store = useMovieSearchWatchStore()
        const completed = { ...watching, status: 'found', version: 3, resultCount: 1 }
        store.apply(completed)
        api.GetMovieSearchWatches.mockResolvedValue([watching])
        await store.refresh()
        expect(store.loaded).toBe(true)
        expect(store.tasks.dune.status).toBe('found')
    })

    it('读取失败留下重试入口，提交失败释放按钮且不伪造监听状态', async () => {
        const store = useMovieSearchWatchStore()
        api.GetMovieSearchWatches.mockRejectedValue(new Error('连接失败'))
        await expect(store.refresh()).rejects.toThrow('连接失败')
        expect(store.error).toBe('连接失败')
        expect(store.loaded).toBe(false)
        api.StartMovieSearchWatch.mockRejectedValue(new Error('开启失败'))
        await expect(store.start('Dune')).rejects.toThrow('开启失败')
        expect(store.busy.dune).toBe(false)
        expect(store.tasks.dune).toBeUndefined()
    })

    it('点击按钮启动监听，监听中再次点击不发送请求', async () => {
        const watches = useMovieSearchWatchStore()
        const vm = { watches, keyword: 'Dune', watching: false, busy: false }
        await WatchAction.methods.start.call(vm)
        expect(api.StartMovieSearchWatch).toHaveBeenCalledWith('Dune')
        expect(window.$message.success).toHaveBeenCalledWith('已开启监听，每 30 分钟检查一次')
        vm.watching = true
        await WatchAction.methods.start.call(vm)
        expect(api.StartMovieSearchWatch).toHaveBeenCalledTimes(1)
    })

    it('查询命中与取消同时发生时，正确提示任务已找到资源', async () => {
        const vm = { keyword: 'Dune', watching: true, busy: false,
            watches: { cancel: vi.fn().mockResolvedValue({ ...watching, status: 'found', version: 3 }) } }
        await WatchAction.methods.cancel.call(vm)
        expect(window.$message.success).toHaveBeenCalledWith('该监听已找到资源，请查看系统通知')
        expect(window.$message.success).not.toHaveBeenCalledWith('已取消监听')
    })

    it('实时系统通知只提示一次，重复推送不会重复弹窗', () => {
        const store = useChatStore()
        const frame = { sessionId: 'system', messages: [{ id: 8, sessionId: 'system', role: 'bot', type: 'notify',
            payload: JSON.stringify({ source: '电影港监听', title: '电影港监听已找到资源：Dune' }) }] }
        store.handleMessages(frame)
        store.handleMessages(frame)
        expect(notification.success).toHaveBeenCalledTimes(1)
        expect(store.messagesBySession.system).toHaveLength(1)
    })
})
