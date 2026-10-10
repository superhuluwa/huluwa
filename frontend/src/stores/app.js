import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
    state: () => ({
        collapsed: false,
        tagNavList: [],
        breadCrumb: []
    }),
    actions: {
        toggleCollapsed() {
            this.collapsed = !this.collapsed
        },
        setBreadCrumb(items) {
            this.breadCrumb = items
        }
    }
})