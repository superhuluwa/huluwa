import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { loadingBar } from '@/libs/discrete'
import { createRouteLoader } from '@/utils/routePreload'
import { isMobileDevice } from '@/composables/useIsMobile'
import { setFullscreenPage } from '@/composables/useFullscreenPage'

const Main = createRouteLoader(() => import('@/components/main/main.vue'))

const routes = [
    {
        path: '/login',
        name: 'login',
        meta: { title: '登录', hideInMenu: true },
        component: createRouteLoader(() => import('@/views/login/index.vue'))
    },
    {
        path: '/',
        component: Main,
        redirect: '/messages',
        children: [
            // 侧栏顺序即此处的声明顺序：首页排在最前，其次才是消息中心
            {
                path: 'dashboard',
                name: 'dashboard',
                meta: { title: '首页', icon: 'fa-solid fa-gauge-high' },
                component: createRouteLoader(() => import('@/views/dashboard/index.vue'))
            },
            // 消息中心（会话）：登录后的默认落地页，手机端为全屏两级结构
            {
                path: 'messages',
                name: 'messages',
                meta: { title: '消息中心', icon: 'fa-solid fa-bell' },
                component: createRouteLoader(() => import('@/views/message/index.vue'))
            },
            // 消息中心：父级分组可折叠（与 quantum 的「消息中心 → 会话」结构一致），
            // 分组下的「会话」子项由 components/main/main.vue 渲染，未读角标取自 chat store；
            // 具体会话用 ?session= 区分（会话列表在页面内呈现），故此处只登记一个路由、不声明 children。
            // 消息中心：父级分组可折叠（与 quantum 的「消息中心 → 会话」结构一致），
            // 分组下另有「我的订阅」「我的下载」两个管理页：
            // 前者维护订阅与磁力基线，后者单独查看订阅触发的下载任务。
            {
                path: 'subscriptions',
                name: 'movie-subscriptions',
                meta: { title: '我的订阅', icon: 'fa-solid fa-bell', hideInMenu: true },
                component: createRouteLoader(() => import('@/views/movie/subscriptions.vue'))
            },
            {
                path: 'downloads',
                name: 'movie-downloads',
                meta: { title: '我的下载', icon: 'fa-solid fa-download', hideInMenu: true },
                component: createRouteLoader(() => import('@/views/movie/downloads.vue'))
            },
            // 本地剧集：扫描共享下载目录，按集查看并批量删除已下载文件
            {
                path: 'library',
                name: 'movie-library',
                meta: { title: '剧集管理', icon: 'fa-solid fa-hard-drive', hideInMenu: true },
                component: createRouteLoader(() => import('@/views/movie/library.vue'))
            },
            // 巡检记录：订阅数据的巡检历史（电影港 + 电影天堂），与「我的订阅」「我的下载」平级
            {
                path: 'check-logs',
                name: 'movie-check-logs',
                meta: { title: '巡检记录', icon: 'fa-solid fa-list-check', hideInMenu: true },
                component: createRouteLoader(() => import('@/views/movie/check-logs.vue'))
            },
            {
                path: 'frp',
                name: 'frp',
                meta: { title: 'Frp管理', icon: 'fa-solid fa-network-wired' },
                component: createRouteLoader(() => import('@/views/frp/index.vue'))
            },
            {
                path: 'android',
                name: 'android-app',
                meta: { title: '安卓 App', icon: 'fa-brands fa-android' },
                component: createRouteLoader(() => import('@/views/android/index.vue'))
            },
            {
                path: 'push',
                name: 'push',
                meta: { title: '推送设置', icon: 'fa-solid fa-paper-plane' },
                component: createRouteLoader(() => import('@/views/push/index.vue'))
            },
            // Docker 管理：父级分组可折叠，子项对应概览 / 容器 / 镜像 / 网络 / 卷。
            // 父级本身只作为 n-menu 的子菜单容器，不直接渲染页面，因此指向概览页。
            {
                path: 'docker',
                name: 'docker',
                meta: { title: 'Docker管理', icon: 'fa-brands fa-docker' },
                redirect: '/docker/index',
                // 纯分组路由：不挂 component，子页面仍渲染在 Main 的 router-view 中；
                // 若在此挂 component，子路由将因缺少嵌套 router-view 而无法显示。
                children: [
                    {
                        path: 'index',
                        name: 'docker-index',
                        meta: { title: '概览', icon: 'fa-solid fa-gauge-high' },
                        component: createRouteLoader(() => import('@/views/docker/index.vue'))
                    },
                    {
                        path: 'containers',
                        name: 'docker-containers',
                        meta: { title: '容器管理', icon: 'fa-solid fa-cube' },
                        component: createRouteLoader(() => import('@/views/docker/containers.vue'))
                    },
                    {
                        path: 'images',
                        name: 'docker-images',
                        meta: { title: '镜像管理', icon: 'fa-regular fa-images' },
                        component: createRouteLoader(() => import('@/views/docker/images.vue'))
                    },
                    {
                        path: 'networks',
                        name: 'docker-networks',
                        meta: { title: '网络管理', icon: 'fa-solid fa-circle-nodes' },
                        component: createRouteLoader(() => import('@/views/docker/networks.vue'))
                    },
                    {
                        path: 'volumes',
                        name: 'docker-volumes',
                        meta: { title: '卷管理', icon: 'fa-solid fa-folder' },
                        component: createRouteLoader(() => import('@/views/docker/volumes.vue'))
                    }
                ]
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
 * 手机端「会话」内容铺满公共顶栏下的剩余空间：导航落地前先写好标记，主框架据此收起侧栏与内容留白，
 * 避免先按控制台布局绘制一帧再被覆盖（会看到一次闪动）。其余页面恒为 false。
 */
router.beforeResolve((to) => {
    setFullscreenPage(to.name === 'messages' && isMobileDevice())
})

router.afterEach((to) => {
    document.title = to.meta && to.meta.title
        ? `${to.meta.title} - 葫芦娃助手`
        : '葫芦娃助手'
    loadingBar.finish()
})

router.onError(() => {
    loadingBar.error()
})

export default router
