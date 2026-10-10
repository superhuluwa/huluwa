import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { loadingBar } from '@/libs/discrete'

const Main = () => import('@/components/main/main.vue')

const routes = [
    {
        path: '/login',
        name: 'login',
        meta: { title: '登录', hideInMenu: true },
        component: () => import('@/views/login/index.vue')
    },
    {
        path: '/',
        component: Main,
        redirect: '/dashboard',
        children: [
            {
                path: 'dashboard',
                name: 'dashboard',
                meta: { title: '首页', icon: 'fa-solid fa-gauge-high' },
                component: () => import('@/views/dashboard/index.vue')
            },
            {
                path: 'messages',
                name: 'messages',
                meta: { title: '消息中心', icon: 'fa-solid fa-bell' },
                component: () => import('@/views/message/index.vue')
            },
            {
                path: 'frp',
                name: 'frp',
                meta: { title: 'Frp管理', icon: 'fa-solid fa-network-wired' },
                component: () => import('@/views/frp/index.vue')
            },
            {
                path: 'push',
                name: 'push',
                meta: { title: '推送设置', icon: 'fa-solid fa-paper-plane' },
                component: () => import('@/views/push/index.vue')
            },
            {
                path: 'docker',
                name: 'docker',
                meta: { title: 'Docker管理', icon: 'fa-brands fa-docker' },
                component: () => import('@/views/docker/index.vue')
            }
        ]
    },
    {
        path: '/500',
        name: 'error_500',
        meta: { title: '500', hideInMenu: true },
        component: () => import('@/views/error-page/500.vue')
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'error_404',
        meta: { title: '404', hideInMenu: true },
        component: () => import('@/views/error-page/404.vue')
    }
]

const router = createRouter({
    routes,
    history: createWebHashHistory()
})

router.beforeEach(async (to) => {
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

router.afterEach((to) => {
    document.title = to.meta && to.meta.title
        ? `${to.meta.title} - 葫芦娃助手`
        : '葫芦娃助手'
    loadingBar.finish()
})

export default router