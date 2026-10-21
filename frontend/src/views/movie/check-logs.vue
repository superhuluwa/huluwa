<template>
    <ManagementShell kind="check-logs" title="巡检记录" subtitle="订阅巡检结果与新增明细" :loading="loading"
        :busy="clearing" @refresh="load">
        <template #actions>
            <button v-if="rows.length" class="header-icon" type="button" title="清空巡检记录" @click="clearRecords">
                <i class="fa-solid fa-trash-can"></i>
            </button>
        </template>

        <div class="mgmt-content">
            <!-- 速览：记录数 + 最近结果 -->
            <section class="stats-row">
                <div class="stat-block">
                    <span>巡检记录</span>
                    <strong>{{ rows.length }}</strong>
                    <em>最近 {{ limit }} 条</em>
                </div>
                <div class="stat-block">
                    <span>有更新</span>
                    <strong>{{ updatedCount }}</strong>
                    <em>发现新资源并提交</em>
                </div>
                <div class="stat-block">
                    <span>失败</span>
                    <strong class="danger">{{ failedCount }}</strong>
                    <em>站点不可达或解析异常</em>
                </div>
            </section>

            <!-- 来源筛选 -->
            <div class="section-tabs">
                <button v-for="option in sourceOptions" :key="option.value" type="button" class="section-tab"
                    :class="{ active: source === option.value }" @click="selectSource(option.value)">
                    {{ option.label }}
                </button>
            </div>

            <p v-if="loadError" class="notice-line"><i class="fa-solid fa-triangle-exclamation"></i>{{ loadError }}</p>

            <div v-if="!rows.length && !loading" class="empty-panel">
                <span class="empty-icon"><i class="fa-solid fa-list-check"></i></span>
                <strong>还没有巡检记录</strong>
                <span>添加订阅后，每个巡检周期都会在这里留一条结果</span>
            </div>

            <article v-for="row in rows" :key="row.id" class="panel log-card">
                <div class="log-head">
                    <strong class="log-title">{{ row.seriesTitle || '未知订阅' }}</strong>
                    <span class="chip-tag" :class="statusTone(row.status)">{{ statusLabel(row.status) }}</span>
                </div>
                <div class="log-tags">
                    <span class="chip-tag outline" :class="row.source">{{ sourceLabel(row.source) }}</span>
                    <span class="chip-tag muted">{{ triggerLabel(row.trigger) }}</span>
                    <span v-if="hasUpdate(row)" class="chip-tag">
                        新增 {{ row.foundCount }} 条 · 提交 {{ row.submittedCount }} 条
                    </span>
                </div>
                <div class="log-time">
                    <i class="fa-regular fa-clock"></i>{{ row.createdAt }}
                    <span v-if="row.durationMs">· 耗时 {{ durationText(row.durationMs) }}</span>
                </div>
                <p v-if="row.message" class="log-message">{{ row.message }}</p>
                <div v-if="visibleDetails(row).length" class="log-details">
                    <div v-for="(line, index) in visibleDetails(row)" :key="index" class="detail-line">{{ line }}</div>
                    <button v-if="hiddenCount(row) > 0" type="button" class="link-btn" @click="toggleDetails(row.id)">
                        {{ expanded[row.id] ? '收起明细' : `展开其余 ${hiddenCount(row)} 条` }}
                    </button>
                </div>
            </article>
        </div>
    </ManagementShell>
</template>

<script>
/**
 * 巡检记录（系统会话）：电影港与电影天堂两个来源共用的巡检时间线。
 * 卡片展示结果状态、来源、新增 / 提交条数、耗时与明细（默认 4 条，可展开）。
 */
import ManagementShell from '@/components/main/management-shell.vue'
import { GetSubscriptionCheckLogs, ClearSubscriptionCheckLogs } from '@/api/chat'

const SOURCE_LABEL = { dygangs: '电影港', dytt: '电影天堂' }
const TRIGGER_LABEL = { scheduled: '定时巡检', manual: '立即检查' }
const STATUS_LABEL = { updated: '有更新', ok: '无更新', baseline: '补建基线', failed: '巡检失败' }
const STATUS_TONE = { updated: '', ok: 'muted', baseline: 'amber', failed: 'danger' }

export default {
    name: 'CheckLogsPage',
    components: { ManagementShell },
    data() {
        return {
            loading: false,
            clearing: false,
            loadError: '',
            source: 'all',
            limit: 200,
            rows: [],
            expanded: {},
            sourceOptions: [
                { label: '全部来源', value: 'all' },
                { label: '电影港', value: 'dygangs' },
                { label: '电影天堂', value: 'dytt' }
            ]
        }
    },
    computed: {
        updatedCount() {
            return this.rows.filter((row) => row.status === 'updated').length
        },
        failedCount() {
            return this.rows.filter((row) => row.status === 'failed').length
        }
    },
    mounted() {
        this.load()
    },
    methods: {
        selectSource(value) {
            this.source = value
            this.load()
        },
        sourceLabel(source) {
            return SOURCE_LABEL[source] || source || '—'
        },
        triggerLabel(trigger) {
            return TRIGGER_LABEL[trigger] || trigger || '—'
        },
        statusLabel(status) {
            return STATUS_LABEL[status] || status || '—'
        },
        statusTone(status) {
            return STATUS_TONE[status] || 'muted'
        },
        hasUpdate(row) {
            return Number(row.foundCount || 0) > 0 || Number(row.submittedCount || 0) > 0
        },
        durationText(ms) {
            const value = Number(ms || 0)
            if (!value) return '—'
            if (value < 1000) return `${value} ms`
            if (value < 60000) return `${(value / 1000).toFixed(1)} s`
            return `${Math.floor(value / 60000)} 分 ${Math.round((value % 60000) / 1000)} 秒`
        },
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
        async load() {
            this.loading = true
            try {
                const list = await GetSubscriptionCheckLogs(this.limit, this.source === 'all' ? '' : this.source)
                this.rows = Array.isArray(list) ? list : []
                this.loadError = ''
            } catch (error) {
                this.loadError = '巡检记录加载失败，请刷新重试。'
            } finally {
                this.loading = false
            }
        },
        clearRecords() {
            this.$dialog.warning({
                title: '清空巡检记录',
                content: '将删除全部巡检历史，订阅本身与下载任务不受影响。确认清空？',
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.clearing = true
                    try {
                        const result = await ClearSubscriptionCheckLogs()
                        this.$message.success((result && result.message) || '已清空巡检记录')
                        await this.load()
                    } catch (error) {
                        // 拦截器已提示
                    } finally {
                        this.clearing = false
                    }
                }
            })
        }
    }
}
</script>

<style scoped>
.header-icon {
    flex: none;
    width: 38px;
    height: 38px;
    border: 0;
    border-radius: 12px;
    background: none;
    color: var(--danger);
    font-size: 15px;
    cursor: pointer;
}

.header-icon:hover {
    background: #FBEEF0;
}

.stats-row {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
    margin-bottom: 12px;
}

.stat-block {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 12px;
    border-radius: 18px;
    border: 1px solid var(--line);
    background: #fff;
}

.stat-block span {
    font-size: 11px;
    color: var(--muted);
}

.stat-block strong {
    font-size: 22px;
    line-height: 28px;
    font-weight: 600;
    color: var(--ink);
}

.stat-block strong.danger {
    color: var(--danger);
}

.stat-block em {
    font-style: normal;
    font-size: 10px;
    color: var(--muted);
    line-height: 1.4;
}

.section-tabs {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
    overflow-x: auto;
    scrollbar-width: none;
}

.section-tabs::-webkit-scrollbar {
    display: none;
}

.section-tab {
    flex: none;
    padding: 7px 16px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: #fff;
    color: var(--muted);
    font-family: inherit;
    font-size: 12.5px;
    cursor: pointer;
}

.section-tab.active {
    background: var(--soft-green);
    border-color: transparent;
    color: var(--pine);
    font-weight: 600;
}

.log-card {
    padding: 14px 16px;
}

.log-head {
    display: flex;
    align-items: center;
    gap: 8px;
}

.log-title {
    flex: 1;
    min-width: 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.log-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
}

.log-time {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-top: 9px;
    font-size: 11px;
    color: var(--muted);
}

.log-message {
    margin: 8px 0 0;
    font-size: 12px;
    color: var(--ink);
    line-height: 1.55;
    overflow-wrap: anywhere;
}

.log-details {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid var(--line);
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.detail-line {
    font-size: 11.5px;
    color: var(--muted);
    line-height: 1.6;
    overflow-wrap: anywhere;
}
</style>
