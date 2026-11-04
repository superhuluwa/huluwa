<template>
    <ManagementShell kind="novel4hu" title="4hu 小说下载" subtitle="一键下载分类全部小说，实时查看进度"
        :loading="loadingCategory" :busy="Boolean(task && task.running)" @refresh="loadCategory">
        <div class="mgmt-content novel-page">
            <section class="hero">
                <div>
                    <span class="eyebrow">4HU · NOVEL ARCHIVE</span>
                    <h1>一键下载全部小说</h1>
                    <p>选择分类即可下载其下全部小说。正文按「书名 / 章节.txt」保存，已有非空文件自动跳过，可随时续传。</p>
                </div>
                <span class="hero-mark"><i class="fa-solid fa-book-open"></i></span>
            </section>

            <section class="panel picker">
                <div class="field-label">小说分类</div>
                <n-select v-model:value="categoryId" :options="categoryOptions" :loading="loadingCategories"
                    placeholder="选择分类" size="large" @update:value="loadCategory" />
                <div class="field-label output-label">保存位置</div>
                <n-input v-model:value="outputDir" clearable size="large"
                    placeholder="留空：数据目录/4hu-novels/分类名" />
                <p class="hint">也可以填写数据目录下的绝对路径；不允许写到数据目录之外。正文按「书名/章节_章节ID.txt」保存。</p>
            </section>

            <section v-if="category" class="summary-grid">
                <div class="summary-card featured">
                    <span>当前分类</span>
                    <strong>{{ category.name }}</strong>
                    <em>分类 ID {{ category.id }}</em>
                </div>
                <div class="summary-card">
                    <span>总章节</span>
                    <strong>{{ category.total.toLocaleString() }}</strong>
                    <em>{{ category.lastPage }} 页 · 每页 {{ category.perPage }} 章</em>
                </div>
                <div class="summary-card">
                    <span>保存路径</span>
                    <strong class="path-text" :title="task?.outputDir || defaultOutputHint">{{ task?.outputDir || defaultOutputHint }}</strong>
                    <em>{{ task?.outputDir ? '本次任务目录' : '启动后显示实际目录' }}</em>
                </div>
            </section>

            <section v-if="category" class="preview-panel panel">
                <div class="panel-head">
                    <div>
                        <strong>最新章节</strong>
                        <span>仅预览第 1 页，不会下载正文</span>
                    </div>
                    <span class="count-pill">{{ category.preview?.length || 0 }} 条</span>
                </div>
                <div v-for="chapter in category.preview" :key="chapter.id" class="chapter-row">
                    <span class="chapter-id">#{{ chapter.id }}</span>
                    <div class="chapter-main">
                        <strong>{{ chapter.title }}</strong>
                        <span>{{ chapter.bookTitle }} · {{ chapter.chapterTitle }}</span>
                    </div>
                    <span class="chapter-date">{{ chapter.date.slice(0, 10) }}</span>
                </div>
            </section>

            <section v-if="task" class="task-panel panel">
                <div class="panel-head">
                    <div>
                        <strong>当前下载进度</strong>
                        <span>{{ taskStateLabel }} · 已发现 {{ task.discoveredBooks || 0 }} 本小说 · {{ task.downloadedChapters }} 下载 / {{ task.skippedChapters }} 跳过 / {{ task.failedChapters }} 失败</span>
                    </div>
                    <span class="state-pill" :class="task.state">{{ taskStateLabel }}</span>
                </div>
                <div class="current-book" v-if="task.currentBook">
                    <span class="current-label">{{ task.running ? '正在处理' : '最后处理' }}</span>
                    <div class="current-main">
                        <strong>{{ task.currentBook }}</strong>
                        <span>{{ task.currentChapter || '等待章节信息' }}</span>
                    </div>
                </div>
                <div class="page-progress">
                    <div class="page-progress-row">
                        <span>当前下载</span>
                        <strong>第 {{ task.currentPage || 0 }} 页 / 共 {{ task.totalPages || 0 }} 页</strong>
                    </div>
                    <div class="page-progress-row">
                        <span>当前页章节</span>
                        <strong>{{ task.currentPageProcessed || 0 }} / {{ task.currentPageTotal || 0 }} 章</strong>
                    </div>
                </div>
                <div class="progress-track"><span :style="{ width: progressWidth }"></span></div>
                <div class="progress-meta">
                    <strong>全部章节 {{ Number(task.processedChapters || 0).toLocaleString() }} / {{ Number(task.totalChapters || 0).toLocaleString() }} 章</strong>
                    <span class="percent-badge">{{ progressPercent }}%</span>
                </div>
                <div class="progress-detail">
                    <span>剩余 {{ Math.max(0, Number(task.totalChapters || 0) - Number(task.processedChapters || 0)).toLocaleString() }} 章</span>
                    <span>已发现 {{ task.discoveredBooks || 0 }} 本小说</span>
                </div>
                <p v-if="task.error" class="notice danger">{{ task.error }}</p>
                <ul v-if="task.recentFailures?.length" class="failures">
                    <li v-for="failure in task.recentFailures" :key="failure">{{ failure }}</li>
                </ul>
                <div class="task-actions">
                    <n-button v-if="task.running" type="warning" secondary :loading="cancelling" @click="cancelDownload">
                        取消任务
                    </n-button>
                    <n-button v-else type="primary" :loading="starting" @click="startDownload">
                        再次检查并补齐
                    </n-button>
                </div>
            </section>

            <div class="action-row">
                <n-button type="primary" size="large" :loading="starting" :disabled="!category || checkingTask || Boolean(task && task.running)"
                    @click="startDownload">
                    <template #icon><i class="fa-solid fa-download"></i></template>
                    {{ checkingTask ? '正在检查任务…' : (task && task.running ? '正在一键下载…' : '一键下载全部小说') }}
                </n-button>
                <span class="action-note">下载全部小说并保留分类/书名目录；已保存章节会跳过，可放心重复点击补齐。</span>
            </div>
        </div>
    </ManagementShell>
</template>

<script>
/**
 * 4hu 小说下载（网页端补充工具）：
 *  - 选分类后调用后端统计总章数并展示第一页预览；
 *  - 下载是后台任务，前端轮询进度；
 *  - 后端按章节 ID 检查目标文件，非空即跳过，重复执行用于断点补齐。
 */
import { NButton, NInput, NSelect } from 'naive-ui'
import ManagementShell from '@/components/main/management-shell.vue'
import {
    getNovel4huCategories, getNovel4huCategory, startNovel4huDownload,
    getNovel4huTask, getNovel4huTasks, cancelNovel4huTask
} from '@/api/novel4hu'

const ACTIVE_TASK_KEY = 'huluwa.4hu.activeTask'
const STATE_LABEL = {
    running: '进行中', cancelling: '正在取消', done: '已完成', failed: '失败', cancelled: '已取消'
}

export default {
    name: 'Novel4huPage',
    components: { ManagementShell, NButton, NInput, NSelect },
    data() {
        return {
            categories: [],
            outputRoot: '',
            categoryId: null,
            category: null,
            outputDir: '',
            task: null,
            loadingCategories: true,
            loadingCategory: false,
            checkingTask: false,
            starting: false,
            cancelling: false,
            timer: null
        }
    },
    computed: {
        categoryOptions() {
            return this.categories.map(item => ({
                label: `${item.name}（${item.id}）`,
                value: item.id
            }))
        },
        defaultOutputHint() {
            if (this.outputRoot && this.category) return `${this.outputRoot}/${this.category.name}`
            return this.category ? `数据目录 / 4hu-novels / ${this.category.name}` : '数据目录 / 4hu-novels'
        },
        progressPercent() {
            if (!this.task) return 0
            if (this.task.totalChapters <= 0) return 0
            return Math.min(100, Math.round(this.task.processedChapters * 100 / this.task.totalChapters))
        },
        progressWidth() {
            return `${this.progressPercent}%`
        },
        taskStateLabel() {
            return (this.task && STATE_LABEL[this.task.state]) || '—'
        }
    },
    async mounted() {
        await this.loadCategories()
        await this.restoreFromServer()
    },
    beforeUnmount() {
        this.stopPolling()
    },
    methods: {
        async loadCategories() {
            this.loadingCategories = true
            try {
                const result = await getNovel4huCategories()
                this.categories = Array.isArray(result) ? result : (result.categories || [])
                this.outputRoot = (result && result.root) || ''
                if (!this.categoryId && this.categories.length) this.categoryId = this.categories[0].id
                if (this.categoryId) await this.loadCategory()
            } catch (error) {
                // http 拦截器已提示
            } finally {
                this.loadingCategories = false
            }
        },
        async loadCategory() {
            if (!this.categoryId) return
            this.loadingCategory = true
            try {
                this.category = await getNovel4huCategory(this.categoryId)
                await this.restoreFromServer()
            } catch (error) {
                this.category = null
            } finally {
                this.loadingCategory = false
            }
        },
        async startDownload() {
            if (!this.categoryId || this.starting) return
            this.starting = true
            try {
                const status = await startNovel4huDownload(this.categoryId, this.outputDir)
                const resumed = Boolean(status.taskId && this.task && this.task.taskId === status.taskId)
                this.task = status
                localStorage.setItem(ACTIVE_TASK_KEY, status.taskId)
                if (status.running) this.startPolling()
                this.$message.success(resumed ? `已继续显示 ${status.categoryName} 的下载进度` : `已启动 ${status.categoryName} 的一键下载任务`)
            } catch (error) {
                // http 拦截器已提示
            } finally {
                this.starting = false
            }
        },
        async cancelDownload() {
            if (!this.task || this.cancelling) return
            this.cancelling = true
            try {
                this.task = await cancelNovel4huTask(this.task.taskId)
                this.$message.info('已请求取消任务，等待当前章节处理完成')
                this.startPolling()
            } catch (error) {
                // http 拦截器已提示
            } finally {
                this.cancelling = false
            }
        },
        startPolling() {
            this.stopPolling()
            this.timer = setInterval(() => this.pollTask(), 1500)
        },
        stopPolling() {
            if (this.timer) clearInterval(this.timer)
            this.timer = null
        },
        async pollTask() {
            if (!this.task) return
            try {
                this.task = await getNovel4huTask(this.task.taskId)
                if (!this.task.running) {
                    this.stopPolling()
                    localStorage.removeItem(ACTIVE_TASK_KEY)
                    this.outputDir = this.outputDir || this.task.outputDir || ''
                    if (this.task.state === 'done') this.$message.success('章节下载任务已完成')
                    else if (this.task.state === 'failed') this.$message.error(this.task.error || '下载任务失败')
                }
            } catch (error) {
                this.stopPolling()
            }
        },
        /**
         * 刷新页面后不能依赖 localStorage：它只保存“上次打开的任务”，
         * 换浏览器/清缓存或任务由其他页面启动时会丢失展示。
         * 这里以服务端任务列表为准，优先运行中的任务，其次最近结束的任务。
         */
        async restoreFromServer() {
            this.checkingTask = true
            try {
                const tasks = await getNovel4huTasks()
                const list = Array.isArray(tasks) ? tasks : []
                if (!list.length) {
                    this.task = null
                    return
                }
                const matches = list.filter(item => !this.categoryId || item.categoryId === this.categoryId)
                const candidates = matches.length ? matches : list
                const selected = candidates.find(item => item.running)
                    || candidates.find(item => this.task && item.taskId === this.task.taskId)
                    || candidates[0]
                if (!selected) return
                this.task = selected
                if (selected.categoryId && selected.categoryId !== this.categoryId) {
                    // 恢复的是其他分类的任务时，同步下拉框；无需再次请求详情。
                    this.categoryId = selected.categoryId
                    this.category = this.categories.find(item => item.id === selected.categoryId) || this.category
                }
                if (selected.running) {
                    this.startPolling()
                } else {
                    this.stopPolling()
                    this.outputDir = this.outputDir || selected.outputDir || ''
                }
                localStorage.setItem(ACTIVE_TASK_KEY, selected.taskId)
            } catch (error) {
                // 恢复失败不阻塞分类查询；页面仍可重新启动或刷新。
            } finally {
                this.checkingTask = false
            }
        }
    }
}
</script>

<style scoped>
/* 外层 ManagementShell 已统一留白，这里只补上下微调 */
.novel-page {
    padding-top: 2px;
}

.hero {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 22px;
    margin-bottom: 14px;
    border-radius: 22px;
    background: linear-gradient(135deg, var(--soft-green), #f8f4e9);
    overflow: hidden;
}

.hero h1 {
    margin: 8px 0 7px;
    font-family: Georgia, "Songti SC", serif;
    font-size: 28px;
    font-weight: 600;
    color: var(--ink);
}

.hero p {
    margin: 0;
    max-width: 560px;
    color: var(--muted);
    font-size: 13px;
    line-height: 1.7;
}

.eyebrow {
    color: var(--pine);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: .16em;
}

.hero-mark {
    flex: none;
    display: grid;
    place-items: center;
    width: 92px;
    height: 92px;
    border-radius: 50%;
    background: rgba(255, 255, 255, .58);
    color: var(--pine);
    font-size: 38px;
}

.picker {
    padding: 18px;
    margin-bottom: 14px;
}

.field-label {
    margin: 0 0 7px 2px;
    color: var(--muted);
    font-size: 11px;
    letter-spacing: .08em;
}

.output-label {
    margin-top: 16px;
}

.hint {
    margin: 8px 2px 0;
    color: var(--muted);
    font-size: 11px;
}

.path-inline {
    margin-left: 6px;
    color: var(--pine);
    font-size: 10px;
    letter-spacing: 0;
    overflow-wrap: anywhere;
}

.summary-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
    margin-bottom: 14px;
}

.summary-card {
    display: flex;
    flex-direction: column;
    gap: 5px;
    min-height: 112px;
    padding: 16px;
    border: 1px solid var(--line);
    border-radius: 18px;
    background: #fff;
}

.summary-card.featured {
    border-color: transparent;
    background: var(--soft-green);
}

.summary-card span,
.summary-card em {
    color: var(--muted);
    font-size: 11px;
    font-style: normal;
}

.summary-card strong {
    color: var(--ink);
    font-size: 22px;
    font-weight: 600;
    line-height: 1.35;
    overflow-wrap: anywhere;
}

.summary-card strong.path-text {
    font-size: 14px;
    font-weight: 500;
}

.preview-panel,
.task-panel {
    padding: 0;
    margin-bottom: 14px;
    overflow: hidden;
}

.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 15px 18px;
    border-bottom: 1px solid var(--line);
}

.panel-head > div {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.panel-head strong {
    color: var(--ink);
    font-size: 14px;
}

.panel-head span {
    color: var(--muted);
    font-size: 11px;
}

.count-pill,
.state-pill {
    flex: none;
    padding: 4px 10px;
    border-radius: 999px;
    background: var(--soft-green);
    color: var(--pine) !important;
    font-weight: 600;
}

.state-pill.failed {
    background: #fbece9;
    color: var(--danger) !important;
}

.state-pill.done {
    background: var(--soft-green);
}

.chapter-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 18px;
    border-top: 1px solid var(--line);
}

.chapter-row:first-of-type {
    border-top: 0;
}

.chapter-id {
    flex: none;
    width: 62px;
    color: var(--muted);
    font-size: 11px;
}

.chapter-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.chapter-main strong {
    color: var(--ink);
    font-size: 13px;
    font-weight: 500;
    overflow-wrap: anywhere;
}

.chapter-main span,
.chapter-date {
    color: var(--muted);
    font-size: 10px;
    overflow-wrap: anywhere;
}

.chapter-date {
    flex: none;
}

.current-book {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    margin: 14px 18px 0;
    padding: 11px 13px;
    border-radius: 14px;
    background: var(--soft-green);
}

.current-label {
    flex: none;
    padding: 2px 7px;
    border-radius: 999px;
    background: var(--pine);
    color: #fff;
    font-size: 10px;
}

.current-main {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.current-main strong,
.current-main span {
    overflow-wrap: anywhere;
}

.current-main strong {
    color: var(--ink);
    font-size: 13px;
}

.current-main span {
    color: var(--muted);
    font-size: 11px;
}

.progress-detail {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 7px 18px 0;
    color: var(--muted);
    font-size: 10px;
}

.page-progress {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin: 14px 18px 0;
}

.page-progress-row {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 10px 12px;
    border-radius: 13px;
    background: var(--paper);
}

.page-progress-row span {
    color: var(--muted);
    font-size: 10px;
}

.page-progress-row strong {
    color: var(--ink);
    font-size: 13px;
    font-weight: 600;
}

.progress-track {
    height: 8px;
    margin: 18px 18px 0;
    border-radius: 999px;
    background: var(--soft-green);
    overflow: hidden;
}

.progress-track span {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, var(--pine), var(--lime));
    transition: width .3s ease;
}

.progress-meta {
    display: flex;
    justify-content: space-between;
    padding: 8px 18px 0;
    color: var(--muted);
    font-size: 11px;
}

.progress-meta strong {
    color: var(--ink);
}

.percent-badge {
    color: var(--pine);
    font-size: 15px;
    font-weight: 700;
}

.notice,
.failures {
    margin: 12px 18px 0;
    font-size: 12px;
}

.notice.danger {
    color: var(--danger);
}

.failures {
    padding-left: 18px;
    color: var(--muted);
    line-height: 1.7;
}

.task-actions {
    display: flex;
    justify-content: flex-end;
    padding: 14px 18px 18px;
}

.action-row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 2px 0 10px;
}

.action-note {
    color: var(--muted);
    font-size: 11px;
}

@media (max-width: 720px) {
    .novel-page { padding-top: 2px; }
    .hero { padding: 18px; }
    .hero h1 { font-size: 24px; }
    .hero-mark { width: 64px; height: 64px; font-size: 26px; }
    .summary-grid { grid-template-columns: 1fr; }
    .summary-card { min-height: 0; }
    .chapter-date { display: none; }
    .chapter-id { width: 52px; }
    .action-row { align-items: stretch; flex-direction: column; }
    .page-progress { grid-template-columns: 1fr; }
}
</style>
