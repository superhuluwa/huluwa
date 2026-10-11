<template>
    <div class="main-console" :class="{ 'is-mobile': isMobile, 'drawer-open': isMobile && mobileDrawerOpen }">
        <aside class="sider" :class="{ collapsed: siderCollapsed }">
            <div class="logo">
                <img class="logo-mark" src="/huluwa-logo.svg" alt="葫芦娃 Logo" width="36" height="36" />
                <div v-show="!siderCollapsed" class="logo-text">
                    <div class="logo-name">葫芦娃助手</div>
                    <div class="logo-sub">OPS CONSOLE</div>
                </div>
            </div>
            <n-menu :value="activeMenu" :options="menuOptions" :collapsed="siderCollapsed" :collapsed-width="64"
                :collapsed-icon-size="17" :indent="18" :expanded-keys="expandedKeys" accordion
                @update:value="turnToPage" @update:expanded-keys="handleExpandedChange" />
            <div class="sider-foot" v-show="!siderCollapsed">
                <span>版本</span>
                <span class="mono">v1.0.0</span>
            </div>
        </aside>

        <!-- 移动端抽屉遮罩：点击关闭侧栏（桌面端不渲染） -->
        <div v-if="isMobile && mobileDrawerOpen" class="sider-mask" @click="closeDrawer"></div>

        <div class="main-body">
            <header-bar :collapsed="headerCollapsed" :mobile="isMobile" @on-coll-change="handleCollapsedChange">
                <user-chip />
            </header-bar>

            <div class="content-wrapper">
                <router-view v-slot="{ Component }">
                    <keep-alive>
                        <component :is="Component" :key="$route.path" />
                    </keep-alive>
                </router-view>
            </div>
        </div>
    </div>
</template>

<script>
import { h } from 'vue'
import { RouterLink } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useChatStore } from '@/stores/chat'
import HeaderBar from './components/header-bar.vue'
import UserChip from './components/user-chip.vue'

/** 「消息中心」分组下的会话子项（对齐 quantum 的 chat-index /「会话」） */
const MESSAGES_INDEX_KEY = 'messages-index'
const MESSAGES_INDEX_TITLE = '会话'

const renderIcon = (iconClass) => {
    if (!iconClass) return undefined
    return () => h('i', { class: iconClass })
}

export default {
    name: 'MainLayout',
    components: { HeaderBar, UserChip },
    setup() {
        const appStore = useAppStore()
        const chatStore = useChatStore()
        return { appStore, chatStore }
    },
    data() {
        return {
            // 展开的子菜单 key；accordion 模式下只保留一个，默认跟随当前页所在分组
            expandedKeys: [],
            // 移动端（窄屏，<= 900px）：侧栏改为覆盖式抽屉，默认关闭
            isMobile: false,
            mobileDrawerOpen: false
        }
    },
    computed: {
        /** 桌面端：沿用 store 的持久化折叠态（行为不变） */
        collapsed() {
            return this.appStore.collapsed
        },
        /** 侧栏实际是否收起：移动端抽屉默认收起，不受桌面折叠偏好影响 */
        siderCollapsed() {
            return this.isMobile ? !this.mobileDrawerOpen : this.collapsed
        },
        /** 顶栏按钮展示状态：移动端仅在抽屉打开时高亮为「收起」 */
        headerCollapsed() {
            return this.isMobile ? !this.mobileDrawerOpen : this.collapsed
        },
        activeMenu() {
            // 会话页高亮「消息中心 → 会话」子项，其余页面高亮路由名
            if (this.$route.name === 'messages') {
                return MESSAGES_INDEX_KEY
            }
            return this.$route.name
        },
        menuOptions() {
            // 顶层路由（登录 / 错误页已通过 hideInMenu 排除）；带 children 的渲染为可折叠子菜单。
            const home = this.$router.options.routes.find((route) => route.path === '/')
            if (!home || !Array.isArray(home.children)) return []
            return home.children
                .filter((item) => !(item.meta && item.meta.hideInMenu))
                .map((item) => (item.name === 'messages' ? this.messagesMenuOption(item) : this.toMenuOption(item)))
        },
    },
    watch: {
        // 跨分组跳转或刷新后，保持当前页所在分组展开；
        // 消息中心内部切换会话（仅 query 变化）不触发本监听，展开态自然保留。
        '$route.name'(next, prev) {
            // 消息中心 ⇄ 其它页面时都要确保会话分组可见
            if (next === 'messages' || prev === 'messages') {
                this.ensureMessagesExpanded()
                if (next === 'messages') {
                    return
                }
            }
            this.syncExpandedKeys()
        }
    },
    async mounted() {
        this.handleResize()
        window.addEventListener('resize', this.handleResize)
        this.syncExpandedKeys()
        // 菜单里的会话子项/未读角标以 chat store 为准：
        // 主框架常驻轮询会话快照（每 15s），离开会话页也能更新侧栏角标；
        // 会话页自己还有更密的轮询（8s），两者互不冲突（refreshSessions 幂等）。
        try {
            await this.chatStore.refreshSessions()
        } catch (e) {
            // 菜单角标拉取失败不影响主框架
        }
        this.startMenuPolling()
    },
    beforeUnmount() {
        window.removeEventListener('resize', this.handleResize)
        this.stopMenuPolling()
    },
    methods: {
        /** 窄屏判定：<= 900px 走移动端布局（抽屉侧栏 + 单列内容），桌面端分支完全不变 */
        handleResize() {
            const mobile = window.innerWidth <= 900
            if (mobile !== this.isMobile) {
                this.isMobile = mobile
                // 从桌面缩到移动端时收起抽屉，避免窄屏下侧栏占满整屏
                if (mobile) {
                    this.mobileDrawerOpen = false
                }
            }
        },
        /** 顶栏按钮：桌面端折叠侧栏（写 store），移动端开合抽屉 */
        handleCollapsedChange(value) {
            if (this.isMobile) {
                this.mobileDrawerOpen = !this.mobileDrawerOpen
                return
            }
            this.appStore.setCollapsed(value)
        },
        closeDrawer() {
            this.mobileDrawerOpen = false
        },
        /** 会话子项的数据保障：列表为空时补拉一次（避免菜单分组渲染成空分组） */
        ensureSessionsLoaded() {
            const sessions = this.chatStore.sessions || []
            if (!sessions.length) {
                this.chatStore.refreshSessions().catch(() => {})
            }
        },
        /** 确保「消息中心」分组处于展开态（会话页内部切换会话时不被收起） */
        ensureMessagesExpanded() {
            this.ensureSessionsLoaded()
            if (!this.expandedKeys.includes('messages')) {
                this.expandedKeys = [...this.expandedKeys, 'messages']
            }
        },
        /** 侧栏角标轮询：只刷新会话快照（不碰消息流），15s 一次 */
        startMenuPolling() {
            if (this._menuTimer) {
                return
            }
            this._menuTimer = setInterval(() => {
                this.chatStore.refreshSessions().catch(() => {})
            }, 15000)
        },
        stopMenuPolling() {
            if (this._menuTimer) {
                clearInterval(this._menuTimer)
                this._menuTimer = null
            }
        },
        /** 路由节点转 n-menu 选项；有 children 的渲染为可折叠分组，父级本身不跳转。 */
        toMenuOption(item) {
            const children = Array.isArray(item.children)
                ? item.children.filter((child) => !(child.meta && child.meta.hideInMenu))
                : []
            if (children.length) {
                return {
                    key: item.name,
                    label: item.meta && item.meta.title,
                    icon: renderIcon(item.meta && item.meta.icon),
                    children: children.map((child) => this.toMenuOption(child))
                }
            }
            return {
                key: item.name,
                label: () => h(RouterLink, { to: { name: item.name } }, { default: () => item.meta.title }),
                icon: renderIcon(item.meta && item.meta.icon)
            }
        },
        /**
         * 消息中心分组：与 Docker 管理同款的可折叠分组，子项 = 各会话（电影港 / 系统通知…）。
         * 子项 key 用 「messages:<sessionId>」，点击跳 /messages?session=<id>；
         * 未读角标直接挂在子项标签上（父级用红点提示有未读，折叠时也能看见）。
         */
        messagesMenuOption(item) {
            // 与 quantum 的菜单结构一致：「消息中心」分组下只有一个「会话」子项
            // （会话列表在页面内呈现，不摊进菜单），未读角标挂在子项上、父级用红点提示。
            const unreadTotal = (this.chatStore.sessions || [])
                .reduce((sum, session) => sum + (session.unread || 0), 0)
            return {
                key: item.name,
                label: () => h('span', { class: 'menu-label' }, [
                    h('span', item.meta && item.meta.title),
                    unreadTotal > 0 ? h('span', { class: 'menu-dot' }) : null
                ]),
                icon: renderIcon(item.meta && item.meta.icon),
                children: [
                    {
                        key: MESSAGES_INDEX_KEY,
                        label: () => h('span', { class: 'menu-label' }, [
                            h('span', MESSAGES_INDEX_TITLE),
                            unreadTotal > 0
                                ? h('span', { class: 'menu-badge' }, unreadTotal > 99 ? '99+' : String(unreadTotal))
                                : null
                        ]),
                        icon: renderIcon('fa-solid fa-comments')
                    }
                ]
            }
        },
        /** 展开当前激活项所属的顶层分组。 */
        syncExpandedKeys() {
            const currentName = this.$route.name
            // 消息中心（会话页）：分组保持展开，便于随时切换会话
            // （用户手动展开其它分组时这里不能把 messages 之外的手动展开态清掉——
            //   accordion 模式下 n-menu 自己只保留一个，这里只需确保当前页所在分组可见）
            if (currentName === 'messages') {
                this.ensureMessagesExpanded()
                return
            }
            const home = this.$router.options.routes.find((route) => route.path === '/')
            const groups = (home && Array.isArray(home.children) ? home.children : [])
                .filter((item) => Array.isArray(item.children) && item.children.length)
            const owner = groups.find((group) => group.children.some((child) => child.name === currentName))
            this.expandedKeys = owner ? [owner.name] : []
        },
        turnToPage(name) {
            // 移动端点完菜单后立即收起抽屉，露出目标页面
            this.closeDrawer()
            // 「消息中心 → 会话」子项：进入会话页（保留当前会话，默认电影港）
            if (name === MESSAGES_INDEX_KEY) {
                this.$router.push({ name: 'messages' }).catch(() => {})
                return
            }
            if (name !== this.$route.name) {
                this.$router.push({ name }).catch(() => {})
            }
        },
        handleExpandedChange(keys) {
            // n-menu 在 options 刷新（会话轮询）时也会回传内部展开态，
            // 会把「消息中心」分组意外收起；会话页要求该分组常开，这里补回。
            if (this.$route.name === 'messages' && !keys.includes('messages')) {
                this.expandedKeys = [...keys, 'messages']
                return
            }
            this.expandedKeys = keys
        }
    }
}
</script>

<style scoped>
.main-console {
    display: flex;
    height: 100%;
    background: var(--bg);
}

.sider {
    /* 宽度含左右 padding 与描边：折叠 64px 才是视觉总宽（否则 64+20+1=85px） */
    box-sizing: border-box;
    width: 236px;
    flex: none;
    background: linear-gradient(180deg, #0d1526 0%, #0b1322 100%);
    border-right: 1px solid var(--line);
    display: flex;
    flex-direction: column;
    padding: 14px 10px 0;
    transition: width .25s cubic-bezier(.4, 0, .2, 1);
    overflow: hidden;
}

.sider.collapsed {
    width: 64px;
}

.logo {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px 18px;
    flex: none;
    justify-content: center;
}

.sider:not(.collapsed) .logo {
    justify-content: flex-start;
}

.logo-mark {
    width: 36px;
    height: 36px;
    flex: none;
    border-radius: 9px;
    display: block;
    object-fit: contain;
    box-shadow: 0 4px 14px rgba(0, 0, 0, .45);
}

.logo-name {
    font-weight: 700;
    font-size: 16px;
    color: var(--text);
    white-space: nowrap;
}

.logo-sub {
    font-size: 11px;
    color: var(--text-3);
    letter-spacing: .5px;
    white-space: nowrap;
}

.sider :deep(.n-menu) {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
}


.sider :deep(.menu-label) {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
}

.sider :deep(.menu-badge) {
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

.sider :deep(.menu-dot) {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--danger);
    box-shadow: 0 0 8px var(--danger);
}

.sider-foot {
    border-top: 1px solid var(--line);
    padding: 10px 12px;
    display: flex;
    align-items: center;
    gap: 10px;
    color: var(--text-3);
    font-size: 12px;
    flex: none;
}

.main-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
}

.content-wrapper {
    flex: 1;
    padding: 18px;
    overflow: auto;
    position: relative;
}
/* ==========================================================================
   移动端适配（<= 900px）：仅覆盖窄屏，桌面端样式保持原样
   侧栏改为覆盖式抽屉（默认收起），内容区独占整屏宽度。
   ========================================================================== */
@media (max-width: 900px) {
    .sider {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        z-index: 130;
        width: 236px;
        transform: translateX(-100%);
        transition: transform .25s cubic-bezier(.4, 0, .2, 1);
        box-shadow: 0 0 40px rgba(0, 0, 0, .55);
    }

    .main-console.drawer-open .sider {
        transform: translateX(0);
    }

    .sider.collapsed {
        width: 236px;
    }

    .sider-mask {
        position: fixed;
        inset: 0;
        z-index: 120;
        background: rgba(0, 0, 0, .5);
        backdrop-filter: blur(2px);
    }

    .content-wrapper {
        padding: 12px;
    }
}
</style>
