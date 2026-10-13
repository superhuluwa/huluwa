<template>
    <div>
        <div class="head"><span>电影天堂离线追更</span><n-button size="small" :loading="loading" @click="load">刷新</n-button></div>
        <n-spin :show="loading">
            <p v-if="!rows.length" class="empty">到“消息中心 → 电影天堂”搜索剧名，点击“订阅离线追更”。</p>
            <div v-for="item in rows" :key="item.id" class="card">
                <div class="title">{{ item.title }} <span>第 {{ item.season }} 季 · {{ item.enabled ? '追更中' : '已暂停' }}</span></div>
                <div class="meta">{{ item.sourceName }} · 每 {{ item.intervalMinutes }} 分钟检查 · 从第 {{ item.fromEpisode }} 集开始 · 已记录 {{ item.episodeCount }} 集</div>
                <div class="meta" v-if="item.lastCheckAt">上次检查 {{ item.lastCheckAt }}<span v-if="item.enabled"> · 下次 {{ item.nextCheckAt }}</span></div>
                <div v-if="item.lastError" class="error">{{ item.lastError }}</div>
                <div class="actions">
                    <n-button size="tiny" @click="edit(item)">订阅设置</n-button>
                    <n-button size="tiny" :disabled="!item.enabled" :loading="checking === item.id" @click="check(item)">立即检查</n-button>
                    <n-button size="tiny" @click="toggle(item)">{{ item.enabled ? '暂停追更' : '恢复追更' }}</n-button>
                </div>
            </div>
        </n-spin>
        <p class="tip">暂停后停止检查和领取该订阅的排队任务，正在下载的一集会继续完成；本地文件始终保留。</p>
        <HlsResourceDialog v-model:show="dialog" :movie="selected" @saved="load" />
    </div>
</template>

<script>
import { NButton, NSpin } from 'naive-ui'
import { CheckHlsSubscription, EnableHlsSubscription, GetHlsSubscriptions } from '@/api/hls'
import HlsResourceDialog from '@/views/message/components/HlsResourceDialog.vue'
export default {
    components: { NButton, NSpin, HlsResourceDialog },
    data() { return { rows: [], loading: false, checking: 0, dialog: false, selected: null } },
    mounted() { this.load() },
    methods: {
        async load() {
            this.loading = true
            try { this.rows = await GetHlsSubscriptions() } catch (error) { /* 拦截器已提示 */ }
            finally { this.loading = false }
        },
        edit(item) { this.selected = item; this.dialog = true },
        async check(item) {
            this.checking = item.id
            try { const result = await CheckHlsSubscription(item.id); window.$message?.success(result.message) }
            catch (error) { /* 拦截器已提示 */ } finally { this.checking = 0; await this.load() }
        },
        async toggle(item) {
            try { await EnableHlsSubscription(item.id, !item.enabled); await this.load() }
            catch (error) { /* 拦截器已提示 */ }
        }
    }
}
</script>

<style scoped>
.head, .actions { display: flex; align-items: center; gap: 8px; }
.head { margin-bottom: 16px; }.head .n-button { margin-left: auto; }
.card { border: 1px solid var(--line); border-radius: 12px; padding: 14px; margin-bottom: 12px; }
.title { font-weight: 600; }.title span, .meta, .tip, .empty { font-size: 12px; color: var(--text-3); }
.meta { margin-top: 8px; }.actions { margin-top: 12px; flex-wrap: wrap; }
.error { color: var(--danger); font-size: 12px; margin-top: 8px; }
</style>
