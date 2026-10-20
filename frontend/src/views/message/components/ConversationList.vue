<template>
    <aside class="conversation-list" :class="{ mobile }">
        <div class="list-head">
            <n-input v-model:value="keyword" :size="mobile ? 'medium' : 'small'" clearable
                placeholder="搜索会话或消息内容">
                <template #prefix>
                    <i class="fa-solid fa-magnifying-glass"></i>
                </template>
            </n-input>
        </div>
        <div class="list-body">
            <div v-if="!filtered.length" class="list-empty">
                {{ keyword ? '没有匹配的会话' : '暂无会话' }}
            </div>
            <div v-for="item in filtered" :key="item.id" class="conversation"
                :class="{ active: item.id === currentKey && !mobile }" @click="$emit('select', item.id)">
                <div class="conv-icon" :class="iconClass(item)">
                    <SessionIcon :kind="item.kind" :fallback-class="iconOf(item)" />
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
import { NInput } from 'naive-ui'
import SessionIcon from './SessionIcon.vue'
/**
 * 会话列表：整行平铺，以文字区域下方的细线分隔，不使用圆角卡片、边框和独立底色。
 * 44px 图标、标题、预览、时间、未读角标（>99 显示 99+），顶部筛选框按标题/预览本地过滤。
 * 各内置会话使用对应服务的官方品牌图标（SessionIcon；见 assets/brand-icons/README.md），
 * 无品牌图标（系统通知等）退回 Font Awesome。
 * 会话由服务端提供（内置电影港 / 磁力下载 / 视频解析 / 迅雷下载【飞牛】/ 迅雷下载【Win】+ 系统通知虚拟会话），列表不做删除。
 *
 * 手机端（mobile）：标题与状态栏安全区由公共顶栏处理，列表行高与触控目标放大，
 * 点击整行进入聊天详情，不保留当前会话的选中态。
 */
import { sessionPreview, formatTimeLabel } from '@/utils/chatFormat'

export default {
    components: { NInput, SessionIcon },
    name: 'ConversationList',
    props: {
        sessions: {
            type: Array,
            default: () => []
        },
        currentKey: {
            type: String,
            default: ''
        },
        /** 手机端形态：全屏一级页面，放大行高与触控目标 */
        mobile: {
            type: Boolean,
            default: false
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
            if (item.kind === 'dytt') {
                return 'fa-solid fa-circle-play'
            }
            if (item.kind === 'magnet') {
                return 'fa-solid fa-magnet'
            }
            if (item.kind === 'video') {
                return 'fa-solid fa-clapperboard'
            }
            if (item.kind === 'xunlei') {
                return 'fa-solid fa-bolt'
            }
            if (item.kind === 'xunlei-win') {
                return 'fa-solid fa-bolt'
            }
            return 'fa-regular fa-comments'
        },
        iconClass(item) {
            return {
                movie: item.kind === 'movie',
                dytt: item.kind === 'dytt',
                magnet: item.kind === 'magnet',
                video: item.kind === 'video',
                xunlei: item.kind === 'xunlei',
                'xunlei-win': item.kind === 'xunlei-win',
                system: item.kind === 'system'
            }
        }
    }
}
</script>

<style scoped>
.conversation-list {
    --conversation-gutter-left: 14px;
    --conversation-gutter-right: 14px;
    --conversation-icon-size: 44px;
    --conversation-gap: 12px;
    width: 292px;
    flex: none;
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--line);
    min-height: 0;
}

.list-head {
    flex: none;
    padding: 14px var(--conversation-gutter-right) 10px var(--conversation-gutter-left);
}

.list-body {
    flex: 1;
    overflow-y: auto;
    padding: 0 0 10px;
    -webkit-overflow-scrolling: touch;
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
    gap: var(--conversation-gap);
    padding: 12px var(--conversation-gutter-right) 12px var(--conversation-gutter-left);
    cursor: pointer;
    transition: opacity .15s ease;
}

.conversation:not(:last-child)::after {
    content: '';
    position: absolute;
    left: calc(var(--conversation-gutter-left) + var(--conversation-icon-size) + var(--conversation-gap));
    right: 0;
    bottom: 0;
    height: 1px;
    background: var(--line);
    pointer-events: none;
}

.conversation:hover .conv-title,
.conversation.active .conv-title {
    color: var(--accent);
}

.conv-icon {
    width: var(--conversation-icon-size);
    height: var(--conversation-icon-size);
    flex: none;
    /* 品牌图标是方形应用图标，圆角方形比圆形更贴合官方样式；系统通知仍用圆形图标字体底 */
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 17px;
    background: transparent;
    color: var(--accent);
    overflow: hidden;
}

.conv-icon.system {
    border-radius: 50%;
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
    transition: color .15s ease;
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
    align-self: stretch;
    justify-content: space-between;
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

/* ==========================================================================
   手机端：会话列表 = 一级全屏页面
   ========================================================================== */
.conversation-list.mobile {
    /* 状态栏安全区由公共顶栏处理，内容只保留左右安全区 */
    --conversation-gutter-left: max(16px, env(safe-area-inset-left));
    --conversation-gutter-right: max(16px, env(safe-area-inset-right));
    --conversation-icon-size: 52px;
    --conversation-gap: 14px;
    width: 100%;
    border-right: 0;
}

.conversation-list.mobile .list-body {
    padding-bottom: calc(env(safe-area-inset-bottom) + 12px);
}

.conversation-list.mobile .conversation:active {
    opacity: .7;
}

.conversation-list.mobile .conv-icon {
    border-radius: 14px;
    font-size: 20px;
}

.conversation-list.mobile .conv-title {
    font-size: 16px;
}

.conversation-list.mobile .conv-preview {
    margin-top: 3px;
    font-size: 13px;
}

.conversation-list.mobile .conv-time {
    font-size: 11.5px;
}

.conversation-list.mobile .conv-unread {
    min-width: 20px;
    height: 20px;
    border-radius: 10px;
    line-height: 20px;
    font-size: 11.5px;
}
</style>
