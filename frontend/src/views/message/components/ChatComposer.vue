<template>
    <div class="composer" :class="{ mobile }">
        <div class="composer-inner" :class="{ focused, busy: sending }">
            <textarea ref="input" v-model="draft" rows="1" :placeholder="placeholder" :disabled="readonly"
                @focus="focused = true" @blur="focused = false" @keydown="onKeydown" @input="autoGrow"></textarea>
            <button class="send" type="button" :disabled="!canSend" :title="sending ? busyText : '发送'"
                @click="submit">
                <i :class="sending ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-paper-plane'"></i>
            </button>
        </div>
        <div v-if="sending" class="composer-tip">
            <i :class="busyIcon"></i>{{ busyText }}
        </div>
        <div v-else-if="hint" class="composer-tip">{{ hint }}</div>
    </div>
</template>

<script>
/**
 * 输入条（对齐量子端 ChatComposer 的交互与视觉，去掉附件/指令面板）：
 * - Enter 发送 / Shift+Enter 换行（量子端 Enter 发送、最多 4 行；桌面端补 Shift+Enter）
 * - 电影港会话：整条输入即搜索关键字，发送后由服务端返回结果气泡
 * - 磁力下载会话：整条输入可含多个磁力链接，发送后由服务端提交 qB 并回复结果
 * - 视频解析会话：整条输入即分享口令，发送后由服务端解析并回复视频 / 图集气泡
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
        /** 会话类型：movie 搜索 / magnet 提交磁力 / video 解析口令 / system 只读 / chat 普通对话 */
        kind: {
            type: String,
            default: 'chat'
        },
        /** 手机端：输入区更宽松，按钮放大便于拇指点按 */
        mobile: {
            type: Boolean,
            default: false
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
            if (this.kind === 'movie') {
                return this.mobile ? '输入片名开始搜索' : '输入片名搜索电影港（Enter 发送 / Shift+Enter 换行）'
            }
            if (this.kind === 'magnet') {
                return this.mobile ? '粘贴磁力链接提交下载' : '粘贴磁力链接（magnet:?xt=urn:btih:…）回车提交；可一次贴多条'
            }
            if (this.kind === 'video') {
                return this.mobile
                    ? '粘贴抖音等分享口令'
                    : '把抖音等 App 的分享口令整段贴进来（含链接），回车开始解析'
            }
            return this.mobile ? '输入消息…' : '输入消息…（Enter 发送 / Shift+Enter 换行）'
        },
        canSend() {
            return !this.readonly && !this.sending && this.draft.trim().length > 0
        },
        /** 发送中的提示文案与图标：磁力会话提交任务、视频会话解析口令，都不是搜索 */
        busyText() {
            if (this.kind === 'magnet') {
                return '正在提交到 qBittorrent，请稍候…'
            }
            if (this.kind === 'video') {
                return '正在解析分享口令，视频较大时可能需要十几秒…'
            }
            return '正在搜索，请稍候…'
        },
        busyIcon() {
            if (this.kind === 'magnet') {
                return 'fa-solid fa-cloud-arrow-up'
            }
            if (this.kind === 'video') {
                return 'fa-solid fa-clapperboard'
            }
            return 'fa-solid fa-magnifying-glass'
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
    /* 左右留白与顶栏 / 消息流共用同一组变量，输入框与头像列上下对齐 */
    padding: 6px var(--chat-gutter-right, 16px) 4px var(--chat-gutter-left, 16px);
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

/* ==========================================================================
   手机端：输入区像聊天软件一样贴底，末段留出系统手势安全距离
   ========================================================================== */
.composer.mobile {
    padding-top: 8px;
    padding-bottom: max(8px, env(safe-area-inset-bottom));
    background: rgba(11, 15, 25, .96);
    backdrop-filter: blur(10px);
}

.composer.mobile .composer-inner {
    gap: 4px;
    padding: 0 4px 0 0;
    border-radius: 18px;
}

.composer.mobile .composer-inner textarea {
    min-height: 44px;
    max-height: 120px;
    padding: 11px 0 11px 15px;
    /* 14px 会触发 iOS 聚焦缩放，手机端统一 16px */
    font-size: 16px;
    line-height: 1.45;
}

.composer.mobile .send {
    width: 44px;
    height: 44px;
    margin: 4px 0 4px 2px;
    font-size: 17px;
    background: var(--accent);
    color: #04121B;
}

.composer.mobile .send:hover:not(:disabled) {
    background: var(--accent-2);
}

.composer.mobile .send:disabled {
    background: rgba(148, 163, 184, .18);
    color: var(--text-3);
    opacity: 1;
}

.composer.mobile .composer-tip {
    padding: 7px 6px 1px;
    font-size: 12px;
}
</style>
