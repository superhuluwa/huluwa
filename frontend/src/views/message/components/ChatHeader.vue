<template>
    <header class="chat-header">
        <button v-if="showBack" class="back" type="button" title="返回会话列表" @click="$emit('back')">
            <i class="fa-solid fa-arrow-left"></i>
        </button>
        <div class="head-main">
            <div class="head-title">{{ title }}</div>
            <div class="head-meta">{{ subtitle }}</div>
        </div>
        <div class="head-actions">
            <n-popover v-model:show="searching" trigger="click" placement="bottom-end" style="width: 420px">
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
            <button v-if="markable" class="icon-btn" type="button" title="全部标记已读" @click="$emit('mark-all')">
                <i class="fa-solid fa-check-double"></i>
            </button>
            <button class="icon-btn" type="button" title="刷新会话" @click="$emit('refresh')">
                <i class="fa-solid fa-rotate"></i>
            </button>
            <button v-if="clearable" class="icon-btn danger" type="button" title="清空会话消息" @click="confirmClear">
                <i class="fa-solid fa-trash-can"></i>
            </button>
        </div>
    </header>
</template>

<script>
/**
 * 会话顶栏（对齐量子端 ChatHeader 的布局）：标题、副标题（消息数/会话类型）、
 * 跨会话消息搜索、刷新、清空会话。系统通知虚拟会话不支持清空（清除入口在消息中心）。
 */
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
        clearable: {
            type: Boolean,
            default: false
        },
        /** 系统通知会话支持「全部已读」（聊天会话打开即已读，无需按钮） */
        markable: {
            type: Boolean,
            default: false
        }
    },
    emits: ['refresh', 'clear', 'back', 'open-session', 'mark-all'],
    data() {
        return {
            searching: false,
            keyword: '',
            results: [],
            searchLoading: false,
            searchDone: false
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
    padding: 0 16px;
    border-bottom: 1px solid var(--line);
}

.back {
    width: 30px;
    height: 30px;
    flex: none;
    border: 0;
    border-radius: 8px;
    background: none;
    color: var(--text-2);
    cursor: pointer;
    font-size: 14px;
}

.back:hover {
    background: rgba(148, 163, 184, .1);
    color: var(--text);
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
</style>
