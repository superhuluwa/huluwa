<template>
    <div>
        <h3 class="page-title">
            <i class="fa-solid fa-bell"></i>
            消息中心
            <span class="sub">系统事件与运行通知</span>
        </h3>

        <div class="filter-card">
            <n-form inline :show-feedback="false">
                <n-form-item label="关键字">
                    <n-input v-model:value="query.keyword" clearable placeholder="标题 / 内容" style="width: 200px"
                        @keydown.enter="load" />
                </n-form-item>
                <n-form-item label="级别">
                    <n-select v-model:value="query.level" :options="levelOptions" clearable placeholder="全部"
                        style="width: 140px" />
                </n-form-item>
                <n-form-item label="状态">
                    <n-select v-model:value="query.read" :options="readOptions" clearable placeholder="全部"
                        style="width: 140px" />
                </n-form-item>
                <n-form-item>
                    <div class="filter-actions">
                        <n-button type="primary" :loading="loading" @click="load">
                            <template #icon><i class="fa-solid fa-magnifying-glass"></i></template>
                            查询
                        </n-button>
                        <n-button @click="reset">
                            <template #icon><i class="fa-solid fa-rotate-left"></i></template>
                            重置
                        </n-button>
                        <n-button quaternary type="primary" @click="markAll">
                            <template #icon><i class="fa-solid fa-check-double"></i></template>
                            全部已读
                        </n-button>
                    </div>
                </n-form-item>
            </n-form>
        </div>

        <div class="glass-card table-wrap">
            <n-data-table :columns="columns" :data="items" :loading="loading" :bordered="false" size="small"
                :row-key="(row) => row.id" />
        </div>
    </div>
</template>

<script>
import { h } from 'vue'
import http from '@/libs/http'

const LEVEL_META = {
    info: { text: '信息', type: 'info' },
    success: { text: '成功', type: 'success' },
    warning: { text: '警告', type: 'warning' },
    error: { text: '错误', type: 'error' }
}

export default {
    name: 'MessageCenter',
    data() {
        return {
            loading: false,
            items: [],
            query: {
                keyword: '',
                level: null,
                read: null
            },
            levelOptions: [
                { label: '信息', value: 'info' },
                { label: '成功', value: 'success' },
                { label: '警告', value: 'warning' },
                { label: '错误', value: 'error' }
            ],
            readOptions: [
                { label: '未读', value: 'false' },
                { label: '已读', value: 'true' }
            ]
        }
    },
    computed: {
        columns() {
            return [
                {
                    title: '级别',
                    key: 'level',
                    width: 90,
                    render: (row) => {
                        const meta = LEVEL_META[row.level] || LEVEL_META.info
                        return h('span', { class: ['pill', row.level === 'error' ? 'danger' : (row.level === 'warning' ? 'warn' : (row.level === 'success' ? 'ok' : 'off'))] }, [
                            h('span', { class: 'dot' }),
                            meta.text
                        ])
                    }
                },
                {
                    title: '状态',
                    key: 'read',
                    width: 80,
                    render: (row) => h('span', {
                        style: { color: row.read ? 'var(--text-3)' : 'var(--accent-2)', fontSize: '12px' }
                    }, row.read ? '已读' : '未读')
                },
                { title: '标题', key: 'title', minWidth: 180, ellipsis: { tooltip: true } },
                { title: '内容', key: 'content', minWidth: 240, ellipsis: { tooltip: true } },
                { title: '来源', key: 'source', width: 110 },
                { title: '时间', key: 'createdAt', width: 170 },
                {
                    title: '操作',
                    key: 'actions',
                    width: 130,
                    render: (row) => h('div', { class: 'op-actions' }, [
                        h('i', {
                            class: 'fa-solid fa-check op-icon',
                            style: { color: 'var(--ok)', display: row.read ? 'none' : 'inline-block' },
                            title: '标记已读',
                            onClick: () => this.markRead(row)
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
        async load() {
            this.loading = true
            try {
                const params = {
                    keyword: this.query.keyword || undefined,
                    level: this.query.level || undefined,
                    read: this.query.read === null ? undefined : this.query.read
                }
                const data = await http.get('/messages', params)
                this.items = data.items || []
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        reset() {
            this.query = { keyword: '', level: null, read: null }
            this.load()
        },
        async markRead(row) {
            if (row.read) return
            await http.post(`/messages/${row.id}/read`)
            row.read = true
            this.$message.success('已标记为已读')
        },
        async markAll() {
            await http.post('/messages/read-all')
            this.$message.success('已全部标记为已读')
            this.load()
        },
        remove(row) {
            this.$dialog.warning({
                title: '删除消息',
                content: `确认删除「${row.title}」？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await http.delete(`/messages/${row.id}`)
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
    padding: 8px 12px 12px;
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