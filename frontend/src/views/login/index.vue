<template>
    <div class="login">
        <div class="login-card glass-card">
            <img class="logo-mark" src="/huluwa-logo.svg" alt="葫芦娃 Logo" width="64" height="64" />
            <h2>葫芦娃助手</h2>
            <div class="sub">{{ initialized ? 'OPS CONSOLE' : 'FIRST RUN SETUP' }}</div>

            <div v-if="initialized" class="tip">请输入管理员账号和密码登录</div>
            <div v-else class="tip">首次启动，请设置管理员账号和密码</div>

            <n-form ref="formRef" :model="form" :rules="rules" :show-label="false" size="large">
                <n-form-item path="username">
                    <n-input v-model:value="form.username" type="text" placeholder="请输入管理员账号" @keydown.enter="submit">
                        <template #prefix>
                            <i class="fa-solid fa-user"></i>
                        </template>
                    </n-input>
                </n-form-item>
                <n-form-item path="password">
                    <n-input v-model:value="form.password" type="password" show-password-on="click"
                        placeholder="请输入密码" @keydown.enter="submit">
                        <template #prefix>
                            <i class="fa-solid fa-lock"></i>
                        </template>
                    </n-input>
                </n-form-item>
                <n-form-item v-if="!initialized" path="confirmPassword">
                    <n-input v-model:value="form.confirmPassword" type="password" show-password-on="click"
                        placeholder="请再次输入密码" @keydown.enter="submit">
                        <template #prefix>
                            <i class="fa-solid fa-lock"></i>
                        </template>
                    </n-input>
                </n-form-item>
            </n-form>

            <n-button type="primary" block size="large" :loading="loading" @click="submit">
                <template #icon>
                    <i :class="initialized ? 'fa-solid fa-right-to-bracket' : 'fa-solid fa-user-shield'"></i>
                </template>
                {{ initialized ? '登录' : '设置账号密码并进入' }}
            </n-button>

            <div v-if="initialized" class="foot">
                <span class="mono">葫芦娃助手 v1.0.0</span>
            </div>
        </div>
    </div>
</template>

<script>
import { NButton, NForm, NFormItem, NInput } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

export default {
    components: { NButton, NForm, NFormItem, NInput },
    name: 'LoginPage',
    setup() {
        return { auth: useAuthStore() }
    },
    data() {
        return {
            loading: false,
            form: {
                username: 'admin',
                password: '',
                confirmPassword: ''
            },
            rules: {
                username: {
                    required: true,
                    validator: (rule, value) => {
                        if (!value || !value.trim()) return new Error('请输入管理员账号')
                        if (!/^[A-Za-z0-9._-]+$/.test(value.trim())) return new Error('账号只能包含字母、数字、点、下划线和短横线')
                        return true
                    },
                    trigger: ['input', 'blur']
                },
                password: {
                    required: true,
                    validator: (rule, value) => {
                        if (!value) return new Error('请输入密码')
                        if (!this.initialized && value.length < 6) return new Error('密码长度至少 6 位')
                        return true
                    },
                    trigger: ['input', 'blur']
                },
                confirmPassword: {
                    validator: (rule, value) => {
                        if (this.initialized) return true
                        if (!value) return new Error('请再次输入密码')
                        if (value !== this.form.password) return new Error('两次输入的密码不一致')
                        return true
                    },
                    trigger: ['input', 'blur']
                }
            }
        }
    },
    computed: {
        initialized() {
            return this.auth.initialized
        }
    },
    async mounted() {
        try {
            await this.auth.fetchStatus()
            if (!this.initialized) {
                this.form.username = this.auth.username || this.form.username || 'admin'
            }
        } catch (error) {
            // 后端未就绪时仍展示登录表单，提交时会再次校验
        }
    },
    methods: {
        submit() {
            this.$refs.formRef.validate(async (errors) => {
                if (errors) return
                this.loading = true
                try {
                    const payload = {
                        username: this.form.username.trim(),
                        password: this.form.password
                    }
                    if (this.initialized) {
                        await this.auth.login(payload)
                        this.$message.success('登录成功')
                    } else {
                        await this.auth.setup(payload)
                        this.$message.success('管理员账号密码设置成功')
                    }
                    const redirect = this.$route.query.redirect || '/messages'
                    this.$router.push(redirect)
                } catch (error) {
                    if (!this.initialized) {
                        // 初始化可能已被其他会话完成，刷新状态让用户改走登录
                        this.auth.fetchStatus().catch(() => {})
                    }
                } finally {
                    this.loading = false
                }
            })
        }
    }
}
</script>

<style scoped>
.login {
    height: 100%;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    background:
        radial-gradient(1200px 600px at 20% -10%, #1e293b 0%, transparent 60%),
        radial-gradient(900px 500px at 110% 110%, #134e4a 0%, transparent 55%),
        var(--bg);
    overflow: auto;
}

.login::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image:
        linear-gradient(var(--line) 1px, transparent 1px),
        linear-gradient(90deg, var(--line) 1px, transparent 1px);
    background-size: 44px 44px;
    opacity: .35;
    mask-image: radial-gradient(600px 400px at 50% 50%, #000 20%, transparent 100%);
    -webkit-mask-image: radial-gradient(600px 400px at 50% 50%, #000 20%, transparent 100%);
}

.login-card {
    position: relative;
    width: 380px;
    padding: 34px 32px 26px;
    text-align: center;
    z-index: 1;
}

.logo-mark {
    width: 64px;
    height: 64px;
    margin: 0 auto 16px;
    border-radius: 16px;
    display: block;
    object-fit: contain;
    box-shadow: 0 8px 24px rgba(14, 165, 233, .35);
}

h2 {
    margin: 0;
    font-size: 21px;
    color: var(--text);
}

.sub {
    margin-top: 6px;
    font-size: 11px;
    letter-spacing: 1.4px;
    color: var(--text-3);
}

.tip {
    margin: 22px 0 12px;
    font-size: 13px;
    color: var(--text-2);
}

.foot {
    margin-top: 18px;
    padding-top: 14px;
    border-top: 1px solid var(--line);
}
/* 移动端：登录卡片占满可用宽度，避免 380px 定宽在窄屏溢出或贴边 */
@media (max-width: 900px) {
    .login {
        align-items: flex-start;
        padding: 24px 0 32px;
    }

    .login-card {
        width: auto;
        max-width: none;
        margin: 0 16px;
        padding: 26px 20px 20px;
    }
}
</style>
