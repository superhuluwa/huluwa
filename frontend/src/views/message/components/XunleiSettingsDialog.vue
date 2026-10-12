<template>
    <n-modal v-model:show="visible" preset="card" :title="title" style="width: 560px" :mask-closable="false">
        <n-alert v-if="intro" type="info" :show-icon="true" style="margin-bottom: 14px">
            {{ intro }}
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
            <n-form-item label="迅雷直连地址" path="url">
                <n-input v-model:value="form.url" placeholder="http://192.168.3.202:5666"
                    @keyup.enter="submit" />
                <template #feedback>
                    飞牛原生迅雷填 NAS 地址和端口，程序自动补全 CGI 路径；Docker 版可填 http://NAS_IP:2345
                </template>
            </n-form-item>
            <n-form-item label="保存路径" path="savePath">
                <n-input v-model:value="form.savePath" placeholder="迅雷 App 新建任务页里选择的完整目录路径" />
            </n-form-item>
            <template v-if="nativeFnos">
                <n-form-item label="飞牛登录账号" path="fnosUsername">
                    <n-input v-model:value="form.fnosUsername" placeholder="飞牛 NAS 登录账号" />
                </n-form-item>
                <n-form-item label="飞牛登录密码" path="fnosPassword">
                    <n-input v-model:value="form.fnosPassword" type="password" show-password-on="click"
                        :placeholder="passwordPlaceholder" />
                    <template #feedback>密码保存在本机服务端配置库，界面不会回显；留空沿用已保存的密码</template>
                </n-form-item>
                <n-form-item label="两步验证码（启用时填写）" path="twofaCode">
                    <n-input v-model:value="form.twofaCode" maxlength="6" placeholder="当前 6 位验证码；不保存" />
                </n-form-item>
            </template>
            <n-form-item v-if="!nativeFnos || !form.fnosUsername.trim()"
                :label="nativeFnos ? '手动 fnos-token（备用）' : 'Authorization 认证头（可选）'" path="token">
                <n-input v-model:value="form.token" type="password" show-password-on="click"
                    :placeholder="tokenPlaceholder" />
                <template #feedback>
                    {{ nativeFnos ? '不用账号密码时，可手动填写 fnos-token Cookie 的值；无需填写 pan-auth 或前缀' : '仅迅雷前端加了 Basic 认证时填写，如 Basic xxx' }}
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
/**
 * 迅雷远程下载连接配置表单（「迅雷下载」会话首次使用与顶栏入口共用）：
 * 原生模式可用飞牛账号密码自动登录并续取 fnos-token，也保留手动 Cookie 入口。
 * 两步验证码只用于当前登录请求；密码与认证值只写不读。
 */
import { GetXunleiSettings, SaveXunleiSettings, TestXunleiConnection } from '@/api/xunlei'

export default {
    name: 'XunleiSettingsDialog',
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
                savePath: '',
                token: '',
                fnosUsername: '',
                fnosPassword: '',
                twofaCode: ''
            },
            tokenSet: false,
            fnosPasswordSet: false,
            savedUsername: '',
            configured: false,
            loading: false,
            saving: false,
            testing: false,
            testResult: null,
            rules: {
                url: { required: true, message: '请填写迅雷直连地址', trigger: ['input', 'blur'] },
                savePath: { required: true, message: '请填写保存路径（迅雷 App 新建任务页里选择的目录）', trigger: ['input', 'blur'] }
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
            return this.configured ? '迅雷连接设置' : '配置迅雷连接'
        },
        nativeFnos() {
            const url = this.form.url.trim()
            return url.includes('/cgi/ThirdParty/xunlei/index.cgi') || /:5666(?:\/|$)/.test(url)
        },
        tokenPlaceholder() {
            if (this.tokenSet) return '留空表示沿用已保存的认证值'
            return this.nativeFnos ? '粘贴 fnos-token 的值' : '如 Basic xxx（一般留空）'
        },
        passwordPlaceholder() {
            return this.fnosPasswordSet && this.form.fnosUsername.trim() === this.savedUsername
                ? '留空表示沿用已保存的密码' : '飞牛 NAS 登录密码'
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
                const data = await GetXunleiSettings()
                const settings = data || {}
                this.form = {
                    url: settings.url || '',
                    savePath: settings.savePath || '',
                    token: '',
                    fnosUsername: settings.fnosUsername || '',
                    fnosPassword: '',
                    twofaCode: ''
                }
                this.tokenSet = !!settings.tokenSet
                this.fnosPasswordSet = !!settings.fnosPasswordSet
                this.savedUsername = settings.fnosUsername || ''
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
                savePath: this.form.savePath.trim(),
                token: this.form.token,
                fnosUsername: this.nativeFnos ? this.form.fnosUsername.trim() : '',
                fnosPassword: this.nativeFnos ? this.form.fnosPassword : '',
                twofaCode: this.nativeFnos ? this.form.twofaCode.trim() : ''
            }
        },
        async test() {
            this.testResult = null
            this.testing = true
            try {
                const result = await TestXunleiConnection(this.payload())
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
                    const data = await SaveXunleiSettings(this.payload())
                    this.tokenSet = !!data?.tokenSet
                    this.fnosPasswordSet = !!data?.fnosPasswordSet
                    this.savedUsername = data?.fnosUsername || ''
                    this.configured = !!data?.configured
                    this.form.token = ''
                    this.form.fnosPassword = ''
                    this.form.twofaCode = ''
                    this.$message.success('迅雷配置已保存')
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
