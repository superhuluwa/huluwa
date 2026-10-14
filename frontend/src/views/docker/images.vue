<template>
    <div class="docker-images">
        <div class="glass-card main-card">
            <div class="header">
                <h2>
                    <i class="fa-regular fa-images"></i>
                    Docker 镜像
                    <span class="sub">共 {{ images.length }} 个</span>
                </h2>
                <div class="actions">
                    <n-button size="small" quaternary :loading="loading" @click="loadImages">
                        <template #icon><i class="fa-solid fa-rotate"></i></template>
                        刷新
                    </n-button>
                    <n-button size="small" type="primary" @click="openPull">
                        <template #icon><i class="fa-solid fa-download"></i></template>
                        拉取镜像
                    </n-button>
                    <n-button size="small" type="error" :disabled="!checkedKeys.length" @click="removeSelected">
                        <template #icon><i class="fa-solid fa-trash-can"></i></template>
                        删除选中 ({{ checkedKeys.length }})
                    </n-button>
                </div>
            </div>

            <n-data-table :columns="columns" :data="images" :loading="loading" :bordered="true" size="small"
                :row-key="(row) => row.Id" :checked-row-keys="checkedKeys"
                @update:checked-row-keys="(keys) => checkedKeys = keys" :scroll-x="1000" />
        </div>

        <n-modal v-model:show="pullModal" preset="card" title="拉取镜像" style="width: 480px" :mask-closable="false">
            <n-form :model="pullForm" label-placement="left" label-width="80">
                <n-form-item label="镜像名">
                    <n-input v-model:value="pullForm.image" placeholder="如：nginx" />
                </n-form-item>
                <n-form-item label="标签">
                    <n-input v-model:value="pullForm.tag" placeholder="latest" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="pullModal = false">取消</n-button>
                    <n-button type="primary" :loading="pulling" @click="submitPull">拉取</n-button>
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
    NModal
} from 'naive-ui'
import { h } from 'vue'
import { listImages, pullImage, removeImage } from '@/api/docker'

export default {
    components: { NButton, NDataTable, NForm, NFormItem, NInput, NModal },
    name: 'DockerImages',
    data() {
        return {
            loading: false,
            pulling: false,
            pullModal: false,
            images: [],
            checkedKeys: [],
            pullForm: { image: '', tag: 'latest' }
        }
    },
    computed: {
        columns() {
            return [
                { type: 'selection' },
                {
                    title: '仓库',
                    key: 'RepoTags',
                    minWidth: 240,
                    render: (row) => {
                        const tags = row.RepoTags || []
                        if (!tags.length) {
                            return h('span', { class: 'pill off' }, '<none>')
                        }
                        return h('div', { class: 'tag-list' }, tags.map((tag) => h('span', { class: 'pill off' }, tag)))
                    }
                },
                {
                    title: '镜像 ID',
                    key: 'Id',
                    width: 160,
                    className: 'mono',
                    render: (row) => h('span', row.Id ? row.Id.substring(7, 19) : '-')
                },
                {
                    title: '容器数', key: 'Containers', width: 90,
                    render: row => h('span', Number(row.Containers) >= 0 ? row.Containers : '未知')
                },
                { title: '创建时间', key: 'Created', width: 160, className: 'mono' },
                {
                    title: '大小',
                    key: 'Size',
                    width: 110,
                    className: 'mono',
                    render: (row) => h('span', row.Size || '-')
                },
                {
                    title: '操作',
                    key: 'actions',
                    width: 80,
                    align: 'center',
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
    // KeepAlive 首次挂载也会调用 activated，统一在这里加载，避免双份请求。
    activated() {
        this.loadImages()
    },
    methods: {
        async loadImages() {
            this.loading = true
            try {
                this.images = (await listImages(false)) || []
                this.checkedKeys = []
            } catch (error) {
                this.images = []
            } finally {
                this.loading = false
            }
        },
        openPull() {
            this.pullForm = { image: '', tag: 'latest' }
            this.pullModal = true
        },
        async submitPull() {
            if (!this.pullForm.image) {
                this.$message.warning('请填写镜像名称')
                return
            }
            this.pulling = true
            try {
                await pullImage(this.pullForm.image.trim(), this.pullForm.tag || 'latest')
                this.$message.success('镜像拉取成功')
                this.pullModal = false
                this.loadImages()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.pulling = false
            }
        },
        remove(row) {
            const tag = (row.RepoTags && row.RepoTags[0]) || row.Id
            this.$dialog.warning({
                title: '删除镜像',
                content: `确定要删除镜像 ${tag} 吗？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    // 先常规删除（静默），若镜像被容器占用再升级为强制删除，避免误删正在使用的镜像。
                    await removeImage(row.Id, false, { silent: true })
                        .catch(() => removeImage(row.Id, true))
                    this.$message.success('镜像删除成功')
                    this.loadImages()
                }
            })
        },
        removeSelected() {
            const count = this.checkedKeys.length
            this.$dialog.warning({
                title: '批量删除镜像',
                content: `确定要删除选中的 ${count} 个镜像吗？`,
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    for (const id of this.checkedKeys) {
                        await removeImage(id, true).catch(() => {})
                    }
                    this.$message.success('镜像删除完成')
                    this.loadImages()
                }
            })
        }
    }
}
</script>

<style scoped>
.docker-images {
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
    .docker-images {
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
