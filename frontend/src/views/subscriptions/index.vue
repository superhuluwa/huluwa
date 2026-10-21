<template>
    <ManagementShell kind="subscriptions" title="我的订阅" subtitle="电影港与电影天堂订阅 · 追更状态与资源列表"
        :loading="loading" :busy="busy" @refresh="reload">
        <div class="mgmt-content">
            <p v-if="loadError" class="subs-error">{{ loadError }}</p>

            <div v-if="!rows.length" class="subs-empty">
                <span class="empty-icon"><i class="fa-regular fa-bookmark"></i></span>
                <strong>{{ loading ? '正在同步订阅…' : '还没有订阅' }}</strong>
                <span v-if="!loading">在「功能 → 电影港 / 电影天堂」搜索片名，在结果卡片上订阅即可追更</span>
            </div>

            <article v-for="item in rows" :key="item.subscriptionKey" class="sub-card" :class="{ off: !item.enabled }">
                <div class="sub-main">
                    <img v-if="item.cover" class="poster" :src="item.cover" :alt="item.title" loading="lazy" />
                    <div v-else class="poster fallback">
                        <i :class="isHls(item) ? 'fa-solid fa-circle-play' : 'fa-solid fa-film'"></i>
                    </div>
                    <div class="sub-info">
                        <div class="sub-title-row">
                            <strong>{{ item.title }}</strong>
                            <div class="more-wrap">
                                <button class="icon-btn" type="button" title="管理订阅" @click="toggleMenu(item.subscriptionKey)">
                                    <i class="fa-solid fa-ellipsis"></i>
                                </button>
                                <div v-if="menuKey === item.subscriptionKey" class="more-menu">
                                    <button type="button" :disabled="busy || !item.enabled" @click="check(item)">立即检查</button>
                                    <button type="button" :disabled="busy" @click="cancel(item)">
                                        {{ item.enabled ? '取消订阅' : '删除订阅' }}
                                    </button>
                                    <button type="button" @click="openResources(item)">资源列表</button>
                                    <button v-if="!item.enabled" type="button" :disabled="busy" @click="resubscribe(item)">
                                        {{ isHls(item) ? '恢复追更' : '重新订阅' }}
                                    </button>
                                </div>
                            </div>
                        </div>
                        <p class="sub-meta">{{ [item.year, item.genre, sourceLabel(item)].filter(Boolean).join(' · ') }}</p>
                        <div class="sub-tags">
                            <span class="tag">{{ isHls(item) ? `已收录 ${item.count || 0} 集` : `${item.count || 0} 条磁力` }}</span>
                            <span class="tag outline" :class="{ paused: !item.enabled }">
                                {{ item.enabled ? '追更中' : (isHls(item) ? '已暂停' : '已取消') }}
                            </span>
                        </div>
                    </div>
                </div>
                <p v-if="item.lastCheckAt" class="sub-line">上次检查：{{ item.lastCheckAt }}</p>
                <p v-if="item.enabled && item.nextCheckAt" class="sub-line">下次检查：{{ item.nextCheckAt }}</p>
                <p v-if="item.lastError" class="sub-error">{{ item.lastError }}</p>
                <div v-if="magnetsOpen === item.subscriptionKey" class="magnet-panel">
                    <p v-if="!magnets.length" class="sub-line">暂无磁力记录</p>
                    <div v-for="magnet in magnets" :key="magnet.id" class="magnet-row">
                        <span class="magnet-quality">{{ magnet.quality }}</span>
                        <span class="magnet-title">{{ magnet.title || `第 ${magnet.episode} 集` }}</span>
                        <button v-if="!magnet.downloaded" type="button" class="mini-btn" :disabled="downloading === magnet.id"
                            @click="downloadMagnet(magnet)">
                            {{ downloading === magnet.id ? '提交中…' : '下载' }}
                        </button>
                        <span v-else class="magnet-done">已下载</span>
                    </div>
                </div>
            </article>
        </div>

        <HlsResourceDialog v-model:show="hlsDialog" :movie="hlsMovie" @saved="reload" />
        <PlaySourceDialog v-model:show="playDialog" :movie="playMovie" :title="playMovie ? playMovie.title : ''" />
        <MagnetListDialog v-model:show="magnetDialog.show" :url="magnetDialog.url" :title="magnetDialog.title"
            @downloaded="reload" />
    </ManagementShell>
</template>

<script>
/**
 * 我的订阅（系统会话，对齐安卓 ManagementSessionScreen + SubscriptionsScreen）：
 *  - 电影港（磁力订阅）与电影天堂（离线剧集订阅）合并为一个列表，按加入时间排列；
 *  - 卡片展示封面、来源、追更状态、已收录数量与巡检时间；
 *  - 「…」菜单提供立即检查、取消 / 删除、资源列表、恢复追更；
 *  - 取消订阅即删除（与网页版既有约定一致，测试也覆盖了这一契约）。
 *  - 原底部「订阅」标签已移除，本页作为系统会话整屏打开，返回键回到系统会话列表。
 */
import ManagementShell from '@/components/main/management-shell.vue'
import { useHlsSubscriptionsStore } from '@/stores/hlsSubscriptions'
import {
    GetSubscriptions, CheckSubscription, DeleteSubscription, SubscriptionMagnets,
    DownloadMagnet, SubscribeMovie
} from '@/api/chat'
import {
    GetHlsSubscriptions, CheckHlsSubscription, DeleteHlsSubscription, EnableHlsSubscription
} from '@/api/hls'
import HlsResourceDialog from '@/views/message/components/HlsResourceDialog.vue'
import PlaySourceDialog from '@/views/message/components/PlaySourceDialog.vue'
import MagnetListDialog from '@/views/message/components/MagnetListDialog.vue'

export default {
    name: 'SubscriptionsPage',
    components: { ManagementShell, HlsResourceDialog, PlaySourceDialog, MagnetListDialog },
    setup() {
        return { hlsStore: useHlsSubscriptionsStore() }
    },
    data() {
        return {
            loading: false,
            busy: false,
            deleting: '',
            checking: '',
            downloading: 0,
            loadError: '',
            rows: [],
            menuKey: '',
            magnetsOpen: '',
            magnets: [],
            hlsDialog: false,
            playDialog: false,
            hlsMovie: null,
            playMovie: null,
            magnetDialog: { show: false, url: '', title: '' }
        }
    },
    mounted() {
        document.addEventListener('click', this.onDocumentClick)
        this.reload()
    },
    activated() {
        this.reload()
    },
    beforeUnmount() {
        document.removeEventListener('click', this.onDocumentClick)
    },
    methods: {
        isHls(item) {
            return item.source === 'dytt'
        },
        sourceLabel(item) {
            return this.isHls(item) ? '电影天堂' : '电影港'
        },
        async reload() {
            this.loading = true
            try {
                const [movie, hls] = await Promise.allSettled([GetSubscriptions(true), GetHlsSubscriptions()])
                const failed = []
                const rows = []
                if (movie.status === 'fulfilled') {
                    for (const item of Array.isArray(movie.value) ? movie.value : []) {
                        rows.push(this.normalizeMovie(item))
                    }
                } else {
                    failed.push('电影港')
                }
                if (hls.status === 'fulfilled') {
                    for (const item of Array.isArray(hls.value) ? hls.value : []) {
                        rows.push(this.normalizeHls(item))
                    }
                } else {
                    failed.push('电影天堂')
                }
                this.rows = rows.sort((left, right) => String(left.createdAt).localeCompare(String(right.createdAt)))
                this.loadError = failed.length ? `${failed.join('、')}订阅加载失败，请刷新重试。` : ''
                // 同步卡片订阅集合与安卓保持一致（电影天堂卡片「已订阅」状态）
                this.hlsStore.refresh().catch(() => {})
            } finally {
                this.loading = false
            }
        },
        normalizeMovie(item) {
            return {
                id: item.id,
                source: 'dygangs',
                sourceType: 'movie',
                subscriptionKey: `dygangs:${item.id}`,
                title: item.title || '未命名订阅',
                cover: item.cover || '',
                year: item.year || '',
                genre: item.genre || '',
                enabled: item.enabled !== false,
                count: item.magnetCount || 0,
                lastCheckAt: item.lastCheckAt || '',
                nextCheckAt: item.nextCheckAt || '',
                lastError: item.lastError || '',
                createdAt: item.createdAt || '',
                detailUrl: item.detailUrl || '',
                downloader: item.downloader || 'qb',
                raw: item
            }
        },
        normalizeHls(item) {
            return {
                id: item.id,
                source: 'dytt',
                sourceType: 'hls',
                subscriptionKey: `dytt:${item.id}`,
                title: item.title || '未命名订阅',
                cover: item.cover || '',
                year: item.year || '',
                genre: '',
                enabled: item.enabled !== false,
                count: item.episodeCount || 0,
                lastCheckAt: item.lastCheckAt || '',
                nextCheckAt: item.nextCheckAt || '',
                lastError: item.lastError || '',
                createdAt: item.createdAt || '',
                detailUrl: item.detailUrl || '',
                apiUrl: item.apiUrl || '',
                resId: item.resId || '',
                raw: item
            }
        },
        toggleMenu(key) {
            this.menuKey = this.menuKey === key ? '' : key
        },
        onDocumentClick(event) {
            if (!event.target.closest?.('.more-wrap')) {
                this.menuKey = ''
            }
        },
        async check(item) {
            this.menuKey = ''
            this.checking = item.subscriptionKey
            this.busy = true
            try {
                const result = item.sourceType === 'hls'
                    ? await CheckHlsSubscription(item.id)
                    : null
                if (!result) {
                    await CheckSubscription(item.id)
                }
                this.$message.success((result && result.message) || '已触发一次订阅检查')
                await this.reload()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.checking = ''
                this.busy = false
            }
        },
        /** 状态徽标即删除入口（与既有交互契约一致）：取消订阅 = 删除订阅 */
        cancel(item) {
            this.menuKey = ''
            const enabled = item.enabled
            this.$dialog.warning({
                title: enabled ? '是否取消订阅' : '是否删除订阅',
                content: enabled
                    ? `确认取消「${item.title}」并删除该订阅记录？已下载文件会保留。`
                    : `确认删除「${item.title}」的订阅记录？`,
                positiveText: enabled ? '取消订阅' : '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.deleting = item.subscriptionKey
                    try {
                        if (item.sourceType === 'hls') {
                            await DeleteHlsSubscription(item.id)
                        } else {
                            await DeleteSubscription(item.id)
                        }
                        this.$message.success('订阅已删除')
                        await this.reload()
                    } catch (error) {
                        // 拦截器已提示
                    } finally {
                        this.deleting = ''
                    }
                }
            })
        },
        async resubscribe(item) {
            this.menuKey = ''
            this.busy = true
            try {
                if (item.sourceType === 'hls') {
                    await EnableHlsSubscription(item.id, true)
                } else {
                    const raw = item.raw || {}
                    await SubscribeMovie({
                        id: raw.resId || item.id,
                        title: item.title,
                        year: item.year,
                        origin: raw.origin || '',
                        genre: item.genre,
                        cover: item.cover,
                        url: raw.detailUrl || item.detailUrl,
                        downloader: item.downloader
                    })
                }
                this.$message.success('已恢复追更')
                await this.reload()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.busy = false
            }
        },
        openResources(item) {
            this.menuKey = ''
            if (item.sourceType === 'hls') {
                this.hlsMovie = { id: item.resId || item.id, title: item.title, apiUrl: item.apiUrl, detailUrl: item.detailUrl }
                this.hlsDialog = true
                return
            }
            const raw = item.raw || {}
            if (raw.playSources) {
                this.playMovie = raw
                this.playDialog = true
                return
            }
            this.magnetDialog = {
                show: true,
                url: raw.detailUrl || item.detailUrl,
                title: item.title
            }
        },
        async downloadMagnet(magnet) {
            this.downloading = magnet.id
            try {
                await DownloadMagnet({ magnetId: magnet.id })
                this.$message.success('已提交下载')
                magnet.downloaded = true
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.downloading = 0
            }
        }
    }
}
</script>

<style scoped>
/* 内容留白与卡片样式交给全局 .mgmt-content / .panel 体系，这里只保留订阅卡自身 */
.subs-error {
    margin: 0 0 12px;
    padding: 9px 12px;
    border-radius: 12px;
    background: #F8F0E4;
    color: var(--amber);
    font-size: 12.5px;
}

.subs-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 64px 20px;
    text-align: center;
}

.empty-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 62px;
    height: 62px;
    margin-bottom: 8px;
    border-radius: 20px;
    background: var(--soft-green);
    color: var(--pine);
    font-size: 26px;
}

.subs-empty strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
}

.subs-empty span:last-child {
    font-size: 12.5px;
    color: var(--muted);
    line-height: 1.6;
}

/* 订阅卡：白卡 + 海报 + 信息（对齐安卓 SubscriptionCard） */
.sub-card {
    margin-bottom: 12px;
    padding: 16px;
    border-radius: 20px;
    border: 1px solid var(--line);
    background: #fff;
}

.sub-card.off {
    background: #FBFCFA;
}

.sub-main {
    display: flex;
    gap: 13px;
}

.poster {
    flex: none;
    width: 70px;
    height: 100px;
    border-radius: 11px;
    object-fit: cover;
    background: linear-gradient(160deg, #C7D6B7, #577A62);
}

.poster.fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 24px;
}

.sub-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.sub-title-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
}

.sub-title-row strong {
    flex: 1;
    min-width: 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.icon-btn {
    flex: none;
    width: 34px;
    height: 34px;
    margin: -6px -6px 0 0;
    border: 0;
    border-radius: 12px;
    background: none;
    color: var(--muted);
    font-size: 14px;
    cursor: pointer;
}

.icon-btn:hover {
    background: var(--soft-green);
    color: var(--pine);
}

.more-wrap {
    position: relative;
    flex: none;
}

.more-menu {
    position: absolute;
    top: 34px;
    right: 0;
    z-index: 30;
    min-width: 150px;
    padding: 6px;
    display: flex;
    flex-direction: column;
    border-radius: 14px;
    background: #fff;
    border: 1px solid var(--line);
    box-shadow: 0 12px 32px rgba(36, 61, 50, .16);
}

.more-menu button {
    padding: 10px 12px;
    border: 0;
    border-radius: 10px;
    background: none;
    color: var(--ink);
    font-family: inherit;
    font-size: 13px;
    text-align: left;
    cursor: pointer;
    white-space: nowrap;
}

.more-menu button:hover:not(:disabled) {
    background: var(--soft-green);
}

.more-menu button:disabled {
    opacity: .45;
    cursor: default;
}

.sub-meta {
    margin: 0;
    font-size: 12px;
    color: var(--muted);
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.sub-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 2px;
}

.tag {
    padding: 4px 8px;
    border-radius: 7px;
    background: rgba(32, 108, 84, .08);
    color: var(--pine);
    font-size: 10px;
    font-weight: 500;
}

.tag.outline {
    background: transparent;
    border: 1px solid var(--line);
    color: var(--muted);
}

.tag.outline.paused {
    color: var(--amber);
    border-color: #EDDFC8;
}

.sub-line {
    margin: 10px 0 0;
    font-size: 11px;
    color: var(--muted);
}

.sub-error {
    margin: 8px 0 0;
    font-size: 12px;
    color: var(--danger);
    line-height: 1.55;
}

.magnet-panel {
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid var(--line);
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.magnet-row {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
}

.magnet-quality {
    flex: none;
    color: var(--pine);
    font-weight: 600;
}

.magnet-title {
    flex: 1;
    min-width: 0;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.mini-btn {
    flex: none;
    padding: 6px 12px;
    border: 0;
    border-radius: 10px;
    background: var(--pine);
    color: #fff;
    font-family: inherit;
    font-size: 12px;
    cursor: pointer;
}

.mini-btn:disabled {
    opacity: .6;
}

.magnet-done {
    flex: none;
    color: var(--muted);
    font-size: 11px;
}
</style>
