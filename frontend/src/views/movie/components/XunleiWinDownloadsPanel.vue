<template>
    <div class="xunlei-win-downloads">
        <div class="head">
            <h3>迅雷【Win】下载任务<span>最近 500 条</span></h3>
            <n-button size="small" type="error" ghost :loading="clearing" :disabled="!rows.length || fetching"
                @click="clearRecords">
                <template #icon><i class="fa-solid fa-trash-can"></i></template>
                清空下载记录
            </n-button>
            <n-button size="small" @click="settings = true">连接设置</n-button>
            <n-button size="small" :loading="loading" :disabled="fetching || clearing" @click="load()">
                <template #icon><i class="fa-solid fa-rotate"></i></template>
                刷新
            </n-button>
        </div>
        <n-alert v-if="loadError" type="warning" :show-icon="true" class="load-error">
            {{ loadError }}
        </n-alert>
        <n-spin :show="loading">
            <p v-if="!rows.length && !loadError" class="empty">还没有迅雷【Win】下载记录，提交下载后会自动记录。</p>
            <div v-else-if="isMobile" class="cards">
                <article v-for="row in rows" :key="row.id" class="task-card">
                    <div class="task-head">
                        <span class="task-title">{{ row.name || '迅雷下载任务' }}</span>
                        <span class="pill" :class="statusTone(row.status)">{{ statusLabel(row.status) }}</span>
                    </div>
                    <div class="task-progress">
                        <n-progress type="line" :percentage="progressPercent(row)" :show-indicator="hasProgress(row)"
                            :processing="row.status === 'downloading'" :status="progressStatus(row)" :height="6" />
                        <div class="progress-detail">
                            <span>{{ hasProgress(row) ? '下载进度' : '等待进度' }}</span>
                            <span>{{ sizeText(row) }}</span>
                        </div>
                    </div>
                    <div class="task-fields">
                        <div class="field">
                            <span class="field-label">流水号</span>
                            <span class="field-value mono">{{ row.serial || '未返回' }}</span>
                        </div>
                        <div class="field">
                            <span class="field-label">保存目录</span>
                            <span class="field-value mono">{{ row.savePath || '迅雷默认目录' }}</span>
                        </div>
                        <div class="field">
                            <span class="field-label">提交时间</span>
                            <span class="field-value">{{ row.createdAt || '—' }}</span>
                        </div>
                        <div v-if="row.checkedAt" class="field">
                            <span class="field-label">查询时间</span>
                            <span class="field-value">{{ row.checkedAt }}</span>
                        </div>
                        <div v-if="row.message" class="field">
                            <span class="field-label">状态说明</span>
                            <span class="field-value">{{ row.message }}</span>
                        </div>
                    </div>
                    <p v-if="row.queryError" class="query-error">{{ row.queryError }}</p>
                </article>
            </div>
            <n-data-table v-else :columns="columns" :data="rows" :row-key="row => row.id" :bordered="false"
                :max-height="560" :scroll-x="1650" size="small" />
        </n-spin>
        <p class="tip">每 1 秒更新下载状态与进度，已完成和失败的记录会保留。</p>
        <XunleiWinSettingsDialog v-model:show="settings" @saved="load()" />
    </div>
</template>

<script>
import { h } from 'vue'
import { NAlert, NButton, NDataTable, NProgress, NSpin } from 'naive-ui'
import { ClearXunleiWinDownloads, GetXunleiWinDownloads } from '@/api/xunlei-win'
import { useIsMobile } from '@/composables/useIsMobile'
import XunleiWinSettingsDialog from '@/views/message/components/XunleiWinSettingsDialog.vue'

const LABELS = {
    pending: '等待创建', creating: '创建中', created: '已创建', downloading: '下载中',
    success: '已完成', failed: '失败', untracked: '无法跟踪'
}
const TONES = {
    pending: 'warn', creating: 'warn', created: 'off', downloading: 'accent',
    success: 'ok', failed: 'danger', untracked: 'warn'
}

export default {
    name: 'XunleiWinDownloadsPanel',
    components: { NAlert, NButton, NDataTable, NProgress, NSpin, XunleiWinSettingsDialog },
    setup() {
        return { isMobile: useIsMobile() }
    },
    data() {
        return { rows: [], loading: false, fetching: false, clearing: false, settings: false, timer: null, loadError: '' }
    },
    computed: {
        columns() {
            return [
                { title: '任务名称', key: 'name', width: 220, ellipsis: { tooltip: true } },
                { title: '流水号', key: 'serial', width: 140, ellipsis: { tooltip: true }, render: row => row.serial || '未返回' },
                { title: '状态', key: 'status', width: 100, render: row => h('span', {
                    class: ['pill', this.statusTone(row.status)]
                }, this.statusLabel(row.status)) },
                { title: '下载进度', key: 'progress', width: 200, render: row => h('div', { class: 'progress-cell' }, [
                    h(NProgress, {
                        type: 'line', percentage: this.progressPercent(row), showIndicator: this.hasProgress(row),
                        processing: row.status === 'downloading', status: this.progressStatus(row), height: 6
                    }),
                    !this.hasProgress(row) ? h('div', { class: 'progress-detail' }, '等待进度') : null
                ]) },
                { title: '已下载 / 总大小', key: 'receivedBytes', width: 170, render: row => this.sizeText(row) },
                { title: '保存目录', key: 'savePath', width: 220, ellipsis: { tooltip: true }, render: row => row.savePath || '迅雷默认目录' },
                { title: '状态说明', key: 'message', width: 280, render: row => h('div', { class: 'task-message' }, [
                    h('span', row.message || '—'),
                    row.queryError ? h('span', { class: 'query-error' }, row.queryError) : null
                ]) },
                { title: '提交时间', key: 'createdAt', width: 160 },
                { title: '查询时间', key: 'checkedAt', width: 160, render: row => row.checkedAt || '—' }
            ]
        }
    },
    mounted() { this.start() },
    activated() { this.start() },
    deactivated() { this.stop() },
    beforeUnmount() { this.stop() },
    methods: {
        statusLabel(status) { return LABELS[status] || status || '未知状态' },
        statusTone(status) { return TONES[status] || 'off' },
        hasProgress(row) { return row.status === 'success' || (typeof row.progress === 'number' && Number.isFinite(row.progress)) },
        progressPercent(row) {
            if (row.status === 'success') return 100
            return this.hasProgress(row) ? Math.round(Math.max(0, Math.min(100, row.progress)) * 10) / 10 : 0
        },
        progressStatus(row) { return row.status === 'success' ? 'success' : row.status === 'failed' ? 'error' : 'default' },
        formatBytes(bytes) {
            if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes < 0) return '—'
            if (bytes === 0) return '0 B'
            const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
            const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
            return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
        },
        sizeText(row) { return `${this.formatBytes(row.receivedBytes)} / ${this.formatBytes(row.totalBytes)}` },
        start() {
            if (this.timer) return
            this.load()
            this.timer = setInterval(() => this.load(true), 1000)
        },
        stop() { clearInterval(this.timer); this.timer = null },
        async clearRecords() {
            if (this.clearing || this.fetching || !this.rows.length) return
            this.clearing = true
            try {
                const result = await ClearXunleiWinDownloads()
                this.rows = []
                this.loadError = ''
                window.$message?.success(result?.message || '已清空下载记录')
            } catch (error) { /* 拦截器已提示 */ }
            finally { this.clearing = false }
        },
        async load(quiet = false) {
            if (this.fetching || this.clearing) return
            this.fetching = true
            if (!quiet) this.loading = true
            try {
                const rows = await GetXunleiWinDownloads(quiet)
                this.rows = Array.isArray(rows) ? rows : []
                this.loadError = ''
            } catch (error) {
                this.loadError = '下载记录暂时无法更新，将自动重试。'
            } finally {
                this.loading = false
                this.fetching = false
            }
        }
    }
}
</script>

<style scoped>
.xunlei-win-downloads { border: 1px solid var(--line); border-radius: 12px; background: rgba(20, 28, 46, .35); padding: 14px; }
.head { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.head h3 { margin: 0 auto 0 0; font-size: 15px; color: var(--text); }
.head h3 span { margin-left: 10px; font-size: 12px; font-weight: 400; color: var(--text-3); }
.tip, .empty { font-size: 12px; color: var(--text-3); }
.empty { padding: 20px 0; text-align: center; }
.load-error { margin-bottom: 14px; }
:deep(.pill.accent) { background: var(--accent-weak); color: var(--accent-2); }
:deep(.progress-cell) { min-width: 170px; }
:deep(.progress-detail) { display: flex; justify-content: space-between; gap: 8px; margin-top: 5px; font-size: 11px; color: var(--text-3); }
:deep(.task-message) { display: flex; flex-direction: column; gap: 4px; overflow-wrap: anywhere; }
:deep(.query-error) { margin: 8px 0 0; font-size: 12px; color: var(--warn); overflow-wrap: anywhere; }
.cards { display: flex; flex-direction: column; gap: 10px; }
.task-card { border: 1px solid var(--line); border-radius: 10px; background: rgba(20, 28, 46, .5); padding: 12px; }
.task-head { display: flex; align-items: flex-start; gap: 8px; }
.task-title { flex: 1; min-width: 0; font-size: 14px; font-weight: 600; color: var(--text); overflow-wrap: anywhere; }
.task-head .pill { flex: none; }
.task-progress { margin-top: 12px; }
.task-fields { display: flex; flex-direction: column; gap: 5px; margin-top: 12px; }
.field { display: flex; gap: 8px; font-size: 12px; }
.field-label { flex: none; color: var(--text-3); }
.field-value { min-width: 0; color: var(--text-2); overflow-wrap: anywhere; }
@media (max-width: 900px) {
    .xunlei-win-downloads { padding: 12px; }
    .head { flex-wrap: wrap; }
    .head h3 { width: 100%; margin-bottom: 2px; }
    .head .n-button { flex: 1; }
}
</style>
