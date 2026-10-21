<template>
    <div class="chat-page" :class="{ 'is-system': isSystem }">
        <!-- 顶栏：返回 / 会话图标 / 标题 + 连接状态 / 刷新 / 更多（对齐安卓 ChatScreen） -->
        <header class="chat-header">
            <button class="icon-btn" type="button" title="返回会话列表" @click="back">
                <i class="fa-solid fa-chevron-left"></i>
            </button>
            <span class="chat-icon">
                <SessionIcon :kind="currentKind" :fallback-class="fallbackIcon" fallback-text="Q" />
            </span>
            <div class="chat-title">
                <strong>{{ title }}</strong>
                <span>{{ chatStore.wsReady ? '实时消息已连接' : '消息自动轮询同步' }}</span>
            </div>
            <button class="icon-btn" type="button" title="刷新会话" @click="refresh">
                <i class="fa-solid fa-rotate"></i>
            </button>
            <div class="more-wrap">
                <button class="icon-btn" type="button" title="会话操作" @click="moreOpen = !moreOpen">
                    <i class="fa-solid fa-ellipsis-vertical"></i>
                </button>
                <div v-if="moreOpen" class="more-menu">
                    <button v-if="!isSystem" type="button" @click="openSettings">
                        <i class="fa-solid fa-sliders"></i>服务设置
                    </button>
                    <button v-if="!isSystem" type="button" class="danger" @click="confirmClear">
                        <i class="fa-solid fa-trash-can"></i>清空会话
                    </button>
                </div>
            </div>
        </header>

        <!-- 消息流 -->
        <MessageList ref="list" :messages="messages" :has-more="hasMore" :loading-earlier="loadingEarlier"
            :loading="loadingMessages" :session-key="currentKey" :empty-title="emptyTitle" :empty-tip="emptyTip"
            :typing="isTyping" @load-earlier="onLoadEarlier" @open-cover="onOpenCover"
            @delete="onDeleteNotify" @resend="onResend" />

        <!-- 输入区：系统通知只读，不渲染输入条（对齐安卓） -->
        <ChatComposer v-if="!isSystem" :disabled="!currentKey" :sending="sending" :kind="currentKind"
            :hint="composerHint" mobile @send="onSend" />

        <CoverViewer v-model:show="viewer.show" :url="viewer.url" :title="viewer.title" />

        <!-- 首次使用时的连接配置引导 -->
        <QbSettingsDialog v-model:show="qbDialog" intro="提交磁力前需要先配置 qBittorrent 的连接信息。"
            @saved="onQbSaved" />
        <VideoSettingsDialog v-model:show="videoDialog" intro="解析分享口令前需要先配置视频解析服务的连接信息。"
            @saved="onVideoSaved" />
        <XunleiSettingsDialog v-model:show="xunleiDialog" intro="提交下载任务前请配置飞牛地址和登录账号密码；原生迅雷自动使用默认下载目录。"
            @saved="onXunleiSaved" />
        <XunleiWinSettingsDialog v-model:show="xunleiWinDialog" intro="提交下载任务前请配置 Windows 机器上 xunlei-win-api 的服务地址。"
            @saved="onXunleiWinSaved" />
        <MovieSubscriptionDialog v-model:show="movieDialog" />
        <ServiceSettingsSheet v-model:show="settingsOpen" :kind="currentKind" />
    </div>
</template>

<script>
/**
 * 会话详情（与安卓 ChatScreen 对齐）：
 *  - 顶栏 38px 会话图标 + 标题 + 连接状态，右侧刷新 / 更多；
 *  - 消息流沿用 MessageList（时间分隔、贴底跟随、上翻分页、封面卡与通知卡）；
 *  - 底部输入条贴底，系统通知为只读会话（不渲染输入条）；
 *  - 返回键回到所属标签的会话列表（功能 / 系统）。
 *
 * 配置引导：机器人回复里声明「尚未配置」时自动弹出对应服务的设置表单，规则见 utils/configPrompt。
 */
import { mapActions, mapState } from 'pinia'
import { useChatStore } from '@/stores/chat'
import { useHlsSubscriptionsStore } from '@/stores/hlsSubscriptions'
import { isLocalSession, routeOfSession } from '@/utils/localSessions'
import { configPromptKind } from '@/utils/configPrompt'
import http from '@/libs/http'
import MessageList from '@/views/message/components/MessageList.vue'
import ChatComposer from '@/views/message/components/ChatComposer.vue'
import SessionIcon from '@/views/message/components/SessionIcon.vue'
import CoverViewer from '@/views/message/components/CoverViewer.vue'
import QbSettingsDialog from '@/views/message/components/QbSettingsDialog.vue'
import VideoSettingsDialog from '@/views/message/components/VideoSettingsDialog.vue'
import XunleiSettingsDialog from '@/views/message/components/XunleiSettingsDialog.vue'
import XunleiWinSettingsDialog from '@/views/message/components/XunleiWinSettingsDialog.vue'
import MovieSubscriptionDialog from '@/views/message/components/MovieSubscriptionDialog.vue'
import ServiceSettingsSheet from '@/views/message/components/ServiceSettingsSheet.vue'

export default {
    name: 'ChatPage',
    components: {
        MessageList,
        ChatComposer,
        SessionIcon,
        CoverViewer,
        QbSettingsDialog,
        VideoSettingsDialog,
        XunleiSettingsDialog,
        XunleiWinSettingsDialog,
        MovieSubscriptionDialog,
        ServiceSettingsSheet
    },
    setup() {
        return { chatStore: useChatStore(), hlsSubscriptions: useHlsSubscriptionsStore() }
    },
    data() {
        return {
            viewer: { show: false, url: '', title: '' },
            moreOpen: false,
            settingsOpen: false,
            qbDialog: false,
            videoDialog: false,
            xunleiDialog: false,
            xunleiWinDialog: false,
            movieDialog: false,
            prompted: { magnet: false, video: false, xunlei: false, 'xunlei-win': false },
            configPromptBaseline: 0,
            configPromptArmed: false
        }
    },
    computed: {
        ...mapState(useChatStore, [
            'sessions', 'currentKey', 'currentMessages', 'currentSession',
            'loadingMessages', 'loadingEarlier', 'sending', 'typing'
        ]),
        ...mapState(useChatStore, ['hasMore']),
        messages() {
            return this.currentMessages
        },
        title() {
            return (this.currentSession && this.currentSession.title) || '会话'
        },
        currentKind() {
            return (this.currentSession && this.currentSession.kind) || 'chat'
        },
        isSystem() {
            return this.currentKind === 'system'
        },
        isMagnet() {
            return this.currentKind === 'magnet'
        },
        isVideoSession() {
            return this.currentKind === 'video'
        },
        isXunleiSession() {
            return this.currentKind === 'xunlei'
        },
        isXunleiWinSession() {
            return this.currentKind === 'xunlei-win'
        },
        isTyping() {
            return !!this.typing[this.currentKey]
        },
        lastBotMessage() {
            const rows = this.currentMessages.filter((item) => item.role === 'bot')
            return rows.length ? rows[rows.length - 1] : null
        },
        fallbackIcon() {
            if (this.isSystem) {
                return 'fa-solid fa-bell'
            }
            return 'fa-regular fa-comments'
        },
        emptyTitle() {
            return {
                movie: '还没有搜索记录',
                dytt: '还没有搜索记录',
                magnet: '还没有 qBittorrent 下载记录',
                video: '还没有视频解析记录',
                xunlei: '还没有迅雷下载记录',
                'xunlei-win': '还没有迅雷【Win】下载记录',
                system: '暂无消息'
            }[this.currentKind] || '暂无消息'
        },
        emptyTip() {
            return {
                movie: '输入片名开始搜索，例如「凡人修仙传」',
                dytt: '输入片名开始搜索，例如「流浪地球」',
                magnet: '把磁力链接粘贴到下方输入框，发送即提交 qBittorrent 下载',
                video: '把抖音等 App 的分享口令整段粘贴到下方，发送即解析',
                xunlei: '把磁力链接粘贴到下方输入框，发送即提交迅雷下载',
                'xunlei-win': '把磁力链接粘贴到下方输入框，发送即提交迅雷【Win】下载',
                system: '系统事件与运行通知会显示在这里'
            }[this.currentKind] || '在下方输入内容开始对话'
        },
        composerHint() {
            // 手机端输入区保持紧凑：placeholder 已说明用法，不再堆提示行
            return ''
        }
    },
    watch: {
        lastBotMessage(next) {
            if (!next || !this.configPromptArmed) {
                return
            }
            const baseline = this.configPromptBaseline || 0
            const kind = configPromptKind(next, { baseline, armed: true })
            if (typeof next.id === 'number' && next.id > baseline) {
                this.configPromptBaseline = next.id
            }
            if (!kind || this.prompted[kind]) {
                return
            }
            this.prompted = { ...this.prompted, [kind]: true }
            if (kind === 'magnet' && this.isMagnet) {
                this.qbDialog = true
            } else if (kind === 'video' && this.isVideoSession) {
                this.videoDialog = true
            } else if (kind === 'xunlei' && this.isXunleiSession) {
                this.xunleiDialog = true
            } else if (kind === 'xunlei-win' && this.isXunleiWinSession) {
                this.xunleiWinDialog = true
            }
        },
        /** 地址栏切换会话（从别处打开同一条会话的另一条记录）时重新加载 */
        '$route.query.session'(next) {
            if (!next) {
                this.back()
                return
            }
            if (next !== this.currentKey) {
                this.openQuietly(next)
            }
        }
    },
    async mounted() {
        document.addEventListener('click', this.onDocumentClick)
        const requested = this.$route.query.session
        if (!requested) {
            this.$router.replace({ name: this.$route.meta?.tab === 'system' ? 'system' : 'functions' })
            return
        }
        // 消息接口失败（断网 / 服务重启）时保留会话外壳，由刷新按钮与错误提示兜底，
        // 不能让异常冒泡打断整页渲染。
        await this.openQuietly(requested).catch((error) => {
            this.$message?.error?.((error && error.message) || '会话加载失败，请点击刷新重试')
        })
    },
    activated() {
        this.chatStore.refreshSessions().catch(() => {})
        this.chatStore.startPolling()
        this.chatStore.initRealtime()
    },
    deactivated() {
        this.chatStore.stopPolling()
    },
    beforeUnmount() {
        document.removeEventListener('click', this.onDocumentClick)
        this.chatStore.stopPolling()
    },
    methods: {
        ...mapActions(useChatStore, ['openSession']),
        /** 打开会话并安静地完成历史加载：加载期间关闭配置引导，避免历史卡片触发弹窗 */
        async openQuietly(sessionId) {
            await this.chatStore.openSession(sessionId)
            // 本地会话（Frp / Docker / 构建 / 巡检 / 剧集）不走聊天详情，直接跳管理页
            const session = this.currentSession
            if (isLocalSession(session)) {
                this.$router.replace({ name: routeOfSession(session) })
                return
            }
            if (!this.chatStore.sessions.length) {
                await this.chatStore.refreshSessions().catch(() => {})
            }
            this.chatStore.initRealtime()
            this.chatStore.startPolling()
            this.markConfigPromptBaseline()
            if (session && session.kind === 'dytt') {
                this.hlsSubscriptions.ensureLoaded().catch(() => {})
            }
        },
        markConfigPromptBaseline() {
            this.configPromptArmed = true
            const last = this.lastBotMessage
            if (last && typeof last.id === 'number') {
                this.configPromptBaseline = Math.max(this.configPromptBaseline || 0, last.id)
            }
        },
        /** 返回所属标签的会话列表（功能 / 系统） */
        back() {
            this.$router.push({ name: this.$route.meta?.tab === 'system' ? 'system' : 'functions' })
        },
        async refresh() {
            this.configPromptArmed = false
            try {
                await this.chatStore.refreshSessions()
                await this.chatStore.reloadSession(this.currentKey)
                await this.hlsSubscriptions.refresh().catch(() => {})
            } finally {
                this.markConfigPromptBaseline()
            }
        },
        async onSend(text) {
            await this.chatStore.send(text)
        },
        async onResend(bubble) {
            await this.chatStore.resend(bubble)
        },
        /** 向上翻页加载更早历史（MessageList 滚动到顶部时触发） */
        async onLoadEarlier() {
            await this.chatStore.loadEarlier()
        },
        onOpenCover({ url, title }) {
            this.viewer = { show: true, url, title }
        },
        onDeleteNotify(message) {
            this.$dialog.warning({
                title: '删除通知',
                content: '确认删除「' + ((message.contentText || '').trim() || '该通知') + '」？',
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await http.delete('/messages/' + message.id)
                    this.$message.success('已删除')
                    await this.chatStore.reloadSession(this.currentKey)
                }
            })
        },
        confirmClear() {
            this.$dialog.warning({
                title: '清空会话',
                content: '将删除「' + this.title + '」的全部会话消息，且不可恢复。',
                positiveText: '清空',
                negativeText: '取消',
                onPositiveClick: () => {
                    this.chatStore.clearSession(this.currentKey)
                        .then(() => this.$message.success('会话已清空'))
                        .catch(() => {})
                }
            })
        },
        /** 「更多 → 服务设置」：按会话类型打开对应服务配置 */
        openSettings() {
            this.moreOpen = false
            this.settingsOpen = true
        },
        onQbSaved() {
            this.prompted = { ...this.prompted, magnet: true }
            this.qbDialog = false
        },
        onVideoSaved() {
            this.prompted = { ...this.prompted, video: true }
            this.videoDialog = false
        },
        onXunleiSaved() {
            this.prompted = { ...this.prompted, xunlei: true }
            this.xunleiDialog = false
        },
        onXunleiWinSaved() {
            this.prompted = { ...this.prompted, 'xunlei-win': true }
            this.xunleiWinDialog = false
        },
        onDocumentClick(event) {
            if (this.moreOpen && !event.target.closest?.('.more-wrap')) {
                this.moreOpen = false
            }
        }
    }
}
</script>

<style scoped>
.chat-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
    background: var(--paper);
}

/* ---------------- 会话顶栏 ---------------- */
.chat-header {
    flex: none;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: max(10px, env(safe-area-inset-top)) 8px 10px;
    padding-left: max(8px, env(safe-area-inset-left));
    padding-right: max(8px, env(safe-area-inset-right));
    background: #fff;
    border-bottom: 1px solid var(--line);
}

.icon-btn {
    flex: none;
    width: 38px;
    height: 38px;
    border: 0;
    border-radius: 12px;
    background: none;
    color: var(--muted);
    font-size: 15px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
}

.icon-btn:hover {
    background: var(--soft-green);
    color: var(--pine);
}

.chat-icon {
    flex: none;
    width: 38px;
    height: 38px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.chat-icon :deep(.session-icon) {
    width: 38px;
    height: 38px;
    border-radius: 11px;
    overflow: hidden;
    object-fit: contain;
}

.chat-title {
    flex: 1;
    min-width: 0;
    padding-left: 2px;
    display: flex;
    flex-direction: column;
    gap: 1px;
}

.chat-title strong {
    font-size: 15px;
    font-weight: 600;
    color: var(--ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.chat-title span {
    font-size: 11px;
    color: var(--muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.more-wrap {
    position: relative;
    flex: none;
}

.more-menu {
    position: absolute;
    top: 44px;
    right: 0;
    z-index: 30;
    min-width: 150px;
    padding: 6px;
    border-radius: 14px;
    background: #fff;
    border: 1px solid var(--line);
    box-shadow: 0 12px 32px rgba(36, 61, 50, .16);
    display: flex;
    flex-direction: column;
}

.more-menu button {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border: 0;
    border-radius: 10px;
    background: none;
    color: var(--ink);
    font-family: inherit;
    font-size: 13px;
    text-align: left;
    cursor: pointer;
}

.more-menu button:hover {
    background: var(--soft-green);
}

.more-menu button.danger {
    color: var(--danger);
}

.more-menu button.danger:hover {
    background: #FBEEF0;
}

/* 消息流占满中间区域 */
.chat-page :deep(.message-stream) {
    flex: 1;
    min-height: 0;
    padding: 18px 14px 8px;
}

/* 输入条：与消息流同宽同留白 */
.chat-page :deep(.composer) {
    flex: none;
    background: #fff;
    border-top: 1px solid var(--line);
    padding: 8px max(14px, env(safe-area-inset-right)) max(8px, env(safe-area-inset-bottom)) max(14px, env(safe-area-inset-left));
}

.chat-page :deep(.composer .composer-inner) {
    border-radius: 18px;
    background: var(--paper);
}

.chat-page :deep(.composer .composer-inner.focused) {
    border-color: var(--pine);
    box-shadow: 0 0 0 3px rgba(32, 108, 84, .12);
}

.chat-page :deep(.composer .send:not(:disabled)) {
    background: var(--pine);
    color: #fff;
    border-radius: 14px;
}

.chat-page :deep(.composer-tip) {
    color: var(--muted);
}
</style>
