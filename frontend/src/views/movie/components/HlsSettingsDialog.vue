<template>
    <n-modal v-model:show="visible" preset="card" title="离线下载设置" style="width: 500px; max-width: calc(100vw - 32px)">
        <n-spin :show="loading">
            <n-form v-if="form" label-placement="top">
                <n-form-item label="容器媒体根目录"><n-input :value="form.mediaRoot" readonly /></n-form-item>
                <p class="tip">{{ form.toolsReady ? '下载工具已就绪' : '缺少下载工具，需要更新 Docker 镜像' }}；{{ form.mediaWritable ? '媒体目录可写' : '媒体目录不可写，请检查挂载与权限' }}。</p>
                <n-form-item label="分片线程数"><n-input-number v-model:value="form.threadCount" :min="1" :max="32" /></n-form-item>
                <n-form-item label="最大尝试次数"><n-input-number v-model:value="form.maxAttempts" :min="1" :max="10" /></n-form-item>
                <n-form-item label="每次任务超时（分钟）"><n-input-number v-model:value="form.timeoutMinutes" :min="5" :max="1440" /></n-form-item>
                <n-form-item label="封装格式"><n-select v-model:value="form.format" :options="formats" /></n-form-item>
                <n-form-item label="限速"><n-input v-model:value="form.maxSpeed" placeholder="如 15M 或 500K，留空不限速" /></n-form-item>
                <p class="tip">同时下载一集，保留原始编码。失败后自动退避重试；已运行的任务沿用启动时的设置。</p>
            </n-form>
        </n-spin>
        <template #footer><div class="footer">
            <n-button @click="visible = false">关闭</n-button>
            <n-button type="primary" :disabled="loading || !form" :loading="saving" @click="save">保存</n-button>
        </div></template>
    </n-modal>
</template>

<script>
import { NButton, NForm, NFormItem, NInput, NInputNumber, NModal, NSelect, NSpin } from 'naive-ui'
import { GetHlsSettings, SaveHlsSettings } from '@/api/hls'
export default {
    components: { NButton, NForm, NFormItem, NInput, NInputNumber, NModal, NSelect, NSpin },
    props: { show: Boolean }, emits: ['update:show'],
    data() { return { form: null, loading: false, saving: false, formats: [{ label: 'MP4', value: 'mp4' }, { label: 'MKV', value: 'mkv' }] } },
    computed: { visible: { get() { return this.show }, set(value) { this.$emit('update:show', value) } } },
    watch: { show(next) { if (next) this.load() } },
    methods: {
        async load() {
            this.loading = true; this.form = null
            try { this.form = await GetHlsSettings() } catch (error) { /* 拦截器已提示 */ }
            finally { this.loading = false }
        },
        async save() {
            this.saving = true
            try { await SaveHlsSettings(this.form); window.$message?.success('离线下载设置已保存'); this.visible = false }
            catch (error) { /* 拦截器已提示 */ } finally { this.saving = false }
        }
    }
}
</script>

<style scoped>
.tip { color: var(--text-3); font-size: 12px; overflow-wrap: anywhere; }
.footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
