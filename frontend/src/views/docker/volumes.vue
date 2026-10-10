<template>
    <div class="docker-volumes">
        <div class="glass-card main-card">
            <div class="header">
                <h2>
                    <i class="fa-solid fa-folder"></i>
                    Docker 卷
                    <span class="sub">共 {{ volumes.length }} 个</span>
                </h2>
                <div class="actions">
                    <n-button size="small" quaternary :loading="loading" @click="loadVolumes">
                        <template #icon><i class="fa-solid fa-rotate"></i></template>
                        刷新
                    </n-button>
                    <n-button size="small" type="primary" @click="openCreate">
                        <template #icon><i class="fa-solid fa-plus"></i></template>
                        创建卷
                    </n-button>
                </div>
            </div>

            <n-data-table :columns="columns" :data="volumes" :loading="loading" :bordered="true" size="small"
                :row-key="(row) => row.Name" :scroll-x="1000" />
        </div>

        <n-modal v-model:show="createModal" preset="card" title="创建数据卷" style="width: 460px" :mask-closable="false">
            <n-form :model="form" label-placement="left" label-width="80">
                <n-form-item label="名称">
                    <n-input v-model:value="form.name" placeholder="如：huluwa-data" />
                </n-form-item>
                <n-form-item label="驱动">
                    <n-input v-model:value="form.driver" placeholder="local" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="createModal = false">取消</n-button>
                    <n-button type="primary" :loading="submitting" @click="submit">创建</n-button>
                </div>
            </template>
        </n-modal>
    </div>
</template>

<script>
import { h } from 'vue'
import { listVolumes, createVolume, removeVolume } from '@/api/docker'

export default {
    name: 'DockerVolumes',
    data() {
        return {
            loading: false,
            submitting: false,
            createModal: false,
            volumes: [],
            form: { name: '', driver: 'local' }
        }
    },
    computed: {
        columns() {
            return [
                { title: '名称', key: 'Name', minWidth: 180, className: 'mono' },
                { title: '驱动', key: 'Driver', width: 110 },
                { title: '作用域', key: 'Scope', width: 100 },
                {
                    title: '创建时间', key: 'CreatedAt', width: 180, className: 'mono',
                    render: row => h('span', row.CreatedAt && !Number.isNaN(Date.parse(row.CreatedAt))
                        ? new Date(row.CreatedAt).toLocaleString() : '-')
                },
                { title: '挂载点', key: 'Mountpoint', minWidth: 260, className: 'mono', ellipsis: { tooltip: true } },
                {
                    title: '操作',
                    key: 'actions',
                    width: 90,
                    render: (row) => h('i', {
                        class: 'fa-solid fa-trash op-icon',
                        style: { color: 'var(--danger)' },
                        title: '删除',
                        onClick: () => this.remove(row)
                    })
                }
            ]
        }
    },
    mounted() {
        this.loadVolumes()
    },
    activated() {
        this.loadVolumes()
    },
    methods: {
        async loadVolumes() {
            this.loading = true
            try {
                const data = await listVolumes()
                this.volumes = (data && data.Volumes) || []
            } catch (error) {
                this.volumes = []
            } finally {
                this.loading = false
            }
        },
        openCreate() {
            this.form = { name: '', driver: 'local' }
            this.createModal = true
        },
        async submit() {
            if (!this.form.name) {
                this.$message.warning('请填写卷名称')
                return
            }
            this.submitting = true
            try {
                await createVolume(this.form.name, this.form.driver)
                this.$message.success('卷创建成功')
                this.createModal = false
                this.loadVolumes()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.submitting = false
            }
        },
        remove(row) {
            this.$dialog.warning({
                title: '删除数据卷',
                content: `确定要删除卷 ${row.Name} 吗？该操作不可恢复。`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await removeVolume(row.Name)
                    this.$message.success('卷删除成功')
                    this.loadVolumes()
                }
            })
        }
    }
}
</script>

<style scoped>
.docker-volumes {
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
    margin-bottom: 16px;
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

.tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}
</style>
