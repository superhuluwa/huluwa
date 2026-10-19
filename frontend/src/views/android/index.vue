<template>
    <div class="android-page">
        <div class="page-heading">
            <div>
                <h3 class="page-title"><i class="fa-brands fa-android"></i> 安卓 App</h3>
                <p class="page-subtitle">更新源码、构建 Release 安装包，随时查看执行日志。</p>
            </div>
            <span class="worker-status" :class="{ online: status.workerOnline }">
                <span class="status-dot"></span>{{ status.workerOnline ? '构建服务在线' : '构建服务离线' }}
            </span>
        </div>

        <n-alert v-if="connectionError" type="warning" :bordered="false" class="notice">
            暂时无法连接服务器，正在自动重连。服务器上的任务会继续执行。
        </n-alert>
        <n-alert v-else-if="loaded && !status.enabled" type="info" :bordered="false" class="notice">
            安卓构建服务尚未启用，请配置并启动构建容器。
        </n-alert>
        <n-alert v-else-if="loaded && !status.workerOnline" type="warning" :bordered="false" class="notice">
            构建容器暂时离线，恢复后即可更新代码和编译。已生成的 APK 仍可下载。
        </n-alert>

        <div class="overview-grid">
            <div class="glass-card overview-card">
                <span class="eyebrow">当前源码</span>
                <div class="overview-value mono">{{ status.source?.commit?.slice(0, 8) || '尚未更新' }}</div>
                <div class="overview-detail" :title="status.source?.commitSubject">{{ status.source?.commitSubject || '点击更新代码，获取最新安卓源码' }}</div>
                <div class="muted">{{ status.branch || 'master' }}<span v-if="status.source?.updatedAt"> · {{ date(status.source.updatedAt) }}</span></div>
            </div>
            <div class="glass-card overview-card">
                <span class="eyebrow">最近成功构建</span>
                <div class="overview-value">{{ status.latestApk ? `v${status.latestApk.versionName}` : '暂无安装包' }}</div>
                <div class="overview-detail">{{ status.latestApk ? `版本代码 ${status.latestApk.versionCode} · ${size(status.latestApk.apkSize)}` : '完成编译后即可下载到手机安装' }}</div>
                <div class="muted">{{ status.latestApk ? date(status.latestApk.completedAt) : 'Release 签名安装包' }}</div>
            </div>
            <div class="glass-card overview-card">
                <span class="eyebrow">当前任务</span>
                <div class="overview-value">{{ activeTask ? stageName(activeTask.stage) : '空闲' }}</div>
                <div class="overview-detail">{{ activeTask ? `${typeName(activeTask.type)} #${activeTask.id} · ${duration(activeTask)}` : '就绪后可以开始新的任务' }}</div>
                <div class="muted">{{ status.signingReady ? 'Release 签名已配置' : 'Release 签名未配置' }}</div>
            </div>
        </div>

        <div class="glass-card action-panel">
            <div class="action-copy"><strong>构建操作</strong><span>先更新代码，再编译 APK；每次编译固定使用当前源码版本。</span></div>
            <div class="action-buttons">
                <n-button :disabled="!canSubmit" :loading="submitting === 'UPDATE'" @click="submit('UPDATE')">
                    <template #icon><i class="fa-solid fa-code-pull-request"></i></template>更新代码
                </n-button>
                <n-button type="primary" :disabled="!canSubmit || !status.source?.commit || !status.signingReady" :loading="submitting === 'BUILD'" @click="submit('BUILD')">
                    <template #icon><i class="fa-solid fa-hammer"></i></template>编译 APK
                </n-button>
                <n-button :disabled="!status.latestApk || !!downloading" :loading="downloading === status.latestApk?.id" @click="download(status.latestApk)">
                    <template #icon><i class="fa-solid fa-download"></i></template>{{ downloadPercent ? `下载 ${downloadPercent}%` : '下载 APK' }}
                </n-button>
                <n-button v-if="activeTask" type="error" secondary :loading="cancelling" :disabled="activeTask.status === 'CANCELLING'" @click="cancel">
                    {{ activeTask.status === 'CANCELLING' ? '正在取消' : '取消任务' }}
                </n-button>
            </div>
        </div>

        <div class="glass-card log-panel">
            <div class="panel-toolbar">
                <div class="panel-title">执行日志 <span v-if="selectedTask" class="muted">{{ typeName(selectedTask.type) }} #{{ selectedTask.id }}</span>
                    <n-tag v-if="selectedTask" :type="tagType(selectedTask.status)" size="small" :bordered="false">{{ statusName(selectedTask.status) }}</n-tag>
                </div>
                <div class="log-actions">
                    <n-button size="small" quaternary @click="follow = !follow">{{ follow ? '暂停滚动' : '自动滚动' }}</n-button>
                    <n-button size="small" quaternary :disabled="!logText" @click="copyLogs">复制</n-button>
                    <n-button size="small" quaternary :disabled="!selectedTask || !logText" @click="download(selectedTask, true)">下载日志</n-button>
                </div>
            </div>
            <div v-if="selectedTask?.error" class="task-error">{{ selectedTask.error }}</div>
            <pre ref="logElement" class="log-content" tabindex="0" aria-label="构建执行日志">{{ logText || (selectedTask ? '等待任务输出日志…' : '开始更新代码或编译后，这里会显示执行日志。') }}</pre>
            <div class="log-footer"><span>日志每秒增量更新，完整内容可下载</span><span v-if="selectedTask">{{ duration(selectedTask) }}</span></div>
        </div>

        <div class="glass-card history-panel">
            <div class="panel-toolbar"><strong>任务历史</strong><span class="muted">点击任务查看日志</span></div>
            <div v-if="!tasks.length" class="empty-history">暂无任务记录</div>
            <div v-else class="history-scroll">
                <table class="history-table">
                    <thead><tr><th>任务</th><th>源码 / 版本</th><th>状态</th><th>开始时间</th><th>耗时</th><th>操作</th></tr></thead>
                    <tbody>
                        <tr v-for="task in tasks" :key="task.id" :class="{ selected: task.id === selectedTask?.id }" @click="selectTask(task)">
                            <td><span class="task-id">#{{ task.id }}</span> {{ typeName(task.type) }}</td>
                            <td><span class="mono">{{ task.commit?.slice(0, 8) || '—' }}</span><span v-if="task.type === 'BUILD'" class="history-version">v{{ task.versionName }} ({{ task.versionCode }})</span></td>
                            <td><n-tag :type="tagType(task.status)" size="small" :bordered="false">{{ statusName(task.status) }}</n-tag></td>
                            <td class="muted">{{ date(task.startedAt || task.createdAt) }}</td><td>{{ duration(task) }}</td>
                            <td><n-button v-if="task.artifactName && task.status === 'SUCCESS'" size="small" text type="primary" :loading="downloading === task.id" :disabled="!!downloading" @click.stop="download(task)">下载 {{ size(task.apkSize) }}</n-button><n-button v-else size="small" text @click.stop="selectTask(task)">查看日志</n-button></td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { NAlert, NButton, NTag } from 'naive-ui'
import { message } from '@/libs/discrete'
import { getAndroidStatus, getAndroidTasks, updateAndroidCode, buildAndroidApk, cancelAndroidTask, getAndroidLogs, downloadAndroidFile } from '@/api/android'

defineOptions({ name: 'AndroidApp' })
const status = ref({}), tasks = ref([]), selectedTask = ref(null), logText = ref(''), logElement = ref(null)
const loaded = ref(false), connectionError = ref(false), follow = ref(true), submitting = ref(''), cancelling = ref(false)
const downloading = ref(null), downloadPercent = ref(0), clock = ref(Date.now())
const activeTask = computed(() => status.value.activeTask)
const canSubmit = computed(() => loaded.value && status.value.enabled && status.value.workerOnline && !activeTask.value && !submitting.value)
const activeStatuses = ['QUEUED', 'RUNNING', 'CANCELLING']
let pageActive = false, generation = 0, logGeneration = 0, timer, controller, cursor = 0, logComplete = false
const typeName = (type) => type === 'BUILD' ? '编译 APK' : '更新代码'
const statusName = (value) => ({ QUEUED: '等待执行', RUNNING: '执行中', CANCELLING: '正在取消', SUCCESS: '成功', FAILED: '失败', CANCELLED: '已取消', TIMED_OUT: '超时', INTERRUPTED: '意外中断' }[value] || value)
const stageName = (value) => ({ QUEUED: '等待执行', FETCHING: '更新源码', PREPARING: '准备编译', COMPILING: '正在编译', VERIFYING: '校验安装包', ARCHIVING: '归档安装包', DONE: '完成' }[value] || value)
const tagType = (value) => value === 'SUCCESS' ? 'success' : ['FAILED', 'TIMED_OUT', 'INTERRUPTED'].includes(value) ? 'error' : activeStatuses.includes(value) ? 'info' : 'default'
const date = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const size = (bytes) => bytes ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : '—'
function duration(task) {
    const start = new Date(task.startedAt || task.createdAt).getTime()
    const end = task.completedAt ? new Date(task.completedAt).getTime() : clock.value
    const seconds = Math.max(0, Math.floor((end - start) / 1000))
    return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}
function selectTask(task) {
    if (!task) return
    if (selectedTask.value?.id === task.id) { selectedTask.value = task; return }
    logGeneration++
    selectedTask.value = task
    logText.value = ''
    cursor = 0
    logComplete = false
    follow.value = true
    if (pageActive) schedule(0)
}
function schedule(delay) {
    clearTimeout(timer)
    if (pageActive && !controller) timer = setTimeout(poll, delay)
}
async function poll() {
    if (!pageActive || controller || document.hidden) return
    const currentGeneration = generation
    const currentController = new AbortController()
    controller = currentController
    let delay = 1000
    try {
        const config = { silent: true, signal: currentController.signal }
        const [snapshot, history] = await Promise.all([getAndroidStatus(config), getAndroidTasks(config)])
        if (!pageActive || generation !== currentGeneration) return
        status.value = snapshot
        tasks.value = history
        loaded.value = true
        connectionError.value = false
        clock.value = Date.now()
        if (selectedTask.value && !history.some((task) => task.id === selectedTask.value.id)) {
            selectedTask.value = null
            logText.value = ''
            logGeneration++
        }
        if (!selectedTask.value) selectTask(snapshot.activeTask || history[0] || snapshot.latestApk)
        if (selectedTask.value) {
            const updated = history.find((task) => task.id === selectedTask.value.id)
            if (updated) selectedTask.value = updated
            if (!logComplete) {
                const selectedId = selectedTask.value.id
                const currentLogGeneration = logGeneration
                const logs = await getAndroidLogs(selectedId, cursor, config)
                if (!pageActive || generation !== currentGeneration || currentLogGeneration !== logGeneration) return
                selectedTask.value = logs.task
                logText.value = ((logs.reset ? '' : logText.value) + logs.text).slice(-200000)
                cursor = logs.cursor
                logComplete = !logs.hasMore && !activeStatuses.includes(logs.task.status)
                if (logs.hasMore) delay = 50
                await nextTick()
                if (follow.value && logElement.value) logElement.value.scrollTop = logElement.value.scrollHeight
            }
        }
        if (!snapshot.activeTask && delay !== 50) delay = 5000
    } catch (error) {
        if (generation === currentGeneration && !currentController.signal.aborted) {
            connectionError.value = true
            delay = 3000
        }
    } finally {
        if (controller === currentController) controller = null
        if (generation === currentGeneration) schedule(delay)
    }
}
async function submit(type) {
    submitting.value = type
    try {
        const task = await (type === 'BUILD' ? buildAndroidApk() : updateAndroidCode())
        status.value = { ...status.value, activeTask: task }
        tasks.value = [task, ...tasks.value.filter((item) => item.id !== task.id)]
        selectTask(task)
        message.success(type === 'BUILD' ? '已提交 APK 编译任务' : '已提交代码更新任务')
    } catch (error) {
        // The shared HTTP interceptor displays the server error.
    } finally { submitting.value = ''; schedule(0) }
}
async function cancel() {
    if (!activeTask.value) return
    cancelling.value = true
    try {
        const task = await cancelAndroidTask(activeTask.value.id)
        status.value = { ...status.value, activeTask: task }
        if (selectedTask.value?.id === task.id) selectedTask.value = task
    } catch (error) {
        // The shared HTTP interceptor displays the server error.
    } finally { cancelling.value = false; schedule(0) }
}
async function download(task, logFile = false) {
    if (!task || downloading.value) return
    downloading.value = task.id
    downloadPercent.value = 0
    try {
        await downloadAndroidFile(task, logFile, (event) => {
            if (event.total) downloadPercent.value = Math.floor(event.loaded * 100 / event.total)
        })
    } catch (error) {
        // The shared HTTP interceptor displays the server error.
    } finally { downloading.value = null; downloadPercent.value = 0 }
}
async function copyLogs() {
    try {
        if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(logText.value)
        else {
            const input = document.createElement('textarea')
            input.value = logText.value
            input.style.position = 'fixed'; input.style.opacity = '0'
            document.body.appendChild(input); input.select()
            const copied = document.execCommand('copy')
            input.remove()
            if (!copied) throw new Error('copy failed')
        }
        message.success('日志已复制')
    } catch (error) { message.warning('复制失败，请选择日志文本复制或下载日志') }
}
function activate() {
    if (pageActive) return
    pageActive = true
    generation++
    schedule(0)
}
function deactivate() {
    pageActive = false
    generation++
    clearTimeout(timer)
    controller?.abort()
    controller = null
}
function visibilityChanged() {
    if (document.hidden) {
        clearTimeout(timer)
        generation++
        controller?.abort()
        controller = null
    } else if (pageActive) schedule(0)
}
onMounted(() => { document.addEventListener('visibilitychange', visibilityChanged); activate() })
onActivated(activate)
onDeactivated(deactivate)
onBeforeUnmount(() => { deactivate(); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>

<style scoped>
.android-page { max-width: 1500px; margin: 0 auto; display: flex; flex-direction: column; gap: 18px; }
.page-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.page-title { margin: 0 0 6px; display: flex; align-items: center; gap: 10px; }
.page-title i { color: var(--ok); }
.page-subtitle, .muted, .eyebrow { color: var(--text-3); font-size: 12px; }
.page-subtitle { margin: 0; }
.worker-status { display: flex; align-items: center; gap: 7px; color: var(--text-3); font-size: 12px; white-space: nowrap; }
.worker-status.online { color: var(--ok); }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.overview-card { padding: 20px; min-width: 0; }
.overview-value { font-size: 25px; font-weight: 650; margin: 12px 0 7px; }
.overview-detail { font-size: 13px; margin-bottom: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mono { font-family: var(--font-mono, monospace); }
.action-panel { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 18px 20px; }
.action-copy { display: flex; flex-direction: column; gap: 6px; }
.action-copy strong, .panel-toolbar strong { font-size: 14px; }
.action-copy span { font-size: 12px; color: var(--text-3); }
.action-buttons, .log-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.panel-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 18px; }
.panel-title { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; font-weight: 600; font-size: 14px; }
.panel-title .muted { font-weight: 400; }
.log-panel, .history-panel { overflow: hidden; }
.log-content { margin: 0; padding: 18px; height: 360px; overflow: auto; background: #080f19; color: #c7d9ca; font: 12px/1.8 var(--font-mono, monospace); white-space: pre-wrap; overflow-wrap: anywhere; tab-size: 4; }
.log-footer { display: flex; justify-content: space-between; padding: 9px 18px; color: var(--text-3); font-size: 11px; }
.task-error { padding: 10px 18px; color: var(--danger); background: rgba(230, 70, 70, .08); overflow-wrap: anywhere; }
.history-scroll { overflow-x: auto; }
.history-table { width: 100%; border-collapse: collapse; text-align: left; font-size: 12px; white-space: nowrap; }
.history-table th { color: var(--text-3); font-weight: 500; padding: 10px 18px; background: rgba(255,255,255,.02); }
.history-table td { padding: 12px 18px; border-top: 1px solid var(--line); }
.history-table tbody tr { cursor: pointer; transition: background .15s; }
.history-table tbody tr:hover, .history-table tbody tr.selected { background: rgba(100,160,180,.07); }
.task-id { color: var(--text-3); margin-right: 6px; }
.history-version { display: block; color: var(--text-3); font-size: 11px; margin-top: 4px; }
.empty-history { padding: 32px; color: var(--text-3); text-align: center; font-size: 13px; }
@media (max-width: 900px) {
    .android-page { gap: 12px; }
    .page-heading { align-items: flex-start; }
    .page-title { font-size: 17px; }
    .page-subtitle { max-width: 230px; line-height: 1.6; }
    .overview-grid { gap: 10px; grid-template-columns: 1fr; }
    .overview-card { padding: 14px 16px; }
    .overview-value { font-size: 22px; margin: 8px 0 5px; }
    .action-panel { align-items: flex-start; flex-direction: column; padding: 16px; gap: 14px; }
    .action-buttons { width: 100%; }
    .panel-toolbar { flex-wrap: wrap; padding: 12px; }
    .log-actions { margin-left: auto; gap: 2px; }
    .log-content { height: 320px; padding: 12px; font-size: 11px; }
    .log-footer { padding: 8px 12px; }
}
</style>
