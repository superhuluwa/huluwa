<template>
    <div class="composer">
        <div class="composer-inner" :class="{ focused, busy: sending }">
            <textarea ref="input" v-model="draft" rows="1" :placeholder="placeholder" :disabled="readonly"
                @focus="focused = true" @blur="focused = false" @keydown="onKeydown" @input="autoGrow"></textarea>
            <button class="send" type="button" :disabled="!canSend" :title="sending ? '搜索中…' : '发送'"
                @click="submit">
                <i :class="sending ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-paper-plane'"></i>
            </button>
        </div>
        <div v-if="sending" class="composer-tip">
            <i class="fa-solid fa-magnifying-glass"></i>正在搜索，请稍候…
        </div>
        <div v-else-if="hint" class="composer-tip">{{ hint }}</div>
    </div>
</template>

<script>
/**
 * 输入条（对齐量子端 ChatComposer 的交互与视觉，去掉附件/指令面板）：
 * - Enter 发送 / Shift+Enter 换行（量子端 Enter 发送、最多 4 行；桌面端补 Shift+Enter）
 * - 电影港会话：整条输入即搜索关键字，发送后由服务端返回结果气泡
 */
export default {
    name: 'ChatComposer',
    props: {
        disabled: {
            type: Boolean,
            default: false
        },
        sending: {
            type: Boolean,
            default: false
        },
        hint: {
            type: String,
            default: ''
        },
        /** 会话类型：movie 提示搜索语义，其它会话提示普通对话 */
        kind: {
            type: String,
            default: 'chat'
        }
    },
    emits: ['send'],
    data() {
        return {
            draft: '',
            focused: false
        }
    },
    computed: {
        /** 系统通知为只读会话（不可输入）；其它会话正常可编辑 */
        readonly() {
            return this.disabled || this.kind === 'system'
        },
        placeholder() {
            if (this.kind === 'system') {
                return '系统通知为只读会话，消息由各模块自动写入'
            }
            if (this.disabled) {
                return '请先选择左侧会话'
            }
            return this.kind === 'movie'
                ? '输入片名搜索电影港（Enter 发送 / Shift+Enter 换行）'
                : '输入消息…（Enter 发送 / Shift+Enter 换行）'
        },
        canSend() {
            return !this.readonly && !this.sending && this.draft.trim().length > 0
        }
    },
    methods: {
        autoGrow() {
            const el = this.$refs.input
            if (!el) {
                return
            }
            el.style.height = 'auto'
            el.style.height = Math.min(el.scrollHeight, 96) + 'px'
        },
        onKeydown(event) {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                this.submit()
            }
        },
        submit() {
            if (!this.canSend) {
                return
            }
            const text = this.draft.trim()
            this.$emit('send', text)
            this.draft = ''
            this.$nextTick(() => {
                this.autoGrow()
                this.$refs.input?.focus()
            })
        }
    }
}
</script>

<style scoped>
.composer {
    flex: none;
    border-top: 1px solid var(--line);
    padding: 6px 8px 4px;
    background: var(--bg);
}

.composer-inner {
    display: flex;
    align-items: flex-end;
    gap: 6px;
    padding: 0 6px 0 0;
    border: 1px solid var(--line-2);
    border-radius: 14px;
    background: var(--chat-input, #1C2841);
}

.composer-inner.focused {
    border-color: var(--accent);
    box-shadow: 0 0 0 3px rgba(14, 165, 233, .14);
}

.composer-inner.busy {
    opacity: .85;
}

.composer-inner textarea {
    flex: 1;
    min-height: 38px;
    max-height: 96px;
    padding: 10px 0 10px 16px;
    border: 0;
    outline: none;
    resize: none;
    background: none;
    color: var(--text);
    font-family: inherit;
    font-size: 14px;
    line-height: 1.55;
}

.composer-inner textarea::placeholder {
    color: rgba(148, 163, 184, .65);
}

.send {
    width: 36px;
    height: 36px;
    flex: none;
    margin-bottom: 4px;
    border: 0;
    border-radius: 50%;
    background: none;
    color: var(--accent);
    cursor: pointer;
    font-size: 14px;
    transition: background .18s, color .18s, opacity .18s;
}

.send:hover:not(:disabled) {
    background: var(--accent-weak);
}

.send:disabled {
    color: var(--text-3);
    cursor: not-allowed;
    opacity: .6;
}

.composer-tip {
    padding: 5px 10px 2px;
    font-size: 11.5px;
    color: var(--text-3);
    display: flex;
    align-items: center;
    gap: 6px;
}
</style>
