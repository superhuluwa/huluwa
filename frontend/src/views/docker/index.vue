<template>
    <div>
        <h3 class="page-title">
            <i class="fa-brands fa-docker"></i>
            Docker 管理
            <span class="sub">容器与镜像查看</span>
        </h3>

        <div class="stat-grid" style="margin-bottom: 16px">
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-cube" style="color: #2496ED"></i> 运行中容器</div>
                <div class="stat-value">{{ summary.running }}</div>
                <div class="stat-foot">共 {{ summary.total }} 个容器</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-heart-pulse" style="color: var(--ok)"></i> 守护进程</div>
                <div class="stat-value" style="font-size: 17px; margin-top: 14px">
                    <span :class="['pill', environment.daemonOnline ? 'ok' : 'danger']">
                        <span class="dot"></span>{{ environment.daemonOnline ? '在线' : '未连接' }}
                    </span>
                </div>
                <div class="stat-foot">{{ environment.message || '检测中…' }}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-tag" style="color: var(--accent-2)"></i> 服务端版本</div>
                <div class="stat-value" style="font-size: 20px; margin-top: 12px">
                    {{ environment.serverVersion || '—' }}
                </div>
                <div class="stat-foot mono">{{ environment.binary || 'docker' }}</div>
            </div>
        </div>

        <div class="glass-card table-wrap">
            <n-tabs v-model:value="tab" type="line" animated>
                <n-tab-pane name="containers" tab="容器">
                    <div class="toolbar">
                        <n-checkbox v-model:checked="showAll" @update:checked="loadContainers">
                            显示已停止容器
                        </n-checkbox>
                        <div class="toolbar-actions">
                            <n-button size="small" quaternary :loading="loading" @click="refresh">
                                <template #icon><i class="fa-solid fa-rotate"></i></template>
                                刷新
                            </n-button>
                        </div>
                    </div>
                    <n-data-table :columns="containerColumns" :data="containers" :loading="loading" :bordered="false"
                        size="small" :row-key="(row) => row.id" />
                </n-tab-pane>

                <n-tab-pane name="images" tab="镜像">
                    <div class="toolbar">
                        <span class="toolbar-title">本地镜像（只读）</span>
                        <div class="toolbar-actions">
                            <n-button size="small" quaternary :loading="imageLoading" @click="loadImages">
                                <template #icon><i class="fa-solid fa-rotate"></i></template>
                                刷新
                            </n-button>
                        </div>
                    </div>
                    <n-data-table :columns="imageColumns" :data="images" :loading="imageLoading" :bordered="false"
                        size="small" :row-key="(row) => row.id" />
                </n-tab-pane>

                <n-tab-pane name="hosts" tab="主机">
                    <div class="toolbar">
                        <span class="toolbar-title">连接配置</span>
                        <div class="toolbar-actions">
                            <n-button size="small" type="primary" @click="hostForm.visible = true">
                                <template #icon><i class="fa-solid fa-plus"></i></template>
                                新增主机
                            </n-button>
                        </div>
                    </div>
                    <n-data-table :columns="hostColumns" :data="hosts" :bordered="false" size="small"
                        :row-key="(row) => row.id" />
                </n-tab-pane>
            </n-tabs>
        </div>

        <n-modal v-model:show="hostForm.visible" preset="card" title="新增主机" style="width: 460px"
            :mask-closable="false">
            <n-form ref="hostFormRef" :model="hostForm.data" :rules="hostRules" label-placement="left" label-width="90">
                <n-form-item label="名称" path="name">
                    <n-input v-model:value="hostForm.data.name" placeholder="如：本机" />
                </n-form-item>
                <n-form-item label="连接地址" path="host">
                    <n-input v-model:value="hostForm.data.host" placeholder="local 或 tcp://host:2375" />
                </n-form-item>
                <n-form-item label="启用" path="enabled">
                    <n-switch v-model:value="hostForm.data.enabled" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="hostForm.visible = false">取消</n-button>
                    <n-button type="primary" :loading="hostForm.loading" @click="submitHost">保存</n-button>
                </div>
            </template>
        </n-modal>
    </div>
</template>

<script>
import { h } from 'vue'
import http from '@/libs/http'

export default {
    name: 'DockerManagement',
    data() {
        return {
            tab: 'containers',
            loading: false,
            imageLoading: false,
            showAll: true,
            containers: [],
            images: [],
            hosts: [],
            summary: { running: 0, total: 0, available: false },
            environment: { cliAvailable: false, daemonOnline: false, serverVersion: null, message: '', binary: 'docker' },
            hostForm: {
                visible: false,
                loading: false,
                data: { name: '', host: 'local', enabled: true }
            },
            hostRules: {
                name: { required: true, message: '请填写主机名称', trigger: ['input', 'blur'] }
            }
        }
    },
    computed: {
        containerColumns() {
            return [
                { title: 'ID', key: 'id', width: 110, render: (row) => h('span', { class: 'mono' }, row.id) },
                { title: '名称', key: 'name', minWidth: 140 },
                { title: '镜像', key: 'image', minWidth: 180, ellipsis: { tooltip: true } },
                {
                    title: '状态',
                    key: 'state',
                    width: 110,
                    render: (row) => h('span', {
                        class: ['pill', row.state === 'running' ? 'ok' : 'off']
                    }, [h('span', { class: 'dot' }), row.state])
                },
                { title: '详细信息', key: 'status', minWidth: 170, ellipsis: { tooltip: true } },
                { title: '端口', key: 'ports', minWidth: 140, ellipsis: { tooltip: true } }
            ]
        },
        imageColumns() {
            return [
                { title: '仓库', key: 'repository', minWidth: 180 },
                { title: '标签', key: 'tag', width: 120 },
                { title: '镜像 ID', key: 'id', width: 140, render: (row) => h('span', { class: 'mono' }, row.id) },
                { title: '大小', key: 'size', width: 110 },
                { title: '创建时间', key: 'created', width: 150 }
            ]
        },
        hostColumns() {
            return [
                { title: '名称', key: 'name', minWidth: 140 },
                { title: '连接地址', key: 'host', minWidth: 200, render: (row) => h('span', { class: 'mono' }, row.host) },
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
                    render: (row) => h('div', { class: 'op-actions' }, [
                        h('i', {
                            class: 'fa-solid fa-trash op-icon',
                            style: { color: 'var(--danger)' },
                            title: '删除',
                            onClick: () => this.removeHost(row)
                        })
                    ])
                }
            ]
        }
    },
    mounted() {
        this.refresh()
        this.loadHosts()
    },
    methods: {
        async refresh() {
            await Promise.all([this.loadEnvironment(), this.loadContainers()])
        },
        async loadEnvironment() {
            try {
                this.environment = await http.get('/docker/environment')
            } catch (error) {
                // 拦截器已提示
            }
        },
        async loadContainers() {
            this.loading = true
            try {
                const [containers, summary] = await Promise.all([
                    http.get('/docker/containers', { all: this.showAll }),
                    http.get('/docker/summary')
                ])
                this.containers = containers || []
                this.summary = summary || this.summary
            } catch (error) {
                this.containers = []
            } finally {
                this.loading = false
            }
        },
        async loadImages() {
            this.imageLoading = true
            try {
                this.images = (await http.get('/docker/images')) || []
            } catch (error) {
                this.images = []
            } finally {
                this.imageLoading = false
            }
        },
        async loadHosts() {
            try {
                this.hosts = (await http.get('/docker/hosts')) || []
            } catch (error) {
                this.hosts = []
            }
        },
        submitHost() {
            this.$refs.hostFormRef.validate(async (errors) => {
                if (errors) return
                this.hostForm.loading = true
                try {
                    await http.post('/docker/hosts', this.hostForm.data)
                    this.$message.success('保存成功')
                    this.hostForm.visible = false
                    this.hostForm.data = { name: '', host: 'local', enabled: true }
                    this.loadHosts()
                } catch (error) {
                    // 拦截器已提示
                } finally {
                    this.hostForm.loading = false
                }
            })
        },
        removeHost(row) {
            this.$dialog.warning({
                title: '删除主机',
                content: `确认删除「${row.name}」？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await http.delete(`/docker/hosts/${row.id}`)
                    this.$message.success('已删除')
                    this.loadHosts()
                }
            })
        }
    },
    watch: {
        tab(value) {
            if (value === 'images' && this.images.length === 0) {
                this.loadImages()
            }
        }
    }
}
</script>

<style scoped>
.table-wrap {
    padding: 12px 14px 14px;
}

.toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
}

.toolbar-title {
    font-size: 13px;
    color: var(--text-2);
}

.toolbar-actions {
    display: flex;
    gap: 8px;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
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
</style>