<template>
    <ManagementShell kind="docker" title="Docker 管理" subtitle="容器与资源管理" :loading="containers.loading"
        :busy="busy" @refresh="refreshAll">
        <template #actions>
            <button class="header-icon" type="button" title="Docker 主机信息" @click="showHostInfo">
                <i class="fa-solid fa-server"></i>
            </button>
        </template>

        <div class="mgmt-content">
            <!-- 速览卡：Docker 引擎（对齐安卓 DockerScreen 的 Pine 速览） -->
            <section class="hero-panel">
                <div class="hero-head">
                    <i class="fa-brands fa-docker"></i>
                    <div class="hero-head-text">
                        <strong>Docker Engine</strong>
                        <span>{{ overview.version ? `服务端 v${overview.version}` : '容器运行环境' }}</span>
                    </div>
                    <span class="hero-tag" :class="{ alert: connection.alert }">{{ connection.text }}</span>
                </div>
                <div class="hero-metrics">
                    <div class="hero-metric"><strong>{{ known ? overview.total : '—' }}</strong><span>全部容器</span></div>
                    <div class="hero-metric"><strong>{{ known ? overview.running : '—' }}</strong><span>运行中</span></div>
                    <div class="hero-metric"><strong>{{ known ? Math.max(0, overview.total - overview.running) : '—' }}</strong><span>未运行</span></div>
                </div>
                <div class="hero-divider"></div>
                <div class="usage-row">
                    <span>容器 CPU 合计 <b>{{ (known && overview.online && overview.usage.cpuPercent != null) ? overview.usage.cpuPercent.toFixed(1) + '%' : '—' }}</b></span>
                    <span>容器内存合计 <b>{{ (known && overview.online) ? formatBytes(overview.usage.memoryBytes) : '—' }}</b></span>
                </div>
            </section>

            <div v-if="containers.error" class="notice-line"><i class="fa-solid fa-triangle-exclamation"></i>{{ containers.error }}</div>
            <div v-else-if="known && !overview.online && overview.message" class="notice-line">
                <i class="fa-solid fa-circle-info"></i>{{ overview.message }}
            </div>

            <!-- 分类切换（对齐安卓 FilterRow） -->
            <div class="section-tabs">
                <button v-for="section in sections" :key="section.key" type="button" class="section-tab"
                    :class="{ active: active === section.key }" @click="selectSection(section.key)">
                    {{ section.label }}
                </button>
            </div>

            <n-input v-model:value="keyword" clearable :placeholder="searchPlaceholder" size="large" class="search-box">
                <template #prefix><i class="fa-solid fa-magnifying-glass"></i></template>
            </n-input>

            <div class="section-title">
                <h2>{{ active === 'containers' ? '我的容器' : sectionLabel + '列表' }}</h2>
                <button class="round-btn" type="button" title="刷新" :disabled="loadingResource" @click="refreshAll">
                    <i class="fa-solid fa-rotate" :class="{ 'fa-spin': loadingResource }"></i>
                </button>
                <button v-if="active !== 'containers'" class="round-btn" type="button"
                    :title="active === 'images' ? '拉取镜像' : `新增${sectionLabel}`" @click="openCreate">
                    <i class="fa-solid fa-plus"></i>
                </button>
            </div>

            <!-- 容器列表 -->
            <template v-if="active === 'containers'">
                <div v-if="!visibleContainers.length" class="empty-panel">
                    <span class="empty-icon"><i class="fa-solid fa-cube"></i></span>
                    <strong>{{ containers.loaded ? '没有找到容器' : '正在同步容器…' }}</strong>
                    <span>容器由当前服务器的 Docker 守护进程提供</span>
                </div>
                <article v-for="row in visibleContainers" :key="row.Id" class="panel container-card">
                    <div class="container-head" @click="showDetail(row)">
                        <span class="container-icon" :class="{ on: row.State === 'running' }">
                            <i class="fa-solid fa-cube"></i>
                        </span>
                        <div class="container-title">
                            <strong>{{ containerName(row) }}</strong>
                            <span>{{ row.Image || '—' }}</span>
                        </div>
                        <span class="chip-tag" :class="stateTone(row)">{{ stateLabel(row) }}</span>
                    </div>
                    <p class="container-ports">{{ formatPorts(row.Ports) }}</p>
                    <div class="container-actions">
                        <button class="link-btn" type="button" @click="showLogs(row)">
                            <i class="fa-solid fa-file-lines"></i>日志
                        </button>
                        <button class="link-btn" type="button" :disabled="!canOperate" @click="doAction(row, 'restart')">
                            <i class="fa-solid fa-rotate"></i>重启
                        </button>
                        <button class="link-btn" type="button" :disabled="!canOperate"
                            @click="doAction(row, row.State === 'running' ? 'stop' : 'start')"
                            :class="{ amber: row.State === 'running' }">
                            <i :class="row.State === 'running' ? 'fa-solid fa-circle-stop' : 'fa-solid fa-circle-play'"></i>
                            {{ row.State === 'running' ? '停止' : '启动' }}
                        </button>
                    </div>
                </article>
            </template>

            <!-- 镜像 / 网络 / 卷 -->
            <template v-else>
                <div v-if="!resources.length" class="empty-panel">
                    <span class="empty-icon"><i class="fa-solid fa-layer-group"></i></span>
                    <strong>{{ resourceState.loaded ? `没有找到${sectionLabel}` : '正在同步…' }}</strong>
                    <span>点击右上角加号添加{{ sectionLabel }}</span>
                </div>
                <article v-for="resource in resources" :key="resource.id" class="panel resource-card">
                    <span class="resource-icon"><i :class="sectionIcon"></i></span>
                    <div class="resource-body">
                        <strong>{{ resource.name }}</strong>
                        <span>{{ resource.subtitle || '—' }}</span>
                    </div>
                    <button class="round-btn danger" type="button" :title="`删除${sectionLabel}`" :disabled="!canOperate"
                        @click="removeResource(resource)">
                        <i class="fa-solid fa-trash-can"></i>
                    </button>
                </article>
            </template>
        </div>

        <!-- 容器详情 -->
        <n-modal v-model:show="detailOpen" preset="card" :title="detailTitle" style="width: 640px; max-width: calc(100vw - 24px)">
            <n-spin :show="detailLoading">
                <template v-if="detail">
                    <div class="kv-row"><span class="kv-label">镜像</span><span class="kv-value mono">{{ detail.Config?.Image || '—' }}</span></div>
                    <div class="kv-row"><span class="kv-label">状态</span><span class="kv-value">{{ detail.State?.Status || '—' }}</span></div>
                    <div class="kv-row"><span class="kv-label">创建时间</span><span class="kv-value mono">{{ detail.Created || '—' }}</span></div>
                    <div class="kv-row"><span class="kv-label">启动时间</span><span class="kv-value mono">{{ detail.State?.StartedAt || '—' }}</span></div>
                    <div class="kv-row"><span class="kv-label">重启策略</span><span class="kv-value">{{ detail.HostConfig?.RestartPolicy?.Name || '—' }}</span></div>
                    <div v-for="(mount, index) in (detail.Mounts || [])" :key="index" class="kv-row">
                        <span class="kv-label">挂载</span>
                        <span class="kv-value mono">{{ mount.Source }} → {{ mount.Destination }}（{{ mount.RW ? '读写' : '只读' }}）</span>
                    </div>
                    <div v-if="stats" class="stat-grid-inner">
                        <span><b>CPU</b>{{ stats.CPUPerc || '—' }}</span>
                        <span><b>内存</b>{{ stats.MemUsage || '—' }}</span>
                        <span><b>网络 I/O</b>{{ stats.NetIO || '—' }}</span>
                        <span><b>磁盘 I/O</b>{{ stats.BlockIO || '—' }}</span>
                    </div>
                    <div class="detail-actions">
                        <n-button tertiary type="error" :disabled="detail.State?.Running" @click="removeContainer(detailName)">
                            删除容器
                        </n-button>
                        <n-button tertiary @click="showLogs(currentContainer)">运行日志</n-button>
                    </div>
                </template>
                <p v-else class="empty-text">暂无详细信息</p>
            </n-spin>
        </n-modal>

        <!-- 日志 -->
        <n-modal v-model:show="logsOpen" preset="card" :title="'容器日志 · ' + logsTitle"
            style="width: 780px; max-width: calc(100vw - 24px)">
            <div class="log-actions">
                <n-input v-model:value="logTail" size="small" style="width: 110px" placeholder="行数" />
                <n-button size="small" :loading="logsLoading" @click="loadLogs">刷新</n-button>
            </div>
            <pre class="log-content">{{ logText || '暂无日志' }}</pre>
        </n-modal>

        <!-- 新建资源 -->
        <n-modal v-model:show="createOpen" preset="card" :title="createTitle" style="width: 460px; max-width: calc(100vw - 24px)">
            <n-form label-placement="top">
                <n-form-item :label="active === 'images' ? '镜像名称' : '资源名称'">
                    <n-input v-model:value="createName"
                        :placeholder="active === 'images' ? 'nginx:stable' : (active === 'networks' ? 'huluwa-net' : 'huluwa-data')" />
                </n-form-item>
            </n-form>
            <p class="create-tip">
                {{ active === 'images' ? '镜像拉取可能需要数分钟，请等待服务端完成。'
                    : active === 'networks' ? '将创建一个 bridge 驱动的网络。' : '将创建一个 local 驱动的存储卷。' }}
            </p>
            <template #footer>
                <div class="modal-actions">
                    <n-button @click="createOpen = false">取消</n-button>
                    <n-button type="primary" :loading="creating" :disabled="!createName.trim()" @click="submitCreate">
                        {{ active === 'images' ? '拉取镜像' : '创建资源' }}
                    </n-button>
                </div>
            </template>
        </n-modal>
    </ManagementShell>
</template>

<script>
/**
 * Docker 管理会话（对齐安卓 DockerScreen）：
 *  - 一个页面覆盖容器 / 镜像 / 网络 / 卷四个分类（原网页的五个独立子页面合并）；
 *  - 速览卡展示容器总数、运行数、容器 CPU / 内存合计与连接状态；
 *  - 容器卡片直接提供日志 / 重启 / 启停，详情弹层含挂载、重启策略与实时资源；
 *  - 资源分类（镜像 / 网络 / 卷）支持新增与删除。
 */
import { NButton, NForm, NFormItem, NInput, NModal, NSpin } from 'naive-ui'
import ManagementShell from '@/components/main/management-shell.vue'
import {
    listContainers, inspectContainer, getContainerStats, getContainersStats,
    startContainer, stopContainer, restartContainer, removeContainer, getContainerLogs,
    listImages, pullImage, removeImage,
    listNetworks, createNetwork, removeNetwork,
    listVolumes, createVolume, removeVolume,
    getSummary, getEnvironment
} from '@/api/docker'
import { formatBytes } from '@/utils/format'

const SECTIONS = [
    { key: 'containers', label: '容器' },
    { key: 'images', label: '镜像' },
    { key: 'networks', label: '网络' },
    { key: 'volumes', label: '存储卷' }
]

const STATE_LABEL = {
    running: '运行中', paused: '已暂停', restarting: '重启中', created: '已创建', dead: '异常', exited: '已停止'
}

function emptyState() {
    return { data: [], loading: false, error: '', loaded: false }
}

export default {
    name: 'DockerManagement',
    components: { ManagementShell, NButton, NForm, NFormItem, NInput, NModal, NSpin },
    data() {
        return {
            sections: SECTIONS,
            active: 'containers',
            keyword: '',
            containers: emptyState(),
            overview: { online: false, total: 0, running: 0, images: 0, version: '', message: '', usage: { cpuPercent: null, memoryBytes: null } },
            resources: { images: emptyState(), networks: emptyState(), volumes: emptyState() },
            previousCpu: {},
            busy: false,
            loadingResource: false,
            detailOpen: false,
            detailLoading: false,
            detail: null,
            detailTitle: '',
            currentContainer: null,
            detailName: '',
            stats: null,
            logsOpen: false,
            logsLoading: false,
            logsTitle: '',
            logText: '',
            logTail: '200',
            createOpen: false,
            creating: false,
            createName: '',
            timers: []
        }
    },
    computed: {
        known() {
            return this.containers.loaded && !this.containers.error
        },
        canOperate() {
            return !this.busy && this.known && this.overview.online
        },
        connection() {
            if (this.containers.loading && !this.containers.loaded) return { text: '连接中', alert: false }
            if (this.containers.error) return { text: '连接异常', alert: true }
            if (!this.containers.loaded) return { text: '等待连接', alert: false }
            if (this.overview.online) return { text: '运行正常', alert: false }
            return { text: '连接异常', alert: true }
        },
        sectionLabel() {
            const matched = SECTIONS.find((item) => item.key === this.active)
            return matched ? matched.label : '资源'
        },
        sectionIcon() {
            if (this.active === 'images') return 'fa-solid fa-layer-group'
            if (this.active === 'networks') return 'fa-solid fa-circle-nodes'
            return 'fa-solid fa-database'
        },
        searchPlaceholder() {
            return this.active === 'containers' ? '搜索容器名称或镜像' : `搜索${this.sectionLabel}`
        },
        createTitle() {
            return this.active === 'images' ? '拉取镜像' : `新增${this.sectionLabel}`
        },
        visibleContainers() {
            const keyword = this.keyword.trim().toLowerCase()
            return this.containers.data.filter((row) => {
                if (!keyword) return true
                const text = `${(row.Names || []).join(' ')} ${row.Image || ''}`.toLowerCase()
                return text.includes(keyword)
            })
        },
        resourceState() {
            return this.resources[this.active] || emptyState()
        },
        resources() {
            const keyword = this.keyword.trim().toLowerCase()
            return this.resourceState.data.filter((row) => !keyword || `${row.name} ${row.subtitle}`.toLowerCase().includes(keyword))
        }
    },
    mounted() {
        this.refreshAll()
        // 容器用量每 5 秒采样一次（与安卓一致）
        this.timers = [setInterval(() => this.loadUsage(true), 5000)]
    },
    beforeUnmount() {
        this.timers.forEach(clearInterval)
        this.timers = []
    },
    methods: {
        formatBytes,
        containerName(row) {
            if (!row) return '—'
            const names = row.Names
            if (Array.isArray(names) && names.length) {
                return String(names[0]).replace(/^\//, '')
            }
            return row.Id ? String(row.Id).slice(0, 12) : '—'
        },
        stateLabel(row) {
            return STATE_LABEL[row.State] || row.State || '未知'
        },
        stateTone(row) {
            if (row.State === 'running') return ''
            if (row.State === 'dead' || row.State === 'exited') return 'danger'
            return 'muted'
        },
        formatPorts(ports) {
            if (!Array.isArray(ports) || !ports.length) return '无端口映射'
            return ports.map((port) => port.PublicPort
                ? `${port.PublicPort}:${port.PrivatePort}/${port.Type}`
                : `${port.PrivatePort}/${port.Type}`).join(' · ')
        },
        async refreshAll() {
            await Promise.all([this.loadContainers(), this.loadResources(true)])
            if (this.active !== 'containers') {
                await this.loadResources(false)
            }
        },
        async loadContainers() {
            this.containers = { ...this.containers, loading: true, error: '' }
            try {
                const [list, summary, environment] = await Promise.all([
                    listContainers(true),
                    getSummary().catch(() => null),
                    getEnvironment().catch(() => null)
                ])
                const containers = Array.isArray(list) ? list : []
                this.containers = { data: containers, loading: false, error: '', loaded: true }
                this.overview = {
                    online: !(environment && environment.daemonOnline === false),
                    total: (summary && summary.total) ?? containers.length,
                    running: (summary && summary.running) ?? containers.filter((item) => item.State === 'running').length,
                    images: (summary && summary.images) ?? 0,
                    version: (environment && environment.serverVersion) || '',
                    message: (environment && environment.message) || '',
                    usage: this.overview.usage
                }
                await this.loadUsage(false)
            } catch (error) {
                this.containers = { ...this.containers, loading: false, loaded: true, error: '暂时无法连接 Docker，点击右上角刷新重试。' }
            }
        },
        /** 容器 CPU / 内存合计：用采样差值计算，失败时保留上一次的真实数据 */
        async loadUsage(silent) {
            const running = this.containers.data.filter((row) => row.State === 'running')
            if (!running.length) {
                this.overview = { ...this.overview, usage: { cpuPercent: null, memoryBytes: null } }
                return
            }
            if (this.loadingResource && silent) return
            try {
                const stats = await getContainersStats(running.map((row) => row.Id))
                let cpu = 0
                let memory = 0
                let cpuKnown = false
                let memoryKnown = false
                for (const row of running) {
                    const value = stats ? stats[row.Id] : null
                    if (!value) continue
                    const cpuStats = value.cpu_stats || {}
                    const previous = this.previousCpu[row.Id] || value.precpu_stats || {}
                    const systemDelta = Number(cpuStats.system_cpu_usage || 0) - Number(previous.system_cpu_usage || 0)
                    const cpuDelta = Number((cpuStats.cpu_usage || {}).total_usage || 0) - Number((previous.cpu_usage || {}).total_usage || 0)
                    const cores = Number(cpuStats.online_cpus) || ((cpuStats.cpu_usage || {}).percpu_usage || []).length || 1
                    if (systemDelta > 0 && cpuDelta >= 0) {
                        cpu += (cpuDelta / systemDelta) * cores * 100
                        cpuKnown = true
                    }
                    const mem = Number((value.memory_stats || {}).usage)
                    if (Number.isFinite(mem)) {
                        memory += mem
                        memoryKnown = true
                    }
                    this.previousCpu[row.Id] = cpuStats
                }
                this.overview = {
                    ...this.overview,
                    usage: { cpuPercent: cpuKnown ? cpu : null, memoryBytes: memoryKnown ? memory : null }
                }
            } catch (error) {
                // 采样失败保留上一次数据
            }
        },
        selectSection(key) {
            this.active = key
            this.keyword = ''
            if (key !== 'containers') {
                this.loadResources(false)
            }
        },
        async loadResources(force) {
            if (this.active === 'containers') {
                return
            }
            const key = this.active
            const current = this.resources[key] || emptyState()
            if (current.loading || (current.loaded && !force && this.loadingResource)) {
                return
            }
            this.loadingResource = true
            this.resources = { ...this.resources, [key]: { ...current, loading: true, error: '' } }
            try {
                if (key === 'images') {
                    const data = await listImages(true)
                    this.resources = {
                        ...this.resources,
                        images: {
                            data: (data || []).map((row) => ({
                                id: row.Id,
                                name: (row.RepoTags && row.RepoTags.join(', ')) || '未标记镜像',
                                subtitle: formatBytes(row.Size),
                                raw: row
                            })),
                            loading: false, error: '', loaded: true
                        }
                    }
                } else if (key === 'networks') {
                    const data = await listNetworks()
                    this.resources = {
                        ...this.resources,
                        networks: {
                            data: (data || []).map((row) => ({ id: row.Id, name: row.Name, subtitle: row.Driver || '', raw: row })),
                            loading: false, error: '', loaded: true
                        }
                    }
                } else {
                    const data = await listVolumes()
                    const rows = (data && (data.Volumes || data.volumes)) || []
                    this.resources = {
                        ...this.resources,
                        volumes: {
                            data: rows.map((row) => ({ id: row.Name, name: row.Name, subtitle: row.Driver || '', raw: row })),
                            loading: false, error: '', loaded: true
                        }
                    }
                }
            } catch (error) {
                const state = this.resources[key] || emptyState()
                this.resources = { ...this.resources, [key]: { ...state, loading: false, loaded: true, error: '资源加载失败，请稍后重试。' } }
            } finally {
                this.loadingResource = false
            }
        },
        doAction(row, action) {
            const title = { start: '启动', stop: '停止', restart: '重启' }[action]
            this.$dialog.warning({
                title: `${title}容器`,
                content: `确定${title}「${this.containerName(row)}」？${action === 'start' ? '' : '服务可能暂时不可用。'}`,
                positiveText: title,
                negativeText: '取消',
                onPositiveClick: async () => {
                    if (action === 'start') await startContainer(row.Id)
                    else if (action === 'stop') await stopContainer(row.Id)
                    else await restartContainer(row.Id)
                    this.$message.success(`容器已${title}`)
                    await this.loadContainers()
                }
            })
        },
        async showDetail(row) {
            this.currentContainer = row
            this.detailTitle = this.containerName(row)
            this.detailName = this.containerName(row)
            this.detail = null
            this.stats = null
            this.detailOpen = true
            this.detailLoading = true
            try {
                this.detail = await inspectContainer(row.Id)
                if (this.detail && this.detail.State && this.detail.State.Running) {
                    this.stats = await getContainerStats(row.Id).catch(() => null)
                }
            } catch (error) {
                this.detail = null
            } finally {
                this.detailLoading = false
            }
        },
        async showLogs(row) {
            if (!row) return
            this.currentContainer = row
            this.logsTitle = this.containerName(row)
            this.logsOpen = true
            this.logText = ''
            await this.loadLogs()
        },
        async loadLogs() {
            if (!this.currentContainer) return
            this.logsLoading = true
            try {
                this.logText = await getContainerLogs(this.currentContainer.Id, false, true, true, this.logTail || '200')
            } catch (error) {
                this.logText = '日志读取失败，请稍后重试。'
            } finally {
                this.logsLoading = false
            }
        },
        removeContainer(name) {
            const row = this.currentContainer
            if (!row) return
            this.$dialog.warning({
                title: '删除容器',
                content: `确定要删除容器「${name}」吗？请先停止容器。`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await removeContainer(row.Id, false, false)
                    this.$message.success('容器已删除')
                    this.detailOpen = false
                    await this.loadContainers()
                }
            })
        },
        openCreate() {
            this.createName = ''
            this.createOpen = true
        },
        async submitCreate() {
            const name = this.createName.trim()
            if (!name) return
            this.creating = true
            try {
                if (this.active === 'images') {
                    const [image, tag = 'latest'] = name.includes(':') ? name.split(/:(?=[^:]+$)/) : [name, 'latest']
                    await pullImage(image, tag)
                } else if (this.active === 'networks') {
                    await createNetwork(name, 'bridge')
                } else {
                    await createVolume(name, 'local')
                }
                this.$message.success(this.active === 'images' ? '镜像拉取完成' : '资源已创建')
                this.createOpen = false
                await this.loadResources(true)
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.creating = false
            }
        },
        removeResource(resource) {
            this.$dialog.warning({
                title: `删除${this.sectionLabel}`,
                content: `确定删除「${resource.name}」？${this.active === 'volumes' ? '存储卷中的数据可能永久删除。' : '正在使用的资源会由服务端拒绝删除。'}`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    if (this.active === 'images') await removeImage(resource.id)
                    else if (this.active === 'networks') await removeNetwork(resource.id)
                    else await removeVolume(resource.id)
                    this.$message.success('资源已删除')
                    await this.loadResources(true)
                }
            })
        },
        showHostInfo() {
            const lines = [
                { label: '连接状态', value: this.overview.online ? '在线' : '不可用' },
                { label: '服务端版本', value: this.overview.version || '未知' },
                { label: '容器数量', value: `${this.overview.running}/${this.overview.total}` },
                { label: '镜像数量', value: String(this.overview.images) }
            ]
            this.$dialog.info({
                title: 'Docker 主机',
                content: lines.map((line) => `${line.label}：${line.value}`).join('\n'),
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

.usage-row {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 16px;
    font-size: 11px;
    color: rgba(255, 255, 255, .8);
}

.usage-row b {
    margin-left: 5px;
    color: #fff;
    font-size: 12px;
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

.search-box {
    margin-bottom: 12px;
}

/* 容器卡 */
.container-card {
    padding: 14px 16px;
}

.container-head {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
}

.container-icon {
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 12px;
    background: #EFF2ED;
    color: var(--muted);
    font-size: 17px;
}

.container-icon.on {
    background: rgba(32, 108, 84, .10);
    color: var(--pine);
}

.container-title {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.container-title strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.container-title span {
    font-size: 11px;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.container-ports {
    margin: 10px 0 2px;
    font-size: 11.5px;
    color: var(--muted);
    overflow-wrap: anywhere;
}

.container-actions {
    display: flex;
    justify-content: flex-end;
    gap: 2px;
    margin-top: 6px;
    padding-top: 8px;
    border-top: 1px solid var(--line);
}

.link-btn.amber {
    color: var(--amber);
}

.link-btn.amber:hover:not(:disabled) {
    background: #F8F0E4;
}

/* 资源卡 */
.resource-card {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
}

.resource-icon {
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

.resource-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.resource-body strong {
    font-size: 14px;
    font-weight: 600;
    color: var(--ink);
    overflow-wrap: anywhere;
}

.resource-body span {
    font-size: 11.5px;
    color: var(--muted);
    overflow-wrap: anywhere;
}

.round-btn.danger {
    color: var(--danger);
    border-color: #F2DDE0;
}

.stat-grid-inner {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 8px;
    margin-top: 12px;
    padding: 10px 12px;
    border-radius: 14px;
    background: var(--paper);
    font-size: 12px;
}

.stat-grid-inner span {
    display: flex;
    flex-direction: column;
    gap: 2px;
    color: var(--ink);
}

.stat-grid-inner b {
    font-size: 10.5px;
    color: var(--muted);
    font-weight: 500;
}

.detail-actions {
    display: flex;
    gap: 8px;
    margin-top: 14px;
}

.log-actions {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
}

.log-content {
    max-height: 56vh;
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

.create-tip {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--muted);
    line-height: 1.6;
}

.empty-text {
    color: var(--muted);
    font-size: 13px;
}

.modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}
</style>
