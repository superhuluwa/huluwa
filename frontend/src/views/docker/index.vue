<template>
    <div class="docker-overview">
        <h3 class="page-title">
            <i class="fa-brands fa-docker"></i>
            Docker 管理
            <span class="sub">容器 / 镜像 / 网络 / 卷</span>
        </h3>

        <n-alert v-if="environment.daemonOnline === false" type="warning" style="margin-bottom: 14px" title="无法连接宿主机 Docker 守护进程">
            {{ environment.message || '请检查容器是否挂载了 /var/run/docker.sock。' }}
            CentOS 部署要点：compose 中需挂载 /var/run/docker.sock，并把 .env 的 DOCKER_GID 设为
            <span class="mono">stat -c '%g' /var/run/docker.sock</span> 的输出值。
        </n-alert>

        <div class="stat-grid" style="margin-bottom: 16px">
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-cube" style="color: #2496ED"></i> 运行中容器</div>
                <div class="stat-value">{{ summary.running }}</div>
                <div class="stat-foot">共 {{ summary.total }} 个容器 · 已停止 {{ summary.stopped }}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-layer-group" style="color: var(--accent-2)"></i> 本地镜像</div>
                <div class="stat-value">{{ summary.images }}</div>
                <div class="stat-foot">docker images</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-circle-nodes" style="color: var(--ok)"></i> 网络</div>
                <div class="stat-value">{{ summary.networks }}</div>
                <div class="stat-foot">docker network ls</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-heart-pulse" style="color: var(--warn)"></i> 守护进程</div>
                <div class="stat-value" style="font-size: 17px; margin-top: 14px">
                    <span :class="['pill', environment.daemonOnline ? 'ok' : 'danger']">
                        <span class="dot"></span>{{ environment.daemonOnline ? '在线' : '未连接' }}
                    </span>
                </div>
                <div class="stat-foot mono">{{ environment.serverVersion || environment.message || '检测中…' }}</div>
            </div>
        </div>

        <div class="glass-card" style="padding: 18px 20px; margin-bottom: 16px">
            <div class="section-title">
                <i class="fa-solid fa-bolt"></i> 快捷入口
            </div>
            <div class="actions-grid">
                <div class="action-item" @click="go('docker-containers')">
                    <i class="fa-solid fa-cube"></i><span>容器管理</span>
                </div>
                <div class="action-item" @click="go('docker-images')">
                    <i class="fa-regular fa-images"></i><span>镜像管理</span>
                </div>
                <div class="action-item" @click="go('docker-networks')">
                    <i class="fa-solid fa-circle-nodes"></i><span>网络管理</span>
                </div>
                <div class="action-item" @click="go('docker-volumes')">
                    <i class="fa-solid fa-folder"></i><span>卷管理</span>
                </div>
            </div>
        </div>

        <div class="glass-card" style="padding: 18px 20px">
            <div class="section-title">
                <i class="fa-solid fa-server"></i> Docker 主机
                <n-button size="small" quaternary style="margin-left: auto" :loading="loading" @click="refresh">
                    <template #icon><i class="fa-solid fa-rotate"></i></template>
                    刷新
                </n-button>
                <n-button size="small" type="primary" @click="openHostModal">
                    <template #icon><i class="fa-solid fa-plus"></i></template>
                    新增主机
                </n-button>
            </div>

            <n-descriptions :column="3" size="small" bordered label-placement="left">
                <n-descriptions-item label="客户端版本">{{ environment.clientVersion || '-' }}</n-descriptions-item>
                <n-descriptions-item label="服务端版本">{{ environment.serverVersion || '-' }}</n-descriptions-item>
                <n-descriptions-item label="API 版本">{{ environment.apiVersion || '-' }}</n-descriptions-item>
                <n-descriptions-item label="平台">{{ environment.os || '-' }} / {{ environment.arch || '-' }}</n-descriptions-item>
                <n-descriptions-item label="CLI">{{ environment.binary || 'docker' }}</n-descriptions-item>
                <n-descriptions-item label="状态">{{ environment.message || '-' }}</n-descriptions-item>
            </n-descriptions>

            <n-data-table style="margin-top: 14px" :columns="hostColumns" :data="hosts" :bordered="false" size="small"
                :row-key="(row) => row.id" />
        </div>

        <n-modal v-model:show="hostModal" preset="card" title="新增 Docker 主机" style="width: 460px"
            :mask-closable="false">
            <n-form :model="hostForm" label-placement="left" label-width="90">
                <n-form-item label="名称">
                    <n-input v-model:value="hostForm.name" placeholder="如：本机" />
                </n-form-item>
                <n-form-item label="连接地址">
                    <n-input v-model:value="hostForm.host" placeholder="local 或 tcp://host:2375" />
                </n-form-item>
                <n-form-item label="启用">
                    <n-switch v-model:value="hostForm.enabled" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="hostModal = false">取消</n-button>
                    <n-button type="primary" :loading="hostSubmitting" @click="submitHost">保存</n-button>
                </div>
            </template>
        </n-modal>
    </div>
</template>

<script>
import { h } from 'vue'
import { getEnvironment, getSummary, listHosts, createHost, deleteHost } from '@/api/docker'

export default {
    name: 'DockerManagement',
    data() {
        return {
            loading: false,
            summary: { running: 0, total: 0, stopped: 0, images: 0, networks: 0, available: false },
            environment: {},
            hosts: [],
            hostModal: false,
            hostSubmitting: false,
            hostForm: { name: '', host: 'local', enabled: true }
        }
    },
    computed: {
        hostColumns() {
            return [
                { title: '名称', key: 'name', minWidth: 120 },
                { title: '连接地址', key: 'host', minWidth: 180, render: (row) => h('span', { class: 'mono' }, row.host) },
                {
                    title: '状态',
                    key: 'enabled',
                    width: 100,
                    render: (row) => h('span', { class: ['pill', row.enabled ? 'ok' : 'off'] }, [
                        h('span', { class: 'dot' }),
                        row.enabled ? '已启用' : '已停用'
                    ])
                },
                { title: '创建时间', key: 'createdAt', width: 170 },
                {
                    title: '操作',
                    key: 'actions',
                    width: 90,
                    render: (row) => h('i', {
                        class: 'fa-solid fa-trash op-icon',
                        style: { color: 'var(--danger)' },
                        title: '删除',
                        onClick: () => this.removeHost(row)
                    })
                }
            ]
        }
    },
    mounted() {
        this.refresh()
        this.loadHosts()
    },
    activated() {
        this.refresh()
        this.loadHosts()
    },
    methods: {
        go(name) {
            this.$router.push({ name })
        },
        async refresh() {
            this.loading = true
            try {
                const [summary, environment] = await Promise.all([getSummary(), getEnvironment()])
                if (summary) this.summary = summary
                if (environment) this.environment = environment
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        async loadHosts() {
            try {
                this.hosts = (await listHosts()) || []
            } catch (error) {
                this.hosts = []
            }
        },
        openHostModal() {
            this.hostForm = { name: '', host: 'local', enabled: true }
            this.hostModal = true
        },
        async submitHost() {
            if (!this.hostForm.name) {
                this.$message.warning('请填写主机名称')
                return
            }
            this.hostSubmitting = true
            try {
                await createHost(this.hostForm)
                this.$message.success('保存成功')
                this.hostModal = false
                this.loadHosts()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.hostSubmitting = false
            }
        },
        removeHost(row) {
            this.$dialog.warning({
                title: '删除主机',
                content: `确认删除「${row.name}」？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await deleteHost(row.id)
                    this.$message.success('已删除')
                    this.loadHosts()
                }
            })
        }
    }
}
</script>

<style scoped>
.docker-overview {
    padding: 2px;
}

.section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text);
    margin-bottom: 14px;
}

.actions-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 14px;
}

.action-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 18px;
    border-radius: 10px;
    background: var(--card-2);
    border: 1px solid var(--line);
    cursor: pointer;
    transition: all .25s ease;
    color: var(--text-2);
    font-size: 13px;
}

.action-item i {
    font-size: 22px;
}

.action-item:hover {
    border-color: rgba(34, 211, 238, .45);
    color: var(--accent);
    transform: translateY(-2px);
    box-shadow: 0 0 20px rgba(34, 211, 238, .12);
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}

.op-icon {
    font-size: 14px;
    cursor: pointer;
    opacity: .85;
}

.op-icon:hover {
    opacity: 1;
}
</style>
