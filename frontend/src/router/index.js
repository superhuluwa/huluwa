import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { loadingBar } from '@/libs/discrete'
import { createRouteLoader } from '@/utils/routePreload'
import { setFullscreenPage } from '@/composables/useFullscreenPage'
import { setPageTransitionName, transitionNameFor } from '@/composables/usePageTransition'

/**
 * 路由结构对齐安卓端 HuluwaRoot：
 *   底部四标签 = 功能 / 系统 / 下载 / 我的（「订阅」已迁入系统会话「我的订阅」）
 *   「功能」「系统」的标签页就是会话列表；点进会话后使用 meta.chat 接管整屏（隐藏标题栏与底部导航），
 *   由会话页自带的返回键回到列表。管理类会话（Frp / Docker / App 构建 / 巡检记录 / 剧集管理 / 我的订阅）
 *   同样以 meta.chat 的全屏页面呈现，视觉与安卓的管理会话一致。
 *
 * meta.tab   当前页面归属的底部标签（决定底部导航高亮）
 * meta.chat  是否是「进入会话」的全屏页（隐藏标签栏）
 */
const AppShell = createRouteLoader(() => import('@/components/main/app-shell.vue'))
const ChatView = createRouteLoader(() => import('@/views/chat/index.vue'))

const routes = [
    {
        path: '/login',
        name: 'login',
        meta: { title: '登录', hideInMenu: true },
        component: createRouteLoader(() => import('@/views/login/index.vue'))
    },
    {
        path: '/',
        component: AppShell,
        redirect: '/functions',
        children: [
            {
                path: 'functions',
                name: 'functions',
                meta: { title: '功能', tab: 'functions' },
                component: createRouteLoader(() => import('@/views/sessions/index.vue'))
            },
            {
                path: 'system',
                name: 'system',
                meta: { title: '系统', tab: 'system' },
                component: createRouteLoader(() => import('@/views/sessions/index.vue'))
            },
            {
                path: 'chat',
                name: 'chat',
                meta: { title: '会话', tab: 'functions', chat: true },
                component: ChatView
            },
            {
                path: 'downloads',
                name: 'downloads',
                meta: { title: '下载', tab: 'downloads' },
                component: createRouteLoader(() => import('@/views/downloads/index.vue'))
            },
            {
                path: 'subscriptions',
                // 旧地址兼容：订阅已从底部标签迁入系统会话「我的订阅」
                redirect: '/my-subscriptions'
            },
            {
                path: 'mine',
                name: 'mine',
                meta: { title: '我的', tab: 'mine' },
                component: createRouteLoader(() => import('@/views/mine/index.vue'))
            },
            // ---- 系统标签下的管理会话（全屏，视觉对齐安卓 ManagementSessionScreen） ----
            {
                path: 'frp',
                name: 'frp',
                meta: { title: 'Frp 管理', tab: 'system', chat: true },
                component: createRouteLoader(() => import('@/views/frp/index.vue'))
            },
            {
                path: 'docker',
                name: 'docker',
                meta: { title: 'Docker 管理', tab: 'system', chat: true },
                component: createRouteLoader(() => import('@/views/docker/index.vue'))
            },
            {
                path: 'android',
                name: 'android-build',
                meta: { title: 'App 构建管理', tab: 'system', chat: true },
                component: createRouteLoader(() => import('@/views/android/index.vue'))
            },
            {
                path: 'check-logs',
                name: 'check-logs',
                meta: { title: '巡检记录', tab: 'system', chat: true },
                component: createRouteLoader(() => import('@/views/movie/check-logs.vue'))
            },
            {
                path: 'library',
                name: 'library',
                meta: { title: '剧集管理', tab: 'system', chat: true },
                component: createRouteLoader(() => import('@/views/movie/library.vue'))
            },
            {
                path: 'my-subscriptions',
                name: 'my-subscriptions',
                meta: { title: '我的订阅', tab: 'system', chat: true },
                component: createRouteLoader(() => import('@/views/subscriptions/index.vue'))
            },
            // ---- 我的 → 附加工具（安卓端没有对应页面，保留在网页端） ----
            {
                path: 'dashboard',
                name: 'dashboard',
                meta: { title: '服务器概览', tab: 'mine', chat: true },
                component: createRouteLoader(() => import('@/views/dashboard/index.vue'))
            },
            {
                path: 'push',
                name: 'push',
                meta: { title: '推送设置', tab: 'mine', chat: true },
                component: createRouteLoader(() => import('@/views/push/index.vue'))
            }
        ]
    },
    {
        path: '/500',
        name: 'error_500',
        meta: { title: '500', hideInMenu: true },
        component: createRouteLoader(() => import('@/views/error-page/500.vue'))
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'error_404',
        meta: { title: '404', hideInMenu: true },
        component: createRouteLoader(() => import('@/views/error-page/404.vue'))
    }
]

const router = createRouter({
    routes,
    history: createWebHashHistory()
})

router.beforeEach((to) => {
    loadingBar.start()
    const auth = useAuthStore()

    if (to.name === 'login') {
        return true
    }
    if (!auth.logged) {
        return { name: 'login', query: { redirect: to.fullPath } }
    }
    return true
})

/**
 * 全屏标记：进入会话 / 管理会话时铺满整屏（与安卓「进入会话隐藏标签栏」一致）。
 * 在导航落地前写好，避免先画一帧带标题栏的布局再被覆盖。
 */
router.beforeResolve((to, from) => {
    setFullscreenPage(Boolean(to.meta && to.meta.chat))
    // 转场方向：列表 → 二级页从右滑入，二级页 → 列表反向滑出（微信式）
    setPageTransitionName(transitionNameFor(from, to))
})

router.afterEach((to) => {
    document.title = to.meta && to.meta.title
        ? `${to.meta.title} - 葫芦娃助手`
        : '葫芦娃助手'
    // 页面跳转后回到顶部（会话详情由消息流自行定位）
    if (!to.meta?.chat) {
        window.scrollTo?.(0, 0)
    }
    loadingBar.finish()
})

router.onError(() => {
    loadingBar.error()
})

export default router
