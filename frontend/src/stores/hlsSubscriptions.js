import { defineStore } from 'pinia'
import { GetHlsSubscriptions } from '@/api/hls'

/**
 * 电影天堂订阅集合（按作品 resId）：
 * 搜索结果卡片载荷里的 subscribed 是「检索当时」的快照，之后在「我的订阅」里
 * 取消 / 恢复都不会回写历史消息，导致卡片状态与真实订阅长期不一致。
 * 这里保存一份订阅接口的实时集合，会话页渲染卡片时以它为准（载荷只作首屏兜底）。
 */
/**
 * 在途请求放在模块作用域：Promise 不参与响应式（放进 state 会被 Pinia 代理包装），
 * 同时天然做到「同一时刻只有一个在途请求，后到的调用复用同一个 promise」。
 */
let inflight = null

export const useHlsSubscriptionsStore = defineStore('hls-subscriptions', {
    state: () => ({
        /** 启用中的采集作品 ID 集合（字符串，与卡片载荷 id 同形） */
        resIds: [],
        loaded: false,
        loading: false,
        /** 数据已过期（订阅页增删改后置位）：ensureLoaded 时强制重取 */
        stale: true,
        /** 每次成功刷新自增：卡片据此把本地乐观状态让位给实时状态 */
        version: 0
    }),
    getters: {
        total: (state) => state.resIds.length
    },
    actions: {
        /** 拉取实时订阅集合；并发去重：后到的调用复用同一个在途 promise，await 后拿到的一定是最新状态 */
        async refresh() {
            if (inflight) return inflight
            inflight = (async () => {
                this.loading = true
                try {
                    const rows = await GetHlsSubscriptions()
                    if (!Array.isArray(rows)) throw new Error('订阅状态返回格式异常')
                    this.resIds = rows
                        .filter((item) => item && item.enabled)
                        .map((item) => String(item.resId || ''))
                        .filter(Boolean)
                    this.loaded = true
                    this.stale = false
                    this.version += 1
                } finally {
                    this.loading = false
                    inflight = null
                }
            })()
            return inflight
        },
        /** 未加载或已过期时才拉取（进入会话 / 返回消息中心时调用，避免每次切换都请求） */
        async ensureLoaded() {
            if (!this.loaded || this.stale) await this.refresh()
        },
        /** 订阅页增删改后标记过期：下次进入会话页重新拉取真实状态 */
        invalidate() {
            this.stale = true
        },
        /** 清空缓存（退出登录等场景） */
        reset() {
            this.resIds = []
            this.loaded = false
            this.loading = false
            this.stale = true
        },
        has(resId) {
            const key = String(resId || '')
            return !!key && this.resIds.includes(key)
        },
        /** 卡片上即时订阅成功时同步集合，避免同一会话内出现「已订阅」与集合不一致 */
        mark(resId, enabled) {
            const key = String(resId || '')
            if (!key) return
            const known = this.resIds.includes(key)
            if (enabled && !known) {
                this.resIds = [...this.resIds, key]
            } else if (!enabled && known) {
                this.resIds = this.resIds.filter((item) => item !== key)
            }
        }
    }
})
