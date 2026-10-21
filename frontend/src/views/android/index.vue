<template>
    <ManagementShell kind="android-build" title="App 构建管理" subtitle="更新源码 · 编译安装包 · 执行日志"
        :loading="tasks.loading" :busy="Boolean(submitting) || cancelling" @refresh="load">
        <template #actions>
            <button class="header-icon" type="button" title="构建宿主信息" @click="showHostInfo">
                <i class="fa-solid fa-server"></i>
            </button>
        </template>

        <div class="mgmt-content">
            <!-- 速览：构建服务状态 + 源码 / 安装包 / 任务 / 签名 -->
            <section class="hero-panel">
                <div class="hero-head">
                    <i class="fa-brands fa-android"></i>
                    <div class="hero-head-text">
                        <strong>App 构建服务</strong>
                        <span>{{ status.workerOnline ? '构建容器在线' : '构建容器离线' }} · {{ status.branch || 'master' }}</span>
                    </div>
                    <span class="hero-tag" :class="{ alert: !status.workerOnline }">{{ status.workerOnline ? '在线' : '离线' }}</span>
                </div>
                <div class="build-grid">
                    <div class="build-cell">
                        <span>当前源码</span>
                        <strong class="mono">{{ shortCommit || '尚未更新' }}</strong>
                        <em>{{ sourceSubject }}</em>
                    </div>
                    <div class="build-cell">
                        <span>最近构建</span>
                        <strong>{{ status.latestApk ? `v${status.latestApk.versionName}` : '暂无安装包' }}</strong>
                        <em>{{ status.latestApk ? `代码 ${status.latestApk.versionCode} · ${formatBytes(status.latestApk.apkSize)}` : '编译成功后即可下载' }}</em>
                    </div>
                    <div class="build-cell">
                        <span>当前任务</span>
                        <strong>{{ activeTask ? stageLabel(activeTask.stage) : '空闲' }}</strong>
                        <em>{{ activeTask ? `${typeLabel(activeTask.type)} #${activeTask.id} · ${elapsed(activeTask)}` : '就绪后可以开始新的任务' }}</em>
                    </div>
                    <div class="build-cell">
                        <span>签名配置</span>
                        <strong>{{ status.signingReady ? '已配置' : '未配置' }}</strong>
                        <em>{{ status.signingReady ? '可以编译 Release 安装包' : '需先恢复 Release 签名' }}</em>
                    </div>
                </div>
            </section>

            <div v-if="connectionError" class="notice-line">
                <i class="fa-solid fa-triangle-exclamation"></i>暂时无法连接构建服务，正在自动重连。服务器上的任务会继续执行。
            </div>
            <div v-else-if="loaded && !status.enabled" class="notice-line">
                <i class="fa-solid fa-circle-info"></i>安卓构建服务尚未启用，请配置并启动构建容器。
            </div>
            <div v-else-if="loaded && !status.workerOnline" class="notice-line">
                <i class="fa-solid fa-triangle-exclamation"></i>构建容器暂时离线，恢复后即可更新代码和编译。
            </div>

            <!-- 构建操作 -->
            <section class="panel action-panel">
                <strong class="action-title">构建操作</strong>
                <p class="action-tip">先更新代码，再编译 APK；每次编译固定使用当前源码版本。</p>
                <n-button type="primary" size="large" block :loading="submitting === 'UPDATE'" :disabled="!canUpdate"
                    @click="submit('UPDATE')">
                    <template #icon><i class="fa-solid fa-code-pull-request"></i></template>更新代码
                </n-button>
                <n-button type="primary" size="large" block :loading="submitting === 'BUILD'" :disabled="!canBuild"
                    class="action-gap" @click="submit('BUILD')">
                    <template #icon><i class="fa-solid fa-hammer"></i></template>编译 APK
                </n-button>
                <p v-if="reason" class="action-reason">{{ reason }}</p>
                <div class="action-foot">
                    <button class="link-btn" type="button" :disabled="!status.latestApk || downloading"
                        @click="downloadApk(status.latestApk)">
                        <i class="fa-solid fa-download"></i>下载 APK
                    </button>
                    <span class="action-spacer"></span>
                    <button v-if="activeTask" class="link-btn danger" type="button"
                        :disabled="activeTask.status === 'CANCELLING' || cancelling" @click="cancelTask(activeTask)">
                        {{ activeTask.status === 'CANCELLING' ? '正在取消' : '取消任务' }}
                    </button>
                </div>
            </section>

            <!-- 下载提示（浏览器未自动开始下载时的手动链接） -->
            <section v-if="lastDownload" class="panel download-panel">
                <strong>下载已准备好：{{ lastDownload.filename }}</strong>
                <p>如果浏览器没有开始下载，请点击链接或复制到系统浏览器打开；链接 10 分钟内有效。</p>
                <div class="download-links">
                    <a :href="lastDownload.url" :download="lastDownload.filename" target="_blank" rel="noopener noreferrer">点击下载文件</a>
                    <button class="link-btn" type="button" @click="copyLink">复制下载链接</button>
                </div>
            </section>

            <!-- 执行日志 -->
            <section class="panel log-panel">
                <div class="log-head">
                    <strong>执行日志</strong>
                    <span v-if="selectedTask">{{ typeLabel(selectedTask.type) }} #{{ selectedTask.id }}</span>
                    <span v-if="selectedTask" class="chip-tag" :class="taskTone(selectedTask.status)">
                        {{ taskStatusLabel(selectedTask.status) }}
                    </span>
                    <span class="log-spacer"></span>
                    <span v-if="selectedTask" class="log-elapsed">{{ elapsed(selectedTask) }}</span>
                </div>
                <div class="log-actions">
                    <button class="link-btn" type="button" @click="follow = !follow">{{ follow ? '暂停滚动' : '自动滚动' }}</button>
                    <button class="link-btn" type="button" :disabled="!logText" @click="copyLogs">复制</button>
                    <button class="link-btn" type="button" :disabled="!selectedTask || !logText" @click="downloadLog">下载日志</button>
                </div>
                <p v-if="selectedTask && selectedTask.error" class="log-error">{{ selectedTask.error }}</p>
                <pre ref="logBox" class="log-content">{{ logText || (selectedTask ? '等待任务输出日志…' : '开始更新代码或编译后，这里会显示执行日志。') }}</pre>
                <p class="log-foot">{{ logComplete ? '日志已全部加载，完整内容可下载' : '日志每秒增量更新，完整内容可下载' }}</p>
            </section>

            <!-- 任务历史 -->
            <div class="section-title"><h2>任务历史</h2><span class="history-tip">点击任务查看日志</span></div>
            <div v-if="loaded && !tasks.data.length" class="empty-panel">
                <span class="empty-icon"><i class="fa-brands fa-android"></i></span>
                <strong>暂无任务记录</strong>
                <span>先更新代码，再编译安装包</span>
            </div>
            <article v-for="task in tasks.data" :key="task.id" class="panel task-row"
                :class="{ selected: selectedId === task.id }" @click="selectTask(task)">
                <div class="task-head">
                    <span class="task-id">#{{ task.id }}</span>
                    <strong>{{ typeLabel(task.type) }}</strong>
                    <span class="chip-tag" :class="taskTone(task.status)">{{ taskStatusLabel(task.status) }}</span>
                </div>
                <div class="task-meta">
                    <span class="mono">{{ short(task.commit) }}</span>
                    <span v-if="task.type === 'BUILD' && task.versionName">v{{ task.versionName }}（{{ task.versionCode }}）</span>
                    <span class="task-spacer"></span>
                    <span v-if="isDownloadable(task)" class="chip-tag outline">{{ formatBytes(task.apkSize) }}</span>
                </div>
                <div class="task-foot">
                    <span>{{ formatTime(task.startedAt || task.createdAt) }}</span>
                    <span>{{ elapsed(task) }}</span>
                    <span class="task-spacer"></span>
                    <button v-if="isDownloadable(task)" class="link-btn" type="button" @click.stop="downloadApk(task)">
                        <i class="fa-solid fa-download"></i>下载
                    </button>
                    <span v-else class="task-hint">{{ selectedId === task.id ? '正在查看日志' : '点击查看日志' }}</span>
                </div>
            </article>
        </div>
    </ManagementShell>
</template>

<script>
/**
 * App 构建管理会话（对齐安卓 AppBuildScreen）：
 *  - 速览：当前源码、最近构建、当前任务、签名配置；
 *  - 构建操作：更新代码 / 编译 APK / 下载 APK / 取消任务；
 *  - 执行日志：按游标增量拉取，支持跟随滚动、复制、下载日志；
 *  - 任务历史：点击切换日志，可下载成功产物。
 */
import { NButton } from 'naive-ui'
import ManagementShell from '@/components/main/management-shell.vue'
import { message } from '@/libs/discrete'
import {
    getAndroidStatus, getAndroidTasks, updateAndroidCode, buildAndroidApk,
    cancelAndroidTask, getAndroidLogs, prepareAndroidDownload, startAndroidDownload
} from '@/api/android'
import { formatBytes } from '@/utils/format'

const STATUS_LABEL = {
    QUEUED: '等待执行', RUNNING: '执行中', CANCELLING: '正在取消', SUCCESS: '成功',
    FAILED: '失败', CANCELLED: '已取消', TIMED_OUT: '超时', INTERRUPTED: '意外中断'
}
const STAGE_LABEL = {
    QUEUED: '等待执行', FETCHING: '更新源码', PREPARING: '准备编译', COMPILING: '正在编译',
    VERIFYING: '校验安装包', ARCHIVING: '归档安装包', DONE: '完成'
}

export default {
    name: 'AppBuildPage',
    components: { ManagementShell, NButton },
    data() {
        return {
            status: {
                enabled: false, workerOnline: false, signingReady: false, branch: 'master',
                source: {}, activeTask: null, latestApk: null
            },
            tasks: { data: [], loading: false, loaded: false },
            selectedId: null,
            logs: '',
            cursor: 0,
            logComplete: false,
            follow: true,
            submitting: '',
            cancelling: false,
            downloading: false,
            lastDownload: null,
            connectionError: false,
            clock: Date.now(),
            timer: null,
            clockTimer: null
        }
    },
    computed: {
        loaded() {
            return this.tasks.loaded
        },
        activeTask() {
            return this.status.activeTask
        },
        selectedTask() {
            return this.tasks.data.find((task) => task.id === this.selectedId) || null
        },
        logText() {
            return this.logs
        },
        shortCommit() {
            return (this.status.source && this.status.source.commit || '').slice(0, 8)
        },
        sourceSubject() {
            const source = this.status.source || {}
            return source.commitSubject || '点击更新代码获取最新源码'
        },
        canUpdate() {
            return this.loaded && this.status.enabled && this.status.workerOnline && !this.activeTask && !this.submitting
        },
        canBuild() {
            return this.canUpdate && /^[0-9a-f]{40}$/.test((this.status.source && this.status.source.commit) || '') && this.status.signingReady
        },
        reason() {
            if (this.submitting || this.activeTask || this.canBuild) return ''
            if (!this.status.enabled) return '安卓构建服务尚未启用'
            if (!this.status.workerOnline) return '构建容器离线，暂时无法编译'
            if (!/^[0-9a-f]{40}$/.test((this.status.source && this.status.source.commit) || '')) return '请先更新代码，获取最新安卓源码'
            if (!this.status.signingReady) return '尚未配置 Release 签名，暂时无法编译'
            return ''
        }
    },
    mounted() {
        this.load()
        this.timer = setInterval(() => this.poll(), 1000)
        this.clockTimer = setInterval(() => { this.clock = Date.now() }, 1000)
    },
    beforeUnmount() {
        clearInterval(this.timer)
        clearInterval(this.clockTimer)
    },
    methods: {
        formatBytes,
        short(commit) {
            return (commit || '').slice(0, 8) || '—'
        },
        typeLabel(type) {
            return type === 'BUILD' ? '编译 APK' : '更新代码'
        },
        taskStatusLabel(status) {
            return STATUS_LABEL[status] || status
        },
        stageLabel(stage) {
            return STAGE_LABEL[stage] || stage || '处理中'
        },
        taskTone(status) {
            if (status === 'SUCCESS') return ''
            if (['FAILED', 'TIMED_OUT', 'INTERRUPTED'].includes(status)) return 'danger'
            if (['QUEUED', 'RUNNING', 'CANCELLING'].includes(status)) return 'amber'
            return 'muted'
        },
        formatTime(value) {
            if (!value) return '—'
            return String(value).replace('T', ' ').slice(0, 16)
        },
        /** 任务耗时：未结束的任务以当前时钟兜底（对齐安卓 elapsedLabel） */
        elapsed(task) {
            const start = Date.parse(String(task.startedAt || task.createdAt || '').replace(' ', 'T'))
            if (!Number.isFinite(start)) return '—'
            const end = task.completedAt ? Date.parse(String(task.completedAt).replace(' ', 'T')) : this.clock
            const seconds = Math.max(0, Math.floor((end - start) / 1000))
            return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
        },
        isDownloadable(task) {
            return Boolean(task && task.type === 'BUILD' && task.status === 'SUCCESS' && task.artifactName && task.apkSize > 0)
        },
        async load() {
            this.tasks = { ...this.tasks, loading: true }
            try {
                const status = await getAndroidStatus()
                this.status = { ...this.status, ...(status || {}) }
                this.connectionError = false
                const tasks = await getAndroidTasks()
                this.tasks = { data: Array.isArray(tasks) ? tasks : [], loading: false, loaded: true }
                if (!this.selectedId) {
                    this.selectedId = (this.activeTask && this.activeTask.id) || (this.tasks.data[0] && this.tasks.data[0].id) || null
                }
                this.tasks = { data: Array.isArray(tasks) ? tasks : [], loading: false, loaded: true }
                await this.refreshLogs()
            } catch (error) {
                this.connectionError = true
                this.tasks = { ...this.tasks, loading: false, loaded: true }
            }
        },
        /** 每秒轮询：状态 + 任务 + 选中任务的增量日志 */
        async poll() {
            if (this.connectionError) {
                this.load()
                return
            }
            try {
                const status = await getAndroidStatus()
                this.status = { ...this.status, ...(status || {}) }
                if (!this.selectedId) {
                    this.selectedId = (this.activeTask && this.activeTask.id) || (this.tasks.data[0] && this.tasks.data[0].id) || null
                }
                await this.refreshLogs()
                const active = this.activeTask
                if (!active && (this.logComplete || !this.selectedId)) {
                    // 空闲时降低刷新频率：每 5 次（5 秒）拉一次任务历史
                    this.pollCount = (this.pollCount || 0) + 1
                    if (this.pollCount % 5 !== 0) return
                }
                const tasks = await getAndroidTasks()
                this.tasks = { data: Array.isArray(tasks) ? tasks : [], loading: false, loaded: true }
            } catch (error) {
                this.connectionError = true
            }
        },
        async refreshLogs() {
            const id = this.selectedId
            if (!id || this.logComplete) return
            try {
                const chunk = await getAndroidLogs(id, this.cursor)
                if (!chunk) return
                if (chunk.reset) {
                    this.logs = ''
                    this.cursor = 0
                }
                const merged = this.logs + (chunk.text || '')
                this.logs = merged.length > 200000 ? merged.slice(-200000) : merged
                this.cursor = chunk.cursor || this.cursor
                this.logComplete = !chunk.hasMore && !(chunk.task && chunk.task.active)
                if (chunk.task) {
                    this.tasks = {
                        data: [chunk.task, ...this.tasks.data.filter((item) => item.id !== chunk.task.id)],
                        loading: this.tasks.loading,
                        loaded: true
                    }
                }
                if (this.follow) {
                    this.$nextTick(() => {
                        const box = this.$refs.logBox
                        if (box) box.scrollTop = box.scrollHeight
                    })
                }
            } catch (error) {
                // 日志接口失败下一轮自动重试
            }
        },
        selectTask(task) {
            if (this.selectedId === task.id) return
            this.selectedId = task.id
            this.logs = ''
            this.cursor = 0
            this.logComplete = false
            this.follow = true
            this.refreshLogs()
        },
        async submit(type) {
            if (!this.canUpdate || this.submitting) return
            this.submitting = type
            try {
                const task = type === 'BUILD' ? await buildAndroidApk() : await updateAndroidCode()
                message.success(type === 'BUILD' ? '已提交 APK 编译任务' : '已提交代码更新任务')
                this.selectedId = task.id
                this.logs = ''
                this.cursor = 0
                this.logComplete = false
                await this.load()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.submitting = ''
            }
        },
        cancelTask(task) {
            this.$dialog.warning({
                title: '取消构建任务',
                content: `确定取消「${this.typeLabel(task.type)} #${task.id}」？构建容器会停止当前进程，未完成的产物会被清理。`,
                positiveText: '取消任务',
                negativeText: '再等等',
                onPositiveClick: async () => {
                    this.cancelling = true
                    try {
                        await cancelAndroidTask(task.id)
                        message.success('已发送取消请求')
                        await this.load()
                    } catch (error) {
                        // 拦截器已提示
                    } finally {
                        this.cancelling = false
                    }
                }
            })
        },
        async downloadApk(task) {
            if (!task || this.downloading) return
            this.downloading = true
            try {
                const download = await prepareAndroidDownload(task, false)
                this.lastDownload = download
                startAndroidDownload(download)
            } catch (error) {
                message.error(error.message || '下载准备失败，请稍后重试')
            } finally {
                this.downloading = false
            }
        },
        async downloadLog() {
            if (!this.selectedTask || this.downloading) return
            this.downloading = true
            try {
                const download = await prepareAndroidDownload(this.selectedTask, true)
                this.lastDownload = download
                startAndroidDownload(download)
            } catch (error) {
                message.error(error.message || '日志下载准备失败')
            } finally {
                this.downloading = false
            }
        },
        copyLink() {
            const url = this.lastDownload && this.lastDownload.url
            if (!url) return
            navigator.clipboard?.writeText(url)
                .then(() => message.success('下载链接已复制'))
                .catch(() => message.error('复制失败，请手动选择链接'))
        },
        async copyLogs() {
            if (!this.logs) return
            try {
                await navigator.clipboard.writeText(this.logs)
                message.success('日志已复制')
            } catch (error) {
                message.error('复制失败，请手动选择文本')
            }
        },
        showHostInfo() {
            const status = this.status
            const lines = [
                `构建服务：${status.enabled ? '已启用' : '未启用'}`,
                `构建容器：${status.workerOnline ? '在线' : '离线'}`,
                `签名配置：${status.signingReady ? '已配置' : '未配置'}`,
                `分支：${status.branch || 'master'}`
            ]
            this.$dialog.info({
                title: '构建宿主信息',
                content: lines.join('\n'),
                positiveText: '关闭',
                style: { whiteSpace: 'pre-line' }
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
    color: var(--muted);
    font-size: 15px;
    cursor: pointer;
}

.header-icon:hover {
    background: var(--soft-green);
    color: var(--pine);
}

/* 速览卡的 2×2 信息格 */
.build-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
    margin-top: 16px;
}

.build-cell {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
}

.build-cell > span {
    font-size: 10px;
    color: rgba(255, 255, 255, .72);
}

.build-cell strong {
    font-size: 15px;
    font-weight: 600;
    color: #fff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.build-cell em {
    font-style: normal;
    font-size: 10.5px;
    color: rgba(255, 255, 255, .72);
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.action-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
}

.action-tip {
    margin: 5px 0 14px;
    font-size: 12px;
    color: var(--muted);
    line-height: 1.55;
}

.action-gap {
    margin-top: 9px;
}

.action-reason {
    margin: 8px 0 0;
    font-size: 11px;
    color: var(--amber);
}

.action-foot {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid var(--line);
}

.action-spacer {
    flex: 1;
}

.download-panel strong {
    font-size: 14px;
    color: var(--ink);
}

.download-panel p {
    margin: 6px 0 10px;
    font-size: 12px;
    color: var(--muted);
    line-height: 1.55;
}

.download-links {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.download-links a {
    color: var(--pine);
    font-size: 13px;
    font-weight: 500;
}

.log-head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.log-head strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
}

.log-head > span {
    font-size: 11px;
    color: var(--muted);
}

.log-spacer {
    flex: 1;
}

.log-elapsed {
    font-size: 11px;
    color: var(--muted);
}

.log-actions {
    display: flex;
    gap: 2px;
    margin: 6px 0;
}

.log-error {
    margin: 0 0 6px;
    font-size: 12px;
    color: var(--danger);
    overflow-wrap: anywhere;
}

.log-content {
    max-height: 260px;
    min-height: 160px;
    overflow: auto;
    margin: 0;
    padding: 14px;
    border-radius: 12px;
    background: #0A1220;
    color: #C7D9CA;
    font-family: Consolas, "SFMono-Regular", monospace;
    font-size: 11px;
    line-height: 17px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}

.log-foot {
    margin: 7px 0 0;
    font-size: 11px;
    color: var(--muted);
}

.history-tip {
    font-size: 11px;
    color: var(--muted);
}

.task-row {
    cursor: pointer;
    padding: 14px;
}

.task-row.selected {
    background: var(--soft-green);
    border-color: rgba(32, 108, 84, .35);
}

.task-head {
    display: flex;
    align-items: center;
    gap: 8px;
}

.task-id {
    font-size: 11px;
    color: var(--muted);
}

.task-head strong {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.task-meta,
.task-foot {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 6px;
    font-size: 11px;
    color: var(--muted);
    flex-wrap: wrap;
}

.task-spacer {
    flex: 1;
}

.task-hint {
    font-size: 11px;
    color: var(--muted);
}
</style>
