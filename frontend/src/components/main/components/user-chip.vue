<template>
    <div class="user-avatar-dropdown">
        <n-dropdown :options="options" @select="handleSelect">
            <button class="user-chip" type="button">
                <span class="ava">{{ avatarText }}</span>
                <span class="name">{{ auth.username || 'admin' }}</span>
                <i class="fa-solid fa-chevron-down"></i>
            </button>
        </n-dropdown>

        <n-modal v-model:show="passwordModal.visible" preset="card" title="修改密码" style="width: 460px"
            :mask-closable="false">
            <n-form ref="passwordForm" :model="passwordModal.data" :rules="passwordRules" label-placement="left"
                label-width="90">
                <n-form-item label="原密码" path="oldPassword">
                    <n-input v-model:value="passwordModal.data.oldPassword" type="password" show-password-on="click"
                        placeholder="请输入原密码" />
                </n-form-item>
                <n-form-item label="新密码" path="newPassword">
                    <n-input v-model:value="passwordModal.data.newPassword" type="password" show-password-on="click"
                        placeholder="至少 6 位" />
                </n-form-item>
                <n-form-item label="确认密码" path="confirmPassword">
                    <n-input v-model:value="passwordModal.data.confirmPassword" type="password"
                        show-password-on="click" placeholder="请再次输入新密码" />
                </n-form-item>
            </n-form>
            <template #footer>
                <div class="modal-footer">
                    <n-button @click="passwordModal.visible = false">取消</n-button>
                    <n-button type="primary" :loading="passwordModal.loading" @click="submitPassword">确认</n-button>
                </div>
            </template>
        </n-modal>
    </div>
</template>

<script>
import { h } from 'vue'
import { useAuthStore } from '@/stores/auth'

export default {
    name: 'UserChip',
    setup() {
        return { auth: useAuthStore() }
    },
    data() {
        return {
            passwordModal: {
                visible: false,
                loading: false,
                data: {
                    oldPassword: '',
                    newPassword: '',
                    confirmPassword: ''
                }
            },
            passwordRules: {
                oldPassword: { required: true, message: '请输入原密码', trigger: ['input', 'blur'] },
                newPassword: [
                    { required: true, message: '请输入新密码', trigger: ['input', 'blur'] },
                    { min: 6, message: '新密码至少 6 位', trigger: ['input', 'blur'] }
                ],
                confirmPassword: {
                    required: true,
                    validator: (rule, value) => {
                        if (!value) return new Error('请再次输入新密码')
                        if (value !== this.passwordModal.data.newPassword) return new Error('两次输入的密码不一致')
                        return true
                    },
                    trigger: ['input', 'blur']
                }
            }
        }
    },
    computed: {
        avatarText() {
            const name = this.auth.username || 'A'
            return name.charAt(0).toUpperCase()
        },
        options() {
            return [
                { label: '修改密码', key: 'password', icon: () => h('i', { class: 'fa-solid fa-key' }) },
                { label: '退出登录', key: 'logout', icon: () => h('i', { class: 'fa-solid fa-right-from-bracket' }) }
            ]
        }
    },
    methods: {
        handleSelect(key) {
            if (key === 'password') {
                this.passwordModal.data = { oldPassword: '', newPassword: '', confirmPassword: '' }
                this.passwordModal.visible = true
            } else if (key === 'logout') {
                this.$dialog.warning({
                    title: '退出登录',
                    content: '确认退出当前账号？',
                    positiveText: '退出',
                    negativeText: '取消',
                    onPositiveClick: () => {
                        this.auth.clear()
                        this.$router.push({ name: 'login' })
                    }
                })
            }
        },
        submitPassword() {
            this.$refs.passwordForm.validate(async (errors) => {
                if (errors) return
                this.passwordModal.loading = true
                try {
                    await this.auth.changePassword({
                        oldPassword: this.passwordModal.data.oldPassword,
                        newPassword: this.passwordModal.data.newPassword
                    })
                    this.passwordModal.visible = false
                    this.$message.success('密码已修改，请重新登录')
                    this.$router.push({ name: 'login' })
                } catch (error) {
                    // 错误提示已由 http 拦截器统一处理
                } finally {
                    this.passwordModal.loading = false
                }
            })
        }
    }
}
</script>

<style scoped>
.user-avatar-dropdown {
    display: flex;
    align-items: center;
}

.user-chip {
    display: flex;
    align-items: center;
    gap: 8px;
    background: rgba(148, 163, 184, .06);
    border: 1px solid var(--line);
    border-radius: 999px;
    padding: 4px 12px 4px 4px;
    color: var(--text-2);
    cursor: pointer;
}

.user-chip:hover {
    border-color: var(--line-2);
    color: var(--text);
}

.ava {
    width: 26px;
    height: 26px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    color: #fff;
    background: linear-gradient(135deg, #0EA5E9 0%, #10B981 100%);
}

.name {
    font-size: 13px;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}
</style>