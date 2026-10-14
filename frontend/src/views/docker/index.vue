<template>
    <div class="docker-management">
        <div class="glass-card main-card">
            <div class="header">
                <h2>
                    <i class="fa-brands fa-docker"></i>
                    Docker 管理中心
                </h2>
                <p>管理宿主机上的 Docker 容器、镜像、网络和卷</p>
            </div>

            <n-alert v-if="environment.daemonOnline === false" type="warning" style="margin-bottom: 16px"
                title="无法连接宿主机 Docker 守护进程">
                {{ environment.message || '请检查容器是否挂载了 /var/run/docker.sock。' }}
                CentOS 部署要点：compose 中需挂载 /var/run/docker.sock，并把 .env 的 DOCKER_GID 设为
                <span class="mono">stat -c '%g' /var/run/docker.sock</span> 的输出值。
            </n-alert>

            <div class="stats-grid">
                <div class="stat">
                    <div class="stat-k">
                        <span class="badge b-cyan"><i class="fa-solid fa-cube"></i></span>
                        运行中容器
                    </div>
                    <div class="stat-v mono">{{ summary.running }}</div>
                    <div class="stat-f">共 {{ summary.total }} 个 · 已停止 {{ summary.stopped }}</div>
                </div>
                <div class="stat">
                    <div class="stat-k">
                        <span class="badge b-amber"><i class="fa-regular fa-images"></i></span>
                        本地镜像
                    </div>
                    <div class="stat-v mono">{{ summary.images }}</div>
                    <div class="stat-f">docker images</div>
                </div>
                <div class="stat">
                    <div class="stat-k">
                        <span class="badge b-green"><i class="fa-solid fa-circle-nodes"></i></span>
                        网络
                    </div>
                    <div class="stat-v mono">{{ summary.networks }}</div>
                    <div class="stat-f">docker network ls</div>
                </div>
                <div class="stat">
                    <div class="stat-k">
                        <span class="badge b-red"><i class="fa-solid fa-heart-pulse"></i></span>
                        守护进程
                    </div>
                    <div class="stat-v" style="font-size: 17px; margin-top: 14px">
                        <span :class="['pill', environment.daemonOnline ? 'ok' : 'danger']">
                            <span class="dot"></span>{{ environment.daemonOnline ? '在线' : '未连接' }}
                        </span>
                    </div>
                    <div class="stat-f mono">{{ environment.serverVersion || environment.message || '检测中…' }}</div>
                </div>
            </div>
        </div>

        <div class="glass-card main-card">
            <div class="section-title">
                <i class="fa-solid fa-bolt"></i> 快捷操作
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

        <div class="glass-card main-card">
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

            <n-descriptions :column="isMobile ? 1 : 3" size="small" bordered label-placement="left">
                <n-descriptions-item label="客户端版本">{{ environment.clientVersion || '-' }}</n-descriptions-item>
                <n-descriptions-item label="服务端版本">{{ environment.serverVersion || '-' }}</n-descriptions-item>
                <n-descriptions-item label="API 版本">{{ environment.apiVersion || '-' }}</n-descriptions-item>
                <n-descriptions-item label="平台">{{ environment.os || '-' }} / {{ environment.arch || '-' }}</n-descriptions-item>
                <n-descriptions-item label="接入方式">{{ environment.transport || 'docker-java' }}</n-descriptions-item>
                <n-descriptions-item label="连接地址"><span class="mono">{{ environment.host || '-' }}</span></n-descriptions-item>
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
import {
    NAlert,
    NButton,
    NDataTable,
    NDescriptions,
    NDescriptionsItem,
    NForm,
    NFormItem,
    NInput,
    NModal,
    NSwitch
} from 'naive-ui'
import { h } from 'vue'
import { getEnvironment, getSummary, listHosts, createHost, deleteHost } from '@/api/docker'
import { useIsMobile } from '@/composables/useIsMobile'

export default {
    components: { NAlert, NButton, NDataTable, NDescriptions, NDescriptionsItem, NForm, NFormItem, NInput, NModal, NSwitch },
    name: 'DockerManagement',
    setup() {
        // 窄屏下详情/主机描述表收成单列，桌面端仍为三列
        return { isMobile: useIsMobile() }
    },
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
    // KeepAlive 首次挂载也会调用 activated，统一在这里加载，避免双份请求。
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
.docker-management {
    padding: 16px;
}

.main-card {
    padding: 20px 22px;
    margin-bottom: 16px;
}

.main-card:last-of-type {
    margin-bottom: 0;
}

.header {
    margin-bottom: 20px;
}

.header h2 {
    margin: 0 0 8px;
    font-size: 18px;
    font-weight: 600;
    color: var(--text);
    display: flex;
    align-items: center;
    gap: 10px;
}

.header p {
    margin: 0;
    color: var(--text-2);
    font-size: 13px;
}

.stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 14px;
}

.stat {
    background: linear-gradient(180deg, var(--card-2) 0%, var(--card) 100%);
    border: 1px solid var(--line);
    border-radius: var(--radius);
    padding: 16px;
}

.stat-k {
    color: var(--text-2);
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.stat-v {
    font-size: 26px;
    font-weight: 700;
    margin-top: 10px;
    color: var(--text);
    font-variant-numeric: tabular-nums;
}

.stat-f {
    margin-top: 8px;
    font-size: 12px;
    color: var(--text-3);
}

.badge {
    width: 30px;
    height: 30px;
    border-radius: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
}

.b-cyan {
    background: var(--accent-weak);
    color: var(--accent);
}

.b-green {
    background: rgba(52, 211, 153, .12);
    color: var(--ok);
}

.b-amber {
    background: rgba(251, 191, 36, .12);
    color: var(--warn);
}

.b-red {
    background: rgba(248, 113, 113, .12);
    color: var(--danger);
}

.section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text);
    margin-bottom: 16px;
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
    padding: 20px;
    border-radius: 10px;
    background: var(--card-2);
    border: 1px solid var(--line);
    cursor: pointer;
    transition: all .3s;
    color: var(--text-2);
    font-size: 13px;
}

.action-item i {
    font-size: 24px;
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
    transition: opacity .15s ease, transform .15s ease;
}

.op-icon:hover {
    opacity: 1;
    transform: scale(1.15);
}
/* 移动端：卡片留白收紧，快捷操作与统计卡片保持双列铺满 */
@media (max-width: 900px) {
    .docker-management {
        padding: 0;
    }

    .main-card {
        padding: 14px 12px;
        border-radius: 10px;
    }

    .header {
        margin-bottom: 14px;
    }

    .header h2 {
        font-size: 16px;
    }

    .stats-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 10px;
    }

    .stat {
        padding: 12px;
    }

    .stat-v {
        font-size: 22px;
    }

    .actions-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 10px;
    }

    .action-item {
        padding: 14px 10px;
    }

    .section-title {
        flex-wrap: wrap;
        row-gap: 8px;
    }
}
</style>
