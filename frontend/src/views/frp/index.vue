<template>
    <ManagementShell kind="frp" title="Frp 管理" subtitle="内网穿透与隧道管理" :loading="loading" :busy="reloading || restarting"
        @refresh="load">
        <div class="mgmt-content">
            <!-- 速览卡：服务状态 + 隧道统计（对齐安卓 FrpScreen） -->
            <section class="hero-panel">
                <div class="hero-head">
                    <i class="fa-solid fa-diagram-project"></i>
                    <div class="hero-head-text">
                        <strong>Frp 隧道服务</strong>
                        <span>{{ known ? (runtime.container || 'frpc') : '连接你的内网服务' }}</span>
                    </div>
                    <span class="hero-tag" :class="{ alert: tunnelsError || (known && !runtime.containerRunning) }">
                        {{ statusText }}
                    </span>
                </div>
                <div class="hero-metrics">
                    <div class="hero-metric"><strong>{{ known ? (runtime.total || items.length) : '—' }}</strong><span>全部隧道</span></div>
                    <div class="hero-metric"><strong>{{ known ? runningCount : '—' }}</strong><span>运行中</span></div>
                    <div class="hero-metric"><strong>{{ known ? disabledCount : '—' }}</strong><span>已停用</span></div>
                </div>
                <div class="hero-divider"></div>
                <div class="hero-foot">将家里的服务连接到外网</div>
            </section>

            <div v-if="tunnelsError" class="notice-line"><i class="fa-solid fa-triangle-exclamation"></i>{{ tunnelsError }}</div>
            <div v-if="runtime.message" class="notice-line"><i class="fa-solid fa-circle-info"></i>{{ runtime.message }}</div>
            <div v-if="runtime.configSharedWithFrpc === false" class="notice-line danger">
                <i class="fa-solid fa-circle-exclamation"></i>
                管理页读取的 frpc.ini 与 frpc 容器实际加载的不是同一份：保存与热加载都不会影响线上隧道。
                请让两边的挂载指向同一目录（宿主机 {{ runtime.hostPath }}）。
            </div>

            <!-- 服务操作 -->
            <div class="action-row">
                <n-button secondary :loading="reloading" @click="reload">
                    <template #icon><i class="fa-solid fa-bolt"></i></template>热加载
                </n-button>
                <n-button secondary @click="showConfig">
                    <template #icon><i class="fa-solid fa-file-code"></i></template>配置
                </n-button>
                <n-button secondary :loading="restarting" @click="restart">
                    <template #icon><i class="fa-solid fa-power-off"></i></template>重启
                </n-button>
            </div>

            <div class="section-title">
                <h2>我的隧道</h2>
                <button class="link-btn" type="button" @click="openForm()">
                    <i class="fa-solid fa-plus"></i>新增隧道
                </button>
            </div>

            <n-input v-model:value="keyword" clearable placeholder="搜索名称 / 端口" size="large" class="search-box">
                <template #prefix><i class="fa-solid fa-magnifying-glass"></i></template>
            </n-input>

            <div v-if="known && !loading && !items.length" class="empty-panel">
                <span class="empty-icon"><i class="fa-solid fa-diagram-project"></i></span>
                <strong>还没有隧道</strong>
                <span>点击「新增隧道」，连接你的第一个内网服务</span>
            </div>

            <article v-for="row in filtered" :key="row.name" class="panel tunnel-card">
                <div class="tunnel-head">
                    <i :class="['fa-solid', row.proxyType === 'http' || row.proxyType === 'https' ? 'fa-globe' : 'fa-diagram-project', 'tunnel-glyph']"></i>
                    <strong class="tunnel-name">{{ row.name }}</strong>
                    <span class="chip-tag" :class="statusMeta(row).cls">{{ statusMeta(row).text }}</span>
                    <n-switch :value="Boolean(row.enabled)" size="small" :loading="toggling === row.name"
                        @update:value="toggle(row)" />
                </div>

                <div class="tunnel-route">
                    <div class="route-side">
                        <span>本地服务</span>
                        <strong>{{ row.localAddress || '—' }}</strong>
                    </div>
                    <i class="fa-solid fa-arrow-right route-arrow"></i>
                    <div class="route-side end">
                        <span>{{ row.proxyType === 'http' || row.proxyType === 'https' ? '远程域名' : '云端端口' }}</span>
                        <strong>{{ row.remoteAddress || row.remotePort || '—' }}</strong>
                    </div>
                </div>

                <div class="tunnel-foot">
                    <span class="chip-tag outline">{{ String(row.proxyType || '').toUpperCase() }}</span>
                    <span class="tunnel-remark">{{ row.remark || '' }}</span>
                    <button class="link-btn" type="button" @click="openForm(row)">编辑</button>
                    <button class="link-btn danger" type="button" @click="remove(row)">删除</button>
                </div>
            </article>
        </div>

        <!-- 编辑 / 新增隧道 -->
        <n-modal v-model:show="form.visible" preset="card" :title="form.editing ? '编辑隧道' : '新增隧道'"
            style="width: 560px; max-width: calc(100vw - 24px)" :mask-closable="false">
            <n-form ref="formRef" :model="form.data" :rules="rules" label-placement="top">
                <n-form-item label="隧道名称" path="name">
                    <n-input v-model:value="form.data.name" placeholder="如：web-ssh" :disabled="form.editing" />
                </n-form-item>
                <n-form-item label="代理类型" path="proxyType">
                    <div class="chips">
                        <button v-for="type in typeOptions" :key="type.value" type="button" class="chip"
                            :class="{ active: form.data.proxyType === type.value }"
                            @click="form.data.proxyType = type.value">{{ type.label }}</button>
                    </div>
                </n-form-item>
                <div class="pair">
                    <n-form-item label="本地地址" path="localIp">
                        <n-input v-model:value="form.data.localIp" placeholder="127.0.0.1" />
                    </n-form-item>
                    <n-form-item label="本地端口" path="localPort">
                        <n-input-number v-model:value="form.data.localPort" :min="1" :max="65535" style="width: 100%" />
                    </n-form-item>
                </div>
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
                    <n-input v-model:value="form.data.remark" placeholder="可选" />
                </n-form-item>
                <n-form-item label="高级选项">
                    <n-space :size="18">
                        <n-checkbox v-model:checked="form.data.encryption">加密传输</n-checkbox>
                        <n-checkbox v-model:checked="form.data.compression">压缩传输</n-checkbox>
                    </n-space>
                </n-form-item>
                <n-form-item label="启用">
                    <n-switch v-model:value="form.data.enabled" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-actions">
                    <n-button @click="form.visible = false">取消</n-button>
                    <n-button type="primary" :loading="form.loading" @click="submit">保存并应用</n-button>
                </div>
            </template>
        </n-modal>

        <!-- 配置原文 -->
        <n-modal v-model:show="config.visible" preset="card" title="frpc.ini 配置原文"
            style="width: 780px; max-width: calc(100vw - 24px)">
            <div class="config-meta mono">
                <span>{{ runtime.configPath }}</span>
                <span>宿主机 {{ runtime.hostPath }}</span>
            </div>
            <n-input v-model:value="config.content" type="textarea" :rows="20" class="config-editor" />
            <template #footer>
                <div class="modal-actions">
                    <n-button @click="config.visible = false">关闭</n-button>
                    <n-button type="primary" :loading="config.loading" @click="saveConfig">保存并热加载</n-button>
                </div>
            </template>
        </n-modal>
    </ManagementShell>
</template>

<script>
import { NButton, NCheckbox, NForm, NFormItem, NInput, NInputNumber, NModal, NSpace, NSwitch } from 'naive-ui'
import ManagementShell from '@/components/main/management-shell.vue'
import http from '@/libs/http'

export default {
    name: 'FrpManagement',
    components: { ManagementShell, NButton, NCheckbox, NForm, NFormItem, NInput, NInputNumber, NModal, NSpace, NSwitch },
    data() {
        return {
            loading: false,
            reloading: false,
            restarting: false,
            toggling: '',
            keyword: '',
            tunnelsError: '',
            items: [],
            runtime: {
                total: 0, enabled: 0, disabled: 0, running: 0, error: 0,
                container: 'frpc', containerRunning: false, containerStatus: '',
                configPath: '/etc/frp/frpc.ini', hostPath: '', configSharedWithFrpc: null, message: ''
            },
            typeOptions: [
                { label: 'TCP', value: 'tcp' },
                { label: 'UDP', value: 'udp' },
                { label: 'HTTP', value: 'http' },
                { label: 'HTTPS', value: 'https' }
            ],
            form: { visible: false, editing: false, loading: false, data: this.emptyForm() },
            config: { visible: false, loading: false, content: '' },
            rules: {
                name: { required: true, message: '请填写隧道名称', trigger: ['input', 'blur'] },
                localPort: { type: 'number', required: true, message: '请填写本地端口', trigger: ['blur', 'change'] }
            }
        }
    },
    computed: {
        known() {
            return !this.tunnelsError && !this.loading
        },
        runningCount() {
            return this.items.filter((row) => row.enabled && row.status === 'running').length
        },
        disabledCount() {
            return this.items.filter((row) => !row.enabled).length
        },
        statusText() {
            if (this.loading) return '正在连接'
            if (this.tunnelsError) return '连接异常'
            if (this.runtime.containerRunning) return '运行中'
            return '未运行'
        },
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
        }
    },
    mounted() {
        this.load()
    },
    methods: {
        emptyForm() {
            return {
                name: '', proxyType: 'tcp', localIp: '127.0.0.1', localPort: null, remotePort: null,
                customDomains: '', subdomain: '', remark: '', encryption: false, compression: false, enabled: true
            }
        },
        statusMeta(row) {
            if (!row.enabled) return { text: '已停用', cls: 'muted' }
            if (row.status === 'running') return { text: '运行中', cls: '' }
            if (row.failed) return { text: '异常', cls: 'danger' }
            if (!row.status) return { text: '未知', cls: 'amber' }
            return { text: row.statusText || '未知', cls: 'amber' }
        },
        async load() {
            this.loading = true
            this.tunnelsError = ''
            try {
                const [items, runtime] = await Promise.all([
                    http.get('/frp/tunnels'),
                    http.get('/frp/runtime')
                ])
                this.items = items || []
                this.runtime = Object.assign(this.runtime, runtime || {})
            } catch (error) {
                this.tunnelsError = '暂时无法连接 Frp，点击右上角刷新重试。'
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
            this.toggling = row.name
            try {
                await http.post('/frp/tunnels/' + encodeURIComponent(row.name) + '/toggle?enabled=' + !row.enabled)
                this.$message.success(row.enabled ? '已停用并热加载' : '已启用并热加载')
                await this.load()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.toggling = ''
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
                        await http.post('/frp/restart')
                        this.$message.success('已重启 ' + (this.runtime.container || 'frpc'))
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
.action-row {
    display: flex;
    gap: 8px;
    margin-bottom: 14px;
}

.action-row .n-button {
    flex: 1;
    height: 40px;
    border-radius: 13px;
}

.search-box {
    margin-bottom: 12px;
}

/* 隧道卡（对齐安卓 TunnelCard） */
.tunnel-card {
    padding: 14px 16px;
}

.tunnel-head {
    display: flex;
    align-items: center;
    gap: 8px;
}

.tunnel-glyph {
    color: var(--pine);
    font-size: 17px;
}

.tunnel-name {
    flex: 1;
    min-width: 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.tunnel-route {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
    padding: 10px 12px;
    border-radius: 14px;
    background: var(--paper);
}

.route-side {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.route-side.end {
    align-items: flex-end;
    text-align: right;
}

.route-side span {
    font-size: 10px;
    color: var(--muted);
}

.route-side strong {
    font-size: 12px;
    font-weight: 500;
    color: var(--ink);
    overflow-wrap: anywhere;
}

.route-arrow {
    flex: none;
    color: var(--muted);
    font-size: 13px;
}

.tunnel-foot {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 10px;
}

.tunnel-remark {
    flex: 1;
    min-width: 0;
    font-size: 11px;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.chip {
    padding: 7px 16px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: #fff;
    color: var(--muted);
    font-family: inherit;
    font-size: 12.5px;
    cursor: pointer;
}

.chip.active {
    border-color: var(--pine);
    background: var(--soft-green);
    color: var(--pine);
    font-weight: 600;
}

.pair {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
}

.config-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 16px;
    margin-bottom: 10px;
    font-size: 11.5px;
}

.modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}
</style>
