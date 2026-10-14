<template>
    <div class="logs-page">
        <div class="logs-head">
            <div class="summary">
                <span class="count">共 {{ rows.length }} 条记录</span>
                <span class="tip">电影港与电影天堂的订阅巡检结果，按时间倒序</span>
            </div>
            <div class="head-actions">
                <n-select v-model:value="source" class="source-select" size="small" :options="sourceOptions"
                    :consistent-menu-width="false" @update:value="load" />
                <n-button size="small" type="error" ghost :disabled="!rows.length" :loading="clearing"
                    @click="clearRecords">
                    <template #icon><i class="fa-solid fa-trash-can"></i></template>
                    清空记录
                </n-button>
                <n-button size="small" :loading="loading" @click="load">
                    <template #icon><i class="fa-solid fa-rotate"></i></template>
                    刷新
                </n-button>
            </div>
        </div>

        <n-spin :show="loading">
            <div v-if="loadError" class="error">{{ loadError }}</div>
            <div v-else-if="!rows.length" class="empty">
                还没有巡检记录。添加订阅后，后端每个巡检周期都会在这里留一条结果。
            </div>

            <!-- 手机端：卡片列表，字段纵向排布，长文本可换行 -->
            <div v-else-if="isMobile" class="cards">
                <article v-for="row in rows" :key="row.id" class="log-card">
                    <div class="card-head">
                        <span class="log-title">{{ row.seriesTitle || '未知订阅' }}</span>
                        <span class="pill" :class="statusTone(row.status)">{{ statusLabel(row.status) }}</span>
                    </div>
                    <div class="card-tags">
                        <span class="tag source" :class="row.source">{{ sourceLabel(row.source) }}</span>
                        <span class="tag">{{ triggerLabel(row.trigger) }}</span>
                        <span v-if="hasUpdate(row)" class="tag update">
                            新增 {{ row.foundCount }} 条 · 提交 {{ row.submittedCount }} 条
                        </span>
                    </div>
                    <div class="card-time">
                        <i class="fa-regular fa-clock"></i> {{ row.createdAt }}
                        <span v-if="row.durationMs">· 耗时 {{ durationText(row.durationMs) }}</span>
                    </div>
                    <div v-if="row.message" class="log-message">{{ row.message }}</div>
                    <div v-if="visibleDetails(row).length" class="log-details">
                        <div v-for="(line, index) in visibleDetails(row)" :key="index" class="detail-line">{{ line }}</div>
                        <button v-if="hiddenCount(row) > 0" type="button" class="detail-more" @click="toggleDetails(row.id)">
                            {{ expanded[row.id] ? '收起明细' : '展开其余 ' + hiddenCount(row) + ' 条' }}
                        </button>
                    </div>
                </article>
            </div>

            <!-- 电脑端：表格，明细可展开查看 -->
            <n-data-table v-else size="small" :columns="columns" :data="rows" :bordered="false"
                :row-key="rowKey" :max-height="620" />
        </n-spin>
    </div>
</template>

<script>
import { NButton, NDataTable, NSelect, NSpin } from 'naive-ui'
import { h } from 'vue'
import { useIsMobile } from '@/composables/useIsMobile'
import { ClearSubscriptionCheckLogs, GetSubscriptionCheckLogs } from '@/api/chat'

/** 桌面端表格的展开区：列出该轮巡检的具体条目（无明细时给出提示） */
function renderDetail(row) {
    const lines = Array.isArray(row.details) ? row.details : []
    if (!lines.length) {
        return h('div', { class: 'expand-empty' }, '本轮没有新增条目明细')
    }
    return h('div', { class: 'expand-detail' }, lines.map((line, index) =>
        h('div', { class: 'expand-line', key: index }, line)))
}

const SOURCE_LABEL = { dygangs: '电影港', dytt: '电影天堂' }
const TRIGGER_LABEL = { scheduled: '定时巡检', manual: '立即检查' }
const STATUS_LABEL = { updated: '有更新', ok: '无更新', baseline: '补建基线', failed: '巡检失败' }
/** 状态色：沿用全局 .pill 主题 */
const STATUS_TONE = { updated: 'ok', ok: 'off', baseline: 'warn', failed: 'danger' }

export default {
    name: 'MovieCheckLogs',
    components: { NButton, NDataTable, NSelect, NSpin },
    setup() {
        // 窄屏（<= 900px）改用卡片列表，桌面端保持表格
        return { isMobile: useIsMobile() }
    },
    data() {
        return {
            loading: false,
            clearing: false,
            loadError: '',
            source: 'all',
            firstActivate: true,
            rows: [],
            /** 手机端卡片明细的展开状态：默认只显示前几条，长列表点开后完整查看 */
            expanded: {},
            sourceOptions: [
                { label: '全部来源', value: 'all' },
                { label: '电影港', value: 'dygangs' },
                { label: '电影天堂', value: 'dytt' }
            ],
            columns: [
                { type: 'expand', renderExpand: (row) => renderDetail(row), width: 36 },
                { title: '时间', key: 'createdAt', width: 165 },
                {
                    title: '来源', key: 'source', width: 100,
                    render: row => this.sourceLabel(row.source)
                },
                { title: '剧名', key: 'seriesTitle', ellipsis: { tooltip: true } },
                {
                    title: '巡检方式', key: 'trigger', width: 100,
                    render: row => this.triggerLabel(row.trigger)
                },
                {
                    title: '结果', key: 'status', width: 130,
                    render: (row) => h('span', { class: ['pill', STATUS_TONE[row.status] || 'off'] },
                        STATUS_LABEL[row.status] || row.status)
                },
                {
                    title: '新增 / 提交', key: 'foundCount', width: 110,
                    render: row => (this.hasUpdate(row)
                        ? row.foundCount + ' / ' + row.submittedCount
                        : '—')
                },
                { title: '说明', key: 'message', ellipsis: { tooltip: true } },
                {
                    title: '耗时', key: 'durationMs', width: 90,
                    render: row => this.durationText(row.durationMs)
                }
            ]
        }
    },
    async mounted() {
        await this.load()
    },
    /** 外壳 keep-alive 缓存本页：从其它页面切回时刷新，避免看到过期记录 */
    async activated() {
        if (this.firstActivate) {
            this.firstActivate = false
            return
        }
        await this.load()
    },
    methods: {
        rowKey(row) { return row.id },
        /** 手机端卡片明细：默认前 4 条，展开后全部显示（记录本身已由后端限制条数） */
        visibleDetails(row) {
            const lines = Array.isArray(row.details) ? row.details : []
            return this.expanded[row.id] ? lines : lines.slice(0, 4)
        },
        hiddenCount(row) {
            const lines = Array.isArray(row.details) ? row.details : []
            return Math.max(0, lines.length - 4)
        },
        toggleDetails(id) {
            this.expanded = { ...this.expanded, [id]: !this.expanded[id] }
        },
        sourceLabel(source) { return SOURCE_LABEL[source] || source || '—' },
        triggerLabel(trigger) { return TRIGGER_LABEL[trigger] || trigger || '—' },
        statusLabel(status) { return STATUS_LABEL[status] || status || '—' },
        statusTone(status) { return STATUS_TONE[status] || 'off' },
        /** 只有真正发现新条目时才显示「新增 / 提交」，无更新与失败都不展示 0 */
        hasUpdate(row) { return Number(row.foundCount || 0) > 0 || Number(row.submittedCount || 0) > 0 },
        /** 耗时展示：小于 1 秒显示毫秒，超过 1 分钟显示分秒 */
        durationText(ms) {
            const value = Number(ms || 0)
            if (!value) return '—'
            if (value < 1000) return value + ' ms'
            if (value < 60000) return (value / 1000).toFixed(1) + ' s'
            return Math.floor(value / 60000) + ' 分 ' + Math.round((value % 60000) / 1000) + ' 秒'
        },
        clearRecords() {
            window.$dialog?.warning({
                title: '清空巡检记录',
                content: '将删除全部巡检历史，订阅本身与下载任务不受影响。确认清空？',
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.clearing = true
                    try {
                        const result = await ClearSubscriptionCheckLogs()
                        window.$message?.success((result && result.message) || '已清空巡检记录')
                        await this.load()
                    } catch (error) { /* 拦截器已提示 */ }
                    finally { this.clearing = false }
                }
            })
        },
        async load() {
            this.loading = true
            try {
                const list = await GetSubscriptionCheckLogs(200, this.source === 'all' ? '' : this.source)
                this.rows = Array.isArray(list) ? list : []
                this.loadError = ''
            } catch (error) {
                this.loadError = '巡检记录加载失败，请刷新重试。'
            } finally {
                this.loading = false
            }
        }
    }
}
</script>

<style scoped>
.logs-page {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.logs-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
}

.summary {
    display: flex;
    align-items: baseline;
    gap: 8px;
    min-width: 0;
    font-size: 12px;
    color: var(--text-3);
}

.summary .count {
    font-size: 13px;
    color: var(--text-2);
}

.head-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.source-select {
    width: 130px;
}

.empty {
    padding: 36px 0;
    text-align: center;
    color: var(--text-3);
    font-size: 13px;
}

.error {
    padding: 14px 0;
    color: var(--danger);
    font-size: 12.5px;
}

/* 桌面端表格展开区：逐条列出本轮新增 / 提交的条目 */
.logs-page :deep(.expand-detail),
.logs-page :deep(.expand-empty) {
    padding: 4px 8px 4px 4px;
    font-size: 12px;
    color: var(--text-3);
}

.logs-page :deep(.expand-line) {
    padding: 2px 0;
    overflow-wrap: anywhere;
}

/* 桌面端表格单元里的状态徽标不换行 */
.logs-page :deep(.n-data-table .pill) {
    white-space: nowrap;
}

/* 手机端卡片列表：每条巡检一块，明细按行铺开 */
.cards {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.log-card {
    border: 1px solid var(--line);
    border-radius: 10px;
    background: rgba(20, 28, 46, .5);
    padding: 12px;
}

.card-head {
    display: flex;
    align-items: flex-start;
    gap: 8px;
}

.log-title {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--text);
    overflow-wrap: anywhere;
}

.card-tags {
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

.tag.source.dygangs {
    background: rgba(99, 102, 241, .14);
    color: #A5B4FC;
}

.tag.source.dytt {
    background: rgba(14, 165, 233, .14);
    color: #7DD3FC;
}

.tag.update {
    background: rgba(16, 185, 129, .14);
    color: #34D399;
}

.card-time {
    margin-top: 8px;
    font-size: 12px;
    color: var(--text-3);
}

.card-time i {
    margin-right: 4px;
}

.log-message {
    margin-top: 8px;
    font-size: 12.5px;
    color: var(--text-2);
    overflow-wrap: anywhere;
}

.log-details {
    margin-top: 8px;
    border-top: 1px dashed var(--line-2);
    padding-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.detail-line {
    font-size: 12px;
    color: var(--text-3);
    overflow-wrap: anywhere;
}

/* 「展开其余 N 条」：保持小号文字外观，也要有足够触控面积 */
.detail-more {
    align-self: flex-start;
    margin-top: 2px;
    padding: 4px 10px;
    border-radius: 999px;
    border: 1px solid var(--line-2);
    background: transparent;
    color: var(--accent-2);
    font-size: 11.5px;
    font-family: inherit;
    cursor: pointer;
}

.detail-more:hover {
    background: rgba(148, 163, 184, .08);
}

/* 移动端：标题换行，下拉与按钮等宽排列方便手指点击 */
@media (max-width: 900px) {
    .summary {
        flex-wrap: wrap;
    }

    .head-actions {
        width: 100%;
    }

    .head-actions .source-select {
        flex: 1 1 100%;
        width: 100%;
    }

    .head-actions .n-button {
        flex: 1 1 auto;
    }
}
</style>
