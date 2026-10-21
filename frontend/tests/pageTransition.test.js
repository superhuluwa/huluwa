import { describe, expect, it } from 'vitest'
import { getPageTransitionName, setPageTransitionName, transitionNameFor } from '@/composables/usePageTransition'

/** 构造只带 meta 的假路由 */
function route(chat) {
    return { meta: { chat } }
}

describe('页面转场方向', () => {
    it('从列表进入二级页使用前进方向（从右滑入）', () => {
        expect(transitionNameFor(route(false), route(true))).toBe('slide-forward')
    })

    it('从二级页返回列表使用返回方向（向右滑出）', () => {
        expect(transitionNameFor(route(true), route(false))).toBe('slide-back')
    })

    it('二级页之间、列表标签页之间互切不播放转场', () => {
        expect(transitionNameFor(route(true), route(true))).toBe('')
        expect(transitionNameFor(route(false), route(false))).toBe('')
        expect(transitionNameFor(undefined, route(false))).toBe('')
    })

    it('首次进入时没有来源路由，不播放转场', () => {
        expect(transitionNameFor(undefined, route(true))).toBe('')
        expect(transitionNameFor(route(null), route(false))).toBe('')
    })

    it('方向写入后可被主框架读取', () => {
        setPageTransitionName(transitionNameFor(route(false), route(true)))
        expect(getPageTransitionName()).toBe('slide-forward')
        setPageTransitionName(transitionNameFor(route(true), route(false)))
        expect(getPageTransitionName()).toBe('slide-back')
    })
})
