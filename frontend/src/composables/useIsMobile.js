import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 统一的移动端断点（与主框架抽屉侧栏保持一致）：
 * 视口宽度 <= 900px 视为手机/窄屏，页面据此切换单列布局。
 * 桌面端（> 900px）返回值恒为 false，布局与交互保持原样。
 */
export const MOBILE_BREAKPOINT = 900

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
