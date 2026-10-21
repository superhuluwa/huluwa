<template>
    <ManagementShell kind="library" title="剧集管理" subtitle="扫描下载目录 · 管理磁盘空间" :loading="loading"
        :busy="deleting" @refresh="load">
        <div class="mgmt-content">
            <!-- 速览：已下载视频 / 占用空间 / 下载目录 -->
            <section class="stats-row">
                <div class="stat-block">
                    <span>已下载视频</span>
                    <strong>{{ entries.length }}</strong>
                    <em>{{ seriesCount }} 部作品 / {{ seasonCount }} 个季度</em>
                </div>
                <div class="stat-block">
                    <span>占用空间</span>
                    <strong>{{ formatBytes(totalBytes) }}</strong>
                    <em>本次扫描的普通视频文件</em>
                </div>
                <div class="stat-block wide">
                    <span>下载目录</span>
                    <strong class="path" :title="scan?.mediaRoot || ''">{{ scan?.mediaRoot || '—' }}</strong>
                    <em :class="{ danger: scan && !scan.writable }">
                        {{ scan && !scan.writable ? '目录不可写，无法删除文件' : '目录可访问' }}
                    </em>
                </div>
            </section>

            <p v-if="scan?.warning" class="notice-line"><i class="fa-solid fa-triangle-exclamation"></i>{{ scan.warning }}</p>

            <n-input v-model:value="keyword" clearable placeholder="搜索剧名、文件名或路径" size="large" class="search-box">
                <template #prefix><i class="fa-solid fa-magnifying-glass"></i></template>
            </n-input>
            <div class="season-row">
                <button type="button" class="season-chip" :class="{ active: seasonFilter === null }" @click="seasonFilter = null">全部季度</button>
                <button v-for="option in seasonOptions" :key="option.value" type="button" class="season-chip"
                    :class="{ active: seasonFilter === option.value }" @click="seasonFilter = option.value">
                    {{ option.label }}
                </button>
            </div>

            <div v-if="!entries.length && !loading" class="empty-panel">
                <span class="empty-icon"><i class="fa-regular fa-folder-open"></i></span>
                <strong>下载目录里还没有可管理的剧集</strong>
                <span>支持 MP4、MKV、AVI、TS 等常见格式；隐藏目录与临时目录会被跳过</span>
            </div>
            <div v-else-if="!groupedEntries.length" class="empty-panel">
                <span class="empty-icon"><i class="fa-solid fa-magnifying-glass"></i></span>
                <strong>没有匹配的剧集</strong>
                <span>试试清空搜索词或季度筛选</span>
            </div>

            <article v-for="group in groupedEntries" :key="group.key" class="panel series-card"
                :class="{ expanded: expandedGroups.includes(group.key) }">
                <div class="series-head">
                    <button class="expand-btn" type="button" :aria-expanded="expandedGroups.includes(group.key)"
                        @click="toggleExpanded(group)">
                        <i class="fa-solid fa-chevron-right"></i>
                    </button>
                    <div class="series-title" @click="toggleExpanded(group)">
                        <strong>{{ group.title }}</strong>
                        <span>{{ group.seasonLabel }} · {{ group.entries.length }} 集 · {{ formatBytes(group.bytes) }}</span>
                    </div>
                    <button class="link-btn danger" type="button" :disabled="deleting"
                        @click="confirmDelete(group.entries, group)">删除全部</button>
                </div>
                <div v-if="expandedGroups.includes(group.key)" class="episode-list">
                    <div v-for="entry in group.entries" :key="entry.id" class="episode-row">
                        <div class="episode-main">
                            <strong class="episode-title">{{ episodeLabel(entry) }}</strong>
                            <span class="episode-file">{{ entry.fileName }}</span>
                            <span class="episode-meta">{{ formatBytes(entry.size) }} · {{ entry.modifiedAt }}</span>
                        </div>
                        <button class="link-btn danger" type="button" :disabled="deleting"
                            @click="confirmDelete([entry])">删除</button>
                    </div>
                </div>
            </article>

            <p v-if="scan?.scannedAt" class="scan-time">上次扫描：{{ scan.scannedAt }}</p>
        </div>
    </ManagementShell>
</template>

<script>
/**
 * 剧集管理（系统会话）：扫描共享下载目录，按「剧名 + 季度」分组查看并批量删除。
 * 删除只提交本次扫描返回的短期 ID，后端会再校验文件类型与大小，避免误删。
 */
import { NInput } from 'naive-ui'
import ManagementShell from '@/components/main/management-shell.vue'
import { DeleteEpisodeLibrary, ScanEpisodeLibrary } from '@/api/library'
import { formatBytes } from '@/utils/format'

export default {
    name: 'EpisodeLibrary',
    components: { ManagementShell, NInput },
    data() {
        return {
            loading: false,
            deleting: false,
            scan: null,
            entries: [],
            expandedGroups: [],
            keyword: '',
            seasonFilter: null
        }
    },
    computed: {
        visibleEntries() {
            const keyword = this.keyword.trim().toLowerCase()
            return this.entries.filter((entry) => {
                if (this.seasonFilter != null && entry.season !== this.seasonFilter) return false
                if (!keyword) return true
                return [entry.seriesTitle, entry.fileName, entry.relativePath]
                    .some((value) => String(value || '').toLowerCase().includes(keyword))
            })
        },
        seasonOptions() {
            return [...new Set(this.entries.map((entry) => entry.season))]
                .sort((a, b) => a - b)
                .map((season) => ({ label: `第 ${season} 季`, value: season }))
        },
        seriesCount() {
            return new Set(this.entries.map((entry) => entry.seriesTitle)).size
        },
        seasonCount() {
            return new Set(this.entries.map((entry) => `${entry.seriesTitle}|${entry.season}`)).size
        },
        totalBytes() {
            return Number((this.scan && this.scan.totalBytes) || 0)
        },
        groupedEntries() {
            const groups = new Map()
            for (const entry of this.visibleEntries) {
                const key = `${entry.seriesTitle}|${entry.season}`
                if (!groups.has(key)) {
                    groups.set(key, {
                        key, title: entry.seriesTitle, season: entry.season,
                        seasonLabel: `第 ${entry.season} 季`, entries: [], bytes: 0
                    })
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
            // 过滤后不保留已不可见分组的展开态，重新显示时仍然默认折叠
            const visible = new Set(this.groupedEntries.map((group) => group.key))
            this.expandedGroups = this.expandedGroups.filter((key) => visible.has(key))
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
                this.entries = Array.isArray(result && result.entries) ? result.entries : []
                this.expandedGroups = []
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        episodeLabel(entry) {
            if (entry.episodeRecognized && entry.episode) return `第 ${entry.episode} 集`
            const name = String(entry.fileName || '').replace(/\.[^.]+$/, '')
            return name.length > 64 ? `${name.slice(0, 64)}…` : name || '集数待识别'
        },
        toggleExpanded(group) {
            if (this.expandedGroups.includes(group.key)) {
                this.expandedGroups = this.expandedGroups.filter((key) => key !== group.key)
            } else {
                this.expandedGroups = [...this.expandedGroups, group.key]
            }
        },
        confirmDelete(rows, group = null) {
            const targets = (rows || []).filter((row) => row && row.id)
            if (!targets.length) return
            const bytes = targets.reduce((sum, entry) => sum + entry.size, 0)
            const preview = targets.slice(0, 6).map((entry) => `${entry.seriesTitle} ${entry.episode ? `第 ${entry.episode} 集` : entry.fileName}`)
            const extra = targets.length > preview.length ? `等 ${targets.length} 个文件` : ''
            this.$dialog.warning({
                title: `删除 ${targets.length} 个剧集文件？`,
                content: `${preview.join('、')}${extra}\n将释放约 ${formatBytes(bytes)}，删除后无法恢复。`,
                positiveText: '确认删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await this.remove(targets.map((entry) => entry.id), bytes, group)
                }
            })
        },
        async remove(ids, expectedBytes, group = null) {
            this.deleting = true
            try {
                const result = await DeleteEpisodeLibrary(ids)
                const freed = Number((result && result.freedBytes) ?? expectedBytes)
                const removed = new Set((result && result.deletedIds) || [])
                this.entries = this.entries.filter((entry) => !removed.has(entry.id))
                if (group && result && result.deleted) {
                    this.expandedGroups = this.expandedGroups.filter((key) => key !== group.key)
                }
                if (result && result.failed) {
                    this.$message.warning(`${result.message}，释放 ${formatBytes(freed)}`)
                    if (result.failures && result.failures.length) {
                        this.$dialog.info({
                            title: '部分文件未删除',
                            content: result.failures.map((item) => `${item.fileName || item.id}：${item.message}`).join('\n')
                        })
                    }
                } else {
                    this.$message.success(`已删除 ${(result && result.deleted) || ids.length} 个文件，释放 ${formatBytes(freed)}`)
                }
                if (result && result.failed) await this.load()
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
.stats-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin-bottom: 12px;
}

.stat-block {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 12px;
    border-radius: 18px;
    border: 1px solid var(--line);
    background: #fff;
}

.stat-block.wide {
    grid-column: 1 / -1;
}

.stat-block span {
    font-size: 11px;
    color: var(--muted);
}

.stat-block strong {
    font-size: 21px;
    line-height: 27px;
    font-weight: 600;
    color: var(--ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.stat-block strong.path {
    font-size: 14px;
    font-weight: 500;
}

.stat-block em {
    font-style: normal;
    font-size: 10px;
    color: var(--muted);
    line-height: 1.4;
}

.stat-block em.danger {
    color: var(--danger);
}

.search-box {
    margin-bottom: 12px;
}

.season-row {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
    overflow-x: auto;
    scrollbar-width: none;
}

.season-row::-webkit-scrollbar {
    display: none;
}

.season-chip {
    flex: none;
    padding: 6px 13px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: #fff;
    color: var(--muted);
    font-family: inherit;
    font-size: 12px;
    cursor: pointer;
}

.season-chip.active {
    background: var(--soft-green);
    border-color: transparent;
    color: var(--pine);
    font-weight: 600;
}

.series-card {
    padding: 0;
    overflow: hidden;
}

.series-head {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 14px;
}

.expand-btn {
    flex: none;
    width: 28px;
    height: 28px;
    border: 0;
    border-radius: 8px;
    background: none;
    color: var(--muted);
    font-size: 11px;
    cursor: pointer;
    transition: transform .18s ease, background .18s ease;
}

.expand-btn:hover {
    background: var(--soft-green);
}

.series-card.expanded .expand-btn {
    transform: rotate(90deg);
}

.series-title {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
    cursor: pointer;
}

.series-title strong {
    font-size: 14px;
    font-weight: 600;
    color: var(--ink);
    overflow-wrap: anywhere;
}

.series-title span {
    font-size: 11px;
    color: var(--muted);
}

.episode-list {
    border-top: 1px solid var(--line);
}

.episode-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px 10px 50px;
    border-top: 1px solid var(--line);
}

.episode-row:first-child {
    border-top: 0;
}

.episode-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.episode-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--ink);
}

.episode-file,
.episode-meta {
    font-size: 11px;
    color: var(--muted);
    overflow-wrap: anywhere;
}

.scan-time {
    margin: 4px 2px 0;
    font-size: 11px;
    color: var(--muted);
}
</style>
