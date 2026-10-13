<template>
    <header class="chat-header" :class="{ compact }">
        <!-- 手机端：从聊天页返回会话列表（一级页面）；控制台菜单则收进右侧「更多」 -->
        <button v-if="showBack" class="back" type="button" title="返回会话列表" @click="$emit('back')">
            <i class="fa-solid fa-chevron-left"></i>
        </button>
        <div class="head-main">
            <div class="head-title">{{ title }}</div>
            <div v-if="metaText" class="head-meta">{{ metaText }}</div>
        </div>
        <div class="head-actions">
            <!-- 弹层从搜索按钮右侧对齐，宽度需为后面的操作按钮与页面留白预留空间 -->
            <n-popover v-model:show="searching" trigger="click" placement="bottom-end"
                :style="{ width: compact ? 'min(420px, calc(100vw - 96px))' : 'min(420px, calc(100vw - 176px))', boxSizing: 'border-box' }">
                <template #trigger>
                    <button class="icon-btn" type="button" title="搜索消息">
                        <i class="fa-solid fa-magnifying-glass"></i>
                    </button>
                </template>
                <div class="search-panel">
                    <n-input v-model:value="keyword" size="small" placeholder="按关键字检索全部会话消息（回车搜索）"
                        :loading="searchLoading" clearable @keyup.enter="doSearch" />
                    <div class="search-results">
                        <div v-if="searchDone && !results.length" class="search-empty">没有匹配的消息</div>
                        <div v-for="item in results" :key="item.sessionId + '-' + item.messageId" class="search-item"
                            @click="openResult(item)">
                            <div class="search-item-head">
                                <span class="search-session">{{ item.sessionTitle }}</span>
                                <span class="search-time">{{ item.createdAt }}</span>
                            </div>
                            <div class="search-text">{{ preview(item) }}</div>
                        </div>
                    </div>
                </div>
            </n-popover>
            <!-- 桌面端：操作按钮常显 -->
            <template v-if="!compact">
                <button v-if="sessionKey === 'movie'" class="icon-btn" type="button" title="电影港默认下载设置"
                    @click="$emit('movie-settings')">
                    <i class="fa-solid fa-sliders"></i>
                </button>
                <button v-if="magnet" class="icon-btn" type="button" title="qBittorrent 设置（仅磁力下载会话）"
                    @click="$emit('qb-settings')">
                    <i class="fa-solid fa-magnet"></i>
                </button>
                <button v-if="video" class="icon-btn" type="button" title="视频解析设置（仅视频解析会话）"
                    @click="$emit('video-settings')">
                    <i class="fa-solid fa-clapperboard"></i>
                </button>
                <button v-if="xunlei" class="icon-btn" type="button" title="迅雷设置（仅迅雷下载会话）"
                    @click="$emit('xunlei-settings')">
                    <i class="fa-solid fa-bolt"></i>
                </button>
                <button v-if="markable" class="icon-btn" type="button" title="全部标记已读" @click="$emit('mark-all')">
                    <i class="fa-solid fa-check-double"></i>
                </button>
                <button class="icon-btn" type="button" title="刷新会话" @click="$emit('refresh')">
                    <i class="fa-solid fa-rotate"></i>
                </button>
                <button v-if="clearable" class="icon-btn danger" type="button" title="清空会话消息"
                    @click="confirmClear">
                    <i class="fa-solid fa-trash-can"></i>
                </button>
            </template>

            <!-- 手机端：会话操作与控制台菜单入口都收进「更多」，顶栏只留返回 / 标题 / 搜索 -->
            <n-dropdown v-if="compact" trigger="click" :options="moreOptions" placement="bottom-end"
                @select="onMenuSelect">
                <button class="icon-btn" type="button" title="更多">
                    <i class="fa-solid fa-ellipsis-vertical"></i>
                </button>
            </n-dropdown>
        </div>
    </header>
</template>

<script>
/**
 * 会话顶栏（对齐量子端 ChatHeader 的布局）：标题、副标题（消息数/会话类型）、
 * 跨会话消息搜索、刷新、清空会话。系统通知虚拟会话不支持清空（清除入口在消息中心）。
 * 会话专属设置入口：磁力下载 → qBittorrent 设置，视频解析 → 视频解析服务设置，迅雷下载 → 迅雷设置。
 */
import { h } from 'vue'
import { SearchMessages } from '@/api/chat'
import { stripMarkup } from '@/utils/chatFormat'

export default {
    name: 'ChatHeader',
    props: {
        sessionKey: {
            type: String,
            default: ''
        },
        title: {
            type: String,
            default: ''
        },
        subtitle: {
            type: String,
            default: ''
        },
        showBack: {
            type: Boolean,
            default: false
        },
        /** 手机端紧凑排版：副标题可省略（由标题 + 消息流承担信息） */
        compact: {
            type: Boolean,
            default: false
        },
        clearable: {
            type: Boolean,
            default: false
        },
        /** 系统通知会话支持「全部已读」（聊天会话打开即已读，无需按钮） */
        markable: {
            type: Boolean,
            default: false
        },
        /** 手机端：显示「更多」下拉，内含控制台菜单入口 */
        showMenu: {
            type: Boolean,
            default: false
        },
        /** 当前是否为「磁力下载」会话：仅该会话提供 qBittorrent 设置入口 */
        magnet: {
            type: Boolean,
            default: false
        },
        /** 当前是否为「视频解析」会话：仅该会话提供解析服务设置入口 */
        video: {
            type: Boolean,
            default: false
        },
        /** 当前是否为「迅雷下载」会话：仅该会话提供迅雷设置入口 */
        xunlei: {
            type: Boolean,
            default: false
        }
    },
    emits: ['refresh', 'clear', 'back', 'open-session', 'mark-all', 'menu', 'qb-settings', 'video-settings', 'xunlei-settings', 'movie-settings'],
    data() {
        return {
            searching: false,
            keyword: '',
            results: [],
            searchLoading: false,
            searchDone: false
        }
    },
    computed: {
        /** 手机端只保留标题（列表里已能看到会话类型与消息数，顶栏越紧凑越好） */
        metaText() {
            return this.compact ? '' : this.subtitle
        },
        /** 手机端「更多」：会话操作 + 进入控制台其它模块（全屏页没有主框架侧栏） */
        moreOptions() {
            const options = []
            if (this.sessionKey === 'movie') {
                options.push({ label: '电影港默认下载设置', key: 'movie-settings', icon: () => h('i', { class: 'fa-solid fa-sliders' }) })
            }
            if (this.markable) {
                options.push({ label: '全部标记已读', key: 'mark-all', icon: () => h('i', { class: 'fa-solid fa-check-double' }) })
            }
            options.push({ label: '刷新会话', key: 'refresh', icon: () => h('i', { class: 'fa-solid fa-rotate' }) })
            if (this.clearable) {
                options.push({ label: '清空会话消息', key: 'clear', icon: () => h('i', { class: 'fa-solid fa-trash-can' }) })
            }
            // qBittorrent 设置只属于「磁力下载」会话，不放进其它会话的更多菜单
            if (this.magnet) {
                options.push({ type: 'divider', key: 'd0' })
                options.push({ label: 'qBittorrent 设置', key: 'qb-settings', icon: () => h('i', { class: 'fa-solid fa-magnet' }) })
            }
            // 视频解析设置同理，只属于「视频解析」会话
            if (this.video) {
                options.push({ type: 'divider', key: 'dv' })
                options.push({ label: '视频解析设置', key: 'video-settings', icon: () => h('i', { class: 'fa-solid fa-clapperboard' }) })
            }
            // 迅雷设置同理，只属于「迅雷下载」会话
            if (this.xunlei) {
                options.push({ type: 'divider', key: 'dx' })
                options.push({ label: '迅雷设置', key: 'xunlei-settings', icon: () => h('i', { class: 'fa-solid fa-bolt' }) })
            }
            if (this.showMenu) {
                options.push({ type: 'divider', key: 'd1' })
                options.push({ label: '控制台菜单', key: 'shell', icon: () => h('i', { class: 'fa-solid fa-bars' }) })
            }
            return options
        }
    },
    methods: {
        confirmClear() {
            this.$dialog.warning({
                title: '清空会话',
                content: '将删除「' + this.title + '」的全部会话消息，且不可恢复。',
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: () => this.$emit('clear')
            })
        },
        async doSearch() {
            const keyword = this.keyword.trim()
            if (!keyword) {
                this.results = []
                this.searchDone = false
                return
            }
            this.searchLoading = true
            try {
                const res = await SearchMessages(keyword, 20)
                this.results = Array.isArray(res) ? res : []
                this.searchDone = true
            } finally {
                this.searchLoading = false
            }
        },
        preview(item) {
            const text = stripMarkup(item.contentText || item.content || '')
            return text.length > 80 ? text.slice(0, 80) + '…' : text
        },
        openResult(item) {
            this.searching = false
            this.$emit('open-session', item.sessionId)
        },
        onMenuSelect(key) {
            if (key === 'shell') {
                this.$emit('menu')
                return
            }
            if (key === 'refresh') {
                this.$emit('refresh')
                return
            }
            if (key === 'clear') {
                this.confirmClear()
                return
            }
            if (key === 'mark-all') {
                this.$emit('mark-all')
                return
            }
            if (key === 'qb-settings') {
                this.$emit('qb-settings')
                return
            }
            if (key === 'video-settings') {
                this.$emit('video-settings')
                return
            }
            if (key === 'movie-settings') {
                this.$emit('movie-settings')
                return
            }
            if (key === 'xunlei-settings') {
                this.$emit('xunlei-settings')
            }
        }
    }
}
</script>

<style scoped>
.chat-header {
    height: 60px;
    flex: none;
    display: flex;
    align-items: center;
    gap: 12px;
    /* 左右留白与消息流 / 输入框共用同一组变量，标题与头像列上下对齐 */
    padding: 0 var(--chat-gutter-right, 16px) 0 var(--chat-gutter-left, 16px);
    border-bottom: 1px solid var(--line);
    background: rgba(11, 15, 25, .92);
    backdrop-filter: blur(10px);
}

.back {
    width: 34px;
    height: 34px;
    /* 负外边距让图标视觉上与下方头像列左边缘对齐（图标本身有内边距） */
    margin-left: -7px;
    flex: none;
    border: 0;
    border-radius: 50%;
    background: none;
    color: var(--text);
    cursor: pointer;
    font-size: 16px;
}

.back:hover {
    background: rgba(148, 163, 184, .1);
}

.head-main {
    min-width: 0;
}

.head-title {
    font-size: 15px;
    font-weight: 700;
    color: var(--text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.head-meta {
    font-size: 11.5px;
    color: var(--text-3);
    margin-top: 1px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.head-actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 6px;
}

.icon-btn {
    width: 32px;
    height: 32px;
    border: 0;
    border-radius: 8px;
    background: none;
    color: var(--text-2);
    cursor: pointer;
    font-size: 13px;
}

.icon-btn:hover {
    background: rgba(148, 163, 184, .10);
    color: var(--text);
}

.icon-btn.danger:hover {
    background: rgba(244, 63, 94, .14);
    color: var(--danger);
}

.search-panel {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.search-results {
    max-height: 340px;
    overflow-y: auto;
}

.search-empty {
    padding: 14px 0;
    text-align: center;
    color: var(--text-3);
    font-size: 12.5px;
}

.search-item {
    padding: 8px 6px;
    border-radius: 8px;
    cursor: pointer;
}

.search-item:hover {
    background: rgba(148, 163, 184, .10);
}

.search-item-head {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 11.5px;
    color: var(--text-3);
}

.search-time {
    margin-left: auto;
}

.search-text {
    margin-top: 2px;
    font-size: 13px;
    color: var(--text);
    word-break: break-word;
}

/* 手机端：顶栏增高到 58px（状态栏安全区由公共控制台顶栏处理），
   标题字号放大到聊天软件常用尺寸，操作按钮保持 34px 触控目标 */
@media (max-width: 900px) {
    .chat-header.compact {
        height: 58px;
        gap: 4px;
    }

    .chat-header.compact .head-title {
        font-size: 16px;
    }

    .chat-header.compact .back {
        width: 38px;
        height: 38px;
        font-size: 17px;
    }

    .chat-header.compact .icon-btn {
        width: 34px;
        height: 34px;
    }
}
</style>
