<template>
    <n-modal v-model:show="visible" preset="card" title="连接配置" style="width: 480px" :mask-closable="false">
        <div class="account">
            <div class="account-row">
                <span class="label">服务器地址</span>
                <span class="value">{{ auth.server || '—' }}</span>
            </div>
            <div class="account-row">
                <span class="label">当前账号</span>
                <span class="value">{{ auth.username || '未登录' }}</span>
            </div>
            <div class="account-row">
                <span class="label">实时连接</span>
                <span class="pill" :class="chatStore.wsReady ? 'ok' : 'off'">
                    <span class="dot"></span>{{ chatStore.wsReady ? '已连接' : '轮询同步' }}
                </span>
            </div>
        </div>

        <n-button quaternary block style="margin: 14px 0" @click="changing = !changing">
            <template #icon><i class="fa-solid fa-key"></i></template>
            {{ changing ? '收起修改密码' : '修改密码' }}
        </n-button>

        <n-form v-if="changing" label-placement="top" :model="form">
            <n-form-item label="原密码">
                <n-input v-model:value="form.oldPassword" type="password" show-password-on="click" placeholder="请输入原密码" />
            </n-form-item>
            <n-form-item label="新密码（6–64 位）">
                <n-input v-model:value="form.newPassword" type="password" show-password-on="click" placeholder="请输入新密码" />
            </n-form-item>
            <n-form-item label="确认新密码">
                <n-input v-model:value="form.confirmPassword" type="password" show-password-on="click" placeholder="请再次输入新密码" />
            </n-form-item>
            <n-button type="primary" block :loading="saving" :disabled="!canSave" @click="submit">确认修改</n-button>
        </n-form>

        <n-button text block class="logout" @click="logout">
            <i class="fa-solid fa-right-from-bracket"></i>&nbsp; 退出登录 / 切换服务器
        </n-button>
    </n-modal>
</template>

<script>
/**
 * 连接配置弹层：对齐安卓 AccountSheet（服务器地址、账号、实时连接状态、修改密码、退出登录）。
 * 修改密码成功后服务端会让历史令牌失效，因此改完直接回登录页。
 */
import { NButton, NForm, NFormItem, NInput, NModal } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useFullscreenPage } from '@/composables/useFullscreenPage'

export default {
    name: 'AccountSheet',
    components: { NButton, NForm, NFormItem, NInput, NModal },
    props: {
        show: {
            type: Boolean,
            default: false
        }
    },
    emits: ['update:show'],
    setup() {
        return { auth: useAuthStore(), chatStore: useChatStore() }
    },
    data() {
        return {
            changing: false,
            saving: false,
            form: { oldPassword: '', newPassword: '', confirmPassword: '' }
        }
    },
    computed: {
        visible: {
            get() {
                return this.show
            },
            set(value) {
                this.$emit('update:show', value)
                if (!value) {
                    this.changing = false
                    this.form = { oldPassword: '', newPassword: '', confirmPassword: '' }
                }
            }
        },
        canSave() {
            return Boolean(this.form.oldPassword && this.form.newPassword && this.form.confirmPassword)
        }
    },
    methods: {
        async submit() {
            if (this.form.newPassword.length < 6 || this.form.newPassword.length > 64) {
                this.$message.warning('新密码长度应为 6–64 位')
                return
            }
            if (this.form.newPassword !== this.form.confirmPassword) {
                this.$message.warning('两次输入的新密码不一致')
                return
            }
            this.saving = true
            try {
                await this.auth.changePassword({
                    oldPassword: this.form.oldPassword,
                    newPassword: this.form.newPassword
                })
                this.visible = false
                this.$message.success('密码已修改，请重新登录')
                useFullscreenPage().value = false
                this.$router.push({ name: 'login' })
            } catch (error) {
                // 拦截器已提示
            } finally {
                this.saving = false
            }
        },
        logout() {
            this.$dialog.warning({
                title: '退出登录',
                content: '退出后可以切换服务器或账号，服务器任务会继续运行。',
                positiveText: '退出',
                negativeText: '取消',
                onPositiveClick: () => {
                    this.visible = false
                    this.auth.clear()
                    useFullscreenPage().value = false
                    this.$router.push({ name: 'login' })
                }
            })
        }
    }
}
</script>

<style scoped>
.account {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.account-row {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13px;
}

.account-row .label {
    flex: none;
    width: 76px;
    color: var(--muted);
}

.account-row .value {
    min-width: 0;
    color: var(--ink);
    overflow-wrap: anywhere;
}

.logout {
    margin-top: 16px;
    color: var(--danger) !important;
}
</style>
