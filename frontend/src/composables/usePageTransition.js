import { ref } from 'vue'

/**
 * 页面转场方向：主标签页（列表）与全屏二级页（会话 / 管理会话 / 服务概览等）之间切换时使用。
 *
 * - 'slide-forward'：从列表进入二级页，新页从右滑入、列表页向左让出一段（微信 push 的视差感）；
 * - 'slide-back'   ：从二级页返回列表，二级页向右滑出、列表页从左侧归位。
 *
 * 路由守卫在导航落地前写入，主框架据此选择 <Transition> 的 name。
 * 方向只由「前一条 / 目标路由是否是全屏页」决定，不依赖浏览器前进后退，
 * 这样页面内的返回键（router.push 回列表）也能得到正确的反向动画。
 */
const direction = ref('slide-forward')

export function getPageTransitionName() {
    return direction.value
}

export function setPageTransitionName(name) {
    direction.value = typeof name === 'string' ? name : ''
}

/** 根据前后两条路由判断转场方向；不是「列表 ↔ 二级页」的切换返回空串（不播放转场） */
export function transitionNameFor(from, to) {
    // 首次进入 / 刷新落地时没有来源路由：直接显示，不播放滑入
    if (!from) {
        return ''
    }
    const wasChat = Boolean(from.meta && from.meta.chat)
    const willChat = Boolean(to && to.meta && to.meta.chat)
    if (willChat && !wasChat) {
        return 'slide-forward'
    }
    if (!willChat && wasChat) {
        return 'slide-back'
    }
    return ''
}

export function usePageTransition() {
    return direction
}
