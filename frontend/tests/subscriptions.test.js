import { beforeEach, describe, expect, it, vi } from 'vitest'
import Subscriptions from '@/views/subscriptions/index.vue'
import * as chatApi from '@/api/chat'
import * as hlsApi from '@/api/hls'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

// 订阅页通过 window 上的离散 API 弹确认框；测试环境用桩替代
vi.stubGlobal('window', { $dialog: null, $message: { success: vi.fn() } })

vi.mock('@/api/chat', () => ({
    GetSubscriptions: vi.fn(), CheckSubscription: vi.fn(), DeleteSubscription: vi.fn()
}))

vi.mock('@/api/hls', () => ({
    GetHlsSubscriptions: vi.fn(), CheckHlsSubscription: vi.fn(), DeleteHlsSubscription: vi.fn()
}))

/** 只取订阅页需要的方法与数据，不挂载组件：验证「取消订阅即删除」的交互契约。 */
function page(dialog) {
    const vm = { ...Subscriptions.data(), $dialog: dialog, $message: { success: vi.fn(), warning: vi.fn() } }
    for (const [name, method] of Object.entries(Subscriptions.methods)) vm[name] = method.bind(vm)
    // 刷新走网络接口，这里只验证「删除后确实触发刷新」
    vm.reload = vi.fn()
    return vm
}

function dialogStub() {
    const shown = []
    return {
        shown,
        warning(options) { shown.push(options) }
    }
}

const movieItem = { id: 7, enabled: true, source: 'dygangs', sourceType: 'movie', subscriptionKey: 'dygangs:7', title: '电影港剧集', raw: {} }
const hlsItem = { id: 9, enabled: true, source: 'dytt', sourceType: 'hls', subscriptionKey: 'dytt:9', title: '电影天堂剧集', raw: {} }

beforeEach(() => {
    vi.resetAllMocks()
    chatApi.DeleteSubscription.mockResolvedValue(undefined)
    hlsApi.DeleteHlsSubscription.mockResolvedValue({ ok: true })
})

describe('我的订阅：取消订阅即删除', () => {
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
    })

    it('电影天堂的「取消订阅」同样按删除处理，删除后不再保留订阅行', async () => {
        const dialog = dialogStub()
        const vm = page(dialog)
        vm.cancel(hlsItem)

        expect(dialog.shown[0].title).toBe('是否取消订阅')
        await dialog.shown[0].onPositiveClick()
        expect(hlsApi.DeleteHlsSubscription).toHaveBeenCalledWith(9)
        expect(chatApi.DeleteSubscription).not.toHaveBeenCalled()
        expect(vm.reload).toHaveBeenCalled()
    })

    it('已取消 / 已暂停的残留项显示「删除订阅」，确认后直接删除', async () => {
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

    it('不再提供恢复追更入口：页面只有检查、资源列表与删除', () => {
        expect(Subscriptions.methods.resubscribe).toBeUndefined()
        expect(Subscriptions.methods.toggleMenu).toBeUndefined()
        expect(typeof Subscriptions.methods.check).toBe('function')
        expect(typeof Subscriptions.methods.openResources).toBe('function')
        expect(typeof Subscriptions.methods.cancel).toBe('function')
    })
})
