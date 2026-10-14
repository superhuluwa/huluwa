import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, KeepAlive, nextTick, ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import Main from '@/components/main/main.vue'
import HeaderBar from '@/components/main/components/header-bar.vue'
import DockerIndex from '@/views/docker/index.vue'
import DockerImages from '@/views/docker/images.vue'
import DockerNetworks from '@/views/docker/networks.vue'
import DockerVolumes from '@/views/docker/volumes.vue'
import DockerContainers from '@/views/docker/containers.vue'
import { createRouteLoader, preloadRoute } from '@/utils/routePreload'
import http from '@/libs/http'

vi.mock('/huluwa-logo.svg', () => ({ default: '/huluwa-logo.svg' }))
vi.mock('@/libs/http', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/libs/ws', () => ({ default: {} }))
vi.mock('@/libs/discrete', () => ({ message: {}, notification: {} }))

function deferred() {
    let resolve, reject
    const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
    return { promise, resolve, reject }
}

function menuVm(router) {
    const vm = { ...Main.data(), $router: router, $message: { error: vi.fn() } }
    Object.defineProperty(vm, '$route', { get: () => router.currentRoute.value })
    for (const [name, method] of Object.entries(Main.methods)) vm[name] = method.bind(vm)
    return vm
}

async function menuRouter(loadImages, loadNetworks = () => Promise.resolve({ render: () => null })) {
    const router = createRouter({ history: createMemoryHistory(), routes: [
        { path: '/', name: 'dashboard', component: { render: () => null } },
        { path: '/images', name: 'images', meta: { title: '镜像管理' }, component: createRouteLoader(loadImages) },
        { path: '/networks', name: 'networks', meta: { title: '网络管理' }, component: createRouteLoader(loadNetworks) }
    ] })
    router.onError(() => {})
    await router.push('/')
    return router
}

// 内存渲染器验证真实 KeepAlive 生命周期，无需启动浏览器或构建产物。
const makeNode = (type, text = '') => ({ type, text, children: [], parent: null })
const renderer = createRenderer({
    createElement: makeNode, createText: text => makeNode('text', text), createComment: text => makeNode('comment', text),
    setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text },
    patchProp: () => {}, parentNode: node => node.parent,
    nextSibling: node => node.parent?.children[node.parent.children.indexOf(node) + 1] || null,
    insert(node, parent, anchor = null) {
        if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
        const index = anchor ? parent.children.indexOf(anchor) : -1
        parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
        node.parent = parent
    },
    remove(node) {
        if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
        node.parent = null
    }
})

beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('window', { matchMedia: () => ({ matches: false }) })
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
        const importPage = vi.fn().mockResolvedValue(DockerImages)
        const router = await menuRouter(importPage)
        expect(await preloadRoute(router, 'images')).toBe(true)
        expect(router.currentRoute.value.name).toBe('dashboard')
        expect(http.get).not.toHaveBeenCalled()
        await router.push({ name: 'images' })
        expect(importPage).toHaveBeenCalledTimes(1)
    })

    it('预加载失败不打断当前页，实际导航可以重新下载', async () => {
        const importPage = vi.fn().mockRejectedValueOnce(new Error('下载失败')).mockResolvedValue(DockerImages)
        const router = await menuRouter(importPage)
        expect(await preloadRoute(router, 'images')).toBe(false)
        expect(router.currentRoute.value.name).toBe('dashboard')
        await router.push({ name: 'images' })
        expect(router.currentRoute.value.name).toBe('images')
        expect(importPage).toHaveBeenCalledTimes(2)
    })
})

describe('菜单切换即时反馈', () => {
    it('慢页面加载完成前立即切换高亮、标题并收起抽屉，同一目标只跳转一次', async () => {
        const download = deferred()
        const router = await menuRouter(() => download.promise)
        const push = vi.spyOn(router, 'push')
        const vm = menuVm(router)
        vm.mobileDrawerOpen = true
        const navigation = vm.turnToPage('images')
        expect(Main.computed.activeMenu.call(vm)).toBe('images')
        expect(HeaderBar.computed.currentTitle.call({ pageTitle: vm.pendingPage.title })).toBe('镜像管理')
        expect(vm.mobileDrawerOpen).toBe(false)
        expect(router.currentRoute.value.name).toBe('dashboard')
        await vm.turnToPage('images')
        expect(push).toHaveBeenCalledTimes(1)
        download.resolve({ render: () => null })
        await navigation
        expect(vm.pendingPage).toBeNull()
        expect(router.currentRoute.value.name).toBe('images')
    })

    it('快速连续点击时，被取消的慢导航不能清掉新目标的加载提示', async () => {
        const images = deferred(), networks = deferred()
        const loadImages = vi.fn(() => images.promise)
        const router = await menuRouter(loadImages, () => networks.promise)
        const vm = menuVm(router)
        const first = vm.turnToPage('images')
        // 确保第一次导航已经开始下载，再点击下一项。
        await vi.waitFor(() => expect(loadImages).toHaveBeenCalledOnce())
        const second = vm.turnToPage('networks')
        images.resolve({ render: () => null })
        await first
        expect(vm.pendingPage.name).toBe('networks')
        networks.resolve({ render: () => null })
        await second
        expect(router.currentRoute.value.name).toBe('networks')
        expect(vm.pendingPage).toBeNull()
    })

    it('加载失败恢复原菜单并给出提示', async () => {
        const router = await menuRouter(() => Promise.reject(new Error('下载失败')))
        const vm = menuVm(router)
        await vm.turnToPage('images')
        expect(vm.pendingPage).toBeNull()
        expect(Main.computed.activeMenu.call(vm)).toBe('dashboard')
        expect(vm.$message.error).toHaveBeenCalledOnce()
    })

    it('远端新增巡检记录菜单兼容统一导航入口', async () => {
        const router = await menuRouter(() => Promise.resolve(DockerImages))
        router.addRoute({ path: '/check-logs', name: 'movie-check-logs', component: { render: () => null } })
        const vm = menuVm(router)
        vm.chatStore = { sessions: [] }
        const group = vm.messagesMenuOption({ name: 'messages', meta: { title: '消息中心' } })
        const entry = group.children.find(item => item.key === 'movie-check-logs')
        const link = entry.label()
        expect(link.type).toBe('a')
        expect(link.props.href).toContain('/check-logs')
        expect(vm.isMessagesGroup('movie-check-logs')).toBe(true)
    })

    it('普通文字点击只导航一次，同时保留组合键打开新标签页', async () => {
        const router = await menuRouter(() => Promise.resolve(DockerImages))
        const vm = menuVm(router)
        vm.turnToPage = vi.fn()
        const props = vm.menuLinkProps('images')
        const event = { button: 0, stopPropagation: vi.fn(), preventDefault: vi.fn() }
        props.onClick(event)
        expect(event.stopPropagation).toHaveBeenCalledOnce()
        expect(event.preventDefault).toHaveBeenCalledOnce()
        expect(vm.turnToPage).toHaveBeenCalledExactlyOnceWith('images')
        props.onClick({ ...event, ctrlKey: true, preventDefault: vi.fn() })
        expect(vm.turnToPage).toHaveBeenCalledTimes(1)
        expect(props.href).toContain('/images')
    })
})

describe.each([
    ['概览', DockerIndex, ['/docker/summary', '/docker/environment', '/docker/hosts']],
    ['镜像', DockerImages, ['/docker/images']],
    ['网络', DockerNetworks, ['/docker/networks']],
    ['卷', DockerVolumes, ['/docker/volumes']]
])('Docker %s 页面', (title, component, urls) => {
    it('首次挂载只请求一次，缓存返回时继续刷新', async () => {
        const visible = ref(true)
        const Page = { ...component, setup: () => ({ isMobile: false }), render: () => h('div') }
        const app = renderer.createApp({ render: () => h(KeepAlive, null, { default: () => visible.value ? h(Page) : null }) })
        app.mount(makeNode('root'))
        await nextTick()
        for (const url of urls) expect(http.get.mock.calls.filter(([value]) => value === url)).toHaveLength(1)
        visible.value = false
        await nextTick()
        visible.value = true
        await nextTick()
        for (const url of urls) expect(http.get.mock.calls.filter(([value]) => value === url)).toHaveLength(2)
        app.unmount()
    })
})

describe('容器列表与资源统计', () => {
    it('统计请求尚未完成时，容器列表已经结束加载', async () => {
        const stats = deferred()
        http.get.mockResolvedValue([{ Id: 'abc', State: 'running', Names: ['/测试容器'] }])
        http.post.mockReturnValue(stats.promise)
        const vm = { ...DockerContainers.data(), pageActive: true }
        vm.loadResourceUsage = DockerContainers.methods.loadResourceUsage.bind(vm)
        await DockerContainers.methods.loadContainers.call(vm)
        expect(vm.loading).toBe(false)
        expect(vm.containers).toHaveLength(1)
        expect(vm.resourceBusy).toBe(true)
        expect(http.post).toHaveBeenCalledWith('/docker/containers/stats', ['abc'], expect.any(Object))
        stats.resolve({})
        await vi.waitFor(() => expect(vm.resourceBusy).toBe(false))
    })
})
