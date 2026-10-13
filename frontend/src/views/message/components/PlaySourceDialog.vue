<template>
    <n-modal v-model:show="visible" preset="card" style="width: 860px; max-width: calc(100vw - 32px)"
        :mask-closable="false">
        <template #header>
            <div class="ps-head">
                <i class="fa-solid fa-circle-play"></i>
                <span class="ps-title">{{ title || '播放地址' }}</span>
                <span v-if="total" class="ps-count">可复制 {{ total }} 条</span>
            </div>
        </template>

        <div class="ps-sub">
            <span class="ps-tip">
                <i class="fa-solid fa-circle-info"></i>
                点击「复制」拿到 m3u8 直链，粘贴到播放器即可观看
            </span>
            <span v-if="sources.length > 1" class="ps-tip">
                <i class="fa-solid fa-shuffle"></i>
                共 {{ sources.length }} 条线路，可切换
            </span>
        </div>

        <div v-if="sources.length > 1" class="ps-tabs">
            <button v-for="(s, i) in sources" :key="s.name + i" type="button" class="ps-tab"
                :class="{ active: i === activeIndex }" @click="activeIndex = i">
                <i class="fa-solid fa-tv"></i>{{ s.name }}
                <span class="ps-tab-num">{{ (s.items || []).length }}<template v-if="s.total > (s.items || []).length">/{{ s.total }}</template></span>
            </button>
        </div>

        <div v-if="!current.items.length" class="ps-empty">该资源没有解析到播放地址</div>
        <div v-else class="ps-list">
            <div v-for="(item, index) in current.items" :key="index" class="ps-row">
                <span class="ps-ep">{{ item.title }}</span>
                <span class="ps-url" :title="item.url">{{ item.url }}</span>
                <n-button size="tiny" type="primary" @click="copy(item.url)">
                    <template #icon><i class="fa-regular fa-copy"></i></template>
                    复制
                </n-button>
            </div>
            <div v-if="current.truncated" class="ps-more">
                该线路剧集较多，仅展示前 {{ current.items.length }} 条；完整列表见详情页
            </div>
        </div>

        <template #footer>
            <div class="ps-footer">
                <span class="ps-foot-tip">电影天堂资源为在线播放，不参与订阅下载</span>
                <n-button size="small" @click="visible = false">关闭</n-button>
            </div>
        </template>
    </n-modal>
</template>

<script>
import { NButton, NModal } from 'naive-ui'

/**
 * 播放地址弹窗（电影天堂搜索结果卡片的「播放地址」按钮）：
 *  - 数据来自卡片载荷里的 playSources（MacCMS 采集接口的 vod_play_from / vod_play_url）；
 *  - 多线路（dytt / dyttm3u8 等）用标签页切换，默认第一条；
 *  - 每行「复制」把 m3u8 直链写入剪贴板，配合本地播放器使用；
 *  - 电影天堂只有在线资源（无磁力），因此不接入订阅下载链路。
 */
export default {
    components: { NButton, NModal },
    name: 'PlaySourceDialog',
    props: {
        show: {
            type: Boolean,
            default: false
        },
        title: {
            type: String,
            default: ''
        },
        /** 卡片载荷里的播放源：[{ name, total, items: [{ title, url }], truncated }] */
        sources: {
            type: Array,
            default: () => []
        }
    },
    emits: ['update:show'],
    data() {
        return {
            activeIndex: 0
        }
    },
    computed: {
        visible: {
            get() {
                return this.show
            },
            set(value) {
                this.$emit('update:show', value)
            }
        },
        current() {
            return this.sources[this.activeIndex] || { items: [] }
        },
        total() {
            return this.sources.reduce((sum, s) => sum + (s.items ? s.items.length : 0), 0)
        }
    },
    watch: {
        show(next) {
            // 每次打开都回到第一条线路
            if (next) {
                this.activeIndex = 0
            }
        }
    },
    methods: {
        async copy(url) {
            if (!url) {
                return
            }
            try {
                await navigator.clipboard.writeText(url)
                this.$message?.success('播放地址已复制')
            } catch (e) {
                // 剪贴板不可用（非 HTTPS / 无权限）时退化为手动选择
                const input = document.createElement('textarea')
                input.value = url
                document.body.appendChild(input)
                input.select()
                document.execCommand('copy')
                document.body.removeChild(input)
                this.$message?.success('播放地址已复制')
            }
        }
    }
}
</script>

<style scoped>
.ps-head {
    display: flex;
    align-items: center;
    gap: 8px;
}

.ps-head i {
    color: var(--accent);
}

.ps-title {
    font-size: 15px;
    font-weight: 600;
}

.ps-count {
    font-size: 12px;
    color: var(--text-3);
}

.ps-sub {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    margin-bottom: 12px;
    font-size: 12px;
    color: var(--text-3);
}

.ps-tip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.ps-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}

.ps-tab {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 5px 12px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: rgba(148, 163, 184, .06);
    color: var(--text-2);
    font-size: 12.5px;
    cursor: pointer;
}

.ps-tab:hover {
    border-color: var(--accent);
    color: var(--text);
}

.ps-tab.active {
    border-color: var(--accent);
    background: rgba(96, 165, 250, .14);
    color: var(--accent);
    font-weight: 600;
}

.ps-tab-num {
    padding: 0 6px;
    border-radius: 999px;
    background: rgba(148, 163, 184, .16);
    font-size: 11px;
}

.ps-empty {
    padding: 28px 0;
    text-align: center;
    color: var(--text-3);
    font-size: 13px;
}

.ps-list {
    max-height: 420px;
    overflow: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.ps-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: rgba(148, 163, 184, .04);
}

.ps-ep {
    flex: none;
    min-width: 64px;
    font-size: 12px;
    color: var(--text-2);
}

.ps-url {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: Consolas, "Segoe UI", monospace;
    font-size: 12px;
    color: var(--text-3);
}

.ps-more {
    padding: 6px 2px;
    font-size: 12px;
    color: var(--text-3);
}

.ps-footer {
    display: flex;
    align-items: center;
    gap: 8px;
}

.ps-foot-tip {
    flex: 1;
    font-size: 12px;
    color: var(--text-3);
}
</style>