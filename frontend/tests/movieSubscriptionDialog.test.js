import { beforeEach, describe, expect, it, vi } from 'vitest'
import Dialog from '@/views/message/components/MovieSubscriptionDialog.vue'
import MessageBubble from '@/views/message/components/MessageBubble.vue'
import * as api from '@/api/chat'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

vi.mock('@/api/chat', () => ({
    GetMovieDownloadSettings: vi.fn(), SaveMovieDownloadSettings: vi.fn(),
    GetMovieSubscriptionStatus: vi.fn(), UpdateMovieSubscriptionSettings: vi.fn(),
    SubscribeMovie: vi.fn(), PreviewMagnets: vi.fn(), DownloadMagnet: vi.fn(),
    GetMovieSearchWatches: vi.fn(), StartMovieSearchWatch: vi.fn(), CancelMovieSearchWatch: vi.fn()
}))

function dialog(movie, resubscribe = false) {
    const vm = { ...Dialog.data(), ...Dialog.methods, movie, resubscribe, $emit: vi.fn(), $message: { success: vi.fn() } }
    Object.defineProperty(vm, 'editing', { get: () => Dialog.computed.editing.call(vm) })
    Object.defineProperty(vm, 'visible', { set: value => vm.$emit('update:show', value) })
    return vm
}

beforeEach(() => {
    vi.resetAllMocks()
    api.GetMovieDownloadSettings.mockResolvedValue({ downloader: 'qb', saveRoot: '/vol1/1000/downloads' })
    api.GetMovieSubscriptionStatus.mockResolvedValue({ subscription: null })
    api.SubscribeMovie.mockResolvedValue({ subscription: { id: 7, enabled: true }, message: '已订阅' })
    api.SaveMovieDownloadSettings.mockResolvedValue({ downloader: 'xunlei' })
})

describe('电影港订阅工具选择', () => {
    it('点击订阅先弹窗，确认后仅绑定本剧的下载工具', async () => {
        const bubble = { subscribed: false, busy: false, parsedPayload: { url: 'https://movie/detail' }, subscriptionDialog: false }
        MessageBubble.methods.onSubscribe.call(bubble)
        expect(bubble.subscriptionDialog).toBe(true)
        expect(api.SubscribeMovie).not.toHaveBeenCalled()
        const vm = dialog({ title: '剧名 A/B', url: 'https://movie/detail' }, true)
        await vm.load()
        expect(vm.downloader).toBe('qb')
        expect(vm.savePath).toBe('/vol1/1000/downloads/剧名 A_B')
        vm.downloader = 'xunlei'
        await vm.save()
        expect(api.SubscribeMovie).toHaveBeenCalledWith({ title: '剧名 A/B', url: 'https://movie/detail', downloader: 'xunlei' })
        expect(api.SaveMovieDownloadSettings).not.toHaveBeenCalled()
        expect(vm.$emit).toHaveBeenCalledWith('saved', expect.objectContaining({ subscription: expect.any(Object) }))
    })

    it('修改已有订阅保留本剧选择，使用设置接口而不重新订阅', async () => {
        api.GetMovieSubscriptionStatus.mockResolvedValue({ subscription: { id: 7, title: '正式剧名', downloader: 'xunlei', enabled: true } })
        api.UpdateMovieSubscriptionSettings.mockResolvedValue({ id: 7, title: '正式剧名', downloader: 'qb', enabled: true })
        const vm = dialog({ title: '别名', url: 'https://movie/detail' })
        await vm.load()
        expect(vm.downloader).toBe('xunlei')
        expect(vm.savePath).toBe('/vol1/1000/downloads/正式剧名')
        vm.downloader = 'qb'
        await vm.save()
        expect(api.UpdateMovieSubscriptionSettings).toHaveBeenCalledWith(7, 'qb')
        expect(api.SubscribeMovie).not.toHaveBeenCalled()
        expect(api.SaveMovieDownloadSettings).not.toHaveBeenCalled()
    })

    it('加载失败后禁止提交，取消不会订阅', async () => {
        api.GetMovieDownloadSettings.mockRejectedValue(new Error('连接失败'))
        const vm = dialog({ title: '剧名', url: 'https://movie/detail' })
        await vm.load()
        await vm.save()
        expect(vm.loaded).toBe(false)
        expect(api.SubscribeMovie).not.toHaveBeenCalled()
        vm.visible = false
        expect(vm.$emit).toHaveBeenCalledWith('update:show', false)
    })
})
