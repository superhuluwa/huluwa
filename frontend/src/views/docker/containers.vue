<template>
    <div class="docker-containers">
        <div class="glass-card main-card">
            <div class="header">
                <h2>
                    <i class="fa-solid fa-cube"></i>
                    Docker 容器
                    <span class="sub">共 {{ filteredContainers.length }} 个</span>
                </h2>
                <div class="actions">
                    <n-input v-model:value="searchText" clearable placeholder="搜索容器名 / 镜像" style="width: 220px">
                        <template #prefix><i class="fa-solid fa-magnifying-glass"></i></template>
                    </n-input>
                    <n-radio-group v-model:value="viewMode" size="small">
                        <n-radio-button value="table">表格</n-radio-button>
                        <n-radio-button value="grid">网格</n-radio-button>
                    </n-radio-group>
                    <n-button size="small" quaternary :loading="loading" @click="loadContainers(true)">
                        <template #icon><i class="fa-solid fa-rotate"></i></template>
                        刷新
                    </n-button>
                </div>
            </div>

            <n-tabs v-model:value="tab" type="line" animated style="margin-bottom: 12px" @update:value="loadContainers">
                <n-tab-pane name="running" tab="运行中" />
                <n-tab-pane name="all" tab="全部" />
            </n-tabs>

            <n-data-table v-if="viewMode === 'table'" :columns="columns" :data="filteredContainers" :loading="loading" :bordered="true"
                size="small" :row-key="(row) => row.Id" :scroll-x="1350" />

            <div v-else class="container-grid">
                <div v-for="row in filteredContainers" :key="row.Id" class="container-card"
                    :class="row.State === 'running' ? 'card-running' : 'card-stopped'">
                    <div class="card-header">
                        <span class="container-name" :title="containerName(row)">{{ containerName(row) }}</span>
                        <span :class="['pill', row.State === 'running' ? 'ok' : row.State === 'exited' ? 'danger' : 'off']">
                            <span class="dot"></span>{{ row.State === 'running' ? '运行中' : (row.Status || row.State) }}
                        </span>
                    </div>
                    <div class="card-body">
                        <div class="card-line"><strong>镜像:</strong> <span class="mono">{{ row.Image || '-' }}</span></div>
                        <div class="card-line"><strong>端口:</strong> <span class="mono">{{ formatPorts(row.Ports) }}</span></div>
                        <div class="card-line" v-if="row.State === 'running'">
                            <strong>资源:</strong>
                            <span :class="usageClass(row.CPU)" class="mono">CPU {{ row.CPU || '-' }}</span>
                            <span :class="memoryUsageClass(row.MemoryBytes)" style="margin-left: 10px">内存 {{ row.Memory || '-' }}</span>
                        </div>
                        <div class="card-line"><strong>创建时间:</strong> <span class="mono">{{ row.Created || '-' }}</span></div>
                        <div class="card-line"><strong>状态:</strong> <span>{{ row.Status || '-' }}</span></div>
                    </div>
                    <div class="card-footer">
                        <n-button v-if="row.State === 'running'" size="tiny" type="warning" @click="doStop(row)">停止</n-button>
                        <n-button v-else size="tiny" type="success" @click="doStart(row)">启动</n-button>
                        <n-button size="tiny" type="primary" @click="doRestart(row)">重启</n-button>
                        <n-button size="tiny" @click="showStats(row)">详情</n-button>
                        <n-button size="tiny" @click="showLogs(row)">日志</n-button>
                        <n-button size="tiny" @click="showExec(row)">执行</n-button>
                        <n-button size="tiny" type="error" @click="doRemove(row)">删除</n-button>
                    </div>
                </div>
                <div v-if="!filteredContainers.length" class="grid-empty">暂无容器数据</div>
            </div>
        </div>

        <n-modal v-model:show="statsModal" preset="card" :title="'容器详情 - ' + containerName(currentStatsContainer)" style="width: 78%"
            :mask-closable="false">
            <n-spin :show="statsLoading">
            <div v-if="stats" class="stats-container">
                <div class="stats-section">
                    <h3>基本信息</h3>
                    <div class="stat-grid-inner">
                        <div class="stat-item"><span class="label">名称</span><span class="value">{{ stats.Name || '-' }}</span></div>
                        <div class="stat-item"><span class="label">镜像</span><span class="value mono">{{ (stats.Config && stats.Config.Image) || '-' }}</span></div>
                        <div class="stat-item"><span class="label">状态</span><span class="value">{{ (stats.State && stats.State.Status) || '-' }}</span></div>
                        <div class="stat-item"><span class="label">创建时间</span><span class="value mono">{{ stats.Created || '-' }}</span></div>
                        <div class="stat-item"><span class="label">启动时间</span><span class="value mono">{{ (stats.State && stats.State.StartedAt) || '-' }}</span></div>
                    </div>
                </div>
                <div class="stats-section" v-if="summaryStats && stats.State?.Running">
                    <h3>实时资源 <small class="empty-text">每秒采样 · 最近 60 个点</small></h3>
                    <div class="stat-grid-inner">
                        <div class="stat-item"><span class="label">CPU</span><span class="value">{{ summaryStats.CPUPerc || '-' }}</span></div>
                        <div class="stat-item"><span class="label">内存</span><span class="value">{{ summaryStats.MemUsage || '-' }}（{{ summaryStats.MemPerc || '-' }}）</span></div>
                        <div class="stat-item"><span class="label">网络 I/O</span><span class="value">{{ summaryStats.NetIO || '-' }}</span></div>
                        <div class="stat-item"><span class="label">磁盘 I/O</span><span class="value">{{ summaryStats.BlockIO || '-' }}</span></div>
                        <div class="stat-item"><span class="label">PIDs</span><span class="value">{{ summaryStats.PIDs || '-' }}</span></div>
                    </div>
                    <ResourceCharts :samples="resourceHistory" />
                </div>
                <div class="stats-section">
                    <h3>端口映射</h3>
                    <div v-if="stats.NetworkSettings && stats.NetworkSettings.Ports">
                        <div v-for="(value, key) in stats.NetworkSettings.Ports" :key="key" class="stat-item">
                            <span class="label">{{ key }}</span>
                            <span class="value mono">{{ formatBindings(value) }}</span>
                        </div>
                    </div>
                    <div v-else class="empty-text">无端口映射</div>
                </div>
                <div class="stats-section">
                    <h3>挂载信息</h3>
                    <div v-for="(mount, index) in (stats.Mounts || [])" :key="index" class="stat-item">
                        <span class="label">{{ mount.Type }}</span>
                        <span class="value mono">{{ mount.Source }} → {{ mount.Destination }}（{{ mount.RW ? '读写' : '只读' }}）</span>
                    </div>
                    <div v-if="!stats.Mounts?.length" class="empty-text">无挂载卷</div>
                </div>
                <div class="stats-section">
                    <h3>启动命令</h3>
                    <div class="stat-item"><span class="label">入口点</span><span class="value mono">{{ commandText(stats.Config?.Entrypoint) }}</span></div>
                    <div class="stat-item"><span class="label">命令</span><span class="value mono">{{ commandText(stats.Config?.Cmd) }}</span></div>
                </div>
                <div class="stats-section">
                    <h3>环境变量</h3>
                    <div v-if="stats.Config && stats.Config.Env && stats.Config.Env.length">
                        <div v-for="(item, index) in stats.Config.Env" :key="index" class="env-item mono">{{ item }}</div>
                    </div>
                    <div v-else class="empty-text">无环境变量</div>
                </div>
            </div>
            <div v-else class="empty-text">暂无详细信息</div>
            </n-spin>
        </n-modal>

        <n-modal v-model:show="logsModal" preset="card" :title="'容器日志 - ' + containerName(currentLogContainer)" style="width: 88%"
            :mask-closable="false">
            <div class="logs-container">
                <div class="logs-actions">
                    <n-input v-model:value="logTail" size="small" style="width: 140px" placeholder="tail 行数" />
                    <n-checkbox v-model:checked="logStdout">标准输出</n-checkbox>
                    <n-checkbox v-model:checked="logStderr">错误输出</n-checkbox>
                    <n-checkbox v-model:checked="followLogs">自动刷新</n-checkbox>
                    <n-button size="small" type="primary" :loading="logsLoading" @click="loadLogs">刷新</n-button>
                </div>
                <pre ref="logsContent" class="logs-content">{{ containerLogs || '暂无日志' }}</pre>
            </div>
        </n-modal>

        <n-modal v-model:show="execModal" preset="card" :title="'执行命令 - ' + currentName" style="width: 640px"
            :mask-closable="false">
            <n-input v-model:value="execCommand" type="textarea" :rows="3" placeholder="例如：ls -al /app" />
            <pre v-if="execOutput" class="exec-output mono">{{ execOutput }}</pre>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="execModal = false">关闭</n-button>
                    <n-button type="primary" :loading="execLoading" @click="runExec">执行</n-button>
                </div>
            </template>
        </n-modal>
    </div>
</template>

<script>
import {
    NButton,
    NCheckbox,
    NDataTable,
    NInput,
    NModal,
    NRadioButton,
    NRadioGroup,
    NSpin,
    NTabPane,
    NTabs
} from 'naive-ui'
import { defineAsyncComponent, h } from 'vue'
import { useIsMobile } from '@/composables/useIsMobile'
import { resourceSnapshot, formatBytes, memoryClass, appendSample } from './resources'
import {
    listContainers,
    inspectContainer,
    getContainerStats,
    getContainersStats,
    startContainer,
    stopContainer,
    restartContainer,
    removeContainer,
    getContainerLogs,
    executeCommand
} from '@/api/docker'

const ResourceCharts = defineAsyncComponent(() => import('./resource-charts.vue'))

export default {
    name: 'DockerContainers',
    components: {
        ResourceCharts,
        NButton,
        NCheckbox,
        NDataTable,
        NInput,
        NModal,
        NRadioButton,
        NRadioGroup,
        NSpin,
        NTabPane,
        NTabs
    },
    setup() {
        // 窄屏判定：弹窗宽度与默认视图据此切换（桌面端行为不变）
        return { isMobile: useIsMobile() }
    },
    data() {
        return {
            tab: 'running',
            // 移动端默认卡片（网格）视图：窄屏表格列多，卡片更易读；桌面端保持表格
            viewMode: window.matchMedia?.('(max-width: 900px)').matches ? 'grid' : 'table',
            searchText: '',
            loading: false,
            containers: [],
            resourceTimer: null,
            statsTimer: null,
            logTimer: null,
            pageActive: false,
            pageGeneration: 0,
            listSequence: 0,
            resourceBusy: false,
            statsBusy: false,
            statsLoading: false,
            statsSequence: 0,
            logSequence: 0,
            previousCpu: {},
            detailPreviousCpu: null,
            resourceHistory: [],
            currentStatsContainer: null,
            currentLogContainer: null,
            followLogs: false,
            logStdout: true,
            logStderr: true,
            statsModal: false,
            logsModal: false,
            execModal: false,
            logsLoading: false,
            execLoading: false,
            currentContainer: null,
            stats: null,
            summaryStats: null,
            containerLogs: '',
            logTail: '100',
            execCommand: '',
            execOutput: ''
        }
    },
    computed: {
        currentName() {
            return this.containerName(this.currentContainer)
        },
        filteredContainers() {
            let list = this.tab === 'running'
                ? this.containers.filter((item) => item.State === 'running')
                : [...this.containers]
            const keyword = (this.searchText || '').trim().toLowerCase()
            if (keyword) {
                list = list.filter((item) => {
                    const names = (item.Names || []).join(',').toLowerCase()
                    return names.includes(keyword) || (item.Image || '').toLowerCase().includes(keyword)
                })
            }
            return list
        },
        columns() {
            return [
                {
                    title: '名称',
                    key: 'Names',
                    minWidth: 160,
                    render: (row) => h('span', { class: 'strong-text' }, this.containerName(row))
                },
                {
                    title: '镜像',
                    key: 'Image',
                    minWidth: 200,
                    className: 'mono',
                    ellipsis: { tooltip: true },
                    render: (row) => h('span', row.Image || '-')
                },
                {
                    title: '状态',
                    key: 'Status',
                    minWidth: 170,
                    render: (row) => h('span', {
                        class: 'pill ' + (row.State === 'running' ? 'ok' : row.State === 'exited' ? 'danger' : 'off'),
                        title: row.Status
                    }, [h('span', { class: 'dot' }), row.Status || row.State])
                },
                {
                    title: 'CPU',
                    key: 'CPU',
                    width: 90,
                    render: (row) => h('span', { class: this.usageClass(row.CPU) }, row.CPU || '-')
                },
                {
                    title: '内存',
                    key: 'Memory',
                    width: 110,
                    render: (row) => h('span', { class: this.memoryUsageClass(row.MemoryBytes) }, row.Memory || '-')
                },
                { title: '创建时间', key: 'Created', width: 170, className: 'mono' },
                {
                    title: '端口',
                    key: 'Ports',
                    minWidth: 170,
                    className: 'mono',
                    render: (row) => h('span', this.formatPorts(row.Ports))
                },
                {
                    title: '操作',
                    key: 'actions',
                    width: 220,
                    align: 'center',
                    fixed: 'right',
                    render: (row) => h('div', { class: 'op-actions' }, [
                        row.State === 'running'
                            ? h('i', {
                                class: 'fa-solid fa-stop op-icon',
                                style: { color: 'var(--warn)' },
                                title: '停止',
                                onClick: () => this.doStop(row)
                            })
                            : h('i', {
                                class: 'fa-solid fa-play op-icon',
                                style: { color: 'var(--ok)' },
                                title: '启动',
                                onClick: () => this.doStart(row)
                            }),
                        h('i', {
                            class: 'fa-solid fa-arrows-rotate op-icon',
                            style: { color: 'var(--accent)' },
                            title: '重启',
                            onClick: () => this.doRestart(row)
                        }),
                        h('i', {
                            class: 'fa-solid fa-circle-info op-icon',
                            title: '详情',
                            onClick: () => this.showStats(row)
                        }),
                        h('i', {
                            class: 'fa-solid fa-file-lines op-icon',
                            title: '日志',
                            onClick: () => this.showLogs(row)
                        }),
                        h('i', {
                            class: 'fa-solid fa-terminal op-icon',
                            title: '执行命令',
                            onClick: () => this.showExec(row)
                        }),
                        h('i', {
                            class: 'fa-solid fa-trash op-icon',
                            style: { color: 'var(--danger)' },
                            title: '删除',
                            onClick: () => this.doRemove(row)
                        })
                    ])
                }
            ]
        }
    },
    watch: {
        statsModal(visible) {
            if (!visible) {
                this.stopStatsPolling()
                this.resourceHistory = []
                this.detailPreviousCpu = null
            }
        },
        logsModal(visible) { if (!visible) this.stopLogPolling() },
        followLogs() { this.updateLogPolling() }
    },
    mounted() { this.resumePage() },
    activated() { this.resumePage() },
    deactivated() { this.suspendPage() },
    beforeUnmount() { this.suspendPage() },
    methods: {
        containerName(row) {
            if (!row) return '-'
            const names = row.Names
            if (names && names.length) {
                const name = names[0]
                return name.startsWith('/') ? name.substring(1) : name
            }
            return row.Id ? row.Id.substring(0, 12) : '-'
        },
        formatPorts(ports) {
            if (!ports || !ports.length) return '-'
            return ports.map((port) => {
                if (port.PublicPort) {
                    return `${port.PublicPort}:${port.PrivatePort}/${port.Type}`
                }
                return `${port.PrivatePort}/${port.Type}`
            }).join(', ')
        },
        formatBindings(bindings) {
            if (!bindings || !bindings.length) return '未映射'
            return bindings.map((item) => `${item.HostIp || '0.0.0.0'}:${item.HostPort}`).join(', ')
        },
        usageClass(value) {
            const percent = parseFloat(String(value || '').replace('%', ''))
            if (isNaN(percent)) return 'mono'
            if (percent > 80) return 'mono high-usage'
            if (percent > 50) return 'mono medium-usage'
            return 'mono low-usage'
        },
        commandText(value) { return Array.isArray(value) ? value.join(' ') : value || '无' },
        memoryUsageClass(bytes) { return 'mono ' + memoryClass(bytes || 0) },
        resumePage() {
            if (this.pageActive) return
            this.pageActive = true
            this.loadContainers()
            this.startPolling()
            if (this.statsModal) this.startStatsPolling()
            this.updateLogPolling()
        },
        suspendPage() {
            this.pageActive = false
            this.pageGeneration++
            this.listSequence++
            this.resourceBusy = false
            this.stopPolling()
            this.stopStatsPolling()
            this.stopLogPolling()
        },
        startPolling() {
            this.stopPolling()
            this.resourceTimer = setInterval(() => this.loadResourceUsage(), 5000)
        },
        stopPolling() {
            clearInterval(this.resourceTimer)
            this.resourceTimer = null
        },
        async loadContainers() {
            const sequence = ++this.listSequence
            this.loading = true
            try {
                const data = await listContainers(this.tab === 'all')
                if (sequence !== this.listSequence || !this.pageActive) return
                const previousRows = new Map(this.containers.map(row => [row.Id, row]))
                this.containers = (data || []).map(item => ({
                    CPU: previousRows.get(item.Id)?.CPU || '0.00%',
                    Memory: previousRows.get(item.Id)?.Memory || '-',
                    MemoryBytes: previousRows.get(item.Id)?.MemoryBytes || 0,
                    ...item
                }))
                const runningIds = new Set(this.containers.filter(row => row.State === 'running').map(row => row.Id))
                this.previousCpu = Object.fromEntries(Object.entries(this.previousCpu).filter(([id]) => runningIds.has(id)))
                await this.loadResourceUsage()
            } catch (error) {
                if (sequence === this.listSequence) this.containers = []
            } finally {
                if (sequence === this.listSequence) this.loading = false
            }
        },
        async loadResourceUsage() {
            if (!this.pageActive || this.resourceBusy) return
            const running = this.containers.filter(item => item.State === 'running')
            if (!running.length) return
            const generation = this.pageGeneration
            this.resourceBusy = true
            try {
                const stats = await getContainersStats(running.map(item => item.Id))
                if (generation !== this.pageGeneration || !this.pageActive) return
                this.containers.filter(item => item.State === 'running').forEach(item => {
                    const value = stats?.[item.Id]
                    if (!value) {
                        item.CPU = item.Memory = '-'
                        delete this.previousCpu[item.Id]
                        return
                    }
                    const sample = resourceSnapshot(value, this.previousCpu[item.Id])
                    this.previousCpu[item.Id] = sample.previousCpu
                    item.CPU = sample.cpuPercent.toFixed(2) + '%'
                    item.Memory = formatBytes(sample.memoryBytes)
                    item.MemoryBytes = sample.memoryBytes
                })
            } catch (error) {
                // 后台采样失败不刷屏、不伪造新的采样点。
            } finally {
                if (generation === this.pageGeneration) this.resourceBusy = false
            }
        },
        startStatsPolling() {
            this.stopStatsPolling()
            if (!this.pageActive || !this.statsModal) return
            this.refreshStatsData()
            this.statsTimer = setInterval(() => this.refreshStatsData(), 1000)
        },
        stopStatsPolling() {
            clearInterval(this.statsTimer)
            this.statsTimer = null
            this.statsSequence++
            this.statsBusy = false
        },
        async refreshStatsData() {
            if (!this.pageActive || !this.statsModal || !this.currentStatsContainer || this.statsBusy) return
            const row = this.currentStatsContainer
            const sequence = this.statsSequence
            this.statsBusy = true
            try {
                const detail = await inspectContainer(row.Id, { silent: !this.statsLoading })
                if (sequence !== this.statsSequence || !this.statsModal || !this.pageActive) return
                this.stats = detail
                if (!detail?.State?.Running) {
                    this.summaryStats = null
                    this.detailPreviousCpu = null
                    return
                }
                const summary = await getContainerStats(row.Id)
                if (sequence !== this.statsSequence || !this.statsModal || !this.pageActive) return
                const sample = resourceSnapshot(summary, this.detailPreviousCpu)
                this.detailPreviousCpu = sample.previousCpu
                this.summaryStats = { ...summary, CPUPerc: sample.cpuPercent.toFixed(2) + '%' }
                this.resourceHistory = appendSample(this.resourceHistory, { at: Date.now(), cpu: sample.cpuPercent, memory: sample.memoryBytes })
            } catch (error) {
                // 保留上一次真实数据，稍后自动重试。
            } finally {
                if (sequence === this.statsSequence) {
                    this.statsBusy = false
                    this.statsLoading = false
                }
            }
        },
        stopLogPolling() {
            clearInterval(this.logTimer)
            this.logTimer = null
            this.logSequence++
            this.logsLoading = false
        },
        updateLogPolling() {
            this.stopLogPolling()
            if (this.pageActive && this.logsModal && this.followLogs) {
                this.loadLogs()
                this.logTimer = setInterval(() => this.loadLogs(), 3000)
            }
        },
        async doStart(row) {
            await startContainer(row.Id)
            this.$message.success('容器启动成功')
            this.loadContainers()
        },
        async doStop(row) {
            await stopContainer(row.Id)
            this.$message.success('容器停止成功')
            this.loadContainers()
        },
        async doRestart(row) {
            await restartContainer(row.Id)
            this.$message.success('容器重启成功')
            this.loadContainers()
        },
        doRemove(row) {
            this.$dialog.warning({
                title: '删除容器',
                content: `确定要删除容器「${this.containerName(row)}」吗？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await removeContainer(row.Id, false, false)
                    this.$message.success('容器删除成功')
                    this.loadContainers()
                }
            })
        },
        async showStats(row) {
            this.currentStatsContainer = row
            this.stats = null
            this.summaryStats = null
            this.detailPreviousCpu = null
            this.resourceHistory = []
            this.statsLoading = true
            this.statsModal = true
            this.startStatsPolling()
        },
        async showLogs(row) {
            this.stopLogPolling()
            this.currentLogContainer = row
            this.containerLogs = ''
            this.logsModal = true
            await this.loadLogs()
            this.updateLogPolling()
        },
        async loadLogs() {
            if (!this.currentLogContainer || !this.logsModal || !this.pageActive || this.logsLoading) return
            const sequence = this.logSequence
            const id = this.currentLogContainer.Id
            this.logsLoading = true
            try {
                // 持续查看通过轮询快照实现，不让浏览器等待无限 follow 流。
                const logs = await getContainerLogs(id, false, this.logStdout, this.logStderr, this.logTail || '100', { silent: this.followLogs })
                if (sequence !== this.logSequence || !this.logsModal || !this.pageActive) return
                this.containerLogs = logs
                this.$nextTick(() => {
                    const el = this.$refs.logsContent
                    if (el) el.scrollTop = el.scrollHeight
                })
            } catch (error) {
                // 刷新失败保留之前的日志。
            } finally {
                if (sequence === this.logSequence) this.logsLoading = false
            }
        },
        showExec(row) {
            this.currentContainer = row
            this.execCommand = ''
            this.execOutput = ''
            this.execModal = true
        },
        async runExec() {
            if (!this.execCommand || !this.execCommand.trim()) {
                this.$message.warning('请输入要执行的命令')
                return
            }
            this.execLoading = true
            try {
                const result = await executeCommand(this.currentContainer.Id, this.execCommand.trim())
                this.execOutput = `exitCode=${result.exitCode || 0}\n${result.output || ''}`
            } catch (error) {
                this.execOutput = ''
            } finally {
                this.execLoading = false
            }
        }
    }
}
</script>

<style scoped>
.docker-containers {
    padding: 16px;
}

.main-card {
    padding: 16px 20px;
}

.header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 12px;
}

.header h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--text);
    display: flex;
    align-items: center;
    gap: 8px;
}

.header h2 .sub {
    font-size: 12px;
    font-weight: 400;
    color: var(--text-3);
}

.actions {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
}

.op-actions {
    display: inline-flex;
    align-items: center;
    gap: 12px;
}

.op-icon {
    font-size: 14px;
    cursor: pointer;
    opacity: .85;
    transition: opacity .15s ease, transform .15s ease;
}

.op-icon:hover {
    opacity: 1;
    transform: scale(1.15);
}

.strong-text {
    font-weight: 600;
}

.high-usage {
    color: var(--danger);
}

.medium-usage {
    color: var(--warn);
}

.low-usage {
    color: var(--ok);
}

.stats-container {
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.stats-section h3 {
    margin: 0 0 10px;
    padding-bottom: 6px;
    border-bottom: 1px solid var(--line);
    font-size: 13px;
    color: var(--text);
}

.stat-grid-inner {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 6px 20px;
}

.stat-item {
    display: flex;
    align-items: baseline;
    gap: 10px;
    font-size: 13px;
    padding: 2px 0;
}

.stat-item .label {
    min-width: 88px;
    color: var(--text-2);
}

.stat-item .value {
    color: var(--text);
    word-break: break-all;
}

.env-item {
    font-size: 12px;
    color: var(--text-2);
    padding: 2px 0;
    word-break: break-all;
}

.empty-text {
    color: var(--text-3);
    font-size: 13px;
    padding: 6px 0;
}

.logs-container {
    height: 600px;
    display: flex;
    flex-direction: column;
}

.logs-actions {
    flex-wrap: wrap;
    align-items: center;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-bottom: 8px;
}

.logs-content {
    flex: 1;
    overflow: auto;
    margin: 0;
    padding: 10px;
    border-radius: 8px;
    background: var(--card-2);
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
    color: var(--text-2);
    white-space: pre-wrap;
    word-break: break-all;
}

.exec-output {
    margin-top: 12px;
    max-height: 280px;
    overflow: auto;
    padding: 10px;
    border-radius: 8px;
    background: var(--card-2);
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-all;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}

.container-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 14px;
    padding: 4px 0;
}

.container-card {
    border: 1px solid var(--line);
    border-radius: var(--radius);
    padding: 14px;
    background: linear-gradient(180deg, var(--card) 0%, var(--card-2) 100%);
    transition: all .25s ease;
}

.container-card:hover {
    border-color: rgba(34, 211, 238, .4);
    transform: translateY(-2px);
    box-shadow: 0 0 20px rgba(34, 211, 238, .1);
}

.card-running { border-left: 4px solid var(--ok); }
.card-stopped { border-left: 4px solid var(--danger); }

.card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
}

.container-name {
    font-size: 15px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.card-body {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 13px;
    color: var(--text-2);
}

.card-line {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.card-footer {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid var(--line);
}

.grid-empty {
    grid-column: 1 / -1;
    text-align: center;
    padding: 40px;
    color: var(--text-3);
    font-size: 13px;
}
/* 移动端：容器内边距收紧，头部操作区换行铺满，表格横向滚动查看（列结构不变） */
@media (max-width: 900px) {
    .docker-containers {
        padding: 0;
    }

    .main-card {
        padding: 14px 12px;
        border-radius: 10px;
    }

    .header {
        margin-bottom: 12px;
    }

    .header h2 {
        font-size: 15px;
    }

    .actions {
        width: 100%;
        gap: 6px;
    }

    .actions :deep(.n-input) {
        width: 100% !important;
    }

    .card-footer {
        gap: 6px;
    }

    .container-grid {
        grid-template-columns: minmax(0, 1fr);
        gap: 10px;
    }

    /* 详情/日志弹窗内部：单列排布，高度收敛到视口内 */
    .stat-grid-inner {
        grid-template-columns: minmax(0, 1fr);
        gap: 4px;
    }

    .stat-item {
        gap: 8px;
    }

    .stat-item .label {
        min-width: 72px;
    }

    .logs-container {
        height: min(62vh, 460px);
    }

    .logs-actions {
        justify-content: flex-start;
    }

    .logs-actions :deep(.n-input) {
        width: 110px !important;
    }

    .exec-output {
        max-height: 200px;
    }
}
</style>
