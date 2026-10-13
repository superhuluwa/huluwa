<template>
    <div>
        <h3 class="page-title">
            <i class="fa-solid fa-paper-plane"></i>
            推送设置
            <span class="sub">消息推送渠道与订阅</span>
        </h3>

        <div class="stat-grid" style="margin-bottom: 16px">
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-tower-broadcast" style="color: var(--accent)"></i> 渠道总数</div>
                <div class="stat-value">{{ summary.total }}</div>
                <div class="stat-foot">已启用 {{ summary.enabled }} 个</div>
            </div>
        </div>

        <div class="glass-card table-wrap">
            <div class="toolbar">
                <span class="toolbar-title">推送渠道</span>
                <div class="toolbar-actions">
                    <n-button size="small" quaternary :loading="loading" @click="load">
                        <template #icon><i class="fa-solid fa-rotate"></i></template>
                        刷新
                    </n-button>
                    <n-button size="small" type="primary" @click="openForm()">
                        <template #icon><i class="fa-solid fa-plus"></i></template>
                        新增渠道
                    </n-button>
                </div>
            </div>
            <n-data-table :columns="columns" :data="items" :loading="loading" :bordered="false" size="small"
                :row-key="(row) => row.id" />
        </div>

        <n-modal v-model:show="form.visible" preset="card" :title="form.data.id ? '编辑渠道' : '新增渠道'"
            style="width: 560px" :mask-closable="false">
            <n-form ref="formRef" :model="form.data" :rules="rules" label-placement="left" label-width="100">
                <n-form-item label="渠道名称" path="name">
                    <n-input v-model:value="form.data.name" placeholder="如：运维告警群" />
                </n-form-item>
                <n-form-item label="推送类型" path="type">
                    <n-select v-model:value="form.data.type" :options="typeOptions" />
                </n-form-item>
                <n-form-item label="渠道参数" path="config">
                    <n-input v-model:value="form.data.config" type="textarea" :rows="3"
                        placeholder="JSON 文本，如 {&quot;url&quot;:&quot;https://...&quot;}" />
                </n-form-item>
                <n-form-item label="订阅事件" path="subscribe">
                    <n-input v-model:value="form.data.subscribe" placeholder="逗号分隔，留空表示全部" />
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
import {
    NButton,
    NDataTable,
    NForm,
    NFormItem,
    NInput,
    NModal,
    NSelect,
    NSwitch
} from 'naive-ui'
import { h } from 'vue'
import http from '@/libs/http'

export default {
    components: { NButton, NDataTable, NForm, NFormItem, NInput, NModal, NSelect, NSwitch },
    name: 'PushSettings',
    data() {
        return {
            loading: false,
            items: [],
            summary: { total: 0, enabled: 0 },
            typeOptions: [],
            form: {
                visible: false,
                loading: false,
                data: this.emptyForm()
            },
            rules: {
                name: { required: true, message: '请填写渠道名称', trigger: ['input', 'blur'] },
                type: { required: true, message: '请选择推送类型', trigger: ['change', 'blur'] }
            }
        }
    },
    computed: {
        columns() {
            return [
                { title: '名称', key: 'name', minWidth: 140 },
                { title: '类型', key: 'type', width: 140 },
                { title: '订阅事件', key: 'subscribe', minWidth: 140, render: (row) => row.subscribe || '全部' },
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
                    width: 180,
                    render: (row) => h('div', { class: 'op-actions' }, [
                        h('i', {
                            class: 'fa-solid fa-vial op-icon',
                            style: { color: 'var(--warn)' },
                            title: '测试推送',
                            onClick: () => this.test(row)
                        }),
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
    async mounted() {
        try {
            const types = await http.get('/push/types')
            this.typeOptions = (types || []).map((item) => ({ label: item.label, value: item.value }))
        } catch (error) {
            // 拦截器已提示
        }
        this.load()
    },
    methods: {
        emptyForm() {
            return { id: null, name: '', type: 'webhook', config: '', subscribe: '', enabled: true }
        },
        async load() {
            this.loading = true
            try {
                const [items, summary] = await Promise.all([
                    http.get('/push/channels'),
                    http.get('/push/summary')
                ])
                this.items = items || []
                this.summary = summary || this.summary
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
                        await http.put(`/push/channels/${payload.id}`, payload)
                    } else {
                        await http.post('/push/channels', payload)
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
            await http.post(`/push/channels/${row.id}/toggle?enabled=${!row.enabled}`)
            this.$message.success(row.enabled ? '已停用' : '已启用')
            this.load()
        },
        async test(row) {
            const result = await http.post(`/push/channels/${row.id}/test`)
            this.$dialog.info({
                title: '测试推送',
                content: result.message || '测试请求已发送'
            })
        },
        remove(row) {
            this.$dialog.warning({
                title: '删除渠道',
                content: `确认删除「${row.name}」？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await http.delete(`/push/channels/${row.id}`)
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
