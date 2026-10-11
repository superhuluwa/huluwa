<template>
    <div class="message-page" :class="{ narrow, 'show-room': narrowShowRoom }">
        <ConversationList :sessions="sessions" :current-key="currentKey" @select="switchSession" />

        <section class="chat-room">
            <ChatHeader :session-key="currentKey" :title="title" :subtitle="subtitle" :show-back="narrow"
                :clearable="canSend" :markable="isSystem" @refresh="refresh" @clear="onClear"
                @back="backToList" @open-session="switchSession" @mark-all="onMarkAll" />

            <MessageList :messages="currentMessages" :has-more="hasMore" :loading-earlier="loadingEarlier"
                :loading="loadingMessages" :session-key="currentKey" :empty-title="emptyTitle"
                :empty-tip="emptyTip"
                @load-earlier="loadEarlier" @open-cover="onOpenCover" @delete="onDeleteNotify" />

            <ChatComposer :disabled="!currentKey" :sending="sending" :kind="currentKind" :hint="composerHint"
                @send="onSend" />
        </section>

        <CoverViewer v-model:show="viewer.show" :url="viewer.url" :title="viewer.title" />
    </div>
</template>

<script>
/**
 * 消息中心（会话页）：左会话列表 + 右会话详情双栏；窄屏（<960px）折叠为单栏，用顶栏返回按钮切换。
 * UI 与交互整体参考 quantum-web 的会话页（view/chat + stores/chat）：
 * 会话列表、气泡流、时间分隔、贴底跟随、顶部消息检索、清空会话。
 *
 * 与量子端的差异（本项目无 WS、无任务体系）：
 *  - 实时性由 store 轮询提供（8s），发送时同步等待服务端搜索完成；
 *  - 内置「电影港」会话：用户发送的内容即搜索关键字，回复为「摘要 + 封面图 + 详情链接」气泡；
 *  - 「系统通知」为虚拟会话，读既有 message 表（消息中心原有数据），只读。
 */
import { mapActions, mapState } from 'pinia'
import { useChatStore } from '@/stores/chat'
import http from '@/libs/http'
import ConversationList from './components/ConversationList.vue'
import ChatHeader from './components/ChatHeader.vue'
import MessageList from './components/MessageList.vue'
import ChatComposer from './components/ChatComposer.vue'
import CoverViewer from './components/CoverViewer.vue'

/** 打开消息中心时默认进入的会话（电影港），无则回落到第一个会话 */
const PREFERRED_SESSION = 'movie'

export default {
    name: 'MessageCenter',
    components: {
        ConversationList,
        ChatHeader,
        MessageList,
        ChatComposer,
        CoverViewer
    },
    data() {
        return {
            viewer: { show: false, url: '', title: '' },
            narrow: false,
            narrowShowRoom: false
        }
    },
    computed: {
        ...mapState(useChatStore, [
            'sessions', 'currentKey', 'currentMessages', 'currentSession',
            'loadingMessages', 'loadingEarlier', 'sending'
        ]),
        ...mapState(useChatStore, ['hasMore']),
        title() {
            return this.currentSession?.title || '会话'
        },
        subtitle() {
            if (!this.currentSession) {
                return ''
            }
            const count = this.currentMessages.length
            const kindText = { movie: '影视搜索', system: '系统事件与运行通知' }[this.currentSession.kind] || '会话'
            return kindText + ' · ' + count + ' 条消息'
        },
        currentKind() {
            return this.currentSession?.kind || 'chat'
        },
        canSend() {
            return !!this.currentSession && this.currentSession.kind !== 'system'
        },
        isSystem() {
            return this.currentSession?.kind === 'system'
        },
        emptyTitle() {
            return this.currentKind === 'movie' ? '还没有搜索记录' : '暂无消息'
        },
        emptyTip() {
            return this.currentKind === 'movie'
                ? '输入片名开始搜索，例如「凡人修仙传」'
                : '系统事件与运行通知会显示在这里'
        },
        composerHint() {
            if (!this.currentSession || this.currentSession.kind === 'system') {
                // 只读会话的说明由输入框 placeholder 承载，避免重复提示
                return ''
            }
            return this.currentKind === 'movie'
                ? '输入片名回车即搜索，回复含封面与详情链接'
                : ''
        }
    },
    watch: {
        '$route.query.session'(next) {
            if (next && next !== this.currentKey) {
                this.switchSession(next)
                return
            }
            // 菜单点击当前已打开的会话时也要显示详情（窄屏两级视图）
            if (next) {
                this.narrowShowRoom = true
            }
        },
        currentKey(next, prev) {
            if (next === prev) {
                return
            }
            this.syncQuery(next)
            this.narrowShowRoom = true
        }
    },
    async mounted() {
        this.handleResize()
        window.addEventListener('resize', this.handleResize)
        const store = useChatStore()
        await store.refreshSessions()
        const target = this.$route.query.session || PREFERRED_SESSION
        const exists = store.sessions.some((item) => item.id === target)
        await store.openSession(exists ? target : (store.sessions[0]?.id || ''))
        this.syncQuery(store.currentKey)
        this.narrowShowRoom = !this.narrow
        store.startPolling()
    },
    activated() {
        const store = useChatStore()
        store.refreshSessions()
        store.startPolling()
        // 从左侧菜单的会话子项进入时 URL 带 ?session=<id>：以它为准切换会话，
        // 否则（页签返回等无 query 的场景）保持当前会话并把 query 补回地址栏。
        const target = this.$route.query.session
        if (target && target !== store.currentKey) {
            this.switchSession(target)
            return
        }
        this.syncQuery(store.currentKey)
    },
    deactivated() {
        // 页面被 keep-alive 缓存时停掉轮询，避免后台持续请求
        useChatStore().stopPolling()
    },
    beforeUnmount() {
        window.removeEventListener('resize', this.handleResize)
        useChatStore().stopPolling()
    },
    methods: {
        ...mapActions(useChatStore, ['openSession', 'refreshSessions', 'loadEarlier']),
        handleResize() {
            this.narrow = window.innerWidth < 960
        },
        /** 把当前会话同步进地址栏 query，便于刷新/分享定位（同一路由内 replace） */
        syncQuery(sessionId) {
            const current = this.$route.query.session || ''
            if (current !== sessionId) {
                this.$router.replace({ query: sessionId ? { session: sessionId } : {} }).catch(() => {})
            }
        },
        async switchSession(sessionId) {
            if (!sessionId) {
                return
            }
            if (sessionId === this.currentKey) {
                this.narrowShowRoom = true
                return
            }
            await this.openSession(sessionId)
            this.syncQuery(sessionId)
            this.narrowShowRoom = true
        },
        backToList() {
            this.narrowShowRoom = false
        },
        async refresh() {
            await this.refreshSessions()
            await useChatStore().reloadSession(this.currentKey)
        },
        async onSend(text) {
            try {
                await useChatStore().send(text)
            } catch (e) {
                // 失败文案由 axios 拦截器提示
            }
        },
        /** 系统通知「全部已读」：沿用消息中心既有接口 */
        async onMarkAll() {
            await http.post('/messages/read-all')
            this.$message.success('已全部标记为已读')
            await useChatStore().reloadSession(this.currentKey)
            await this.refreshSessions()
        },
        onClear() {
            useChatStore().clearSession(this.currentKey)
                .then(() => this.$message.success('会话已清空'))
                .catch(() => {})
        },
        onOpenCover({ url, title }) {
            this.viewer = { show: true, url, title }
        },
        /** 系统通知删除：沿用消息中心既有接口，删除后刷新当前会话 */
        onDeleteNotify(message) {
            this.$dialog.warning({
                title: '删除通知',
                content: '确认删除「' + ((message.contentText || '').trim() || '该通知') + '」？',
                positiveText: '删除',
                negativeText: '取消',
                onPositiveClick: async () => {
                    await http.delete('/messages/' + message.id)
                    this.$message.success('已删除')
                    await useChatStore().reloadSession(this.currentKey)
                }
            })
        }
    }
}
</script>

<style scoped>
.message-page {
    display: flex;
    height: 100%;
    min-height: 0;
    background: linear-gradient(180deg, var(--card) 0%, var(--card-2) 100%);
    border: 1px solid var(--line);
    border-radius: var(--radius);
    box-shadow: 0 8px 32px rgba(0, 0, 0, .40);
    overflow: hidden;
}

.chat-room {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    min-height: 0;
    background: var(--bg);
}

/* 窄屏（<960px）折叠为单栏：列表 ⇄ 详情，等价两端的两级页面体验 */
.message-page.narrow.show-room :deep(.conversation-list) {
    display: none;
}

.message-page.narrow:not(.show-room) .chat-room {
    display: none;
}
/* 移动端（<= 900px）：会话列表独占整屏，避免残留右栏空隙 */
@media (max-width: 900px) {
    .message-page {
        border-radius: 10px;
    }

    .message-page :deep(.conversation-list) {
        width: 100%;
        border-right: 0;
    }

    .chat-room :deep(.chat-header) {
        padding: 0 10px;
        gap: 8px;
    }
}
</style>