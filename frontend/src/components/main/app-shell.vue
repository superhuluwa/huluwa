<template>
    <div class="app-shell" :class="{ 'chat-open': chatOpen }">
        <!-- 居中手机宽度：桌面浏览器里像原生 App 一样一列居中；手机上铺满 -->
        <div class="app-frame">
            <template v-if="!chatOpen">
                <!-- 标题栏：与安卓 PageHeader 一致（26sp 加粗标题、左对齐、右侧动作位） -->
                <header class="page-header" :class="{ 'is-sessions': tab.isSessionTab }">
                    <h1 class="page-title">{{ tab.title }}</h1>
                    <div class="header-action">
                        <button v-if="tab.key === 'mine'" class="account-chip" type="button" title="连接配置"
                            @click="openAccount">
                            <span class="account-avatar">葫</span>
                        </button>
                        <span v-else-if="tab.isSessionTab && sessionLoading" class="header-loading" aria-label="正在加载">
                            <i class="fa-solid fa-spinner fa-spin"></i>
                        </span>
                    </div>
                </header>

                <main class="page-body" :class="{ 'is-sessions': tab.isSessionTab }">
                    <keep-alive>
                        <component :is="currentPage" :key="activeTab" />
                    </keep-alive>
                </main>
            </template>

            <!-- 会话 / 管理页：整屏接管（页面自带返回标题栏），底部导航收起，与安卓一致 -->
            <router-view v-else v-slot="{ Component }">
                <component :is="Component" :key="$route.fullPath" />
            </router-view>

            <!-- 底部四标签：安卓进入会话后收起导航，这里保持一致（全屏会话只保留页面内返回键） -->
            <nav v-if="!chatOpen" class="tab-bar" aria-label="主导航">
                <button v-for="item in tabs" :key="item.key" class="tab-item" :class="{ active: item.key === activeTab }"
                    type="button" :aria-current="item.key === activeTab ? 'page' : undefined" @click="selectTab(item)">
                    <span class="tab-icon">
                        <i :class="item.icon"></i>
                        <span v-if="badgeOf(item) > 0" class="tab-badge">{{ badgeOf(item) > 99 ? '99+' : badgeOf(item) }}</span>
                    </span>
                    <span class="tab-label">{{ item.title }}</span>
                </button>
            </nav>

            <AccountSheet v-model:show="accountOpen" />
        </div>
    </div>
</template>

<script>
/**
 * 网页端主框架：与安卓 HuluwaRoot 对齐的「标题栏 + 内容 + 底部四标签」结构。
 *
 * - 标签固定为：功能 → 系统 → 下载 → 我的；「订阅」已迁入系统会话「我的订阅」；
 * - 会话详情（含 Frp / Docker / App 构建 / 我的订阅等管理会话）进入后用自己的返回键回到列表，
 *   路由上仍落在对应标签内（?session= 区分具体会话）；
 * - 桌面浏览器把内容限制在 660px 居中列，观感同手机 App；
 * - 会话未读角标与安卓一致：功能 / 系统两个标签分别汇总各自分类的未读数。
 */
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { setFullscreenPage } from '@/composables/useFullscreenPage'
import { isSystemSession } from '@/utils/localSessions'
import SessionsView from '@/views/sessions/index.vue'
import DownloadsView from '@/views/downloads/index.vue'
import MineView from '@/views/mine/index.vue'
import AccountSheet from '@/components/main/components/account-sheet.vue'

/** 底部标签定义：key 与路由名共用，图标取安卓对应的 Material 图标意象 */
export const TABS = [
    { key: 'functions', title: '功能', icon: 'fa-solid fa-table-cells-large', isSessionTab: true, system: false },
    { key: 'system', title: '系统', icon: 'fa-solid fa-shield-halved', isSessionTab: true, system: true },
    { key: 'downloads', title: '下载', icon: 'fa-solid fa-circle-down' },
    { key: 'mine', title: '我的', icon: 'fa-regular fa-user' }
]

export default {
    name: 'AppShell',
    components: { SessionsView, DownloadsView, MineView, AccountSheet },
    setup() {
        return { chatStore: useChatStore(), auth: useAuthStore() }
    },
    data() {
        return {
            tabs: TABS,
            accountOpen: false,
            sessionLoading: false,
            polling: null
        }
    },
    computed: {
        /** 当前标签：由路由 meta.tab 决定（会话详情页也归属其所在标签） */
        activeTab() {
            return (this.$route.meta && this.$route.meta.tab) || 'functions'
        },
        tab() {
            return TABS.find((item) => item.key === this.activeTab) || TABS[0]
        },
        /** 是否处于会话详情 / 管理会话（整屏接管，隐藏标题栏与底部导航） */
        chatOpen() {
            return Boolean(this.$route.meta && this.$route.meta.chat)
        },
        currentPage() {
            return this.tab.key === 'system' || this.tab.key === 'functions' ? SessionsView
                : this.tab.key === 'downloads' ? DownloadsView : MineView
        },
        /** 功能 / 系统两个标签分别汇总各自分类的未读（对齐安卓 HuluwaRoot 的角标逻辑） */
        unreadByTab() {
            const sessions = this.chatStore.sessions || []
            return {
                functions: sessions.filter((item) => !isSystemSession(item)).reduce((sum, item) => sum + (item.unread || 0), 0),
                system: sessions.filter(isSystemSession).reduce((sum, item) => sum + (item.unread || 0), 0)
            }
        }
    },
    watch: {
        '$route.query.session'() {
            this.syncFullscreen()
        }
    },
    async mounted() {
        this.syncFullscreen()
        await this.refreshSessions()
        this.polling = setInterval(() => this.refreshSessions(), 15000)
    },
    activated() {
        this.refreshSessions()
    },
    beforeUnmount() {
        clearInterval(this.polling)
        this.polling = null
    },
    methods: {
        badgeOf(item) {
            return this.unreadByTab[item.key] || 0
        },
        async refreshSessions() {
            this.sessionLoading = true
            try {
                await this.chatStore.refreshSessions()
            } catch (error) {
                // 角标失败不影响主框架
            } finally {
                this.sessionLoading = false
            }
        },
        /** 切标签：回到该标签的列表页（清掉会话 query，离开会话详情） */
        selectTab(item) {
            if (item.key === this.activeTab && !this.chatOpen) {
                return
            }
            // 标签 key 与路由名一致（functions / system / downloads / mine），
            // 会话 / 管理会话页里点标签也会回到该标签的列表页。
            this.$router.push({ name: item.key, query: {} })
        },
        openAccount() {
            this.accountOpen = true
        },
        /** 会话详情页需要整屏：进入后收起标题栏与底部导航（与安卓一致） */
        syncFullscreen() {
            setFullscreenPage(this.chatOpen)
        }
    }
}
</script>

<style scoped>
.app-shell {
    width: 100%;
    height: 100%;
    background: var(--frame-outside);
    display: flex;
    justify-content: center;
    overflow: hidden;
}

/* 手机 App 宽度：与安卓 HuluwaRoot 的 widthIn(max = 660.dp) 一致 */
.app-frame {
    position: relative;
    display: flex;
    flex-direction: column;
    width: 100%;
    max-width: 660px;
    height: 100%;
    min-height: 0;
    background: var(--paper);
    box-shadow: 0 0 60px rgba(36, 61, 50, .10);
    overflow: hidden;
}

/* ---------------- 标题栏（PageHeader） ---------------- */
.page-header {
    flex: none;
    display: flex;
    align-items: center;
    gap: 16px;
    /* 与安卓一致：上下 15/16、左右 22，最小高度 63 */
    min-height: 63px;
    padding: 15px 22px 16px;
    background: var(--paper);
    padding-top: max(15px, env(safe-area-inset-top));
}

.page-header.is-sessions {
    background: var(--sessions-bg);
}

.page-title {
    flex: 1;
    margin: 0;
    font-size: 26px;
    line-height: 32px;
    font-weight: 700;
    color: var(--ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.header-action {
    flex: none;
    display: flex;
    align-items: center;
    gap: 10px;
}

.header-loading {
    display: inline-flex;
    color: var(--muted);
    font-size: 16px;
}

.account-chip {
    width: 40px;
    height: 40px;
    padding: 0;
    border: 0;
    border-radius: 50%;
    background: var(--soft-green);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
}

.account-avatar {
    font-family: Georgia, "Songti SC", serif;
    font-size: 19px;
    font-weight: 700;
    color: #49633B;
}

/* ---------------- 内容区 ---------------- */
.page-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    background: var(--paper);
    -webkit-overflow-scrolling: touch;
}

.page-body.is-sessions {
    background: var(--sessions-bg);
}

/* ---------------- 底部标签栏 ---------------- */
.tab-bar {
    flex: none;
    display: flex;
    align-items: stretch;
    background: #FFFFFF;
    border-top: 1px solid var(--line);
    padding-bottom: env(safe-area-inset-bottom);
    z-index: 20;
}

.tab-item {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    padding: 8px 2px 7px;
    border: 0;
    background: none;
    color: var(--muted);
    cursor: pointer;
    font-family: inherit;
    transition: color .15s ease;
    -webkit-tap-highlight-color: transparent;
}

.tab-item.active {
    color: var(--pine);
}

.tab-icon {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 24px;
    font-size: 17px;
}

/* 选中态：安卓用 Material 的胶囊指示器，这里用同色浅底胶囊还原 */
.tab-item.active .tab-icon::before {
    content: "";
    position: absolute;
    inset: -2px -8px;
    border-radius: 999px;
    background: var(--soft-green);
    z-index: -1;
}

.tab-icon i {
    position: relative;
    z-index: 1;
}

.tab-label {
    font-size: 10px;
    line-height: 15px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
}

.tab-badge {
    position: absolute;
    top: -5px;
    left: 21px;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: 8px;
    background: var(--pine);
    color: #fff;
    font-size: 9px;
    line-height: 16px;
    font-weight: 700;
    text-align: center;
    z-index: 2;
}

/* 会话详情整屏时：导航贴底，内容区自行处理 */
.app-shell.chat-open .app-frame {
    background: var(--paper);
}

@media (max-width: 660px) {
    .app-frame {
        box-shadow: none;
    }

    .tab-item {
        padding-bottom: max(7px, env(safe-area-inset-bottom));
    }
}
</style>
