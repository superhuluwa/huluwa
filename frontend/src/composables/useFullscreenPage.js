import { ref } from 'vue'

/**
 * 全屏页面标记：进入会话 / 管理会话时整屏展示（隐藏标题栏与底部导航）。
 *
 * 与旧实现相同，保留模块级 ref：路由守卫在导航落地前写入，主框架据此收起外壳，
 * 避免先按带标签栏的布局绘制一帧再被覆盖（会看到一次闪动）。
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

/** 旧组件直接调用 `useFullscreenPage()` 当 ref 用；这里仍返回只读 ref，写入统一走 setFullscreenPage */
export const useFullscreen = useFullscreenPage

/**
 * 兼容旧页面（如历史消息中心）的「打开控制台菜单」通道。
 * 新外壳使用底部标签导航，不再需要抽屉；保留空实现避免旧代码报错。
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
