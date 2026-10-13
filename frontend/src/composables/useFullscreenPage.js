import { ref } from 'vue'

/**
 * 会话内容铺满可用空间的全局标记。
 *
 * 手机端会话页在公共控制台顶栏下铺满剩余空间（默认隐藏侧栏、无外层留白），
 * 该状态由路由守卫在导航前写入，主框架保留公共顶栏，页面本身负责内容区全高布局。
 * 之所以放在模块级 ref 而不是 store：只有守卫与主框架两个使用方，且不参与持久化。
 */
const fullscreen = ref(false)

export function isFullscreenPage() {
    return fullscreen.value
}

export function setFullscreenPage(value) {
    fullscreen.value = !!value
}

export function useFullscreenPage() {
    return fullscreen
}

/**
 * 全屏沉浸页面打开「控制台菜单」的通道。
 *
 * 手机端会话页铺满整屏、主框架侧栏不渲染，但用户仍需要能切到 Docker / 首页等模块。
 * 主框架挂载时注册回调，页面里的菜单按钮调用 requestShellDrawer() 即可拉出同款抽屉，
 * 不必为全屏页面复制一套导航。
 */
let shellDrawerHandler = null

export function onShellDrawerRequest(handler) {
    shellDrawerHandler = handler
}

export function requestShellDrawer() {
    if (typeof shellDrawerHandler === 'function') {
        shellDrawerHandler()
    }
}
