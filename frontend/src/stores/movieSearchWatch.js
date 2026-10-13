import { defineStore } from 'pinia'
import { GetMovieSearchWatches, StartMovieSearchWatch, CancelMovieSearchWatch } from '@/api/chat'

export function movieSearchWatchKey(keyword) {
    return String(keyword || '').trim().toLowerCase()
}

/** 同一关键字的所有气泡共用监听状态，以版本号阻止旧响应覆盖新状态。 */
export const useMovieSearchWatchStore = defineStore('movie-search-watch', {
    state: () => ({ tasks: {}, loaded: false, loading: false, error: '', busy: {} }),
    actions: {
        apply(watch) {
            if (!watch?.keyword) return
            const key = movieSearchWatchKey(watch.keyword)
            const current = this.tasks[key]
            if (current && current.version > watch.version) return
            this.tasks = { ...this.tasks, [key]: watch }
        },
        async refresh() {
            if (this.loading) return
            this.loading = true
            this.error = ''
            try {
                const rows = await GetMovieSearchWatches()
                if (!Array.isArray(rows)) throw new Error('监听状态返回格式异常')
                rows.forEach(watch => this.apply(watch))
                this.loaded = true
            } catch (e) {
                this.error = e.message || '读取监听状态失败'
                throw e
            } finally {
                this.loading = false
            }
        },
        async ensureLoaded() {
            if (!this.loaded) await this.refresh()
        },
        async start(keyword) {
            const key = movieSearchWatchKey(keyword)
            if (this.busy[key]) return
            this.busy = { ...this.busy, [key]: true }
            try {
                const watch = await StartMovieSearchWatch(keyword)
                this.apply(watch)
                return watch
            } finally {
                this.busy = { ...this.busy, [key]: false }
            }
        },
        async cancel(keyword) {
            const key = movieSearchWatchKey(keyword)
            const task = this.tasks[key]
            if (!task || task.status !== 'watching' || this.busy[key]) return
            this.busy = { ...this.busy, [key]: true }
            try {
                const watch = await CancelMovieSearchWatch(task.id)
                this.apply(watch)
                return watch
            } finally {
                this.busy = { ...this.busy, [key]: false }
            }
        }
    }
})
