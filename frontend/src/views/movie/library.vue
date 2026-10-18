<template>
    <div class="library-page">
        <section class="hero">
            <div class="hero-main">
                <div class="hero-icon"><i class="fa-solid fa-hard-drive"></i></div>
                <div>
                    <h2>本地剧集管理</h2>
                    <p>扫描共享下载目录，集中查看已下载的剧集并管理磁盘空间。</p>
                </div>
            </div>
            <div class="hero-actions">
                <n-button :loading="loading" @click="load">
                    <template #icon><i class="fa-solid fa-rotate"></i></template>
                    重新扫描
                </n-button>
            </div>
        </section>

        <div class="stat-grid library-stats">
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-film"></i> 已下载视频</div>
                <div class="stat-value">{{ entries.length }}</div>
                <div class="stat-foot">{{ seriesCount }} 部作品 / {{ seasonCount }} 个季度</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-database"></i> 占用空间</div>
                <div class="stat-value">{{ formatBytes(totalBytes) }}</div>
                <div class="stat-foot">本次扫描的普通视频文件</div>
            </div>
            <div class="stat-card">
                <div class="stat-label"><i class="fa-solid fa-folder-open"></i> 下载目录</div>
                <div class="stat-value directory-value" :title="scan?.mediaRoot || ''">{{ scan?.mediaRoot || '—' }}</div>
                <div class="stat-foot" :class="{ danger: scan && !scan.writable }">
                    {{ scan && !scan.writable ? '目录不可写，无法删除文件' : '目录可访问' }}
                </div>
            </div>
        </div>

        <n-alert v-if="scan?.warning" type="warning" :show-icon="true" class="scan-alert">
            {{ scan.warning }}
        </n-alert>

        <section class="library-card">
            <div class="toolbar">
                <div class="toolbar-main">
                    <n-input v-model:value="keyword" clearable placeholder="搜索剧名、文件名或路径">
                        <template #prefix><i class="fa-solid fa-magnifying-glass"></i></template>
                    </n-input>
                    <n-select v-model:value="seasonFilter" :options="seasonOptions" clearable placeholder="全部季度" />
                    <n-checkbox :checked="allSelected" :indeterminate="indeterminate" :disabled="!visibleEntries.length"
                        @update:checked="toggleAll">
                        全选当前结果
                    </n-checkbox>
                </div>
                <div class="toolbar-actions">
                    <span class="selection-text">已选 {{ selected.length }} 项 · {{ formatBytes(selectedBytes) }}</span>
                    <n-button type="error" :disabled="!selected.length" :loading="deleting" @click="confirmDelete">
                        <template #icon><i class="fa-solid fa-trash-can"></i></template>
                        批量删除
                    </n-button>
                </div>
            </div>

            <n-spin :show="loading">
                <div v-if="!entries.length && !loading" class="empty">
                    <i class="fa-regular fa-folder-open"></i>
                    <strong>下载目录里还没有可管理的剧集</strong>
                    <span>支持 MP4、MKV、AVI、TS、MOV 等常见视频格式；隐藏目录和临时下载目录会被跳过。</span>
                </div>
                <div v-else-if="!visibleEntries.length" class="empty compact">
                    <strong>没有匹配的剧集</strong>
                    <span>试试清空搜索词或季度筛选。</span>
                </div>
                <template v-else>
                    <!-- 手机端按剧集分组呈现，避免宽表格横向滚动 -->
                    <div v-if="isMobile" class="mobile-groups">
                        <article v-for="group in groupedEntries" :key="group.key" class="series-card">
                            <div class="series-head">
                                <n-checkbox :checked="groupChecked(group)" :indeterminate="groupIndeterminate(group)"
                                    @update:checked="checked => toggleGroup(group, checked)" />
                                <div class="series-title">
                                    <strong>{{ group.title }}</strong>
                                    <span>{{ group.entries.length }} 集 · {{ formatBytes(group.bytes) }}</span>
                                </div>
                            </div>
                            <div v-for="entry in group.entries" :key="entry.id" class="episode-row">
                                <n-checkbox :checked="selected.includes(entry.id)"
                                    @update:checked="checked => toggleEntry(entry.id, checked)" />
                                <div class="episode-main">
                                    <div class="episode-title">{{ episodeLabel(entry) }}</div>
                                    <div class="episode-file">{{ entry.fileName }}</div>
                                    <div class="episode-meta">{{ formatBytes(entry.size) }} · {{ entry.modifiedAt }}</div>
                                </div>
                                <n-button size="tiny" quaternary type="error" title="删除此集" @click.prevent="confirmDelete([entry])">
                                    <i class="fa-solid fa-trash-can"></i>
                                </n-button>
                            </div>
                        </article>
                    </div>
                    <n-data-table v-else :columns="columns" :data="visibleEntries" :row-key="row => row.id"
                        :bordered="false" :max-height="600" size="small" />
                </template>
            </n-spin>
        </section>
        <p v-if="scan?.scannedAt" class="scan-time">上次扫描：{{ scan.scannedAt }}</p>
    </div>
</template>

<script>
import { h } from 'vue'
import { NAlert, NButton, NCheckbox, NDataTable, NInput, NSelect, NSpin } from 'naive-ui'
import { useIsMobile } from '@/composables/useIsMobile'
import { DeleteEpisodeLibrary, ScanEpisodeLibrary } from '@/api/library'
import { formatBytes } from '@/utils/format'

export default {
    name: 'EpisodeLibrary',
    components: { NAlert, NButton, NCheckbox, NDataTable, NInput, NSelect, NSpin },
    setup() {
        return { isMobile: useIsMobile() }
    },
    data() {
        return {
            loading: false,
            deleting: false,
            scan: null,
            entries: [],
            selected: [],
            keyword: '',
            seasonFilter: null,
            columns: [
                {
                    title: '',
                    key: 'selected',
                    width: 42,
                    render: row => h(NCheckbox, {
                        checked: this.selected.includes(row.id),
                        onUpdateChecked: checked => this.toggleEntry(row.id, checked)
                    })
                },
                { title: '剧名', key: 'seriesTitle', minWidth: 180, ellipsis: { tooltip: true } },
                {
                    title: '季度 / 集数',
                    key: 'episode',
                    width: 118,
                    render: row => this.seasonEpisode(row)
                },
                { title: '文件', key: 'fileName', minWidth: 260, ellipsis: { tooltip: true } },
                {
                    title: '大小',
                    key: 'size',
                    width: 112,
                    render: row => formatBytes(row.size)
                },
                { title: '下载时间', key: 'modifiedAt', width: 168 },
                {
                    title: '操作',
                    key: 'actions',
                    width: 82,
                    render: row => h(NButton, {
                        size: 'tiny', quaternary: true, type: 'error', title: '删除此集',
                        onClick: () => this.confirmDelete([row])
                    }, { icon: () => h('i', { class: 'fa-solid fa-trash-can' }) })
                }
            ]
        }
    },
    computed: {
        selectedBytes() {
            return this.entries.filter(entry => this.selected.includes(entry.id))
                .reduce((sum, entry) => sum + entry.size, 0)
        },
        visibleEntries() {
            const keyword = this.keyword.trim().toLowerCase()
            return this.entries.filter(entry => {
                if (this.seasonFilter != null && entry.season !== this.seasonFilter) return false
                if (!keyword) return true
                return [entry.seriesTitle, entry.fileName, entry.relativePath]
                    .some(value => String(value || '').toLowerCase().includes(keyword))
            })
        },
        seasonOptions() {
            return [...new Set(this.entries.map(entry => entry.season))]
                .sort((a, b) => a - b)
                .map(season => ({ label: `第 ${season} 季`, value: season }))
        },
        seriesCount() {
            return new Set(this.entries.map(entry => entry.seriesTitle)).size
        },
        seasonCount() {
            return new Set(this.entries.map(entry => `${entry.seriesTitle}|${entry.season}`)).size
        },
        totalBytes() {
            return Number(this.scan?.totalBytes || 0)
        },
        allSelected() {
            return this.visibleEntries.length > 0 && this.visibleEntries.every(entry => this.selected.includes(entry.id))
        },
        indeterminate() {
            const count = this.visibleEntries.filter(entry => this.selected.includes(entry.id)).length
            return count > 0 && count < this.visibleEntries.length
        },
        groupedEntries() {
            const groups = new Map()
            for (const entry of this.visibleEntries) {
                const key = `${entry.seriesTitle}|${entry.season}`
                if (!groups.has(key)) {
                    groups.set(key, { key, title: entry.seriesTitle, season: entry.season, entries: [], bytes: 0 })
                }
                const group = groups.get(key)
                group.entries.push(entry)
                group.bytes += entry.size
            }
            return [...groups.values()]
        }
    },
    watch: {
        visibleEntries() {
            // 过滤条件变化后只保留仍可见的选中项，避免误删被隐藏文件。
            const visible = new Set(this.visibleEntries.map(entry => entry.id))
            this.selected = this.selected.filter(id => visible.has(id))
        }
    },
    mounted() {
        this.load()
    },
    methods: {
        formatBytes,
        async load() {
            this.loading = true
            try {
                const result = await ScanEpisodeLibrary()
                this.scan = result
                this.entries = Array.isArray(result?.entries) ? result.entries : []
                this.selected = []
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        seasonEpisode(row) {
            return `第 ${row.season} 季 · ${row.episode ? `第 ${row.episode} 集` : '集数待识别'}`
        },
        episodeLabel(entry) {
            return entry.episode ? `第 ${entry.episode} 集` : '集数待识别'
        },
        toggleEntry(id, checked) {
            if (checked) {
                if (!this.selected.includes(id)) this.selected = [...this.selected, id]
            } else {
                this.selected = this.selected.filter(value => value !== id)
            }
        },
        toggleAll(checked) {
            this.selected = checked ? this.visibleEntries.map(entry => entry.id) : []
        },
        groupChecked(group) {
            return group.entries.length > 0 && group.entries.every(entry => this.selected.includes(entry.id))
        },
        groupIndeterminate(group) {
            const count = group.entries.filter(entry => this.selected.includes(entry.id)).length
            return count > 0 && count < group.entries.length
        },
        toggleGroup(group, checked) {
            const ids = new Set(group.entries.map(entry => entry.id))
            if (checked) {
                this.selected = [...new Set([...this.selected, ...ids])]
            } else {
                this.selected = this.selected.filter(id => !ids.has(id))
            }
        },
        confirmDelete(rows) {
            const targets = rows ? rows.filter(row => row?.id) : this.entries.filter(entry => this.selected.includes(entry.id))
            if (!targets.length) return
            const bytes = targets.reduce((sum, entry) => sum + entry.size, 0)
            const preview = targets.slice(0, 6).map(entry => `${entry.seriesTitle} ${entry.episode ? `第 ${entry.episode} 集` : entry.fileName}`)
            const extra = targets.length > preview.length ? `等 ${targets.length} 个文件` : ''
            this.$dialog.warning({
                title: `删除 ${targets.length} 个剧集文件？`,
                content: `${preview.join('、')}${extra}\n将释放约 ${formatBytes(bytes)}，删除后无法恢复。`,
                positiveText: '确认删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await this.remove(targets.map(entry => entry.id), bytes)
                }
            })
        },
        async remove(ids, expectedBytes) {
            this.deleting = true
            try {
                const result = await DeleteEpisodeLibrary(ids)
                const freed = Number(result?.freedBytes ?? expectedBytes)
                const removed = new Set(result?.deletedIds || [])
                this.entries = this.entries.filter(entry => !removed.has(entry.id))
                this.selected = this.selected.filter(id => !removed.has(id))
                if (result?.failed) {
                    this.$message.warning(`${result.message}，释放 ${formatBytes(freed)}`)
                    if (result.failures?.length) {
                        this.$dialog.info({
                            title: '部分文件未删除',
                            content: result.failures.map(item => `${item.fileName || item.id}：${item.message}`).join('\n')
                        })
                    }
                } else {
                    this.$message.success(`已删除 ${result?.deleted || ids.length} 个文件，释放 ${formatBytes(freed)}`)
                }
                if (result?.failed) await this.load()
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.deleting = false
            }
        }
    }
}
</script>

<style scoped>
.library-page { display: flex; flex-direction: column; gap: 16px; }
.hero, .library-card {
    border: 1px solid var(--line);
    border-radius: 14px;
    background: linear-gradient(145deg, rgba(20, 28, 46, .92), rgba(13, 21, 38, .78));
    box-shadow: 0 10px 28px rgba(0, 0, 0, .2);
}
.hero { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 20px; }
.hero-main { display: flex; align-items: center; gap: 14px; min-width: 0; }
.hero-icon {
    display: grid; place-items: center; width: 44px; height: 44px; flex: none;
    border-radius: 12px; color: var(--accent-2); background: var(--accent-weak); font-size: 18px;
}
.hero h2 { margin: 0; font-size: 18px; color: var(--text); }
.hero p { margin: 5px 0 0; color: var(--text-3); font-size: 12.5px; }
.hero-actions { flex: none; }
.library-stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.library-stats .stat-value { font-size: 22px; }
.directory-value { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 16px !important; }
.stat-foot.danger { color: var(--danger); }
.scan-alert { margin: 0; }
.library-card { padding: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.toolbar-main { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.toolbar-main .n-input { width: 270px; }
.toolbar-main .n-select { width: 140px; }
.toolbar-actions { display: flex; align-items: center; gap: 12px; flex: none; }
.selection-text { color: var(--text-3); font-size: 12px; white-space: nowrap; }
.empty { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: var(--text-3); text-align: center; }
.empty i { font-size: 28px; margin-bottom: 4px; }
.empty strong { color: var(--text-2); font-size: 14px; }
.empty span { max-width: 480px; font-size: 12px; }
.empty.compact { min-height: 120px; }
.mobile-groups { display: flex; flex-direction: column; gap: 10px; }
.series-card { border: 1px solid var(--line); border-radius: 10px; overflow: hidden; background: rgba(20, 28, 46, .45); }
.series-head { display: flex; align-items: center; gap: 10px; padding: 12px; background: rgba(24, 34, 56, .75); }
.series-title { display: flex; flex-direction: column; min-width: 0; gap: 3px; }
.series-title strong { color: var(--text); font-size: 14px; overflow-wrap: anywhere; }
.series-title span { color: var(--text-3); font-size: 11px; }
.episode-row { display: flex; align-items: flex-start; gap: 10px; padding: 10px 12px; border-top: 1px solid var(--line); }
.episode-main { flex: 1; min-width: 0; }
.episode-title { color: var(--text); font-size: 13px; font-weight: 600; }
.episode-file, .episode-meta { margin-top: 3px; color: var(--text-3); font-size: 11.5px; overflow-wrap: anywhere; }
.scan-time { margin: -4px 2px 0; color: var(--text-3); font-size: 11px; }
@media (max-width: 900px) {
    .hero { align-items: flex-start; padding: 14px; }
    .hero-icon { width: 38px; height: 38px; }
    .hero h2 { font-size: 16px; }
    .hero p { line-height: 1.5; }
    .hero-actions .n-button { padding: 0 12px; }
    .library-stats { grid-template-columns: 1fr; }
    .library-stats .stat-card { padding: 14px; }
    .library-stats .stat-value { font-size: 19px; }
    .library-card { padding: 12px; }
    .toolbar { flex-direction: column; align-items: stretch; }
    .toolbar-main { flex-wrap: wrap; }
    .toolbar-main .n-input { flex: 1 1 100%; width: auto; }
    .toolbar-main .n-select { flex: 1 1 120px; width: auto; }
    .toolbar-actions { justify-content: space-between; }
}
</style>
