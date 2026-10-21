<template>
    <ManagementShell kind="upgrade" title="在线升级" subtitle="App 内更新已发布的安卓安装包" :loading="loading"
        @refresh="load">
        <div class="mgmt-content">
            <section class="hero-panel">
                <div class="hero-head">
                    <i class="fa-solid fa-arrow-up-right-dots"></i>
                    <div class="hero-head-text">
                        <strong>在线升级</strong>
                        <span>{{ release ? `最新版本 v${release.versionName}` : '暂无可用安装包' }}</span>
                    </div>
                    <span class="hero-tag" :class="{ alert: !release }">{{ release ? '有新版本' : '已是最新' }}</span>
                </div>
                <div class="hero-metrics">
                    <div class="hero-metric"><strong>{{ release ? release.versionName : '—' }}</strong><span>最新版本</span></div>
                    <div class="hero-metric"><strong>{{ release ? release.versionCode : '—' }}</strong><span>版本代码</span></div>
                    <div class="hero-metric"><strong>{{ release ? formatBytes(release.apkSize) : '—' }}</strong><span>安装包大小</span></div>
                </div>
                <div class="hero-divider"></div>
                <div class="hero-foot">
                    {{ release ? `发布于 ${release.publishedAt || '—'}` : '服务器尚未发布带签名的 Release 安装包' }}
                </div>
            </section>

            <p v-if="error" class="notice-line"><i class="fa-solid fa-triangle-exclamation"></i>{{ error }}</p>

            <section v-if="release" class="panel">
                <div class="kv-row"><span class="kv-label">应用包名</span><span class="kv-value mono">{{ release.packageName }}</span></div>
                <div class="kv-row"><span class="kv-label">最低系统</span><span class="kv-value">Android {{ release.minSdk }}</span></div>
                <div class="kv-row"><span class="kv-label">SHA-256</span><span class="kv-value mono">{{ release.sha256 }}</span></div>
                <div class="kv-row"><span class="kv-label">签名指纹</span><span class="kv-value mono">{{ release.signatureSha256 }}</span></div>
                <n-button type="primary" size="large" block class="upgrade-btn" :loading="downloading" @click="download">
                    <template #icon><i class="fa-solid fa-download"></i></template>
                    下载安装包
                </n-button>
                <p class="panel-tip">下载完成后可在通知栏或「下载」目录安装；首次安装需要在系统里允许本应用安装未知来源应用。</p>
            </section>

            <div v-else class="empty-panel">
                <span class="empty-icon"><i class="fa-solid fa-box-open"></i></span>
                <strong>暂无可用安装包</strong>
                <span>前往「App 构建管理」更新代码并编译 APK 后，这里会出现最新版本</span>
            </div>
            <a v-if="downloadUrl" :href="downloadUrl" :download="downloadName" class="fallback-link" target="_blank"
                rel="noopener noreferrer">如果浏览器没有开始下载，点击这里手动下载</a>
        </div>
    </ManagementShell>
</template>

<script>
/**
 * 在线升级（系统会话）：
 * 网页端不需要安装 App，但服务器发布新 APK 后用户可能想在浏览器里直接下载安装包。
 * 这里读取安卓端同一份「最新 Release」接口，展示版本信息并提供下载入口。
 */
import { NButton } from 'naive-ui'
import ManagementShell from '@/components/main/management-shell.vue'
import http from '@/libs/http'
import { prepareAndroidDownload, startAndroidDownload } from '@/api/android'
import { formatBytes } from '@/utils/format'

export default {
    name: 'UpgradePage',
    components: { ManagementShell, NButton },
    data() {
        return {
            loading: false,
            error: '',
            release: null,
            releaseTaskId: null,
            downloading: false,
            downloadUrl: '',
            downloadName: ''
        }
    },
    mounted() {
        this.load()
    },
    methods: {
        formatBytes,
        async load() {
            this.loading = true
            this.error = ''
            try {
                const data = await http.get('/android/releases/latest', null, { silent: true })
                if (data && data.available && data.release) {
                    this.release = data.release
                    this.releaseTaskId = data.release.taskId
                } else {
                    this.release = null
                    this.releaseTaskId = null
                }
            } catch (error) {
                this.error = '暂时无法检查更新，请稍后重试。'
            } finally {
                this.loading = false
            }
        },
        async download() {
            if (!this.releaseTaskId || this.downloading) return
            this.downloading = true
            try {
                const task = { id: this.releaseTaskId }
                const download = await prepareAndroidDownload(task, false)
                this.downloadUrl = download.url
                this.downloadName = download.filename
                startAndroidDownload(download)
            } catch (error) {
                this.error = error.message || '下载准备失败，请稍后重试。'
            } finally {
                this.downloading = false
            }
        }
    }
}
</script>

<style scoped>
.upgrade-btn {
    margin-top: 12px;
}

.panel-tip {
    margin: 10px 0 0;
    font-size: 11.5px;
    color: var(--muted);
    line-height: 1.6;
}

.fallback-link {
    display: block;
    margin-top: 12px;
    text-align: center;
    font-size: 12.5px;
    color: var(--pine);
}
</style>
