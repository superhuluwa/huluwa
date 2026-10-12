<template>
    <n-modal v-model:show="visible" preset="card" :title="title" style="width: 560px" :mask-closable="false">
        <n-alert v-if="intro" type="info" :show-icon="true" style="margin-bottom: 14px">
            {{ intro }}
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
            <n-form-item label="迅雷直连地址" path="url">
                <n-input v-model:value="form.url" placeholder="http://192.168.3.202:2345"
                    @keyup.enter="submit" />
            </n-form-item>
            <n-form-item label="保存路径" path="savePath">
                <n-input v-model:value="form.savePath" placeholder="如 /downloads/，迅雷 App 新建任务页里选择的目录" />
            </n-form-item>
            <n-form-item label="Authorization 认证头（可选）" path="token">
                <n-input v-model:value="form.token" type="password" show-password-on="click"
                    :placeholder="tokenPlaceholder" />
                <template #feedback>
                    迅雷前端加了 Basic 认证时填写（如 Basic xxx），飞牛直连端口一般不需要
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
 * 直连地址、保存路径、可选 Authorization 认证头；「测试连接」用表单里的最新值直接探测，
 * 认证头留空表示沿用服务端已保存的值（接口不回传明文）。
 * 直连地址与保存路径的口径来自参考脚本 qBittorrentXunleiTransfer.cs 的 xlURL / xlSavePath。
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
                token: ''
            },
            tokenSet: false,
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
        tokenPlaceholder() {
            return this.tokenSet ? '留空表示沿用已保存的认证头' : '如 Basic xxx（一般留空）'
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
                    token: ''
                }
                this.tokenSet = !!settings.tokenSet
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
                // 空串表示沿用已保存的认证头（服务端只写不读）
                token: this.form.token
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
                    this.configured = !!data?.configured
                    this.form.token = ''
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
