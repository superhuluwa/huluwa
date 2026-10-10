<template>
    <div class="docker-images">
        <h3 class="page-title">
            <i class="fa-regular fa-images"></i>
            Docker 镜像
            <span class="sub">拉取 / 删除 / 查看</span>
        </h3>

        <div class="glass-card table-card">
            <div class="toolbar">
                <span class="toolbar-title">共 {{ images.length }} 个镜像</span>
                <div class="toolbar-actions">
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

            <n-data-table :columns="columns" :data="images" :loading="loading" :bordered="false" size="small"
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
import { h } from 'vue'
import { listImages, pullImage, removeImage } from '@/api/docker'

export default {
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
    mounted() {
        this.loadImages()
    },
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
                    await removeImage(row.Id, false)
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
    padding: 2px;
}

.table-card {
    padding: 16px 18px;
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

.tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
}

.op-icon {
    font-size: 14px;
    cursor: pointer;
    opacity: .85;
}

.op-icon:hover {
    opacity: 1;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}
</style>
