<template>
    <div>
        <h3 class="page-title">
            <i class="fa-solid fa-network-wired"></i>
            Frp 管理
            <span class="sub">通过 frpc.ini 管理内网穿透隧道</span>
        </h3>

        <div class="stat-grid" style="margin-bottom: 16px">
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-diagram-project" style="color: var(--accent)"></i> 隧道总数</div>
                <div class="stat-value">{{ runtime.total }}</div>
                <div class="stat-foot">启用 {{ runtime.enabled }} · 停用 {{ runtime.disabled }}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-tower-broadcast" style="color: var(--ok)"></i> 运行中</div>
                <div class="stat-value" style="color: var(--ok)">{{ runtime.running }}</div>
                <div class="stat-foot">
                    <template v-if="runtime.error">异常 {{ runtime.error }} 条</template>
                    <template v-else>无异常隧道</template>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-cube" style="color: var(--accent-2)"></i> frpc 容器</div>
                <div class="stat-value" style="font-size: 17px; margin-top: 14px">
                    <span class="pill" :class="runtime.containerRunning ? 'ok' : 'off'">
                        <span class="dot"></span>
                        {{ runtime.containerRunning ? '运行中' : (runtime.containerStatus || '未知') }}
                    </span>
                </div>
                <div class="stat-foot mono">{{ runtime.container || 'frpc' }}</div>
            </div>
        </div>

        <div v-if="runtime.message" class="glass-card notice">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span>{{ runtime.message }}</span>
        </div>

        <div class="glass-card table-wrap">
            <div class="toolbar">
                <span class="toolbar-title">
                    隧道列表
                    <span class="mono path-hint">{{ runtime.hostPath }}</span>
                </span>
                <div class="toolbar-actions">
                    <n-input v-model:value="keyword" size="small" clearable placeholder="搜索名称 / 端口"
                        style="width: 190px">
                        <template #prefix><i class="fa-solid fa-magnifying-glass"></i></template>
                    </n-input>
                    <n-button size="small" quaternary :loading="loading" @click="load">
                        <template #icon><i class="fa-solid fa-rotate"></i></template>
                        刷新
                    </n-button>
                    <n-button size="small" quaternary @click="showConfig">
                        <template #icon><i class="fa-solid fa-file-code"></i></template>
                        配置原文
                    </n-button>
                    <n-button size="small" type="warning" ghost :loading="reloading" @click="reload">
                        <template #icon><i class="fa-solid fa-bolt"></i></template>
                        热加载
                    </n-button>
                    <n-button size="small" type="error" ghost :loading="restarting" @click="restart">
                        <template #icon><i class="fa-solid fa-power-off"></i></template>
                        重启 frpc
                    </n-button>
                    <n-button size="small" type="primary" @click="openForm()">
                        <template #icon><i class="fa-solid fa-plus"></i></template>
                        新增隧道
                    </n-button>
                </div>
            </div>
            <n-data-table :columns="columns" :data="filtered" :loading="loading" :bordered="false" size="small"
                :row-key="(row) => row.name" :scroll-x="1080" />
        </div>

        <n-modal v-model:show="form.visible" preset="card" :title="form.editing ? '编辑隧道' : '新增隧道'"
            style="width: 580px" :mask-closable="false">
            <n-form ref="formRef" :model="form.data" :rules="rules" label-placement="left" label-width="100">
                <n-form-item label="隧道名称" path="name">
                    <n-input v-model:value="form.data.name" placeholder="如：web-ssh" :disabled="form.editing" />
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
                <template v-else>
                    <n-form-item label="自定义域名" path="customDomains">
                        <n-input v-model:value="form.data.customDomains" placeholder="如：app.example.com" />
                    </n-form-item>
                    <n-form-item label="子域名" path="subdomain">
                        <n-input v-model:value="form.data.subdomain" placeholder="如：demo" />
                    </n-form-item>
                </template>
                <n-form-item label="备注" path="remark">
                    <n-input v-model:value="form.data.remark" placeholder="可选，写入 remark 字段" />
                </n-form-item>
                <n-form-item label="高级选项">
                    <n-space :size="18">
                        <n-checkbox v-model:checked="form.data.encryption">加密传输</n-checkbox>
                        <n-checkbox v-model:checked="form.data.compression">压缩传输</n-checkbox>
                    </n-space>
                </n-form-item>
                <n-form-item label="启用" path="enabled">
                    <n-switch v-model:value="form.data.enabled" />
                </n-form-item>
                <n-alert v-if="!form.editing" type="info" :bordered="false">
                    保存前会先校验语法，通过后自动热加载 frpc；校验失败不会改动线上配置。
                </n-alert>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="form.visible = false">取消</n-button>
                    <n-button type="primary" :loading="form.loading" @click="submit">保存</n-button>
                </div>
            </template>
        </n-modal>

        <n-modal v-model:show="config.visible" preset="card" title="frpc.ini 配置原文" style="width: 780px">
            <div class="config-meta mono">
                <span>{{ runtime.configPath }}</span>
                <span>宿主机 {{ runtime.hostPath }}</span>
            </div>
            <n-input v-model:value="config.content" type="textarea" :rows="22" class="config-editor" />
            <template #footer>
                <div class="modal-footer">
                    <span class="config-hint">保存前自动校验语法，并把上一版备份为 {{ runtime.configPath }}.bak</span>
                    <n-button @click="config.visible = false">关闭</n-button>
                    <n-button type="primary" :loading="config.loading" @click="saveConfig">保存并热加载</n-button>
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
            reloading: false,
            restarting: false,
            keyword: '',
            items: [],
            runtime: {
                total: 0,
                enabled: 0,
                disabled: 0,
                running: 0,
                error: 0,
                container: 'frpc',
                containerRunning: false,
                containerStatus: '',
                configPath: '/etc/frp/frpc.ini',
                hostPath: '/root/frp/frpc.ini',
                message: ''
            },
            typeOptions: [
                { label: 'TCP', value: 'tcp' },
                { label: 'UDP', value: 'udp' },
                { label: 'HTTP', value: 'http' },
                { label: 'HTTPS', value: 'https' }
            ],
            form: {
                visible: false,
                editing: false,
                loading: false,
                data: this.emptyForm()
            },
            config: {
                visible: false,
                loading: false,
                content: ''
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
        filtered() {
            const keyword = this.keyword.trim().toLowerCase()
            if (!keyword) return this.items
            return this.items.filter((row) => {
                const text = [row.name, row.localAddress, row.remoteAddress, row.statusText, row.remark]
                    .join(' ').toLowerCase()
                return text.includes(keyword)
            })
        },
        columns() {
            return [
                {
                    title: '状态',
                    key: 'status',
                    width: 96,
                    render: (row) => {
                        const meta = this.statusMeta(row)
                        return h('span', { class: ['pill', meta.cls] }, [
                            h('span', { class: 'dot' }),
                            meta.text
                        ])
                    }
                },
                { title: '隧道名称', key: 'name', minWidth: 130 },
                {
                    title: '类型',
                    key: 'proxyType',
                    width: 76,
                    render: (row) => h('span', { class: 'mono' }, String(row.proxyType).toUpperCase())
                },
                {
                    title: '本地监听',
                    key: 'localAddress',
                    minWidth: 165,
                    render: (row) => h('div', { class: 'cell-stack' }, [
                        h('span', { class: 'mono strong' }, row.localAddress || '-'),
                        row.probe ? h('span', {
                            class: ['probe', row.probe === 'alive' ? 'ok' : 'bad']
                        }, row.probeText) : null
                    ])
                },
                {
                    title: '远程端口',
                    key: 'remoteAddress',
                    minWidth: 140,
                    render: (row) => h('div', { class: 'cell-stack' }, [
                        h('span', { class: 'mono strong' }, row.remotePort ? String(row.remotePort) : '-'),
                        row.customDomains ? h('span', { class: 'probe' }, row.customDomains) : null,
                        row.subdomain ? h('span', { class: 'probe' }, 'subdomain: ' + row.subdomain) : null
                    ])
                },
                {
                    title: '备注',
                    key: 'remark',
                    minWidth: 110,
                    render: (row) => h('span', { class: 'remark' }, row.remark || '-')
                },
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
                name: '',
                proxyType: 'tcp',
                localIp: '127.0.0.1',
                localPort: null,
                remotePort: null,
                customDomains: '',
                subdomain: '',
                remark: '',
                encryption: false,
                compression: false,
                enabled: true
            }
        },
        statusMeta(row) {
            if (!row.enabled) return { text: '已停用', cls: 'off' }
            if (row.status === 'running') return { text: '运行中', cls: 'ok' }
            if (row.failed) return { text: '异常', cls: 'danger' }
            if (!row.status) return { text: '未知', cls: 'warn' }
            return { text: row.statusText || '未知', cls: 'warn' }
        },
        async load() {
            this.loading = true
            try {
                const [items, runtime] = await Promise.all([
                    http.get('/frp/tunnels'),
                    http.get('/frp/runtime')
                ])
                this.items = items || []
                this.runtime = Object.assign(this.runtime, runtime || {})
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        openForm(row) {
            this.form.editing = Boolean(row)
            this.form.data = row ? {
                name: row.name,
                proxyType: row.proxyType,
                localIp: row.localIp,
                localPort: row.localPort,
                remotePort: row.remotePort,
                customDomains: row.customDomains,
                subdomain: row.subdomain,
                remark: row.remark,
                encryption: Boolean(row.encryption),
                compression: Boolean(row.compression),
                enabled: Boolean(row.enabled)
            } : this.emptyForm()
            this.form.visible = true
        },
        submit() {
            this.$refs.formRef.validate(async (errors) => {
                if (errors) return
                this.form.loading = true
                try {
                    const payload = Object.assign({}, this.form.data)
                    if (this.form.editing) {
                        await http.put('/frp/tunnels/' + encodeURIComponent(payload.name), payload)
                        this.$message.success('已保存，配置已热加载')
                    } else {
                        await http.post('/frp/tunnels', payload)
                        this.$message.success('新增成功，配置已热加载')
                    }
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
            try {
                await http.post('/frp/tunnels/' + encodeURIComponent(row.name) + '/toggle?enabled=' + !row.enabled)
                this.$message.success(row.enabled ? '已停用并热加载' : '已启用并热加载')
                this.load()
            } catch (error) {
                // 拦截器已提示
            }
        },
        remove(row) {
            this.$dialog.warning({
                title: '删除隧道',
                content: '确认删除「' + row.name + '」？删除后会立即热加载 frpc。',
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await http.delete('/frp/tunnels/' + encodeURIComponent(row.name))
                    this.$message.success('已删除并热加载')
                    this.load()
                }
            })
        },
        reload() {
            this.$dialog.info({
                title: '热加载配置',
                content: '调用 frpc reload，不断开现有连接。确认执行？',
                positiveText: '执行',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.reloading = true
                    try {
                        const data = await http.post('/frp/reload')
                        if (data && data.success) {
                            this.$message.success('热加载成功')
                        } else {
                            this.$message.warning('热加载返回异常：' + ((data && data.output) || '无输出'))
                        }
                        this.load()
                    } catch (error) {
                        // 拦截器已提示
                    } finally {
                        this.reloading = false
                    }
                }
            })
        },
        restart() {
            this.$dialog.warning({
                title: '重启 frpc',
                content: '将执行 docker restart ' + (this.runtime.container || 'frpc') + '，所有隧道会短暂中断。确认重启？',
                positiveText: '重启',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.restarting = true
                    try {
                        const data = await http.post('/frp/restart')
                        this.$message.success('已重启 ' + ((data && data.container) || 'frpc'))
                        setTimeout(() => this.load(), 1200)
                    } catch (error) {
                        // 拦截器已提示
                    } finally {
                        this.restarting = false
                    }
                }
            })
        },
        async showConfig() {
            try {
                const data = await http.get('/frp/config')
                this.config.content = (data && data.content) || ''
                this.config.visible = true
            } catch (error) {
                // 拦截器已提示
            }
        },
        saveConfig() {
            this.$dialog.warning({
                title: '保存配置',
                content: '保存前会校验语法，通过后热加载 frpc。确认保存？',
                positiveText: '保存',
                negativeText: '取消',
                onPositiveClick: async () => {
                    this.config.loading = true
                    try {
                        await http.put('/frp/config', { content: this.config.content })
                        this.$message.success('已保存并热加载')
                        this.config.visible = false
                        this.load()
                    } catch (error) {
                        // 拦截器已提示
                    } finally {
                        this.config.loading = false
                    }
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
    gap: 10px;
    margin-bottom: 12px;
}

.toolbar-title {
    font-size: 14px;
    font-weight: 600;
    display: inline-flex;
    align-items: baseline;
    gap: 10px;
    flex-wrap: wrap;
}

.path-hint {
    font-weight: 400;
    font-size: 11.5px;
}

.toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.notice {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    margin-bottom: 14px;
    font-size: 13px;
    color: var(--warn);
}

.cell-stack {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.mono.strong {
    color: var(--text-1);
    font-weight: 600;
}

.probe {
    font-size: 11px;
    color: var(--text-3);
}

.probe.ok {
    color: var(--ok);
}

.probe.bad {
    color: var(--danger);
}

.remark {
    font-size: 12px;
    color: var(--text-3);
}

.modal-footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
}

.config-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    margin-bottom: 8px;
}

.config-hint {
    margin-right: auto;
    font-size: 12px;
    color: var(--text-3);
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

.config-editor :deep(textarea) {
    font-family: Consolas, "Segoe UI", monospace;
    font-size: 12.5px;
    line-height: 1.55;
}

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
        width: 100%;
    }

    .toolbar-actions :deep(.n-input) {
        width: 100% !important;
    }

    .page-title {
        font-size: 16px;
        flex-wrap: wrap;
        row-gap: 4px;
        margin-bottom: 12px;
    }
}
</style>
