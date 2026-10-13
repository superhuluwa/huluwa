<template>
    <n-modal v-model:show="visible" preset="card" :title="dialogTitle"
        style="width: 520px; max-width: calc(100vw - 32px)" :mask-closable="false">
        <n-spin :show="loading">
            <p v-if="movie" class="movie-title">{{ movie.title }}</p>
            <n-form label-placement="top">
                <n-form-item :label="movie ? '本剧下载工具' : '默认下载工具'">
                    <n-select v-model:value="downloader" :options="toolOptions" :disabled="loading || saving" />
                </n-form-item>
                <n-form-item label="保存目录">
                    <n-input :value="savePath" readonly />
                </n-form-item>
            </n-form>
            <p class="tip">迅雷与 qBittorrent 都保存到 {{ saveRoot }}，按剧名创建文件夹。</p>
            <p v-if="movie" class="tip">本剧绑定所选下载工具，订阅列表手动下载与自动下载都使用该工具。</p>
            <p v-if="movie" class="tip">{{ editing ? '修改后，新下载与失败任务重试使用所选工具，已提交任务继续由原工具下载。' : '订阅后记录现有磁力作为基线，每小时检查更新，新剧集选择最高清晰度自动下载。' }}</p>
            <div class="connections">
                <n-button size="small" @click="qbDialog = true">qBittorrent 连接设置</n-button>
                <n-button size="small" @click="xunleiDialog = true">迅雷连接设置</n-button>
            </div>
        </n-spin>
        <template #footer>
            <div class="footer">
                <n-button :disabled="saving" @click="visible = false">取消</n-button>
                <n-button type="primary" :loading="saving" :disabled="loading || !loaded" @click="save">
                    {{ movie && !editing ? '确认订阅' : '保存' }}
                </n-button>
            </div>
        </template>
    </n-modal>
    <QbSettingsDialog v-model:show="qbDialog" />
    <XunleiSettingsDialog v-model:show="xunleiDialog" />
</template>

<script>
import {
    GetMovieDownloadSettings, SaveMovieDownloadSettings, GetMovieSubscriptionStatus,
    UpdateMovieSubscriptionSettings, SubscribeMovie
} from '@/api/chat'
import QbSettingsDialog from './QbSettingsDialog.vue'
import XunleiSettingsDialog from './XunleiSettingsDialog.vue'

export default {
    name: 'MovieSubscriptionDialog',
    components: { QbSettingsDialog, XunleiSettingsDialog },
    props: {
        show: { type: Boolean, default: false },
        movie: { type: Object, default: null },
        resubscribe: { type: Boolean, default: false }
    },
    emits: ['update:show', 'saved'],
    data() {
        return {
            downloader: 'qb',
            saveRoot: '/vol1/1000/downloads',
            savePath: '/vol1/1000/downloads',
            subscription: null,
            loading: false,
            loaded: false,
            saving: false,
            qbDialog: false,
            xunleiDialog: false,
            toolOptions: [{ label: '迅雷', value: 'xunlei' }, { label: 'qBittorrent', value: 'qb' }]
        }
    },
    computed: {
        visible: {
            get() { return this.show },
            set(value) { this.$emit('update:show', value) }
        },
        editing() { return !!this.subscription && !this.resubscribe },
        dialogTitle() { return !this.movie ? '电影港默认下载设置' : this.editing ? '订阅设置' : '选择订阅下载工具' }
    },
    watch: {
        show(next) { if (next) this.load() }
    },
    methods: {
        async load() {
            this.loaded = false
            this.loading = true
            this.subscription = null
            try {
                const [settings, status] = await Promise.all([
                    GetMovieDownloadSettings(),
                    this.movie ? GetMovieSubscriptionStatus(this.movie.url) : Promise.resolve(null)
                ])
                this.subscription = status?.subscription || null
                this.downloader = this.subscription?.downloader || settings.downloader || 'qb'
                this.saveRoot = settings.saveRoot
                const title = String(this.subscription?.title || this.movie?.title || '').trim()
                const clean = title.replace(/[\\/:*?"<>|\x00-\x1f\x7f-\x9f]/g, '_').trim()
                const folder = !clean ? '未命名' : ['.', '..'].includes(clean) ? '_' : clean
                this.savePath = this.movie ? this.saveRoot + '/' + folder : this.saveRoot
                this.loaded = true
            } catch (error) {
                // 请求拦截器已提示错误，加载成功前禁止保存。
            } finally {
                this.loading = false
            }
        },
        async save() {
            if (!this.loaded || this.saving) return
            this.saving = true
            try {
                let result
                if (!this.movie) {
                    result = await SaveMovieDownloadSettings(this.downloader)
                } else if (this.editing) {
                    const subscription = await UpdateMovieSubscriptionSettings(this.subscription.id, this.downloader)
                    result = { subscription }
                } else {
                    result = await SubscribeMovie({ ...this.movie, downloader: this.downloader })
                }
                this.$message?.success(result.message || (this.movie ? '订阅设置已保存' : '默认下载设置已保存'))
                this.$emit('saved', result)
                this.visible = false
            } catch (error) {
                // 请求拦截器已提示。
            } finally {
                this.saving = false
            }
        }
    }
}
</script>

<style scoped>
.movie-title { margin-top: 0; font-weight: 600; overflow-wrap: anywhere; }
.tip { color: var(--text-3); font-size: 12px; overflow-wrap: anywhere; }
.connections { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 16px; }
.footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
