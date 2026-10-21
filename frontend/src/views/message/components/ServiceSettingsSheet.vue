<template>
    <n-modal v-model:show="visible" preset="card" :title="title" style="width: 520px" :mask-closable="false">
        <div v-if="loading" class="settings-loading">
            <n-spin size="small" /> 正在读取配置…
        </div>
        <p v-else-if="error" class="settings-error">{{ error }}</p>

        <n-form v-else :label-placement="'top'" :model="values">
            <!-- 电影港：默认下载器 -->
            <template v-if="kind === 'movie'">
                <p class="settings-tip">此设置用于新建电影港订阅和手动磁力下载；已有订阅保留各自的下载器。</p>
                <div class="chips">
                    <button v-for="tool in downloadTools" :key="tool.value" type="button" class="chip"
                        :class="{ active: values.downloader === tool.value }" @click="values.downloader = tool.value">
                        {{ tool.label }}
                    </button>
                </div>
                <div class="field-line">
                    <span class="field-label">服务器下载根目录</span>
                    <span class="field-value mono">{{ raw.saveRoot || '—' }}</span>
                </div>
            </template>

            <!-- 离线剧集（电影天堂订阅 / HLS 下载）：线程、重试、超时、封装格式、限速 -->
            <template v-else-if="kind === 'dytt'">
                <n-form-item label="下载线程数">
                    <n-input v-model:value="values.threadCount" placeholder="1–32" />
                </n-form-item>
                <n-form-item label="最大重试次数">
                    <n-input v-model:value="values.maxAttempts" placeholder="1–10" />
                </n-form-item>
                <n-form-item label="任务超时（分钟）">
                    <n-input v-model:value="values.timeoutMinutes" placeholder="5–1440" />
                </n-form-item>
                <n-form-item label="封装格式">
                    <div class="chips">
                        <button v-for="item in ['mp4', 'mkv']" :key="item" type="button" class="chip"
                            :class="{ active: values.format === item }" @click="values.format = item">{{ item }}</button>
                    </div>
                </n-form-item>
                <n-form-item label="限速（留空表示不限速）">
                    <n-input v-model:value="values.maxSpeed" placeholder="例如：10M" />
                </n-form-item>
            </template>

            <!-- qBittorrent / 视频解析 / 迅雷：地址 + 账号密码（字段与安卓一致） -->
            <template v-else>
                <n-form-item v-if="kind === 'magnet'">
                    <template #label>使用账号密码认证</template>
                    <n-switch v-model:value="values.useAuth" />
                </n-form-item>
                <n-form-item v-for="field in fields" :key="field.key" :label="field.label">
                    <n-input v-model:value="values[field.key]"
                        :type="field.secret ? 'password' : 'text'" show-password-on="click"
                        :placeholder="secretHint(field)" />
                </n-form-item>
            </template>

            <p v-if="error" class="settings-error">{{ error }}</p>
            <n-button type="primary" block size="large" :loading="saving" @click="save">{{ submitText }}</n-button>
            <p class="settings-tip foot">{{ footTip }}</p>
        </n-form>
    </n-modal>
</template>

<script>
/**
 * 服务设置弹层：把安卓 ServiceSettings 的字段映射搬到网页端，
 * 由会话顶栏「更多 → 服务设置」按当前会话类型打开（magnet / video / xunlei / xunlei-win / movie / dytt）。
 * 密码类字段只写不读：查询只回传是否已保存，留空表示沿用服务器上的旧值。
 */
import { NButton, NForm, NFormItem, NInput, NModal, NSpin, NSwitch } from 'naive-ui'
import { GetQbSettings, SaveQbSettings } from '@/api/qbittorrent'
import { GetVideoSettings, SaveVideoSettings } from '@/api/video'
import { GetXunleiSettings, SaveXunleiSettings } from '@/api/xunlei'
import { GetXunleiWinSettings, SaveXunleiWinSettings } from '@/api/xunlei-win'
import { GetHlsSettings, SaveHlsSettings } from '@/api/hls'
import { GetMovieDownloadSettings, SaveMovieDownloadSettings } from '@/api/chat'

const TITLES = {
    magnet: 'qBittorrent 连接设置',
    video: '视频解析服务设置',
    xunlei: '迅雷【飞牛】连接设置',
    'xunlei-win': '迅雷【Win】连接设置',
    movie: '电影港下载设置',
    dytt: '离线下载设置'
}

/** 各服务字段（与安卓 SettingField 一一对应） */
const FIELDS = {
    magnet: [
        { key: 'url', label: '服务地址' },
        { key: 'username', label: '账号' },
        { key: 'password', label: '密码', secret: true },
        { key: 'savePath', label: '保存目录（可选）' }
    ],
    video: [
        { key: 'url', label: '解析服务地址' },
        { key: 'username', label: '账号（可选）' },
        { key: 'password', label: '密码（可选）', secret: true }
    ],
    xunlei: [
        { key: 'url', label: '飞牛 / 迅雷服务地址' },
        { key: 'fnosUsername', label: '飞牛账号' },
        { key: 'fnosPassword', label: '飞牛密码', secret: true },
        { key: 'twofaCode', label: '两步验证码（需要时填写）', secret: true },
        { key: 'token', label: '手动认证值（可选）', secret: true },
        { key: 'savePath', label: '保存目录（可选）' }
    ],
    'xunlei-win': [
        { key: 'url', label: 'xunlei-win-api 服务地址' },
        { key: 'savePath', label: 'Windows 保存目录（可选）' }
    ]
}

export default {
    name: 'ServiceSettingsSheet',
    components: { NButton, NForm, NFormItem, NInput, NModal, NSpin, NSwitch },
    props: {
        show: {
            type: Boolean,
            default: false
        },
        kind: {
            type: String,
            default: 'magnet'
        }
    },
    emits: ['update:show', 'saved'],
    data() {
        return {
            loading: false,
            saving: false,
            error: '',
            raw: {},
            values: {},
            downloadTools: [
                { label: 'qBittorrent', value: 'qb' },
                { label: '迅雷【飞牛】', value: 'xunlei' },
                { label: '迅雷【Win】', value: 'xunlei-win' }
            ]
        }
    },
    computed: {
        visible: {
            get() {
                return this.show
            },
            set(value) {
                this.$emit('update:show', value)
            }
        },
        title() {
            return TITLES[this.kind] || '服务设置'
        },
        fields() {
            return FIELDS[this.kind] || []
        },
        submitText() {
            return this.kind === 'movie' ? '保存设置'
                : this.kind === 'dytt' ? '保存下载设置' : '保存连接设置'
        },
        footTip() {
            if (this.kind === 'magnet') {
                return '密码字段留空表示沿用服务器已保存的密码。'
            }
            if (this.kind === 'dytt') {
                return '下载参数对之后新建的任务生效，进行中的任务保持原设置。'
            }
            if (this.kind === 'movie') {
                return '只影响新建订阅；已有订阅可在「订阅 → 订阅设置」中单独调整。'
            }
            return '带密码的字段留空表示沿用服务器已保存的值。'
        }
    },
    watch: {
        show(next) {
            if (next) {
                this.load()
            }
        }
    },
    methods: {
        secretHint(field) {
            return field.secret ? '留空沿用已保存的值' : ''
        },
        async load() {
            this.loading = true
            this.error = ''
            try {
                if (this.kind === 'movie') {
                    const data = await GetMovieDownloadSettings()
                    this.raw = data || {}
                    this.values = { downloader: (data && data.downloader) || 'qb' }
                    return
                }
                if (this.kind === 'dytt') {
                    const data = await GetHlsSettings()
                    const settings = (data && data.settings) || data || {}
                    this.raw = settings
                    this.values = {
                        threadCount: String(settings.threadCount ?? 8),
                        maxAttempts: String(settings.maxAttempts ?? 3),
                        timeoutMinutes: String(settings.timeoutMinutes ?? 60),
                        format: settings.format || 'mp4',
                        maxSpeed: settings.maxSpeed || ''
                    }
                    return
                }
                const loaders = {
                    magnet: GetQbSettings,
                    video: GetVideoSettings,
                    xunlei: GetXunleiSettings,
                    'xunlei-win': GetXunleiWinSettings
                }
                const data = await (loaders[this.kind] || GetQbSettings)()
                const settings = (data && data.settings) || data || {}
                this.raw = settings
                const next = {}
                for (const field of this.fields) {
                    next[field.key] = field.secret ? '' : String(settings[field.key] ?? '')
                }
                if (this.kind === 'magnet') {
                    next.useAuth = settings.configured ? settings.useAuth !== false : true
                }
                this.values = next
            } catch (error) {
                this.error = '配置读取失败，请稍后重试。'
            } finally {
                this.loading = false
            }
        },
        async save() {
            this.error = ''
            if (this.kind === 'movie') {
                this.saving = true
                try {
                    await SaveMovieDownloadSettings(this.values.downloader)
                    this.$message.success('已保存默认下载器')
                    this.visible = false
                    this.$emit('saved')
                } catch (error) {
                    // 拦截器已提示
                } finally {
                    this.saving = false
                }
                return
            }
            if (this.kind === 'dytt') {
                const threads = Number(this.values.threadCount)
                const attempts = Number(this.values.maxAttempts)
                const timeout = Number(this.values.timeoutMinutes)
                if (!(threads >= 1 && threads <= 32) || !(attempts >= 1 && attempts <= 10) || !(timeout >= 5 && timeout <= 1440)) {
                    this.error = '线程应为 1–32，重试应为 1–10，超时应为 5–1440 分钟'
                    return
                }
                this.saving = true
                try {
                    await SaveHlsSettings({
                        threadCount: threads,
                        maxAttempts: attempts,
                        timeoutMinutes: timeout,
                        format: this.values.format,
                        maxSpeed: String(this.values.maxSpeed || '').trim()
                    })
                    this.$message.success('下载设置已保存')
                    this.visible = false
                    this.$emit('saved')
                } catch (error) {
                    // 拦截器已提示
                } finally {
                    this.saving = false
                }
                return
            }
            if (!String(this.values.url || '').trim()) {
                this.error = '请填写服务地址'
                return
            }
            const payload = {}
            for (const field of this.fields) {
                payload[field.key] = field.secret ? String(this.values[field.key] || '') : String(this.values[field.key] || '').trim()
            }
            if (this.kind === 'magnet') {
                payload.useAuth = this.values.useAuth !== false
                if (!payload.useAuth) {
                    payload.username = ''
                    payload.password = ''
                }
            }
            this.saving = true
            try {
                const savers = {
                    magnet: SaveQbSettings,
                    video: SaveVideoSettings,
                    xunlei: SaveXunleiSettings,
                    'xunlei-win': SaveXunleiWinSettings
                }
                await (savers[this.kind] || SaveQbSettings)(payload)
                this.$message.success('连接设置已保存')
                this.visible = false
                this.$emit('saved')
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.saving = false
            }
        }
    }
}
</script>

<style scoped>
.settings-loading {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 18px 0;
    color: var(--muted);
    font-size: 13px;
}

.settings-error {
    padding: 9px 12px;
    margin-bottom: 10px;
    border-radius: 12px;
    background: #FBEEF0;
    color: var(--danger);
    font-size: 12.5px;
}

.settings-tip {
    margin: 0 0 12px;
    color: var(--muted);
    font-size: 12.5px;
    line-height: 1.6;
}

.settings-tip.foot {
    margin: 10px 0 0;
    text-align: center;
}

.chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}

.chip {
    padding: 7px 14px;
    border-radius: 999px;
    border: 1px solid var(--line);
    background: #fff;
    color: var(--muted);
    font-family: inherit;
    font-size: 12.5px;
    cursor: pointer;
}

.chip.active {
    border-color: var(--pine);
    background: var(--soft-green);
    color: var(--pine);
    font-weight: 600;
}

.field-line {
    display: flex;
    gap: 10px;
    font-size: 13px;
    margin-bottom: 14px;
}

.field-label {
    flex: none;
    width: 108px;
    color: var(--muted);
}

.field-value {
    min-width: 0;
    color: var(--ink);
    overflow-wrap: anywhere;
}
</style>
