<template>
    <n-modal v-model:show="visible" preset="card" :title="title" style="width: 580px" :mask-closable="false">
        <n-alert v-if="intro" type="info" :show-icon="true" style="margin-bottom: 14px">
            {{ intro }}
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
            <n-form-item label="视频解析服务地址" path="url">
                <n-input v-model:value="form.url" placeholder="http://192.168.3.202:8080"
                    @keyup.enter="submit" />
            </n-form-item>
            <n-form-item label="登录账号（可选）" path="username">
                <n-input v-model:value="form.username" placeholder="留空表示匿名调用（服务端按 IP 放行）"
                    @keyup.enter="submit" />
            </n-form-item>
            <n-form-item label="登录密码" path="password">
                <n-input v-model:value="form.password" type="password" show-password-on="click"
                    :placeholder="passwordPlaceholder" />
            </n-form-item>
            <n-form-item label="试解析（可选）" path="sample">
                <n-input v-model:value="form.sample" type="textarea" :rows="2" :maxlength="500"
                    placeholder="粘贴一条抖音等分享口令，点右侧「试解析」验证服务是否可用" />
            </n-form-item>
        </n-form>
        <div v-if="testResult" class="test-result" :class="testResult.success ? 'ok' : 'fail'">
            <i :class="testResult.success ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle-exclamation'"></i>
            <span class="test-message">{{ testResult.message }}</span>
        </div>
        <div v-if="parsed" class="parsed-card">
            <img v-if="parsed.cover" class="parsed-cover" :src="parsed.cover" alt="作品封面" />
            <div class="parsed-body">
                <div class="parsed-title">{{ parsed.title || '（无标题）' }}</div>
                <div class="parsed-meta">作者：{{ parsed.author || '未知作者' }}</div>
                <div class="parsed-meta">
                    视频：{{ parsed.video ? '已解析到直链' : '无' }} · 图片：{{ parsed.imageCount || 0 }} 张
                </div>
            </div>
        </div>
        <template #footer>
            <div class="modal-footer">
                <n-button :loading="testing" @click="test">
                    <template #icon><i class="fa-solid fa-plug-circle-check"></i></template>
                    测试连接
                </n-button>
                <n-button :loading="parsing" :disabled="!form.sample.trim()" @click="parseSample">
                    <template #icon><i class="fa-solid fa-wand-magic-sparkles"></i></template>
                    试解析
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
 * 视频解析服务配置表单（「视频解析」会话首次使用与顶栏入口共用）：
 * 服务地址 / 登录账号 / 密码，外加一个不落库的「试解析」用于验证链路。
 * 密码留空表示沿用服务端已保存的密码（接口不回传明文）；账号留空表示匿名调用。
 */
import { GetVideoSettings, SaveVideoSettings, TestVideoConnection, TestVideoParse } from '@/api/video'

export default {
    name: 'VideoSettingsDialog',
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
                username: '',
                password: '',
                sample: ''
            },
            passwordSet: false,
            /** 载入时的连接信息快照：用于判断表单是否被改过（改了要先保存再试解析） */
            savedUrl: '',
            savedUsername: '',
            saving: false,
            testing: false,
            parsing: false,
            loading: false,
            testResult: null,
            parsed: null,
            rules: {
                url: { required: true, message: '请填写视频解析服务地址', trigger: ['input', 'blur'] }
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
            return this.passwordSet ? '视频解析设置' : '配置视频解析服务'
        },
        passwordPlaceholder() {
            return this.passwordSet ? '留空表示沿用已保存的密码' : '登录密码（账号留空时无需填写）'
        },
        /** 连接信息是否被改动过（地址 / 账号变了，或填了新密码） */
        formDirty() {
            return this.form.url.trim() !== (this.savedUrl || '').trim()
                || this.form.username.trim() !== (this.savedUsername || '').trim()
                || !!this.form.password
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
            this.parsed = null
            this.loading = true
            try {
                const data = (await GetVideoSettings()) || {}
                this.form = {
                    url: data.url || '',
                    username: data.username || '',
                    password: '',
                    sample: ''
                }
                this.savedUrl = data.url || ''
                this.savedUsername = data.username || ''
                this.passwordSet = !!data.passwordSet
            } catch (e) {
                // 拦截器已提示
            } finally {
                this.loading = false
            }
        },
        payload() {
            return {
                url: this.form.url.trim(),
                username: this.form.username.trim(),
                // 空串表示沿用已保存的密码（服务端只写不读）
                password: this.form.password
            }
        },
        async test() {
            this.testResult = null
            this.testing = true
            try {
                const result = await TestVideoConnection(this.payload())
                this.testResult = result || { success: false, message: '未返回测试结果' }
            } catch (e) {
                this.testResult = { success: false, message: '测试失败，请检查地址与网络' }
            } finally {
                this.testing = false
            }
        },
        /**
         * 试解析：后端按「已保存」的配置调用解析服务，因此表单里的连接信息若被改过，
         * 先提示保存，避免出现「明明改了地址却仍按旧配置解析」的困惑。
         */
        async parseSample() {
            const sample = this.form.sample.trim()
            if (!sample) {
                return
            }
            if (this.formDirty) {
                this.testResult = { success: false, message: '连接信息已修改，请先点「保存」再试解析' }
                return
            }
            this.parsing = true
            this.testResult = null
            try {
                this.parsed = (await TestVideoParse(sample)) || null
                if (this.parsed) {
                    this.testResult = { success: true, message: '解析成功，链路可用' }
                }
            } catch (e) {
                this.parsed = null
                this.testResult = {
                    success: false,
                    message: e?.message || '解析失败；请先保存配置再重试'
                }
            } finally {
                this.parsing = false
            }
        },
        submit() {
            this.$refs.formRef.validate(async (errors) => {
                if (errors) {
                    return
                }
                this.saving = true
                try {
                    const data = await SaveVideoSettings(this.payload())
                    this.passwordSet = !!data?.passwordSet
                    this.form.password = ''
                    this.$message.success('视频解析配置已保存')
                    this.visible = false
                    this.$emit('saved', data)
                } catch (e) {
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
    align-items: flex-start;
    gap: 8px;
    margin-top: 2px;
    padding: 8px 10px;
    border-radius: 10px;
    font-size: 12.5px;
    line-height: 1.55;
}

.test-result.ok {
    color: var(--ok);
    background: rgba(16, 185, 129, .10);
}

.test-result.fail {
    color: var(--danger);
    background: rgba(244, 63, 94, .10);
}

.parsed-card {
    display: flex;
    gap: 12px;
    margin-top: 10px;
    padding: 10px;
    border: 1px solid var(--line);
    border-radius: 10px;
    background: rgba(148, 163, 184, .05);
}

.parsed-cover {
    width: 72px;
    height: 96px;
    flex: none;
    border-radius: 8px;
    object-fit: cover;
    background: rgba(148, 163, 184, .1);
}

.parsed-body {
    min-width: 0;
}

.parsed-title {
    font-size: 13.5px;
    font-weight: 600;
    color: var(--text);
    margin-bottom: 6px;
    word-break: break-word;
}

.parsed-meta {
    font-size: 12px;
    color: var(--text-2);
    line-height: 1.7;
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
