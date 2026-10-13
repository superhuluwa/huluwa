<template>
    <n-modal v-model:show="visible" preset="card" :title="title" style="width: 560px" :mask-closable="false">
        <n-alert v-if="intro" type="info" :show-icon="true" style="margin-bottom: 14px">
            {{ intro }}
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
            <n-form-item label="qBittorrent 服务地址" path="url">
                <n-input v-model:value="form.url" placeholder="http://192.168.3.202:8080"
                    @keyup.enter="submit" />
            </n-form-item>
            <n-form-item label="使用账号密码登录" path="useAuth">
                <n-switch v-model:value="form.useAuth" />
                <span class="switch-tip">关闭后依赖 qB「Web UI」里的 IP 白名单放行</span>
            </n-form-item>
            <template v-if="form.useAuth">
                <n-form-item label="用户名" path="username">
                    <n-input v-model:value="form.username" placeholder="qBittorrent 登录用户名" />
                </n-form-item>
                <n-form-item label="密码" path="password">
                    <n-input v-model:value="form.password" type="password" show-password-on="click"
                        :placeholder="passwordPlaceholder" />
                </n-form-item>
            </template>
            <n-form-item label="默认保存路径（可选）" path="savePath">
                <n-input v-model:value="form.savePath" placeholder="如 /downloads/电影，留空用 qB 默认目录" />
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
import {
    NAlert,
    NButton,
    NForm,
    NFormItem,
    NInput,
    NModal,
    NSwitch
} from 'naive-ui'
/**
 * qBittorrent 连接配置表单（「磁力下载」会话首次使用与页脚入口共用）：
 * 服务地址、账号密码、默认保存路径；「测试连接」用表单里的最新值直接探测，
 * 密码留空表示沿用服务端已保存的密码（接口不回传明文）。
 */
import { GetQbSettings, SaveQbSettings, TestQbConnection } from '@/api/qbittorrent'

export default {
    components: { NAlert, NButton, NForm, NFormItem, NInput, NModal, NSwitch },
    name: 'QbSettingsDialog',
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
                useAuth: true,
                username: '',
                password: '',
                savePath: ''
            },
            passwordSet: false,
            loading: false,
            saving: false,
            testing: false,
            testResult: null,
            rules: {
                url: { required: true, message: '请填写 qBittorrent 服务地址', trigger: ['input', 'blur'] },
                username: {
                    validator: (rule, value) => {
                        if (!this.form.useAuth) {
                            return true
                        }
                        return !!(value && value.trim())
                    },
                    message: '请填写登录用户名，或关闭账号密码登录',
                    trigger: ['input', 'blur']
                }
            }
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
            return this.passwordSet ? 'qBittorrent 连接设置' : '配置 qBittorrent 连接'
        },
        passwordPlaceholder() {
            return this.passwordSet ? '留空表示沿用已保存的密码' : 'qBittorrent 登录密码'
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
                const data = await GetQbSettings()
                const settings = data || {}
                this.form = {
                    url: settings.url || '',
                    useAuth: settings.configured ? !!settings.useAuth : true,
                    username: settings.username || '',
                    password: '',
                    savePath: settings.savePath || ''
                }
                this.passwordSet = !!settings.passwordSet
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        payload() {
            return {
                url: this.form.url.trim(),
                username: this.form.useAuth ? this.form.username.trim() : '',
                // 空串表示沿用已保存的密码（服务端只写不读）
                password: this.form.password,
                savePath: this.form.savePath.trim(),
                useAuth: this.form.useAuth
            }
        },
        async test() {
            this.testResult = null
            this.testing = true
            try {
                const result = await TestQbConnection(this.payload())
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
                    const data = await SaveQbSettings(this.payload())
                    this.passwordSet = !!data?.passwordSet
                    this.form.password = ''
                    this.$message.success('qBittorrent 配置已保存')
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
.switch-tip {
    margin-left: 10px;
    font-size: 12px;
    color: var(--text-3);
}

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
