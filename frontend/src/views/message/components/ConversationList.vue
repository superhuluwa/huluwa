<template>
    <aside class="conversation-list">
        <div class="list-head">
            <div class="list-title">
                <h3>会话</h3>
                <span class="count">{{ sessions.length }} 个</span>
            </div>
            <n-input v-model:value="keyword" size="small" clearable placeholder="筛选会话 / 消息内容">
                <template #prefix>
                    <i class="fa-solid fa-magnifying-glass"></i>
                </template>
            </n-input>
        </div>
        <div class="list-body">
            <div v-if="!filtered.length" class="list-empty">暂无会话</div>
            <div v-for="item in filtered" :key="item.id" class="conversation"
                :class="{ active: item.id === currentKey }" @click="$emit('select', item.id)">
                <div class="conv-icon" :class="iconClass(item)">
                    <i :class="iconOf(item)"></i>
                </div>
                <div class="conv-body">
                    <div class="conv-title">{{ item.title }}</div>
                    <div class="conv-preview">{{ previewOf(item) }}</div>
                </div>
                <div class="conv-side">
                    <span class="conv-time">{{ timeOf(item) }}</span>
                    <span v-if="item.unread > 0" class="conv-unread">{{ item.unread > 99 ? '99+' : item.unread }}</span>
                </div>
            </div>
        </div>
    </aside>
</template>

<script>
/**
 * 会话列表（对齐 quantum ConversationList 的视觉）：
 * 44px 圆图标、标题、预览、时间、未读角标（>99 显示 99+），顶部筛选框按标题/预览本地过滤。
 * 会话由服务端提供（内置电影港 + 系统通知虚拟会话），列表不做删除。
 */
import { sessionPreview, formatTimeLabel } from '@/utils/chatFormat'

export default {
    name: 'ConversationList',
    props: {
        sessions: {
            type: Array,
            default: () => []
        },
        currentKey: {
            type: String,
            default: ''
        }
    },
    emits: ['select'],
    data() {
        return {
            keyword: ''
        }
    },
    computed: {
        filtered() {
            const keyword = this.keyword.trim().toLowerCase()
            if (!keyword) {
                return this.sessions
            }
            return this.sessions.filter((item) => {
                const text = (item.title + ' ' + this.previewOf(item)).toLowerCase()
                return text.includes(keyword)
            })
        }
    },
    methods: {
        previewOf(item) {
            return sessionPreview(item.last) || '（暂无内容）'
        },
        /** 列表时间走相对友好的格式（当天 HH:mm / 昨天 / 同年 MM-DD），避免宽串挤压标题 */
        timeOf(item) {
            return formatTimeLabel((item.last && item.last.createdAt) || '')
        },
        iconOf(item) {
            if (item.kind === 'system') {
                return 'fa-solid fa-bell'
            }
            if (item.kind === 'movie') {
                return 'fa-solid fa-film'
            }
            return 'fa-regular fa-comments'
        },
        iconClass(item) {
            return { movie: item.kind === 'movie', system: item.kind === 'system' }
        }
    }
}
</script>

<style scoped>
.conversation-list {
    width: 292px;
    flex: none;
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--line);
    background: rgba(20, 28, 46, .45);
    min-height: 0;
}

.list-head {
    flex: none;
    padding: 14px 14px 10px;
}

.list-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
}

.list-title h3 {
    font-size: 16px;
    font-weight: 700;
    margin: 0;
    color: var(--text);
}

.count {
    margin-left: auto;
    font-size: 11.5px;
    color: var(--text-2);
}

.list-body {
    flex: 1;
    overflow-y: auto;
    padding: 2px 10px 10px;
}

.list-empty {
    text-align: center;
    color: var(--text-3);
    font-size: 12.5px;
    padding: 24px 0;
}

.conversation {
    position: relative;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px;
    border-radius: 12px;
    cursor: pointer;
    border: 1px solid transparent;
}

.conversation:hover {
    background: rgba(148, 163, 184, .07);
}

.conversation.active {
    background: var(--accent-weak);
    border-color: rgba(14, 165, 233, .28);
}

.conv-icon {
    width: 44px;
    height: 44px;
    flex: none;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 17px;
    background: rgba(14, 165, 233, .15);
    color: var(--accent);
}

.conv-icon.movie {
    background: rgba(99, 102, 241, .16);
    color: #A5B4FC;
}

.conv-icon.system {
    background: rgba(16, 185, 129, .14);
    color: var(--ok);
}

.conv-body {
    flex: 1;
    min-width: 0;
}

.conv-title {
    font-size: 14px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--text);
}

.conv-preview {
    margin-top: 2px;
    font-size: 12px;
    color: var(--text-2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.conv-side {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    flex: none;
}

.conv-time {
    font-size: 11px;
    color: var(--text-2);
    white-space: nowrap;
}

.conv-unread {
    min-width: 18px;
    height: 18px;
    padding: 0 6px;
    border-radius: 9px;
    background: var(--danger);
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    line-height: 18px;
    text-align: center;
}
</style>
