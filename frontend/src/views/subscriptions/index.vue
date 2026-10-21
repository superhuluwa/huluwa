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
                            <!-- 取消订阅即删除订阅记录（已下载文件保留）；历史遗留的已暂停项显示「删除订阅」 -->
                            <button class="unsub-btn" type="button" :disabled="busy || deleting === item.subscriptionKey"
                                @click="cancel(item)">
                                {{ item.enabled ? '取消订阅' : '删除订阅' }}
                            </button>
                        </div>
                        <!-- 来源 + 收录条数合并成一行（原年份 / 类型与下方标签行一并移除），与安卓卡片保持一致 -->
                        <p class="sub-meta">{{ metaLine(item) }}</p>
                        <p v-if="item.lastError" class="sub-error">{{ item.lastError }}</p>
                        <!-- 立即检查与资源列表紧跟在「电影港 · 已收录 N 条」下方，不再横跨卡片底部 -->
                        <div class="sub-actions">
                            <button class="sub-action" type="button" :disabled="busy || !item.enabled"
                                @click="check(item)">
                                <i class="fa-solid fa-rotate"></i>
                                {{ checking === item.subscriptionKey ? '检查中…' : '立即检查' }}
                            </button>
                            <button class="sub-action" type="button" @click="openResources(item)">
                                <i class="fa-solid fa-list"></i>资源列表
                            </button>
                        </div>
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
 *  - 卡片展示封面、来源与已收录条数（巡检时间不再展示，只保留失败原因）；
 *  - 卡片右上角为「取消订阅」（取消即删除记录，已下载文件保留），
 *    原「…」菜单里的立即检查与资源列表改为卡片底部按钮；
 *  - 不再提供暂停 / 恢复追更：历史遗留的已暂停项只保留「删除订阅」。
 *  - 原底部「订阅」标签已移除，本页作为系统会话整屏打开，返回键回到系统会话列表。
 */
import ManagementShell from '@/components/main/management-shell.vue'
import { useHlsSubscriptionsStore } from '@/stores/hlsSubscriptions'
import { GetSubscriptions, CheckSubscription, DeleteSubscription } from '@/api/chat'
import { GetHlsSubscriptions, CheckHlsSubscription, DeleteHlsSubscription } from '@/api/hls'
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
            loadError: '',
            rows: [],
            hlsDialog: false,
            playDialog: false,
            hlsMovie: null,
            playMovie: null,
            magnetDialog: { show: false, url: '', title: '' }
        }
    },
    mounted() {
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
        /** 剧名下方的一行摘要：来源 + 收录条数（历史遗留的暂停项补一个状态），与安卓卡片一致 */
        metaLine(item) {
            return [this.sourceLabel(item), `已收录 ${item.count || 0} 条`, item.enabled ? '' : '已暂停']
                .filter(Boolean).join(' · ')
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
        async check(item) {
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
        /** 右上角「取消订阅」即删除入口：取消订阅 = 删除订阅记录 */
        cancel(item) {
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
        openResources(item) {
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

/* 订阅卡：白卡 + 海报 + 信息（对齐安卓 SubscriptionCard，紧凑布局） */
.sub-card {
    margin-bottom: 10px;
    padding: 12px;
    border-radius: 18px;
    border: 1px solid var(--line);
    background: #fff;
}

.sub-card.off {
    background: #FBFCFA;
}

.sub-main {
    display: flex;
    gap: 11px;
}

.poster {
    flex: none;
    width: 56px;
    height: 80px;
    border-radius: 9px;
    object-fit: cover;
    background: linear-gradient(160deg, #C7D6B7, #577A62);
}

.poster.fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 20px;
}

.sub-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 5px;
}

.sub-title-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
}

.sub-title-row strong {
    flex: 1;
    min-width: 0;
    font-size: 14.5px;
    font-weight: 600;
    color: var(--ink);
    line-height: 1.35;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* 右上角「取消订阅」：次要文字按钮，删除类操作用警示色区分 */
.unsub-btn {
    flex: none;
    margin: -2px -4px 0 0;
    padding: 3px 7px;
    border: 0;
    border-radius: 9px;
    background: none;
    color: var(--danger);
    font-family: inherit;
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
    cursor: pointer;
    transition: background .15s;
}

.unsub-btn:hover:not(:disabled) {
    background: rgba(180, 68, 82, .08);
}

.unsub-btn:disabled {
    opacity: .45;
    cursor: default;
}

/* 剧名下方摘要：来源 + 收录条数，与安卓 bodySmall 同字号 */
.sub-meta {
    margin: 0;
    font-size: 12px;
    color: var(--muted);
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.sub-error {
    margin: 5px 0 0;
    font-size: 11.5px;
    color: var(--danger);
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

/* 卡片内操作条：紧跟在「电影港 · 已收录 N 条」下方，等宽两个按钮 */
.sub-actions {
    display: flex;
    gap: 7px;
    margin-top: 8px;
}

.sub-action {
    flex: 1;
    min-width: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    height: 32px;
    padding: 0 8px;
    border: 1px solid var(--line);
    border-radius: 9px;
    background: #fff;
    color: var(--pine);
    font-family: inherit;
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
    cursor: pointer;
    transition: background .15s, border-color .15s;
}

.sub-action:hover:not(:disabled) {
    background: var(--soft-green);
    border-color: rgba(32, 108, 84, .35);
}

.sub-action:disabled {
    opacity: .45;
    cursor: default;
}
</style>
