<template>
    <div>
        <div class="head"><span>离线剧集任务（最近 500 条）</span>
            <n-button size="small" type="error" ghost :disabled="!clearable" :loading="clearing"
                @click="clearRecords">清空下载记录</n-button>
            <n-button size="small" @click="settings = true">下载设置</n-button>
            <n-button size="small" :loading="loading" @click="load">刷新</n-button>
        </div>
        <n-spin :show="loading">
            <p v-if="!rows.length" class="empty">还没有离线下载任务。可在电影天堂订阅追更或下载单集。</p>
            <n-data-table v-else :columns="columns" :data="rows" :row-key="row => row.id" :bordered="false" :max-height="560" :scroll-x="1350" size="small" />
        </n-spin>
        <p class="tip">页面显示期间每 3 秒更新进度；下载后还需合并、检查和归档，完成后显示 100%。</p>
        <HlsSettingsDialog v-model:show="settings" />
        <n-modal v-model:show="logDialog" preset="card" title="下载日志" style="width: 760px; max-width: calc(100vw - 32px)">
            <n-spin :show="logLoading"><pre class="log">{{ logContent }}</pre></n-spin>
        </n-modal>
    </div>
</template>

<script>
import { h } from 'vue'
import { NButton, NDataTable, NModal, NProgress, NSpin } from 'naive-ui'
import { ClearHlsDownloads, GetHlsDownloadLog, GetHlsDownloads, RetryHlsDownload } from '@/api/hls'
import HlsSettingsDialog from './HlsSettingsDialog.vue'
const LABELS = { pending: '等待下载', downloading: '下载中', verifying: '检查中', completed: '已完成', failed: '失败', skipped: '首次跳过' }
const PHASES = { parsing: '正在解析资源', downloading: '下载中', merging: '正在合并', verifying: '检查并归档中' }
export default {
    components: { NButton, NDataTable, NModal, NSpin, HlsSettingsDialog },
    data() { return { rows: [], loading: false, fetching: false, settings: false, retrying: 0, clearing: false, timer: null,
        logDialog: false, logLoading: false, logContent: '' } },
    computed: {
        /** 只有已结束的历史行可清空；等待与下载中的任务会保留 */
        clearable() { return this.rows.some(row => ['completed', 'failed', 'skipped'].includes(row.status)) },
        columns() { return [
            { title: '剧名', key: 'seriesTitle', width: 180, ellipsis: { tooltip: true } },
            { title: '季集', key: 'episode', width: 90, render: row => `S${String(row.season).padStart(2, '0')}E${String(row.episode).padStart(2, '0')}` },
            { title: '状态', key: 'status', width: 110, render: row => row.paused && row.status === 'pending' ? '订阅已暂停' : LABELS[row.status] || row.status },
            { title: '下载进度', key: 'progressPercent', width: 210, render: row => {
                const known = typeof row.progressPercent === 'number' && Number.isFinite(row.progressPercent)
                if (!['downloading', 'verifying', 'completed'].includes(row.status)) {
                    return row.status === 'pending' ? '等待开始' : '—'
                }
                return h('div', { class: 'progress-cell' }, [
                    h(NProgress, { type: 'line', percentage: known ? row.progressPercent : 0,
                        processing: row.status !== 'completed', showIndicator: known, height: 6,
                        status: row.status === 'completed' ? 'success' : 'default' }),
                    h('div', { class: 'progress-detail' }, [
                        h('span', row.status === 'completed' ? '已归档' : PHASES[row.phase] || '等待进度'),
                        row.downloadSpeed && row.phase === 'downloading' ? h('span', row.downloadSpeed) : null
                    ])
                ])
            } },
            { title: '尝试次数', key: 'attempts', width: 90 },
            { title: '下次尝试', key: 'nextAttemptAt', width: 160 },
            { title: '归档文件', key: 'savePath', width: 240, ellipsis: { tooltip: true } },
            { title: '提示', key: 'message', width: 200, ellipsis: { tooltip: true } },
            { title: '操作', key: 'action', width: 140, render: row => h('div', { style: 'display:flex;gap:6px' }, [
                h(NButton, { size: 'tiny', onClick: () => this.showLog(row) }, { default: () => '日志' }),
                ['failed', 'skipped'].includes(row.status)
                    ? h(NButton, { size: 'tiny', disabled: !!row.paused, loading: this.retrying === row.id, onClick: () => this.retry(row) },
                        { default: () => row.status === 'skipped' ? '补下载' : '重试' }) : null
            ]) }
        ] }
    },
    mounted() { this.load(); this.start() },
    activated() { if (!this.timer) { this.load(); this.start() } },
    deactivated() { this.stop() }, beforeUnmount() { this.stop() },
    methods: {
        async showLog(row) {
            this.logDialog = true; this.logLoading = true; this.logContent = ''
            try { this.logContent = (await GetHlsDownloadLog(row.id)).content }
            catch (error) { /* 拦截器已提示 */ } finally { this.logLoading = false }
        },
        start() { if (!this.timer) this.timer = setInterval(() => this.load(true), 3000) },
        stop() { clearInterval(this.timer); this.timer = null },
        async load(quiet = false) {
            if (this.fetching) return
            this.fetching = true
            if (!quiet) this.loading = true
            try { this.rows = await GetHlsDownloads() } catch (error) { /* 拦截器已提示 */ }
            finally { this.loading = false; this.fetching = false }
        },
        clearRecords() {
            window.$dialog?.warning({
                title: '清空下载记录',
                content: '将清空已完成 / 失败 / 跳过的历史记录，等待与下载中的任务保留。已归档的视频文件不受影响，确认清空？',
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.clearing = true
                    try {
                        const result = await ClearHlsDownloads()
                        window.$message?.success(result?.message || '已清空下载记录')
                        await this.load()
                    } catch (error) { /* 拦截器已提示 */ }
                    finally { this.clearing = false }
                }
            })
        },
        async retry(row) {
            this.retrying = row.id
            try { await RetryHlsDownload(row.id); window.$message?.success('已重新加入下载队列'); await this.load() }
            catch (error) { /* 拦截器已提示 */ } finally { this.retrying = 0 }
        }
    }
}
</script>

<style scoped>
.head { display: flex; gap: 8px; align-items: center; margin-bottom: 16px; }.head span { margin-right: auto; }
.tip, .empty { font-size: 12px; color: var(--text-3); }
.log { max-height: 60vh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 12px; }
:deep(.progress-cell) { min-width: 170px; }
:deep(.progress-detail) { display: flex; justify-content: space-between; gap: 8px; margin-top: 5px; font-size: 11px; color: var(--text-3); }
@media (max-width: 600px) { .head { flex-wrap: wrap; }.head span { width: 100%; } }
</style>
