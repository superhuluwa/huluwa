<template>
    <div class="downloads-page">
        <!-- 顶部速览卡：当前总速度 + 进行中 / 等待 / 已完成（对齐安卓 DownloadHero） -->
        <section class="download-hero">
            <span class="hero-label">当前下载速度</span>
            <div class="hero-speed">
                <strong>{{ totalSpeed > 0 ? formatBytes(totalSpeed) : '待同步' }}</strong>
                <span v-if="totalSpeed > 0">/s</span>
            </div>
            <div class="hero-divider"></div>
            <div class="hero-metrics">
                <span><b>{{ activeCount }}</b> 个任务正在下载</span>
                <span><b>{{ waitingCount }}</b> 个等待中</span>
                <span><b>{{ completedCount }}</b> 个已完成</span>
            </div>
        </section>

        <!-- 状态筛选 -->
        <div class="filter-row">
            <button v-for="(item, index) in statusFilters" :key="item.label" type="button" class="filter-chip"
                :class="{ active: filter === index }" @click="filter = index">{{ item.label }}</button>
        </div>

        <!-- 三个来源合并为一份列表，不再按来源分区或筛选；来源由卡片上的标签区分 -->
        <div class="section-head">
            <h2>下载任务</h2>
            <div class="section-actions">
                <button class="tool-btn" type="button" title="刷新下载" :disabled="loading" @click="load">
                    <i class="fa-solid fa-rotate" :class="{ 'fa-spin': loading }"></i>
                </button>
                <div class="more-wrap">
                    <button class="tool-btn" type="button" title="下载操作" @click="menu = !menu">
                        <i class="fa-solid fa-ellipsis"></i>
                    </button>
                    <div v-if="menu" class="more-menu">
                        <button type="button" @click="openSettings('dytt')">离线下载设置</button>
                        <button type="button" @click="openSettings('magnet')">qBittorrent 设置</button>
                        <button type="button" @click="openSettings('xunlei-win')">迅雷【Win】设置</button>
                        <button v-for="type in sources" :key="type.key" type="button" @click="clearRecords(type)">
                            清空{{ type.label }}记录
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <p v-if="error" class="load-error">{{ error }}</p>

        <div v-if="!tasks.length" class="empty-state">
            <span class="empty-icon"><i class="fa-solid fa-file-arrow-down"></i></span>
            <strong>{{ all.length ? '当前筛选下暂无任务' : '这里还没有下载任务' }}</strong>
            <span>{{ all.length ? '可以切换状态查看其他下载任务' : '订阅追更或提交磁力链接后，可在这里查看进度' }}</span>
        </div>

        <article v-for="task in tasks" :key="task.key" class="task-card"
            :class="{ failed: task.status === 'failed', done: task.completed }">
            <div class="task-head">
                <span class="task-icon" :class="{ blue: task.source !== 'Hls' }">
                    <i :class="task.source === 'Hls' ? 'fa-solid fa-file-video' : 'fa-solid fa-cloud-arrow-down'"></i>
                </span>
                <div class="task-title">
                    <strong>{{ task.title }}</strong>
                    <!-- 来源统一用标签标注，不再和集数拼成一行文字 -->
                    <div class="task-tags">
                        <span class="source-tag" :class="sourceTone(task.source)">{{ sourceLabel(task.source) }}</span>
                        <span v-if="task.episode" class="task-episode">{{ task.episode }}</span>
                    </div>
                </div>
                <div class="more-wrap">
                    <button class="icon-btn" type="button" title="任务操作" @click="toggleTaskMenu(task.key)">
                        <i class="fa-solid fa-ellipsis"></i>
                    </button>
                    <div v-if="taskMenu === task.key" class="more-menu right">
                        <button v-if="task.cancellable" type="button" @click="cancelDownload(task)">取消下载</button>
                        <button v-if="task.retryable" type="button" @click="retryDownload(task)">
                            {{ task.status === 'skipped' ? '补下载' : '重新下载' }}
                        </button>
                        <button v-if="task.source === 'Hls'" type="button" @click="showLog(task)">查看日志</button>
                        <button type="button" @click="showDetail(task)">详细信息</button>
                    </div>
                </div>
            </div>

            <div class="progress-track">
                <i :style="{ width: progressPercent(task) + '%' }" :class="{ danger: task.status === 'failed' }"></i>
            </div>
            <div class="progress-line">
                <span :class="{ danger: task.status === 'failed' }">{{ progressText(task) }}</span>
                <b v-if="task.progress != null" :class="{ danger: task.status === 'failed' }">{{ Math.round(task.progress) }}%</b>
            </div>
            <div class="task-foot">
                <span>{{ task.paused ? '订阅已暂停' : task.message ? '处理异常' : statusLabel(task.status) }}</span>
                <span v-if="task.speed" class="speed">{{ task.speed }}</span>
            </div>
            <p v-if="task.message" class="task-message" :class="{ danger: task.status === 'failed' }">{{ task.message }}</p>
        </article>

        <ServiceSettingsSheet v-model:show="settingsOpen" :kind="settingsKind" @saved="load" />

        <n-modal v-model:show="logOpen" preset="card" title="下载日志" style="width: 760px; max-width: calc(100vw - 32px)">
            <pre class="log-content">{{ logText || '暂无日志' }}</pre>
        </n-modal>

        <n-modal v-model:show="detailOpen" preset="card" :title="detailTitle" style="width: 520px">
            <div class="detail-list">
                <div v-for="line in detailLines" :key="line.label" class="detail-row">
                    <span>{{ line.label }}</span>
                    <b>{{ line.value }}</b>
                </div>
            </div>
        </n-modal>
    </div>
</template>

<script>
/**
 * 下载标签页（对齐安卓 DownloadsScreen）：
 *  - 三个来源（离线剧集 / 磁力 / 迅雷【Win】）聚合为一份列表，来源失败时保留其余来源并给出提示；
 *  - 状态筛选（进行中 / 已完成 / 全部）+ 来源筛选；
 *  - 卡片包含进度条、速度、状态与失败原因，操作收进「更多」菜单；
 *  - 页面可见时按来源节奏轮询（HLS / 迅雷 每秒，磁力 5 秒）。
 */
import { NModal, NSpin } from 'naive-ui'
import { GetHlsDownloads, CancelHlsDownload, RetryHlsDownload, GetHlsDownloadLog, ClearHlsDownloads } from '@/api/hls'
import { SubscriptionDownloads, ClearSubscriptionDownloads } from '@/api/chat'
import { GetXunleiWinDownloads, ClearXunleiWinDownloads } from '@/api/xunlei-win'
import { formatBytes } from '@/utils/format'
import ServiceSettingsSheet from '@/views/message/components/ServiceSettingsSheet.vue'

/** 统一下载状态文案（与安卓 Models.kt statusLabel 一致） */
const STATUS_LABEL = {
    pending: '等待开始',
    added: '等待开始',
    queued: '等待开始',
    submitted: '等待开始',
    downloading: '下载中',
    running: '下载中',
    verifying: '校验中',
    completed: '已完成',
    complete: '已完成',
    finished: '已完成',
    success: '已完成',
    seeding: '已完成',
    uploading: '已完成',
    failed: '失败',
    error: '失败',
    cancelled: '已取消',
    canceled: '已取消',
    skipped: '已跳过',
    paused: '已暂停',
    stopped: '已暂停'
}

function statusLabel(value) {
    return STATUS_LABEL[String(value || '').toLowerCase()] || '等待同步'
}

const SOURCES = [
    { key: 'Hls', label: '离线剧集' },
    { key: 'Magnet', label: '磁力下载' },
    { key: 'XunleiWin', label: '迅雷【Win】' }
]

/** 每种来源的状态字段略有差异，这里统一成展示模型 */
function normalize(source, raw) {
    const status = String(raw.status || raw.state || 'unknown').toLowerCase()
    const completed = ['completed', 'finished', 'complete', 'success', 'seeding', 'uploading'].includes(status)
    const episode = raw.episodeTitle
        || (raw.episode ? (source === 'Hls' ? `第 ${raw.episode} 集` : String(raw.episode)) : '')
    let progress = raw.progressPercent
    if (progress == null && typeof raw.progress === 'number') {
        progress = raw.progress
    }
    if (completed) {
        progress = 100
    }
    const speed = raw.downloadSpeed != null
        ? (typeof raw.downloadSpeed === 'number' ? `${formatBytes(raw.downloadSpeed)}/s` : String(raw.downloadSpeed))
        : (raw.speedText || '')
    return {
        key: `${source}:${raw.id}`,
        id: raw.id,
        source,
        title: raw.seriesTitle || raw.name || raw.title || '下载任务',
        episode,
        status,
        progress: progress == null ? null : Math.max(0, Math.min(100, Number(progress))),
        speed,
        downloaded: raw.receivedBytes ?? raw.downloadedBytes ?? raw.downloadedSize,
        total: raw.totalBytes ?? raw.totalSize,
        savePath: raw.savePath || raw.path || '',
        message: raw.message || raw.queryError || raw.errorMessage || '',
        createdAt: raw.createdAt || '',
        paused: Boolean(raw.paused),
        completed,
        active: ['pending', 'added', 'submitted', 'queued', 'downloading', 'verifying', 'running', 'paused'].includes(status),
        retryable: source === 'Hls' && ['failed', 'cancelled', 'skipped'].includes(status) && !raw.paused,
        cancellable: source === 'Hls' && ['pending', 'downloading', 'verifying'].includes(status)
    }
}

function speedBytes(text) {
    const match = /([0-9]+(?:\.[0-9]+)?)\s*([kmgt]?b)(?:\/s)?/i.exec(String(text || '').trim())
    if (!match) {
        return 0
    }
    const amount = Number(match[1]) || 0
    const unit = match[2].toLowerCase()
    const multiplier = { kb: 1024, mb: 1024 ** 2, gb: 1024 ** 3, tb: 1024 ** 4 }[unit] || 1
    return amount * multiplier
}

export default {
    name: 'DownloadsPage',
    components: { NModal, NSpin, ServiceSettingsSheet },
    data() {
        return {
            sources: SOURCES,
            all: [],
            loading: false,
            error: '',
            filter: 0,
            menu: false,
            taskMenu: '',
            settingsOpen: false,
            settingsKind: 'dytt',
            logOpen: false,
            logText: '',
            detailOpen: false,
            detailTitle: '',
            detailLines: [],
            statusFilters: [
                { label: '进行中', match: (task) => task.active },
                { label: '已完成', match: (task) => task.completed },
                { label: '全部任务', match: () => true }
            ],
            timers: []
        }
    },
    computed: {
        /** 三个来源合并为一个列表，只按状态筛选 */
        tasks() {
            const match = this.statusFilters[this.filter].match
            return this.all.filter(match)
        },
        activeCount() {
            return this.all.filter((task) => task.active).length
        },
        waitingCount() {
            return this.all.filter((task) => task.active && (task.progress == null || task.progress <= 0)).length
        },
        completedCount() {
            return this.all.filter((task) => task.completed).length
        },
        totalSpeed() {
            return this.all.filter((task) => task.active).reduce((sum, task) => sum + speedBytes(task.speed), 0)
        }
    },
    watch: {
        filter() {
            this.filter = Number(this.filter)
        }
    },
    mounted() {
        document.addEventListener('click', this.onDocumentClick)
        this.load()
        // 离线剧集与迅雷每秒刷新，磁力每 5 秒（与安卓的轮询节奏一致）
        this.timers = [
            setInterval(() => this.load(true, ['Hls', 'XunleiWin']), 1000),
            setInterval(() => this.load(true, ['Magnet']), 5000)
        ]
    },
    activated() {
        this.load()
    },
    beforeUnmount() {
        document.removeEventListener('click', this.onDocumentClick)
        this.timers.forEach(clearInterval)
        this.timers = []
    },
    methods: {
        sourceLabel(key) {
            const matched = SOURCES.find((item) => item.key === key)
            return matched ? matched.label : key
        },
        /** 来源标签配色：离线剧集松柏绿、磁力蓝、迅雷【Win】紫，与卡片左侧图标一致 */
        sourceTone(key) {
            if (key === 'Hls') return 'pine'
            return key === 'XunleiWin' ? 'lilac' : 'blue'
        },
        statusLabel,
        formatBytes,
        progressPercent(task) {
            if (task.completed) {
                return 100
            }
            return task.progress == null ? 0 : task.progress
        },
        progressText(task) {
            if (task.total != null || task.downloaded != null) {
                return `${formatBytes(task.downloaded)} / ${formatBytes(task.total)}`
            }
            if (task.paused) {
                return '订阅已暂停'
            }
            return statusLabel(task.status)
        },
        async load(quiet = false, only = null) {
            if (!quiet) {
                this.loading = true
                this.error = ''
            }
            const wanted = only ? new Set(only) : null
            const warnings = []
            const settled = await Promise.allSettled([
                wanted && !wanted.has('Hls') ? Promise.resolve(null) : GetHlsDownloads(),
                wanted && !wanted.has('Magnet') ? Promise.resolve(null) : SubscriptionDownloads(0, { refresh: true, quiet }),
                wanted && !wanted.has('XunleiWin') ? Promise.resolve(null) : GetXunleiWinDownloads(quiet)
            ])
            const [hls, magnet, xunlei] = settled
            const collect = (result, source) => {
                if (result.status !== 'fulfilled') {
                    // 局部刷新失败时保留旧数据；首次加载失败给出提示
                    if (wanted) {
                        return this.all.filter((task) => task.source === source)
                    }
                    warnings.push(`${this.sourceLabel(source)}记录暂时无法更新`)
                    return this.all.filter((task) => task.source === source)
                }
                if (result.value == null) {
                    return this.all.filter((task) => task.source === source)
                }
                const rows = Array.isArray(result.value) ? result.value : []
                return rows.map((raw) => normalize(source, raw))
            }
            const merged = [
                ...collect(hls, 'Hls'),
                ...collect(magnet, 'Magnet'),
                ...collect(xunlei, 'XunleiWin')
            ].sort((left, right) => String(right.createdAt).localeCompare(String(left.createdAt)))
            this.all = merged
            this.error = warnings.join('；')
            if (!quiet) {
                this.loading = false
            }
        },
        statusLabelOf(task) {
            return statusLabel(task.status)
        },        toggleTaskMenu(key) {
            this.taskMenu = this.taskMenu === key ? '' : key
        },
        onDocumentClick(event) {
            if (!event.target.closest?.('.more-wrap')) {
                this.menu = false
                this.taskMenu = ''
            }
        },
        openSettings(kind) {
            this.menu = false
            this.settingsKind = kind
            this.settingsOpen = true
        },
        async showLog(task) {
            this.taskMenu = ''
            try {
                const data = await GetHlsDownloadLog(task.id)
                this.logText = (data && data.content) || ''
                this.logOpen = true
            } catch (error) {
                // 拦截器已提示
            }
        },
        showDetail(task) {
            this.taskMenu = ''
            this.detailTitle = task.title
            this.detailLines = [
                { label: '状态', value: statusLabel(task.status) },
                { label: '集数', value: task.episode || '—' },
                { label: '保存目录', value: task.savePath || '由下载器管理' },
                { label: '创建时间', value: task.createdAt || '—' },
                { label: '说明', value: task.message || '—' }
            ]
            this.detailOpen = true
        },
        cancelDownload(task) {
            this.taskMenu = ''
            this.$dialog.warning({
                title: '取消下载',
                content: `取消「${task.title} ${task.episode}」后，临时分片会被清理，已归档文件保留。`,
                positiveText: '取消下载',
                negativeText: '继续下载',
                onPositiveClick: async () => {
                    await CancelHlsDownload(task.id)
                    this.$message.success('任务已取消')
                    await this.load()
                }
            })
        },
        retryDownload(task) {
            this.taskMenu = ''
            RetryHlsDownload(task.id)
                .then(() => {
                    this.$message.success('已重新加入下载队列')
                    return this.load()
                })
                .catch(() => {})
        },
        clearRecords(type) {
            this.menu = false
            const content = type.key === 'Hls'
                ? '清空已完成 / 失败 / 跳过 / 已取消的历史记录，进行中的任务保留，已归档文件不受影响。'
                : '清空该来源的全部记录，下载器中的任务仍会继续执行，已下载文件保留。'
            this.$dialog.warning({
                title: `清空${type.label}记录`,
                content,
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: async () => {
                    if (type.key === 'Hls') {
                        await ClearHlsDownloads()
                    } else if (type.key === 'Magnet') {
                        await ClearSubscriptionDownloads()
                    } else {
                        await ClearXunleiWinDownloads()
                    }
                    this.$message.success('记录已清空')
                    await this.load()
                }
            })
        }
    }
}
</script>

<style scoped>
.downloads-page {
    min-height: 100%;
    padding: 4px 22px 28px;
    background: var(--paper);
}

/* ---------- 顶部速度卡（Pine 渐变，对安卓 DownloadHero） ---------- */
.download-hero {
    position: relative;
    overflow: hidden;
    border-radius: 24px;
    padding: 17px 18px;
    color: #fff;
    background: linear-gradient(105deg, #174E3D 0%, #206C54 55%, #2A785F 100%);
    box-shadow: 0 2px 10px rgba(23, 78, 61, .18);
}

.download-hero::after {
    content: "";
    position: absolute;
    top: -82px;
    right: -52px;
    width: 170px;
    height: 170px;
    border-radius: 50%;
    background: rgba(255, 255, 255, .07);
}

.hero-label {
    font-size: 11px;
    color: var(--lime);
}

.hero-speed {
    display: flex;
    align-items: baseline;
    gap: 5px;
    margin-top: 4px;
}

.hero-speed strong {
    font-size: 31px;
    line-height: 34px;
    font-weight: 700;
    letter-spacing: -.8px;
}

.hero-speed span {
    font-size: 11px;
    color: rgba(255, 255, 255, .68);
}

.hero-divider {
    height: 1px;
    margin: 15px 0 12px;
    background: rgba(255, 255, 255, .14);
}

.hero-metrics {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 16px;
    font-size: 11px;
    color: rgba(255, 255, 255, .74);
}

.hero-metrics b {
    color: #fff;
    font-size: 15px;
    margin-right: 3px;
}

/* ---------- 筛选 ---------- */
.filter-row {
    display: flex;
    gap: 8px;
    margin-top: 14px;
    overflow-x: auto;
    scrollbar-width: none;
}

.filter-row::-webkit-scrollbar {
    display: none;
}

.filter-chip {
    flex: none;
    padding: 7px 14px;
    border: 1px solid var(--line);
    border-radius: 999px;
    background: #fff;
    color: var(--muted);
    font-family: inherit;
    font-size: 12.5px;
    cursor: pointer;
}

.filter-chip.active {
    background: var(--soft-green);
    border-color: transparent;
    color: var(--pine);
    font-weight: 600;
}

/* ---------- 列表 ---------- */
.section-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 14px 0 12px;
}

.section-head h2 {
    flex: 1;
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
}

.section-actions {
    display: flex;
    gap: 4px;
}

.tool-btn {
    width: 40px;
    height: 40px;
    border: 1px solid var(--line);
    border-radius: 13px;
    background: #fff;
    color: var(--pine);
    font-size: 14px;
    cursor: pointer;
}

.tool-btn:disabled {
    color: var(--muted);
    opacity: .6;
}

.more-wrap {
    position: relative;
}

.more-menu {
    position: absolute;
    top: 46px;
    right: 0;
    z-index: 30;
    min-width: 172px;
    padding: 6px;
    display: flex;
    flex-direction: column;
    border-radius: 14px;
    background: #fff;
    border: 1px solid var(--line);
    box-shadow: 0 12px 32px rgba(36, 61, 50, .16);
}

.more-menu.right {
    top: 40px;
}

.more-menu button {
    padding: 10px 12px;
    border: 0;
    border-radius: 10px;
    background: none;
    color: var(--ink);
    font-family: inherit;
    font-size: 13px;
    text-align: left;
    cursor: pointer;
    white-space: nowrap;
}

.more-menu button:hover {
    background: var(--soft-green);
}

.load-error {
    margin: 0 0 10px;
    padding: 9px 12px;
    border-radius: 12px;
    background: #F8F0E4;
    color: var(--amber);
    font-size: 12.5px;
}

.empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 44px 20px;
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

.empty-state strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
}

.empty-state span:last-child {
    font-size: 12.5px;
    color: var(--muted);
}

/* ---------- 任务卡（对齐安卓 DownloadCard） ---------- */
.task-card {
    margin-bottom: 12px;
    padding: 16px;
    border-radius: 20px;
    border: 1px solid var(--line);
    background: #fff;
}

.task-card.failed {
    background: #FFFDFD;
    border-color: #F2DDE0;
}

.task-card.done {
    background: #FBFCFA;
}

.task-head {
    display: flex;
    align-items: center;
    gap: 11px;
}

.task-icon {
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 12px;
    background: rgba(32, 108, 84, .10);
    color: var(--pine);
    font-size: 17px;
}

.task-icon.blue {
    background: rgba(78, 120, 167, .12);
    color: var(--blue);
}

.task-title {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.task-title strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

/* 来源标签 + 集数：与安卓卡片上的 Tag 一致 */
.task-tags {
    display: flex;
    align-items: center;
    gap: 7px;
    min-width: 0;
}

.source-tag {
    flex: none;
    padding: 2px 7px;
    border-radius: 7px;
    font-size: 10px;
    font-weight: 500;
    line-height: 14px;
}

.source-tag.pine {
    background: rgba(32, 108, 84, .08);
    color: var(--pine);
}

.source-tag.blue {
    background: rgba(78, 120, 167, .12);
    color: var(--blue);
}

.source-tag.lilac {
    background: rgba(130, 96, 161, .12);
    color: var(--lilac);
}

.task-episode {
    min-width: 0;
    font-size: 11px;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.icon-btn {
    width: 36px;
    height: 36px;
    border: 0;
    border-radius: 12px;
    background: none;
    color: var(--muted);
    font-size: 14px;
    cursor: pointer;
}

.icon-btn:hover {
    background: var(--soft-green);
    color: var(--pine);
}

.progress-track {
    height: 6px;
    margin-top: 12px;
    border-radius: 3px;
    background: var(--soft-green);
    overflow: hidden;
}

.progress-track i {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: var(--pine);
    transition: width .32s ease;
}

.progress-track i.danger {
    background: var(--danger);
}

.progress-line {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
    font-size: 11px;
    color: var(--muted);
}

.progress-line span {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.progress-line b {
    color: var(--pine);
    font-size: 12px;
    font-weight: 600;
}

.progress-line .danger {
    color: var(--danger);
}

.task-foot {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 9px;
    font-size: 11px;
    color: var(--muted);
}

.task-foot span:first-child {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.task-foot .speed {
    flex: none;
    color: var(--pine);
    font-weight: 600;
}

.task-message {
    margin: 8px 0 0;
    font-size: 12px;
    color: var(--muted);
    line-height: 1.55;
    overflow-wrap: anywhere;
}

.task-message.danger {
    color: var(--danger);
}

.log-content {
    max-height: 60vh;
    overflow: auto;
    margin: 0;
    padding: 12px;
    border-radius: 12px;
    background: var(--paper);
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}

.detail-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.detail-row {
    display: flex;
    gap: 12px;
    font-size: 13px;
}

.detail-row span {
    flex: none;
    width: 84px;
    color: var(--muted);
}

.detail-row b {
    min-width: 0;
    font-weight: 500;
    color: var(--ink);
    overflow-wrap: anywhere;
}
</style>
