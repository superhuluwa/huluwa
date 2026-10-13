<template>
    <div class="docker-networks">
        <div class="glass-card main-card">
            <div class="header">
                <h2>
                    <i class="fa-solid fa-circle-nodes"></i>
                    Docker 网络
                    <span class="sub">共 {{ networks.length }} 个</span>
                </h2>
                <div class="actions">
                    <n-button size="small" quaternary :loading="loading" @click="loadNetworks">
                        <template #icon><i class="fa-solid fa-rotate"></i></template>
                        刷新
                    </n-button>
                    <n-button size="small" type="primary" @click="openCreate">
                        <template #icon><i class="fa-solid fa-plus"></i></template>
                        创建网络
                    </n-button>
                </div>
            </div>

            <n-data-table :columns="columns" :data="networks" :loading="loading" :bordered="true" size="small"
                :row-key="(row) => row.Id" :scroll-x="960" />
        </div>

        <n-modal v-model:show="createModal" preset="card" title="创建网络" style="width: 460px" :mask-closable="false">
            <n-form :model="form" label-placement="left" label-width="80">
                <n-form-item label="名称">
                    <n-input v-model:value="form.name" placeholder="如：huluwa-net" />
                </n-form-item>
                <n-form-item label="驱动">
                    <n-input v-model:value="form.driver" placeholder="bridge" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="createModal = false">取消</n-button>
                    <n-button type="primary" :loading="submitting" @click="submit">创建</n-button>
                </div>
            </template>
        </n-modal>

        <n-modal v-model:show="connectModal" preset="card" :title="'连接容器 - ' + (currentNetwork && currentNetwork.Name)"
            style="width: 520px" :mask-closable="false">
            <n-form label-placement="left" label-width="90">
                <n-form-item label="容器">
                    <n-select v-model:value="selectedContainer" filterable placeholder="选择要连接 / 断开的容器"
                        :options="containerOptions" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="connectModal = false">关闭</n-button>
                    <n-button type="warning" :loading="connecting" @click="doDisconnect">断开</n-button>
                    <n-button type="primary" :loading="connecting" @click="doConnect">连接</n-button>
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
    NSelect
} from 'naive-ui'
import { h } from 'vue'
import {
    listNetworks,
    createNetwork,
    removeNetwork,
    connectNetwork,
    disconnectNetwork,
    listContainers
} from '@/api/docker'

export default {
    components: { NButton, NDataTable, NForm, NFormItem, NInput, NModal, NSelect },
    name: 'DockerNetworks',
    data() {
        return {
            loading: false,
            submitting: false,
            connecting: false,
            createModal: false,
            connectModal: false,
            networks: [],
            containers: [],
            currentNetwork: null,
            selectedContainer: null,
            form: { name: '', driver: 'bridge' }
        }
    },
    computed: {
        containerOptions() {
            return this.containers.map((item) => {
                let name = item.Id ? item.Id.substring(0, 12) : '-'
                if (item.Names && item.Names.length) {
                    name = item.Names[0].startsWith('/') ? item.Names[0].substring(1) : item.Names[0]
                }
                return { label: `${name}（${(item.Image || '')}）`, value: item.Id }
            })
        },
        columns() {
            return [
                {
                    title: '网络 ID',
                    key: 'Id',
                    width: 160,
                    className: 'mono',
                    render: (row) => h('span', row.Id ? row.Id.substring(0, 12) : '-')
                },
                { title: '名称', key: 'Name', minWidth: 180 },
                { title: '驱动', key: 'Driver', width: 120 },
                { title: '作用域', key: 'Scope', width: 110 },
                {
                    title: '创建时间',
                    key: 'Created',
                    width: 170,
                    className: 'mono',
                    render: (row) => h('span', row.Created ? new Date(row.Created).toLocaleString() : '-')
                },
                {
                    title: '操作',
                    key: 'actions',
                    width: 140,
                    align: 'center',
                    render: (row) => h('div', { class: 'op-actions' }, [
                        h('i', {
                            class: 'fa-solid fa-plug op-icon',
                            style: { color: 'var(--accent)' },
                            title: '连接容器',
                            onClick: () => this.openConnect(row)
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
        this.loadNetworks()
    },
    activated() {
        this.loadNetworks()
    },
    methods: {
        async loadNetworks() {
            this.loading = true
            try {
                this.networks = (await listNetworks()) || []
            } catch (error) {
                this.networks = []
            } finally {
                this.loading = false
            }
        },
        openCreate() {
            this.form = { name: '', driver: 'bridge' }
            this.createModal = true
        },
        async submit() {
            if (!this.form.name) {
                this.$message.warning('请填写网络名称')
                return
            }
            this.submitting = true
            try {
                await createNetwork(this.form.name, this.form.driver)
                this.$message.success('网络创建成功')
                this.createModal = false
                this.loadNetworks()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.submitting = false
            }
        },
        async openConnect(row) {
            this.currentNetwork = row
            this.selectedContainer = null
            this.connectModal = true
            try {
                this.containers = (await listContainers(true)) || []
            } catch (error) {
                this.containers = []
            }
        },
        async doConnect() {
            if (!this.selectedContainer) {
                this.$message.warning('请选择容器')
                return
            }
            this.connecting = true
            try {
                await connectNetwork(this.currentNetwork.Id, this.selectedContainer)
                this.$message.success('容器已连接到网络')
                this.connectModal = false
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.connecting = false
            }
        },
        async doDisconnect() {
            if (!this.selectedContainer) {
                this.$message.warning('请选择容器')
                return
            }
            this.connecting = true
            try {
                await disconnectNetwork(this.currentNetwork.Id, this.selectedContainer)
                this.$message.success('容器已从网络断开')
                this.connectModal = false
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.connecting = false
            }
        },
        remove(row) {
            this.$dialog.warning({
                title: '删除网络',
                content: `确定要删除网络 ${row.Name} 吗？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await removeNetwork(row.Id)
                    this.$message.success('网络删除成功')
                    this.loadNetworks()
                }
            })
        }
    }
}
</script>

<style scoped>
.docker-networks {
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
/* 移动端：容器内边距收紧，头部操作区换行铺满，表格横向滚动查看（列结构不变） */
@media (max-width: 900px) {
    .docker-networks {
        padding: 0;
    }

    .main-card {
        padding: 14px 12px;
        border-radius: 10px;
    }

    .header {
        margin-bottom: 12px;
    }

    .header h2 {
        font-size: 15px;
    }

    .actions {
        width: 100%;
        gap: 6px;
    }

    .actions :deep(.n-input) {
        width: 100% !important;
    }

    .card-footer {
        gap: 6px;
    }
}
</style>
