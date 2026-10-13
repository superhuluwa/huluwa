<template>
    <div class="search-watch-actions">
        <div class="watch-controls">
            <button class="watch-btn" type="button" :class="{ listening: watching }"
                :disabled="watching || busy || (watches.loading && !watches.loaded)" @click="start">
                <i :class="busy ? 'fa-solid fa-spinner fa-spin' : (watching ? 'fa-solid fa-bell' : 'fa-regular fa-bell')"></i>
                {{ busy ? '处理中…' : (watching ? '监听中' : (found ? '重新监听' : '监听')) }}
            </button>
            <button v-if="watching" class="watch-link" type="button" :disabled="busy" @click="cancel">取消监听</button>
            <span class="watch-tip">{{ tip }}</span>
        </div>
        <span v-if="watching && task.nextCheckAt" class="watch-detail">下次检查：{{ nextCheckTime }}</span>
        <span v-if="watching && task.checkError" class="watch-detail warning" :title="task.checkError">
            上次搜索失败，将按计划重试
        </span>
        <span v-if="watches.error" class="watch-detail warning">
            监听状态读取失败
            <button class="watch-link" type="button" :disabled="watches.loading" @click="refresh">重新读取</button>
        </span>
    </div>
</template>

<script>
import { useMovieSearchWatchStore, movieSearchWatchKey } from '@/stores/movieSearchWatch'
import { formatFullTime } from '@/utils/chatFormat'

export default {
    name: 'MovieSearchWatchAction',
    props: { keyword: { type: String, required: true } },
    setup() {
        return { watches: useMovieSearchWatchStore() }
    },
    computed: {
        task() {
            return this.watches.tasks[movieSearchWatchKey(this.keyword)] || null
        },
        watching() {
            return this.task?.status === 'watching'
        },
        found() {
            return this.task?.status === 'found'
        },
        busy() {
            return !!this.watches.busy[movieSearchWatchKey(this.keyword)]
        },
        intervalMinutes() {
            return this.task?.intervalMinutes || 30
        },
        nextCheckTime() {
            return formatFullTime(this.task?.nextCheckAt)
        },
        tip() {
            if (this.found) return '已找到 ' + this.task.resultCount + ' 条资源，本次监听已结束'
            if (this.watching) return '每 ' + this.intervalMinutes + ' 分钟后台搜索，找到后发送系统通知'
            return '默认每 ' + this.intervalMinutes + ' 分钟检查一次，找到后通知'
        }
    },
    mounted() {
        this.watches.ensureLoaded().catch(() => {})
    },
    methods: {
        async start() {
            if (this.watching || this.busy) return
            try {
                const task = await this.watches.start(this.keyword)
                if (task?.status === 'watching') {
                    window.$message?.success('已开启监听，每 ' + task.intervalMinutes + ' 分钟检查一次')
                } else if (task?.status === 'found') {
                    window.$message?.success('该监听已找到资源，请查看系统通知')
                }
            } catch (e) {
                window.$message?.error(e.message || '开启监听失败')
            }
        },
        async cancel() {
            if (!this.watching || this.busy) return
            try {
                const task = await this.watches.cancel(this.keyword)
                if (task?.status === 'cancelled') {
                    window.$message?.success('已取消监听')
                } else if (task?.status === 'found') {
                    window.$message?.success('该监听已找到资源，请查看系统通知')
                }
            } catch (e) {
                window.$message?.error(e.message || '取消监听失败')
            }
        },
        async refresh() {
            try {
                await this.watches.refresh()
            } catch (e) {
                window.$message?.error(e.message || '读取监听状态失败')
            }
        }
    }
}
</script>

<style scoped>
.search-watch-actions {
    margin-top: 10px;
    padding-top: 9px;
    border-top: 1px solid var(--line);
    display: flex;
    flex-direction: column;
    gap: 6px;
}
.watch-controls {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
}
.watch-btn {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 11px;
    border: 1px solid var(--line);
    border-radius: 7px;
    background: var(--accent-weak);
    color: var(--accent);
    font-size: 12px;
    cursor: pointer;
}
.watch-btn.listening {
    color: var(--ok);
}
.watch-btn:disabled,
.watch-link:disabled {
    cursor: default;
    opacity: 0.7;
}
.watch-link {
    padding: 2px 0;
    border: 0;
    background: transparent;
    color: var(--accent);
    font-size: 12px;
    cursor: pointer;
}
.watch-tip,
.watch-detail {
    color: var(--text-2);
    font-size: 12px;
    line-height: 1.5;
    overflow-wrap: anywhere;
}
.watch-detail.warning {
    color: var(--warn);
}
</style>
