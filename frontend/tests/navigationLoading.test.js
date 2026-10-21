import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppShell, { TABS } from '@/components/main/app-shell.vue'
import DockerManagement from '@/views/docker/index.vue'
import { createRouteLoader, preloadRoute } from '@/utils/routePreload'
import http from '@/libs/http'

vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
vi.mock('@/libs/ws', () => ({ default: {} }))
vi.mock('@/libs/discrete', () => ({ message: {}, notification: {} }))

function deferred() {
    let resolve, reject
    const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
    return { promise, resolve, reject }
}

function shellVm(router, options = {}) {
    const vm = { ...AppShell.data(), ...options, $router: router }
    Object.defineProperty(vm, '$route', { get: () => router.currentRoute.value })
    for (const [name, method] of Object.entries(AppShell.methods)) vm[name] = method.bind(vm)
    return vm
}

async function pageRouter(loadImages, loadNetworks = () => Promise.resolve({ render: () => null })) {
    const router = createRouter({ history: createMemoryHistory(), routes: [
        { path: '/', name: 'functions', meta: { tab: 'functions' }, component: { render: () => null } },
        { path: '/images', name: 'images', meta: { title: '镜像管理', tab: 'system', chat: true }, component: createRouteLoader(loadImages) },
        { path: '/networks', name: 'networks', meta: { title: '网络管理', tab: 'system', chat: true }, component: createRouteLoader(loadNetworks) }
    ] })
    router.onError(() => {})
    await router.push('/')
    return router
}

beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('window', { matchMedia: () => ({ matches: false }), scrollTo: vi.fn() })
    http.get.mockResolvedValue([])
})

describe('路由代码预加载', () => {
    it('预加载与导航复用请求，失败后可以重试', async () => {
        const first = deferred()
        const page = { render: () => null }
        const importPage = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue(page)
        const load = createRouteLoader(importPage)
        const preloading = load()
        expect(load()).toBe(preloading)
        await Promise.resolve()
        expect(importPage).toHaveBeenCalledTimes(1)
        first.reject(new Error('下载失败'))
        await expect(preloading).rejects.toThrow('下载失败')
        await expect(load()).resolves.toBe(page)
        expect(importPage).toHaveBeenCalledTimes(2)
        await load()
        expect(importPage).toHaveBeenCalledTimes(2)
    })

    it('预加载不挂载页面，不请求业务接口，并供实际导航复用', async () => {
        const importPage = vi.fn().mockResolvedValue({ render: () => null })
        const router = await pageRouter(importPage)
        expect(await preloadRoute(router, 'images')).toBe(true)
        expect(router.currentRoute.value.name).toBe('functions')
        expect(http.get).not.toHaveBeenCalled()
        await router.push({ name: 'images' })
        expect(importPage).toHaveBeenCalledTimes(1)
    })

    it('预加载失败不打断当前页，实际导航可以重新下载', async () => {
        const importPage = vi.fn()
            .mockRejectedValueOnce(new Error('下载失败'))
            .mockResolvedValue({ render: () => null })
        const router = await pageRouter(importPage)
        expect(await preloadRoute(router, 'images')).toBe(false)
        expect(router.currentRoute.value.name).toBe('functions')
        await router.push({ name: 'images' })
        expect(router.currentRoute.value.name).toBe('images')
        expect(importPage).toHaveBeenCalledTimes(2)
    })
})

describe('底部五标签外壳', () => {
    it('标签固定为 功能 / 系统 / 下载 / 订阅 / 我的，与安卓 MainTab 一致', () => {
        expect(TABS.map((item) => item.title)).toEqual(['功能', '系统', '下载', '订阅', '我的'])
        // 只有「功能」「系统」是会话标签页，其余为独立页面
        expect(TABS.filter((item) => item.isSessionTab).map((item) => item.key)).toEqual(['functions', 'system'])
    })

    it('当前标签与整屏状态由路由 meta 决定', async () => {
        const router = await pageRouter(() => Promise.resolve({ render: () => null }))
        const vm = shellVm(router)
        expect(AppShell.computed.activeTab.call(vm)).toBe('functions')
        expect(AppShell.computed.chatOpen.call(vm)).toBe(false)
        await router.push({ name: 'images' })
        expect(AppShell.computed.activeTab.call(vm)).toBe('system')
        // 管理会话整屏接管：隐藏标题栏与底部导航
        expect(AppShell.computed.chatOpen.call(vm)).toBe(true)
    })

    it('会话未读角标按功能 / 系统分类汇总（系统本地会话不混入功能角标）', async () => {
        const router = await pageRouter(() => Promise.resolve({ render: () => null }))
        const vm = shellVm(router, {
            chatStore: {
                sessions: [
                    { id: 'movie', kind: 'movie', unread: 3 },
                    { id: 'system', kind: 'system', unread: 2 },
                    { id: 'local-frp-management', kind: 'frp', unread: 0 },
                    { id: 'local-check-logs', kind: 'check-logs', unread: 0 }
                ]
            }
        })
        expect(AppShell.computed.unreadByTab.call(vm)).toEqual({ functions: 5, system: 0 })
    })
})

describe('Docker 管理会话', () => {
    it('首次挂载同时拉取容器、汇总与环境，并按分类懒加载资源', async () => {
        http.get.mockImplementation((url) => {
            if (url === '/docker/containers') return Promise.resolve([{ Id: 'abc', State: 'running', Names: ['/测试容器'] }])
            if (url === '/docker/summary') return Promise.resolve({ total: 1, running: 1, images: 2 })
            if (url === '/docker/environment') return Promise.resolve({ daemonOnline: true, serverVersion: '27.0' })
            if (url === '/docker/images') return Promise.resolve([{ Id: 'img', RepoTags: ['nginx:latest'], Size: 1024 }])
            return Promise.resolve([])
        })
        http.post.mockResolvedValue({})
        const vm = { ...DockerManagement.data() }
        for (const [name, method] of Object.entries(DockerManagement.methods)) vm[name] = method.bind(vm)
        vm.loadUsage = vi.fn()
        await vm.loadContainers()
        expect(vm.containers.data).toHaveLength(1)
        expect(vm.overview.total).toBe(1)
        expect(vm.overview.running).toBe(1)
        expect(vm.containerName(vm.containers.data[0])).toBe('测试容器')
        // 镜像分类在切换时才请求
        expect(http.get.mock.calls.some(([url]) => url === '/docker/images')).toBe(false)
        vm.active = 'images'
        await vm.loadResources(false)
        expect(vm.resources.images.data[0].name).toBe('nginx:latest')
    })

    it('资源加载失败时保留错误提示，不伪造数据', async () => {
        http.get.mockRejectedValue(new Error('网络错误'))
        const vm = { ...DockerManagement.data() }
        for (const [name, method] of Object.entries(DockerManagement.methods)) vm[name] = method.bind(vm)
        vm.active = 'networks'
        await vm.loadResources(false)
        expect(vm.resources.networks.error).toContain('资源加载失败')
        expect(vm.resources.networks.data).toEqual([])
    })
})
