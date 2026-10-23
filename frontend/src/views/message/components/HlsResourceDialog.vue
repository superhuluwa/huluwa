<template>
    <n-modal v-model:show="visible" preset="card" :title="mode === 'download' ? '下载离线剧集' : '订阅离线追更'"
        style="width: 580px; max-width: calc(100vw - 32px)" :mask-closable="false">
        <n-spin :show="loading">
            <n-alert v-if="settings && (!settings.toolsReady || !settings.mediaWritable)" type="warning" style="margin-bottom: 16px">
                {{ !settings.toolsReady ? '请先更新包含下载工具的 Docker 镜像。' : '媒体目录不可写，请检查挂载和目录权限。' }}
            </n-alert>
            <n-form label-placement="top">
                <n-form-item label="标准剧名"><n-input v-model:value="form.title" placeholder="用于飞牛影视识别，不填写更新至第几集" /></n-form-item>
                <div class="pair">
                    <n-form-item label="年份"><n-input v-model:value="form.year" placeholder="可留空" /></n-form-item>
                    <n-form-item label="季号"><n-input-number v-model:value="form.season" :min="1" :max="999" :disabled="editing" /></n-form-item>
                </div>
                <n-form-item label="下载线路"><n-select v-model:value="form.sourceName" :options="sourceOptions" placeholder="选择 m3u8 线路" /></n-form-item>
                <div v-if="mode === 'subscribe'" class="pair">
                    <n-form-item label="从第几集开始"><n-input-number v-model:value="form.fromEpisode" :min="1" :max="9999" /></n-form-item>
                    <n-form-item label="检查间隔（分钟）"><n-input-number v-model:value="form.intervalMinutes" :min="15" :max="10080" /></n-form-item>
                </div>
                <n-form-item v-else label="集号（需与资源编号一致）"><n-input-number v-model:value="form.episode" :min="1" :max="9999" placeholder="请确认集号" /></n-form-item>
                <n-form-item v-if="mode === 'subscribe' && !editing" label="首次订阅">
                    <n-select v-model:value="form.initialMode" :options="initialOptions" />
                </n-form-item>
                <details>
                    <summary>来源与请求头设置</summary>
                    <n-form-item label="采集接口"><n-input v-model:value="form.apiUrl" :disabled="editing" /></n-form-item>
                    <n-form-item label="作品 ID"><n-input v-model:value="form.resId" :disabled="editing || !!movie?.id" /></n-form-item>
                    <n-button size="small" :loading="loading" @click="loadDetail">重新加载线路</n-button>
                    <n-form-item label="来源页面（Referer）"><n-input v-model:value="form.referer" placeholder="需要时填写来源页面地址" /></n-form-item>
                    <n-form-item label="客户端标识（User-Agent）"><n-input v-model:value="form.userAgent" placeholder="留空使用默认值" /></n-form-item>
                    <n-form-item :label="subscription?.cookieConfigured ? 'Cookie（已保存，留空保留）' : 'Cookie（可选）'">
                        <n-input v-model:value="form.cookie" type="password" show-password-on="click" />
                    </n-form-item>
                    <n-checkbox v-if="editing && subscription.cookieConfigured" v-model:checked="form.clearCookie">清除已保存 Cookie</n-checkbox>
                </details>
            </n-form>
            <p class="tip">归档示例：{{ samplePath }}</p>
            <p class="tip">只下载可识别集号的正片。预告和花絮自动跳过；无法确认集号时请先核实。文件直接放在下载根目录下的剧名文件夹，可将该文件夹加入飞牛影视。</p>
            <p v-if="editing" class="tip">设置影响后续下载；已完成文件保留原名。首次跳过的剧集可在“我的下载”中补下载。</p>
        </n-spin>
        <template #footer>
            <div class="footer">
                <n-button :disabled="saving" @click="visible = false">取消</n-button>
                <n-button type="primary" :loading="saving" :disabled="loading || !loaded || !settings?.toolsReady || !settings?.mediaWritable" @click="save">
                    {{ mode === 'download' ? '加入下载队列' : editing ? '保存订阅' : '确认订阅' }}
                </n-button>
            </div>
        </template>
    </n-modal>
</template>

<script>
import { NAlert, NButton, NCheckbox, NForm, NFormItem, NInput, NInputNumber, NModal, NSelect, NSpin } from 'naive-ui'
import { DownloadHls, GetHlsDetail, GetHlsSettings, GetHlsSubscriptions, SubscribeHls } from '@/api/hls'

const API = 'https://dyttzyw.com/api.php/provide/vod/'
const blank = () => ({ apiUrl: API, resId: '', title: '', year: '', season: 1, sourceName: '', intervalMinutes: 60,
    fromEpisode: 1, initialMode: 'future', episode: null, referer: '', userAgent: '', cookie: '', clearCookie: false })

export default {
    name: 'HlsResourceDialog',
    components: { NAlert, NButton, NCheckbox, NForm, NFormItem, NInput, NInputNumber, NModal, NSelect, NSpin },
    props: { show: Boolean, movie: { type: Object, default: null }, mode: { type: String, default: 'subscribe' },
        play: { type: Object, default: null }, sourceName: { type: String, default: '' } },
    emits: ['update:show', 'saved'],
    data() { return { form: blank(), settings: null, subscription: null, sources: [], loading: false, loaded: false, saving: false,
        initialOptions: [{ label: '只下载后续新增剧集', value: 'future' }, { label: '下载当前已发布剧集，并追后续更新', value: 'all' }] } },
    computed: {
        visible: { get() { return this.show }, set(value) { this.$emit('update:show', value) } },
        editing() { return !!this.subscription && this.mode === 'subscribe' },
        sourceOptions() { return this.sources.filter(source => source.items?.some(play => /\.m3u8(?:[?#]|$)/i.test(play.url)))
            .map(source => ({ label: source.name + '（' + source.total + ' 集）', value: source.name })) },
        samplePath() {
            const title = String(this.form.title || '剧名').replace(/[\\/:*?"<>|]/g, '_')
            const season = String(this.form.season || 1).padStart(2, '0')
            const episode = String(this.mode === 'download' ? this.form.episode || 1 : this.form.fromEpisode || 1).padStart(2, '0')
            return `${this.settings?.mediaRoot || '/vol1/1000/downloads'}/${title}/${title} S${season}E${episode}.${this.settings?.format || 'mp4'}`
        }
    },
    watch: { show(next) { if (next) this.load() } },
    methods: {
        async load() {
            this.loaded = false; this.loading = true; this.sources = []; this.settings = null; this.subscription = null
            const movie = this.movie || {}
            this.form = { ...blank(), apiUrl: movie.apiUrl || API, resId: String(movie.resId || movie.id || ''),
                title: movie.title || '', year: movie.year || '', season: movie.season || 1,
                sourceName: this.sourceName || movie.sourceName || '', fromEpisode: movie.fromEpisode || 1,
                intervalMinutes: movie.intervalMinutes || 60, referer: movie.referer || '', userAgent: movie.userAgent || '' }
            const match = String(this.play?.title || '').match(/(?:第\s*|S\d+E|(?:^|\s)EP?)(\d+)(?:集|话|期)?/i)
                || String(this.play?.title || '').match(/^(\d+)$/)
            this.form.episode = match ? Number(match[1]) : null
            try {
                const [settings, detail, subscriptions] = await Promise.all([GetHlsSettings(), GetHlsDetail(this.form.resId, this.form.apiUrl),
                    GetHlsSubscriptions()])
                this.subscription = subscriptions.find(item => item.resId === this.form.resId
                    && item.apiUrl.replace(/\/+$/, '') === this.form.apiUrl.replace(/\/+$/, '')
                    && (!movie.resId || item.season === movie.season)) || null
                if (this.subscription) {
                    const saved = this.subscription
                    this.form = { ...this.form, title: saved.title, year: saved.year || '', season: saved.season,
                        sourceName: this.mode === 'download' ? this.sourceName || saved.sourceName : saved.sourceName,
                        fromEpisode: saved.fromEpisode, intervalMinutes: saved.intervalMinutes,
                        referer: saved.referer || '', userAgent: saved.userAgent || '' }
                }
                this.settings = settings; this.applyDetail(detail); this.loaded = true
            } catch (error) { /* 请求拦截器已提示 */ }
            finally { this.loading = false }
        },
        applyDetail(detail) {
            this.sources = detail.playSources || []
            if (!this.form.sourceName) this.form.sourceName = this.sourceOptions.find(option => /m3u8/i.test(option.value))?.value || this.sourceOptions[0]?.value || ''
        },
        async loadDetail() {
            this.loading = true; this.loaded = false
            try {
                const [settings, detail] = await Promise.all([GetHlsSettings(), GetHlsDetail(this.form.resId, this.form.apiUrl)])
                this.settings = settings; this.applyDetail(detail); this.loaded = true
            } catch (error) { /* 请求拦截器已提示 */ }
            finally { this.loading = false }
        },
        async save() {
            if (this.saving || !this.loaded) return
            if (!this.form.title.trim() || !this.form.sourceName || (this.mode === 'download' && !this.form.episode)) {
                window.$message?.warning('请确认剧名、m3u8 线路和集号'); return
            }
            this.saving = true
            try {
                const { initialMode, ...body } = this.form
                const result = this.mode === 'download'
                    ? await DownloadHls({ ...body, url: this.play?.url, episodeTitle: this.play?.title || '' })
                    : await SubscribeHls({ ...body, downloadExisting: initialMode === 'all' })
                window.$message?.success(result.message || (result.status === 'completed' ? '该剧集已下载完成' : '下载任务已记录'))
                this.$emit('saved', result); this.visible = false
            } catch (error) { /* 请求拦截器已提示 */ }
            finally { this.saving = false }
        }
    }
}
</script>

<style scoped>
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.pair :deep(.n-input-number) { width: 100%; }
.tip { font-size: 12px; color: var(--text-3); overflow-wrap: anywhere; }
.footer { display: flex; justify-content: flex-end; gap: 8px; }
details { margin-bottom: 12px; }
summary { cursor: pointer; color: var(--text-2); margin-bottom: 16px; }
</style>
