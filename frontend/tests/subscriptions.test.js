import { beforeEach, describe, expect, it, vi } from 'vitest'
import Subscriptions from '@/views/movie/subscriptions.vue'
import * as chatApi from '@/api/chat'
import * as hlsApi from '@/api/hls'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

// 订阅页通过 window 上的离散 API 弹确认框；测试环境用桩替代
vi.stubGlobal('window', { $dialog: null, $message: { success: vi.fn() } })

vi.mock('@/api/chat', () => ({
    GetSubscriptions: vi.fn(), CheckSubscription: vi.fn(), DeleteSubscription: vi.fn(),
    SubscriptionMagnets: vi.fn(), DownloadMagnet: vi.fn()
}))

vi.mock('@/api/hls', () => ({
    GetHlsSubscriptions: vi.fn(), CheckHlsSubscription: vi.fn(),
    EnableHlsSubscription: vi.fn(), DeleteHlsSubscription: vi.fn()
}))

/** 只取订阅页需要的方法与数据，不挂载组件：验证「状态徽标即删除入口」的交互契约。 */
function page(dialog) {
    const vm = { ...Subscriptions.data(), ...Subscriptions.methods, reload: vi.fn() }
    vm.isHls = Subscriptions.methods.isHls.bind(vm)
    window.$dialog = dialog
    return vm
}

function dialogStub() {
    const shown = []
    return {
        shown,
        warning(options) { shown.push(options) }
    }
}

const movieItem = { id: 7, enabled: true, source: 'dygangs', subscriptionKey: 'dygangs:7', title: '电影港剧集' }
const hlsItem = { id: 9, enabled: true, source: 'dytt', subscriptionKey: 'dytt:9', title: '电影天堂剧集' }

beforeEach(() => {
    vi.resetAllMocks()
    chatApi.DeleteSubscription.mockResolvedValue(undefined)
    hlsApi.DeleteHlsSubscription.mockResolvedValue({ ok: true })
})

describe('我的订阅：状态徽标即删除入口', () => {
    it('点击「已订阅」先确认「是否取消订阅」，确认后删除该订阅', async () => {
        const dialog = dialogStub()
        const vm = page(dialog)
        vm.cancel(movieItem)

        expect(dialog.shown).toHaveLength(1)
        expect(dialog.shown[0].title).toBe('是否取消订阅')
        expect(dialog.shown[0].positiveText).toBe('取消订阅')
        expect(chatApi.DeleteSubscription).not.toHaveBeenCalled()

        await dialog.shown[0].onPositiveClick()
        expect(chatApi.DeleteSubscription).toHaveBeenCalledWith(7)
        expect(vm.reload).toHaveBeenCalled()
        expect(vm.deleting).toBe('')

        // 取消订阅即删除，不再走「仅停用、保留基线」与「单独删除按钮」的旧路径
        expect(Subscriptions.methods.unsubscribe).toBeUndefined()
        expect(Subscriptions.methods.remove).toBeUndefined()
        expect(hlsApi.EnableHlsSubscription).not.toHaveBeenCalled()
    })

    it('电影天堂的暂停追更同样按删除处理，删除后不再保留订阅行', async () => {
        const dialog = dialogStub()
        const vm = page(dialog)
        vm.cancel(hlsItem)

        expect(dialog.shown[0].title).toBe('是否取消订阅')
        await dialog.shown[0].onPositiveClick()
        expect(hlsApi.DeleteHlsSubscription).toHaveBeenCalledWith(9)
        expect(chatApi.DeleteSubscription).not.toHaveBeenCalled()
        expect(vm.reload).toHaveBeenCalled()
    })

    it('已取消 / 已暂停的残留项点徽标可直接删除，不提供恢复入口以外的操作', async () => {
        const dialog = dialogStub()
        const vm = page(dialog)
        vm.cancel({ ...movieItem, enabled: false })

        expect(dialog.shown[0].title).toBe('是否删除订阅')
        await dialog.shown[0].onPositiveClick()
        expect(chatApi.DeleteSubscription).toHaveBeenCalledWith(7)
    })

    it('弹窗点「取消」不会删除任何订阅', async () => {
        const dialog = dialogStub()
        const vm = page(dialog)
        vm.cancel(movieItem)
        expect(dialog.shown[0].negativeText).toBe('取消')
        expect(chatApi.DeleteSubscription).not.toHaveBeenCalled()
        expect(hlsApi.DeleteHlsSubscription).not.toHaveBeenCalled()
    })

    it('恢复入口只在非订阅状态出现：恢复追更走启用接口，不删除订阅', async () => {
        hlsApi.EnableHlsSubscription.mockResolvedValue({})
        const vm = page(dialogStub())
        await vm.resubscribe({ ...hlsItem, enabled: false })

        expect(hlsApi.EnableHlsSubscription).toHaveBeenCalledWith(9, true)
        expect(hlsApi.DeleteHlsSubscription).not.toHaveBeenCalled()
    })
})
