<template>
    <!--
      会话中心：手机端是一个「聊天软件」式的两级全屏页面 ——
      一级 = 会话列表（默认落地），点击会话进入二级 = 聊天详情，顶栏返回键回到列表。
      桌面端（宽屏 / 非触屏）保持原有双栏布局，样式与交互不变。
    -->
    <div class="message-page"
        :class="{ narrow, 'show-room': narrowShowRoom, mobile: isMobile, desktop: !isMobile, chrome: !fullscreen }">
        <ConversationList v-if="showList" :sessions="sessions" :current-key="currentKey" :mobile="isMobile"
            :show-menu="isMobile" @select="switchSession" @menu="openShellDrawer" />

        <section v-if="showRoom" class="chat-room">
            <ChatHeader :session-key="currentKey" :title="title" :subtitle="subtitle" :show-back="narrow"
                :compact="isMobile" :clearable="canSend" :markable="isSystem" :show-menu="isMobile"
                @refresh="refresh" @clear="onClear" @back="backToList" @open-session="switchSession"
                @mark-all="onMarkAll" @menu="openShellDrawer" @qb-settings="openQbDialog" />

            <MessageList :messages="currentMessages" :has-more="hasMore" :loading-earlier="loadingEarlier"
                :loading="loadingMessages" :session-key="currentKey" :empty-title="emptyTitle"
                :empty-tip="emptyTip"
                @load-earlier="loadEarlier" @open-cover="onOpenCover" @delete="onDeleteNotify" />

            <ChatComposer :disabled="!currentKey" :sending="sending" :kind="currentKind" :hint="composerHint"
                :mobile="isMobile" @send="onSend" />
        </section>

        <CoverViewer v-model:show="viewer.show" :url="viewer.url" :title="viewer.title" />

        <!-- 首次在「磁力下载」会话提交磁力时，自动弹出 qBittorrent 配置表单 -->
        <QbSettingsDialog v-model:show="qbDialog" intro="提交磁力前需要先配置 qBittorrent 的连接信息。"
            @saved="onQbSaved" />
    </div>
</template>

<script>
/**
 * 消息中心（会话页）：左会话列表 + 右会话详情双栏；窄屏折叠为单栏，用顶栏返回按钮切换。
 * UI 与交互整体参考 quantum-web 的会话页（view/chat + stores/chat）：
 * 会话列表、气泡流、时间分隔、贴底跟随、顶部消息检索、清空会话。
 *
 * 本项目的差异：
 *  - 实时性由 store 轮询提供（8s），发送时同步等待服务端处理完成；
 *  - 内置「电影港」会话：用户发送的内容即搜索关键字，回复为「摘要 + 封面图 + 详情链接」气泡；
 *  - 内置「磁力下载」会话：发送磁力链接即提交 qBittorrent，首次使用自动弹出连接配置表单；
 *  - 「系统通知」为虚拟会话，读既有 message 表（消息中心原有数据），只读。
 *
 * 手机端（触屏 + 窄视口）的页面形态：
 *  - 全屏沉浸（主框架收起侧栏与顶栏，见 composables/useFullscreenPage）：一级会话列表独占整屏，
 *    点击会话进入二级聊天页，返回键回到列表，形态与聊天软件一致；
 *  - 桌面端仍是双栏同屏，手机端不做宽高占位，直接铺满。
 */
import { mapActions, mapState } from 'pinia'
import { useChatStore } from '@/stores/chat'
import http from '@/libs/http'
import { isMobileDevice } from '@/composables/useIsMobile'
import { requestShellDrawer, useFullscreenPage } from '@/composables/useFullscreenPage'
import ConversationList from './components/ConversationList.vue'
import ChatHeader from './components/ChatHeader.vue'
import MessageList from './components/MessageList.vue'
import ChatComposer from './components/ChatComposer.vue'
import CoverViewer from './components/CoverViewer.vue'
import QbSettingsDialog from './components/QbSettingsDialog.vue'

/** 打开消息中心时默认进入的会话（电影港），无则回落到第一个会话 */
const PREFERRED_SESSION = 'movie'

export default {
    name: 'MessageCenter',
    components: {
        ConversationList,
        ChatHeader,
        MessageList,
        ChatComposer,
        CoverViewer,
        QbSettingsDialog
    },
    setup() {
        // 手机端会话页由路由守卫标记为全屏沉浸（壳层收起）；桌面端恒为 false
        const fullscreen = useFullscreenPage()
        return { fullscreen }
    },
    data() {
        return {
            viewer: { show: false, url: '', title: '' },
            narrow: false,
            narrowShowRoom: false,
            /** 当前是否为「手机」形态：触屏 + 窄视口，决定单栏两级视图 */
            isMobile: false,
            /** 首次提交磁力且尚未配置时自动弹出的配置表单 */
            qbDialog: false,
            /** 已自动弹过一次就不再打扰（用户可在页脚状态条手动打开） */
            qbPrompted: false
        }
    },
    computed: {
        ...mapState(useChatStore, [
            'sessions', 'currentKey', 'currentMessages', 'currentSession',
            'loadingMessages', 'loadingEarlier', 'sending'
        ]),
        ...mapState(useChatStore, ['hasMore']),
        /** 列表区是否可见：手机端与详情页互斥（两级视图），桌面端常显 */
        showList() {
            return !this.isMobile || !this.narrowShowRoom
        },
        /** 详情区是否可见：桌面端常显，手机端由是否进入会话决定 */
        showRoom() {
            return !this.isMobile || this.narrowShowRoom
        },
        title() {
            return this.currentSession?.title || '会话'
        },
        subtitle() {
            if (!this.currentSession) {
                return ''
            }
            const count = this.currentMessages.length
            const kindText = {
                movie: '影视搜索',
                magnet: '磁力下载',
                system: '系统事件与运行通知'
            }[this.currentSession.kind] || '会话'
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
        isMagnet() {
            return this.currentSession?.kind === 'magnet'
        },
        emptyTitle() {
            if (this.currentKind === 'movie') {
                return '还没有搜索记录'
            }
            if (this.currentKind === 'magnet') {
                return '还没有磁力下载记录'
            }
            return '暂无消息'
        },
        emptyTip() {
            if (this.currentKind === 'movie') {
                return '输入片名开始搜索，例如「凡人修仙传」'
            }
            if (this.currentKind === 'magnet') {
                return this.isMobile
                    ? '把磁力链接粘贴到下方输入框，发送即提交下载'
                    : '把磁力链接粘贴到下方输入框，回车即提交 qBittorrent 下载'
            }
            return '系统事件与运行通知会显示在这里'
        },
        composerHint() {
            if (!this.currentSession || this.currentSession.kind === 'system') {
                // 只读会话的说明由输入框 placeholder 承载，避免重复提示
                return ''
            }
            if (this.currentKind === 'movie') {
                return '输入片名回车即搜索，回复含封面与详情链接'
            }
            if (this.currentKind === 'magnet') {
                // 手机端：placeholder 与状态条已说明用法，再堆一行提示会把输入区顶得很高
                return this.isMobile ? '' : '粘贴磁力链接回车即提交 qB；输入「状态」看任务，「帮助」看用法'
            }
            return ''
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
                return
            }
            // session 被清掉（手机端点侧栏「会话」、或浏览历史回到列表）：回到一级会话列表
            if (this.isMobile) {
                this.narrowShowRoom = false
            }
        },
        currentKey(next, prev) {
            if (next === prev) {
                return
            }
            // 桌面端双栏同屏：切换会话即同步地址栏（刷新可定位）。
            // 手机端不在这里写 query —— 进入二级页的时机由 switchSession 决定，
            // 否则初始化打开默认会话就会把「列表页」写成「聊天页」，刷新后直接跳进聊天。
            if (!this.isMobile) {
                this.syncQuery(next)
            }
        },
        isMobile(next) {
            // 在页面内跨越断点时校正视图：手机形态默认停在会话列表
            this.handleResize()
            if (next && !this.$route.query.session) {
                this.narrowShowRoom = false
            }
        }
    },
    async mounted() {
        this.handleResize()
        window.addEventListener('resize', this.handleResize)
        const store = useChatStore()
        await store.refreshSessions()
        // 手机端是两级页面：落地即会话列表，只有地址栏显式指定会话时才直接进聊天
        const requested = this.$route.query.session || (this.isMobile ? '' : PREFERRED_SESSION)
        const exists = store.sessions.some((item) => item.id === requested)
        this.narrowShowRoom = this.isMobile ? !!requested : true
        await store.openSession(exists ? requested : (store.sessions[0]?.id || ''))
        // 手机端直接带 session 进来（分享链接 / 刷新聊天页）时把地址栏补全；列表页则保持干净
        if (this.isMobile && requested) {
            this.syncQuery(store.currentKey)
        } else if (!this.isMobile) {
            this.syncQuery(store.currentKey)
        }
        store.startPolling()
    },
    activated() {
        const store = useChatStore()
        store.refreshSessions()
        store.startPolling()
        // 从左侧菜单的会话子项进入时 URL 带 ?session=<id>：以它为准切换会话，
        // 否则（页签返回等无 query 的场景）保持当前会话，手机端停在会话列表。
        const target = this.$route.query.session
        if (target && target !== store.currentKey) {
            this.switchSession(target)
            return
        }
        this.handleResize()
        if (!target) {
            this.narrowShowRoom = false
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
            this.isMobile = isMobileDevice()
        },
        /**
         * 把当前会话同步进地址栏 query（同一路由内 replace）：
         * 手机端停在会话列表时不写 session —— 刷新/回退即可回到列表，语义与聊天软件一致；
         * 桌面端双栏同屏，始终保持 session，便于刷新定位。
         */
        syncQuery(sessionId) {
            const current = this.$route.query.session || ''
            const next = this.isMobile && !this.narrowShowRoom ? '' : sessionId
            if (current !== next) {
                this.$router.replace({ query: next ? { session: next } : {} }).catch(() => {})
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
            // 先切换视图再同步 query：手机端由此把 session 写进地址栏（进入二级页）
            this.narrowShowRoom = true
            this.syncQuery(sessionId)
        },
        /** 返回会话列表（一级页）；手机端同时清掉地址栏里的 session */
        backToList() {
            this.narrowShowRoom = false
            this.syncQuery(this.currentKey)
        },
        /** 全屏沉浸页面没有主框架侧栏：菜单按钮通过回调把抽屉请求转给主框架 */
        openShellDrawer() {
            requestShellDrawer()
        },
        async refresh() {
            await this.refreshSessions()
            await useChatStore().reloadSession(this.currentKey)
        },
        async onSend(text) {
            const store = useChatStore()
            try {
                await store.send(text)
            } catch (e) {
                // 失败文案由 axios 拦截器提示
            }
            // 磁力会话：后端在未配置时会回一条配置引导，此处据此自动打开配置表单
            if (this.isMagnet && !this.qbPrompted && this.hasConfigPrompt()) {
                this.qbPrompted = true
                this.qbDialog = true
            }
        },
        /** 最近一条回复是否在提示配置 qBittorrent */
        hasConfigPrompt() {
            const rows = this.currentMessages
            if (!rows.length) {
                return false
            }
            const last = rows[rows.length - 1]
            if (!last || !last.payload) {
                return false
            }
            try {
                const payload = JSON.parse(last.payload)
                return payload && payload.kind === 'magnet' && payload.status === 'config'
            } catch (e) {
                return false
            }
        },
        /** 打开 qBittorrent 连接配置表单（顶栏「更多 → qBittorrent 设置」与配置引导共用） */
        openQbDialog() {
            this.qbDialog = true
        },
        /** 配置保存后标记已引导过，避免再次自动弹出配置表单 */
        onQbSaved() {
            this.qbPrompted = true
            this.qbDialog = false
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
    /* ======================================================================
       聊天区统一水平留白：顶栏标题 / 消息流头像列 / 输入框三者的左右边缘
       必须落在同一条垂直线上，否则会出现「头像、气泡、输入框各对齐各」的错位。
       子组件（ChatHeader / MessageList / ChatComposer）统一读这两个变量。
       ====================================================================== */
    --chat-gutter-left: 16px;   /* 左右留白：桌面 16px / 移动端 14px + 安全区 */
    --chat-gutter-right: 16px;
    --chat-avatar-gap: 10px;    /* 头像与气泡的水平间距 */
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

/* 桌面端：窗口拖窄（<960px）时折叠为单栏，列表 ⇄ 详情用顶栏返回按钮切换 */
.message-page.desktop.narrow.show-room :deep(.conversation-list) {
    display: none;
}

.message-page.desktop.narrow:not(.show-room) .chat-room {
    display: none;
}

/* 手机端：两种情况都独占整屏，不存在「残留半栏」的中间态 */
.message-page.mobile > :deep(.conversation-list),
.message-page.mobile > .chat-room {
    flex: 1;
    width: 100%;
    min-width: 0;
}

/* 手机端会话列表：宽度铺满、取消右描边 */
.message-page.mobile :deep(.conversation-list) {
    border-right: 0;
    background: var(--bg);
}

/* ==========================================================================
   二级聊天页：顶栏 → 消息流 → 输入区 三段式，末段留出系统手势安全距离。
   三者共用同一组左右留白变量（含刘海屏安全区），保证头像列与输入框上下对齐。
   ========================================================================== */
.message-page.mobile {
    --chat-gutter-left: max(14px, env(safe-area-inset-left));
    --chat-gutter-right: max(14px, env(safe-area-inset-right));
    --chat-avatar-gap: 10px;
}

.message-page.mobile :deep(.chat-header) {
    padding-top: env(safe-area-inset-top);
    height: calc(58px + env(safe-area-inset-top));
}

.message-page.mobile :deep(.composer) {
    padding-bottom: max(4px, env(safe-area-inset-bottom));
}

/* 非全屏（桌面端）：卡片四周留出安全边距，避免贴边显示 */
.message-page.desktop.chrome {
    border-radius: var(--radius);
}

/* 全屏沉浸：没有卡片边框与圆角，真正铺满整屏 */
.message-page.mobile {
    border: 0;
    border-radius: 0;
    box-shadow: none;
    background: var(--bg);
}

/* 窄视口（桌面端窗口拖窄 / 平板）：会话列表铺满，仅保留卡片圆角 */
@media (max-width: 900px) {
    .message-page {
        --chat-gutter-left: 14px;
        --chat-gutter-right: 14px;
    }

    .message-page :deep(.conversation-list) {
        width: 100%;
        border-right: 0;
    }

    .chat-room :deep(.chat-header) {
        gap: 8px;
    }
}
</style>
