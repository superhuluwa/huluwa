<template>
    <div class="subs-page">
        <div class="subs-head">
            <span>共 {{ rows.length }} 个订阅 · 按加入时间排列</span>
            <n-button size="small" :loading="loading" @click="reload">刷新</n-button>
        </div>
        <n-spin :show="loading">
            <div v-if="loadError" class="error">{{ loadError }}</div>
            <div v-if="!rows.length && !loadError" class="empty">
                还没有订阅。到「消息中心 → 电影港 / 电影天堂」搜索片名，在结果卡片上添加订阅。
            </div>
            <div v-for="item in rows" :key="item.subscriptionKey" class="sub-card" :class="{ off: !item.enabled }">
                <div class="card-main">
                    <img v-if="item.cover" class="cover" :src="item.cover" alt="封面" />
                    <div v-else class="cover fallback"><i :class="isHls(item) ? 'fa-solid fa-circle-play' : 'fa-solid fa-film'"></i></div>
                    <div class="info">
                        <div class="title-row">
                            <span class="title">{{ item.title }}</span>
                            <span class="badge source" :class="{ dytt: isHls(item) }">{{ isHls(item) ? '电影天堂' : '电影港' }}</span>
                            <span v-if="item.enabled" class="badge on">订阅中</span>
                            <span v-else class="badge off">{{ isHls(item) ? '已暂停' : '已取消' }}</span>
                        </div>
                        <div class="meta">{{ metaOf(item) }}</div>
                        <a v-if="item.detailUrl" class="link" :href="item.detailUrl" target="_blank" rel="noreferrer">{{ item.detailUrl }}</a>
                        <div class="stat">
                            <template v-if="isHls(item)">
                                <span><i class="fa-solid fa-download"></i> 离线剧集下载</span>
                                <span><i class="fa-solid fa-list"></i> 已记录 {{ item.episodeCount }} 集</span>
                                <span>每 {{ item.intervalMinutes }} 分钟检查 · 从第 {{ item.fromEpisode }} 集开始</span>
                            </template>
                            <template v-else>
                                <span><i class="fa-solid fa-download"></i> {{ item.downloader === 'xunlei' ? '迅雷' : 'qBittorrent' }}</span>
                                <span><i class="fa-solid fa-magnet"></i> 已记录 {{ item.magnetCount }} 条磁力</span>
                            </template>
                            <span v-if="item.createdAt"><i class="fa-regular fa-calendar-plus"></i> 加入于 {{ item.createdAt }}</span>
                            <span v-if="item.lastCheckAt"><i class="fa-regular fa-clock"></i> 上次巡检 {{ item.lastCheckAt }}</span>
                            <span v-if="item.nextCheckAt && item.enabled"><i class="fa-regular fa-calendar"></i> 下次 {{ item.nextCheckAt }}</span>
                        </div>
                        <div v-if="item.lastError" class="error">
                            <i class="fa-solid fa-triangle-exclamation"></i> 最近一次巡检失败：{{ item.lastError }}
                        </div>
                    </div>
                </div>
                <div class="card-actions">
                    <n-button size="tiny" @click="openSettings(item)">
                        <template #icon><i class="fa-solid fa-sliders"></i></template>
                        订阅设置
                    </n-button>
                    <n-button v-if="item.enabled" size="tiny" :loading="checking === item.subscriptionKey" @click="check(item)">
                        <template #icon><i class="fa-solid fa-bolt"></i></template>
                        立即检查
                    </n-button>
                    <n-button v-if="!isHls(item)" size="tiny" @click="toggleMagnets(item)">
                        <template #icon><i class="fa-solid fa-list"></i></template>
                        {{ magnetsOpen === item.subscriptionKey ? '收起磁力' : '查看磁力' }}
                    </n-button>
                    <n-button v-if="item.enabled" size="tiny" @click="unsubscribe(item)">
                        <template #icon><i class="fa-regular fa-bell-slash"></i></template>
                        {{ isHls(item) ? '暂停追更' : '取消订阅' }}
                    </n-button>
                    <n-button v-else size="tiny" type="primary" ghost @click="resubscribe(item)">
                        <template #icon><i class="fa-regular fa-bell"></i></template>
                        {{ isHls(item) ? '恢复追更' : '重新订阅' }}
                    </n-button>
                    <n-button v-if="!isHls(item)" size="tiny" type="error" ghost @click="remove(item)">
                        <template #icon><i class="fa-solid fa-trash-can"></i></template>
                        删除
                    </n-button>
                </div>
                <div v-if="!isHls(item) && magnetsOpen === item.subscriptionKey" class="magnet-panel">
                    <div v-if="!magnets.length" class="magnet-empty">暂无磁力记录</div>
                    <div v-for="m in magnets" :key="m.id" class="magnet-row" :class="{ downloaded: m.downloaded }">
                        <span class="magnet-quality">{{ m.quality }}</span>
                        <span v-if="m.episode" class="magnet-ep">第 {{ m.episode }} 集</span>
                        <span class="magnet-title">{{ m.title || '（无标题）' }}</span>
                        <span v-if="m.baseline" class="magnet-tag">基线</span>
                        <span class="magnet-time">{{ m.firstSeenAt }}</span>
                        <n-button v-if="m.downloaded" size="tiny" disabled>
                            <template #icon><i class="fa-solid fa-check"></i></template>
                            已下载
                        </n-button>
                        <n-button v-else size="tiny" type="primary" :loading="downloading === m.id"
                            @click="downloadMagnet(m)">
                            <template #icon><i class="fa-solid fa-download"></i></template>
                            {{ item.downloader === 'xunlei' ? '迅雷下载' : 'qBittorrent 下载' }}
                        </n-button>
                    </div>
                </div>
            </div>
        </n-spin>

        <MovieSubscriptionDialog v-model:show="settingsDialog" :movie="settingsMovie"
            :resubscribe="settingsResubscribe" @saved="reload" />
        <HlsResourceDialog v-model:show="hlsDialog" :movie="hlsMovie" @saved="reload" />
    </div>
</template>

<script>
import { NButton, NSpin } from 'naive-ui'
/**
 * 我的订阅：电影港与电影天堂共用一个列表，按首次加入时间升序排列。
 * 来源只在每项信息中标明，操作按该项对应的订阅服务执行。
 */
import {
    GetSubscriptions, CheckSubscription, UnsubscribeMovie,
    DeleteSubscription, SubscriptionMagnets, DownloadMagnet
} from '@/api/chat'
import MovieSubscriptionDialog from '@/views/message/components/MovieSubscriptionDialog.vue'
import HlsResourceDialog from '@/views/message/components/HlsResourceDialog.vue'
import { CheckHlsSubscription, EnableHlsSubscription, GetHlsSubscriptions } from '@/api/hls'
import { mergeSubscriptions } from '@/utils/subscriptionList'

export default {
    name: 'MovieSubscriptions',
    components: {
        HlsResourceDialog,
        MovieSubscriptionDialog,
        NButton,
        NSpin
    },
    data() {
        return {
            loading: false,
            firstActivate: true,
            loadError: '',
            settingsDialog: false,
            settingsMovie: null,
            settingsResubscribe: false,
            hlsDialog: false,
            hlsMovie: null,
            rows: [],
            magnetsOpen: '',
            magnets: [],
            checking: '',
            downloading: 0
        }
    },
    async mounted() {
        await this.reload()
    },
    async activated() {
        if (this.firstActivate) {
            this.firstActivate = false
            return
        }
        await this.reload()
    },
    methods: {
        isHls(item) { return item.source === 'dytt' },
        async reload() {
            this.loading = true
            try {
                const [movie, hls] = await Promise.allSettled([GetSubscriptions(true), GetHlsSubscriptions()])
                const movieRows = movie.status === 'fulfilled' ? movie.value : this.rows.filter(item => !this.isHls(item))
                const hlsRows = hls.status === 'fulfilled' ? hls.value : this.rows.filter(item => this.isHls(item))
                this.rows = mergeSubscriptions(movieRows, hlsRows)
                const failed = []
                if (movie.status === 'rejected') failed.push('电影港')
                if (hls.status === 'rejected') failed.push('电影天堂')
                this.loadError = failed.length ? failed.join('、') + '订阅加载失败，请刷新重试。' : ''
            } finally {
                this.loading = false
            }
        },
        metaOf(item) {
            const parts = (this.isHls(item)
                ? [item.year, '第 ' + item.season + ' 季', item.sourceName]
                : [item.year, item.origin, item.genre]).filter(Boolean)
            return parts.length ? parts.join(' / ') : '—'
        },
        async check(item) {
            this.checking = item.subscriptionKey
            try {
                const result = await (this.isHls(item) ? CheckHlsSubscription(item.id) : CheckSubscription(item.id))
                window.$message?.success(result && result.message ? result.message : '巡检完成')
            } catch (e) {
                // 拦截器已提示
            } finally {
                this.checking = ''
                await this.reload()
            }
        },
        async toggleMagnets(item) {
            if (this.magnetsOpen === item.subscriptionKey) {
                this.magnetsOpen = ''
                return
            }
            this.magnetsOpen = item.subscriptionKey
            this.magnets = []
            const list = await SubscriptionMagnets(item.id)
            if (this.magnetsOpen === item.subscriptionKey) this.magnets = Array.isArray(list) ? list : []
        },
        /** 手动下载订阅库里的一条磁力：与订阅巡检共用后端逻辑（目录 / 剧名 / 通知一致） */
        async downloadMagnet(m) {
            this.downloading = m.id
            try {
                const result = await DownloadMagnet({ magnetId: m.id })
                if (result && result.ok) {
                    window.$message?.success(result.message || '已提交下载')
                    m.downloaded = true
                } else {
                    window.$message?.warning((result && result.message) || '提交失败')
                }
            } catch (e) {
                // 拦截器已提示
            } finally {
                this.downloading = 0
            }
        },
        async unsubscribe(item) {
            if (this.isHls(item)) {
                await EnableHlsSubscription(item.id, false)
                window.$message?.success('已暂停追更，正在下载的剧集会继续完成，本地文件保留')
                await this.reload()
                return
            }
            await UnsubscribeMovie(item.id)
            window.$message?.success('已取消订阅，历史磁力与下载记录仍保留')
            await this.reload()
        },
        openSettings(item = null, resubscribe = false) {
            if (item && this.isHls(item)) {
                this.hlsMovie = item
                this.hlsDialog = true
                return
            }
            this.settingsMovie = item ? {
                id: item.resId, title: item.title, year: item.year, origin: item.origin,
                genre: item.genre, cover: item.cover, url: item.detailUrl
            } : null
            this.settingsResubscribe = resubscribe
            this.settingsDialog = true
        },
        async resubscribe(item) {
            if (this.isHls(item)) {
                await EnableHlsSubscription(item.id, true)
                window.$message?.success('已恢复追更')
                await this.reload()
                return
            }
            this.openSettings(item, true)
        },
        remove(item) {
            window.$dialog?.warning({
                title: '删除订阅',
                content: '删除订阅并清除磁力基线？下载器里已提交的任务不受影响。',
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await DeleteSubscription(item.id)
                    window.$message?.success('已删除订阅')
                    await this.reload()
                }
            })
        }
    }
}
</script>

<style scoped>
.subs-page {
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.subs-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    font-size: 12px;
    color: var(--text-3);
}

.empty {
    padding: 36px 0;
    text-align: center;
    color: var(--text-3);
    font-size: 13px;
}

.sub-card {
    border: 1px solid var(--line);
    border-radius: 12px;
    background: rgba(20, 28, 46, .45);
    padding: 14px;
    margin-bottom: 12px;
}

.sub-card.off {
    opacity: .62;
}

.card-main {
    display: flex;
    gap: 14px;
}

.cover {
    width: 76px;
    height: 104px;
    flex: none;
    object-fit: cover;
    border-radius: 8px;
    background: rgba(148, 163, 184, .08);
}

.cover.fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-3);
    font-size: 22px;
}

.info {
    flex: 1;
    min-width: 0;
}

.title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.title {
    font-size: 15px;
    font-weight: 700;
    color: var(--text);
}

.badge {
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 11px;
    border: 1px solid;
}

.badge.on {
    color: #86EFAC;
    border-color: rgba(74, 222, 128, .45);
    background: rgba(34, 197, 94, .14);
}

.badge.off {
    color: var(--text-3);
    border-color: var(--line-2);
}

.badge.source {
    color: #A5B4FC;
    border-color: rgba(99, 102, 241, .4);
    background: rgba(99, 102, 241, .12);
}

.badge.source.dytt {
    color: #7DD3FC;
    border-color: rgba(14, 165, 233, .4);
    background: rgba(14, 165, 233, .12);
}

.meta {
    margin-top: 3px;
    font-size: 12.5px;
    color: var(--text-2);
}

.link {
    display: inline-block;
    margin-top: 4px;
    font-size: 12px;
    color: var(--accent);
    word-break: break-all;
}

.stat {
    margin-top: 8px;
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
    font-size: 12px;
    color: var(--text-2);
}

.stat i {
    margin-right: 4px;
}

.error {
    margin-top: 6px;
    font-size: 12px;
    color: var(--danger);
}

.card-actions {
    margin-top: 12px;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.magnet-row.downloaded {
    opacity: .62;
}

.magnet-panel {
    margin-top: 12px;
    border-top: 1px dashed var(--line-2);
    padding-top: 10px;
    max-height: 280px;
    overflow-y: auto;
}

.magnet-empty {
    font-size: 12.5px;
    color: var(--text-3);
    padding: 8px 0;
}

.magnet-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 0;
    font-size: 12.5px;
    color: var(--text-2);
}

.magnet-quality {
    flex: none;
    min-width: 56px;
    padding: 1px 6px;
    border-radius: 6px;
    text-align: center;
    font-size: 11px;
    color: #A5B4FC;
    background: rgba(99, 102, 241, .16);
}

.magnet-ep {
    flex: none;
    color: var(--text-3);
    font-size: 11.5px;
}

.magnet-title {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.magnet-tag {
    flex: none;
    padding: 1px 6px;
    border-radius: 6px;
    font-size: 11px;
    color: var(--text-3);
    border: 1px solid var(--line-2);
}

.magnet-time {
    flex: none;
    font-size: 11.5px;
    color: var(--text-3);
}

@media (max-width: 900px) {
    .card-main {
        gap: 10px;
    }

    .cover {
        width: 62px;
        height: 86px;
    }

    .stat {
        gap: 10px;
    }

    .magnet-time {
        display: none;
    }
}
</style>
