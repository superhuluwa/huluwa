<template>
    <div class="downloads-page">
        <n-tabs v-model:value="kind" type="line">
            <!-- 标签用 #tab 插槽：当前 Vue 与 naive-ui 组合下 tab 属性会渲染成空白 -->
            <n-tab-pane name="hls">
                <template #tab>离线剧集下载</template>
            </n-tab-pane>
            <n-tab-pane name="magnet">
                <template #tab>磁力下载</template>
            </n-tab-pane>
            <n-tab-pane name="xunlei-win">
                <template #tab>迅雷【Win】</template>
            </n-tab-pane>
        </n-tabs>
        <HlsDownloadsPanel v-if="kind === 'hls'" />
        <XunleiWinDownloadsPanel v-else-if="kind === 'xunlei-win'" />
        <div v-else class="downloads">
            <div class="downloads-head">
                <h3>下载任务</h3>
                <n-button size="tiny" type="error" ghost :disabled="!clearable" :loading="clearing"
                    @click="clearRecords">
                    <template #icon><i class="fa-solid fa-trash-can"></i></template>
                    清空下载记录
                </n-button>
                <n-button size="tiny" :loading="loading" @click="load">
                    <template #icon><i class="fa-solid fa-rotate"></i></template>
                    刷新
                </n-button>
            </div>
            <n-spin :show="loading">
                <div v-if="!rows.length" class="empty">还没有订阅触发的下载任务</div>
                <!-- 手机端改用卡片：表格列多，横滑查看很难读 -->
                <div v-else-if="isMobile" class="cards">
                    <article v-for="row in rows" :key="row.id" class="task-card">
                        <div class="task-head">
                            <span class="task-title">{{ row.seriesTitle || '未知剧集' }}</span>
                            <span class="pill" :class="statusTone(row.status)">{{ statusLabel(row.status) }}</span>
                        </div>
                        <div class="task-tags">
                            <span v-if="row.episode" class="tag">第 {{ row.episode }} 集</span>
                            <span v-if="row.quality" class="tag">{{ row.quality }}</span>
                            <span class="tag">{{ downloaderLabel(row.downloader) }}</span>
                            <span v-if="progressText(row)" class="tag progress">{{ progressText(row) }}</span>
                        </div>
                        <div class="task-fields">
                            <div class="field">
                                <span class="field-label">保存目录</span>
                                <span class="field-value mono">{{ row.savePath || '—' }}</span>
                            </div>
                            <div class="field">
                                <span class="field-label">时间</span>
                                <span class="field-value">{{ row.createdAt || '—' }}</span>
                            </div>
                            <div v-if="row.message" class="field">
                                <span class="field-label">提示</span>
                                <span class="field-value">{{ row.message }}</span>
                            </div>
                        </div>
                    </article>
                </div>
                <n-data-table v-else size="small" :columns="columns" :data="rows" :bordered="false"
                    :max-height="560" />
            </n-spin>
        </div>
    </div>
</template>

<script>
import { NButton, NDataTable, NSpin, NTabPane, NTabs } from 'naive-ui'
import { useIsMobile } from '@/composables/useIsMobile'
import HlsDownloadsPanel from './components/HlsDownloadsPanel.vue'
import XunleiWinDownloadsPanel from './components/XunleiWinDownloadsPanel.vue'
/**
 * 我的下载：订阅巡检 / 手动提交触发的下载任务清单。
 * 与「我的订阅」同级，订阅页只保留订阅维护，下载记录独立成页查看。
 */
import { ClearSubscriptionDownloads, SubscriptionDownloads } from '@/api/chat'

const STATUS_LABEL = {
    pending: '待提交',
    added: '已提交',
    downloading: '下载中',
    completed: '已完成',
    failed: '失败',
    removed: '已移除'
}

/** 状态色：沿用全局 .pill 主题（手机端卡片与表格共用语义） */
const STATUS_TONE = {
    pending: 'warn',
    added: 'off',
    downloading: 'accent',
    completed: 'ok',
    failed: 'danger',
    removed: 'off'
}

export default {
    name: 'MovieDownloads',
    components: {
        HlsDownloadsPanel,
        XunleiWinDownloadsPanel,
        NTabs,
        NTabPane,
        NButton,
        NDataTable,
        NSpin
    },
    setup() {
        // 窄屏（<= 900px）改用卡片列表，桌面端保持表格
        return { isMobile: useIsMobile() }
    },
    data() {
        return {
            kind: 'hls',
            loading: false,
            clearing: false,
            // 首次由 mounted 负责拉取，避免 activated 紧跟挂载时重复请求
            firstActivate: true,
            rows: [],
            columns: [
                { title: '剧名', key: 'seriesTitle', ellipsis: { tooltip: true } },
                { title: '集号', key: 'episode', width: 70 },
                { title: '清晰度', key: 'quality', width: 90 },
                {
                    title: '工具', key: 'downloader', width: 110,
                    render: row => this.downloaderLabel(row.downloader)
                },
                {
                    title: '状态', key: 'status', width: 90,
                    render: (row) => STATUS_LABEL[row.status] || row.status
                },
                {
                    // 下载进度：迅雷【Win】与 qBittorrent 实时回填，其余工具无进度数据时显示 —
                    title: '进度', key: 'progress', width: 90,
                    render: (row) => this.progressText(row) || '—'
                },
                { title: '保存目录', key: 'savePath', ellipsis: { tooltip: true } },
                { title: '时间', key: 'createdAt', width: 160 }
            ]
        }
    },
    computed: {
        /** 只有已结束的历史行可清空；待提交 / 已提交 / 下载中的任务会保留 */
        clearable() { return this.rows.some(row => ['completed', 'failed', 'removed'].includes(row.status)) }
    },
    async mounted() {
        await this.load()
    },
    /** 外壳用 keep-alive 缓存页面：从订阅页切回来时刷新，避免看到过期下载记录 */
    async activated() {
        if (this.firstActivate) {
            this.firstActivate = false
            return
        }
        await this.load()
    },
    methods: {
        statusLabel(status) {
            return STATUS_LABEL[status] || status
        },
        statusTone(status) {
            return STATUS_TONE[status] || 'off'
        },
        /** 下载工具显示名：迅雷【飞牛】/ 迅雷【Win】/ qBittorrent */
        downloaderLabel(downloader) {
            if (downloader === 'xunlei-win') return '迅雷【Win】'
            return downloader === 'xunlei' ? '迅雷【飞牛】' : 'qBittorrent'
        },
        /** 进度文案：只有带进度数据且未完成的行显示百分比，完成行由状态列表达 */
        progressText(row) {
            if (typeof row.progress !== 'number' || row.status === 'completed') {
                return ''
            }
            return Math.max(0, Math.min(100, Math.round(row.progress))) + '%'
        },
        clearRecords() {
            window.$dialog?.warning({
                title: '清空下载记录',
                content: '将清空已完成 / 失败的历史记录，待提交与下载中的任务保留。下载器里已提交的任务不受影响，确认清空？',
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.clearing = true
                    try {
                        const result = await ClearSubscriptionDownloads()
                        window.$message?.success(result?.message || '已清空下载记录')
                        await this.load()
                    } catch (error) { /* 拦截器已提示 */ }
                    finally { this.clearing = false }
                }
            })
        },
        async load() {
            this.loading = true
            try {
                const list = await SubscriptionDownloads(0)
                this.rows = Array.isArray(list) ? list : []
            } finally {
                this.loading = false
            }
        }
    }
}
</script>

<style scoped>
.downloads-page {
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.empty {
    padding: 20px 0;
    text-align: center;
    color: var(--text-3);
    font-size: 13px;
}

.downloads {
    border: 1px solid var(--line);
    border-radius: 12px;
    background: rgba(20, 28, 46, .35);
    padding: 14px;
}

.downloads-head {
    display: flex;
    align-items: center;
    margin-bottom: 10px;
}

.downloads-head h3 {
    margin: 0;
    font-size: 15px;
    color: var(--text);
    margin-right: auto;
}

.downloads-head .n-button + .n-button {
    margin-left: 8px;
}

/* 手机端卡片列表：每行任务一块，字段纵向排布，长路径换行不截断 */
.cards {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.task-card {
    border: 1px solid var(--line);
    border-radius: 10px;
    background: rgba(20, 28, 46, .5);
    padding: 12px;
}

.task-head {
    display: flex;
    align-items: flex-start;
    gap: 8px;
}

.task-title {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--text);
    overflow-wrap: anywhere;
}

.pill.accent {
    background: var(--accent-weak);
    color: var(--accent-2);
}

.task-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
}

.tag {
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    background: rgba(148, 163, 184, .12);
    color: var(--text-2);
}

/* 进度标签：下载中行的实时百分比，用主题色弱化底突出 */
.tag.progress {
    background: var(--accent-weak);
    color: var(--accent-2);
    font-variant-numeric: tabular-nums;
}

.task-fields {
    display: flex;
    flex-direction: column;
    gap: 5px;
    margin-top: 10px;
}

.field {
    display: flex;
    gap: 8px;
    font-size: 12px;
}

.field-label {
    flex: none;
    color: var(--text-3);
}

.field-value {
    min-width: 0;
    color: var(--text-2);
    overflow-wrap: anywhere;
}

/* 移动端：标题独占一行，操作按钮换行铺满，方便手指点击 */
@media (max-width: 900px) {
    .downloads {
        padding: 12px;
    }

    .downloads-head {
        flex-wrap: wrap;
        row-gap: 8px;
    }

    .downloads-head h3 {
        width: 100%;
        margin-right: 0;
    }

    .downloads-head .n-button {
        flex: 1 1 auto;
    }

    .downloads-head .n-button + .n-button {
        margin-left: 0;
    }
}
</style>