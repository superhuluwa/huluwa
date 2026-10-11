import { defineStore } from 'pinia'

/**
 * 左侧菜单折叠态持久化：刷新后保持用户上次的展开/收起选择（交互习惯对齐 quantum 侧栏）。
 */
const COLLAPSE_KEY = 'huluwa.collapsed'

function readFlag(key, fallback) {
    try {
        const raw = localStorage.getItem(key)
        return raw === null ? fallback : raw === '1'
    } catch (e) {
        return fallback
    }
}

function writeFlag(key, value) {
    try {
        localStorage.setItem(key, value ? '1' : '0')
    } catch (e) {
        // 存储不可用（隐私模式）时仅内存生效
    }
}

export const useAppStore = defineStore('app', {
    state: () => ({
        collapsed: readFlag(COLLAPSE_KEY, false),
        tagNavList: [],
        breadCrumb: []
    }),
    actions: {
        toggleCollapsed() {
            this.collapsed = !this.collapsed
            writeFlag(COLLAPSE_KEY, this.collapsed)
        },
        setCollapsed(value) {
            this.collapsed = !!value
            writeFlag(COLLAPSE_KEY, this.collapsed)
        },
        setBreadCrumb(items) {
            this.breadCrumb = items
        }
    }
})
