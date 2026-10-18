<template>
    <n-modal v-model:show="visible" preset="card" :title="title" style="width: 560px" :mask-closable="false">
        <n-alert v-if="intro" type="info" :show-icon="true" style="margin-bottom: 14px">
            {{ intro }}
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
            <n-form-item label="xunlei-win-api 服务地址" path="url">
                <n-input v-model:value="form.url" placeholder="http://192.168.3.100:8899"
                    @keyup.enter="submit" />
                <template #feedback>
                    Windows 机器上部署的 xunlei-win-api 服务（负责静默创建迅雷任务并读取进度）
                </template>
            </n-form-item>
            <n-form-item label="Windows 保存根目录（可选）" path="savePath">
                <n-input v-model:value="form.savePath" placeholder="D:\迅雷下载" />
                <template #feedback>
                    留空时新任务使用迅雷客户端的默认下载目录；电影港下载会在此目录下按剧名建子目录
                </template>
            </n-form-item>
        </n-form>
        <div v-if="testResult" class="test-result" :class="testResult.success ? 'ok' : 'fail'">
            <i :class="testResult.success ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle-exclamation'"></i>
            {{ testResult.message }}
        </div>
        <template #footer>
            <div class="modal-footer">
                <n-button :loading="testing" @click="test">
                    <template #icon><i class="fa-solid fa-plug-circle-check"></i></template>
                    测试连接
                </n-button>
                <span class="footer-gap"></span>
                <n-button @click="visible = false">取消</n-button>
                <n-button type="primary" :loading="saving" @click="submit">保存</n-button>
            </div>
        </template>
    </n-modal>
</template>

<script>
import { NAlert, NButton, NForm, NFormItem, NInput, NModal } from 'naive-ui'
/**
 * 迅雷下载【Win】连接配置表单（「迅雷下载【Win】」会话首次使用与顶栏入口共用）：
 * 只需填写 Windows 机器上 xunlei-win-api 的服务地址与保存根目录，无账号密码。
 */
import { GetXunleiWinSettings, SaveXunleiWinSettings, TestXunleiWinConnection } from '@/api/xunlei-win'

export default {
    components: { NAlert, NButton, NForm, NFormItem, NInput, NModal },
    name: 'XunleiWinSettingsDialog',
    props: {
        show: {
            type: Boolean,
            default: false
        },
        /** 首次配置时显示的一段引导说明（普通设置场景留空） */
        intro: {
            type: String,
            default: ''
        }
    },
    emits: ['update:show', 'saved'],
    data() {
        return {
            form: {
                url: '',
                savePath: ''
            },
            configured: false,
            loading: false,
            saving: false,
            testing: false,
            testResult: null
        }
    },
    computed: {
        rules() {
            return { url: { required: true, message: '请填写 xunlei-win-api 服务地址', trigger: ['input', 'blur'] } }
        },
        visible: {
            get() {
                return this.show
            },
            set(value) {
                this.$emit('update:show', value)
            }
        },
        title() {
            return this.configured ? '迅雷【Win】连接设置' : '配置迅雷【Win】连接'
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
        async load() {
            this.testResult = null
            this.loading = true
            try {
                const settings = (await GetXunleiWinSettings()) || {}
                this.form = {
                    url: settings.url || '',
                    savePath: settings.savePath || ''
                }
                this.configured = !!settings.configured
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        payload() {
            return {
                url: this.form.url.trim(),
                savePath: this.form.savePath.trim()
            }
        },
        async test() {
            this.testResult = null
            this.testing = true
            try {
                const result = await TestXunleiWinConnection(this.payload())
                this.testResult = result || { success: false, message: '未返回测试结果' }
            } catch (error) {
                this.testResult = { success: false, message: '测试失败，请检查地址与网络' }
            } finally {
                this.testing = false
            }
        },
        submit() {
            this.$refs.formRef.validate(async (errors) => {
                if (errors) {
                    return
                }
                this.saving = true
                try {
                    const data = await SaveXunleiWinSettings(this.payload())
                    this.configured = !!data?.configured
                    this.$message.success('迅雷【Win】配置已保存')
                    this.visible = false
                    this.$emit('saved', data)
                } catch (error) {
                    // 拦截器已提示
                } finally {
                    this.saving = false
                }
            })
        }
    }
}
</script>

<style scoped>
.test-result {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 2px;
    padding: 8px 10px;
    border-radius: 10px;
    font-size: 12.5px;
}

.test-result.ok {
    color: var(--ok);
    background: rgba(16, 185, 129, .10);
}

.test-result.fail {
    color: var(--danger);
    background: rgba(244, 63, 94, .10);
}

.modal-footer {
    display: flex;
    align-items: center;
    gap: 8px;
}

.footer-gap {
    flex: 1;
}
</style>
