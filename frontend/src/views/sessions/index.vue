<template>
    <div class="sessions-page">
        <p v-if="error" class="sessions-error">{{ error }}</p>

        <div v-if="!rows.length" class="sessions-empty">
            <i class="fa-regular fa-comments"></i>
            <strong>{{ loading ? '正在同步…' : (system ? '暂无系统会话' : '暂无功能会话') }}</strong>
            <span v-if="!loading">{{ system ? 'Frp / Docker / 构建管理会显示在这里' : '发送一条消息即可开始使用' }}</span>
        </div>

        <div v-else class="session-list">
            <button v-for="(item, index) in rows" :key="item.id" class="session-row" type="button"
                @click="open(item)">
                <span class="session-icon" :class="{ 'is-local': isLocalKind(item.kind) }">
                    <SessionIcon v-if="!isLocalKind(item.kind)" :kind="item.kind" :fallback-class="fallbackIcon(item)" fallback-text="Q" />
                    <i v-else :class="localIcon(item.kind)" class="local-glyph"></i>
                </span>
                <span class="session-body">
                    <span class="session-title">{{ item.title }}</span>
                    <span class="session-preview">{{ previewOf(item) }}</span>
                </span>
                <span class="session-side">
                    <span class="session-time">{{ timeOf(item) }}</span>
                    <span v-if="item.unread > 0" class="session-unread" :class="{ system }">
                        {{ item.unread > 99 ? '99+' : item.unread }}
                    </span>
                </span>
                <span v-if="index !== rows.length - 1" class="session-divider" aria-hidden="true"></span>
            </button>
        </div>
    </div>
</template>

<script>
/**
 * 会话列表（功能 / 系统两个标签共用，与安卓 SessionsScreen 一一对应）：
 *  - 44px 图标、标题 15px 半粗、预览 12px 单行截断、右侧时间与未读角标；
 *  - 整行平铺 + 左侧缩进的分隔线，不用圆角卡片；
 *  - 系统标签只展示本地服务会话（Frp / Docker / App 构建 / 巡检记录 / 剧集管理 / 我的订阅）；
 *    「在线升级」只在安卓 App 内出现，网页端不提供。
 */
import SessionIcon from '@/views/message/components/SessionIcon.vue'
import { useChatStore } from '@/stores/chat'
import { formatTimeLabel, stripMarkup } from '@/utils/chatFormat'
import { isLocalSession, LOCAL_KINDS, routeOfSession } from '@/utils/localSessions'

const LOCAL_ICONS = {
    frp: 'fa-solid fa-diagram-project',
    docker: 'fa-brands fa-docker',
    'android-build': 'fa-brands fa-android',
    'check-logs': 'fa-solid fa-list-check',
    library: 'fa-solid fa-hard-drive',
    subscriptions: 'fa-regular fa-bookmark'
}

export default {
    name: 'SessionsPage',
    components: { SessionIcon },
    setup() {
        return { chatStore: useChatStore() }
    },
    computed: {
        /** 系统标签：只显示本地服务会话；功能标签：显示其余聊天会话 */
        system() {
            return this.$route.name === 'system'
        },
        rows() {
            return (this.chatStore.sessions || []).filter((item) => isLocalSession(item) === this.system)
        },
        loading() {
            return this.chatStore.loadingSessions
        },
        error() {
            // 服务端会话加载失败时给出可重试提示；本地会话摘要失败已在各自行内体现
            return this.chatStore.sessionsError || ''
        }
    },
    watch: {
        /** 切到列表页时刷新一次摘要（管理会话里的操作会改变 Frp / Docker / 构建摘要） */
        '$route.name': {
            immediate: true,
            handler() {
                this.chatStore.refreshSessions().catch(() => {})
            }
        }
    },
    methods: {
        isLocalKind(kind) {
            return LOCAL_KINDS.has(kind)
        },
        localIcon(kind) {
            return LOCAL_ICONS[kind] || 'fa-solid fa-gear'
        },
        /** 会话列表预览：本地会话用聚合摘要，聊天会话用最后一条消息 */
        previewOf(item) {
            if (item.previewOverride) {
                return item.previewOverride
            }
            if (item.last) {
                return stripMarkup(item.last.contentText || item.last.content || '').slice(0, 160) || '发条消息，开始使用这个服务'
            }
            return '发条消息，开始使用这个服务'
        },
        timeOf(item) {
            if (isLocalSession(item)) {
                return ''
            }
            return formatTimeLabel((item.last && item.last.createdAt) || item.updatedAt || '')
        },
        fallbackIcon(item) {
            if (item.kind === 'system') {
                return 'fa-solid fa-bell'
            }
            if (isLocalSession(item)) {
                return this.localIcon(item.kind)
            }
            return 'fa-regular fa-comments'
        },
        /** 打开会话：本地会话跳对应管理页，聊天会话进入聊天详情 */
        open(item) {
            const name = routeOfSession(item)
            if (name === 'chat') {
                this.$router.push({ name: 'chat', query: { session: item.id } })
                return
            }
            this.$router.push({ name })
        }
    }
}
</script>

<style scoped>
.sessions-page {
    min-height: 100%;
    background: var(--sessions-bg);
}

.sessions-error {
    margin: 12px 22px;
    padding: 10px 12px;
    border-radius: 14px;
    background: #F8F0E4;
    color: var(--amber);
    font-size: 12.5px;
}

.sessions-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 64px 24px;
    color: var(--muted);
    text-align: center;
}

.sessions-empty i {
    font-size: 30px;
    color: var(--pine);
    opacity: .55;
}

.sessions-empty strong {
    font-size: 15px;
    color: var(--ink);
    font-weight: 600;
}

.sessions-empty span {
    font-size: 12.5px;
}

.session-row {
    position: relative;
    display: flex;
    align-items: center;
    gap: 13px;
    width: 100%;
    min-height: 66px;
    padding: 9px 21px;
    border: 0;
    background: none;
    text-align: left;
    font-family: inherit;
    cursor: pointer;
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
}

.session-row:active {
    background: rgba(32, 108, 84, .05);
}

.session-icon {
    position: relative;
    flex: none;
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    color: var(--pine);
}

/* 本地服务会话：44px 圆角浅底 + 主题色图标（对齐安卓管理会话图标） */
.session-icon.is-local {
    border-radius: 13px;
    background: var(--soft-green);
    color: var(--pine);
    font-size: 20px;
}

/* 品牌图标（SessionIcon 的 img）铺满 44px 圆角方块 */
.session-icon :deep(.session-icon) {
    width: 44px;
    height: 44px;
    border-radius: 13px;
    overflow: hidden;
    object-fit: contain;
}

.session-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.session-title {
    font-size: 15px;
    line-height: 22px;
    font-weight: 600;
    color: var(--sessions-ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.session-preview {
    font-size: 12px;
    line-height: 18px;
    color: var(--sessions-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.session-side {
    flex: none;
    min-width: 28px;
    height: 44px;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    justify-content: space-between;
}

.session-time {
    font-size: 10px;
    line-height: 14px;
    color: var(--sessions-muted);
    white-space: nowrap;
}

.session-unread {
    min-width: 17px;
    height: 17px;
    padding: 0 5px;
    border-radius: 999px;
    background: var(--pine);
    color: #fff;
    font-size: 9px;
    line-height: 17px;
    font-weight: 600;
    text-align: center;
    box-sizing: border-box;
}

.session-unread.system {
    background: var(--system-tone);
}

/* 分隔线：与标题左边缘对齐（21 + 44 + 13 = 78px），末端留 21px */
.session-divider {
    position: absolute;
    left: 78px;
    right: 21px;
    bottom: 0;
    height: 1px;
    background: #E9EEE7;
}
</style>
