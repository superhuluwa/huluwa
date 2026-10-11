import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 统一的移动端断点（与主框架抽屉侧栏保持一致）：
 * 视口宽度 <= 900px 视为手机/窄屏，页面据此切换单列布局。
 * 桌面端（> 900px）返回值恒为 false，布局与交互保持原样。
 */
export const MOBILE_BREAKPOINT = 900

/**
 * 当前设备是否按「手机」对待。
 * 供路由守卫（决定是否走全屏沉浸布局）等非组件场景使用：
 * 触屏 + 窄视口 + 移动端 UA 才算手机，避免桌面端把窗口拖窄后误判。
 */
export function isMobileDevice(width = window.innerWidth) {
    if (width > MOBILE_BREAKPOINT) {
        return false
    }
    const touch = (navigator.maxTouchPoints || 0) > 0 || /Android|iPhone|iPad|iPod/i.test(navigator.userAgent)
    return touch && /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
}

export function useIsMobile(breakpoint = MOBILE_BREAKPOINT) {
    const isMobile = ref(false)
    const query = `(max-width: ${breakpoint}px)`
    let mediaQuery = null

    const sync = (event) => {
        isMobile.value = event.matches
    }

    onMounted(() => {
        mediaQuery = window.matchMedia(query)
        isMobile.value = mediaQuery.matches
        mediaQuery.addEventListener('change', sync)
    })

    onBeforeUnmount(() => {
        mediaQuery?.removeEventListener('change', sync)
        mediaQuery = null
    })

    return isMobile
}
