<template>
    <n-modal v-model:show="visible" preset="card" style="width: 860px" :mask-closable="false">
        <template #header>
            <div class="mm-head">
                <i class="fa-solid fa-magnet"></i>
                <span class="mm-title">{{ data.title || '磁力列表' }}</span>
                <span v-if="data.count" class="mm-count">{{ data.count }} 条</span>
            </div>
        </template>

        <div class="mm-sub">
            <span class="mm-tip">
                <i class="fa-regular fa-folder-open"></i>
                保存目录：<code>{{ data.savePath || '（未确定）' }}</code>
            </span>
            <span class="mm-tip">
                <i class="fa-solid fa-circle-info"></i>
                使用{{ data.downloader === 'xunlei' ? '迅雷' : 'qBittorrent' }}下载，按剧名创建文件夹
            </span>
        </div>

        <n-spin :show="loading">
            <div v-if="!items.length && !loading" class="mm-empty">该详情页没有解析到磁力链接</div>
            <div ref="listRef" class="mm-list">
                <div v-for="(m, index) in items" :key="m.hash || index" class="mm-row"
                    :class="{ downloaded: m.downloaded }">
                    <span class="mm-quality" :class="qualityClass(m.quality)">{{ m.quality }}</span>
                    <span v-if="m.episode" class="mm-ep">第 {{ m.episode }} 集</span>
                    <span class="mm-name" :title="m.title">{{ m.title || '（无标题）' }}</span>
                    <n-button v-if="m.downloaded" size="tiny" disabled>
                        <template #icon><i class="fa-solid fa-check"></i></template>
                        已下载
                    </n-button>
                    <n-button v-else size="tiny" type="primary" :loading="busyIndex === index"
                        @click="download(m, index)">
                        <template #icon><i class="fa-solid fa-download"></i></template>
                        下载
                    </n-button>
                </div>
            </div>
        </n-spin>

        <template #footer>
            <div class="mm-footer">
                <span class="mm-foot-tip">
                    下载结果会推送到对应下载工具会话；同名剧归入同一文件夹
                </span>
                <n-button size="small" @click="visible = false">关闭</n-button>
                <n-button size="small" :loading="loading" @click="load">
                    <template #icon><i class="fa-solid fa-rotate"></i></template>
                    重新抓取
                </n-button>
            </div>
        </template>
    </n-modal>
</template>

<script>
/**
 * 磁力列表弹窗（搜索结果卡片的「查看磁力」按钮）：
 *  - 实时抓取电影港详情页的磁力，按清晰度给出色标；
 *  - 每行「下载」按钮提交本剧选择的下载工具，保存目录为「下载根目录 / 剧名」，
 *    结果同时写入对应工具的下载会话；
 *  - 已在下载任务表里的条目渲染成「已下载」，避免重复提交；
 *  - 后端按集数倒序返回，默认展示最新一集（列表打开 / 刷新都回到顶部）。
 */
import { PreviewMagnets, DownloadMagnet } from '@/api/chat'

export default {
    name: 'MagnetListDialog',
    props: {
        show: {
            type: Boolean,
            default: false
        },
        url: {
            type: String,
            default: ''
        },
        title: {
            type: String,
            default: ''
        }
    },
    emits: ['update:show', 'downloaded'],
    data() {
        return {
            loading: false,
            items: [],
            busyIndex: -1,
            data: { title: '', savePath: '', count: 0 }
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
        }
    },
    watch: {
        show(next) {
            if (next) {
                this.load()
            }
        }
    },
    methods: {
        async load() {
            if (!this.url) {
                this.$message?.error('缺少电影港详情页链接')
                return
            }
            this.loading = true
            try {
                const data = (await PreviewMagnets(this.url, this.title)) || {}
                this.data = data
                this.items = Array.isArray(data.magnets) ? data.magnets : []
                // 后端已按集数倒序，打开 / 刷新后把滚动条定位到顶部，默认展示最新一集
                await this.$nextTick()
                if (this.$refs.listRef) {
                    this.$refs.listRef.scrollTop = 0
                }
            } catch (e) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        qualityClass(quality) {
            const value = String(quality || '').toUpperCase()
            if (value === '2160P' || value === '4K') return 'q4k'
            if (value === '1080P') return 'q1080'
            if (value === '720P') return 'q720'
            return 'qother'
        },
        async download(m, index) {
            this.busyIndex = index
            try {
                const result = (await DownloadMagnet({
                    url: this.url,
                    title: this.data.title || this.title,
                    magnet: m.magnet,
                    quality: m.quality,
                    episode: m.episode
                })) || {}
                if (result.ok) {
                    this.items.splice(index, 1, { ...m, downloaded: true })
                    this.$message?.success(result.message || '已提交下载')
                    this.$emit('downloaded', result)
                } else {
                    this.$message?.warning(result.message || '提交失败')
                }
            } catch (e) {
                // 拦截器已提示
            } finally {
                this.busyIndex = -1
            }
        }
    }
}
</script>

<style scoped>
.mm-head {
    display: flex;
    align-items: center;
    gap: 8px;
}

.mm-head i {
    color: var(--accent);
}

.mm-title {
    font-size: 15px;
    font-weight: 600;
}

.mm-count {
    font-size: 12px;
    color: var(--text-3);
}

.mm-sub {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    margin-bottom: 12px;
    font-size: 12px;
    color: var(--text-3);
}

.mm-tip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.mm-tip code {
    font-family: Consolas, "Segoe UI", monospace;
    color: var(--accent-2);
}

.mm-empty {
    padding: 28px 0;
    text-align: center;
    color: var(--text-3);
    font-size: 13px;
}

.mm-list {
    max-height: 420px;
    overflow: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.mm-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: rgba(148, 163, 184, .04);
}

.mm-row.downloaded {
    opacity: .6;
}

.mm-quality {
    flex: none;
    min-width: 58px;
    padding: 2px 8px;
    border-radius: 999px;
    text-align: center;
    font-size: 11px;
    font-weight: 600;
}

.mm-quality.q4k {
    background: rgba(52, 211, 153, .14);
    color: var(--ok);
}

.mm-quality.q1080 {
    background: rgba(96, 165, 250, .14);
    color: var(--accent);
}

.mm-quality.q720 {
    background: rgba(251, 191, 36, .14);
    color: var(--warn);
}

.mm-quality.qother {
    background: rgba(148, 163, 184, .12);
    color: var(--text-2);
}

.mm-ep {
    flex: none;
    font-size: 12px;
    color: var(--text-2);
}

.mm-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12.5px;
    color: var(--text);
}

.mm-footer {
    display: flex;
    align-items: center;
    gap: 8px;
}

.mm-foot-tip {
    flex: 1;
    font-size: 12px;
    color: var(--text-3);
}
</style>
