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
        </n-tabs>
        <HlsDownloadsPanel v-if="kind === 'hls'" />
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
                <n-data-table v-else size="small" :columns="columns" :data="rows" :bordered="false"
                    :max-height="560" />
            </n-spin>
        </div>
    </div>
</template>

<script>
import { NButton, NDataTable, NSpin, NTabPane, NTabs } from 'naive-ui'
import HlsDownloadsPanel from './components/HlsDownloadsPanel.vue'
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

export default {
    name: 'MovieDownloads',
    components: {
        HlsDownloadsPanel,
        NTabs,
        NTabPane,
        NButton,
        NDataTable,
        NSpin
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
                { title: '工具', key: 'downloader', width: 100, render: row => row.downloader === 'xunlei' ? '迅雷' : 'qBittorrent' },
                {
                    title: '状态', key: 'status', width: 90,
                    render: (row) => STATUS_LABEL[row.status] || row.status
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
</style>
