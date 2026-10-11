<template>
    <div>
        <h3 class="page-title">
            <i class="fa-solid fa-network-wired"></i>
            Frp 管理
            <span class="sub">内网穿透隧道配置</span>
        </h3>

        <div class="stat-grid" style="margin-bottom: 16px">
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-diagram-project" style="color: var(--accent)"></i> 隧道总数</div>
                <div class="stat-value">{{ runtime.total }}</div>
                <div class="stat-foot">已启用 {{ runtime.enabled }} 个</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-plug" style="color: var(--warn)"></i> 运行模式</div>
                <div class="stat-value" style="font-size: 17px; margin-top: 14px">
                    <span class="pill warn"><span class="dot"></span>配置管理阶段</span>
                </div>
                <div class="stat-foot">frpc 进程托管待接入</div>
            </div>
        </div>

        <div class="glass-card table-wrap">
            <div class="toolbar">
                <span class="toolbar-title">隧道列表</span>
                <div class="toolbar-actions">
                    <n-button size="small" quaternary :loading="loading" @click="load">
                        <template #icon><i class="fa-solid fa-rotate"></i></template>
                        刷新
                    </n-button>
                    <n-button size="small" type="primary" @click="openForm()">
                        <template #icon><i class="fa-solid fa-plus"></i></template>
                        新增隧道
                    </n-button>
                </div>
            </div>
            <n-data-table :columns="columns" :data="items" :loading="loading" :bordered="false" size="small"
                :row-key="(row) => row.id" />
        </div>

        <n-modal v-model:show="form.visible" preset="card" :title="form.data.id ? '编辑隧道' : '新增隧道'"
            style="width: 560px" :mask-closable="false">
            <n-form ref="formRef" :model="form.data" :rules="rules" label-placement="left" label-width="100">
                <n-form-item label="隧道名称" path="name">
                    <n-input v-model:value="form.data.name" placeholder="如：web-ssh" />
                </n-form-item>
                <n-form-item label="代理类型" path="proxyType">
                    <n-select v-model:value="form.data.proxyType" :options="typeOptions" />
                </n-form-item>
                <n-form-item label="本地地址" path="localIp">
                    <n-input v-model:value="form.data.localIp" placeholder="127.0.0.1" />
                </n-form-item>
                <n-form-item label="本地端口" path="localPort">
                    <n-input-number v-model:value="form.data.localPort" :min="1" :max="65535" style="width: 100%" />
                </n-form-item>
                <n-form-item v-if="isPortType" label="远程端口" path="remotePort">
                    <n-input-number v-model:value="form.data.remotePort" :min="1" :max="65535" style="width: 100%"
                        placeholder="服务端暴露端口" />
                </n-form-item>
                <n-form-item v-else label="自定义域名" path="customDomain">
                    <n-input v-model:value="form.data.customDomain" placeholder="如：app.example.com" />
                </n-form-item>
                <n-form-item v-if="!isPortType" label="子域名" path="subdomain">
                    <n-input v-model:value="form.data.subdomain" placeholder="如：demo" />
                </n-form-item>
                <n-form-item label="附加参数" path="extra">
                    <n-input v-model:value="form.data.extra" type="textarea" :rows="2"
                        placeholder="透传给 frpc 的附加参数，可留空" />
                </n-form-item>
                <n-form-item label="启用" path="enabled">
                    <n-switch v-model:value="form.data.enabled" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="form.visible = false">取消</n-button>
                    <n-button type="primary" :loading="form.loading" @click="submit">保存</n-button>
                </div>
            </template>
        </n-modal>
    </div>
</template>

<script>
import { h } from 'vue'
import http from '@/libs/http'

export default {
    name: 'FrpManagement',
    data() {
        return {
            loading: false,
            items: [],
            runtime: { total: 0, enabled: 0, binaryConfigured: false },
            typeOptions: [
                { label: 'TCP', value: 'tcp' },
                { label: 'UDP', value: 'udp' },
                { label: 'HTTP', value: 'http' },
                { label: 'HTTPS', value: 'https' }
            ],
            form: {
                visible: false,
                loading: false,
                data: this.emptyForm()
            },
            rules: {
                name: { required: true, message: '请填写隧道名称', trigger: ['input', 'blur'] },
                localPort: { type: 'number', required: true, message: '请填写本地端口', trigger: ['blur', 'change'] }
            }
        }
    },
    computed: {
        isPortType() {
            return this.form.data.proxyType === 'tcp' || this.form.data.proxyType === 'udp'
        },
        columns() {
            return [
                { title: '名称', key: 'name', minWidth: 120 },
                {
                    title: '类型',
                    key: 'proxyType',
                    width: 80,
                    render: (row) => h('span', { class: 'mono' }, String(row.proxyType).toUpperCase())
                },
                {
                    title: '本地',
                    key: 'local',
                    minWidth: 150,
                    render: (row) => h('span', { class: 'mono' }, `${row.localIp}:${row.localPort}`)
                },
                {
                    title: '远程',
                    key: 'remote',
                    minWidth: 140,
                    render: (row) => h('span', { class: 'mono' },
                        row.customDomain || row.subdomain || (row.remotePort || '-'))
                },
                {
                    title: '状态',
                    key: 'enabled',
                    width: 100,
                    render: (row) => h('span', { class: ['pill', row.enabled ? 'ok' : 'off'] }, [
                        h('span', { class: 'dot' }),
                        row.enabled ? '已启用' : '已停用'
                    ])
                },
                { title: '更新时间', key: 'updatedAt', width: 170 },
                {
                    title: '操作',
                    key: 'actions',
                    width: 150,
                    render: (row) => h('div', { class: 'op-actions' }, [
                        h('i', {
                            class: 'fa-solid fa-pen-to-square op-icon',
                            style: { color: 'var(--accent-2)' },
                            title: '编辑',
                            onClick: () => this.openForm(row)
                        }),
                        h('i', {
                            class: row.enabled ? 'fa-solid fa-toggle-on op-icon' : 'fa-solid fa-toggle-off op-icon',
                            style: { color: row.enabled ? 'var(--ok)' : 'var(--text-3)', fontSize: '17px' },
                            title: row.enabled ? '停用' : '启用',
                            onClick: () => this.toggle(row)
                        }),
                        h('i', {
                            class: 'fa-solid fa-trash op-icon',
                            style: { color: 'var(--danger)' },
                            title: '删除',
                            onClick: () => this.remove(row)
                        })
                    ])
                }
            ]
        }
    },
    mounted() {
        this.load()
    },
    methods: {
        emptyForm() {
            return {
                id: null,
                name: '',
                proxyType: 'tcp',
                localIp: '127.0.0.1',
                localPort: null,
                remotePort: null,
                customDomain: '',
                subdomain: '',
                extra: '',
                enabled: true
            }
        },
        async load() {
            this.loading = true
            try {
                const [items, runtime] = await Promise.all([
                    http.get('/frp/configs'),
                    http.get('/frp/runtime')
                ])
                this.items = items || []
                this.runtime = runtime || this.runtime
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        openForm(row) {
            this.form.data = row ? Object.assign(this.emptyForm(), row) : this.emptyForm()
            this.form.visible = true
        },
        submit() {
            this.$refs.formRef.validate(async (errors) => {
                if (errors) return
                this.form.loading = true
                try {
                    const payload = Object.assign({}, this.form.data)
                    if (payload.id) {
                        await http.put(`/frp/configs/${payload.id}`, payload)
                    } else {
                        await http.post('/frp/configs', payload)
                    }
                    this.$message.success('保存成功')
                    this.form.visible = false
                    this.load()
                } catch (error) {
                    // 拦截器已提示
                } finally {
                    this.form.loading = false
                }
            })
        },
        async toggle(row) {
            await http.post(`/frp/configs/${row.id}/toggle?enabled=${!row.enabled}`)
            this.$message.success(row.enabled ? '已停用' : '已启用')
            this.load()
        },
        remove(row) {
            this.$dialog.warning({
                title: '删除隧道',
                content: `确认删除「${row.name}」？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await http.delete(`/frp/configs/${row.id}`)
                    this.$message.success('已删除')
                    this.load()
                }
            })
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
    font-size: 14px;
    font-weight: 600;
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
/* 移动端：工具栏换行、统计卡片收窄留白；表格保持横向滚动，表单弹窗占满宽度 */
@media (max-width: 900px) {
    .stat-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 10px;
    }

    .stat-card {
        padding: 14px;
    }

    .stat-card .stat-value {
        font-size: 22px;
    }

    .table-wrap {
        padding: 12px;
        border-radius: 10px;
    }

    .toolbar {
        flex-wrap: wrap;
        row-gap: 8px;
    }

    .toolbar-actions {
        margin-left: auto;
    }

    .page-title {
        font-size: 16px;
        flex-wrap: wrap;
        row-gap: 4px;
        margin-bottom: 12px;
    }
}
</style>