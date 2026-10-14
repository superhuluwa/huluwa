import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { useHlsSubscriptionsStore } from '@/stores/hlsSubscriptions'
import MessageBubble from '@/views/message/components/MessageBubble.vue'
import * as hlsApi from '@/api/hls'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
vi.mock('@/api/hls', () => ({ GetHlsSubscriptions: vi.fn(), QuickSubscribeHls: vi.fn() }))

const dyttCard = (overrides = {}) => ({
    sessionId: 'dytt',
    role: 'bot',
    type: 'image',
    content: 'https://img/cover.jpg',
    contentText: '[42]沙丘（2021 / 美国 / 科幻）',
    payload: JSON.stringify({
        source: 'dytt', id: '42', title: '沙丘', cover: 'https://img/cover.jpg',
        url: 'https://dytt/42', subscribed: true
    }),
    ...overrides
})

/** 只取气泡需要用到的计算属性，不挂载组件（与项目其它测试同一风格） */
function bubble(message, store) {
    const vm = { message, subscribedLocal: null, hlsSubscriptions: store }
    Object.defineProperty(vm, 'payload', { get: () => MessageBubble.computed.payload.call(vm) })
    Object.defineProperty(vm, 'parsedPayload', { get: () => MessageBubble.computed.payload.call(vm) })
    Object.defineProperty(vm, 'isMovieCard', { get: () => MessageBubble.computed.isMovieCard.call(vm) })
    Object.defineProperty(vm, 'isDytt', { get: () => MessageBubble.computed.isDytt.call(vm) })
    Object.defineProperty(vm, 'subscribed', { get: () => MessageBubble.computed.subscribed.call(vm) })
    return vm
}

beforeEach(() => {
    vi.resetAllMocks()
    setActivePinia(createPinia())
})

describe('电影天堂订阅集合：卡片状态与「我的订阅」保持一致', () => {
    it('只收录启用中的订阅作品 ID，暂停 / 删除的行不进入集合', async () => {
        hlsApi.GetHlsSubscriptions.mockResolvedValue([
            { id: 9, resId: '42', enabled: true },
            { id: 10, resId: '43', enabled: false }
        ])
        const store = useHlsSubscriptionsStore()
        await store.refresh()
        expect(store.loaded).toBe(true)
        expect(store.has('42')).toBe(true)
        expect(store.has('43')).toBe(false)
    })

    it('已加载且未过期时不重复请求；被置为过期后重新拉取', async () => {
        hlsApi.GetHlsSubscriptions.mockResolvedValue([{ id: 9, resId: '42', enabled: true }])
        const store = useHlsSubscriptionsStore()
        await store.ensureLoaded()
        await store.ensureLoaded()
        expect(hlsApi.GetHlsSubscriptions).toHaveBeenCalledTimes(1)

        store.invalidate()
        await store.ensureLoaded()
        expect(hlsApi.GetHlsSubscriptions).toHaveBeenCalledTimes(2)
    })

    it('列表请求失败不留下已加载标记，缓存被清空后交由载荷快照兜底', async () => {
        hlsApi.GetHlsSubscriptions.mockRejectedValue(new Error('连接失败'))
        const store = useHlsSubscriptionsStore()
        await expect(store.ensureLoaded()).rejects.toThrow('连接失败')
        expect(store.loaded).toBe(false)
        expect(store.loading).toBe(false)
    })

    it('在「我的订阅」删除后：集合重新拉取，历史卡片不再显示「已订阅」', async () => {
        const store = useHlsSubscriptionsStore()
        // 首次进入会话：卡片载荷写着 subscribed=true，订阅也确实存在
        hlsApi.GetHlsSubscriptions.mockResolvedValue([{ id: 9, resId: '42', enabled: true }])
        await store.ensureLoaded()
        const card = bubble(dyttCard(), store)
        expect(card.subscribed).toBe(true)

        // 在「我的订阅」删除该订阅 → 订阅页把集合置为过期 → 回到会话页重新拉取
        store.invalidate()
        hlsApi.GetHlsSubscriptions.mockResolvedValue([])
        await store.ensureLoaded()
        expect(card.subscribed).toBe(false)
    })

    it('集合尚未加载时退回载荷快照；不是电影天堂卡片时不读集合', async () => {
        hlsApi.GetHlsSubscriptions.mockResolvedValue([])
        const store = useHlsSubscriptionsStore()
        const card = bubble(dyttCard(), store)
        expect(store.loaded).toBe(false)
        expect(card.subscribed).toBe(true)

        await store.ensureLoaded()
        expect(card.subscribed).toBe(false)

        const movieCard = bubble({
            sessionId: 'movie', role: 'bot', type: 'image', content: 'https://img/a.jpg',
            payload: JSON.stringify({
                id: '42', title: '沙丘', cover: 'https://img/a.jpg',
                url: 'https://movie/42', subscribed: true
            })
        }, store)
        expect(movieCard.subscribed).toBe(true)
    })

    it('卡片上订阅成功后同步集合，同作品的其它卡片立刻变为已订阅', async () => {
        hlsApi.GetHlsSubscriptions.mockResolvedValue([])
        const store = useHlsSubscriptionsStore()
        await store.ensureLoaded()
        const card = bubble(dyttCard(), store)
        expect(card.subscribed).toBe(false)

        MessageBubble.methods.onSubscriptionSaved.call({
            subscribedLocal: null,
            isDytt: true,
            parsedPayload: { id: '42', url: 'https://dytt/42' },
            hlsSubscriptions: store,
            $emit: vi.fn()
        }, { subscription: { id: 9, enabled: true } })
        expect(store.has('42')).toBe(true)
        expect(card.subscribed).toBe(true)
    })
})

describe('电影天堂卡片布局契约', () => {
    const source = () => readFileSync(fileURLToPath(new URL(
        '../src/views/message/components/MessageBubble.vue', import.meta.url)), 'utf8')

    it('封面卡片的订阅按钮固定在右上角，且不再出现在底部操作条', () => {
        const template = source().slice(0, source().lastIndexOf('</template>'))
        expect(template).toMatch(/v-if="isDytt && isImage"[\s\S]{0,400}class="subscribe-corner"/)
        const dyttCover = template.slice(template.indexOf('<RichText class="cover-caption"'), template.indexOf('<!-- 视频气泡'))
        const dyttActions = dyttCover.slice(dyttCover.indexOf('<div v-else-if="isDytt"'))
        expect(dyttActions).not.toContain('subscribe-btn')
        expect(dyttActions).toContain('资源列表')
    })

    it('原来那行提示文字已删除，右上角按钮出现时复制工具条让位到左侧', () => {
        const text = source()
        expect(text).not.toContain('新剧集自动下载，供飞牛影视播放')
        expect(text).not.toContain('监听新增剧集并自动下载')
        expect(text).toMatch(/'has-corner-action': \(isMovie \|\| isDytt\) && isImage/)
        expect(text).toMatch(/\.bubble\.has-corner-action \.bubble-tools\s*\{[\s\S]*?right:\s*auto;[\s\S]*?left:\s*8px;/)
    })

    it('无封面（纯文本）卡片仍保留操作条订阅按钮，保证任何卡片都能订阅', () => {
        const template = source().slice(0, source().lastIndexOf('</template>'))
        const textBranch = template.slice(template.indexOf('<!-- 电影港卡片：订阅 + 磁力 + 订阅设置 -->'))
        const dyttText = textBranch.slice(textBranch.lastIndexOf('<div v-else-if="isDytt"'))
        expect(dyttText).toContain('onHlsSubscribe')
    })
})
