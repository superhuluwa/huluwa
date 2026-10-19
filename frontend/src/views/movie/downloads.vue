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
                <n-button size="tiny" type="error" ghost :disabled="!clearable || fetching" :loading="clearing"
                    @click="clearRecords">
                    <template #icon><i class="fa-solid fa-trash-can"></i></template>
                    清空下载记录
                </n-button>
                <n-button size="tiny" :loading="loading" :disabled="fetching || clearing" @click="load()">
                    <template #icon><i class="fa-solid fa-rotate"></i></template>
                    刷新
                </n-button>
            </div>
            <n-alert v-if="syncMessage" type="warning" class="sync-alert">{{ syncMessage }}</n-alert>
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
                        </div>
                        <div class="task-progress">
                            <n-progress type="line" :percentage="progressPercent(row)"
                                :show-indicator="hasProgress(row)" :processing="row.status === 'downloading'"
                                :status="progressStatus(row)" :height="6" />
                            <span v-if="!hasProgress(row)" class="progress-detail">等待进度</span>
                        </div>
                        <div class="task-fields">
                            <div class="field">
                                <span class="field-label">已下载 / 总大小</span>
                                <span class="field-value">{{ sizeText(row) }}</span>
                            </div>
                            <div v-if="row.downloader === 'xunlei'" class="field">
                                <span class="field-label">下载速度</span>
                                <span class="field-value">{{ speedText(row) }}<template v-if="speedupText(row)"> · {{ speedupText(row) }}</template></span>
                            </div>
                            <div class="field">
                                <span class="field-label">保存目录</span>
                                <span class="field-value mono">{{ row.savePath || '—' }}</span>
                            </div>
                            <div class="field">
                                <span class="field-label">时间</span>
                                <span class="field-value">{{ row.createdAt || '—' }}</span>
                            </div>
                            <div v-if="row.checkedAt" class="field">
                                <span class="field-label">查询时间</span>
                                <span class="field-value">{{ row.checkedAt }}</span>
                            </div>
                            <div v-if="row.queryError" class="field">
                                <span class="field-label">同步提示</span>
                                <span class="field-value query-error">{{ row.queryError }}</span>
                            </div>
                            <div v-if="row.message" class="field">
                                <span class="field-label">提示</span>
                                <span class="field-value">{{ row.message }}</span>
                            </div>
                        </div>
                    </article>
                </div>
                <n-data-table v-else size="small" :columns="columns" :data="rows" :bordered="false"
                    :max-height="560" :scroll-x="1640" />
            </n-spin>
        </div>
    </div>
</template>

<script>
import { h } from 'vue'
import { NAlert, NButton, NDataTable, NProgress, NSpin, NTabPane, NTabs } from 'naive-ui'
import { useIsMobile } from '@/composables/useIsMobile'
import HlsDownloadsPanel from './components/HlsDownloadsPanel.vue'
import XunleiWinDownloadsPanel from './components/XunleiWinDownloadsPanel.vue'
import { ClearSubscriptionDownloads, SubscriptionDownloads } from '@/api/chat'

const STATUS_LABEL = {
    pending: '待提交', added: '已提交', downloading: '下载中',
    completed: '已完成', failed: '失败', removed: '已移除'
}

const STATUS_TONE = {
    pending: 'warn', added: 'off', downloading: 'accent',
    completed: 'ok', failed: 'danger', removed: 'off'
}

/** 我的下载：磁力标签打开期间批量同步飞牛迅雷，离开标签或页面后停止轮询。 */
export default {
    name: 'MovieDownloads',
    components: {
        HlsDownloadsPanel, XunleiWinDownloadsPanel,
        NAlert, NTabs, NTabPane, NButton, NDataTable, NProgress, NSpin
    },
    setup() {
        return { isMobile: useIsMobile() }
    },
    data() {
        return {
            kind: 'hls', rows: [], loading: false, fetching: false, clearing: false,
            pageActive: false, timer: null, requestVersion: 0, loadError: ''
        }
    },
    computed: {
        clearable() { return this.rows.length > 0 },
        syncMessage() {
            if (this.loadError) return this.loadError
            return this.rows.some(row => row.queryError)
                ? '部分迅雷任务未能同步，当前保留最近一次下载信息。' : ''
        },
        columns() {
            return [
                { title: '剧名', key: 'seriesTitle', width: 200, ellipsis: { tooltip: true } },
                { title: '集号', key: 'episode', width: 60 },
                { title: '清晰度', key: 'quality', width: 90 },
                { title: '工具', key: 'downloader', width: 110, render: row => this.downloaderLabel(row.downloader) },
                { title: '状态', key: 'status', width: 90, render: row => this.statusLabel(row.status) },
                {
                    title: '下载进度', key: 'progress', width: 180,
                    render: row => h('div', { class: 'progress-cell' }, [
                        h(NProgress, {
                            type: 'line', percentage: this.progressPercent(row), height: 6,
                            showIndicator: this.hasProgress(row), processing: row.status === 'downloading',
                            status: this.progressStatus(row)
                        }),
                        !this.hasProgress(row) ? h('span', { class: 'progress-detail' }, '等待进度') : null
                    ])
                },
                { title: '已下载 / 总大小', key: 'totalBytes', width: 180, render: row => this.sizeText(row) },
                {
                    title: '下载速度', key: 'downloadSpeed', width: 135,
                    render: row => h('div', { class: 'speed-cell' }, [
                        h('span', this.speedText(row)),
                        this.speedupText(row) ? h('span', { class: 'progress-detail' }, this.speedupText(row)) : null
                    ])
                },
                { title: '保存目录', key: 'savePath', width: 220, ellipsis: { tooltip: true } },
                { title: '时间', key: 'createdAt', width: 160 },
                { title: '查询时间', key: 'checkedAt', width: 160, render: row => row.checkedAt || '—' }
            ]
        }
    },
    watch: {
        kind() {
            this.stopPolling()
            this.startPolling()
        }
    },
    mounted() { this.pageActive = true; this.startPolling() },
    activated() { this.pageActive = true; this.startPolling() },
    deactivated() { this.pageActive = false; this.stopPolling() },
    beforeUnmount() { this.pageActive = false; this.stopPolling() },
    methods: {
        statusLabel(status) { return STATUS_LABEL[status] || status },
        statusTone(status) { return STATUS_TONE[status] || 'off' },
        downloaderLabel(downloader) {
            if (downloader === 'xunlei-win') return '迅雷【Win】'
            return downloader === 'xunlei' ? '迅雷【飞牛】' : 'qBittorrent'
        },
        hasProgress(row) {
            return row.status === 'completed' || (typeof row.progress === 'number' && Number.isFinite(row.progress))
        },
        progressPercent(row) {
            if (row.status === 'completed') return 100
            return this.hasProgress(row) ? Math.round(Math.max(0, Math.min(100, row.progress))) : 0
        },
        progressStatus(row) {
            return row.status === 'completed' ? 'success' : row.status === 'failed' ? 'error' : 'default'
        },
        formatBytes(bytes) {
            if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes < 0) return '—'
            if (bytes === 0) return '0 B'
            const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB']
            const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
            return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 2)} ${units[index]}`
        },
        sizeText(row) {
            const received = row.status === 'completed' && row.receivedBytes == null ? row.totalBytes : row.receivedBytes
            if (row.totalBytes == null && received == null) {
                return row.downloader === 'xunlei' && ['added', 'downloading'].includes(row.status) ? '待解析' : '—'
            }
            return `${this.formatBytes(received)} / ${row.totalBytes == null ? '待解析' : this.formatBytes(row.totalBytes)}`
        },
        speedText(row) {
            return row.status === 'downloading' && typeof row.downloadSpeed === 'number'
                ? `${this.formatBytes(row.downloadSpeed)}/s` : '—'
        },
        speedupText(row) {
            return row.status === 'downloading' && typeof row.speedupSpeed === 'number' && row.speedupSpeed > 0
                ? `加速 ${this.formatBytes(row.speedupSpeed)}/s` : ''
        },
        startPolling() {
            if (!this.pageActive || this.kind !== 'magnet' || this.timer !== null) return
            this.load()
            this.timer = setInterval(() => this.load(true), 5000)
        },
        stopPolling() {
            clearInterval(this.timer)
            this.timer = null
        },
        clearRecords() {
            if (this.clearing || this.fetching || !this.clearable) return
            window.$dialog?.warning({
                title: '清空下载记录',
                content: '将清空磁力下载列表中的全部记录，包括待提交、已提交和下载中的任务记录。下载器中的任务仍会继续执行，已下载文件保留，确认清空？',
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: async () => {
                    if (this.clearing) return false
                    this.clearing = true
                    // 丢弃清空之前发出的轮询结果，避免刚清理的行再次出现。
                    this.requestVersion += 1
                    try {
                        const result = await ClearSubscriptionDownloads()
                        this.rows = []
                        this.loadError = ''
                        window.$message?.success(result?.message || '已清空下载记录')
                    } catch (error) { /* 拦截器已提示 */ }
                    finally { this.clearing = false }
                    await this.load()
                }
            })
        },
        async load(quiet = false) {
            if (this.fetching || this.clearing) return
            if (quiet && (!this.pageActive || this.kind !== 'magnet' || document.hidden)) return
            this.fetching = true
            if (!quiet) this.loading = true
            const version = this.requestVersion
            try {
                const list = await SubscriptionDownloads(0, { refresh: true, quiet })
                if (version === this.requestVersion) {
                    this.rows = Array.isArray(list) ? list : []
                    this.loadError = ''
                }
            } catch (error) {
                if (version === this.requestVersion) {
                    this.loadError = '下载记录暂时无法更新，当前显示上次数据，将自动重试。'
                }
            } finally {
                this.loading = false
                this.fetching = false
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

.sync-alert {
    margin-bottom: 12px;
}

.task-progress {
    margin-top: 12px;
}

.progress-cell,
.speed-cell {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-variant-numeric: tabular-nums;
}

.progress-detail {
    font-size: 11px;
    color: var(--text-3);
}

.query-error {
    color: var(--warn, #f0b45a);
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
