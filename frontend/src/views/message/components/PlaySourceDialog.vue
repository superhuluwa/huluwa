<template>
    <n-modal v-model:show="visible" preset="card" style="width: 860px; max-width: calc(100vw - 32px)"
        :mask-closable="false">
        <template #header>
            <div class="ps-head">
                <i class="fa-solid fa-circle-play"></i>
                <span class="ps-title">{{ title || '资源列表' }}</span>
                <span v-if="total" class="ps-count">可复制 {{ total }} 条</span>
            </div>
        </template>

        <div class="ps-sub">
            <span class="ps-tip">
                <i class="fa-solid fa-circle-info"></i>
                点击「复制」拿到 m3u8 直链，粘贴到播放器即可观看
            </span>
            <span v-if="sortedSources.length > 1" class="ps-tip">
                <i class="fa-solid fa-shuffle"></i>
                共 {{ sortedSources.length }} 条线路，dyttm3u8 已置顶
            </span>
        </div>

        <n-spin :show="loadingDetail">
            <div v-if="sortedSources.length > 1" class="ps-tabs">
                <button v-for="(s, i) in sortedSources" :key="s.name + i" type="button" class="ps-tab"
                    :class="{ active: i === activeIndex }" @click="activeIndex = i">
                    <i class="fa-solid fa-tv"></i>{{ s.name }}
                    <span class="ps-tab-num">{{ (s.items || []).length }}<template v-if="s.total > (s.items || []).length">/{{ s.total }}</template></span>
                </button>
            </div>

            <div v-if="!currentItems.length" class="ps-empty">该资源没有解析到可用地址</div>
            <div v-else class="ps-list">
                <div v-for="(item, index) in currentItems" :key="rowKey(item) + '@' + index" class="ps-row">
                    <span class="ps-ep">{{ item.title }}</span>
                    <span class="ps-url" :title="item.url">{{ item.url }}</span>
                    <n-button size="tiny" type="primary" @click="copy(item.url)">
                        <template #icon><i class="fa-regular fa-copy"></i></template>
                        复制
                    </n-button>
                    <n-button v-if="movie && isM3u8(item.url)" size="tiny"
                        :loading="downloadingKey === rowKey(item)" :disabled="!!downloadingKey && downloadingKey !== rowKey(item)"
                        @click="download(item)">离线下载</n-button>
                </div>
                <div v-if="current.truncated" class="ps-more">
                    该线路剧集较多，仅展示前 {{ currentItems.length }} 条；完整列表见详情页
                </div>
            </div>
        </n-spin>

        <template #footer>
            <div class="ps-footer">
                <span class="ps-foot-tip">「离线下载」使用当前默认参数直接加入下载队列，可在「我的下载」查看进度</span>
                <n-button size="small" @click="visible = false">关闭</n-button>
            </div>
        </template>
    </n-modal>
</template>

<script>
import { NButton, NModal, NSpin } from 'naive-ui'
import { DownloadHls, GetHlsDetail } from '@/api/hls'
import { DEFAULT_HLS_API, defaultTitle, episodeNumber, isExtra, isM3u8Url, seasonNumber, sortPlayItems, sortPlaySources } from '@/utils/hlsPlay'

/**
 * 资源列表弹窗（电影天堂搜索结果卡片的「资源列表」按钮）：
 *  - 打开时从采集接口拉取完整剧集（卡片只带前 60 条），失败时回退到卡片载荷；
 *  - 线路默认把 dyttm3u8 排在最前，剧集按集号倒序（新集在前）；
 *  - 每行「复制」把 m3u8 直链写入剪贴板，配合本地播放器使用；
 *  - m3u8 线路点「离线下载」直接用默认参数入队，不再弹出设置对话框。
 */
export default {
    components: { NButton, NModal, NSpin },
    name: 'PlaySourceDialog',
    props: {
        movie: { type: Object, default: null },
        show: {
            type: Boolean,
            default: false
        },
        title: {
            type: String,
            default: ''
        },
        /** 卡片载荷里的播放源：[{ name, total, items: [{ title, url }], truncated }] */
        sources: {
            type: Array,
            default: () => []
        }
    },
    emits: ['update:show', 'queued'],
    data() {
        return {
            activeIndex: 0,
            downloadingKey: '',
            loadingDetail: false,
            /** 完整线路（接口返回）；为空表示仍用卡片载荷 */
            detailSources: null
        }
    },
    computed: {
        visible: {
            get() {
                return this.show
            },
            set(value) {
                this.$emit('update:show', value)
            }
        },
        /** 优先使用完整线路；dyttm3u8 置顶，打开时默认选中第一条 */
        sortedSources() {
            return sortPlaySources(this.detailSources || this.sources)
        },
        current() {
            return this.sortedSources[this.activeIndex] || { items: [], name: '' }
        },
        /** 当前线路的剧集：集号大的在前，无法识别集号的条目排在最后 */
        currentItems() {
            return sortPlayItems(this.current.items)
        },
        total() {
            return this.sortedSources.reduce((sum, s) => sum + (s.items ? s.items.length : 0), 0)
        }
    },
    watch: {
        show(next) {
            // 每次打开都回到第一条线路（已优先 dyttm3u8），并重新拉取完整线路
            if (next) {
                this.activeIndex = 0
                this.downloadingKey = ''
                this.detailSources = null
                this.loadDetail()
            }
        }
    },
    methods: {
        isM3u8(url) { return isM3u8Url(url) },
        rowKey(item) { return (item.title || '') + '|' + (item.url || '') },
        /** 拉取完整资源详情：卡片只带前 60 条，长剧集的最新集需要实时获取 */
        async loadDetail() {
            const movie = this.movie || {}
            const resId = movie.resId || movie.id || ''
            if (!resId) {
                return
            }
            this.loadingDetail = true
            try {
                const detail = await GetHlsDetail(String(resId), movie.apiUrl)
                const sources = detail && detail.playSources
                if (Array.isArray(sources) && sources.length) {
                    this.detailSources = sources
                }
            } catch (error) {
                // 请求拦截器已提示；保留卡片载荷，用户仍可复制/下载已展示的剧集
            } finally {
                this.loadingDetail = false
            }
        },
        /** 直接入队：标题/年份/线路都取卡片与当前线路的默认值，集号从播放条目标题解析 */
        async download(item) {
            if (this.downloadingKey) {
                return
            }
            const movie = this.movie || {}
            const resId = movie.resId || movie.id || ''
            if (!resId) {
                window.$message?.error('这条卡片缺少作品 ID，无法下载')
                return
            }
            let episode = episodeNumber(item.title)
            if (!episode) {
                // 电影等整条线路都没有集号：默认第 1 集，使用当前行地址；预告、花絮仍不下载
                const numbered = this.currentItems.some(candidate => episodeNumber(candidate.title) != null)
                if (isExtra(item.title) || numbered) {
                    window.$message?.warning(`无法识别「${item.title || '正片'}」的集号，请改用订阅或手动下载`)
                    return
                }
                episode = 1
            }
            this.downloadingKey = this.rowKey(item)
            try {
                const result = await DownloadHls({
                    apiUrl: movie.apiUrl || DEFAULT_HLS_API,
                    resId: String(resId),
                    title: defaultTitle(movie.title || ''),
                    year: movie.year || '',
                    season: seasonNumber(movie.title) || movie.season || seasonNumber(item.title) || 1,
                    episode,
                    episodeTitle: item.title || '',
                    url: item.url,
                    sourceName: this.current.name || ''
                })
                window.$message?.success(result?.message || '已加入下载队列')
                this.$emit('queued', result)
            } catch (error) {
                // 请求拦截器已提示，失败后仍可重新点击下载
            } finally {
                this.downloadingKey = ''
            }
        },
        async copy(url) {
            if (!url) {
                return
            }
            try {
                await navigator.clipboard.writeText(url)
                this.$message?.success('资源地址已复制')
            } catch (e) {
                // 剪贴板不可用（非 HTTPS / 无权限）时退化为手动选择
                const input = document.createElement('textarea')
                input.value = url
                document.body.appendChild(input)
                input.select()
                document.execCommand('copy')
                document.body.removeChild(input)
                this.$message?.success('资源地址已复制')
            }
        }
    }
}
</script>

<style scoped>
.ps-head {
    display: flex;
    align-items: center;
    gap: 8px;
}

.ps-head i {
    color: var(--accent);
}

.ps-title {
    font-size: 15px;
    font-weight: 600;
}

.ps-count {
    font-size: 12px;
    color: var(--text-3);
}

.ps-sub {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    margin-bottom: 12px;
    font-size: 12px;
    color: var(--text-3);
}

.ps-tip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.ps-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}

.ps-tab {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 12px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: rgba(148, 163, 184, .06);
    color: var(--text-2);
    font-size: 12.5px;
    cursor: pointer;
}

.ps-tab:hover {
    border-color: var(--accent);
    color: var(--text);
}

.ps-tab.active {
    border-color: var(--accent);
    background: rgba(96, 165, 250, .14);
    color: var(--accent);
    font-weight: 600;
}

.ps-tab-num {
    padding: 0 6px;
    border-radius: 999px;
    background: rgba(148, 163, 184, .16);
    font-size: 11px;
}

.ps-empty {
    padding: 28px 0;
    text-align: center;
    color: var(--text-3);
    font-size: 13px;
}

.ps-list {
    max-height: 420px;
    overflow: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.ps-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: rgba(148, 163, 184, .04);
}

.ps-ep {
    flex: none;
    min-width: 64px;
    font-size: 12px;
    color: var(--text-2);
}

.ps-url {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: Consolas, "Segoe UI", monospace;
    font-size: 12px;
    color: var(--text-3);
}

.ps-more {
    padding: 6px 2px;
    font-size: 12px;
    color: var(--text-3);
}

.ps-footer {
    display: flex;
    align-items: center;
    gap: 8px;
}

.ps-foot-tip {
    flex: 1;
    font-size: 12px;
    color: var(--text-3);
}
</style>

